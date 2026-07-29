package com.example.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.example.security.CryptoManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException

class UdpDiscoveryManager(
    private val context: Context,
    private val myDeviceId: String,
    private var myDeviceName: String,
    private val myTcpPort: Int,
    private var myAvatarColorHex: String,
    private var myStatusMessage: String
) {
    companion object {
        private const val TAG = "UdpDiscovery"
        const val UDP_PORT = 8888
        private const val BROADCAST_INTERVAL_MS = 3000L
        private const val BUFFER_SIZE = 8192
    }

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val jsonAdapter = moshi.adapter(NetworkPacket::class.java)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var broadcastJob: Job? = null
    private var listenJob: Job? = null

    private val _peerDiscoveredFlow = MutableSharedFlow<NetworkPacket>(extraBufferCapacity = 64)
    val peerDiscoveredFlow: SharedFlow<NetworkPacket> = _peerDiscoveredFlow.asSharedFlow()

    private var socket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun updateProfile(name: String, avatarColorHex: String, statusMessage: String) {
        myDeviceName = name
        myAvatarColorHex = avatarColorHex
        myStatusMessage = statusMessage
    }

    fun start() {
        acquireMulticastLock()
        initSocket()
        startListening()
        startBroadcasting()
    }

    fun stop() {
        broadcastJob?.cancel()
        listenJob?.cancel()

        // Send disconnect broadcast packet
        scope.launch {
            sendDisconnectPacket()
            try {
                socket?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing socket", e)
            }
            releaseMulticastLock()
        }
    }

    private fun acquireMulticastLock() {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("LANMessengerMulticastLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire MulticastLock", e)
        }
    }

    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MulticastLock", e)
        }
    }

    private fun initSocket() {
        try {
            socket = DatagramSocket(UDP_PORT).apply {
                broadcast = true
                reuseAddress = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing UDP DatagramSocket on port $UDP_PORT", e)
        }
    }

    private fun startBroadcasting() {
        broadcastJob?.cancel()
        broadcastJob = scope.launch {
            while (isActive) {
                broadcastPresence()
                delay(BROADCAST_INTERVAL_MS)
            }
        }
    }

    private fun broadcastPresence() {
        val myIp = getLocalIpAddress() ?: return
        val packet = NetworkPacket(
            packetType = PacketType.DISCOVERY,
            senderId = myDeviceId,
            senderName = myDeviceName,
            senderIp = myIp,
            senderPort = myTcpPort,
            avatarColorHex = myAvatarColorHex,
            statusMessage = myStatusMessage
        )

        sendUdpPacket(packet)
    }

    private fun sendDisconnectPacket() {
        val myIp = getLocalIpAddress() ?: return
        val packet = NetworkPacket(
            packetType = PacketType.DISCONNECT,
            senderId = myDeviceId,
            senderName = myDeviceName,
            senderIp = myIp,
            senderPort = myTcpPort
        )
        sendUdpPacket(packet)
    }

    private fun sendUdpPacket(packet: NetworkPacket) {
        try {
            val json = jsonAdapter.toJson(packet)
            val encrypted = CryptoManager.encrypt(json)
            val bytes = encrypted.toByteArray(Charsets.UTF_8)

            val broadcastAddr = getBroadcastAddress() ?: InetAddress.getByName("255.255.255.255")
            val datagram = DatagramPacket(bytes, bytes.size, broadcastAddr, UDP_PORT)

            socket?.send(datagram)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending UDP packet", e)
        }
    }

    private fun startListening() {
        listenJob?.cancel()
        listenJob = scope.launch {
            val buffer = ByteArray(BUFFER_SIZE)
            while (isActive) {
                try {
                    val udpSocket = socket ?: break
                    val datagram = DatagramPacket(buffer, buffer.size)
                    udpSocket.receive(datagram)

                    val receivedStr = String(datagram.data, 0, datagram.length, Charsets.UTF_8)
                    val decryptedJson = CryptoManager.decrypt(receivedStr)
                    val packet = jsonAdapter.fromJson(decryptedJson)

                    if (packet != null && packet.senderId != myDeviceId) {
                        _peerDiscoveredFlow.emit(packet)
                    }
                } catch (e: SocketException) {
                    Log.d(TAG, "UDP Socket closed or interrupted")
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Error receiving or parsing UDP packet", e)
                }
            }
        }
    }

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address.hostAddress.indexOf(':') < 0) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching local IP address", e)
        }
        return "127.0.0.1"
    }

    private fun getBroadcastAddress(): InetAddress? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                for (interfaceAddress in networkInterface.interfaceAddresses) {
                    val broadcast = interfaceAddress.broadcast
                    if (broadcast != null) {
                        return broadcast
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching broadcast address", e)
        }
        return null
    }
}

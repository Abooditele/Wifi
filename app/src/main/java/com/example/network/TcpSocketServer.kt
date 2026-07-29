package com.example.network

import android.util.Log
import com.example.security.CryptoManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

class TcpSocketServer(val port: Int = 8889) {
    companion object {
        private const val TAG = "TcpSocketServer"
    }

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val jsonAdapter = moshi.adapter(NetworkPacket::class.java)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null
    private var listenJob: Job? = null

    private val _incomingPacketFlow = MutableSharedFlow<NetworkPacket>(extraBufferCapacity = 128)
    val incomingPacketFlow: SharedFlow<NetworkPacket> = _incomingPacketFlow.asSharedFlow()

    fun start() {
        listenJob?.cancel()
        listenJob = scope.launch {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                Log.d(TAG, "TCP Server Started on port $port")

                while (isActive) {
                    val clientSocket = serverSocket?.accept() ?: break
                    launch { handleClientSocket(clientSocket) }
                }
            } catch (e: SocketException) {
                Log.d(TAG, "TCP ServerSocket closed")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting TCP ServerSocket", e)
            }
        }
    }

    fun stop() {
        listenJob?.cancel()
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing TCP ServerSocket", e)
        }
    }

    private suspend fun handleClientSocket(socket: Socket) {
        socket.use { client ->
            try {
                val inputStream = DataInputStream(client.getInputStream())
                val outputStream = DataOutputStream(client.getOutputStream())

                // Read length prefix
                val length = inputStream.readInt()
                if (length <= 0 || length > 10 * 1024 * 1024) { // 10MB max packet guard
                    return
                }

                val buffer = ByteArray(length)
                inputStream.readFully(buffer)

                val encryptedStr = String(buffer, Charsets.UTF_8)
                val decryptedJson = CryptoManager.decrypt(encryptedStr)
                val packet = jsonAdapter.fromJson(decryptedJson)

                if (packet != null) {
                    _incomingPacketFlow.emit(packet)

                    // Write immediate TCP acknowledgement for connection verification
                    outputStream.writeBoolean(true)
                    outputStream.flush()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing client TCP connection", e)
            }
        }
    }
}

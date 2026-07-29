package com.example.network

import android.util.Log
import com.example.security.CryptoManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

object TcpSocketClient {
    private const val TAG = "TcpSocketClient"
    private const val TIMEOUT_MS = 5000
    private const val MAX_RETRIES = 3

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val jsonAdapter = moshi.adapter(NetworkPacket::class.java)

    /**
     * Sends NetworkPacket to target device IP and Port.
     * Returns true if successfully delivered.
     */
    suspend fun sendPacket(targetIp: String, targetPort: Int, packet: NetworkPacket): Boolean {
        return withContext(Dispatchers.IO) {
            val json = jsonAdapter.toJson(packet)
            val encryptedStr = CryptoManager.encrypt(json)
            val bytes = encryptedStr.toByteArray(Charsets.UTF_8)

            var success = false
            var attempt = 0

            while (!success && attempt < MAX_RETRIES) {
                attempt++
                try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(targetIp, targetPort), TIMEOUT_MS)
                        socket.soTimeout = TIMEOUT_MS

                        val outputStream = DataOutputStream(socket.getOutputStream())
                        val inputStream = DataInputStream(socket.getInputStream())

                        // Write packet length then packet bytes
                        outputStream.writeInt(bytes.size)
                        outputStream.write(bytes)
                        outputStream.flush()

                        // Read response ack
                        success = inputStream.readBoolean()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Attempt $attempt failed sending packet to $targetIp:$targetPort - ${e.message}")
                    if (attempt < MAX_RETRIES) {
                        delay(300L * attempt)
                    }
                }
            }

            success
        }
    }
}

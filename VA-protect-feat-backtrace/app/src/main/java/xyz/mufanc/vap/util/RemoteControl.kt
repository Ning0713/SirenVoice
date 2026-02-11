package xyz.mufanc.vap.util

import android.os.Bundle
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.BindException
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class RemoteControl private constructor(
    private val port: Int,
    private val bus: EventBus<Bundle>
) {

    companion object {
        private const val TAG = "RemoteControl"
        private const val BIND_ADDRESS = "0.0.0.0"
        private const val INITIAL_PORT = 25000

        // 将 Json 字符串转换为 Android Bundle 类型
        private fun parseJsonToBundle(json: String): Bundle {
            val bundle = Bundle()
            val jsonObj = Gson().fromJson(json, JsonObject::class.java)

            for ((key, element) in jsonObj.entrySet()) {
                when {
                    element.isJsonPrimitive -> {
                        val value = element.asJsonPrimitive
                        when {
                            value.isBoolean -> bundle.putBoolean(key, value.asBoolean)
                            value.isNumber -> bundle.putInt(key, value.asInt)
                            value.isString -> bundle.putString(key, value.asString)
                        }
                    }
                }
            }

            Log.i(TAG, "parse result: $bundle")
            return bundle
        }

        private fun handleClient(client: Socket, bus: EventBus<Bundle>) {
            try {
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                val message = reader.readLine()
                
                Log.d(TAG, "TCP message received: $message")

                if (message == null || message.trim().isEmpty()) {
                    Log.w(TAG, "Empty message received")
                    return
                }

                when {
                    message.trim() == "ping" -> {
                        Log.i(TAG, "pong!")
                        client.getOutputStream().write("pong\n".toByteArray())
                    }
                    message.trim().startsWith("{") -> {
                        // JSON message - treat as replay command
                        try {
                            Log.i(TAG, "replaying...")
                            bus.emit("command", parseJsonToBundle(message.trim()))
                        } catch (err: Throwable) {
                            Log.e(TAG, "failed to replay", err)
                        }
                    }
                    else -> {
                        Log.w(TAG, "Unknown message: $message")
                    }
                }
            } catch (err: Throwable) {
                Log.e(TAG, "Error handling client", err)
            } finally {
                try {
                    client.close()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }

        private fun run(port: Int, bus: EventBus<Bundle>): RemoteControl {
            val rc = RemoteControl(port, bus)
            
            thread {
                try {
                    val serverSocket = ServerSocket(port, 50, null)
                    Log.i(TAG, "Remote control server is running on 0.0.0.0:$port")
                    
                    while (true) {
                        try {
                            val client = serverSocket.accept()
                            Log.d(TAG, "Client connected: ${client.inetAddress}")
                            
                            // Handle each client in a new thread
                            thread {
                                handleClient(client, bus)
                            }
                        } catch (err: Throwable) {
                            Log.e(TAG, "Error accepting client", err)
                }
            }
                } catch (err: BindException) {
                    Log.e(TAG, "Port $port already in use", err)
                } catch (err: Throwable) {
                    Log.e(TAG, "Server error", err)
                }
            }
            
            return rc
        }

        fun init(): EventBus<Bundle> {
            val bus = EventBus<Bundle>()

            // 尝试绑定端口
            for (i in 0..1000) {
                val port = INITIAL_PORT + i
                try {
                    run(port, bus)
                    break
                } catch (err: BindException) {
                    Log.d(TAG, "Port $port already in use, trying next...")
                }
            }

            return bus
        }
    }
}

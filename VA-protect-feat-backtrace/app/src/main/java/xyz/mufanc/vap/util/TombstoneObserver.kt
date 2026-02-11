package xyz.mufanc.vap.util

import android.os.FileObserver
import android.system.Os
import android.util.ArrayMap
import xyz.mufanc.vap.TombstoneProtos.Tombstone
import java.io.File
import java.lang.reflect.Modifier
import kotlin.concurrent.thread

@Suppress("Deprecation")
class TombstoneObserver private constructor() : FileObserver(TARGET_DIR, EVENTS) {

    private val lastModifiedTime = ArrayMap<String, Long>()
    private var isRunning = true
    
    // 用于跟踪重放攻击标记
    companion object {
        private const val TAG = "TombstoneObserver"
        private const val TARGET_DIR = "/data/tombstones"
        private const val EVENTS = ALL_EVENTS

        @JvmStatic
        private lateinit var observer: TombstoneObserver
        
        // 标记是否为重放攻击触发的 tombstone
        @Volatile
        private var expectReplayTombstone = false

        private val eventNames: Map<Int, String> = mapOf(
            *FileObserver::class.java.declaredFields
                .filter { it.name == it.name.uppercase() }
                .filter { it.type == Int::class.java }
                .filter { it.modifiers and Modifier.STATIC != 0 && it.modifiers and Modifier.FINAL != 0 }
                .map { Pair(it.get(null) as Int, it.name) }
                .toTypedArray()
        )

        fun install() {
            observer = TombstoneObserver()
            
            val dir = File(TARGET_DIR)
            if (!dir.exists()) {
                Log.w(TAG, "Tombstone directory does not exist: $TARGET_DIR")
                return
            }
            
            if (!dir.canRead()) {
                Log.w(TAG, "Cannot read tombstone directory: $TARGET_DIR")
                return
            }
            
            observer.startWatching()
            Log.i(TAG, "FileObserver started watching: $TARGET_DIR")
            
            observer.startPolling()

            var cur: Int = EVENTS
            val names = mutableListOf<String>()

            while (cur != 0) {
                val lowbit = cur and -cur
                cur = cur xor lowbit
                eventNames[lowbit]?.let { names.add(it) }
            }

            Log.i(TAG, "file observer installed (${names.joinToString(", ")})")
        }
        
        fun markReplayTombstone() {
            expectReplayTombstone = true
            Log.d(TAG, "Marked next tombstone as replay attack")
        }
    }

    override fun onEvent(event: Int, path: String?) {
        Log.d(TAG, "FileObserver event: $event, path: $path")
        
        val file = File(TARGET_DIR, path ?: return)
        val name = file.name

        // 支持 tombstone_XX 和 tombstone_XX.pb 两种格式
        if (!name.matches("tombstone_\\d{2}(\\.pb)?".toRegex())) {
            Log.d(TAG, "Ignoring non-tombstone file: $name")
            return
        }

        val ts = Os.stat(file.absolutePath).st_mtime

        if (lastModifiedTime[name] == ts) {
            Log.d(TAG, "Tombstone $name already processed (same timestamp)")
            return
        }

        lastModifiedTime[name] = ts

        Log.i(TAG, "new tombstone detected: $name, parsing...")

        try {
            handleNewTombstone(file)
        } catch (err: Throwable) {
            Log.e(TAG, "Failed to parse tombstone: $name", err)
        }
    }

    private fun handleNewTombstone(file: File) {
        // 等待文件写入完成
        Thread.sleep(100)
        
        if (!file.exists() || !file.canRead()) {
            Log.w(TAG, "Cannot read tombstone file: ${file.absolutePath}")
            return
        }
        
        // 检测文件格式
        val firstLine = file.bufferedReader().use { it.readLine() }
        
        if (firstLine?.startsWith("*** *** ***") == true) {
            // 文本格式的 tombstone
            Log.i(TAG, "Detected text format tombstone, parsing...")
            handleTextTombstone(file)
        } else {
            // Protobuf 格式的 tombstone
            Log.i(TAG, "Detected protobuf format tombstone, parsing...")
            try {
                val tombstone = Tombstone.parseFrom(file.inputStream())
                Log.i(TAG, "tombstone loaded.")

                val protoFile = File("/data/tombstones", file.name.split(".")[0] + ".proto")
                protoFile.writeText(tombstone.toString())

                val verifier = Verifier(tombstone)
                verifier.verify()
            } catch (err: Throwable) {
                Log.e(TAG, "Failed to parse protobuf tombstone", err)
            }
        }
    }

    private fun handleTextTombstone(file: File) {
        val content = file.readText()
        
        // 检查信号类型
        if (!content.contains("signal 35")) {
            Log.d(TAG, "Not a debuggerd signal 35 tombstone, ignore!")
            return
        }
        
        Log.i(TAG, "Signal 35 tombstone detected, analyzing backtrace...")
        
        // 检查是否为重放攻击触发的 tombstone
        val isReplay = expectReplayTombstone
        if (isReplay) {
            expectReplayTombstone = false
            Log.w(TAG, "🛡️ REPLAY ATTACK DETECTED! This tombstone was triggered by replay()")
            TextVerifier.warnUser()
            TextVerifier.forceStopPackage()
            return
        }
        
        val backtraceStart = content.indexOf("backtrace:")
        if (backtraceStart == -1) {
            Log.w(TAG, "No backtrace found in tombstone")
            return
        }
        
        val backtrace = content.substring(backtraceStart)
        Log.d(TAG, "Backtrace extracted, length: ${backtrace.length}")
        
        val fromHw = backtrace.contains("libhidlbase.so")
        
        Log.i(TAG, "Hardware call detected: $fromHw")
        
        if (!fromHw) {
            Log.w(TAG, "🛡️ ATTACK DETECTED: No hardware call in backtrace!")
            TextVerifier.warnUser()
            TextVerifier.forceStopPackage()
        } else {
            Log.i(TAG, "✅ Legitimate voice trigger: Hardware call found")
        }
    }
    
    private fun startPolling() {
        thread(name = "TombstonePoller") {
            Log.i(TAG, "Polling thread started")
            var lastCheck = System.currentTimeMillis()
            
            while (isRunning) {
                try {
                    Thread.sleep(2000)
                    
                    val dir = File(TARGET_DIR)
                    if (!dir.exists() || !dir.canRead()) {
                        continue
                    }
                    
                    dir.listFiles()?.forEach { file ->
                        if (file.name.matches("tombstone_\\d{2}(\\.pb)?".toRegex())) {
                            val mtime = Os.stat(file.absolutePath).st_mtime * 1000
                            
                            if (mtime > lastCheck) {
                                Log.d(TAG, "Polling detected new/modified tombstone: ${file.name}")
                                
                                if (lastModifiedTime[file.name] != Os.stat(file.absolutePath).st_mtime) {
                                    onEvent(CLOSE_WRITE, file.name)
                                }
                            }
                        }
                    }
                    
                    lastCheck = System.currentTimeMillis()
                } catch (err: Throwable) {
                    Log.e(TAG, "Polling error", err)
                }
            }
            
            Log.i(TAG, "Polling thread stopped")
        }
    }
}

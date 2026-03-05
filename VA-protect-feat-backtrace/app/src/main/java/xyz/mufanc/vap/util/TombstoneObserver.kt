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
        private const val EVENTS = CLOSE_WRITE or MODIFY or CREATE

        @JvmStatic
        private lateinit var observer: TombstoneObserver

        private val eventNames: Map<Int, String> = mapOf(
            *FileObserver::class.java.declaredFields
                .filter { it.name == it.name.uppercase() }
                .filter { it.type == Int::class.java }
                .filter { it.modifiers and Modifier.STATIC != 0 && it.modifiers and Modifier.FINAL != 0 }
                .map { Pair(it.get(null) as Int, it.name) }
                .toTypedArray()
        )

        fun install() {
            Log.i(TAG, "TombstoneObserver.install() CALLED")
            
            observer = TombstoneObserver()
            Log.i(TAG, "TombstoneObserver instance created")
            
            val dir = File(TARGET_DIR)
            if (!dir.exists()) {
                Log.w(TAG, "Tombstone directory does not exist: $TARGET_DIR")
                return
            }
            
            if (!dir.canRead()) {
                Log.w(TAG, "Cannot read tombstone directory: $TARGET_DIR")
                return
            }
            
            Log.i(TAG, "Tombstone directory accessible: $TARGET_DIR")
            
            // 列出现有的 tombstone 文件并初始化时间戳
            dir.listFiles()?.let { files ->
                Log.i(TAG, "Existing tombstone files: ${files.size}")
                files.filter { it.name.matches("tombstone_\\d{2}(\\.pb)?".toRegex()) }
                    .forEach { 
                        Log.d(TAG, "  - ${it.name} (${it.length()} bytes)")
                        // 初始化已存在文件的时间戳，避免重复处理
                        observer.lastModifiedTime[it.name] = Os.stat(it.absolutePath).st_mtime
                    }
            }
            
            Log.i(TAG, "Starting FileObserver...")
            observer.startWatching()
            Log.i(TAG, "FileObserver started watching: $TARGET_DIR")
            
            Log.i(TAG, "Starting polling thread...")
            observer.startPolling()
            Log.i(TAG, "Polling thread started (async)")

            var cur: Int = EVENTS
            val names = mutableListOf<String>()

            while (cur != 0) {
                val lowbit = cur and -cur
                cur = cur xor lowbit
                eventNames[lowbit]?.let { names.add(it) }
            }

            Log.i(TAG, "File observer events: ${names.joinToString(", ")}")
            Log.i(TAG, "TombstoneObserver.install() COMPLETED")
        }
    }

    override fun onEvent(event: Int, path: String?) {
        if (path == null) {
            // 过滤掉 path 为 null 的事件，减少日志噪音
            return
        }
        
        Log.i(TAG, "onEvent() called: event=$event, path=$path")
        
        val file = File(TARGET_DIR, path)
        val name = file.name

        // 支持 tombstone_XX 和 tombstone_XX.pb 两种格式
        if (!name.matches("tombstone_\\d{2}(\\.pb)?".toRegex())) {
            Log.d(TAG, "Ignoring non-tombstone file: $name")
            return
        }

        if (!file.exists()) {
            Log.w(TAG, "File does not exist: ${file.absolutePath}")
            return
        }

        val ts = Os.stat(file.absolutePath).st_mtime
        val savedTs = lastModifiedTime[name]
        
        Log.d(TAG, "Checking tombstone: $name, currentTs=$ts, savedTs=$savedTs")

        if (lastModifiedTime[name] == ts) {
            Log.d(TAG, "Tombstone $name already processed (same timestamp: $ts)")
            return
        }

        lastModifiedTime[name] = ts
        Log.i(TAG, "NEW tombstone detected: $name (timestamp: $ts), parsing...")

        try {
            handleNewTombstone(file)
        } catch (err: Throwable) {
            Log.e(TAG, "Failed to parse tombstone: $name", err)
        }
    }

    private fun handleNewTombstone(file: File) {
        Log.i(TAG, ">>> handleNewTombstone() called: ${file.name}")
        
        try {
            // 等待文件写入完成
            Thread.sleep(100)
            
            if (!file.exists()) {
                Log.w(TAG, "File does not exist: ${file.absolutePath}")
                return
            }
            
            if (!file.canRead()) {
                Log.w(TAG, "Cannot read file: ${file.absolutePath}")
                return
            }
            
            Log.i(TAG, "File accessible, size: ${file.length()} bytes")
            
            // 检测文件格式
            val firstLine = try {
                file.bufferedReader().use { it.readLine() }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to read first line", e)
                return
            }
            
            Log.d(TAG, "First line: ${firstLine?.take(50)}")
            
            if (firstLine?.startsWith("*** *** ***") == true) {
                Log.i(TAG, ">>> Detected TEXT format tombstone")
                handleTextTombstone(file)
            } else {
                Log.i(TAG, ">>> Detected PROTOBUF format tombstone")
                try {
                    val tombstone = Tombstone.parseFrom(file.inputStream())
                    Log.i(TAG, "Protobuf tombstone loaded")

                    val protoFile = File("/data/tombstones", file.name.split(".")[0] + ".proto")
                    protoFile.writeText(tombstone.toString())

                    val verifier = Verifier(tombstone)
                    verifier.verify()
                } catch (err: Throwable) {
                    Log.e(TAG, "Failed to parse protobuf tombstone", err)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error in handleNewTombstone", e)
        }
        
        Log.i(TAG, ">>> handleNewTombstone() finished")
    }

    private fun handleTextTombstone(file: File) {
        Log.i(TAG, "[STEP 1] handleTextTombstone() START")
        
        try {
            val content = file.readText()
            Log.i(TAG, "[STEP 1.1] Read tombstone file, size: ${content.length} bytes")
            
            // 检查信号类型
            if (!content.contains("signal 35")) {
                Log.w(TAG, "[STEP 1.2] Not a signal 35 tombstone, ignoring")
                return
            }
            
            Log.i(TAG, "[STEP 1.2] Signal 35 tombstone CONFIRMED")
            
            val backtraceStart = content.indexOf("backtrace:")
            if (backtraceStart == -1) {
                Log.w(TAG, "[STEP 1.3] No backtrace found")
                return
            }
            
            val backtrace = content.substring(backtraceStart)
            Log.i(TAG, "[STEP 1.3] Backtrace extracted, length: ${backtrace.length} bytes")
            
            // 提取前 50 行 backtrace 用于分析
            val backtraceLines = backtrace.lines().take(50)
            Log.i(TAG, "[DEBUG] First 50 lines of backtrace:")
            backtraceLines.forEachIndexed { index, line ->
                if (line.contains("libhidlbase") || line.contains("replay") || 
                    line.contains("RemoteControl") || line.contains("EventBus") ||
                    line.contains("Thread-") || line.contains("invoke")) {
                    Log.i(TAG, "[DEBUG]   Line $index: $line")
                }
            }
            
            // 检查是否包含硬件层调用
            val hasHardwareCall = backtrace.contains("libhidlbase.so")
            
            Log.i(TAG, "[STEP 1.4] Hardware call found: $hasHardwareCall")
            
            if (!hasHardwareCall) {
                Log.w(TAG, "")
                Log.w(TAG, "!!! REPLAY ATTACK DETECTED !!!")
                Log.w(TAG, "!!! No hardware call in backtrace !!!")
                Log.w(TAG, "")
                
                try {
                    Log.i(TAG, "[STEP 2] Calling TextVerifier.warnUser()...")
                    TextVerifier.warnUser()
                    Log.i(TAG, "[STEP 2] warnUser() completed")
                    
                    Log.i(TAG, "[STEP 3] Calling TextVerifier.forceStopPackage()...")
                    TextVerifier.forceStopPackage()
                    Log.i(TAG, "[STEP 3] forceStopPackage() completed")
                    
                    Log.i(TAG, "")
                    Log.i(TAG, ">>> ATTACK BLOCKED SUCCESSFULLY <<<")
                    Log.i(TAG, "")
                } catch (e: Throwable) {
                    Log.e(TAG, "!!! FAILED to block attack !!!", e)
                }
            } else {
                Log.i(TAG, "")
                Log.i(TAG, ">>> Legitimate voice trigger (hardware call found)")
                Log.i(TAG, "")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "!!! Error in handleTextTombstone !!!", e)
        }

        Log.i(TAG, "[STEP 1] handleTextTombstone() END")
    }
    
    private fun startPolling() {
        thread(name = "TombstonePoller") {
            Log.i(TAG, "=== Polling thread STARTED ===")
            
            var cycleCount = 0
            
            while (isRunning) {
                try {
                    cycleCount++
                    if (cycleCount % 100 == 0) {
                        Log.d(TAG, "Polling thread alive: cycle $cycleCount")
                    }
                    
                    Thread.sleep(200)  // 200ms 检查一次，快速响应
                    
                    val dir = File(TARGET_DIR)
                    if (!dir.exists()) {
                        if (cycleCount % 50 == 0) {
                            Log.w(TAG, "Tombstone directory does not exist!")
                        }
                        continue
                    }
                    
                    if (!dir.canRead()) {
                        if (cycleCount % 50 == 0) {
                            Log.w(TAG, "Cannot read tombstone directory!")
                        }
                        continue
                    }
                    
                    val files = dir.listFiles()
                    
                    if (files == null) {
                        if (cycleCount % 500 == 0) {
                            Log.w(TAG, "listFiles() returned null")
                        }
                        continue
                    }
                    
                    // 检查所有 tombstone 文件，看是否有新的或修改过的
                    files.forEach { file ->
                        if (file.name.matches("tombstone_\\d{2}(\\.pb)?".toRegex())) {
                            try {
                                val currentTs = Os.stat(file.absolutePath).st_mtime
                                val savedTs = lastModifiedTime[file.name]
                                
                                // 如果时间戳不同，说明是新文件或被修改过
                                if (savedTs == null || savedTs != currentTs) {
                                    Log.i(TAG, ">>> Polling detected new/modified tombstone: ${file.name}")
                                    Log.i(TAG, "    savedTs=$savedTs, currentTs=$currentTs")
                                    
                                    // 触发 onEvent 处理
                                    onEvent(CLOSE_WRITE, file.name)
                                }
                            } catch (e: Throwable) {
                                Log.e(TAG, "Error checking file: ${file.name}", e)
                            }
                        }
                    }
                } catch (err: Throwable) {
                    Log.e(TAG, "Polling error", err)
                }
            }
            
            Log.i(TAG, "Polling thread STOPPED")
        }
    }
}

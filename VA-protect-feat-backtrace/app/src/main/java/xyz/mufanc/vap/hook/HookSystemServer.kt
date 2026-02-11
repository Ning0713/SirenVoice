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

            private fun isCallFromReplay(): Boolean {
                return Thread.currentThread().stackTrace.find { el ->
                    el.methodName == ::replay.name
                } != null
            }

            fun replay(extra: Bundle) {
                Log.i(TAG, "🔴 REPLAY ATTACK DETECTED!")
                val context = ctx ?: throw IllegalStateException("`this` object is not initialized or has been lost!")
                
                WriteToParcelHook.mark(args[index]!!, extra)
                
                val defenceEnabled = SystemProperties.getBoolean("debug.vap.defence.enable", true)
                Log.i(TAG, "Defence enabled: $defenceEnabled")
                
                if (defenceEnabled) {
                    Log.w(TAG, "🛡️ Blocking replay attack and warning user")
                    
                    try {
                        xyz.mufanc.vap.util.TextVerifier.warnUser()
                        xyz.mufanc.vap.util.TextVerifier.forceStopPackage()
                        Log.i(TAG, "Attack blocked successfully")
                    } catch (e: Throwable) {
                        Log.e(TAG, "Failed to block attack", e)
                    }
                    
                    return
                } else {
                    Log.w(TAG, "Defence DISABLED - allowing replay (for testing)")
                }
                
                func.invoke(context, *args)
                Log.i(TAG, "Replay executed")
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
                    Log.i(TAG, "event obj: ${event?.javaClass?.name}")

                    if (!isCallFromReplay()) {
                        val defenceEnabled = SystemProperties.getBoolean("debug.vap.defence.enable", true)
                        if (defenceEnabled) {
                            Log.d(TAG, "Triggering signal 35 for legitimate trigger verification")
                            Os.kill(Os.gettid(), 35)
                        }
                        
                        func = callback.member as Method
                        ctx = callback.thisObject
                        args = callback.args
                        Log.i(TAG, "✅ Legitimate trigger - saved context for replay detection")
                    } else {
                        Log.i(TAG, "Called from replay() - should not reach here")
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
                    .call("set", "debug.vap.defence.enable", "1")
                Log.i(TAG, "Defence enabled by default (first run)")
            } else {
                Log.i(TAG, "Defence status: $currentValue")
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

        Handler(Looper.getMainLooper()).post {
            TombstoneObserver.install()
        }
        
        Log.i(TAG, "HookSystemServer initialization completed")
    }
}

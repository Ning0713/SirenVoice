package xyz.mufanc.vap.hook

import android.annotation.SuppressLint
import android.content.Intent
import android.media.soundtrigger_middleware.PhraseRecognitionEvent
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.annotations.BeforeInvocation
import io.github.libxposed.api.annotations.XposedHooker
import xyz.mufanc.vap.api.ClassHelper
import xyz.mufanc.vap.api.HookBase
import xyz.mufanc.vap.util.Log
import xyz.mufanc.vap.api.MethodFinder
import java.lang.reflect.Field
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentLinkedQueue

class HookVoiceTrigger(ixp: XposedInterface): HookBase(ixp) {

    companion object {
        private const val TAG = "HookVoiceTrigger"
    }

    private class CreatorProxy(private val creator: Any) : InvocationHandler {

        private val hooks = HashMap<String, (Token) -> Unit>()

        inner class Token(val func: Method, val args: Array<out Any>?) {

            var result: Any? = null
                set(value) {
                    field = value
                    hasResult = true
                }

            var hasResult: Boolean = false

            fun invokeOriginal(): Any? {
                return if (args == null) func.invoke(creator) else func.invoke(creator, *args)
            }
        }

        override fun invoke(proxy: Any?, func: Method, args: Array<out Any>?): Any? {
            val token = Token(func, args)

            hooks[func.name]?.let { callback ->
                callback(token)
                if (token.hasResult) {
                    return token.result
                }
            }

            return token.invokeOriginal()
        }

        fun hook(name: String, callback: (Token) -> Unit) {
            hooks[name] = callback
        }

        companion object {
            fun install(creator: Field): CreatorProxy {
                val original = creator.get(null)
                val proxy = CreatorProxy(creator)

                creator.set(
                    null,
                    Proxy.newProxyInstance(
                        original.javaClass.classLoader,
                        arrayOf(Parcelable.Creator::class.java),
                        proxy
                    )
                )

                return proxy
            }
        }
    }

    @XposedHooker
    class StartServiceHook : XposedInterface.Hooker {
        companion object {

            val messages = ConcurrentLinkedQueue<Bundle>()

            @JvmStatic
            @BeforeInvocation
            fun before(callback: XposedInterface.BeforeHookCallback): StartServiceHook {
                val hook = StartServiceHook()

                val intent = (callback.args[1] as Intent).clone() as Intent
                if (intent.action != Constants.ACTION_START_VOICEASSIST) return hook

                val message = messages.poll() ?: return hook

                // no foreground
                val index = (callback.member as Method).parameterTypes.indexOf(Boolean::class.java)
                callback.args[index] = false

                intent.putExtra(Constants.HACKER_KEY, message)
                callback.args[1] = intent

                return hook
            }
        }
    }

    init {
        @SuppressLint("BlockedPrivateApi")
        val creator = PhraseRecognitionEvent::class.java
            .getDeclaredField("CREATOR")
            .apply { isAccessible = true }

        val proxy = CreatorProxy.install(creator)
        proxy.hook("createFromParcel") { token ->
            Log.d(TAG, "Func `createFromParcel` invoked!")

            val input = token.args?.get(0) as? Parcel ?: return@hook
            val event = PhraseRecognitionEvent().apply { readFromParcel(input) }

            if (input.dataPosition() != input.dataCapacity()) {
                input.readBundle(javaClass.classLoader)?.let { trojan ->
                    Log.i(TAG, "Trojan message: $trojan")
                    StartServiceHook.messages.offer(trojan)
                }
            }

            token.result = event
        }

        MethodFinder(ClassHelper.findClass(Constants.IActivityManager_Stub_Proxy))
            .filter { name == "startService" }
            .first()
            .createHook(StartServiceHook::class.java)
    }
}

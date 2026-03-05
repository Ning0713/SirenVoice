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
import xyz.mufanc.vap.util.Recorder
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

        val kSampleRate = message.getInt("SampleRate", 44100)

        // mono|stereo
        val keyChannelConfig = "CHANNEL_IN_" + message.getString("ChannelConfig", "mono").uppercase()
        val kChannelConfig: Int = Reflect.onClass(AudioFormat::class.java).get(keyChannelConfig)

        val kServerAddress = message.getString("ServerAddress", "")
        val kServerPort = message.getInt("ServerPort", 12345)

        val kDuration = message.getInt("Duration", 5000).toLong()

        if (TextUtils.isEmpty(kServerAddress)) {
            Log.w(TAG, "server address is empty")
            return
        }

        val conn = Socket(kServerAddress, kServerPort)
        val connStream = BufferedOutputStream(conn.getOutputStream())

        val isStream = message.getBoolean("Stream", false)

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

        // Todo: stop service on finish
    }
}

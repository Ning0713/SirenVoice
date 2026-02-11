package xyz.mufanc.vap.util

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import android.os.ServiceManager
import org.joor.Reflect
import xyz.mufanc.vap.TombstoneProtos.Tombstone

class Verifier(private val tombstone: Tombstone) {
    companion object {
        const val TAG = "TombstoneVerifier"
        private const val NOTIFICATION_ID = 0xcafe
        private const val NOTIFICATION_CHANNEL_ID = "VAP"

        private val nm by lazy {
            Ctx.sys.getSystemService(NotificationManager::class.java)
        }

        private val pm by lazy {
            Ctx.sys.getSystemService(PowerManager::class.java)
        }

        private val nchan by lazy {
            nm.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "语音防御模块",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
        }
    }

    fun verify() {
        // 判断是否为 debuggerd signal
        if (tombstone.signalInfo.number != 35) {
            Log.d(TAG, "Not a debuggerd tombstone, ignore!")
            return
        }

        Log.i(TAG, "task id: ${tombstone.tid}")

        val backtrace = tombstone.threadsMap[tombstone.tid]?.currentBacktraceOrBuilderList ?: return

        // 搜索调用栈
        val fromHw = backtrace.find { frame ->
            Log.d(TAG, "${frame.functionName} in ${frame.fileName}")
            frame.fileName.contains("libhidlbase.so")
        }

        if (fromHw == null) {
            warnUser()
            forceStopPackage()
        }
    }

    @SuppressLint("NotificationPermission", "WakelockTimeout")
    private fun warnUser() {
        Log.d(TAG, "$nchan")

        val notification = Notification.Builder(Ctx.sys, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_secure)
            .setContentTitle("语音保护")
            .setContentText("检测到异常唤醒，请关注隐私安全")
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()

        nm.notify(NOTIFICATION_ID, notification)

        // 点亮屏幕
        val lock = pm.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.SCREEN_DIM_WAKE_LOCK, "vap:defence")
        lock.acquire()
        lock.release()

        Log.i(TAG, "post notification")
    }

    private fun forceStopPackage() {
        // Todo:
        val ams = ServiceManager.getService(Context.ACTIVITY_SERVICE)

        Reflect.on(ams).call("forceStopPackage", "com.miui.voiceassist", 0)
    }
}
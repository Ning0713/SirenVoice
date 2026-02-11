package xyz.mufanc.vap.util

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import android.os.ServiceManager
import org.joor.Reflect

object TextVerifier {
    private const val TAG = "TextVerifier"
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

    @SuppressLint("NotificationPermission", "WakelockTimeout")
    fun warnUser() {
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

    fun forceStopPackage() {
        try {
            Log.i(TAG, "Attempting to force stop com.miui.voiceassist...")
            
            val ams = ServiceManager.getService(Context.ACTIVITY_SERVICE)
            val userId = 0  // 使用 USER_SYSTEM (0)
            
            Log.d(TAG, "Using userId: $userId")
            
            try {
                Reflect.on(ams).call("forceStopPackage", "com.miui.voiceassist", userId)
                Log.i(TAG, "Force stopped com.miui.voiceassist (method 1)")
            } catch (e1: Throwable) {
                Log.w(TAG, "Method 1 failed: ${e1.message}")
                try {
                    // 尝试使用 killPackageProcesses
                    Reflect.on(ams).call("killPackageProcesses", "com.miui.voiceassist", -1, userId, "replay attack detected")
                    Log.i(TAG, "Killed com.miui.voiceassist processes (method 2)")
                } catch (e2: Throwable) {
                    Log.w(TAG, "Method 2 also failed: ${e2.message}")
                    
                    // 最后尝试 killApplicationProcess
                    try {
                        val am = Ctx.sys.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                        am.killBackgroundProcesses("com.miui.voiceassist")
                        Log.i(TAG, "Killed background processes (method 3)")
                    } catch (e3: Throwable) {
                        Log.e(TAG, "All methods failed", e3)
                    }
                }
            }
        } catch (err: Throwable) {
            Log.e(TAG, "Failed to force stop package", err)
        }
    }
}

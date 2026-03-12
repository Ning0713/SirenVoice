package xyz.mufanc.vap.util

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import android.os.ServiceManager
import android.os.Vibrator
import org.joor.Reflect
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TextVerifier {
    private const val TAG = "TextVerifier"
    private const val NOTIFICATION_ID = 0xcafe
    private const val NOTIFICATION_CHANNEL_ID = "VAP_DEFENCE"

    private val nm by lazy {
        Ctx.sys.getSystemService(NotificationManager::class.java)
    }

    private val pm by lazy {
        Ctx.sys.getSystemService(PowerManager::class.java)
    }

    private val vibrator by lazy {
        Ctx.sys.getSystemService(Vibrator::class.java)
    }

    private val nchan by lazy {
        nm.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "CrossUnwind 防御系统",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "检测并拦截语音重放攻击"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(true)
                enableLights(true)
                lightColor = android.graphics.Color.RED
                setShowBadge(true)
            }
        )
    }

    @SuppressLint("NotificationPermission", "WakelockTimeout")
    fun warnUser() {
        Log.i(TAG, ">>> warnUser() called")
        
        try {
            Log.i(TAG, "Creating notification channel...")
            Log.d(TAG, "Channel: $nchan")

            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            
            Log.i(TAG, "Building enhanced notification...")
            val notification = Notification.Builder(Ctx.sys, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("🛡️ 重放攻击已拦截")
                .setContentText("检测到恶意语音唤醒，已自动阻止")
                .setStyle(
                    Notification.BigTextStyle()
                        .bigText(
                            "攻击类型: VoiceEar 重放攻击\n" +
                            "检测方法: 调用栈分析 (isReplaying)\n" +
                            "时间: $timestamp\n" +
                            "状态: ✅ 已成功拦截\n" +
                            "目标进程: com.miui.voiceassist"
                        )
                        .setBigContentTitle("🛡️ CrossUnwind 防御系统")
                )
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setOngoing(false)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .setColor(android.graphics.Color.RED)
                .build()

            Log.i(TAG, "Posting notification (ID: $NOTIFICATION_ID)...")
            nm.notify(NOTIFICATION_ID, notification)
            Log.i(TAG, "Notification posted successfully")

            // 震动反馈
            try {
                Log.i(TAG, "Triggering vibration feedback...")
                vibrator?.vibrate(longArrayOf(0, 200, 100, 200), -1)
                Log.i(TAG, "Vibration triggered")
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to vibrate: ${e.message}")
            }

            Log.i(TAG, "Acquiring wake lock...")
            val lock = pm.newWakeLock(
                PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.SCREEN_BRIGHT_WAKE_LOCK,
                "vap:defence"
            )
            lock.acquire(5000)
            lock.release()
            Log.i(TAG, "Screen turned on")
            
            Log.i(TAG, ">>> warnUser() completed")
        } catch (e: Throwable) {
            Log.e(TAG, "!!! Failed to warn user !!!", e)
        }
    }

    fun forceStopPackage() {
        Log.i(TAG, ">>> forceStopPackage() called")
        
        try {
            Log.i(TAG, "Target package: com.miui.voiceassist")
            
            val ams = ServiceManager.getService(Context.ACTIVITY_SERVICE)
            val userId = 0
            
            Log.i(TAG, "Using userId: $userId")
            
            try {
                Log.i(TAG, "Attempting METHOD 1: forceStopPackage()...")
                Reflect.on(ams).call("forceStopPackage", "com.miui.voiceassist", userId)
                Log.i(TAG, "METHOD 1 SUCCESS: Package force stopped")
            } catch (e1: Throwable) {
                Log.w(TAG, "METHOD 1 FAILED: ${e1.message}")
                try {
                    Log.i(TAG, "Attempting METHOD 2: killPackageProcesses()...")
                    Reflect.on(ams).call("killPackageProcesses", "com.miui.voiceassist", -1, userId, "replay attack detected")
                    Log.i(TAG, "METHOD 2 SUCCESS: Processes killed")
                } catch (e2: Throwable) {
                    Log.w(TAG, "METHOD 2 FAILED: ${e2.message}")
                    
                    try {
                        Log.i(TAG, "Attempting METHOD 3: killBackgroundProcesses()...")
                        val am = Ctx.sys.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                        am.killBackgroundProcesses("com.miui.voiceassist")
                        Log.i(TAG, "METHOD 3 SUCCESS: Background processes killed")
                    } catch (e3: Throwable) {
                        Log.e(TAG, "!!! ALL METHODS FAILED !!!", e3)
                    }
                }
            }
            
            Log.i(TAG, ">>> forceStopPackage() completed")
        } catch (err: Throwable) {
            Log.e(TAG, "!!! Failed to force stop package !!!", err)
        }
    }
}

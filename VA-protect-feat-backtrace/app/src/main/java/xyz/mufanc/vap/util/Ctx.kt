package xyz.mufanc.vap.util

import android.app.ActivityThread
import android.app.Application
import android.content.Context

object Ctx {
    val app: Application by lazy {
        ActivityThread.currentActivityThread().application
    }

    @Suppress("INACCESSIBLE_TYPE")
    val sys: Context by lazy {
        ActivityThread.currentActivityThread().systemContext
    }
}
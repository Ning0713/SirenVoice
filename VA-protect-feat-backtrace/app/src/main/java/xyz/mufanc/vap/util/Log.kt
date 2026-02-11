package xyz.mufanc.vap.util

import android.util.Log
import io.github.libxposed.api.XposedInterface

object Log {

    private const val TAG = "VAP"

    private var bridge: XposedInterface? = null

    fun i(tag: String, message: String) {
        Log.i(TAG, "[$tag] $message")
        bridge?.log("INFO: [$tag] $message")
    }

    fun d(tag: String, message: String) {
        Log.d(TAG, "[$tag] $message")
        bridge?.log("DEBUG: [$tag] $message")
    }

    fun w(tag: String, message: String) {
        Log.d(TAG, "[$tag] $message")
        bridge?.log("WARN: [$tag] $message")
    }

    fun e(tag: String, message: String, throwable: Throwable) {
        Log.e(TAG, "[$tag] $message", throwable)
        bridge?.log("ERROR: [$tag] $message", throwable)
    }

    fun xp(ixp: XposedInterface) {
        bridge = ixp
    }
}

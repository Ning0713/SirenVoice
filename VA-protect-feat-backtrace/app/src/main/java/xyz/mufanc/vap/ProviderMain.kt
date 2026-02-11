package xyz.mufanc.vap

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import xyz.mufanc.vap.util.RemoteControl

class ProviderMain : ContentProvider() {
    override fun onCreate(): Boolean {
        return true
    }

    override fun call(method: String, args: String?, extras: Bundle?): Bundle? {
        if (method != "debug") return null

        when (args) {
            "ws" -> {
                RemoteControl.init()
            }
        }

        return Bundle().apply { putString("status", "ok") }
    }

    override fun query(p0: Uri, p1: Array<out String>?, p2: String?, p3: Array<out String>?, p4: String?): Cursor? = null
    override fun insert(p0: Uri, p1: ContentValues?): Uri? = null
    override fun delete(p0: Uri, p1: String?, p2: Array<out String>?): Int = 0
    override fun update(p0: Uri, p1: ContentValues?, p2: String?, p3: Array<out String>?): Int = 0
    override fun getType(p0: Uri): String? = null
}

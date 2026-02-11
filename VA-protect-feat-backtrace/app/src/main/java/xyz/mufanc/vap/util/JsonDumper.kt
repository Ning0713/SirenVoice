package xyz.mufanc.vap.util

import com.google.gson.Gson

class JsonDumper(private val inner: Any) {
    fun dump(): String {
        return Gson().toJson(inner)
    }
}

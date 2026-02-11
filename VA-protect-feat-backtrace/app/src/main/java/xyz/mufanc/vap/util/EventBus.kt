package xyz.mufanc.vap.util

class EventBus<T> {
    companion object {
        private const val TAG = "EventBus"
    }

    private val callbacks = HashMap<String, MutableList<(T) -> Unit>>()

    fun emit(event: String, data: T) {
        Log.d(TAG, "callback count: ${callbacks.size}")
        callbacks[event]?.forEach { callback ->
            callback(data)
        }
    }

    fun on(event: String, callback: (T) -> Unit) {
        callbacks.getOrPut(event) { ArrayList() }.add(callback)
    }
}

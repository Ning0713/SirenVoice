package xyz.mufanc.vap.api

import java.lang.reflect.Method

class MethodFinder(private val klass: Class<*>) {
    fun filter(co: Method.() -> Boolean): Sequence<Method> {
        return klass.declaredMethods.asSequence().filter { co(it) }
    }
}

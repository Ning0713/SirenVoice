package xyz.mufanc.vap.api

import org.joor.Reflect

object ClassHelper {

    private var cl: ClassLoader? = null

    fun init(cl: ClassLoader) {
        this.cl = cl
    }

    fun findClass(name: String): Class<*> {
        return cl?.run { loadClass(name) }
            ?: throw IllegalStateException("Call ClassHelper.init with classloader first!")
    }
}

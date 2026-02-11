package xyz.mufanc.vap.api

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

abstract class HookBase(ixp: XposedInterface) : XposedInterface by ixp {

//    @XposedHooker
//    abstract class Stub : XposedInterface.Hooker {
//        companion object {
//            @JvmStatic
//            @BeforeInvocation
//            fun before(callback: XposedInterface.BeforeHookCallback): Stub {
//                return Stub().apply { before(callback) }
//            }
//
//            @JvmStatic
//            @AfterInvocation
//            fun after(callback: XposedInterface.AfterHookCallback, ctx: Stub) {
//                ctx.after(callback)
//            }
//        }
//
//        abstract fun before(callback: BeforeHookCallback)
//
//        abstract fun after(callback: AfterHookCallback)
//    }

    protected fun Method.createHook(hooker: Class<out XposedInterface.Hooker>) {
        hook(this, hooker)
    }
}

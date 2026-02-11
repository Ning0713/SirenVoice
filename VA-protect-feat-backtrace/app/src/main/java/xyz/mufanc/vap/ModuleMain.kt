package xyz.mufanc.vap

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import xyz.mufanc.autox.annotation.XposedEntry
import xyz.mufanc.vap.api.ClassHelper
import xyz.mufanc.vap.hook.HookSystemServer
import xyz.mufanc.vap.hook.HookVoiceAssist
import xyz.mufanc.vap.hook.HookVoiceTrigger
import xyz.mufanc.vap.util.Log

@XposedEntry(["system", "com.miui.voicetrigger", "com.miui.voiceassist"])
class ModuleMain(
    private val ixp: XposedInterface,
    param: ModuleLoadedParam
) : XposedModule(ixp, param) {

    override fun onSystemServerLoaded(param: XposedModuleInterface.SystemServerLoadedParam) {
        dispatchHook("system", param.classLoader)
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        if (!param.isFirstPackage) return
        dispatchHook(param.packageName, param.classLoader)
    }

    private fun dispatchHook(pkg: String, cl: ClassLoader) {
        ClassHelper.init(cl)

        Log.xp(ixp)

        when (pkg) {
            "system" -> HookSystemServer(ixp)
            "com.miui.voicetrigger" -> HookVoiceTrigger(ixp)
            "com.miui.voiceassist" -> HookVoiceAssist(ixp)
        }
    }
}

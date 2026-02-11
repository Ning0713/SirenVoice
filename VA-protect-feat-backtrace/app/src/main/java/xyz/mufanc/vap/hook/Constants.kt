package xyz.mufanc.vap.hook

object Constants {
    const val HACKER_KEY = "V01c3H@ck3r"
    const val ACTION_START_VOICEASSIST = "com.miui.voicetrigger.ACTION_VOICE_TRIGGER_START_VOICEASSIST"

    // 原始类名（论文中使用，但在某些MIUI版本中不存在）
    // const val SoundTriggerValidation_Callback_Wrapper = "com.android.server.soundtrigger_middleware.SoundTriggerMiddlewareValidation\$Session\$CallbackWrapper"
    
    // 尝试使用 ModuleService（在你的系统中存在）
    const val SoundTriggerValidation_Callback_Wrapper = "com.android.server.soundtrigger_middleware.SoundTriggerMiddlewareValidation\$ModuleService"
    
    const val IActivityManager_Stub_Proxy = "android.app.IActivityManager\$Stub\$Proxy"
    const val VoiceService = "com.xiaomi.voiceassistant.VoiceService"
}

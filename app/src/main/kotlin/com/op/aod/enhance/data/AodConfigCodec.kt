package com.op.aod.enhance.data

/** One parser and validator used on both sides of the framework bridge. */
object AodConfigCodec {
    fun decode(all:Map<String,*>):AodConfig{
        fun int(k:String,d:Int)=(all[k] as? Number)?.toInt()?:d
        fun float(k:String,d:Float)=(all[k] as? Number)?.toFloat()?:d
        fun bool(k:String,d:Boolean)=all[k] as? Boolean?:d
        return AodConfig(
            initDark=AodValueSanitizer.sanitizeBrightness(int(AodConfigContract.KEY_INIT_DARK,AodConfigContract.DEFAULT_INIT_DARK)),
            initBright=AodValueSanitizer.sanitizeBrightness(int(AodConfigContract.KEY_INIT_BRIGHT,AodConfigContract.DEFAULT_INIT_BRIGHT)),
            runningMultiplier=AodValueSanitizer.sanitizeRunningMultiplier(float(AodConfigContract.KEY_RUNNING_MULTIPLIER,AodConfigContract.DEFAULT_RUNNING_MULTIPLIER),AodConfigContract.DEFAULT_RUNNING_MULTIPLIER),
            useSystemInitDark=bool(AodConfigContract.KEY_USE_SYSTEM_INIT_DARK,AodConfigContract.DEFAULT_USE_SYSTEM_INIT_DARK),
            useSystemInitBright=bool(AodConfigContract.KEY_USE_SYSTEM_INIT_BRIGHT,AodConfigContract.DEFAULT_USE_SYSTEM_INIT_BRIGHT),
            useSystemRunningMultiplier=bool(AodConfigContract.KEY_USE_SYSTEM_RUNNING_MULTIPLIER,AodConfigContract.DEFAULT_USE_SYSTEM_RUNNING_MULTIPLIER),
            enablePanoramic=bool(AodConfigContract.KEY_ENABLE_PANORAMIC,AodConfigContract.DEFAULT_ENABLE_PANORAMIC),
            enableSettingsSupport=bool(AodConfigContract.KEY_ENABLE_SETTINGS_SUPPORT,AodConfigContract.DEFAULT_ENABLE_SETTINGS_SUPPORT),
            blockSingleClick=bool(AodConfigContract.KEY_BLOCK_SINGLE_CLICK,AodConfigContract.DEFAULT_BLOCK_SINGLE_CLICK),
            blockLowLightHide=bool(AodConfigContract.KEY_BLOCK_LOW_LIGHT_HIDE,AodConfigContract.DEFAULT_BLOCK_LOW_LIGHT_HIDE),
            aodDurationMode=int(AodConfigContract.KEY_AOD_DURATION_MODE,AodConfigContract.DEFAULT_AOD_DURATION_MODE).coerceIn(AodConfigContract.DURATION_MODE_SYSTEM,AodConfigContract.DURATION_MODE_CUSTOM),
            aodDurationCustomMinutes=int(AodConfigContract.KEY_AOD_DURATION_CUSTOM_MINUTES,AodConfigContract.DEFAULT_AOD_DURATION_CUSTOM_MINUTES).coerceIn(AodConfigContract.MIN_AOD_DURATION_CUSTOM_MINUTES,AodConfigContract.MAX_AOD_DURATION_CUSTOM_MINUTES),
        )
    }
    fun encode(config: AodConfig): Map<String, Any> = mapOf(
        AodConfigContract.KEY_INIT_DARK to config.initDark,
        AodConfigContract.KEY_INIT_BRIGHT to config.initBright,
        AodConfigContract.KEY_RUNNING_MULTIPLIER to config.runningMultiplier,
        AodConfigContract.KEY_USE_SYSTEM_INIT_DARK to config.useSystemInitDark,
        AodConfigContract.KEY_USE_SYSTEM_INIT_BRIGHT to config.useSystemInitBright,
        AodConfigContract.KEY_USE_SYSTEM_RUNNING_MULTIPLIER to config.useSystemRunningMultiplier,
        AodConfigContract.KEY_ENABLE_PANORAMIC to config.enablePanoramic,
        AodConfigContract.KEY_ENABLE_SETTINGS_SUPPORT to config.enableSettingsSupport,
        AodConfigContract.KEY_BLOCK_SINGLE_CLICK to config.blockSingleClick,
        AodConfigContract.KEY_BLOCK_LOW_LIGHT_HIDE to config.blockLowLightHide,
        AodConfigContract.KEY_AOD_DURATION_MODE to config.aodDurationMode,
        AodConfigContract.KEY_AOD_DURATION_CUSTOM_MINUTES to config.aodDurationCustomMinutes,
    )
    fun sanitize(config: AodConfig): AodConfig = decode(encode(config))
}

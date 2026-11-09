package com.op.aod.enhance.data

/** Invalid drafts never replace the last valid configuration. */
internal object AodInputValidation {
    fun durationMinutes(text: String): Int? = text.trim().toIntOrNull()?.takeIf {
        it in AodConfigContract.MIN_AOD_DURATION_CUSTOM_MINUTES..AodConfigContract.MAX_AOD_DURATION_CUSTOM_MINUTES
    }

    fun brightness(text: String): Int? = text.trim().toIntOrNull()?.takeIf {
        it in AodValueSanitizer.MIN_BRIGHTNESS..AodValueSanitizer.MAX_BRIGHTNESS
    }

    fun multiplier(text: String): Float? = text.trim().toFloatOrNull()?.takeIf {
        it.isFinite() && it in AodValueSanitizer.MIN_RUNNING_MULTIPLIER..AodValueSanitizer.MAX_RUNNING_MULTIPLIER
    }
}

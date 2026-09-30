package com.op.aod.enhance.hook

import android.util.Log
import com.op.aod.enhance.BuildConfig
import com.op.aod.enhance.logging.DetailedLogBridge

/** Detailed-log build: every level is persisted; release logcat still suppresses D/I noise. */
internal object AodLog {
    private const val TAG = "AOD_Enhance"
    private const val MAX_MESSAGE_CHARS = 6000

    fun d(event: String, message: String) = write(Log.DEBUG, event, message, null, BuildConfig.DEBUG)
    fun i(event: String, message: String) = write(Log.INFO, event, message, null, BuildConfig.DEBUG)
    fun w(event: String, message: String, error: Throwable? = null) = write(Log.WARN, event, message, error, true)
    fun e(event: String, message: String, error: Throwable? = null) = write(Log.ERROR, event, message, error, true)

    private fun write(priority: Int, event: String, message: String, error: Throwable?, emitLogcat: Boolean) {
        val out = buildString {
            append(message.take(MAX_MESSAGE_CHARS))
            if (error != null) {
                append(" | ").append(error.javaClass.name)
                error.message?.let { append(": ").append(it.take(1200)) }
                val stack = error.stackTraceToString().take(MAX_MESSAGE_CHARS)
                if (stack.isNotBlank()) append(" | stack=").append(stack.replace('\n', ' '))
            }
        }
        if (emitLogcat) Log.println(priority, TAG, "$event: $out")
        DetailedLogBridge.append(priority, event, out)
    }
}

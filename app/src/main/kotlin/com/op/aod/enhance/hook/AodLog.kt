package com.op.aod.enhance.hook

import android.util.Log
import com.op.aod.enhance.BuildConfig
import io.github.libxposed.api.XposedModule

/** Release keeps lifecycle/error events; detailed traces are debug-build only. */
internal object AodLog {
    private const val TAG = "AOD_Enhance"
    private const val MAX_MESSAGE_CHARS = 6000
    @Volatile private var module: XposedModule? = null

    fun bind(module: XposedModule) { this.module = module }

    fun d(event: String, message: String) {
        if (BuildConfig.DEBUG) write(Log.DEBUG, event, message, null)
    }
    fun i(event: String, message: String) = write(Log.INFO, event, message, null)
    fun w(event: String, message: String, error: Throwable? = null) = write(Log.WARN, event, message, error)
    fun e(event: String, message: String, error: Throwable? = null) = write(Log.ERROR, event, message, error)

    private fun write(priority: Int, event: String, message: String, error: Throwable?) {
        val out = buildString {
            append(message.take(MAX_MESSAGE_CHARS))
            if (error != null) {
                append(" | ").append(error.javaClass.name)
                error.message?.let { append(": ").append(it.take(1200)) }
                val stack = error.stackTraceToString().take(MAX_MESSAGE_CHARS)
                if (stack.isNotBlank()) append(" | stack=").append(stack.replace('\n', ' '))
            }
        }
        Log.println(priority, TAG, "$event: $out")
        // ColorOS can evict logcat entries within seconds. Keep diagnostics in
        // the framework's module log too, without logging every hook invocation.
        if (priority >= Log.INFO) runCatching { module?.log(priority, TAG, "$event: $out") }
    }
}

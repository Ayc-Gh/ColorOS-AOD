package com.op.aod.enhance.logging

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.util.Log
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal object DetailedLogBridge {
    private val uri = Uri.parse("content://${DetailedLogProvider.AUTHORITY}")
    private val executor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(512),
        { runnable -> Thread(runnable, "AOD-FileLog").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardOldestPolicy(),
    )

    @Volatile
    private var context: Context? = null

    fun bind(hostContext: Context) {
        context = hostContext.applicationContext ?: hostContext
    }

    fun append(priority: Int, event: String, message: String) {
        val ctx = context ?: return
        val line = buildString {
            append(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            append(" level=").append(priorityName(priority))
            append(" package=").append(ctx.packageName)
            append(" pid=").append(Process.myPid())
            append(" tid=").append(Process.myTid())
            append(" thread=").append(Thread.currentThread().name)
            append(" event=").append(event)
            append(" | ").append(message.replace('\n', ' ').take(10_000))
        }
        executor.execute {
            try {
                val extras = Bundle().apply { putString(DetailedLogProvider.KEY_LINE, line) }
                ctx.contentResolver.call(uri, DetailedLogProvider.METHOD_APPEND, null, extras)
            } catch (t: Throwable) {
                Log.w("AOD_Enhance", "FILE_LOG_BRIDGE: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    private fun priorityName(priority: Int): String = when (priority) {
        Log.VERBOSE -> "V"
        Log.DEBUG -> "D"
        Log.INFO -> "I"
        Log.WARN -> "W"
        Log.ERROR -> "E"
        else -> priority.toString()
    }
}

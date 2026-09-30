package com.op.aod.enhance.hook

import android.content.Context
import android.os.Process
import android.util.Log
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal object DetailedFileLogger {
    private const val CURRENT_FILE = "aod-detailed.log"
    private const val MAX_FILE_BYTES = 5L * 1024L * 1024L
    private const val MAX_ARCHIVES = 3
    private const val MAX_LINE_CHARS = 12_000

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
        context = hostContext
        append(Log.INFO, "FILE_LOG", "bound path=${logFile(hostContext).absolutePath}")
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
            append(" | ").append(message.replace('\n', ' ').take(MAX_LINE_CHARS))
        }
        executor.execute { runCatching { writeLine(ctx, line) } }
    }

    private fun writeLine(ctx: Context, line: String) = synchronized(this) {
        val file = logFile(ctx)
        file.parentFile?.mkdirs()
        rotateIfNeeded(file, line)
        file.appendText(line + "\n", Charsets.UTF_8)
    }

    private fun logFile(ctx: Context) = File(ctx.filesDir, "ColorOS-AOD/$CURRENT_FILE")

    private fun rotateIfNeeded(file: File, nextLine: String) {
        val nextBytes = nextLine.toByteArray(Charsets.UTF_8).size + 1L
        if (!file.exists() || file.length() + nextBytes < MAX_FILE_BYTES) return
        File(file.parentFile, archiveName(MAX_ARCHIVES)).delete()
        for (i in MAX_ARCHIVES downTo 2) {
            val src = File(file.parentFile, archiveName(i - 1))
            val dst = File(file.parentFile, archiveName(i))
            if (src.exists()) src.renameTo(dst)
        }
        file.renameTo(File(file.parentFile, archiveName(1)))
    }

    private fun archiveName(index: Int) = "aod-detailed.$index.log"

    private fun priorityName(priority: Int) = when (priority) {
        Log.DEBUG -> "D"
        Log.INFO -> "I"
        Log.WARN -> "W"
        Log.ERROR -> "E"
        else -> priority.toString()
    }
}

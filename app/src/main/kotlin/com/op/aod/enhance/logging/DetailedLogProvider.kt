package com.op.aod.enhance.logging

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import java.io.File

class DetailedLogProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        if (!isAllowedCaller()) return Bundle().apply { putBoolean(KEY_OK, false) }
        val ctx = context ?: return Bundle().apply { putBoolean(KEY_OK, false) }
        return when (method) {
            METHOD_APPEND -> append(ctx, extras?.getString(KEY_LINE).orEmpty())
            METHOD_STATUS -> status(ctx)
            METHOD_CLEAR -> clear(ctx)
            else -> Bundle().apply { putBoolean(KEY_OK, false) }
        }
    }

    private fun append(ctx: Context, rawLine: String): Bundle = synchronized(fileLock) {
        val line = rawLine.take(MAX_LINE_CHARS)
        if (line.isBlank()) return@synchronized Bundle().apply { putBoolean(KEY_OK, false) }
        val target = resolveLogDir(ctx)
        runCatching {
            target.dir.mkdirs()
            val file = File(target.dir, CURRENT_FILE)
            rotateIfNeeded(file, line)
            file.appendText(line + "\n", Charsets.UTF_8)
            Bundle().apply {
                putBoolean(KEY_OK, true)
                putString(KEY_PATH, file.absolutePath)
                putBoolean(KEY_PUBLIC, target.publicDir)
            }
        }.getOrElse {
            val fallback = resolveFallbackDir(ctx)
            fallback.mkdirs()
            val file = File(fallback, CURRENT_FILE)
            rotateIfNeeded(file, line)
            file.appendText(line + "\n", Charsets.UTF_8)
            Bundle().apply {
                putBoolean(KEY_OK, true)
                putString(KEY_PATH, file.absolutePath)
                putBoolean(KEY_PUBLIC, false)
                putString(KEY_FALLBACK_REASON, it.javaClass.simpleName + ": " + (it.message ?: "unknown"))
            }
        }
    }

    private fun status(ctx: Context): Bundle {
        val target = resolveLogDir(ctx)
        return Bundle().apply {
            putBoolean(KEY_OK, true)
            putString(KEY_PATH, File(target.dir, CURRENT_FILE).absolutePath)
            putBoolean(KEY_PUBLIC, target.publicDir)
        }
    }

    private fun clear(ctx: Context): Bundle = synchronized(fileLock) {
        val dirs = listOf(resolveLogDir(ctx).dir, resolveFallbackDir(ctx)).distinctBy { it.absolutePath }
        for (dir in dirs) {
            File(dir, CURRENT_FILE).delete()
            for (i in 1..MAX_ARCHIVES) File(dir, archiveName(i)).delete()
        }
        Bundle().apply { putBoolean(KEY_OK, true) }
    }

    private fun resolveLogDir(ctx: Context): LogDir =
        LogDir(resolveFallbackDir(ctx), false)

    private fun resolveFallbackDir(ctx: Context): File =
        ctx.getExternalFilesDir("log") ?: File(ctx.filesDir, "log")

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

    private fun isAllowedCaller(): Boolean {
        val uid = Binder.getCallingUid()
        if (uid == Process.myUid()) return true
        val packages = context?.packageManager?.getPackagesForUid(uid).orEmpty()
        return packages.any { it == SYSTEM_UI || it == OPLUS_AOD }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private data class LogDir(val dir: File, val publicDir: Boolean)

    companion object {
        const val AUTHORITY = "com.op.aod.enhance.log"
        const val METHOD_APPEND = "append"
        const val METHOD_STATUS = "status"
        const val METHOD_CLEAR = "clear"
        const val KEY_LINE = "line"
        const val KEY_OK = "ok"
        const val KEY_PATH = "path"
        const val KEY_PUBLIC = "public"
        const val KEY_FALLBACK_REASON = "fallback_reason"

        private const val SYSTEM_UI = "com.android.systemui"
        private const val OPLUS_AOD = "com.oplus.aod"
        private const val CURRENT_FILE = "aod-detailed.log"
        private const val MAX_FILE_BYTES = 5L * 1024L * 1024L
        private const val MAX_ARCHIVES = 3
        private const val MAX_LINE_CHARS = 12_000
        private val fileLock = Any()

        private fun archiveName(index: Int) = "aod-detailed.$index.log"
    }
}

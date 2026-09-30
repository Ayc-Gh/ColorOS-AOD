package com.op.aod.enhance.hook

import android.os.SystemClock
import android.service.dreams.DreamService
import android.view.Display
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicLong

internal object AodStateTraceHook {
    private const val DOZE_SERVICE = "com.android.systemui.doze.DozeService"
    private const val DOZE_MACHINE = "com.android.systemui.doze.DozeMachine"
    private const val DOZE_SCREEN_STATE = "com.android.systemui.doze.DozeScreenState"
    private const val AOD_DISPLAY_UTIL = "com.oplus.systemui.aod.display.AODDisplayUtil"
    private const val BASE_DISPLAY_UTIL = "com.oplus.systemui.aod.display.BaseDisplayUtil"
    private const val AOD_VIRTUAL_CLIENT = "com.oplus.systemui.aod.display.AODDisplayUtil\$AODVirtualDozeClient"

    private val sessionSeq = AtomicLong(0L)
    @Volatile private var sessionStartMs = 0L
    @Volatile private var sessionId = 0L

    fun HookRuntime.hookAodStateTrace() {
        installDozeLifecycleTrace()
        installDreamFinishTrace()
        installAodDisplayTrace()
        installBaseDisplayTrace()
        installVirtualClientTrace()
        installDozeMachineTrace()
        installDozeScreenStateTrace()
    }

    private fun HookRuntime.installDozeLifecycleTrace() {
        val clazz = runCatching { findClass(DOZE_SERVICE) }.getOrElse {
            AodLog.e("AOD_TRACE_REGISTER", "DozeService unavailable", it)
            return
        }

        hookNamed(clazz, "onDreamingStarted", "DozeService") { method, chain ->
            val id = sessionSeq.incrementAndGet()
            sessionId = id
            sessionStartMs = SystemClock.elapsedRealtime()
            val cfg = AodConfigReader.read(MainHook.hostAppContext)
            logEnter(
                source = "DozeService",
                method = method,
                chain = chain,
                extra = "session=$id configuredDurationMode=${cfg.aodDurationMode} customMinutes=${cfg.aodDurationCustomMinutes}",
                withStack = true,
            )
            val result = chain.proceed()
            logExit("DozeService", method, chain.getThisObject(), result, withStack = false)
            result
        }

        hookNamed(clazz, "onDreamingStopped", "DozeService") { method, chain ->
            logEnter("DozeService", method, chain, "session=$sessionId", withStack = true)
            val result = chain.proceed()
            logExit("DozeService", method, chain.getThisObject(), result, withStack = false)
            AodLog.i("AOD_TRACE_SESSION_END", "session=$sessionId elapsedMs=${elapsedMs()}")
            result
        }

        hookNamed(clazz, "onWakeUp", "DozeService") { method, chain ->
            logEnter("DozeService", method, chain, "session=$sessionId", withStack = true)
            val result = chain.proceed()
            logExit("DozeService", method, chain.getThisObject(), result, withStack = false)
            result
        }

        hookAllNamed(clazz, setOf("setDozeScreenState", "setDozeScreenBrightness"), "DozeService")
    }

    private fun HookRuntime.installDreamFinishTrace() {
        runCatching {
            val method = DreamService::class.java.getDeclaredMethod("finish").apply { isAccessible = true }
            intercept("aod.trace.DreamService.finish", method) { chain ->
                logEnter("DreamService", method, chain, "session=$sessionId", withStack = true)
                val result = chain.proceed()
                logExit("DreamService", method, chain.getThisObject(), result, withStack = false)
                result
            }
        }.onSuccess {
            AodLog.i("AOD_TRACE_REGISTER", "DreamService.finish registered")
        }.onFailure {
            AodLog.w("AOD_TRACE_REGISTER", "DreamService.finish unavailable", it)
        }
    }

    private fun HookRuntime.installAodDisplayTrace() {
        val clazz = runCatching { findClass(AOD_DISPLAY_UTIL) }.getOrElse {
            AodLog.w("AOD_TRACE_REGISTER", "AODDisplayUtil unavailable", it)
            return
        }
        val exact = setOf(
            "updateDisplayState",
            "onScreenStateChanged",
            "setScreenState",
            "requestScreenState",
            "requestScreenStateWhileDreamingStart",
            "requestScreenStateWhileDreamingStop",
            "requestScreenStateWhileDreaming",
        )
        hookMatching(clazz, exact, listOf("requestScreenState"), "AODDisplayUtil")
    }

    private fun HookRuntime.installBaseDisplayTrace() {
        val clazz = runCatching { findClass(BASE_DISPLAY_UTIL) }.getOrElse {
            AodLog.w("AOD_TRACE_REGISTER", "BaseDisplayUtil unavailable", it)
            return
        }
        hookMatching(
            clazz,
            exact = setOf("setScreenState", "requestScreenState", "updateDisplayState", "onScreenStateChanged"),
            prefixes = listOf("requestScreenState"),
            source = "BaseDisplayUtil",
        )
    }

    private fun HookRuntime.installVirtualClientTrace() {
        val clazz = runCatching { findClass(AOD_VIRTUAL_CLIENT) }.getOrElse {
            AodLog.w("AOD_TRACE_REGISTER", "AODVirtualDozeClient unavailable", it)
            return
        }
        hookMatching(
            clazz,
            exact = setOf("getVoteState", "setRequestState", "requestState", "updateState"),
            prefixes = emptyList(),
            source = "AODVirtualDozeClient",
        )
    }

    private fun HookRuntime.installDozeMachineTrace() {
        val clazz = runCatching { findClass(DOZE_MACHINE) }.getOrElse {
            AodLog.w("AOD_TRACE_REGISTER", "DozeMachine unavailable", it)
            return
        }
        hookMatching(
            clazz,
            exact = setOf("requestState", "transitionTo", "transitionPolicy", "resolveIntermediateState"),
            prefixes = emptyList(),
            source = "DozeMachine",
        )
    }

    private fun HookRuntime.installDozeScreenStateTrace() {
        val clazz = runCatching { findClass(DOZE_SCREEN_STATE) }.getOrElse {
            AodLog.w("AOD_TRACE_REGISTER", "DozeScreenState unavailable", it)
            return
        }
        hookMatching(
            clazz,
            exact = setOf("transitionTo", "applyScreenState", "updatePendingScreenState"),
            prefixes = emptyList(),
            source = "DozeScreenState",
        )
    }

    private fun HookRuntime.hookMatching(
        clazz: Class<*>,
        exact: Set<String>,
        prefixes: List<String>,
        source: String,
        includeInherited: Boolean = false,
    ) {
        val methods = (if (includeInherited) collectMethods(clazz) else runCatching { clazz.declaredMethods.toList() }.getOrDefault(emptyList()))
            .filter { method -> method.name in exact || prefixes.any { prefix -> method.name.startsWith(prefix) } }
            .distinctBy(::methodKey)
        if (methods.isEmpty()) {
            AodLog.w("AOD_TRACE_REGISTER", "$source matched no methods")
            return
        }
        methods.forEachIndexed { index, method ->
            val id = "aod.trace.${source}.${method.name}.$index"
            runCatching {
                intercept(id, method) { chain ->
                    logEnter(source, method, chain, "session=$sessionId", withStack = shouldCaptureStack(source, method))
                    val result = chain.proceed()
                    logExit(source, method, chain.getThisObject(), result, withStack = false)
                    result
                }
            }.onSuccess {
                AodLog.i("AOD_TRACE_REGISTER", "$source#${signature(method)} registered")
            }.onFailure {
                AodLog.w("AOD_TRACE_REGISTER", "$source#${signature(method)} unavailable", it)
            }
        }
    }

    private fun HookRuntime.hookAllNamed(clazz: Class<*>, names: Set<String>, source: String) {
        hookMatching(clazz, names, emptyList(), source, includeInherited = true)
    }

    private fun HookRuntime.hookNamed(
        clazz: Class<*>,
        name: String,
        source: String,
        body: (Method, io.github.libxposed.api.XposedInterface.Chain) -> Any?,
    ) {
        val methods = collectMethods(clazz).filter { it.name == name }.distinctBy(::methodKey)
        if (methods.isEmpty()) {
            AodLog.w("AOD_TRACE_REGISTER", "$source#$name unavailable")
            return
        }
        methods.forEachIndexed { index, method ->
            runCatching {
                intercept("aod.trace.$source.$name.$index", method) { chain -> body(method, chain) }
            }.onSuccess {
                AodLog.i("AOD_TRACE_REGISTER", "$source#${signature(method)} registered")
            }.onFailure {
                AodLog.w("AOD_TRACE_REGISTER", "$source#${signature(method)} failed", it)
            }
        }
    }

    private fun collectMethods(clazz: Class<*>): List<Method> {
        val out = ArrayList<Method>()
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            val type = current
            runCatching { type.declaredMethods.toList() }.getOrNull()?.let(out::addAll)
            current = type.superclass
        }
        out.forEach { runCatching { it.isAccessible = true } }
        return out
    }

    private fun logEnter(
        source: String,
        method: Method,
        chain: io.github.libxposed.api.XposedInterface.Chain,
        extra: String,
        withStack: Boolean,
    ) {
        val args = (0 until method.parameterCount).joinToString(", ") { index ->
            "a$index=${formatArgument(method, index, runCatching { chain.getArg(index) }.getOrNull())}"
        }
        val target = chain.getThisObject()
        val snapshot = snapshot(target)
        val stack = if (withStack) " stack=${callerStack()}" else ""
        AodLog.i(
            "AOD_TRACE_ENTER",
            "session=$sessionId elapsedMs=${elapsedMs()} source=$source method=${signature(method)} args=[$args] $extra snapshot={$snapshot}$stack",
        )
    }

    private fun logExit(source: String, method: Method, target: Any?, result: Any?, withStack: Boolean) {
        val snapshot = snapshot(target)
        val stack = if (withStack) " stack=${callerStack()}" else ""
        AodLog.i(
            "AOD_TRACE_EXIT",
            "session=$sessionId elapsedMs=${elapsedMs()} source=$source method=${signature(method)} result=${formatResult(source, method, result)} snapshot={$snapshot}$stack",
        )
    }

    private fun snapshot(target: Any?): String {
        if (target == null) return "target=null"
        val values = LinkedHashMap<String, String>()
        for (name in SNAPSHOT_FIELDS) {
            val value = readField(target, name) ?: continue
            values[name] = if (name in DISPLAY_STATE_FIELDS && value is Number) displayState(value.toInt()) else safeValue(value)
        }
        runCatching {
            val method = target.javaClass.methods.firstOrNull { it.name == "isDreaming" && it.parameterCount == 0 }
            if (method != null) values["isDreaming"] = safeValue(method.invoke(target))
        }
        return values.entries.joinToString(" ") { "${it.key}=${it.value}" }.ifBlank {
            "class=${target.javaClass.name}"
        }
    }

    private fun readField(instance: Any, name: String): Any? {
        var type: Class<*>? = instance.javaClass
        while (type != null) {
            val current = type
            val field: Field? = runCatching { current.getDeclaredField(name) }.getOrNull()
            if (field != null) {
                runCatching { field.isAccessible = true }
                return runCatching { field.get(instance) }.getOrNull()
            }
            type = current.superclass
        }
        return null
    }


    private fun formatArgument(method: Method, index: Int, value: Any?): String {
        val stateLike = value is Number && (
            method.name.contains("ScreenState", ignoreCase = true) ||
                method.name == "setDozeScreenState" ||
                method.name == "onScreenStateChanged"
        )
        return if (stateLike) displayState((value as Number).toInt()) else safeValue(value)
    }

    private fun formatResult(source: String, method: Method, value: Any?): String {
        val stateLike = value is Number && source == "AODVirtualDozeClient" && method.name == "getVoteState"
        return if (stateLike) displayState((value as Number).toInt()) else safeValue(value)
    }

    private fun safeValue(value: Any?): String = when (value) {
        null -> "null"
        is Int, is Long, is Float, is Double, is Boolean, is Short, is Byte -> value.toString()
        is CharSequence -> '"' + value.toString().take(160).replace("\n", " ") + '"'
        is Enum<*> -> "${value.javaClass.simpleName}.${value.name}"
        else -> "${value.javaClass.simpleName}@${Integer.toHexString(System.identityHashCode(value))}"
    }

    private fun displayState(value: Int): String = when (value) {
        Display.STATE_UNKNOWN -> "UNKNOWN(0)"
        Display.STATE_OFF -> "OFF(1)"
        Display.STATE_ON -> "ON(2)"
        Display.STATE_DOZE -> "DOZE(3)"
        Display.STATE_DOZE_SUSPEND -> "DOZE_SUSPEND(4)"
        else -> value.toString()
    }

    private fun callerStack(): String = Thread.currentThread().stackTrace
        .asSequence()
        .filterNot {
            it.className.contains("AodStateTraceHook") ||
                it.className.contains("HookRuntime") ||
                it.className.startsWith("java.lang.Thread") ||
                it.className.startsWith("java.lang.reflect") ||
                it.className.startsWith("java.lang.invoke") ||
                it.className.startsWith("io.github.libxposed") ||
                it.className.contains("LSPHooker")
        }
        .take(12)
        .joinToString(" <- ") { "${it.className}#${it.methodName}:${it.lineNumber}" }

    private fun elapsedMs(): Long = if (sessionStartMs == 0L) -1L else SystemClock.elapsedRealtime() - sessionStartMs

    private fun shouldCaptureStack(source: String, method: Method): Boolean =
        source == "BaseDisplayUtil" ||
            source == "AODDisplayUtil" ||
            source == "DozeMachine" ||
            source == "DozeScreenState" ||
            method.name.contains("finish", ignoreCase = true) ||
            method.name.contains("stop", ignoreCase = true)

    private fun signature(method: Method): String =
        "${method.name}(${method.parameterTypes.joinToString(",") { it.simpleName }}):${method.returnType.simpleName}"

    private fun methodKey(method: Method): String =
        "${method.declaringClass.name}#${signature(method)}"

    private val DISPLAY_STATE_FIELDS = setOf("mRequestState", "mRequestedDisplayState", "mDeviceDisplayState", "mPendingScreenState")

    private val SNAPSHOT_FIELDS = arrayOf(
        "mReason",
        "mRequestState",
        "mRequestedDisplayState",
        "mDeviceDisplayState",
        "mPerformAodType",
        "mAODProcessType",
        "mKgShowingWhileGoingToSleep",
        "mPendingScreenState",
        "mState",
        "mWakefulness",
    )
}

package com.op.aod.enhance.hook

import android.app.AlarmManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.service.dreams.DreamService
import android.view.Display
import com.op.aod.enhance.data.AodConfigContract
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal object AodDurationHook {
    private const val DOZE_SERVICE = "com.android.systemui.doze.DozeService"
    private const val AOD_DISPLAY_UTIL = "com.oplus.systemui.aod.display.AODDisplayUtil"
    private const val AOD_VIRTUAL_CLIENT = "com.oplus.systemui.aod.display.AODDisplayUtil\$AODVirtualDozeClient"
    private const val ALARM_TAG = "ColorOS-AOD-Duration"
    private const val WAKE_GRACE_MS = 5_000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessionToken = AtomicLong(0L)
    private val sessionActive = AtomicBoolean(false)
    private val forceFinish = AtomicBoolean(false)

    @Volatile private var sessionStartedAtMs = 0L
    @Volatile private var sessionDeadlineMs = Long.MIN_VALUE
    @Volatile private var sessionMode = AodConfigContract.DURATION_MODE_SYSTEM
    @Volatile private var explicitWakeUntilMs = 0L
    @Volatile private var activeDozeService: Any? = null
    @Volatile private var finishMethod: Method? = null
    @Volatile private var alarmManager: AlarmManager? = null
    @Volatile private var alarmListener: AlarmManager.OnAlarmListener? = null
    @Volatile private var fallbackRunnable: Runnable? = null

    fun HookRuntime.hookAodDurationLimit() {
        val dozeClass = findClass(DOZE_SERVICE)
        installDreamLifecycleHooks(dozeClass)
        installFinishGuard()
        installNativeVoteGuard()
    }

    private fun HookRuntime.installDreamLifecycleHooks(dozeClass: Class<*>) {
        runCatching {
            val method = findMethod(dozeClass, "onDreamingStarted")
            intercept("aod.duration.started", method) { chain ->
                val result = chain.proceed()
                chain.getThisObject()?.let(::startSession)
                result
            }
        }.onSuccess {
            AodLog.i("DURATION_HOOK", "onDreamingStarted registered")
        }.onFailure {
            AodLog.e("DURATION_HOOK", "onDreamingStarted unavailable", it)
        }

        runCatching {
            val method = findMethod(dozeClass, "onDreamingStopped")
            intercept("aod.duration.stopped", method) { chain ->
                stopSession("DozeService.onDreamingStopped")
                chain.proceed()
            }
        }.onSuccess {
            AodLog.i("DURATION_HOOK", "onDreamingStopped registered")
        }.onFailure {
            AodLog.e("DURATION_HOOK", "onDreamingStopped unavailable", it)
        }

        runCatching {
            val method = findMethod(dozeClass, "onWakeUp")
            intercept("aod.duration.wakeup", method) { chain ->
                explicitWakeUntilMs = SystemClock.elapsedRealtime() + WAKE_GRACE_MS
                AodLog.i("AOD_DURATION_WAKE", "allow native wake for ${WAKE_GRACE_MS}ms")
                chain.proceed()
            }
        }.onFailure {
            AodLog.w("DURATION_HOOK", "onWakeUp guard unavailable", it)
        }
    }

    private fun HookRuntime.installFinishGuard() {
        runCatching {
            val method = DreamService::class.java.getDeclaredMethod("finish").apply { isAccessible = true }
            finishMethod = method
            intercept("aod.duration.finish", method) { chain ->
                val service = chain.getThisObject()
                if (service != null && shouldBlockFinish()) {
                    val now = SystemClock.elapsedRealtime()
                    val remaining = remainingMs(now)
                    AodLog.w(
                        "AOD_DURATION_FINISH_BLOCKED",
                        "mode=${durationModeName(sessionMode)} elapsedMs=${now - sessionStartedAtMs} remainingMs=$remaining stack=${finishCallerStack()}",
                    )
                    null
                } else {
                    chain.proceed()
                }
            }
        }.onSuccess {
            AodLog.i("DURATION_HOOK", "DreamService.finish guard registered")
        }.onFailure {
            AodLog.e("DURATION_HOOK", "DreamService.finish guard unavailable", it)
        }
    }

    private fun HookRuntime.installNativeVoteGuard() {
        runCatching {
            val clientClass = findClass(AOD_VIRTUAL_CLIENT)
            val method = findMethod(clientClass, "getVoteState")
            intercept("aod.duration.native-vote", method) { chain ->
                val original = chain.proceed() as? Int ?: return@intercept chain.proceed()
                val client = chain.getThisObject() ?: return@intercept original
                val requestState = readIntField(client, "mRequestState") ?: return@intercept original
                val reason = readField(client, "mReason")?.toString()
                val now = SystemClock.elapsedRealtime()
                val active = isCustomSessionActive()
                val beforeDeadline = isBeforeDeadline(now)
                val powerSave = isPowerSaveMode()
                val hold = AodDurationPolicy.shouldHoldNativeSuspend(
                    active = active,
                    beforeDeadline = beforeDeadline,
                    powerSave = powerSave,
                    requestState = requestState,
                    voteState = original,
                    reason = reason,
                )
                if (hold) {
                    AodLog.d(
                        "AOD_DURATION_NATIVE_HOLD",
                        "reason=$reason request=${stateName(requestState)} original=${stateName(original)} target=${stateName(Display.STATE_DOZE)} remainingMs=${remainingMs(now)}",
                    )
                    Display.STATE_DOZE
                } else {
                    if (active && original != requestState) {
                        AodLog.d(
                            "AOD_DURATION_NATIVE_VOTE",
                            "reason=$reason request=${stateName(requestState)} vote=${stateName(original)} beforeDeadline=$beforeDeadline powerSave=$powerSave",
                        )
                    }
                    original
                }
            }
        }.onSuccess {
            AodLog.i("DURATION_HOOK", "AODVirtualDozeClient vote guard registered")
        }.onFailure {
            AodLog.w("DURATION_HOOK", "AODVirtualDozeClient vote guard unavailable", it)
        }
    }

    private fun startSession(dozeService: Any) {
        cancelDeadlineAlarm()
        val cfg = AodConfigReader.read(MainHook.hostAppContext)
        val mode = cfg.aodDurationMode
        val limitMs = durationLimitMs(cfg)
        val now = SystemClock.elapsedRealtime()
        val token = sessionToken.incrementAndGet()

        sessionStartedAtMs = now
        sessionMode = mode
        explicitWakeUntilMs = 0L
        activeDozeService = dozeService
        sessionActive.set(mode != AodConfigContract.DURATION_MODE_SYSTEM)

        if (mode == AodConfigContract.DURATION_MODE_SYSTEM) {
            sessionDeadlineMs = Long.MIN_VALUE
            AodLog.i("AOD_DURATION", "mode=system action=native")
            return
        }

        if (mode == AodConfigContract.DURATION_MODE_ALWAYS) {
            sessionDeadlineMs = Long.MAX_VALUE
            AodLog.i("AOD_DURATION_SCHEDULE", "mode=always deadline=none nativeEarlyFinishGuard=true")
            return
        }

        if (limitMs == null) {
            sessionDeadlineMs = Long.MIN_VALUE
            sessionActive.set(false)
            AodLog.w("AOD_DURATION", "mode=${durationModeName(mode)} invalid-limit; falling back to native")
            return
        }

        sessionDeadlineMs = now + limitMs
        AodLog.i(
            "AOD_DURATION_SCHEDULE",
            "token=$token mode=${durationModeName(mode)} limitMs=$limitMs deadlineElapsed=${sessionDeadlineMs}",
        )
        scheduleDeadlineAlarm(token, dozeService, limitMs)
    }

    private fun scheduleDeadlineAlarm(token: Long, dozeService: Any, limitMs: Long) {
        val context = MainHook.hostAppContext
        val alarm = context?.getSystemService(AlarmManager::class.java)
        if (alarm != null) {
            val listener = AlarmManager.OnAlarmListener { expireSession(token, dozeService, limitMs, "alarm") }
            alarmManager = alarm
            alarmListener = listener
            runCatching {
                alarm.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    sessionDeadlineMs,
                    ALARM_TAG,
                    listener,
                    mainHandler,
                )
            }.onSuccess {
                AodLog.d("AOD_DURATION_TIMER", "exact elapsed-realtime alarm scheduled")
                return
            }.onFailure {
                AodLog.w("AOD_DURATION_TIMER", "exact alarm unavailable; falling back to handler", it)
                alarmListener = null
                alarmManager = null
            }
        }

        val fallback = Runnable { expireSession(token, dozeService, limitMs, "handler-fallback") }
        fallbackRunnable = fallback
        mainHandler.postDelayed(fallback, limitMs)
    }

    private fun expireSession(token: Long, dozeService: Any, limitMs: Long, source: String) {
        if (sessionToken.get() != token || !sessionActive.get()) return
        val now = SystemClock.elapsedRealtime()
        if (sessionMode == AodConfigContract.DURATION_MODE_ALWAYS) return
        AodLog.i(
            "AOD_DURATION_EXPIRED",
            "token=$token mode=${durationModeName(sessionMode)} limitMs=$limitMs elapsedMs=${now - sessionStartedAtMs} source=$source",
        )
        forceFinish.set(true)
        try {
            val method = finishMethod ?: DreamService::class.java.getDeclaredMethod("finish").apply { isAccessible = true }
            method.invoke(dozeService)
        } catch (t: Throwable) {
            AodLog.e("AOD_DURATION_EXPIRED", "forced finish failed", t)
        } finally {
            forceFinish.set(false)
        }
    }

    private fun shouldBlockFinish(): Boolean {
        if (!isCustomSessionActive()) return false
        if (forceFinish.get()) return false
        val now = SystemClock.elapsedRealtime()
        if (!isBeforeDeadline(now)) return false
        if (now <= explicitWakeUntilMs) return false
        val context = MainHook.hostAppContext
        val power = runCatching { context?.getSystemService(PowerManager::class.java) }.getOrNull()
        if (power?.isInteractive == true) return false
        if (power?.isPowerSaveMode == true) return false
        return true
    }

    private fun isCustomSessionActive(): Boolean =
        sessionActive.get() && sessionMode != AodConfigContract.DURATION_MODE_SYSTEM

    private fun isBeforeDeadline(now: Long): Boolean = when (sessionMode) {
        AodConfigContract.DURATION_MODE_ALWAYS -> true
        AodConfigContract.DURATION_MODE_SYSTEM -> false
        else -> sessionDeadlineMs != Long.MIN_VALUE && now < sessionDeadlineMs
    }

    private fun isPowerSaveMode(): Boolean = runCatching {
        MainHook.hostAppContext?.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
    }.getOrDefault(false)

    private fun remainingMs(now: Long): Long = when (sessionMode) {
        AodConfigContract.DURATION_MODE_ALWAYS -> Long.MAX_VALUE
        AodConfigContract.DURATION_MODE_SYSTEM -> 0L
        else -> (sessionDeadlineMs - now).coerceAtLeast(0L)
    }

    private fun stopSession(reason: String) {
        cancelDeadlineAlarm()
        val now = SystemClock.elapsedRealtime()
        AodLog.d(
            "AOD_DURATION_CANCEL",
            "reason=$reason mode=${durationModeName(sessionMode)} elapsedMs=${(now - sessionStartedAtMs).coerceAtLeast(0L)}",
        )
        sessionToken.incrementAndGet()
        sessionActive.set(false)
        activeDozeService = null
        sessionDeadlineMs = Long.MIN_VALUE
        explicitWakeUntilMs = 0L
    }

    private fun cancelDeadlineAlarm() {
        alarmListener?.let { listener -> runCatching { alarmManager?.cancel(listener) } }
        alarmListener = null
        alarmManager = null
        fallbackRunnable?.let(mainHandler::removeCallbacks)
        fallbackRunnable = null
    }

    private fun durationLimitMs(cfg: AodConfig): Long? = when (cfg.aodDurationMode) {
        AodConfigContract.DURATION_MODE_SYSTEM, AodConfigContract.DURATION_MODE_ALWAYS -> null
        AodConfigContract.DURATION_MODE_30_SECONDS -> 30_000L
        AodConfigContract.DURATION_MODE_1_MINUTE -> 60_000L
        AodConfigContract.DURATION_MODE_5_MINUTES -> 300_000L
        AodConfigContract.DURATION_MODE_10_MINUTES -> 600_000L
        AodConfigContract.DURATION_MODE_30_MINUTES -> 1_800_000L
        AodConfigContract.DURATION_MODE_60_MINUTES -> 3_600_000L
        AodConfigContract.DURATION_MODE_CUSTOM -> cfg.aodDurationCustomMinutes
            .coerceIn(
                AodConfigContract.MIN_AOD_DURATION_CUSTOM_MINUTES,
                AodConfigContract.MAX_AOD_DURATION_CUSTOM_MINUTES,
            ).toLong() * 60_000L
        else -> null
    }

    private fun durationModeName(mode: Int) = arrayOf(
        "system", "30s", "1m", "5m", "10m", "30m", "60m", "always", "custom",
    ).getOrElse(mode) { "system" }

    private fun stateName(state: Int): String = runCatching { Display.stateToString(state) }.getOrElse { state.toString() }

    private fun readIntField(instance: Any, name: String): Int? =
        (readField(instance, name) as? Number)?.toInt()

    private fun readField(instance: Any, name: String): Any? {
        var type: Class<*>? = instance.javaClass
        while (type != null) {
            val field: Field? = runCatching { type.getDeclaredField(name) }.getOrNull()
            if (field != null) {
                field.isAccessible = true
                return runCatching { field.get(instance) }.getOrNull()
            }
            type = type.superclass
        }
        return null
    }

    private fun finishCallerStack(): String = Thread.currentThread().stackTrace
        .dropWhile { it.className.contains("AodDurationHook") || it.className.contains("java.lang.Thread") }
        .take(8)
        .joinToString(" <- ") { "${it.className}#${it.methodName}:${it.lineNumber}" }
}

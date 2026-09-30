package com.op.aod.enhance.hook

import android.app.AlarmManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.op.aod.enhance.data.AodConfigContract
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Controls only the ColorOS panoramic AOD native timeout.
 *
 * The native timeout is identified from device traces as:
 * AODDisplayUtil.requestScreenState(OFF, 100, "Panoramic-Aod-Show").
 * All other screen-state requests are passed through unchanged.
 */
internal object AodDurationController {
    private const val DOZE_SERVICE = "com.android.systemui.doze.DozeService"
    private const val AOD_DISPLAY_UTIL = "com.oplus.systemui.aod.display.AODDisplayUtil"
    private const val ALARM_TAG = "ColorOS-AOD-NativeTimeout"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessionSeq = AtomicLong(0L)
    private val active = AtomicBoolean(false)
    private val replayGuard = ThreadLocal.withInitial { false }
    private val stateLock = Any()

    @Volatile private var sessionId = 0L
    @Volatile private var sessionStartedAtMs = 0L
    @Volatile private var sessionMode = AodConfigContract.DURATION_MODE_SYSTEM
    @Volatile private var sessionCustomMinutes = AodConfigContract.DEFAULT_AOD_DURATION_CUSTOM_MINUTES
    @Volatile private var sessionTargetMs: Long? = null
    @Volatile private var pendingNativeOff: NativeOffRequest? = null
    @Volatile private var alarmManager: AlarmManager? = null
    @Volatile private var alarmListener: AlarmManager.OnAlarmListener? = null
    @Volatile private var fallbackRunnable: Runnable? = null

    fun HookRuntime.hookAodDurationControl() {
        installLifecycleHooks()
        installNativeTimeoutInterceptor()
    }

    private fun HookRuntime.installLifecycleHooks() {
        val clazz = findClass(DOZE_SERVICE)

        runCatching {
            val method = clazz.getDeclaredMethod("onDreamingStarted").apply { isAccessible = true }
            intercept("aod.duration.session-start", method) { chain ->
                val result = chain.proceed()
                beginSession()
                result
            }
        }.onSuccess {
            AodLog.i("DURATION_HOOK", "DozeService.onDreamingStarted registered")
        }.onFailure {
            AodLog.e("DURATION_HOOK", "DozeService.onDreamingStarted unavailable", it)
        }

        runCatching {
            val method = clazz.getDeclaredMethod("onDreamingStopped").apply { isAccessible = true }
            intercept("aod.duration.session-stop", method) { chain ->
                endSession("DozeService.onDreamingStopped")
                chain.proceed()
            }
        }.onSuccess {
            AodLog.i("DURATION_HOOK", "DozeService.onDreamingStopped registered")
        }.onFailure {
            AodLog.e("DURATION_HOOK", "DozeService.onDreamingStopped unavailable", it)
        }

        runCatching {
            val method = clazz.getDeclaredMethod("onWakeUp").apply { isAccessible = true }
            intercept("aod.duration.wakeup", method) { chain ->
                AodLog.i(
                    "AOD_SESSION_WAKE",
                    "session=$sessionId elapsedMs=${elapsedMs()} action=pass-through",
                )
                chain.proceed()
            }
        }.onFailure {
            AodLog.w("DURATION_HOOK", "DozeService.onWakeUp unavailable", it)
        }
    }

    private fun HookRuntime.installNativeTimeoutInterceptor() {
        val clazz = findClass(AOD_DISPLAY_UTIL)
        val method = clazz.getDeclaredMethod(
            "requestScreenState",
            Integer.TYPE,
            Integer.TYPE,
            String::class.java,
        ).apply { isAccessible = true }

        intercept("aod.duration.native-panoramic-timeout", method) { chain ->
            if (replayGuard.get()) {
                return@intercept chain.proceed()
            }

            val state = chain.getArg(0) as? Int ?: return@intercept chain.proceed()
            val delayMs = chain.getArg(1) as? Int ?: return@intercept chain.proceed()
            val reason = chain.getArg(2) as? String

            if (!active.get()) {
                return@intercept chain.proceed()
            }

            val elapsed = elapsedMs()
            val mode = sessionMode
            val target = sessionTargetMs
            val shouldIntercept = AodDurationPolicy.shouldInterceptNativeTimeout(
                state = state,
                reason = reason,
                mode = mode,
                elapsedMs = elapsed,
                targetMs = target,
            )

            if (!shouldIntercept) {
                if (state == android.view.Display.STATE_OFF && reason == AodDurationPolicy.NATIVE_TIMEOUT_REASON) {
                    AodLog.i(
                        "AOD_NATIVE_TIMEOUT_PASS",
                        "session=$sessionId mode=${modeName(mode)} elapsedMs=$elapsed targetMs=$target delayMs=$delayMs",
                    )
                }
                return@intercept chain.proceed()
            }

            val receiver = chain.getThisObject() ?: return@intercept chain.proceed()
            synchronized(stateLock) {
                if (pendingNativeOff == null) {
                    pendingNativeOff = NativeOffRequest(
                        receiver = receiver,
                        method = method,
                        state = state,
                        delayMs = delayMs,
                        reason = reason ?: AodDurationPolicy.NATIVE_TIMEOUT_REASON,
                    )
                }
            }

            if (mode == AodConfigContract.DURATION_MODE_ALWAYS) {
                AodLog.i(
                    "AOD_NATIVE_TIMEOUT_INTERCEPT",
                    "session=$sessionId mode=always elapsedMs=$elapsed action=hold-indefinitely nativeDelayMs=$delayMs",
                )
                return@intercept null
            }

            val remaining = ((target ?: 0L) - elapsed).coerceAtLeast(0L)
            AodLog.i(
                "AOD_NATIVE_TIMEOUT_INTERCEPT",
                "session=$sessionId mode=${modeName(mode)} elapsedMs=$elapsed targetMs=$target remainingMs=$remaining nativeDelayMs=$delayMs",
            )
            scheduleReplayIfNeeded(remaining)
            null
        }

        AodLog.i(
            "DURATION_HOOK",
            "AODDisplayUtil.requestScreenState(int,int,String) registered reason=${AodDurationPolicy.NATIVE_TIMEOUT_REASON}",
        )
    }

    private fun beginSession() {
        cancelReplayAlarm()
        val cfg = AodConfigReader.read(MainHook.hostAppContext)
        val id = sessionSeq.incrementAndGet()
        val now = SystemClock.elapsedRealtime()

        synchronized(stateLock) {
            sessionId = id
            sessionStartedAtMs = now
            sessionMode = cfg.aodDurationMode
            sessionCustomMinutes = cfg.aodDurationCustomMinutes
            sessionTargetMs = AodDurationPolicy.targetDurationMs(
                cfg.aodDurationMode,
                cfg.aodDurationCustomMinutes,
            )
            pendingNativeOff = null
            active.set(true)
        }

        AodLog.i(
            "AOD_SESSION_START",
            "session=$id mode=${modeName(sessionMode)} targetMs=$sessionTargetMs customMinutes=$sessionCustomMinutes",
        )
    }

    private fun endSession(reason: String) {
        val wasActive = active.getAndSet(false)
        val elapsed = elapsedMs()
        cancelReplayAlarm()
        synchronized(stateLock) {
            pendingNativeOff = null
        }
        if (wasActive) {
            AodLog.i(
                "AOD_SESSION_END",
                "session=$sessionId mode=${modeName(sessionMode)} elapsedMs=$elapsed reason=$reason",
            )
        }
    }

    private fun scheduleReplayIfNeeded(remainingMs: Long) {
        if (!active.get()) return
        if (sessionMode == AodConfigContract.DURATION_MODE_ALWAYS) return
        if (alarmListener != null || fallbackRunnable != null) return

        val expectedSession = sessionId
        val triggerAt = SystemClock.elapsedRealtime() + remainingMs
        val context = MainHook.hostAppContext
        val alarm = runCatching { context?.getSystemService(AlarmManager::class.java) }.getOrNull()

        if (alarm != null) {
            val listener = AlarmManager.OnAlarmListener {
                replayNativeOff(expectedSession, "alarm")
            }
            alarmManager = alarm
            alarmListener = listener
            val scheduled = runCatching {
                alarm.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    ALARM_TAG,
                    listener,
                    mainHandler,
                )
            }.onFailure {
                AodLog.w("AOD_DEADLINE_SCHEDULE", "exact alarm failed; using handler fallback", it)
            }.isSuccess

            if (scheduled) {
                AodLog.i(
                    "AOD_DEADLINE_SCHEDULE",
                    "session=$expectedSession source=alarm remainingMs=$remainingMs triggerElapsed=$triggerAt",
                )
                return
            }

            alarmManager = null
            alarmListener = null
        }

        val fallback = Runnable { replayNativeOff(expectedSession, "handler-fallback") }
        fallbackRunnable = fallback
        mainHandler.postDelayed(fallback, remainingMs)
        AodLog.i(
            "AOD_DEADLINE_SCHEDULE",
            "session=$expectedSession source=handler-fallback remainingMs=$remainingMs",
        )
    }

    private fun replayNativeOff(expectedSession: Long, source: String) {
        if (!active.get() || sessionId != expectedSession) return

        val request = synchronized(stateLock) { pendingNativeOff } ?: return
        val elapsed = elapsedMs()
        AodLog.i(
            "AOD_DEADLINE_REACHED",
            "session=$expectedSession elapsedMs=$elapsed source=$source",
        )

        replayGuard.set(true)
        try {
            AodLog.i(
                "AOD_NATIVE_OFF_REPLAY",
                "session=$expectedSession state=${request.state} delayMs=${request.delayMs} reason=${request.reason}",
            )
            request.method.invoke(
                request.receiver,
                request.state,
                request.delayMs,
                request.reason,
            )
        } catch (t: Throwable) {
            AodLog.e("AOD_NATIVE_OFF_REPLAY", "replay failed", t)
        } finally {
            replayGuard.set(false)
        }
    }

    private fun cancelReplayAlarm() {
        alarmListener?.let { listener ->
            runCatching { alarmManager?.cancel(listener) }
        }
        alarmListener = null
        alarmManager = null
        fallbackRunnable?.let(mainHandler::removeCallbacks)
        fallbackRunnable = null
    }

    private fun elapsedMs(): Long {
        val start = sessionStartedAtMs
        if (start <= 0L) return -1L
        return (SystemClock.elapsedRealtime() - start).coerceAtLeast(0L)
    }

    private fun modeName(mode: Int): String = when (mode) {
        AodConfigContract.DURATION_MODE_SYSTEM -> "system"
        AodConfigContract.DURATION_MODE_30_SECONDS -> "30s"
        AodConfigContract.DURATION_MODE_1_MINUTE -> "1m"
        AodConfigContract.DURATION_MODE_5_MINUTES -> "5m"
        AodConfigContract.DURATION_MODE_10_MINUTES -> "10m"
        AodConfigContract.DURATION_MODE_30_MINUTES -> "30m"
        AodConfigContract.DURATION_MODE_60_MINUTES -> "60m"
        AodConfigContract.DURATION_MODE_ALWAYS -> "always"
        AodConfigContract.DURATION_MODE_CUSTOM -> "custom"
        else -> "unknown($mode)"
    }

    private data class NativeOffRequest(
        val receiver: Any,
        val method: Method,
        val state: Int,
        val delayMs: Int,
        val reason: String,
    )
}

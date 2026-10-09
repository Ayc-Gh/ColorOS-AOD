package com.op.aod.enhance.hook

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.op.aod.enhance.data.AodConfigContract
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Controls the ColorOS panoramic AOD energy-saving UI hide and native timeout.
 *
 * The native timeout is identified from device traces as:
 * AODDisplayUtil.requestScreenState(OFF, 100, "Panoramic-Aod-Show").
 * Other screen-state requests and proximity, schedule or user-disable hides
 * are passed through unchanged.
 */
internal object AodDurationController {
    private const val DOZE_SERVICE = "com.android.systemui.doze.DozeService"
    private const val AOD_DISPLAY_UTIL = "com.oplus.systemui.aod.display.AODDisplayUtil"
    private const val PANORAMIC_CONTROLLER = "com.oplus.systemui.aod.controller.PanoramicAodController"
    // ColorOS preserves exact timing for its native AOD alarm action. A private
    // data URI keeps this operation distinct: native action-only receiver
    // filters do not match intents carrying our custom data scheme.
    private const val alarmAction = "com.android.systemui.aod.HIDE_TIME"
    private const val alarmScheme = "coloros-aod-duration"
    private val alarmToken = UUID.randomUUID().toString()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessionSeq = AtomicLong(0L)
    private val replayGuard = ThreadLocal.withInitial { false }
    private val stateLock = Any()

    private class Session(val timing: AodDurationSession) {
        var pendingOff: NativeOffRequest? = null
        var pendingHide: NativeHideRequest? = null
    }
    @Volatile private var session: Session? = null
    private val sessionId get() = session?.timing?.id ?: 0L
    private val sessionMode get() = session?.timing?.mode ?: AodConfigContract.DURATION_MODE_SYSTEM
    private val sessionTargetMs get() = session?.timing?.targetMs
    private val active get() = session?.timing?.visible == true
    private var pendingNativeOff: NativeOffRequest?
        get() = session?.pendingOff
        set(value) { session?.pendingOff = value }
    private var pendingNativeHide: NativeHideRequest?
        get() = session?.pendingHide
        set(value) { session?.pendingHide = value }
    @Volatile private var alarmManager: AlarmManager? = null
    @Volatile private var alarmIntent: PendingIntent? = null
    @Volatile private var alarmReceiver: BroadcastReceiver? = null
    @Volatile private var alarmContext: Context? = null
    @Volatile private var fallbackRunnable: Runnable? = null

    fun HookRuntime.hookAodDurationControl() {
        installLifecycleHooks()
        installEnergySavingHideInterceptor()
        installNativeTimeoutInterceptor()
    }

    private fun HookRuntime.installEnergySavingHideInterceptor() {
        runCatching {
            val method = findMethod(PANORAMIC_CONTROLLER, "onEnergySavingNotifyHide")
            val hideMethod = findClass(PANORAMIC_CONTROLLER).getDeclaredMethod("hideClock", Integer.TYPE).apply { isAccessible = true }
            intercept("aod.duration.panoramic-energy-hide", method) { chain ->
                if (replayGuard.get() == true || !AodDurationPolicy.shouldDeferEnergySavingHide(
                        active, sessionMode, elapsedMs(), sessionTargetMs,
                    )) {
                    endSession("native UI hide allowed", hidden = true)
                    return@intercept chain.proceed()
                }
                val receiver = chain.getThisObject() ?: return@intercept chain.proceed()
                synchronized(stateLock) { pendingNativeHide = NativeHideRequest(receiver, hideMethod) }
                AodLog.i("AOD_UI_HIDE_DEFER", "session=$sessionId elapsedMs=${elapsedMs()} targetMs=$sessionTargetMs mode=${modeName(sessionMode)}")
                null
            }
            // The minute update can independently hide with reason 12 after
            // isDisplayModeAllowUpdateClock rejects the native energy-saving
            // quota. Other reasons (proximity, schedule, user switch) pass.
            intercept("aod.duration.panoramic-clock-hide", hideMethod) { chain ->
                val reason = chain.getArg(0) as? Int
                if (replayGuard.get() == true || !AodDurationPolicy.shouldDeferClockHide(
                        reason, active, sessionMode, elapsedMs(), sessionTargetMs,
                    )) {
                    endSession("native UI hide allowed", hidden = true)
                    return@intercept chain.proceed()
                }
                val receiver = chain.getThisObject() ?: return@intercept chain.proceed()
                synchronized(stateLock) { pendingNativeHide = NativeHideRequest(receiver, hideMethod) }
                AodLog.i("AOD_UI_HIDE_DEFER", "session=$sessionId reason=$reason elapsedMs=${elapsedMs()} targetMs=$sessionTargetMs")
                null
            }
        }.onFailure { AodLog.e("DURATION_HOOK", "Panoramic energy-saving hide unavailable", it) }
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
            val method = findMethod(clazz, "onWakeUp")
            intercept("aod.duration.wakeup", method) { chain ->
                AodLog.i(
                    "AOD_SESSION_WAKE",
                    "session=$sessionId elapsedMs=${elapsedMs()} action=pass-through",
                )
                endSession("DreamService.onWakeUp")
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
            if (replayGuard.get() == true) {
                return@intercept chain.proceed()
            }

            val state = chain.getArg(0) as? Int ?: return@intercept chain.proceed()
            val delayMs = chain.getArg(1) as? Int ?: return@intercept chain.proceed()
            val reason = chain.getArg(2) as? String
            if (reason == AodDurationPolicy.NATIVE_TIMEOUT_REASON) {
                AodLog.d("AOD_NATIVE_REQUEST", "session=$sessionId active=${active} state=$state delayMs=$delayMs reason=$reason elapsedMs=${elapsedMs()}")
            }

            if (!active) {
                return@intercept chain.proceed()
            }

            val elapsed = elapsedMs()
            val mode = sessionMode
            val target = sessionTargetMs
            // All-day ColorOS may never emit the ordinary OFF timeout. The
            // native panoramic SHOW request gives us a receiver and a safe
            // matching OFF template, so finite modes always get a deadline.
            if (AodDurationPolicy.shouldArmDeadline(state, reason, target) && target != null) {
                chain.getThisObject()?.let { receiver ->
                    synchronized(stateLock) {
                        if (pendingNativeOff == null) pendingNativeOff = NativeOffRequest(
                            receiver, method, android.view.Display.STATE_OFF, 100, AodDurationPolicy.NATIVE_TIMEOUT_REASON,
                        )
                    }
                    scheduleReplayIfNeeded((target - elapsed).coerceAtLeast(0L))
                }
            }
            val shouldIntercept = AodDurationPolicy.shouldInterceptNativeTimeout(
                state = state,
                reason = reason,
                mode = mode,
                visible = active,
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
        val cfg = AodConfigReader.read(MainHook.hostAppContext)
        synchronized(stateLock) {
            cancelReplayAlarm()
            session?.timing?.end()
            session = Session(AodDurationSession(
                sessionSeq.incrementAndGet(), SystemClock.elapsedRealtime(),
                cfg.aodDurationMode, cfg.aodDurationCustomMinutes,
            ))
        }
        AodLog.i("AOD_SESSION_START", "session=$sessionId mode=${modeName(sessionMode)} targetMs=$sessionTargetMs")
    }

    private fun endSession(reason: String, hidden: Boolean = false) = synchronized(stateLock) {
        val current = session ?: return@synchronized
        val wasActive = current.timing.visible
        if (hidden) current.timing.allowHide() else current.timing.end()
        cancelReplayAlarm()
        current.pendingOff = null
        current.pendingHide = null
        if (wasActive) AodLog.i("AOD_SESSION_END", "session=${current.timing.id} elapsedMs=${elapsedMs()} reason=$reason")
    }

    private fun scheduleReplayIfNeeded(remainingMs: Long): Unit = synchronized(stateLock) {
        if (!active) return
        if (sessionMode == AodConfigContract.DURATION_MODE_ALWAYS) return
        if (alarmIntent != null || fallbackRunnable != null) return

        val expectedSession = sessionId
        val triggerAt = SystemClock.elapsedRealtime() + remainingMs
        val context = MainHook.hostAppContext
        val alarm = runCatching { context?.getSystemService(AlarmManager::class.java) }.getOrNull()

        if (alarm != null && context != null) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == alarmAction && intent.data?.host == alarmToken &&
                        intent.data?.lastPathSegment == expectedSession.toString()) {
                        replayNativeOff(expectedSession, "idle-allowed-alarm")
                    }
                }
            }
            alarmManager = alarm
            alarmContext = context
            alarmReceiver = receiver
            val scheduled = runCatching {
                val filter = IntentFilter(alarmAction).apply {
                    addDataScheme(alarmScheme)
                    addDataAuthority(alarmToken, null)
                }
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                val operation = PendingIntent.getBroadcast(
                    context, expectedSession.toInt(), Intent(alarmAction).setPackage(context.packageName)
                        .setData(Uri.parse("$alarmScheme://$alarmToken/$expectedSession")),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                alarmIntent = operation
                alarm.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    operation,
                )
            }.onFailure {
                AodLog.w("AOD_DEADLINE_SCHEDULE", "exact alarm failed; using handler fallback", it)
            }.isSuccess

            if (scheduled) {
                AodLog.i(
                    "AOD_DEADLINE_SCHEDULE",
                    "session=$expectedSession source=idle-allowed-alarm remainingMs=$remainingMs triggerElapsed=$triggerAt",
                )
                return
            }

            cancelReplayAlarm()
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
        if (!active || sessionId != expectedSession) return

        val request = synchronized(stateLock) { pendingNativeOff } ?: return
        val elapsed = elapsedMs()
        AodLog.i(
            "AOD_DEADLINE_REACHED",
            "session=$expectedSession elapsedMs=$elapsed source=$source",
        )

        replayGuard.set(true)
        try {
            // Hiding the rendered panoramic UI is a separate operation from
            // switching the physical display OFF. Replay its native timeout
            // callback first, so ColorOS performs its normal fade and cleanup.
            val hide = synchronized(stateLock) { pendingNativeHide }
            if (hide != null) {
                AodLog.i("AOD_UI_HIDE_REPLAY", "session=$expectedSession elapsedMs=$elapsed")
                hide.method.invoke(hide.receiver, 3)
                return
            }
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
            endSession("deadline replay complete", hidden = true)
        }
    }

    private fun cancelReplayAlarm() = synchronized(stateLock) {
        alarmIntent?.let { operation ->
            runCatching { alarmManager?.cancel(operation) }
            runCatching { operation.cancel() }
        }
        alarmIntent = null
        alarmManager = null
        alarmReceiver?.let { receiver -> runCatching { alarmContext?.unregisterReceiver(receiver) } }
        alarmReceiver = null
        alarmContext = null
        fallbackRunnable?.let(mainHandler::removeCallbacks)
        fallbackRunnable = null
    }

    private fun elapsedMs(): Long = session?.timing?.elapsedMs(SystemClock.elapsedRealtime()) ?: -1L

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

    private data class NativeHideRequest(val receiver: Any, val method: Method)
}

package com.motionzero.app.signal

import android.os.SystemClock
import com.motionzero.app.core.BrakeSignal
import com.motionzero.app.core.CautionSignal
import com.motionzero.app.core.DemoScenario
import com.motionzero.app.core.GearSignal
import com.motionzero.app.core.NaviVoiceSignal
import com.motionzero.app.core.RestResumeSignal
import com.motionzero.app.core.SeatSignal
import com.motionzero.app.core.SectionForecastSignal
import com.motionzero.app.core.Signal
import com.motionzero.app.core.SpeedSignal
import com.motionzero.app.core.SteeringSignal
import com.motionzero.app.core.SymptomSignal
import com.motionzero.app.core.TbtSignal
import com.motionzero.app.core.TurnSignalSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * DemoScenario 를 실시간으로 재생한다(화면 표기: 데모(시뮬레이션)).
 * 시나리오의 0ms 를 시작 시각의 SystemClock.elapsedRealtime() 에 맞춰 옮긴다.
 * scope 는 메인 디스패처여야 한다(엔진은 메인 스레드에서만 호출).
 */
class DemoReplaySource(
    private val scope: CoroutineScope,
    private val sink: (Signal) -> Unit,
    private val onFinished: () -> Unit,
) {
    private var job: Job? = null

    val isRunning: Boolean get() = job != null

    fun start() {
        if (job != null) return
        val signals = DemoScenario.build()
        val startMs = SystemClock.elapsedRealtime()
        job = scope.launch {
            for (signal in signals) {
                val at = startMs + signal.timeMs
                val wait = at - SystemClock.elapsedRealtime()
                if (wait > 0) delay(wait)
                sink(signal.retimed(at))
            }
            job = null
            onFinished()
        }
    }

    /** 재생을 멈춘다. onFinished 는 부르지 않는다. */
    fun stop() {
        job?.cancel()
        job = null
    }
}

internal fun Signal.retimed(timeMs: Long): Signal = when (this) {
    is SpeedSignal -> copy(timeMs = timeMs)
    is GearSignal -> copy(timeMs = timeMs)
    is TurnSignalSignal -> copy(timeMs = timeMs)
    is BrakeSignal -> copy(timeMs = timeMs)
    is SteeringSignal -> copy(timeMs = timeMs)
    is TbtSignal -> copy(timeMs = timeMs)
    is CautionSignal -> copy(timeMs = timeMs)
    is SeatSignal -> copy(timeMs = timeMs)
    is NaviVoiceSignal -> copy(timeMs = timeMs)
    is SectionForecastSignal -> copy(timeMs = timeMs)
    is SymptomSignal -> copy(timeMs = timeMs)
    is RestResumeSignal -> copy(timeMs = timeMs)
}

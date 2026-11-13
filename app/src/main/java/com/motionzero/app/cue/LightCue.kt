package com.motionzero.app.cue

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.motionzero.app.signal.reason

/**
 * 빛 큐: 앰비언트 라이트를 1Hz로 두 번 깜빡인다(뒷좌석 영역 우선).
 * 지원 여부(checkAmbientLightSwitchCapabilities)를 먼저 확인해 기억하고,
 * 미지원·실패 시에는 아무것도 하지 않는다(화면 카드의 정적 표시등은 엔진 상태로 따로 켜진다).
 */
class LightCue(
    private val vehicle: () -> Vehicle?,
    private val log: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val token = Any()
    private var areas: List<SeatArea> = emptyList()
    private val original = mutableMapOf<SeatArea, LightSwitch>()
    private var available: Boolean? = null
    private var lastCheckMs = 0L

    /** 지원 여부를 확인한다. 실패하면 다음 큐에서 일정 간격 뒤 다시 확인한다. */
    fun checkCapability() {
        val v = vehicle() ?: return markUnavailable("차량 SDK 미연결")
        lastCheckMs = SystemClock.elapsedRealtime()
        try {
            v.getLight().checkAmbientLightSwitchCapabilities(
                { config, switches ->
                    main.post {
                        val usable = switches.filterValues { LightSwitch.ON in it && LightSwitch.OFF in it }.keys
                        val rear = usable.filter { area -> area.values.any { it.name.startsWith("ROW_2") } }
                        areas = rear.ifEmpty { usable.toList() }
                        if (!config.isWritable() || areas.isEmpty()) {
                            markUnavailable("제어 미지원")
                        } else {
                            areas.forEach { area -> readOriginal(v, area) }
                            if (available != true) log("빛 큐 · 앰비언트 라이트 사용 (${areas.size}개 영역)")
                            available = true
                        }
                    }
                },
                { e -> main.post { markUnavailable(e.reason()) } },
            )
        } catch (e: Exception) {
            markUnavailable(e.reason())
        }
    }

    fun blink() {
        if (available != true) {
            if (SystemClock.elapsedRealtime() - lastCheckMs > RECHECK_MS) checkCapability()
            return
        }
        main.removeCallbacksAndMessages(token)
        val start = SystemClock.uptimeMillis()
        val steps = listOf(LightSwitch.ON, LightSwitch.OFF, LightSwitch.ON, LightSwitch.OFF)
        steps.forEachIndexed { i, sw ->
            main.postAtTime({ setAll { sw } }, token, start + i * HALF_PERIOD_MS)
        }
        main.postAtTime({ setAll { original[it] ?: LightSwitch.OFF } }, token, start + steps.size * HALF_PERIOD_MS)
    }

    /** 진행 중인 점멸을 멈추고, 점멸 중이었다면 원래 상태로 되돌린다. */
    fun cancel() {
        val blinking = main.hasMessages(0, token)
        main.removeCallbacksAndMessages(token)
        if (blinking) setAll { original[it] ?: LightSwitch.OFF }
    }

    private fun readOriginal(v: Vehicle, area: SeatArea) {
        runCatching {
            v.getLight().getAmbientLightSwitch(area, { sw -> if (sw != null) main.post { original[area] = sw } }, {})
        }
    }

    private fun setAll(switchFor: (SeatArea) -> LightSwitch) {
        val v = vehicle() ?: return
        for (area in areas) {
            try {
                v.getLight().setAmbientLightSwitch(area, switchFor(area), { _, _ -> }, { e ->
                    main.post { markUnavailable(e.reason()) }
                })
            } catch (e: Exception) {
                markUnavailable(e.reason())
            }
        }
    }

    private fun markUnavailable(reason: String) {
        if (available != false) log("빛 큐 · 앰비언트 라이트 사용 안 함 ($reason) → 화면 표시등만")
        available = false
        main.removeCallbacksAndMessages(token)
    }

    private companion object {
        const val HALF_PERIOD_MS = 500L
        const val RECHECK_MS = 30_000L
    }
}

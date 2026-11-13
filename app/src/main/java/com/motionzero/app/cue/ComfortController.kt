package com.motionzero.app.cue

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.Area
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.Window
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.WindowValue
import android.os.Handler
import android.os.Looper
import com.motionzero.app.core.ComfortAction
import com.motionzero.app.signal.reason
import kotlin.math.ceil

/** done(실행됨, 결과 설명). 어느 스레드에서 불려도 된다. */
private typealias Done = (Boolean, String) -> Unit

/**
 * 증상 대응·구간 예보 동작(ComfortAction)을 실행한다.
 * 차량 제어(외기 유입·풍량·뒷창문)는 지원 여부를 확인한 뒤 실행하고, 미지원·실패·무응답이면
 * 운전자에게 음성으로 제안한다. 한 묶음의 안내 문구는 모아서 한 번에 말한다.
 * 각 동작의 결과(실행/제안)는 log 로 화면 기록에 남긴다.
 */
class ComfortController(
    private val vehicle: () -> Vehicle?,
    private val voice: VoiceGuide,
    private val log: (String) -> Unit,
    /** 동작별 결과(실행했으면 true, 운전자에게 제안했으면 false)를 화면에 알린다. */
    private val onResult: (ComfortAction, Boolean) -> Unit = { _, _ -> },
) {
    private val main = Handler(Looper.getMainLooper())

    fun execute(actions: List<ComfortAction>) {
        if (actions.isEmpty()) return
        val speech = arrayOfNulls<String>(actions.size)
        val pending = BooleanArray(actions.size) { true }
        var finished = false

        fun finish() {
            if (finished) return
            finished = true
            actions.forEachIndexed { i, action ->
                if (pending[i]) {
                    pending[i] = false
                    speech[i] = suggestion(action)
                    log("${label(action)} · 제안 (차량 응답 없음)")
                    onResult(action, false)
                }
            }
            val text = speech.filterNotNull().joinToString(" ")
            if (text.isNotEmpty()) voice.say(text)
        }

        actions.forEachIndexed { i, action ->
            run(action) { executed, detail ->
                main.post {
                    if (!pending[i]) return@post
                    pending[i] = false
                    speech[i] = if (executed) null else suggestion(action)
                    onResult(action, executed)
                    log(
                        if (action is ComfortAction.Say) {
                            "음성 안내 · ${action.text}"
                        } else {
                            "${label(action)} · ${if (executed) "실행" else "제안"} ($detail)"
                        },
                    )
                    if (pending.none { it }) finish()
                }
            }
        }
        main.postDelayed({ finish() }, RESPONSE_TIMEOUT_MS)
    }

    private fun run(action: ComfortAction, done: Done) {
        if (action is ComfortAction.Say) return done(false, "음성 안내")
        if (action is ComfortAction.SuggestRestStop) return done(false, "음성 안내")
        val v = vehicle() ?: return done(false, "차량 SDK 미연결")
        val fail: (Exception) -> Unit = { done(false, it.reason()) }
        try {
            when (action) {
                ComfortAction.FreshAir -> freshAir(v, done, fail)
                is ComfortAction.FanUp -> fanUp(v, action.steps, done, fail)
                is ComfortAction.OpenWindow -> openRearWindows(v, action.percent, done, fail)
                else -> Unit
            }
        } catch (e: Exception) {
            fail(e)
        }
    }

    private fun freshAir(v: Vehicle, done: Done, fail: (Exception) -> Unit) {
        val hvac = v.getHVAC()
        hvac.checkHvacRecirculationCapabilities({ config, areas ->
            val area = areas.firstOrNull()
            if (!config.isWritable() || area == null) {
                done(false, "제어 미지원")
            } else {
                guard(fail) { hvac.setHvacRecirculation(area, false, { _, _ -> done(true, "내기 순환 해제") }, fail) }
            }
        }, fail)
    }

    private fun fanUp(v: Vehicle, steps: Int, done: Done, fail: (Exception) -> Unit) {
        val hvac = v.getHVAC()
        hvac.checkHvacFanSpeedCapabilities({ config, bounds ->
            val entry = bounds.entries.firstOrNull { it.key.isRear() } ?: bounds.entries.firstOrNull()
            if (!config.isWritable() || entry == null) {
                done(false, "제어 미지원")
            } else {
                val (area, range) = entry
                guard(fail) {
                    hvac.getHvacFanSpeed(area, { reading ->
                        val current = reading ?: range.minValue
                        val target = minOf(range.maxValue, maxOf(range.minValue, current + steps))
                        if (target == current) {
                            done(true, "이미 최대 풍량 $current")
                        } else {
                            guard(fail) {
                                hvac.setHvacFanSpeed(area, target, { _, _ -> done(true, "풍량 $current → $target") }, fail)
                            }
                        }
                    }, fail)
                }
            }
        }, fail)
    }

    private fun openRearWindows(v: Vehicle, percent: Int, done: Done, fail: (Exception) -> Unit) {
        val window = v.getWindow()
        window.checkWindowPositionCapabilities({ config, bounds ->
            val rear = bounds.filterKeys { it.isRear() }
            if (!config.isWritable() || rear.isEmpty()) {
                done(false, "뒷창문 제어 미지원")
            } else {
                openAll(window, rear, percent, done)
            }
        }, fail)
    }

    private fun openAll(window: Window, rear: Map<WindowArea, AreaBounds<Int>>, percent: Int, done: Done) {
        var remaining = rear.size
        var opened = 0
        val lock = Any()
        val settle: (Boolean) -> Unit = { ok ->
            val last = synchronized(lock) {
                if (ok) opened++
                --remaining == 0
            }
            if (last) {
                if (opened > 0) done(true, "뒷창문 ${opened}곳 ${percent}%") else done(false, "뒷창문 제어 실패")
            }
        }
        rear.forEach { (area, range) ->
            // 최솟값(0 이하)을 닫힘으로 보고, 열림 범위의 percent 만큼(최소 1단계) 연다.
            val closed = maxOf(range.minValue, 0)
            val step = ceil((range.maxValue - closed) * percent / 100.0).toInt().coerceAtLeast(1)
            val target = (closed + step).coerceAtMost(range.maxValue)
            try {
                window.setWindowPosition(area, target, { _, _ -> settle(true) }, { settle(false) })
            } catch (e: Exception) {
                settle(false)
            }
        }
    }

    private inline fun guard(fail: (Exception) -> Unit, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            fail(e)
        }
    }

    private fun label(action: ComfortAction): String = when (action) {
        is ComfortAction.Say -> "음성 안내"
        ComfortAction.FreshAir -> "외기 유입"
        is ComfortAction.FanUp -> "풍량 +${action.steps}"
        is ComfortAction.OpenWindow -> "뒷창문 ${action.percent}%"
        ComfortAction.SuggestRestStop -> "휴식 권장"
    }

    private fun suggestion(action: ComfortAction): String = when (action) {
        is ComfortAction.Say -> action.text
        ComfortAction.FreshAir -> "외기 유입으로 바꿔 주세요."
        is ComfortAction.FanUp -> "바람을 조금 세게 해 주세요."
        is ComfortAction.OpenWindow -> "창문을 조금 열어 주세요."
        ComfortAction.SuggestRestStop -> "가까운 휴게소에서 15~30분 쉬어 가세요."
    }

    private companion object {
        const val RESPONSE_TIMEOUT_MS = 3_000L
    }
}

internal fun CapabilityConfig.isWritable(): Boolean =
    accessType == AccessType.WRITE || accessType == AccessType.READ_WRITE

private fun Area.isRear(): Boolean = when (this) {
    is SeatArea -> values.any { it.name.startsWith("ROW_2") }
    is WindowArea -> values.any { it == WindowValue.ROW_2_LEFT || it == WindowValue.ROW_2_RIGHT }
    else -> false
}

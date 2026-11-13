package com.motionzero.app.core

import kotlin.math.max

enum class MotionKind(val label: String) {
    TURN("회전"),
    LANE_CHANGE("차선 변경"),
    BRAKE("감속"),
    BUMP("방지턱"),
    DOWNHILL("내리막"),
    CURVE("커브"),
}

/** 예측한 움직임. secondsAhead 초 뒤에 expectedAccel(m/s²) 크기의 움직임이 온다. */
data class PredictedMotion(
    val timeMs: Long,
    val kind: MotionKind,
    val direction: Direction,
    val secondsAhead: Double,
    val expectedAccel: Double,
    val label: String,
)

/**
 * F1 움직임 예측.
 *
 * - 다음 회전·주의 구간: 남은 시간 t = d ÷ max(v, vMin). 안내 사이의 빈 시간은
 *   차속을 적분한 주행거리로 남은 거리를 줄여 가며 메운다(추측항법).
 *   t ≤ leadTimeSec 이 되는 순간 기동마다 한 번만 예고한다.
 * - 방향지시등 ON: 차선 변경(또는 회전) 직전 신호로 즉시 예고한다.
 * - 브레이크 ON: 감속을 즉시 예고한다.
 * - 회생제동: 페달 없이도 감속하므로 평활화한 차속의 미분이 기준보다 크게 줄면 실시간으로 알린다.
 */
class MotionPredictor(
    var leadTimeSec: Double = DEFAULT_LEAD_SEC,
    private val minSpeedMps: Double = 1.0,
) {
    private class Pending<T>(
        val type: T,
        val anchorDistanceM: Double,
        val anchorOdometerM: Double,
        var announced: Boolean,
    )

    private var speedMps = 0.0
    private var lastSpeedTimeMs: Long? = null
    private var odometerM = 0.0

    private var smoothedSpeed: Double? = null
    private var lastSampleTimeMs: Long? = null
    private var smoothedAccel = 0.0
    private var regenArmed = true

    private var tbt: Pending<TurnType>? = null
    private var caution: Pending<CautionType>? = null

    private var turnSignal = TurnSignalState.NONE
    private var brakePressed = false

    val currentSpeedMps: Double get() = speedMps
    val longitudinalAccel: Double get() = smoothedAccel

    /** 남은 거리(m). 안내가 없으면 null. */
    fun remainingToTurnM(): Double? = tbt?.let { remaining(it) }

    fun onSignal(signal: Signal): List<PredictedMotion> = when (signal) {
        is SpeedSignal -> onSpeed(signal)
        is TbtSignal -> onTbt(signal)
        is CautionSignal -> onCaution(signal)
        is TurnSignalSignal -> onTurnSignal(signal)
        is BrakeSignal -> onBrake(signal)
        else -> emptyList()
    }

    /** 주기적으로(예: 100ms) 호출해 추측항법으로 남은 시간을 갱신한다. */
    fun tick(nowMs: Long): List<PredictedMotion> {
        advanceOdometer(nowMs)
        return checkLeadTime(nowMs)
    }

    private fun onSpeed(s: SpeedSignal): List<PredictedMotion> {
        // tick()도 주행거리 적분 시각을 갱신하므로, 차속 미분에는 직전 차속 샘플 시각을 따로 쓴다.
        val prevTime = lastSampleTimeMs
        lastSampleTimeMs = s.timeMs
        advanceOdometer(s.timeMs)
        val out = mutableListOf<PredictedMotion>()

        val prevSmoothed = smoothedSpeed
        if (prevSmoothed == null || prevTime == null) {
            smoothedSpeed = s.speedMps
        } else {
            val dt = (s.timeMs - prevTime) / 1000.0
            if (dt > 0) {
                val alpha = dt / (SPEED_SMOOTHING_TAU_SEC + dt)
                val next = prevSmoothed + alpha * (s.speedMps - prevSmoothed)
                val rawAccel = (next - prevSmoothed) / dt
                smoothedAccel += alpha * (rawAccel - smoothedAccel)
                smoothedSpeed = next
            }
        }
        speedMps = s.speedMps

        // 회생제동 등 페달 없는 감속: 한 번 알린 뒤 감속이 풀려야 다시 알린다.
        if (smoothedAccel <= -REGEN_DECEL_THRESHOLD && regenArmed && !brakePressed) {
            regenArmed = false
            out += PredictedMotion(
                timeMs = s.timeMs,
                kind = MotionKind.BRAKE,
                direction = Direction.CENTER,
                secondsAhead = 0.0,
                expectedAccel = -smoothedAccel,
                label = "회생제동 감속",
            )
        } else if (smoothedAccel > -REGEN_REARM_THRESHOLD) {
            regenArmed = true
        }

        out += checkLeadTime(s.timeMs)
        return out
    }

    private fun onTbt(s: TbtSignal): List<PredictedMotion> {
        advanceOdometer(s.timeMs)
        if (s.turnType == TurnType.STRAIGHT) return checkLeadTime(s.timeMs)
        val current = tbt
        val isNewManeuver = current == null ||
            current.type != s.turnType ||
            s.distanceM > remaining(current) + NEW_MANEUVER_JUMP_M
        tbt = Pending(
            type = s.turnType,
            anchorDistanceM = s.distanceM,
            anchorOdometerM = odometerM,
            announced = if (isNewManeuver) false else current!!.announced,
        )
        return checkLeadTime(s.timeMs)
    }

    private fun onCaution(s: CautionSignal): List<PredictedMotion> {
        advanceOdometer(s.timeMs)
        if (s.type == CautionType.OTHER) return checkLeadTime(s.timeMs)
        val current = caution
        val isNew = current == null ||
            current.type != s.type ||
            s.distanceM > remaining(current) + NEW_MANEUVER_JUMP_M
        caution = Pending(
            type = s.type,
            anchorDistanceM = s.distanceM,
            anchorOdometerM = odometerM,
            announced = if (isNew) false else current!!.announced,
        )
        return checkLeadTime(s.timeMs)
    }

    private fun onTurnSignal(s: TurnSignalSignal): List<PredictedMotion> {
        val prev = turnSignal
        turnSignal = s.state
        if (prev != TurnSignalState.NONE) return emptyList()
        val direction = when (s.state) {
            TurnSignalState.LEFT -> Direction.LEFT
            TurnSignalState.RIGHT -> Direction.RIGHT
            else -> return emptyList()
        }
        // 이미 예고한 회전을 위해 켠 방향지시등이면 중복으로 알리지 않는다.
        val pending = tbt
        if (pending != null && pending.announced && pending.type.direction == direction &&
            remaining(pending) < TURN_SIGNAL_MATCH_DISTANCE_M
        ) {
            return emptyList()
        }
        return listOf(
            PredictedMotion(
                timeMs = s.timeMs,
                kind = MotionKind.LANE_CHANGE,
                direction = direction,
                secondsAhead = TURN_SIGNAL_LEAD_SEC,
                expectedAccel = LANE_CHANGE_ACCEL,
                label = if (direction == Direction.LEFT) "왼쪽 차선 변경" else "오른쪽 차선 변경",
            ),
        )
    }

    private fun onBrake(s: BrakeSignal): List<PredictedMotion> {
        val prev = brakePressed
        brakePressed = s.pressed
        if (prev || !s.pressed || speedMps < BRAKE_MIN_SPEED_MPS) return emptyList()
        regenArmed = false
        return listOf(
            PredictedMotion(
                timeMs = s.timeMs,
                kind = MotionKind.BRAKE,
                direction = Direction.CENTER,
                secondsAhead = 0.0,
                expectedAccel = BRAKE_ACCEL,
                label = "감속",
            ),
        )
    }

    private fun checkLeadTime(nowMs: Long): List<PredictedMotion> {
        val out = mutableListOf<PredictedMotion>()
        val v = max(speedMps, minSpeedMps)

        tbt?.let { p ->
            val d = remaining(p)
            if (d < -PASSED_CLEAR_M) {
                tbt = null
            } else if (!p.announced && d >= 0) {
                val t = d / v
                if (t <= leadTimeSec) {
                    p.announced = true
                    out += PredictedMotion(
                        timeMs = nowMs,
                        kind = MotionKind.TURN,
                        direction = p.type.direction,
                        secondsAhead = t,
                        expectedAccel = p.type.expectedAccel,
                        label = p.type.label,
                    )
                }
            }
        }

        caution?.let { p ->
            val d = remaining(p)
            if (d < -PASSED_CLEAR_M) {
                caution = null
            } else if (!p.announced && d >= 0) {
                val t = d / v
                if (t <= leadTimeSec) {
                    p.announced = true
                    out += PredictedMotion(
                        timeMs = nowMs,
                        kind = when (p.type) {
                            CautionType.SPEED_BUMP -> MotionKind.BUMP
                            CautionType.DOWNHILL -> MotionKind.DOWNHILL
                            else -> MotionKind.CURVE
                        },
                        direction = Direction.CENTER,
                        secondsAhead = t,
                        expectedAccel = p.type.expectedAccel,
                        label = p.type.label,
                    )
                }
            }
        }
        return out
    }

    private fun remaining(p: Pending<*>): Double = p.anchorDistanceM - (odometerM - p.anchorOdometerM)

    private fun advanceOdometer(nowMs: Long) {
        val last = lastSpeedTimeMs
        if (last != null && nowMs > last) {
            odometerM += speedMps * (nowMs - last) / 1000.0
        }
        if (last == null || nowMs > last) lastSpeedTimeMs = nowMs
    }

    companion object {
        const val DEFAULT_LEAD_SEC = 2.0
        const val MIN_LEAD_SEC = 1.0
        const val MAX_LEAD_SEC = 3.0

        private const val SPEED_SMOOTHING_TAU_SEC = 0.5
        private const val REGEN_DECEL_THRESHOLD = 2.0
        private const val REGEN_REARM_THRESHOLD = 1.0
        private const val NEW_MANEUVER_JUMP_M = 30.0
        private const val PASSED_CLEAR_M = 30.0
        private const val TURN_SIGNAL_MATCH_DISTANCE_M = 150.0
        private const val TURN_SIGNAL_LEAD_SEC = 1.5
        private const val LANE_CHANGE_ACCEL = 0.8
        private const val BRAKE_ACCEL = 1.5
        private const val BRAKE_MIN_SPEED_MPS = 3.0
    }
}

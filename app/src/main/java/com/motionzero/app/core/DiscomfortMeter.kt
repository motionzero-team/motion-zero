package com.motionzero.app.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.tan

/** 구간별 멀미 유발 지수. */
data class SegmentScore(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val startDistanceM: Double,
    val msdv: Double,
)

/**
 * 멀미 유발 지수(트립 리포트용 상대 지표).
 *
 * - 횡가속도 a_y ≈ (v² / L) · tan(δ_sw / i_s)  (자전거 모델 근사)
 * - 종가속도 a_x = Δv / Δt  (평활화한 차속의 차분)
 * - ISO 2631-1 MSDV 개념을 수평 가속도에 적용: MSDV = sqrt(∫ a_w² dt).
 *   a_w는 약 0.25Hz 차단 1차 저역통과로 근사한 주파수 가중 가속도다
 *   (Donohew & Griffin 횡방향 가중: 0.25Hz까지 평탄, 그 위 감쇠).
 * 의학적 예측이 아니라 구간끼리 비교하는 용도다.
 */
class DiscomfortMeter(
    private val wheelbaseM: Double = 2.9,
    private val steeringRatio: Double = 15.0,
    cutoffHz: Double = 0.25,
    private val segmentMs: Long = 60_000L,
) {
    private val rc = 1.0 / (2 * PI * cutoffHz)

    private var lastTimeMs: Long? = null
    private var speedMps = 0.0
    private var smoothedSpeed: Double? = null
    private var steeringDeg = 0.0

    private var axWeighted = 0.0
    private var ayWeighted = 0.0
    private var totalDose = 0.0

    var distanceM = 0.0
        private set
    var hardBrakeCount = 0
        private set
    var sharpTurnCount = 0
        private set
    var lastLateralAccel = 0.0
        private set
    var lastLongitudinalAccel = 0.0
        private set

    private var hardBrakeArmed = true
    private var sharpTurnArmed = true

    private var segmentStartMs: Long? = null
    private var segmentStartDistance = 0.0
    private var segmentDose = 0.0
    private val segments = mutableListOf<SegmentScore>()

    fun onSteering(angleDeg: Double) {
        steeringDeg = angleDeg
    }

    fun onSpeed(timeMs: Long, newSpeedMps: Double) {
        val last = lastTimeMs
        lastTimeMs = timeMs
        if (segmentStartMs == null) segmentStartMs = timeMs
        if (last == null || timeMs <= last) {
            speedMps = newSpeedMps
            smoothedSpeed = newSpeedMps
            return
        }
        val dt = (timeMs - last) / 1000.0
        distanceM += speedMps * dt

        val prevSmoothed = smoothedSpeed ?: newSpeedMps
        val alpha = dt / (SPEED_TAU_SEC + dt)
        val nextSmoothed = prevSmoothed + alpha * (newSpeedMps - prevSmoothed)
        val ax = (nextSmoothed - prevSmoothed) / dt
        smoothedSpeed = nextSmoothed
        speedMps = newSpeedMps

        val wheelRad = steeringDeg * PI / 180.0 / steeringRatio
        val ay = nextSmoothed * nextSmoothed / wheelbaseM * tan(wheelRad)
        lastLongitudinalAccel = ax
        lastLateralAccel = ay

        val lp = dt / (rc + dt)
        axWeighted += lp * (ax - axWeighted)
        ayWeighted += lp * (ay - ayWeighted)
        val dose = (axWeighted * axWeighted + ayWeighted * ayWeighted) * dt
        totalDose += dose
        segmentDose += dose

        if (ax <= -HARD_BRAKE && hardBrakeArmed) {
            hardBrakeCount++
            hardBrakeArmed = false
        } else if (ax > -REARM) {
            hardBrakeArmed = true
        }
        if (abs(ay) >= SHARP_TURN && sharpTurnArmed) {
            sharpTurnCount++
            sharpTurnArmed = false
        } else if (abs(ay) < REARM) {
            sharpTurnArmed = true
        }

        val start = segmentStartMs!!
        if (timeMs - start >= segmentMs) closeSegment(timeMs)
    }

    fun msdv(): Double = sqrt(totalDose)

    /** 진행 중인 구간까지 포함한 구간 목록. */
    fun segments(nowMs: Long? = lastTimeMs): List<SegmentScore> {
        val start = segmentStartMs
        if (start == null || nowMs == null || segmentDose <= 0.0) return segments.toList()
        return segments + SegmentScore(segments.size, start, nowMs, segmentStartDistance, sqrt(segmentDose))
    }

    fun topSegments(count: Int = 2): List<SegmentScore> =
        segments().filter { it.msdv > 0.0 }.sortedByDescending { it.msdv }.take(count)

    private fun closeSegment(endMs: Long) {
        segments += SegmentScore(segments.size, segmentStartMs!!, endMs, segmentStartDistance, sqrt(segmentDose))
        segmentStartMs = endMs
        segmentStartDistance = distanceM
        segmentDose = 0.0
    }

    companion object {
        const val HARD_BRAKE = 2.5
        const val SHARP_TURN = 3.0
        private const val REARM = 1.0
        private const val SPEED_TAU_SEC = 0.5
    }
}

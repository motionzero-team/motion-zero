package com.motionzero.app.core

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 차속 추정기.
 *
 * Vehicle SDK 2.3.2에서 차속 리스너(VehicleSpeedListener)가 제거되어, 공개 SDK 범위 안에서
 * 다음 두 신호로 차속을 추정한다.
 *  1) NaviHelper onCurrentLocationInfo 의 위경도 변화 (하버사인 거리 ÷ 시간)
 *  2) NaviHelper onTBTInfo 의 "다음 회전까지 남은 거리"가 줄어드는 속도
 * 위치 기반 추정이 최근(LOCATION_FRESH_MS 이내)에 있으면 그것을 우선한다.
 */
class SpeedEstimator(
    private val smoothing: Double = 0.4,
) {
    private var lastLat: Double? = null
    private var lastLon: Double? = null
    private var lastLocationMs: Long? = null
    private var lastLocationEstimateMs: Long? = null

    private var lastTbtType: TurnType? = null
    private var lastTbtDistance: Double? = null
    private var lastTbtMs: Long? = null

    private var estimate: Double? = null

    val currentMps: Double? get() = estimate

    fun onLocation(timeMs: Long, lat: Double, lon: Double): SpeedSignal? {
        val pLat = lastLat
        val pLon = lastLon
        val pMs = lastLocationMs
        if (pMs != null && timeMs - pMs < MIN_LOCATION_INTERVAL_MS) return null
        lastLat = lat
        lastLon = lon
        lastLocationMs = timeMs
        if (pLat == null || pLon == null || pMs == null || timeMs <= pMs) return null
        if (timeMs - pMs > MAX_GAP_MS) return null
        val v = haversineM(pLat, pLon, lat, lon) / ((timeMs - pMs) / 1000.0)
        if (v > MAX_PLAUSIBLE_MPS) return null
        lastLocationEstimateMs = timeMs
        return update(timeMs, v)
    }

    fun onTbt(timeMs: Long, type: TurnType, distanceM: Double): SpeedSignal? {
        val pType = lastTbtType
        val pDist = lastTbtDistance
        val pMs = lastTbtMs
        lastTbtType = type
        lastTbtDistance = distanceM
        lastTbtMs = timeMs
        val locMs = lastLocationEstimateMs
        if (locMs != null && timeMs - locMs < LOCATION_FRESH_MS) return null
        if (pType != type || pDist == null || pMs == null || timeMs <= pMs) return null
        if (timeMs - pMs > MAX_GAP_MS) return null
        val travelled = pDist - distanceM
        if (travelled < 0) return null // 새 안내로 바뀜
        val v = travelled / ((timeMs - pMs) / 1000.0)
        if (v > MAX_PLAUSIBLE_MPS) return null
        return update(timeMs, v)
    }

    private fun update(timeMs: Long, raw: Double): SpeedSignal {
        val prev = estimate
        val next = if (prev == null) raw else prev + smoothing * (raw - prev)
        estimate = next
        return SpeedSignal(timeMs, next)
    }

    companion object {
        private const val MIN_LOCATION_INTERVAL_MS = 300L
        private const val MAX_GAP_MS = 5_000L
        private const val LOCATION_FRESH_MS = 3_000L
        private const val MAX_PLAUSIBLE_MPS = 70.0
        private const val EARTH_RADIUS_M = 6_371_000.0

        fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
            return 2 * EARTH_RADIUS_M * asin(sqrt(a))
        }
    }
}

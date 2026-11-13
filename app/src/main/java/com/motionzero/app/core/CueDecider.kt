package com.motionzero.app.core

/** 실제로 내보낼 큐. intensity는 0.2~1.0. */
data class Cue(val motion: PredictedMotion, val intensity: Double)

sealed interface CueDecision {
    data class Emit(val cue: Cue) : CueDecision
    data class Suppressed(val motion: PredictedMotion, val reason: Reason) : CueDecision

    enum class Reason(val label: String) {
        BELOW_THRESHOLD("기준 미만"),
        REFRACTORY("재알림 간격"),
        NAVI_VOICE("내비 음성 안내 중"),
        NO_PASSENGER("동승자 없음"),
        CARE_OFF("케어 꺼짐"),
    }
}

/**
 * 큐를 낼지 정하는 규칙(기획서 5.4-(5)).
 *
 * 예상 |a| ≥ θ(민감도) AND 마지막 큐 이후 ≥ 4초 AND 내비 음성 안내 중이 아님 → 큐 발생.
 * θ 기본값 1.0 m/s², 민감 프로필·증상 신고 후·휴식 후 2시간은 0.7 m/s².
 */
class CueDecider(
    private val normalThreshold: Double = NORMAL_THRESHOLD,
    private val sensitiveThreshold: Double = SENSITIVE_THRESHOLD,
    private val refractoryMs: Long = REFRACTORY_MS,
) {
    var careEnabled = true
    var sensitiveProfile = false
    var hasPassenger = true
    private var sensitiveUntilMs = Long.MIN_VALUE
    private var naviSpeaking = false
    private var lastCueMs: Long? = null

    fun setNaviSpeaking(speaking: Boolean) {
        naviSpeaking = speaking
    }

    /** nowMs ~ untilMs 동안 민감 기준을 쓴다. */
    fun enableSensitiveUntil(untilMs: Long) {
        if (untilMs > sensitiveUntilMs) sensitiveUntilMs = untilMs
    }

    fun isSensitive(nowMs: Long): Boolean = sensitiveProfile || nowMs < sensitiveUntilMs

    fun threshold(nowMs: Long): Double = if (isSensitive(nowMs)) sensitiveThreshold else normalThreshold

    fun decide(motion: PredictedMotion): CueDecision {
        val now = motion.timeMs
        if (!careEnabled) return CueDecision.Suppressed(motion, CueDecision.Reason.CARE_OFF)
        if (!hasPassenger) return CueDecision.Suppressed(motion, CueDecision.Reason.NO_PASSENGER)
        if (motion.expectedAccel < threshold(now)) {
            return CueDecision.Suppressed(motion, CueDecision.Reason.BELOW_THRESHOLD)
        }
        val last = lastCueMs
        if (last != null && now - last < refractoryMs) {
            return CueDecision.Suppressed(motion, CueDecision.Reason.REFRACTORY)
        }
        if (naviSpeaking) return CueDecision.Suppressed(motion, CueDecision.Reason.NAVI_VOICE)
        lastCueMs = now
        val intensity = ((motion.expectedAccel - 0.5) / 2.5).coerceIn(0.2, 1.0)
        return CueDecision.Emit(Cue(motion, intensity))
    }

    companion object {
        const val NORMAL_THRESHOLD = 1.0
        const val SENSITIVE_THRESHOLD = 0.7
        const val REFRACTORY_MS = 4_000L
    }
}

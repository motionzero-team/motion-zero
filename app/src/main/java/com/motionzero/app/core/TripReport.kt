package com.motionzero.app.core

/** 트립 타임라인의 한 점. kind 가 null 이면 증상 신고다. */
data class TripMark(
    val offsetMs: Long,
    val kind: MotionKind?,
    val direction: Direction,
    val label: String,
    val symptom: Severity? = null,
)

/** F6 트립 리포트. */
data class TripReport(
    val durationMs: Long,
    val distanceM: Double,
    val cuesByKind: Map<MotionKind, Int>,
    val suppressedCount: Int,
    val hardBrakes: Int,
    val sharpTurns: Int,
    val msdv: Double,
    val topSegments: List<SegmentScore>,
    val symptomReports: Int,
    val coaching: List<String>,
    /** 타임라인 표시용: 트립 시작부터의 시각별 예고·증상 신고. */
    val marks: List<TripMark> = emptyList(),
) {
    val totalCues: Int get() = cuesByKind.values.sum()

    companion object {
        fun build(
            startMs: Long,
            endMs: Long,
            meter: DiscomfortMeter,
            emitted: List<Cue>,
            suppressedCount: Int,
            symptomReports: Int,
            symptomTimes: List<Pair<Long, Severity>> = emptyList(),
        ): TripReport {
            val durationMs = (endMs - startMs).coerceAtLeast(0)
            val cuesByKind = emitted.groupingBy { it.motion.kind }.eachCount()
            return TripReport(
                durationMs = durationMs,
                distanceM = meter.distanceM,
                cuesByKind = cuesByKind,
                suppressedCount = suppressedCount,
                hardBrakes = meter.hardBrakeCount,
                sharpTurns = meter.sharpTurnCount,
                msdv = meter.msdv(),
                topSegments = meter.topSegments(2),
                symptomReports = symptomReports,
                coaching = coaching(durationMs, meter.hardBrakeCount, meter.sharpTurnCount, symptomReports),
                marks = (
                    emitted.map { TripMark((it.motion.timeMs - startMs).coerceAtLeast(0), it.motion.kind, it.motion.direction, it.motion.label) } +
                        symptomTimes.map { (t, sev) -> TripMark((t - startMs).coerceAtLeast(0), null, Direction.CENTER, sev.label, sev) }
                    ).sortedBy { it.offsetMs },
            )
        }

        fun coaching(durationMs: Long, hardBrakes: Int, sharpTurns: Int, symptomReports: Int): List<String> {
            val tips = mutableListOf<String>()
            val tenMinutes = (durationMs / 600_000.0).coerceAtLeast(1.0)
            if (hardBrakes / tenMinutes >= 3.0 || hardBrakes >= 5) {
                tips += "급감속이 잦았어요. 회생제동 단계를 1단계 낮추면 동승자가 더 편합니다."
            }
            if (sharpTurns >= 3) {
                tips += "회전 구간에서 속도를 조금 더 줄이면 뒷좌석 흔들림이 줄어듭니다."
            }
            if (symptomReports > 0) {
                tips += "다음에는 출발 전에 환기하고, 굽은 길 앞 휴게소를 경유지로 계획해 보세요."
            }
            if (tips.isEmpty()) tips += "동승자에게 편안한 주행이었어요."
            return tips
        }
    }
}

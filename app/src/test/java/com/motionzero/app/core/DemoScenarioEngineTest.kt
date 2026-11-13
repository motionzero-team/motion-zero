package com.motionzero.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 데모 시나리오 전체를 엔진에 흘려 예고·억제·증상 대응·리포트가 설계대로 나오는지 확인한다. */
class DemoScenarioEngineTest {

    private class Recorder : EngineListener {
        val cues = mutableListOf<Cue>()
        val suppressed = mutableListOf<Pair<PredictedMotion, CueDecision.Reason>>()
        val actions = mutableListOf<ComfortAction>()
        val reports = mutableListOf<TripReport>()
        override fun onCue(cue: Cue) { cues += cue }
        override fun onSuppressed(motion: PredictedMotion, reason: CueDecision.Reason) { suppressed += motion to reason }
        override fun onComfortActions(actions: List<ComfortAction>) { this.actions += actions }
        override fun onTripEnded(report: TripReport) { reports += report }
    }

    private fun runScenario(): Recorder {
        val rec = Recorder()
        val engine = MotionCareEngine(rec)
        val signals = DemoScenario.build()
        var i = 0
        var t = 0L
        while (t <= DemoScenario.DURATION_MS) {
            while (i < signals.size && signals[i].timeMs <= t) engine.onSignal(signals[i++])
            engine.tick(t)
            t += 100
        }
        return rec
    }

    @Test
    fun scenarioProducesExpectedCues() {
        val rec = runScenario()
        val summary = rec.cues.joinToString { "${it.motion.timeMs}:${it.motion.kind}/${it.motion.direction}/${it.motion.label}" }

        val turns = rec.cues.filter { it.motion.kind == MotionKind.TURN }
        assertEquals(summary, listOf(Direction.RIGHT, Direction.LEFT, Direction.LEFT), turns.map { it.motion.direction })
        assertTrue(summary, rec.cues.any { it.motion.kind == MotionKind.BUMP })
        assertTrue(summary, rec.cues.any { it.motion.kind == MotionKind.BRAKE && it.motion.label == "회생제동 감속" })
        assertTrue(summary, rec.cues.any { it.motion.kind == MotionKind.BRAKE && it.motion.label == "감속" })

        // 증상 신고(77초) 전 차선 변경은 기준 미만, 후에는 민감 모드라 알린다
        val laneChanges = rec.cues.filter { it.motion.kind == MotionKind.LANE_CHANGE }
        assertEquals(summary, 1, laneChanges.size)
        assertTrue(laneChanges.single().motion.timeMs > 77_000)
        assertTrue(rec.suppressed.count { it.second == CueDecision.Reason.BELOW_THRESHOLD } >= 2)

        // 회전 예고는 실제 회전(조향 최대) 1~3초 전
        val right = turns[0].motion.timeMs
        assertTrue("right turn cue at $right", right in 48_000..50_000)
        val left = turns[1].motion.timeMs
        assertTrue("left turn cue at $left", left in 70_000..72_000)
    }

    @Test
    fun scenarioTriggersComfortActionsAndReport() {
        val rec = runScenario()
        assertTrue(rec.actions.any { it is ComfortAction.Say && it.text.contains("굽은 길") })
        assertTrue(rec.actions.any { it is ComfortAction.OpenWindow })
        assertTrue(rec.actions.any { it is ComfortAction.FanUp })

        assertEquals(1, rec.reports.size)
        val report = rec.reports.single()
        assertNotNull(report)
        assertEquals(1, report.symptomReports)
        assertTrue("hardBrakes=${report.hardBrakes}", report.hardBrakes >= 1)
        assertTrue(report.totalCues >= 6)
        // README 의 체험 주행 표(예고 7회·억제 2회)와 같은 값. "걸러낸 작은 움직임"은 기준 미만만 센다.
        assertEquals(7, report.totalCues)
        assertEquals(2, report.suppressedCount)
        assertTrue(report.distanceM > 1_000)
        assertTrue(report.topSegments.isNotEmpty())
        assertTrue(report.coaching.isNotEmpty())
    }
}

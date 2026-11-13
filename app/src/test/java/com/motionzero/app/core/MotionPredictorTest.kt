package com.motionzero.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionPredictorTest {

    /** 일정 속도로 주행하며 100ms마다 속도 신호를 넣고, 나온 예측을 모은다. */
    private fun drive(
        p: MotionPredictor,
        fromMs: Long,
        toMs: Long,
        speedMps: Double,
        events: Map<Long, Signal> = emptyMap(),
    ): List<PredictedMotion> {
        val out = mutableListOf<PredictedMotion>()
        var t = fromMs
        while (t <= toMs) {
            out += p.onSignal(SpeedSignal(t, speedMps))
            events[t]?.let { out += p.onSignal(it) }
            t += 100
        }
        return out
    }

    @Test
    fun turnCueFiresOnceAtLeadTime() {
        val p = MotionPredictor(leadTimeSec = 2.0)
        val out = drive(p, 0, 12_000, 10.0, mapOf(0L to TbtSignal(0, TurnType.RIGHT, 100.0)))
        val turns = out.filter { it.kind == MotionKind.TURN }
        assertEquals(1, turns.size)
        val cue = turns.single()
        assertEquals(Direction.RIGHT, cue.direction)
        // 100m 남음, 10m/s → 2초 전이 되는 시점은 8초
        assertTrue("cue at ${cue.timeMs}", cue.timeMs in 7_800..8_100)
        assertTrue(cue.secondsAhead <= 2.0)
    }

    @Test
    fun updateOfSameManeuverDoesNotReannounce() {
        val p = MotionPredictor()
        val events = mapOf(
            0L to TbtSignal(0, TurnType.LEFT, 100.0),
            9_000L to TbtSignal(9_000, TurnType.LEFT, 10.0),
        )
        val out = drive(p, 0, 10_000, 10.0, events)
        assertEquals(1, out.count { it.kind == MotionKind.TURN })
    }

    @Test
    fun newManeuverAfterDistanceJumpIsAnnounced() {
        val p = MotionPredictor()
        val events = mapOf(
            0L to TbtSignal(0, TurnType.LEFT, 50.0),
            6_000L to TbtSignal(6_000, TurnType.LEFT, 200.0),
        )
        val out = drive(p, 0, 25_000, 10.0, events)
        assertEquals(2, out.count { it.kind == MotionKind.TURN })
    }

    @Test
    fun speedBumpIsAnnounced() {
        val p = MotionPredictor()
        val out = drive(p, 0, 10_000, 12.0, mapOf(0L to CautionSignal(0, CautionType.SPEED_BUMP, 60.0)))
        val bump = out.single { it.kind == MotionKind.BUMP }
        assertTrue(bump.timeMs in 2_800..3_200)
    }

    @Test
    fun turnSignalRisingEdgeEmitsLaneChange() {
        val p = MotionPredictor()
        p.onSignal(SpeedSignal(0, 20.0))
        val first = p.onSignal(TurnSignalSignal(100, TurnSignalState.LEFT))
        val again = p.onSignal(TurnSignalSignal(200, TurnSignalState.LEFT))
        assertEquals(MotionKind.LANE_CHANGE, first.single().kind)
        assertEquals(Direction.LEFT, first.single().direction)
        assertTrue(again.isEmpty())
    }

    @Test
    fun turnSignalForAlreadyAnnouncedTurnIsNotDuplicated() {
        val p = MotionPredictor()
        val out = drive(
            p, 0, 9_000, 10.0,
            mapOf(
                0L to TbtSignal(0, TurnType.RIGHT, 100.0),
                8_500L to TurnSignalSignal(8_500, TurnSignalState.RIGHT),
            ),
        )
        assertEquals(1, out.count { it.kind == MotionKind.TURN })
        assertEquals(0, out.count { it.kind == MotionKind.LANE_CHANGE })
    }

    @Test
    fun brakeEdgeEmitsOnlyWhenMoving() {
        val p = MotionPredictor()
        p.onSignal(SpeedSignal(0, 1.0))
        assertTrue(p.onSignal(BrakeSignal(100, true)).isEmpty())
        p.onSignal(BrakeSignal(200, false))
        p.onSignal(SpeedSignal(300, 15.0))
        val out = p.onSignal(BrakeSignal(400, true))
        assertEquals(MotionKind.BRAKE, out.single().kind)
    }

    @Test
    fun regenerativeDecelerationEmitsOncePerEpisode() {
        val p = MotionPredictor()
        val out = mutableListOf<PredictedMotion>()
        var v = 25.0
        var t = 0L
        repeat(30) { out += p.onSignal(SpeedSignal(t, v)); t += 100 }
        // 3 m/s² 감속 4초
        repeat(40) { v -= 0.3; out += p.onSignal(SpeedSignal(t, v)); t += 100 }
        repeat(30) { out += p.onSignal(SpeedSignal(t, v)); t += 100 }
        assertEquals(1, out.count { it.kind == MotionKind.BRAKE })
    }

    @Test
    fun gentleDecelerationDoesNotEmit() {
        val p = MotionPredictor()
        val out = mutableListOf<PredictedMotion>()
        var v = 20.0
        var t = 0L
        repeat(50) { v -= 0.12; out += p.onSignal(SpeedSignal(t, v)); t += 100 } // 1.2 m/s²
        assertTrue(out.isEmpty())
    }
}

package com.motionzero.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CueDeciderTest {

    private fun motion(t: Long, accel: Double) =
        PredictedMotion(t, MotionKind.TURN, Direction.RIGHT, 2.0, accel, "우회전")

    private fun reasonOf(d: CueDecision) = (d as CueDecision.Suppressed).reason

    @Test
    fun belowThresholdIsSuppressed() {
        val d = CueDecider()
        assertEquals(CueDecision.Reason.BELOW_THRESHOLD, reasonOf(d.decide(motion(0, 0.8))))
        assertTrue(d.decide(motion(0, 1.0)) is CueDecision.Emit)
    }

    @Test
    fun sensitiveProfileLowersThreshold() {
        val d = CueDecider()
        d.sensitiveProfile = true
        assertTrue(d.decide(motion(0, 0.8)) is CueDecision.Emit)
    }

    @Test
    fun temporarySensitiveModeExpires() {
        val d = CueDecider()
        d.enableSensitiveUntil(10_000)
        assertTrue(d.decide(motion(0, 0.8)) is CueDecision.Emit)
        assertEquals(CueDecision.Reason.BELOW_THRESHOLD, reasonOf(d.decide(motion(20_000, 0.8))))
    }

    @Test
    fun refractoryIntervalIsFourSeconds() {
        val d = CueDecider()
        assertTrue(d.decide(motion(0, 2.0)) is CueDecision.Emit)
        assertEquals(CueDecision.Reason.REFRACTORY, reasonOf(d.decide(motion(3_999, 2.0))))
        assertTrue(d.decide(motion(4_000, 2.0)) is CueDecision.Emit)
    }

    @Test
    fun suppressedWhileNaviSpeaks() {
        val d = CueDecider()
        d.setNaviSpeaking(true)
        assertEquals(CueDecision.Reason.NAVI_VOICE, reasonOf(d.decide(motion(0, 2.0))))
        d.setNaviSpeaking(false)
        assertTrue(d.decide(motion(100, 2.0)) is CueDecision.Emit)
    }

    @Test
    fun noPassengerOrCareOff() {
        val d = CueDecider()
        d.hasPassenger = false
        assertEquals(CueDecision.Reason.NO_PASSENGER, reasonOf(d.decide(motion(0, 2.0))))
        d.hasPassenger = true
        d.careEnabled = false
        assertEquals(CueDecision.Reason.CARE_OFF, reasonOf(d.decide(motion(0, 2.0))))
    }

    @Test
    fun intensityScalesWithAcceleration() {
        val d = CueDecider(refractoryMs = 0)
        val weak = (d.decide(motion(0, 1.0)) as CueDecision.Emit).cue.intensity
        val strong = (d.decide(motion(1, 3.0)) as CueDecision.Emit).cue.intensity
        assertTrue(strong > weak)
        assertTrue(weak >= 0.2 && strong <= 1.0)
    }
}

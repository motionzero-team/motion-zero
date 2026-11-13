package com.motionzero.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedEstimatorTest {

    @Test
    fun locationFixesGiveSpeed() {
        val e = SpeedEstimator(smoothing = 1.0)
        // 위도 0.0001도 ≈ 11.1m
        assertNull(e.onLocation(0, 37.5, 127.0))
        val s = e.onLocation(1_000, 37.5001, 127.0)!!
        assertEquals(11.1, s.speedMps, 0.2)
    }

    @Test
    fun tbtDistanceRateGivesSpeedWhenNoLocation() {
        val e = SpeedEstimator(smoothing = 1.0)
        assertNull(e.onTbt(0, TurnType.LEFT, 300.0))
        val s = e.onTbt(2_000, TurnType.LEFT, 270.0)!!
        assertEquals(15.0, s.speedMps, 0.01)
        // 다른 안내로 바뀌면 추정하지 않는다
        assertNull(e.onTbt(3_000, TurnType.RIGHT, 500.0))
    }

    @Test
    fun locationTakesPriorityOverTbt() {
        val e = SpeedEstimator(smoothing = 1.0)
        e.onLocation(0, 37.5, 127.0)
        e.onLocation(1_000, 37.5001, 127.0)
        e.onTbt(1_100, TurnType.LEFT, 300.0)
        assertNull(e.onTbt(2_100, TurnType.LEFT, 250.0))
    }
}

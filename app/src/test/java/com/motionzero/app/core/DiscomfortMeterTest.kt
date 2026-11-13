package com.motionzero.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscomfortMeterTest {

    private fun run(meter: DiscomfortMeter, profile: (Long) -> Pair<Double, Double>, untilMs: Long) {
        var t = 0L
        while (t <= untilMs) {
            val (v, steer) = profile(t)
            meter.onSteering(steer)
            meter.onSpeed(t, v)
            t += 100
        }
    }

    @Test
    fun steadyStraightDrivingHasNearZeroDose() {
        val m = DiscomfortMeter()
        run(m, { 20.0 to 0.0 }, 60_000)
        assertTrue("msdv=${m.msdv()}", m.msdv() < 0.05)
        assertEquals(0, m.hardBrakeCount)
        assertTrue(m.distanceM in 1_150.0..1_250.0)
    }

    @Test
    fun hardBrakingIsCountedAndRaisesDose() {
        val m = DiscomfortMeter()
        run(m, { t ->
            val v = when {
                t < 5_000 -> 20.0
                t < 10_000 -> 20.0 - 3.0 * (t - 5_000) / 1000.0
                else -> 5.0
            }
            v to 0.0
        }, 20_000)
        assertEquals(1, m.hardBrakeCount)
        assertTrue(m.msdv() > 1.0)
    }

    @Test
    fun corneringRaisesDoseMoreThanStraight() {
        val straight = DiscomfortMeter().also { run(it, { 10.0 to 0.0 }, 30_000) }
        val curvy = DiscomfortMeter().also {
            run(it, { t -> 10.0 to if ((t / 5_000) % 2 == 0L) 90.0 else -90.0 }, 30_000)
        }
        assertTrue(curvy.msdv() > straight.msdv() + 1.0)
        assertTrue(curvy.topSegments(1).isNotEmpty())
    }
}

package com.motionzero.app.cue

import com.motionzero.app.core.Direction
import com.motionzero.app.core.MotionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AudioCueSynthTest {

    private fun channel(pcm: ShortArray, offset: Int) = pcm.filterIndexed { i, _ -> i % 2 == offset }

    @Test
    fun leftCueSoundsOnlyInLeftChannel() {
        val pcm = AudioCue.synthesize(MotionKind.TURN, Direction.LEFT, 1.0)
        assertTrue(channel(pcm, 0).any { it != 0.toShort() })
        assertTrue(channel(pcm, 1).all { it == 0.toShort() })
    }

    @Test
    fun rightCueSoundsOnlyInRightChannel() {
        val pcm = AudioCue.synthesize(MotionKind.LANE_CHANGE, Direction.RIGHT, 1.0)
        assertTrue(channel(pcm, 0).all { it == 0.toShort() })
        assertTrue(channel(pcm, 1).any { it != 0.toShort() })
    }

    @Test
    fun centerCueIsEqualOnBothChannels() {
        val pcm = AudioCue.synthesize(MotionKind.BRAKE, Direction.CENTER, 0.6)
        assertEquals(channel(pcm, 0), channel(pcm, 1))
    }

    @Test
    fun volumeScalesWithIntensity() {
        val loud = AudioCue.synthesize(MotionKind.TURN, Direction.CENTER, 1.0).maxOf { abs(it.toInt()) }
        val soft = AudioCue.synthesize(MotionKind.TURN, Direction.CENTER, 0.2).maxOf { abs(it.toInt()) }
        assertTrue("loud=$loud soft=$soft", soft < loud / 3)
    }

    @Test
    fun bumpIsTwoBlipsWithSilentGap() {
        val pcm = AudioCue.synthesize(MotionKind.BUMP, Direction.CENTER, 1.0)
        val frames = pcm.size / 2
        val rate = AudioCue.SAMPLE_RATE
        assertEquals(rate * (80 + 70 + 80) / 1000, frames)
        val gap = channel(pcm, 0).subList(rate * 80 / 1000, rate * 150 / 1000)
        assertTrue(gap.all { it == 0.toShort() })
    }
}

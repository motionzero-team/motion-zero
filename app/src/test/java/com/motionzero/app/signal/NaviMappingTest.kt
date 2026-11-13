package com.motionzero.app.signal

import ai.pleos.playground.navi.constants.CautionPointType
import ai.pleos.playground.navi.data.CautionPoint
import com.motionzero.app.core.CautionType
import com.motionzero.app.core.DemoScenario
import com.motionzero.app.core.TurnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ai.pleos.playground.navi.constants.TurnType as NaviTurnType

class NaviMappingTest {

    @Test
    fun turnTypesMapToExpectedAccelerationClasses() {
        assertEquals(TurnType.LEFT, NaviTurnType.TURN_LEFT.toCore())
        assertEquals(TurnType.RIGHT, NaviTurnType.TURN_RIGHT.toCore())
        assertEquals(TurnType.SHARP_LEFT, NaviTurnType.TURN_SHARP_L.toCore())
        assertEquals(TurnType.SHARP_RIGHT, NaviTurnType.TURN_SHARP_R.toCore())
        assertEquals(TurnType.U_TURN, NaviTurnType.TURN_UTURN.toCore())
        assertEquals(TurnType.SLIGHT_RIGHT, NaviTurnType.TURN_KEEP_R.toCore())
        assertEquals(TurnType.SLIGHT_LEFT, NaviTurnType.TURN_EXIT_IC_L.toCore())
        assertEquals(TurnType.SLIGHT_RIGHT, NaviTurnType.TURN_ENTER_IC_R.toCore())
        assertEquals(TurnType.ROUNDABOUT, NaviTurnType.TURN_ENTER_ROUNDABOUT_03.toCore())
        assertEquals(TurnType.DESTINATION, NaviTurnType.TURN_ARRIVE.toCore())
        assertEquals(TurnType.STRAIGHT, NaviTurnType.TURN_STRAIGHT.toCore())
        assertEquals(TurnType.STRAIGHT, NaviTurnType.TURN_EXIT_IC_C.toCore())
        assertEquals(TurnType.STRAIGHT, NaviTurnType.TURN_TOLL.toCore())
    }

    @Test
    fun nearestCautionIgnoresUnrelatedAndPassedPoints() {
        val points = listOf(
            CautionPoint("a", CautionPointType.SCHOOL_ZONE, 20),
            CautionPoint("b", CautionPointType.SPEED_BUMP, 150),
            CautionPoint("c", CautionPointType.DOWNHILL_CAUTION_AREA, 90),
            CautionPoint("d", CautionPointType.SPEED_BUMP, -10),
        )
        assertEquals(CautionType.DOWNHILL to 90.0, nearestCaution(points))
        assertNull(nearestCaution(listOf(CautionPoint("x", CautionPointType.FOG_AREA, 10))))
    }

    @Test
    fun retimedKeepsPayloadAndShiftsTime() {
        val original = DemoScenario.build()
        val shifted = original.map { it.retimed(it.timeMs + 5_000) }
        original.zip(shifted).forEach { (a, b) ->
            assertEquals(a.timeMs + 5_000, b.timeMs)
            assertEquals(a, b.retimed(a.timeMs))
        }
    }
}

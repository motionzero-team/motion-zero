package com.motionzero.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** 기어 P 에서 리포트를 띄울지: 짧은 이동·혼자 운전·케어 꺼짐이면 띄우지 않는다. */
class TripReportGateTest {

    private class Recorder : EngineListener {
        val reports = mutableListOf<TripReport>()
        val skipped = mutableListOf<String>()
        override fun onTripEnded(report: TripReport) { reports += report }
        override fun onTripSkipped(reason: String) { skipped += reason }
    }

    private fun drive(seats: Set<Seat>, durationMs: Long, careEnabled: Boolean = true, symptomAtMs: Long? = null): Recorder {
        val rec = Recorder()
        val engine = MotionCareEngine(rec)
        engine.careEnabled = careEnabled
        engine.onSignal(SeatSignal(0, seats))
        engine.onSignal(GearSignal(1_000, Gear.DRIVE))
        symptomAtMs?.let { engine.reportSymptom(Severity.MILD, it) }
        engine.onSignal(GearSignal(1_000 + durationMs, Gear.PARK))
        return rec
    }

    @Test
    fun familyTripShowsReport() {
        val rec = drive(setOf(Seat.DRIVER, Seat.REAR_LEFT), durationMs = 120_000)
        assertEquals(1, rec.reports.size)
        assertEquals(120_000L, rec.reports.single().durationMs)
    }

    @Test
    fun shortMoveWhileParkingIsSkipped() {
        val rec = drive(setOf(Seat.DRIVER, Seat.REAR_LEFT), durationMs = 4_000)
        assertEquals(emptyList<TripReport>(), rec.reports)
        assertEquals(listOf("짧은 이동"), rec.skipped)
    }

    @Test
    fun shortTripWithSymptomStillShowsReport() {
        val rec = drive(setOf(Seat.DRIVER, Seat.REAR_LEFT), durationMs = 20_000, symptomAtMs = 10_000)
        assertEquals(1, rec.reports.size)
    }

    @Test
    fun driverAloneIsSkipped() {
        val rec = drive(setOf(Seat.DRIVER), durationMs = 600_000)
        assertEquals(listOf("동승자 없음"), rec.skipped)
        assertEquals(0, rec.reports.size)
    }

    @Test
    fun passengerBoardingMidTripCounts() {
        val rec = Recorder()
        val engine = MotionCareEngine(rec)
        engine.onSignal(SeatSignal(0, setOf(Seat.DRIVER)))
        engine.onSignal(GearSignal(1_000, Gear.DRIVE))
        engine.onSignal(SeatSignal(30_000, setOf(Seat.DRIVER, Seat.FRONT_PASSENGER)))
        engine.onSignal(GearSignal(200_000, Gear.PARK))
        assertEquals(1, rec.reports.size)
    }

    /** 실차에서는 차속을 위치 변화로 추정해 정차 후 몇 초간 0.5 m/s 이상이 남는다. P 에서 이것으로 새 트립이 시작되면 안 된다. */
    @Test
    fun decayingSpeedAfterParkDoesNotStartTrip() {
        val rec = Recorder()
        val engine = MotionCareEngine(rec)
        engine.onSignal(SeatSignal(0, setOf(Seat.DRIVER, Seat.REAR_LEFT)))
        engine.onSignal(GearSignal(1_000, Gear.DRIVE))
        engine.onSignal(SpeedSignal(60_000, 8.0))
        engine.onSignal(GearSignal(120_000, Gear.PARK))
        listOf(2.3, 1.4, 0.8, 0.49).forEachIndexed { i, v -> engine.onSignal(SpeedSignal(121_000L + i * 1_000, v)) }
        assertEquals(1, rec.reports.size)
        assertFalse(engine.isTripActive)
        assertFalse(engine.state().tripActive)
    }

    @Test
    fun careTurnedOffJustBeforeArrivalStillShowsReport() {
        val rec = Recorder()
        val engine = MotionCareEngine(rec)
        engine.onSignal(SeatSignal(0, setOf(Seat.DRIVER, Seat.REAR_LEFT)))
        engine.onSignal(GearSignal(1_000, Gear.DRIVE))
        engine.careEnabled = false
        engine.onSignal(GearSignal(300_000, Gear.PARK))
        assertEquals(1, rec.reports.size)
    }

    /** 체험 주행을 중간에 멈추면 리포트 없이 끝난다(만들자마자 지워지는 리포트 화면이 뜨지 않게). */
    @Test
    fun cancelledTripMakesNoReport() {
        val rec = Recorder()
        val engine = MotionCareEngine(rec)
        engine.onSignal(SeatSignal(0, setOf(Seat.DRIVER, Seat.REAR_LEFT)))
        engine.onSignal(GearSignal(1_000, Gear.DRIVE))
        engine.reportSymptom(Severity.MILD, 30_000)
        engine.cancelTrip(90_000)
        engine.onSignal(GearSignal(95_000, Gear.PARK))
        assertEquals(0, rec.reports.size)
        assertEquals(emptyList<String>(), rec.skipped)
        assertFalse(engine.isTripActive)
    }

    @Test
    fun careOffIsSkipped() {
        val rec = drive(setOf(Seat.DRIVER, Seat.REAR_LEFT), durationMs = 600_000, careEnabled = false)
        assertEquals(listOf("케어 꺼짐"), rec.skipped)
        assertNull(rec.reports.firstOrNull())
    }
}

package com.motionzero.app.signal

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.motionzero.app.core.BrakeSignal
import com.motionzero.app.core.Gear
import com.motionzero.app.core.GearSignal
import com.motionzero.app.core.Seat
import com.motionzero.app.core.SeatSignal
import com.motionzero.app.core.Signal
import com.motionzero.app.core.SteeringSignal
import com.motionzero.app.core.TurnSignalSignal
import com.motionzero.app.core.TurnSignalState

/**
 * Pleos Vehicle SDK 조회 신호(방향지시등·브레이크·기어·조향각·좌석 착석)를 core 신호로 바꾼다.
 * 차속 리스너는 SDK 2.3.2에 없으므로 PleosNaviSource 가 추정한다.
 * 모든 SDK 호출은 예외를 잡아 상태 문자열로만 알리고, 신호는 메인 스레드에서 sink 로 보낸다.
 * 앰비언트 라이트·공조·창문 제어도 같은 Vehicle 인스턴스를 쓰므로 [vehicle] 로 공유한다.
 */
class PleosVehicleSource(
    context: Context,
    private val sink: (Signal) -> Unit,
    private val onStatus: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val failures = linkedMapOf<String, String>()
    private val unregisters = mutableListOf<() -> Unit>()
    private val occupied = mutableSetOf<Seat>()

    var vehicle: Vehicle? = null
        private set

    private val turnListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(value: TurnSignalValue?) {
            if (value != null) emit { TurnSignalSignal(it, value.toCore()) }
        }
        override fun onFailed(e: Exception) = fail(TURN, e)
    }

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(pressed: Boolean?) = emit { BrakeSignal(it, pressed == true) }
        override fun onFailed(e: Exception) = fail(BRAKE, e)
    }

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            if (gear != null) emit { GearSignal(it, gear.toCore()) }
        }
        override fun onFailed(e: Exception) = fail(GEAR, e)
    }

    private val steeringListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(angle: Float?) {
            if (angle != null) emit { SteeringSignal(it, angle.toDouble()) }
        }
        override fun onFailed(e: Exception) = fail(STEERING, e)
    }

    // 기어 리스너는 SDK 2.3.2에서 deprecated(안드로이드 GEAR_SELECTION 속성 권장)지만 아직 동작하는 공개 API다.
    @Suppress("DEPRECATION")
    fun start() {
        val v = try {
            Vehicle(appContext).also { it.initialize() }
        } catch (e: Exception) {
            onStatus("연결 실패: ${e.reason()}")
            return
        }
        vehicle = v
        register(TURN, { v.getTurnSignal().registerTurnSignalState(turnListener) }) {
            v.getTurnSignal().unregisterTurnSignalState(turnListener)
        }
        register(BRAKE, { v.getBrake().registerBrakePedalStatus(brakeListener) }) {
            v.getBrake().unregisterBrakePedalStatus(brakeListener)
        }
        register(GEAR, {
            v.getGear().registerGearSelection(gearListener)
            v.getGear().getCurrentGearSelectionState(gearListener::onSelected) { fail(GEAR, it) }
        }) { v.getGear().unregisterGearSelection(gearListener) }
        register(STEERING, { v.getSteeringWheel().registerSteeringWheelAngle(steeringListener) }) {
            v.getSteeringWheel().unregisterSteeringWheelAngle(steeringListener)
        }
        for ((value, seat) in SEATS) {
            val area = SeatArea(value)
            val listener = seatListener(seat)
            register(SEAT, {
                v.getSeat().registerSeatOccupancy(area, listener)
                v.getSeat().getCurrentSeatOccupancy(area, listener::onSeatOccupancyUpdated) { fail(SEAT, it) }
            }) { v.getSeat().unregisterSeatOccupancy(area, listener) }
        }
        publishStatus()
    }

    fun stop() {
        unregisters.forEach { runCatching(it) }
        unregisters.clear()
        vehicle?.let { runCatching { it.release() } }
        vehicle = null
    }

    private fun seatListener(seat: Seat) = object : SeatOccupancyListener {
        override fun onSeatOccupancyUpdated(isOccupied: Boolean?) {
            if (isOccupied == null) return
            val present = isOccupied != SEAT_OCCUPANCY_INVERTED
            main.post {
                if (present) occupied.add(seat) else occupied.remove(seat)
                sink(SeatSignal(SystemClock.elapsedRealtime(), occupied.toSet()))
            }
        }
        override fun onFailed(e: Exception) = fail(SEAT, e)
    }

    private fun register(domain: String, subscribe: () -> Unit, unsubscribe: () -> Unit) {
        try {
            subscribe()
            unregisters += unsubscribe
        } catch (e: Exception) {
            failures[domain] = e.reason()
        }
    }

    private fun emit(make: (Long) -> Signal) {
        main.post { sink(make(SystemClock.elapsedRealtime())) }
    }

    private fun fail(domain: String, e: Exception) {
        main.post {
            failures[domain] = e.reason()
            publishStatus()
        }
    }

    private fun publishStatus() {
        val ok = DOMAINS.filterNot { it in failures }
        onStatus(
            when {
                failures.isEmpty() -> "연결됨 (${ok.joinToString("·")})"
                ok.isEmpty() -> "수신 실패: ${failures.values.first()}"
                else -> "일부 연결 (${ok.joinToString("·")}) · 실패: " +
                    failures.entries.joinToString { "${it.key}(${it.value})" }
            },
        )
    }

    companion object {
        private const val TURN = "방향지시등"
        private const val BRAKE = "브레이크"
        private const val GEAR = "기어"
        private const val STEERING = "조향"
        private const val SEAT = "좌석"
        private val DOMAINS = listOf(TURN, BRAKE, GEAR, STEERING, SEAT)

        /**
         * Vehicle SDK 2.3.2는 SEAT_OCCUPANCY 원시값이 1일 때 isOccupied=true를 준다.
         * AOSP VehicleSeatOccupancyState는 1=VACANT, 2=OCCUPIED라서, Pleos Connect Emulator v2.1.0의
         * Virtual Console에서 "탑승"으로 둔 좌석이 앱에서는 빈자리로 들어온다(실측).
         * SDK가 바로잡히면 이 값만 false로 바꾸면 된다.
         */
        private const val SEAT_OCCUPANCY_INVERTED = true

        private val SEATS = listOf(
            SeatValue.ROW_1_LEFT to Seat.DRIVER,
            SeatValue.ROW_1_RIGHT to Seat.FRONT_PASSENGER,
            SeatValue.ROW_2_LEFT to Seat.REAR_LEFT,
            SeatValue.ROW_2_CENTER to Seat.REAR_CENTER,
            SeatValue.ROW_2_RIGHT to Seat.REAR_RIGHT,
        )
    }
}

internal fun TurnSignalValue.toCore(): TurnSignalState = when (this) {
    TurnSignalValue.LEFT -> TurnSignalState.LEFT
    TurnSignalValue.RIGHT -> TurnSignalState.RIGHT
    else -> TurnSignalState.NONE
}

internal fun VehicleGear.toCore(): Gear = when (this) {
    VehicleGear.GEAR_PARK -> Gear.PARK
    VehicleGear.GEAR_REVERSE -> Gear.REVERSE
    VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
    VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
    // GEAR_DRIVE 와 수동 단수(GEAR_1~9)는 모두 전진 주행이다.
    else -> Gear.DRIVE
}

/** SDK 예외를 화면용 짧은 사유로 바꾼다. */
internal fun Throwable.reason(): String = when (this) {
    is SecurityException -> "권한 없음"
    else -> message?.take(60) ?: javaClass.simpleName
}

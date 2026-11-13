package com.motionzero.app.core

/**
 * 차량·내비게이션 신호를 SDK와 무관한 형태로 표현한다.
 * 모든 시간은 단조 증가하는 밀리초(ms) 값이다.
 */
sealed interface Signal {
    val timeMs: Long
}

enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

enum class TurnSignalState { NONE, LEFT, RIGHT, HAZARD }

enum class Seat(val label: String) {
    DRIVER("운전석"),
    FRONT_PASSENGER("조수석"),
    REAR_LEFT("뒷좌석 왼쪽"),
    REAR_CENTER("뒷좌석 가운데"),
    REAR_RIGHT("뒷좌석 오른쪽"),
}

enum class Direction { LEFT, RIGHT, CENTER }

/**
 * 회전 종류별 방향과 예상 가속도(m/s²).
 * 예상 가속도는 큐를 낼지 정하는 기준(θ)과 비교하는 설계값이며, 시험 주행으로 보정한다.
 */
enum class TurnType(val direction: Direction, val expectedAccel: Double, val label: String) {
    SLIGHT_LEFT(Direction.LEFT, 1.0, "완만한 좌회전"),
    LEFT(Direction.LEFT, 2.0, "좌회전"),
    SHARP_LEFT(Direction.LEFT, 2.5, "급좌회전"),
    SLIGHT_RIGHT(Direction.RIGHT, 1.0, "완만한 우회전"),
    RIGHT(Direction.RIGHT, 2.0, "우회전"),
    SHARP_RIGHT(Direction.RIGHT, 2.5, "급우회전"),
    U_TURN(Direction.LEFT, 3.0, "유턴"),
    ROUNDABOUT(Direction.RIGHT, 2.0, "회전교차로"),
    STRAIGHT(Direction.CENTER, 0.0, "직진"),
    DESTINATION(Direction.CENTER, 1.5, "도착 감속"),
}

enum class CautionType(val expectedAccel: Double, val label: String) {
    SPEED_BUMP(1.5, "과속방지턱"),
    DOWNHILL(1.2, "내리막"),
    SHARP_CURVE(2.0, "급커브"),
    OTHER(0.0, "주의 구간"),
}

enum class Severity(val label: String) {
    MILD("가벼움"),
    MODERATE("보통"),
    SEVERE("심함"),
}

data class SpeedSignal(override val timeMs: Long, val speedMps: Double) : Signal

data class GearSignal(override val timeMs: Long, val gear: Gear) : Signal

data class TurnSignalSignal(override val timeMs: Long, val state: TurnSignalState) : Signal

data class BrakeSignal(override val timeMs: Long, val pressed: Boolean) : Signal

data class SteeringSignal(override val timeMs: Long, val angleDeg: Double) : Signal

/** 다음 회전 안내(NaviHelper onTBTInfo): 회전 종류와 남은 거리. */
data class TbtSignal(override val timeMs: Long, val turnType: TurnType, val distanceM: Double) : Signal

/** 주의 구간 안내(NaviHelper onCautionPointInfo): 종류와 남은 거리. */
data class CautionSignal(override val timeMs: Long, val type: CautionType, val distanceM: Double) : Signal

data class SeatSignal(override val timeMs: Long, val occupied: Set<Seat>) : Signal

/** 내비게이션 음성 안내가 나오는 중인지. 안내 중에는 효과음을 내지 않는다. */
data class NaviVoiceSignal(override val timeMs: Long, val speaking: Boolean) : Signal

/** 남은 경로의 굽은 구간 예보(F3). */
data class SectionForecastSignal(override val timeMs: Long, val minutes: Int, val description: String) : Signal

/** 증상 신고(음성·버튼·데모 시나리오). */
data class SymptomSignal(override val timeMs: Long, val severity: Severity) : Signal

/** 휴게소 휴식 후 재출발. 이후 2시간은 민감 모드로 운영한다. */
data class RestResumeSignal(override val timeMs: Long) : Signal

package com.motionzero.app.core

/**
 * 시연용 주행 시나리오: "4인 가족(뒷좌석 7세·4세)의 서울→강릉 여행"을 약 110초로 압축했다.
 *
 * 에뮬레이터의 시뮬레이션 주행이 회전 안내(onTBTInfo)를 보내지 않을 수 있어,
 * 같은 형태의 신호를 시간순으로 재생해 앱 전체 흐름을 확인할 수 있게 한다.
 * 화면에는 "데모(시뮬레이션)"로 표기한다.
 *
 * 흐름: 정차·좌석 인식 → 고속도로(차선 변경은 기준 미만이라 알리지 않음) → 굽은 길 예보
 *   → 출구 감속(회생제동) → 우회전 → 과속방지턱 → 좌회전 → 증상 신고(보통)
 *   → 민감 모드에서 완만한 좌회전·차선 변경 예고 → 도착 감속 → 기어 P(트립 리포트)
 */
object DemoScenario {
    const val DURATION_MS = 110_000L
    private const val SAMPLE_MS = 200L

    /** 화면 자막용 장면 (시작 초, 설명). 신호 타임라인과 같은 시각에 맞췄다. */
    val scenes: List<Pair<Int, String>> = listOf(
        0 to "출발 준비 · 뒷좌석 아이 2명 인식",
        3 to "고속도로 주행",
        18 to "차선 변경 · 작은 움직임은 알리지 않아요",
        24 to "굽은 길 예보 · 미리 환기",
        34 to "고속도로 출구 · 감속",
        40 to "국도 · 우회전 준비",
        53 to "과속방지턱 앞",
        62 to "좌회전 준비",
        77 to "아이가 \"속이 이상해\"",
        80 to "민감 모드 · 작은 움직임도 알려요",
        96 to "도착 · 감속",
        106 to "주차 · 트립 리포트",
    )

    fun sceneAt(elapsedMs: Long): String = scenes.last { it.first * 1000L <= elapsedMs.coerceAtLeast(0) }.second

    /** (초, m/s) 키프레임. 사이는 선형 보간. */
    private val speedKeys = listOf(
        0.0 to 0.0, 3.0 to 0.0, 13.0 to 27.0, 34.0 to 27.0, 39.0 to 13.0,
        45.0 to 13.0, 49.0 to 7.0, 51.0 to 7.0, 55.0 to 13.0,
        66.0 to 13.0, 69.0 to 7.0, 73.0 to 7.0, 77.0 to 13.0,
        96.0 to 13.0, 104.0 to 0.0, 110.0 to 0.0,
    )

    /** (초, 조향각 deg) 키프레임. +는 오른쪽. */
    private val steeringKeys = listOf(
        0.0 to 0.0, 49.5 to 0.0, 51.0 to 120.0, 52.5 to 0.0,
        71.5 to 0.0, 73.0 to -120.0, 74.5 to 0.0,
        86.0 to 0.0, 87.0 to -30.0, 88.5 to 0.0, 110.0 to 0.0,
    )

    fun build(): List<Signal> {
        val out = mutableListOf<Signal>()
        val allSeats = setOf(Seat.DRIVER, Seat.REAR_LEFT, Seat.REAR_RIGHT)

        fun at(sec: Double) = (sec * 1000).toLong()

        out += GearSignal(at(0.0), Gear.PARK)
        out += SeatSignal(at(0.0), allSeats)
        out += GearSignal(at(2.0), Gear.DRIVE)

        // 고속도로 차선 변경: 예상 가속도 0.8 < θ 1.0 → 알리지 않음
        out += TurnSignalSignal(at(18.0), TurnSignalState.LEFT)
        out += TurnSignalSignal(at(21.0), TurnSignalState.NONE)

        // 굽은 구간 예보(F3)
        out += SectionForecastSignal(at(24.0), 10, "굽은 길")

        // 국도 우회전
        out += TbtSignal(at(40.0), TurnType.RIGHT, 120.0)
        out += TbtSignal(at(44.0), TurnType.RIGHT, 68.0)

        // 과속방지턱
        out += CautionSignal(at(55.0), CautionType.SPEED_BUMP, 60.0)

        // 좌회전 (방향지시등은 회전 예고 전에 켜짐 → 차선 변경 기준 미만)
        out += TbtSignal(at(62.0), TurnType.LEFT, 110.0)
        out += TurnSignalSignal(at(66.0), TurnSignalState.LEFT)
        out += TurnSignalSignal(at(75.0), TurnSignalState.NONE)

        // 뒷좌석 아이가 "속이 이상해" → 보통 단계 증상 신고
        out += SymptomSignal(at(77.0), Severity.MODERATE)

        // 민감 모드(θ 0.7)에서는 완만한 회전·차선 변경도 알린다
        out += TbtSignal(at(80.0), TurnType.SLIGHT_LEFT, 90.0)
        out += TurnSignalSignal(at(91.0), TurnSignalState.RIGHT)
        out += TurnSignalSignal(at(94.0), TurnSignalState.NONE)

        // 도착 감속(브레이크) → 정차 → 기어 P
        out += BrakeSignal(at(97.0), true)
        out += BrakeSignal(at(105.0), false)
        out += GearSignal(at(106.0), Gear.PARK)

        var t = 0L
        while (t <= DURATION_MS) {
            val sec = t / 1000.0
            out += SteeringSignal(t, interpolate(steeringKeys, sec))
            out += SpeedSignal(t, interpolate(speedKeys, sec))
            t += SAMPLE_MS
        }
        // 같은 시각이면 차량 상태(속도·조향)를 먼저 반영한 뒤 이벤트를 처리한다.
        return out.sortedWith(compareBy<Signal> { it.timeMs }.thenBy { if (it is SpeedSignal || it is SteeringSignal) 0 else 1 })
    }

    private fun interpolate(keys: List<Pair<Double, Double>>, sec: Double): Double {
        if (sec <= keys.first().first) return keys.first().second
        for (i in 1 until keys.size) {
            val (t1, v1) = keys[i]
            if (sec <= t1) {
                val (t0, v0) = keys[i - 1]
                return if (t1 == t0) v1 else v0 + (v1 - v0) * (sec - t0) / (t1 - t0)
            }
        }
        return keys.last().second
    }
}

package com.motionzero.app.core

/** 화면에 보여 줄 엔진 상태(주행 중 정적 카드 1장 분량). */
data class EngineState(
    val careEnabled: Boolean = true,
    val tripActive: Boolean = false,
    val gear: Gear = Gear.UNKNOWN,
    val speedKph: Double = 0.0,
    val seats: Set<Seat>? = null,
    val sensitive: Boolean = false,
    val lastCue: Cue? = null,
    val lightOn: Boolean = false,
    val forecast: String? = null,
    val cueCount: Int = 0,
    val suppressedCount: Int = 0,
    val remainingToTurnM: Double? = null,
)

/** 완화 동작을 낸 계기. 화면에서 "왜 이렇게 했는지"를 보여 줄 때 쓴다. */
sealed interface ComfortTrigger {
    data class Forecast(val text: String) : ComfortTrigger
    data class Symptom(val severity: Severity) : ComfortTrigger
}

interface EngineListener {
    fun onCue(cue: Cue) {}
    fun onSuppressed(motion: PredictedMotion, reason: CueDecision.Reason) {}
    fun onComfortActions(actions: List<ComfortAction>) {}
    /** 완화 동작과 그 계기(구간 예보·증상 신고). 기본 구현은 onComfortActions 로 넘긴다. */
    fun onComfort(trigger: ComfortTrigger, actions: List<ComfortAction>) = onComfortActions(actions)
    fun onTripEnded(report: TripReport) {}
    /** 트립은 끝났지만 리포트를 띄우지 않을 때(짧은 이동·동승자 없음·케어 꺼짐). */
    fun onTripSkipped(reason: String) {}
    fun onState(state: EngineState) {}
}

/**
 * 예측 → 예고 → 완화 → 학습을 묶는 엔진. Android 의존이 없어 단위 테스트로 검증한다.
 * 신호 소스(차량 SDK, 내비, 데모 재생)는 onSignal 로 신호를 넣고, 화면·출력은 listener 로 받는다.
 */
class MotionCareEngine(private val listener: EngineListener) {
    val predictor = MotionPredictor()
    val decider = CueDecider()

    private var meter = DiscomfortMeter()
    private var symptoms = SymptomManager()
    private val emitted = mutableListOf<Cue>()
    private val symptomTimes = mutableListOf<Pair<Long, Severity>>()
    private var suppressed = 0
    private var passengerSeen = false
    private var careOnDuringTrip = false

    private var tripStartMs: Long? = null
    private var gear = Gear.UNKNOWN
    private var seats: Set<Seat>? = null
    private var forecast: String? = null
    private var lastCue: Cue? = null
    private var lightOffAtMs: Long? = null
    private var lastNowMs = 0L

    /** 운전석 큐는 기본 OFF(주행 안전). 켜면 운전자 혼자여도 큐를 낸다. */
    var driverCueEnabled = false
        set(value) {
            field = value
            updatePassenger()
        }

    var voiceForecastEnabled = true

    var careEnabled: Boolean
        get() = decider.careEnabled
        set(value) {
            decider.careEnabled = value
            if (value && tripStartMs != null) careOnDuringTrip = true
            publish(lastNowMs)
        }

    var leadTimeSec: Double
        get() = predictor.leadTimeSec
        set(value) {
            predictor.leadTimeSec = value.coerceIn(MotionPredictor.MIN_LEAD_SEC, MotionPredictor.MAX_LEAD_SEC)
        }

    var sensitiveProfile: Boolean
        get() = decider.sensitiveProfile
        set(value) {
            decider.sensitiveProfile = value
            publish(lastNowMs)
        }

    val isTripActive: Boolean get() = tripStartMs != null

    fun onSignal(signal: Signal) {
        val now = signal.timeMs
        lastNowMs = now
        when (signal) {
            is SpeedSignal -> {
                // 기어 정보가 없을 때만 차속으로 트립을 시작한다. P 에서는 정차 직후 차속 추정이 천천히 0으로 떨어져도 시작하지 않는다.
                if (tripStartMs == null && gear != Gear.PARK && signal.speedMps > TRIP_START_SPEED_MPS) startTrip(now)
                meter.onSpeed(now, signal.speedMps)
            }
            is SteeringSignal -> meter.onSteering(signal.angleDeg)
            is GearSignal -> {
                gear = signal.gear
                when {
                    signal.gear == Gear.DRIVE && tripStartMs == null -> startTrip(now)
                    signal.gear == Gear.PARK && tripStartMs != null -> endTrip(now)
                }
            }
            is SeatSignal -> {
                seats = signal.occupied
                updatePassenger()
                if (tripStartMs != null && decider.hasPassenger) passengerSeen = true
            }
            is NaviVoiceSignal -> decider.setNaviSpeaking(signal.speaking)
            is SectionForecastSignal -> {
                forecast = "앞으로 ${signal.minutes}분 ${signal.description}"
                val actions = mutableListOf<ComfortAction>(ComfortAction.FreshAir)
                if (voiceForecastEnabled) {
                    actions.add(
                        0,
                        ComfortAction.Say("앞으로 ${signal.minutes}분 ${signal.description}이에요. 창밖 먼 곳을 봐 주세요."),
                    )
                }
                listener.onComfort(ComfortTrigger.Forecast(forecast!!), actions)
            }
            is SymptomSignal -> {
                val response = symptoms.report(signal.severity, now)
                if (tripStartMs != null) symptomTimes += now to signal.severity
                decider.enableSensitiveUntil(response.sensitiveUntilMs)
                listener.onComfort(ComfortTrigger.Symptom(signal.severity), response.actions)
            }
            is RestResumeSignal -> decider.enableSensitiveUntil(symptoms.onRestResume(now))
            else -> Unit
        }
        handle(predictor.onSignal(signal))
        publish(now)
    }

    /** 주기적으로 호출(예: 100ms): 추측항법 갱신, 빛 큐 끄기. */
    fun tick(nowMs: Long) {
        lastNowMs = nowMs
        val motions = predictor.tick(nowMs)
        handle(motions)
        val off = lightOffAtMs
        if (off != null && nowMs >= off) {
            lightOffAtMs = null
            publish(nowMs)
        } else if (motions.isNotEmpty()) {
            publish(nowMs)
        }
    }

    /** 체험 주행을 중간에 멈출 때: 리포트 없이 트립을 버린다(끝까지 가지 않은 체험은 리포트 대상이 아니다). */
    fun cancelTrip(nowMs: Long) {
        tripStartMs = null
        publish(nowMs)
    }

    fun reportSymptom(severity: Severity, nowMs: Long) = onSignal(SymptomSignal(nowMs, severity))

    fun state(nowMs: Long = lastNowMs): EngineState = EngineState(
        careEnabled = decider.careEnabled,
        tripActive = tripStartMs != null,
        gear = gear,
        speedKph = predictor.currentSpeedMps * 3.6,
        seats = seats,
        sensitive = decider.isSensitive(nowMs),
        lastCue = lastCue,
        lightOn = lightOffAtMs?.let { nowMs < it } ?: false,
        forecast = forecast,
        cueCount = emitted.size,
        suppressedCount = suppressed,
        remainingToTurnM = predictor.remainingToTurnM(),
    )

    private fun handle(motions: List<PredictedMotion>) {
        for (m in motions) {
            when (val d = decider.decide(m)) {
                is CueDecision.Emit -> {
                    emitted += d.cue
                    lastCue = d.cue
                    lightOffAtMs = m.timeMs + LIGHT_ON_MS
                    listener.onCue(d.cue)
                }
                is CueDecision.Suppressed -> {
                    // 리포트의 "걸러낸 작은 움직임"은 기준 미만만 센다(재알림 간격·동승자 없음 등은 제외).
                    if (d.reason == CueDecision.Reason.BELOW_THRESHOLD) suppressed++
                    listener.onSuppressed(d.motion, d.reason)
                }
            }
        }
    }

    private fun updatePassenger() {
        val s = seats
        decider.hasPassenger = driverCueEnabled || s == null || s.any { it != Seat.DRIVER }
    }

    private fun startTrip(nowMs: Long) {
        tripStartMs = nowMs
        meter = DiscomfortMeter()
        symptoms = SymptomManager()
        emitted.clear()
        symptomTimes.clear()
        suppressed = 0
        passengerSeen = decider.hasPassenger
        careOnDuringTrip = decider.careEnabled
        lastCue = null
        forecast = null
    }

    private fun endTrip(nowMs: Long): TripReport? {
        val start = tripStartMs ?: nowMs
        tripStartMs = null
        val skip = when {
            !careOnDuringTrip -> "케어 꺼짐"
            !passengerSeen && symptomTimes.isEmpty() -> "동승자 없음"
            nowMs - start < MIN_REPORT_TRIP_MS && emitted.isEmpty() && symptomTimes.isEmpty() -> "짧은 이동"
            else -> null
        }
        if (skip != null) {
            listener.onTripSkipped(skip)
            return null
        }
        val report = TripReport.build(start, nowMs, meter, emitted.toList(), suppressed, symptoms.reportCount, symptomTimes.toList())
        listener.onTripEnded(report)
        return report
    }

    private fun publish(nowMs: Long) = listener.onState(state(nowMs))

    companion object {
        private const val TRIP_START_SPEED_MPS = 0.5
        /** 이보다 짧고 예고·증상이 없던 주행(주차 중 기어 변경 등)은 리포트를 띄우지 않는다. */
        const val MIN_REPORT_TRIP_MS = 60_000L
        /** 빛 큐 점등 시간(1Hz 점멸 2회 분량). */
        const val LIGHT_ON_MS = 2_000L
    }
}

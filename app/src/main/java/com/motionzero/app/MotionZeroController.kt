package com.motionzero.app

import android.app.Application
import android.content.Context
import android.os.SystemClock
import com.motionzero.app.core.ComfortAction
import com.motionzero.app.core.ComfortTrigger
import com.motionzero.app.core.Cue
import com.motionzero.app.core.CueDecision
import com.motionzero.app.core.EngineListener
import com.motionzero.app.core.EngineState
import com.motionzero.app.core.Gear
import com.motionzero.app.core.GearSignal
import com.motionzero.app.core.MotionCareEngine
import com.motionzero.app.core.MotionPredictor
import com.motionzero.app.core.PredictedMotion
import com.motionzero.app.core.SeatSignal
import com.motionzero.app.core.Severity
import com.motionzero.app.core.Signal
import com.motionzero.app.core.TripReport
import com.motionzero.app.cue.AudioCue
import com.motionzero.app.cue.ComfortController
import com.motionzero.app.cue.LightCue
import com.motionzero.app.cue.VoiceGuide
import com.motionzero.app.signal.DemoReplaySource
import com.motionzero.app.signal.DrivingRestriction
import com.motionzero.app.signal.PleosNaviSource
import com.motionzero.app.signal.PleosVehicleSource
import com.motionzero.app.ui.directionLabel
import com.motionzero.app.ui.leadLabel
import com.motionzero.app.ui.symptomTitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class UiState(
    val engine: EngineState = EngineState(),
    val leadTimeSec: Double = MotionPredictor.DEFAULT_LEAD_SEC,
    val sensitiveProfile: Boolean = false,
    val voiceForecast: Boolean = true,
    val vehicleStatus: String = "연결 중…",
    val naviStatus: String = "연결 중…",
    val vehicleConnected: Boolean = false,
    val naviConnected: Boolean = false,
    val demoRunning: Boolean = false,
    val demoElapsedMs: Long = 0,
    /** 플랫폼 주행 규제(CarUxRestrictions)가 주행 중 최적화를 요구하는지. */
    val restricted: Boolean = false,
    val comfort: ComfortFeed? = null,
    /** 사용자가 버튼으로 증상을 신고한 횟수. 화면이 "새 신고"를 알아채는 데만 쓴다(체험 시나리오의 증상은 세지 않는다). */
    val symptomSeq: Int = 0,
    val log: List<String> = emptyList(),
) {
    /** 주행 화면(설정·체험·기록 숨김)을 쓸지. 실제 주행 규제 또는 체험 주행 중. */
    val drivingLayout: Boolean get() = restricted || (demoRunning && engine.tripActive)
}

enum class ComfortKind { VOICE, FRESH_AIR, FAN, WINDOW, REST, SENSITIVE }

enum class ComfortStatus { PENDING, DONE, SUGGESTED }

data class ComfortItem(val kind: ComfortKind, val status: ComfortStatus, val text: String)

/** 마지막 완화 동작 묶음: 왜(title) 무엇을 했는지(items). */
data class ComfortFeed(val title: String, val time: String, val severity: Severity?, val items: List<ComfortItem>)

/**
 * 엔진 1개에 모든 신호 소스를 메인 스레드에서 연결하고, 엔진 출력을 소리·빛·음성·차량 제어로 내보낸다.
 * 시간 기준은 모두 SystemClock.elapsedRealtime() 이다.
 *
 * 화면(Activity)이 아니라 프로세스 단위로 하나만 둔다. 주행 중에는 내비가 앱 영역 전체를 쓰고 멀미 제로는 화면 없이
 * [MotionZeroService](포그라운드 서비스) 안에서 계속 빛·소리로 알린다. 화면은 이 객체의 [ui] 를 구독만 한다.
 */
class MotionZeroController private constructor(app: Application) : EngineListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _ui = MutableStateFlow(
        UiState(
            leadTimeSec = prefs.getFloat(KEY_LEAD, MotionPredictor.DEFAULT_LEAD_SEC.toFloat()).toDouble(),
            sensitiveProfile = prefs.getBoolean(KEY_SENSITIVE, false),
            voiceForecast = prefs.getBoolean(KEY_VOICE, true),
            engine = EngineState(careEnabled = prefs.getBoolean(KEY_CARE, true)),
        ),
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val voice = VoiceGuide(app, ::log)
    private val audio = AudioCue(app)
    private val vehicleSource = PleosVehicleSource(app, { feed(it, fromDemo = false) }) { status ->
        _ui.update { it.copy(vehicleStatus = status, vehicleConnected = status.startsWith("연결됨") || status.startsWith("일부")) }
    }
    private val naviSource = PleosNaviSource(app, { feed(it, fromDemo = false) }) { status ->
        _ui.update { it.copy(naviStatus = status, naviConnected = status.startsWith("연결됨")) }
    }
    private val restriction = DrivingRestriction(app) { restricted ->
        if (restricted != _ui.value.restricted) log(if (restricted) "주행 규제 · 주행 화면으로 전환" else "주행 규제 해제 · 정차 화면")
        // 이미 본 리포트는 주행 규제가 걸리면 닫는다. 아직 보지 않은 리포트는 기어 기준 보관 규칙(onGearChanged)을 따른다.
        if (restricted && ReportRelay.current.value === ReportRelay.opened) closeReport()
        _ui.update { it.copy(restricted = restricted) }
    }
    private val light = LightCue({ vehicleSource.vehicle }, ::log)
    private val comfort = ComfortController({ vehicleSource.vehicle }, voice, ::log, ::onComfortResult)
    private val pendingComfort = mutableListOf<ComfortAction?>()
    private var demoStartMs = 0L
    private var lastSymptomMs = Long.MIN_VALUE / 2
    private var lastSymptom: Severity? = null
    private val demo = DemoReplaySource(scope, { feed(it, fromDemo = true) }, ::onDemoFinished)

    // 리포트 보관(엔진보다 먼저 초기화한다. 엔진을 만들 때 onState 가 불린다): 기어가 "바뀌는 순간"에만 판단한다(상태로 판단하면 리포트를 만든 직후의 상태 갱신이 바로 지워 버린다).
    private var lastGear = Gear.UNKNOWN
    private var discardReportAtMs: Long? = null

    private var engine = newEngine()

    // 데모가 끝나면 새 엔진에 실제 차량 상태를 다시 알려 주기 위해 마지막 값을 기억한다.
    private var lastRealSeats: SeatSignal? = null
    private var lastRealGear: GearSignal? = null

    init {
        voice.start()
        vehicleSource.start()
        naviSource.start()
        restriction.start()
        light.checkCapability()
        scope.launch {
            while (isActive) {
                discardReportAtMs?.let { if (now() >= it) closeReport() }
                engine.tick(now())
                if (demo.isRunning) {
                    // 1초 단위로만 갱신해 화면이 불필요하게 자주 다시 그려지지 않게 한다.
                    val elapsed = ((now() - demoStartMs) / 1000) * 1000
                    if (elapsed != _ui.value.demoElapsedMs) _ui.update { it.copy(demoElapsedMs = elapsed) }
                }
                delay(TICK_MS)
            }
        }
    }

    fun toggleDemo() {
        if (demo.isRunning) {
            // 중간에 멈춘 체험은 리포트 대상이 아니다. 트립을 버리고 정차 화면으로 돌아간다.
            demo.stop()
            engine.cancelTrip(now())
            log("체험 주행 중지 · 리포트 없음")
            onDemoFinished()
        } else {
            // 케어가 꺼져 있으면 체험해도 소리·조명·리포트가 나오지 않으므로, 체험을 시작할 때 켠다(스위치에도 보인다).
            if (!_ui.value.engine.careEnabled) {
                setCareEnabled(true)
                log("체험 주행을 위해 멀미 케어를 켬")
            }
            engine = newEngine()
            light.checkCapability()
            demoStartMs = now()
            closeReport()
            _ui.update { it.copy(demoRunning = true, demoElapsedMs = 0, comfort = null) }
            log("체험 주행 시작 · 끝날 때까지 실제 차량·내비 신호는 반영하지 않음")
            demo.start()
        }
    }

    fun setCareEnabled(enabled: Boolean) {
        engine.careEnabled = enabled
        prefs.edit().putBoolean(KEY_CARE, enabled).apply()
    }

    fun setLeadTime(seconds: Double) {
        engine.leadTimeSec = seconds
        _ui.update { it.copy(leadTimeSec = engine.leadTimeSec) }
        prefs.edit().putFloat(KEY_LEAD, engine.leadTimeSec.toFloat()).apply()
    }

    fun setSensitiveProfile(enabled: Boolean) {
        _ui.update { it.copy(sensitiveProfile = enabled) }
        engine.sensitiveProfile = enabled
        prefs.edit().putBoolean(KEY_SENSITIVE, enabled).apply()
    }

    fun setVoiceForecast(enabled: Boolean) {
        engine.voiceForecastEnabled = enabled
        _ui.update { it.copy(voiceForecast = enabled) }
        prefs.edit().putBoolean(KEY_VOICE, enabled).apply()
    }

    fun reportSymptom(severity: Severity) {
        val t = now()
        // 흔들리는 차에서 같은 버튼이 두 번 눌려도 환기·풍량 증가가 두 번 나가지 않게 한다. 더 심한 단계는 바로 받는다.
        val last = lastSymptom
        if (last != null && severity <= last && t - lastSymptomMs < SYMPTOM_DEBOUNCE_MS) return
        lastSymptomMs = t
        lastSymptom = severity
        log("증상 신고 · ${symptomTitle(severity)}")
        engine.reportSymptom(severity, t)
        _ui.update { it.copy(symptomSeq = it.symptomSeq + 1) }
    }

    /** 음성으로 한 줄 알린다(도착 안내 등). */
    fun announce(text: String) {
        log("음성 안내 · $text")
        voice.say(text)
    }

    fun dismissComfort() {
        _ui.update { it.copy(comfort = null) }
    }

    override fun onCue(cue: Cue) {
        audio.play(cue)
        light.blink()
        val m = cue.motion
        log("예고 · ${m.label} (${directionLabel(m.direction)}, ${leadLabel(m.secondsAhead)}) 세기 ${(cue.intensity * 100).toInt()}%")
    }

    override fun onSuppressed(motion: PredictedMotion, reason: CueDecision.Reason) {
        log("억제 · ${motion.label} — ${reason.label}")
    }

    override fun onComfort(trigger: ComfortTrigger, actions: List<ComfortAction>) {
        val items = mutableListOf<ComfortItem>()
        pendingComfort.clear()
        actions.forEach { action ->
            items += comfortItem(action, ComfortStatus.PENDING)
            pendingComfort += action
        }
        val severity = (trigger as? ComfortTrigger.Symptom)?.severity
        if (severity != null) {
            items += ComfortItem(ComfortKind.SENSITIVE, ComfortStatus.DONE, "30분 동안 작은 움직임도 미리 알려요")
            pendingComfort += null
        }
        val title = when (trigger) {
            is ComfortTrigger.Forecast -> "${trigger.text} 예보"
            is ComfortTrigger.Symptom -> symptomTitle(trigger.severity)
        }
        _ui.update { it.copy(comfort = ComfortFeed(title, clock(), severity, items)) }
        comfort.execute(actions)
    }

    private fun onComfortResult(action: ComfortAction, executed: Boolean) {
        val index = pendingComfort.indexOf(action)
        if (index < 0) return
        pendingComfort[index] = null
        val status = when {
            // 음성 안내·휴식 권장은 말로 전한 것 자체가 완료다.
            action is ComfortAction.Say || action is ComfortAction.SuggestRestStop -> ComfortStatus.DONE
            executed -> ComfortStatus.DONE
            else -> ComfortStatus.SUGGESTED
        }
        _ui.update { s ->
            val feed = s.comfort ?: return@update s
            if (index >= feed.items.size) return@update s
            s.copy(comfort = feed.copy(items = feed.items.toMutableList().also { it[index] = comfortItem(action, status) }))
        }
    }

    override fun onTripEnded(report: TripReport) {
        discardReportAtMs = null
        ReportRelay.current.value = report
        log("주행 종료 · 트립 리포트")
    }

    override fun onTripSkipped(reason: String) {
        log("주행 종료 · 리포트 생략 ($reason)")
    }

    override fun onState(state: EngineState) {
        if (state.gear != lastGear) {
            lastGear = state.gear
            onGearChanged(state.gear)
        }
        _ui.update { it.copy(engine = state) }
    }

    /**
     * 다시 출발할 때(기어 D·R) 리포트를 정리한다. 리포트 화면은 주행 중 사용 대상이 아니다.
     * - 이미 본 리포트: 바로 닫는다.
     * - 아직 보지 않은 리포트: 주차 위치를 고치는 짧은 R·D 로는 버리지 않는다. 다시 P 에 두면 그대로 남고,
     *   새 주행이 1분을 넘기면 버린다. (도착 음성 안내 뒤 앱을 열었는데 리포트가 없는 일을 막는다.)
     * 정차 직후 차속 추정이 늦게 0으로 떨어지는 것과 헷갈리지 않도록 차속이 아닌 기어로 판단한다.
     */
    private fun onGearChanged(gear: Gear) {
        val report = ReportRelay.current.value ?: return
        when (gear) {
            Gear.DRIVE, Gear.REVERSE -> when {
                report === ReportRelay.opened -> closeReport()
                discardReportAtMs == null -> discardReportAtMs = now() + MotionCareEngine.MIN_REPORT_TRIP_MS
            }
            Gear.PARK -> discardReportAtMs = null
            else -> Unit
        }
    }

    private fun closeReport() {
        discardReportAtMs = null
        ReportRelay.current.value = null
    }

    private fun feed(signal: Signal, fromDemo: Boolean) {
        if (!fromDemo) {
            if (signal is SeatSignal) lastRealSeats = signal
            if (signal is GearSignal) lastRealGear = signal
            if (demo.isRunning) return
        }
        engine.onSignal(signal)
    }

    private fun onDemoFinished() {
        engine = newEngine()
        lastRealSeats?.let { engine.onSignal(it.copy(timeMs = now())) }
        lastRealGear?.let { engine.onSignal(it.copy(timeMs = now())) }
        // 체험 중의 도움 기록은 실제 주행 화면에 남기지 않는다(기록 목록에는 남는다).
        _ui.update { it.copy(demoRunning = false, demoElapsedMs = 0, comfort = null) }
        log("체험 주행 종료 · 실제 차량·내비 신호로 복귀")
    }

    /** 현재 설정을 그대로 가진 새 엔진. 데모와 실제 주행의 상태가 섞이지 않게 한다. */
    private fun newEngine(): MotionCareEngine {
        val s = _ui.value
        return MotionCareEngine(this).apply {
            leadTimeSec = s.leadTimeSec
            voiceForecastEnabled = s.voiceForecast
            sensitiveProfile = s.sensitiveProfile
            careEnabled = s.engine.careEnabled
        }
    }

    private fun log(line: String) {
        val stamped = "${LocalTime.now().format(TIME_FORMAT)}  $line"
        _ui.update { it.copy(log = (listOf(stamped) + it.log).take(LOG_LINES)) }
    }

    private fun clock(): String = LocalTime.now().format(CLOCK_FORMAT)

    private fun now() = SystemClock.elapsedRealtime()

    companion object {
        @Volatile
        private var instance: MotionZeroController? = null

        /**
         * 프로세스에 하나뿐인 컨트롤러. 프로세스와 수명이 같아 따로 정리하지 않는다(서비스가 끝나도 프로세스가 살아 있으면 그대로 쓴다).
         * SDK 연결을 메인 스레드에서 시작하므로 메인 스레드에서 부른다.
         */
        fun get(context: Context): MotionZeroController =
            instance ?: synchronized(this) {
                instance ?: MotionZeroController(context.applicationContext as Application).also { instance = it }
            }

        private const val TICK_MS = 100L
        private const val LOG_LINES = 12
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        private val CLOCK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private const val KEY_LEAD = "lead_time_sec"
        private const val KEY_SENSITIVE = "sensitive_profile"
        private const val KEY_VOICE = "voice_forecast"
        private const val KEY_CARE = "care_enabled"
        private const val SYMPTOM_DEBOUNCE_MS = 1_500L

        /** 완화 동작을 사람이 읽는 문장으로 바꾼다. */
        fun comfortItem(action: ComfortAction, status: ComfortStatus): ComfortItem = when (action) {
            is ComfortAction.Say -> ComfortItem(ComfortKind.VOICE, status, "음성 안내 · “${action.text}”")
            ComfortAction.FreshAir -> ComfortItem(
                ComfortKind.FRESH_AIR, status,
                when (status) {
                    ComfortStatus.PENDING -> "바깥 공기로 바꾸는 중"
                    ComfortStatus.DONE -> "바깥 공기로 바꿨어요"
                    ComfortStatus.SUGGESTED -> "운전자에게 바깥 공기로 바꿔 달라고 했어요"
                },
            )
            is ComfortAction.FanUp -> ComfortItem(
                ComfortKind.FAN, status,
                when (status) {
                    ComfortStatus.PENDING -> "바람을 세게 하는 중"
                    ComfortStatus.DONE -> "바람을 ${action.steps}단계 세게 했어요"
                    ComfortStatus.SUGGESTED -> "운전자에게 바람을 세게 해 달라고 했어요"
                },
            )
            is ComfortAction.OpenWindow -> ComfortItem(
                ComfortKind.WINDOW, status,
                when (status) {
                    ComfortStatus.PENDING -> "뒷창문을 여는 중"
                    ComfortStatus.DONE -> "뒷창문을 ${action.percent}% 열었어요"
                    ComfortStatus.SUGGESTED -> "운전자에게 창문을 조금 열어 달라고 했어요"
                },
            )
            ComfortAction.SuggestRestStop -> ComfortItem(ComfortKind.REST, status, "가까운 휴게소에서 15~30분 쉬어 가자고 안내했어요")
        }
    }
}

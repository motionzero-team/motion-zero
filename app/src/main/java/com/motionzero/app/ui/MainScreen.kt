package com.motionzero.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motionzero.app.ComfortFeed
import com.motionzero.app.ComfortStatus
import com.motionzero.app.MotionZeroController
import com.motionzero.app.UiState
import com.motionzero.app.core.CueDecider
import com.motionzero.app.core.DemoScenario
import com.motionzero.app.core.EngineState
import com.motionzero.app.core.Gear
import com.motionzero.app.core.Seat
import com.motionzero.app.core.Severity
import kotlin.math.abs
import kotlin.math.roundToInt

private val WIDE_LAYOUT_MIN_WIDTH = 1100.dp

/** 옆 창이 이 너비 이상이면 증상 버튼을 가로 3칸으로 둔다(Connect-S 1:1 ≈ 841dp). 더 좁으면(Connect-W 1/3 ≈ 520dp) 세로로 쌓는다. */
private val COMPANION_TILES_MIN_WIDTH = 640.dp
private val Gap = 24.dp
private val CompanionGap = 16.dp
private val LEAD_OPTIONS = listOf(1.0, 1.5, 2.0, 2.5, 3.0)

/**
 * 메인 화면.
 * - 넓은 창(앱 영역 2칸): 왼쪽은 "지금"(상태·예고·완화 동작), 오른쪽은 동승자 버튼과 설정
 * - 좁은 창(내비 옆 창)·주행 중: 동반 화면. 스크롤 없이 예고 · 굽은 길 예보 · 증상 버튼 · 도움 요약 · 체험 진행만 둔다
 * - 좁은 창·정차 중: 1열 스크롤. 체험 주행 버튼은 첫 화면 안에 보인다
 * 주행 중에도 한눈에 읽히도록 정적 글자와 색만 바꾸고 애니메이션은 쓰지 않는다.
 */
@Composable
fun MainScreen(vm: MotionZeroController, onShowNavigation: () -> Unit = {}) {
    val state by vm.ui.collectAsState()
    val c = Mz.colors
    BoxWithConstraints(Modifier.fillMaxSize().background(c.background).safeDrawingPadding()) {
        val wide = maxWidth >= WIDE_LAYOUT_MIN_WIDTH
        val companion = !wide && (state.drivingLayout || state.demoRunning || state.engine.tripActive)
        val tiles = maxWidth >= COMPANION_TILES_MIN_WIDTH
        Column(
            Modifier.fillMaxSize().padding(horizontal = if (wide) 28.dp else 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(if (companion) CompanionGap else 20.dp),
        ) {
            AppBar(state, compact = !wide, onCareChange = vm::setCareEnabled)
            if (companion) {
                CompanionLayout(state, vm, tiles)
            } else if (wide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Gap)) {
                    if (state.drivingLayout) {
                        // 주행 화면: "지금" 패널이 남는 높이를 모두 써서 예고 칸과 글자를 최대한 크게 보여 준다.
                        Column(Modifier.weight(1.2f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Gap)) {
                            NowSection(state, vm, Modifier.weight(1f), fill = true)
                            state.comfort?.let { ComfortSection(it, vm::dismissComfort) }
                        }
                    } else {
                        Column(
                            Modifier.weight(1.2f).fillMaxHeight().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(Gap),
                        ) { NowColumn(state, vm) }
                    }
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Gap),
                    ) { ActionColumn(state, vm, onShowNavigation) }
                }
            } else {
                // 좁은 창 · 정차 중: 시작 안내와 체험 버튼이 맨 위, 설정·좌석·기록은 아래로 스크롤.
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Gap),
                ) {
                    NowSection(state, vm)
                    state.comfort?.let { ComfortSection(it, vm::dismissComfort) }
                    if (!state.welcomeVisible && !state.engine.tripActive) DemoIntroSection(state, vm)
                    SymptomSection(vm)
                    SeatSection(state)
                    SettingsSection(state, vm)
                    DiagnosticsSection(state)
                }
            }
        }
    }
}

/** 왼쪽 열: 지금 무슨 일이 일어나는지. 정차 중에는 시작 안내 바로 아래에 "내비 옆에 두기" 안내를 한 번 둔다. */
@Composable
private fun ColumnScope.NowColumn(state: UiState, vm: MotionZeroController) {
    NowSection(state, vm)
    if (!state.drivingLayout && !state.demoRunning) SplitHint()
    state.comfort?.let { ComfortSection(it, vm::dismissComfort) }
    if (!state.drivingLayout) SeatSection(state)
}

/**
 * 내비 옆 창(동반 화면). 주행 중에는 스크롤 없이 한 화면에 다 보이게, 중요한 순서대로 쌓는다.
 * 체험 진행(맨 위 한 줄) → 지금 예고(남는 높이 전부) → 도움 요약(한 줄) → 증상 버튼(항상 보임).
 * 설정·좌석·기록은 두지 않는다(차가 멈추면 다시 보인다).
 */
@Composable
private fun ColumnScope.CompanionLayout(state: UiState, vm: MotionZeroController, tiles: Boolean) {
    if (state.demoRunning) CompanionDemo(state, vm)
    NowSection(state, vm, Modifier.weight(1f), fill = true, companion = true)
    state.comfort?.let { CompanionComfort(it, vm::dismissComfort) }
    CompanionSymptoms(vm, tiles)
}

/** 오른쪽 열: 누를 수 있는 것. 주행 화면에서는 설정 대신 좌석을 둔다. */
@Composable
private fun ColumnScope.ActionColumn(state: UiState, vm: MotionZeroController, onShowNavigation: () -> Unit) {
    if (state.demoRunning) DemoSection(state, vm)
    // 주행 중 앱 영역 전체를 덮고 있으면 내비로 바로 돌아갈 길을 맨 위에 둔다(증상을 누르면 5초 뒤 저절로 돌아간다).
    if (state.drivingLayout || state.engine.tripActive) {
        TdsButton("내비로 돌아가기", onShowNavigation, modifier = Modifier.fillMaxWidth(), style = ButtonStyle.Weak, glyph = Glyph.NAVI)
    }
    if (state.restricted) LockedNote()
    SymptomSection(vm)
    if (state.drivingLayout) {
        SeatSection(state)
    } else {
        // 실제 주행 중(케어 꺼짐 등으로 주행 화면이 아닐 때)에는 체험을 시작해 실제 트립을 버리지 않게 한다.
        if (!state.demoRunning && !state.welcomeVisible && !state.engine.tripActive) DemoIntroSection(state, vm)
        SettingsSection(state, vm)
        DiagnosticsSection(state)
    }
}

/** 정차 중 첫 화면(시작 안내 + 체험 버튼)이 보이는지. 이때는 체험 섹션을 따로 두지 않는다. */
private val UiState.welcomeVisible: Boolean
    get() = engine.careEnabled && !engine.tripActive && !demoRunning

private fun leadSeconds(sec: Double): String = decimal(sec, 1).removeSuffix(".0")

// ───────────────────────── 앱 바 ─────────────────────────

@Composable
private fun AppBar(state: UiState, compact: Boolean, onCareChange: (Boolean) -> Unit) {
    val c = Mz.colors
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppMark(44.dp)
        Text("멀미 제로", style = Mz.type.title2, color = c.text, modifier = Modifier.weight(1f))
        // 좁은 창(앱 영역 1칸)에서는 연결 표시를 빼고 케어 스위치만 남긴다(연결 상태는 기록 섹션에 있음).
        if (!compact) {
            ConnectionStatus("차량", state.vehicleConnected)
            ConnectionStatus("내비", state.naviConnected)
            Spacer(Modifier.width(8.dp))
        }
        CareSwitch(state.engine.careEnabled, onCareChange)
    }
}

@Composable
private fun AppMark(size: Dp) {
    Canvas(Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(Color(0xFF0E2233))) {
        val w = this.size.width
        val p = Path().apply {
            moveTo(w * 0.31f, w * 0.70f)
            cubicTo(w * 0.31f, w * 0.52f, w * 0.69f, w * 0.56f, w * 0.69f, w * 0.37f)
        }
        drawPath(p, Color(0xFF4FC3F7), style = Stroke(width = w * 0.10f, cap = StrokeCap.Round))
        drawCircle(Color(0xFFFFC400), radius = w * 0.075f, center = Offset(w * 0.69f, w * 0.27f))
        drawCircle(Color.White, radius = w * 0.055f, center = Offset(w * 0.31f, w * 0.74f))
    }
}

@Composable
private fun ConnectionStatus(name: String, connected: Boolean) {
    val c = Mz.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Dot(if (connected) c.green else c.orange)
        Text(if (connected) "$name 연결됨" else "$name 연결 중", style = Mz.type.body2, color = c.textSub)
    }
}

/** 멀미 케어 전체 켜기/끄기. 앱 바에 있어 주행 중에도 동승자가 끌 수 있다. */
@Composable
private fun CareSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val c = Mz.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Row(
        Modifier
            .clip(TdsShape.button)
            .background(c.surface)
            .then(if (pressed) Modifier.background(c.pressed) else Modifier)
            .toggleable(value = on, interactionSource = source, indication = null, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 64.dp)
            .padding(start = 22.dp, end = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("멀미 케어", style = Mz.type.title3, color = c.text)
        TdsSwitch(on)
    }
}

// ───────────────────────── 지금 ─────────────────────────

/** 상태 한 줄: 정차·주행·체험 여부와 속도. */
private fun statusLine(state: UiState): String {
    val e = state.engine
    return when {
        state.demoRunning -> "체험 주행 · ${e.speedKph.roundToInt()} km/h"
        e.tripActive -> "주행 중 · ${gearLabel(e.gear)} · ${e.speedKph.roundToInt()} km/h"
        e.gear == Gear.UNKNOWN -> "차량 상태 확인 중"
        else -> "정차 중 · ${gearLabel(e.gear)}"
    }
}

/**
 * "지금" 섹션. fill 이면 남는 높이를 모두 쓰고(주행 화면), companion 이면 내비 옆 창에 맞춰
 * 예고 칸이 더 커질 수 있을 때 숫자 줄을 빼고 잠금 표시를 상태 줄에 붙인다.
 */
@Composable
private fun NowSection(
    state: UiState,
    vm: MotionZeroController,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
    companion: Boolean = false,
) {
    val e = state.engine
    val driving = e.careEnabled && (e.tripActive || state.demoRunning)
    Section(modifier, spacing = 28.dp, contentPadding = PaddingValues(SectionPadding)) {
        when {
            fill && driving -> BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val forecast = e.forecast != null
                val withStats = HeroSize.fitting(maxWidth, maxHeight, forecast, stats = true)
                // 옆 창에서는 예고 칸이 우선이다. 숫자 줄 때문에 칸이 Large 보다 작아지면 숫자 줄을 뺀다.
                val showStats = !companion || withStats.ordinal <= HeroSize.Large.ordinal
                val hero = if (showStats) withStats else HeroSize.fitting(maxWidth, maxHeight, forecast, stats = false)
                // 머리(상태) · 가운데(예고) · 바닥(숫자) 3단. 예고 칸은 남는 높이의 가운데에 둔다.
                Column(Modifier.fillMaxSize()) {
                    StatusRow(statusLine(state), e, compact = companion, locked = companion && state.restricted)
                    Spacer(Modifier.weight(1f).heightIn(min = 24.dp))
                    Driving(state, hero)
                    Spacer(Modifier.weight(1f).heightIn(min = 24.dp))
                    if (showStats) StatsRow(e)
                }
            }
            // 주행 화면인데 케어가 꺼져 있거나 아직 출발 전: 안내를 세로 가운데에 둔다.
            fill -> Column(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
            ) {
                if (e.careEnabled) Welcome(state, vm) else CareOff(state, vm)
            }
            !e.careEnabled -> CareOff(state, vm)
            !e.tripActive && !state.demoRunning -> Welcome(state, vm)
            else -> {
                Driving(state, HeroSize.Normal)
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatusRow(statusLine(state), e)
                    StatsRow(e)
                }
            }
        }
    }
}

private val HERO_TEXT_GAP = 32.dp

/** 예고 칸 크기와 제목 글자 크기. 주행 화면에서는 남는 높이와 너비(글자 칸 300dp 이상)에 맞춰 가장 큰 것을 고른다. */
private enum class HeroSize(val tile: Dp, val titleSp: Int) {
    Huge(320.dp, 72), Large(280.dp, 68), Medium(232.dp, 60), Normal(184.dp, 52), Small(144.dp, 40);

    companion object {
        private val STATUS = 36.dp
        private val STATS = 72.dp
        private val FORECAST = 128.dp
        private val SPACING = 48.dp
        private val TEXT_MIN = 300.dp

        fun fitting(width: Dp, height: Dp, hasForecast: Boolean, stats: Boolean): HeroSize {
            val rest = STATUS + SPACING + (if (stats) STATS else 0.dp) + (if (hasForecast) FORECAST else 0.dp)
            return entries.firstOrNull { it.tile + rest <= height && it.tile + HERO_TEXT_GAP + TEXT_MIN <= width } ?: Small
        }
    }
}

@Composable
private fun CareOff(state: UiState, vm: MotionZeroController) {
    val c = Mz.colors
    CueHero(
        tile = c.fill,
        glyph = Glyph.POWER,
        tint = c.textSub,
        badge = { Badge(statusLine(state), BadgeTone.Grey) },
        title = "멀미 케어가 꺼져 있어요",
        line1 = "켜면 다음 움직임을 ${leadSeconds(state.leadTimeSec)}초 먼저 알려 드려요.",
        line2 = "꺼져 있는 동안은 소리·조명 알림을 보내지 않아요.",
        titleStyle = Mz.type.title1,
    )
    BottomCta("멀미 케어 켜기", { vm.setCareEnabled(true) }, glyph = Glyph.POWER)
}

@Composable
private fun Welcome(state: UiState, vm: MotionZeroController) {
    val c = Mz.colors
    Top(
        upper = statusLine(state),
        title = "출발하면 알아서 시작해요",
        subtitle = "회전·제동·방지턱을 ${leadSeconds(state.leadTimeSec)}초 먼저 소리와 조명으로 알려 드려요.",
    )
    Column {
        ListRow(
            "도는 쪽에서 소리가 나요",
            description = "오른쪽으로 돌면 오른쪽 스피커에서 짧게 울려요",
            leading = { IconTile(Glyph.TURN_RIGHT, c.blue, c.blueWeak) },
        )
        ListRow(
            "실내 조명이 함께 켜져요",
            description = "화면을 보지 않아도 눈가로 알 수 있어요",
            leading = { IconTile(Glyph.LIGHT, c.blue, c.blueWeak) },
        )
        ListRow(
            "속이 안 좋으면 바로 환기해요",
            description = "버튼 한 번에 바깥 공기 · 바람 · 창문 10%",
            leading = { IconTile(Glyph.AIR, c.blue, c.blueWeak) },
        )
    }
    if (!state.drivingLayout) {
        BottomCta("체험 주행 시작 · 1분 50초", vm::toggleDemo, glyph = Glyph.PLAY)
    }
}

/** 주행 중 "지금": 마지막 예고를 큰 칸 하나와 큰 글자로. 빛 큐가 켜진 2초 동안 칸이 앰버색이 된다. */
@Composable
private fun Driving(state: UiState, hero: HeroSize) {
    val c = Mz.colors
    val e = state.engine
    val cue = e.lastCue
    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        if (cue == null) {
            CueHero(
                tile = c.fill,
                glyph = Glyph.WATCH,
                tint = c.textSub,
                badge = { Badge("살피는 중", BadgeTone.Grey) },
                // 좁은 창에서 "가는 / 중"으로 끊기지 않게 "가는 중"을 붙여 둔다.
                title = "편안하게 가는 중",
                line1 = "큰 움직임이 오면 ${leadSeconds(state.leadTimeSec)}초 먼저 알려요",
                line2 = "작은 흔들림은 알리지 않아요",
                size = hero,
            )
        } else {
            val m = cue.motion
            CueHero(
                tile = if (e.lightOn) c.light else c.blueWeak,
                glyph = motionGlyph(m.kind, m.direction),
                tint = if (e.lightOn) c.onLight else c.blue,
                badge = {
                    if (e.lightOn) Badge("지금 알리는 중", BadgeTone.Light, glyph = Glyph.LIGHT) else Badge("최근 알림", BadgeTone.Grey)
                },
                title = m.label,
                line1 = if (m.secondsAhead < 0.05) "바로 알렸어요" else "${leadLabel(m.secondsAhead)}에 알렸어요",
                line2 = cueChannels(m.direction),
                size = hero,
            )
        }
        e.forecast?.let { ForecastBanner(it) }
    }
}

@Composable
private fun CueHero(
    tile: Color,
    glyph: Glyph,
    tint: Color,
    badge: @Composable () -> Unit,
    title: String,
    line1: String,
    line2: String,
    size: HeroSize = HeroSize.Normal,
    titleStyle: TextStyle = Mz.type.glance.copy(fontSize = size.titleSp.sp, lineHeight = (size.titleSp * 1.2f).sp),
) {
    val c = Mz.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HERO_TEXT_GAP)) {
        Box(Modifier.size(size.tile).background(tile, RoundedCornerShape(size.tile * 0.24f)), contentAlignment = Alignment.Center) {
            GlyphIcon(glyph, tint, size.tile * 0.6f)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            badge()
            Text(title, style = titleStyle, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(line1, style = Mz.type.title3, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(line2, style = Mz.type.body1, color = c.textSub, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ForecastBanner(text: String) {
    val c = Mz.colors
    Row(
        Modifier.fillMaxWidth().background(c.orangeWeak, RoundedCornerShape(20.dp)).padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        GlyphIcon(Glyph.CURVE, c.orange, 40.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text, style = Mz.type.title3, color = c.text)
            Text("창밖 먼 곳을 봐 주세요. 바깥 공기를 미리 넣었어요.", style = Mz.type.body2, color = c.textSub)
        }
    }
}

/** 상태 한 줄(정차·주행·속도)과 민감 모드 배지. 옆 창에서 주행 규제 중이면 설정 잠김 배지를 붙인다. */
@Composable
private fun StatusRow(status: String, e: EngineState, compact: Boolean = false, locked: Boolean = false) {
    val c = Mz.colors
    Row(Modifier.heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(status, style = Mz.type.body1, color = c.textSub, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (e.sensitive) {
            Badge(if (compact) "민감 모드" else "민감 모드 · 기준 ${CueDecider.SENSITIVE_THRESHOLD} m/s²", BadgeTone.Blue, glyph = Glyph.SENSITIVE)
        }
        if (locked) Badge("설정 잠김", BadgeTone.Grey, glyph = Glyph.LOCK)
    }
}

/** 이번 주행 숫자 3개. 상자 없이 여백으로만 나눈다. */
@Composable
private fun StatsRow(e: EngineState) {
    Row {
        Stat("미리 알림", "${e.cueCount}회", Modifier.weight(1f), valueStyle = Mz.type.numberSmall)
        Stat("걸러낸 작은 움직임", "${e.suppressedCount}회", Modifier.weight(1f), valueStyle = Mz.type.numberSmall)
        Box(Modifier.weight(1f)) {
            e.remainingToTurnM?.let { Stat("다음 회전까지", "${it.roundToInt().coerceAtLeast(0)} m", valueStyle = Mz.type.numberSmall) }
        }
    }
}

// ───────────────────────── 완화 동작 ─────────────────────────

@Composable
private fun ComfortSection(feed: ComfortFeed, onClose: () -> Unit) {
    val c = Mz.colors
    Section(
        caption = "${feed.time} · ${feed.title}",
        title = "이렇게 도왔어요",
        trailing = { TdsButton("확인", onClose, style = ButtonStyle.Weak, modifier = Modifier.width(120.dp)) },
        spacing = 12.dp,
    ) {
        Column {
            feed.items.forEach { item ->
                ListRow(
                    item.text,
                    leading = { IconTile(comfortGlyph(item.kind), c.blue, c.blueWeak) },
                    trailing = { ComfortBadge(item.status) },
                    titleMaxLines = 3,
                )
            }
        }
    }
}

/**
 * 옆 창용 도움 요약 한 줄. 무엇을 했는지 목록 대신 몇 가지를 마쳤는지만 보여 주고, 확인으로 닫는다.
 * 하는 중인 동작이 있으면 그것을, 운전자에게 요청한 것이 있으면 그것을 먼저 알린다.
 */
@Composable
private fun CompanionComfort(feed: ComfortFeed, onClose: () -> Unit) {
    val c = Mz.colors
    val total = feed.items.size
    val done = feed.items.count { it.status == ComfortStatus.DONE }
    val pending = feed.items.count { it.status == ComfortStatus.PENDING }
    val asked = feed.items.count { it.status == ComfortStatus.SUGGESTED }
    val title = when {
        pending > 0 -> "도움 ${total}가지 중 ${done}가지 마쳤어요"
        asked > 0 -> "운전자에게 ${asked}가지를 요청했어요"
        else -> "도움 ${done}가지를 마쳤어요"
    }
    val (glyph, tint, tile) = when {
        pending > 0 -> Triple(Glyph.CLOCK, c.textSub, c.fill)
        asked > 0 -> Triple(Glyph.CHAT, c.orangeText, c.orangeWeak)
        else -> Triple(Glyph.CHECK, c.greenText, c.greenWeak)
    }
    Section(contentPadding = PaddingValues(start = 24.dp, end = 20.dp, top = 16.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconTile(glyph, tint, tile)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = Mz.type.title3, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${feed.time} · ${feed.title}", style = Mz.type.body2, color = c.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TdsButton("확인", onClose, style = ButtonStyle.Weak, modifier = Modifier.width(96.dp))
        }
    }
}

@Composable
private fun ComfortBadge(status: ComfortStatus) {
    when (status) {
        ComfortStatus.DONE -> Badge("완료", BadgeTone.Green, glyph = Glyph.CHECK)
        ComfortStatus.PENDING -> Badge("하는 중", BadgeTone.Grey)
        ComfortStatus.SUGGESTED -> Badge("운전자에게 요청", BadgeTone.Orange, glyph = Glyph.CHAT)
    }
}

// ───────────────────────── 동승자 ─────────────────────────

@Composable
private fun SymptomSection(vm: MotionZeroController) {
    Section(
        title = "동승자가 불편할 때",
        description = "누르면 바로 도와 드려요. 운전자는 조작하지 않아도 돼요.",
        spacing = 20.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Severity.entries.forEach { severity -> SymptomButton(severity) { vm.reportSymptom(severity) } }
        }
    }
}

/**
 * 옆 창용 증상 버튼. 주행 중 언제나 보이도록 화면 맨 아래에 고정한다.
 * 창이 넉넉하면(≥640dp) 가로 3칸 타일, 좁으면 설명 없이 세로 3줄.
 */
@Composable
private fun CompanionSymptoms(vm: MotionZeroController, tiles: Boolean) {
    Section(title = "동승자가 불편할 때", spacing = 16.dp, contentPadding = PaddingValues(24.dp)) {
        if (tiles) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Severity.entries.forEach { severity ->
                    SymptomTile(severity, Modifier.weight(1f)) { vm.reportSymptom(severity) }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Severity.entries.forEach { severity -> SymptomButton(severity, compact = true) { vm.reportSymptom(severity) } }
            }
        }
    }
}

@Composable
private fun symptomPress(onClick: () -> Unit): () -> Unit {
    val view = LocalView.current
    return {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        onClick()
    }
}

/** 단계 막대 칸. 심함만 빨강으로 칠한다. */
@Composable
private fun SymptomLevel(severity: Severity, size: Dp) {
    val c = Mz.colors
    Box(Modifier.size(size).background(c.surface, RoundedCornerShape(size * 0.29f)), contentAlignment = Alignment.Center) {
        LevelBars(
            level = severity.ordinal + 1,
            color = if (severity == Severity.SEVERE) c.red else c.text,
            empty = c.fillStrong,
            size = size * 0.5f,
        )
    }
}

@Composable
private fun SymptomTile(severity: Severity, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Mz.colors
    Pressable(
        onClick = symptomPress(onClick),
        color = c.fill,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.heightIn(min = 120.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SymptomLevel(severity, 48.dp)
            Text(symptomTitle(severity), style = Mz.type.title3, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SymptomButton(severity: Severity, compact: Boolean = false, onClick: () -> Unit) {
    val c = Mz.colors
    Pressable(
        onClick = symptomPress(onClick),
        color = c.fill,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = if (compact) 76.dp else 96.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = if (compact) 12.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SymptomLevel(severity, if (compact) 48.dp else 56.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(symptomTitle(severity), style = Mz.type.title3, color = c.text)
                if (!compact) Text(symptomHint(severity), style = Mz.type.body2, color = c.textSub)
            }
        }
    }
}

// ───────────────────────── 좌석 ─────────────────────────

@Composable
private fun SeatSection(state: UiState) {
    val c = Mz.colors
    val seats = state.engine.seats
    val cared = seats?.let { s -> Seat.entries.filter { it != Seat.DRIVER && it in s } }.orEmpty()
    val (headline, detail, note) = when {
        seats == null -> Triple("좌석 정보를 기다리는 중이에요", "그동안은 동승자가 있다고 보고 알려요.", null)
        cared.isEmpty() -> Triple("운전자만 타고 있어요", "동승자가 없으면 알리지 않아요.", null)
        else -> Triple("${cared.size}명을 챙기고 있어요", cared.joinToString(" · ") { it.label }, "운전석에는 알리지 않아요.")
    }
    val summary = @Composable {
        Text(headline, style = Mz.type.title3, color = c.text)
        Text(detail, style = Mz.type.body1, color = c.textSub)
        note?.let { Text(it, style = Mz.type.body2, color = c.caption) }
    }
    Section(title = "탑승 좌석") {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // 좁은 옆 창(1/3)에서는 배치도 아래에 글을 둔다.
            if (maxWidth < SEAT_ROW_MIN_WIDTH) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    SeatMap(seats)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { summary() }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    SeatMap(seats)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { summary() }
                }
            }
        }
    }
}

private val SEAT_ROW_MIN_WIDTH = 480.dp

/** 위에서 본 차와 좌석(왼쪽 앞이 운전석). 탑승한 동승자 자리는 파랑, 빈자리는 흰색. */
@Composable
private fun SeatMap(seats: Set<Seat>?) {
    val c = Mz.colors
    val described = seats?.let { s -> Seat.entries.filter { it != Seat.DRIVER && it in s } }?.joinToString(", ") { it.label }
    Column(
        Modifier
            .width(196.dp)
            .background(c.fill, RoundedCornerShape(topStart = 84.dp, topEnd = 84.dp, bottomStart = 40.dp, bottomEnd = 40.dp))
            .drawBehind {
                // 앞 유리(위)와 뒷유리(아래)를 둥근 선 하나씩으로 그려 차의 앞뒤를 알린다.
                val w = size.width
                val h = size.height
                val front = Path().apply {
                    moveTo(w * 0.22f, 52.dp.toPx())
                    quadraticTo(w * 0.5f, 22.dp.toPx(), w * 0.78f, 52.dp.toPx())
                }
                drawPath(front, c.fillStrong, style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round))
                val rear = Path().apply {
                    moveTo(w * 0.30f, h - 20.dp.toPx())
                    quadraticTo(w * 0.5f, h - 10.dp.toPx(), w * 0.70f, h - 20.dp.toPx())
                }
                drawPath(rear, c.fillStrong, style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round))
            }
            .padding(start = 16.dp, end = 16.dp, top = 68.dp, bottom = 36.dp)
            .semantics { contentDescription = if (described.isNullOrEmpty()) "좌석 배치도: 동승자 없음" else "좌석 배치도: $described 탑승" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            SeatBox(Seat.DRIVER, seats, 66.dp, 72.dp)
            SeatBox(Seat.FRONT_PASSENGER, seats, 66.dp, 72.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SeatBox(Seat.REAR_LEFT, seats, 52.dp, 68.dp)
            SeatBox(Seat.REAR_CENTER, seats, 42.dp, 68.dp)
            SeatBox(Seat.REAR_RIGHT, seats, 52.dp, 68.dp)
        }
    }
}

@Composable
private fun SeatBox(seat: Seat, seats: Set<Seat>?, width: Dp, height: Dp) {
    val c = Mz.colors
    val occupied = seats?.contains(seat) == true
    val bg = when {
        seat == Seat.DRIVER -> c.seatDriver
        occupied -> c.seatOn
        else -> c.surface
    }
    Box(Modifier.size(width, height).background(bg, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        when {
            seat == Seat.DRIVER -> GlyphIcon(Glyph.WHEEL, c.textSub, 30.dp)
            occupied -> GlyphIcon(Glyph.PERSON, c.thumb, 30.dp)
        }
    }
}

// ───────────────────────── 체험 주행 ─────────────────────────

@Composable
private fun DemoSection(state: UiState, vm: MotionZeroController) {
    val c = Mz.colors
    Section(
        caption = "체험 주행 중",
        title = DemoScenario.sceneAt(state.demoElapsedMs),
        trailing = {
            Text(
                "${clockLabel(state.demoElapsedMs)} / ${clockLabel(DemoScenario.DURATION_MS)}",
                style = Mz.type.title3.copy(fontFeatureSettings = "tnum"),
                color = c.textSub,
            )
        },
        spacing = 16.dp,
    ) {
        ProgressBar(state.demoElapsedMs.toFloat() / DemoScenario.DURATION_MS)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "서울→강릉 가족 여행 · 뒷좌석 7세·4세",
                style = Mz.type.body2,
                color = c.caption,
                modifier = Modifier.weight(1f),
            )
            TdsButton("체험 그만하기", vm::toggleDemo, style = ButtonStyle.Tertiary, glyph = Glyph.STOP)
        }
    }
}

/** 옆 창용 체험 주행 한 줄: 장면 · 경과 시간 · 그만하기, 그 아래 진행 막대. */
@Composable
private fun CompanionDemo(state: UiState, vm: MotionZeroController) {
    val c = Mz.colors
    Section(spacing = 14.dp, contentPadding = PaddingValues(start = 24.dp, end = 20.dp, top = 18.dp, bottom = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "체험 주행 · ${clockLabel(state.demoElapsedMs)} / ${clockLabel(DemoScenario.DURATION_MS)}",
                    style = Mz.type.body2.copy(fontFeatureSettings = "tnum"),
                    color = c.caption,
                    maxLines = 1,
                )
                Text(
                    DemoScenario.sceneAt(state.demoElapsedMs),
                    style = Mz.type.title3,
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TdsButton("그만하기", vm::toggleDemo, style = ButtonStyle.Tertiary, glyph = Glyph.STOP)
        }
        ProgressBar(state.demoElapsedMs.toFloat() / DemoScenario.DURATION_MS)
    }
}

/**
 * 넓은 창 · 정차 중에 한 번 보여 주는 안내. 출발하면 화면을 내비에게 돌려주고 빛·소리로만 알린다는 것,
 * 증상은 아래쪽 앱 아이콘으로 열어 알리고 5초 뒤 내비로 돌아간다는 것, 화면에 두고 싶으면 창 막대(⋮)로 내비 옆에 둘 수 있다는 것을 알려 준다.
 * (Pleos 는 일반 앱의 알림을 화면에 띄우지 않으므로 알림 버튼은 안내하지 않는다.)
 */
@Composable
private fun SplitHint() {
    val c = Mz.colors
    // 창 막대 기호(⋮)는 본문 글자로는 너무 가늘어서 굵고 크게 그려 화면 오른쪽 끝의 막대와 같은 모양으로 보이게 한다.
    val body = buildAnnotatedString {
        append("출발하면 화면을 내비에게 돌려주고 빛·소리로만 알려요. 아이가 불편해하면 아래쪽 멀미 제로 아이콘을 눌러 알려 주세요. 5초 뒤 내비로 돌아가요. 계속 두고 싶으면 오른쪽 끝 ")
        withStyle(SpanStyle(color = c.text, fontWeight = FontWeight.W700, fontSize = 24.sp)) { append("⋮") }
        append(" 막대를 끌어 내비 옆에 두세요.")
    }
    Section(contentPadding = PaddingValues(horizontal = SectionPadding, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconTile(Glyph.NAVI, c.blue, c.blueWeak)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("주행 중에는 내비가 화면 전체를 써요", style = Mz.type.title3, color = c.text)
                Text(body, style = Mz.type.body2, color = c.caption, maxLines = 4)
            }
        }
    }
}

/** 정차 중 첫 화면이 아닐 때(케어 꺼짐 등) 체험 주행으로 들어가는 길. 주요 동작이 따로 있으므로 옅은 버튼. */
@Composable
private fun DemoIntroSection(state: UiState, vm: MotionZeroController) {
    Section(
        title = "체험 주행",
        description = "실제 차 없이 서울→강릉 가족 여행을 1분 50초로 체험해요. 소리·조명·환기·트립 리포트가 그대로 동작해요.",
    ) {
        TdsButton(
            "체험 주행 시작",
            vm::toggleDemo,
            modifier = Modifier.fillMaxWidth(),
            style = if (state.engine.careEnabled) ButtonStyle.Primary else ButtonStyle.Weak,
            glyph = Glyph.PLAY,
        )
    }
}

// ───────────────────────── 설정 ─────────────────────────

@Composable
private fun SettingsSection(state: UiState, vm: MotionZeroController) {
    val c = Mz.colors
    Section(title = "설정", spacing = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("알림 시점", style = Mz.type.body1, color = c.textStrong)
                Text("움직이기 몇 초 전에 알릴까요? 기본은 2초예요.", style = Mz.type.body2, color = c.caption)
            }
            SegmentedControl(
                options = LEAD_OPTIONS.map { "${leadSeconds(it)}초" },
                selectedIndex = LEAD_OPTIONS.indexOfFirst { abs(state.leadTimeSec - it) < 0.01 },
                onSelect = { vm.setLeadTime(LEAD_OPTIONS[it]) },
            )
        }
        Column {
            ListRow(
                "민감한 동승자",
                description = "작은 움직임도 알려요 · 기준 ${CueDecider.NORMAL_THRESHOLD} → ${CueDecider.SENSITIVE_THRESHOLD} m/s²",
                leading = { IconTile(Glyph.SENSITIVE, c.blue, c.blueWeak) },
                trailing = { TdsSwitch(state.sensitiveProfile) },
                toggle = state.sensitiveProfile to vm::setSensitiveProfile,
            )
            ListRow(
                "굽은 길 음성 예보",
                description = "굽은 길이 시작되기 전에 말로 알려요",
                leading = { IconTile(Glyph.VOICE, c.blue, c.blueWeak) },
                trailing = { TdsSwitch(state.voiceForecast) },
                toggle = state.voiceForecast to vm::setVoiceForecast,
            )
        }
    }
}

@Composable
private fun LockedNote() {
    val c = Mz.colors
    Section(contentPadding = PaddingValues(horizontal = SectionPadding, vertical = 16.dp)) {
        ListRow(
            "주행 중에는 설정을 잠가 두었어요",
            description = "차가 멈추면 설정과 기록을 다시 볼 수 있어요.",
            leading = { IconTile(Glyph.LOCK, c.textSub, c.fill) },
            titleStyle = Mz.type.title3,
            titleColor = c.text,
        )
    }
}

// ───────────────────────── 연결 상태·기록 ─────────────────────────

@Composable
private fun DiagnosticsSection(state: UiState) {
    val c = Mz.colors
    var open by rememberSaveable { mutableStateOf(false) }
    Section(contentPadding = PaddingValues(horizontal = SectionPadding, vertical = 12.dp), spacing = 8.dp) {
        ListRow(
            "연결 상태 · 기록",
            description = "Pleos SDK 연결과 최근 동작을 확인해요",
            leading = { IconTile(Glyph.CLOCK, c.textSub, c.fill) },
            trailing = { GlyphIcon(if (open) Glyph.CHEVRON_UP else Glyph.CHEVRON_DOWN, c.iconMuted, 28.dp) },
            onClick = { open = !open },
        )
        if (open) {
            ListRow("차량 SDK", description = state.vehicleStatus, leading = { IconTile(Glyph.CAR, c.textSub, c.fill) })
            ListRow("내비 SDK", description = state.naviStatus, leading = { IconTile(Glyph.NAVI, c.textSub, c.fill) })
            ListRow(
                "주행 규제",
                description = if (state.restricted) "주행 중 · 화면을 줄였어요" else "정차 · 전체 화면",
                leading = { IconTile(Glyph.LOCK, c.textSub, c.fill) },
            )
            Column(Modifier.padding(top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("최근 기록", style = Mz.type.body1, color = c.textStrong)
                if (state.log.isEmpty()) {
                    Text("아직 기록이 없어요.", style = Mz.type.body2, color = c.caption)
                } else {
                    state.log.forEach { Text(it, style = Mz.type.body2.copy(fontFeatureSettings = "tnum"), color = c.caption) }
                }
            }
        }
    }
}

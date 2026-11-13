package com.motionzero.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * 색 토큰. 회색 배경 위에 흰 섹션을 올리고, 위계는 테두리·그림자 대신 글자 크기·굵기·여백으로 만든다.
 * 강조색은 파랑 하나만 쓰고, 빨강·초록·주황은 의미가 있을 때(증상 심함·완료·요청)만 쓴다.
 * light 는 빛 큐 색(앰버)으로, 예고가 나가는 2초 동안만 쓴다.
 *
 * 글자색은 쓰이는 배경과의 대비가 4.5:1 이상이 되도록 골랐다(Pleos UI 가이드).
 * - caption 은 흰 섹션(surface) 위에서만 쓴다. 회색 배경·회색 버튼 위에서는 textSub 를 쓴다.
 * - 파랑 글자·채움 버튼은 blue(#3182F6) 대신 더 짙은 blueText/primary 를 쓴다(흰 글자 3.7:1 → 5.4:1).
 */
@Immutable
data class MzColors(
    /** 화면 바탕(가장 아래 층). */
    val background: Color,
    /** 흰 섹션. */
    val surface: Color,
    /** 섹션 안의 회색 채움(보조 버튼, 세그먼트 트랙, 증상 버튼). */
    val fill: Color,
    /** 진행 막대 트랙, 운전석 등 한 단계 진한 채움. */
    val fillStrong: Color,
    val text: Color,
    val textStrong: Color,
    val textSub: Color,
    val caption: Color,
    val iconMuted: Color,
    /** 아이콘·스위치·진행 막대용 파랑(글자에는 쓰지 않는다). */
    val blue: Color,
    val blueText: Color,
    val blueWeak: Color,
    /** 채움 버튼 바탕. */
    val primary: Color,
    val onPrimary: Color,
    val light: Color,
    val onLight: Color,
    val red: Color,
    val redText: Color,
    val redWeak: Color,
    /** 흰 글자를 올리는 빨강 채움(리포트 증상 표시). */
    val redStrong: Color,
    val green: Color,
    val greenText: Color,
    val greenWeak: Color,
    val orange: Color,
    val orangeText: Color,
    val orangeWeak: Color,
    val switchOff: Color,
    val thumb: Color,
    val seatOn: Color,
    val seatDriver: Color,
    /** 세그먼트 컨트롤 트랙과 선택된 칸. */
    val segmentTrack: Color,
    val segmentThumb: Color,
    /** 누르는 동안 덮는 색. */
    val pressed: Color,
    val isDark: Boolean,
)

val DayColors = MzColors(
    background = Color(0xFFF2F4F6),
    surface = Color(0xFFFFFFFF),
    fill = Color(0xFFF2F4F6),
    fillStrong = Color(0xFFE5E8EB),
    text = Color(0xFF191F28),
    textStrong = Color(0xFF333D4B),
    textSub = Color(0xFF4E5968),
    caption = Color(0xFF6B7684),
    iconMuted = Color(0xFF8B95A1),
    blue = Color(0xFF3182F6),
    blueText = Color(0xFF1B64DA),
    blueWeak = Color(0xFFE8F3FF),
    primary = Color(0xFF1B64DA),
    onPrimary = Color(0xFFFFFFFF),
    light = Color(0xFFFFC342),
    onLight = Color(0xFF191F28),
    red = Color(0xFFF04452),
    redText = Color(0xFFD22030),
    redWeak = Color(0xFFFFEEEE),
    redStrong = Color(0xFFD22030),
    green = Color(0xFF03B26C),
    greenText = Color(0xFF027648),
    greenWeak = Color(0xFFF0FAF6),
    orange = Color(0xFFE45600),
    orangeText = Color(0xFFB04600),
    orangeWeak = Color(0xFFFFF3E0),
    switchOff = Color(0xFFB0B8C1),
    thumb = Color(0xFFFFFFFF),
    seatOn = Color(0xFF3182F6),
    seatDriver = Color(0xFFE5E8EB),
    segmentTrack = Color(0xFFE5E8EB),
    segmentThumb = Color(0xFFFFFFFF),
    pressed = Color(0x14191F28),
    isDark = false,
)

val NightColors = MzColors(
    background = Color(0xFF17171C),
    surface = Color(0xFF202027),
    fill = Color(0xFF2C2C35),
    fillStrong = Color(0xFF3C3C47),
    text = Color(0xFFEDEDF0),
    textStrong = Color(0xFFD9D9DE),
    textSub = Color(0xFFB5B5BD),
    caption = Color(0xFF9E9EA6),
    iconMuted = Color(0xFF7E7E87),
    blue = Color(0xFF4593FC),
    blueText = Color(0xFF6AABFF),
    blueWeak = Color(0xFF1C2A40),
    primary = Color(0xFF1B64DA),
    onPrimary = Color(0xFFFFFFFF),
    light = Color(0xFFFFC342),
    onLight = Color(0xFF191F28),
    red = Color(0xFFFF6B76),
    redText = Color(0xFFFF7A84),
    redWeak = Color(0xFF3A1E23),
    redStrong = Color(0xFFD22030),
    green = Color(0xFF3DD68C),
    greenText = Color(0xFF4ADE96),
    greenWeak = Color(0xFF15302A),
    orange = Color(0xFFFFA94D),
    orangeText = Color(0xFFFFB366),
    orangeWeak = Color(0xFF3A2A17),
    switchOff = Color(0xFF5A5A66),
    thumb = Color(0xFFFFFFFF),
    seatOn = Color(0xFF3182F6),
    seatDriver = Color(0xFF3C3C47),
    segmentTrack = Color(0xFF2C2C35),
    segmentThumb = Color(0xFF4D4D59),
    pressed = Color(0x1FFFFFFF),
    isDark = true,
)

/**
 * 글자 단계. 시스템 기본 서체(Pleos 에서는 Asta Sans)를 그대로 쓴다.
 * Asta Sans 는 600 → Bold, 700 → ExtraBold 로 그려진다. 화면·섹션 제목과 한눈에 읽는 글자·숫자는 700, 버튼·행 강조는 600.
 * 차 안에서 읽도록 본문은 19sp 이상, 한눈에 보는 글자는 34sp 이상.
 */
@Immutable
data class MzType(
    /** 지금 알리는 움직임 이름처럼 1초 안에 읽어야 하는 글자. */
    val glance: TextStyle,
    /** 화면·상태 제목. */
    val title1: TextStyle,
    /** 섹션 제목. */
    val title2: TextStyle,
    /** 버튼·강조한 행 제목. */
    val title3: TextStyle,
    /** 목록 행 제목·본문. */
    val body1: TextStyle,
    /** 설명·보조 문장. */
    val body2: TextStyle,
    /** 배지. */
    val label: TextStyle,
    /** 큰 숫자(리포트 KPI). */
    val number: TextStyle,
    /** 중간 숫자(주행 중 통계). */
    val numberSmall: TextStyle,
)

private const val TABULAR = "tnum"

/** 한국어 문장이 음절 중간("보세/요.")에서 끊기지 않게 어절 단위로 줄을 바꾼다(지원하는 OS 에서). */
private val KoreanLineBreak = LineBreak(
    strategy = LineBreak.Strategy.HighQuality,
    strictness = LineBreak.Strictness.Normal,
    wordBreak = LineBreak.WordBreak.Phrase,
)

/** 큰 제목은 두 줄이 될 때 줄 길이를 고르게 나눈다("편안하게 가는 / 중" 대신 "편안하게 / 가는 중"). */
private val TitleLineBreak = LineBreak(
    strategy = LineBreak.Strategy.Balanced,
    strictness = LineBreak.Strictness.Normal,
    wordBreak = LineBreak.WordBreak.Phrase,
)

val TdsType = MzType(
    glance = TextStyle(fontSize = 52.sp, lineHeight = 62.sp, fontWeight = FontWeight.W700, letterSpacing = (-0.02).em, lineBreak = TitleLineBreak),
    title1 = TextStyle(fontSize = 36.sp, lineHeight = 46.sp, fontWeight = FontWeight.W700, letterSpacing = (-0.02).em, lineBreak = TitleLineBreak),
    title2 = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.W700, letterSpacing = (-0.01).em, lineBreak = TitleLineBreak),
    title3 = TextStyle(fontSize = 23.sp, lineHeight = 31.sp, fontWeight = FontWeight.W600, lineBreak = KoreanLineBreak),
    body1 = TextStyle(fontSize = 21.sp, lineHeight = 30.sp, fontWeight = FontWeight.W400, lineBreak = KoreanLineBreak),
    body2 = TextStyle(fontSize = 19.sp, lineHeight = 27.sp, fontWeight = FontWeight.W400, lineBreak = KoreanLineBreak),
    label = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.W600),
    number = TextStyle(fontSize = 52.sp, lineHeight = 60.sp, fontWeight = FontWeight.W700, letterSpacing = (-0.02).em, fontFeatureSettings = TABULAR),
    numberSmall = TextStyle(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.W700, letterSpacing = (-0.01).em, fontFeatureSettings = TABULAR),
)

private val LocalMzColors = staticCompositionLocalOf { DayColors }

/** 현재 테마 색과 글자 단계. */
object Mz {
    val colors: MzColors
        @Composable @ReadOnlyComposable get() = LocalMzColors.current

    val type: MzType get() = TdsType
}

/** Material 컴포넌트가 기본값으로 쓸 글자 단계. 화면 코드는 Mz.type 을 쓴다. */
private val materialTypography = Typography(
    displaySmall = TdsType.title1,
    headlineSmall = TdsType.title2,
    titleLarge = TdsType.title2,
    titleMedium = TdsType.title3,
    bodyLarge = TdsType.body1,
    bodyMedium = TdsType.body2,
    labelLarge = TdsType.title3,
    labelMedium = TdsType.label,
)

/** 움직임 효과를 쓰지 않는다(물결 효과도 끔). 시스템 주간/야간 모드를 따른다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotionZeroTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) NightColors else DayColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.primary, onPrimary = c.onPrimary, background = c.background, onBackground = c.text,
            surface = c.surface, onSurface = c.text, surfaceContainerHigh = c.surface, onSurfaceVariant = c.textSub, outline = c.fillStrong,
        )
    } else {
        lightColorScheme(
            primary = c.primary, onPrimary = c.onPrimary, background = c.background, onBackground = c.text,
            surface = c.surface, onSurface = c.text, surfaceContainerHigh = c.surface, onSurfaceVariant = c.textSub, outline = c.fillStrong,
        )
    }
    MaterialTheme(colorScheme = scheme, typography = materialTypography) {
        CompositionLocalProvider(
            LocalMzColors provides c,
            LocalRippleConfiguration provides null,
            LocalContentColor provides c.text,
            LocalTextStyle provides TdsType.body1,
            content = content,
        )
    }
}

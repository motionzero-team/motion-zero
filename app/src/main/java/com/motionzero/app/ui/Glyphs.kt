package com.motionzero.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.motionzero.app.ComfortKind
import com.motionzero.app.core.Direction
import com.motionzero.app.core.MotionKind

/**
 * 앱에서 쓰는 아이콘. 외부 아이콘 라이브러리 없이 Canvas 로 그려 APK 를 가볍게 유지한다.
 * Pleos 아이콘 가이드의 48dp 그리드를 따른다: 사방 4dp(≈0.08) 여백, 선 두께 4dp(0.085), 둥근 끝·둥근 꺾임.
 * 모든 좌표는 0~1 비율이라 크기와 무관하게 같은 모양이 나온다.
 */
enum class Glyph {
    TURN_LEFT, TURN_RIGHT, LANE_LEFT, LANE_RIGHT, BRAKE, BUMP, CURVE, DOWNHILL,
    WATCH, VOICE, AIR, FAN, WINDOW, REST, SENSITIVE, PLAY, STOP, CHECK, CHAT, PERSON, WHEEL, LIGHT,
    CHEVRON_DOWN, CHEVRON_UP, LOCK, CAR, NAVI, CLOCK, POWER, SPLIT,
}

fun motionGlyph(kind: MotionKind, direction: Direction): Glyph = when (kind) {
    MotionKind.TURN -> if (direction == Direction.LEFT) Glyph.TURN_LEFT else Glyph.TURN_RIGHT
    MotionKind.LANE_CHANGE -> if (direction == Direction.LEFT) Glyph.LANE_LEFT else Glyph.LANE_RIGHT
    MotionKind.BRAKE -> Glyph.BRAKE
    MotionKind.BUMP -> Glyph.BUMP
    MotionKind.CURVE -> Glyph.CURVE
    MotionKind.DOWNHILL -> Glyph.DOWNHILL
}

fun comfortGlyph(kind: ComfortKind): Glyph = when (kind) {
    ComfortKind.VOICE -> Glyph.VOICE
    ComfortKind.FRESH_AIR -> Glyph.AIR
    ComfortKind.FAN -> Glyph.FAN
    ComfortKind.WINDOW -> Glyph.WINDOW
    ComfortKind.REST -> Glyph.REST
    ComfortKind.SENSITIVE -> Glyph.SENSITIVE
}

@Composable
fun GlyphIcon(glyph: Glyph, color: Color, size: Dp = 32.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawGlyph(glyph, color) }
}

private const val STROKE = 0.085f

private fun DrawScope.drawGlyph(g: Glyph, color: Color) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = w * STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun line(block: PathScope.() -> Unit) {
        val p = Path()
        PathScope(p, w, h).block()
        drawPath(p, color, style = stroke)
    }
    fun fill(block: PathScope.() -> Unit) {
        val p = Path()
        PathScope(p, w, h).block()
        drawPath(p, color)
    }
    val mirror = g == Glyph.TURN_LEFT || g == Glyph.LANE_LEFT
    fun x(v: Float) = if (mirror) 1f - v else v

    when (g) {
        Glyph.TURN_LEFT, Glyph.TURN_RIGHT -> {
            line { m(x(0.34f), 0.88f); l(x(0.34f), 0.54f); q(x(0.34f), 0.32f, x(0.56f), 0.32f); l(x(0.80f), 0.32f) }
            line { m(x(0.64f), 0.16f); l(x(0.80f), 0.32f); l(x(0.64f), 0.48f) }
        }
        Glyph.LANE_LEFT, Glyph.LANE_RIGHT -> {
            line { m(x(0.30f), 0.88f); l(x(0.30f), 0.70f); c(x(0.30f), 0.50f, x(0.70f), 0.52f, x(0.70f), 0.32f); l(x(0.70f), 0.14f) }
            line { m(x(0.55f), 0.29f); l(x(0.70f), 0.14f); l(x(0.85f), 0.29f) }
        }
        Glyph.BRAKE -> {
            line { m(0.26f, 0.16f); l(0.50f, 0.36f); l(0.74f, 0.16f) }
            line { m(0.26f, 0.42f); l(0.50f, 0.62f); l(0.74f, 0.42f) }
            line { m(0.20f, 0.84f); l(0.80f, 0.84f) }
        }
        Glyph.BUMP -> {
            line { m(0.08f, 0.76f); l(0.24f, 0.76f); q(0.50f, 0.18f, 0.76f, 0.76f); l(0.92f, 0.76f) }
            line { m(0.50f, 0.14f); l(0.50f, 0.26f) }
        }
        Glyph.CURVE -> {
            line { m(0.36f, 0.88f); c(0.36f, 0.62f, 0.68f, 0.66f, 0.66f, 0.42f); c(0.64f, 0.26f, 0.44f, 0.26f, 0.44f, 0.14f) }
            line { m(0.30f, 0.26f); l(0.44f, 0.12f); l(0.58f, 0.26f) }
        }
        Glyph.DOWNHILL -> {
            line { m(0.14f, 0.22f); l(0.84f, 0.70f) }
            line { m(0.60f, 0.74f); l(0.84f, 0.70f); l(0.80f, 0.46f) }
            line { m(0.14f, 0.86f); l(0.86f, 0.86f) }
        }
        Glyph.WATCH -> {
            drawCircle(color, radius = w * 0.34f, center = center, style = stroke)
            drawCircle(color, radius = w * 0.11f, center = center)
        }
        Glyph.VOICE -> {
            fill { m(0.12f, 0.38f); l(0.28f, 0.38f); l(0.48f, 0.20f); l(0.48f, 0.80f); l(0.28f, 0.62f); l(0.12f, 0.62f); close() }
            drawArc(color, -45f, 90f, false, Offset(w * 0.40f, h * 0.32f), Size(w * 0.28f, h * 0.36f), style = stroke)
            drawArc(color, -50f, 100f, false, Offset(w * 0.40f, h * 0.18f), Size(w * 0.48f, h * 0.64f), style = stroke)
        }
        Glyph.AIR -> {
            for (y in listOf(0.28f, 0.50f, 0.72f)) {
                line { m(0.12f, y); q(0.31f, y - 0.10f, 0.50f, y); q(0.69f, y + 0.10f, 0.88f, y) }
            }
        }
        Glyph.FAN -> {
            for (angle in listOf(0f, 120f, 240f)) {
                rotate(angle) {
                    fill { m(0.50f, 0.50f); c(0.40f, 0.34f, 0.40f, 0.10f, 0.56f, 0.10f); c(0.70f, 0.12f, 0.66f, 0.36f, 0.50f, 0.50f); close() }
                }
            }
            drawCircle(color, radius = w * 0.07f, center = center)
        }
        Glyph.WINDOW -> {
            drawRoundRect(color, Offset(w * 0.14f, h * 0.12f), Size(w * 0.72f, h * 0.76f), CornerRadius(w * 0.10f), style = stroke)
            line { m(0.14f, 0.38f); l(0.86f, 0.38f) }
            line { m(0.50f, 0.52f); l(0.50f, 0.74f) }
            line { m(0.40f, 0.64f); l(0.50f, 0.74f); l(0.60f, 0.64f) }
        }
        Glyph.REST -> {
            line { m(0.16f, 0.44f); l(0.20f, 0.84f); l(0.62f, 0.84f); l(0.66f, 0.44f); close() }
            drawArc(color, -90f, 180f, false, Offset(w * 0.56f, h * 0.50f), Size(w * 0.24f, h * 0.20f), style = stroke)
            line { m(0.32f, 0.32f); q(0.26f, 0.24f, 0.32f, 0.14f) }
            line { m(0.50f, 0.32f); q(0.44f, 0.24f, 0.50f, 0.14f) }
        }
        Glyph.SENSITIVE -> {
            fill {
                m(0.50f, 0.86f)
                c(0.16f, 0.62f, 0.06f, 0.42f, 0.18f, 0.26f)
                c(0.30f, 0.12f, 0.46f, 0.18f, 0.50f, 0.32f)
                c(0.54f, 0.18f, 0.70f, 0.12f, 0.82f, 0.26f)
                c(0.94f, 0.42f, 0.84f, 0.62f, 0.50f, 0.86f)
                close()
            }
        }
        Glyph.PLAY -> fill { m(0.30f, 0.16f); q(0.30f, 0.12f, 0.36f, 0.15f); l(0.84f, 0.46f); q(0.88f, 0.50f, 0.84f, 0.54f); l(0.36f, 0.85f); q(0.30f, 0.88f, 0.30f, 0.84f); close() }
        Glyph.STOP -> drawRoundRect(color, Offset(w * 0.22f, h * 0.22f), Size(w * 0.56f, h * 0.56f), CornerRadius(w * 0.10f))
        Glyph.CHECK -> line { m(0.18f, 0.52f); l(0.40f, 0.74f); l(0.82f, 0.30f) }
        Glyph.CHAT -> {
            line { m(0.14f, 0.20f); l(0.86f, 0.20f); l(0.86f, 0.66f); l(0.46f, 0.66f); l(0.28f, 0.84f); l(0.28f, 0.66f); l(0.14f, 0.66f); close() }
        }
        Glyph.PERSON -> {
            drawCircle(color, radius = w * 0.17f, center = Offset(w * 0.5f, h * 0.30f))
            fill { m(0.18f, 0.88f); c(0.18f, 0.56f, 0.82f, 0.56f, 0.82f, 0.88f); close() }
        }
        Glyph.WHEEL -> {
            drawCircle(color, radius = w * 0.34f, center = center, style = stroke)
            drawCircle(color, radius = w * 0.08f, center = center)
            line { m(0.18f, 0.50f); l(0.82f, 0.50f) }
            line { m(0.50f, 0.50f); l(0.50f, 0.84f) }
        }
        Glyph.LIGHT -> {
            drawCircle(color, radius = w * 0.18f, center = center)
            for (i in 0 until 8) {
                rotate(i * 45f) { line { m(0.50f, 0.08f); l(0.50f, 0.18f) } }
            }
        }
        Glyph.CHEVRON_DOWN -> line { m(0.24f, 0.38f); l(0.50f, 0.64f); l(0.76f, 0.38f) }
        Glyph.CHEVRON_UP -> line { m(0.24f, 0.62f); l(0.50f, 0.36f); l(0.76f, 0.62f) }
        Glyph.LOCK -> {
            drawRoundRect(color, Offset(w * 0.18f, h * 0.44f), Size(w * 0.64f, h * 0.44f), CornerRadius(w * 0.10f))
            line { m(0.32f, 0.44f); l(0.32f, 0.32f); c(0.32f, 0.08f, 0.68f, 0.08f, 0.68f, 0.32f); l(0.68f, 0.44f) }
        }
        Glyph.CAR -> {
            line { m(0.22f, 0.46f); l(0.30f, 0.24f); l(0.70f, 0.24f); l(0.78f, 0.46f) }
            drawRoundRect(color, Offset(w * 0.12f, h * 0.46f), Size(w * 0.76f, h * 0.28f), CornerRadius(w * 0.08f), style = stroke)
            line { m(0.26f, 0.76f); l(0.26f, 0.86f) }
            line { m(0.74f, 0.76f); l(0.74f, 0.86f) }
        }
        Glyph.NAVI -> fill { m(0.50f, 0.10f); l(0.84f, 0.86f); l(0.50f, 0.68f); l(0.16f, 0.86f); close() }
        Glyph.CLOCK -> {
            drawCircle(color, radius = w * 0.36f, center = center, style = stroke)
            line { m(0.50f, 0.30f); l(0.50f, 0.50f); l(0.64f, 0.60f) }
        }
        Glyph.POWER -> {
            drawArc(color, -55f, 290f, false, Offset(w * 0.16f, h * 0.18f), Size(w * 0.68f, h * 0.68f), style = stroke)
            line { m(0.50f, 0.10f); l(0.50f, 0.46f) }
        }
        // 화면 하나를 두 창으로 나눈 모양(왼쪽 창은 채움).
        Glyph.SPLIT -> {
            drawRoundRect(color, Offset(w * 0.10f, h * 0.20f), Size(w * 0.80f, h * 0.60f), CornerRadius(w * 0.10f), style = stroke)
            drawRoundRect(color, Offset(w * 0.20f, h * 0.30f), Size(w * 0.22f, h * 0.40f), CornerRadius(w * 0.04f))
            line { m(0.52f, 0.20f); l(0.52f, 0.80f) }
        }
    }
}

/** 0~1 비율 좌표로 Path 를 그리는 도우미. */
private class PathScope(private val p: Path, private val w: Float, private val h: Float) {
    fun m(x: Float, y: Float) = p.moveTo(x * w, y * h)
    fun l(x: Float, y: Float) = p.lineTo(x * w, y * h)
    fun q(cx: Float, cy: Float, x: Float, y: Float) = p.quadraticTo(cx * w, cy * h, x * w, y * h)
    fun c(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) = p.cubicTo(x1 * w, y1 * h, x2 * w, y2 * h, x * w, y * h)
    fun close() = p.close()
}

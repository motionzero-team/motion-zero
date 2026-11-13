package com.motionzero.app.ui

import com.motionzero.app.core.Direction
import com.motionzero.app.core.Gear
import com.motionzero.app.core.Severity
import java.util.Locale

fun directionLabel(direction: Direction): String = when (direction) {
    Direction.LEFT -> "왼쪽"
    Direction.RIGHT -> "오른쪽"
    Direction.CENTER -> "앞뒤"
}

/** 움직임보다 몇 초 먼저 알렸는지. */
fun leadLabel(secondsAhead: Double): String =
    if (secondsAhead < 0.05) "즉시" else String.format(Locale.KOREA, "%.1f초 전", secondsAhead)

fun gearLabel(gear: Gear): String = when (gear) {
    Gear.PARK -> "P"
    Gear.REVERSE -> "R"
    Gear.NEUTRAL -> "N"
    Gear.DRIVE -> "D"
    Gear.UNKNOWN -> "-"
}

fun durationLabel(ms: Long): String {
    val totalSec = ms / 1000
    val hour = totalSec / 3600
    val min = totalSec % 3600 / 60
    val sec = totalSec % 60
    return when {
        hour > 0 -> if (min > 0) "${hour}시간 ${min}분" else "${hour}시간"
        min > 0 -> "${min}분 ${sec}초"
        else -> "${sec}초"
    }
}

fun distanceLabel(meters: Double): String =
    if (meters < 1000) "${meters.toInt()} m" else String.format(Locale.KOREA, "%.1f km", meters / 1000)

fun decimal(value: Double, digits: Int = 2): String = String.format(Locale.KOREA, "%.${digits}f", value)

/** 증상 버튼 제목: 아이·부모가 실제로 하는 말로 쓴다. */
fun symptomTitle(severity: Severity): String = when (severity) {
    Severity.MILD -> "조금 어지러워요"
    Severity.MODERATE -> "속이 안 좋아요"
    Severity.SEVERE -> "토할 것 같아요"
}

/** 증상 버튼 아래 한 줄: 누르면 무엇을 하는지. */
fun symptomHint(severity: Severity): String = when (severity) {
    Severity.MILD -> "먼 곳을 보게 안내하고 더 일찍 알려요"
    Severity.MODERATE -> "바깥 공기 · 바람 · 창문 10%로 환기해요"
    Severity.SEVERE -> "환기하고 가까운 휴게소를 알려 드려요"
}

/** 예고 방식 설명(소리 방향 + 빛). */
fun cueChannels(direction: Direction): String = when (direction) {
    Direction.LEFT -> "왼쪽 소리 · 실내 조명"
    Direction.RIGHT -> "오른쪽 소리 · 실내 조명"
    Direction.CENTER -> "양쪽 소리 · 실내 조명"
}

fun clockLabel(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.KOREA, "%d:%02d", totalSec / 60, totalSec % 60)
}

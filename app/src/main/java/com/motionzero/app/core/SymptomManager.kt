package com.motionzero.app.core

/** 증상 대응 동작. 차량 제어는 지원 여부를 확인한 뒤 실행하고, 안 되면 Say로 안내한다. */
sealed interface ComfortAction {
    data class Say(val text: String) : ComfortAction
    data object FreshAir : ComfortAction
    data class FanUp(val steps: Int) : ComfortAction
    data class OpenWindow(val percent: Int) : ComfortAction
    data object SuggestRestStop : ComfortAction
}

/**
 * F4 증상 단계별 대응(기획서 3.2).
 * 1단계: 큐 기준을 낮추고 시선 안내
 * 2단계: 외기 유입·풍량 증가·창문 10%
 * 3단계: 휴게소에서 15~30분 휴식 권장, 재출발 후 2시간 민감 모드
 */
class SymptomManager {
    var reportCount = 0
        private set
    var highestSeverity: Severity? = null
        private set

    data class Response(val actions: List<ComfortAction>, val sensitiveUntilMs: Long)

    fun report(severity: Severity, nowMs: Long): Response {
        reportCount++
        val prev = highestSeverity
        if (prev == null || severity > prev) highestSeverity = severity

        val actions = mutableListOf<ComfortAction>()
        actions += ComfortAction.Say("앞 창밖 먼 곳을 봐 주세요. 움직임을 더 일찍 알려 드릴게요.")
        if (severity >= Severity.MODERATE) {
            actions += ComfortAction.FreshAir
            actions += ComfortAction.FanUp(steps = 2)
            actions += ComfortAction.OpenWindow(percent = 10)
        }
        if (severity == Severity.SEVERE) {
            actions += ComfortAction.SuggestRestStop
        }
        return Response(actions, nowMs + SYMPTOM_SENSITIVE_MS)
    }

    /** 휴식 후 재출발: 재노출 민감화가 최소 2시간 지속된다는 근거에 따라 민감 모드를 켠다. */
    fun onRestResume(nowMs: Long): Long = nowMs + REST_SENSITIVE_MS

    companion object {
        const val SYMPTOM_SENSITIVE_MS = 30 * 60 * 1000L
        const val REST_SENSITIVE_MS = 2 * 60 * 60 * 1000L
    }
}

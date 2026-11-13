package com.motionzero.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.motionzero.app.core.Direction
import com.motionzero.app.core.TripMark
import com.motionzero.app.core.TripReport

private val REPORT_WIDE_MIN_WIDTH = 1100.dp

/**
 * 도착(기어 P) 후 보여 주는 트립 리포트. TripReportActivity 가 앱 영역 전체(landscape)로 띄운다.
 * 왼쪽은 요약(판정·숫자·팁)과 확인 버튼, 오른쪽은 근거(타임라인·흔들림).
 * 판정 문장은 실제 기록(증상 신고, 급감속·급회전 횟수)만으로 정한다.
 */
@Composable
fun TripReportScreen(report: TripReport, onClose: () -> Unit) {
    val c = Mz.colors
    BoxWithConstraints(
        Modifier.fillMaxSize().background(c.background).safeDrawingPadding().padding(horizontal = 28.dp, vertical = 28.dp),
    ) {
        if (maxWidth >= REPORT_WIDE_MIN_WIDTH) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(0.85f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Summary(report)
                        CueBreakdown(report)
                    }
                    BottomCta("확인", onClose)
                }
                Column(
                    Modifier.weight(1.15f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    TimelineSection(report)
                    ShakeSection(report)
                    Tips(report)
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Summary(report)
                    CueBreakdown(report)
                    TimelineSection(report)
                    ShakeSection(report)
                    Tips(report)
                }
                BottomCta("확인", onClose)
            }
        }
    }
}

@Composable
private fun Summary(report: TripReport) {
    val c = Mz.colors
    val verdict = when {
        report.symptomReports > 0 -> "동승자가 불편함을 느꼈어요"
        report.hardBrakes + report.sharpTurns > 0 -> "흔들림이 조금 있었어요"
        else -> "편안한 주행이었어요"
    }
    val summary = buildString {
        append(if (report.totalCues > 0) "움직임 ${report.totalCues}번을 미리 알렸어요." else "미리 알릴 만큼 큰 움직임이 없었어요.")
        if (report.suppressedCount > 0) append(" 작은 움직임 ${report.suppressedCount}번은 알리지 않았어요.")
    }
    Top(
        upper = "트립 리포트",
        title = verdict,
        subtitle = summary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
    )
    Section(spacing = 32.dp, contentPadding = PaddingValues(horizontal = SectionPadding, vertical = 32.dp)) {
        Row {
            Stat("주행 시간", durationLabel(report.durationMs), Modifier.weight(1f))
            Stat("거리", distanceLabel(report.distanceM), Modifier.weight(1f))
        }
        Row {
            Stat("미리 알린 움직임", "${report.totalCues}회", Modifier.weight(1f), valueColor = c.blueText)
            Stat("걸러낸 작은 움직임", "${report.suppressedCount}회", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Tips(report: TripReport) {
    val c = Mz.colors
    Section(title = "다음 주행 팁", spacing = 16.dp) {
        report.coaching.forEach { tip ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                GlyphIcon(Glyph.CHECK, c.blue, 26.dp, Modifier.padding(top = 2.dp))
                Text(tip, style = Mz.type.body1, color = c.textStrong, maxLines = 3)
            }
        }
    }
}

@Composable
private fun TimelineSection(report: TripReport) {
    Section(title = "언제 알렸나요", spacing = 24.dp) {
        Timeline(report)
        Legend(report)
    }
}

@Composable
private fun ShakeSection(report: TripReport) {
    val c = Mz.colors
    Section(title = "흔들림", spacing = 8.dp) {
        ValueRow("급감속", "${report.hardBrakes}회")
        ValueRow("급회전", "${report.sharpTurns}회")
        ValueRow("증상 신고", "${report.symptomReports}회", valueColor = if (report.symptomReports > 0) c.redText else c.text)
        report.topSegments.firstOrNull()?.let { ValueRow("가장 흔들린 구간", "${it.index}~${it.index + 1}분") }
        ValueRow("멀미 유발 지수 (MSDV)", decimal(report.msdv))
        Text(
            "흔들림의 크기와 지속 시간을 함께 본 값이에요(ISO 2631-1). 낮을수록 편해요.",
            style = Mz.type.body2,
            color = c.caption,
        )
    }
}

/**
 * 0분부터 도착까지 가로축 위에 예고(파랑 칸)와 증상 신고(빨강 칸)를 시각대로 놓는다.
 * 칸이 겹치면 한 줄 위로 올리고, 긴 주행에서 [MAX_LANES] 줄을 넘는 예고 칸은 생략한다(축 위 점은 모두 찍고, 생략한 개수를 적는다).
 * 증상 신고 칸은 생략하지 않고 맨 윗줄에 겹쳐 그린다.
 */
@Composable
private fun Timeline(report: TripReport) {
    val c = Mz.colors
    val duration = report.durationMs.coerceAtLeast(1)
    val tile = 40.dp
    val laneGap = 8.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val usable = maxWidth - tile
        val placed = placeMarks(report.marks, usable, tile, duration)
        val tiles = placed.mapNotNull { p ->
            when {
                p.lane < MAX_LANES -> p
                p.mark.symptom != null -> PlacedMark(p.mark, p.x, MAX_LANES - 1)
                else -> null
            }
        }.sortedBy { it.mark.symptom != null } // 증상 칸을 나중에 그려 위에 오게 한다
        val hidden = placed.size - tiles.size
        val lanes = (tiles.maxOfOrNull { it.lane } ?: 0) + 1
        val marksHeight = tile * lanes + laneGap * (lanes - 1)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (tiles.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().height(marksHeight)) {
                    tiles.forEach { p ->
                        MarkTile(p.mark, Modifier.offset(x = p.x, y = (tile + laneGap) * (lanes - 1 - p.lane)).size(tile))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(8.dp)) {
                Box(Modifier.fillMaxWidth().height(4.dp).offset(y = 2.dp).background(c.fillStrong, CircleShape))
                placed.forEach { p ->
                    Box(
                        Modifier.offset(x = p.x + tile / 2 - 4.dp).size(8.dp)
                            .background(if (p.mark.symptom != null) c.redStrong else c.blue, CircleShape),
                    )
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Text("출발", style = Mz.type.body2, color = c.caption, modifier = Modifier.weight(1f))
                if (hidden > 0) {
                    Text("겹친 알림 ${hidden}개는 점으로 표시", style = Mz.type.body2, color = c.caption, modifier = Modifier.weight(1f))
                }
                Text("도착 ${clockLabel(report.durationMs)}", style = Mz.type.body2.copy(fontFeatureSettings = "tnum"), color = c.caption)
            }
        }
    }
}

private class PlacedMark(val mark: TripMark, val x: Dp, val lane: Int)

/** 시간축 위에 쌓는 칸의 최대 줄 수. 2시간 넘는 실제 주행에서도 리포트가 한 화면에 들어오게 한다. */
private const val MAX_LANES = 3

private fun placeMarks(marks: List<TripMark>, usable: Dp, tile: Dp, duration: Long): List<PlacedMark> {
    val laneEnds = mutableListOf<Dp>()
    return marks.map { mark ->
        val x = usable * (mark.offsetMs.toFloat() / duration).coerceIn(0f, 1f)
        var lane = laneEnds.indexOfFirst { end -> x >= end }
        if (lane < 0) {
            laneEnds += x + tile + 4.dp
            lane = laneEnds.lastIndex
        } else {
            laneEnds[lane] = x + tile + 4.dp
        }
        PlacedMark(mark, x, lane)
    }
}

@Composable
private fun MarkTile(mark: TripMark, modifier: Modifier) {
    val c = Mz.colors
    val symptom = mark.symptom
    if (symptom != null) {
        Box(
            modifier.background(c.redStrong, RoundedCornerShape(12.dp)).semantics { contentDescription = "증상 신고 · ${symptom.label}" },
            contentAlignment = Alignment.Center,
        ) {
            Text("!", style = Mz.type.title3, color = c.onPrimary)
        }
    } else {
        val kind = mark.kind ?: return
        Box(
            modifier.background(c.blueWeak, RoundedCornerShape(12.dp)).semantics { contentDescription = mark.label },
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(motionGlyph(kind, mark.direction), c.blue, 24.dp)
        }
    }
}

/** 어떤 움직임을 몇 번 알렸는지. 아이콘은 그 움직임이 실제로 난 방향(타임라인 첫 기록)을 따른다. */
@Composable
private fun CueBreakdown(report: TripReport) {
    val c = Mz.colors
    Section(title = "어떤 움직임을 알렸나요", spacing = 12.dp) {
        val kinds = report.cuesByKind.entries.sortedByDescending { it.value }
        if (kinds.isEmpty()) {
            Text("미리 알릴 만큼 큰 움직임이 없었어요.", style = Mz.type.body1, color = c.textSub)
        }
        Column {
            kinds.forEach { (kind, count) ->
                val direction = report.marks.firstOrNull { it.kind == kind }?.direction ?: Direction.RIGHT
                ListRow(
                    kind.label,
                    leading = { IconTile(motionGlyph(kind, direction), c.blue, c.blueWeak) },
                    trailing = { Text("${count}회", style = Mz.type.title3.copy(fontFeatureSettings = "tnum"), color = c.text) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(report: TripReport) {
    val c = Mz.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Dot(c.blue, 12.dp)
            Text("미리 알림 ${report.totalCues}회", style = Mz.type.body2, color = c.textSub)
        }
        if (report.symptomReports > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Dot(c.redStrong, 12.dp)
                Text("증상 신고 ${report.symptomReports}회", style = Mz.type.body2, color = c.textSub)
            }
        }
    }
}

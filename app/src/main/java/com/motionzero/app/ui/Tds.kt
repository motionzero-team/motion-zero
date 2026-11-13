package com.motionzero.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * 토스 디자인 시스템(TDS)의 생김새를 따른 작은 컴포넌트 묶음.
 * - 회색 바탕 위 흰 섹션, 테두리·그림자·그라데이션 없음
 * - 위계는 글자 크기·굵기·색과 8dp 단위 여백으로
 * - Pleos 가이드: 터치 영역 64dp 이상, 움직임 효과 없음(누르는 동안 색만 살짝 어두워진다)
 */

object TdsShape {
    val section = RoundedCornerShape(28.dp)
    val button = RoundedCornerShape(18.dp)
    val badge = RoundedCornerShape(10.dp)
    val row = RoundedCornerShape(16.dp)
}

/** 섹션 안쪽 여백. 목록 행은 이 여백 안으로 bleed 만큼 넓혀 누름 영역을 섹션 끝까지 채운다. */
val SectionPadding = 28.dp
private val RowBleed = 12.dp

/** 누르면 색만 살짝 바뀌는 바탕(물결·확대 효과 없음). */
@Composable
fun Pressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color.Transparent,
    shape: Shape = TdsShape.button,
    role: Role = Role.Button,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = Mz.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .clip(shape)
            .background(color)
            .then(if (pressed) Modifier.background(c.pressed) else Modifier)
            .clickable(interactionSource = source, indication = null, role = role, onClick = onClick),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/** 흰 섹션. 제목은 왼쪽 정렬, 오른쪽에 보조 동작을 둘 수 있다. */
@Composable
fun Section(
    modifier: Modifier = Modifier,
    title: String? = null,
    caption: String? = null,
    description: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    spacing: Dp = 20.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = SectionPadding, vertical = 28.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Mz.colors
    Column(
        modifier.fillMaxWidth().background(c.surface, TdsShape.section).padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        if (title != null || caption != null || trailing != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    caption?.let { Text(it, style = Mz.type.body2, color = c.caption) }
                    title?.let { Text(it, style = Mz.type.title2, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    description?.let { Text(it, style = Mz.type.body2, color = c.caption, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                }
                trailing?.invoke(this)
            }
        }
        content()
    }
}

/** 화면·상태 제목(TDS Top). upper 는 제목 위 작은 글, subtitle 은 제목 아래 설명. */
@Composable
fun Top(
    title: String,
    modifier: Modifier = Modifier,
    upper: String? = null,
    subtitle: String? = null,
    titleStyle: TextStyle = Mz.type.title1,
    titleColor: Color = Mz.colors.text,
) {
    val c = Mz.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        upper?.let { Text(it, style = Mz.type.body1, color = c.textSub) }
        Text(title, style = titleStyle, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
        subtitle?.let { Text(it, style = Mz.type.body1, color = c.textSub, maxLines = 3, overflow = TextOverflow.Ellipsis) }
    }
}

enum class ButtonStyle { Primary, Weak, Tertiary }

@Composable
fun TdsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Primary,
    glyph: Glyph? = null,
    height: Dp = 64.dp,
) {
    val c = Mz.colors
    val (bg, fg) = when (style) {
        ButtonStyle.Primary -> c.primary to c.onPrimary
        ButtonStyle.Weak -> c.blueWeak to c.blueText
        ButtonStyle.Tertiary -> c.fill to c.text
    }
    Pressable(onClick = onClick, color = bg, shape = TdsShape.button, modifier = modifier.heightIn(min = height)) {
        Row(
            Modifier.padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (glyph != null) GlyphIcon(glyph, fg, 24.dp)
            Text(text, style = Mz.type.title3, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 화면의 단 하나뿐인 주요 동작(TDS BottomCTA). 가로를 꽉 채운다. */
@Composable
fun BottomCta(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, glyph: Glyph? = null) {
    TdsButton(text, onClick, modifier.fillMaxWidth(), ButtonStyle.Primary, glyph, height = 72.dp)
}

/** iOS 형 스위치. 상태 표시만 하고, 누르는 동작은 감싸는 행이 맡는다. 움직임 없이 바로 바뀐다. */
@Composable
fun TdsSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val c = Mz.colors
    Box(
        modifier.size(width = 64.dp, height = 38.dp).background(if (checked) c.blue else c.switchOff, CircleShape).padding(4.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(30.dp).background(c.thumb, CircleShape))
    }
}

/** 회색 트랙 위에 선택된 칸만 밝게. 칸 하나하나가 64dp 높이의 터치 영역이다. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Mz.colors
    Row(
        modifier.fillMaxWidth().height(72.dp).background(c.segmentTrack, RoundedCornerShape(20.dp)).padding(4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            val source = remember { MutableInteractionSource() }
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) c.segmentThumb else Color.Transparent)
                    .selectable(selected = selected, interactionSource = source, indication = null, role = Role.RadioButton) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = if (selected) Mz.type.title3 else Mz.type.body1, color = if (selected) c.text else c.textSub)
            }
        }
    }
}

enum class BadgeTone { Grey, Blue, Green, Red, Orange, Light }

@Composable
fun Badge(text: String, tone: BadgeTone, modifier: Modifier = Modifier, glyph: Glyph? = null) {
    val c = Mz.colors
    val (bg, fg) = when (tone) {
        BadgeTone.Grey -> c.fill to c.textSub
        BadgeTone.Blue -> c.blueWeak to c.blueText
        BadgeTone.Green -> c.greenWeak to c.greenText
        BadgeTone.Red -> c.redWeak to c.redText
        BadgeTone.Orange -> c.orangeWeak to c.orangeText
        BadgeTone.Light -> c.light to c.onLight
    }
    Row(
        modifier.background(bg, TdsShape.badge).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (glyph != null) GlyphIcon(glyph, fg, 18.dp)
        Text(text, style = Mz.type.label, color = fg, maxLines = 1)
    }
}

/** 목록 행 앞의 아이콘 칸(둥근 사각 + 옅은 바탕). */
@Composable
fun IconTile(glyph: Glyph, tint: Color, background: Color, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(background, RoundedCornerShape(size * 0.3f)), contentAlignment = Alignment.Center) {
        GlyphIcon(glyph, tint, size * 0.58f)
    }
}

/**
 * TDS ListRow: 앞 아이콘 · 제목/설명 · 뒤 보조 요소(스위치·값·배지·화살표).
 * onClick/onToggle 이 있으면 행 전체가 터치 영역이 되고, 누름 표시는 섹션 여백까지 넓혀 그린다.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    toggle: Pair<Boolean, (Boolean) -> Unit>? = null,
    titleStyle: TextStyle = Mz.type.body1,
    titleColor: Color = Mz.colors.textStrong,
    descriptionColor: Color = Mz.colors.caption,
    titleMaxLines: Int = 2,
) {
    val c = Mz.colors
    val interactive = onClick != null || toggle != null
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val action = when {
        toggle != null -> Modifier.toggleable(value = toggle.first, interactionSource = source, indication = null, role = Role.Switch, onValueChange = toggle.second)
        onClick != null -> Modifier.clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
        else -> Modifier
    }
    Row(
        modifier
            .fillMaxWidth()
            .then(if (interactive) Modifier.bleed(RowBleed).clip(TdsShape.row) else Modifier)
            .then(if (pressed) Modifier.background(c.pressed) else Modifier)
            .then(action)
            .heightIn(min = if (interactive) 80.dp else 64.dp)
            .padding(horizontal = if (interactive) RowBleed else 0.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = titleStyle, color = titleColor, maxLines = titleMaxLines, overflow = TextOverflow.Ellipsis)
            description?.let { Text(it, style = Mz.type.body2, color = descriptionColor, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        trailing?.invoke()
    }
}

/** 값만 오른쪽에 두는 행(리포트 통계·연결 상태). */
@Composable
fun ValueRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Mz.colors.text) {
    val c = Mz.colors
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Mz.type.body1, color = c.textSub, modifier = Modifier.weight(1f))
        Text(value, style = Mz.type.title3.copy(fontFeatureSettings = "tnum"), color = valueColor)
    }
}

/** 작은 회색 이름표 아래 큰 숫자. 테두리 상자 없이 여백으로만 나눈다. */
@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, valueStyle: TextStyle = Mz.type.number, valueColor: Color = Mz.colors.text) {
    val c = Mz.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = Mz.type.body2, color = c.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = valueStyle, color = valueColor, maxLines = 1)
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    val c = Mz.colors
    Box(modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(c.fillStrong)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(c.blue, CircleShape))
    }
}

@Composable
fun Dot(color: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).background(color, CircleShape))
}

/** 증상 단계: 높이가 오르는 막대 3개 중 level 개를 채운다(휴대폰 신호 막대처럼). */
@Composable
fun LevelBars(level: Int, color: Color, empty: Color, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val bar = w * 0.22f
        val gap = (w - bar * 3) / 2
        for (i in 0 until 3) {
            val barH = h * (0.42f + 0.29f * i)
            drawRoundRect(
                if (i < level) color else empty,
                topLeft = Offset(i * (bar + gap), h - barH),
                size = Size(bar, barH),
                cornerRadius = CornerRadius(bar * 0.35f),
            )
        }
    }
}

/** 부모의 가로 여백 안으로 horizontal 만큼 양쪽을 넓힌다(누름 표시가 글자에 붙지 않게). */
fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth) {
        val p = measurable.measure(constraints)
        return@layout layout(p.width, p.height) { p.place(0, 0) }
    }
    val extra = horizontal.roundToPx() * 2
    val wide = Constraints(
        minWidth = constraints.maxWidth + extra,
        maxWidth = constraints.maxWidth + extra,
        minHeight = constraints.minHeight,
        maxHeight = constraints.maxHeight,
    )
    val p = measurable.measure(wide)
    layout(constraints.maxWidth, p.height) { p.place(-extra / 2, 0) }
}

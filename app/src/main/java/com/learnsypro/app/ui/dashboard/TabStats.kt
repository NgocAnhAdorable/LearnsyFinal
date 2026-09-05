package com.learnsypro.app.ui.dashboard

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsypro.app.ui.theme.Baloo2FontFamily
import com.learnsypro.app.ui.theme.NunitoFontFamily
import com.learnsypro.app.ui.theme.rememberFadeUpState
import com.learnsypro.app.ui.theme.rememberFloatOffset

private data class Achievement(val achieved: Boolean, val label: String, val icon: String, val color: Color)

/**
 * ── TabStats ──
 * Tương đương function TabStats({history,dark}) trong dashboard.jsx.
 * Rank card lớn + progress bar, biểu đồ xu hướng 8 lần gần nhất, lưới 4 thẻ
 * thống kê, breakdown theo môn học, danh sách huy hiệu thành tích (mờ nếu
 * chưa đạt).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TabStats(
    history: List<HistoryEntry>,
    dark: Boolean,
    studentId: String? = null,
    /** presetId hiện tại của nền dùng chung — "custom_image" khi đang bật ảnh nền tự upload
     *  (xem DashboardColors.resolvedCardColor); trước đây tab này chưa nhận nên card đứng yên
     *  ở màu đặc trong khi TabHome đã mờ, gây cảm giác "blur yếu hơn" giữa các tab. */
    bgPresetId: String = "default_light",
    modifier: Modifier = Modifier
) {
    val C = dashboardColors(dark)
    val cardColor = resolvedCardColor(dark, bgPresetId)
    val cardBorderColor = resolvedCardBorderColor(dark, bgPresetId)
    var showLeaderboard by remember { mutableStateOf(false) }

    if (history.isEmpty()) {
        EmptyStatsState(dark, cardBorderColor, modifier)
        return
    }

    val avgPct = history.map { it.pct }.average()
    val best = history.maxOf { it.pct }
    val total = history.sumOf { it.total }
    val correct = history.sumOf { it.score }
    val badge = rankBadge(best)
    val chart = history.reversed().take(8).map { it.pct.toFloat() }
    val bySubject = history.groupBy { it.subject ?: "Tiếng Anh" }
        .mapValues { (_, items) -> items.map { it.pct }.average() to items.size }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Rank Card ──
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (dark) Brush.linearGradient(listOf(Color(0x26F472B6), Color(0x1FA855F7)))
                        else Brush.linearGradient(listOf(Color(0xFFFCE7F3), Color(0xFFEDE9FE)))
                    )
                    .border(1.5.dp, badge.color.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Ring(pct = avgPct.toFloat(), size = 88.dp, strokeWidth = 9.dp, color = pctColor(avgPct), dark = dark)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = fmtScore(avgPct), fontSize = 20.sp, color = pctColor(avgPct), fontFamily = Baloo2FontFamily, fontWeight = FontWeight.ExtraBold)
                            Text(text = "/10", fontSize = 9.sp, color = C.sub, fontFamily = NunitoFontFamily)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        val bounceState = rememberBounceOffset()
                        Box(modifier = Modifier.graphicsLayer { translationY = bounceState.value }) {
                            DashboardIcon(name = badge.icon, size = 36.dp, color = badge.color)
                        }
                        Text(text = badge.label, fontSize = 18.sp, fontWeight = FontWeight.Black, color = badge.color, fontFamily = Baloo2FontFamily)
                        Text(
                            text = "Cao nhất: ${fmtScore(best)}/10",
                            fontSize = 12.sp,
                            color = C.sub,
                            fontFamily = NunitoFontFamily
                        )
                        Text(
                            text = "Đã làm ${history.size} bài",
                            fontSize = 11.sp,
                            color = C.sub,
                            fontFamily = NunitoFontFamily
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ProgressBar(pct = avgPct.toFloat(), color = pctColor(avgPct), dark = dark)
                    }
                }
            }
        }

        // ── Xếp hạng lớp ──
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.card))
                    .background(cardColor)
                    .border(1.5.dp, cardBorderColor, RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.card))
                    .clickable { showLeaderboard = true }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFFEF3C7), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    DashboardIcon(name = "trophy", size = 18.dp, color = Color(0xFFF59E0B))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Xếp hạng lớp",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = C.fg,
                        fontFamily = NunitoFontFamily
                    )
                    Text(
                        text = "Xem thứ hạng XP trong lớp của bạn",
                        fontSize = 11.sp,
                        color = C.sub,
                        fontFamily = NunitoFontFamily
                    )
                }
                DashboardIcon(name = "chevronRight", size = 12.dp, color = C.sub)
            }
        }

        // ── Trend ──
        if (chart.size >= 2) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.card))
                        .background(cardColor)
                        .border(1.5.dp, cardBorderColor, RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.card))
                        .padding(16.dp)
                ) {
                    SectionHeader(
                        title = "Xu hướng điểm số",
                        dark = dark,
                        color = Color(0xFFF472B6),
                        icon = { DashboardIcon(name = "trending", size = 16.dp, color = Color(0xFFF472B6)) }
                    )
                    Sparkline(data = chart, color = Color(0xFFF472B6), width = 280.dp, height = 52.dp)
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Cũ nhất", fontSize = 10.sp, color = C.sub, fontFamily = NunitoFontFamily)
                        Text(text = "Gần nhất", fontSize = 10.sp, color = C.sub, fontFamily = NunitoFontFamily)
                    }
                }
            }
        }

        // ── Stat Grid ──
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    label = "Điểm đúng", value = correct.toString(), sub = "trên $total điểm",
                    color = Color(0xFF10B981), dark = dark,
                    icon = { DashboardIcon(name = "check", size = 20.dp, color = Color(0xFF10B981)) },
                    cardColor = cardColor,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Bài đã làm", value = history.size.toString(),
                    color = Color(0xFFA855F7), dark = dark,
                    icon = { DashboardIcon(name = "book", size = 20.dp, color = Color(0xFFA855F7)) },
                    cardColor = cardColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            val ratioScore = if (total > 0) Math.round(correct.toDouble() / total * 100).toDouble() else 0.0
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    label = "Tỉ lệ đúng",
                    value = if (total > 0) "${fmtScore(ratioScore)}/10" else "—",
                    color = Color(0xFFF59E0B), dark = dark,
                    icon = { DashboardIcon(name = "target", size = 20.dp, color = Color(0xFFF59E0B)) },
                    cardColor = cardColor,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Tốt nhất", value = "${fmtScore(best)}/10", sub = pctLabel(best),
                    color = pctColor(best), dark = dark,
                    icon = { DashboardIcon(name = "trophy", size = 20.dp, color = pctColor(best)) },
                    cardColor = cardColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── By Subject ──
        if (bySubject.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.card))
                        .background(cardColor)
                        .border(1.5.dp, cardBorderColor, RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.card))
                        .padding(16.dp)
                ) {
                    SectionHeader(
                        title = "Theo môn học",
                        dark = dark,
                        color = Color(0xFFF472B6),
                        icon = { DashboardIcon(name = "folder", size = 16.dp, color = Color(0xFFF472B6)) }
                    )
                    bySubject.forEach { (subject, pair) ->
                        val (avg, count) = pair
                        Column(modifier = Modifier.padding(bottom = 14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = subject, fontSize = 13.sp, fontWeight = FontWeight.Black, color = C.fg, fontFamily = Baloo2FontFamily)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(text = "$count bài", fontSize = 11.sp, color = C.sub, fontFamily = NunitoFontFamily)
                                    ScoreBadgeInline(pct = avg, fontSize = 11.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(5.dp))
                            ProgressBar(pct = avg.toFloat(), color = pctColor(avg), dark = dark)
                        }
                    }
                }
            }
        }

        // ── Achievements ──
        item {
            val ratio = if (total > 0) correct.toDouble() / total else 0.0
            val achievements = listOf(
                Achievement(history.size >= 1, "Bắt đầu rồi!", "check", Color(0xFF34D399)),
                Achievement(history.size >= 5, "Siêng năng", "book", Color(0xFFA855F7)),
                Achievement(history.size >= 10, "Chăm chỉ", "star", Color(0xFFF59E0B)),
                Achievement(history.size >= 20, "Thần đồng", "zap", Color(0xFF06B6D4)),
                Achievement(best >= 70, "Giỏi lắm", "trending", Color(0xFFF472B6)),
                Achievement(best >= 90, "Xuất sắc!", "trophy", Color(0xFFF59E0B)),
                Achievement(ratio >= 0.8, "Chính xác", "target", Color(0xFF10B981)),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.card))
                    .background(cardColor)
                    .border(1.5.dp, cardBorderColor, RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.card))
                    .padding(16.dp)
            ) {
                SectionHeader(
                    title = "Thành tích",
                    dark = dark,
                    color = Color(0xFFF472B6),
                    icon = { DashboardIcon(name = "ribbon", size = 16.dp, color = Color(0xFFF472B6)) }
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    achievements.forEach { a ->
                        AchievementChip(a, dark, cardColor)
                    }
                }
            }
        }
    }

    ClassLeaderboardSheet(
        visible = showLeaderboard,
        studentId = studentId,
        dark = dark,
        onDismiss = { showLeaderboard = false }
    )
}

@Composable
private fun AchievementChip(a: Achievement, dark: Boolean, cardColor: Color) {
    val C = dashboardColors(dark)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (a.achieved) Brush.linearGradient(listOf(a.color.copy(alpha = 0.19f), a.color.copy(alpha = 0.09f)))
                else Brush.linearGradient(listOf(cardColor, cardColor))
            )
            .border(
                1.5.dp,
                if (a.achieved) a.color.copy(alpha = 0.33f) else Color(0x26808080),
                RoundedCornerShape(50)
            )
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.graphicsLayer { alpha = if (a.achieved) 1f else 0.42f }
        ) {
            DashboardIcon(name = a.icon, size = 12.dp, color = if (a.achieved) a.color else C.sub)
            Text(text = a.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (a.achieved) a.color else C.sub, fontFamily = NunitoFontFamily)
        }
    }
}

@Composable
private fun EmptyStatsState(dark: Boolean, cardBorderColor: Color, modifier: Modifier = Modifier) {
    val C = dashboardColors(dark)
    Column(
        modifier = modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 100.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DashboardIcon(name = "stats", size = 18.dp, color = C.accent)
            Text(text = "Thống kê", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.fg, fontFamily = Baloo2FontFamily)
        }
        Spacer(modifier = Modifier.height(14.dp))
        val (fadeAlpha, fadeOffsetY) = rememberFadeUpState()
        val floatState = rememberFloatOffset()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = fadeAlpha; translationY = fadeOffsetY }
                .clip(RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.card))
                .border(1.5.dp, cardBorderColor, RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.card))
                .padding(vertical = 48.dp, horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.graphicsLayer {
                    translationY = floatState.translateY.value
                    rotationZ = floatState.rotation.value
                }
            ) {
                DashboardIcon(name = "stats", size = 56.dp, color = Color(0x80F472B6))
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(text = "Chưa có dữ liệu", fontSize = 20.sp, fontWeight = FontWeight.Black, color = C.fg, fontFamily = Baloo2FontFamily)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Làm bài quiz để xem thống kê nhé!", fontSize = 13.sp, color = C.sub, fontFamily = NunitoFontFamily)
        }
    }
}

/** Nhấp nhô lên xuống nhẹ — tương đương @keyframes bb-bounce 2s ease-in-out infinite */
@Composable
private fun rememberBounceOffset(): androidx.compose.runtime.State<Float> {
    if (com.learnsypro.app.ui.theme.LocalLiteMode.current) {
        return remember { mutableStateOf(0f) }
    }
    val transition = rememberInfiniteTransition(label = "bounce")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounceOffset"
    )
}

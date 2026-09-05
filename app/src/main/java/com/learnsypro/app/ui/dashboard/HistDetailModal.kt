package com.learnsypro.app.ui.dashboard

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsypro.app.ui.quiz.PerQuestionResult
import com.learnsypro.app.ui.theme.NunitoFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dữ liệu câu hỏi/đáp án trong lịch sử có thể chứa HTML thô (vd:
 * `<span style="font-weight: 400;">A</span>`) do được lưu từ trình soạn
 * quiz RichInp trên web. Compose Text không tự parse HTML nên cần loại bỏ
 * tag trước khi hiển thị, đồng thời giải mã vài HTML entity phổ biến.
 */
private fun stripHtml(raw: String): String {
    if (raw.isBlank()) return raw
    return raw
        .replace(Regex("<[^>]*>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .trim()
}

private fun formatDuration(sec: Int?): String? {
    if (sec == null || sec <= 0) return null
    val m = sec / 60
    val s = sec % 60
    return if (m > 0) "${m}p${s.toString().padStart(2, '0')}s" else "${s}s"
}

/**
 * ── HistDetailModal ──
 * Tương đương window.HistDetailModal trong hist-detail.jsx.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistDetailModal(
    entry: HistoryEntry,
    perQuestion: List<PerQuestionResult>,
    timeTakenSec: Int? = null,
    dark: Boolean,
    onClose: () -> Unit
) {
    var expandedIdx by remember { mutableStateOf<Int?>(null) }

    val pct = entry.pct
    val pctRound = Math.round(pct).toInt()
    val scoreColor = when {
        pct >= 80 -> Color(0xFF10B981)
        pct >= 50 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }
    val labelIcon = when {
        pct >= 80 -> "sparkle"
        pct >= 50 -> "star"
        else -> "zap"
    }
    val labelText = when {
        pct >= 80 -> "Xuất sắc"
        pct >= 50 -> "Khá tốt"
        else -> "Cần ôn thêm"
    }

    val dateFormatter = remember { SimpleDateFormat("d/M/yyyy HH:mm", Locale("vi", "VN")) }
    val dateStr = dateFormatter.format(Date(entry.timestampMillis))
    val timeStr = formatDuration(timeTakenSec)

    val nCorrect = perQuestion.count { it.ok }
    val nPartial = perQuestion.count { !it.ok && it.partial }
    val nWrong = perQuestion.count { !it.ok && !it.partial }
    val hasPartial = nPartial > 0

    val titleColor = if (dark) Color(0xFFF0DCE8) else Color(0xFF3D1830)
    val subColor = if (dark) Color(0xFF8A6080) else Color(0xFFA07090)
    val cardBg = if (dark) Color(0x0AFFFFFF) else Color(0x0DA855F7)
    val cardBorder = if (dark) Color(0x0FFFFFFF) else Color(0x1FA855F7)
    val bgGradient = if (dark) {
        Brush.linearGradient(listOf(Color(0xFF1E0845), Color(0xFF120330)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFFFF5F9), Color(0xFFF0E6FF)))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD10A0219))
            .clickable(onClick = onClose)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .heightIn(max = 620.dp)
                .clickable(enabled = false) {}
                .background(bgGradient, RoundedCornerShape(24.dp))
                .border(1.5.dp, if (dark) Color(0x33FF96C8) else Color(0xFFF5D5E8), RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DashboardIcon(name = "history", size = 16.dp, color = Color(0xFFFF6B95))
                Text(text = "Chi tiết lần làm", fontSize = 14.sp, fontWeight = FontWeight.Black, color = titleColor, fontFamily = NunitoFontFamily, modifier = Modifier.weight(1f))
                Box(modifier = Modifier.clickable(onClick = onClose).padding(4.dp)) {
                    DashboardIcon(name = "close", size = 14.dp, color = subColor)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = entry.lessonTitle, fontSize = 15.sp, fontWeight = FontWeight.Black, color = titleColor, lineHeight = 20.sp, fontFamily = NunitoFontFamily)
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                entry.subject?.let { subj ->
                    Box(
                        modifier = Modifier
                            .background(Color(0x1FFF6B95), RoundedCornerShape(20.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(text = subj, fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFFFF6B95), fontFamily = NunitoFontFamily)
                    }
                }
                MetaChip(icon = "calendar", text = dateStr, color = subColor)
                timeStr?.let { MetaChip(icon = "clock", text = it, color = subColor) }
                MetaChip(icon = "notes", text = "${perQuestion.size} câu", color = subColor)
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(scoreColor.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
                    .border(1.5.dp, scoreColor.copy(alpha = 0.19f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ScoreRingCanvas(pct = pct.toFloat(), color = scoreColor, dark = dark)
                Column {
                    Text(text = "$pctRound%", fontSize = 34.sp, fontWeight = FontWeight.Black, color = scoreColor, fontFamily = NunitoFontFamily)
                    Text(text = "${fmtScore(pct)} / 10 điểm", fontSize = 13.sp, fontWeight = FontWeight.Black, color = scoreColor, fontFamily = NunitoFontFamily)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DashboardIcon(name = labelIcon, size = 12.dp, color = subColor)
                        Text(text = labelText, fontSize = 11.sp, color = subColor, fontFamily = NunitoFontFamily)
                    }
                }
            }

            if (perQuestion.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HistStatPill(count = nCorrect, label = "Đúng", color = Color(0xFF10B981), modifier = Modifier.weight(1f))
                    if (hasPartial) HistStatPill(count = nPartial, label = "Một phần", color = Color(0xFFF59E0B), modifier = Modifier.weight(1f))
                    HistStatPill(count = nWrong, label = "Sai", color = Color(0xFFEF4444), modifier = Modifier.weight(1f))
                }
            }

            if (perQuestion.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(text = "CHI TIẾT TỪNG CÂU", fontSize = 10.sp, fontWeight = FontWeight.Black, color = subColor, letterSpacing = 1.sp, fontFamily = NunitoFontFamily)
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    perQuestion.forEachIndexed { pi, pq ->
                        HistQuestionRow(
                            pq = pq,
                            index = pi,
                            dark = dark,
                            subColor = subColor,
                            cardBg = cardBg,
                            cardBorder = cardBorder,
                            expanded = expandedIdx == pi,
                            onToggle = { expandedIdx = if (expandedIdx == pi) null else pi }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaChip(icon: String, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        DashboardIcon(name = icon, size = 11.dp, color = color)
        Text(text = text, fontSize = 11.sp, color = color, fontFamily = NunitoFontFamily)
    }
}

@Composable
private fun HistStatPill(count: Int, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(13.dp))
            .border(1.5.dp, color.copy(alpha = 0.2f), RoundedCornerShape(13.dp))
            .padding(vertical = 9.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = count.toString(), fontSize = 20.sp, fontWeight = FontWeight.Black, color = color, fontFamily = NunitoFontFamily)
        Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Black, color = color, fontFamily = NunitoFontFamily)
    }
}

@Composable
private fun HistQuestionRow(
    pq: PerQuestionResult,
    index: Int,
    dark: Boolean,
    subColor: Color,
    cardBg: Color,
    cardBorder: Color,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val color = if (pq.ok) Color(0xFF10B981) else if (pq.partial) Color(0xFFF59E0B) else Color(0xFFEF4444)
    val label = if (pq.ok) "✓ Đúng" else if (pq.partial) "~ Một phần" else "✗ Sai"
    val hasDetail = pq.qText.isNotBlank() || pq.correctAns.isNotBlank()
    val cleanQText = remember(pq.qText) { stripHtml(pq.qText) }
    val cleanCorrectAns = remember(pq.correctAns) { stripHtml(pq.correctAns) }
    val truncatedText = if (cleanQText.length > 36) cleanQText.take(36) + "…" else cleanQText

    Column(modifier = Modifier.animateContentSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBg, RoundedCornerShape(10.dp))
                .border(1.dp, cardBorder, RoundedCornerShape(10.dp))
                .clickable(enabled = hasDetail, onClick = onToggle)
                .padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
            Text(
                text = "Câu ${index + 1}" + if (cleanQText.isNotBlank()) " – $truncatedText" else "",
                fontSize = 12.sp,
                color = if (dark) Color(0xFFC898B8) else Color(0xFF6B3050),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = NunitoFontFamily,
                modifier = Modifier.weight(1f)
            )
            Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Black, color = color, fontFamily = NunitoFontFamily)
            if (hasDetail) {
                Box(modifier = Modifier.graphicsLayer { rotationZ = if (expanded) 180f else 0f }) {
                    DashboardIcon(name = "chevronRight", size = 10.dp, color = subColor)
                }
            }
        }

        if (expanded && hasDetail) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (dark) Color(0x06FFFFFF) else Color(0x08A855F7),
                        RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)
                    )
                    .border(1.dp, cardBorder, RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val mutedText = if (dark) Color(0xFFA08898) else Color(0xFF8A6070)
                if (cleanCorrectAns.isNotBlank()) {
                    Text(
                        text = if (pq.ok) "Đáp án: $cleanCorrectAns" else "Đáp án đúng: $cleanCorrectAns",
                        fontSize = 11.sp,
                        color = if (pq.ok) mutedText else Color(0xFF10B981),
                        fontWeight = if (pq.ok) FontWeight.Normal else FontWeight.Bold,
                        fontFamily = NunitoFontFamily
                    )
                } else if (cleanQText.isNotBlank()) {
                    Text(
                        text = if (pq.ok) "Trả lời đúng" else "Trả lời sai",
                        fontSize = 11.sp,
                        color = mutedText,
                        fontStyle = FontStyle.Italic,
                        fontFamily = NunitoFontFamily
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreRingCanvas(pct: Float, color: Color, dark: Boolean) {
    Canvas(modifier = Modifier.size(94.dp)) {
        val strokeWidth = 10.dp.toPx()
        val diameter = this.size.minDimension - strokeWidth
        val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
        val arcSize = Size(diameter, diameter)
        drawArc(
            color = if (dark) Color(0x12FFFFFF) else Color(0x0F000000),
            startAngle = -90f, sweepAngle = 360f, useCenter = false,
            topLeft = topLeft, size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        drawArc(
            color = color,
            startAngle = -90f, sweepAngle = 360f * (pct.coerceIn(0f, 100f) / 100f), useCenter = false,
            topLeft = topLeft, size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

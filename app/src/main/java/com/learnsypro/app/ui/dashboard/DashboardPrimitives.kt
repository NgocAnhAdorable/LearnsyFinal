package com.learnsypro.app.ui.dashboard

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsypro.app.ui.theme.NunitoFontFamily
import com.learnsypro.app.ui.theme.Baloo2FontFamily
import kotlin.math.max
import kotlin.math.min

/**
 * ── Ring: vòng tròn tiến độ đơn giản ──
 * Tương đương function Ring({pct,size,stroke,color,dark}) trong dashboard.jsx
 */
@Composable
fun Ring(
    pct: Float,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 80.dp,
    strokeWidth: androidx.compose.ui.unit.Dp = 8.dp,
    color: Color = Color(0xFFF472B6),
    dark: Boolean = false
) {
    val animatedPct by animateFloatAsState(
        targetValue = pct.coerceIn(0f, 100f),
        animationSpec = tween(700, easing = EaseInOut),
        label = "ringPct"
    )
    val trackColor = if (dark) Color(0x1AFFFFFF) else Color(0x0F000000)

    Canvas(modifier = modifier.size(size)) {
        val stroke = strokeWidth.toPx()
        val diameter = this.size.minDimension - stroke
        val topLeft = Offset(stroke / 2, stroke / 2)
        val arcSize = Size(diameter, diameter)

        // Track nền
        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        // Tiến độ
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * (animatedPct / 100f),
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

/**
 * ── RingScore: vòng tròn tiến độ có hiển thị điểm số ở giữa (thang 10) ──
 * Tương đương RingScore trong dashboard.jsx (bản có text điểm + /10 optional)
 */
@Composable
fun RingScore(
    pct: Double,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 80.dp,
    strokeWidth: androidx.compose.ui.unit.Dp = 8.dp,
    dark: Boolean = false,
    showMax: Boolean = true
) {
    val color = pctColor(pct)
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Ring(
            pct = pct.toFloat(),
            size = size,
            strokeWidth = strokeWidth,
            color = color,
            dark = dark
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = fmtScore(pct),
                color = color,
                fontFamily = Baloo2FontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = (size.value * 0.22f).sp
            )
            if (showMax) {
                Text(
                    text = "/10",
                    color = if (dark) Color(0x4DFFFFFF) else Color(0x47000000),
                    fontFamily = NunitoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.11f).sp
                )
            }
        }
    }
}

/**
 * ── ScoreBadgeInline: pill nhỏ hiển thị điểm dạng "8.5/10" ──
 */
@Composable
fun ScoreBadgeInline(pct: Double, fontSize: androidx.compose.ui.unit.TextUnit = 13.sp) {
    val color = pctColor(pct)
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .border(1.2.dp, color.copy(alpha = 0.33f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text = "${fmtScore(pct)}/10",
            color = color,
            fontFamily = Baloo2FontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = fontSize
        )
    }
}

/**
 * ── StatCard: thẻ thống kê kawaii với icon ──
 */
@Composable
fun StatCard(
    label: String,
    value: String,
    color: Color,
    dark: Boolean,
    icon: @Composable () -> Unit,
    sub: String? = null,
    cardColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val C = dashboardColors(dark)
    Row(
        modifier = modifier
            .background(cardColor ?: C.card, RoundedCornerShape(20.dp))
            .border(1.5.dp, color.copy(alpha = 0.19f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(
                    Brush.linearGradient(listOf(color.copy(alpha = 0.16f), color.copy(alpha = 0.08f))),
                    RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Column {
            Text(
                text = value,
                color = color,
                fontFamily = Baloo2FontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                lineHeight = 22.sp
            )
            Text(
                text = label,
                color = C.sub,
                fontFamily = NunitoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
            if (sub != null) {
                Text(
                    text = sub,
                    color = C.sub.copy(alpha = 0.7f),
                    fontFamily = NunitoFontFamily,
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * ── Sparkline: biểu đồ đường mini có vùng tô gradient ──
 */
@Composable
fun Sparkline(
    data: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFF472B6),
    width: androidx.compose.ui.unit.Dp = 120.dp,
    height: androidx.compose.ui.unit.Dp = 44.dp
) {
    if (data.size < 2) return
    val maxVal = max(data.max(), 1f)
    val minVal = min(data.min(), 0f)
    val range = (maxVal - minVal).let { if (it == 0f) 1f else it }

    Canvas(modifier = modifier.size(width, height)) {
        val w = this.size.width
        val h = this.size.height
        val points = data.mapIndexed { i, v ->
            val x = i / (data.size - 1).toFloat() * w
            val y = h - (v - minVal) / range * (h - 8) - 4
            Offset(x, y)
        }

        // Vùng tô gradient dưới đường
        val areaPath = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f)))
        )

        // Đường line chính
        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            path = linePath,
            color = color,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )

        // Chấm điểm cuối
        val last = points.last()
        drawCircle(color = color.copy(alpha = 0.14f), radius = 9.dp.toPx(), center = last)
        drawCircle(color = color, radius = 5.dp.toPx(), center = last)
    }
}

/**
 * ── ProgressBar: thanh tiến độ có shimmer chạy ──
 * Lưu ý: shimmer động cần AnimatedProgressBar riêng nếu muốn hiệu ứng chạy;
 * đây là bản tĩnh, đủ dùng cho hầu hết trường hợp.
 */
@Composable
fun ProgressBar(pct: Float, color: Color, dark: Boolean, modifier: Modifier = Modifier) {
    val animatedPct by animateFloatAsState(
        targetValue = pct.coerceIn(0f, 100f),
        animationSpec = tween(700, easing = EaseInOut),
        label = "progressBarPct"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .background(
                if (dark) Color(0x1AFFFFFF) else Color(0x0F000000),
                RoundedCornerShape(50)
            )
            .clip(RoundedCornerShape(50))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedPct / 100f)
                .background(
                    Brush.horizontalGradient(listOf(color.copy(alpha = 0.73f), color)),
                    RoundedCornerShape(50)
                )
        )
    }
}

/**
 * ── Toggle: công tắc kawaii bật/tắt ──
 */
@Composable
fun KawaiiToggle(value: Boolean, onChange: (Boolean) -> Unit) {
    val thumbOffset by animateDpAsState(
        targetValue = if (value) 26.dp else 4.dp,
        animationSpec = tween(250),
        label = "toggleThumb"
    )
    Box(
        modifier = Modifier
            .size(width = 50.dp, height = 28.dp)
            .background(
                brush = if (value) AccentGradient else Brush.linearGradient(
                    listOf(Color(0x33808080), Color(0x33808080))
                ),
                shape = RoundedCornerShape(50)
            )
            .clickable { onChange(!value) }
    ) {
        Box(
            modifier = Modifier
                .padding(start = thumbOffset, top = 4.dp)
                .size(20.dp)
                .background(Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (value) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFFF472B6),
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

/**
 * ── ScoreSVG: biến thể RingScore có thêm hiệu ứng radar quét 1 vòng khi mount ──
 * Tương đương function ScoreSVG({pct,size,dark,showMax,liteMode}) trong dashboard.jsx.
 * Khác RingScore ở chỗ có 1 vệt sáng quét quanh vòng tròn trong 900ms đầu rồi biến mất
 * (liteMode=true thì bỏ qua hiệu ứng này để tiết kiệm hiệu năng, đúng logic gốc).
 */
@Composable
fun ScoreSVG(
    pct: Double,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 56.dp,
    dark: Boolean = false,
    showMax: Boolean = true,
    liteMode: Boolean = false
) {
    val color = pctColor(pct)
    var showSweep by remember { mutableStateOf(!liteMode) }

    LaunchedEffect(liteMode) {
        if (liteMode) return@LaunchedEffect
        kotlinx.coroutines.delay(900)
        showSweep = false
    }

    val sweepAlpha by animateFloatAsState(
        targetValue = if (showSweep) 0.7f else 0f,
        animationSpec = tween(if (showSweep) 850 else 300),
        label = "radarSweepAlpha"
    )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        // Nền mờ phía sau vòng tròn
        Canvas(modifier = Modifier.size(size)) {
            drawCircle(color = color.copy(alpha = 0.07f), radius = this.size.minDimension / 2 + 2.dp.toPx())
        }
        Ring(pct = pct.toFloat(), size = size, strokeWidth = 4.dp, color = color, dark = dark)

        // Radar sweep — vệt sáng chạy 1 vòng rồi mờ dần biến mất
        if (sweepAlpha > 0f) {
            Canvas(modifier = Modifier.size(size).alpha(sweepAlpha)) {
                val stroke = 2.dp.toPx()
                val diameter = this.size.minDimension - stroke
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 80f,
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(diameter, diameter),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = fmtScore(pct),
                color = color,
                fontFamily = Baloo2FontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = (size.value * 0.24f).sp
            )
            if (showMax) {
                Text(
                    text = "/10",
                    color = if (dark) Color(0x4DFFFFFF) else Color(0x47000000),
                    fontFamily = NunitoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.11f).sp
                )
            }
        }
    }
}

/**
 * ── SectionHeader: tiêu đề mục có icon tròn nền màu ──
 */
@Composable
fun SectionHeader(
    title: String,
    dark: Boolean,
    icon: @Composable () -> Unit,
    color: Color = Color(0xFFF472B6),
    modifier: Modifier = Modifier
) {
    val C = dashboardColors(dark)
    Row(
        modifier = modifier.padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(color.copy(alpha = 0.125f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Text(
            text = title,
            color = C.fg,
            fontFamily = Baloo2FontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

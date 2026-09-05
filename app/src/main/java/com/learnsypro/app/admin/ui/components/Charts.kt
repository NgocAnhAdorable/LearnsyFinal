package com.learnsypro.app.admin.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsypro.app.admin.ui.theme.LearnsyColors
import kotlin.math.min

data class ChartBar(val label: String, val count: Int, val color: Color? = null)
data class DonutSlice(val label: String, val value: Int, val color: Color)

// Tương đương BarChart trong dashboard.jsx
@Composable
fun BarChartView(data: List<ChartBar>, color: Color, colors: LearnsyColors, modifier: Modifier = Modifier) {
    val max = (data.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
    Canvas(modifier = modifier.fillMaxWidth().height(140.dp)) {
        val w = size.width; val h = size.height
        val padL = 32f; val padR = 8f; val padT = 14f; val padB = 28f
        val plotH = h - padT - padB
        val bGap = (w - padL - padR) / data.size
        val bW = bGap * 0.55f

        listOf(0f, .25f, .5f, .75f, 1f).forEach { f ->
            val y = padT + plotH * (1 - f)
            drawLine(Color.Black.copy(alpha = 0.05f), Offset(padL, y), Offset(w - padR, y), strokeWidth = 1f)
        }

        data.forEachIndexed { i, d ->
            val c = d.color ?: color
            val x = padL + i * bGap + (bGap - bW) / 2
            val isEmpty = d.count == 0
            val barY = if (isEmpty) h - padB - 3f else padT + plotH * (1 - d.count.toFloat() / max)
            val barH = if (isEmpty) 3f else (h - padB - barY).coerceAtLeast(3f)

            drawRoundRect(
                brush = Brush.verticalGradient(listOf(c.copy(alpha = 0.95f), c.copy(alpha = 0.4f))),
                topLeft = Offset(x, barY),
                size = Size(bW, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                alpha = if (isEmpty) 0.4f else 1f
            )

            drawContext.canvas.nativeCanvas.apply {
                if (!isEmpty) {
                    val paint = android.graphics.Paint().apply {
                        textSize = 9.sp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        isFakeBoldText = true
                        setColor(c.toArgb())
                    }
                    drawText("${d.count}", x + bW / 2, barY - 4f, paint)
                }
                val labelPaint = android.graphics.Paint().apply {
                    textSize = 9.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    setColor(colors.text3.toArgb())
                }
                drawText(d.label, x + bW / 2, h - padB + 12f, labelPaint)
            }
        }
    }
}

// Tương đương AreaChart trong dashboard.jsx
@Composable
fun AreaChartView(data: List<ChartBar>, color: Color, colors: LearnsyColors, modifier: Modifier = Modifier) {
    val max = (data.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
    Canvas(modifier = modifier.fillMaxWidth().height(110.dp)) {
        val w = size.width; val h = size.height
        val padL = 32f; val padR = 10f; val padT = 14f; val padB = 26f
        val plotW = w - padL - padR; val plotH = h - padT - padB
        val n = (data.size - 1).coerceAtLeast(1)

        fun scaleX(i: Int) = padL + i * plotW / n
        fun scaleY(v: Int) = padT + plotH * (1 - v.toFloat() / max)

        val points = data.mapIndexed { i, d -> Offset(scaleX(i), scaleY(d.count)) }

        listOf(0f, .5f, 1f).forEach { f ->
            val y = padT + plotH * (1 - f)
            drawLine(Color.Black.copy(alpha = 0.05f), Offset(padL, y), Offset(w - padR, y), strokeWidth = 1f)
        }

        val linePath = Path()
        points.forEachIndexed { i, p ->
            if (i == 0) linePath.moveTo(p.x, p.y)
            else {
                val prev = points[i - 1]
                val cpx = (prev.x + p.x) / 2
                linePath.cubicTo(cpx, prev.y, cpx, p.y, p.x, p.y)
            }
        }
        val areaPath = Path().apply {
            addPath(linePath)
            lineTo(points.last().x, h - padB)
            lineTo(points.first().x, h - padB)
            close()
        }
        drawPath(areaPath, brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0f))))
        drawPath(linePath, color = color, style = Stroke(width = 2.5f, cap = StrokeCap.Round))

        points.forEachIndexed { i, p ->
            drawCircle(color, radius = 5f, center = p)
            drawCircle(Color.White, radius = 3f, center = p)
            drawContext.canvas.nativeCanvas.apply {
                val labelPaint = android.graphics.Paint().apply {
                    textSize = 8.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    setColor(colors.text3.toArgb())
                }
                drawText(data[i].label, p.x, h - padB + 11f, labelPaint)
            }
        }
    }
}

// Tương đương DonutChart trong dashboard.jsx
@Composable
fun DonutChartView(slices: List<DonutSlice>, colors: LearnsyColors, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.value }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Canvas(modifier = Modifier.size(120.dp)) {
            val cx = size.width / 2; val cy = size.height / 2
            val outerR = min(cx, cy) * 0.75f
            val innerR = outerR * 0.55f
            var startAngle = -90f
            if (total > 0) {
                slices.filter { it.value > 0 }.forEach { sl ->
                    val sweep = 360f * sl.value / total
                    drawArc(
                        color = sl.color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = Offset(cx - outerR, cy - outerR),
                        size = Size(outerR * 2, outerR * 2),
                        alpha = 0.92f
                    )
                    startAngle += sweep
                }
                drawCircle(Color(0xFFFFFFFF), radius = innerR, center = Offset(cx, cy))
            } else {
                drawCircle(
                    color = Color.Black.copy(alpha = 0.07f),
                    radius = outerR,
                    center = Offset(cx, cy),
                    style = Stroke(width = outerR * 0.28f)
                )
            }
            drawContext.canvas.nativeCanvas.apply {
                val totalPaint = android.graphics.Paint().apply {
                    textSize = 14.sp.toPx(); isFakeBoldText = true
                    textAlign = android.graphics.Paint.Align.CENTER
                    setColor(colors.text.toArgb())
                }
                drawText("$total", cx, cy - 4f, totalPaint)
                val subPaint = android.graphics.Paint().apply {
                    textSize = 8.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    setColor(colors.text3.toArgb())
                }
                drawText("câu hỏi", cx, cy + 12f, subPaint)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            slices.forEach { sl ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                            .background(sl.color)
                    )
                    Text(sl.label, modifier = Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.text2, maxLines = 1)
                    Text("${sl.value}", fontSize = 11.sp, fontWeight = FontWeight.Black, color = sl.color)
                }
            }
        }
    }
}

// Tương đương RadialProgress trong dashboard.jsx
@Composable
fun RadialProgressView(value: Int, max: Int, color: Color, label: String, colors: LearnsyColors) {
    val pct = if (max > 0) (value.toFloat() / max).coerceIn(0f, 1f) else 0f
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(modifier = Modifier.size(88.dp)) {
            val strokeW = 7.dp.toPx()
            val r = (size.minDimension - strokeW) / 2 - 4
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(Color.Black.copy(alpha = 0.06f), radius = r, center = center, style = Stroke(strokeW))
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * pct,
                useCenter = false,
                topLeft = Offset(center.x - r, center.y - r),
                size = Size(r * 2, r * 2),
                style = Stroke(strokeW, cap = StrokeCap.Round)
            )
            drawContext.canvas.nativeCanvas.apply {
                val valuePaint = android.graphics.Paint().apply {
                    textSize = 15.sp.toPx(); isFakeBoldText = true
                    textAlign = android.graphics.Paint.Align.CENTER
                    setColor(colors.text.toArgb())
                }
                drawText("$value", center.x, center.y - 3f, valuePaint)
                val maxPaint = android.graphics.Paint().apply {
                    textSize = 9.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    setColor(colors.text3.toArgb())
                }
                drawText("/$max", center.x, center.y + 14f, maxPaint)
            }
        }
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = colors.text3)
    }
}

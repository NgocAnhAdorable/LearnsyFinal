package com.learnsypro.app.admin.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

// Tương đương các SVG icon trang trí trong ui-components.jsx (Flower/Heart/Star/Sparkle/Bow).
// viewBox gốc giữ nguyên tỉ lệ, vẽ bằng Path thay vì SVG path string.

@Composable
fun FlowerIcon(size: Int = 16, color: Color = Color(0xFFFFB7C9), modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val scale = this.size.width / 24f
        listOf(0f, 72f, 144f, 216f, 288f).forEachIndexed { i, deg ->
            rotate(degrees = deg, pivot = Offset(12f * scale, 12f * scale)) {
                drawOval(
                    color = color.copy(alpha = if (i % 2 == 0) 0.9f else 0.7f),
                    topLeft = Offset((12f - 3f) * scale, (6f - 5.5f) * scale),
                    size = androidx.compose.ui.geometry.Size(6f * scale, 11f * scale)
                )
            }
        }
        drawCircle(Color(0xFFFFF5CC), radius = 3.5f * scale, center = Offset(12f * scale, 12f * scale))
    }
}

@Composable
fun HeartIcon(size: Int = 14, color: Color = Color(0xFFF9A8D4), modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = this.size.width / 20f
        val path = Path().apply {
            moveTo(10f * s, 17f * s)
            cubicTo(10f * s, 17f * s, 2f * s, 11.5f * s, 2f * s, 6.5f * s)
            cubicTo(2f * s, 4.2f * s, 3.8f * s, 2.5f * s, 6f * s, 2.5f * s)
            cubicTo(7.8f * s, 2.5f * s, 9.2f * s, 3.4f * s, 10f * s, 4.8f * s)
            cubicTo(10.8f * s, 3.4f * s, 12.2f * s, 2.5f * s, 14f * s, 2.5f * s)
            cubicTo(16.2f * s, 2.5f * s, 18f * s, 4.2f * s, 18f * s, 6.5f * s)
            cubicTo(18f * s, 11.5f * s, 10f * s, 17f * s, 10f * s, 17f * s)
            close()
        }
        drawPath(path, color = color)
    }
}

@Composable
fun StarIcon(size: Int = 13, color: Color = Color(0xFFFCD34D), modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = this.size.width / 20f
        val pts = listOf(
            10f to 1.5f, 12.47f to 7.35f, 18.78f to 7.64f, 14.09f to 11.89f,
            15.85f to 18.09f, 10f to 14.55f, 4.15f to 18.09f, 5.91f to 11.89f,
            1.22f to 7.64f, 7.53f to 7.35f
        )
        val path = Path().apply {
            pts.forEachIndexed { i, p ->
                if (i == 0) moveTo(p.first * s, p.second * s) else lineTo(p.first * s, p.second * s)
            }
            close()
        }
        drawPath(path, color = color)
    }
}

@Composable
fun SparkleIcon(size: Int = 14, color: Color = Color(0xFFC084FC), modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = this.size.width / 20f
        val path = Path().apply {
            moveTo(10f * s, 0f)
            lineTo(11.5f * s, 8.5f * s)
            lineTo(20f * s, 10f * s)
            lineTo(11.5f * s, 11.5f * s)
            lineTo(10f * s, 20f * s)
            lineTo(8.5f * s, 11.5f * s)
            lineTo(0f, 10f * s)
            lineTo(8.5f * s, 8.5f * s)
            close()
        }
        drawPath(path, color = color)
    }
}

@Composable
fun BowIcon(size: Int = 28, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = this.size.width / 28f
        val leftWing = Path().apply {
            moveTo(14f * s, 9f * s)
            cubicTo(11f * s, 5.5f * s, 2f * s, 1.5f * s, 1.5f * s, 5.5f * s)
            cubicTo(1f * s, 9f * s, 9f * s, 12.5f * s, 14f * s, 9f * s)
            close()
        }
        val rightWing = Path().apply {
            moveTo(14f * s, 9f * s)
            cubicTo(17f * s, 5.5f * s, 26f * s, 1.5f * s, 26.5f * s, 5.5f * s)
            cubicTo(27f * s, 9f * s, 19f * s, 12.5f * s, 14f * s, 9f * s)
            close()
        }
        drawPath(leftWing, color = Color(0xFFFFAEC9).copy(alpha = 0.8f))
        drawPath(rightWing, color = Color(0xFFFFAEC9).copy(alpha = 0.8f))
        drawCircle(Color(0xFFFF85A5), radius = 2.8f * s, center = Offset(14f * s, 9f * s))
    }
}

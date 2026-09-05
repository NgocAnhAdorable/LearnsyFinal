package com.learnsypro.app.admin.ui.branding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Mini badge khối "L" bo góc — cổng từ app học sinh (ui/branding/LearnsyLogo.kt)
 * để đồng bộ logo header admin với header index (student app).
 *
 * [badgeColor] tô màu khối "L"; [backgroundColor] tô nền tròn phía sau
 * (Color.Transparent để ẩn, vì header admin tự vẽ nền vuông bo góc gradient).
 */
@Composable
fun AtomBadge(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    badgeColor: Color = Color(0xFFA855F7),
    backgroundColor: Color = Color.White
) {
    Canvas(modifier = modifier.size(size)) {
        if (backgroundColor != Color.Transparent) {
            drawCircle(
                color = backgroundColor,
                radius = this.size.width / 2f,
                center = Offset(this.size.width / 2f, this.size.height / 2f)
            )
        }
        drawLMark(color = badgeColor)
    }
}

private fun DrawScope.drawLMark(color: Color) {
    val scale = this.size.minDimension / 108f
    drawPath(path = buildLPath(scale), color = color)
}

private fun buildLPath(scale: Float): Path {
    val radius = CornerRadius(10f * scale)
    return Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = 38f * scale, top = 30f * scale,
                right = 48f * scale, bottom = 70f * scale,
                cornerRadius = radius
            )
        )
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = 38f * scale, top = 60f * scale,
                right = 74f * scale, bottom = 70f * scale,
                cornerRadius = radius
            )
        )
    }
}

package com.learnsypro.app.admin.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Tương đương .bb-input-wrap::before + @keyframes bb-border-flow trong banh-beo-ui.css —
// viền gradient 4 màu chạy khi input được focus. Compose không có background-position
// animation trực tiếp, nên dịch chuyển điểm bắt đầu/kết thúc của linear gradient theo
// thời gian để tạo hiệu ứng "chảy" tương tự.
//
// FIX: trước đây `phase` đọc qua "by" rồi dùng để build Brush.linearGradient(...) ngay
// trong thân composable, truyền thẳng vào Modifier.border(brush = ...) — việc build Brush
// đó xảy ra ở COMPOSITION phase (không phải draw phase), nên bbFlowingBorderModifier()
// (và toàn bộ composable cha gọi nó — tức là MỌI ô input đang focus trong Lesson
// Editor/Listening Form/Student modal) bị recompose lại 60 lần/giây suốt thời gian ô đó
// còn focus. Đây chính là lý do gõ vào ô tiêu đề/mật khẩu... cảm giác giật.
// Giờ tự vẽ border bằng drawWithCache — chỉ đọc phase.value bên trong onDrawWithContent
// (draw phase, deferred), không còn ép recompose.
@Composable
fun bbFlowingBorderModifier(base: Modifier, focused: Boolean, cornerRadius: Dp = 12.dp): Modifier {
    if (!focused) return base

    val transition = rememberInfiniteTransition(label = "bb-border-flow")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart),
        label = "bb-border-flow-phase"
    )

    val colors = listOf(Color(0xFFF472B6), Color(0xFFA855F7), Color(0xFF6EE7B7), Color(0xFF818CF8), Color(0xFFF472B6))

    return base.drawWithCache {
        val strokeWidthPx = 2.5.dp.toPx()
        val cornerPx = (cornerRadius + 2.dp).toPx()
        onDrawWithContent {
            drawContent()
            // Đọc phase.value ở đây (draw phase) — mỗi frame chỉ vẽ lại, không
            // recompose cả cây UI cha.
            val angleRad = (phase.value * 2 * Math.PI).toFloat()
            val dx = kotlin.math.cos(angleRad) * 200f
            val dy = kotlin.math.sin(angleRad) * 200f
            val brush = Brush.linearGradient(
                colors = colors,
                start = Offset(0f - dx, 0f - dy),
                end = Offset(200f + dx, 200f + dy)
            )
            drawRoundRect(
                brush = brush,
                topLeft = Offset(strokeWidthPx / 2, strokeWidthPx / 2),
                size = Size(size.width - strokeWidthPx, size.height - strokeWidthPx),
                cornerRadius = CornerRadius(cornerPx),
                style = Stroke(width = strokeWidthPx)
            )
        }
    }
}

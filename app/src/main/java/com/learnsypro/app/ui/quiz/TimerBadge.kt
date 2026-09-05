package com.learnsypro.app.ui.quiz

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsypro.app.ui.dashboard.DashboardIcon
import com.learnsypro.app.ui.theme.NunitoFontFamily

/** Tương đương fmtTime(seconds) trong quiz-player.jsx. */
fun fmtTime(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}

/** Tương đương timerColor(seconds) — đổi màu theo mức khẩn cấp. */
fun timerColor(seconds: Int): Color = when {
    seconds <= 10 -> Color(0xFFEF4444)
    seconds <= 30 -> Color(0xFFF59E0B)
    else -> Color(0xFF10B981)
}

/**
 * ── TimerBadge ──
 * Pill hiển thị thời gian còn lại, rung nhẹ khi <=10s (urgent).
 */
@Composable
fun TimerBadge(secondsLeft: Int) {
    val targetColor = timerColor(secondsLeft)
    val color by animateColorAsState(targetColor, label = "timerColor")
    val urgent = secondsLeft <= 10

    val shakeXState = if (urgent) rememberTimerShake() else null

    Row(
        modifier = Modifier
            .graphicsLayer { translationX = shakeXState?.value ?: 0f }
            .background(color.copy(alpha = 0.13f), RoundedCornerShape(50))
            .border(1.4.dp, color.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DashboardIcon(name = "history", size = 13.dp, color = color)
        Text(
            text = fmtTime(secondsLeft),
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = color,
            fontFamily = NunitoFontFamily,
            modifier = Modifier.padding(start = 5.dp)
        )
    }
}

@Composable
private fun rememberTimerShake(): androidx.compose.runtime.State<Float> {
    val transition = rememberInfiniteTransition(label = "timerShake")
    return transition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(120, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "timerShakeX"
    )
}

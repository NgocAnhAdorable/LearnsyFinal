package com.learnsypro.app.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/**
 * ── LetterAvatar (bản thật, dựa theo avatar.jsx) ──
 * Tương đương function LetterAvatar({name,size,dark,animate,avatarUrl}).
 *
 * Logic màu nền: hash tên → hue 0-360 (giống hệt công thức gốc:
 * sum(charCode)*37 % 360), sau đó dùng HSL — Compose không có HSL dựng
 * sẵn nên convert thủ công sang RGB bằng hslToColor().
 *
 * initials: lấy chữ cái đầu của tối đa 2 từ trong tên (ví dụ "Minh An" -> "MA").
 *
 * animate=true: hiệu ứng "heartbeat" (phóng to nhẹ theo nhịp tim, tương đương
 * @keyframes bb-heartbeat trong CSS gốc: scale 1 -> 1.08 -> 1 -> 1.05 -> 1).
 */
@Composable
fun LetterAvatar(
    name: String,
    size: Dp = 64.dp,
    dark: Boolean = false,
    animate: Boolean = false,
    avatarUrl: String? = null,
    modifier: Modifier = Modifier
) {
    val trimmedName = name.trim().ifEmpty { "?" }
    val initials = trimmedName
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }

    val hue = remember(trimmedName) {
        (trimmedName.sumOf { it.code } * 37) % 360
    }
    val bgColor = remember(hue, dark) {
        hslToColor(hue.toFloat(), 0.55f, if (dark) 0.38f else 0.68f)
    }

    var imgOk by remember(avatarUrl) { mutableStateOf(!avatarUrl.isNullOrBlank()) }
    var retryCount by remember(avatarUrl) { mutableIntStateOf(0) }
    var retryUrl by remember(avatarUrl) { mutableStateOf(avatarUrl) }
    var retryTrigger by remember(avatarUrl) { mutableIntStateOf(0) }

    val maxRetries = 3
    val retryDelays = longArrayOf(600L, 1500L, 3000L) // ms, tăng dần

    LaunchedEffect(avatarUrl, retryTrigger) {
        if (retryTrigger > 0 && retryTrigger <= maxRetries && !avatarUrl.isNullOrBlank()) {
            delay(retryDelays[retryTrigger - 1])
            // cache-bust nhẹ để né cache của Coil/CDN trả lại lỗi cũ
            val sep = if (avatarUrl.contains("?")) "&" else "?"
            retryUrl = "$avatarUrl${sep}_r=${System.currentTimeMillis()}"
        }
    }

    val scaleState = if (animate) rememberHeartbeatScale() else null

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                val s = scaleState?.value ?: 1f
                scaleX = s; scaleY = s
            }
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrBlank() && imgOk) {
            AsyncImage(
                model = retryUrl,
                contentDescription = trimmedName,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                onError = {
                    if (retryCount < maxRetries) {
                        retryCount += 1
                        retryTrigger = retryCount
                    } else {
                        // đã thử hết số lần cho phép -> chấp nhận hiện chữ cái
                        imgOk = false
                    }
                }
            )
        } else {
            Text(
                text = initials,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = (size.value * 0.38f).sp,
                letterSpacing = (-1).sp
                // LƯU Ý: font gốc là 'Baloo 2' — nếu muốn khớp 100%, thêm
                // Baloo2FontFamily vào Theme.kt và dùng ở đây thay cho font mặc định.
            )
        }
    }
}

/**
 * ── Heartbeat scale animation ──
 * Tương đương @keyframes bb-heartbeat 2.5s ease-in-out infinite:
 * 0%,100%: scale(1) · 25%: scale(1.08) · 40%: scale(1) · 60%: scale(1.05)
 */
@Composable
private fun rememberHeartbeatScale(): androidx.compose.runtime.State<Float> {
    if (com.learnsypro.app.ui.theme.LocalLiteMode.current) {
        return remember { mutableStateOf(1f) }
    }
    val transition = rememberInfiniteTransition(label = "heartbeat")
    return transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2500
                1f at 0
                1.08f at 625   // 25%
                1f at 1000     // 40%
                1.05f at 1500  // 60%
                1f at 2500
            }
        ),
        label = "heartbeatScale"
    )
}

/** Convert HSL (hue 0-360, saturation/lightness 0-1) sang Compose Color, tương đương CSS hsl(). */
private fun hslToColor(h: Float, s: Float, l: Float): Color {
    val c = (1f - kotlin.math.abs(2 * l - 1f)) * s
    val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f)
    )
}

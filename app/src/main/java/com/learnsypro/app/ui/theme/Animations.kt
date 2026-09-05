package com.learnsypro.app.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * ── LocalLiteMode ──
 * Cờ Lite Mode toàn app, đọc được từ BẤT KỲ composable nào mà không cần
 * truyền tay qua từng tham số hàm. Set giá trị 1 lần ở gốc cây (MainActivity)
 * dựa trên DataStore key "bb_lite_mode" — cùng nguồn dữ liệu mà
 * DashboardViewModel đang dùng, chỉ đọc song song, không đổi ViewModel.
 *
 * staticCompositionLocalOf vì giá trị hiếm khi đổi (chỉ khi người dùng bật/tắt
 * Lite Mode trong Settings) — đổi lại composition ở root sẽ recompose toàn bộ
 * cây 1 lần, chấp nhận được để tránh overhead theo dõi thay đổi liên tục của
 * compositionLocalOf thông thường.
 */
val LocalLiteMode = staticCompositionLocalOf { false }

// ═══ fadeUp: tương đương @keyframes fadeUp (translateY 12px + opacity 0→1) ═══
@Composable
fun rememberFadeUpState(delayMillis: Int = 0): Pair<Float, Float> {
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(12f) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) kotlinx.coroutines.delay(delayMillis.toLong())
        alpha.animateTo(1f, tween(240, easing = EaseInOut))
    }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) kotlinx.coroutines.delay(delayMillis.toLong())
        offsetY.animateTo(0f, tween(240, easing = EaseInOut))
    }
    return alpha.value to offsetY.value
}

// ═══ shimmer: gradient text chạy liên tục, dùng cho logo "Learnsy" ═══
// Lite Mode: đứng yên ở offset giữa (0.5f) thay vì animate — vẫn có gradient
// đẹp, chỉ không tốn frame liên tục.
@Composable
fun rememberShimmerBrush(): Brush {
    val liteMode = LocalLiteMode.current
    val offset by if (liteMode) {
        remember { mutableStateOf(0.5f) }
    } else {
        val transition = rememberInfiniteTransition(label = "shimmer")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(4000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "shimmerOffset"
        )
    }
    // Màu lấy từ .logo-learnsy gradient gốc: #f472b6,#a855f7,#6366f1,#06b6d4,#10b981,#f472b6
    val colors = listOf(
        androidx.compose.ui.graphics.Color(0xFFF472B6),
        androidx.compose.ui.graphics.Color(0xFFA855F7),
        androidx.compose.ui.graphics.Color(0xFF6366F1),
        androidx.compose.ui.graphics.Color(0xFF06B6D4),
        androidx.compose.ui.graphics.Color(0xFF10B981),
        androidx.compose.ui.graphics.Color(0xFFF472B6)
    )
    return Brush.linearGradient(
        colors = colors,
        start = androidx.compose.ui.geometry.Offset(offset * 300f, 0f),
        end = androidx.compose.ui.geometry.Offset(offset * 300f + 300f, 0f)
    )
}

/**
 * State holder cho float+rotate, KHÔNG unwrap bằng "by" ở đây — cố tình giữ
 * dạng State<Float> để nơi gọi chỉ được phép đọc .value bên trong
 * Modifier.graphicsLayer{} (draw phase). Nếu đọc .value ngay trong thân
 * composable (vd. destructure ra Float rồi return) thì scope đó sẽ bị
 * recompose lại mỗi frame — đây chính là nguyên nhân gây lag nặng vì Compose
 * phải tính lại layout/composition liên tục thay vì chỉ vẽ lại (draw).
 */
class FloatOffsetState(val translateY: State<Float>, val rotation: State<Float>)

// ═══ float / floatB: xoay + nhấp nhô nhẹ, lệch pha nhau (dùng cho icon bay lượn) ═══
// Lite Mode: đứng yên ở vị trí trung tính (0f) — icon bay lượn là hiệu ứng
// trang trí thuần túy, tắt hoàn toàn không ảnh hưởng chức năng.
@Composable
fun rememberFloatOffset(delayMillis: Int = 0, reverseRotation: Boolean = false): FloatOffsetState {
    val liteMode = LocalLiteMode.current
    if (liteMode) {
        val zero = remember { mutableStateOf(0f) }
        return FloatOffsetState(zero, zero)
    }
    val transition = rememberInfiniteTransition(label = "float")
    val translateY = transition.animateFloat(
        initialValue = 0f,
        targetValue = if (reverseRotation) -4f else -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (reverseRotation) 3200 else 2800, delayMillis = delayMillis, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )
    val rotation = transition.animateFloat(
        initialValue = if (reverseRotation) 3f else -4f,
        targetValue = if (reverseRotation) -3f else 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (reverseRotation) 3200 else 2800, delayMillis = delayMillis, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatRotate"
    )
    return FloatOffsetState(translateY, rotation)
}

// ═══ spin: xoay 360 độ liên tục, dùng cho loading spinner ═══
// Trả về State<Float> — đọc .value bên trong graphicsLayer{} ở nơi gọi, KHÔNG
// đọc trực tiếp ở đây (mất tác dụng chống recompose nếu đọc sớm).
@Composable
fun rememberSpinRotation(durationMillis: Int = 1000): State<Float> {
    val transition = rememberInfiniteTransition(label = "spin")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing)
        ),
        label = "spinRotation"
    )
}

// ═══ pop: scale bounce khi phần tử xuất hiện (0.8 → 1.0 + opacity) ═══
@Composable
fun rememberPopState(): Pair<Float, Float> {
    val scale = remember { Animatable(0.8f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, tween(220, easing = EaseInOut))
    }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(220, easing = EaseInOut))
    }
    return scale.value to alpha.value
}

// ═══ pulse: nhấp nháy opacity liên tục (0.7 ↔ 1.0) ═══
// Lite Mode: đứng yên ở alpha trung bình (0.85f) — thuần trang trí, tắt an toàn.
@Composable
fun rememberPulseAlpha(): State<Float> {
    val liteMode = LocalLiteMode.current
    if (liteMode) {
        return remember { mutableStateOf(0.85f) }
    }
    val transition = rememberInfiniteTransition(label = "pulse")
    return transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
}

// ═══ shake: rung ngang, dùng khi trả lời sai trong quiz ═══
@Composable
fun rememberShakeOffset(trigger: Boolean): Float {
    val offsetX = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger) {
            offsetX.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 400
                    0f at 0
                    -7f at 80
                    7f at 160
                    -7f at 240
                    7f at 320
                    0f at 400
                }
            )
        }
    }
    return offsetX.value
}

// ═══ scoreIn: hiệu ứng hiện khung điểm số sau khi làm quiz xong ═══
@Composable
fun rememberScoreInState(): Pair<Float, Float> {
    val scale = remember { Animatable(0.7f) }
    val translateY = remember { Animatable(30f) }
    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = keyframes {
                durationMillis = 400
                0.7f at 0
                1.06f at 240 // 60% mốc
                1f at 400
            }
        )
    }
    LaunchedEffect(Unit) {
        translateY.animateTo(0f, tween(400, easing = EaseInOut))
    }
    return scale.value to translateY.value
}

// ═══ scoreNum: hiệu ứng số điểm nhảy lên khi hiển thị ═══
@Composable
fun rememberScoreNumScale(): Float {
    val scale = remember { Animatable(0.5f) }
    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = keyframes {
                durationMillis = 350
                0.5f at 0
                1.12f at 245 // 70% mốc
                1f at 350
            }
        )
    }
    return scale.value
}

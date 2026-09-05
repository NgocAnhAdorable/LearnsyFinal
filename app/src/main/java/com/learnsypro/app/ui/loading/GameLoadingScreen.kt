package com.learnsypro.app.ui.loading

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsypro.app.ui.theme.NunitoFontFamily
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * ── GameLoadingScreen ──
 * Màn hình loading phong cách game mobile: logo "nảy" (bounce/pulse liên
 * tục) + thanh tiến độ chạy % lên tới 100 rồi gọi onFinished. Dùng chung
 * cho splash lúc mở app và các lượt chuyển màn hình lớn (vào Quiz/Listening)
 * — nơi trước đây chuyển ngay lập tức không có hiệu ứng gì.
 *
 * durationMillis: tổng thời gian giả lập loading trước khi gọi onFinished.
 * Khi isReady == null (mặc định, dùng cho Transitioning giữa Quiz/Listening
 * — không có tiến trình mạng cần chờ), thanh % chạy hết theo thời gian như
 * trước, mang tính trải nghiệm giống loading tips của game.
 *
 * Khi isReady != null (dùng cho splash lúc mở app — chờ session/DataStore
 * đọc xong), progress chỉ chạy giả lập tới trần 92% trong lúc chờ, rồi CHỜ
 * isReady chuyển true mới chạy nốt lên 100% và gọi onFinished — khớp cách
 * HoloSplash bên admin làm, tránh báo "xong" trước khi dữ liệu thật sẵn sàng.
 */
@Composable
fun GameLoadingScreen(
    dark: Boolean,
    durationMillis: Int = 1400,
    label: String = "Đang tải...",
    isReady: Boolean? = null,
    onFinished: (() -> Unit)? = null
) {
    val primary = Color(0xFF6366F1)
    val bgBrush = if (dark) {
        Brush.radialGradient(
            colors = listOf(Color(0xFF1A1F3A), Color(0xFF0F172A), Color(0xFF0F172A)),
            center = Offset(0.3f, 0.25f)
        )
    } else {
        Brush.radialGradient(
            colors = listOf(Color(0xFFEDEBFF), Color(0xFFF8FAFC), Color(0xFFF8FAFC)),
            center = Offset(0.3f, 0.25f)
        )
    }
    val tMain = if (dark) Color(0xFFF1F5F9) else Color(0xFF1E293B)
    val tSub = if (dark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val trackBg = if (dark) Color(0x33FFFFFF) else Color(0x1F6366F1)

    // Bounce/pulse liên tục cho logo — giống mascot nảy trong màn loading game.
    val infinite = rememberInfiniteTransition(label = "gameLoadingPulse")
    val bounce by infinite.animateFloat(
        initialValue = 0f,
        targetValue = -14f,
        animationSpec = infiniteRepeatable(
            animation = tween(480, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )
    val pulseScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(480, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val ringRotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing)
        ),
        label = "ringRotation"
    )

    // Tiến độ % chạy không tuyến tính (nhanh đầu, khựng nhẹ giữa chừng,
    // rồi vọt lên 100) — cảm giác "game loading" quen thuộc hơn so với
    // một đường thẳng đều đều.
    var progress by remember { mutableFloatStateOf(0f) }
    if (isReady == null) {
        LaunchedEffect(Unit) {
            val steps = listOf(0.18f, 0.34f, 0.47f, 0.52f, 0.68f, 0.81f, 0.93f, 1f)
            val stepDelay = (durationMillis / steps.size).toLong()
            for (target in steps) {
                progress = target
                kotlinx.coroutines.delay(stepDelay + Random.nextLong(-30, 60))
            }
            onFinished?.invoke()
        }
    } else {
        // Có tiến trình thật để chờ (vd. session/DataStore đọc xong) — chạy giả
        // lập tới trần 92% dựa theo thời gian trôi qua, dừng ở đó nếu isReady vẫn
        // chưa true, rồi khi true mới cho phép vọt nốt lên 100% và kết thúc.
        LaunchedEffect(Unit) {
            val cap = 0.92f
            val start = System.currentTimeMillis()
            while (progress < cap && isReady != true) {
                val elapsed = (System.currentTimeMillis() - start) / durationMillis.toFloat()
                progress = elapsed.coerceAtMost(cap)
                kotlinx.coroutines.delay(16)
            }
        }
        LaunchedEffect(isReady) {
            if (isReady) {
                while (progress < 1f) {
                    progress = (progress + 0.04f).coerceAtMost(1f)
                    kotlinx.coroutines.delay(12)
                }
                kotlinx.coroutines.delay(250) // giữ 100% một nhịp ngắn cho người dùng kịp thấy
                onFinished?.invoke()
            }
        }
    }

    val percent = (progress * 100).roundToInt()

    Box(
        modifier = Modifier.fillMaxSize().background(bgBrush),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // Logo nảy trong vòng tròn xoay quanh — hiệu ứng "spinner + mascot"
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .graphicsLayer { rotationZ = ringRotation }
                        .background(
                            Brush.sweepGradient(
                                listOf(primary.copy(alpha = 0f), primary.copy(alpha = 0.55f), primary.copy(alpha = 0f))
                            ),
                            CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .graphicsLayer {
                            translationY = bounce
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                        .background(Brush.linearGradient(listOf(Color(0xFF818CF8), primary)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    com.learnsypro.app.ui.branding.AtomBadge(
                        size = 40.dp,
                        badgeColor = Color.White,
                        backgroundColor = Color.Transparent
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = "Learnsy Pro", fontSize = 22.sp, fontWeight = FontWeight.Black, color = primary, fontFamily = NunitoFontFamily)
                Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tSub, fontFamily = NunitoFontFamily)
            }

            // Thanh tiến độ % kiểu game
            Column(
                modifier = Modifier.width(220.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(trackBg, RoundedCornerShape(50))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(Brush.horizontalGradient(listOf(Color(0xFF818CF8), primary)), RoundedCornerShape(50))
                    )
                }
                Text(text = "$percent%", fontSize = 11.sp, fontWeight = FontWeight.Black, color = tMain, fontFamily = NunitoFontFamily)
            }
        }
    }
}

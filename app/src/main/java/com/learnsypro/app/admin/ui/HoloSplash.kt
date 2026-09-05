package com.learnsypro.app.admin.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// "Loading screen" kiểu game — thay cho splash chớp nhoáng cũ. Progress bar chạy
// mượt tới ~92% trong lúc chờ (như game load asset), rồi khi isReady=true (session
// Supabase đã kiểm tra xong ở AppRoot) mới chạy nốt lên 100% và đóng lại — không
// bao giờ báo "xong" trước khi dữ liệu thật sự sẵn sàng, nhưng cũng không đứng
// yên khiến người dùng tưởng app treo.
@Composable
fun HoloSplash(isReady: Boolean, onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }
    val tips = remember {
        listOf(
            "Đang nạp dữ liệu bài học…",
            "Đồng bộ Listening từ máy chủ…",
            "Khởi động module Học sinh…",
            "Mẹo: vuốt để chuyển tab nhanh hơn",
            "Đang kết nối Supabase…"
        ).shuffled()
    }
    var tipIndex by remember { mutableIntStateOf(0) }

    // Chạy tới 92% dựa trên thời gian trôi qua (giống thanh load game), dừng chờ
    // ở đó nếu isReady vẫn chưa true, rồi khi true mới cho phép chạy nốt 100%.
    LaunchedEffect(Unit) {
        val cap = 0.92f
        val start = System.currentTimeMillis()
        while (progress < cap) {
            val elapsed = (System.currentTimeMillis() - start) / 1600f
            progress = min(cap, elapsed)
            delay(16)
        }
    }
    LaunchedEffect(isReady) {
        if (isReady) {
            while (progress < 1f) {
                progress = min(1f, progress + 0.04f)
                delay(12)
            }
            delay(250) // giữ 100% một nhịp ngắn cho người dùng kịp thấy trước khi đóng
            visible = false
            delay(400)
            onFinished()
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1600)
            tipIndex = (tipIndex + 1) % tips.size
        }
    }

    AnimatedVisibility(visible = visible, exit = fadeOut(tween(400))) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0715)),
            contentAlignment = Alignment.Center
        ) {
            FloatingParticles()
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HoloRing()
                Spacer(Modifier.height(28.dp))
                ScanningLogo()
                Spacer(Modifier.height(36.dp))
                LoadingBar(progress)
                Spacer(Modifier.height(14.dp))
                Text(
                    "${(progress * 100).toInt()}%",
                    fontSize = 13.sp, fontWeight = FontWeight.Black,
                    color = Color(0xFF10B981)
                )
                Spacer(Modifier.height(10.dp))
                AnimatedContent(targetState = tipIndex, transitionSpec = {
                    (fadeIn(tween(300)) togetherWith fadeOut(tween(200)))
                }, label = "tip") { idx ->
                    Text(
                        tips[idx], fontSize = 11.5.sp, color = Color(0xFF9CA3AF),
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingBar(progress: Float) {
    val animated by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "bar"
    )
    Box(
        modifier = Modifier
            .width(220.dp).height(8.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFF1F1730))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF7C3AED), Color(0xFF10B981))))
        )
    }
}

@Composable
private fun HoloRing() {
    val transition = rememberInfiniteTransition(label = "holo-ring")
    val angle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "angle"
    )
    Canvas(modifier = Modifier.width(120.dp).height(120.dp)) {
        rotate(angle) {
            val r = size.minDimension / 2f
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(Color(0xFF7C3AED).copy(alpha = 0f), Color(0xFF7C3AED), Color(0xFF10B981), Color(0xFF7C3AED).copy(alpha = 0f))
                ),
                radius = r,
                center = Offset(size.width / 2f, size.height / 2f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx())
            )
        }
    }
}

@Composable
private fun ScanningLogo() {
    val transition = rememberInfiniteTransition(label = "scan")
    val scanY by transition.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "scanY"
    )
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .width(140.dp).height(48.dp)
                .blur(28.dp)
                .background(Brush.horizontalGradient(listOf(Color(0xFF7C3AED), Color(0xFF10B981))))
        )
        Text(
            "Learnsy",
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFFEDE9FE)
        )
        Canvas(modifier = Modifier.width(160.dp).height(48.dp)) {
            val x = (scanY + 1f) / 2f * size.width
            drawLine(
                brush = Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF10B981).copy(alpha = 0.9f), Color.Transparent)),
                start = Offset(x, 0f), end = Offset(x, size.height),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

@Composable
private fun FloatingParticles() {
    val transition = rememberInfiniteTransition(label = "particles")
    val t by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "t"
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val colors = listOf(Color(0xFF7C3AED), Color(0xFF10B981), Color(0xFFF59E0B))
        for (i in 0 until 10) {
            val seedAngle = (i * 36f) * (Math.PI / 180f)
            val progress = (t + i * 0.1f) % 1f
            val radius = 90f + progress * 220f
            val cx = size.width / 2f + (cos(seedAngle) * radius).toFloat()
            val cy = size.height / 2f + (sin(seedAngle) * radius).toFloat() - progress * 80f
            drawCircle(
                color = colors[i % colors.size].copy(alpha = (1f - progress) * 0.8f),
                radius = 3.dp.toPx(),
                center = Offset(cx, cy)
            )
        }
    }
}

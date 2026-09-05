package com.learnsypro.app.ui.listening

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsypro.app.ui.dashboard.DashboardIcon
import com.learnsypro.app.ui.theme.NunitoFontFamily
import com.learnsypro.app.ui.theme.rememberPulseAlpha
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class ScoreVisual(val icon: String, val color: Color, val label: String)

/**
 * ── ScoreToastListening ──
 * Cùng hệ "Dynamic Island" với ScoreIsland (quiz-player), giờ đồng bộ luôn
 * cách animate: dùng Animatable + keyframes{} thay animateDpAsState, để
 * hiệu ứng MỞ thực sự pop ra từ chấm tròn 36dp rồi nảy giãn thành pill,
 * thay vì nhảy thẳng vào full-size (bug cũ do animateDpAsState khởi tạo
 * ngay ở giá trị đích lúc component được mount). Đóng lại dùng easing êm
 * khác hẳn, không nảy — y hệt ScoreIsland để 2 toast trong app cảm giác
 * là MỘT hệ thống, không lệch tốc độ/độ nảy.
 */

private val OpenEasing = CubicBezierEasing(0.34f, 1.3f, 0.64f, 1f)
private val CloseEasing = CubicBezierEasing(0.55f, 0f, 0.45f, 1f)

private const val OPEN_MS = 550
private const val CLOSE_MS = 450
private const val FULL_WIDTH = 300f
private const val FULL_HEIGHT = 56f
private const val FULL_RADIUS = 28f

@Composable
fun ScoreToastListening(visible: Boolean, correct: Int, total: Int, onClose: () -> Unit) {
    var mounted by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }

    // Khai báo TRƯỚC early-return để chỉ khởi tạo 1 lần ở trạng thái "chấm
    // tròn đóng" — animateTo() sau đó mới thực sự chạy animation mở.
    val width = remember { Animatable(36f) }
    val height = remember { Animatable(36f) }
    val radius = remember { Animatable(18f) }
    val boxAlpha = remember { Animatable(0f) }
    val squashY = remember { Animatable(0.6f) }
    val contentAlpha = remember { Animatable(0f) }
    val contentScale = remember { Animatable(0.85f) }
    val contentOffsetY = remember { Animatable(3f) }

    LaunchedEffect(visible) {
        if (!visible) {
            // visible chuyển về false (do onClose tự gọi) — phải unmount hẳn,
            // nếu không Box thu nhỏ (36dp, nền tối) sẽ đứng yên mãi trên màn
            // hình thay vì biến mất hoàn toàn.
            mounted = false
            leaving = false
            return@LaunchedEffect
        }
        leaving = false
        mounted = true
        delay(3000)
        leaving = true
        delay(500)
        onClose()
    }

    // Chuỗi MỞ — pop chấm tròn ở 20%, rồi nảy giãn ra pill full-size;
    // nội dung chữ trễ tới 40% mới hiện. Y hệt ScoreIsland.
    LaunchedEffect(mounted) {
        if (!mounted) return@LaunchedEffect
        launch {
            width.animateTo(FULL_WIDTH, keyframes {
                durationMillis = OPEN_MS
                36f at 0
                36f at 110 using OpenEasing
                280f at 302 using OpenEasing
                FULL_WIDTH at OPEN_MS using OpenEasing
            })
        }
        launch {
            height.animateTo(FULL_HEIGHT, keyframes {
                durationMillis = OPEN_MS
                36f at 0
                36f at 110 using OpenEasing
                52f at 302 using OpenEasing
                FULL_HEIGHT at OPEN_MS using OpenEasing
            })
        }
        launch {
            radius.animateTo(FULL_RADIUS, keyframes {
                durationMillis = OPEN_MS
                18f at 0
                18f at 110 using OpenEasing
                26f at 302 using OpenEasing
                FULL_RADIUS at OPEN_MS using OpenEasing
            })
        }
        launch {
            boxAlpha.animateTo(1f, keyframes {
                durationMillis = OPEN_MS
                0f at 0
                1f at 110 using OpenEasing
                1f at OPEN_MS
            })
        }
        launch {
            squashY.animateTo(1f, keyframes {
                durationMillis = OPEN_MS
                0.6f at 0
                1f at 110 using OpenEasing
                1f at OPEN_MS
            })
        }
        launch {
            contentAlpha.animateTo(1f, keyframes {
                durationMillis = OPEN_MS
                0f at 0
                0f at 220
                1f at OPEN_MS using OpenEasing
            })
        }
        launch {
            contentScale.animateTo(1f, keyframes {
                durationMillis = OPEN_MS
                0.85f at 0
                0.85f at 220
                1f at OPEN_MS using OpenEasing
            })
        }
        launch {
            contentOffsetY.animateTo(0f, keyframes {
                durationMillis = OPEN_MS
                3f at 0
                3f at 220
                0f at OPEN_MS using OpenEasing
            })
        }
    }

    // Chuỗi ĐÓNG — easing êm không nảy, y hệt ScoreIsland.
    LaunchedEffect(leaving) {
        if (!leaving) return@LaunchedEffect
        launch {
            width.animateTo(36f, keyframes {
                durationMillis = CLOSE_MS
                FULL_WIDTH at 0
                280f at 180 using CloseEasing
                36f at 337 using CloseEasing
                36f at CLOSE_MS
            })
        }
        launch {
            height.animateTo(36f, keyframes {
                durationMillis = CLOSE_MS
                FULL_HEIGHT at 0
                52f at 180 using CloseEasing
                36f at 337 using CloseEasing
                36f at CLOSE_MS
            })
        }
        launch {
            radius.animateTo(18f, keyframes {
                durationMillis = CLOSE_MS
                FULL_RADIUS at 0
                26f at 180 using CloseEasing
                18f at 337 using CloseEasing
                18f at CLOSE_MS
            })
        }
        launch {
            boxAlpha.animateTo(0f, keyframes {
                durationMillis = CLOSE_MS
                1f at 0
                1f at 337
                0f at CLOSE_MS using CloseEasing
            })
        }
        launch {
            contentAlpha.animateTo(0f, keyframes {
                durationMillis = 160
                1f at 0
                0f at 160 using CloseEasing
            })
        }
    }

    if (!mounted) return

    val pct = if (total > 0) Math.round(correct.toDouble() / total * 100).toInt() else 0
    val visual = when {
        pct >= 90 -> ScoreVisual("trophy", Color(0xFFF59E0B), "Xuất sắc!")
        pct >= 70 -> ScoreVisual("thumbsup", Color(0xFF34D399), "Giỏi lắm!")
        pct >= 50 -> ScoreVisual("star", Color(0xFFA855F7), "Khá ổn!")
        else -> ScoreVisual("sad", Color(0xFFF472B6), "Cố lên nhé!")
    }

    Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.TopCenter) {
        Box(
            modifier = Modifier
                .width(width.value.dp)
                .height(height.value.dp)
                .graphicsLayer {
                    alpha = boxAlpha.value
                    scaleY = squashY.value
                }
                .background(
                    Brush.linearGradient(listOf(Color(0xF7120608), Color(0xF71E0A16))),
                    RoundedCornerShape(radius.value.dp)
                )
                .clickable { leaving = true }
        ) {
            // Ngưỡng thấp (60dp) + luôn dùng alpha/scale để ẩn/hiện — tránh
            // mount/unmount đột ngột giữa animation gây giật.
            if (width.value > 60f) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = contentAlpha.value
                            scaleX = contentScale.value
                            scaleY = contentScale.value
                            translationY = contentOffsetY.value.dp.toPx()
                        }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val iconPulse by rememberPulseAlpha()
                    Box {
                        // Glow halo phía sau icon — tương đương @keyframes lp-glow-pulse 1.2s ease-in-out infinite
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .graphicsLayer { alpha = 0.18f * iconPulse }
                                .background(visual.color, CircleShape)
                        )
                        Box(modifier = Modifier.graphicsLayer { alpha = iconPulse }) {
                            DashboardIcon(name = visual.icon, size = 22.dp, color = visual.color)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            DashboardIcon(name = "book", size = 10.dp, color = visual.color)
                            Text(text = visual.label.uppercase(), fontSize = 9.5.sp, fontWeight = FontWeight.Black, color = visual.color, letterSpacing = 0.7.sp, fontFamily = NunitoFontFamily)
                        }
                        Text(text = "Đúng $correct/$total câu · $pct%", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFFFCE4F0), fontFamily = NunitoFontFamily)
                    }
                }
            }
        }
    }
}

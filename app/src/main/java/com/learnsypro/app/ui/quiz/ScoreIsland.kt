package com.learnsypro.app.ui.quiz

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsypro.app.ui.dashboard.DashboardIcon
import com.learnsypro.app.ui.dashboard.MascotImage
import com.learnsypro.app.ui.dashboard.MascotPose
import com.learnsypro.app.ui.theme.NunitoFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ── ScoreIsland ──
 * Tương đương function ScoreIsland trong quiz-player.jsx.
 *
 * FIX hiệu ứng mở/đóng: bản cũ dùng animateDpAsState với 1 spring y hệt
 * cho cả 2 chiều, và vì component chỉ được đưa vào composition đúng lúc
 * target đã bằng 290dp, Compose khởi tạo animatable THẲNG ở giá trị đích
 * — nghĩa là lúc MỞ không hề có animation nào chạy cả (chỉ đóng mới thấy
 * mượt). Bản này dùng Animatable + keyframes{} để bám sát đúng chuỗi
 * @keyframes di-pill-in / di-pill-out / di-content-in của web: mở ra từ
 * 1 chấm tròn 36dp (pop nhẹ rồi nảy giãn thành pill 290x56), đóng lại
 * bằng easing êm khác hẳn (không nảy) — 2 easing convert 1:1 từ
 * cubic-bezier CSS gốc sang CubicBezierEasing của Compose.
 */

// cubic-bezier(.34,1.3,.64,1) — easing nảy nhẹ dùng lúc MỞ, y hệt web
private val OpenEasing = CubicBezierEasing(0.34f, 1.3f, 0.64f, 1f)
// cubic-bezier(.55,0,.45,1) — easing êm dùng lúc ĐÓNG, y hệt web
private val CloseEasing = CubicBezierEasing(0.55f, 0f, 0.45f, 1f)

private const val OPEN_MS = 550
private const val CLOSE_MS = 450

@Composable
fun ScoreIsland(
    visible: Boolean,
    pct: Double,
    correctCount: Int,
    wrongCount: Int,
    onClose: () -> Unit
) {
    var mounted by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }

    // Khai báo TRƯỚC early-return để chỉ khởi tạo 1 LẦN DUY NHẤT ở trạng thái
    // "chấm tròn đóng" — nhờ vậy animateTo() bên dưới mới thực sự chạy
    // animation mở từ nhỏ ra to, thay vì Compose set thẳng vào giá trị đích.
    val width = remember { Animatable(36f) }
    val height = remember { Animatable(36f) }
    val radius = remember { Animatable(18f) }
    val boxAlpha = remember { Animatable(0f) }
    val squashY = remember { Animatable(0.6f) } // scaleY lúc pop-in, giống web
    val contentAlpha = remember { Animatable(0f) }
    val contentScale = remember { Animatable(0.85f) }
    val contentOffsetY = remember { Animatable(3f) }

    LaunchedEffect(visible) {
        if (!visible) {
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

    // Chuỗi MỞ — bám @keyframes di-pill-in (550ms): pop chấm tròn ở 20%,
    // rồi nảy giãn ra pill full-size; nội dung chữ trễ tới 40% mới hiện.
    LaunchedEffect(mounted) {
        if (!mounted) return@LaunchedEffect
        launch {
            width.animateTo(290f, keyframes {
                durationMillis = OPEN_MS
                36f at 0
                36f at 110 using OpenEasing
                270f at 302 using OpenEasing
                290f at OPEN_MS using OpenEasing
            })
        }
        launch {
            height.animateTo(56f, keyframes {
                durationMillis = OPEN_MS
                36f at 0
                36f at 110 using OpenEasing
                52f at 302 using OpenEasing
                56f at OPEN_MS using OpenEasing
            })
        }
        launch {
            radius.animateTo(28f, keyframes {
                durationMillis = OPEN_MS
                18f at 0
                18f at 110 using OpenEasing
                26f at 302 using OpenEasing
                28f at OPEN_MS using OpenEasing
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

    // Chuỗi ĐÓNG — bám @keyframes di-pill-out (450ms), easing êm không nảy.
    LaunchedEffect(leaving) {
        if (!leaving) return@LaunchedEffect
        launch {
            width.animateTo(36f, keyframes {
                durationMillis = CLOSE_MS
                290f at 0
                270f at 180 using CloseEasing
                36f at 337 using CloseEasing
                36f at CLOSE_MS
            })
        }
        launch {
            height.animateTo(36f, keyframes {
                durationMillis = CLOSE_MS
                56f at 0
                52f at 180 using CloseEasing
                36f at 337 using CloseEasing
                36f at CLOSE_MS
            })
        }
        launch {
            radius.animateTo(18f, keyframes {
                durationMillis = CLOSE_MS
                28f at 0
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

    val rc = resultColor(pct)
    val label = when {
        pct >= 0.8 -> "Xuất sắc!"
        pct >= 0.5 -> "Khá tốt!"
        else -> "Cần ôn thêm"
    }

    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        // Mascot theo điểm số — chỉ hiện lúc pill đã mở đủ để có chỗ (tránh
        // đè lên nhau lúc đang animate pop-in từ chấm tròn nhỏ).
        if (width.value > 60f) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val mascotPose = when {
                    pct >= 0.8 -> MascotPose.PEACE_SIGN
                    pct >= 0.5 -> MascotPose.SMILE_WINK
                    else -> MascotPose.CRYING
                }
                MascotImage(
                    drawableRes = mascotPose,
                    sizeDp = 52,
                    modifier = Modifier.graphicsLayer { alpha = contentAlpha.value }
                )
                Spacer(modifier = Modifier.height(4.dp))
                ScoreIslandPill(width, height, radius, boxAlpha, squashY, contentAlpha, contentScale, contentOffsetY, rc, label, pct, correctCount, wrongCount) { leaving = true }
            }
        } else {
            ScoreIslandPill(width, height, radius, boxAlpha, squashY, contentAlpha, contentScale, contentOffsetY, rc, label, pct, correctCount, wrongCount) { leaving = true }
        }
    }
}

@Composable
private fun ScoreIslandPill(
    width: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    height: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    radius: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    boxAlpha: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    squashY: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    contentAlpha: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    contentScale: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    contentOffsetY: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    rc: Color,
    label: String,
    pct: Double,
    correctCount: Int,
    wrongCount: Int,
    onClick: () -> Unit
) {
        Box(
            modifier = Modifier
                .width(width.value.dp)
                .height(height.value.dp)
                .graphicsLayer {
                    alpha = boxAlpha.value
                    scaleY = squashY.value
                }
                .clip(RoundedCornerShape(radius.value.dp))
                .background(
                    Brush.linearGradient(listOf(Color(0xF70E060E), Color(0xF71A0A14)))
                )
                .border(1.5.dp, Color(0x17FFFFFF), RoundedCornerShape(radius.value.dp))
                .clickable { onClick() }
        ) {
            // Luôn giữ Row trong composition, chỉ ẩn/hiện bằng alpha/scale —
            // mount/unmount đột ngột giữa lúc đang animate mới là thứ gây giật.
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
                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(rc.copy(alpha = 0.15f))
                            .border(1.5.dp, rc.copy(alpha = 0.33f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        val icon = when {
                            pct >= 0.8 -> "check"
                            pct >= 0.5 -> "star"
                            else -> "sad"
                        }
                        DashboardIcon(name = icon, size = 15.dp, color = rc)
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "KẾT QUẢ BÀI THI",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = rc,
                            letterSpacing = 0.6.sp,
                            fontFamily = NunitoFontFamily
                        )
                        Text(
                            text = "${fmtS(pct * 10)}/10 · $label",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFF0E6FF),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = NunitoFontFamily
                        )
                    }

                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        ResultBadge(count = correctCount, color = Color(0xFF10B981), icon = "check")
                        ResultBadge(count = wrongCount, color = Color(0xFFF87171), icon = "close")
                    }
                }
            }
        }
    }

@Composable
private fun ResultBadge(count: Int, color: Color, icon: String) {
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(text = count.toString(), fontSize = 9.sp, fontWeight = FontWeight.Black, color = color, fontFamily = NunitoFontFamily)
        DashboardIcon(name = icon, size = 8.dp, color = color)
    }
}

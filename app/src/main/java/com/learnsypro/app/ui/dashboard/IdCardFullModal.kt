package com.learnsypro.app.ui.dashboard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.learnsypro.app.ui.theme.NunitoFontFamily
import kotlinx.coroutines.delay

/**
 * ── IdCardFullModal ──
 * Tương đương function IdCardFullModal({student,avatarUrl,dark,liteMode,avgPct,
 * streak,badge,totalDone,onClose}) trong dashboard.jsx.
 *
 * Modal full-screen mở khi bấm avatar ở hero — thẻ ID acrylic 3D phong cách
 * hologram: ảnh chuyển luminosity + neon gradient overlay + lưới wireframe
 * + scanline quét dọc liên tục. Tilt 3D theo cử chỉ kéo/chạm (thay onMouseMove/
 * onTouchMove của web).
 *
 * liteMode=true → tắt hết hiệu ứng hologram (giữ ảnh màu bình thường), tắt
 * tilt động — đúng logic gốc để tiết kiệm hiệu năng máy yếu.
 */
@Composable
fun IdCardFullModal(
    studentName: String,
    avatarUrl: String?,
    dark: Boolean,
    liteMode: Boolean,
    avgPct: Double,
    streak: Int,
    badge: RankBadge,
    totalDone: Int,
    username: String,
    onClose: () -> Unit
) {
    var rotX by remember { mutableStateOf(6f) }
    var rotY by remember { mutableStateOf(0f) }
    var mx by remember { mutableStateOf(0.5f) }
    var my by remember { mutableStateOf(0.35f) }

    fun resetTilt() {
        rotX = 6f; rotY = 0f; mx = 0.5f; my = 0.35f
    }

    val animRotX by animateFloatAsState(rotX, spring(stiffness = 180f), label = "idRotX")
    val animRotY by animateFloatAsState(rotY, spring(stiffness = 180f), label = "idRotY")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xEB280A1E), Color(0xF5000000)),
                    center = Offset.Unspecified
                )
            )
            .clickable(onClick = onClose) // tap nền để đóng, giống bản web click ra ngoài
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        // Nút đóng
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(36.dp)
                .background(Color(0x1AFFFFFF), CircleShape)
                .border(1.dp, Color(0x33FFFFFF), CircleShape)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            DashboardIcon(name = "close", size = 16.dp, color = Color.White)
        }

        // Sparkle trang trí góc
        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 60.dp, end = 20.dp)) {
            val sparkleRotationState = if (liteMode) null else rememberSpinRotation4s()
            DashboardIcon(
                name = "sparkle",
                size = 26.dp,
                color = Color.White,
                modifier = Modifier.graphicsLayer { rotationZ = sparkleRotationState?.value ?: 0f; alpha = 0.6f }
            )
        }

        // Thẻ ID chính — clickable(null) để chặn tap-to-close khi bấm vào thẻ
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .aspectRatio(0.62f)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = {} // chặn propagation lên nền
                )
                .pointerInput(liteMode) {
                    if (liteMode) return@pointerInput
                    detectDragGestures(
                        onDrag = { change, _ ->
                            val px = (change.position.x / size.width).coerceIn(0f, 1f)
                            val py = (change.position.y / size.height).coerceIn(0f, 1f)
                            rotY = (px - 0.5f) * 20f
                            rotX = (0.5f - py) * 14f
                            mx = px
                            my = py
                        },
                        onDragEnd = { resetTilt() },
                        onDragCancel = { resetTilt() }
                    )
                }
                .graphicsLayer {
                    rotationX = animRotX
                    rotationY = animRotY
                    cameraDistance = 10f * density
                }
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0x1AFFFFFF), Color(0x0DF472B6), Color(0x1AA855F7))
                    )
                )
                .border(1.5.dp, Color(0x59FFB4DC), RoundedCornerShape(26.dp))
                .padding(horizontal = 18.dp, vertical = 20.dp)
        ) {
            // Sheen theo vị trí kéo
            if (!liteMode) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(this.size.width * mx, this.size.height * my)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                            center = center,
                            radius = this.size.maxDimension * 0.5f
                        ),
                        radius = this.size.maxDimension * 0.5f,
                        center = center
                    )
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // ── Header: avatar nhỏ + tên + verified ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, Color(0x80FFFFFF), CircleShape)
                    ) {
                        LetterAvatar(name = studentName, size = 38.dp, dark = true, animate = true, avatarUrl = avatarUrl)
                    }
                    Text(
                        text = studentName,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(
                                Brush.linearGradient(listOf(Color(0xFFFDE68A), Color(0xFFF59E0B))),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        DashboardIcon(name = "check", size = 13.dp, color = Color(0xFF78350F))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Portrait lớn hologram wireframe ──
                var portraitOk by remember(avatarUrl) { mutableStateOf(!avatarUrl.isNullOrBlank()) }
                var portraitRetryCount by remember(avatarUrl) { mutableIntStateOf(0) }
                var portraitRetryUrl by remember(avatarUrl) { mutableStateOf(avatarUrl) }
                var portraitRetryTrigger by remember(avatarUrl) { mutableIntStateOf(0) }
                val portraitMaxRetries = 3
                val portraitRetryDelays = longArrayOf(600L, 1500L, 3000L)

                LaunchedEffect(avatarUrl, portraitRetryTrigger) {
                    if (portraitRetryTrigger > 0 && portraitRetryTrigger <= portraitMaxRetries && !avatarUrl.isNullOrBlank()) {
                        delay(portraitRetryDelays[portraitRetryTrigger - 1])
                        val sep = if (avatarUrl.contains("?")) "&" else "?"
                        portraitRetryUrl = "$avatarUrl${sep}_r=${System.currentTimeMillis()}"
                    }
                }
                val onPortraitError: () -> Unit = {
                    if (portraitRetryCount < portraitMaxRetries) {
                        portraitRetryCount += 1
                        portraitRetryTrigger = portraitRetryCount
                    } else {
                        portraitOk = false
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0x24F472B6), Color(0x59000000))
                            )
                        )
                        .border(1.dp, Color(0x40FF96C8), RoundedCornerShape(18.dp))
                ) {
                    if (!avatarUrl.isNullOrBlank() && portraitOk) {
                        if (liteMode) {
                            AsyncImage(
                                model = portraitRetryUrl,
                                contentDescription = studentName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                onError = { onPortraitError() }
                            )
                        } else {
                            // saturate(0) = desaturate hoàn toàn -> luminosity/wireframe look
                            AsyncImage(
                                model = portraitRetryUrl,
                                contentDescription = studentName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                                alpha = 0.92f,
                                onError = { onPortraitError() }
                            )
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Box(modifier = Modifier.graphicsLayer { scaleX = 2.1f; scaleY = 2.1f }) {
                                LetterAvatar(name = studentName, size = 64.dp, dark = true, animate = true, avatarUrl = null)
                            }
                        }
                    }

                    // Overlay hologram: gradient neon + lưới + scanline quét — chỉ khi !liteMode
                    if (!liteMode) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0x40F472B6), Color(0x40A855F7), Color(0x4022D3EE))
                                    )
                                )
                        )
                        HologramGrid(modifier = Modifier.fillMaxSize())
                        ScanlineSweep(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.26f).align(Alignment.TopCenter))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ── Stats dưới thẻ ──
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .background(badge.gradient, RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            DashboardIcon(name = badge.icon, size = 11.dp, color = Color.White)
                            Text(text = badge.label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (streak > 0) {
                        Box(
                            modifier = Modifier
                                .background(
                                    Brush.linearGradient(listOf(Color(0xFFFDE68A), Color(0xFFFBBF24))),
                                    RoundedCornerShape(50)
                                )
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                DashboardIcon(name = "fire", size = 11.dp, color = Color(0xFF92400E))
                                Text(text = "$streak ngày", color = Color(0xFF92400E), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "ID: ${username.uppercase().take(14)}\n" +
                        "ĐIỂM TB: ${avgPct.toInt()}/100 · ĐÃ HỌC: $totalDone BÀI\n" +
                        "STATUS: ĐÃ XÁC THỰC",
                    color = Color(0x8CFFFFFF),
                    fontSize = 10.sp,
                    lineHeight = 17.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}

/** Lưới wireframe mờ mô phỏng repeating-linear-gradient ngang/dọc của bản CSS gốc */
@Composable
private fun HologramGrid(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val step = 8.dp.toPx()
        val lineColor = Color.White.copy(alpha = 0.25f)
        var x = 0f
        while (x < size.width) {
            drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += step
        }
    }
}

/** Vệt sáng quét dọc liên tục — tương đương @keyframes bb-scanline 2.6s ease-in-out infinite */
@Composable
private fun ScanlineSweep(modifier: Modifier = Modifier) {
    // Lite Mode: hiệu ứng quét thuần trang trí trên thẻ ID, ẩn hẳn thay vì animate.
    if (com.learnsypro.app.ui.theme.LocalLiteMode.current) return

    val transition = rememberInfiniteTransition(label = "scanline")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = androidx.compose.animation.core.EaseInOut),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanlineProgress"
    )
    Box(
        modifier = modifier.graphicsLayer {
            translationY = progress * (4 * 24f) // dịch chuyển dọc trong vùng portrait, tỉ lệ tương đối
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0x59FFFFFF), Color.Transparent)
                    )
                )
        )
    }
}

/** Xoay liên tục 360° trong 4s — dùng cho sparkle góc modal */
@Composable
private fun rememberSpinRotation4s(): androidx.compose.runtime.State<Float> {
    if (com.learnsypro.app.ui.theme.LocalLiteMode.current) {
        return remember { mutableStateOf(0f) }
    }
    val transition = rememberInfiniteTransition(label = "sparkleSpin4s")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "sparkleSpinRotation"
    )
}

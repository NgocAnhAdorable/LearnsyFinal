package com.learnsypro.app.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * ── AcrylicAvatarCard ──
 * Tương đương function AcrylicAvatarCard({name,avatarUrl,dark,size,liteMode,verified})
 * trong dashboard.jsx. Thẻ mica bán trong suốt bọc quanh LetterAvatar, viền
 * neon phát sáng ở dark mode, nghiêng nhẹ theo hướng kéo chạm (giả lập tilt
 * 3D vì Compose không có onMouseMove — dùng pointerInput detectDragGestures
 * để bắt cử chỉ kéo/chạm tương đương onTouchMove của bản web).
 *
 * liteMode=true → tắt hiệu ứng tilt + glow radial để tiết kiệm hiệu năng,
 * đúng logic gốc (kiểm tra `if(liteMode||!ref.current)return`).
 */
@Composable
fun AcrylicAvatarCard(
    name: String,
    avatarUrl: String?,
    dark: Boolean,
    size: androidx.compose.ui.unit.Dp = 58.dp,
    liteMode: Boolean = false,
    verified: Boolean = true,
    modifier: Modifier = Modifier
) {
    var rotX by remember { mutableStateOf(0f) }
    var rotY by remember { mutableStateOf(0f) }
    var glowMx by remember { mutableStateOf(0.5f) }
    var glowMy by remember { mutableStateOf(0.5f) }

    val animRotX by animateFloatAsState(rotX, spring(), label = "tiltRotX")
    val animRotY by animateFloatAsState(rotY, spring(), label = "tiltRotY")

    fun resetTilt() {
        rotX = 0f; rotY = 0f; glowMx = 0.5f; glowMy = 0.5f
    }

    val cardSize = size + 22.dp
    val glowColor = if (dark) Color(0x8CF472B6) else Color(0x59A855F7)
    val borderColor = if (dark) Color(0x73F472B6) else Color(0x4DA855F7)
    val cardBg = if (dark) {
        Brush.linearGradient(
            listOf(Color(0x1AFFFFFF), Color(0x0FF472B6), Color(0x1AA855F7))
        )
    } else {
        Brush.linearGradient(
            listOf(Color(0xBFFFFFFF), Color(0x8CFCE7F3), Color(0xA6E9D5FF))
        )
    }

    Box(
        modifier = modifier
            .size(cardSize)
            .pointerInput(liteMode) {
                if (liteMode) return@pointerInput
                detectDragGestures(
                    onDrag = { change, _ ->
                        val px = (change.position.x / size.toPx()).coerceIn(0f, 1f)
                        val py = (change.position.y / size.toPx()).coerceIn(0f, 1f)
                        rotY = (px - 0.5f) * 18f
                        rotX = (0.5f - py) * 14f
                        glowMx = px
                        glowMy = py
                    },
                    onDragEnd = { resetTilt() },
                    onDragCancel = { resetTilt() }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationX = animRotX
                    rotationY = animRotY
                    cameraDistance = 12f * density
                }
                .clip(RoundedCornerShape(18.dp))
                .background(cardBg)
                .border(1.4.dp, borderColor, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Sheen theo vị trí kéo — mô phỏng radial-gradient(circle at mx% my%)
            if (!liteMode) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(this.size.width * glowMx, this.size.height * glowMy)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (dark) 0.28f else 0.5f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = this.size.maxDimension * 0.55f
                        ),
                        radius = this.size.maxDimension * 0.55f,
                        center = center
                    )
                }
            }

            // Viền neon glow ở dark mode
            if (dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, glowColor.copy(alpha = if (liteMode) 0.4f else 0.85f), RoundedCornerShape(18.dp))
                )
            }

            // Badge verified — góc trên phải
            if (verified) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(14.dp)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFFFDE68A), Color(0xFFF59E0B))),
                            androidx.compose.foundation.shape.CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    DashboardIcon(name = "check", size = 8.dp, color = Color(0xFF78350F))
                }
            }

            // Avatar chính, đẩy lên "trên" theo trục Z (translateZ mô phỏng)
            Box(
                modifier = Modifier.graphicsLayer {
                    translationX = 0f
                    translationY = 0f
                    shadowElevation = 16f
                }
            ) {
                LetterAvatar(
                    name = name,
                    size = size,
                    dark = dark,
                    animate = true,
                    avatarUrl = avatarUrl
                )
            }
        }
    }
}

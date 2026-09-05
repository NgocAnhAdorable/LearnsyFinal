package com.learnsypro.app.ui.quiz

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.learnsypro.app.ui.dashboard.LevelState
import com.learnsypro.app.ui.dashboard.MascotImage
import com.learnsypro.app.ui.dashboard.MascotPose
import com.learnsypro.app.ui.theme.NunitoFontFamily

/**
 * ── LevelUpModal ──
 * Hiện khi QuizViewModel phát _levelUpEvent (level tăng sau khi nộp bài).
 * Tách riêng khỏi ScoreIsland vì đây là sự kiện hiếm/lớn hơn — không nhét
 * chung vào pill kết quả vốn đã canh animation timing rất khít.
 */
@Composable
fun LevelUpModal(levelState: LevelState?, dark: Boolean, onDismiss: () -> Unit) {
    val visible = levelState != null
    if (!visible) return

    Dialog(onDismissRequest = onDismiss) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.8f),
            exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.8f)
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF2A1030), Color(0xFF1A0A20))
                        )
                    )
                    .padding(28.dp)
                    .clickable(onClick = onDismiss),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MascotImage(drawableRes = MascotPose.HEART_HANDS, sizeDp = 88)
                Text(
                    text = "LÊN CẤP!",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFFBBF24),
                    fontFamily = NunitoFontFamily
                )
                Text(
                    text = "Cấp ${levelState?.level ?: 1}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF0E6FF),
                    fontFamily = NunitoFontFamily
                )
                Text(
                    text = "Chạm để tiếp tục",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0x99F0E6FF),
                    fontFamily = NunitoFontFamily
                )
            }
        }
    }
}

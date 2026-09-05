package com.learnsypro.app.ui.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsypro.app.ui.dashboard.DashboardIcon
import com.learnsypro.app.ui.theme.NunitoFontFamily
import com.learnsypro.app.ui.theme.rememberPopState

/**
 * ── WarnModal ──
 * Tương đương Warn modal trong quiz-player.jsx — cảnh báo còn câu chưa trả
 * lời trước khi nộp bài, cho phép "Xem lại" (nhảy tới câu đầu tiên chưa
 * làm) hoặc "Nộp thôi!" (bỏ qua, nộp luôn).
 */
@Composable
fun WarnModal(
    unansweredQuestionNumbers: List<Int>?,
    dark: Boolean,
    onReview: (firstUnansweredIndex: Int) -> Unit,
    onSubmitAnyway: () -> Unit,
    onDismiss: () -> Unit
) {
    if (unansweredQuestionNumbers == null) return

    val textColor = if (dark) Color(0xFFFDE68A) else Color(0xFFC89700)
    val subColor = if (dark) Color(0xFFC0A0D8) else Color(0xFF806090)
    val bgGradient = if (dark) {
        Brush.linearGradient(listOf(Color(0xFF1A0640), Color(0xFF110228)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFFFF5E8), Color(0xFFFFF0F6)))
    }

    val message = if (unansweredQuestionNumbers.size == 1) {
        "Câu ${unansweredQuestionNumbers[0]} chưa trả lời."
    } else {
        "${unansweredQuestionNumbers.size} câu chưa trả lời: câu ${unansweredQuestionNumbers.joinToString(", ")}."
    }

    val (popScale, popAlpha) = rememberPopState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xDE080116))
            .clickable(onClick = onDismiss)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 300.dp)
                .graphicsLayer { scaleX = popScale; scaleY = popScale; alpha = popAlpha }
                .clickable(enabled = false) {}
                .background(bgGradient, RoundedCornerShape(26.dp))
                .border(1.5.dp, Color(0x4DFCD34D), RoundedCornerShape(26.dp))
                .padding(horizontal = 22.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color(0x1FFCD34D), CircleShape)
                    .border(2.dp, Color(0x4DFCD34D), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                DashboardIcon(name = "sad", size = 26.dp, color = Color(0xFFEEB800))
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Còn câu chưa làm!",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = textColor,
                fontFamily = NunitoFontFamily
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                fontSize = 13.sp,
                color = subColor,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                fontFamily = NunitoFontFamily
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(1.5.dp, Color(0x47FF96C8), RoundedCornerShape(50))
                        .clickable {
                            onReview((unansweredQuestionNumbers.first() - 1).coerceAtLeast(0))
                        }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Xem lại",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (dark) Color(0xFFFBAFCE) else Color(0xFFE8547A),
                        fontFamily = NunitoFontFamily
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Brush.linearGradient(listOf(Color(0xFFF472B6), Color(0xFFA855F7))), RoundedCornerShape(50))
                        .clickable(onClick = onSubmitAnyway)
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Nộp thôi!", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = NunitoFontFamily)
                }
            }
        }
    }
}

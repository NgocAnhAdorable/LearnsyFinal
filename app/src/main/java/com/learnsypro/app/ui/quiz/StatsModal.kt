package com.learnsypro.app.ui.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsypro.app.ui.dashboard.DashboardIcon
import com.learnsypro.app.ui.dashboard.ProgressBar
import com.learnsypro.app.ui.theme.Baloo2FontFamily
import com.learnsypro.app.ui.theme.NunitoFontFamily
import com.learnsypro.app.ui.theme.rememberPopState

private fun questionTypeAbbr(type: QuestionType): String = when (type) {
    QuestionType.TRUE_FALSE -> "DS"
    QuestionType.MULTIPLE -> "TN"
    QuestionType.MULTI_SELECT -> "CN"
    QuestionType.FILL_BLANK -> "DT"
}

/**
 * ── StatsModal ──
 * Tương đương Stats modal trong quiz-player.jsx: điểm lớn + progress bar,
 * 2 thẻ (streak tốt nhất, thời gian TB/câu), danh sách từng câu đúng/sai,
 * nút Làm lại.
 */
@Composable
fun StatsModal(
    visible: Boolean,
    score: Int,
    total: Int,
    pct: Double,
    bestStreak: Int,
    answerTimesSec: Map<Int, Int>,
    questions: List<Question>,
    answers: List<Answer>,
    dark: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    if (!visible) return
    val rc = resultColor(pct)
    val textColor = if (dark) Color(0xFFF2EAFF) else Color(0xFF2D1245)
    val subColor = if (dark) Color(0xFF9B7FC0) else Color(0xFF8060A0)
    val bgGradient = if (dark) {
        Brush.linearGradient(listOf(Color(0xFF1A0640), Color(0xFF110228)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFFFF0F8), Color(0xFFF4E8FF)))
    }

    val avgTime = if (answerTimesSec.isNotEmpty()) answerTimesSec.values.sum() / answerTimesSec.size else 0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xDE080116))
            .clickable(onClick = onDismiss)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        val (popScale, popAlpha) = rememberPopState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 340.dp)
                .graphicsLayer { scaleX = popScale; scaleY = popScale; alpha = popAlpha }
                .clickable(enabled = false) {}
                .background(bgGradient, RoundedCornerShape(26.dp))
                .border(1.5.dp, if (dark) Color(0x2EC4B5FD) else Color(0x80DCB4FF), RoundedCornerShape(26.dp))
                .padding(horizontal = 19.dp, vertical = 22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Thống kê bài làm", fontSize = 14.sp, fontWeight = FontWeight.Black, color = textColor, fontFamily = NunitoFontFamily, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(subColor.copy(alpha = 0.12f), CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    DashboardIcon(name = "close", size = 13.dp, color = subColor)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(text = fmtS(pct * 10), fontSize = 40.sp, fontWeight = FontWeight.Black, color = rc, fontFamily = Baloo2FontFamily)
                    Text(text = "/10", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = rc.copy(alpha = 0.35f), fontFamily = Baloo2FontFamily)
                }
                Text(text = "$score / $total điểm", fontSize = 12.sp, color = subColor, fontFamily = NunitoFontFamily)
                Spacer(modifier = Modifier.height(12.dp))
                ProgressBar(pct = (pct * 100).toFloat(), color = rc, dark = dark)
                Spacer(modifier = Modifier.height(10.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                StatMiniCard(label = "Streak tốt nhất", value = "$bestStreak câu", color = Color(0xFFEEB800), dark = dark, modifier = Modifier.weight(1f))
                StatMiniCard(label = "Trung bình/câu", value = "${avgTime}s", color = Color(0xFFB07CF0), dark = dark, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(15.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 190.dp)
                    .background(if (dark) Color(0x09FFFFFF) else Color(0x0DB07CF0), RoundedCornerShape(16.dp))
                    .padding(horizontal = 13.dp, vertical = 11.dp)
            ) {
                Text(text = "TỪNG CÂU", fontSize = 9.sp, fontWeight = FontWeight.Black, color = subColor, letterSpacing = 0.8.sp, fontFamily = NunitoFontFamily)
                Spacer(modifier = Modifier.height(9.dp))
                LazyColumn {
                    // key = id thật của câu hỏi, không dùng index — tránh Compose
                    // recompose nhầm khi list re-render (ví dụ đóng/mở lại modal).
                    items(questions.size, key = { qi -> questions[qi].id }) { qi ->
                        val q2 = questions[qi]
                        val ok2 = isAnswerCorrect(q2, answers.getOrElse(qi) { Answer.Empty })
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(text = "Câu ${qi + 1}", fontSize = 12.sp, color = subColor, fontWeight = FontWeight.SemiBold, fontFamily = NunitoFontFamily)
                                Text(text = questionTypeAbbr(q2.type), fontSize = 9.sp, color = subColor.copy(alpha = 0.4f), fontFamily = NunitoFontFamily)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                answerTimesSec[qi]?.let {
                                    Text(text = "${it}s", fontSize = 10.sp, color = subColor, fontFamily = NunitoFontFamily)
                                }
                                Box(
                                    modifier = Modifier
                                        .background(if (ok2) Color(0x1A10B981) else Color(0x14EF4444), RoundedCornerShape(50))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (ok2) "Đúng" else "Sai",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (ok2) Color(0xFF10B981) else Color(0xFFEF4444),
                                        fontFamily = NunitoFontFamily
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (bestStreak >= 3) {
                Spacer(modifier = Modifier.height(11.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x1AFCD34D), RoundedCornerShape(10.dp))
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Streak tốt nhất: $bestStreak câu liên tiếp!", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFFC89700), fontFamily = NunitoFontFamily)
                }
            }

            Spacer(modifier = Modifier.height(11.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(Color(0xFFF472B6), Color(0xFFA855F7))), RoundedCornerShape(50))
                    .clickable(onClick = onRetry)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Làm lại", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White, fontFamily = NunitoFontFamily)
            }
        }
    }
}

@Composable
private fun StatMiniCard(label: String, value: String, color: Color, dark: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(if (dark) Color(0x0BFFFFFF) else color.copy(alpha = 0.09f), RoundedCornerShape(14.dp))
            .border(1.dp, if (dark) Color(0x14FFFFFF) else color.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label.uppercase(), fontSize = 9.sp, color = if (dark) Color(0xFF9B7FC0) else Color(0xFF8060A0), fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, fontFamily = NunitoFontFamily)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Black, color = color, fontFamily = NunitoFontFamily)
    }
}

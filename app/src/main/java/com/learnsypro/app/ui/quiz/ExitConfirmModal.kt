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
 * ── ExitConfirmModal ──
 * Hỏi xác nhận trước khi thoát giữa chừng khi đang làm bài (chưa nộp và đã
 * trả lời ít nhất 1 câu) — "Thoát" sẽ xoá toàn bộ tiến độ đang làm dở, lần
 * sau vào lại phải làm lại từ đầu (không giữ auto-save như trước).
 *
 * Cùng ngôn ngữ hình ảnh với WarnModal (nền tối mờ, card bo góc 26dp, viền
 * mảnh 1.5dp) nhưng dùng tông đỏ/hồng cảnh báo mất dữ liệu thay vì tông vàng
 * "còn câu chưa làm" — hai tình huống khác nhau nên không dùng chung 1 modal.
 */
@Composable
fun ExitConfirmModal(
    visible: Boolean,
    dark: Boolean,
    onKeepGoing: () -> Unit,
    onConfirmExit: () -> Unit
) {
    if (!visible) return

    val textColor = if (dark) Color(0xFFFECDD3) else Color(0xFFE11D48)
    val subColor = if (dark) Color(0xFFC0A0D8) else Color(0xFF806090)
    val bgGradient = if (dark) {
        Brush.linearGradient(listOf(Color(0xFF2A0618), Color(0xFF110228)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFFFF0F3), Color(0xFFFFF0F6)))
    }

    val (popScale, popAlpha) = rememberPopState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xDE080116))
            // Bấm ra ngoài card = huỷ thoát, coi như bấm "Tiếp tục học" —
            // an toàn hơn mặc định vì lỡ tay chạm ngoài không làm mất bài.
            .clickable(onClick = onKeepGoing)
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
                .border(1.5.dp, Color(0x4DFB7185), RoundedCornerShape(26.dp))
                .padding(horizontal = 22.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color(0x1FFB7185), CircleShape)
                    .border(2.dp, Color(0x4DFB7185), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                DashboardIcon(name = "sad", size = 26.dp, color = Color(0xFFE11D48))
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Thoát bài tập?",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = textColor,
                fontFamily = NunitoFontFamily
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Bạn đang làm dở bài này. Nếu thoát bây giờ, toàn bộ bài làm sẽ mất và lần sau phải làm lại từ đầu.",
                fontSize = 13.sp,
                color = subColor,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                fontFamily = NunitoFontFamily
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(1.5.dp, Color(0x47FF96C8), RoundedCornerShape(50))
                        .clickable(onClick = onKeepGoing)
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Tiếp tục học",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (dark) Color(0xFFFBAFCE) else Color(0xFFE8547A),
                        fontFamily = NunitoFontFamily
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Brush.linearGradient(listOf(Color(0xFFFB7185), Color(0xFFE11D48))), RoundedCornerShape(50))
                        .clickable(onClick = onConfirmExit)
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Thoát", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = NunitoFontFamily)
                }
            }
        }
    }
}

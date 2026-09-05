package com.learnsypro.app.ui.quiz

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsypro.app.ui.dashboard.DashboardIcon
import com.learnsypro.app.ui.theme.NunitoFontFamily

enum class DotState { UNANSWERED, ANSWERED, CORRECT, WRONG }

/**
 * ── NavDots ──
 * Tương đương thanh chấm điều hướng câu hỏi dưới cùng trong quiz-player.jsx.
 * Chấm hiện tại to hơn, bo góc (thay vì tròn hẳn) + viền sáng tím nhẹ, có
 * flag icon nếu đã đánh dấu, màu theo trạng thái (chỉ hiện đúng/sai sau khi
 * submit hoặc practiceMode). Tự cuộn ngang để luôn thấy chấm đang chọn khi
 * `current` đổi (chuyển câu hoặc cuộn trong chế độ scroll).
 */
@Composable
fun NavDots(
    total: Int,
    current: Int,
    states: List<DotState>,
    flags: List<Boolean>,
    dark: Boolean,
    onDotClick: (Int) -> Unit
) {
    val C = quizColors(dark)
    val listState = rememberLazyListState()
    LaunchedEffect(current, total) {
        if (total <= 0) return@LaunchedEffect
        // Lùi lại vài chấm để chấm hiện tại không dính sát mép trái —
        // gần giống hiệu ứng "canh giữa" mà không cần đo pixel viewport.
        listState.animateScrollToItem((current - 2).coerceIn(0, total - 1))
    }
    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(total, key = { it }) { idx ->
            val isActive = idx == current
            val state = states.getOrElse(idx) { DotState.UNANSWERED }
            val color = when (state) {
                DotState.CORRECT -> Color(0xFF10B981)
                DotState.WRONG -> Color(0xFFEF4444)
                DotState.ANSWERED -> C.navBtnText
                DotState.UNANSWERED -> if (dark) Color(0x33FFFFFF) else Color(0x1F000000)
            }

            val size by animateDpAsState(if (isActive) 30.dp else 24.dp, spring(), label = "dotSize")
            val shape = CircleShape

            Box(
                modifier = Modifier
                    .size(size)
                    .background(
                        if (state == DotState.UNANSWERED) Color.Transparent else color.copy(alpha = 0.18f),
                        shape
                    )
                    .border(
                        if (isActive) 2.dp else 1.4.dp,
                        if (isActive) Color(0xFFC4A0FF).copy(alpha = 0.75f) else color.copy(alpha = 0.5f),
                        shape
                    )
                    .clickable { onDotClick(idx) },
                contentAlignment = Alignment.Center
            ) {
                if (flags.getOrElse(idx) { false }) {
                    DashboardIcon(name = "star", size = 10.dp, color = Color(0xFFF59E0B))
                } else {
                    Text(
                        text = (idx + 1).toString(),
                        fontSize = if (isActive) 12.sp else 10.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isActive) C.navBtnText else C.textMid,
                        fontFamily = NunitoFontFamily
                    )
                }
            }
        }
    }
}

package com.learnsypro.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsypro.app.data.LeaderboardEntry
import com.learnsypro.app.data.LeaderboardRepository
import com.learnsypro.app.data.LeaderboardResult
import com.learnsypro.app.ui.theme.NunitoFontFamily

/**
 * ── ClassLeaderboardSheet ──
 * Bảng xếp hạng XP trong PHẠM VI LỚP của học sinh (không so toàn trường —
 * tránh áp lực so sánh diện rộng, tăng ganh đua lành mạnh trong nhóm quen
 * biết). Dữ liệu lấy qua Edge Function `class-leaderboard` (service role,
 * đã ẩn danh username/id thật) — không tự query chéo học sinh bằng anon
 * key ở client.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassLeaderboardSheet(
    visible: Boolean,
    studentId: String?,
    dark: Boolean,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val C = dashboardColors(dark)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var className by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<LeaderboardEntry>>(emptyList()) }

    LaunchedEffect(studentId) {
        if (studentId.isNullOrBlank()) {
            loading = false
            errorMsg = "Chưa đăng nhập"
            return@LaunchedEffect
        }
        loading = true
        errorMsg = null
        when (val result = LeaderboardRepository().fetchClassLeaderboard(studentId)) {
            is LeaderboardResult.Success -> {
                className = result.className
                entries = result.entries
            }
            is LeaderboardResult.Failure -> {
                errorMsg = result.message
            }
        }
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (dark) Color(0xFF1A0E20) else Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DashboardIcon(name = "trophy", size = 20.dp, color = Color(0xFFF59E0B))
                Text(
                    text = if (className.isNotBlank()) "Xếp hạng lớp $className" else "Xếp hạng lớp",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    color = C.fg,
                    fontFamily = NunitoFontFamily
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Xếp theo tổng XP tích luỹ trong lớp của bạn",
                fontSize = 11.sp,
                color = C.sub,
                fontFamily = NunitoFontFamily
            )
            Spacer(modifier = Modifier.height(14.dp))

            when {
                loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFFA855F7))
                    }
                }
                errorMsg != null -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        MascotImage(drawableRes = MascotPose.ARMS_CROSSED, sizeDp = 56)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMsg ?: "",
                            fontSize = 12.sp,
                            color = C.sub,
                            fontFamily = NunitoFontFamily
                        )
                    }
                }
                entries.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Lớp chưa có dữ liệu XP",
                            fontSize = 12.sp,
                            color = C.sub,
                            fontFamily = NunitoFontFamily
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 420.dp)
                    ) {
                        items(entries) { entry ->
                            LeaderboardRow(entry = entry, dark = dark)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardRow(entry: LeaderboardEntry, dark: Boolean) {
    val C = dashboardColors(dark)
    val medalColor = when (entry.rank) {
        1 -> Color(0xFFFBBF24)
        2 -> Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32)
        else -> null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (entry.isSelf) Color(0x1FA855F7) else Color.Transparent,
                RoundedCornerShape(14.dp)
            )
            .border(
                1.5.dp,
                if (entry.isSelf) Color(0x59A855F7) else Color.Transparent,
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    medalColor?.copy(alpha = 0.2f) ?: (if (dark) Color(0x1AFFFFFF) else Color(0x0F000000)),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (medalColor != null) {
                DashboardIcon(name = "medal", size = 14.dp, color = medalColor)
            } else {
                Text(
                    text = entry.rank.toString(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = C.sub,
                    fontFamily = NunitoFontFamily
                )
            }
        }
        Text(
            text = entry.displayName,
            fontSize = 13.sp,
            fontWeight = if (entry.isSelf) FontWeight.Black else FontWeight.Bold,
            color = C.fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            fontFamily = NunitoFontFamily
        )
        Text(
            text = "${entry.totalXp} XP",
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFFA855F7),
            fontFamily = NunitoFontFamily
        )
    }
}

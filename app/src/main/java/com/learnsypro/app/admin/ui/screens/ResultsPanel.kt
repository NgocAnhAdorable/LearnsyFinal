package com.learnsypro.app.admin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.learnsypro.app.admin.ui.ResultsViewModel
import com.learnsypro.app.admin.ui.ToastCenter
import com.learnsypro.app.admin.ui.theme.LearnsyColors

// Tương đương ResultsPanel trong dashboard.jsx
@Composable
fun ResultsPanel(colors: LearnsyColors, dark: Boolean = false, refreshKey: Int = 0, viewModel: ResultsViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    var showConfirmDelete by remember { mutableStateOf(false) }

    // FIX: trước đây LaunchedEffect(Unit) chỉ load() đúng 1 lần khi ResultsPanel
    // được tạo lần đầu. Vì viewModel() giữ nguyên instance qua composition, nếu
    // admin mở tab Kết quả trước khi học sinh nộp bài rồi quay lại sau, dữ liệu
    // không bao giờ tự làm mới -> luôn thấy "Chưa có kết quả" dù đã có bài nộp.
    // Key theo refreshKey (đổi mỗi lần vào lại tab RESULTS hoặc bấm nút làm mới)
    // để load() chạy lại đúng lúc cần.
    LaunchedEffect(refreshKey) { viewModel.load() }

    if (showConfirmDelete) {
        com.learnsypro.app.admin.ui.components.ConfirmDialog(
            title = "Xoá toàn bộ kết quả?",
            message = "Xoá toàn bộ ${state.results.size} kết quả? Hành động này không thể hoàn tác.",
            onDismiss = { showConfirmDelete = false },
            onConfirm = {
                viewModel.clearAll { success, msg ->
                    ToastCenter.show(msg, if (success) "🗑️" else "❌", Color(0xFFEF4444))
                }
            },
            colors = colors, dark = dark,
            confirmLabel = "Xoá", iconType = com.learnsypro.app.admin.ui.components.ConfirmIconType.DELETE
        )
    }

    when {
        state.loading -> {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFFF472B6))
                    Spacer(Modifier.height(14.dp))
                    Text("Đang tải kết quả...", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.text3)
                }
            }
        }
        state.error != null -> {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFEF4444).copy(alpha = 0.08f))
                        .border(1.5.dp, Color(0xFFEF4444).copy(alpha = 0.25f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Refresh, null, tint = Color(0xFFEF4444))
                }
                Text("Không tải được kết quả", fontSize = 15.sp, fontWeight = FontWeight.Black, color = colors.text2)
                Text(
                    state.error ?: "",
                    fontSize = 12.sp, color = colors.text3, textAlign = TextAlign.Center
                )
                TextButton(onClick = { viewModel.load() }) {
                    Icon(Icons.Default.Refresh, null, tint = Color(0xFFA855F7), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Thử lại", color = Color(0xFFA855F7), fontWeight = FontWeight.Black)
                }
            }
        }
        state.results.isEmpty() -> {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFA855F7).copy(alpha = 0.08f))
                        .border(1.5.dp, Color(0xFFA855F7).copy(alpha = 0.25f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Groups, null, tint = Color(0xFFA855F7))
                }
                Text("Chưa có kết quả làm bài", fontSize = 15.sp, fontWeight = FontWeight.Black, color = colors.text2)
                Text(
                    "Kết quả xuất hiện khi học sinh nộp bài. Dữ liệu lưu vào bảng quiz_results.",
                    fontSize = 12.sp, color = colors.text3, textAlign = TextAlign.Center
                )
            }
        }
        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFA855F7).copy(alpha = 0.08f))
                            .border(1.5.dp, Color(0xFFA855F7).copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                            .clickable { viewModel.load() }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Icon(Icons.Default.Refresh, "Làm mới", tint = Color(0xFFA855F7), modifier = Modifier.size(15.dp))
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFEF4444).copy(alpha = 0.08f))
                            .border(1.5.dp, Color(0xFFEF4444).copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                            .clickable(enabled = !state.clearing) { showConfirmDelete = true }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (state.clearing) {
                            CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = Color(0xFFEF4444))
                        } else {
                            Icon(Icons.Default.Delete, "Xoá toàn bộ", tint = Color(0xFFEF4444), modifier = Modifier.size(13.dp))
                        }
                        Text("Xoá toàn bộ", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFEF4444))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    MiniStat("Điểm TB", "${viewModel.avg()}%", Color(0xFFA855F7), colors, Modifier.weight(1f))
                    MiniStat("Đạt giỏi (≥80%)", "${viewModel.highCount()}", Color(0xFF10B981), colors, Modifier.weight(1f))
                    MiniStat("Tổng lượt", "${state.results.size}", Color(0xFFF472B6), colors, Modifier.weight(1f))
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.surface)
                        .border(1.5.dp, colors.border, RoundedCornerShape(18.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Phân bố điểm", fontSize = 12.sp, fontWeight = FontWeight.Black, color = colors.text2)
                    viewModel.scoreBins().forEach { (label, count) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.text3, modifier = Modifier.width(48.dp))
                            com.learnsypro.app.admin.ui.components.ShimmerProgressBar(
                                pct = if (state.results.isNotEmpty()) count.toFloat() / state.results.size else 0f,
                                color = Color(0xFFF472B6),
                                modifier = Modifier.weight(1f)
                            )
                            Text("$count", fontSize = 11.sp, fontWeight = FontWeight.Black, color = colors.text2)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.surface)
                        .border(1.5.dp, colors.border, RoundedCornerShape(18.dp))
                        .padding(vertical = 8.dp)
                ) {
                    state.results.forEachIndexed { i, r ->
                        val p = viewModel.pct(r)
                        val col = when {
                            p >= 85 -> Color(0xFF10B981)
                            p >= 70 -> Color(0xFFEAB308)
                            p >= 50 -> Color(0xFFF97316)
                            else -> Color(0xFFEF4444)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(col.copy(alpha = 0.15f))
                                    .border(1.5.dp, col.copy(alpha = 0.3f), RoundedCornerShape(11.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${i + 1}", fontSize = 11.sp, fontWeight = FontWeight.Black, color = col)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(r.displayStudentName.ifBlank { "Ẩn danh" }, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = colors.text, maxLines = 1)
                                Text(r.displayLessonTitle.ifBlank { "Không rõ bài" }, fontSize = 10.sp, color = colors.text3, maxLines = 1)
                            }
                            Text("${r.safeScore}/${r.safeTotal} ($p%)", fontSize = 13.sp, fontWeight = FontWeight.Black, color = col)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color, colors: LearnsyColors, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = colors.text3, maxLines = 1)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Black, color = color)
    }
}

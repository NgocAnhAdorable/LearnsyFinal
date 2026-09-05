package com.learnsypro.app.admin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsypro.app.admin.data.ListeningStatement
import com.learnsypro.app.admin.data.TfnmAnswer
import com.learnsypro.app.admin.data.countBlanks
import com.learnsypro.app.admin.ui.ListeningFormUiState
import com.learnsypro.app.admin.ui.ListeningFormViewModel
import com.learnsypro.app.admin.ui.ListeningListUiState
import com.learnsypro.app.admin.ui.ToastCenter
import com.learnsypro.app.admin.ui.theme.LearnsyColors

// Tương đương phần form ('tab==form') trong ListeningManager (listening-panel.jsx).
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ListeningFormContent(
    colors: LearnsyColors,
    formVm: ListeningFormViewModel,
    formState: ListeningFormUiState,
    listState: ListeningListUiState,
    onCancel: () -> Unit,
    onSaved: (isNew: Boolean) -> Unit,
    onMismatch: (blanks: Int, answers: Int) -> Unit,
    isSpeaking: Boolean,
    onSpeakForm: (String) -> Unit,
    ttsSpeed: Float,
    onSpeedChange: (Float) -> Unit
) {
    var wbInput by remember { mutableStateOf("") }
    var tagInput by remember { mutableStateOf("") }
    val blanks = countBlanks(formState.text)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.lavPale)
            .border(1.5.dp, colors.border2, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (formState.editingId != null) "Sửa câu Listening" else "Thêm câu Listening mới",
                fontSize = 13.sp, fontWeight = FontWeight.Black, color = colors.lav, modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onCancel) { Text("Quay lại", fontSize = 12.sp) }
        }

        Column {
            Row {
                Text("Đoạn văn để đọc (dùng ___ cho chỗ trống)", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = colors.text3)
                if (blanks > 0) Text(" · $blanks chỗ trống", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF059669))
            }
            Spacer(Modifier.height(5.dp))
            com.learnsypro.app.admin.ui.components.CompactTextField(
                value = formState.text, onValueChange = formVm::setText,
                placeholder = "VD: Trang An is famous ___ its beautiful landscape.",
                minLines = 5, singleLine = false, colors = colors,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(7.dp))
            // Hàng "Đồng bộ chỗ trống" + "Nghe thử" + chọn tốc độ — khớp đúng
            // layout web thật (2 pill + 4 nút tốc độ trên cùng 1 hàng, wrap khi hẹp).
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF059669).copy(alpha = 0.1f))
                        .border(1.dp, Color(0xFF059669).copy(alpha = 0.3f), RoundedCornerShape(999.dp))
                        .clickable {
                            formVm.syncBlanksFromText { n ->
                                if (n == 0) ToastCenter.show("! Không tìm thấy ___ trong văn bản", "⚠️", Color(0xFFF59E0B))
                                else ToastCenter.show("✓ Đồng bộ $n chỗ trống từ văn bản", "✅", Color(0xFF10B981))
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Sync, null, modifier = Modifier.size(12.dp), tint = Color(0xFF059669))
                    Spacer(Modifier.width(5.dp))
                    Text("Đồng bộ $blanks chỗ trống → ${formState.answers.size} đáp án", fontSize = 11.sp, color = Color(0xFF059669), fontWeight = FontWeight.Black)
                }
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(999.dp))
                        .background(colors.mintL)
                        .border(1.dp, colors.mint.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                        .clickable { onSpeakForm(formState.text) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isSpeaking) Icons.Default.Stop else Icons.Default.PlayArrow,
                        null, modifier = Modifier.size(12.dp), tint = colors.mint
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(if (isSpeaking) "Dừng" else "Nghe thử", fontSize = 11.sp, color = colors.mint, fontWeight = FontWeight.Black)
                }
                listOf(0.75f, 1f, 1.25f, 1.5f).forEach { sp ->
                    val selected = sp == ttsSpeed
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(999.dp))
                            .background(if (selected) colors.lavL else Color.Transparent)
                            .border(1.dp, if (selected) colors.lav else colors.border2, RoundedCornerShape(999.dp))
                            .clickable { onSpeedChange(sp) }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text("${sp}×", fontSize = 11.sp, fontWeight = FontWeight.Black, color = if (selected) colors.lav else colors.text3)
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(colors.surface).border(1.5.dp, colors.border2, RoundedCornerShape(12.dp)).padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Word Box — từ cho học sinh chọn", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF4338CA), modifier = Modifier.weight(1f))
                if (formState.wordBox.size > 1) {
                    IconButton(onClick = { formVm.setShuffleWordBox(!formState.shuffleWordBox) }, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.Shuffle, "Tự tráo Word Box", tint = if (formState.shuffleWordBox) Color(0xFFDC2626) else Color(0xFF4338CA), modifier = Modifier.size(13.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF4338CA).copy(alpha = 0.1f))
                        .border(1.dp, Color(0xFF4338CA).copy(alpha = 0.3f), RoundedCornerShape(999.dp))
                        .clickable {
                            formVm.suggestWordBoxFromAnswers { added ->
                                when {
                                    added < 0 -> ToastCenter.show("! Chưa có đáp án nào", "⚠️", Color(0xFFF59E0B))
                                    added == 0 -> ToastCenter.show("! Tất cả đáp án đã có trong Word Box", "⚠️", Color(0xFFF59E0B))
                                    else -> ToastCenter.show("✓ Đã thêm $added từ vào Word Box", "✅", Color(0xFF10B981))
                                }
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF4338CA), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Gợi ý từ đáp án", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF4338CA))
                }
            }
            Spacer(Modifier.height(7.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                formState.wordBox.forEachIndexed { i, w ->
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0xFF4338CA).copy(alpha = 0.12f)).padding(start = 10.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(w, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4338CA))
                        IconButton(onClick = { formVm.removeWord(i) }, modifier = Modifier.size(18.dp)) {
                            Icon(Icons.Default.Close, "Xoá từ", tint = Color(0xFF4338CA), modifier = Modifier.size(10.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                com.learnsypro.app.admin.ui.components.CompactTextField(
                    value = wbInput, onValueChange = { wbInput = it },
                    placeholder = "Nhập từ rồi Enter...", colors = colors,
                    accentColor = Color(0xFF4338CA), modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF4338CA).copy(alpha = 0.1f))
                        .border(1.5.dp, Color(0xFF4338CA).copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .clickable {
                            formVm.addWord(wbInput) { ToastCenter.show("! Từ này đã có trong Word Box", "⚠️", Color(0xFFF59E0B)) }
                            wbInput = ""
                        }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text("+ Thêm", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF4338CA))
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF10B981).copy(alpha = 0.05f)).border(1.5.dp, Color(0xFF10B981).copy(alpha = 0.25f), RoundedCornerShape(12.dp)).padding(12.dp)
        ) {
            Text("Đáp án đúng theo thứ tự (1),(2),(3)...", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF059669))
            Spacer(Modifier.height(8.dp))
            formState.answers.forEachIndexed { i, a ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    Text("(${i + 1})", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF059669))
                    com.learnsypro.app.admin.ui.components.CompactTextField(
                        value = a, onValueChange = { formVm.updateAnswer(i, it) },
                        placeholder = "Đáp án ${i + 1}", colors = colors,
                        accentColor = Color(0xFF059669), modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).clickable { formVm.removeAnswer(i) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Remove, "Xoá đáp án", tint = Color(0xFFDC2626), modifier = Modifier.size(13.dp))
                    }
                }
            }
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF10B981).copy(alpha = 0.1f))
                    .border(1.5.dp, Color(0xFF10B981).copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .clickable { formVm.addAnswer() }
                    .padding(horizontal = 11.dp, vertical = 6.dp)
            ) {
                Text("+ Thêm chỗ trống", fontSize = 11.sp, color = Color(0xFF059669), fontWeight = FontWeight.Black)
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFDC2626).copy(alpha = 0.04f)).border(1.5.dp, Color(0xFFDC2626).copy(alpha = 0.2f), RoundedCornerShape(12.dp)).padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("True / False / Not Mentioned (tuỳ chọn)", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFDC2626), modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(999.dp))
                        .background(if (formState.shuffleStatements) Color(0xFFDC2626) else Color.Transparent)
                        .border(1.dp, if (formState.shuffleStatements) Color(0xFFDC2626) else colors.border2, RoundedCornerShape(999.dp))
                        .clickable { formVm.setShuffleStatements(!formState.shuffleStatements) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Shuffle, null, modifier = Modifier.size(11.dp), tint = if (formState.shuffleStatements) Color.White else colors.text3)
                    Spacer(Modifier.width(4.dp))
                    Text("Tráo thứ tự", fontSize = 10.sp, fontWeight = FontWeight.Black, color = if (formState.shuffleStatements) Color.White else colors.text3)
                }
            }
            if (formState.shuffleStatements) {
                Text("Đang bật: mỗi học sinh sẽ thấy các nhận định theo thứ tự ngẫu nhiên khác nhau.", fontSize = 10.sp, color = Color(0xFFB45309), modifier = Modifier.padding(bottom = 6.dp))
            }
            formState.statements.forEachIndexed { i, s ->
                if (i > 0) Spacer(Modifier.height(7.dp))
                StatementRow(
                    index = i, statement = s, colors = colors,
                    canMoveUp = i > 0, canMoveDown = i < formState.statements.size - 1,
                    onTextChange = { formVm.updateStatementText(i, it) },
                    onAnswerChange = { formVm.updateStatementAnswer(i, it) },
                    onMoveUp = { formVm.moveStatement(i, -1) },
                    onMoveDown = { formVm.moveStatement(i, 1) },
                    onRemove = { formVm.removeStatement(i) }
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFDC2626).copy(alpha = 0.08f))
                    .border(1.5.dp, Color(0xFFDC2626).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .clickable { formVm.addStatement() }
                    .padding(horizontal = 11.dp, vertical = 6.dp)
            ) {
                Text("+ Thêm nhận định", fontSize = 11.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Black)
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF7C3AED).copy(alpha = 0.04f)).border(1.5.dp, Color(0xFF7C3AED).copy(alpha = 0.25f), RoundedCornerShape(12.dp)).padding(12.dp)
        ) {
            Text("Nhãn (Tags) (tuỳ chọn)", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF7C3AED))
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                formState.tags.forEach { t ->
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0xFF7C3AED).copy(alpha = 0.12f)).padding(start = 10.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(t, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7C3AED))
                        IconButton(onClick = { formVm.removeTag(t) }, modifier = Modifier.size(16.dp)) {
                            Icon(Icons.Default.Close, "Xoá nhãn", tint = Color(0xFF7C3AED), modifier = Modifier.size(9.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                com.learnsypro.app.admin.ui.components.CompactTextField(
                    value = tagInput, onValueChange = { tagInput = it },
                    placeholder = "VD: Unit 5, Beginner, ...", colors = colors,
                    accentColor = Color(0xFF7C3AED), modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF7C3AED).copy(alpha = 0.1f))
                        .border(1.5.dp, Color(0xFF7C3AED).copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .clickable { formVm.addTag(tagInput); tagInput = "" }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text("+ Thêm", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF7C3AED))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(999.dp),
                contentPadding = PaddingValues(vertical = 9.dp)
            ) { Text("Huỷ", fontSize = 12.5.sp, fontWeight = FontWeight.Black) }
            Button(
                onClick = {
                    formVm.requestSave(
                        allItems = listState.items,
                        onDupText = { ToastCenter.show("Đã có câu Listening khác với nội dung giống y hệt!", "❌", Color(0xFFEF4444)) },
                        onEmptyText = { ToastCenter.show("Nhập đoạn văn để đọc trước!", "⚠️", Color(0xFFF59E0B)) },
                        onMismatchConfirmNeeded = { b, a -> onMismatch(b, a) },
                        onSaved = { _, isNew -> onSaved(isNew) },
                        onError = { msg -> ToastCenter.show("Lưu thất bại: $msg", "❌", Color(0xFFEF4444)) }
                    )
                },
                enabled = !formState.saving,
                modifier = Modifier.weight(2f),
                shape = RoundedCornerShape(999.dp),
                contentPadding = PaddingValues(vertical = 9.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.lav)
            ) {
                Text(if (formState.saving) "Đang lưu..." else if (formState.editingId != null) "Lưu thay đổi" else "Thêm câu", fontSize = 12.5.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun StatementRow(
    index: Int,
    statement: ListeningStatement,
    colors: LearnsyColors,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onTextChange: (String) -> Unit,
    onAnswerChange: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    // FIX: padding(vertical=4.dp) ngoài viền + padding(10.dp) đều trong viền khiến
    // mỗi card nhận định cao/thưa hơn bản web (JSX chỉ padding:'8px 10px', spacing
    // giữa các item do Column cha quản lý qua gap, không tự thêm margin riêng lẻ).
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp)).background(colors.surface)
            .border(1.dp, Color(0xFFDC2626).copy(alpha = 0.15f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${index + 1}.", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFFDC2626))
            com.learnsypro.app.admin.ui.components.UnderlineOnlyInp(
                valueHtml = statement.statement, onChange = onTextChange,
                placeholder = "Nhận định ${index + 1}", colors = colors, modifier = Modifier.weight(1f)
            )
            // FIX: IconButton Material giữ touch-target tối thiểu 48dp dù đã
            // set size(26.dp), khiến 3 nút ↑↓− trông to/dồn hơn cần thiết trên
            // hàng hẹp. Đổi sang Box+clickable (không ép touch target) để nút
            // gọn đúng kích thước hiển thị, giống SquareIconBtn ở list.
            listOf(
                Triple(Icons.Default.KeyboardArrowUp, "Lên", canMoveUp to onMoveUp),
                Triple(Icons.Default.KeyboardArrowDown, "Xuống", canMoveDown to onMoveDown)
            ).forEach { (icon, desc, pair) ->
                val (enabled, action) = pair
                Box(
                    modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = enabled, onClick = action),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, desc, tint = if (enabled) colors.text3 else colors.text4, modifier = Modifier.size(14.dp))
                }
            }
            Box(
                modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Remove, "Xoá", tint = Color(0xFFDC2626), modifier = Modifier.size(13.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TfnmAnswer.values().forEach { ans ->
                val selected = statement.answer == ans.stored
                val color = when (ans) {
                    TfnmAnswer.TRUE -> Color(0xFF16A34A)
                    TfnmAnswer.FALSE -> Color(0xFFDC2626)
                    TfnmAnswer.NOT_MENTIONED -> Color(0xFF6366F1)
                }
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .background(if (selected) color else color.copy(alpha = 0.08f))
                        .border(1.5.dp, color.copy(alpha = if (selected) 1f else 0.35f), RoundedCornerShape(8.dp))
                        .clickable { onAnswerChange(ans.stored) }
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(ans.label, fontSize = 11.sp, fontWeight = FontWeight.Black, color = if (selected) Color.White else color)
                }
            }
        }
    }
}

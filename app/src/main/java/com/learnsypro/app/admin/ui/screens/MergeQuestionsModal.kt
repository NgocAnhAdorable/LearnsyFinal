package com.learnsypro.app.admin.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.learnsypro.app.admin.data.Lesson
import com.learnsypro.app.admin.data.Question
import com.learnsypro.app.admin.ui.MergeQuestionsViewModel
import com.learnsypro.app.admin.ui.MqTypeFilter
import com.learnsypro.app.admin.ui.questionPreviewText
import com.learnsypro.app.admin.ui.questionTypeShort
import com.learnsypro.app.admin.ui.theme.LearnsyColors

// Tương đương MergeQuestionsModal (merge-questions.css) — gộp câu hỏi lẻ từ các bài
// khác vào bài đang soạn. onMerge trả về list Question đã chọn để LessonEditorScreen
// nối vào questions[] hiện tại.
// Trước đây nhận allLessons từ Editor cha (Editor giữ sẵn snapshot từ màn
// Danh sách) — giờ Editor là route độc lập trong NavHost, không còn state
// đó nữa, nên modal tự fetch danh sách bài học của riêng nó khi mở lên.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MergeQuestionsModal(
    colors: LearnsyColors,
    excludeLessonId: String?,
    onDismiss: () -> Unit,
    onMerge: (List<Question>) -> Unit,
    vm: MergeQuestionsViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()
    var allLessons by remember { mutableStateOf<List<Lesson>>(emptyList()) }
    var loadingLessons by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        allLessons = try { com.learnsypro.app.admin.data.AdminLessonRepository().fetchAll() } catch (e: Exception) { emptyList() }
        loadingLessons = false
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(24.dp, 24.dp, 0.dp, 0.dp))
                .background(colors.surface),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp, 18.dp, 18.dp, 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFFFF0F5), Color(0xFFF0E6FF))))
                        .border(1.5.dp, colors.border, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.CallMerge, null, tint = colors.lav, modifier = Modifier.size(17.dp)) }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Gộp câu hỏi từ bài khác", fontSize = 17.sp, fontWeight = FontWeight.Black, color = colors.text)
                    Text("Chọn câu hỏi từ các bộ đề khác để thêm vào bài đang soạn", fontSize = 11.sp, color = colors.text3)
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp).clip(CircleShape).border(1.5.dp, colors.border, CircleShape)
                ) { Icon(Icons.Default.Close, "Đóng", tint = colors.text3, modifier = Modifier.size(14.dp)) }
            }
            HorizontalDivider(color = colors.border)

            Column(modifier = Modifier.fillMaxWidth().padding(16.dp, 10.dp)) {
                OutlinedTextField(
                    value = state.search, onValueChange = vm::setSearch,
                    placeholder = { Text("Tìm bài học hoặc nội dung câu hỏi...") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(999.dp),
                    trailingIcon = {
                        if (state.search.isNotEmpty()) {
                            IconButton(onClick = { vm.setSearch("") }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, "Xoá tìm kiếm", tint = colors.text4, modifier = Modifier.size(13.dp))
                            }
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        MqTypeFilter.ALL to "Tất cả",
                        MqTypeFilter.TRUE_FALSE to "Đúng/Sai",
                        MqTypeFilter.MULTIPLE_ISH to "Trắc nghiệm",
                        MqTypeFilter.FILL_BLANK to "Điền từ"
                    ).forEach { pair ->
                        val filter = pair.first; val label = pair.second
                        val active = state.typeFilter == filter
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(999.dp))
                                .background(if (active) colors.lav else colors.lavL)
                                .border(1.5.dp, if (active) colors.lav else colors.border2, RoundedCornerShape(999.dp))
                                .clickable { vm.setTypeFilter(filter) }
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Black, color = if (active) Color.White else colors.lav)
                        }
                    }
                }
            }
            HorizontalDivider(color = colors.border)

            val lessons = vm.matchingLessons(allLessons, excludeLessonId)
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp, 10.dp)) {
                if (loadingLessons) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.lav, modifier = Modifier.size(28.dp))
                    }
                } else if (lessons.isEmpty()) {
                    Text(
                        "Không có bài học nào để gộp.", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.text3,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), textAlign = TextAlign.Center
                    )
                } else {
                    lessons.forEach { lesson ->
                        LessonRow(lesson = lesson, colors = colors, vm = vm, expanded = lesson.id in state.expandedLessonIds)
                        Spacer(Modifier.height(14.dp))
                    }
                }
            }

            if (state.selected.isNotEmpty()) {
                HorizontalDivider(color = colors.border)
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 110.dp).background(colors.bg2)
                        .padding(14.dp, 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("${state.selected.size}", fontSize = 13.sp, fontWeight = FontWeight.Black, color = colors.lav)
                    FlowRow(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        state.selected.forEach { sel ->
                            Row(
                                modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(colors.surface)
                                    .border(1.5.dp, colors.border2, RoundedCornerShape(999.dp))
                                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    questionPreviewText(sel.question).take(24), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.text,
                                    maxLines = 1
                                )
                                IconButton(onClick = { vm.removeSelected(sel.question.id) }, modifier = Modifier.size(18.dp)) {
                                    Icon(Icons.Default.Close, "Bỏ chọn", tint = colors.text4, modifier = Modifier.size(10.dp))
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = colors.border)
            val totalAvailable = vm.totalAvailable(allLessons, excludeLessonId)
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!state.confirming) {
                    Text(
                        buildCounterText("Đã chọn ", "${state.selected.size}", "/$totalAvailable câu", colors.text, colors.lav),
                        modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Black
                    )
                    OutlinedButton(
                        onClick = onDismiss, enabled = !state.merging, shape = RoundedCornerShape(999.dp)
                    ) { Text("Huỷ") }
                    Button(
                        onClick = { vm.askConfirm() },
                        enabled = state.selected.isNotEmpty() && !state.merging,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.rose)
                    ) {
                        if (state.merging) {
                            CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.Default.CallMerge, null, modifier = Modifier.size(13.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (state.merging) "Đang gộp..."
                            else if (state.selected.isNotEmpty()) "Gộp ${state.selected.size} câu" else "Gộp",
                            fontWeight = FontWeight.Black
                        )
                    }
                } else {
                    // Tương đương bước confirming trong merge-questions.jsx — xác nhận
                    // trước khi thật sự gộp, tránh bấm nhầm khi đã chọn nhiều câu.
                    Text(
                        "Gộp ${state.selected.size} câu vào bài đang soạn?",
                        modifier = Modifier.weight(1f), fontSize = 12.5.sp, fontWeight = FontWeight.Black, color = colors.text
                    )
                    IconButton(
                        onClick = { vm.cancelConfirm() },
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp))
                            .background(colors.surface).border(1.5.dp, colors.border, RoundedCornerShape(12.dp))
                    ) { Icon(Icons.Default.Close, "Huỷ gộp", tint = colors.text, modifier = Modifier.size(15.dp)) }
                    IconButton(
                        onClick = {
                            vm.setMerging(true)
                            onMerge(vm.mergedQuestions())
                            vm.reset()
                            onDismiss()
                        },
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(colors.grad)
                    ) { Icon(Icons.Default.Check, "Xác nhận gộp", tint = Color.White, modifier = Modifier.size(15.dp)) }
                }
            }
        }
    }
}

private fun buildCounterText(pre: String, num: String, post: String, textColor: Color, numColor: Color) =
    buildAnnotatedString {
        withStyle(SpanStyle(color = textColor)) { append(pre) }
        withStyle(SpanStyle(color = numColor, fontSize = 16.sp)) { append(num) }
        withStyle(SpanStyle(color = textColor)) { append(post) }
    }

// Tương đương .mq-badge (mq-badge-ds/tn/dt) trong merge-questions.jsx
@Composable
private fun TypeCountBadge(text: String, color: Color, bg: Color) {
    Box(modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(bg).padding(horizontal = 6.dp, vertical = 1.dp)) {
        Text(text, fontSize = 9.5.sp, fontWeight = FontWeight.Black, color = color)
    }
}

@Composable
private fun LessonRow(lesson: Lesson, colors: LearnsyColors, vm: MergeQuestionsViewModel, expanded: Boolean) {
    val visibleQuestions = vm.matchingQuestionsOf(lesson)
    val allSelected = visibleQuestions.isNotEmpty() && visibleQuestions.all { vm.isSelected(it) }

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface)
            .border(1.dp, if (expanded) colors.lav else colors.border2, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { vm.toggleExpand(lesson.id) }.padding(11.dp, 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
                    .background(colors.lav.copy(alpha = 0.1f)).border(1.5.dp, colors.lav.copy(alpha = 0.2f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.MenuBook, null, tint = colors.lav, modifier = Modifier.size(16.dp)) }
            Column(modifier = Modifier.weight(1f)) {
                Text(lesson.title.ifBlank { "(Chưa đặt tên)" }, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = colors.text, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("${lesson.subject} · ${visibleQuestions.size} câu", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.text3.copy(alpha = 0.75f))
                    val tfCount = visibleQuestions.count { it is Question.TrueFalse }
                    val tnCount = visibleQuestions.count { it is Question.Multiple || it is Question.MultiSelect }
                    val dtCount = visibleQuestions.count { it is Question.FillBlank }
                    if (tfCount > 0) TypeCountBadge("$tfCount ĐS", colors.lav, colors.lavL)
                    if (tnCount > 0) TypeCountBadge("$tnCount TN", colors.rose, colors.roseL)
                    if (dtCount > 0) TypeCountBadge("$dtCount ĐT", colors.peach, colors.peachL)
                }
            }
            if (visibleQuestions.isNotEmpty()) {
                val selectAllLabel = if (allSelected) "Bỏ chọn" else "Chọn hết"
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(999.dp))
                        .background(if (allSelected) colors.lav else colors.lavL)
                        .border(1.5.dp, colors.border2, RoundedCornerShape(999.dp))
                        .clickable { vm.toggleSelectAllInLesson(lesson) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(selectAllLabel, fontSize = 10.sp, fontWeight = FontWeight.Black, color = if (allSelected) Color.White else colors.lav)
                }
            }
            Icon(
                Icons.Default.KeyboardArrowDown, null,
                tint = if (expanded) colors.lav else colors.text4,
                modifier = Modifier.size(18.dp)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(11.dp, 0.dp, 11.dp, 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (visibleQuestions.isEmpty()) {
                    Text("Không có câu nào khớp bộ lọc.", fontSize = 11.sp, color = colors.text3, modifier = Modifier.padding(vertical = 10.dp))
                }
                visibleQuestions.forEach { q ->
                    val selected = vm.isSelected(q)
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (selected) colors.lav.copy(alpha = 0.08f) else colors.bg2)
                            .border(1.5.dp, if (selected) colors.lav else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable { vm.toggleSelect(lesson, q) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(22.dp).clip(RoundedCornerShape(7.dp))
                                .background(if (selected) colors.lav else Color.Transparent)
                                .border(2.dp, if (selected) colors.lav else colors.text4, RoundedCornerShape(7.dp)),
                            contentAlignment = Alignment.Center
                        ) { if (selected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(13.dp)) }

                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(colors.lavL)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(questionTypeShort(q), fontSize = 10.sp, fontWeight = FontWeight.Black, color = colors.lav)
                        }

                        Text(
                            questionPreviewText(q).ifBlank { "(Chưa có nội dung)" },
                            fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.text, maxLines = 2,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

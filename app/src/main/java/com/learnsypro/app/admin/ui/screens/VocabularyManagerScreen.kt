package com.learnsypro.app.admin.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.learnsypro.app.admin.data.*
import com.learnsypro.app.admin.ui.ToastCenter
import com.learnsypro.app.admin.ui.VocabularyViewModel
import com.learnsypro.app.admin.ui.components.IconActionButton
import com.learnsypro.app.admin.ui.components.IconBtnSize
import com.learnsypro.app.admin.ui.theme.LearnsyColors

// Tương đương VocabularyManager (vocabulary-manager.jsx) trên web — tab "Từ vựng".
// Cấu trúc: Bài học (vocab_courses) > Unit (vocab_units) > Từ vựng (vocab_words).
@Composable
fun VocabularyManagerScreen(
    colors: LearnsyColors,
    dark: Boolean,
    refreshKey: Any = Unit,
    vm: VocabularyViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()
    LaunchedEffect(refreshKey) { vm.loadIfNeeded(refreshKey) }

    var courseModal by remember { mutableStateOf<VocabCourse?>(null) }
    var showCourseModal by remember { mutableStateOf(false) }
    var deleteCourseTarget by remember { mutableStateOf<VocabCourse?>(null) }

    val filtered = remember(state.courses, state.search) { vm.filteredCourses() }

    // FIX: bỏ .background(colors.bg) opaque — che mất BackgroundLayer (nền
    // tuỳ chỉnh dùng chung) mà AppRoot vẽ ở lớp dưới cùng root. Nội dung tab
    // vẫn đọc rõ nhờ card/row bên trong đã tự có nền riêng.
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = state.search,
                onValueChange = { vm.setSearch(it) },
                placeholder = { Text("Tìm bài học...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = colors.text3, modifier = Modifier.size(16.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.lav, unfocusedBorderColor = colors.border2,
                    focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surface
                ),
                modifier = Modifier.weight(1f).height(52.dp)
            )
            Button(
                onClick = { courseModal = null; showCourseModal = true },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.lav)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Thêm từ vựng", fontWeight = FontWeight.Black, fontSize = 12.5.sp, maxLines = 1)
            }
        }

        Text(
            "${state.courses.size} bài học" + if (state.search.isNotBlank()) " · ${filtered.size} khớp tìm kiếm" else "",
            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.text3,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        when {
            state.loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.lav)
                }
            }
            filtered.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MenuBook, null, tint = colors.lav.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (state.search.isNotBlank()) "Không tìm thấy bài học" else "Chưa có bài học nào",
                            fontWeight = FontWeight.Black, fontSize = 14.5.sp, color = colors.text2
                        )
                        Text(
                            if (state.search.isNotBlank()) "Thử từ khoá khác nhé" else "Bấm \"Thêm từ vựng\" để tạo bài học đầu tiên",
                            fontSize = 12.sp, color = colors.text3
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    state = vm.listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filtered, key = { it.id }) { course ->
                        CourseBlock(
                            course = course, colors = colors, dark = dark, vm = vm,
                            onEditCourse = { courseModal = course; showCourseModal = true },
                            onDeleteCourse = { deleteCourseTarget = course }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showCourseModal) {
        CourseModal(
            colors = colors, dark = dark, initial = courseModal,
            onClose = { showCourseModal = false },
            onSave = { title, desc ->
                vm.saveCourse(courseModal?.id, title, desc) { ok, msg ->
                    ToastCenter.show(msg, if (ok) "✅" else "❌", if (ok) Color(0xFF10B981) else Color(0xFFEF4444))
                    if (ok) showCourseModal = false
                }
            }
        )
    }

    deleteCourseTarget?.let { c ->
        ConfirmDeleteDialog(
            title = "Xoá bài học?",
            message = "\"${c.title}\" và toàn bộ Unit + từ vựng bên trong sẽ bị xoá vĩnh viễn.",
            colors = colors,
            dark = dark,
            onCancel = { deleteCourseTarget = null },
            onConfirm = {
                vm.deleteCourse(c.id) { ok, msg -> ToastCenter.show(msg, if (ok) "✅" else "❌", if (ok) Color(0xFF10B981) else Color(0xFFEF4444)) }
                deleteCourseTarget = null
            }
        )
    }
}

@Composable
private fun CourseBlock(
    course: VocabCourse,
    colors: LearnsyColors,
    dark: Boolean,
    vm: VocabularyViewModel,
    onEditCourse: () -> Unit,
    onDeleteCourse: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val state by vm.uiState.collectAsState()
    val units = state.unitsByCourse[course.id] ?: emptyList()
    val loaded = course.id in state.loadedUnitCourses

    var showUnitModal by remember { mutableStateOf(false) }
    var editUnitTarget by remember { mutableStateOf<VocabUnit?>(null) }
    var deleteUnitTarget by remember { mutableStateOf<VocabUnit?>(null) }

    LaunchedEffect(open) { if (open) vm.loadUnits(course.id) }

    val rotation by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, label = "chevron")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (dark) Color.White.copy(alpha = 0.02f) else colors.lav.copy(alpha = 0.02f))
            .border(1.5.dp, colors.border, RoundedCornerShape(18.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .background(if (open) colors.lavPale else Color.Transparent)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.KeyboardArrowDown, null, tint = colors.lav, modifier = Modifier.size(16.dp).rotate(rotation))
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(colors.grad),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MenuBook, null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(course.title, fontWeight = FontWeight.Black, fontSize = 14.5.sp, color = colors.text, maxLines = 1)
                Text(
                    course.description.ifBlank { if (loaded) "${units.size} unit" else "Bấm để xem units" },
                    fontSize = 11.5.sp, color = colors.text3, maxLines = 1
                )
            }
            IconActionButton(Icons.Default.Edit, "Sửa bài học", onEditCourse, size = IconBtnSize.Medium, tint = colors.lav, background = colors.bg2, borderColor = colors.border2)
            Spacer(Modifier.width(4.dp))
            IconActionButton(Icons.Default.Delete, "Xoá bài học", onDeleteCourse, size = IconBtnSize.Medium, tint = Color(0xFFEF4444), background = Color(0x14EF4444), borderColor = Color(0x59EF4444))
        }

        AnimatedVisibility(visible = open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DashedActionButton(
                    label = "Thêm Unit", icon = Icons.Default.Add, colors = colors,
                    onClick = { editUnitTarget = null; showUnitModal = true }
                )
                when {
                    !loaded -> {
                        repeat(2) {
                            Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(colors.bg2.copy(alpha = 0.6f)))
                        }
                    }
                    units.isEmpty() -> {
                        Text("Chưa có Unit nào trong bài học này", fontSize = 12.5.sp, color = colors.text3, modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                    else -> {
                        units.forEach { unit ->
                            UnitBlock(
                                unit = unit, colors = colors, dark = dark, vm = vm,
                                onEditUnit = { editUnitTarget = unit; showUnitModal = true },
                                onDeleteUnit = { deleteUnitTarget = unit }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showUnitModal) {
        UnitModal(
            colors = colors, dark = dark, initial = editUnitTarget,
            onClose = { showUnitModal = false },
            onSave = { title, level ->
                vm.saveUnit(course.id, editUnitTarget?.id, title, level) { ok, msg ->
                    ToastCenter.show(msg, if (ok) "✅" else "❌", if (ok) Color(0xFF10B981) else Color(0xFFEF4444))
                    if (ok) { showUnitModal = false; open = true }
                }
            }
        )
    }

    deleteUnitTarget?.let { u ->
        ConfirmDeleteDialog(
            title = "Xoá Unit?",
            message = "\"${u.title}\" và toàn bộ từ vựng bên trong sẽ bị xoá vĩnh viễn.",
            colors = colors,
            dark = dark,
            onCancel = { deleteUnitTarget = null },
            onConfirm = {
                vm.deleteUnit(course.id, u.id) { ok, msg -> ToastCenter.show(msg, if (ok) "✅" else "❌", if (ok) Color(0xFF10B981) else Color(0xFFEF4444)) }
                deleteUnitTarget = null
            }
        )
    }
}

@Composable
private fun UnitBlock(
    unit: VocabUnit,
    colors: LearnsyColors,
    dark: Boolean,
    vm: VocabularyViewModel,
    onEditUnit: () -> Unit,
    onDeleteUnit: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val state by vm.uiState.collectAsState()
    val words = state.wordsByUnit[unit.id] ?: emptyList()
    val loaded = unit.id in state.loadedWordUnits

    var wordModal by remember { mutableStateOf<VocabWord?>(null) }
    var showWordModal by remember { mutableStateOf(false) }
    var showBulkModal by remember { mutableStateOf(false) }
    var deleteWordTarget by remember { mutableStateOf<VocabWord?>(null) }

    LaunchedEffect(open) { if (open) vm.loadWords(unit.id) }

    val rotation by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, label = "chevron2")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.5.dp, colors.border, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .background(if (open) colors.lavPale else Color.Transparent)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.KeyboardArrowDown, null, tint = colors.lav, modifier = Modifier.size(14.dp).rotate(rotation))
            Column(modifier = Modifier.weight(1f)) {
                Text(unit.title, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = colors.text, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (unit.level.isNotBlank()) {
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(colors.peachL).padding(horizontal = 7.dp, vertical = 1.dp)
                        ) { Text(unit.level, fontSize = 10.sp, fontWeight = FontWeight.Black, color = colors.peach) }
                    }
                    Text(if (loaded) "${words.size} từ" else "···", fontSize = 11.sp, color = colors.text3)
                }
            }
            IconActionButton(Icons.Default.Edit, "Sửa Unit", onEditUnit, size = IconBtnSize.Medium, tint = colors.lav, background = colors.bg2, borderColor = colors.border2)
            Spacer(Modifier.width(4.dp))
            IconActionButton(Icons.Default.Delete, "Xoá Unit", onDeleteUnit, size = IconBtnSize.Medium, tint = Color(0xFFEF4444), background = Color(0x14EF4444), borderColor = Color(0x59EF4444))
        }

        AnimatedVisibility(visible = open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    DashedActionButton(
                        label = "Thêm từ vựng", icon = Icons.Default.Add, colors = colors,
                        onClick = { wordModal = null; showWordModal = true },
                        modifier = Modifier.weight(1f)
                    )
                    DashedActionButton(
                        label = "Nhập nhanh", icon = Icons.Filled.List, colors = colors,
                        onClick = { showBulkModal = true }
                    )
                }
                when {
                    !loaded -> {
                        repeat(2) { Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).background(colors.bg2.copy(alpha = 0.6f))) }
                    }
                    words.isEmpty() -> {
                        Text("Chưa có từ vựng nào trong unit này", fontSize = 12.5.sp, color = colors.text3, modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                    else -> {
                        words.forEach { w ->
                            WordRow(
                                w = w, colors = colors,
                                onEdit = { wordModal = w; showWordModal = true },
                                onDelete = { deleteWordTarget = w }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showWordModal) {
        WordModal(
            colors = colors, dark = dark, initial = wordModal,
            onClose = { showWordModal = false },
            onSave = { word, pos, ipa, meaning, example ->
                vm.saveWord(unit.id, wordModal?.id, word, pos, ipa, meaning, example) { ok, msg ->
                    ToastCenter.show(msg, if (ok) "✅" else "❌", if (ok) Color(0xFF10B981) else Color(0xFFEF4444))
                    if (ok) showWordModal = false
                }
            }
        )
    }

    if (showBulkModal) {
        BulkWordModal(
            colors = colors, dark = dark,
            onClose = { showBulkModal = false },
            onSave = { lines ->
                vm.bulkAddWords(unit.id, lines) { ok, msg ->
                    ToastCenter.show(msg, if (ok) "✅" else "❌", if (ok) Color(0xFF10B981) else Color(0xFFEF4444))
                    if (ok) showBulkModal = false
                }
            }
        )
    }

    deleteWordTarget?.let { w ->
        ConfirmDeleteDialog(
            title = "Xoá từ vựng?",
            message = "\"${w.word}\" sẽ bị xoá vĩnh viễn.",
            colors = colors,
            dark = dark,
            onCancel = { deleteWordTarget = null },
            onConfirm = {
                vm.deleteWord(unit.id, w.id) { ok, msg -> ToastCenter.show(msg, if (ok) "✅" else "❌", if (ok) Color(0xFF10B981) else Color(0xFFEF4444)) }
                deleteWordTarget = null
            }
        )
    }
}

@Composable
private fun WordRow(w: VocabWord, colors: LearnsyColors, onEdit: () -> Unit, onDelete: () -> Unit) {
    val col = posColor(w.pos)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.5.dp, colors.border, RoundedCornerShape(14.dp))
            .padding(11.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.padding(top = 5.dp).size(8.dp).clip(RoundedCornerShape(99.dp)).background(col))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(w.word, fontWeight = FontWeight.Black, fontSize = 14.5.sp, color = colors.text)
                Box(modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(col.copy(alpha = 0.1f)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                    Text(posLabel(w.pos), fontSize = 10.sp, fontWeight = FontWeight.Black, color = col)
                }
                if (w.ipa.isNotBlank()) {
                    Text("/${w.ipa.trim('/')}/ ", fontSize = 12.sp, color = colors.text3, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                }
            }
            if (w.meaning.isNotBlank()) Text(w.meaning, fontSize = 13.sp, color = colors.text2, fontWeight = FontWeight.SemiBold)
            if (w.example.isNotBlank()) Text("\"${w.example}\"", fontSize = 12.sp, color = colors.text3, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        }
        IconActionButton(Icons.Default.Edit, "Sửa", onEdit, size = IconBtnSize.Medium, tint = colors.lav, background = colors.bg2, borderColor = colors.border2)
        Spacer(Modifier.width(4.dp))
        IconActionButton(Icons.Default.Delete, "Xoá", onDelete, size = IconBtnSize.Medium, tint = Color(0xFFEF4444), background = Color(0x14EF4444), borderColor = Color(0x59EF4444))
    }
}

@Composable
private fun DashedActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, colors: LearnsyColors, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, colors.lav2, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = colors.lav, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Black, color = colors.lav)
    }
}

@Composable
private fun CourseModal(colors: LearnsyColors, dark: Boolean, initial: VocabCourse?, onClose: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    ModalScaffold(colors, dark, if (initial != null) "Sửa bài học" else "Tạo bài học mới", onClose, {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LabeledField("Tên bài học *", title, { title = it }, colors, placeholder = "Vd: Tiếng Anh Cơ Bản A1")
            LabeledField("Mô tả", description, { description = it }, colors, minLines = 2, placeholder = "Mô tả ngắn về bài học...")
        }
    }, confirmLabel = if (initial != null) "Lưu thay đổi" else "Tạo bài học", onConfirm = { onSave(title, description) })
}

@Composable
private fun UnitModal(colors: LearnsyColors, dark: Boolean, initial: VocabUnit?, onClose: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var level by remember { mutableStateOf(initial?.level ?: "") }
    ModalScaffold(colors, dark, if (initial != null) "Sửa Unit" else "Tạo Unit mới", onClose, {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LabeledField("Tên Unit *", title, { title = it }, colors, placeholder = "Vd: Unit 1 - Greetings")
            LabeledField("Level", level, { level = it }, colors, placeholder = "Vd: Beginner, A1, A2...")
        }
    }, confirmLabel = if (initial != null) "Lưu thay đổi" else "Tạo Unit", onConfirm = { onSave(title, level) })
}

@Composable
private fun WordModal(colors: LearnsyColors, dark: Boolean, initial: VocabWord?, onClose: () -> Unit, onSave: (String, String, String, String, String) -> Unit) {
    var word by remember { mutableStateOf(initial?.word ?: "") }
    var pos by remember { mutableStateOf(initial?.pos ?: "noun") }
    var ipa by remember { mutableStateOf(initial?.ipa ?: "") }
    var meaning by remember { mutableStateOf(initial?.meaning ?: "") }
    var example by remember { mutableStateOf(initial?.example ?: "") }
    var posMenuOpen by remember { mutableStateOf(false) }

    ModalScaffold(colors, dark, if (initial != null) "Sửa từ vựng" else "Thêm từ vựng", onClose, {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LabeledField("Từ vựng *", word, { word = it }, colors, placeholder = "Vd: Hello")
            Column {
                Text("Loại từ", fontSize = 11.sp, fontWeight = FontWeight.Black, color = colors.text3)
                Spacer(Modifier.height(4.dp))
                Box {
                    OutlinedButton(onClick = { posMenuOpen = true }, shape = RoundedCornerShape(11.dp)) {
                        Text(POS_OPTIONS.find { it.value == pos }?.label ?: pos, fontSize = 13.sp)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = posMenuOpen, onDismissRequest = { posMenuOpen = false }) {
                        POS_OPTIONS.forEach { opt ->
                            DropdownMenuItem(text = { Text(opt.label) }, onClick = { pos = opt.value; posMenuOpen = false })
                        }
                    }
                }
            }
            LabeledField("Phiên âm (IPA)", ipa, { ipa = it }, colors, placeholder = "/həˈloʊ/")
            LabeledField("Nghĩa", meaning, { meaning = it }, colors, minLines = 2, placeholder = "Giải thích nghĩa...")
            LabeledField("Ví dụ", example, { example = it }, colors, minLines = 2, placeholder = "Câu ví dụ...")
        }
    }, confirmLabel = if (initial != null) "Lưu thay đổi" else "Thêm từ vựng", onConfirm = { onSave(word, pos, ipa, meaning, example) })
}

@Composable
private fun BulkWordModal(colors: LearnsyColors, dark: Boolean, onClose: () -> Unit, onSave: (List<ParsedWordLine>) -> Unit) {
    var text by remember { mutableStateOf("") }
    val parsed = remember(text) { parseBulkWords(text) }

    ModalScaffold(colors, dark, "Nhập nhanh nhiều từ vựng", onClose, {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Mỗi dòng 1 từ vựng — cách nhau bằng dấu |: từ | loại từ | phiên âm | nghĩa | ví dụ", fontSize = 11.5.sp, color = colors.text3, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                placeholder = { Text("hello | dt | /həˈloʊ/ | xin chào | Hello, how are you?\nrun | đt | /rʌn/ | chạy\napple | dt", fontSize = 11.5.sp) },
                minLines = 6, maxLines = 10,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.lav, unfocusedBorderColor = colors.border2),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                if (parsed.isNotEmpty()) "Xem trước: ${parsed.size} từ vựng hợp lệ" else "Xem trước: chưa có từ nào",
                fontSize = 11.5.sp, fontWeight = FontWeight.Black, color = colors.text3
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.5.dp, colors.border2, RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                if (parsed.isEmpty()) {
                    Text("Nhập từ vựng bên trên để xem trước ở đây", fontSize = 12.sp, color = colors.text3, modifier = Modifier.padding(12.dp))
                } else {
                    parsed.take(20).forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp)) {
                            Text(r.word, fontSize = 12.5.sp, fontWeight = FontWeight.Black, color = colors.text, modifier = Modifier.widthIn(min = 60.dp))
                            Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(posColor(r.pos).copy(alpha = 0.1f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Text(posLabel(r.pos), fontSize = 9.5.sp, fontWeight = FontWeight.Black, color = posColor(r.pos))
                            }
                            if (r.meaning.isNotBlank()) Text(r.meaning, fontSize = 11.5.sp, color = colors.text3, maxLines = 1, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }, confirmLabel = "Thêm ${if (parsed.isNotEmpty()) parsed.size else ""} từ vựng", onConfirm = { onSave(parsed) }, confirmEnabled = parsed.isNotEmpty())
}

@Composable
private fun LabeledField(
    label: String, value: String, onChange: (String) -> Unit, colors: LearnsyColors,
    minLines: Int = 1, placeholder: String = "", keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    Column {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Black, color = colors.text3)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value, onValueChange = onChange,
            placeholder = { Text(placeholder, fontSize = 13.sp) },
            minLines = minLines, keyboardOptions = keyboardOptions,
            shape = RoundedCornerShape(11.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.lav, unfocusedBorderColor = colors.border2),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// Khung modal chuẩn hoá dùng chung — tương đương phần khung
// (backdrop mờ + card bo góc + footer Huỷ/Lưu) lặp lại trong mọi modal ở vocabulary-manager.jsx
@Composable
private fun ModalScaffold(
    colors: LearnsyColors, dark: Boolean, title: String, onClose: () -> Unit,
    content: @Composable () -> Unit, confirmLabel: String, onConfirm: () -> Unit, confirmEnabled: Boolean = true
) {
    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(if (dark) Color(0xFF1E0D15) else Color.White)
                .border(1.5.dp, colors.border2, RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(18.dp).weight(1f, fill = false)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Black, fontSize = 15.sp, color = colors.text)
                    IconActionButton(Icons.Default.Close, "Đóng", onClose, size = IconBtnSize.Small, tint = colors.text3, background = colors.bg2, borderColor = colors.border2)
                }
                Spacer(Modifier.height(14.dp))
                content()
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onClose, shape = RoundedCornerShape(999.dp), modifier = Modifier.weight(1f)) {
                    Text("Huỷ", fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
                Button(
                    onClick = onConfirm, enabled = confirmEnabled, shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.lav),
                    modifier = Modifier.weight(2f)
                ) {
                    Text(confirmLabel, fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ConfirmDeleteDialog(title: String, message: String, colors: LearnsyColors, dark: Boolean, onCancel: () -> Unit, onConfirm: () -> Unit) {
    com.learnsypro.app.admin.ui.components.ConfirmDialog(
        title = title,
        message = message,
        onDismiss = onCancel,
        onConfirm = onConfirm,
        colors = colors, dark = dark,
        confirmLabel = "Xoá", iconType = com.learnsypro.app.admin.ui.components.ConfirmIconType.DELETE
    )
}

package com.learnsypro.app.admin.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import com.learnsypro.app.admin.ui.components.glowShadow
import com.learnsypro.app.admin.ui.components.lavenderBorder
import com.learnsypro.app.admin.ui.components.ConfirmDialog
import com.learnsypro.app.admin.ui.components.ConfirmIconType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.rememberAsyncImagePainter
import com.learnsypro.app.admin.data.AdminAvatarRepository
import com.learnsypro.app.admin.data.Student
import com.learnsypro.app.admin.ui.*
import com.learnsypro.app.admin.ui.theme.LearnsyColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Tương đương StudentManager trong student-manager.jsx.
@Composable
fun StudentManagerScreen(
    colors: LearnsyColors,
    dark: Boolean,
    refreshKey: Any = Unit,
    vm: StudentListViewModel = viewModel(
        factory = viewModelFactory {
            initializer { StudentListViewModel(savedStateHandle = createSavedStateHandle()) }
        }
    )
) {
    val state by vm.uiState.collectAsState()
    val (avatarState, requestAvatarPick) = rememberAvatarUiState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val avatarRepo = remember { AdminAvatarRepository(context) }
    val scope = rememberCoroutineScope()

    var statusOpen by remember { mutableStateOf(false) }
    var modal by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Student?>(null) }
    var confirmDelete by remember { mutableStateOf<Student?>(null) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    var confirmResetPassword by remember { mutableStateOf<Student?>(null) }
    var nowUTC7 by remember { mutableStateOf("") }

    // Tương đương exportCSV() trong student-manager.jsx — xuất danh sách đang lọc ra file .csv
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val header = "Username,Tên hiển thị,Lớp,Trạng thái,Ngày tạo"
            val rows = vm.filteredStudents().joinToString("\n") { s ->
                val name = "\"" + (s.displayName ?: "").replace("\"", "\"\"") + "\""
                val date = runCatching {
                    java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("vi", "VN"))
                        .format(java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).parse(s.createdAt.take(19)))
                }.getOrDefault("")
                listOf(s.username, name, s.className, if (s.isActive) "Hoạt động" else "Khoá", date).joinToString(",")
            }
            val csv = "\uFEFF" + header + "\n" + rows // BOM để Excel đọc đúng tiếng Việt
            context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
            ToastCenter.show("Đã xuất file CSV", "✅", Color(0xFF10B981))
        } catch (e: Exception) {
            ToastCenter.show("Xuất file thất bại: ${e.message}", "❌", Color(0xFFEF4444))
        }
    }

    LaunchedEffect(refreshKey) {
        vm.loadIfNeeded(refreshKey)
        vm.ping()
    }
    // FIX: loadAvatarUrl() tồn tại nhưng trước đây không nơi nào gọi -> avatar
    // chỉ hiện đúng trong phiên vừa upload (lưu tạm trong RAM), còn sau khi tải
    // lại danh sách (mở lại màn hình, refresh...) avatarState.urls rỗng lại nên
    // rơi về chữ cái đầu dù ảnh đã lưu thành công trên Supabase Storage. Tải
    // song song signed URL cho các học sinh chưa có trong cache mỗi khi danh
    // sách đổi (bỏ qua học sinh đã có url để không gọi lại API thừa).
    LaunchedEffect(state.students) {
        val missing = state.students.filter { it.id !in avatarState.urls }
        if (missing.isEmpty()) return@LaunchedEffect
        missing.forEach { s ->
            launch {
                val url = loadAvatarUrl(avatarRepo, s.id)
                if (url != null) avatarState.putUrl(s.id, url)
            }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            val utc7 = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
            nowUTC7 = String.format("%02d:%02d:%02d", utc7.hour, utc7.minute, utc7.second)
            delay(1000)
        }
    }
    LaunchedEffect(Unit) {
        while (true) { delay(3_600_000); vm.ping() }
    }

    val filtered by remember(
        state.students, state.search, state.filterClass, state.filterStatus, state.sortBy
    ) {
        derivedStateOf { vm.filteredStudents() }
    }
    val active = state.students.count { it.isActive }
    val locked = state.students.size - active

    LazyColumn(
        state = vm.listState,
        modifier = Modifier.fillMaxSize()
            .padding(14.dp, 16.dp, 14.dp, 100.dp)
    ) {
        item {
        // FIX: 4 ô thống kê trước đây dính sát ngay mép trên (chỉ có 14.dp
        // padding chung của LazyColumn, không có khoảng đệm riêng) -> nhìn
        // như bị "cắt" khỏi header/tabs phía trên. Thêm Spacer để tách hẳn ra.
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
            listOf(
                Triple("Tổng", state.students.size, Color(0xFFA855F7)),
                Triple("Hoạt động", active, Color(0xFF10B981)),
                Triple("Khoá", locked, Color(0xFFEF4444)),
                Triple("Lớp", vm.classes().size, Color(0xFFF472B6))
            ).forEach { (label, value, color) ->
                Column(
                    modifier = Modifier.weight(1f)
                        .glowShadow(dark = dark, cornerRadius = 22.dp)
                        .clip(RoundedCornerShape(22.dp)).background(colors.surface)
                        // FIX: border2 ở dark mode gần như trùng màu nền (tím rất tối)
                        // nên viền gần như vô hình -> đổi sang colors.lav (tím sáng, cùng
                        // tông "active" của lavenderBorder) với alpha vừa phải để viền rõ
                        // và sang hơn hẳn, không lộ liễu như alpha 1.0 của trạng thái active.
                        .border(1.5.dp, colors.lav.copy(alpha = if (dark) 0.55f else 0.9f), RoundedCornerShape(22.dp))
                        .padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("$value", fontSize = 20.sp, fontWeight = FontWeight.Black, color = color)
                    Spacer(Modifier.height(2.dp))
                    Text(label.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Black, color = colors.text3, letterSpacing = 0.3.sp)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 18.dp)) {
            Text("Tài khoản học sinh", fontSize = 15.sp, fontWeight = FontWeight.Black, color = colors.text, modifier = Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { statusOpen = !statusOpen },
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
                        .background(
                            if (statusOpen) (if (state.srvStatus == "online") Color(0xFF10B981) else Color(0xFFEF4444))
                            else (if (state.srvStatus == "online") Color(0xFF10B981).copy(alpha = 0.1f) else Color(0xFFEF4444).copy(alpha = 0.1f))
                        )
                ) {
                    Icon(Icons.Default.Dns, "Trạng thái hệ thống", tint = if (statusOpen) Color.White else if (state.srvStatus == "online") Color(0xFF059669) else Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                }
                IconButton(onClick = {
                    val filename = "students_${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())}.csv"
                    exportLauncher.launch(filename)
                }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Download, "Xuất CSV", tint = Color(0xFFA855F7), modifier = Modifier.size(13.dp))
                }
                IconButton(onClick = { vm.toggleBulkMode() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Checklist, "Chọn nhiều", tint = if (state.bulkMode) Color.White else Color(0xFFA855F7), modifier = Modifier.size(13.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = { modal = "add" },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.rose),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text("Thêm", fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        }

        if (statusOpen) {
            StatusPanel(state = state, saving = state.saving, nowUTC7 = nowUTC7, colors = colors, dark = dark)
            Spacer(Modifier.height(16.dp))
        }

        if (state.bulkMode && state.bulkSelected.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFA855F7).copy(alpha = 0.08f)).border(1.5.dp, Color(0xFFA855F7).copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                    .padding(10.dp, 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Đã chọn ${state.bulkSelected.size}", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFFA855F7), modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.bulkToggleActive(true) { } }) { Text("Mở khoá", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Black) }
                TextButton(onClick = { vm.bulkToggleActive(false) { } }) { Text("Khoá", color = Color(0xFFEF4444), fontSize = 11.sp, fontWeight = FontWeight.Black) }
                TextButton(onClick = { confirmBulkDelete = true }) { Text("Xoá ${state.bulkSelected.size}", color = Color(0xFFEF4444), fontSize = 11.sp, fontWeight = FontWeight.Black) }
            }
            Spacer(Modifier.height(12.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            // FIX: OutlinedTextField mặc định (bo góc nhẹ 4dp, viền xám M3)
            // không khớp phong cách "bánh bèo" (pill bo tròn hẳn, viền tím
            // pastel) đã dùng ở các ô nhập khác trong app. Thay bằng
            // BasicTextField tự vẽ — vừa bo cong hơn, vừa nhỏ gọn hơn (padding
            // tường minh thay vì min-height nội tại của OutlinedTextField).
            var searchFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.surface)
                    .lavenderBorder(colors, RoundedCornerShape(999.dp), active = searchFocused)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = state.search,
                    onValueChange = vm::setSearch,
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = colors.text),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.lav),
                    modifier = Modifier.fillMaxWidth()
                        .onFocusChanged { searchFocused = it.isFocused },
                    decorationBox = { inner ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Search, null, tint = colors.text4, modifier = Modifier.size(15.dp))
                            Box {
                                if (state.search.isEmpty()) {
                                    Text("Tìm học sinh...", fontSize = 13.sp, color = colors.text4)
                                }
                                inner()
                            }
                        }
                    }
                )
            }
        }
        // Tương đương 3 <select> filterClass/filterStatus/sortBy trong student-manager.jsx
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            StudentClassDropdown(colors = colors, classes = vm.classes(), selected = state.filterClass, onSelect = vm::setFilterClass, modifier = Modifier.weight(1f))
            StudentStatusDropdown(colors = colors, status = state.filterStatus, onSelect = vm::setFilterStatus, modifier = Modifier.weight(1f))
            StudentSortDropdown(colors = colors, sortBy = state.sortBy, onSelect = vm::setSortBy, modifier = Modifier.weight(1f))
        }

        if (state.loading) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.rose)
            }
        } else if (filtered.isEmpty()) {
            Text("Không có học sinh nào.", fontSize = 12.sp, color = colors.text3, modifier = Modifier.padding(vertical = 20.dp))
        }
        } // end header item

        if (!state.loading && filtered.isNotEmpty()) {
            items(filtered, key = { it.id }) { student ->
                Box(modifier = Modifier.padding(bottom = 10.dp)) {
                    StudentCard(
                        student = student, colors = colors, dark = dark,
                        bulkMode = state.bulkMode, selected = student.id in state.bulkSelected,
                        avatarUrl = avatarState.urls[student.id],
                        uploading = avatarState.uploadingId == student.id,
                        plainPassword = state.plainPassView[student.id],
                        onToggleSelect = { vm.toggleBulkSelect(student.id) },
                        onToggleActive = { vm.toggleActive(student) },
                        onEdit = { selected = student; modal = "edit" },
                        onDelete = { confirmDelete = student },
                        onResetPassword = { confirmResetPassword = student },
                        onAvatarClick = { requestAvatarPick(student.id) },
                        onDismissPlainPassword = { vm.dismissPlainPass(student.id) },
                        onCopyPassword = {
                            state.plainPassView[student.id]?.let {
                                clipboard.setText(AnnotatedString(it))
                                ToastCenter.show("Đã copy mật khẩu!", "✅", Color(0xFF10B981))
                            }
                        }
                    )
                }
            }
        }
    }

    if (modal == "add") {
        AddStudentModal(
            colors = colors, dark = dark, classes = vm.classes(), saving = state.saving,
            onDismiss = { modal = null },
            onSubmit = { username, name, className, password ->
                vm.addStudent(username, name, className, password,
                    onSuccess = { _, _ ->
                        modal = null
                        ToastCenter.show("Đã tạo tài khoản!", "🎉", Color(0xFF10B981))
                    },
                    onError = { msg -> ToastCenter.show(msg, "❌", Color(0xFFEF4444)) }
                )
            }
        )
    }

    if (modal == "edit" && selected != null) {
        EditStudentModal(
            student = selected!!, colors = colors, dark = dark, saving = state.saving, classes = vm.classes(),
            avatarUrl = avatarState.urls[selected!!.id], uploading = avatarState.uploadingId == selected!!.id,
            onAvatarClick = { requestAvatarPick(selected!!.id) },
            onDismiss = { modal = null; selected = null },
            onSubmit = { name, className ->
                vm.editStudent(selected!!.id, name, className) { ok, msg ->
                    ToastCenter.show(msg, if (ok) "✅" else "❌", if (ok) Color(0xFF10B981) else Color(0xFFEF4444))
                    if (ok) { modal = null; selected = null }
                }
            }
        )
    }

    // ViewCreatedModal đã bỏ — mật khẩu vừa tạo/reset giờ hiện inline trên
    // StudentCard (chip reveal-once) thay vì modal riêng, giống bản web.

    confirmDelete?.let { s ->
        ConfirmDialog(
            title = "Xoá tài khoản?",
            message = "@${s.username} · ${s.displayName ?: ""}\nHành động này không thể hoàn tác.",
            onDismiss = { confirmDelete = null },
            onConfirm = {
                scope.launch { runCatching { avatarRepo.delete(s.id) } }
                vm.deleteStudent(s.id) { ok, msg -> ToastCenter.show(msg, if (ok) "🗑️" else "❌", Color(0xFFEF4444)) }
            },
            colors = colors, dark = dark,
            confirmLabel = "Xoá", iconType = ConfirmIconType.DELETE
        )
    }

    if (confirmBulkDelete) {
        ConfirmDialog(
            title = "Xoá ${state.bulkSelected.size} tài khoản?",
            message = "Toàn bộ lịch sử bài làm và ảnh đại diện sẽ bị xóa vĩnh viễn.",
            onDismiss = { confirmBulkDelete = false },
            onConfirm = {
                val ids = state.bulkSelected.toList()
                scope.launch { ids.forEach { id -> runCatching { avatarRepo.delete(id) } } }
                vm.bulkDelete { ok, fail -> ToastCenter.show("Đã xóa $ok tài khoản${if (fail > 0) " ($fail lỗi)" else ""}!", "🗑️", Color(0xFFEF4444)) }
            },
            colors = colors, dark = dark,
            confirmLabel = "Xoá tất cả", iconType = ConfirmIconType.DELETE
        )
    }

    confirmResetPassword?.let { s ->
        ConfirmDialog(
            title = "Đặt lại mật khẩu?",
            message = "Tài khoản @${s.username}\nMật khẩu mới sẽ được tạo tự động theo định dạng Google.",
            onDismiss = { confirmResetPassword = null },
            onConfirm = {
                vm.resetPassword(s, null) { ok, msg, _ ->
                    ToastCenter.show(msg, if (ok) "✅" else "❌", if (ok) Color(0xFF10B981) else Color(0xFFEF4444))
                }
            },
            colors = colors, dark = dark,
            confirmLabel = "Đặt lại", confirmColor = colors.lav, iconType = ConfirmIconType.KEY
        )
    }
}

@Composable
private fun StudentClassDropdown(colors: LearnsyColors, classes: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val label = selected.ifBlank { "Tất cả lớp" }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, colors.border2)
        ) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(15.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Tất cả lớp") }, onClick = { expanded = false; onSelect("") })
            classes.forEach { c ->
                DropdownMenuItem(text = { Text(c) }, onClick = { expanded = false; onSelect(c) })
            }
        }
    }
}

@Composable
private fun StudentStatusDropdown(colors: LearnsyColors, status: StatusFilter, onSelect: (StatusFilter) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(StatusFilter.ALL to "Tất cả", StatusFilter.ACTIVE to "Hoạt động", StatusFilter.LOCKED to "Đã khoá")
    val label = options.find { it.first == status }?.second ?: "Tất cả"
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, colors.border2)
        ) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(15.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { expanded = false; onSelect(value) })
            }
        }
    }
}

@Composable
private fun StudentSortDropdown(colors: LearnsyColors, sortBy: StudentSortBy, onSelect: (StudentSortBy) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(StudentSortBy.NEWEST to "Mới nhất", StudentSortBy.NAME to "Tên A-Z", StudentSortBy.CLASS to "Theo lớp")
    val label = options.find { it.first == sortBy }?.second ?: "Mới nhất"
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, colors.border2)
        ) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(15.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { expanded = false; onSelect(value) })
            }
        }
    }
}

@Composable
private fun StatusPanel(state: StudentListUiState, saving: Boolean, nowUTC7: String, colors: LearnsyColors, dark: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .glowShadow(dark = dark, cornerRadius = 18.dp)
            .clip(RoundedCornerShape(18.dp)).background(colors.surface)
            .lavenderBorder(colors, RoundedCornerShape(18.dp)).padding(14.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusMini(if (state.srvStatus == "online") "Online" else if (state.srvStatus == "offline") "Offline" else "...", if (state.srvStatus == "online") Color(0xFF059669) else if (state.srvStatus == "offline") Color(0xFFEF4444) else colors.text3, Modifier.weight(1f))
            StatusMini(state.pingMs?.let { "${it}ms" } ?: "—", colors.text, Modifier.weight(1f))
            StatusMini(if (saving) "Đang lưu" else "Đã lưu", if (saving) Color(0xFFA855F7) else Color(0xFF059669), Modifier.weight(1f))
            StatusMini(nowUTC7.ifBlank { "--:--:--" }, colors.text, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.bg).padding(10.dp, 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            InfoRow("Backend", "Supabase (PostgREST)", colors)
            InfoRow("Bảng đang theo dõi", "students", colors)
            InfoRow("Số học sinh tải được", "${state.students.size}", colors)
            InfoRow("Tự ping mỗi", "1 giờ", colors)
        }
        Spacer(Modifier.height(12.dp))
        Text("HOẠT ĐỘNG GẦN ĐÂY", fontSize = 10.sp, fontWeight = FontWeight.Black, color = colors.text3, modifier = Modifier.padding(bottom = 6.dp))
        if (state.activityLog.isEmpty()) {
            Text("Chưa có hoạt động nào", fontSize = 11.sp, color = colors.text3, modifier = Modifier.padding(vertical = 8.dp))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.activityLog.forEach { item ->
                    val color = when (item.type) {
                        "error" -> Color(0xFFEF4444); "insert" -> Color(0xFFA855F7); "delete" -> Color(0xFFEF4444)
                        "update" -> Color(0xFFF59E0B); "fn" -> Color(0xFFF472B6); "ping" -> colors.text3; else -> Color(0xFF059669)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
                        Text(item.msg, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.text, modifier = Modifier.weight(1f))
                        val time = java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(item.ts))
                        Text(time, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.text3)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusMini(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.1f)).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Black, color = color)
    }
}

@Composable
private fun InfoRow(label: String, value: String, colors: LearnsyColors) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.text3)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Black, color = colors.text)
    }
}

@Composable
private fun StudentCard(
    student: Student,
    colors: LearnsyColors,
    dark: Boolean,
    bulkMode: Boolean,
    selected: Boolean,
    avatarUrl: String?,
    uploading: Boolean,
    plainPassword: String?,
    onToggleSelect: () -> Unit,
    onToggleActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onResetPassword: () -> Unit,
    onAvatarClick: () -> Unit,
    onDismissPlainPassword: () -> Unit,
    onCopyPassword: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .glowShadow(dark = dark, cornerRadius = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.5.dp, if (selected) Color(0xFFA855F7) else colors.border2, RoundedCornerShape(16.dp))
            .alpha(if (student.isActive) 1f else 0.55f)
            .padding(12.dp, 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (bulkMode) {
            Checkbox(checked = selected, onCheckedChange = { onToggleSelect() }, modifier = Modifier.padding(top = 2.dp))
        }

        // Avatar 40x40, bo góc 13dp, gradient hồng-tím — đúng kích thước web
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(13.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFF472B6), Color(0xFFA855F7))))
                .let { if (!bulkMode) it.clickable(onClick = onAvatarClick) else it },
            contentAlignment = Alignment.Center
        ) {
            if (uploading) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
            } else if (avatarUrl != null) {
                Image(
                    painter = rememberAsyncImagePainter(avatarUrl),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(13.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text((student.displayName ?: student.username).take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                student.displayName ?: student.username,
                fontSize = 13.sp, fontWeight = FontWeight.Black, color = colors.text,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("@${student.username}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.text3)
                if (student.className.isNotBlank()) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFA855F7).copy(alpha = if (dark) 0.13f else 0.08f)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                        Text(student.className, fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFFA855F7))
                    }
                }
                if (!student.isActive) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0xFFEF4444).copy(alpha = 0.1f)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                        Text("Khoá", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFFEF4444))
                    }
                }
            }
            // Chip mật khẩu reveal-once — tương đương plainPassView[s.id] trong student-manager.jsx
            if (plainPassword != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp))
                        .background(colors.rose.copy(alpha = 0.08f))
                        .border(1.dp, colors.rose.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Icon(Icons.Default.Lock, null, tint = colors.rose, modifier = Modifier.size(10.dp))
                    Text(plainPassword, fontSize = 11.sp, fontWeight = FontWeight.Black, color = colors.rose)
                    IconButton(onClick = onCopyPassword, modifier = Modifier.size(16.dp)) {
                        Icon(Icons.Default.ContentCopy, "Copy mật khẩu", tint = colors.lav, modifier = Modifier.size(10.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Text("×", fontSize = 13.sp, fontWeight = FontWeight.Black, color = colors.text3,
                        modifier = Modifier.clickable(onClick = onDismissPlainPassword).padding(horizontal = 2.dp))
                }
            }
        }

        // 4 icon nút vuông bo góc pastel — đúng màu/layout web: Sửa · Reset · Khoá · Xoá
        if (!bulkMode) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ActionIconButton(icon = Icons.Default.Edit, tint = Color(0xFFA855F7), bg = Color(0xFFA855F7).copy(alpha = 0.08f), border = Color(0xFFA855F7).copy(alpha = 0.28f), desc = "Sửa thông tin", onClick = onEdit)
                ActionIconButton(icon = Icons.Default.Refresh, tint = colors.text3, bg = colors.text3.copy(alpha = 0.08f), border = colors.text3.copy(alpha = 0.3f), desc = "Đặt lại mật khẩu", onClick = onResetPassword)
                if (student.isActive) {
                    ActionIconButton(icon = Icons.Default.LockOpen, tint = Color(0xFFF59E0B), bg = Color(0xFFF59E0B).copy(alpha = 0.1f), border = Color(0xFFF59E0B).copy(alpha = 0.32f), desc = "Khoá tài khoản", onClick = onToggleActive)
                } else {
                    ActionIconButton(icon = Icons.Default.Lock, tint = Color(0xFF059669), bg = Color(0xFF10B981).copy(alpha = 0.16f), border = Color(0xFF10B981).copy(alpha = 0.4f), desc = "Mở khoá", onClick = onToggleActive)
                }
                ActionIconButton(icon = Icons.Default.Delete, tint = Color(0xFFEF4444), bg = Color(0xFFEF4444).copy(alpha = 0.08f), border = Color(0xFFEF4444).copy(alpha = 0.3f), desc = "Xoá", onClick = onDelete)
            }
        }
    }
}

// Nút icon vuông bo góc 9dp, 30x30dp — đúng kích thước/style 4 nút action web
@Composable
private fun ActionIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, bg: Color, border: Color, desc: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(bg)
            .border(1.5.dp, border, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(13.dp))
    }
}

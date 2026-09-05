package com.learnsypro.app.admin.ui.screens

import android.content.Intent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.learnsypro.app.admin.data.Lesson
import com.learnsypro.app.admin.data.CardBlurLevel
import com.learnsypro.app.admin.data.QuestionType
import com.learnsypro.app.admin.data.SUBJECTS
import com.learnsypro.app.admin.data.newQuestion
import com.learnsypro.app.admin.ui.LessonEditorViewModel
import com.learnsypro.app.admin.ui.SaveStatus
import com.learnsypro.app.admin.ui.ToastCenter
import com.learnsypro.app.admin.ui.components.BowIcon
import com.learnsypro.app.admin.ui.components.QEditor
import com.learnsypro.app.admin.ui.components.SparkleIcon
import com.learnsypro.app.admin.ui.branding.AtomBadge
import com.learnsypro.app.admin.ui.theme.LearnsyColors
import com.learnsypro.app.admin.data.Question
import com.learnsypro.app.admin.data.importJSON
import com.learnsypro.app.admin.data.parseText
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val exportJson = Json { prettyPrint = true; encodeDefaults = true }

// Tương đương phần soạn bài (khi mở 1 lesson) trong app.jsx — ghép
// LessonEditorViewModel (state + auto-save debounce) với QEditor cho từng câu hỏi,
// và MergeQuestionsModal (merge-questions.css) để gộp câu từ bài khác vào.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LessonEditorScreen(
    colors: LearnsyColors,
    lessonId: String,
    onBack: () -> Unit,
    dark: Boolean = false,
    // FIX: viewModel() mặc định không biết cách cấp SavedStateHandle cho
    // constructor có thêm tham số `repo` — phải tự khai factory dùng
    // createSavedStateHandle() để ViewModel này lưu/khôi phục bài đang soạn
    // qua process death (xem comment chi tiết trong LessonEditorViewModel.kt).
    editorVm: LessonEditorViewModel = viewModel(
        factory = viewModelFactory {
            initializer { LessonEditorViewModel(savedStateHandle = createSavedStateHandle()) }
        }
    )
) {
    val state by editorVm.uiState.collectAsState()
    var showMergeModal by remember { mutableStateOf(false) }
    // FIX (chọn file JSON trong panel Import xong quay lại không thấy gì): showImportPanel trước
    // đây dùng remember thường — nếu Android thu hồi tiến trình nền trong lúc người dùng đang ở
    // trình chọn file hệ thống (chọn JSON để import), khi quay lại LessonEditorScreen được
    // compose lại từ đầu, showImportPanel reset về false mặc định -> panel Import tự đóng, kết
    // quả chọn file (filePicker trong ImportPanel) bị mất luôn vì panel không còn tồn tại để nhận
    // callback. Cùng nguyên nhân gốc với vụ filePickerLauncher trong FileManagerScreen (đã fix ở
    // AppRoot.kt) — dùng rememberSaveable để showImportPanel sống sót qua process-death, panel
    // vẫn mở lại đúng sau khi Activity resume, filePicker bên trong nó vẫn kịp đăng ký nhận kết
    // quả trả về.
    var showImportPanel by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val settingsStore = remember { com.learnsypro.app.admin.data.SettingsStore(context) }
    var cardBlur by remember { mutableStateOf(settingsStore.cardBlur) }
    // Tương đương noTitleWarn trong app.jsx — nhấp nháy đỏ 3s khi thoát mà chưa đặt tên bài
    var noTitleWarn by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(lessonId) { editorVm.loadLessonById(lessonId) }

    // Route Editor giờ tự fetch dữ liệu theo lessonId (nav argument) thay vì
    // nhận sẵn Lesson object từ màn cha — hiện màn chờ trong lúc fetch, và
    // báo lỗi rõ ràng (thay vì crash/màn trắng) nếu bài đã bị xoá từ nơi khác.
    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize().background(colors.bg), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.lav)
        }
        return
    }
    if (state.notFound) {
        Box(modifier = Modifier.fillMaxSize().background(colors.bg), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Không tìm thấy bài học này", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.text2)
                Text("Có thể đã bị xoá ở nơi khác", fontSize = 12.sp, color = colors.text3, modifier = Modifier.padding(top = 4.dp))
                TextButton(onClick = onBack, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Quay lại danh sách", color = colors.lav, fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    if (showMergeModal) {
        MergeQuestionsModal(
            colors = colors,
            excludeLessonId = lessonId,
            onDismiss = { showMergeModal = false },
            onMerge = { merged ->
                editorVm.setQuestions(state.questions + merged)
                ToastCenter.show("Đã gộp ${merged.size} câu hỏi vào bài tập!", "➕", colors.mint)
            }
        )
    }

    if (showImportPanel) {
        ImportPanel(
            colors = colors,
            onDismiss = { showImportPanel = false },
            onQuestionsParsed = { parsed ->
                editorVm.setQuestions(state.questions + parsed)
                showImportPanel = false
            }
        )
    }

    var showTabMenu by remember { mutableStateOf(false) }

    // Tương đương goHome() trong app.jsx — chặn thoát nếu chưa đặt tên bài,
    // nhấp nháy cảnh báo đỏ 3s rồi tự tắt.
    //
    // FIX: trước đây gọi onBack() ngay lập tức mà không chờ lưu xong. Vì
    // auto-save có debounce 800ms và cả manualSave lẫn debounce đều là
    // network call bất đồng bộ, người dùng gõ tên → thoát nhanh có thể
    // điều hướng về danh sách TRƯỚC KHI Supabase kịp ghi — màn danh sách
    // load lại thấy dữ liệu cũ (bug: lưu xong thoát ra vào lại mất tên).
    // Giờ luôn chờ manualSave() hoàn tất trước khi điều hướng.
    fun handleBack() {
        if (state.title.isBlank()) {
            noTitleWarn = true
            ToastCenter.show("Đặt tên cho bài tập trước khi thoát nhé!", "⚠️", Color(0xFFEF4444))
            scope.launch {
                delay(3000)
                noTitleWarn = false
            }
            return
        }
        scope.launch {
            val saved = editorVm.manualSave()
            if (saved) {
                onBack()
            } else {
                val msg = state.lastError
                if (state.titleDupWarn || msg == "Tên bài tập bị trùng") {
                    ToastCenter.show("Tên bài tập bị trùng, đổi tên khác nhé!", "⚠️", Color(0xFFF59E0B))
                } else {
                    ToastCenter.show("Lưu thất bại: ${msg ?: "không rõ nguyên nhân"} — thử lại nhé!", "❌", Color(0xFFEF4444))
                }
            }
        }
    }

    androidx.activity.compose.BackHandler(onBack = { handleBack() })

    // FIX: màn này tự vẽ full-screen (không qua Scaffold) nên trước đây bị
    // vẽ đè lên status bar (giờ/wifi/pin đè lên "Learnsy") và bị thanh điều
    // hướng hệ thống che mất hàng nút cuối cùng. Thêm systemBarsPadding để
    // tự chừa đúng khoảng trống 2 đầu, không phụ thuộc theme/OEM nào cắt bao nhiêu.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 12.dp, top = 13.dp, end = 12.dp, bottom = 32.dp)
    ) {
        // Hàng 1 — logo + badge "Quiz Builder", tương đương header trên cùng web
        // (logo-fl/logo-learnsy/logo-flb trong app.jsx) — dùng cùng khối "L" trên nền
        // gradient hồng-tím bo góc như header Bài học/Admin để đồng bộ toàn app,
        // thay vì icon Material (MenuBook/AutoAwesome) không khớp bộ nhận diện thương hiệu.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFF9A8D4), Color(0xFFC084FC))),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                AtomBadge(size = 14.dp, badgeColor = Color.White, backgroundColor = Color.Transparent)
            }
            Spacer(Modifier.width(6.dp))
            Text("Learnsy", fontSize = 15.sp, fontWeight = FontWeight.Black, color = colors.rose)
            Spacer(Modifier.width(3.dp))
            SparkleIcon(size = 11, color = colors.lav, modifier = Modifier.size(11.dp))
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(colors.lavL)
                    .border(1.dp, colors.border2, RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text("✦ Quiz Builder", fontSize = 10.sp, fontWeight = FontWeight.Black, color = colors.lav)
            }
        }

        // Hàng 2 — "Danh sách" back + badge số câu
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(colors.bg2)
                    .border(1.5.dp, if (noTitleWarn) Color(0xFFEF4444) else colors.border, RoundedCornerShape(999.dp))
                    .clickable { handleBack() }
                    .padding(horizontal = 11.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.ArrowBack, null, tint = colors.text3, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text("Danh sách", fontSize = 12.sp, fontWeight = FontWeight.Black, color = colors.text3)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(colors.rosePale)
                    .border(1.dp, colors.border, RoundedCornerShape(999.dp))
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text("${state.questions.size} câu", fontSize = 10.sp, fontWeight = FontWeight.Black, color = colors.rose)
            }
        }

        // Hàng 3 — tab "Soạn" (dropdown, chỉ 1 tab khả dụng: Preview/Import đã gộp vào Toolbox)
        // + Toolbox + nút lưu thủ công + trạng thái lưu.
        // horizontalScroll: trên màn hình hẹp 4 phần tử (Soạn/Toolbox/nút lưu/trạng thái)
        // không đủ chỗ và bị ép co lại/tràn viền — cuộn ngang thay vì vỡ layout,
        // giống hành vi thực tế bên web khi thanh công cụ quá dài.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 12.dp)
        ) {
            Box {
                com.learnsypro.app.admin.ui.components.IconPillButton(
                    icon = Icons.Default.List,
                    label = "Soạn",
                    trailingIcon = Icons.Default.KeyboardArrowDown,
                    tint = colors.rose,
                    background = if (showTabMenu) colors.rosePale else colors.bg2,
                    borderColor = if (showTabMenu) colors.rose else colors.border,
                    onClick = { showTabMenu = true }
                )
                DropdownMenu(expanded = showTabMenu, onDismissRequest = { showTabMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Soạn", fontWeight = FontWeight.Black, color = colors.rose) },
                        leadingIcon = { Icon(Icons.Default.List, null, tint = colors.rose) },
                        onClick = { showTabMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Nhập") },
                        leadingIcon = { Icon(Icons.Default.ContentPaste, null) },
                        onClick = { showTabMenu = false; showImportPanel = true }
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            ToolboxMenu(
                colors = colors,
                onImportClick = { showImportPanel = true },
                onMergeClick = { showMergeModal = true },
                onExportJsonClick = {
                    val fileName = (state.title.trim().ifBlank { "quiz" }
                        .replace(Regex("\\s+"), "-").lowercase()) + ".json"
                    try {
                        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                        val file = File(exportDir, fileName)
                        file.writeText(
                            exportJson.encodeToString(
                                ListSerializer(Question.serializer()),
                                state.questions
                            )
                        )
                        val uri = FileProvider.getUriForFile(context, "com.learnsypro.app.admin.fileprovider", file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Lưu / chia sẻ JSON"))
                        ToastCenter.show("Đã tạo file JSON! Lần sau import lại dễ dàng", "💾", colors.mint)
                    } catch (e: Exception) {
                        ToastCenter.show(e.message ?: "Lỗi khi xuất JSON", "❌", Color(0xFFEF4444))
                    }
                }
            )
            Spacer(Modifier.width(6.dp))
            ManualSaveButton(colors = colors, onClick = { scope.launch { editorVm.manualSave() } })
            Spacer(Modifier.width(6.dp))
            SaveStatusButton(
                status = state.saveStatus, lastError = state.lastError, colors = colors,
                lessonId = state.lessonId,
                lastSaveDurationMs = state.lastSaveDurationMs,
                lastSavedAt = state.lastSavedAt
            )
        }

        // Card "Tên bài học" — khớp JSX dòng 1524-1682: MỘT card duy nhất bọc
        // TẤT CẢ (Tên bài học, Môn học, Mật khẩu, nút toggle + nội dung Cài đặt
        // đề thi). Bản sửa trước đây tách thành 2 card riêng là SAI cấu trúc.
        Column(
            modifier = Modifier.fillMaxWidth()
                .shadow(3.dp, RoundedCornerShape(18.dp), ambientColor = Color(0xFFFF6496), spotColor = Color(0xFFFF6496))
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surface)
                .border(1.5.dp, colors.border, RoundedCornerShape(18.dp))
                .padding(horizontal = 15.dp, vertical = 13.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                BowIcon(size = 22)
                Text(
                    "TÊN BÀI HỌC", fontSize = 11.sp, fontWeight = FontWeight.Black,
                    color = colors.rose, letterSpacing = 0.8.sp
                )
            }
            // FIX: cùng vấn đề như ô Mật khẩu — OutlinedTextField (M3) áp
            // min-height/padding nội tại khá dày, ô "to" hơn mức cần thiết so
            // với 1 dòng chữ 14sp bên trong. Thay bằng BasicTextField tự vẽ để
            // làm chủ padding, viền đỏ cảnh báo trùng tên vẫn giữ nguyên.
            var titleFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .border(
                        1.5.dp,
                        if (state.titleDupWarn) Color(0xFFEF4444) else if (titleFocused) colors.lav else colors.border,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                BasicTextField(
                    value = state.title,
                    onValueChange = editorVm::setTitle,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = colors.text),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.lav),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { titleFocused = it.isFocused },
                    decorationBox = { inner ->
                        if (state.title.isEmpty()) {
                            Text(
                                "Nhập tên bài tập, ví dụ: Ôn tập Lịch sử Chương 3",
                                fontSize = 14.sp, color = colors.text4
                            )
                        }
                        inner()
                    }
                )
            }
            if (state.titleDupWarn) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFEF2F2))
                        .border(1.5.dp, Color(0xFFEF4444), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Icon(Icons.Default.Warning, null, tint = Color(0xFFEF4444), modifier = Modifier.size(13.dp))
                    Text(
                        "Trùng tên với bài tập khác — sẽ không lưu được, đổi tên khác nhé!",
                        fontSize = 11.5.sp, fontWeight = FontWeight.Black, color = Color(0xFFEF4444)
                    )
                }
            }
            if (noTitleWarn) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFEF2F2))
                        .border(1.5.dp, Color(0xFFEF4444), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Icon(Icons.Default.Warning, null, tint = Color(0xFFEF4444), modifier = Modifier.size(13.dp))
                    Text(
                        "Chưa đặt tên bài tập — đặt tên trước khi thoát nhé!",
                        fontSize = 11.5.sp, fontWeight = FontWeight.Black, color = Color(0xFFEF4444)
                    )
                }
            }
            // Môn học — khớp JSX dòng 1549: label + pill dropdown INLINE trên cùng dòng,
            // không phải ExposedDropdownMenuBox full-width như trước.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    "Môn học:", fontSize = 11.sp, fontWeight = FontWeight.Black,
                    color = colors.text3, letterSpacing = 0.5.sp
                )
                var subjExpanded by remember { mutableStateOf(false) }
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (subjExpanded) colors.lavL else colors.bg)
                            .border(1.5.dp, if (subjExpanded) colors.lav else colors.border, RoundedCornerShape(999.dp))
                            .clickable { subjExpanded = !subjExpanded }
                            .padding(horizontal = 13.dp, vertical = 5.dp)
                    ) {
                        // Icon sách mở — khớp SVG path JSX dòng 1558 (hai trang sách gập)
                        Icon(Icons.Default.MenuBook, null, tint = if (subjExpanded) colors.lav else colors.text2, modifier = Modifier.size(12.dp))
                        Text(
                            state.subject, fontSize = 12.sp, fontWeight = FontWeight.Black,
                            color = if (subjExpanded) colors.lav else colors.text2
                        )
                        Icon(
                            if (subjExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            null, tint = if (subjExpanded) colors.lav else colors.text2, modifier = Modifier.size(10.dp)
                        )
                    }
                    DropdownMenu(expanded = subjExpanded, onDismissRequest = { subjExpanded = false }) {
                        SUBJECTS.forEach { s ->
                            DropdownMenuItem(text = { Text(s) }, onClick = { editorVm.setSubject(s); subjExpanded = false })
                        }
                    }
                }
            }
            // Mật khẩu — khớp JSX dòng 1588: LUÔN hiển thị (không nằm trong phần
            // Cài đặt đề thi mở rộng như bản sửa trước đây nhầm vị trí).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Lock, null, tint = colors.text3, modifier = Modifier.size(11.dp))
                    Text(
                        "Mật khẩu:", fontSize = 11.sp, fontWeight = FontWeight.Black,
                        color = colors.text3, letterSpacing = 0.5.sp
                    )
                }
                // FIX: OutlinedTextField (M3) áp min-height nội tại khá lớn
                // (~56dp) bất kể .heightIn(min = 38.dp) đặt thêm — ô mật khẩu vì
                // vậy to hẳn ra so với hàng "Môn học"/badge cạnh nó. Tự vẽ bằng
                // BasicTextField để làm chủ hoàn toàn padding/chiều cao, cùng
                // pill bo tròn và màu sắc y hệt bản gốc.
                var passwordFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (state.password.isNotEmpty()) colors.lavPale else Color.Transparent)
                        .border(
                            1.5.dp,
                            if (passwordFocused) colors.lav else if (state.password.isNotEmpty()) colors.lav else colors.border,
                            RoundedCornerShape(999.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    BasicTextField(
                        value = state.password,
                        onValueChange = editorVm::setPassword,
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.text),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.lav),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { passwordFocused = it.isFocused },
                        decorationBox = { inner ->
                            if (state.password.isEmpty()) {
                                Text("Để trống = không cần mật khẩu", fontSize = 12.sp, color = colors.text4)
                            }
                            inner()
                        }
                    )
                }
                // Badge "Đã đặt" khi có mật khẩu — khớp JSX dòng 1595, trước đó thiếu hoàn toàn.
                if (state.password.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.lavL)
                            .border(1.dp, colors.border2, RoundedCornerShape(999.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Lock, null, tint = colors.lav, modifier = Modifier.size(11.dp))
                        Text("Đã đặt", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = colors.lav)
                    }
                }
            }
            // Khớp JSX dòng 1598: {marginTop:10} — nút toggle Cài đặt đề thi nằm NGAY
            // TRONG cùng Title card (không phải card riêng như bản sửa trước đây nhầm).
            var settingsExpanded by remember { mutableStateOf(false) }
            Column(modifier = Modifier.padding(top = 10.dp)) {
                // Khớp JSX: nút pill gọn 'padding:6px 13px', border đổi màu theo trạng thái mở,
                // KHÔNG chiếm full width (trước đó Compose dùng Row full-width sai kiểu).
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (settingsExpanded) colors.lavL else colors.bg2)
                        .border(1.5.dp, if (settingsExpanded) colors.lav else colors.border, RoundedCornerShape(999.dp))
                        .clickable { settingsExpanded = !settingsExpanded }
                        .padding(horizontal = 13.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Settings, null, tint = if (settingsExpanded) colors.lav else colors.text3, modifier = Modifier.size(13.dp))
                    Text(
                        "Cài đặt đề thi",
                        fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                        color = if (settingsExpanded) colors.lav else colors.text3
                    )
                    Icon(
                        if (settingsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null, tint = if (settingsExpanded) colors.lav else colors.text3, modifier = Modifier.size(10.dp)
                    )
                }
                if (settingsExpanded) {
                    // Khớp JSX: box nền bg2, border2, radius 14, padding '13px 14px'
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.bg2)
                            .border(1.5.dp, colors.border2, RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Khớp JSX: label và các nút cùng chung 1 FlowRow (flexWrap), không
                        // tách label ra dòng riêng như trước. padding nút: '4px 11px'.
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "⏱ Giới hạn thời gian:", fontSize = 11.sp, fontWeight = FontWeight.Black,
                                color = colors.text3, letterSpacing = 0.5.sp,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                            listOf(0, 5, 10, 15, 20, 30, 45, 60).forEach { m ->
                                val active = state.timerLimit == m
                                Box(
                                    modifier = Modifier.clip(RoundedCornerShape(999.dp))
                                        .background(if (active) colors.roseL else colors.bg2)
                                        .border(1.5.dp, if (active) colors.rose else colors.border, RoundedCornerShape(999.dp))
                                        .clickable { editorVm.setTimerLimit(m) }
                                        .padding(horizontal = 11.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        if (m == 0) "Không" else "${m}p",
                                        fontSize = 11.sp, fontWeight = FontWeight.Black,
                                        color = if (active) colors.rose else colors.text3
                                    )
                                }
                            }
                        }

                        // Tương đương "Card Blur" trong Cài đặt đề thi (app.jsx) — dùng chung
                        // key learnsy_card_blur với Dashboard/SettingsPanel để đồng bộ toàn app.
                        // Khớp JSX: label + nút cùng FlowRow, icon 2 vòng tròn overlap, padding '4px 12px'.
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.align(Alignment.CenterVertically)
                            ) {
                                Icon(Icons.Default.Circle, null, tint = colors.text3, modifier = Modifier.size(12.dp))
                                Text(
                                    "Blur card:", fontSize = 11.sp, fontWeight = FontWeight.Black,
                                    color = colors.text3, letterSpacing = 0.5.sp
                                )
                            }
                            data class BlurOpt(val level: CardBlurLevel, val label: String, val desc: String, val c: Color, val bg: Color)
                            listOf(
                                BlurOpt(CardBlurLevel.OFF, "Tắt", "không blur", colors.text3, colors.bg2),
                                BlurOpt(CardBlurLevel.FIFTY, "50%", "nhẹ, xuyên card", Color(0xFF0EA5E9), Color(0xFFE0F2FE)),
                                BlurOpt(CardBlurLevel.EIGHTY_FIVE, "85%", "mạnh, trong suốt", Color(0xFF8B5CF6), colors.lavL)
                            ).forEach { opt ->
                                val active = cardBlur == opt.level
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(1.dp),
                                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                        .background(if (active) opt.bg else colors.bg2)
                                        .border(1.5.dp, if (active) opt.c else colors.border, RoundedCornerShape(12.dp))
                                        .clickable {
                                            cardBlur = opt.level
                                            settingsStore.cardBlur = opt.level
                                        }
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(opt.label, fontSize = 11.sp, fontWeight = FontWeight.Black, color = if (active) opt.c else colors.text3)
                                    Text(opt.desc, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = if (active) opt.c else colors.text4)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        AddQuestionButton(colors = colors, onAdd = { type -> editorVm.addQuestion(newQuestion(type)) })

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 10.dp)) {
            Text("${state.questions.size} câu hỏi", fontSize = 12.sp, fontWeight = FontWeight.Black, color = colors.text2, modifier = Modifier.weight(1f))
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            state.questions.forEachIndexed { i, q ->
                QEditor(
                    q = q, qi = i,
                    onQuestionChange = { updated ->
                        editorVm.setQuestions(state.questions.mapIndexed { idx, old -> if (idx == i) updated else old })
                    },
                    onRemove = { editorVm.removeQuestion(q.id) },
                    canRemove = state.questions.size > 1,
                    colors = colors
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Tương đương "Tip box" cuối tab Soạn trong app.jsx
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(colors.gradSoft).border(1.5.dp, colors.border, RoundedCornerShape(16.dp))
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(colors.rosePale)
                    .border(1.5.dp, colors.border, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) { Text("🌸", fontSize = 14.sp) }
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.rose, fontWeight = FontWeight.Black)) { append("Lưu JSON") }
                    withStyle(SpanStyle(color = colors.text3)) { append(" = lưu để import lại sau  •  ") }
                    withStyle(SpanStyle(color = colors.text2, fontWeight = FontWeight.Black)) { append("Nhập nhanh") }
                    withStyle(SpanStyle(color = colors.text3)) { append(" = dán văn bản hoặc upload file JSON") }
                },
                fontSize = 11.5.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f)
            )
        }
    }
}

// Tương đương nút "Lưu thủ công" trong app.jsx — lưu ngay lập tức bỏ qua debounce,
// viền đỏ nhấp nháy liên tục (manualSavePulse keyframe) để nhắc người dùng đây là
// hành động thủ công, khác với auto-save nền.
@Composable
private fun ManualSaveButton(colors: LearnsyColors, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "manual-save-pulse")
    // FIX: trước đây "pulseAlpha by ..." rồi build Color(...).copy(alpha = pulseAlpha)
    // ngay trong thân composable — đọc state đó xảy ra ở composition phase, ép
    // ManualSaveButton (luôn hiện trong toolbar editor) recompose 60 lần/giây vĩnh
    // viễn. Giữ State<Float> và chỉ đọc .value trong graphicsLayer{} (draw phase).
    val pulseAlphaState = infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "manual-save-pulse-alpha"
    )
    // Cùng size/radius/ripple/scale với mọi IconActionButton khác trong khu vực
    // soạn bài (bậc Large 34dp) — riêng viền pulse đỏ vẽ đè lên trên để giữ
    // đặc trưng "đây là lưu thủ công, khác auto-save nền".
    Box(contentAlignment = Alignment.Center) {
        com.learnsypro.app.admin.ui.components.IconActionButton(
            icon = Icons.Default.Save,
            contentDescription = "Lưu thủ công",
            onClick = onClick,
            size = com.learnsypro.app.admin.ui.components.IconBtnSize.Large,
            tint = Color(0xFFEF4444),
            background = Color(0xFFFEF2F2),
            borderColor = Color(0xFFEF4444),
            shape = CircleShape
        )
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .graphicsLayer { alpha = pulseAlphaState.value }
                .border(4.dp, Color(0xFFEF4444), CircleShape)
        )
    }
}

@Composable
private fun AddQuestionButton(colors: LearnsyColors, onAdd: (QuestionType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    // Tương đương getTypes() trong app.jsx — mô tả ngắn cho từng loại câu hỏi
    fun descOf(t: QuestionType): String = when (t) {
        QuestionType.TRUE_FALSE -> "Đoạn tư liệu + 4 ý đúng/sai"
        QuestionType.MULTIPLE -> "4 lựa chọn, 1 đáp án đúng"
        QuestionType.MULTI_SELECT -> "Nhiều đáp án đúng"
        QuestionType.FILL_BLANK -> "Điền từ vào chỗ trống"
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.lavPale)
                .border(1.5.dp, colors.lav2, RoundedCornerShape(14.dp))
                .clickable { expanded = true }
                .padding(vertical = 13.dp)
        ) {
            Icon(Icons.Default.Add, null, tint = colors.lav, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("Thêm câu hỏi mới", fontSize = 13.sp, fontWeight = FontWeight.Black, color = colors.lav)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            QuestionType.values().forEach { type ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(type.label, fontSize = 13.sp, fontWeight = FontWeight.Black, color = colors.text)
                            Text(descOf(type), fontSize = 11.sp, color = colors.text3)
                        }
                    },
                    onClick = { onAdd(type); expanded = false }
                )
            }
        }
    }
}

// Tương đương nút "Toolbox" trong header web (dropdown: Lưu JSON / Gộp câu hỏi).
// Export HTML tương tác không port sang app vì đó là công cụ build file đặc thù
// cho trình duyệt (đóng gói toàn bộ engine quiz vào 1 file .html) — không hợp
// với ngữ cảnh app native.
@Composable
private fun ToolboxMenu(
    colors: LearnsyColors,
    onImportClick: () -> Unit,
    onMergeClick: () -> Unit,
    onExportJsonClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        com.learnsypro.app.admin.ui.components.IconPillButton(
            icon = Icons.Default.Build,
            label = "Toolbox",
            tint = if (expanded) colors.rose else colors.text3,
            background = if (expanded) colors.rosePale else colors.bg2,
            borderColor = if (expanded) colors.rose else colors.border,
            onClick = { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // Tương đương tab "Dán văn bản / Upload JSON" trong app.jsx
            DropdownMenuItem(
                text = { Text("Nhập nhanh (dán / JSON)", color = colors.rose, fontWeight = FontWeight.Black) },
                leadingIcon = { Icon(Icons.Default.ContentPaste, null, tint = colors.rose) },
                onClick = { expanded = false; onImportClick() }
            )
            DropdownMenuItem(
                text = { Text("Lưu JSON", color = colors.mint, fontWeight = FontWeight.Black) },
                leadingIcon = { Icon(Icons.Default.Save, null, tint = colors.mint) },
                onClick = { expanded = false; onExportJsonClick() }
            )
            DropdownMenuItem(
                text = { Text("Gộp câu hỏi", color = colors.mint, fontWeight = FontWeight.Black) },
                leadingIcon = { Icon(Icons.Default.CallMerge, null, tint = colors.mint) },
                onClick = { expanded = false; onMergeClick() }
            )
        }
    }
}

// Tương đương nút lưu tích hợp giờ VN trong header web — hình tròn màu đổi theo
// saveStatus, bấm vào mở panel chi tiết (giờ hiện tại, trạng thái, lỗi gần nhất...).
@Composable
private fun SaveStatusButton(
    status: SaveStatus,
    lastError: String?,
    colors: LearnsyColors,
    lessonId: String?,
    lastSaveDurationMs: Long?,
    lastSavedAt: String?
) {
    var panelOpen by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(vnTimeNow()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = vnTimeNow()
            kotlinx.coroutines.delay(1000)
        }
    }

    val bgColor = when (status) {
        SaveStatus.ERROR, SaveStatus.DUP_BLOCKED -> Color(0xFFEF4444)
        SaveStatus.SAVING, SaveStatus.PENDING -> Color(0xFFF59E0B)
        SaveStatus.SAVED -> Color(0xFF10B981)
        SaveStatus.IDLE -> colors.lav
    }

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .heightIn(min = 34.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(bgColor)
                .clickable { panelOpen = !panelOpen }
                .padding(start = 11.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Text(now, fontSize = 10.5.sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.92f))
            Box(
                modifier = Modifier.size(22.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                if (status == SaveStatus.SAVING || status == SaveStatus.PENDING) {
                    CircularProgressIndicator(modifier = Modifier.size(11.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Save, null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
        }

        if (panelOpen) {
            androidx.compose.ui.window.Popup(
                alignment = Alignment.TopEnd,
                offset = androidx.compose.ui.unit.IntOffset(0, 52),
                onDismissRequest = { panelOpen = false },
                properties = androidx.compose.ui.window.PopupProperties(focusable = true)
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 200.dp, max = 260.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        Icon(Icons.Default.Save, null, tint = colors.rose, modifier = Modifier.size(14.dp))
                        Text("Trạng thái lưu bài", fontSize = 13.sp, fontWeight = FontWeight.Black, color = colors.text)
                    }
                    val nowDate = remember { java.time.LocalDate.now() }
                    val dayNames = listOf("Chủ Nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy")
                    val dayName = dayNames[nowDate.dayOfWeek.value % 7]
                    StatusRow("Giờ hiện tại", "$now · $dayName, ${nowDate.dayOfMonth}/${nowDate.monthValue}", colors.text2, colors)
                    StatusRow(
                        "Trạng thái",
                        when (status) {
                            SaveStatus.IDLE -> "Chưa có thay đổi"
                            SaveStatus.PENDING -> "Đang chờ (debounce)…"
                            SaveStatus.SAVING -> "Đang lưu…"
                            SaveStatus.SAVED -> "Đã lưu"
                            SaveStatus.ERROR -> "Lỗi khi lưu"
                            SaveStatus.DUP_BLOCKED -> "Bị chặn — tên trùng"
                        },
                        bgColor, colors
                    )
                    if (status == SaveStatus.SAVED && lastSaveDurationMs != null) {
                        StatusRow("Thời gian lưu", "${lastSaveDurationMs} ms", colors.text2, colors)
                    }
                    if (lastSavedAt != null) {
                        StatusRow("Lưu lần cuối", lastSavedAt, colors.text2, colors)
                    }
                    StatusRow("Lưu ở đâu", "Supabase · bảng \"lessons\"", colors.text2, colors)
                    if (lessonId != null) {
                        StatusRow("ID bài", lessonId, colors.text2, colors)
                    }
                    if (lastError != null) {
                        StatusRow("Lỗi gần nhất", lastError, Color(0xFFEF4444), colors)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, valueColor: Color, colors: LearnsyColors) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.text3)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Black, color = valueColor, maxLines = 1)
    }
}

// Tương đương tab "import" trong app.jsx (dòng ~1728-1794) — 2 khối:
// upload file JSON (importJSON) và dán văn bản tự nhận diện (parseText).
// onQuestionsParsed nhận list câu hỏi đã parse để LessonEditorScreen nối vào questions[].
@Composable
private fun ImportPanel(
    colors: LearnsyColors,
    onDismiss: () -> Unit,
    onQuestionsParsed: (List<Question>) -> Unit
) {
    var rawText by remember { mutableStateOf("") }
    var parsing by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            if (text.isNullOrBlank()) {
                ToastCenter.show("! File JSON rỗng!", "⚠️", Color(0xFFF59E0B))
                return@rememberLauncherForActivityResult
            }
            val parsed = importJSON(text)
            if (parsed.isEmpty()) {
                ToastCenter.show("! Không tìm thấy câu hỏi trong JSON!", "⚠️", Color(0xFFF59E0B))
            } else {
                onQuestionsParsed(parsed)
                ToastCenter.show("+ Đã import ${parsed.size} câu từ JSON!", "✅", colors.mint)
            }
        } catch (e: Exception) {
            ToastCenter.show("x File JSON lỗi: ${e.message}", "❌", Color(0xFFEF4444))
        }
    }

    suspend fun handleParse() {
        if (rawText.isBlank()) {
            ToastCenter.show("! Dán nội dung câu hỏi vào trước!", "⚠️", Color(0xFFF59E0B))
            return
        }
        parsing = true
        delay(400) // Tương đương setTimeout 400ms trong app.jsx (hiệu ứng "đang phân tích")
        try {
            val parsed = parseText(rawText)
            if (parsed.isEmpty()) {
                ToastCenter.show("! Không nhận diện được. Thử định dạng khác!", "⚠️", Color(0xFFF59E0B))
            } else {
                onQuestionsParsed(parsed)
                ToastCenter.show("+ Đã thêm ${parsed.size} câu hỏi!", "✅", colors.mint)
            }
        } catch (e: Exception) {
            ToastCenter.show("x Lỗi: ${e.message}", "❌", Color(0xFFEF4444))
        }
        parsing = false
    }

    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier.fillMaxSize().background(colors.bg)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text(
                    "Nhập nhanh câu hỏi", fontSize = 16.sp, fontWeight = FontWeight.Black, color = colors.text,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Đóng", tint = colors.text3) }
            }

            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ── Upload JSON ──
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(colors.surface).border(1.5.dp, colors.border2, RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(13.dp))
                                .background(colors.lavL).border(1.5.dp, colors.border2, RoundedCornerShape(13.dp)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.UploadFile, null, tint = colors.lav, modifier = Modifier.size(20.dp)) }
                        Column {
                            Text("Upload file JSON", fontSize = 14.sp, fontWeight = FontWeight.Black, color = colors.lav)
                            Text(
                                "Upload file .json đã lưu từ lần trước. Hỗ trợ nhiều format JSON phổ biến.",
                                fontSize = 12.sp, color = colors.text3, lineHeight = 18.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(13.dp))
                    OutlinedButton(
                        onClick = { filePicker.launch("application/json") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(2.dp, colors.lav2),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = colors.lavL, contentColor = colors.lav)
                    ) {
                        Icon(Icons.Default.InsertDriveFile, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Chọn file JSON", fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.height(11.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
                            .background(colors.lavPale).padding(12.dp)
                    ) {
                        Text("Format hỗ trợ:", fontSize = 11.5.sp, fontWeight = FontWeight.Black, color = colors.text2)
                        Text("• Array: [{type, question, options, correct}]", fontSize = 11.5.sp, color = colors.text3, lineHeight = 19.sp)
                        Text("• Object: {questions: [...]}", fontSize = 11.5.sp, color = colors.text3, lineHeight = 19.sp)
                        Text("• Nhiều tên field: question / content / câu_hỏi, answer / correct / key...", fontSize = 11.5.sp, color = colors.text3, lineHeight = 19.sp)
                    }
                }

                // ── Dán văn bản ──
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(colors.gradSoft).border(1.5.dp, colors.border, RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(13.dp))
                                .background(colors.roseL).border(1.5.dp, colors.border, RoundedCornerShape(13.dp)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Edit, null, tint = colors.rose, modifier = Modifier.size(20.dp)) }
                        Column {
                            Text("Dán văn bản — nhận diện tự động", fontSize = 14.sp, fontWeight = FontWeight.Black, color = colors.rose)
                            Text(
                                "Dán từ sách, đề thi... Hệ thống tự phân tích không cần mạng internet.",
                                fontSize = 12.sp, color = colors.text3, lineHeight = 18.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(11.dp))
                    OutlinedTextField(
                        value = rawText,
                        onValueChange = { rawText = it },
                        placeholder = {
                            Text(
                                "Dán câu hỏi vào đây — hỗ trợ:\n\n" +
                                    "• Đúng/Sai: Đoạn văn + a. ... S  b. ... Đ\n" +
                                    "• Trắc nghiệm: Câu hỏi + A. ... B. ... Answer: A\n" +
                                    "• Điền chỗ trống: Câu có ___ + Answer: từ\n\n" +
                                    "Có thể dán nhiều câu cùng lúc!",
                                fontSize = 12.sp, color = colors.text4
                            )
                        },
                        textStyle = TextStyle(color = colors.text, fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.text, unfocusedTextColor = colors.text,
                            focusedBorderColor = colors.lav, unfocusedBorderColor = colors.border,
                            cursorColor = colors.lav
                        ),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions.Default
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { scope.launch { handleParse() } },
                        enabled = !parsing,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.rose)
                    ) {
                        if (parsing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.5.dp, color = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Đang phân tích...", fontWeight = FontWeight.Black)
                        } else {
                            Icon(Icons.Default.Search, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Nhận diện & Thêm câu hỏi", fontWeight = FontWeight.Black)
                        }
                    }
                }

                // ── Ví dụ định dạng ──
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(colors.surface).border(1.5.dp, colors.border, RoundedCornerShape(16.dp))
                        .padding(15.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Star, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp))
                        Text(
                            "VÍ DỤ ĐỊNH DẠNG VĂN BẢN", fontSize = 11.sp, fontWeight = FontWeight.Black, color = colors.mint,
                            letterSpacing = 0.8.sp
                        )
                    }
                    Spacer(Modifier.height(11.dp))
                    FormatExample("Đúng/Sai", colors.lav, colors.lavL,
                        "Câu 1. Cho đoạn: \"...\" \na. Nội dung ý a. Đ\nb. Nội dung ý b. S", colors)
                    FormatExample("Trắc nghiệm", colors.rose, colors.roseL,
                        "Câu 2. Ngô Quyền đánh trận nào?\nA. Bạch Đằng\nB. Chi Lăng\nC. Đống Đa\nD. Điện Biên Phủ\nAnswer: A", colors)
                    FormatExample("Điền chỗ trống", colors.peach, colors.peachL,
                        "Câu 3. Ngô Quyền đánh quân ___ năm 938.\nAnswer: Nam Hán", colors)
                }

                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun FormatExample(label: String, color: Color, bg: Color, example: String, colors: LearnsyColors) {
    Column(modifier = Modifier.padding(bottom = 10.dp)) {
        Box(modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(bg).padding(horizontal = 9.dp, vertical = 2.dp)) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Black, color = color)
        }
        Text(
            example, fontSize = 11.5.sp, color = colors.text2, lineHeight = 19.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.bg)
                .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                .padding(horizontal = 11.dp, vertical = 9.dp)
        )
    }
}

private fun vnTimeNow(): String {
    val fmt = SimpleDateFormat("HH:mm:ss", Locale("vi", "VN"))
    fmt.timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    return fmt.format(Date())
}

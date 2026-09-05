package com.learnsypro.app.admin.ui.screens

import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.learnsypro.app.admin.data.ListeningItem
import com.learnsypro.app.admin.ui.*
import com.learnsypro.app.admin.ui.theme.LearnsyColors
import java.util.Locale

enum class ListeningTab { LIST, FORM, STATS }

// Key cố định để phân biệt "đang đọc ô đoạn văn trong form" với đọc 1 item cụ thể trong list
private const val FORM_TTS_KEY = "__form__"

// Tương đương ListeningManager trong listening-panel.jsx (không gồm ListeningPreview —
// đã loại bỏ theo yêu cầu bỏ tính năng Thử đề).
@Composable
fun ListeningManagerScreen(
    colors: LearnsyColors,
    dark: Boolean,
    refreshKey: Any = Unit,
    listVm: ListeningListViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ListeningListViewModel(savedStateHandle = createSavedStateHandle()) }
        }
    ),
    // FIX: viewModel() mặc định không biết cách cấp SavedStateHandle cho
    // constructor có thêm tham số `repo` -> phải tự khai factory dùng
    // createSavedStateHandle() (API chuẩn của lifecycle-viewmodel-compose)
    // để ViewModel này lưu/khôi phục state qua process death (xem comment
    // chi tiết trong ListeningFormViewModel.kt).
    formVm: ListeningFormViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ListeningFormViewModel(savedStateHandle = createSavedStateHandle()) }
        }
    )
) {
    // FIX: trước đây tab (LIST/FORM/STATS) là 1 biến rememberSaveable RIÊNG,
    // tách biệt hoàn toàn khỏi SavedStateHandle của formVm. Khi vào Recents
    // (Activity trải qua onSaveInstanceState, dù chỉ vài giây) rồi quay lại,
    // 2 cơ chế lưu khác nhau có thể LỆCH PHA: nội dung form (SavedStateHandle,
    // gắn ViewModelStore của Activity) khôi phục đúng, nhưng tab
    // (rememberSaveable, phụ thuộc vị trí composition trong
    // SaveableStateRegistry — có thể đụng độ slot khi nằm sâu trong nhánh
    // AnimatedContent/when của AppRoot) lại rơi về LIST — dù nội dung vẫn
    // còn, người dùng chỉ thấy list rỗng, tưởng mất hết. Giờ tab được lưu
    // NGAY TRONG ListeningFormUiState (field activeTab), đọc/ghi qua chính
    // formVm — chỉ còn 1 cơ chế lưu duy nhất, không thể lệch pha nữa.
    val formState by formVm.uiState.collectAsState()
    val tab = when (formState.activeTab) {
        "FORM" -> ListeningTab.FORM
        "STATS" -> ListeningTab.STATS
        else -> ListeningTab.LIST
    }
    fun setTab(t: ListeningTab) = formVm.setActiveTab(t.name)
    // FIX: back vật lý/gesture của điện thoại trước đây không bị chặn ở
    // đây — khi đang ở form "Thêm câu Listening mới" (tab == FORM) hoặc màn
    // Thống kê (STATS), bấm back không có Composable nào tiêu thụ sự kiện,
    // nên nó rơi thẳng xuống Activity gốc và THOÁT HẲN RA HOME, mất luôn nội
    // dung đang soạn — dù dữ liệu vẫn còn trong ViewModel, người dùng không
    // biết đường quay lại đúng chỗ. Chặn back vật lý ở đây: nếu đang ở
    // FORM/STATS thì quay về LIST trước, chỉ khi đã ở LIST mới cho back
    // vật lý đi tiếp ra ngoài (thoát app) như bình thường.
    androidx.activity.compose.BackHandler(enabled = tab != ListeningTab.LIST) {
        if (tab == ListeningTab.FORM) formVm.resetForm() else setTab(ListeningTab.LIST)
    }
    var showImport by remember { mutableStateOf(false) }
    var importJson by remember { mutableStateOf("") }
    var mismatchDialog by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var deleteTarget by remember { mutableStateOf<ListeningItem?>(null) }
    var bulkDeleteConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Tương đương handleTTS()/ttsSpeed trong listening-panel.jsx (dùng window.speechSynthesis) —
    // Android dùng TextToSpeech engine native. Khởi tạo 1 lần, giải phóng khi rời màn
    // (tương đương useEffect cleanup ()=>window.speechSynthesis?.cancel() khi unmount).
    // speakKey dùng để phân biệt "đang đọc cái nào" — id thật cho card trong list,
    // hằng số cố định "__form__" cho ô đoạn văn đang soạn trong form (chưa có id).
    var ttsReady by remember { mutableStateOf(false) }
    var speakingKey by remember { mutableStateOf<String?>(null) }
    var ttsSpeed by remember { mutableFloatStateOf(1f) }
    val tts = remember {
        arrayOfNulls<TextToSpeech>(1).also { holder ->
            holder[0] = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    holder[0]?.language = Locale.US
                    ttsReady = true
                }
            }
        }[0]
    }
    DisposableEffect(Unit) {
        onDispose { tts?.stop(); tts?.shutdown() }
    }
    fun speak(key: String, rawText: String) {
        val engine = tts ?: return
        if (!ttsReady) { ToastCenter.show("! Chưa sẵn sàng đọc, thử lại sau", "⚠️", Color(0xFFF59E0B)); return }
        if (speakingKey == key) {
            engine.stop(); speakingKey = null; return
        }
        val plain = com.learnsypro.app.admin.data.stripHTML(rawText)
            .replace(Regex("_{3,}"), " blank ").replace(Regex("\\s+"), " ").trim()
        if (plain.isEmpty()) return
        engine.setSpeechRate(ttsSpeed)
        engine.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { speakingKey = key }
            override fun onDone(utteranceId: String?) { speakingKey = null }
            override fun onError(utteranceId: String?) { speakingKey = null }
        })
        engine.speak(plain, TextToSpeech.QUEUE_FLUSH, null, key)
    }
    fun speakItem(item: ListeningItem) = speak(item.id, item.text)

    // Tương đương exportJSON() trong listening-panel.jsx — tải file .json chứa toàn bộ items
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val json = kotlinx.serialization.json.Json { prettyPrint = true }
            val data = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(ListeningItem.serializer()), listVm.uiState.value.items)
            context.contentResolver.openOutputStream(uri)?.use { it.write(data.toByteArray()) }
            ToastCenter.show("✓ Đã xuất file JSON", "✅", Color(0xFF059669))
        } catch (e: Exception) {
            ToastCenter.show("Xuất file thất bại: ${e.message}", "❌", Color(0xFFEF4444))
        }
    }

    val listState by listVm.uiState.collectAsState()
    val displayItems by remember(
        listState.items, listState.filter, listState.searchQuery, listState.sortBy
    ) {
        derivedStateOf { listVm.displayItems() }
    }

    LaunchedEffect(refreshKey) { listVm.loadIfNeeded(refreshKey) }

    deleteTarget?.let { item ->
        com.learnsypro.app.admin.ui.components.ConfirmDialog(
            title = "Xoá câu Listening?",
            message = com.learnsypro.app.admin.data.stripHTML(item.text).take(60),
            onDismiss = { deleteTarget = null },
            onConfirm = { listVm.deleteItem(item.id) { _, msg -> ToastCenter.show(msg, "🗑️", Color(0xFFEF4444)) } },
            colors = colors, dark = dark,
            confirmLabel = "Xoá", iconType = com.learnsypro.app.admin.ui.components.ConfirmIconType.DELETE
        )
    }
    if (bulkDeleteConfirm) {
        com.learnsypro.app.admin.ui.components.ConfirmDialog(
            title = "Xoá ${listState.selected.size} câu Listening?",
            message = "Không thể hoàn tác.",
            onDismiss = { bulkDeleteConfirm = false },
            onConfirm = { listVm.bulkDelete { _, msg -> ToastCenter.show(msg, "🗑️", Color(0xFFEF4444)) } },
            colors = colors, dark = dark,
            confirmLabel = "Xoá tất cả", iconType = com.learnsypro.app.admin.ui.components.ConfirmIconType.DELETE
        )
    }
    mismatchDialog?.let { pair ->
        val blanks = pair.first; val ans = pair.second
        com.learnsypro.app.admin.ui.components.ConfirmDialog(
            title = "Số chỗ trống không khớp",
            message = "Văn bản có $blanks chỗ trống (___) nhưng bạn nhập $ans đáp án. Vẫn tiếp tục lưu?",
            onDismiss = { mismatchDialog = null },
            onConfirm = {
                formVm.confirmSaveAnyway(
                    listState.items,
                    onSaved = { item, isNew ->
                        if (isNew) listVm.load() else listVm.replaceItem(item)
                        setTab(ListeningTab.LIST)
                        ToastCenter.show(if (isNew) "+ Đã thêm câu Listening!" else "+ Đã cập nhật câu Listening!", "✅", Color(0xFF10B981))
                    },
                    onError = { msg -> ToastCenter.show("Lưu thất bại: $msg", "❌", Color(0xFFEF4444)) }
                )
            },
            colors = colors, dark = dark,
            confirmLabel = "Lưu anyway", confirmColor = Color(0xFFF59E0B),
            iconType = com.learnsypro.app.admin.ui.components.ConfirmIconType.WARN
        )
    }

    // Dùng LazyColumn thay vì Column+verticalScroll: mọi nội dung (header, form
    // "Thêm câu Listening mới" dài, hoặc danh sách items) cuộn được trong 1 layer
    // duy nhất, đồng thời danh sách items chỉ compose phần đang hiện trên màn hình
    // (lazy) thay vì toàn bộ list cùng lúc — quan trọng khi có hàng trăm câu.
    LazyColumn(
        state = listVm.listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp, 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        item {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(colors.lavL)
                    .border(1.5.dp, colors.border2, RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Headphones, null, tint = colors.lav, modifier = Modifier.size(17.dp)) }
            Column(modifier = Modifier.weight(1f)) {
                Text("Listening", fontSize = 18.sp, fontWeight = FontWeight.Black, color = colors.text)
                Text("${listState.items.size} câu · Đoạn văn + Điền từ + True/False/NM", fontSize = 12.sp, color = colors.text3)
            }
            SquareIconBtn(
                icon = Icons.Default.BarChart, contentDescription = "Thống kê", tint = colors.lav,
                background = if (tab == ListeningTab.STATS) colors.lavL else colors.bg2,
                borderColor = colors.border2,
                onClick = { setTab(if (tab == ListeningTab.STATS) ListeningTab.LIST else ListeningTab.STATS) }
            )
            SquareIconBtn(
                icon = Icons.Default.Download, contentDescription = "Xuất JSON", tint = Color(0xFF059669),
                background = colors.bg2, borderColor = colors.border2,
                onClick = {
                    val filename = "listening_items_${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())}.json"
                    exportLauncher.launch(filename)
                }
            )
            SquareIconBtn(
                icon = Icons.Default.Upload, contentDescription = "Import JSON", tint = Color(0xFFD97706),
                background = if (showImport) Color(0xFFD97706).copy(alpha = 0.12f) else colors.bg2,
                borderColor = colors.border2,
                onClick = { showImport = !showImport }
            )
            Button(
                onClick = { formVm.resetForm(); setTab(if (tab == ListeningTab.FORM) ListeningTab.LIST else ListeningTab.FORM) },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.lav),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Thêm", fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = colors.border)

        if (listState.loadError) {
            Spacer(Modifier.height(14.dp))
            Text(
                "Không tải được dữ liệu Listening — kiểm tra bảng listening_items đã tạo trên Supabase chưa.",
                color = Color(0xFFDC2626), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(Color(0xFFDC2626).copy(alpha = 0.08f), RoundedCornerShape(12.dp)).padding(12.dp)
            )
        }

        if (tab == ListeningTab.STATS) { Spacer(Modifier.height(14.dp)); ListeningStatsPanel(listState.items, colors) }

        if (showImport) {
            Spacer(Modifier.height(14.dp))
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFD97706).copy(alpha = 0.06f))
                    .border(1.5.dp, Color(0xFFD97706).copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Text("Import JSON", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFFD97706))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = importJson, onValueChange = { importJson = it },
                    placeholder = { Text("Dán JSON mảng [...] vào đây") },
                    minLines = 4, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.text, unfocusedTextColor = colors.text,
                        focusedBorderColor = Color(0xFFD97706), unfocusedBorderColor = colors.border,
                        cursorColor = Color(0xFFD97706)
                    )
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            try {
                                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                                val parsed = json.decodeFromString<List<ListeningItem>>(importJson)
                                listVm.importItems(parsed) { ok, msg ->
                                    ToastCenter.show(msg, if (ok) "✅" else "❌", if (ok) Color(0xFF10B981) else Color(0xFFEF4444))
                                    if (ok) { importJson = ""; showImport = false }
                                }
                            } catch (e: Exception) {
                                ToastCenter.show("JSON không hợp lệ: ${e.message}", "❌", Color(0xFFEF4444))
                            }
                        },
                        enabled = importJson.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                    ) { Text("Import", fontWeight = FontWeight.Black) }
                    TextButton(onClick = { showImport = false; importJson = "" }) { Text("Huỷ") }
                }
            }
        }

        if (listState.loading) {
            Spacer(Modifier.height(14.dp))
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.lav)
            }
        }
        } // end fixed header item

        if (tab == ListeningTab.LIST && !listState.loading) {
            listeningListContent(
                colors = colors, dark = dark, listVm = listVm, listState = listState, formVm = formVm,
                displayItems = displayItems,
                onOpenForm = { setTab(ListeningTab.FORM) },
                onRequestDelete = { deleteTarget = it },
                onRequestBulkDelete = { bulkDeleteConfirm = true },
                speakingId = speakingKey,
                onSpeak = ::speakItem
            )
        }

        if (tab == ListeningTab.FORM) {
            item {
            ListeningFormContent(
                colors = colors, formVm = formVm, formState = formState, listState = listState,
                onCancel = { formVm.resetForm() },
                onSaved = { isNew ->
                    listVm.load()
                    setTab(ListeningTab.LIST)
                    ToastCenter.show(if (isNew) "+ Đã thêm câu Listening!" else "+ Đã cập nhật câu Listening!", "✅", Color(0xFF10B981))
                },
                onMismatch = { blanks, ans -> mismatchDialog = blanks to ans },
                isSpeaking = speakingKey == FORM_TTS_KEY,
                onSpeakForm = { text -> speak(FORM_TTS_KEY, text) },
                ttsSpeed = ttsSpeed,
                onSpeedChange = { ttsSpeed = it }
            )
            }
        }
    }
}

private fun LazyListScope.listeningListContent(
    colors: LearnsyColors,
    dark: Boolean,
    listVm: ListeningListViewModel,
    listState: ListeningListUiState,
    formVm: ListeningFormViewModel,
    displayItems: List<ListeningItem>,
    onOpenForm: () -> Unit,
    onRequestDelete: (ListeningItem) -> Unit,
    onRequestBulkDelete: () -> Unit,
    speakingId: String?,
    onSpeak: (ListeningItem) -> Unit
) {
    item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (listState.items.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.lav.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp)
                ) {
                    Icon(Icons.Default.Search, null, tint = colors.text4, modifier = Modifier.size(15.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (listState.searchQuery.isEmpty()) {
                            Text("Tìm câu...", fontSize = 13.sp, color = colors.text4)
                        }
                        androidx.compose.foundation.text.BasicTextField(
                            value = listState.searchQuery,
                            onValueChange = listVm::setSearch,
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = colors.text),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.lav),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                TextButton(onClick = { listVm.toggleBulkMode() }) {
                    Text(if (listState.bulkMode) "Thoát" else "Chọn nhiều", fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
            // Tương đương 2 <select> filter/sort trong listening-panel.jsx
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ListeningFilterDropdown(colors = colors, filter = listState.filter, onSelect = listVm::setFilter, modifier = Modifier.weight(1f))
                ListeningSortDropdown(colors = colors, sortBy = listState.sortBy, onSelect = listVm::setSortBy, modifier = Modifier.weight(1f))
            }
        }

        if (listState.bulkMode) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFDC2626).copy(alpha = 0.06f))
                    .border(1.5.dp, Color(0xFFDC2626).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(10.dp, 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (listState.selected.isNotEmpty()) "Đã chọn ${listState.selected.size} câu" else "Chọn câu để xoá hàng loạt",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626), modifier = Modifier.weight(1f)
                )
                if (listState.selected.isNotEmpty()) {
                    TextButton(onClick = onRequestBulkDelete) { Text("Xoá ${listState.selected.size}", color = Color(0xFFDC2626), fontWeight = FontWeight.Black) }
                } else {
                    TextButton(onClick = { listVm.selectAll() }) { Text("Chọn tất cả") }
                }
            }
        }

        if (listState.items.isEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Chưa có câu Listening nào.", fontSize = 12.sp, color = colors.text3, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = { formVm.resetForm(); onOpenForm() }) { Text("+ Thêm câu đầu tiên") }
            }
        } else if (displayItems.isEmpty()) {
            Text("Không tìm thấy câu nào khớp với bộ lọc.", fontSize = 12.sp, color = colors.text3, modifier = Modifier.padding(vertical = 16.dp))
        }
        }
    }

    itemsIndexed(displayItems, key = { _, item -> item.id }) { idx, item ->
        Box(modifier = Modifier.padding(top = 10.dp)) {
            ListeningItemCard(
                item = item, colors = colors, dark = dark, order = idx + 1,
                bulkMode = listState.bulkMode, selected = item.id in listState.selected,
                onToggleSelect = { listVm.toggleSelect(item.id) },
                onEdit = { formVm.openForEdit(item); onOpenForm() },
                onDuplicate = { listVm.duplicateItem(item) { _, msg -> ToastCenter.show(msg, "✓", Color(0xFF059669)) } },
                onDelete = { onRequestDelete(item) },
                isSpeaking = speakingId == item.id,
                onSpeak = { onSpeak(item) },
                // Nút lên/xuống thay cho kéo-thả (phù hợp thao tác chạm hơn trên mobile) —
                // chỉ có ý nghĩa khi đang sắp theo "Thứ tự" và không ở chế độ chọn nhiều.
                showReorder = listState.sortBy == ListeningSort.ORDER && !listState.bulkMode,
                canMoveUp = idx > 0,
                canMoveDown = idx < displayItems.size - 1,
                onMoveUp = { if (idx > 0) listVm.reorder(item.id, displayItems[idx - 1].id) },
                onMoveDown = { if (idx < displayItems.size - 1) listVm.reorder(item.id, displayItems[idx + 1].id) }
            )
        }
    }
}

@Composable
private fun ListeningItemCard(
    item: ListeningItem,
    colors: LearnsyColors,
    dark: Boolean,
    order: Int,
    bulkMode: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    isSpeaking: Boolean,
    onSpeak: () -> Unit,
    showReorder: Boolean = false,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {}
) {
    // Glow tím nhẹ quanh card — đồng bộ LessonCard, đúng tông app.jsx
    val glowColor = if (dark) Color.Black.copy(alpha = 0.24f) else Color(0xFFA855F7).copy(alpha = 0.12f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val radiusPx = 20.dp.toPx()
                val layers = 4
                for (i in layers downTo 1) {
                    val spread = (i * 3).dp.toPx()
                    val alpha = glowColor.alpha * (1f - i.toFloat() / layers) * 0.9f
                    drawRoundRect(
                        color = glowColor.copy(alpha = alpha),
                        topLeft = androidx.compose.ui.geometry.Offset(-spread, -spread + 3.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(size.width + spread * 2, size.height + spread * 2),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radiusPx + spread, radiusPx + spread)
                    )
                }
            }
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) colors.lavPale else colors.bg2)
            .border(1.5.dp, if (selected) colors.lav else colors.border, RoundedCornerShape(20.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (bulkMode) {
            Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
        }
        if (showReorder) {
            Column {
                IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, "Lên", tint = if (canMoveUp) colors.text3 else colors.border, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, "Xuống", tint = if (canMoveDown) colors.text3 else colors.border, modifier = Modifier.size(16.dp))
                }
            }
        }
        // Order badge #idx — tương đương <span>#{idx+1}</span> trong listening-panel.jsx
        Box(
            modifier = Modifier.padding(top = 1.dp).clip(RoundedCornerShape(999.dp)).background(colors.lavL)
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Text("#$order", fontSize = 10.sp, fontWeight = FontWeight.Black, color = colors.lav)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                com.learnsypro.app.admin.data.stripHTML(item.text).take(90).ifBlank { "(Chưa có nội dung)" },
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.text, maxLines = 2
            )
            Spacer(Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (item.answers.isNotEmpty()) Tag("${item.answers.size} chỗ trống", Color(0xFF059669), colors)
                if (item.wordBox.isNotEmpty()) Tag("${item.wordBox.size} từ WB", Color(0xFF4338CA), colors)
                if (item.statements.isNotEmpty()) Tag("${item.statements.size} T/F/NM", Color(0xFFDC2626), colors)
            }
        }
        // Action buttons — ô vuông bo góc, viền + nền màu riêng từng nút,
        // khớp iconBtn() trong listening-panel.jsx (thay vì IconButton tròn Material mặc định).
        FlowRow2(horizontalSpacing = 5.dp, verticalSpacing = 5.dp) {
            SquareIconBtn(
                icon = if (isSpeaking) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = "Nghe thử",
                tint = colors.mint,
                background = colors.mintL,
                borderColor = colors.mint.copy(alpha = 0.35f),
                onClick = onSpeak
            )
            SquareIconBtn(
                icon = Icons.Default.ContentCopy,
                contentDescription = "Nhân đôi",
                tint = Color(0xFF059669),
                background = Color(0xFF059669).copy(alpha = 0.08f),
                borderColor = Color(0xFF059669).copy(alpha = 0.3f),
                onClick = onDuplicate
            )
            SquareIconBtn(
                icon = Icons.Default.Edit,
                contentDescription = "Sửa",
                tint = colors.lav,
                background = colors.lavL,
                borderColor = colors.border2,
                onClick = onEdit
            )
            SquareIconBtn(
                icon = Icons.Default.Delete,
                contentDescription = "Xoá",
                tint = Color(0xFFEF4444),
                background = colors.rosePale,
                borderColor = Color(0xFFFECDD3),
                onClick = onDelete
            )
        }
    }
}

// Tương đương iconBtn() helper trong listening-panel.jsx — nút icon hình vuông
// bo góc 8px, viền 1.5px + nền màu nhạt, KHÔNG phải hình tròn Material mặc định.
// Dùng Box + clickable thay IconButton để khớp đúng kích thước 26x26 của JSX
// (IconButton M3 tự thêm khung chạm 48dp tối thiểu, làm nút to/lệch hơn bản gốc).
@Composable
private fun SquareIconBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    background: Color,
    borderColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(13.dp))
    }
}

// FlowRow tối giản (tránh phụ thuộc ExperimentalLayoutApi ở nơi gọi) — bọc dòng
// khi 4 nút hành động không đủ chỗ trên màn hẹp, tương đương flexWrap trong JSX.
@Composable
private fun FlowRow2(horizontalSpacing: androidx.compose.ui.unit.Dp, verticalSpacing: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(horizontalSpacing)) { content() }
}

@Composable
private fun Tag(text: String, color: Color, colors: LearnsyColors) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(text, fontSize = 9.sp, fontWeight = FontWeight.Black, color = color)
    }
}

@Composable
private fun ListeningFilterDropdown(colors: LearnsyColors, filter: ListeningFilter, onSelect: (ListeningFilter) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        ListeningFilter.ALL to "Tất cả", ListeningFilter.HAS_WORD_BOX to "Có Word Box",
        ListeningFilter.HAS_TFNM to "Có T/F/NM", ListeningFilter.NO_WORD_BOX to "Không có WB"
    )
    val label = options.find { it.first == filter }?.second ?: "Tất cả"
    Box(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { expanded = false; onSelect(value) })
            }
        }
    }
}

@Composable
private fun ListeningSortDropdown(colors: LearnsyColors, sortBy: ListeningSort, onSelect: (ListeningSort) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        ListeningSort.ORDER to "Thứ tự", ListeningSort.CREATED to "Mới nhất", ListeningSort.BLANKS to "Nhiều chỗ trống"
    )
    val label = options.find { it.first == sortBy }?.second ?: "Thứ tự"
    Box(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { expanded = false; onSelect(value) })
            }
        }
    }
}

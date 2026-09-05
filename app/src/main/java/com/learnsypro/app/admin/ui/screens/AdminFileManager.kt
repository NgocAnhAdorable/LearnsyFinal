package com.learnsypro.app.admin.ui.screens

// ═══════════════════════════════════════════════════════════════════════════
// AdminFileManager.kt — Lõi "File Manager" của user Admin (Learnsy Pro)
//
// Gộp 3 thành phần trước đây nằm rời rạc ở 3 file/package khác nhau thành 1
// codebase duy nhất, theo đúng luồng dữ liệu:
//   1) AdminLearningFileRepository — tầng dữ liệu (Supabase: bảng learning_files
//      + Storage bucket "learning_files")
//   2) FileManagerViewModel        — tầng state/logic (StateFlow, upload/sửa/xoá)
//   3) FileManagerScreen           — tầng giao diện Compose (tab "Tài liệu")
//
// Repository và ViewModel được chuyển từ package admin.data / admin.ui về
// CHUNG package admin.ui.screens với Screen để cả 3 nằm trong 1 file — nhưng
// vẫn giữ nguyên TÊN class/hàm (AdminLearningFileRepository, FileManagerViewModel,
// FileManagerScreen) nên nơi gọi duy nhất, AppRoot.kt (MainTab.FILES ->
// FileManagerScreen(...)), không cần sửa gì, chỉ cần import lại đúng package mới
// nếu trình biên dịch báo thiếu import.
// ═══════════════════════════════════════════════════════════════════════════

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.learnsypro.app.admin.ui.restoredLazyListState
import com.learnsypro.app.admin.ui.saveLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.learnsypro.app.admin.data.*
import com.learnsypro.app.admin.ui.ToastCenter
import com.learnsypro.app.admin.ui.components.IconActionButton
import com.learnsypro.app.admin.ui.components.IconBtnSize
import com.learnsypro.app.admin.ui.theme.LearnsyColors
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────
// 1) DATA — AdminLearningFileRepository
// ─────────────────────────────────────────────────────────────────────────

// Tương đương các hàm CRUD + upload/xoá Storage trong file-manager.jsx (admin web)
class AdminLearningFileRepository {
    private val table = SupabaseConfig.client.from("learning_files")
    private val bucket get() = SupabaseConfig.client.storage.from(LEARNING_FILE_BUCKET)

    suspend fun fetchAll(): List<LearningFile> =
        table.select {
            order("sort_order", Order.ASCENDING)
            order("created_at", Order.DESCENDING)
        }.decodeList<LearningFile>()

    // Tương đương upload lên Storage rồi insert/update row trong FileFormModal.handleSave()
    suspend fun uploadNew(
        title: String, description: String, subject: String,
        filename: String, bytes: ByteArray, contentType: String?
    ): LearningFile {
        val storagePath = "files/${genFileId()}_${sanitizeFilename(filename)}"
        bucket.upload(storagePath, bytes)
        val publicUrl = bucket.publicUrl(storagePath)
        // FIX "invalid input syntax for type": dùng LearningFileInsert (không có id/created_at)
        // thay vì gửi nguyên LearningFile — để Postgres tự sinh id (gen_random_uuid()) và
        // created_at (now()) theo đúng default đã khai trong SQL của bảng, thay vì Kotlin tự gửi
        // created_at="" (rỗng) khiến Postgres không parse được thành timestamptz. select() trong
        // builder yêu cầu Postgrest trả lại đúng row vừa insert (kèm id/created_at thật do server
        // sinh) để app có ngay object đầy đủ, không cần fetch lại danh sách.
        val payload = LearningFileInsert(
            title = title, description = description, subject = subject,
            filename = filename, path = publicUrl, storagePath = storagePath,
            size = bytes.size.toLong(), sortOrder = 0
        )
        return table.insert(payload) { select() }.decodeSingle<LearningFile>()
    }

    // Tương đương nhánh "sửa + đổi file mới" trong handleSave(): upload file mới,
    // xoá file cũ khỏi Storage, update row
    suspend fun replaceFile(
        existing: LearningFile, title: String, description: String, subject: String,
        filename: String, bytes: ByteArray, contentType: String?
    ): LearningFile {
        val storagePath = "files/${genFileId()}_${sanitizeFilename(filename)}"
        bucket.upload(storagePath, bytes)
        if (existing.storagePath.isNotBlank()) {
            runCatching { bucket.delete(listOf(existing.storagePath)) }
        }
        val publicUrl = bucket.publicUrl(storagePath)
        // FIX cùng lý do uploadNew(): LearningFileUpdate không có created_at, tránh ghi đè
        // created_at thật của row đang sửa thành chuỗi rỗng.
        val payload = LearningFileUpdate(
            title = title, description = description, subject = subject,
            filename = filename, path = publicUrl, storagePath = storagePath, size = bytes.size.toLong()
        )
        return table.update(payload) {
            select()
            filter { eq("id", existing.id) }
        }.decodeSingle<LearningFile>()
    }

    // Tương đương nhánh "chỉ sửa metadata, không đổi file" trong handleSave()
    suspend fun updateMetadata(id: String, title: String, description: String, subject: String) {
        table.update({
            set("title", title)
            set("description", description)
            set("subject", subject)
        }) { filter { eq("id", id) } }
    }

    suspend fun delete(file: LearningFile) {
        if (file.storagePath.isNotBlank()) {
            runCatching { bucket.delete(listOf(file.storagePath)) }
        }
        table.delete { filter { eq("id", file.id) } }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// 2) VIEWMODEL — FileManagerViewModel
// ─────────────────────────────────────────────────────────────────────────

data class FileManagerUiState(
    val files: List<LearningFile> = emptyList(),
    val loading: Boolean = true,
    val search: String = "",
    val uploading: Boolean = false,
    val deletingId: String? = null
)

// Tương đương FileManager (file-manager.jsx) trên web — quản lý tài liệu
// cho học sinh tải về, file thật lưu Supabase Storage bucket "learning_files".
class FileManagerViewModel(
    private val repo: AdminLearningFileRepository = AdminLearningFileRepository(),
    private val savedStateHandle: androidx.lifecycle.SavedStateHandle? = null
) : ViewModel() {

    // FIX "app tự refresh khi vào lại" — cùng cách với LessonListViewModel:
    // trước đây FileManagerViewModel không nhận SavedStateHandle nên khi hệ
    // thống kill tiến trình nền rồi mở lại, danh sách tài liệu về rỗng, phải
    // chờ fetch mạng lại từ đầu thay vì có ngay như các tab khác.
    private val cachedFiles: List<LearningFile> = savedStateHandle
        ?.get<String>(KEY_FILES_CACHE)
        ?.let { runCatching { kotlinx.serialization.json.Json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(LearningFile.serializer()), it) }.getOrNull() }
        ?: emptyList()

    private val _uiState = MutableStateFlow(FileManagerUiState(files = cachedFiles, loading = cachedFiles.isEmpty()))
    val uiState: StateFlow<FileManagerUiState> = _uiState.asStateFlow()

    // Vị trí cuộn của LazyColumn danh sách tài liệu. Đặt ở ViewModel (sống ngoài
    // AnimatedContent, không bị huỷ khi đổi tab) thay vì rememberLazyListState()
    // ngay trong FileManagerScreen — nếu để trong Screen, mỗi lần thoát tab Files
    // rồi quay lại, Screen bị dispose/mount lại nên state cuộn tạo mới, luôn nhảy
    // về đầu danh sách dù dữ liệu không đổi.
    // FIX (cùng lý do LessonListViewModel): khôi phục từ SavedStateHandle để
    // vị trí cuộn sống sót qua process death, không chỉ qua chuyển tab.
    val listState = savedStateHandle.restoredLazyListState(KEY_SCROLL)

    init {
        _uiState.onEach { state ->
            savedStateHandle?.set(
                KEY_FILES_CACHE,
                kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(LearningFile.serializer()), state.files)
            )
        }.launchIn(viewModelScope)
        if (savedStateHandle != null) {
            androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .onEach { savedStateHandle.saveLazyListState(KEY_SCROLL, listState) }
                .launchIn(viewModelScope)
        }
    }

    companion object {
        private const val KEY_FILES_CACHE = "file_manager_cache_json"
        private const val KEY_SCROLL = "file_manager_scroll"
    }

    // Ghi nhớ refreshKey lần gần nhất Screen báo lên, để phân biệt "vừa bấm nút
    // refresh thật sự" (refreshKey đổi giá trị) với "Composable chỉ mount lại do
    // chuyển tab qua lại" (refreshKey y hệt lần trước, vì AppRoot chỉ tăng
    // refreshTick khi người dùng bấm nút refresh, không tăng khi đổi tab).
    private var lastRefreshKey: Any? = null

    /**
     * Gọi khi Screen mount hoặc khi refreshKey đổi. ViewModel giờ sống ngoài
     * AnimatedContent (hoist ở AppRoot) nên KHÔNG bị tạo lại mỗi lần thoát/vào
     * tab — chỉ load lại khi thực sự cần: lần đầu tiên (files rỗng) hoặc khi
     * refreshKey đổi giá trị thật (bấm nút refresh). Tránh nhấp nháy xoá-rồi-
     * load-lại toàn bộ danh sách mỗi khi quay lại tab Tệp tin.
     */
    fun loadIfNeeded(refreshKey: Any) {
        val keyChanged = refreshKey != lastRefreshKey
        lastRefreshKey = refreshKey
        if (_uiState.value.files.isEmpty() || keyChanged) {
            load()
        }
    }

    fun load() {
        // Chỉ bật spinner toàn màn hình khi CHƯA có dữ liệu gì (lần đầu mở tab).
        // Khi bấm nút refresh mà danh sách cũ đã có sẵn, giữ nguyên list đang
        // hiển thị trong lúc chờ dữ liệu mới — tránh xoá trắng màn hình rồi vẽ
        // lại (nhấp nháy) chỉ để refresh dữ liệu đã có sẵn từ trước.
        val showSpinner = _uiState.value.files.isEmpty()
        _uiState.update { it.copy(loading = showSpinner) }
        viewModelScope.launch {
            try {
                val files = repo.fetchAll()
                _uiState.update { it.copy(files = files, loading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false) }
                ToastCenter.show("Không tải được danh sách tài liệu", "⚠️", Color(0xFFF59E0B))
            }
        }
    }

    fun setSearch(q: String) = _uiState.update { it.copy(search = q) }

    fun filteredFiles(): List<LearningFile> {
        val s = _uiState.value
        val q = s.search.trim().lowercase()
        if (q.isEmpty()) return s.files
        return s.files.filter {
            it.title.lowercase().contains(q) || it.description.lowercase().contains(q) || it.subject.lowercase().contains(q)
        }
    }

    // Tương đương handleSave() trong FileFormModal (nhánh thêm mới / đổi file)
    fun uploadNew(
        title: String, description: String, subject: String,
        filename: String, sizeBytes: Long, bytes: ByteArray, contentType: String?,
        onDone: (Boolean, String) -> Unit
    ) {
        if (title.isBlank()) { onDone(false, "Nhập tên tài liệu nhé!"); return }
        if (sizeBytes > LEARNING_FILE_MAX_MB * 1024 * 1024) { onDone(false, "File tối đa ${LEARNING_FILE_MAX_MB}MB"); return }
        _uiState.update { it.copy(uploading = true) }
        viewModelScope.launch {
            try {
                val created = repo.uploadNew(title.trim(), description.trim(), subject.trim(), filename, bytes, contentType)
                _uiState.update { it.copy(files = listOf(created) + it.files, uploading = false) }
                onDone(true, "Đã thêm tài liệu mới!")
            } catch (e: Exception) {
                _uiState.update { it.copy(uploading = false) }
                onDone(false, fileErrorMessage(e))
            }
        }
    }

    fun replaceFile(
        existing: LearningFile, title: String, description: String, subject: String,
        filename: String, sizeBytes: Long, bytes: ByteArray, contentType: String?,
        onDone: (Boolean, String) -> Unit
    ) {
        if (title.isBlank()) { onDone(false, "Nhập tên tài liệu nhé!"); return }
        if (sizeBytes > LEARNING_FILE_MAX_MB * 1024 * 1024) { onDone(false, "File tối đa ${LEARNING_FILE_MAX_MB}MB"); return }
        _uiState.update { it.copy(uploading = true) }
        viewModelScope.launch {
            try {
                val updated = repo.replaceFile(existing, title.trim(), description.trim(), subject.trim(), filename, bytes, contentType)
                _uiState.update { st ->
                    st.copy(files = st.files.map { if (it.id == existing.id) updated else it }, uploading = false)
                }
                onDone(true, "Đã cập nhật tài liệu!")
            } catch (e: Exception) {
                _uiState.update { it.copy(uploading = false) }
                onDone(false, fileErrorMessage(e))
            }
        }
    }

    // Tương đương nhánh "chỉ sửa metadata" trong handleSave()
    fun updateMetadata(id: String, title: String, description: String, subject: String, onDone: (Boolean, String) -> Unit) {
        if (title.isBlank()) { onDone(false, "Nhập tên tài liệu nhé!"); return }
        _uiState.update { it.copy(uploading = true) }
        viewModelScope.launch {
            try {
                repo.updateMetadata(id, title.trim(), description.trim(), subject.trim())
                _uiState.update { st ->
                    st.copy(
                        files = st.files.map { if (it.id == id) it.copy(title = title.trim(), description = description.trim(), subject = subject.trim()) else it },
                        uploading = false
                    )
                }
                onDone(true, "Đã cập nhật tài liệu!")
            } catch (e: Exception) {
                _uiState.update { it.copy(uploading = false) }
                onDone(false, fileErrorMessage(e))
            }
        }
    }

    fun deleteFile(file: LearningFile, onDone: (Boolean, String) -> Unit) {
        _uiState.update { it.copy(deletingId = file.id) }
        viewModelScope.launch {
            try {
                repo.delete(file)
                _uiState.update { it.copy(files = it.files.filter { f -> f.id != file.id }, deletingId = null) }
                onDone(true, "Đã xoá tài liệu")
            } catch (e: Exception) {
                _uiState.update { it.copy(deletingId = null) }
                onDone(false, "Xoá thất bại, thử lại nhé!")
            }
        }
    }

    private fun fileErrorMessage(e: Exception): String {
        val msg = e.message?.lowercase() ?: ""
        return if (msg.contains("bucket")) "Lỗi storage: tạo bucket \"learning_files\" trong Supabase nhé!" else (e.message ?: "Có lỗi xảy ra, thử lại nhé!")
    }
}

// ─────────────────────────────────────────────────────────────────────────
// 2.5) PICKER — FileManagerPickerState + rememberFileManagerPicker()
// ─────────────────────────────────────────────────────────────────────────

// FIX THẬT SỰ (bug: chọn file ở tab Tài liệu xong quay lại app không thấy gì
// xảy ra — không toast, không lỗi). Trước đây filePickerLauncher +
// pickedUri/pickedName/pickedSize/pickErr nằm ngay trong FileManagerScreen —
// nhưng FileManagerScreen được compose bên trong AnimatedContent(currentTab)
// ở AppRoot, nên launcher có thể bị dispose/đăng ký lại trễ hơn thời điểm
// ActivityResultRegistry redeliver kết quả khi quay lại từ trình chọn file hệ
// thống (Samsung "My Files" — Storage Access Framework, nặng hơn nhiều so với
// Google Photo Picker mà nút đổi ảnh đại diện dùng, nên dễ bị hệ thống thu hồi
// tiến trình nền hơn). Class này tách launcher + state ra khỏi Screen, để
// AppRoot có thể remember nó ở NGOÀI AnimatedContent (ngang hàng fileManagerVm)
// — cùng cách đã áp dụng cho ViewModel — và truyền xuống làm tham số.
class FileManagerPickerState internal constructor(
    private val launcher: androidx.activity.result.ActivityResultLauncher<String>,
    private val pickedUriState: MutableState<Uri?>,
    private val pickedNameState: MutableState<String?>,
    private val pickedSizeState: MutableState<Long>,
    private val pickErrState: MutableState<String>,
    private val showFormRequest: MutableState<Boolean>
) {
    val pickedUri: Uri? get() = pickedUriState.value
    val pickedName: String? get() = pickedNameState.value
    val pickedSize: Long get() = pickedSizeState.value
    val pickErr: String get() = pickErrState.value
    // true đúng 1 lần ngay sau khi chọn file thành công, để Screen biết cần mở
    // lại form (trường hợp showForm lỡ bị reset) — Screen đọc xong tự set về false.
    val shouldShowForm: Boolean get() = showFormRequest.value

    fun consumeShowFormRequest() { showFormRequest.value = false }

    fun pick() = launcher.launch("*/*")

    fun clear() {
        pickedUriState.value = null
        pickedNameState.value = null
        pickedSizeState.value = 0L
        pickErrState.value = ""
    }
}

@Composable
fun rememberFileManagerPicker(): FileManagerPickerState {
    val context = LocalContext.current
    val pickedUri = rememberSaveable { mutableStateOf<Uri?>(null) }
    val pickedName = rememberSaveable { mutableStateOf<String?>(null) }
    val pickedSize = rememberSaveable { mutableStateOf(0L) }
    val pickErr = rememberSaveable { mutableStateOf("") }
    val showFormRequest = rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        var name = "file"
        var size = 0L
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
            }
        }
        if (size > LEARNING_FILE_MAX_MB * 1024L * 1024L) {
            pickErr.value = "File tối đa ${LEARNING_FILE_MAX_MB}MB"
            return@rememberLauncherForActivityResult
        }
        pickErr.value = ""
        pickedUri.value = uri; pickedName.value = name; pickedSize.value = size
        showFormRequest.value = true
    }

    return remember(launcher) {
        FileManagerPickerState(launcher, pickedUri, pickedName, pickedSize, pickErr, showFormRequest)
    }
}

// ─────────────────────────────────────────────────────────────────────────
// 3) UI — FileManagerScreen
// ─────────────────────────────────────────────────────────────────────────

// Tương đương FileManager (file-manager.jsx) trên web — tab "Tài liệu".
// File thật upload lên Supabase Storage, bucket "learning_files".
@Composable
fun FileManagerScreen(
    colors: LearnsyColors,
    dark: Boolean,
    refreshKey: Any = Unit,
    vm: FileManagerViewModel = viewModel(),
    picker: FileManagerPickerState
) {
    val state by vm.uiState.collectAsState()
    // loadIfNeeded thay vì load() vô điều kiện: khi vm được hoist ở AppRoot (không
    // bị tạo lại mỗi lần đổi tab), refreshKey giữ nguyên giá trị lúc quay lại tab
    // này -> không load lại, giữ đúng danh sách/vị trí cuộn/từ khoá tìm kiếm đang có.
    // Chỉ load lại khi refreshKey thực sự đổi (bấm nút refresh) hoặc lần đầu mở app.
    LaunchedEffect(refreshKey) { vm.loadIfNeeded(refreshKey) }

    // FIX: cùng lý do với pickedUri/... bên FileFormModal — showForm/editing
    // dùng remember{} thường trước đây khiến modal tự đóng (state về mặc định)
    // nếu tiến trình nền bị hệ thống kill trong lúc app Files đang mở foreground
    // để chọn ảnh. rememberSaveable giữ được showForm=true qua process death,
    // modal sẽ hiện lại đúng như lúc rời đi khi Activity dựng lại.
    // editing lưu qua editingId (String, kiểu nguyên thuỷ rememberSaveable hỗ
    // trợ sẵn) rồi tra lại LearningFile từ state.files khi cần — LearningFile
    // chỉ @Serializable (kotlinx) chứ không phải Parcelable/java.io.Serializable
    // nên rememberSaveable không thể lưu trực tiếp object này (sẽ crash).
    var showForm by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = editingId?.let { id -> state.files.find { it.id == id } }
    var deleteTarget by remember { mutableStateOf<LearningFile?>(null) }

    // FIX THẬT SỰ (bug chọn file xong không thấy gì): launcher + state file đã
    // chọn không còn sống ở đây nữa — đã hoist hẳn lên AppRoot qua tham số
    // `picker` (xem rememberFileManagerPicker() và comment ở đó). FileManagerScreen
    // giờ chỉ ĐỌC picker.pickedUri/pickedName/pickedSize/pickErr và gọi
    // picker.pick() khi cần mở trình chọn file — không tự đăng ký
    // rememberLauncherForActivityResult nào nữa, nên không còn phụ thuộc vào
    // việc Screen có đang được AnimatedContent compose hay không.
    val pickedUri = picker.pickedUri
    val pickedName = picker.pickedName
    val pickedSize = picker.pickedSize
    val pickErr = picker.pickErr

    // Đảm bảo modal đang mở khi picker vừa trả về kết quả (trường hợp showForm
    // bị đóng/reset do timing) — không mở đè lên form Sửa đang có initial khác.
    LaunchedEffect(picker.shouldShowForm) {
        if (picker.shouldShowForm) {
            if (!showForm) showForm = true
            picker.consumeShowFormRequest()
        }
    }

    val filtered = remember(state.files, state.search) { vm.filteredFiles() }

    // FIX: bỏ .background(colors.bg) opaque — cùng lý do VocabularyManagerScreen,
    // che mất BackgroundLayer dùng chung mà AppRoot vẽ ở lớp dưới cùng root.
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = state.search,
                onValueChange = { vm.setSearch(it) },
                placeholder = { Text("Tìm tài liệu...", fontSize = 13.sp) },
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
                onClick = { editingId = null; showForm = true },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.lav)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Thêm file", fontWeight = FontWeight.Black, fontSize = 12.5.sp, maxLines = 1)
            }
        }

        Text(
            "${state.files.size} tài liệu" + if (state.search.isNotBlank()) " · ${filtered.size} khớp tìm kiếm" else "",
            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.text3,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        when {
            state.loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = colors.lav) }
            }
            filtered.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Folder, null, tint = colors.text3.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(if (state.search.isNotBlank()) "Không tìm thấy tài liệu" else "Chưa có tài liệu nào", fontWeight = FontWeight.Black, fontSize = 14.sp, color = colors.text2)
                        Text(if (state.search.isNotBlank()) "Thử từ khoá khác nhé" else "Bấm \"Thêm file\" để tải tài liệu đầu tiên lên", fontSize = 12.sp, color = colors.text3)
                    }
                }
            }
            else -> {
                LazyColumn(
                    state = vm.listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filtered, key = { it.id }) { f ->
                        FileRow(
                            f = f, colors = colors, deleting = state.deletingId == f.id,
                            onEdit = { editingId = f.id; showForm = true },
                            onDelete = { deleteTarget = f }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showForm) {
        FileFormModal(
            colors = colors, dark = dark, initial = editing, uploading = state.uploading,
            pickedUri = pickedUri, pickedName = pickedName, pickedSize = pickedSize, pickErr = pickErr,
            onPickFile = { picker.pick() },
            onClose = {
                showForm = false
                picker.clear()
            },
            onSaveMetaOnly = { title, desc, subject ->
                vm.updateMetadata(editing!!.id, title, desc, subject) { ok, msg ->
                    ToastCenter.show(msg, if (ok) "✅" else "❌", if (ok) Color(0xFF10B981) else Color(0xFFEF4444))
                    if (ok) { showForm = false; picker.clear() }
                }
            },
            onSaveWithFile = { title, desc, subject, filename, sizeBytes, bytes, contentType ->
                if (editing != null) {
                    vm.replaceFile(editing!!, title, desc, subject, filename, sizeBytes, bytes, contentType) { ok, msg ->
                        ToastCenter.show(msg, if (ok) "✅" else "❌", if (ok) Color(0xFF10B981) else Color(0xFFEF4444))
                        if (ok) { showForm = false; picker.clear() }
                    }
                } else {
                    vm.uploadNew(title, desc, subject, filename, sizeBytes, bytes, contentType) { ok, msg ->
                        ToastCenter.show(msg, if (ok) "✅" else "❌", if (ok) Color(0xFF10B981) else Color(0xFFEF4444))
                        if (ok) { showForm = false; picker.clear() }
                    }
                }
            }
        )
    }

    deleteTarget?.let { f ->
        com.learnsypro.app.admin.ui.components.ConfirmDialog(
            title = "Xoá tài liệu?",
            message = "\"${f.title}\" sẽ bị xoá vĩnh viễn khỏi hệ thống.",
            onDismiss = { deleteTarget = null },
            onConfirm = {
                vm.deleteFile(f) { ok, msg -> ToastCenter.show(msg, if (ok) "✅" else "❌", if (ok) Color(0xFF10B981) else Color(0xFFEF4444)) }
            },
            colors = colors, dark = dark,
            confirmLabel = "Xoá", iconType = com.learnsypro.app.admin.ui.components.ConfirmIconType.DELETE
        )
    }
}

@Composable
private fun FileRow(f: LearningFile, colors: LearnsyColors, deleting: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val ext = getFileExt(f.filename)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.5.dp, colors.border, RoundedCornerShape(16.dp))
            .clickable {
                runCatching {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(f.path))
                    context.startActivity(intent)
                }
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(fileExtColor(ext).copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.InsertDriveFile, null, tint = fileExtColor(ext), modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(f.title, fontWeight = FontWeight.Black, fontSize = 13.5.sp, color = colors.text, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (f.subject.isNotBlank()) Text(f.subject, fontSize = 11.sp, color = colors.lav, fontWeight = FontWeight.Bold)
                Text(fmtFileBytes(f.size), fontSize = 11.sp, color = colors.text3)
            }
        }
        if (deleting) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.lav)
        } else {
            IconActionButton(Icons.Default.Edit, "Sửa", onEdit, size = IconBtnSize.Medium, tint = colors.lav, background = colors.bg2, borderColor = colors.border2)
            Spacer(Modifier.width(4.dp))
            IconActionButton(Icons.Default.Delete, "Xoá", onDelete, size = IconBtnSize.Medium, tint = Color(0xFFEF4444), background = Color(0x14EF4444), borderColor = Color(0x59EF4444))
        }
    }
}

@Composable
private fun FileFormModal(
    colors: LearnsyColors,
    dark: Boolean,
    initial: LearningFile?,
    uploading: Boolean,
    pickedUri: Uri?,
    pickedName: String?,
    pickedSize: Long,
    pickErr: String,
    onPickFile: () -> Unit,
    onClose: () -> Unit,
    onSaveMetaOnly: (String, String, String) -> Unit,
    onSaveWithFile: (String, String, String, String, Long, ByteArray, String?) -> Unit
) {
    val isEdit = initial != null
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }
    var description by rememberSaveable { mutableStateOf(initial?.description ?: "") }
    var subject by rememberSaveable { mutableStateOf(initial?.subject ?: "") }
    // FIX (thật sự): launcher + pickedUri/pickedName/pickedSize KHÔNG còn sống ở
    // đây nữa — đã dời hẳn lên FileManagerScreen (xem comment ở đó). Trước đây
    // đặt trong Dialog này tưởng rememberSaveable là đủ, nhưng Dialog có Window/
    // SavedStateRegistry riêng nên đăng ký lại launcher trễ hơn thời điểm Android
    // redeliver kết quả sau process death -> mất kết quả dù đã chọn ảnh thật.
    // Modal giờ chỉ NHẬN pickedUri/pickedName/pickedSize/pickErr qua tham số và
    // gọi onPickFile() khi cần chọn — không tự giữ state hay launcher nào nữa.
    var err by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(pickErr) { if (pickErr.isNotBlank()) err = pickErr }
    LaunchedEffect(pickedName) {
        if (pickedName != null && title.isBlank()) title = pickedName.substringBeforeLast('.', pickedName)
    }

    Dialog(onDismissRequest = { if (!uploading) onClose() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(if (dark) Color(0xFF1E0D15) else Color.White)
                .border(1.5.dp, colors.border2, RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (isEdit) "Sửa tài liệu" else "Thêm tài liệu mới", fontWeight = FontWeight.Black, fontSize = 15.5.sp, color = colors.text)
                IconActionButton(Icons.Default.Close, "Đóng", onClose, size = IconBtnSize.Small, tint = colors.text3, background = colors.bg2, borderColor = colors.border2, enabled = !uploading)
            }
            Spacer(Modifier.height(14.dp))

            // Dropzone / picker
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, colors.border2, RoundedCornerShape(16.dp))
                    .background(colors.bg2)
                    .clickable { onPickFile() }
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    pickedName != null -> {
                        Icon(Icons.Filled.InsertDriveFile, null, tint = fileExtColor(getFileExt(pickedName!!)), modifier = Modifier.size(26.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(pickedName!!, fontWeight = FontWeight.Black, fontSize = 13.sp, color = colors.text, maxLines = 1)
                        Text(fmtFileBytes(pickedSize), fontSize = 11.sp, color = colors.text3)
                    }
                    isEdit -> {
                        Icon(Icons.Filled.InsertDriveFile, null, tint = fileExtColor(getFileExt(initial!!.filename)), modifier = Modifier.size(26.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(initial.filename, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = colors.text2)
                        Text("Bấm để thay file khác (không bắt buộc)", fontSize = 11.sp, color = colors.text3)
                    }
                    else -> {
                        Icon(Icons.Default.CloudUpload, null, tint = colors.text3, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.height(6.dp))
                        Text("Bấm để chọn file", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = colors.text2)
                        Text("Tối đa ${LEARNING_FILE_MAX_MB}MB", fontSize = 11.sp, color = colors.text3)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            LabeledFieldF("Tên tài liệu *", title, { title = it }, colors, placeholder = "Vd: Đề cương Unit 5")
            Spacer(Modifier.height(10.dp))
            LabeledFieldF("Mô tả", description, { description = it }, colors, minLines = 2, placeholder = "Vd: Ôn tập từ vựng và ngữ pháp Unit 5")
            Spacer(Modifier.height(10.dp))
            LabeledFieldF("Môn học (tuỳ chọn)", subject, { subject = it }, colors, placeholder = "Vd: Tiếng Anh")

            if (err.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(err, fontSize = 12.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onClose, enabled = !uploading, shape = RoundedCornerShape(999.dp), modifier = Modifier.weight(1f)) {
                    Text("Huỷ", fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
                Button(
                    onClick = {
                        if (title.isBlank()) { err = "Nhập tên tài liệu nhé!"; return@Button }
                        if (!isEdit && pickedUri == null) { err = "Chọn file để tải lên!"; return@Button }
                        val uri = pickedUri
                        if (uri != null) {
                            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            if (bytes == null) { err = "Không đọc được file, thử lại nhé!"; return@Button }
                            val contentType = context.contentResolver.getType(uri)
                            onSaveWithFile(title, description, subject, pickedName ?: "file", pickedSize, bytes, contentType)
                        } else {
                            onSaveMetaOnly(title, description, subject)
                        }
                    },
                    enabled = !uploading,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.lav),
                    modifier = Modifier.weight(2f)
                ) {
                    if (uploading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Text(if (isEdit) "Lưu thay đổi" else "Tải lên", fontWeight = FontWeight.Black, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledFieldF(label: String, value: String, onChange: (String) -> Unit, colors: LearnsyColors, minLines: Int = 1, placeholder: String = "") {
    Column {
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Black, color = colors.text3)
        Spacer(Modifier.height(5.dp))
        OutlinedTextField(
            value = value, onValueChange = onChange,
            placeholder = { Text(placeholder, fontSize = 13.sp) },
            minLines = minLines,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.lav, unfocusedBorderColor = colors.border2),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

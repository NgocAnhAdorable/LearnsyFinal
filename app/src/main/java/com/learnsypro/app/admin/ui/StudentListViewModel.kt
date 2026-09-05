package com.learnsypro.app.admin.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnsypro.app.admin.data.Student
import com.learnsypro.app.admin.data.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.Locale

enum class StudentSortBy { NEWEST, NAME, CLASS }
enum class StatusFilter { ALL, ACTIVE, LOCKED }

data class ActivityLogEntry(val ts: Long, val type: String, val msg: String)

data class StudentListUiState(
    val students: List<Student> = emptyList(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val search: String = "",
    val filterClass: String = "",
    val filterStatus: StatusFilter = StatusFilter.ALL,
    val sortBy: StudentSortBy = StudentSortBy.NEWEST,
    val bulkMode: Boolean = false,
    val bulkSelected: Set<String> = emptySet(),
    val srvStatus: String = "checking",
    val pingMs: Int? = null,
    val activityLog: List<ActivityLogEntry> = emptyList(),
    val lastPingAt: Long? = null,
    // Tương đương plainPassView object trong student-manager.jsx — mật khẩu
    // vừa tạo/reset, hiện inline ngay dưới tên trên card (reveal-once),
    // không phải modal riêng.
    val plainPassView: Map<String, String> = emptyMap()
)

// Tương đương phần state chính của StudentManager (student-manager.jsx),
// trừ đồng hồ UTC+7 (đặt riêng trong Composable bằng LaunchedEffect vì đó
// là giá trị hiển thị thuần tuý, không cần lưu trong ViewModel).
class StudentListViewModel(
    private val repo: StudentRepository = StudentRepository(),
    private val savedStateHandle: SavedStateHandle? = null
) : ViewModel() {

    // FIX "app tự refresh khi vào lại" — cùng cách với LessonListViewModel:
    // cache students xuống SavedStateHandle. Chỉ cache đúng field `students`
    // (không cache plainPassView/activityLog — dữ liệu tạm thời của phiên
    // hiện tại, không cần và không nên sống qua recreate).
    private val cachedStudents: List<Student> = savedStateHandle
        ?.get<String>(KEY_STUDENTS_CACHE)
        ?.let { runCatching { Json.decodeFromString(ListSerializer(Student.serializer()), it) }.getOrNull() }
        ?: emptyList()

    private val _uiState = MutableStateFlow(
        StudentListUiState(students = cachedStudents, loading = cachedStudents.isEmpty())
    )
    val uiState: StateFlow<StudentListUiState> = _uiState.asStateFlow()

    // Vị trí cuộn danh sách học sinh — hoist ở ViewModel để không mất khi
    // AnimatedContent dispose Composable của tab lúc chuyển tab qua lại.
    // FIX (cùng lý do LessonListViewModel): khôi phục từ SavedStateHandle để
    // vị trí cuộn sống sót qua process death, không chỉ qua chuyển tab.
    val listState = savedStateHandle.restoredLazyListState(KEY_SCROLL)

    init {
        _uiState.onEach { state ->
            savedStateHandle?.set(
                KEY_STUDENTS_CACHE,
                Json.encodeToString(ListSerializer(Student.serializer()), state.students)
            )
        }.launchIn(viewModelScope)
        if (savedStateHandle != null) {
            androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .onEach { savedStateHandle.saveLazyListState(KEY_SCROLL, listState) }
                .launchIn(viewModelScope)
        }
    }

    companion object {
        private const val KEY_STUDENTS_CACHE = "student_list_cache_json"
        private const val KEY_SCROLL = "student_list_scroll"
    }

    private fun logActivity(type: String, msg: String) {
        _uiState.update {
            it.copy(activityLog = (listOf(ActivityLogEntry(System.currentTimeMillis(), type, msg)) + it.activityLog).take(12))
        }
    }

    // FIX: xem comment chi tiết ở ListeningListViewModel.load() — cùng bug
    // race condition với token Supabase chưa kịp sẵn sàng ngay sau khi app
    // bị kill tiến trình rồi mở lại. Thử lại 1 lần nếu lần đầu về rỗng.
    private var hasLoadedOnce = false
    private var lastRefreshKey: Any? = null

    // Chỉ gọi fetch danh sách khi thực sự cần: chưa từng load lần nào, hoặc
    // refreshKey đổi giá trị thật (bấm nút refresh) — tránh gọi lại mỗi khi
    // Composable mount lại do đổi tab qua lại (refreshKey y hệt lần trước).
    // ping() (kiểm tra server) vẫn gọi riêng mỗi lần vào tab vì nhẹ và không
    // đụng tới danh sách đang hiển thị.
    fun loadIfNeeded(refreshKey: Any) {
        val keyChanged = refreshKey != lastRefreshKey
        lastRefreshKey = refreshKey
        if (!hasLoadedOnce || keyChanged) {
            load()
        }
    }

    fun load() {
        val hasCache = _uiState.value.students.isNotEmpty()
        _uiState.update { it.copy(loading = !hasCache) }
        viewModelScope.launch {
            val t0 = System.currentTimeMillis()
            try {
                var students = repo.fetchAll()
                if (students.isEmpty() && !hasLoadedOnce) {
                    kotlinx.coroutines.delay(600)
                    students = repo.fetchAll()
                }
                _uiState.update { it.copy(students = students, loading = false) }
                logActivity("get", "GET students — ${students.size} bản ghi (${System.currentTimeMillis() - t0}ms)")
                hasLoadedOnce = true
            } catch (e: Exception) {
                // Có cache rồi thì fetch nền lỗi không nên xoá list đang hiện.
                _uiState.update { it.copy(loading = false) }
                logActivity("error", "GET students lỗi: ${e.message}")
                hasLoadedOnce = true
            }
        }
    }

    fun ping() {
        viewModelScope.launch {
            val t0 = System.currentTimeMillis()
            try {
                repo.fetchAll()
                val ms = (System.currentTimeMillis() - t0).toInt()
                _uiState.update { it.copy(srvStatus = "online", pingMs = ms, lastPingAt = System.currentTimeMillis()) }
                logActivity("ping", "Ping server — ${ms}ms")
            } catch (e: Exception) {
                _uiState.update { it.copy(srvStatus = "offline", pingMs = null, lastPingAt = System.currentTimeMillis()) }
                logActivity("error", "Ping thất bại: ${e.message}")
            }
        }
    }

    fun setSearch(q: String) = _uiState.update { it.copy(search = q) }
    fun setFilterClass(c: String) = _uiState.update { it.copy(filterClass = c) }
    fun setFilterStatus(s: StatusFilter) = _uiState.update { it.copy(filterStatus = s) }
    fun setSortBy(s: StudentSortBy) = _uiState.update { it.copy(sortBy = s) }

    fun toggleBulkMode() = _uiState.update { it.copy(bulkMode = !it.bulkMode, bulkSelected = emptySet()) }
    fun toggleBulkSelect(id: String) = _uiState.update {
        val s = it.bulkSelected.toMutableSet()
        if (id in s) s.remove(id) else s.add(id)
        it.copy(bulkSelected = s)
    }

    fun classes(): List<String> = _uiState.value.students.mapNotNull { it.className.ifBlank { null } }.distinct().sorted()

    fun filteredStudents(): List<Student> {
        val s = _uiState.value
        var list = s.students.filter { st ->
            val q = s.search.lowercase()
            val matchQ = q.isBlank() || st.username.lowercase().contains(q) ||
                (st.displayName ?: "").lowercase().contains(q) || st.className.lowercase().contains(q)
            val matchC = s.filterClass.isBlank() || st.className == s.filterClass
            val matchS = when (s.filterStatus) {
                StatusFilter.ALL -> true
                StatusFilter.ACTIVE -> st.isActive
                StatusFilter.LOCKED -> !st.isActive
            }
            matchQ && matchC && matchS
        }
        list = when (s.sortBy) {
            StudentSortBy.NAME -> list.sortedWith(compareBy(java.text.Collator.getInstance(Locale("vi"))) { it.displayName ?: it.username })
            StudentSortBy.CLASS -> list.sortedWith(compareBy(java.text.Collator.getInstance(Locale("vi"))) { it.className })
            StudentSortBy.NEWEST -> list
        }
        return list
    }

    fun addStudent(
        username: String, displayName: String, className: String, password: String,
        onSuccess: (Student, plainPassword: String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (username.isBlank()) { onError("Nhập username!"); return }
        if (_uiState.value.saving) return
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            var insertedId: String? = null
            try {
                val student = repo.createPending(username, displayName, className)
                insertedId = student.id
                logActivity("insert", "INSERT students — @${student.username}")

                val plain = withTimeoutOrNull(12000) {
                    repo.setPassword(student.id, password.trim().ifBlank { null })
                } ?: run {
                    repo.rollbackDelete(student.id)
                    logActivity("error", "Timeout Edge Function (12s), đã rollback")
                    onError("Server không phản hồi, thử lại nhé!")
                    _uiState.update { it.copy(saving = false) }
                    return@launch
                }

                logActivity("fn", "Edge Function student-set-password — OK")
                val finalPlain = plain ?: password
                _uiState.update {
                    it.copy(
                        students = listOf(student) + it.students,
                        saving = false,
                        plainPassView = it.plainPassView + (student.id to finalPlain)
                    )
                }
                onSuccess(student, finalPlain)
            } catch (e: Exception) {
                insertedId?.let { runCatching { repo.rollbackDelete(it) } }
                _uiState.update { it.copy(saving = false) }
                logActivity("error", "Lỗi mạng khi tạo tài khoản: ${e.message}")
                onError("Lỗi kết nối! Kiểm tra mạng và thử lại.")
            }
        }
    }

    fun editStudent(id: String, displayName: String, className: String, onDone: (Boolean, String) -> Unit) {
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                repo.update(id, displayName, className)
                _uiState.update { st ->
                    st.copy(
                        students = st.students.map { if (it.id == id) it.copy(displayName = displayName, className = className) else it },
                        saving = false
                    )
                }
                logActivity("update", "UPDATE students — id ${id.take(8)}…")
                onDone(true, "Đã lưu thay đổi!")
            } catch (e: Exception) {
                _uiState.update { it.copy(saving = false) }
                onDone(false, "Lỗi: ${e.message}")
            }
        }
    }

    fun updateInlineField(id: String, field: String, value: String) {
        if (value.isBlank()) return
        viewModelScope.launch {
            try {
                repo.updateField(id, field, value)
                _uiState.update { st ->
                    st.copy(students = st.students.map {
                        if (it.id != id) it
                        else if (field == "display_name") it.copy(displayName = value) else it.copy(className = value)
                    })
                }
            } catch (e: Exception) { }
        }
    }

    fun resetPassword(student: Student, newPassword: String?, onDone: (Boolean, String, plain: String?) -> Unit) {
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val plain = repo.setPassword(student.id, newPassword?.ifBlank { null })
                val finalPlain = newPassword?.ifBlank { null } ?: plain
                _uiState.update {
                    it.copy(
                        saving = false,
                        plainPassView = if (finalPlain != null) it.plainPassView + (student.id to finalPlain) else it.plainPassView
                    )
                }
                onDone(true, "Đã đổi mật khẩu!", finalPlain)
            } catch (e: Exception) {
                _uiState.update { it.copy(saving = false) }
                logActivity("error", "resetPassword lỗi: ${e::class.simpleName}: ${e.message}")
                onDone(false, "Lỗi: ${e.message ?: e::class.simpleName ?: "Không rõ nguyên nhân"}", null)
            }
        }
    }

    // Tương đương setPlainPassView(p => { delete p[id] }) — ẩn chip mật khẩu trên card
    fun dismissPlainPass(id: String) {
        _uiState.update { it.copy(plainPassView = it.plainPassView - id) }
    }

    fun toggleActive(student: Student) {
        val next = !student.isActive
        viewModelScope.launch {
            try {
                repo.toggleActive(student.id, next)
                _uiState.update { st -> st.copy(students = st.students.map { if (it.id == student.id) it.copy(isActive = next) else it }) }
            } catch (e: Exception) { }
        }
    }

    fun deleteStudent(id: String, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                repo.delete(id)
                _uiState.update { it.copy(students = it.students.filter { s -> s.id != id }) }
                logActivity("delete", "DELETE students — id ${id.take(8)}…")
                onDone(true, "Đã xoá tài khoản!")
            } catch (e: Exception) {
                logActivity("error", "DELETE students lỗi: ${e.message}")
                onDone(false, "Lỗi xóa tài khoản!")
            }
        }
    }

    fun bulkDelete(onDone: (Int, Int) -> Unit) {
        val ids = _uiState.value.bulkSelected.toList()
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = repo.bulkDelete(ids)
            _uiState.update {
                it.copy(
                    students = it.students.filter { s -> s.id !in ids },
                    bulkSelected = emptySet(), bulkMode = false, saving = false
                )
            }
            logActivity(if (result.second > 0) "error" else "delete", "Bulk DELETE — ${result.first} OK${if (result.second > 0) ", ${result.second} lỗi" else ""}")
            onDone(result.first, result.second)
        }
    }

    fun bulkToggleActive(active: Boolean, onDone: (Boolean) -> Unit) {
        val ids = _uiState.value.bulkSelected.toList()
        viewModelScope.launch {
            try {
                repo.bulkToggleActive(ids, active)
                _uiState.update {
                    it.copy(
                        students = it.students.map { s -> if (s.id in ids) s.copy(isActive = active) else s },
                        bulkSelected = emptySet(), bulkMode = false
                    )
                }
                onDone(true)
            } catch (e: Exception) {
                onDone(false)
            }
        }
    }
}

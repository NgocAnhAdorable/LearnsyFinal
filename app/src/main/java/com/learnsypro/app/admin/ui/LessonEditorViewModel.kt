package com.learnsypro.app.admin.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnsypro.app.admin.data.Lesson
import com.learnsypro.app.admin.data.AdminLessonRepository
import com.learnsypro.app.admin.data.Question
import com.learnsypro.app.admin.data.emptyTF
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class SaveStatus { IDLE, PENDING, SAVING, SAVED, ERROR, DUP_BLOCKED }

@Serializable
data class LessonEditorUiState(
    val lessonId: String? = null,
    val title: String = "",
    val subject: String = "Tiếng Anh",
    val password: String = "",
    val timerLimit: Int = 0,
    val questions: List<Question> = listOf(emptyTF()),
    // FIX: thiếu field này khiến manualSave() build lại Lesson(...) với
    // createdAt mặc định "" (default của Lesson.createdAt), upsert gửi
    // created_at = "" lên Postgres -> Postgres từ chối vì cột là
    // timestamptz ("invalid input syntax for type timestamp with time
    // zone: ''") -> MỌI lần lưu một lesson đã tồn tại đều fail âm thầm,
    // kể cả chỉ gõ tên rồi bấm back. Giờ giữ nguyên giá trị gốc từ
    // loadLesson() và truyền lại nguyên vẹn khi save.
    val createdAt: String = "",
    val titleDupWarn: Boolean = false,
    val saveStatus: SaveStatus = SaveStatus.IDLE,
    val lastError: String? = null,
    // Tương đương lastSaveDuration/lastSavedAt hiện trong panel "Trạng thái
    // lưu bài" của app.jsx — đo thời gian request lưu mất bao lâu, và mốc
    // giờ:phút:giây của lần lưu thành công gần nhất.
    val lastSaveDurationMs: Long? = null,
    val lastSavedAt: String? = null,
    // Cho route Editor trong NavHost: true khi đang fetch lesson theo id
    // (chưa có sẵn object đầy đủ như kiến trúc cũ), và notFound khi id đó
    // không tồn tại trên Supabase (ví dụ đã bị xoá từ thiết bị/tab khác).
    val isLoading: Boolean = false,
    val notFound: Boolean = false
)

// Tương đương toàn bộ khối state + auto-save effect trong app.jsx (dòng ~272-540).
// Khác biệt chủ đích so với bản JS:
// - Không cần _isLoadingLesson/_isSaving ref-guard phức tạp: ViewModel chỉ có
//   1 lesson đang mở tại 1 thời điểm, load() luôn chạy xong trước khi user
//   gõ được gì (UI hiện loading), nên không có race condition kiểu React double-render.
// - Không cần retry loadRetryTick: load lesson bằng suspend fun trực tiếp từ Supabase,
//   không phụ thuộc lessonsRef đồng bộ từ list màn hình khác.
class LessonEditorViewModel(
    private val repo: AdminLessonRepository = AdminLessonRepository(),
    private val savedStateHandle: SavedStateHandle? = null
) : ViewModel() {

    // FIX: trước đây _uiState chỉ là MutableStateFlow(LessonEditorUiState())
    // thuần trong RAM — không có SavedStateHandle. Auto-save đang chạy debounce
    // 800ms (scheduleAutoSave), nên nếu hệ thống kill hẳn tiến trình đúng lúc
    // đang gõ (trong khung 800ms đó, hoặc lúc mạng đang lỗi khiến autosave
    // request trước đó fail), toàn bộ câu hỏi/tiêu đề vừa soạn dở mất sạch —
    // quay lại app thấy đúng bài đang mở (vì editingLessonId đã rememberSaveable)
    // nhưng nội dung bên trong rơi về đúng bản đã lưu server gần nhất, y hệt
    // bug đã từng vá ở ListeningFormViewModel. Áp cùng cách: tự ghi JSON xuống
    // Bundle qua SavedStateHandle mỗi khi state đổi, khôi phục lại lúc khởi tạo.
    private val _uiState = MutableStateFlow(
        savedStateHandle?.get<String>(KEY_STATE)?.let {
            runCatching { Json.decodeFromString<LessonEditorUiState>(it) }.getOrNull()
                // saveStatus/isLoading là cờ tạm thời của 1 lượt load/save đang
                // chạy dở — nếu process bị kill giữa chừng, request đó đã chết
                // theo rồi, không được khôi phục về SAVING/isLoading=true (sẽ
                // kẹt UI ở trạng thái "đang lưu..."/"đang tải..." mãi mãi dù
                // không có request nào thực sự đang chạy).
                ?.copy(saveStatus = SaveStatus.IDLE, isLoading = false)
        } ?: LessonEditorUiState()
    )
    val uiState: StateFlow<LessonEditorUiState> = _uiState.asStateFlow()

    init {
        _uiState.onEach { state ->
            savedStateHandle?.set(KEY_STATE, Json.encodeToString(LessonEditorUiState.serializer(), state))
        }.launchIn(viewModelScope)
    }

    companion object {
        private const val KEY_STATE = "lesson_editor_state_json"
    }

    private var autoSaveJob: Job? = null

    fun loadLesson(lesson: Lesson) {
        autoSaveJob?.cancel()
        _uiState.value = LessonEditorUiState(
            lessonId = lesson.id,
            title = lesson.title,
            subject = lesson.subject,
            password = lesson.password,
            timerLimit = lesson.timerLimit,
            questions = lesson.questions.ifEmpty { listOf(emptyTF()) },
            createdAt = lesson.createdAt
        )
    }

    // Tương đương loadLesson() nhưng tự fetch từ Supabase theo id — dùng
    // trong route Editor của NavHost, nơi chỉ có lessonId (String, nav
    // argument) chứ không có sẵn Lesson object đầy đủ từ màn cha như kiến
    // trúc điều hướng tự chế trước đây. Bỏ qua nếu đã đang load đúng id này
    // (tránh load lại thừa khi Composable recompose nhiều lần).
    fun loadLessonById(id: String) {
        if (_uiState.value.lessonId == id && !_uiState.value.isLoading) return
        autoSaveJob?.cancel()
        _uiState.value = LessonEditorUiState(isLoading = true)
        viewModelScope.launch {
            try {
                val lesson = repo.fetchOne(id)
                if (lesson == null) {
                    _uiState.value = LessonEditorUiState(isLoading = false, notFound = true)
                } else {
                    _uiState.value = LessonEditorUiState(
                        lessonId = lesson.id,
                        title = lesson.title,
                        subject = lesson.subject,
                        password = lesson.password,
                        timerLimit = lesson.timerLimit,
                        questions = lesson.questions.ifEmpty { listOf(emptyTF()) },
                        createdAt = lesson.createdAt
                    )
                }
            } catch (e: Exception) {
                _uiState.value = LessonEditorUiState(isLoading = false, notFound = true, lastError = e.message)
            }
        }
    }

    fun newLesson() {
        autoSaveJob?.cancel()
        _uiState.value = LessonEditorUiState()
    }

    fun setTitle(title: String) {
        _uiState.update { it.copy(title = title) }
        checkDup()
        scheduleAutoSave()
    }

    fun setSubject(subject: String) {
        _uiState.update { it.copy(subject = subject) }
        scheduleAutoSave()
    }

    fun setPassword(password: String) {
        _uiState.update { it.copy(password = password) }
        scheduleAutoSave()
    }

    fun setTimerLimit(minutes: Int) {
        _uiState.update { it.copy(timerLimit = minutes) }
        scheduleAutoSave()
    }

    fun setQuestions(questions: List<Question>) {
        _uiState.update { it.copy(questions = questions) }
        scheduleAutoSave()
    }

    fun addQuestion(q: Question) {
        _uiState.update { it.copy(questions = it.questions + q) }
        scheduleAutoSave()
    }

    // Tương đương manualSave() trong app.jsx — lưu ngay lập tức, bỏ qua debounce 800ms.
    // Dùng khi người dùng muốn chắc chắn bài đã lưu trước khi thoát (VD: chuẩn bị tắt app).
    //
    // SUSPEND thay vì fire-and-forget: trước đây hàm này launch một job nền rồi
    // return ngay, khiến người gọi (ví dụ handleBack() điều hướng thoát màn hình)
    // không có cách nào biết khi nào lưu xong. Nếu người dùng bấm lưu rồi thoát
    // ngay lập tức, listVm.load() ở màn danh sách có thể fetch lại TRƯỚC KHI
    // upsert lên Supabase kịp hoàn tất — dẫn tới thấy dữ liệu cũ (bug đã gặp:
    // gõ tên → lưu → thoát → vào lại vẫn "Chưa đặt tên"). Giờ gọi trực tiếp
    // trong viewModelScope và await xong mới return, để người gọi có thể
    // suspend chờ trước khi điều hướng.
    // FIX: trước đây hàm này không có giá trị trả về, nên handleBack() ở màn
    // editor gọi manualSave() rồi LUÔN điều hướng về danh sách ngay sau đó —
    // kể cả khi upsert lên Supabase thất bại (mất mạng, RLS chặn, timeout...).
    // Exception đã bị try/catch nuốt vào saveStatus/lastError của editor,
    // nhưng người gọi (handleBack) không đọc lại state đó, nên người dùng
    // thấy "thoát thành công" trong khi tên vừa gõ chưa hề được lưu — quay
    // lại danh sách rồi mở lại bài thì title vẫn rỗng ("Chưa đặt tên"), và
    // Dashboard/danh sách có thể báo lỗi tải ở lần fetch kế tiếp do cùng sự
    // cố mạng đó. Giờ trả về true/false để handleBack() chỉ điều hướng khi
    // save thật sự thành công, và hiện toast lỗi thật nếu không.
    suspend fun manualSave(): Boolean {
        val lessonId = _uiState.value.lessonId ?: return true
        autoSaveJob?.cancel()
        val s = _uiState.value
        if (repo.isDuplicateTitleRemote(s.title, s.lessonId)) {
            _uiState.update { it.copy(saveStatus = SaveStatus.DUP_BLOCKED, lastError = "Tên bài tập bị trùng") }
            return false
        }
        _uiState.update { it.copy(saveStatus = SaveStatus.SAVING) }
        val startMs = System.currentTimeMillis()
        return try {
            repo.save(
                Lesson(
                    id = lessonId,
                    title = s.title,
                    subject = s.subject,
                    password = s.password,
                    timerLimit = s.timerLimit,
                    questions = s.questions,
                    createdAt = s.createdAt.ifBlank { java.time.Instant.now().toString() }
                )
            )
            val durationMs = System.currentTimeMillis() - startMs
            val savedAt = java.time.LocalTime.now().withNano(0).toString()
            _uiState.update {
                it.copy(
                    saveStatus = SaveStatus.SAVED, lastError = null,
                    lastSaveDurationMs = durationMs, lastSavedAt = savedAt
                )
            }
            true
        } catch (e: Exception) {
            _uiState.update { it.copy(saveStatus = SaveStatus.ERROR, lastError = e.message) }
            false
        }
    }

    fun removeQuestion(id: String) {
        _uiState.update { it.copy(questions = it.questions.filter { q -> q.id != id }) }
        scheduleAutoSave()
    }

    // Tương đương check trùng tên real-time khi gõ title (effect titleDupWarn).
    // Trước đây dùng allLessonsProvider() (snapshot từ màn Danh sách truyền
    // vào) — với NavHost, Editor là route độc lập, không còn quyền truy cập
    // trực tiếp state của route khác nữa, nên đổi hẳn sang query Supabase
    // thật (đã có sẵn isDuplicateTitleRemote dùng cho lúc lưu thật).
    private var dupCheckJob: Job? = null
    private fun checkDup() {
        dupCheckJob?.cancel()
        dupCheckJob = viewModelScope.launch {
            delay(400) // tránh query Supabase dồn dập mỗi lần gõ phím
            val s = _uiState.value
            val dup = repo.isDuplicateTitleRemote(s.title, s.lessonId)
            _uiState.update { it.copy(titleDupWarn = dup) }
        }
    }

    // Tương đương debounce 800ms trước khi upsert lên Supabase
    private fun scheduleAutoSave() {
        val lessonId = _uiState.value.lessonId ?: return
        autoSaveJob?.cancel()
        _uiState.update { it.copy(saveStatus = SaveStatus.PENDING) }
        autoSaveJob = viewModelScope.launch {
            delay(800)

            val s = _uiState.value
            // Chặn lưu nếu tên trùng — không upsert lên Supabase. Query trực
            // tiếp Supabase (không dùng snapshot allLessonsProvider) để tránh
            // chặn oan khi dữ liệu đã đổi từ thiết bị/tab khác trong lúc đang soạn.
            if (repo.isDuplicateTitleRemote(s.title, s.lessonId)) {
                _uiState.update {
                    it.copy(saveStatus = SaveStatus.DUP_BLOCKED, lastError = "Tên bài tập bị trùng")
                }
                return@launch
            }

            _uiState.update { it.copy(saveStatus = SaveStatus.SAVING) }
            val startMs = System.currentTimeMillis()
            try {
                repo.save(
                    Lesson(
                        id = lessonId,
                        title = s.title,
                        subject = s.subject,
                        password = s.password,
                        timerLimit = s.timerLimit,
                        questions = s.questions,
                        createdAt = s.createdAt.ifBlank { java.time.Instant.now().toString() }
                    )
                )
                val durationMs = System.currentTimeMillis() - startMs
                val savedAt = java.time.LocalTime.now().withNano(0).toString()
                _uiState.update {
                    it.copy(
                        saveStatus = SaveStatus.SAVED, lastError = null,
                        lastSaveDurationMs = durationMs, lastSavedAt = savedAt
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(saveStatus = SaveStatus.ERROR, lastError = e.message) }
            }
        }
    }

    override fun onCleared() {
        autoSaveJob?.cancel()
    }
}

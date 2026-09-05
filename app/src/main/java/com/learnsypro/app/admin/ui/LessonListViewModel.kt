package com.learnsypro.app.admin.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnsypro.app.admin.data.Lesson
import com.learnsypro.app.admin.data.LessonFilter
import com.learnsypro.app.admin.data.AdminLessonRepository
import com.learnsypro.app.admin.data.SortBy
import com.learnsypro.app.admin.data.CardBlur
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

data class LessonListUiState(
    val lessons: List<Lesson> = emptyList(),
    val loading: Boolean = false,
    val filter: LessonFilter = LessonFilter.ALL,
    val sortBy: SortBy = SortBy.NEWEST,
    val searchQuery: String = "",
    // Tương đương cardBlur trong app.jsx — làm mờ nội dung câu hỏi trên card
    // danh sách bài, dùng khi demo trước lớp để học sinh không đọc trộm đề.
    val cardBlur: CardBlur = CardBlur.OFF,
    val error: String? = null
)

// Tương đương phần load lessons + addLesson/deleteLesson/dupLesson trong app.jsx
class LessonListViewModel(
    private val repo: AdminLessonRepository = AdminLessonRepository(),
    private val savedStateHandle: SavedStateHandle? = null
) : ViewModel() {

    // FIX "app tự refresh khi vào lại": trước đây mỗi khi Activity bị hệ
    // thống recreate ở nền (RAM thấp, không phải người dùng chủ động kill),
    // ViewModel này bị tạo mới -> uiState.lessons về rỗng -> load() luôn set
    // loading=true -> màn hình chớp qua spinner + list rỗng rồi mới có dữ
    // liệu lại, nhìn giống app "tự refresh" dù dữ liệu thực ra không đổi.
    // Giờ cache list xuống SavedStateHandle (giống ListeningFormViewModel),
    // khôi phục ngay lúc khởi tạo -> màn hình có dữ liệu ngay, không rỗng/
    // không spinner. load() chỉ hiện spinner khi thực sự chưa có gì trong
    // tay; nếu đã có cache thì fetch mới âm thầm ở nền rồi thay vào.
    private val cachedLessons: List<Lesson> = savedStateHandle
        ?.get<String>(KEY_LESSONS_CACHE)
        ?.let { runCatching { Json.decodeFromString(ListSerializer(Lesson.serializer()), it) }.getOrNull() }
        ?: emptyList()

    private val _uiState = MutableStateFlow(LessonListUiState(lessons = cachedLessons))
    // Vị trí cuộn danh sách bài học — hoist ở ViewModel (không bị huỷ khi
    // AnimatedContent dispose Composable của tab lúc chuyển sang tab khác)
    // để giữ đúng vị trí khi quay lại tab Bài học.
    // FIX: trước đây khởi tạo LazyListState() trơn -> index/offset không nằm
    // trong SavedStateHandle -> app bị hệ thống kill tiến trình nền rồi mở
    // lại thì list khôi phục đúng (nhờ cachedLessons ở trên) nhưng vị trí
    // cuộn luôn về đầu. Giờ khôi phục từ SavedStateHandle ngay lúc khởi tạo.
    val listState = savedStateHandle.restoredLazyListState(KEY_SCROLL)
    val uiState: StateFlow<LessonListUiState> = _uiState.asStateFlow()

    init {
        _uiState.onEach { state ->
            savedStateHandle?.set(
                KEY_LESSONS_CACHE,
                Json.encodeToString(ListSerializer(Lesson.serializer()), state.lessons)
            )
        }.launchIn(viewModelScope)
        // Ghi lại vị trí cuộn mỗi khi người dùng lướt danh sách, để onCleared()
        // xảy ra do process bị kill vẫn có giá trị mới nhất đã lưu sẵn.
        if (savedStateHandle != null) {
            androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .onEach { savedStateHandle.saveLazyListState(KEY_SCROLL, listState) }
                .launchIn(viewModelScope)
        }
    }

    companion object {
        private const val KEY_LESSONS_CACHE = "lesson_list_cache_json"
        private const val KEY_SCROLL = "lesson_list_scroll"
    }

    // FIX: xem comment chi tiết ở ListeningListViewModel.load() — cùng bug:
    // sau khi app bị kill tiến trình rồi mở lại, fetchAll() có thể chạy
    // đúng lúc access token Supabase chưa kịp đính kèm request (RLS ẩn hết
    // row, không throw lỗi) -> "0 bài · Chưa có bài tập nào" dù dữ liệu vẫn
    // còn trên server. Thử lại 1 lần sau độ trễ ngắn nếu lần đầu về rỗng.
    private var hasLoadedOnce = false
    private var lastRefreshKey: Any? = null

    // Chỉ gọi fetch mạng khi thực sự cần: chưa từng load lần nào, hoặc
    // refreshKey đổi giá trị thật (bấm nút refresh) — tránh gọi lại mỗi khi
    // Composable mount lại do đổi tab qua lại (refreshKey y hệt lần trước).
    fun loadIfNeeded(refreshKey: Any) {
        val keyChanged = refreshKey != lastRefreshKey
        lastRefreshKey = refreshKey
        if (!hasLoadedOnce || keyChanged) {
            load()
        }
    }

    fun load() {
        val hasCache = _uiState.value.lessons.isNotEmpty()
        if (!hasCache) _uiState.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val lessons = repo.fetchAll()
                if (lessons.isEmpty() && !hasLoadedOnce) {
                    kotlinx.coroutines.delay(600)
                    val retry = repo.fetchAll()
                    _uiState.update { it.copy(lessons = retry, loading = false) }
                } else {
                    _uiState.update { it.copy(lessons = lessons, loading = false) }
                }
                hasLoadedOnce = true
            } catch (e: Exception) {
                // Có cache rồi thì fetch nền lỗi (vd. mất mạng thoáng qua) không
                // nên xoá list đang hiện — giữ nguyên lessons cũ, chỉ báo lỗi.
                _uiState.update { it.copy(loading = false, error = e.message) }
                hasLoadedOnce = true
            }
        }

    }

    fun setFilter(f: LessonFilter) = _uiState.update { it.copy(filter = f) }
    fun setSortBy(s: SortBy) = _uiState.update { it.copy(sortBy = s) }
    fun setSearchQuery(q: String) = _uiState.update { it.copy(searchQuery = q) }
    fun setCardBlur(b: CardBlur) = _uiState.update { it.copy(cardBlur = b) }

    // Trả về danh sách đã lọc + sắp xếp — tương đương chain filter().sort() trong JSX
    fun filteredSortedLessons(): List<Lesson> {
        val s = _uiState.value
        return s.lessons
            .filter { l ->
                when (s.filter) {
                    LessonFilter.ALL -> true
                    LessonFilter.ENGLISH -> l.subject == "Tiếng Anh"
                    LessonFilter.OTHER -> l.subject != "Tiếng Anh"
                }
            }
            .filter { l ->
                s.searchQuery.isBlank() ||
                    l.title.contains(s.searchQuery, ignoreCase = true) ||
                    l.subject.contains(s.searchQuery, ignoreCase = true)
            }
            .let { list ->
                when (s.sortBy) {
                    SortBy.NAME -> list.sortedWith(compareBy(java.text.Collator.getInstance(java.util.Locale("vi"))) { it.title })
                    SortBy.COUNT -> list.sortedByDescending { it.questions.size }
                    SortBy.OLDEST -> list.sortedBy { it.id }
                    SortBy.NEWEST -> list.sortedByDescending { it.id }
                }
            }
    }

    // Tương đương addLesson() — trả về id bài mới để caller điều hướng sang màn edit
    fun createLesson(onCreated: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val lesson = repo.create()
                _uiState.update { it.copy(lessons = it.lessons + lesson) }
                onCreated(lesson.id)
            } catch (e: Exception) {
                onError(e.message ?: "Không tạo được bài")
            }
        }
    }

    // FIX: trước đây xoá xong chỉ update state, không callback gì ra ngoài
    // (lỗi thì set vào `error` nhưng không nơi nào đọc field này để hiện
    // gì cả) -> bấm Xoá xong màn hình im lặng, không có DiToast xác nhận,
    // và nếu Supabase lỗi mạng thì người dùng cũng không biết bài chưa
    // thực sự bị xoá. Thêm onSuccess/onError để màn hình tự bắn toast,
    // khớp cách createLesson() đang làm.
    fun deleteLesson(id: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                repo.delete(id)
                _uiState.update { it.copy(lessons = it.lessons.filter { l -> l.id != id }) }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
                onError(e.message ?: "Không xoá được bài")
            }
        }
    }

    fun duplicateLesson(lesson: Lesson, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val existingTitles = _uiState.value.lessons
                    .filter { it.id != lesson.id }
                    .map { it.title.trim().lowercase() }
                    .toSet()
                val uniqueTitle = repo.makeUniqueTitle("${lesson.title} (bản sao)", existingTitles)
                val dup = repo.duplicate(lesson, uniqueTitle)
                _uiState.update { it.copy(lessons = it.lessons + dup) }
                onDone(true)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
                onDone(false)
            }
        }
    }
}

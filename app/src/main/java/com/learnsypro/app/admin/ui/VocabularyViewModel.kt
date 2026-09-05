package com.learnsypro.app.admin.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnsypro.app.admin.data.ParsedWordLine
import com.learnsypro.app.admin.data.VocabCourse
import com.learnsypro.app.admin.data.VocabUnit
import com.learnsypro.app.admin.data.VocabWord
import com.learnsypro.app.admin.data.VocabularyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

data class VocabularyUiState(
    val courses: List<VocabCourse> = emptyList(),
    val loading: Boolean = true,
    val search: String = "",
    // unitId -> words (chỉ tải khi unit được mở, giống accordion trên web)
    val unitsByCourse: Map<String, List<VocabUnit>> = emptyMap(),
    val wordsByUnit: Map<String, List<VocabWord>> = emptyMap(),
    val loadedUnitCourses: Set<String> = emptySet(),
    val loadedWordUnits: Set<String> = emptySet()
)

// Tương đương VocabularyManager (vocabulary-manager.jsx) trên web —
// bài học (course) > Unit > Từ vựng, dùng chung state accordion 2 cấp.
class VocabularyViewModel(
    private val repo: VocabularyRepository = VocabularyRepository(),
    private val savedStateHandle: SavedStateHandle? = null
) : ViewModel() {

    // FIX "app tự refresh khi vào lại" — cùng cách với LessonListViewModel:
    // trước đây VocabularyViewModel không nhận SavedStateHandle nên khi hệ
    // thống kill tiến trình nền rồi mở lại, courses về rỗng, phải chờ fetch
    // mạng lại từ đầu (kèm chớp spinner) thay vì có dữ liệu ngay như 3 tab
    // Bài học/Listening/Học sinh còn lại.
    private val cachedCourses: List<VocabCourse> = savedStateHandle
        ?.get<String>(KEY_COURSES_CACHE)
        ?.let { runCatching { Json.decodeFromString(ListSerializer(VocabCourse.serializer()), it) }.getOrNull() }
        ?: emptyList()

    private val _uiState = MutableStateFlow(VocabularyUiState(courses = cachedCourses, loading = cachedCourses.isEmpty()))
    val uiState: StateFlow<VocabularyUiState> = _uiState.asStateFlow()

    // Vị trí cuộn danh sách bài học — hoist ở đây (ViewModel sống ngoài
    // AnimatedContent khi được hoist ở AppRoot) để không mất khi đổi tab qua lại.
    // FIX (cùng lý do LessonListViewModel): khôi phục từ SavedStateHandle để
    // vị trí cuộn sống sót qua process death, không chỉ qua chuyển tab.
    val listState = savedStateHandle.restoredLazyListState(KEY_SCROLL)

    init {
        _uiState.onEach { state ->
            savedStateHandle?.set(
                KEY_COURSES_CACHE,
                Json.encodeToString(ListSerializer(VocabCourse.serializer()), state.courses)
            )
        }.launchIn(viewModelScope)
        if (savedStateHandle != null) {
            androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .onEach { savedStateHandle.saveLazyListState(KEY_SCROLL, listState) }
                .launchIn(viewModelScope)
        }
    }

    companion object {
        private const val KEY_COURSES_CACHE = "vocabulary_courses_cache_json"
        private const val KEY_SCROLL = "vocabulary_list_scroll"
    }

    // Giữ giá trị refreshKey lần trước để phân biệt "vừa bấm nút refresh thật"
    // (refreshKey đổi) với "chỉ mount lại do đổi tab" (refreshKey y hệt lần
    // trước) — cùng cơ chế đã áp cho FileManagerViewModel.loadIfNeeded().
    private var lastRefreshKey: Any? = null

    fun loadIfNeeded(refreshKey: Any) {
        val keyChanged = refreshKey != lastRefreshKey
        lastRefreshKey = refreshKey
        if (_uiState.value.courses.isEmpty() || keyChanged) {
            load()
        }
    }

    fun load() {
        // Chỉ hiện spinner toàn màn hình khi CHƯA có dữ liệu (lần đầu mở tab) —
        // khi refresh mà đã có list cũ, giữ nguyên trên màn hình trong lúc chờ
        // dữ liệu mới, tránh xoá trắng rồi vẽ lại (nhấp nháy).
        val showSpinner = _uiState.value.courses.isEmpty()
        _uiState.update { it.copy(loading = showSpinner) }
        viewModelScope.launch {
            try {
                val courses = repo.fetchCourses()
                _uiState.update { it.copy(courses = courses, loading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false) }
                ToastCenter.show("Không tải được danh sách bài học", "⚠️", androidx.compose.ui.graphics.Color(0xFFF59E0B))
            }
        }
    }

    fun setSearch(q: String) = _uiState.update { it.copy(search = q) }

    fun filteredCourses(): List<VocabCourse> {
        val s = _uiState.value
        val q = s.search.trim().lowercase()
        if (q.isEmpty()) return s.courses
        return s.courses.filter { it.title.lowercase().contains(q) || it.description.lowercase().contains(q) }
    }

    fun loadUnits(courseId: String, force: Boolean = false) {
        if (!force && courseId in _uiState.value.loadedUnitCourses) return
        viewModelScope.launch {
            try {
                val units = repo.fetchUnits(courseId)
                _uiState.update {
                    it.copy(
                        unitsByCourse = it.unitsByCourse + (courseId to units),
                        loadedUnitCourses = it.loadedUnitCourses + courseId
                    )
                }
            } catch (e: Exception) {
                ToastCenter.show("Không tải được units của bài học", "⚠️", androidx.compose.ui.graphics.Color(0xFFF59E0B))
            }
        }
    }

    fun loadWords(unitId: String, force: Boolean = false) {
        if (!force && unitId in _uiState.value.loadedWordUnits) return
        viewModelScope.launch {
            try {
                val words = repo.fetchWords(unitId)
                _uiState.update {
                    it.copy(
                        wordsByUnit = it.wordsByUnit + (unitId to words),
                        loadedWordUnits = it.loadedWordUnits + unitId
                    )
                }
            } catch (e: Exception) {
                ToastCenter.show("Không tải được từ vựng của unit", "⚠️", androidx.compose.ui.graphics.Color(0xFFF59E0B))
            }
        }
    }

    fun saveCourse(id: String?, title: String, description: String, onDone: (Boolean, String) -> Unit) {
        if (title.isBlank()) { onDone(false, "Nhập tên bài học nhé!"); return }
        viewModelScope.launch {
            try {
                if (id != null) {
                    repo.updateCourse(id, title.trim(), description.trim())
                    _uiState.update { st ->
                        st.copy(courses = st.courses.map { if (it.id == id) it.copy(title = title.trim(), description = description.trim()) else it })
                    }
                    onDone(true, "Đã cập nhật bài học!")
                } else {
                    val created = repo.createCourse(title.trim(), description.trim())
                    _uiState.update { it.copy(courses = listOf(created) + it.courses) }
                    onDone(true, "Đã tạo bài học mới!")
                }
            } catch (e: Exception) {
                onDone(false, e.message ?: "Có lỗi xảy ra, thử lại nhé!")
            }
        }
    }

    fun deleteCourse(id: String, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                repo.deleteCourse(id)
                _uiState.update { it.copy(courses = it.courses.filter { c -> c.id != id }) }
                onDone(true, "Đã xoá bài học")
            } catch (e: Exception) {
                onDone(false, "Xoá thất bại, thử lại nhé!")
            }
        }
    }

    fun saveUnit(courseId: String, id: String?, title: String, level: String, onDone: (Boolean, String) -> Unit) {
        if (title.isBlank()) { onDone(false, "Nhập tên bài học nhé!"); return }
        viewModelScope.launch {
            try {
                if (id != null) {
                    repo.updateUnit(id, title.trim(), level.trim())
                    _uiState.update { st ->
                        st.copy(unitsByCourse = st.unitsByCourse.mapValues { (cid, units) ->
                            if (cid != courseId) units else units.map { if (it.id == id) it.copy(title = title.trim(), level = level.trim()) else it }
                        })
                    }
                    onDone(true, "Đã cập nhật Unit!")
                } else {
                    val created = repo.createUnit(courseId, title.trim(), level.trim())
                    _uiState.update { st ->
                        val cur = st.unitsByCourse[courseId] ?: emptyList()
                        st.copy(unitsByCourse = st.unitsByCourse + (courseId to (cur + created)))
                    }
                    onDone(true, "Đã tạo Unit mới!")
                }
            } catch (e: Exception) {
                onDone(false, e.message ?: "Có lỗi xảy ra, thử lại nhé!")
            }
        }
    }

    fun deleteUnit(courseId: String, id: String, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                repo.deleteUnit(id)
                _uiState.update { st ->
                    st.copy(unitsByCourse = st.unitsByCourse.mapValues { (cid, units) ->
                        if (cid != courseId) units else units.filter { it.id != id }
                    })
                }
                onDone(true, "Đã xoá Unit")
            } catch (e: Exception) {
                onDone(false, "Xoá thất bại, thử lại nhé!")
            }
        }
    }

    fun saveWord(
        unitId: String, id: String?, word: String, pos: String, ipa: String, meaning: String, example: String,
        onDone: (Boolean, String) -> Unit
    ) {
        if (word.isBlank()) { onDone(false, "Nhập từ vựng nhé!"); return }
        viewModelScope.launch {
            try {
                if (id != null) {
                    repo.updateWord(id, word.trim(), pos, ipa.trim(), meaning.trim(), example.trim())
                    _uiState.update { st ->
                        st.copy(wordsByUnit = st.wordsByUnit.mapValues { (uid, words) ->
                            if (uid != unitId) words else words.map {
                                if (it.id == id) it.copy(word = word.trim(), pos = pos, ipa = ipa.trim(), meaning = meaning.trim(), example = example.trim()) else it
                            }
                        })
                    }
                    onDone(true, "Đã lưu thay đổi!")
                } else {
                    val created = repo.createWord(unitId, word.trim(), pos, ipa.trim(), meaning.trim(), example.trim())
                    _uiState.update { st ->
                        val cur = st.wordsByUnit[unitId] ?: emptyList()
                        st.copy(wordsByUnit = st.wordsByUnit + (unitId to (cur + created)))
                    }
                    onDone(true, "Đã thêm từ vựng!")
                }
            } catch (e: Exception) {
                onDone(false, e.message ?: "Có lỗi xảy ra, thử lại nhé!")
            }
        }
    }

    fun deleteWord(unitId: String, id: String, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                repo.deleteWord(id)
                _uiState.update { st ->
                    st.copy(wordsByUnit = st.wordsByUnit.mapValues { (uid, words) ->
                        if (uid != unitId) words else words.filter { it.id != id }
                    })
                }
                onDone(true, "Đã xoá từ vựng")
            } catch (e: Exception) {
                onDone(false, "Xoá thất bại, thử lại nhé!")
            }
        }
    }

    fun bulkAddWords(unitId: String, lines: List<ParsedWordLine>, onDone: (Boolean, String) -> Unit) {
        if (lines.isEmpty()) { onDone(false, "Chưa có từ vựng hợp lệ nào để thêm!"); return }
        viewModelScope.launch {
            try {
                val created = repo.bulkInsertWords(unitId, lines)
                _uiState.update { st ->
                    val cur = st.wordsByUnit[unitId] ?: emptyList()
                    st.copy(wordsByUnit = st.wordsByUnit + (unitId to (cur + created)))
                }
                onDone(true, "Đã thêm ${created.size} từ vựng!")
            } catch (e: Exception) {
                onDone(false, e.message ?: "Có lỗi xảy ra, thử lại nhé!")
            }
        }
    }
}

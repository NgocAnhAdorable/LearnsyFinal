package com.learnsypro.app.admin.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnsypro.app.admin.data.ListeningItem
import com.learnsypro.app.admin.data.ListeningRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

enum class ListeningFilter { ALL, HAS_WORD_BOX, HAS_TFNM, NO_WORD_BOX }
enum class ListeningSort { ORDER, CREATED, BLANKS }

data class ListeningListUiState(
    val items: List<ListeningItem> = emptyList(),
    val loading: Boolean = true,
    val loadError: Boolean = false,
    val searchQuery: String = "",
    val filter: ListeningFilter = ListeningFilter.ALL,
    val sortBy: ListeningSort = ListeningSort.ORDER,
    val bulkMode: Boolean = false,
    val selected: Set<String> = emptySet()
)

// Tương đương phần list state trong ListeningManager (listening-panel.jsx)
class ListeningListViewModel(
    private val repo: ListeningRepository = ListeningRepository(),
    private val savedStateHandle: SavedStateHandle? = null
) : ViewModel() {

    // FIX "app tự refresh khi vào lại" — cùng cách với LessonListViewModel:
    // cache items xuống SavedStateHandle để ViewModel bị recreate (Activity
    // bị hệ thống thu hồi ở nền) vẫn có sẵn dữ liệu ngay, không rỗng/không
    // spinner chớp qua trước khi fetch nền xong.
    private val cachedItems: List<ListeningItem> = savedStateHandle
        ?.get<String>(KEY_ITEMS_CACHE)
        ?.let { runCatching { Json.decodeFromString(ListSerializer(ListeningItem.serializer()), it) }.getOrNull() }
        ?: emptyList()

    private val _uiState = MutableStateFlow(
        ListeningListUiState(items = cachedItems, loading = cachedItems.isEmpty())
    )
    val uiState: StateFlow<ListeningListUiState> = _uiState.asStateFlow()

    // Vị trí cuộn danh sách câu Listening — hoist ở ViewModel để không mất khi
    // AnimatedContent dispose Composable của tab lúc chuyển tab qua lại.
    // FIX (cùng lý do LessonListViewModel): khôi phục từ SavedStateHandle để
    // vị trí cuộn sống sót qua process death, không chỉ qua chuyển tab.
    val listState = savedStateHandle.restoredLazyListState(KEY_SCROLL)

    init {
        _uiState.onEach { state ->
            savedStateHandle?.set(
                KEY_ITEMS_CACHE,
                Json.encodeToString(ListSerializer(ListeningItem.serializer()), state.items)
            )
        }.launchIn(viewModelScope)
        if (savedStateHandle != null) {
            androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .onEach { savedStateHandle.saveLazyListState(KEY_SCROLL, listState) }
                .launchIn(viewModelScope)
        }
    }

    companion object {
        private const val KEY_ITEMS_CACHE = "listening_list_cache_json"
        private const val KEY_SCROLL = "listening_list_scroll"
    }

    // FIX: sau khi app bị hệ thống kill hẳn tiến trình rồi mở lại (không phải
    // xoay màn hình — đó Activity chỉ recreate, ViewModel vẫn sống), lúc
    // Compose vào lại màn Listening thường auto gọi load() gần như ngay khi
    // authed chuyển true. Nhưng supabase-kt có thể phát SessionStatus.
    // Authenticated một nhịp TRƯỚC KHI access token thực sự được đính kèm
    // vào các request Postgrest tiếp theo (đọc lại từ storage cần thời gian).
    // Nếu fetchAll() lọt vào đúng khe hở đó và bảng có RLS theo auth.uid(),
    // Postgrest không throw lỗi — nó chỉ lặng lẽ trả về mảng rỗng [] (đúng
    // hành vi RLS chuẩn) -> app hiện "Chưa có câu Listening nào" dù dữ liệu
    // vẫn còn nguyên trên server. Vá bằng 1 lần thử lại sau độ trễ ngắn nếu
    // lần đầu về rỗng ngay sau khi ViewModel vừa khởi tạo (không lặp vô hạn,
    // không ảnh hưởng trường hợp danh sách trống thật).
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
        val hasCache = _uiState.value.items.isNotEmpty()
        _uiState.update { it.copy(loading = !hasCache, loadError = false) }
        viewModelScope.launch {
            try {
                val items = repo.fetchAll()
                if (items.isEmpty() && !hasLoadedOnce) {
                    kotlinx.coroutines.delay(600)
                    val retry = repo.fetchAll()
                    _uiState.update { it.copy(items = retry, loading = false) }
                } else {
                    _uiState.update { it.copy(items = items, loading = false) }
                }
                hasLoadedOnce = true
            } catch (e: Exception) {
                // Có cache rồi thì fetch nền lỗi không nên xoá list đang hiện.
                _uiState.update { it.copy(loading = false, loadError = true) }
                hasLoadedOnce = true
            }
        }
    }

    fun setSearch(q: String) = _uiState.update { it.copy(searchQuery = q) }
    fun setFilter(f: ListeningFilter) = _uiState.update { it.copy(filter = f) }
    fun setSortBy(s: ListeningSort) = _uiState.update { it.copy(sortBy = s) }

    fun toggleBulkMode() = _uiState.update {
        it.copy(bulkMode = !it.bulkMode, selected = if (it.bulkMode) emptySet() else it.selected)
    }

    fun toggleSelect(id: String) = _uiState.update {
        val s = it.selected.toMutableSet()
        if (id in s) s.remove(id) else s.add(id)
        it.copy(selected = s)
    }

    fun selectAll() = _uiState.update { it.copy(selected = displayItems().map { i -> i.id }.toSet()) }
    fun deselectAll() = _uiState.update { it.copy(selected = emptySet()) }

    fun displayItems(): List<ListeningItem> {
        val s = _uiState.value
        var list = s.items

        list = when (s.filter) {
            ListeningFilter.ALL -> list
            ListeningFilter.HAS_WORD_BOX -> list.filter { it.wordBox.isNotEmpty() }
            ListeningFilter.HAS_TFNM -> list.filter { it.statements.isNotEmpty() }
            ListeningFilter.NO_WORD_BOX -> list.filter { it.wordBox.isEmpty() }
        }

        if (s.searchQuery.isNotBlank()) {
            val q = s.searchQuery.lowercase()
            list = list.filter {
                it.text.lowercase().contains(q) ||
                    it.tags.any { t -> t.lowercase().contains(q) } ||
                    it.answers.any { a -> a.lowercase().contains(q) }
            }
        }

        list = when (s.sortBy) {
            ListeningSort.BLANKS -> list.sortedByDescending { it.answers.size }
            ListeningSort.CREATED -> list.sortedByDescending { it.createdAt }
            ListeningSort.ORDER -> list
        }

        return list
    }

    fun deleteItem(id: String, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                repo.delete(id)
                _uiState.update {
                    it.copy(items = it.items.filter { i -> i.id != id }, selected = it.selected - id)
                }
                onDone(true, "Đã xoá câu Listening")
            } catch (e: Exception) {
                onDone(false, "Xoá thất bại: ${e.message}")
            }
        }
    }

    fun bulkDelete(onDone: (Boolean, String) -> Unit) {
        val ids = _uiState.value.selected.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                repo.bulkDelete(ids)
                _uiState.update {
                    it.copy(
                        items = it.items.filter { i -> i.id !in ids },
                        selected = emptySet(),
                        bulkMode = false
                    )
                }
                onDone(true, "Đã xoá ${ids.size} câu")
            } catch (e: Exception) {
                onDone(false, "Xoá thất bại: ${e.message}")
            }
        }
    }

    fun duplicateItem(item: ListeningItem, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val dup = repo.duplicate(item, _uiState.value.items)
                _uiState.update { it.copy(items = it.items + dup) }
                onDone(true, "✓ Đã nhân đôi câu Listening")
            } catch (e: Exception) {
                onDone(false, "Nhân đôi thất bại: ${e.message}")
            }
        }
    }

    fun reorder(srcId: String, targetId: String) {
        if (srcId == targetId) return
        val list = _uiState.value.items.toMutableList()
        val srcIdx = list.indexOfFirst { it.id == srcId }
        val tgtIdx = list.indexOfFirst { it.id == targetId }
        if (srcIdx < 0 || tgtIdx < 0) return
        val moved = list.removeAt(srcIdx)
        list.add(tgtIdx, moved)
        val reindexed = list.mapIndexed { i, it -> it.copy(sortOrder = i) }
        _uiState.update { it.copy(items = reindexed) }
        viewModelScope.launch { repo.persistOrder(reindexed.map { it.id }) }
    }

    fun replaceItem(item: ListeningItem) {
        _uiState.update { st -> st.copy(items = st.items.map { if (it.id == item.id) item else it }) }
    }

    fun importItems(items: List<ListeningItem>, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val refreshed = repo.importItems(items, _uiState.value.items)
                _uiState.update { it.copy(items = refreshed) }
                onDone(true, "✓ Đã import ${items.size} câu")
            } catch (e: Exception) {
                onDone(false, "Import thất bại: ${e.message}")
            }
        }
    }
}

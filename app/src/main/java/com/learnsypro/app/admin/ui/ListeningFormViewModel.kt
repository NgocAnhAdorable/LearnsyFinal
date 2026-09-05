package com.learnsypro.app.admin.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnsypro.app.admin.data.ListeningItem
import com.learnsypro.app.admin.data.ListeningRepository
import com.learnsypro.app.admin.data.ListeningStatement
import com.learnsypro.app.admin.data.cleanListeningStr
import com.learnsypro.app.admin.data.countBlanks
import com.learnsypro.app.admin.data.genListeningId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

@Serializable
data class ListeningFormUiState(
    val editingId: String? = null,
    val text: String = "",
    val wordBox: List<String> = emptyList(),
    val shuffleWordBox: Boolean = false,
    val answers: List<String> = emptyList(),
    val statements: List<ListeningStatement> = emptyList(),
    val shuffleStatements: Boolean = false,
    val tags: List<String> = emptyList(),
    val saving: Boolean = false,
    val pendingMismatchConfirm: Boolean = false,
    // FIX: trước đây tab (LIST/FORM/STATS) là rememberSaveable RIÊNG trong
    // ListeningManagerScreen — tách biệt hoàn toàn khỏi SavedStateHandle của
    // form này. Khi vào Recents (Activity trải qua onSaveInstanceState) rồi
    // quay lại, 2 cơ chế lưu khác nhau này có thể LỆCH PHA nhau: nội dung
    // form (SavedStateHandle, gắn ViewModelStore của Activity — đáng tin cậy
    // hơn) khôi phục đúng, nhưng tab (Bundle-Serializable qua rememberSaveable,
    // phụ thuộc vị trí composition trong SaveableStateRegistry) có thể rơi về
    // giá trị khởi tạo LIST — nên dù nội dung vẫn còn, người dùng chỉ thấy
    // list rỗng, tưởng mất hết. Gộp tab vào cùng UiState này để nó lưu/khôi
    // phục ĐỒNG BỘ 100% với nội dung form, chỉ qua 1 cơ chế duy nhất. Lưu
    // bằng String (tên enum) thay vì import enum ListeningTab trực tiếp để
    // tránh phụ thuộc vòng giữa 2 package ui/ và ui.screens/.
    val activeTab: String = "LIST"
)

// Tương đương phần form state trong ListeningManager (listening-panel.jsx)
class ListeningFormViewModel(
    private val repo: ListeningRepository = ListeningRepository(),
    private val savedStateHandle: SavedStateHandle? = null
) : ViewModel() {

    // FIX: trước đây form không có SavedStateHandle -> khi hệ thống kill hẳn
    // tiến trình app ở nền (vd. rời sang app Tin nhắn một lúc), toàn bộ state
    // đang soạn (đoạn văn, Word Box, đáp án, T/F/NM, tags) mất sạch vì chỉ
    // nằm trong RAM; quay lại app thấy đúng tab Listening nhưng form về rỗng.
    // ViewModel thường chỉ sống qua Activity recreate (xoay màn hình...),
    // KHÔNG sống qua process death — phải tự lưu xuống Bundle qua
    // SavedStateHandle (ghi JSON bằng kotlinx.serialization sẵn có trong
    // project) thì mới khôi phục được sau khi process bị kill.
    private val _uiState = MutableStateFlow(
        savedStateHandle?.get<String>(KEY_STATE)?.let {
            runCatching { Json.decodeFromString<ListeningFormUiState>(it) }.getOrNull()
                // saving/pendingMismatchConfirm là cờ tạm thời của 1 lượt lưu —
                // nếu process bị kill giữa chừng, request đó đã chết theo, không
                // được khôi phục về saving=true (sẽ kẹt nút Lưu mãi mãi).
                ?.copy(saving = false, pendingMismatchConfirm = false)
        } ?: ListeningFormUiState()
    )
    val uiState: StateFlow<ListeningFormUiState> = _uiState.asStateFlow()

    init {
        _uiState.onEach { state ->
            savedStateHandle?.set(KEY_STATE, Json.encodeToString(ListeningFormUiState.serializer(), state))
        }.launchIn(viewModelScope)
    }

    companion object {
        private const val KEY_STATE = "listening_form_state_json"
    }

    fun resetForm() {
        _uiState.value = ListeningFormUiState()
    }

    // Composable gọi hàm này thay vì tự giữ 1 biến rememberSaveable riêng —
    // đảm bảo tab luôn đồng bộ, lưu/khôi phục cùng lúc với nội dung form.
    fun setActiveTab(tabName: String) {
        _uiState.update { it.copy(activeTab = tabName) }
    }

    fun openForEdit(item: ListeningItem) {
        _uiState.value = ListeningFormUiState(
            editingId = item.id,
            text = item.text,
            wordBox = item.wordBox,
            shuffleWordBox = item.shuffleWordBox,
            answers = item.answers,
            statements = item.statements,
            shuffleStatements = item.shuffleStatements,
            tags = item.tags,
            activeTab = "FORM"
        )
    }

    fun setText(text: String) = _uiState.update { it.copy(text = text) }
    fun setShuffleWordBox(v: Boolean) = _uiState.update { it.copy(shuffleWordBox = v) }
    fun setShuffleStatements(v: Boolean) = _uiState.update { it.copy(shuffleStatements = v) }

    fun addWord(word: String, onDup: () -> Unit) {
        val w = word.trim()
        if (w.isEmpty()) return
        val s = _uiState.value
        if (s.wordBox.any { it.equals(w, ignoreCase = true) }) { onDup(); return }
        _uiState.update { it.copy(wordBox = it.wordBox + w) }
    }
    fun removeWord(index: Int) = _uiState.update { it.copy(wordBox = it.wordBox.filterIndexed { i, _ -> i != index }) }
    fun updateWord(index: Int, value: String) =
        _uiState.update { it.copy(wordBox = it.wordBox.mapIndexed { i, w -> if (i == index) value else w }) }

    fun addAnswer() = _uiState.update { it.copy(answers = it.answers + "") }
    fun updateAnswer(index: Int, value: String) =
        _uiState.update { it.copy(answers = it.answers.mapIndexed { i, a -> if (i == index) value else a }) }
    fun removeAnswer(index: Int) = _uiState.update { it.copy(answers = it.answers.filterIndexed { i, _ -> i != index }) }

    fun syncBlanksFromText(onResult: (Int) -> Unit) {
        val n = countBlanks(_uiState.value.text)
        if (n == 0) { onResult(0); return }
        _uiState.update { st ->
            val next = (0 until n).map { i -> st.answers.getOrElse(i) { "" } }
            st.copy(answers = next)
        }
        onResult(n)
    }

    fun suggestWordBoxFromAnswers(onResult: (Int) -> Unit) {
        val s = _uiState.value
        val newWords = s.answers.map { it.trim() }.filter { it.isNotEmpty() }
        if (newWords.isEmpty()) { onResult(-1); return }
        var added = 0
        val next = s.wordBox.toMutableList()
        newWords.forEach { w ->
            if (next.none { it.equals(w, ignoreCase = true) }) { next.add(w); added++ }
        }
        _uiState.update { it.copy(wordBox = next) }
        onResult(added)
    }

    fun addStatement() = _uiState.update { it.copy(statements = it.statements + ListeningStatement()) }
    fun updateStatementText(index: Int, text: String) =
        _uiState.update { it.copy(statements = it.statements.mapIndexed { i, s -> if (i == index) s.copy(statement = text) else s }) }
    fun updateStatementAnswer(index: Int, answer: String) =
        _uiState.update { it.copy(statements = it.statements.mapIndexed { i, s -> if (i == index) s.copy(answer = answer) else s }) }
    fun removeStatement(index: Int) =
        _uiState.update { it.copy(statements = it.statements.filterIndexed { i, _ -> i != index }) }

    fun moveStatement(index: Int, dir: Int) {
        val s = _uiState.value.statements.toMutableList()
        val j = index + dir
        if (j < 0 || j >= s.size) return
        val tmp = s[index]; s[index] = s[j]; s[j] = tmp
        _uiState.update { it.copy(statements = s) }
    }

    fun addTag(tag: String) {
        val t = tag.trim()
        if (t.isEmpty() || t in _uiState.value.tags) return
        _uiState.update { it.copy(tags = it.tags + t) }
    }
    fun removeTag(tag: String) = _uiState.update { it.copy(tags = it.tags.filter { x -> x != tag }) }

    fun requestSave(
        allItems: List<ListeningItem>,
        onDupText: () -> Unit,
        onEmptyText: () -> Unit,
        onMismatchConfirmNeeded: (blanks: Int, answers: Int) -> Unit,
        onSaved: (ListeningItem, isNew: Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        val s = _uiState.value
        if (s.saving) return
        val cleanText = cleanListeningStr(s.text)
        if (cleanText.isEmpty()) { onEmptyText(); return }

        if (repo.isDuplicateText(cleanText, s.editingId, allItems)) { onDupText(); return }

        val blankCount = countBlanks(cleanText)
        val cleanAnswers = s.answers.map { cleanListeningStr(it) }.filter { it.isNotEmpty() }

        if (blankCount > 0 && blankCount != cleanAnswers.size && !s.pendingMismatchConfirm) {
            onMismatchConfirmNeeded(blankCount, cleanAnswers.size)
            return
        }

        doSave(allItems, onSaved, onError)
    }

    fun confirmSaveAnyway(allItems: List<ListeningItem>, onSaved: (ListeningItem, isNew: Boolean) -> Unit, onError: (String) -> Unit) {
        doSave(allItems, onSaved, onError)
    }

    private fun doSave(
        allItems: List<ListeningItem>,
        onSaved: (ListeningItem, isNew: Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        val s = _uiState.value
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val cleanText = cleanListeningStr(s.text)
                val cleanWordBox = s.wordBox.map { cleanListeningStr(it) }.filter { it.isNotEmpty() }
                val cleanAnswers = s.answers.map { cleanListeningStr(it) }.filter { it.isNotEmpty() }
                val cleanStatements = s.statements
                    .filter { it.statement.isNotBlank() }
                    .map { it.copy(statement = cleanListeningStr(it.statement), answer = cleanListeningStr(it.answer)) }
                val cleanTags = s.tags.filter { it.isNotBlank() }

                val isNew = s.editingId == null
                val item = if (!isNew) {
                    // FIX: trước đây không truyền sortOrder/createdAt khi update, nên
                    // ListeningItem dùng giá trị mặc định (sortOrder=0, createdAt="").
                    // Gửi createdAt="" lên cột timestamptz của Supabase bị Postgres từ
                    // chối với lỗi "invalid input syntax" — đúng lỗi thấy khi bấm Lưu
                    // thay đổi. Giữ nguyên 2 giá trị gốc của item đang sửa để tránh cả
                    // lỗi lưu lẫn việc vô tình reset thứ tự sắp xếp về 0.
                    val original = allItems.find { it.id == s.editingId }
                    ListeningItem(
                        id = s.editingId!!,
                        text = cleanText, wordBox = cleanWordBox, answers = cleanAnswers,
                        statements = cleanStatements, shuffleStatements = s.shuffleStatements,
                        shuffleWordBox = s.shuffleWordBox, tags = cleanTags,
                        sortOrder = original?.sortOrder ?: 0,
                        createdAt = original?.createdAt ?: Instant.now().toString()
                    ).also { repo.update(it) }
                } else {
                    val sortMax = allItems.maxOfOrNull { it.sortOrder } ?: 0
                    ListeningItem(
                        id = genListeningId(), text = cleanText, wordBox = cleanWordBox,
                        answers = cleanAnswers, statements = cleanStatements,
                        shuffleStatements = s.shuffleStatements, shuffleWordBox = s.shuffleWordBox,
                        tags = cleanTags, sortOrder = sortMax + 1, createdAt = Instant.now().toString()
                    ).also { repo.create(it) }
                }

                _uiState.update { it.copy(saving = false, pendingMismatchConfirm = false) }
                resetForm()
                onSaved(item, isNew)
            } catch (e: Exception) {
                _uiState.update { it.copy(saving = false) }
                // TẠM THỜI: hiện chi tiết lỗi đầy đủ (class + message gốc từ Postgrest)
                // thay vì chỉ e.message rút gọn, để xác định chính xác nguyên nhân
                // "invalid input" — nhiều khả năng message thật bị Toast cắt bớt hoặc
                // e.message không phản ánh đúng lỗi Postgrest trả về. Log đầy đủ ra
                // Logcat (không bị giới hạn hiển thị như toast) + toast bản rút gọn.
                // Sau khi xác định xong nguyên nhân thật, đổi lại thành thông báo gọn.
                android.util.Log.e("ListeningSave", "Lỗi lưu Listening", e)
                onError("${e::class.simpleName}: ${e.message}")
            }
        }
    }
}

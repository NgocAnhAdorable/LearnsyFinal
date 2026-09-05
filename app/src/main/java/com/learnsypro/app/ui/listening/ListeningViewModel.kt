package com.learnsypro.app.ui.listening

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.learnsypro.app.audio.QuizAudioEngine
import com.learnsypro.app.data.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable

@Serializable
private data class ListeningItemRow(
    val id: String,
    val text: String? = null,
    val word_box: List<String>? = null,
    val answers: List<String>? = null,
    val statements: List<StatementRow>? = null,
    val shuffle_statements: Boolean? = null,
    val shuffle_word_box: Boolean? = null
)

@Serializable
private data class StatementRow(val statement: String, val answer: String)

private fun StatementRow.toStatement(): Statement = Statement(
    text = statement,
    answer = when (answer) {
        "True" -> StatementAnswer.TRUE
        "False" -> StatementAnswer.FALSE
        else -> StatementAnswer.NOT_MENTIONED
    }
)

/**
 * ── ListeningViewModel ──
 * Thay state đầu ListeningPractice trong listening-practice.jsx: load items
 * từ Supabase (bảng listening_items), quản lý bài đang mở (selected),
 * blanks/stmtSel, TTS state, tính điểm.
 */
class ListeningViewModel(application: Application) : AndroidViewModel(application) {

    private val tts = ListeningTts(application)
    private val cache = com.learnsypro.app.data.OfflineCacheStore(application)
    // Dùng chung engine âm thanh với QuizPlayerScreen — bấm Đúng/Sai/NM
    // phát cùng tiếng "click" như khi chọn đáp án trắc nghiệm.
    private val audio = QuizAudioEngine(application, viewModelScope)

    private val _items = MutableStateFlow<List<ListeningItem>>(emptyList())
    val items: StateFlow<List<ListeningItem>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    // Trạng thái riêng cho nút refresh chủ động (khác với _loading dùng cho lần
    // tải đầu tiên toàn màn hình) — để UI biết hiện spinner nhỏ trên nút thay vì
    // đè lại toàn bộ danh sách bằng skeleton loading như lúc mới mở màn.
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _loadError = MutableStateFlow(false)
    val loadError: StateFlow<Boolean> = _loadError.asStateFlow()

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val _downloadedIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedIds: StateFlow<Set<String>> = _downloadedIds.asStateFlow()

    private val _selected = MutableStateFlow<ListeningItem?>(null)
    val selected: StateFlow<ListeningItem?> = _selected.asStateFlow()

    private val _wordBoxDisplay = MutableStateFlow<List<String>>(emptyList())
    val wordBoxDisplay: StateFlow<List<String>> = _wordBoxDisplay.asStateFlow()

    private val _blanks = MutableStateFlow<List<String>>(emptyList())
    val blanks: StateFlow<List<String>> = _blanks.asStateFlow()

    // Ô trống đang được chọn (focus) — bấm 1 từ trong Word Box sẽ điền vào ô này.
    private val _activeBlankIndex = MutableStateFlow<Int?>(null)
    val activeBlankIndex: StateFlow<Int?> = _activeBlankIndex.asStateFlow()

    // Từ trong Word Box đã được dùng ở ít nhất 1 ô trống — tô mờ để biết còn từ nào chưa dùng.
    private val _usedWords = MutableStateFlow<Set<String>>(emptySet())
    val usedWords: StateFlow<Set<String>> = _usedWords.asStateFlow()

    private val _stmtSel = MutableStateFlow<List<StatementAnswer?>>(emptyList())
    val stmtSel: StateFlow<List<StatementAnswer?>> = _stmtSel.asStateFlow()

    private val _submitted = MutableStateFlow(false)
    val submitted: StateFlow<Boolean> = _submitted.asStateFlow()

    private val _showScoreToast = MutableStateFlow(false)
    val showScoreToast: StateFlow<Boolean> = _showScoreToast.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _speechRate = MutableStateFlow(1.0f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _isRestarting = MutableStateFlow(false)
    val isRestarting: StateFlow<Boolean> = _isRestarting.asStateFlow()

    // ── Ước lượng tiến trình đọc (TTS không có duration/seek thật) ──
    // Chuẩn hoá theo tốc độ đọc trung bình 150 từ/phút ở rate = 1.0x, quy đổi
    // theo speechRate hiện tại để thanh tiến trình chạy đúng nhịp đọc thực tế.
    private var totalWords = 0
    private val _elapsedSeconds = MutableStateFlow(0f)
    val elapsedSeconds: StateFlow<Float> = _elapsedSeconds.asStateFlow()
    private val _estimatedDurationSeconds = MutableStateFlow(0f)
    val estimatedDurationSeconds: StateFlow<Float> = _estimatedDurationSeconds.asStateFlow()
    private var progressJob: kotlinx.coroutines.Job? = null

    private fun recomputeDuration() {
        val wps = (150f / 60f) * _speechRate.value
        _estimatedDurationSeconds.value = if (wps > 0f) totalWords / wps else 0f
    }

    private fun startProgressTimer() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(200)
                _elapsedSeconds.value = (_elapsedSeconds.value + 0.2f).coerceAtMost(_estimatedDurationSeconds.value)
            }
        }
    }

    private fun stopProgressTimer() {
        progressJob?.cancel()
        progressJob = null
    }

    // Dùng chung trạng thái mute với QuizAudioEngine (đồng bộ DataStore "qp_muted")
    // — tắt tiếng ở Listening cũng tắt luôn tiếng click/sfx dùng chung engine này.
    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    init {
        tts.setListeners(
            onStart = { _isPlaying.value = true; _isRestarting.value = false; startProgressTimer() },
            onEnd = {
                _isPlaying.value = false
                _isRestarting.value = false
                stopProgressTimer()
                _elapsedSeconds.value = _estimatedDurationSeconds.value
            },
            onError = { _isPlaying.value = false; _isRestarting.value = false; stopProgressTimer() }
        )
        viewModelScope.launch { _muted.value = audio.isMuted() }
        loadItems()
    }

    override fun onCleared() {
        super.onCleared()
        stopProgressTimer()
        tts.shutdown()
    }

    private fun loadItems() {
        viewModelScope.launch {
            _downloadedIds.value = cache.downloadedListeningIds()
            fetchAndApply()
            _loading.value = false
        }
    }

    /** Học sinh chủ động bấm nút refresh trên header — tải lại danh sách bài
     *  Listening từ Supabase mà không rời màn hình hay mất bài đang mở dở.
     *  Tương đương onRefresh của DashboardScreen (LearnsyNavHost.kt) nhưng
     *  scope riêng cho Listening. */
    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            _downloadedIds.value = cache.downloadedListeningIds()
            fetchAndApply()
            _isRefreshing.value = false
        }
    }

    private suspend fun fetchAndApply() {
        try {
            // withTimeout: khi mất mạng hoàn toàn, socket connect có thể
            // treo rất lâu trước khi hệ thống tự báo lỗi — giới hạn 6s để
            // chủ động rơi về cache offline thay vì kẹt màn hình loading.
            val rows = withTimeout(6000) {
                SupabaseClientProvider.client.postgrest["listening_items"]
                    .select()
                    .decodeList<ListeningItemRow>()
            }
            val loaded = rows.map { r ->
                ListeningItem(
                    id = r.id,
                    text = r.text ?: "",
                    wordBox = r.word_box ?: emptyList(),
                    answers = r.answers ?: emptyList(),
                    statements = r.statements?.map { it.toStatement() } ?: emptyList(),
                    shuffleStatements = r.shuffle_statements ?: false,
                    shuffleWordBox = r.shuffle_word_box ?: false
                )
            }
            _items.value = loaded
            _isOffline.value = false
            _loadError.value = false
            cache.saveListeningItems(loaded)
        } catch (e: Exception) {
            // Mất mạng — dùng bài đã cache offline thay vì báo lỗi trắng trang.
            // Khi đang refresh (đã có _items từ trước), giữ nguyên danh sách hiện
            // tại thay vì phá dở sang cache cũ hơn có thể ít bài hơn.
            if (_items.value.isNotEmpty()) return
            val cached = cache.loadListeningItems()
            if (cached.isNotEmpty()) {
                _items.value = cached
                _isOffline.value = true
                _loadError.value = false
            } else {
                _loadError.value = true
            }
        }
    }

    /** Học sinh chủ động bấm "Tải về" 1 bài — chỉ đánh dấu để hiển thị icon,
     *  vì nội dung bài đã có sẵn trong cache tự động ở trên rồi. */
    fun downloadItem(itemId: String) {
        viewModelScope.launch {
            cache.markListeningDownloaded(itemId)
            _downloadedIds.value = cache.downloadedListeningIds()
        }
    }

    fun openItem(item: ListeningItem) {
        tts.stop()
        _isPlaying.value = false
        _isRestarting.value = false

        val stmts = if (item.shuffleStatements) shuffleList(item.statements) else item.statements
        val wb = if (item.shuffleWordBox) shuffleList(item.wordBox) else item.wordBox

        _selected.value = item.copy(statements = stmts)
        _wordBoxDisplay.value = wb
        _blanks.value = item.answers.map { "" }
        _stmtSel.value = stmts.map { null }
        _submitted.value = false
        _showScoreToast.value = false
        _usedWords.value = emptySet()
        _activeBlankIndex.value = null

        totalWords = tts.toPlainText(item.text).split(" ").filter { it.isNotEmpty() }.size
        _elapsedSeconds.value = 0f
        recomputeDuration()
    }

    fun closeItem() {
        tts.stop()
        stopProgressTimer()
        _isPlaying.value = false
        _isRestarting.value = false
        _selected.value = null
        _submitted.value = false
    }

    /** Tua tới/lùi [deltaSeconds] giây (ước lượng) — dừng và đọc lại từ vị trí từ tương ứng. */
    fun seek(deltaSeconds: Float) {
        val sel = _selected.value ?: return
        if (_muted.value || totalWords == 0) return
        val wps = (150f / 60f) * _speechRate.value
        val newElapsed = (_elapsedSeconds.value + deltaSeconds).coerceIn(0f, _estimatedDurationSeconds.value)
        _elapsedSeconds.value = newElapsed
        val wordIndex = (newElapsed * wps).toInt()
        tts.stop()
        stopProgressTimer()
        if (wordIndex >= totalWords - 1) {
            // Tua hết bài — coi như đã đọc xong, không phát tiếp.
            _isPlaying.value = false
            _isRestarting.value = false
            return
        }
        _isRestarting.value = true
        tts.speakFromWord(sel.text, wordIndex, _speechRate.value)
    }

    fun reshuffleWordBox() {
        val current = _wordBoxDisplay.value
        if (current.size <= 1) return
        var next: List<String>
        do {
            next = shuffleList(current)
        } while (next == current)
        _wordBoxDisplay.value = next
    }

    fun setBlank(index: Int, value: String) {
        val list = _blanks.value.toMutableList()
        if (index in list.indices) list[index] = value
        _blanks.value = list
        recomputeUsedWords()
    }

    fun setActiveBlank(index: Int?) {
        _activeBlankIndex.value = index
    }

    /** Bấm 1 từ trong Word Box: điền vào ô trống đang focus, nếu chưa focus ô nào
     *  thì điền vào ô trống đầu tiên chưa có nội dung. */
    fun fillActiveBlankWithWord(word: String) {
        val target = _activeBlankIndex.value
            ?: _blanks.value.indexOfFirst { it.isBlank() }.takeIf { it >= 0 }
            ?: return
        setBlank(target, word)
        // Tự nhảy sang ô trống kế tiếp còn thiếu để điền nhanh liên tục.
        val nextEmpty = _blanks.value.indexOfFirst { it.isBlank() }
        _activeBlankIndex.value = if (nextEmpty >= 0) nextEmpty else null
    }

    private fun recomputeUsedWords() {
        _usedWords.value = _blanks.value.filter { it.isNotBlank() }
            .map { normAnswer(it) }
            .toSet()
    }

    fun setStatementAnswer(index: Int, value: StatementAnswer) {
        audio.playClick()
        val list = _stmtSel.value.toMutableList()
        if (index in list.indices) list[index] = value
        _stmtSel.value = list
    }

    fun toggleMuted() {
        val next = !_muted.value
        _muted.value = next
        audio.setMuted(next)
        if (next) {
            // Tắt tiếng: dừng ngay giọng đọc đang phát (nếu có)
            tts.stop()
            _isPlaying.value = false
            _isRestarting.value = false
        }
    }

    fun togglePlayPause() {
        val sel = _selected.value ?: return
        if (_muted.value) return
        if (tts.isSpeaking()) {
            tts.pause()
        } else {
            _isPlaying.value = true
            tts.speak(sel.text, _speechRate.value)
        }
    }

    fun setSpeechRate(rate: Float) {
        _speechRate.value = rate
        recomputeDuration()
        val sel = _selected.value ?: return
        if (tts.isSpeaking()) {
            _isRestarting.value = true
            tts.speak(sel.text, rate)
        }
    }

    fun restart() {
        val sel = _selected.value ?: return
        if (_muted.value) return
        tts.stop()
        _isPlaying.value = false
        _isRestarting.value = true
        tts.speak(sel.text, _speechRate.value)
    }

    /** Tương đương score useMemo — trả về (correct, total). */
    fun computeScore(): Pair<Int, Int> {
        val sel = _selected.value ?: return 0 to 0
        var correct = 0
        var total = 0
        sel.answers.forEachIndexed { i, ans ->
            total++
            if (normAnswer(_blanks.value.getOrNull(i)) == normAnswer(ans)) correct++
        }
        sel.statements.forEachIndexed { i, st ->
            total++
            if (_stmtSel.value.getOrNull(i) == st.answer) correct++
        }
        return correct to total
    }

    fun submit() {
        _submitted.value = true
        viewModelScope.launch {
            kotlinx.coroutines.delay(420)
            _showScoreToast.value = true
        }
    }

    fun dismissScoreToast() {
        _showScoreToast.value = false
    }
}

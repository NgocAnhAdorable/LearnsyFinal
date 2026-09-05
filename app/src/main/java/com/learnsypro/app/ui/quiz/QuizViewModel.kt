package com.learnsypro.app.ui.quiz

import android.app.Application
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.learnsypro.app.audio.QuizAudioEngine
import com.learnsypro.app.ui.dashboard.LevelState
import com.learnsypro.app.ui.dashboard.levelForXp
import com.learnsypro.app.ui.dashboard.xpForResult
import com.learnsypro.app.ui.dashboard.xpForSpeedResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.Date

private val Application.quizStateDataStore by preferencesDataStore(name = "learnsy_quiz_state")
private val PRACTICE_MODE_KEY = booleanPreferencesKey("qp_practice")
private val BGM_ON_KEY = booleanPreferencesKey("qp_bgm")

data class HistoryRecord(
    val id: Long,
    val timestampIso: String,
    val lessonTitle: String,
    val score: Int,
    val total: Int,
    val pct: Int, // 0-100
    val xpEarned: Int = 0
)

@Serializable
private data class SavedQuizState(
    val answersJson: String,
    val cur: Int,
    // Mốc thời gian tuyệt đối (epoch millis) khi hết giờ — KHÔNG lưu số giây
    // còn lại nữa. Số giây còn lại là 1 giá trị "chết", chỉ đúng tại đúng thời
    // điểm ghi xuống đĩa, nên trước đây phải ghi lại mỗi giây mới theo kịp thời
    // gian thực (~2700 lần ghi đĩa/bài 45 phút). Mốc epoch millis thì lưu ĐÚNG
    // 1 LẦN khi bắt đầu bài là đủ — timeLeft luôn tính lại bằng
    // (deadlineEpochMillis - System.currentTimeMillis()) / 1000, đúng dù app bị
    // kill/mở lại sau bao lâu, không cần ghi đĩa lại theo từng giây trôi qua.
    val deadlineEpochMillis: Long?,
    val flags: List<Boolean>
)

/**
 * ── QuizViewModel ──
 * Thay toàn bộ state + useEffect trong function QuizPlayer(quiz-player.jsx):
 * answers/flags/cur/submitted/streak/timeLeft/answerTimes, timer countdown,
 * lưu tiến độ (DataStore thay localStorage), audio engine, computeScore,
 * doSubmit/handleSubmit/resetQuiz.
 *
 * CHƯA convert: lưu kết quả lên Supabase (window.saveQuizResult) — cần
 * biết cấu trúc bảng quiz_results thật trước khi viết; hiện chỉ emit
 * HistoryRecord qua callback onSaveHistory, nơi gọi (QuizPlayerScreen)
 * chịu trách nhiệm lưu Supabase + cập nhật Dashboard history.
 */
class QuizViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = application.quizStateDataStore
    private val audio = QuizAudioEngine(application, viewModelScope)
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var lesson: Lesson
    private var storageKey: String = ""

    private val _questions = MutableStateFlow<List<Question>>(emptyList())
    val questions: StateFlow<List<Question>> = _questions.asStateFlow()

    private val _answers = MutableStateFlow<List<Answer>>(emptyList())
    val answers: StateFlow<List<Answer>> = _answers.asStateFlow()

    private val _flags = MutableStateFlow<List<Boolean>>(emptyList())
    val flags: StateFlow<List<Boolean>> = _flags.asStateFlow()

    private val _cur = MutableStateFlow(0)
    val cur: StateFlow<Int> = _cur.asStateFlow()

    private val _submitted = MutableStateFlow(false)
    val submitted: StateFlow<Boolean> = _submitted.asStateFlow()

    private val _timeLeft = MutableStateFlow<Int?>(null)
    val timeLeft: StateFlow<Int?> = _timeLeft.asStateFlow()

    private val _streak = MutableStateFlow(0)
    val streak: StateFlow<Int> = _streak.asStateFlow()

    private val _bestStreak = MutableStateFlow(0)
    val bestStreak: StateFlow<Int> = _bestStreak.asStateFlow()

    private val _answerTimesSec = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val answerTimesSec: StateFlow<Map<Int, Int>> = _answerTimesSec.asStateFlow()

    private val _scoreModalVisible = MutableStateFlow(false)
    val scoreModalVisible: StateFlow<Boolean> = _scoreModalVisible.asStateFlow()

    private val _warnModal = MutableStateFlow<List<Int>?>(null)
    val warnModal: StateFlow<List<Int>?> = _warnModal.asStateFlow()

    private val _showStats = MutableStateFlow(false)
    val showStats: StateFlow<Boolean> = _showStats.asStateFlow()

    private val _confettiOn = MutableStateFlow(false)
    val confettiOn: StateFlow<Boolean> = _confettiOn.asStateFlow()

    // XP tích luỹ TRƯỚC lần làm bài này — do màn gọi initQuiz() truyền vào
    // (Dashboard biết tổng XP từ history, ViewModel này không tự tính lại
    // toàn bộ lịch sử mỗi lần). Dùng để so sánh level trước/sau, phát hiện
    // level-up ngay khi nộp bài.
    private var xpBeforeThisAttempt = 0

    // Thử thách tốc độ — do QuizPlayerScreen truyền vào lúc initQuiz(), chỉ
    // ảnh hưởng công thức tính XP lúc nộp bài, KHÔNG đổi luồng chấm điểm
    // đúng/sai hay UI làm bài.
    private var speedModeActive = false

    private val _xpEarned = MutableStateFlow(0)
    val xpEarned: StateFlow<Int> = _xpEarned.asStateFlow()

    private val _levelUpEvent = MutableStateFlow<LevelState?>(null)
    val levelUpEvent: StateFlow<LevelState?> = _levelUpEvent.asStateFlow()

    fun consumeLevelUpEvent() { _levelUpEvent.value = null }

    private val _practiceMode = MutableStateFlow(false)
    val practiceMode: StateFlow<Boolean> = _practiceMode.asStateFlow()

    private val _bgmOn = MutableStateFlow(false)
    val bgmOn: StateFlow<Boolean> = _bgmOn.asStateFlow()

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private var qStartTimeMillis = System.currentTimeMillis()
    // Mốc thời gian tuyệt đối khi hết giờ làm bài — nguồn sự thật duy nhất cho
    // _timeLeft (được TÍNH LẠI mỗi giây từ mốc này, không lưu số giây còn lại).
    // null nếu bài không giới hạn thời gian.
    private var deadlineEpochMillis: Long? = null
    private var submitGuard = false
    private var timerJob: kotlinx.coroutines.Job? = null
    private var onSaveHistoryCallback: ((HistoryRecord, List<PerQuestionResult>) -> Unit)? = null

    val score: StateFlow<Pair<Int, Int>> = MutableStateFlow(0 to 0).also { flow ->
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(_answers, _questions) { a, q -> computeScore(q, a) }
                .collect { flow.value = it }
        }
    }.asStateFlow()

    fun initQuiz(
        lesson: Lesson,
        currentTotalXp: Int = 0,
        speedMode: Boolean = false,
        onSaveHistory: (HistoryRecord, List<PerQuestionResult>) -> Unit
    ) {
        this.lesson = lesson
        this.xpBeforeThisAttempt = currentTotalXp
        this.speedModeActive = speedMode
        this.onSaveHistoryCallback = onSaveHistory
        storageKey = "quizstate_${lesson.title}_${lesson.questions.size}"
        _questions.value = lesson.questions
        submitGuard = false
        // Reset trạng thái đã nộp bài — QuizViewModel có thể bị tái sử dụng (Compose
        // Navigation giữ nguyên instance ViewModel) khi quay lại và vào lại cùng bài
        // học, nên nếu không reset ở đây, "submitted" từ lần làm trước sẽ còn sót lại
        // và khiến app hiện sẵn đáp án dù người dùng chưa nộp bài lần này.
        _submitted.value = false
        _scoreModalVisible.value = false
        _confettiOn.value = false
        _streak.value = 0
        _bestStreak.value = 0
        _xpEarned.value = 0
        _levelUpEvent.value = null

        viewModelScope.launch {
            _practiceMode.value = dataStore.data.first()[PRACTICE_MODE_KEY] ?: false
            _bgmOn.value = dataStore.data.first()[BGM_ON_KEY] ?: false
            _muted.value = audio.isMuted()

            val saved = loadSavedState()
            if (saved != null) {
                _answers.value = decodeAnswers(saved.answersJson, lesson.questions)
                _cur.value = saved.cur
                deadlineEpochMillis = saved.deadlineEpochMillis
                _timeLeft.value = saved.deadlineEpochMillis?.let {
                    ((it - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
                }
                _flags.value = saved.flags
            } else {
                _answers.value = lesson.questions.map { emptyAnswerFor(it) }
                _flags.value = lesson.questions.map { false }
                if (lesson.timeLimitMinutes > 0) {
                    val totalSec = lesson.timeLimitMinutes * 60
                    deadlineEpochMillis = System.currentTimeMillis() + totalSec * 1000L
                    _timeLeft.value = totalSec
                } else {
                    deadlineEpochMillis = null
                    _timeLeft.value = null
                }
            }

            qStartTimeMillis = System.currentTimeMillis()
            startTimerIfNeeded()

            if (_bgmOn.value && !_muted.value) audio.startBgm()
        }
    }

    override fun onCleared() {
        super.onCleared()
        audio.stopBgm()
    }

    // ═══════════════ Navigation ═══════════════

    fun navTo(idx: Int) {
        if (idx == _cur.value || idx !in _questions.value.indices) return
        _cur.value = idx
        qStartTimeMillis = System.currentTimeMillis()
        audio.playNav()
        persistState()
    }

    /** Dùng khi chế độ cuộn tự đồng bộ `cur` theo câu đang hiện trong khung
     * nhìn (LazyColumn) — KHÔNG phát âm thanh / reset giờ tính thời gian
     * trả lời, vì đây không phải hành động "chuyển câu" chủ động của
     * người dùng, chỉ là cập nhật hiển thị (progress bar / NavDots). */
    fun setCurQuietly(idx: Int) {
        if (idx == _cur.value || idx !in _questions.value.indices) return
        _cur.value = idx
    }

    fun goNext() {
        if (_cur.value < _questions.value.size - 1) navTo(_cur.value + 1)
    }

    fun goPrev() {
        if (_cur.value > 0) navTo(_cur.value - 1)
    }

    // ═══════════════ Answer handling ═══════════════

    fun setAnswer(index: Int, answer: Answer) {
        val list = _answers.value.toMutableList()
        list[index] = answer
        _answers.value = list

        val q = _questions.value.getOrNull(index) ?: return
        val ok = isAnswerCorrect(q, answer)
        val elapsed = ((System.currentTimeMillis() - qStartTimeMillis) / 1000).toInt()
        _answerTimesSec.value = _answerTimesSec.value + (index to elapsed)

        // Cập nhật streak âm thầm để dùng cho thống kê (StatsModal) dù ở chế độ nào,
        // nhưng CHỈ phát âm thanh đúng/sai/streak khi đang ở chế độ Luyện tập — vì
        // chỉ lúc đó đáp án mới được lộ ra ngay (questionRevealed). Ở chế độ Thi cử,
        // đáp án chưa được tiết lộ nên chỉ nên nghe tiếng "chọn" trung tính, giống
        // quiz-player.jsx (nơi việc chọn đáp án chỉ gọi _sfxClick(), không có
        // _sfxCorrect/_sfxWrong nào được gọi trong luồng chọn đáp án cả).
        if (ok) {
            val ns = _streak.value + 1
            _streak.value = ns
            _bestStreak.value = maxOf(_bestStreak.value, ns)
            if (_practiceMode.value) {
                if (ns >= 3) audio.playStreak(ns) else audio.playCorrect()
            } else {
                audio.playClick()
            }
        } else {
            _streak.value = 0
            if (_practiceMode.value) audio.playWrong() else audio.playClick()
        }
        persistState()
    }

    fun toggleFlag(index: Int) {
        val list = _flags.value.toMutableList()
        list[index] = !list[index]
        _flags.value = list
        audio.playClick()
        persistState()
    }

    fun playClickSound() = audio.playClick()

    // ═══════════════ Practice / BGM / Mute ═══════════════

    fun togglePracticeMode() {
        _practiceMode.value = !_practiceMode.value
        viewModelScope.launch { dataStore.edit { it[PRACTICE_MODE_KEY] = _practiceMode.value } }
    }

    fun toggleMuted() {
        val next = !_muted.value
        _muted.value = next
        audio.setMuted(next)
    }

    fun toggleBgm() {
        val next = !_bgmOn.value
        _bgmOn.value = next
        viewModelScope.launch { dataStore.edit { it[BGM_ON_KEY] = next } }
        if (next) {
            if (_muted.value) { _muted.value = false; audio.setMuted(false) }
            audio.startBgm()
        } else {
            audio.stopBgm()
        }
    }

    // ═══════════════ Timer ═══════════════

    private fun startTimerIfNeeded() {
        timerJob?.cancel()
        if (_timeLeft.value == null) return
        timerJob = viewModelScope.launch {
            while (isActive && _timeLeft.value != null && !_submitted.value) {
                val t = _timeLeft.value ?: break
                if (t <= 0) {
                    doSubmit()
                    break
                }
                if (t <= 5) audio.playTickUrgent() else if (t <= 10) audio.playTick()
                delay(1000)
                // Tính lại từ deadlineEpochMillis (mốc thời gian tuyệt đối) thay vì
                // trừ dần 1 từ giá trị cũ — chỉ cập nhật StateFlow trong RAM, KHÔNG
                // ghi đĩa ở đây nữa. deadlineEpochMillis đã được persistState() lưu
                // ĐÚNG 1 LẦN lúc bắt đầu bài (xem nơi khởi tạo _timeLeft/deadlineEpochMillis
                // ở start()) — số giây còn lại luôn tính đúng lại được từ đó bất kể
                // app có bị kill/mở lại giữa chừng hay không, nên không cần ghi đĩa
                // lặp lại mỗi giây như trước (từng là ~2700 lần ghi/bài 45 phút).
                val deadline = deadlineEpochMillis
                _timeLeft.value = if (deadline != null) {
                    ((deadline - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
                } else {
                    (_timeLeft.value ?: 0) - 1
                }
            }
        }
    }

    // ═══════════════ Submit flow ═══════════════

    fun getUnanswered(): List<Int> {
        val q = _questions.value
        val a = _answers.value
        return q.indices.filter { i ->
            val ans = a.getOrElse(i) { Answer.Empty }
            when (q[i].type) {
                QuestionType.MULTIPLE -> (ans as? Answer.Single)?.index == null
                QuestionType.MULTI_SELECT -> (ans as? Answer.Multi)?.indices.isNullOrEmpty()
                QuestionType.FILL_BLANK -> (ans as? Answer.Text)?.value.isNullOrBlank()
                QuestionType.TRUE_FALSE -> (ans as? Answer.TrueFalseAnswer)?.values?.any { it == null } ?: true
            }
        }.map { it + 1 }
    }

    fun handleSubmit() {
        val unanswered = getUnanswered()
        if (unanswered.isNotEmpty()) {
            _warnModal.value = unanswered
            return
        }
        doSubmit()
    }

    fun dismissWarnAndReview() {
        val first = _warnModal.value?.firstOrNull()
        _warnModal.value = null
        if (first != null) navTo(first - 1)
    }

    fun dismissWarnAndSubmitAnyway() {
        _warnModal.value = null
        doSubmit()
    }

    private fun doSubmit() {
        // Guard chống nộp bài / lưu kết quả 2 lần: kiểm tra VÀ khoá ngay trong
        // cùng một dòng trước khi làm bất cứ việc gì khác. Trước đây "if (guard)
        // return; guard = true" nằm ở 2 dòng tách biệt — nếu doSubmit() vô tình
        // được gọi lại lần 2 rất nhanh (ví dụ do double-tap nút Nộp bài trước khi
        // Compose kịp ẩn nút sau lần nhấn đầu), phần thân hàm phía dưới (bao gồm
        // insert Supabase) có thể chạy tới trước khi guard cũ kịp phát huy tác
        // dụng. Gộp lại đảm bảo lần gọi thứ 2 luôn bị chặn ngay lập tức.
        if (submitGuard || _submitted.value) return
        submitGuard = true
        _submitted.value = true
        timerJob?.cancel()

        val (hs, ht) = computeScore(_questions.value, _answers.value)
        val hpct = if (ht > 0) Math.round(hs.toDouble() / ht * 100).toInt() else 0

        // XP + level-up: so level TRƯỚC (xpBeforeThisAttempt) và level SAU khi
        // cộng XP bài vừa làm — không lưu state XP riêng, chỉ derive tại chỗ.
        // Chế độ tốc độ dùng công thức khác (thưởng theo thời gian trả lời
        // từng câu ĐÚNG), chế độ thường dùng công thức cố định theo số câu đúng.
        val earnedXp = if (speedModeActive) {
            val correctTimes = _questions.value.indices
                .filter { qi -> isAnswerCorrect(_questions.value[qi], _answers.value.getOrElse(qi) { Answer.Empty }) }
                .mapNotNull { qi -> _answerTimesSec.value[qi] }
            xpForSpeedResult(correctTimes)
        } else {
            xpForResult(hs, ht)
        }
        val levelBefore = levelForXp(xpBeforeThisAttempt)
        val levelAfter = levelForXp(xpBeforeThisAttempt + earnedXp)
        _xpEarned.value = earnedXp
        if (levelAfter.level > levelBefore.level) {
            _levelUpEvent.value = levelAfter
        }

        val perQ = _questions.value.mapIndexed { qi, q ->
            val ans = _answers.value.getOrElse(qi) { Answer.Empty }
            PerQuestionResult(
                type = q.type,
                ok = isAnswerCorrect(q, ans),
                qText = q.displayText.take(120),
                correctAns = buildCorrectAnsText(q)
            )
        }

        onSaveHistoryCallback?.invoke(
            HistoryRecord(
                id = System.currentTimeMillis(),
                timestampIso = Date().toInstant().toString(),
                lessonTitle = lesson.title,
                score = hs,
                total = ht,
                pct = hpct,
                xpEarned = earnedXp
            ),
            perQ
        )

        clearSavedState()
        audio.playSubmit()

        viewModelScope.launch {
            delay(420)
            _scoreModalVisible.value = true
            val leveledUp = _levelUpEvent.value != null
            if (hpct >= 70 || leveledUp) {
                audio.playFanfare()
                _confettiOn.value = true
                // Level-up confetti kéo dài hơn (2.8s) so với confetti điểm cao
                // thường (2.2s) để cảm giác "sự kiện lớn hơn" — người chơi có
                // đủ thời gian đọc modal level-up trước khi confetti tắt.
                delay(if (leveledUp) 2800 else 2200)
                _confettiOn.value = false
            } else {
                audio.playSad()
            }
        }
    }

    fun closeScoreModal() { _scoreModalVisible.value = false }
    fun openStats() { _showStats.value = true }
    fun closeStats() { _showStats.value = false }

    fun resetQuiz() {
        submitGuard = false
        _answers.value = _questions.value.map { emptyAnswerFor(it) }
        _flags.value = _questions.value.map { false }
        _submitted.value = false
        _scoreModalVisible.value = false
        _showStats.value = false
        _cur.value = 0
        _streak.value = 0
        _bestStreak.value = 0
        _xpEarned.value = 0
        _levelUpEvent.value = null
        _answerTimesSec.value = emptyMap()
        if (lesson.timeLimitMinutes > 0) _timeLeft.value = lesson.timeLimitMinutes * 60
        qStartTimeMillis = System.currentTimeMillis()
        clearSavedState()
        startTimerIfNeeded()
    }

    // ═══════════════ Persistence (thay localStorage STORAGE_KEY) ═══════════════

    private fun persistState() {
        if (_submitted.value) return
        viewModelScope.launch {
            val answersJson = encodeAnswers(_answers.value)
            val state = SavedQuizState(answersJson, _cur.value, deadlineEpochMillis, _flags.value)
            dataStore.edit { it[stringPreferencesKey(storageKey)] = json.encodeToString(state) }
        }
    }

    private suspend fun loadSavedState(): SavedQuizState? {
        return try {
            val raw = dataStore.data.first()[stringPreferencesKey(storageKey)] ?: return null
            json.decodeFromString<SavedQuizState>(raw)
        } catch (e: Exception) {
            null
        }
    }

    private fun clearSavedState() {
        viewModelScope.launch {
            dataStore.edit { it.remove(stringPreferencesKey(storageKey)) }
        }
    }

    // answers cần custom encode vì Answer là sealed class không auto-serializable đơn giản qua Json thường
    private fun encodeAnswers(answers: List<Answer>): String {
        val simplified = answers.map { a ->
            when (a) {
                is Answer.Single -> listOf("single", a.index?.toString() ?: "")
                is Answer.Multi -> listOf("multi", a.indices.joinToString(","))
                is Answer.TrueFalseAnswer -> listOf("tf", a.values.joinToString(",") { it?.toString() ?: "null" })
                is Answer.Text -> listOf("text", a.value ?: "")
                Answer.Empty -> listOf("empty", "")
            }
        }
        return json.encodeToString(simplified)
    }

    private fun decodeAnswers(raw: String, questions: List<Question>): List<Answer> = try {
        val simplified: List<List<String>> = json.decodeFromString(raw)
        simplified.mapIndexed { i, pair ->
            val (tag, value) = pair
            when (tag) {
                "single" -> Answer.Single(value.toIntOrNull())
                "multi" -> Answer.Multi(if (value.isBlank()) emptyList() else value.split(",").map { it.toInt() })
                "tf" -> Answer.TrueFalseAnswer(value.split(",").map { if (it == "null") null else it.toBoolean() })
                "text" -> Answer.Text(value.ifBlank { null })
                else -> emptyAnswerFor(questions.getOrElse(i) { questions.first() })
            }
        }
    } catch (e: Exception) {
        questions.map { emptyAnswerFor(it) }
    }
}

/**
 * Sinh mô tả đáp án đúng dạng text, khớp format thật thấy trong Supabase:
 * true_false → "a:Đ b:S c:Đ d:S" (chữ cái từng ý + Đ/S)
 * fill_blank → đáp án đúng trực tiếp
 * multiple/multi_select → nối các lựa chọn đúng bằng dấu phẩy
 */
private fun buildCorrectAnsText(q: Question): String = when (q.type) {
    QuestionType.TRUE_FALSE -> q.items.mapIndexed { i, item ->
        "${('a' + i)}:${if (item.correct) "Đ" else "S"}"
    }.joinToString(" ")
    QuestionType.FILL_BLANK -> q.answer
    QuestionType.MULTIPLE -> q.correctIndex?.let { q.options.getOrNull(it) } ?: ""
    QuestionType.MULTI_SELECT -> q.correctIndices.mapNotNull { q.options.getOrNull(it) }.joinToString(", ")
}

data class PerQuestionResult(
    val type: QuestionType,
    val ok: Boolean,
    val partial: Boolean = false,
    val qText: String,
    val correctAns: String = "" // mô tả đáp án đúng dạng text, khớp cột per_q.correctAns trong Supabase
)

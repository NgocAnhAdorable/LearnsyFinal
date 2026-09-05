package com.learnsypro.app.ui.vocab

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.learnsypro.app.data.VocabCourse
import com.learnsypro.app.data.VocabRepository
import com.learnsypro.app.data.VocabUnit
import com.learnsypro.app.ui.listening.ListeningTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class VocabUiState(
    val loading: Boolean = true,
    val loadError: Boolean = false,
    val courses: List<VocabCourse> = emptyList(),
    val openCourseId: String? = null,
    val activeUnit: VocabUnit? = null,
    // unitId -> số từ đã thành thạo — tương đương mastery{} + localStorage
    // vmaster_* trong vocab-practice.jsx
    val mastery: Map<String, Int> = emptyMap()
)

/**
 * ── VocabViewModel ──
 * Tương đương state + effects của VocabPractice trong vocab-practice.jsx:
 * tải courses/units/words, điều hướng course→unit→learning, TTS phát âm,
 * và lưu tiến độ (mastery cục bộ qua SharedPreferences + vocab_progress
 * trên Supabase, tối đa 1 lần/ngày/unit).
 */
class VocabViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = VocabRepository()
    private val tts = ListeningTts(application)
    private val prefs = application.getSharedPreferences("vocab_mastery", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(VocabUiState())
    val uiState: StateFlow<VocabUiState> = _uiState.asStateFlow()

    init {
        loadCourses()
    }

    private fun loadCourses() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, loadError = false)
            try {
                val courses = repo.fetchCourses()
                // Nạp mastery cục bộ đã lưu từ trước cho mọi unit hiện có
                val masteryMap = mutableMapOf<String, Int>()
                courses.forEach { c ->
                    c.units.forEach { u ->
                        val saved = prefs.getInt("vmaster_${u.id}", -1)
                        if (saved >= 0) masteryMap[u.id] = saved.coerceAtMost(u.vocab.size)
                    }
                }
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    courses = courses,
                    mastery = masteryMap
                )
            } catch (e: Exception) {
                android.util.Log.e("VocabViewModel", "Không tải được danh sách từ vựng", e)
                _uiState.value = _uiState.value.copy(loading = false, loadError = true)
            }
        }
    }

    fun openCourse(courseId: String) {
        _uiState.value = _uiState.value.copy(openCourseId = courseId)
    }

    fun closeCourse() {
        _uiState.value = _uiState.value.copy(openCourseId = null)
    }

    fun pickUnit(unit: VocabUnit) {
        _uiState.value = _uiState.value.copy(activeUnit = unit)
    }

    fun exitLearning() {
        _uiState.value = _uiState.value.copy(activeUnit = null, openCourseId = null)
    }

    fun speak(word: String, rate: Float = 1f) {
        tts.speak(word, rate)
    }

    /**
     * Lưu tiến độ khi học sinh hoàn thành 1 unit — tương đương saveProgress()
     * trong vocab-practice.jsx. Mastery cục bộ luôn được cập nhật; lưu lên
     * Supabase (vocab_progress) chỉ 1 lần/ngày/unit, khớp hành vi cũ.
     */
    fun saveProgress(studentId: String?, unit: VocabUnit, learnedCount: Int) {
        // Cập nhật mastery cục bộ ngay — không phụ thuộc mạng
        prefs.edit().putInt("vmaster_${unit.id}", learnedCount).apply()
        _uiState.value = _uiState.value.copy(
            mastery = _uiState.value.mastery + (unit.id to learnedCount)
        )

        if (studentId.isNullOrBlank()) return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val saveKey = "vocabsave_${today}_${unit.id}"
        if (prefs.getBoolean(saveKey, false)) return // đã lưu hôm nay rồi

        viewModelScope.launch {
            try {
                repo.saveProgress(studentId, unit.id, learnedCount)
                prefs.edit().putBoolean(saveKey, true).apply()
            } catch (e: Exception) {
                android.util.Log.e("VocabViewModel", "Lưu tiến độ từ vựng thất bại", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        tts.shutdown()
    }
}

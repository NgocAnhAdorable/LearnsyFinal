package com.learnsypro.app.admin.ui

import androidx.lifecycle.ViewModel
import com.learnsypro.app.admin.data.Lesson
import com.learnsypro.app.admin.data.Question
import com.learnsypro.app.admin.data.stripHTML
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class MqTypeFilter { ALL, TRUE_FALSE, MULTIPLE_ISH, FILL_BLANK }

data class SelectedQuestion(val lessonId: String, val lessonTitle: String, val question: Question)

data class MergeQuestionsUiState(
    val search: String = "",
    val typeFilter: MqTypeFilter = MqTypeFilter.ALL,
    val expandedLessonIds: Set<String> = emptySet(),
    val selected: List<SelectedQuestion> = emptyList(),
    // Tương đương confirming/merging trong merge-questions.jsx — bước xác nhận
    // trước khi gộp, và trạng thái loading trong lúc onMerge() đang chạy.
    val confirming: Boolean = false,
    val merging: Boolean = false
)

// Tương đương MergeQuestionsModal trong merge-questions.css (+ logic gốc suy ra từ CSS
// class: search, filter theo loại ĐS/TN/ĐT, expand từng bài, chọn lẻ câu hỏi, preview chip).
// excludeLessonId = bài đang soạn, không hiện trong danh sách nguồn để tránh tự gộp vào chính nó.
class MergeQuestionsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MergeQuestionsUiState())
    val uiState: StateFlow<MergeQuestionsUiState> = _uiState.asStateFlow()

    fun reset() {
        _uiState.value = MergeQuestionsUiState()
    }

    fun setSearch(q: String) = _uiState.update { it.copy(search = q) }
    fun setTypeFilter(f: MqTypeFilter) = _uiState.update { it.copy(typeFilter = f) }

    fun toggleExpand(lessonId: String) = _uiState.update {
        val s = it.expandedLessonIds.toMutableSet()
        if (lessonId in s) s.remove(lessonId) else s.add(lessonId)
        it.copy(expandedLessonIds = s)
    }

    fun matchingLessons(allLessons: List<Lesson>, excludeLessonId: String?): List<Lesson> {
        val s = _uiState.value
        return allLessons
            .filter { it.id != excludeLessonId && it.questions.isNotEmpty() }
            .filter { lesson ->
                if (s.search.isBlank()) true
                else lesson.title.contains(s.search, ignoreCase = true) ||
                    matchingQuestionsOf(lesson).isNotEmpty()
            }
    }

    fun matchingQuestionsOf(lesson: Lesson): List<Question> {
        val s = _uiState.value
        return lesson.questions.filter { q ->
            val typeOk = when (s.typeFilter) {
                MqTypeFilter.ALL -> true
                MqTypeFilter.TRUE_FALSE -> q is Question.TrueFalse
                MqTypeFilter.MULTIPLE_ISH -> q is Question.Multiple || q is Question.MultiSelect
                MqTypeFilter.FILL_BLANK -> q is Question.FillBlank
            }
            val searchOk = s.search.isBlank() || questionPreviewText(q).contains(s.search, ignoreCase = true)
            typeOk && searchOk
        }
    }

    fun isSelected(question: Question): Boolean = _uiState.value.selected.any { it.question.id == question.id }

    fun toggleSelect(lesson: Lesson, question: Question) {
        _uiState.update {
            val exists = it.selected.any { s -> s.question.id == question.id }
            val next = if (exists) it.selected.filter { s -> s.question.id != question.id }
            else it.selected + SelectedQuestion(lesson.id, lesson.title, question)
            it.copy(selected = next)
        }
    }

    fun toggleSelectAllInLesson(lesson: Lesson) {
        val visible = matchingQuestionsOf(lesson)
        val allSelected = visible.isNotEmpty() && visible.all { q -> isSelected(q) }
        _uiState.update { st ->
            val withoutThisLesson = st.selected.filter { it.lessonId != lesson.id || visible.none { v -> v.id == it.question.id } }
            val next = if (allSelected) withoutThisLesson
            else withoutThisLesson + visible.map { SelectedQuestion(lesson.id, lesson.title, it) }
            st.copy(selected = next)
        }
    }

    fun removeSelected(questionId: String) {
        _uiState.update { it.copy(selected = it.selected.filter { s -> s.question.id != questionId }) }
    }

    // Tương đương totalAvailable trong merge-questions.jsx — tổng câu hỏi khớp bộ lọc
    // hiện tại trên toàn bộ các bài (không chỉ bài đang mở rộng), dùng cho "Đã chọn X/Y câu".
    fun totalAvailable(allLessons: List<Lesson>, excludeLessonId: String?): Int =
        matchingLessons(allLessons, excludeLessonId).sumOf { matchingQuestionsOf(it).size }

    fun askConfirm() {
        if (_uiState.value.selected.isEmpty() || _uiState.value.merging) return
        _uiState.update { it.copy(confirming = true) }
    }

    fun cancelConfirm() {
        _uiState.update { it.copy(confirming = false) }
    }

    // Tương đương doMerge() trong merge-questions.jsx — mỗi câu hỏi được gộp nhận
    // id mới (giống 'mq'+Date.now()+Math.random() bên web) để không trùng id với
    // câu hỏi đã tồn tại sẵn trong bài đích khi 2 bài từng share chung câu hỏi gốc.
    fun mergedQuestions(): List<Question> = _uiState.value.selected.map { withFreshId(it.question) }

    private fun withFreshId(q: Question): Question = when (q) {
        is Question.TrueFalse -> q.copy(id = Question.newId())
        is Question.Multiple -> q.copy(id = Question.newId())
        is Question.MultiSelect -> q.copy(id = Question.newId())
        is Question.FillBlank -> q.copy(id = Question.newId())
    }

    fun setMerging(value: Boolean) = _uiState.update { it.copy(merging = value, confirming = false) }
}

fun questionPreviewText(q: Question): String = when (q) {
    is Question.TrueFalse -> stripHTML(q.passage)
    is Question.Multiple -> stripHTML(q.question)
    is Question.MultiSelect -> stripHTML(q.question)
    is Question.FillBlank -> stripHTML(q.question)
}

fun questionTypeShort(q: Question): String = when (q) {
    is Question.TrueFalse -> "ĐS"
    is Question.Multiple -> "TN"
    is Question.MultiSelect -> "CN"
    is Question.FillBlank -> "ĐT"
}

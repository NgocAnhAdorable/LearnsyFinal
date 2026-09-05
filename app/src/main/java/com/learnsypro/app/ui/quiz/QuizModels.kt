package com.learnsypro.app.ui.quiz

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

@Serializable
enum class QuestionType { MULTIPLE, MULTI_SELECT, TRUE_FALSE, FILL_BLANK }

@Serializable
data class TrueFalseItem(val text: String, val correct: Boolean)

@Serializable
data class Question(
    val id: Double,
    val type: QuestionType,
    val text: String = "",           // dùng cho multiple/multi_select
    val question: String = "",       // dùng cho fill_blank (tên field khác trong DB)
    val hint: String = "",           // fill_blank
    val options: List<String> = emptyList(),
    val correctIndex: Int? = null,
    val correctIndices: List<Int> = emptyList(),
    val items: List<TrueFalseItem> = emptyList(),
    val passage: String = "",        // true_false — đoạn tư liệu
    val source: String = "",         // true_false — nguồn trích dẫn
    val answer: String = "",         // fill_blank — MỘT đáp án đúng (không phải list)
    val explanation: String? = null
) {
    /** Text hiển thị chính, tự chọn đúng field theo loại câu hỏi. */
    val displayText: String get() = if (type == QuestionType.FILL_BLANK) question else text
}

@Serializable
data class Lesson(
    val id: String = "",
    val title: String,
    val subject: String? = null,
    val questions: List<Question>,
    val timeLimitMinutes: Int = 0,
    val password: String? = null
) {
    val questionCount: Int get() = questions.size
}

/**
 * Trả về bản Lesson đã xáo trộn thứ tự câu hỏi (shuffleQuestions) và/hoặc
 * thứ tự đáp án trong từng câu (shuffleAnswers), tương đương 2 toggle
 * "Xáo câu hỏi" / "Xáo đáp án" trong Cài đặt Dashboard.
 *
 * Quan trọng: khi xáo options của MULTIPLE/MULTI_SELECT, correctIndex và
 * correctIndices phải được ánh xạ lại theo vị trí mới — nếu không đáp án
 * đúng sẽ trỏ sai chỗ sau khi trộn. TRUE_FALSE chỉ cần xáo list items vì
 * "correct" đi kèm ngay trong từng item. FILL_BLANK không có gì để xáo.
 */
fun Lesson.shuffled(shuffleQuestions: Boolean, shuffleAnswers: Boolean): Lesson {
    if (!shuffleQuestions && !shuffleAnswers) return this

    val newQuestions = questions.map { q ->
        if (!shuffleAnswers) return@map q
        when (q.type) {
            QuestionType.MULTIPLE -> {
                val order = q.options.indices.shuffled()
                val newOptions = order.map { q.options[it] }
                val newCorrectIndex = q.correctIndex?.let { order.indexOf(it) }
                q.copy(options = newOptions, correctIndex = newCorrectIndex)
            }
            QuestionType.MULTI_SELECT -> {
                val order = q.options.indices.shuffled()
                val newOptions = order.map { q.options[it] }
                val correctSet = q.correctIndices.toSet()
                val newCorrectIndices = order.mapIndexedNotNull { newIdx, oldIdx ->
                    if (oldIdx in correctSet) newIdx else null
                }
                q.copy(options = newOptions, correctIndices = newCorrectIndices)
            }
            QuestionType.TRUE_FALSE -> q.copy(items = q.items.shuffled())
            QuestionType.FILL_BLANK -> q
        }
    }

    return copy(questions = if (shuffleQuestions) newQuestions.shuffled() else newQuestions)
}

sealed class Answer {
    object Empty : Answer()
    data class Single(val index: Int?) : Answer()
    data class Multi(val indices: List<Int>) : Answer()
    data class TrueFalseAnswer(val values: List<Boolean?>) : Answer()
    data class Text(val value: String?) : Answer()
}

fun emptyAnswerFor(q: Question): Answer = when (q.type) {
    QuestionType.TRUE_FALSE -> Answer.TrueFalseAnswer(q.items.map { null as Boolean? })
    QuestionType.MULTI_SELECT -> Answer.Multi(emptyList())
    QuestionType.FILL_BLANK -> Answer.Text(null)
    QuestionType.MULTIPLE -> Answer.Single(null)
}

fun isAnswerCorrect(q: Question, answer: Answer): Boolean {
    return when (q.type) {
        QuestionType.MULTIPLE -> {
            val a = answer as? Answer.Single
            a?.index != null && a.index == q.correctIndex
        }
        QuestionType.MULTI_SELECT -> {
            val a = answer as? Answer.Multi ?: return false
            a.indices.toSet() == q.correctIndices.toSet() && a.indices.isNotEmpty()
        }
        QuestionType.TRUE_FALSE -> {
            val a = answer as? Answer.TrueFalseAnswer ?: return false
            a.values.size == q.items.size &&
                a.values.indices.all { i -> a.values[i] != null && a.values[i] == q.items[i].correct }
        }
        QuestionType.FILL_BLANK -> {
            val a = answer as? Answer.Text ?: return false
            val userText = a.value?.trim()?.lowercase().orEmpty()
            userText.isNotEmpty() && q.answer.trim().lowercase() == userText
        }
    }
}

fun computeScore(questions: List<Question>, answers: List<Answer>): Pair<Int, Int> {
    var s = 0
    val t = questions.size
    questions.forEachIndexed { i, q ->
        val a = answers.getOrNull(i) ?: Answer.Empty
        if (isAnswerCorrect(q, a)) s++
    }
    return s to t
}

fun fmtS(score: Double): String {
    val rounded = Math.round(score * 10) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else "%.1f".format(rounded)
}

data class QuizColors(
    val text: Color,
    val text2: Color,
    val textMid: Color,
    val surface: Color,
    val surfaceQ: Color,
    val border: Color,
    val borderQ: Color,
    val optBg: Color,
    val optBorder: Color,
    val optSel: Color,
    val navBtn: Color,
    val navBtnBorder: Color,
    val navBtnText: Color,
    val stickyBg: Color,
    val stickyBorder: Color,
    val headerBg: Color,
    val progressBg: Color,
    val tfPassageBg: Color,
    // Chữ phụ (placeholder "...", nhãn "0.8x/1.2x", thời lượng audio...) cần
    // tương phản tốt hơn textMid trên nền tím — đây là bản sáng hơn dùng riêng
    // cho các chữ nhỏ dễ bị chìm theo feedback UI.
    val textMuted: Color
)

fun quizColors(dark: Boolean): QuizColors = if (dark) {
    QuizColors(
        text = Color(0xFFF2EAFF),
        text2 = Color(0xFFDDD0F8),
        textMid = Color(0xFF9B7FC0),
        surface = Color(0x0DFFFFFF),
        surfaceQ = Color(0x0FFFFFFF),
        border = Color(0x21C4B5FD),
        borderQ = Color(0x2EC4B5FD),
        optBg = Color(0x0BFFFFFF),
        optBorder = Color(0x2EC4B5FD),
        optSel = Color(0x24C4B5FD),
        navBtn = Color(0x12FF96C8),
        navBtnBorder = Color(0x47FF96C8),
        navBtnText = Color(0xFFFBAFCE),
        stickyBg = Color(0xF50F0225),
        stickyBorder = Color(0x1FC4B5FD),
        headerBg = Color(0x09FFFFFF),
        progressBg = Color(0x1FFF96C8),
        tfPassageBg = Color(0x14B07CF0),
        textMuted = Color(0xFFC9B8E8)
    )
} else {
    QuizColors(
        text = Color(0xFF2D1245),
        text2 = Color(0xFF4A1860),
        textMid = Color(0xFF8060A0),
        surface = Color(0xD1FFFFFF),
        surfaceQ = Color(0xEBFFFFFF),
        border = Color(0x21B464FF),
        borderQ = Color(0x2EB464FF),
        optBg = Color(0xBFFFFFFF),
        optBorder = Color(0x2EB464FF),
        optSel = Color(0x1AB464FF),
        navBtn = Color(0x0FFF6B95),
        navBtnBorder = Color(0x47FF6B95),
        navBtnText = Color(0xFFE8547A),
        stickyBg = Color(0xF5FFF5FC),
        stickyBorder = Color(0x1AB464FF),
        headerBg = Color(0xBFFFFFFF),
        progressBg = Color(0x1AB464FF),
        tfPassageBg = Color(0x0FB07CF0),
        textMuted = Color(0xFF6B4894)
    )
}

fun resultColor(pct: Double): Color = when {
    pct >= 0.8 -> Color(0xFF10B981)
    pct >= 0.5 -> Color(0xFFF59E0B)
    else -> Color(0xFFEF4444)
}

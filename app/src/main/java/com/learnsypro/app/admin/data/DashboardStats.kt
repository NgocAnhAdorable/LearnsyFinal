package com.learnsypro.app.admin.data

import java.time.LocalDate
import java.time.YearMonth

data class MonthlyCount(val label: String, val count: Int)

data class QuestionTypeCounts(
    val trueFalse: Int = 0,
    val multiple: Int = 0,
    val multiSelect: Int = 0,
    val fillBlank: Int = 0
)

data class DashboardStats(
    val total: Int,
    val totalQ: Int,
    val subjects: Map<String, Int>,
    val monthly: List<MonthlyCount>,
    val types: QuestionTypeCounts
)

// ── Insight tự động: học sinh chưa làm bài trong 7 ngày gần nhất ──
data class InactiveStudentGroup(val className: String, val inactiveCount: Int, val totalCount: Int)

/**
 * Với mỗi lớp (chỉ tính học sinh is_active=true), đếm bao nhiêu em KHÔNG có
 * quiz_results nào trong `windowDays` ngày gần nhất — dựa trên created_at
 * của quiz_results (đã có sẵn, không cần thêm cột "last_active" trên bảng
 * students). Chỉ trả về các lớp CÓ học sinh chưa làm bài (inactiveCount > 0)
 * — lớp mà ai cũng làm bài đầy đủ thì không cần hiện, tránh nhiễu insight.
 */
fun buildInactiveInsight(
    students: List<Student>,
    results: List<QuizResult>,
    windowDays: Long = 7
): List<InactiveStudentGroup> {
    val cutoff = java.time.Instant.now().minusSeconds(windowDays * 24 * 3600)
    val activeStudentIds = results.mapNotNull { r ->
        val createdAt = r.createdAt?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() }
        if (createdAt != null && createdAt.isAfter(cutoff)) r.studentId else null
    }.toSet()

    return students
        .filter { it.isActive }
        .groupBy { it.className.ifBlank { "Chưa xếp lớp" } }
        .map { (className, group) ->
            val inactive = group.count { it.id !in activeStudentIds }
            InactiveStudentGroup(className, inactive, group.size)
        }
        .filter { it.inactiveCount > 0 }
        .sortedByDescending { it.inactiveCount }
}

// Tương đương buildStats() trong dashboard.jsx
fun buildStats(lessons: List<Lesson>): DashboardStats {
    val total = lessons.size
    val totalQ = lessons.sumOf { it.questions.size }

    val subjects = mutableMapOf<String, Int>()
    lessons.forEach { l ->
        val s = l.subject.ifBlank { "Khác" }
        subjects[s] = (subjects[s] ?: 0) + 1
    }

    // 6 tháng gần nhất, tương đương vòng lặp i=5..0
    val now = YearMonth.now()
    val monthly = (5 downTo 0).map { i ->
        val ym = now.minusMonths(i.toLong())
        val label = "T${ym.monthValue}"
        val count = lessons.count { l ->
            val created = parseCreatedAt(l.createdAt)
            created != null && created.year == ym.year && created.monthValue == ym.monthValue
        }
        MonthlyCount(label, count)
    }

    var tf = 0; var mc = 0; var ms = 0; var fb = 0
    lessons.forEach { l ->
        l.questions.forEach { q ->
            when (q) {
                is Question.TrueFalse -> tf++
                is Question.Multiple -> mc++
                is Question.MultiSelect -> ms++
                is Question.FillBlank -> fb++
            }
        }
    }

    return DashboardStats(total, totalQ, subjects, monthly, QuestionTypeCounts(tf, mc, ms, fb))
}

private fun parseCreatedAt(raw: String): LocalDate? {
    if (raw.isBlank()) return null
    return runCatching {
        java.time.Instant.parse(raw).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    }.getOrElse {
        runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()
    }
}

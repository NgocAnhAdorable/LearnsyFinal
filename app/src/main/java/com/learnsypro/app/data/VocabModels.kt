package com.learnsypro.app.data

import kotlinx.serialization.Serializable

/**
 * ── VocabWord ──
 * 1 dòng bảng `vocab_words` — tương đương 1 mục "vocab" trong
 * vocab-practice.jsx (word, pos, ipa, meaning, example).
 */
@Serializable
data class VocabWord(
    val id: String,
    val unit_id: String,
    val word: String,
    val pos: String? = null,
    val ipa: String? = null,
    val meaning: String? = null,
    val example: String? = null,
    val sort_order: Int = 0
)

/**
 * ── VocabUnit ──
 * 1 dòng bảng `vocab_units`, kèm danh sách từ vựng của unit đó (được nối
 * ở tầng repository, không phải cột thật trong Supabase — giống cách
 * unitsByCourse/wordsByUnit gộp lại trong vocab-practice.jsx).
 */
@Serializable
data class VocabUnit(
    val id: String,
    val course_id: String,
    val title: String,
    val level: String? = null,
    val sort_order: Int = 0,
    val vocab: List<VocabWord> = emptyList()
)

/**
 * ── VocabCourse ──
 * 1 dòng bảng `vocab_courses`, kèm danh sách unit của khoá học đó.
 */
@Serializable
data class VocabCourse(
    val id: String,
    val title: String,
    val description: String? = null,
    val sort_order: Int = 0,
    val units: List<VocabUnit> = emptyList()
)

/** Payload gửi lên bảng vocab_progress khi học sinh hoàn thành 1 unit. */
@Serializable
data class VocabProgressInsert(
    val id: String,
    val student_id: String,
    val unit_id: String,
    val date: String,
    val vocab_count: Int
)

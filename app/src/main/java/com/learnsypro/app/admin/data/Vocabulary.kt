package com.learnsypro.app.admin.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

// Tương đương vocab_courses / vocab_units / vocab_words trong
// vocabulary-manager.jsx (admin web) — module "Từ vựng" độc lập với lessons.

// FIX: createdAt mặc định "" và KHÔNG có @Transient khiến insert() gửi
// created_at:"" lên Supabase thay vì để cột timestamptz tự nhận giá trị
// mặc định now() — Postgres báo "invalid input syntax for type timestamp
// with time zone: \"\"" mỗi khi tạo mới bài học/Unit/từ vựng. Đánh dấu
// @Transient để field chỉ tồn tại phía client (đọc về hiển thị), không
// bao giờ được serialize gửi đi lúc insert/update.
@Serializable
data class VocabCourse(
    val id: String,
    val title: String = "",
    val description: String = "",
    @SerialName("sort_order") val sortOrder: Int = 0,
    @Transient @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class VocabUnit(
    val id: String,
    @SerialName("course_id") val courseId: String,
    val title: String = "",
    val level: String = "",
    @SerialName("sort_order") val sortOrder: Int = 0,
    @Transient @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class VocabWord(
    val id: String,
    @SerialName("unit_id") val unitId: String,
    val word: String = "",
    val pos: String = "noun",
    val ipa: String = "",
    val meaning: String = "",
    val example: String = "",
    @SerialName("sort_order") val sortOrder: Int = 0,
    @Transient @SerialName("created_at") val createdAt: String = ""
)

// Tương đương POS_OPTIONS/POS_COLORS trong vocabulary-manager.jsx
data class PosOption(val value: String, val label: String, val short: String)

val POS_OPTIONS = listOf(
    PosOption("noun", "Danh từ", "n."),
    PosOption("verb", "Động từ", "v."),
    PosOption("adjective", "Tính từ", "adj."),
    PosOption("adverb", "Trạng từ", "adv."),
    PosOption("pronoun", "Đại từ", "pron."),
    PosOption("preposition", "Giới từ", "prep."),
    PosOption("conjunction", "Liên từ", "conj."),
    PosOption("interjection", "Thán từ", "interj.")
)

fun posLabel(pos: String): String = POS_OPTIONS.find { it.value == pos }?.short ?: pos

fun posColor(pos: String): androidx.compose.ui.graphics.Color = when (pos) {
    "noun" -> androidx.compose.ui.graphics.Color(0xFF3B82F6)
    "verb" -> androidx.compose.ui.graphics.Color(0xFFEF4444)
    "adjective" -> androidx.compose.ui.graphics.Color(0xFFF59E0B)
    "adverb" -> androidx.compose.ui.graphics.Color(0xFF10B981)
    "pronoun" -> androidx.compose.ui.graphics.Color(0xFFA855F7)
    "preposition" -> androidx.compose.ui.graphics.Color(0xFF06B6D4)
    "conjunction" -> androidx.compose.ui.graphics.Color(0xFFF97316)
    "interjection" -> androidx.compose.ui.graphics.Color(0xFFEC4899)
    else -> androidx.compose.ui.graphics.Color(0xFF9CA3AF)
}

// Tương đương POS_ALIASES/parsePos trong vocabulary-manager.jsx — nhận diện
// loại từ viết tắt/tiếng Việt lúc nhập nhanh nhiều từ (VD: "dt" -> noun)
private val POS_ALIASES = mapOf(
    "n" to "noun", "noun" to "noun", "dt" to "noun", "danh từ" to "noun", "danh tu" to "noun",
    "v" to "verb", "verb" to "verb", "đt" to "verb", "động từ" to "verb", "dong tu" to "verb",
    "adj" to "adjective", "adjective" to "adjective", "tt" to "adjective", "tính từ" to "adjective", "tinh tu" to "adjective",
    "adv" to "adverb", "adverb" to "adverb", "trt" to "adverb", "trạng từ" to "adverb", "trang tu" to "adverb",
    "pron" to "pronoun", "pronoun" to "pronoun", "dait" to "pronoun", "đại từ" to "pronoun", "dai tu" to "pronoun",
    "prep" to "preposition", "preposition" to "preposition", "gt" to "preposition", "giới từ" to "preposition", "gioi tu" to "preposition",
    "conj" to "conjunction", "conjunction" to "conjunction", "lt" to "conjunction", "liên từ" to "conjunction", "lien tu" to "conjunction",
    "interj" to "interjection", "interjection" to "interjection", "tht" to "interjection", "thán từ" to "interjection", "than tu" to "interjection"
)

fun parsePos(raw: String?): String {
    val key = raw?.trim()?.lowercase() ?: ""
    if (key.isEmpty()) return "noun"
    return POS_ALIASES[key] ?: "noun"
}

// Tương đương 1 dòng đã parse trong BulkWordModal (nhập nhanh nhiều từ)
data class ParsedWordLine(
    val word: String,
    val pos: String,
    val ipa: String,
    val meaning: String,
    val example: String
)

// Tương đương phần parse trong BulkWordModal: mỗi dòng "từ | loại từ | phiên âm | nghĩa | ví dụ"
fun parseBulkWords(text: String): List<ParsedWordLine> =
    text.split("\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val parts = line.split("|").map { it.trim() }
            val word = parts.getOrNull(0) ?: ""
            if (word.isBlank()) return@mapNotNull null
            ParsedWordLine(
                word = word,
                pos = parsePos(parts.getOrNull(1)),
                ipa = parts.getOrNull(2) ?: "",
                meaning = parts.getOrNull(3) ?: "",
                example = parts.getOrNull(4) ?: ""
            )
        }

fun genVocabId(): String = java.util.UUID.randomUUID().toString()

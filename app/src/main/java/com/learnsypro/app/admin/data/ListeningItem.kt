package com.learnsypro.app.admin.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Tương đương True/False/Not Mentioned trong listening-panel.jsx
enum class TfnmAnswer(val label: String, val stored: String) {
    TRUE("Đúng", "True"),
    FALSE("Sai", "False"),
    NOT_MENTIONED("NM", "Not Mentioned");

    companion object {
        fun fromStored(s: String): TfnmAnswer = when (s) {
            "True" -> TRUE
            "False" -> FALSE
            else -> NOT_MENTIONED
        }
    }
}

@Serializable
data class ListeningStatement(
    val statement: String = "",
    val answer: String = "True" // "True" | "False" | "Not Mentioned"
)

// Tương đương 1 row trong bảng listening_items (độc lập, không thuộc lessons)
@Serializable
data class ListeningItem(
    val id: String,
    val text: String = "",
    @SerialName("word_box") val wordBox: List<String> = emptyList(),
    val answers: List<String> = emptyList(),
    val statements: List<ListeningStatement> = emptyList(),
    @SerialName("shuffle_statements") val shuffleStatements: Boolean = false,
    @SerialName("shuffle_word_box") val shuffleWordBox: Boolean = false,
    @SerialName("sort_order") val sortOrder: Int = 0,
    val tags: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String = ""
)

// Tương đương stripHTML/cleanStr/countBlanks/genId trong listening-panel.jsx
fun countBlanks(text: String): Int = Regex("_{3,}").findAll(text).count()

fun cleanListeningStr(s: String): String =
    s.replace(Regex("[\u200B-\u200D\uFEFF\u00A0]"), " ").replace(Regex("[ \t]+"), " ").trim()

fun genListeningId(): String = "ls${System.currentTimeMillis()}${(0..99999).random()}"

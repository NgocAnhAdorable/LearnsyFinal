package com.learnsypro.app.ui.listening

import kotlinx.serialization.Serializable

sealed class PassagePart {
    data class TextPart(val content: String) : PassagePart()
    data class BlankPart(val index: Int) : PassagePart()
}

fun stripHtml(s: String?): String = (s ?: "").replace(Regex("<[^>]*>"), "")

fun normAnswer(s: String?): String = (s ?: "").trim().lowercase()

/**
 * ── splitPassage ──
 * Tương đương function splitPassage(text, blankCount) trong listening-practice.jsx.
 */
fun splitPassage(text: String, blankCount: Int): List<PassagePart> {
    val raw = stripHtml(text)
    val delimiterRegex = Regex("_{3,}|▁{3,}")
    val result = mutableListOf<PassagePart>()
    var blankIndex = 0
    var lastEnd = 0

    delimiterRegex.findAll(raw).forEach { match ->
        val textChunk = raw.substring(lastEnd, match.range.first)
        if (textChunk.isNotEmpty()) result.add(PassagePart.TextPart(textChunk))
        if (blankIndex < blankCount) {
            result.add(PassagePart.BlankPart(blankIndex++))
        } else {
            result.add(PassagePart.TextPart(match.value))
        }
        lastEnd = match.range.last + 1
    }
    if (lastEnd < raw.length) {
        result.add(PassagePart.TextPart(raw.substring(lastEnd)))
    }

    if (blankIndex == 0 && blankCount > 0) {
        val sentenceRegex = Regex("([.!?]\\s+)")
        val newResult = mutableListOf<PassagePart>()
        var cnt = 0
        var pos = 0

        fun appendChunk(chunk: String) {
            if (chunk.isNotEmpty() && cnt < blankCount) {
                val words = chunk.trim().split(" ").filter { it.isNotBlank() }
                if (words.size > 4) {
                    val mid = words.size / 2
                    newResult.add(PassagePart.TextPart(words.take(mid).joinToString(" ") + " "))
                    newResult.add(PassagePart.BlankPart(cnt++))
                    newResult.add(PassagePart.TextPart(" " + words.drop(mid).joinToString(" ")))
                } else {
                    newResult.add(PassagePart.TextPart(chunk + " "))
                    newResult.add(PassagePart.BlankPart(cnt++))
                }
            } else if (chunk.isNotEmpty()) {
                newResult.add(PassagePart.TextPart(chunk))
            }
        }

        sentenceRegex.findAll(raw).forEach { m ->
            appendChunk(raw.substring(pos, m.range.first))
            newResult.add(PassagePart.TextPart(m.value))
            pos = m.range.last + 1
        }
        if (pos < raw.length) appendChunk(raw.substring(pos))
        return newResult
    }

    return result
}

fun <T> shuffleList(list: List<T>): List<T> {
    val a = list.toMutableList()
    for (i in a.size - 1 downTo 1) {
        val j = (0..i).random()
        val tmp = a[i]; a[i] = a[j]; a[j] = tmp
    }
    return a
}

@Serializable
enum class StatementAnswer { TRUE, FALSE, NOT_MENTIONED }

@Serializable
data class Statement(val text: String, val answer: StatementAnswer)

@Serializable
data class ListeningItem(
    val id: String,
    val text: String,
    val wordBox: List<String> = emptyList(),
    val answers: List<String> = emptyList(),
    val statements: List<Statement> = emptyList(),
    val shuffleStatements: Boolean = false,
    val shuffleWordBox: Boolean = false
)

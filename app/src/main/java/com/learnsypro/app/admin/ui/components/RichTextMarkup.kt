package com.learnsypro.app.admin.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

// Chuyển đổi HTML đơn giản (<b>/<strong>, <i>/<em>, <u>, <s>/<strike>) mà bản web
// lưu trong Supabase (execCommand output) sang AnnotatedString để hiển thị/soạn
// trong Compose, và ngược lại — giữ tương thích dữ liệu 2 chiều giữa web & native.
object RichTextMarkup {

    private val TAG_REGEX = Regex("<(/?)(b|strong|i|em|u|s|strike|br|div|p)[^>]*>", RegexOption.IGNORE_CASE)

    // HTML (từ web) → AnnotatedString
    fun htmlToAnnotated(html: String): AnnotatedString {
        if (html.isBlank()) return AnnotatedString("")
        return buildAnnotatedString {
            var lastEnd = 0
            // Stack các (tagName) đang mở, dùng pushStyle/pop đúng thứ tự LIFO như push/pop chuẩn.
            TAG_REGEX.findAll(html).forEach { m ->
                val textChunk = decodeEntities(html.substring(lastEnd, m.range.first))
                if (textChunk.isNotEmpty()) append(textChunk)
                lastEnd = m.range.last + 1

                val closing = m.groupValues[1] == "/"
                val tag = m.groupValues[2].lowercase()
                val style = styleForTag(tag) ?: run {
                    if (tag == "br") append("\n")
                    if ((tag == "div" || tag == "p") && closing && length > 0) append("\n")
                    return@forEach
                }
                if (!closing) pushStyle(style) else pop()
            }
            val tail = decodeEntities(html.substring(lastEnd))
            if (tail.isNotEmpty()) append(tail)
        }
    }

    private fun styleForTag(tag: String): SpanStyle? = when (tag) {
        "b", "strong" -> SpanStyle(fontWeight = FontWeight.Black)
        "i", "em" -> SpanStyle(fontStyle = FontStyle.Italic)
        "u" -> SpanStyle(textDecoration = TextDecoration.Underline)
        "s", "strike" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        else -> null
    }

    // AnnotatedString (đã soạn trong Compose) → HTML (để lưu lên Supabase, tương thích web)
    fun annotatedToHtml(text: AnnotatedString): String {
        if (text.text.isEmpty()) return ""
        val sb = StringBuilder()
        val boundaries = sortedSetOf(0, text.text.length)
        text.spanStyles.forEach { boundaries.add(it.start); boundaries.add(it.end) }
        val sorted = boundaries.toList()
        for (idx in 0 until sorted.size - 1) {
            val start = sorted[idx]; val end = sorted[idx + 1]
            if (start >= end) continue
            val stylesHere = text.spanStyles.filter { it.start <= start && it.end >= end }.map { it.item }
            var open = ""
            var close = ""
            if (stylesHere.any { it.fontWeight == FontWeight.Black || it.fontWeight == FontWeight.Bold }) {
                open += "<b>"; close = "</b>$close"
            }
            if (stylesHere.any { it.fontStyle == FontStyle.Italic }) {
                open += "<i>"; close = "</i>$close"
            }
            if (stylesHere.any { it.textDecoration == TextDecoration.Underline }) {
                open += "<u>"; close = "</u>$close"
            }
            if (stylesHere.any { it.textDecoration == TextDecoration.LineThrough }) {
                open += "<s>"; close = "</s>$close"
            }
            sb.append(open)
            sb.append(encodeEntities(text.text.substring(start, end)))
            sb.append(close)
        }
        return sb.toString()
    }

    fun stripToPlainText(html: String): String =
        decodeEntities(html.replace(Regex("<[^>]*>"), ""))

    private fun decodeEntities(s: String): String =
        s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")

    private fun encodeEntities(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

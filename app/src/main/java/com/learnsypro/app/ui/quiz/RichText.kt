package com.learnsypro.app.ui.quiz

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * ── richText ──
 * Admin (web) lưu nội dung câu hỏi/đoạn tư liệu dạng HTML inline đơn giản
 * (giống contenteditable của trình duyệt), ví dụ:
 *   <span style="font-weight: normal;">A</span>
 *   <b>đậm</b> <u>gạch chân</u> <strike>gạch ngang</strike>
 * Compose Text() không tự parse HTML như innerHTML của web, nên cần convert
 * thủ công sang AnnotatedString để giữ đúng định dạng thay vì in ra tag thô.
 *
 * Chỉ hỗ trợ các tag phẳng, không lồng phức tạp — đủ cho dữ liệu admin xuất
 * ra (bôi đen rồi bấm B/I/U/S). Nếu gặp tag lạ, bỏ qua tag và giữ nội dung.
 */
fun richText(raw: String): AnnotatedString {
    val text = raw
    val builder = AnnotatedString.Builder()

    data class OpenTag(val name: String, val style: SpanStyle)
    val stack = ArrayDeque<OpenTag>()

    val tagRegex = Regex("""<(/?)([a-zA-Z]+)([^>]*)>""")
    var lastIndex = 0

    fun styleFor(tag: String, attrs: String): SpanStyle? = when (tag.lowercase()) {
        "b", "strong" -> SpanStyle(fontWeight = FontWeight.Bold)
        "i", "em" -> SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        "u" -> SpanStyle(textDecoration = TextDecoration.Underline)
        "s", "strike", "del" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        "span" -> {
            val isBold = Regex("""font-weight\s*:\s*(bold|[6-9]00)""", RegexOption.IGNORE_CASE)
                .containsMatchIn(attrs)
            val isUnderline = Regex("""text-decoration\s*:\s*underline""", RegexOption.IGNORE_CASE)
                .containsMatchIn(attrs)
            val isStrike = Regex("""text-decoration\s*:\s*line-through""", RegexOption.IGNORE_CASE)
                .containsMatchIn(attrs)
            when {
                isBold && isUnderline -> SpanStyle(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)
                isBold && isStrike -> SpanStyle(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.LineThrough)
                isBold -> SpanStyle(fontWeight = FontWeight.Bold)
                isUnderline -> SpanStyle(textDecoration = TextDecoration.Underline)
                isStrike -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                else -> SpanStyle() // span không style đặc biệt (vd font-weight: normal) — không đổi gì
            }
        }
        else -> null
    }

    for (match in tagRegex.findAll(text)) {
        val plain = text.substring(lastIndex, match.range.first)
        if (plain.isNotEmpty()) builder.append(unescapeHtmlEntities(plain))
        lastIndex = match.range.last + 1

        val isClosing = match.groupValues[1] == "/"
        val tagName = match.groupValues[2]
        val attrs = match.groupValues[3]

        if (!isClosing) {
            val style = styleFor(tagName, attrs)
            if (style != null) {
                builder.pushStyle(style)
                stack.addLast(OpenTag(tagName.lowercase(), style))
            }
        } else {
            // Đóng đúng tag gần nhất cùng tên (bỏ qua nếu không khớp — dữ liệu có thể lệch tag)
            val idx = stack.indexOfLast { it.name == tagName.lowercase() }
            if (idx >= 0) {
                val reopen = mutableListOf<OpenTag>()
                while (stack.size > idx) {
                    val popped = stack.removeLast()
                    builder.pop()
                    if (stack.size > idx) reopen.add(0, popped)
                }
                for (tag in reopen) {
                    builder.pushStyle(tag.style)
                    stack.addLast(tag)
                }
            }
        }
    }

    val rest = text.substring(lastIndex)
    if (rest.isNotEmpty()) builder.append(unescapeHtmlEntities(rest))

    return builder.toAnnotatedString()
}

private fun unescapeHtmlEntities(s: String): String = s
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")

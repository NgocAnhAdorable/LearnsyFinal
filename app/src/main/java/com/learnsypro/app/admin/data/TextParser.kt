package com.learnsypro.app.admin.data

import org.json.JSONArray
import org.json.JSONObject

// Tương đương stripQNum() trong app.jsx — bỏ tiền tố "Câu 1:" / "1." đầu dòng.
fun stripQNum(s: String): String {
    return s
        .replace(Regex("^Câu\\s*\\d+[.:]\\s*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^\\d+\\s*[.)]\\s*"), "")
        .trim()
}

// Tương đương parseText() trong app.jsx — parser offline: nhận văn bản thô dán
// từ Word/PDF, tự tách thành nhiều câu hỏi (ĐS/TN/Điền từ) theo pattern:
// - Đúng/Sai: có ≥2 dòng "a. ... Đ/S"
// - Trắc nghiệm: có dòng "A. ..." "B. ..." (chữ hoa)
// - Điền từ: có "___" hoặc dòng đầu bắt đầu bằng "Điền" và không khớp 2 loại trên
// Nếu không tách được câu nào, toàn bộ text được nhét vào 1 câu trắc nghiệm rỗng
// để người dùng tự sửa tay thay vì mất trắng nội dung đã dán.
fun parseText(raw: String): List<Question> {
    val results = mutableListOf<Question>()
    val blocks = raw
        .split(Regex("(?=Câu\\s*\\d+[.:])|(?=\\n{2,})"))
        .map { it.trim() }
        .filter { it.length > 10 }

    for (block in blocks) {
        val lines = block.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) continue

        val hasABCD = lines.any { Regex("^[A-Da-d][.)]\\s").containsMatchIn(it) && Regex("^[A-D]").containsMatchIn(it) }
        val tfLines = lines.filter { Regex("^[a-d][.)]\\s").containsMatchIn(it) }
        val hasTF = tfLines.size >= 2
        val hasFill = !hasTF && !hasABCD && (
            Regex("___+").containsMatchIn(block) ||
                Regex("^điền\\s", RegexOption.IGNORE_CASE).containsMatchIn(lines[0])
            )

        when {
            hasTF -> {
                val items = tfLines.map { l ->
                    val text = l.replace(Regex("^[a-d][.)]\\s*"), "")
                        .replace(Regex("[\\s\\u00a0]*[SĐ]$"), "")
                        .trim()
                    val suf = l.trimEnd().takeLast(1)
                    val answer = when (suf) {
                        "Đ" -> true
                        "S" -> false
                        else -> true
                    }
                    TFItem(text = text, answer = answer)
                }
                val firstIdx = lines.indexOfFirst { Regex("^[a-d][.)]\\s").containsMatchIn(it) }
                val passageLines = if (firstIdx > 0) lines.subList(0, firstIdx) else emptyList()
                val passage = stripQNum(passageLines.joinToString(" "))
                    .replace(Regex("^(Cho đoạn tư liệu|Đọc đoạn|Dựa vào đoạn)[^:]*:\\s*", RegexOption.IGNORE_CASE), "")
                    .trim()
                if (items.size >= 2) {
                    val padded = if (items.size >= 4) items else items + List(4 - items.size) { TFItem("", true) }
                    results.add(Question.TrueFalse(passage = passage, source = "", items = padded))
                }
            }
            hasABCD -> {
                val optLines = lines.filter { Regex("^[A-D][.)]\\s").containsMatchIn(it) }
                val options = optLines.map { it.replace(Regex("^[A-D][.)]\\s*"), "").trim() }
                val ansLine = lines.find { Regex("^(answer|đáp án|Đáp án)\\s*[:=]", RegexOption.IGNORE_CASE).containsMatchIn(it) }
                var correct = 0
                if (ansLine != null) {
                    val letterStr = ansLine.replace(Regex("^(answer|đáp án|Đáp án)\\s*[:=]\\s*", RegexOption.IGNORE_CASE), "").trim()
                    val letter = letterStr.firstOrNull()?.uppercaseChar()?.toString() ?: "A"
                    correct = maxOf(0, LETTERS.indexOf(letter))
                }
                val firstOptIdx = lines.indexOfFirst { Regex("^[A-D][.)]\\s").containsMatchIn(it) }
                val question = stripQNum(lines.subList(0, if (firstOptIdx >= 0) firstOptIdx else 0).joinToString(" "))
                if (options.size >= 2) {
                    val padded = if (options.size >= 4) options else options + List(4 - options.size) { "" }
                    results.add(Question.Multiple(question = question, options = padded, correct = correct))
                }
            }
            hasFill -> {
                val ansLine = lines.find { Regex("^(answer|đáp án|Đáp án)\\s*[:=]", RegexOption.IGNORE_CASE).containsMatchIn(it) }
                val answer = ansLine?.replace(Regex("^(answer|đáp án|Đáp án)\\s*[:=]\\s*", RegexOption.IGNORE_CASE), "")?.trim() ?: ""
                val question = stripQNum(lines.filter { it != ansLine }.joinToString(" "))
                results.add(Question.FillBlank(question = question, answer = answer, hint = ""))
            }
        }
    }

    if (results.isEmpty() && raw.trim().length > 10) {
        results.add(Question.Multiple(question = raw.trim().take(200), options = listOf("", "", "", ""), correct = 0))
    }
    return results
}

// Tương đương importJSON() trong app.jsx — import linh hoạt từ nhiều format JSON
// khác nhau (khoá tiếng Anh lẫn tiếng Việt, mảng trần hoặc bọc trong {questions:[...]}).
// Dùng org.json thay vì kotlinx.serialization vì input không có schema cố định —
// mỗi câu hỏi có thể thiếu/thừa field tùy nguồn xuất ra JSON đó.
fun importJSON(raw: String): List<Question> {
    val root = org.json.JSONTokener(raw).nextValue()
    val arr: JSONArray = when (root) {
        is JSONArray -> root
        is JSONObject -> when {
            root.has("questions") -> root.getJSONArray("questions")
            root.has("data") -> root.getJSONArray("data")
            else -> {
                // Object.values(data) — không có mảng câu hỏi rõ ràng, thử lấy toàn bộ value là object
                val values = JSONArray()
                root.keys().forEach { k -> values.put(root.get(k)) }
                values
            }
        }
        else -> JSONArray()
    }

    val result = mutableListOf<Question>()
    for (i in 0 until arr.length()) {
        val q = arr.optJSONObject(i) ?: continue
        val t = (q.optString("type", "")).lowercase()

        val items = q.optJSONArray("items") ?: q.optJSONArray("statements")
        val hasItemsWithAnswer = items != null && items.length() > 0 &&
            items.optJSONObject(0)?.has("answer") == true
        val autoTF = t.isEmpty() && q.has("passage") && hasItemsWithAnswer
        val isTF = t == "true_false" || t == "truefalse" || t == "dung_sai" || autoTF

        when {
            isTF -> {
                val passage = q.optString("passage", q.optString("content", q.optString("doantulieu", "")))
                val source = q.optString("source", q.optString("nguon", ""))
                val tfItems = mutableListOf<TFItem>()
                val srcItems = items ?: JSONArray()
                for (j in 0 until srcItems.length()) {
                    val it = srcItems.optJSONObject(j) ?: continue
                    val text = it.optString("text", it.optString("content", it.optString("statement", "")))
                    val ansRaw = it.opt("answer")
                    val answer = ansRaw == true || ansRaw == "true" || ansRaw == "Đúng" || ansRaw == 1
                    tfItems.add(TFItem(text = text, answer = answer))
                }
                if (passage.isNotBlank() || tfItems.isNotEmpty()) {
                    result.add(Question.TrueFalse(passage = passage, source = source, items = tfItems.ifEmpty { Question.TrueFalse().items }))
                }
            }
            t == "multi_select" || t == "multiselect" || t == "checkbox" -> {
                val question = q.optString("question", q.optString("content", q.optString("câu_hỏi", "")))
                val optsArr = q.optJSONArray("options") ?: q.optJSONArray("choices") ?: q.optJSONArray("answers")
                val options = mutableListOf<String>()
                if (optsArr != null) {
                    for (j in 0 until optsArr.length()) {
                        val o = optsArr.opt(j)
                        options.add(
                            when (o) {
                                is JSONObject -> o.optString("text", o.optString("content", ""))
                                else -> o?.toString() ?: ""
                            }
                        )
                    }
                }
                val correctArr = q.optJSONArray("correct") ?: q.optJSONArray("correctAnswers") ?: q.optJSONArray("answers_correct")
                val correct = mutableListOf<Int>()
                if (correctArr != null) {
                    for (j in 0 until correctArr.length()) correct.add(correctArr.optInt(j, 0))
                } else {
                    correct.add(0)
                }
                if (question.isNotBlank()) {
                    result.add(Question.MultiSelect(question = question, options = options.ifEmpty { listOf("", "", "", "") }, correct = correct))
                }
            }
            t == "fill_blank" || t == "fillblank" || t == "fill" -> {
                val question = q.optString("question", q.optString("content", ""))
                val answer = q.optString("answer", q.optString("correct_answer", q.optString("key", "")))
                val hint = q.optString("hint", q.optString("goi_y", ""))
                if (question.isNotBlank()) {
                    result.add(Question.FillBlank(question = question, answer = answer, hint = hint))
                }
            }
            else -> {
                val question = q.optString("question", q.optString("content", q.optString("câu_hỏi", "")))
                val optsArr = q.optJSONArray("options") ?: q.optJSONArray("choices")
                val optsList = mutableListOf<String>()
                if (optsArr != null) {
                    for (j in 0 until optsArr.length()) {
                        val o = optsArr.opt(j)
                        optsList.add(
                            when (o) {
                                is JSONObject -> o.optString("text", o.optString("content", o.optString("label", "")))
                                else -> o?.toString() ?: ""
                            }
                        )
                    }
                }
                var correct = 0
                when {
                    q.opt("correct") is Int -> correct = q.optInt("correct")
                    q.opt("correct") is String -> correct = maxOf(0, LETTERS.indexOf((q.optString("correct")).uppercase()))
                    q.opt("correctAnswer") is String -> correct = maxOf(0, LETTERS.indexOf((q.optString("correctAnswer")).uppercase()))
                    q.opt("answer") is Int -> correct = q.optInt("answer")
                }
                if (question.isNotBlank()) {
                    val padded = if (optsList.size >= 4) optsList else optsList + List(maxOf(0, 4 - optsList.size)) { "" }
                    result.add(Question.Multiple(question = question, options = padded, correct = correct))
                }
            }
        }
    }
    return result
}

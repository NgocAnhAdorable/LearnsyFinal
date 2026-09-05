package com.learnsypro.app.admin.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlin.random.Random

// Web tạo id câu hỏi bằng `Date.now()+Math.random()` (một SỐ trong JS),
// không convert sang string trước khi lưu — nên Supabase trả id dạng JSON
// number (vd. 1784767654021.8857), trong khi Question.id khai là String.
// decodeList<Lesson>() gặp type mismatch này sẽ throw ngay khi decode —
// đây là nguyên nhân thật của lỗi "Lỗi tải bài học: String literal" ở
// Dashboard. Serializer này tự nhận cả string lẫn number rồi ép về String
// (bỏ phần thập phân nếu number, giống Number.toString() dùng nguyên).
object LenientStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)

    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        val element = jsonDecoder.decodeJsonElement()
        return if (element is JsonPrimitive) element.content else element.toString()
    }
}

// Tương đương getTypes() trong app.jsx — 4 loại câu hỏi.
enum class QuestionType(val label: String, val short: String) {
    @SerialName("true_false") TRUE_FALSE("Đúng / Sai", "ĐS"),
    @SerialName("multiple") MULTIPLE("Trắc nghiệm 4 đáp án", "TN"),
    @SerialName("multi_select") MULTI_SELECT("Chọn nhiều đáp án", "CN"),
    @SerialName("fill_blank") FILL_BLANK("Điền chỗ trống", "ĐT")
}

@Serializable
data class TFItem(val text: String = "", val answer: Boolean = true)

// Câu hỏi — dùng sealed interface để mỗi loại giữ đúng field, thay vì
// 1 object dùng chung nhiều field optional như bên JS.
@Serializable
sealed interface Question {
    val id: String

    @Serializable
    @SerialName("true_false")
    data class TrueFalse(
        @Serializable(with = LenientStringSerializer::class)
        override val id: String = newId(),
        val passage: String = "",
        val source: String = "",
        val items: List<TFItem> = listOf(
            TFItem("", true), TFItem("", false), TFItem("", true), TFItem("", false)
        )
    ) : Question

    @Serializable
    @SerialName("multiple")
    data class Multiple(
        @Serializable(with = LenientStringSerializer::class)
        override val id: String = newId(),
        val question: String = "",
        val options: List<String> = listOf("", "", "", ""),
        val correct: Int = 0
    ) : Question

    @Serializable
    @SerialName("multi_select")
    data class MultiSelect(
        @Serializable(with = LenientStringSerializer::class)
        override val id: String = newId(),
        val question: String = "",
        val options: List<String> = listOf("", "", "", ""),
        val correct: List<Int> = listOf(0)
    ) : Question

    @Serializable
    @SerialName("fill_blank")
    data class FillBlank(
        @Serializable(with = LenientStringSerializer::class)
        override val id: String = newId(),
        val question: String = "",
        val answer: String = "",
        val hint: String = ""
    ) : Question

    companion object {
        // Tương đương Date.now()+Math.random() bên JS làm id tạm cho câu hỏi mới
        fun newId(): String = "${System.currentTimeMillis()}${Random.nextInt(100000)}"
    }
}

// ── Empty factories — tương đương emptyTF/emptyMC/emptyMS/emptyFB ──
fun emptyTF() = Question.TrueFalse()
fun emptyMC() = Question.Multiple()
fun emptyMS() = Question.MultiSelect()
fun emptyFB() = Question.FillBlank()

fun newQuestion(type: QuestionType): Question = when (type) {
    QuestionType.TRUE_FALSE -> emptyTF()
    QuestionType.MULTIPLE -> emptyMC()
    QuestionType.MULTI_SELECT -> emptyMS()
    QuestionType.FILL_BLANK -> emptyFB()
}

val LETTERS = listOf("A", "B", "C", "D", "E", "F")

fun stripHTML(s: String): String = s.replace(Regex("<[^>]*>"), "")

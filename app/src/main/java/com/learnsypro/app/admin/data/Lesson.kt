package com.learnsypro.app.admin.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive

// Cột `questions` trong Supabase là kiểu jsonb, nhưng giá trị bên trong bị
// JSON.stringify() ở web ÁP DỤNG NHIỀU LẦN trước khi insert (double/triple-
// encoded) — thay vì lưu mảng jsonb thật, mỗi lớp lưu một CHUỖI chứa JSON
// của lớp trong. decodeList<Lesson>() mặc định mong đợi JsonArray thật ở
// đây; gặp JsonPrimitive (string) sẽ throw ngay ("String literal") và làm
// CẢ danh sách bài học load thất bại — đây là lý do Dashboard báo lỗi dù
// màn "Bài học" (đã unwrap đúng 1 lớp) từng hiển thị được cho case chỉ có
// 1 lớp lồng. Vì số lớp lồng không cố định, hàm dưới đây tự bóc từng lớp
// JsonPrimitive(string) cho tới khi ra được JsonArray thật hoặc hết layer.
object QuestionsFieldSerializer : KSerializer<List<Question>> {
    private val listSerializer = ListSerializer(Question.serializer())
    private const val MAX_UNWRAP_LAYERS = 5
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("QuestionsField")

    override fun serialize(encoder: Encoder, value: List<Question>) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("QuestionsFieldSerializer chỉ dùng được với Json")
        jsonEncoder.encodeJsonElement(jsonEncoder.json.encodeToJsonElement(listSerializer, value))
    }

    override fun deserialize(decoder: Decoder): List<Question> {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("QuestionsFieldSerializer chỉ dùng được với Json")
        var element: JsonElement = jsonDecoder.decodeJsonElement()
        var layers = 0
        while (element is JsonPrimitive && element.isString && layers < MAX_UNWRAP_LAYERS) {
            if (element.content.isBlank()) return emptyList()
            element = jsonDecoder.json.parseToJsonElement(element.content)
            layers++
        }
        val arrayElement = element as? JsonArray ?: return emptyList()
        return jsonDecoder.json.decodeFromJsonElement(listSerializer, arrayElement)
    }
}

@Serializable
data class Lesson(
    val id: String,
    val title: String = "",
    val subject: String = "Tiếng Anh",
    val password: String = "",
    val timerLimit: Int = 0,
    @Serializable(with = QuestionsFieldSerializer::class)
    val questions: List<Question> = emptyList(),
    @kotlinx.serialization.SerialName("created_at") val createdAt: String = ""
)

val SUBJECTS = listOf(
    "Tiếng Anh", "Lịch Sử", "Địa Lý", "Vật Lý",
    "GDKTPL", "GDQPAN", "Công Nghệ", "Ngữ Văn", "Khác"
)

enum class LessonFilter { ALL, ENGLISH, OTHER }
enum class SortBy { NEWEST, OLDEST, NAME, COUNT }
enum class CardBlur { OFF, FIFTY, EIGHTY_FIVE }

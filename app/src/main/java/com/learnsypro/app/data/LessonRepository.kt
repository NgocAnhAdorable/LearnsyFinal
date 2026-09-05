package com.learnsypro.app.data

import io.github.jan.supabase.postgrest.postgrest
import com.learnsypro.app.ui.quiz.Lesson as QuizLesson
import com.learnsypro.app.ui.quiz.Question
import com.learnsypro.app.ui.quiz.QuestionType
import com.learnsypro.app.ui.quiz.TrueFalseItem
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class LessonRow(
    val id: String,
    val title: String,
    val description: String? = null,
    val is_published: Boolean = false,
    val timerLimit: Int = 0,
    val subject: String? = null,
    val password: JsonElement? = null,
    val questions: JsonElement? = null
)

/**
 * ── LessonRepository ──
 * Tải danh sách bài học từ bảng `lessons` (Supabase). Cột `questions` là
 * jsonb — thực tế có 2 dạng đã gặp: MẢNG JSON THẬT (jsonb chuẩn) hoặc
 * STRING chứa JSON (double-encoded, dữ liệu export cũ). Xử lý cả 2 để
 * tránh JsonDecodingException khi gặp bài học có định dạng khác.
 *
 * Schema câu hỏi xác nhận từ dữ liệu thật:
 * - true_false: {id, type, items:[{text,answer:Boolean}], passage, source}
 * - fill_blank: {id, type, question, hint, answer} — "answer" là 1 String
 *   đơn, field câu hỏi tên "question" (khác "text")
 * - multiple/multi_select: CHƯA có mẫu thật — suy đoán theo cấu trúc chung.
 *   Cần xác nhận thêm khi có dữ liệu thật loại này.
 *
 * ── Offline cache ──
 * fetchLessons() giờ tự cache: tải thành công từ Supabase → ghi đè cache
 * (OfflineCacheStore) để dùng lại khi mất mạng. Nếu request thất bại (mất
 * mạng, timeout, v.v.) → âm thầm trả về bản đã cache thay vì ném lỗi lên,
 * để học sinh vẫn mở được bài đã từng tải trước đó.
 *
 * QUAN TRỌNG: khi hoàn toàn mất mạng (không có sóng/wifi), socket connect
 * có thể treo rất lâu (hoặc vô hạn tùy thiết bị/OS) trước khi hệ thống tự
 * ném lỗi — nếu không giới hạn thời gian, màn hình sẽ kẹt ở "Đang tải bài
 * học..." mãi mãi dù đã có cache offline sẵn sàng dùng. Do đó bọc request
 * bằng withTimeout(6s): quá hạn thì chủ động coi như lỗi mạng, rơi về cache
 * ngay lập tức thay vì chờ hệ thống.
 */
class LessonRepository(private val cache: OfflineCacheStore? = null) {

    private val json = Json { ignoreUnknownKeys = true }

    /** true nếu lần fetchLessons() gần nhất phải rơi về bản cache offline
     *  (do lỗi mạng/Supabase) thay vì tải mới thành công. */
    var lastFetchWasFromCache: Boolean = false
        private set

    suspend fun fetchLessons(): List<QuizLesson> {
        return try {
            val rows = withTimeout(6000) {
                SupabaseClientProvider.client.postgrest["lessons"]
                    .select()
                    .decodeList<LessonRow>()
            }

            val lessons = rows.mapNotNull { row -> rowToLesson(row) }
            cache?.saveLessons(lessons)
            lastFetchWasFromCache = false
            lessons
        } catch (e: Exception) {
            // Mất mạng, timeout (kể cả TimeoutCancellationException từ
            // withTimeout ở trên), hoặc lỗi Supabase khác — dùng bản đã
            // cache offline nếu có, thay vì kẹt màn hình loading vô hạn.
            val cached = cache?.loadLessons() ?: emptyList()
            if (cached.isEmpty()) throw e
            lastFetchWasFromCache = true
            cached
        }
    }

    private fun rowToLesson(row: LessonRow): QuizLesson? {
        val questionsJson = try {
            when (val q = row.questions) {
                null -> return null
                is kotlinx.serialization.json.JsonArray -> q
                is JsonPrimitive -> json.parseToJsonElement(q.content).jsonArray
                else -> return null
            }
        } catch (e: Exception) {
            return null
        }

        val questions = questionsJson.mapNotNull { el -> parseQuestion(el) }

        return QuizLesson(
            id = row.id,
            title = row.title,
            subject = row.subject,
            questions = questions,
            timeLimitMinutes = row.timerLimit,
            password = row.password
                ?.jsonPrimitive
                ?.contentOrNull
                ?.ifBlank { null }
        )
    }

    private fun parseQuestion(el: JsonElement): Question? {
        val obj = el.jsonObject
        // id thường là số (Date.now()+Math.random() từ web), NHƯNG câu hỏi được
        // "gộp từ bài khác" (merge-questions.jsx) có id dạng chuỗi "mq"+timestamp+random
        // (vd. "mq1784325742314.657") để tránh trùng id khi gộp — chuỗi này KHÔNG parse
        // được thành Double. Trước đây parseQuestion() trả về null (loại bỏ câu hỏi) khi
        // gặp id dạng này, khiến các câu hỏi đã gộp biến mất hoàn toàn trên app —
        // trong khi web (đọc đúng id chuỗi) vẫn hiển thị bình thường.
        // Sửa: nếu id không parse được thành số, dùng hash ổn định của chuỗi id thay vì
        // loại bỏ câu hỏi. Hash chỉ cần ổn định cho 1 lần fetch (phân biệt các câu hỏi
        // với nhau trong danh sách) — không cần khớp giữa các lần fetch khác nhau.
        val idRaw = obj["id"]?.jsonPrimitive
        val idDouble = idRaw?.doubleOrNullSafe()
            ?: idRaw?.contentOrNull?.let { it.hashCode().toDouble() }
            ?: return null
        val typeStr = obj["type"]?.jsonPrimitive?.contentOrNull ?: return null

        return when (typeStr) {
            "true_false" -> {
                val items = obj["items"]?.jsonArray?.mapNotNull { itemEl ->
                    val itemObj = itemEl.jsonObject
                    val text = itemObj["text"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val answer = itemObj["answer"]?.jsonPrimitive?.booleanOrNullSafe() ?: return@mapNotNull null
                    TrueFalseItem(text = text, correct = answer)
                } ?: emptyList()

                Question(
                    id = idDouble,
                    type = QuestionType.TRUE_FALSE,
                    items = items,
                    passage = obj["passage"]?.jsonPrimitive?.contentOrNull ?: "",
                    source = obj["source"]?.jsonPrimitive?.contentOrNull ?: ""
                )
            }

            "fill_blank" -> Question(
                id = idDouble,
                type = QuestionType.FILL_BLANK,
                question = obj["question"]?.jsonPrimitive?.contentOrNull ?: "",
                hint = obj["hint"]?.jsonPrimitive?.contentOrNull ?: "",
                answer = obj["answer"]?.jsonPrimitive?.contentOrNull ?: ""
            )

            "multiple" -> Question(
                id = idDouble,
                type = QuestionType.MULTIPLE,
                text = (obj["text"] ?: obj["question"])?.jsonPrimitive?.contentOrNull ?: "",
                options = obj["options"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                correctIndex = (obj["correctIndex"] ?: obj["correct"])?.jsonPrimitive?.intOrNull,
                explanation = obj["explanation"]?.jsonPrimitive?.contentOrNull
            )

            "multi_select" -> Question(
                id = idDouble,
                type = QuestionType.MULTI_SELECT,
                text = (obj["text"] ?: obj["question"])?.jsonPrimitive?.contentOrNull ?: "",
                options = obj["options"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                correctIndices = (obj["correctIndices"] ?: obj["correct"])?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull } ?: emptyList(),
                explanation = obj["explanation"]?.jsonPrimitive?.contentOrNull
            )

            else -> null
        }
    }

    private fun JsonPrimitive.doubleOrNullSafe(): Double? = try { double } catch (e: Exception) { null }
    private fun JsonPrimitive.booleanOrNullSafe(): Boolean? = try { boolean } catch (e: Exception) { null }
}

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

// FIX: cột số của quiz_results không nhất quán kiểu JSON giữa các field —
// dữ liệu thật export từ Supabase cho thấy score/diem10 là STRING
// ("score":"1.00", "diem10":"10.0") trong khi total/pct/question_count lại
// là NUMBER thật ("total":1, "pct":100). Đây là do PostgREST serialize kiểu
// cột numeric/decimal thành text để không mất độ chính xác dấu phẩy động,
// còn cột integer thì giữ nguyên dạng số — 2 nhóm cột dùng kiểu Postgres
// khác nhau nên JSON trả về không đồng nhất. Không thể khai báo cứng Double
// hay Int cho các field này (đã thử cả 2, đều gãy ở field còn lại). Dùng
// serializer tùy chỉnh chấp nhận CẢ String lẫn Number — an toàn với mọi kiểu
// Postgrest có thể trả về, kể cả nếu Postgrest đổi cách serialize 1 cột nào
// đó trong tương lai.
//
// Serializer viết cho kiểu KHÔNG nullable (Double, không phải Double?) —
// đây là cách chuẩn của kotlinx.serialization: khi field trong data class
// khai báo kiểu Double?, framework tự động bọc serializer này trong
// NullableSerializer để xử lý null/field vắng mặt; tự viết KSerializer<Double?>
// trực tiếp sẽ can thiệp sai vào cơ chế null-handling nội bộ của framework
// và có thể không tương thích ở một số phiên bản.
object FlexibleDoubleSerializer : KSerializer<Double> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleDouble", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Double {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeDouble()
        val element = jsonDecoder.decodeJsonElement()
        val primitive = element as? JsonPrimitive
        val raw = primitive?.content
        return raw?.toDoubleOrNull() ?: 0.0
    }

    override fun serialize(encoder: Encoder, value: Double) {
        encoder.encodeDouble(value)
    }
}

// ── Tương đương xepLoai() trong score.js ─────────────────────────
data class Rank(val label: String, val emoji: String, val color: String)

fun xepLoai(diem10: Double): Rank = when {
    diem10 >= 9   -> Rank("Xuất sắc", "🏆", "#10b981")
    diem10 >= 8   -> Rank("Giỏi", "🥇", "#f59e0b")
    diem10 >= 6.5 -> Rank("Khá", "🥈", "#a855f7")
    diem10 >= 5   -> Rank("Trung bình", "👍", "#f472b6")
    else          -> Rank("Cần cố gắng", "📚", "#ef4444")
}

fun calcDiem10(score: Int, total: Int): Double {
    if (total <= 0) return 0.0
    return Math.round((score.toDouble() / total) * 100) / 10.0
}

// ── quiz_results row ──────────────────────────────────────────────
// FIX (4 lớp lỗi khác nhau, cả 4 đều gây decode toàn bộ list thất bại):
// 1. Field vắng mặt hoàn toàn trong JSON: "= default" xử lý được việc này.
// 2. Field CÓ MẶT nhưng giá trị là null tường minh, vd. lesson_id là null:
//    "= default" KHÔNG có tác dụng ở trường hợp này; kotlinx.serialization vẫn
//    ném lỗi "Unexpected null value instead of string literal" nếu field
//    khai báo non-nullable. Bắt buộc phải khai báo kiểu nullable (String?,
//    Int? v.v.) thì null tường minh mới decode được, dùng dấu ?: khi hiển thị.
// 3 & 4. Field CÓ MẶT, không null, nhưng SAI KIỂU SỐ theo 2 chiều khác nhau:
//    Int không decode được từ "0.00" (có dấu chấm), NHƯNG Double cũng không
//    decode được nếu Postgrest trả field đó dưới dạng STRING thay vì number
//    JSON thật (xác nhận qua dữ liệu thật: "score":"1.00" là string, trong
//    khi "total":1 lại là number). Dùng FlexibleDoubleSerializer ở trên cho
//    mọi field số — chấp nhận cả 2 dạng, không còn phụ thuộc giả định kiểu
//    cột DB nào cả.
// Đối chiếu dashboard.jsx bản web: ResultsPanel chỉ thực sự dùng 6 field
// id/student_name/lesson_title/score/total/created_at, tự tính lại pct từ
// score/total chứ không đọc cột "pct" có sẵn — các field còn lại chỉ mang
// tính hiển thị thêm nên được khai báo nullable, xử lý null lúc hiển thị.
@Serializable
data class QuizResult(
    val id: String? = null,
    @SerialName("student_name") val studentName: String? = null,
    @SerialName("student_id") val studentId: String? = null,
    @SerialName("lesson_id") val lessonId: String? = null,
    @SerialName("lesson_title") val lessonTitle: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class) val score: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class) val total: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class) val diem10: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class) val pct: Double? = null,
    @SerialName("xep_loai") val xepLoaiLabel: String? = null,
    @SerialName("question_count")
    @Serializable(with = FlexibleDoubleSerializer::class) val questionCount: Double? = null,
    @SerialName("submitted_at") val submittedAt: String? = null,
    // Cột created_at do Postgres tự sinh khi insert — bản web dùng cột này để
    // sắp xếp (order by created_at).
    @SerialName("created_at") val createdAt: String? = null
) {
    // Helper hiển thị an toàn — dùng ở UI thay vì đọc field null trực tiếp.
    // safeScore/safeTotal làm tròn về Int để hiển thị "3/5 câu" như trước;
    // dữ liệu gốc (số câu đúng) vốn luôn là số nguyên dù cột DB lưu kiểu thập phân.
    val displayStudentName: String get() = studentName ?: "Ẩn danh"
    val displayLessonTitle: String get() = lessonTitle ?: "Không rõ"
    val safeScore: Int get() = score?.let { Math.round(it).toInt() } ?: 0
    val safeTotal: Int get() = total?.let { Math.round(it).toInt() } ?: 0
    val safeDiem10: Double get() = diem10 ?: calcDiem10(safeScore, safeTotal)
}

// ── Summary aggregate (tương đương summary=1 trong score.js) ────
data class ScoreDist(
    val nineToTen: Int = 0,
    val sevenToNine: Int = 0,
    val fiveToSeven: Int = 0,
    val belowFive: Int = 0
)

data class ScoreSummary(
    val lessonId: String,
    val count: Int,
    val avgDiem10: Double,
    val maxDiem10: Double,
    val minDiem10: Double,
    val dist: ScoreDist,
    val top5: List<QuizResult>,
    val rows: List<QuizResult>
)

fun buildSummary(lessonId: String, rows: List<QuizResult>): ScoreSummary? {
    if (rows.isEmpty()) return null
    val diem10s = rows.map { it.safeDiem10 }
    val avg = Math.round((diem10s.sum() / diem10s.size) * 10) / 10.0

    var d9 = 0; var d7 = 0; var d5 = 0; var dLow = 0
    diem10s.forEach { d ->
        when {
            d >= 9 -> d9++
            d >= 7 -> d7++
            d >= 5 -> d5++
            else -> dLow++
        }
    }

    val top5 = rows.sortedByDescending { it.safeDiem10 }.take(5)

    return ScoreSummary(
        lessonId = lessonId,
        count = rows.size,
        avgDiem10 = avg,
        maxDiem10 = diem10s.max(),
        minDiem10 = diem10s.min(),
        dist = ScoreDist(d9, d7, d5, dLow),
        top5 = top5,
        rows = rows
    )
}

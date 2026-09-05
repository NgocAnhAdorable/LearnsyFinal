package com.learnsypro.app.data

import com.learnsypro.app.ui.dashboard.HistoryEntry
import com.learnsypro.app.ui.quiz.PerQuestionResult
import com.learnsypro.app.ui.quiz.QuestionType
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

@Serializable
private data class PerQRow(
    val ok: Boolean,
    val type: String,
    val qText: String,
    val partial: Boolean,
    val correctAns: String
)

@Serializable
private data class QuizResultRow(
    val student_id: String? = null,
    val lesson_id: String? = null,
    val lesson_title: String,
    val score: Double,
    val total: Int,
    val pct: Int,
    val created_at: String? = null,
    val per_q: JsonElement? = null
)

@Serializable
private data class QuizResultInsert(
    val student_id: String,
    val student_name: String,
    val lesson_id: String,
    val lesson_title: String,
    val score: String,
    val total: Int,
    val per_q: String,
    val diem10: String,
    val xep_loai: String,
    val pct: Int,
    val question_count: Int
)

private fun xepLoai(pct: Int): String = when {
    pct >= 90 -> "Xuất sắc"
    pct >= 70 -> "Giỏi"
    pct >= 50 -> "Khá"
    else -> "Cần cố gắng"
}

/**
 * ── QuizResultRepository ──
 * Lưu kết quả bài làm lên bảng `quiz_results`, khớp đúng schema thật đã
 * export (score/diem10 dạng String, per_q là JSON double-encoded).
 */
class QuizResultRepository {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun saveResult(
        studentId: String,
        studentName: String,
        lessonId: String,
        lessonTitle: String,
        score: Int,
        total: Int,
        perQuestion: List<PerQuestionResult>
    ) {
        val pct = if (total > 0) Math.round(score.toDouble() / total * 100).toInt() else 0
        val diem10 = if (total > 0) score.toDouble() / total * 10 else 0.0

        val perQJson = json.encodeToString(
            perQuestion.map {
                PerQRow(ok = it.ok, type = it.type.name.lowercase(), qText = it.qText, partial = it.partial, correctAns = it.correctAns)
            }
        )

        val insert = QuizResultInsert(
            student_id = studentId,
            student_name = studentName,
            lesson_id = lessonId,
            lesson_title = lessonTitle,
            score = String.format(java.util.Locale.US, "%.2f", score.toDouble()),
            total = total,
            per_q = perQJson,
            diem10 = String.format(java.util.Locale.US, "%.1f", diem10),
            xep_loai = xepLoai(pct),
            pct = pct,
            question_count = total
        )

        // Log ngay từ đầu (không chỉ khi lỗi) để có thể đối chiếu Logcat với dữ
        // liệu thật trên Supabase Dashboard — trả lời câu hỏi "app Android build
        // ra có đang trỏ đúng project Supabase mà web/admin dùng không?" mà
        // không cần đoán. So sánh giá trị SUPA_URL này với biến môi trường
        // SUPA_URL trong Cloudflare Pages (Settings → Environment variables) của
        // dự án web — nếu khác nhau, đó chính là lý do điểm "lưu thành công"
        // trên Android nhưng không bao giờ hiện ở admin.
        android.util.Log.i(
            "LearnsySaveResult",
            "Lưu kết quả — SUPA_URL: ${com.learnsypro.app.BuildConfig.SUPA_URL}, studentId=$studentId, lessonId=$lessonId"
        )

        // Đã XÁC NHẬN qua functions/api/score.js (bên web) rằng bảng quiz_results
        // thật sự có UNIQUE constraint (student_id, lesson_id) — bên web dùng
        // upsert qua Supabase REST với "on_conflict=student_id,lesson_id" và hoạt
        // động đúng. Quay lại dùng upsert() cho gọn và atomic (tránh race
        // select→insert như cách tạm trước đây).
        try {
            SupabaseClientProvider.client.postgrest["quiz_results"].upsert(
                value = insert,
                onConflict = "student_id,lesson_id"
            )
            android.util.Log.i("LearnsySaveResult", "Upsert quiz_results thành công cho lessonId=$lessonId")
        } catch (e: Exception) {
            // Log rõ SUPA_URL đang dùng (KHÔNG log key) để chẩn đoán trường hợp
            // Android build với SUPA_URL/SUPA_KEY khác với project Supabase mà
            // Cloudflare Function /api/score.js (bên web) đang dùng — nếu 2 nơi
            // trỏ khác project, app Android vẫn "lưu thành công" (không exception)
            // nhưng dữ liệu nằm ở project khác nên admin không bao giờ thấy được.
            // Nếu log này hiện lỗi 404/401/PGRST xảy ra, gần như chắc chắn là do
            // SUPA_URL/SUPA_KEY trong GitHub Secrets (dùng để build APK) không
            // khớp với SUPA_URL/SUPA_KEY trong Cloudflare Pages env vars (dùng
            // bởi web) — 2 nơi cấu hình hoàn toàn tách biệt, dễ bị lệch khi đổi
            // key/project mà chỉ cập nhật 1 trong 2 chỗ.
            android.util.Log.e(
                "LearnsySaveResult",
                "Upsert quiz_results thất bại — SUPA_URL đang dùng: ${com.learnsypro.app.BuildConfig.SUPA_URL}",
                e
            )
            throw e
        }
    }

    /**
     * Tải lịch sử làm bài thật từ Supabase theo studentId, thay vì chỉ giữ
     * tạm in-memory (bị mất khi app bị kill / khởi động lại — đây là lý do
     * "Lịch sử" trước đây luôn chỉ hiện đúng 1 bài vừa làm trong phiên).
     *
     * Trả về cặp (danh sách HistoryEntry, map chi tiết từng câu theo lessonTitle)
     * — trước đây chỉ trả HistoryEntry nên bấm vào 1 dòng lịch sử cũ (đã lưu từ
     * phiên trước, không còn trong bộ nhớ) sẽ không hiện được chi tiết đúng/sai
     * từng câu dù dữ liệu per_q đã có sẵn trên Supabase.
     */
    suspend fun fetchHistory(studentId: String): Pair<List<HistoryEntry>, Map<String, List<PerQuestionResult>>> {
        if (studentId.isBlank()) return emptyList<HistoryEntry>() to emptyMap()
        return try {
            val rows = SupabaseClientProvider.client.postgrest["quiz_results"]
                .select {
                    filter { eq("student_id", studentId) }
                    order("created_at", order = io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                }
                .decodeList<QuizResultRow>()

            val entries = mutableListOf<HistoryEntry>()
            val perQMap = mutableMapOf<String, List<PerQuestionResult>>()

            rows.forEach { row ->
                val scoreDouble = row.score
                val millis = try {
                    row.created_at?.let { java.time.Instant.parse(it).toEpochMilli() }
                } catch (e: Exception) { null } ?: System.currentTimeMillis()

                entries += HistoryEntry(
                    lessonTitle = row.lesson_title,
                    pct = row.pct.toDouble(),
                    timestampMillis = millis,
                    total = row.total,
                    score = scoreDouble.toInt()
                )

                row.per_q?.let { raw ->
                    try {
                        val decoded = json.decodeFromJsonElement<List<PerQRow>>(raw)
                        perQMap[row.lesson_title] = decoded.map {
                            PerQuestionResult(
                                type = runCatching { QuestionType.valueOf(it.type.uppercase()) }
                                    .getOrDefault(QuestionType.MULTIPLE),
                                ok = it.ok,
                                partial = it.partial,
                                qText = it.qText,
                                correctAns = it.correctAns
                            )
                        }
                    } catch (e: Exception) {
                        // per_q hỏng/khác định dạng cho dòng cũ — bỏ qua chi tiết dòng
                        // này, vẫn giữ nguyên HistoryEntry để không mất cả dòng lịch sử.
                    }
                }
            }

            entries to perQMap
        } catch (e: Exception) {
            // TRƯỚC ĐÂY: nuốt lỗi âm thầm, không log gì — khiến không thể biết
            // vì sao "Lịch sử" trống dù saveResult() đã upsert thành công lên
            // Supabase. Log rõ ra để xem Logcat/LogFox tag "LearnsyFetchHistory"
            // biết chính xác nguyên nhân (ví dụ: SerializationException nếu cột
            // `score`/`pct`/`total` trên Supabase khác kiểu dữ liệu so với
            // QuizResultRow, hoặc lỗi RLS chặn SELECT theo student_id).
            android.util.Log.e(
                "LearnsyFetchHistory",
                "fetchHistory thất bại cho studentId=$studentId",
                e
            )
            emptyList<HistoryEntry>() to emptyMap()
        }
    }
}

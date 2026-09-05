package com.learnsypro.app.admin.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable

// Native app gọi thẳng Supabase Postgrest, không qua Cloudflare Function
// /api/score nữa (đó chỉ là proxy để giấu SUPA_KEY khỏi client web).
// Rate limit (5 POST/studentId/60s trong score.js) cần dời sang
// Supabase RLS policy hoặc Edge Function nếu muốn giữ, native client
// không tự enforce được rate limit phía server.

@Serializable
data class SubmitScorePayload(
    val student_name: String,
    val student_id: String? = null,
    val lesson_id: String,
    val lesson_title: String,
    val score: Int,
    val total: Int,
    val diem10: Double,
    val pct: Int,
    val xep_loai: String,
    val question_count: Int,
    val submitted_at: String
)

class ScoreRepository {
    private val table = SupabaseConfig.client.from("quiz_results")

    suspend fun submit(
        lessonId: String,
        lessonTitle: String,
        studentName: String,
        studentId: String?,
        score: Int,
        total: Int
    ): QuizResult {
        val diem10 = calcDiem10(score, total)
        val pct = Math.round((score.toDouble() / total) * 100).toInt()
        val rank = xepLoai(diem10)
        val submittedAt = java.time.Instant.now().toString()

        val payload = SubmitScorePayload(
            student_name = studentName.trim().take(100).ifBlank { "Ẩn danh" },
            student_id = studentId,
            lesson_id = lessonId.trim().take(200),
            lesson_title = lessonTitle.trim().take(200).ifBlank { "Không rõ" },
            score = score,
            total = total,
            diem10 = diem10,
            pct = pct,
            xep_loai = rank.label,
            question_count = total,
            submitted_at = submittedAt
        )

        table.upsert(payload, onConflict = "student_id,lesson_id")

        return QuizResult(
            studentName = payload.student_name,
            studentId = payload.student_id,
            lessonId = payload.lesson_id,
            lessonTitle = payload.lesson_title,
            score = score.toDouble(),
            total = total.toDouble(),
            diem10 = diem10,
            pct = pct.toDouble(),
            xepLoaiLabel = rank.label,
            questionCount = total.toDouble(),
            submittedAt = submittedAt
        )
    }

    suspend fun byLesson(lessonId: String, limit: Long = 100, offset: Long = 0): List<QuizResult> {
        return table.select {
            filter { eq("lesson_id", lessonId) }
            order("created_at", Order.DESCENDING)
            limit(limit)
            range(offset, offset + limit - 1)
        }.decodeList<QuizResult>()
    }

    suspend fun byStudent(studentId: String, limit: Long = 100, offset: Long = 0): List<QuizResult> {
        return table.select {
            filter { eq("student_id", studentId) }
            order("created_at", Order.DESCENDING)
            limit(limit)
            range(offset, offset + limit - 1)
        }.decodeList<QuizResult>()
    }

    suspend fun summaryByLesson(lessonId: String): ScoreSummary? {
        val rows = byLesson(lessonId, limit = 500)
        return buildSummary(lessonId, rows)
    }

    // Tương đương ResultsPanel load() trong dashboard.jsx — 50 kết quả gần
    // nhất, mọi bài. FIX: đổi sort key từ "submitted_at" sang "created_at" để
    // khớp đúng bản web gốc — created_at là cột Postgres tự sinh khi insert
    // nên luôn tồn tại và đáng tin cậy, còn submitted_at là field do client tự
    // gửi lên (có thể sai định dạng/lệch giờ máy học sinh, hoặc không tồn tại
    // trong bảng nếu schema thật không có cột này — khi đó Postgrest báo lỗi
    // "column does not exist" và toàn bộ query thất bại).
    suspend fun recentResults(limit: Long = 50): List<QuizResult> {
        return table.select {
            order("created_at", Order.DESCENDING)
            limit(limit)
        }.decodeList<QuizResult>()
    }

    // Cho insight "học sinh chưa làm bài tuần này" — cần TOÀN BỘ quiz_results
    // trong windowDays gần nhất (không giới hạn 50 như recentResults(), vì
    // trường có nhiều lớp có thể vượt 50 lượt nộp bài/tuần dễ dàng, làm
    // insight đếm thiếu học sinh đã làm bài). Lọc bằng gte(created_at) ngay
    // trên Postgrest thay vì tải hết rồi lọc client — tránh kéo toàn bộ
    // lịch sử quiz_results (có thể rất lớn) chỉ để tính 1 tuần gần nhất.
    suspend fun resultsSince(windowDays: Long = 7): List<QuizResult> {
        val cutoffIso = java.time.Instant.now().minusSeconds(windowDays * 24 * 3600).toString()
        return table.select {
            filter { gte("created_at", cutoffIso) }
            order("created_at", Order.DESCENDING)
        }.decodeList<QuizResult>()
    }

    // Tương đương handleClearAll() trong dashboard.jsx — xoá toàn bộ
    // quiz_results (.not('id','is',null)).
    // FIX: trước đây dùng neq("id", "") — nhưng cột id là kiểu uuid trên
    // Postgres, so sánh != với chuỗi rỗng "" (không phải UUID hợp lệ) khiến
    // PostgREST không match được row nào (fail-safe âm thầm, không báo lỗi
    // rõ ràng) thay vì báo cú pháp sai — kết quả: lệnh DELETE "chạy" nhưng
    // xoá 0 dòng, code tưởng nhầm là do RLS chặn quyền xoá.
    // PostgREST luôn yêu cầu có ít nhất 1 filter cho DELETE hàng loạt (không
    // thể bỏ hẳn filter), nên dùng gte trên created_at — cột kiểu timestamp
    // do Postgres tự sinh khi insert, luôn có giá trị và luôn >= mốc thời
    // gian rất xa trong quá khứ nên khớp mọi row thật, không rủi ro lỗi kiểu
    // dữ liệu như so sánh chuỗi với cột uuid.
    suspend fun clearAll() {
        table.delete {
            filter {
                gte("created_at", "1970-01-01T00:00:00")
            }
        }
    }
}

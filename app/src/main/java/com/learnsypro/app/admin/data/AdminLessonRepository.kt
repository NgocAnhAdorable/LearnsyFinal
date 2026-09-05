package com.learnsypro.app.admin.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import com.learnsypro.app.admin.cache.UpstashCache
import java.time.Instant

class AdminLessonRepository {
    private val table = SupabaseConfig.client.from("lessons")

    suspend fun fetchAll(): List<Lesson> =
        table.select { order("created_at", Order.ASCENDING) }.decodeList<Lesson>()

    // Tương đương fetch 1 lesson theo id — cần cho NavHost: route Editor chỉ
    // nhận lessonId (String) làm nav argument, không nhận sẵn Lesson object
    // đầy đủ từ màn cha như kiến trúc cũ (tự chế điều hướng bằng state).
    suspend fun fetchOne(id: String): Lesson? =
        table.select { filter { eq("id", id) } }.decodeSingleOrNull<Lesson>()

    // Tương đương addLesson() — tạo id 'l'+timestamp, insert Supabase
    suspend fun create(): Lesson {
        val lesson = Lesson(
            id = "l${System.currentTimeMillis()}",
            title = "",
            subject = "Tiếng Anh",
            password = "",
            questions = listOf(emptyTF()),
            createdAt = Instant.now().toString()
        )
        table.insert(lesson)
        UpstashCache.invalidateLessonsCache()
        return lesson
    }

    // Tương đương phần lưu trong auto-save effect (upsert onConflict=id)
    suspend fun save(lesson: Lesson) {
        table.upsert(lesson) { }
        UpstashCache.invalidateLessonsCache()
    }

    suspend fun delete(id: String) {
        table.delete { filter { eq("id", id) } }
        UpstashCache.invalidateLessonsCache()
    }

    // Tương đương dupLesson() — sao chép bài, đổi id + đổi id từng câu hỏi
    suspend fun duplicate(lesson: Lesson, uniqueTitle: String): Lesson {
        val newId = "l${System.currentTimeMillis()}"
        val dup = lesson.copy(
            id = newId,
            title = uniqueTitle,
            questions = lesson.questions.map { reassignId(it) }
        )
        table.insert(dup)
        UpstashCache.invalidateLessonsCache()
        return dup
    }

    private fun reassignId(q: Question): Question = when (q) {
        is Question.TrueFalse -> q.copy(id = Question.newId())
        is Question.Multiple -> q.copy(id = Question.newId())
        is Question.MultiSelect -> q.copy(id = Question.newId())
        is Question.FillBlank -> q.copy(id = Question.newId())
    }

    // Tương đương makeUniqueTitle() — tránh trùng tên khi sao chép/đổi tên
    fun makeUniqueTitle(base: String, existingTitles: Set<String>): String {
        val candidate = base.trim()
        if (candidate.isEmpty()) return candidate
        if (candidate.lowercase() !in existingTitles) return candidate
        var n = 2
        while ("$candidate ($n)".lowercase() in existingTitles) n++
        return "$candidate ($n)"
    }

    // Tương đương check trùng tên trong effect titleDupWarn / trong auto-save
    fun isDuplicateTitle(title: String, excludeId: String?, allLessons: List<Lesson>): Boolean {
        val t = title.trim().lowercase()
        if (t.isEmpty()) return false
        return allLessons.any { it.id != excludeId && it.title.trim().lowercase() == t }
    }

    @kotlinx.serialization.Serializable
    private data class IdTitle(val id: String, val title: String)

    // FIX: isDuplicateTitle() ở trên dựa vào allLessons truyền từ ngoài vào —
    // đây là 1 snapshot chụp tại thời điểm mở màn Editor, không tự refresh
    // trong lúc soạn bài lâu. Nếu dữ liệu thật trên Supabase đã đổi (bài bị
    // xoá/sửa từ thiết bị khác, tab web khác), app vẫn check trùng với dữ
    // liệu CŨ trong bộ nhớ -> có thể báo trùng tên oan với 1 bài đã không
    // còn tồn tại, chặn lưu vô lý. Hàm này query thẳng Supabase (chỉ 2 cột
    // id+title, nhẹ) ngay trước khi lưu thật, để check đúng dữ liệu hiện tại.
    suspend fun isDuplicateTitleRemote(title: String, excludeId: String?): Boolean {
        val t = title.trim().lowercase()
        if (t.isEmpty()) return false
        val rows = table.select(columns = Columns.list("id", "title"))
            .decodeList<IdTitle>()
        return rows.any { it.id != excludeId && it.title.trim().lowercase() == t }
    }
}

package com.learnsypro.app.data

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * ── VocabRepository ──
 * Tương đương phần load courses/units/words + saveProgress trong
 * vocab-practice.jsx (VocabPractice component). Tải 3 bảng
 * (vocab_courses/vocab_units/vocab_words) song song rồi gộp thủ công
 * thành cây course→unit→vocab, giống hệt logic wordsByUnit/unitsByCourse
 * bên JSX.
 */
class VocabRepository {

    suspend fun fetchCourses(): List<VocabCourse> = coroutineScope {
        withTimeout(8000) {
            val coursesDeferred = async {
                SupabaseClientProvider.client.postgrest["vocab_courses"]
                    .select {
                        order("sort_order", order = Order.ASCENDING)
                        order("created_at", order = Order.DESCENDING)
                    }
                    .decodeList<VocabCourse>()
            }
            val unitsDeferred = async {
                SupabaseClientProvider.client.postgrest["vocab_units"]
                    .select {
                        order("sort_order", order = Order.ASCENDING)
                        order("created_at", order = Order.ASCENDING)
                    }
                    .decodeList<VocabUnit>()
            }
            val wordsDeferred = async {
                SupabaseClientProvider.client.postgrest["vocab_words"]
                    .select {
                        order("sort_order", order = Order.ASCENDING)
                        order("created_at", order = Order.ASCENDING)
                    }
                    .decodeList<VocabWord>()
            }

            val courses = coursesDeferred.await()
            val units = unitsDeferred.await()
            val words = wordsDeferred.await()

            val wordsByUnit = words.groupBy { it.unit_id }
            val unitsByCourse = units
                .map { u -> u.copy(vocab = wordsByUnit[u.id].orEmpty()) }
                .groupBy { it.course_id }

            courses.map { c -> c.copy(units = unitsByCourse[c.id].orEmpty()) }
        }
    }

    /**
     * Lưu tiến độ hoàn thành 1 unit — tương đương saveProgress() trong
     * vocab-practice.jsx. Không kiểm tra "mỗi ngày 1 lần" ở đây (khác với
     * JSX dùng localStorage) — việc chặn trùng lặp do người gọi (ViewModel/
     * Screen) tự quản lý bằng SharedPreferences, xem VocabPracticeScreen.kt.
     */
    suspend fun saveProgress(studentId: String, unitId: String, learnedCount: Int) {
        withTimeout(6000) {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            SupabaseClientProvider.client.postgrest["vocab_progress"]
                .insert(
                    VocabProgressInsert(
                        id = UUID.randomUUID().toString(),
                        student_id = studentId,
                        unit_id = unitId,
                        date = today,
                        vocab_count = learnedCount
                    )
                )
        }
    }
}

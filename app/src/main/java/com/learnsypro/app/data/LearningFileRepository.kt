package com.learnsypro.app.data

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable

/**
 * ── LearningFile ──
 * Tương đương 1 dòng bảng `learning_files` (Supabase) trong files-tab.jsx.
 * `path` là public URL trong Supabase Storage (bucket "learning_files"),
 * dùng trực tiếp để tải/preview — không cần storage_path phía app (chỉ
 * admin cần để xoá).
 */
@Serializable
data class LearningFile(
    val id: String,
    val title: String,
    val description: String? = null,
    val filename: String,
    val path: String,
    val size: Long = 0,
    val subject: String? = null,
    val sort_order: Int = 0,
    val created_at: String? = null
)

/**
 * ── LearningFileRepository ──
 * Tải danh sách tài liệu học sinh (bảng `learning_files`) từ Supabase.
 * Tương đương fetchFiles() trong files-tab.jsx — sắp theo sort_order rồi
 * created_at giảm dần. Cùng chiến lược timeout 6s như LessonRepository để
 * tránh treo màn hình khi mất mạng.
 */
class LearningFileRepository {
    suspend fun fetchFiles(): List<LearningFile> {
        return withTimeout(6000) {
            SupabaseClientProvider.client.postgrest["learning_files"]
                .select {
                    order("sort_order", order = Order.ASCENDING)
                    order("created_at", order = Order.DESCENDING)
                }
                .decodeList<LearningFile>()
        }
    }
}

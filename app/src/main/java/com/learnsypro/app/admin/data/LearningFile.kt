package com.learnsypro.app.admin.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Tương đương bảng learning_files trong file-manager.jsx (admin web).
// File thật lưu trong Supabase Storage, bucket "learning_files".
@Serializable
data class LearningFile(
    val id: String,
    val title: String = "",
    val description: String = "",
    val filename: String = "",
    val path: String = "",
    @SerialName("storage_path") val storagePath: String = "",
    val size: Long = 0,
    val subject: String = "",
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("created_at") val createdAt: String = ""
)

// FIX ("invalid input syntax for type" khi Thêm/Sửa tài liệu): LearningFile.createdAt mặc định
// là "" (rỗng) — nhưng SupabaseClientProvider bật encodeDefaults=true nên field này VẪN được gửi
// lên trong payload insert/update dù không set, và cột created_at trên Postgres là kiểu
// timestamptz nên Postgres không parse được chuỗi rỗng "" thành timestamp -> lỗi 400 "invalid
// input syntax for type timestamp with time zone". Web (file-manager.jsx) không gặp lỗi này vì
// JS chỉ gửi đúng những field có mặt trong object literal, không tự thêm field rỗng như
// encodeDefaults=true bên Kotlin.
// Tách riêng payload gửi lên khi INSERT: không có id/created_at (để Postgres tự sinh qua
// default gen_random_uuid()/now() đã khai trong SQL của bảng), chỉ gửi đúng các cột thật sự cần
// ghi.
@Serializable
data class LearningFileInsert(
    val title: String = "",
    val description: String = "",
    val filename: String = "",
    val path: String = "",
    @SerialName("storage_path") val storagePath: String = "",
    val size: Long = 0,
    val subject: String = "",
    @SerialName("sort_order") val sortOrder: Int = 0
)

// FIX cùng lý do LearningFileInsert ở trên, áp dụng cho nhánh "sửa + đổi file mới"
// (repo.replaceFile()): update() bằng nguyên LearningFile sẽ gửi kèm created_at="" trong payload
// PATCH, ghi đè created_at thật đang lưu trên server thành chuỗi rỗng không hợp lệ. Payload
// update KHÔNG có id/created_at — filter theo id đã tách riêng qua .eq("id", ...), không cần gửi
// lại id, và không được đụng tới created_at.
@Serializable
data class LearningFileUpdate(
    val title: String = "",
    val description: String = "",
    val filename: String = "",
    val path: String = "",
    @SerialName("storage_path") val storagePath: String = "",
    val size: Long = 0,
    val subject: String = ""
)

const val LEARNING_FILE_BUCKET = "learning_files"
const val LEARNING_FILE_MAX_MB = 50

private val EXT_COLORS = mapOf(
    "pdf" to 0xFFEF4444, "doc" to 0xFF3B82F6, "docx" to 0xFF3B82F6,
    "xls" to 0xFF22C55E, "xlsx" to 0xFF22C55E, "ppt" to 0xFFF97316, "pptx" to 0xFFF97316,
    "zip" to 0xFFA855F7, "rar" to 0xFFA855F7,
    "mp4" to 0xFF06B6D4, "mp3" to 0xFF06B6D4,
    "jpg" to 0xFFF59E0B, "jpeg" to 0xFFF59E0B, "png" to 0xFFF59E0B, "gif" to 0xFFF59E0B, "webp" to 0xFFF59E0B
)

fun fileExtColor(ext: String): androidx.compose.ui.graphics.Color =
    androidx.compose.ui.graphics.Color(EXT_COLORS[ext.lowercase()]?.toInt()?.toLong() ?: 0xFF9CA3AFL)

fun getFileExt(name: String): String = name.substringAfterLast('.', "").lowercase()

fun fmtFileBytes(n: Long): String = when {
    n <= 0 -> ""
    n < 1024 -> "$n B"
    n < 1024 * 1024 -> "%.1f KB".format(n / 1024.0)
    else -> "%.1f MB".format(n / (1024.0 * 1024.0))
}

fun sanitizeFilename(name: String): String =
    name.ifBlank { "file" }.replace(Regex("[^\\w.\\-]+"), "_").replace(Regex("_+"), "_")

fun genFileId(): String = "f${System.currentTimeMillis()}_${(0..99999).random()}"

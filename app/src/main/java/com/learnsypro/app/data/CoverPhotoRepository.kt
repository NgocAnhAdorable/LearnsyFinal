package com.learnsypro.app.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

val Context.coverPhotoDataStore by preferencesDataStore(name = "learnsy_cover_cache")

private const val COVER_BUCKET = "covers"

/**
 * ── CoverPhotoRepository ──
 * Y hệt cấu trúc AvatarRepository (xem file đó để biết lý do KHÔNG dùng Upstash — cùng lý do
 * áp dụng ở đây: publicUrl() là URL công khai cố định, DataStore local là đủ) nhưng bucket
 * riêng "covers" — ảnh bìa không đi qua compressAvatar() (vốn ép vuông 256x256 cho avatar); ảnh
 * bìa được crop theo tỉ lệ ngang (16:9, xem CoverCropActivity/CropUtils) ở bước UI TRƯỚC khi
 * gọi uploadCover(), nên repository này chỉ upload thẳng byte đã crop sẵn, không tự nén/crop gì
 * thêm — tách trách nhiệm rõ ràng: UI lo hình dạng/crop, repository chỉ lo lưu trữ.
 */
class CoverPhotoRepository(
    private val context: Context
) {
    private fun cacheKey(userId: String) = stringPreferencesKey("ls_cover_$userId")

    suspend fun getCoverUrl(userId: String): String? {
        val local = context.coverPhotoDataStore.data.first()[cacheKey(userId)]
        if (!local.isNullOrBlank()) return local

        val fromStorage = try {
            SupabaseClientProvider.client.storage
                .from(COVER_BUCKET)
                .publicUrl("covers/$userId.jpg")
        } catch (e: Exception) {
            null
        }
        if (!fromStorage.isNullOrBlank()) {
            context.coverPhotoDataStore.edit { it[cacheKey(userId)] = fromStorage }
        }
        return fromStorage
    }

    /**
     * Upload ảnh bìa ĐÃ CROP SẴN (từ CoverCropActivity/uCrop) — nhận thẳng [croppedUri] (file
     * tạm uCrop xuất ra), đọc bytes rồi đẩy lên Supabase Storage bucket "covers".
     */
    suspend fun uploadCover(userId: String, croppedUri: Uri): AvatarUploadResult = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(croppedUri)?.use { it.readBytes() }
                ?: return@withContext AvatarUploadResult(ok = false, msg = "Không đọc được ảnh đã crop")
            val path = "covers/$userId.jpg"

            SupabaseClientProvider.client.storage
                .from(COVER_BUCKET)
                .upload(path, bytes, upsert = true)

            val baseUrl = SupabaseClientProvider.client.storage
                .from(COVER_BUCKET)
                .publicUrl(path)
            val publicUrl = "$baseUrl?t=${System.currentTimeMillis()}"

            context.coverPhotoDataStore.edit { it[cacheKey(userId)] = publicUrl }

            AvatarUploadResult(ok = true, sizeBytes = bytes.size)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            val friendly = if (msg.lowercase().contains("bucket")) {
                "Lỗi storage: tạo bucket \"covers\" trong Supabase nhé!"
            } else {
                msg.ifBlank { "Upload thất bại, thử lại nhé!" }
            }
            AvatarUploadResult(ok = false, msg = friendly)
        }
    }

    suspend fun removeCover(userId: String): AvatarUploadResult {
        try {
            val path = "covers/$userId.jpg"
            SupabaseClientProvider.client.storage.from(COVER_BUCKET).delete(path)
        } catch (e: Exception) {
            // Silent — vẫn xóa cache local như AvatarRepository
        }
        context.coverPhotoDataStore.edit { it.remove(cacheKey(userId)) }
        return AvatarUploadResult(ok = true)
    }
}

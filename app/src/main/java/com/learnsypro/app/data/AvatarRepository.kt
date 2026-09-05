package com.learnsypro.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

val Context.avatarDataStore by preferencesDataStore(name = "learnsy_avatar_cache")

private const val AVATAR_BUCKET = "avatars"
private const val AVATAR_SIZE = 256          // output px (square)
private const val TARGET_BYTES = 80 * 1024   // 80 KB

data class AvatarUploadResult(val ok: Boolean, val sizeBytes: Int? = null, val msg: String? = null)

/**
 * ── AvatarRepository (thay window.useAvatar trong avatar.jsx) ──
 *
 * Luồng:
 * 1. compressAvatar()  — center-crop hình vuông + giảm chất lượng dần đến ≤80KB
 * 2. upload()          — đẩy lên Supabase Storage bucket "avatars" (public URL, không hết hạn)
 * 3. cache             — chỉ DataStore local (KHÔNG còn qua Upstash — xem ghi chú dưới)
 *
 * KHÔNG còn phụ thuộc UpstashClient: trước đây avatar được cache thêm 1 lớp qua Upstash (30
 * ngày) bên cạnh DataStore local, nhưng vì uploadAvatar() dùng publicUrl() (URL công khai CỐ
 * ĐỊNH của Supabase Storage, không phải signed URL có hạn), lớp cache Upstash không mang lại
 * lợi ích thật — publicUrl() không đổi giữa các lần gọi nên không cần "cache lại URL" để tránh
 * gọi lại API, chỉ DataStore local (nhanh, luôn có sẵn ngay cả khi mất mạng) là đủ. Bỏ Upstash
 * khỏi luồng avatar để giữ Upstash chỉ phục vụ đúng 1 việc: cache dữ liệu bài học/nghe (nơi
 * việc cache thật sự cần thiết vì tránh gọi lại Supabase liên tục cho nội dung lớn, ít đổi).
 * Khi cache local trống (cài lại app, xóa cache thủ công, avatar set từ nơi khác như admin
 * panel...), getAvatarUrl() tự lấy lại publicUrl() thẳng từ Supabase Storage — không cần bước
 * trung gian nào, vì đằng nào Supabase cũng phải được hỏi nếu Upstash cũng trống.
 */
class AvatarRepository(
    private val context: Context
) {
    private fun cacheKey(userId: String) = stringPreferencesKey("ls_avatar_$userId")

    /**
     * Đọc avatar URL: ưu tiên cache local (DataStore), nếu trống thì lấy thẳng publicUrl() từ
     * Supabase Storage (URL công khai cố định, không cần signed URL/hết hạn) rồi cache lại vào
     * DataStore cho lần sau. Trả về null nếu chưa từng có avatar nào được upload.
     */
    suspend fun getAvatarUrl(userId: String): String? {
        val local = context.avatarDataStore.data.first()[cacheKey(userId)]
        if (!local.isNullOrBlank()) return local

        val fromStorage = try {
            val path = "avatars/$userId.jpg"
            SupabaseClientProvider.client.storage
                .from(AVATAR_BUCKET)
                .publicUrl(path)
        } catch (e: Exception) {
            null
        }
        if (!fromStorage.isNullOrBlank()) {
            context.avatarDataStore.edit { it[cacheKey(userId)] = fromStorage }
        }
        return fromStorage
    }

    /**
     * Nén ảnh: center-crop thành hình vuông AVATAR_SIZE x AVATAR_SIZE,
     * giảm quality JPEG dần 0.05 mỗi vòng cho đến khi ≤ TARGET_BYTES hoặc quality ≤ 0.40.
     * Quality khởi điểm: 0.92 nếu ảnh < 1 megapixel, ngược lại 0.82 (giống bản gốc).
     */
    private suspend fun compressAvatar(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Không mở được ảnh")
        val original = BitmapFactory.decodeStream(input)
        input.close()

        val sw = original.width
        val sh = original.height
        val side = minOf(sw, sh)
        val sx = (sw - side) / 2
        val sy = (sh - side) / 2

        val cropped = Bitmap.createBitmap(original, sx, sy, side, side)
        val scaled = Bitmap.createScaledBitmap(cropped, AVATAR_SIZE, AVATAR_SIZE, true)

        val megapixels = (sw * sh) / 1_000_000.0
        var quality = if (megapixels < 1) 92 else 82

        var bytes: ByteArray
        while (true) {
            val stream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            bytes = stream.toByteArray()
            if (bytes.size <= TARGET_BYTES || quality <= 40) break
            quality -= 5
        }

        if (cropped != original) cropped.recycle()
        if (scaled != cropped) scaled.recycle()
        original.recycle()

        bytes
    }

    /**
     * Upload avatar: nén ảnh → Supabase Storage → cache DataStore local.
     * Trả về AvatarUploadResult tương đương {ok,size,msg} của bản web.
     */
    suspend fun uploadAvatar(userId: String, imageUri: Uri): AvatarUploadResult {
        return try {
            val bytes = compressAvatar(imageUri)
            val path = "avatars/$userId.jpg"

            SupabaseClientProvider.client.storage
                .from(AVATAR_BUCKET)
                .upload(path, bytes, upsert = true)

            val baseUrl = SupabaseClientProvider.client.storage
                .from(AVATAR_BUCKET)
                .publicUrl(path)
            val publicUrl = "$baseUrl?t=${System.currentTimeMillis()}"

            context.avatarDataStore.edit { it[cacheKey(userId)] = publicUrl }

            AvatarUploadResult(ok = true, sizeBytes = bytes.size)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            val friendly = if (msg.lowercase().contains("bucket")) {
                "Lỗi storage: tạo bucket \"avatars\" trong Supabase nhé!"
            } else {
                msg.ifBlank { "Upload thất bại, thử lại nhé!" }
            }
            AvatarUploadResult(ok = false, msg = friendly)
        }
    }

    /** Xóa avatar: Supabase Storage + DataStore local (xóa local dù có lỗi hay không). */
    suspend fun removeAvatar(userId: String): AvatarUploadResult {
        try {
            val path = "avatars/$userId.jpg"
            SupabaseClientProvider.client.storage.from(AVATAR_BUCKET).delete(path)
        } catch (e: Exception) {
            // Silent — vẫn xóa cache local như bản gốc
        }
        context.avatarDataStore.edit { it.remove(cacheKey(userId)) }
        return AvatarUploadResult(ok = true)
    }
}

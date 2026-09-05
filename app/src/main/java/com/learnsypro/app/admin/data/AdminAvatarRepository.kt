package com.learnsypro.app.admin.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

// Tương đương resizeImage() + doUploadAvatar() trong student-manager.jsx.
// Bucket 'avatars', path avatars/<student_id>.jpg — giữ nguyên convention với bản web.
class AdminAvatarRepository(private val context: Context) {
    private val bucket = SupabaseConfig.client.storage.from("avatars")

    // Tương đương resizeImage(file, 256) — crop vuông ở giữa, resize còn 256x256, JPEG q=0.88
    suspend fun resizeSquare(uri: Uri, size: Int = 256): ByteArray = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
        val original = BitmapFactory.decodeStream(input)
        input?.close()

        val s = minOf(original.width, original.height)
        val sx = (original.width - s) / 2
        val sy = (original.height - s) / 2
        val cropped = Bitmap.createBitmap(original, sx, sy, s, s)
        val resized = Bitmap.createScaledBitmap(cropped, size, size, true)

        val out = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 88, out)
        out.toByteArray()
    }

    fun validate(bytes: ByteArray): String? {
        if (bytes.size > 2 * 1024 * 1024) return "Ảnh tối đa 2MB!"
        return null
    }

    suspend fun upload(studentId: String, jpegBytes: ByteArray) {
        bucket.upload("avatars/$studentId.jpg", jpegBytes, upsert = true)
    }

    suspend fun getSignedUrl(studentId: String, expiresInSeconds: Int = 3600): String? =
        try {
            bucket.createSignedUrl("avatars/$studentId.jpg", kotlin.time.Duration.parse("${expiresInSeconds}s"))
        } catch (e: Exception) {
            null
        }

    suspend fun delete(studentId: String) {
        try { bucket.delete("avatars/$studentId.jpg") } catch (e: Exception) { }
    }
}

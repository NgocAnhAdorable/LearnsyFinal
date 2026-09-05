package com.learnsypro.app.filemanager.cloud
import com.learnsypro.app.R

import android.content.Context
import com.learnsypro.app.filemanager.model.CloudProvider
import com.learnsypro.app.filemanager.model.RemoteFile
import com.learnsypro.app.filemanager.util.SecurePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class BoxService(private val context: Context) : CloudFileService {

    private val api by lazy { RetrofitFactory.box(context) }
    private val prefs = SecurePrefs.getInstance(context)

    override suspend fun isLinked(): Boolean =
        !prefs.getCloudAccessToken(CloudProvider.BOX).isNullOrEmpty()

    override suspend fun listFiles(folderId: String): Result<List<RemoteFile>> = withContext(Dispatchers.IO) {
        try {
            val id = folderId.ifBlank { "0" } // "0" = thư mục gốc trong Box API
            val response = api.listItems(id)
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Lỗi Box: ${response.code()}"))
            }
            val items = response.body()?.entries.orEmpty().map {
                RemoteFile(
                    name = it.name,
                    path = it.name,
                    isDirectory = it.type == "folder",
                    size = it.size,
                    cloudFileId = it.id
                )
            }
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadFile(localFile: File, parentId: String, onProgress: UploadProgressListener?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = RetrofitFactory.okHttpFor(context, CloudProvider.BOX)
            val parent = parentId.ifBlank { "0" }
            val attributes = """{"name":"${localFile.name}","parent":{"id":"$parent"}}"""
            var requestBody: RequestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("attributes", attributes)
                .addFormDataPart(
                    "file", localFile.name,
                    localFile.asRequestBody("application/octet-stream".toMediaTypeOrNull())
                )
                .build()
            if (onProgress != null) {
                // contentLength() của MultipartBody đã cộng gộp cả phần "attributes" JSON nhỏ
                // lẫn phần "file" nặng — bọc NGOÀI CÙNG multipart (thay vì chỉ bọc riêng phần
                // file bên trong) để % báo ra đúng bằng % của TOÀN BỘ request thật sự gửi lên
                // socket, không lệch đi vài KB do bỏ sót phần metadata.
                requestBody = ProgressRequestBody(requestBody, onProgress)
            }
            val request = Request.Builder()
                .url("https://upload.box.com/api/2.0/files/content")
                .post(requestBody)
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("Tải lên Box thất bại: ${response.code}"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun downloadFile(cloudFileId: String, destination: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = api.downloadFile(cloudFileId)
            if (!response.isSuccessful) return@withContext Result.failure(Exception("Tải xuống Box thất bại"))
            response.body()?.byteStream()?.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteFile(cloudFileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Thử xóa như file trước; nếu lỗi thì thử như folder (đơn giản hóa;
            // trong thực tế nên biết trước type từ RemoteFile.isDirectory).
            val response = api.deleteFile(cloudFileId)
            if (!response.isSuccessful) {
                val folderResponse = api.deleteFolder(cloudFileId)
                if (!folderResponse.isSuccessful) return@withContext Result.failure(Exception("Xóa thất bại"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createFolder(name: String, parentId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val parent = parentId.ifBlank { "0" }
            val body = mapOf("name" to name, "parent" to mapOf("id" to parent))
            val response = api.createFolder(body)
            if (!response.isSuccessful) return@withContext Result.failure(Exception("Tạo thư mục thất bại"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun unlink() {
        prefs.clearCloudToken(CloudProvider.BOX)
    }

    override suspend fun getStorageQuota(): Result<CloudStorageQuota> = withContext(Dispatchers.IO) {
        try {
            val response = api.getCurrentUser()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("Lỗi lấy dung lượng Box: ${response.code()}"))
            val user = response.body()
            Result.success(CloudStorageQuota(usedBytes = user?.space_used ?: 0L, totalBytes = user?.space_amount ?: 0L))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * RemoteFile không mang theo isDirectory qua tới cloudFileId (chỉ có id) nên không biết chắc
     * đây là file hay folder chỉ từ id — thử endpoint file trước (trường hợp phổ biến hơn), nếu
     * lỗi thì thử endpoint folder, giống đúng cách deleteFile() ở trên đã xử lý.
     */
    override suspend fun renameFile(cloudFileId: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = mapOf("name" to newName)
            val response = api.updateFile(cloudFileId, body)
            if (!response.isSuccessful) {
                val folderResponse = api.updateFolder(cloudFileId, body)
                if (!folderResponse.isSuccessful) {
                    return@withContext Result.failure(Exception("${context.getString(com.learnsypro.app.R.string.cloud_rename_failed)}: ${folderResponse.code()}"))
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getShareLink(cloudFileId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // "access":"open" = ai có link cũng xem được, không cần đăng nhập Box — phù hợp với
            // hành vi chia sẻ nhanh của Drive/Dropbox ở trên (cả 2 đều tạo link công khai xem-được).
            val body = mapOf("shared_link" to mapOf("access" to "open"))
            var response = api.updateFile(cloudFileId, body)
            if (!response.isSuccessful) {
                response = api.updateFolder(cloudFileId, body)
            }
            val url = response.body()?.shared_link?.url
                ?: return@withContext Result.failure(Exception(context.getString(com.learnsypro.app.R.string.cloud_share_link_failed)))
            Result.success(url)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getThumbnailRequest(file: RemoteFile): Pair<String, Map<String, String>>? {
        if (file.isDirectory || !com.learnsypro.app.filemanager.util.FileTypeUtils.isImageOrVideoName(file.name)) return null
        val cloudId = file.cloudFileId ?: return null
        val token = prefs.getCloudAccessToken(CloudProvider.BOX) ?: return null
        // Box có endpoint /thumbnail.{ext} riêng cho ảnh nhỏ, nhưng chỉ hỗ trợ 1 số định dạng cụ
        // thể (png/jpg) và có thể trả 202 (đang xử lý, cần đợi) thay vì ảnh ngay — dùng thẳng
        // /content (ảnh gốc) cho đơn giản và luôn có sẵn ngay lập tức, giống cách Drive/Dropbox
        // ở trên cũng đều dùng endpoint tải ảnh gốc thay vì thumbnail chuyên dụng.
        val url = "https://api.box.com/2.0/files/$cloudId/content"
        return url to mapOf("Authorization" to "Bearer $token")
    }

    override suspend fun getStreamRequest(file: RemoteFile): Pair<String, Map<String, String>>? {
        if (file.isDirectory) return null
        val cloudId = file.cloudFileId ?: return null
        val token = prefs.getCloudAccessToken(CloudProvider.BOX) ?: return null
        // Cùng endpoint với getThumbnailRequest() ở trên, chỉ khác không giới hạn ảnh/video —
        // audio cũng cần qua đây để ExoPlayer stream trực tiếp thay vì downloadFile() trước.
        val url = "https://api.box.com/2.0/files/$cloudId/content"
        return url to mapOf("Authorization" to "Bearer $token")
    }
}

package com.learnsypro.app.filemanager.cloud
import com.learnsypro.app.R

import android.content.Context
import com.learnsypro.app.filemanager.model.CloudProvider
import com.learnsypro.app.filemanager.model.RemoteFile
import com.learnsypro.app.filemanager.util.SecurePrefs
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/**
 * Dropbox API v2 dùng đường dẫn logic (path), không dùng id như Box cho hầu hết thao tác,
 * ngoại trừ download/upload nội dung phải gọi qua content.dropboxapi.com với header Dropbox-API-Arg.
 */
class DropboxService(private val context: Context) : CloudFileService {

    private val api by lazy { RetrofitFactory.dropbox(context) }
    private val prefs = SecurePrefs.getInstance(context)
    private val gson = Gson()

    override suspend fun isLinked(): Boolean =
        !prefs.getCloudAccessToken(CloudProvider.DROPBOX).isNullOrEmpty()

    override suspend fun listFiles(folderId: String): Result<List<RemoteFile>> = withContext(Dispatchers.IO) {
        try {
            // folderId ở Dropbox chính là path, rỗng = "" (thư mục gốc)
            val response = api.listFolder(DropboxListFolderRequest(path = folderId))
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Lỗi Dropbox (${response.code()}): ${response.errorBody()?.string().orEmpty()}"))
            }
            val items = response.body()?.entries.orEmpty().map { entry ->
                RemoteFile(
                    name = entry.name,
                    path = entry.path_lower ?: entry.name,
                    isDirectory = entry.id?.startsWith("id:") == true && entry.size == 0L && entry.server_modified == null,
                    size = entry.size,
                    cloudFileId = entry.path_lower
                )
            }
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadFile(localFile: File, parentId: String, onProgress: UploadProgressListener?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = RetrofitFactory.okHttpFor(context, CloudProvider.DROPBOX)
            val destPath = if (parentId.isBlank()) "/${localFile.name}" else "$parentId/${localFile.name}"
            val apiArg = """{"path":"$destPath","mode":"overwrite","autorename":false,"mute":false}"""
            var body: RequestBody = localFile.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            if (onProgress != null) {
                body = ProgressRequestBody(body, onProgress)
            }
            val request = Request.Builder()
                .url("https://content.dropboxapi.com/2/files/upload")
                .addHeader("Dropbox-API-Arg", apiArg)
                .addHeader("Content-Type", "application/octet-stream")
                .post(body)
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("Tải lên Dropbox thất bại (${response.code}): ${response.body?.string().orEmpty()}"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun downloadFile(cloudFileId: String, destination: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = RetrofitFactory.okHttpFor(context, CloudProvider.DROPBOX)
            val apiArg = """{"path":"$cloudFileId"}"""
            val request = Request.Builder()
                .url("https://content.dropboxapi.com/2/files/download")
                .addHeader("Dropbox-API-Arg", apiArg)
                .post(RequestBody.create(null, ByteArray(0)))
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("Tải xuống Dropbox thất bại (${response.code}): ${response.body?.string().orEmpty()}"))
            response.body?.byteStream()?.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteFile(cloudFileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = api.delete(DropboxDeleteRequest(path = cloudFileId))
            if (!response.isSuccessful) return@withContext Result.failure(Exception("Xóa thất bại (${response.code()}): ${response.errorBody()?.string().orEmpty()}"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createFolder(name: String, parentId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val path = if (parentId.isBlank()) "/$name" else "$parentId/$name"
            val response = api.createFolder(DropboxCreateFolderRequest(path))
            if (!response.isSuccessful) return@withContext Result.failure(Exception("Tạo thư mục thất bại (${response.code()}): ${response.errorBody()?.string().orEmpty()}"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun unlink() {
        prefs.clearCloudToken(CloudProvider.DROPBOX)
    }

    override suspend fun getStorageQuota(): Result<CloudStorageQuota> = withContext(Dispatchers.IO) {
        try {
            val response = api.getSpaceUsage()
            if (!response.isSuccessful) return@withContext Result.failure(Exception("Lỗi lấy dung lượng Dropbox: ${response.code()}"))
            val usage = response.body()
            Result.success(CloudStorageQuota(usedBytes = usage?.used ?: 0L, totalBytes = usage?.allocation?.allocated ?: 0L))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** cloudFileId ở Dropbox chính là path đầy đủ (path_lower) — đổi tên = move sang cùng thư mục cha, tên mới. */
    override suspend fun renameFile(cloudFileId: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val parentPath = cloudFileId.substringBeforeLast('/', "")
            val newPath = if (parentPath.isBlank()) "/$newName" else "$parentPath/$newName"
            val response = api.move(DropboxMoveRequest(from_path = cloudFileId, to_path = newPath))
            if (!response.isSuccessful) return@withContext Result.failure(Exception("${context.getString(com.learnsypro.app.R.string.cloud_rename_failed)}: ${response.errorBody()?.string().orEmpty()}"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getShareLink(cloudFileId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val createResponse = api.createSharedLink(DropboxCreateSharedLinkRequest(path = cloudFileId))
            if (createResponse.isSuccessful) {
                val url = createResponse.body()?.url
                    ?: return@withContext Result.failure(Exception(context.getString(com.learnsypro.app.R.string.cloud_share_link_failed)))
                return@withContext Result.success(url)
            }
            // Link đã tồn tại từ trước (409 shared_link_already_exists) -> lấy lại link cũ thay vì báo lỗi.
            val errorBody = createResponse.errorBody()?.string().orEmpty()
            if (createResponse.code() == 409 && errorBody.contains("shared_link_already_exists")) {
                val listResponse = api.listSharedLinks(DropboxListSharedLinksRequest(path = cloudFileId))
                val existing = listResponse.body()?.links?.firstOrNull()?.url
                if (existing != null) return@withContext Result.success(existing)
            }
            Result.failure(Exception("${context.getString(com.learnsypro.app.R.string.cloud_share_link_failed)}: $errorBody"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getThumbnailRequest(file: RemoteFile): Pair<String, Map<String, String>>? {
        if (file.isDirectory || !com.learnsypro.app.filemanager.util.FileTypeUtils.isImageOrVideoName(file.name)) return null
        val cloudId = file.cloudFileId ?: return null
        val token = prefs.getCloudAccessToken(CloudProvider.DROPBOX) ?: return null
        // Không có endpoint thumbnail cỡ nhỏ nào chấp nhận GET thuần với query token — Dropbox
        // bắt buộc header Dropbox-API-Arg (chứa path dạng JSON) trên CHÍNH endpoint download ảnh
        // gốc, nên header trả về đây gồm cả Authorization lẫn Dropbox-API-Arg để Coil gắn đúng
        // khi tải, tương tự cách uploadFile()/downloadFile() ở trên đã dùng 2 header này.
        val apiArg = """{"path":"$cloudId"}"""
        val url = "https://content.dropboxapi.com/2/files/download"
        return url to mapOf(
            "Authorization" to "Bearer $token",
            "Dropbox-API-Arg" to apiArg
        )
    }

    override suspend fun getStreamRequest(file: RemoteFile): Pair<String, Map<String, String>>? {
        if (file.isDirectory) return null
        val cloudId = file.cloudFileId ?: return null
        val token = prefs.getCloudAccessToken(CloudProvider.DROPBOX) ?: return null
        // Cùng endpoint/header với getThumbnailRequest() ở trên, chỉ khác là KHÔNG giới hạn
        // isImageOrVideoName — audio (mp3, m4a...) cũng cần qua đây để ExoPlayer stream được.
        val apiArg = """{"path":"$cloudId"}"""
        val url = "https://content.dropboxapi.com/2/files/download"
        return url to mapOf(
            "Authorization" to "Bearer $token",
            "Dropbox-API-Arg" to apiArg
        )
    }
}

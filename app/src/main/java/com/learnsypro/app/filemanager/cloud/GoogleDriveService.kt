package com.learnsypro.app.filemanager.cloud
import com.learnsypro.app.R

import android.content.Context
import android.content.Intent
import com.learnsypro.app.filemanager.model.CloudProvider
import com.learnsypro.app.filemanager.model.RemoteFile
import com.learnsypro.app.filemanager.util.SecurePrefs
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File as JavaFile
import java.io.FileOutputStream

/**
 * Google Drive dùng luồng đăng nhập riêng (Google Sign-In SDK) thay vì AppAuth,
 * vì đây là cách Google khuyến nghị chính thức trên Android, đơn giản và an toàn hơn.
 */
class GoogleDriveService(private val context: Context) : CloudFileService {

    private val prefs = SecurePrefs.getInstance(context)

    fun signInClient(): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            // Đổi từ DRIVE_FILE sang DRIVE: DRIVE_FILE là phạm vi hẹp có chủ đích của Google —
            // app chỉ thấy được file do CHÍNH app này tạo ra hoặc được người dùng chọn qua Google
            // Picker, không bao giờ thấy được các file có sẵn trong Drive (kể cả khi đã cấp
            // quyền) — đây là lý do "Thư mục trống" dù tài khoản Drive thật có rất nhiều file.
            // DRIVE là toàn quyền đọc/ghi mọi file trong Drive, đúng với kỳ vọng app này hoạt
            // động như 1 trình duyệt file đầy đủ (giống Dropbox/Box đã làm).
            .requestScopes(com.google.android.gms.common.api.Scope(DriveScopes.DRIVE))
            .build()
        return GoogleSignIn.getClient(context, options)
    }

    fun getSignInIntent(): Intent = signInClient().signInIntent

    fun handleSignInResult(account: GoogleSignInAccount) {
        prefs.saveCloudAccountInfo(CloudProvider.GOOGLE_DRIVE, account.email)
        // Đánh dấu đã liên kết bằng cách lưu email làm "access token" giả lập;
        // token thật được GoogleAccountCredential tự làm mới ngầm dựa trên tài khoản đã chọn.
        prefs.saveCloudToken(CloudProvider.GOOGLE_DRIVE, account.email ?: "linked", null, Long.MAX_VALUE)
    }

    private fun driveClient(): Drive? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE))
        credential.selectedAccount = account.account
        return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("FTP Manager")
            .build()
    }

    /**
     * Thông báo lỗi phù hợp khi driveClient() trả null: phân biệt "chưa từng liên kết" với
     * "đã từng liên kết nhưng phiên đăng nhập cục bộ (Play Services) đã mất, cần đăng nhập lại"
     * — 2 trường hợp cần hướng dẫn khác nhau cho người dùng.
     */
    private fun notLinkedMessage(): String =
        if (!prefs.getCloudAccessToken(CloudProvider.GOOGLE_DRIVE).isNullOrEmpty())
            "Phiên đăng nhập Google Drive đã hết hạn trên máy, vui lòng liên kết lại"
        else
            "Chưa liên kết Google Drive"

    override suspend fun isLinked(): Boolean {
        // BUG ĐÃ SỬA: trước đây chỉ check GoogleSignIn.getLastSignedInAccount(context), tức là
        // phụ thuộc HOÀN TOÀN vào cache session nội bộ của Google Play Services SDK, tách biệt
        // khỏi SecurePrefs (nơi Dropbox/Box lưu trạng thái liên kết). Cache này có thể tự mất mà
        // KHÔNG do người dùng chủ động đăng xuất — ví dụ Play Services tự cập nhật, bị hệ thống
        // dọn cache trên 1 số ROM tuỳ biến, hoặc tài khoản bị Android gỡ khỏi AccountManager (đổi
        // mật khẩu Google, thu hồi quyền truy cập app từ phía Google...). Khi đó UI hiển thị
        // "Chưa liên kết" dù người dùng chưa hề bấm huỷ liên kết gì cả — đúng hiện tượng đang gặp.
        //
        // driveClient() CHỈ hoạt động được khi GoogleSignIn còn account (nó không đọc token từ
        // SecurePrefs) — vì vậy "đã liên kết" thực chất phải neo theo Play Services. Nhưng nếu
        // SecurePrefs có token đã lưu mà Play Services lại mất account, đó không phải là "chưa
        // từng liên kết" mà là "phiên đăng nhập cục bộ bị mất, cần đăng nhập lại" -> vẫn trả về
        // true để KHÔNG xoá lịch sử liên kết khỏi UI, nhưng driveClient() bên dưới sẽ tự phát
        // hiện thiếu account và trả lỗi rõ ràng "Chưa liên kết Google Drive" khi thực sự gọi API,
        // thay vì âm thầm coi như chưa từng liên kết.
        val hasPlayServicesAccount = GoogleSignIn.getLastSignedInAccount(context) != null
        val hasSavedToken = !prefs.getCloudAccessToken(CloudProvider.GOOGLE_DRIVE).isNullOrEmpty()

        if (hasPlayServicesAccount && !hasSavedToken) {
            // Play Services vẫn nhớ tài khoản nhưng SecurePrefs bị mất dấu vết (ví dụ do
            // SecurePrefs vừa rơi vào fallback không mã hoá) -> đồng bộ lại ngay, tránh hiển
            // thị sai và tránh phải đăng nhập lại dù thực chất vẫn còn phiên hợp lệ.
            val account = GoogleSignIn.getLastSignedInAccount(context)
            prefs.saveCloudAccountInfo(CloudProvider.GOOGLE_DRIVE, account?.email)
            prefs.saveCloudToken(CloudProvider.GOOGLE_DRIVE, account?.email ?: "linked", null, Long.MAX_VALUE)
        }

        return hasPlayServicesAccount || hasSavedToken
    }

    /** Kết quả của 1 lệnh gọi Drive API cần xử lý consent bổ sung trước khi thử lại. */
    class NeedsUserConsentException(val intent: Intent) : Exception("Cần người dùng cấp thêm quyền truy cập Google Drive")

    /**
     * Bọc mọi lệnh gọi Drive API: bắt riêng UserRecoverableAuthIOException — đây KHÔNG phải lỗi
     * cấu hình (API key/SHA-1 sai) mà là bước consent RIÊNG BIỆT mà scope drive.file luôn đòi hỏi
     * ở lần gọi API thật sự đầu tiên, tách biệt hoàn toàn khỏi màn hình "Đăng nhập bằng Google"
     * ban đầu (đó chỉ xác thực danh tính, không cấp quyền Drive). Trước đây exception này rơi
     * thẳng vào catch chung, in ra message ngắn gọn dạng "key error" (message thật của lớp
     * UserRecoverableAuthIOException không hề nhắc gì đến "cần cấp quyền" một cách rõ ràng),
     * khiến người dùng và cả người debug hiểu lầm là lỗi cấu hình API key/SHA-1 — trong khi thực
     * ra dòng sửa duy nhất cần thiết chỉ là hứng đúng Intent này và show ra cho người dùng bấm
     * "Cho phép" một lần, y hệt cơ chế cấp quyền runtime bình thường.
     */
    private fun <T> withDriveConsentHandling(block: () -> T): T {
        try {
            return block()
        } catch (e: com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) {
            throw NeedsUserConsentException(e.intent)
        }
    }

    override suspend fun listFiles(folderId: String): Result<List<RemoteFile>> = withContext(Dispatchers.IO) {
        try {
            val drive = driveClient() ?: return@withContext Result.failure(Exception(notLinkedMessage()))
            val parent = folderId.ifBlank { "root" }
            val result = withDriveConsentHandling {
                drive.files().list()
                    .setQ("'$parent' in parents and trashed = false")
                    .setFields("files(id, name, mimeType, size, modifiedTime)")
                    .execute()
            }

            val items = result.files.orEmpty().map { f: DriveFile ->
                RemoteFile(
                    name = f.name,
                    path = f.name,
                    isDirectory = f.mimeType == "application/vnd.google-apps.folder",
                    size = f.getSize() ?: 0L,
                    cloudFileId = f.id
                )
            }
            Result.success(items)
        } catch (e: Exception) {
            com.learnsypro.app.filemanager.util.LogBus.warning(
                "Google Drive listFiles lỗi (${e.javaClass.simpleName}): ${e.message}",
                source = "CLOUD"
            )
            // Log THÊM toàn bộ stacktrace riêng cho lỗi Drive — "key error" (IllegalArgumentException)
            // là message quá ngắn để biết chính xác dòng nào trong thư viện Google API Client ném
            // ra nó; error() thường chỉ trích frame đầu tiên thuộc app, vô ích khi lỗi nằm hẳn bên
            // trong thư viện bên thứ 3 — dùng crash() ở đây để có đủ stacktrace chẩn đoán, dù đây
            // không phải crash thật (app vẫn chạy tiếp bình thường sau lỗi này).
            com.learnsypro.app.filemanager.util.LogBus.crash(e)
            Result.failure(e)
        }
    }

    override suspend fun uploadFile(localFile: JavaFile, parentId: String, onProgress: UploadProgressListener?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val drive = driveClient() ?: return@withContext Result.failure(Exception(notLinkedMessage()))
            val metadata = DriveFile().apply {
                name = localFile.name
                parents = listOf(parentId.ifBlank { "root" })
            }
            val content = com.google.api.client.http.FileContent("application/octet-stream", localFile)
            val request = drive.files().create(metadata, content).setFields("id")
            if (onProgress != null) {
                val totalBytes = localFile.length()
                val startTimeMs = System.currentTimeMillis()
                // SDK Drive tự chia file lớn thành nhiều "chunk" khi tải lên (resumable upload)
                // và gọi lại listener này sau mỗi chunk — không cần tự đếm byte thủ công như
                // Dropbox/Box (những nơi phải tự bọc RequestBody vì dùng OkHttp thuần), khác hẳn
                // interface: đây chỉ là 1 lần setProgressListener(), không phải wrap request body.
                request.mediaHttpUploader.setProgressListener { uploader ->
                    val bytesUploaded = uploader.numBytesUploaded
                    val elapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000.0
                    val speed = if (elapsedSec > 0) bytesUploaded / elapsedSec else 0.0
                    val remainingBytes = (totalBytes - bytesUploaded).coerceAtLeast(0)
                    val eta = if (speed > 0) (remainingBytes / speed).toLong() else null
                    onProgress.onProgress(bytesUploaded, totalBytes, speed, eta)
                }
            }
            request.execute()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * File Docs/Sheets/Slides/Drawings "gốc" của Google (mimeType dạng
     * application/vnd.google-apps.*) KHÔNG có nội dung nhị phân để tải trực tiếp qua
     * files().get().executeMediaAndDownloadTo() — Drive API luôn trả 403 Forbidden cho loại
     * file này (đúng lỗi "GET .../files/... 403 Forbidden" đang gặp phải, xảy ra với các mục
     * "Tài liệu không có tiêu đề"/"Untitled document" trong danh sách — đó chính là Google Docs
     * gốc, không phải .docx thật). Phải dùng files().export() với mimeType đích cụ thể, xuất ra
     * định dạng tương đương (docx/xlsx/pptx/pdf) rồi tải file export đó về.
     */
    private fun exportMimeTypeFor(googleMimeType: String): String? = when (googleMimeType) {
        "application/vnd.google-apps.document" ->
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" // .docx
        "application/vnd.google-apps.spreadsheet" ->
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" // .xlsx
        "application/vnd.google-apps.presentation" ->
            "application/vnd.openxmlformats-officedocument.presentationml.presentation" // .pptx
        "application/vnd.google-apps.drawing" -> "image/png"
        else -> null // các mimeType Google khác (form, script, site...) không có định dạng export phù hợp để mở trong app này
    }

    override suspend fun downloadFile(cloudFileId: String, destination: JavaFile): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val drive = driveClient() ?: return@withContext Result.failure(Exception(notLinkedMessage()))
            val meta = drive.files().get(cloudFileId).setFields("mimeType").execute()
            val exportMime = exportMimeTypeFor(meta.mimeType)
            FileOutputStream(destination).use { out ->
                if (exportMime != null) {
                    drive.files().export(cloudFileId, exportMime).executeMediaAndDownloadTo(out)
                } else {
                    drive.files().get(cloudFileId).executeMediaAndDownloadTo(out)
                }
            }
            // Google Docs/Sheets/Slides gốc thường có tên KHÔNG đuôi (VD "Tài liệu không có
            // tiêu đề") — sau khi export ra docx/xlsx/pptx, file trên máy cần đúng đuôi đó để
            // app (và các ứng dụng khác) nhận diện mở đúng viewer, nếu không sẽ thành file
            // "không đuôi" chứa nội dung docx thật bên trong, dễ mở nhầm hoặc không mở được.
            if (exportMime != null && !destination.name.contains('.')) {
                val ext = when (exportMime) {
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx"
                    "image/png" -> "png"
                    else -> null
                }
                if (ext != null) {
                    val renamed = JavaFile(destination.parentFile, "${destination.name}.$ext")
                    if (destination.renameTo(renamed)) {
                        return@withContext Result.success(Unit).also {
                            com.learnsypro.app.filemanager.util.LogBus.info(
                                "Đã export Google Docs và đổi tên thành ${renamed.name}", source = "CLOUD"
                            )
                        }
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteFile(cloudFileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val drive = driveClient() ?: return@withContext Result.failure(Exception(notLinkedMessage()))
            drive.files().delete(cloudFileId).execute()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createFolder(name: String, parentId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val drive = driveClient() ?: return@withContext Result.failure(Exception(notLinkedMessage()))
            val metadata = DriveFile().apply {
                this.name = name
                mimeType = "application/vnd.google-apps.folder"
                parents = listOf(parentId.ifBlank { "root" })
            }
            drive.files().create(metadata).setFields("id").execute()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun unlink() {
        signInClient().signOut()
        prefs.clearCloudToken(CloudProvider.GOOGLE_DRIVE)
    }

    override suspend fun getStorageQuota(): Result<com.learnsypro.app.filemanager.cloud.CloudStorageQuota> = withContext(Dispatchers.IO) {
        try {
            val drive = driveClient() ?: return@withContext Result.failure(Exception(notLinkedMessage()))
            val about = drive.about().get().setFields("storageQuota").execute()
            val quota = about.storageQuota
            val used = quota?.usage ?: 0L
            val total = quota?.limit ?: 0L // Drive trả về null nếu tài khoản có dung lượng "không giới hạn"
            Result.success(com.learnsypro.app.filemanager.cloud.CloudStorageQuota(used, total))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun renameFile(cloudFileId: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val drive = driveClient() ?: return@withContext Result.failure(Exception(notLinkedMessage()))
            val metadata = DriveFile().apply { name = newName }
            withDriveConsentHandling { drive.files().update(cloudFileId, metadata).execute() }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Drive yêu cầu 2 bước tách biệt để có link xem-công khai: (1) tạo permission "anyone/reader"
     * trên chính file đó — nếu bỏ qua bước này, webViewLink vẫn trả về nhưng chỉ người có sẵn
     * quyền truy cập file mới mở được, người khác nhận link sẽ gặp lỗi xin quyền; (2) đọc lại
     * webViewLink sau khi đã có permission công khai.
     */
    override suspend fun getShareLink(cloudFileId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val drive = driveClient() ?: return@withContext Result.failure(Exception(notLinkedMessage()))
            val permission = com.google.api.services.drive.model.Permission().apply {
                type = "anyone"
                role = "reader"
            }
            withDriveConsentHandling { drive.permissions().create(cloudFileId, permission).execute() }
            val file = withDriveConsentHandling {
                drive.files().get(cloudFileId).setFields("webViewLink").execute()
            }
            val link = file.webViewLink
                ?: return@withContext Result.failure(Exception(context.getString(com.learnsypro.app.R.string.cloud_share_link_failed)))
            Result.success(link)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getThumbnailRequest(file: RemoteFile): Pair<String, Map<String, String>>? = withContext(Dispatchers.IO) {
        if (file.isDirectory || !com.learnsypro.app.filemanager.util.FileTypeUtils.isImageOrVideoName(file.name)) return@withContext null
        val cloudId = file.cloudFileId ?: return@withContext null
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context) ?: return@withContext null
            val credential = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE))
            credential.selectedAccount = account.account
            // credential.token gọi mạng để lấy/làm mới access token — PHẢI chạy off main thread
            // (đã bọc trong withContext(Dispatchers.IO) ở đầu hàm). Dùng thẳng endpoint
            // alt=media để tải ẢNH GỐC làm thumbnail — Drive không có endpoint thumbnail cỡ nhỏ
            // public dễ dùng qua access token thường (chỉ có qua Drive UI nội bộ của Google),
            // nên chấp nhận Coil tự downsample ảnh gốc khi hiển thị trong ô 48dp, giống cách
            // MediaViewerActivity vẫn tải ảnh gốc rồi mới hiện.
            val token = credential.token ?: return@withContext null
            val url = "https://www.googleapis.com/drive/v3/files/$cloudId?alt=media"
            url to mapOf("Authorization" to "Bearer $token")
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getStreamRequest(file: RemoteFile): Pair<String, Map<String, String>>? = withContext(Dispatchers.IO) {
        if (file.isDirectory) return@withContext null
        val cloudId = file.cloudFileId ?: return@withContext null
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context) ?: return@withContext null
            val credential = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE))
            credential.selectedAccount = account.account
            val drive = driveClient() ?: return@withContext null
            // File Google Docs/Sheets/Slides gốc (mimeType application/vnd.google-apps.*) không
            // có nội dung nhị phân stream trực tiếp được — bắt buộc export ra định dạng khác
            // trước (xem downloadFile()/exportMimeTypeFor() ở trên), nên KHÔNG áp dụng streaming
            // cho nhóm này, trả về null để nơi gọi tự rơi về downloadFile() như cũ.
            val meta = drive.files().get(cloudId).setFields("mimeType").execute()
            if (exportMimeTypeFor(meta.mimeType) != null) return@withContext null
            val token = credential.token ?: return@withContext null
            val url = "https://www.googleapis.com/drive/v3/files/$cloudId?alt=media"
            url to mapOf("Authorization" to "Bearer $token")
        } catch (e: Exception) {
            null
        }
    }
}

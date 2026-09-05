package com.learnsypro.app.filemanager.cloud

import com.learnsypro.app.filemanager.model.RemoteFile
import java.io.File

/**
 * Bọc quanh 1 [CloudFileService] thật (Drive/Dropbox/Box) để cache kết quả listFiles() và
 * getStorageQuota() trong bộ nhớ (process memory) theo TTL ngắn — KHÔNG đổi hành vi API, chỉ
 * tránh gọi mạng lặp lại khi không cần.
 *
 * Vì sao cần: trước đây mỗi lần vào/ra 1 thư mục cloud (kể cả bấm Back rồi bấm lại đúng thư mục
 * cũ, hoặc xoay màn hình khiến Activity recreate) đều gọi lại listFiles() qua mạng dù nội dung
 * thư mục gần như chắc chắn chưa đổi — tốn dữ liệu di động + có độ trễ + UI phải hiện loading
 * lại từ đầu mỗi lần. loadQuota() cũng vậy: gọi lại mỗi lần mở CloudBrowserActivity dù dung
 * lượng tài khoản hiếm khi đổi trong vài phút.
 *
 * Chiến lược cache — cố tình ĐƠN GIẢN, không dùng Room/DB:
 * - Cache trong RAM, mất khi process bị kill — chấp nhận được vì đây là dữ liệu "nguồn thật"
 *   luôn nằm trên cloud, cache chỉ để tránh gọi lại NGAY LẬP TỨC, không phải để dùng offline.
 * - TTL ngắn (mặc định 60s cho listFiles, 5 phút cho quota).
 * - GIỚI HẠN KÍCH THƯỚC (LRU): [maxListEntries] thư mục gần dùng nhất — vượt quá thì entry cũ
 *   nhất (theo thứ tự truy cập, không phải thứ tự thêm) tự bị đẩy ra. Không giới hạn trước đây
 *   khiến cache phình vô hạn suốt phiên duyệt cloud dài/nhiều thư mục (đặc biệt Drive nhiều cấp
 *   folder) — mỗi thư mục mở qua chỉ thêm entry, không bao giờ tự xoá trừ khi bị invalidate bởi
 *   thao tác ghi.
 * - DỌN ENTRY HẾT HẠN: entry quá TTL trước đây vẫn nằm trong map (chỉ không được dùng để trả
 *   kết quả) — tốn RAM vô ích. Giờ dọn thụ động: mỗi lần đọc/ghi cache đều quét bỏ entry hết hạn
 *   trước, không cần thread nền riêng.
 * - Bất kỳ thao tác ghi nào (upload/delete/rename/createFolder) tự động xoá cache của ĐÚNG
 *   folderId liên quan (invalidate có mục tiêu) thay vì xoá sạch toàn bộ cache của provider —
 *   các thư mục khác không bị ảnh hưởng vẫn giữ nguyên cache, không phải tải lại oan.
 * - forceRefresh (dùng bởi pull-to-refresh) bỏ qua cache đọc, nhưng vẫn GHI kết quả mới vào
 *   cache để các lần gọi listFiles() thường tiếp theo hưởng lợi.
 */
class CachedCloudFileService(
    private val delegate: CloudFileService,
    private val listTtlMs: Long = 60_000L,
    private val quotaTtlMs: Long = 5 * 60_000L,
    private val maxListEntries: Int = 40
) : CloudFileService by delegate {

    private data class Entry<T>(val value: T, val timestampMs: Long)

    // LinkedHashMap với accessOrder=true: mỗi lần get() đụng tới 1 entry, nó được đẩy lên
    // "mới nhất" trong thứ tự duyệt — removeEldestEntry() nhờ đó luôn đẩy ra đúng entry ÍT
    // dùng gần đây nhất khi vượt cap, đúng nghĩa LRU chứ không phải chỉ FIFO theo lúc thêm.
    // Không dùng ConcurrentHashMap được nữa vì accessOrder cần LinkedHashMap; đồng bộ hoá thủ
    // công bằng 1 lock chung cho cả map (dung lượng nhỏ, tranh chấp không đáng lo).
    private val listLock = Any()
    private val listCache = object : LinkedHashMap<String, Entry<List<RemoteFile>>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry<List<RemoteFile>>>?): Boolean =
            size > maxListEntries
    }

    @Volatile private var quotaCache: Entry<CloudStorageQuota>? = null

    private fun now() = System.currentTimeMillis()

    /** Dọn mọi entry đã hết TTL trước khi đọc/ghi — tránh rác tích tụ giữa các entry còn sống. */
    private fun purgeExpiredListEntries() {
        val cutoff = now()
        listCache.entries.removeAll { cutoff - it.value.timestampMs >= listTtlMs }
    }

    override suspend fun listFiles(folderId: String): Result<List<RemoteFile>> {
        val cached = synchronized(listLock) {
            purgeExpiredListEntries()
            listCache[folderId] // get() qua LinkedHashMap(accessOrder=true) tự cập nhật thứ tự LRU
        }
        if (cached != null) {
            return Result.success(cached.value)
        }
        val result = delegate.listFiles(folderId)
        result.getOrNull()?.let { files ->
            synchronized(listLock) { listCache[folderId] = Entry(files, now()) }
        }
        return result
    }

    /** Dùng bởi pull-to-refresh: luôn gọi mạng, nhưng vẫn cập nhật cache cho các lần đọc sau. */
    suspend fun listFilesForceRefresh(folderId: String): Result<List<RemoteFile>> {
        val result = delegate.listFiles(folderId)
        result.getOrNull()?.let { files ->
            synchronized(listLock) {
                purgeExpiredListEntries()
                listCache[folderId] = Entry(files, now())
            }
        }
        return result
    }

    override suspend fun getStorageQuota(): Result<CloudStorageQuota> {
        val cached = quotaCache
        if (cached != null && now() - cached.timestampMs < quotaTtlMs) {
            return Result.success(cached.value)
        }
        val result = delegate.getStorageQuota()
        result.getOrNull()?.let { quota ->
            quotaCache = Entry(quota, now())
        }
        return result
    }

    override suspend fun uploadFile(localFile: File, parentId: String, onProgress: UploadProgressListener?): Result<Unit> {
        val result = delegate.uploadFile(localFile, parentId, onProgress)
        if (result.isSuccess) {
            synchronized(listLock) { listCache.remove(parentId) }
            quotaCache = null // dung lượng đã dùng vừa tăng
        }
        return result
    }

    override suspend fun deleteFile(cloudFileId: String): Result<Unit> {
        val result = delegate.deleteFile(cloudFileId)
        if (result.isSuccess) {
            // Không biết chắc file bị xoá thuộc folder nào (chỉ có id) -> xoá toàn bộ cache
            // listFiles để đảm bảo đúng, chấp nhận tải lại hơi rộng còn hơn hiện file đã xoá.
            synchronized(listLock) { listCache.clear() }
            quotaCache = null // dung lượng đã dùng vừa giảm
        }
        return result
    }

    override suspend fun renameFile(cloudFileId: String, newName: String): Result<Unit> {
        val result = delegate.renameFile(cloudFileId, newName)
        if (result.isSuccess) {
            // Tương tự deleteFile: không biết chắc thuộc folder nào từ id -> xoá sạch cache list.
            synchronized(listLock) { listCache.clear() }
        }
        return result
    }

    override suspend fun createFolder(name: String, parentId: String): Result<Unit> {
        val result = delegate.createFolder(name, parentId)
        if (result.isSuccess) {
            synchronized(listLock) { listCache.remove(parentId) }
        }
        return result
    }

    override fun unlink() {
        synchronized(listLock) { listCache.clear() }
        quotaCache = null
        delegate.unlink()
    }

    /** Xoá toàn bộ cache thủ công (vd. khi cần chắc chắn dữ liệu mới nhất cho mọi thư mục). */
    fun invalidateAll() {
        synchronized(listLock) { listCache.clear() }
        quotaCache = null
    }
}

package com.learnsypro.app.filemanager.cloud

import android.content.Context
import com.learnsypro.app.filemanager.model.CloudProvider
import java.util.concurrent.ConcurrentHashMap

object CloudServiceFactory {

    // Giữ đúng 1 CachedCloudFileService/provider cho cả vòng đời process — không tạo mới mỗi
    // lần get() được gọi (mỗi lần mở CloudBrowserActivity). Quan trọng để cache thật sự có tác
    // dụng: nếu tạo CachedCloudFileService mới mỗi lần vào màn hình, cache luôn rỗng lúc mở lại,
    // mất hết lợi ích (vd bấm vào Google Drive -> back -> bấm lại Google Drive trong vài giây).
    private val instances = ConcurrentHashMap<CloudProvider, CachedCloudFileService>()

    fun get(context: Context, provider: CloudProvider): CachedCloudFileService =
        instances.getOrPut(provider) {
            val raw: CloudFileService = when (provider) {
                CloudProvider.GOOGLE_DRIVE -> GoogleDriveService(context.applicationContext)
                CloudProvider.DROPBOX -> DropboxService(context.applicationContext)
                CloudProvider.BOX -> BoxService(context.applicationContext)
            }
            CachedCloudFileService(raw)
        }

    /** Gọi khi unlink tài khoản hẳn (đăng xuất) để lần liên kết lại sau tạo service mới hoàn toàn thay vì tái dùng cache/instance cũ. */
    fun reset(provider: CloudProvider) {
        instances.remove(provider)
    }
}

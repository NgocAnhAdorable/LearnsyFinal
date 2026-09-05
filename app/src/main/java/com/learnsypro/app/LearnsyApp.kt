package com.learnsypro.app

import android.app.Application
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * ── LearnsyApp ──
 * Application class chính của toàn app, gộp 2 nguồn trước đây tách riêng:
 * 1. Cấu hình Coil (load ảnh) — ban đầu chỉ phục vụ avatar/ảnh nền Dashboard.
 * 2. Khởi tạo module Quản lý tệp (trước đây là app MyFile Manager độc lập,
 *    có Application class MyFileApp.kt riêng) — crash handler toàn cục,
 *    LogBus, và App Lock lifecycle callback.
 *
 * Chỉ có THỂ có 1 ImageLoaderFactory cho toàn tiến trình app (Coil chỉ gọi
 * newImageLoader() một lần, lấy từ Application singleton) — không thể giữ 2
 * Application class riêng biệt như trước khi gộp. ImageLoader dưới đây phục
 * vụ CẢ 2 nhu cầu cùng lúc:
 * - Decoder GIF + HEIC/HEIF (ImageDecoderDecoder, từ MyFileApp) — cần cho
 *   thumbnail ảnh trong Quản lý tệp; vô hại với avatar JPG/PNG của Dashboard,
 *   Coil tự chọn decoder phù hợp theo định dạng file thực tế.
 * - allowHardware(false) + bitmapConfig(RGB_565) (từ MyFileApp) — bắt buộc
 *   để crossfade không crash trên hardware bitmap (xem giải thích gốc trong
 *   git history của MyFileApp.kt); vẫn áp dụng tốt cho avatar.
 * - okHttpClient với Authenticator tự làm mới token Dropbox/Box (từ
 *   MyFileApp) — cần để thumbnail Cloud không lỗi khi token hết hạn; vô hại
 *   với URL Supabase Storage của Dashboard (client chỉ thêm auth header cho
 *   đúng host Dropbox/Box, xem HostBasedCloudAuthenticator).
 * - Memory cache 30% RAM (giữ theo LearnsyApp gốc — cao hơn 15% của MyFileApp
 *   gốc; không có lý do kỹ thuật để hạ xuống vì cả 2 phía đều dùng RGB_565).
 * - Disk cache: giữ thư mục + dung lượng riêng của LearnsyApp gốc
 *   (100MB, "learnsy_image_cache") cho avatar/ảnh nền; Quản lý tệp có
 *   cache thumbnail RIÊNG của chính nó qua ArchiveEntryAdapter/MediaPagerAdapter
 *   nên không phụ thuộc thư mục cache đĩa này.
 *
 * Cần khai báo android:name=".LearnsyApp" trong AndroidManifest.xml để
 * Android dùng class này thay vì Application mặc định (giữ nguyên yêu cầu
 * từ bản gốc — KHÔNG đổi sang MyFileApp, vì đây vẫn là app Learnsy Pro).
 */
class LearnsyApp : Application(), ImageLoaderFactory {

    companion object {
        // Instance tĩnh của Application context — dùng cho các nơi cần
        // ConnectivityManager mà không có sẵn Activity/Context trong tay, ví dụ
        // FtpClientManager (chạy trên Dispatchers.IO, không nhận Context qua
        // constructor để giữ nguyên interface RemoteClient chung cho FTP/SFTP/
        // SMB). An toàn dùng static ở đây vì luôn là applicationContext (sống
        // suốt vòng đời tiến trình), không phải Activity context (sẽ leak).
        lateinit var instance: LearnsyApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        com.learnsypro.app.filemanager.util.LogBus.init(this)

        // Crash handler toàn cục — chuyển nguyên từ MyFileApp.kt: bắt MỌI exception
        // không được xử lý (kể cả coroutine không try/catch, background thread...)
        // TRƯỚC KHI hệ thống Android kill tiến trình, ghi đồng bộ xuống file NGAY LẬP
        // TỨC để không mất log dù tiến trình chết ngay dòng sau. Vẫn gọi lại
        // defaultHandler gốc sau khi ghi log — không "nuốt" crash, chỉ ghi lại trước.
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                com.learnsypro.app.filemanager.util.LogBus.crash(throwable)
            } catch (e: Exception) {
                // Ghi log lỗi cũng không được phép ném exception mới đè lên exception gốc.
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        registerActivityLifecycleCallbacks(FileManagerAppLockCallbacks())

        cleanupStaleTempCacheFiles()
    }

    /**
     * Dọn các file/thư mục tạm mà nhiều màn hình trong module Quản lý tệp ghi
     * thẳng vào cacheDir (preview docx/xlsx/pdf/html, "extract_*" khi mở archive,
     * "xfer_*" khi truyền file cloud, "dlna_preview_*", "compress_*", "archive_thumb/",
     * "exports/"...) — PHẦN LỚN các chỗ này không tự xoá lại sau khi dùng xong (chỉ
     * 2/12+ chỗ có gọi deleteRecursively()), nên cache tích luỹ không giới hạn theo
     * thời gian dùng app (khác hẳn cache ảnh 100MB có giới hạn cứng của Coil ở
     * newImageLoader() bên dưới). Đây là nguyên nhân mục "Bộ nhớ đệm" trong
     * Cài đặt > Ứng dụng > Learnsy Pro > Lưu trữ phình to bất thường theo thời gian.
     *
     * Chạy mỗi lần app khởi động, trên background thread (không chặn onCreate),
     * chỉ xoá file/thư mục có lastModified() CŨ HƠN 24 GIỜ — đủ an toàn vì mọi
     * file tạm đang được dùng dở trong phiên hiện tại luôn mới hơn mốc đó; không
     * đụng tới thư mục "learnsy_image_cache" của Coil (đã tự quản lý dung lượng
     * riêng bằng LRU + giới hạn 100MB, không cần dọn thủ công ở đây).
     */
    private fun cleanupStaleTempCacheFiles() {
        Thread {
            try {
                val maxAgeMs = 24 * 60 * 60 * 1000L
                val now = System.currentTimeMillis()
                val protectedDirName = "learnsy_image_cache"
                cacheDir.listFiles()?.forEach { entry ->
                    if (entry.name == protectedDirName) return@forEach
                    val age = now - entry.lastModified()
                    if (age > maxAgeMs) {
                        entry.deleteRecursively()
                    }
                }
            } catch (e: Exception) {
                com.learnsypro.app.filemanager.util.LogBus.error(
                    "Dọn cache tạm lúc khởi động thất bại, bỏ qua để không crash",
                    "CACHE_CLEANUP",
                    e
                )
            }
        }.start()
    }

    /**
     * Theo dõi vòng đời TOÀN BỘ Activity để tự động hiện màn khoá PIN của module
     * Quản lý tệp (AppLockActivity) đúng lúc app quay lại từ nền — chuyển nguyên
     * logic từ MyFileApp.AppLockCallbacks, xem giải thích chi tiết về cách phân
     * biệt "app rời nền" vs "chuyển màn nội bộ" trong file AppLockActivity.kt.
     *
     * CHỈ áp dụng App Lock cho module Quản lý tệp — Dashboard/Quiz/Vocab... của
     * Learnsy Pro không có khái niệm khoá PIN riêng nên không bị ảnh hưởng.
     */
    private inner class FileManagerAppLockCallbacks : ActivityLifecycleCallbacks {
        private var startedActivityCount = 0

        override fun onActivityStarted(activity: android.app.Activity) {
            val wasInBackground = startedActivityCount == 0
            startedActivityCount++
            if (wasInBackground && activity !is com.learnsypro.app.filemanager.AppLockActivity) {
                maybeShowLockScreen(activity)
            }
        }

        override fun onActivityStopped(activity: android.app.Activity) {
            startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
        }

        private fun maybeShowLockScreen(activity: android.app.Activity) {
            // BỌC TRY-CATCH TOÀN BỘ: callback này chạy cho MỌI Activity của TOÀN
            // APP (kể cả Dashboard/Quiz của Learnsy Pro, không riêng gì module
            // Quản lý tệp), mọi lần mở app — không có nơi nào khác bắt lỗi hộ.
            try {
                val prefs = com.learnsypro.app.filemanager.util.SecurePrefs.getInstance(activity)
                if (!prefs.appLockEnabled || prefs.appLockPinHash == null) return
                val intent = android.content.Intent(activity, com.learnsypro.app.filemanager.AppLockActivity::class.java).apply {
                    putExtra(
                        com.learnsypro.app.filemanager.AppLockActivity.EXTRA_MODE,
                        com.learnsypro.app.filemanager.AppLockActivity.MODE_UNLOCK
                    )
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                activity.startActivity(intent)
            } catch (e: Exception) {
                com.learnsypro.app.filemanager.util.LogBus.error(
                    "Lỗi khi kiểm tra khoá app (module Quản lý tệp), bỏ qua để không crash",
                    "APP_LOCK",
                    e
                )
            }
        }

        override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: Bundle?) {}
        override fun onActivityResumed(activity: android.app.Activity) {}
        override fun onActivityPaused(activity: android.app.Activity) {}
        override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: android.app.Activity) {}
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                }
                add(GifDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.30) // cấp thêm RAM cho cache ảnh trong bộ nhớ
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("learnsy_image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024) // 100MB cache đĩa
                    .build()
            }
            .bitmapConfig(Bitmap.Config.RGB_565)
            .allowHardware(false)
            .crossfade(true)
            // okHttpClient riêng có Authenticator tự làm mới access token Dropbox/Box khi hết
            // hạn (401) — cần cho thumbnail Cloud trong module Quản lý tệp; xem
            // RetrofitFactory.coilClient()/HostBasedCloudAuthenticator.
            .okHttpClient { com.learnsypro.app.filemanager.cloud.RetrofitFactory.coilClient(this) }
            .build()
    }
}

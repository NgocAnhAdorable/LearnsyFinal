package com.learnsypro.app.filemanager.util

/**
 * Nhận diện loại file dùng chung cho cả 3 nguồn (Bộ nhớ trong/Cloud/DLNA) — tách ra từ
 * LocalFileAdapter (trước đây set IMAGE_VIDEO_EXTENSIONS chỉ private trong 1 file) để
 * RemoteFileAdapter (Cloud) và RemoteDidlAdapter (DLNA) dùng lại đúng 1 danh sách, tránh
 * lệch tiêu chí "ảnh/video nào hiện thumbnail thật" giữa 3 màn hình.
 */
object FileTypeUtils {

    val IMAGE_VIDEO_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif",
        "mp4", "mkv", "webm", "3gp", "mov", "avi"
    )

    /**
     * Phần mở rộng coi là văn bản/mã nguồn thuần — mở được bằng CodeEditorActivity (xem nội
     * dung dạng chữ) thay vì hỏi ứng dụng ngoài. Đồng bộ với editableExtensions vốn có trong
     * CategoryFilesActivity (Bộ nhớ trong), bổ sung "kts" (Kotlin Script — build.gradle.kts,
     * settings.gradle.kts, rất phổ biến với chính dự án Android như app này) mà bản trước đó
     * chưa có, dù đã có "kt" thường.
     */
    val TEXT_EXTENSIONS = setOf(
        "kt", "kts", "java", "js", "ts", "jsx", "tsx", "html", "htm", "css", "json", "xml",
        "py", "c", "cpp", "h", "cs", "php", "rb", "go", "rs", "sh", "sql", "yml", "yaml",
        "gradle", "properties", "md", "txt", "log", "ini", "env"
    )

    fun isImageOrVideoName(name: String, mimeType: String? = null): Boolean {
        if (mimeType != null) return mimeType.startsWith("image/") || mimeType.startsWith("video/")
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in IMAGE_VIDEO_EXTENSIONS
    }

    fun isTextFileName(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in TEXT_EXTENSIONS
    }

    // Giải nén archive dùng ArchiveUtils.isArchive() có sẵn (zip/rar/7z) — không định nghĩa lại
    // ở đây để tránh 2 danh sách phần mở rộng archive lệch nhau giữa 2 file tiện ích.
}

package com.learnsypro.app.filemanager.notes

import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Ghi chú lưu thành file .html THẬT trong thư mục Notes/ ở Bộ nhớ trong (KHÔNG dùng database
 * riêng) — theo đúng yêu cầu: ghi chú phải xem chung được với các file khác qua Bộ nhớ trong
 * bình thường (Notes/ hiện y hệt 1 thư mục con như DCIM/Download/Movies...). Định dạng (đậm/
 * nghiêng/gạch chân/màu/cỡ chữ/danh sách/ảnh chèn) lưu bằng chính thẻ HTML thật (<b>, <i>, <u>,
 * <span style="color:...">, <ul><li>, <img src="data:...">) — mở file này bằng trình duyệt bất
 * kỳ hay HtmlViewerActivity có sẵn của app đều đọc đúng định dạng, không phải format riêng.
 */
object NoteFileStore {

    /** Notes/ nằm ngay trong Bộ nhớ trong, cùng cấp với DCIM/Download — để hiện lên như 1 thư mục bình thường. */
    val notesDir: File by lazy {
        File(Environment.getExternalStorageDirectory(), "Notes").apply { mkdirs() }
    }

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    data class NoteSummary(
        val file: File,
        val title: String,
        val previewText: String,
        val lastModified: Long
    )

    fun listNotes(): List<NoteSummary> {
        val files = notesDir.listFiles { f -> f.isFile && f.extension.equals("html", ignoreCase = true) } ?: emptyArray()
        return files.map { file ->
            val html = try { file.readText() } catch (e: Exception) { "" }
            val title = extractTitle(html, file.nameWithoutExtension)
            val preview = extractPreviewText(html)
            NoteSummary(file, title, preview, file.lastModified())
        }.sortedByDescending { it.lastModified }
    }

    fun formattedDate(timestamp: Long): String = dateFormat.format(java.util.Date(timestamp))

    /** Tiêu đề = nội dung thẻ <title>; nếu ghi chú chưa từng đặt tên, dùng tên file làm tiêu đề hiển thị. */
    private fun extractTitle(html: String, fallback: String): String {
        val match = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL).find(html)
        val raw = match?.groupValues?.get(1)?.trim()
        return if (raw.isNullOrBlank()) fallback else unescapeHtml(raw)
    }

    /**
     * Xem trước dạng CHỮ THUẦN cho card trong danh sách (bỏ hết thẻ HTML/định dạng) — card danh
     * sách chỉ cần vài dòng chữ đầu, không cần render lại toàn bộ định dạng như màn soạn thảo.
     */
    private fun extractPreviewText(html: String): String {
        val bodyMatch = Regex("<body[^>]*>(.*?)</body>", RegexOption.DOT_MATCHES_ALL).find(html)
        val body = bodyMatch?.groupValues?.get(1) ?: html
        val withBreaks = body.replace(Regex("<(br|/p|/div|/li)\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        val stripped = withBreaks.replace(Regex("<[^>]*>"), "")
        return unescapeHtml(stripped).trim().take(200)
    }

    private fun unescapeHtml(s: String): String = s
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")

    /** Tên file an toàn từ tiêu đề — loại ký tự không hợp lệ trên Android filesystem, cộng timestamp để không đè file cùng tên. */
    fun suggestFileName(title: String): String {
        val safe = title.trim().ifBlank { "ghi_chu" }
            .replace(Regex("[/\\\\:*?\"<>|]"), "_")
            .take(60)
        return "${safe}_${System.currentTimeMillis()}.html"
    }
}

package com.learnsypro.app.filemanager.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import coil.size.Scale
import com.learnsypro.app.R
import com.learnsypro.app.databinding.ItemArchiveEntryBinding
import com.learnsypro.app.filemanager.model.ArchiveNode
import com.learnsypro.app.filemanager.util.ArchiveUtils
import com.learnsypro.app.filemanager.util.FileTypeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Adapter cho danh sách mục (file/thư mục) trong 1 cấp của cây file nén, dùng ở ArchivePreviewActivity.
 * [isSelected] tra trạng thái tick của 1 node theo entryPath (nguồn dữ liệu chọn nằm ở Activity,
 * adapter chỉ hỏi & vẽ lại, không giữ state chọn để tránh lệch khi điều hướng qua lại các cấp).
 *
 * [archiveFile] + [cacheDir]: dùng để trích riêng 1 entry ảnh/video/PDF ra file cache tạm rồi
 * hiển thị THUMBNAIL THẬT (thay vì icon tĩnh giống nhau cho mọi ảnh/video) — giống hành vi
 * LocalFileAdapter ở màn hình bộ nhớ trong. [thumbCache]: nhớ lại entry nào đã trích để tránh
 * giải nén lại mỗi lần RecyclerView bind lại view (cuộn qua cuộn lại).
 */
class ArchiveEntryAdapter(
    private val isSelected: (ArchiveNode) -> Boolean,
    private val onToggleSelect: (ArchiveNode) -> Unit,
    private val onOpenFolder: (ArchiveNode) -> Unit,
    private val entryDate: Long,
    private val archiveFile: File,
    private val cacheDir: File,
    private val scope: LifecycleCoroutineScope
) : RecyclerView.Adapter<ArchiveEntryAdapter.VH>() {

    private val items = mutableListOf<ArchiveNode>()
    private val dateFmt = SimpleDateFormat("d 'Th'M HH:mm", Locale.getDefault())

    // entryPath -> file cache đã trích thành công (ảnh/video gốc, hoặc bitmap trang đầu PDF đã render sẵn)
    private val thumbCache = mutableMapOf<String, File>()
    // entryPath đã thử trích nhưng lỗi (file hỏng/không đọc được) -> không thử lại, tránh spam IO
    private val thumbFailed = mutableSetOf<String>()

    fun submit(newItems: List<ArchiveNode>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    inner class VH(val binding: ItemArchiveEntryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemArchiveEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val node = items[position]
        val b = holder.binding
        b.tvEntryName.text = node.name
        b.ivSelectedCheck.setImageResource(
            if (isSelected(node)) R.drawable.ic_check_circle else R.drawable.ic_check_circle_outline
        )

        val fallbackIcon = if (node.isDirectory) R.drawable.ic_folder else R.drawable.ic_file
        b.ivIcon.dispose()
        if (!node.isDirectory && canThumbnail(node.name)) {
            bindThumbnail(b, node, fallbackIcon)
        } else {
            b.ivIcon.setImageResource(fallbackIcon)
        }

        if (node.isDirectory) {
            b.tvEntryMeta.text = dateFmt.format(entryDate)
            b.tvEntryExtra.text = b.root.context.getString(R.string.items_count, node.children.size)
            b.tvEntryExtra.visibility = android.view.View.VISIBLE
        } else {
            b.tvEntryMeta.text = dateFmt.format(entryDate)
            b.tvEntryExtra.text = formatSize(node.size)
            b.tvEntryExtra.visibility = android.view.View.VISIBLE
        }

        b.ivSelectedCheck.setOnClickListener { onToggleSelect(node) }
        b.root.setOnClickListener {
            if (node.isDirectory) onOpenFolder(node) else onToggleSelect(node)
        }
        b.root.setOnLongClickListener { onToggleSelect(node); true }
    }

    // LƯU Ý: app hiện KHÔNG cấu hình video decoder cho Coil (xem LearnsyApp.newImageLoader() ở package cha —
    // chỉ add ImageDecoderDecoder cho heic/heif + GifDecoder, không có coil-video), nên
    // LocalFileAdapter ở màn hình bộ nhớ trong CŨNG chỉ hiện thumbnail thật cho ẢNH, video vẫn
    // rơi về icon tĩnh dù gọi load(File(...)). Adapter này giữ đúng hành vi nhất quán đó thay
    // vì tự thêm 1 kiểu xử lý video riêng chỉ cho archive (sẽ lệch icon/thumbnail giữa 2 màn hình).
    private fun canThumbnail(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return FileTypeUtils.isImageOrVideoName(name) || ext == "pdf"
    }

    /** Trích entry ra cache (nếu chưa có) trên IO thread rồi load bằng Coil; huỷ an toàn nếu view bị tái sử dụng trước khi xong. */
    private fun bindThumbnail(b: ItemArchiveEntryBinding, node: ArchiveNode, fallbackIcon: Int) {
        val cached = thumbCache[node.entryPath]
        if (cached != null && cached.exists()) {
            b.ivIcon.load(cached) { scale(Scale.FILL); placeholder(fallbackIcon); error(fallbackIcon) }
            return
        }
        if (node.entryPath in thumbFailed) {
            b.ivIcon.setImageResource(fallbackIcon)
            return
        }
        b.ivIcon.setImageResource(fallbackIcon)
        val boundEntryPath = node.entryPath
        scope.launch {
            val extracted = withContext(Dispatchers.IO) {
                extractThumbSource(node)
            }
            // View có thể đã bị tái sử dụng cho 1 node KHÁC trong lúc trích xuất chạy nền —
            // chỉ áp kết quả nếu vẫn đang bind đúng entry này (so tag đã gán ngay dưới).
            if (b.root.tag != boundEntryPath) return@launch
            if (extracted != null) {
                thumbCache[boundEntryPath] = extracted
                b.ivIcon.load(extracted) { scale(Scale.FILL); placeholder(fallbackIcon); error(fallbackIcon) }
            } else {
                thumbFailed.add(boundEntryPath)
                b.ivIcon.setImageResource(fallbackIcon)
            }
        }
        b.root.tag = boundEntryPath
    }

    /** Trích 1 entry (ảnh/video: dùng nguyên bytes; PDF: trích rồi render trang đầu thành bitmap) ra file cache tạm. Chạy trên IO thread. */
    private fun extractThumbSource(node: ArchiveNode): File? {
        val ext = node.name.substringAfterLast('.', "").lowercase()
        val safeName = node.entryPath.hashCode().toString() + "_" + node.name.substringAfterLast('/')
        val rawOut = File(cacheDir, "archive_thumb/$safeName")
        val result = ArchiveUtils.extractEntryToFile(archiveFile, node.entryPath, rawOut)
        val raw = result.getOrNull() ?: return null
        if (ext != "pdf") return raw
        return renderPdfFirstPage(raw)
    }

    /** Render trang đầu 1 PDF (đã trích ra đĩa) thành PNG cache — PdfRenderer cần ParcelFileDescriptor trên đĩa thật, không đọc trực tiếp từ archive được. */
    private fun renderPdfFirstPage(pdfFile: File): File? {
        return try {
            val pfd = android.os.ParcelFileDescriptor.open(pdfFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount == 0) return null
                renderer.openPage(0).use { page ->
                    val bitmap = android.graphics.Bitmap.createBitmap(page.width, page.height, android.graphics.Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val outFile = File(pdfFile.parentFile, pdfFile.name + "_p0.png")
                    outFile.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it) }
                    bitmap.recycle()
                    outFile
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun getItemCount(): Int = items.size

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val safeGroup = digitGroups.coerceIn(0, units.size - 1)
        return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, safeGroup.toDouble())) + " " + units[safeGroup]
    }
}

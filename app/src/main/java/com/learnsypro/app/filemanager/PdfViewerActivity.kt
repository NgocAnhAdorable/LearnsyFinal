package com.learnsypro.app.filemanager

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.learnsypro.app.databinding.ActivityPdfViewerBinding
import com.learnsypro.app.filemanager.util.ActivityTransitions
import com.learnsypro.app.filemanager.util.LogBus
import com.learnsypro.app.filemanager.util.ZoomController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Xem file .pdf trực tiếp trong app, không cần app ngoài. Dùng android.graphics.pdf.PdfRenderer
 * có sẵn của Android (KHÔNG cần thư viện thứ 3) - render từng trang ra Bitmap, cuộn dọc liên tục.
 * Chỉ đọc (không chỉnh sửa/annotate/tìm kiếm text).
 */
class PdfViewerActivity : LearnsyFileManagerActivity() {

    private lateinit var binding: ActivityPdfViewerBinding
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private val adapter = PageAdapter()
    private lateinit var zoomController: ZoomController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener {
            finish()
            ActivityTransitions.backward(this)
        }

        binding.rvPages.layoutManager = LinearLayoutManager(this)
        binding.rvPages.adapter = adapter

        // Zoom 50%-300%: áp scale lên chính RecyclerView (chứa các trang PDF đã render) — pinch
        // trực tiếp trên trang PDF, hoặc dùng nút +/- trên thanh công cụ cho thao tác chính xác.
        zoomController = ZoomController(this, binding.rvPages) { scale ->
            binding.tvZoomLevel.text = "${(scale * 100).toInt()}%"
        }
        zoomController.attachPinchToZoom()
        binding.btnZoomIn.setOnClickListener { zoomController.zoomIn() }
        binding.btnZoomOut.setOnClickListener { zoomController.zoomOut() }

        // Zoom nét như Samsung Notes: khi đang kéo/pinch, chỉ scaleX/scaleY tạm thời (mượt, đỡ
        // giật) — nhưng bitmap gốc vẫn render ở độ phân giải CŨ (bằng đúng bề rộng màn hình lúc
        // 100%), nên phóng to quá mức sẽ vỡ nét/mờ giống phóng ảnh JPG, KHÔNG re-render lại như
        // PDF vector thật. Khi người dùng NGỪNG pinch (buông tay) hoặc bấm nút +/-, render lại
        // TỪNG trang đang hiển thị ở độ phân giải mới = widthPx gốc * scale hiện tại, rồi reset
        // scaleX/scaleY của RecyclerView về 1 (ảnh mới đã tự đủ to, không cần scale ảo nữa) —
        // người dùng luôn thấy chữ/hình sắc nét ở bất kỳ mức zoom nào, đúng cách Samsung Notes/
        // Google PDF Viewer/Adobe Acrobat hoạt động.
        zoomController.setOnZoomSettled { rerenderVisiblePagesAtCurrentZoom() }

        val file = resolveIncomingFile()
        if (file == null || !file.exists()) {
            showError()
            return
        }
        binding.toolbar.title = file.name
        loadPdf(file)
    }

    private fun resolveIncomingFile(): File? {
        val pathExtra = intent.getStringExtra(EXTRA_FILE_PATH)
        if (pathExtra != null) return File(pathExtra)
        val data: Uri = intent.data ?: return null
        return when (data.scheme) {
            "file" -> data.path?.let { File(it) }
            "content" -> {
                val name = queryDisplayName(data) ?: "shared_${System.currentTimeMillis()}.pdf"
                val target = File(cacheDir, name)
                try {
                    contentResolver.openInputStream(data)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    target
                } catch (e: Exception) {
                    LogBus.error("Không thể đọc file PDF được chia sẻ: $name", source = "PDF", throwable = e)
                    null
                }
            }
            else -> null
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }

    private fun loadPdf(file: File) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val pageCount = try {
                withContext(Dispatchers.IO) {
                    val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    pfd = fd
                    val r = PdfRenderer(fd)
                    renderer = r
                    r.pageCount
                }
            } catch (e: Exception) {
                LogBus.error("Không mở được PDF: ${file.path}", source = "PDF", throwable = e)
                -1
            }
            binding.progressBar.visibility = View.GONE
            if (isFinishing || isDestroyed) return@launch
            if (pageCount <= 0) {
                showError()
                return@launch
            }
            adapter.pageCount = pageCount
            adapter.notifyDataSetChanged()
        }
    }

    private fun showError() {
        binding.layoutError.visibility = View.VISIBLE
        binding.rvPages.visibility = View.GONE
    }

    /** Render 1 trang PDF ra Bitmap theo độ rộng màn hình NHÂN với mức zoom hiện tại — giữ cho
     *  chữ/hình luôn nét dù đang phóng to, vì PDF là dữ liệu vector (re-render lúc nào cũng nét
     *  tuyệt đối), không phải ảnh raster cố định như JPG/PNG. PdfRenderer không thread-safe khi
     *  mở nhiều page cùng lúc -> synchronized. */
    private suspend fun renderPage(index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val r = renderer ?: return@withContext null
        synchronized(r) {
            var page: PdfRenderer.Page? = null
            try {
                page = r.openPage(index)
                val scale = widthPx.toFloat() / page.width
                val bmp = Bitmap.createBitmap(widthPx, (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            } catch (e: Exception) {
                null
            } finally {
                page?.close()
            }
        }
    }

    /**
     * Gọi khi zoom vừa "chốt" (ngừng pinch, hoặc bấm nút +/-): render lại các trang ĐANG hiển thị
     * trên màn hình ở độ phân giải mới (widthPx gốc * zoomController.scale), rồi reset scaleX/
     * scaleY của RecyclerView về 1 — ảnh mới đã tự đủ to nên không cần phóng ảo qua transform
     * nữa. Chỉ render lại trang đang thấy (không phải toàn bộ file) để không tốn bộ nhớ/thời
     * gian với PDF nhiều trang; các trang khác sẽ tự render đúng độ phân giải mới khi cuộn tới
     * (onBindViewHolder đọc currentRenderScale mỗi lần bind).
     */
    private fun rerenderVisiblePagesAtCurrentZoom() {
        val newScale = zoomController.scale
        adapter.currentRenderScale = newScale
        val lm = binding.rvPages.layoutManager as? LinearLayoutManager ?: return
        val first = lm.findFirstVisibleItemPosition()
        val last = lm.findLastVisibleItemPosition()
        if (first < 0 || last < 0) return
        // Reset transform TRƯỚC khi bitmap mới kịp vẽ ra sẽ gây nháy giật (thấy ảnh cũ bị co lại
        // 1 khắc) — validate: notifyItemRangeChanged trước, transform reset ngay sau trong cùng
        // frame vẫn mượt vì Android gộp các thay đổi layout/vẽ trong 1 chu kỳ, không tách frame.
        adapter.notifyItemRangeChanged(first, last - first + 1)
        binding.rvPages.scaleX = 1f
        binding.rvPages.scaleY = 1f
        binding.rvPages.translationX = 0f
        binding.rvPages.translationY = 0f
    }

    private inner class PageAdapter : RecyclerView.Adapter<PageAdapter.VH>() {
        var pageCount = 0
        /** Mức zoom hiện tại dùng để tính độ phân giải render — 1f = mặc định (bằng bề rộng màn hình). */
        var currentRenderScale = 1f

        inner class VH(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val iv = ImageView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                adjustViewBounds = true
                setPadding(0, 4, 0, 4)
            }
            return VH(iv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.imageView.setImageBitmap(null)
            val baseWidthPx = binding.rvPages.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
            // Nhân theo mức zoom hiện tại để bitmap có đủ độ phân giải hiển thị nét ở mức zoom đó
            // — trần 3x giới hạn bởi MAX_SCALE của ZoomController, không lo bitmap phình quá lớn.
            val widthPx = (baseWidthPx * currentRenderScale).toInt().coerceAtLeast(1)
            lifecycleScope.launch {
                val bmp = renderPage(position, widthPx)
                if (holder.bindingAdapterPosition == position) {
                    holder.imageView.setImageBitmap(bmp)
                }
            }
        }

        override fun getItemCount() = pageCount
    }

    override fun onDestroy() {
        renderer?.close()
        pfd?.close()
        deleteIfTempCacheFile(intent.getStringExtra(EXTRA_FILE_PATH))
        super.onDestroy()
    }

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"
    }
}

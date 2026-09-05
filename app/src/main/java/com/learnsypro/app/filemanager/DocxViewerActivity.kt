package com.learnsypro.app.filemanager

import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.AlignmentSpan
import android.text.style.LeadingMarginSpan
import android.text.style.StyleSpan
import android.util.Xml
import android.view.LayoutInflater
import android.view.View
import android.widget.PopupWindow
import androidx.lifecycle.lifecycleScope
import com.google.android.material.materialswitch.MaterialSwitch
import com.learnsypro.app.R
import com.learnsypro.app.databinding.ActivityDocxViewerBinding
import com.learnsypro.app.filemanager.util.ActivityTransitions
import com.learnsypro.app.filemanager.util.LogBus
import com.learnsypro.app.filemanager.util.ZoomController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipFile

/**
 * Xem nhanh nội dung file .docx trực tiếp trong app - đọc thẳng word/document.xml bên trong
 * file .docx (bản chất là 1 file .zip chứa XML), lấy text kèm in đậm/in nghiêng. KHÔNG render
 * layout/ảnh/bảng/cột như Word thật - chỉ để đọc nội dung nhanh, không thay thế Word.
 */
class DocxViewerActivity : LearnsyFileManagerActivity() {

    private lateinit var binding: ActivityDocxViewerBinding
    private var currentFile: File? = null

    // "Chế độ xem" (giống tùy chọn trong Google Docs ở màn hình chia sẻ): BẬT = giữ bố cục
    // đoạn văn gần với bản gốc (căn giữa/phải, thụt lề dòng đầu) bằng cách parse thêm <w:pPr>;
    // TẮT = chỉ text thuần chảy trái, không đọc <w:pPr> - dựng nhanh hơn với file dài.
    private var layoutViewModeEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDocxViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener {
            finish()
            ActivityTransitions.backward(this)
        }

        // Zoom 50%-300%: pinch bằng 2 ngón, vẫn giữ được bôi đen/chọn text bằng 1 ngón
        // (textIsSelectable) như trước vì ZoomController chỉ chặn sự kiện chạm khi phát hiện
        // thực sự có 2 điểm chạm trở lên.
        //
        // KHÁC VỚI PDF (ảnh raster): đây là TextView vẽ font thật (vector) — thay vì chỉ
        // scaleX/scaleY (phóng ảnh đã vẽ, dễ mờ ở mức zoom cao/màn hình mật độ điểm ảnh thấp),
        // khi zoom ổn định ta đặt lại TEXTSIZE THẬT theo đúng tỉ lệ — Android vẽ lại toàn bộ chữ
        // ở cỡ mới với antialiasing đầy đủ, luôn sắc nét ở MỌI mức zoom giống Samsung Notes,
        // không phụ thuộc độ phân giải "chụp sẵn" nào cả.
        val baseTextSizeSp = binding.tvContent.textSize / resources.displayMetrics.scaledDensity
        val zoomController = ZoomController(this, binding.tvContent) { scale ->
            binding.tvZoomLevel.text = "${(scale * 100).toInt()}%"
        }
        zoomController.attachPinchToZoom()
        zoomController.setOnZoomSettled {
            binding.tvContent.textSize = baseTextSizeSp * zoomController.scale
            binding.tvContent.scaleX = 1f
            binding.tvContent.scaleY = 1f
            binding.tvContent.translationX = 0f
            binding.tvContent.translationY = 0f
        }
        binding.btnZoomIn.setOnClickListener { zoomController.zoomIn() }
        binding.btnZoomOut.setOnClickListener { zoomController.zoomOut() }
        binding.btnDocxMenu.setOnClickListener { showDocxOptionsMenu() }

        val file = resolveIncomingFile()
        if (file == null || !file.exists()) {
            showError()
            return
        }
        currentFile = file
        binding.toolbar.title = file.name
        loadDocx(file)
    }

    /** Popup "⋮" ở toolbar chứa công tắc Chế độ xem, giống popup tùy chọn tài liệu của Google Docs. */
    private fun showDocxOptionsMenu() {
        val popupView = LayoutInflater.from(this).inflate(R.layout.popup_docx_options, binding.root, false)
        val switchViewMode = popupView.findViewById<MaterialSwitch>(R.id.switch_view_mode)
        switchViewMode.isChecked = layoutViewModeEnabled

        val popup = PopupWindow(
            popupView,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.elevation = 12f

        switchViewMode.setOnCheckedChangeListener { _, isChecked ->
            if (layoutViewModeEnabled == isChecked) return@setOnCheckedChangeListener
            layoutViewModeEnabled = isChecked
            currentFile?.let { loadDocx(it) }
        }

        popup.showAsDropDown(binding.btnDocxMenu, 0, 0)
    }

    private fun resolveIncomingFile(): File? {
        val pathExtra = intent.getStringExtra(EXTRA_FILE_PATH)
        if (pathExtra != null) return File(pathExtra)
        val data: Uri = intent.data ?: return null
        return when (data.scheme) {
            "file" -> data.path?.let { File(it) }
            "content" -> {
                val name = queryDisplayName(data) ?: "shared_${System.currentTimeMillis()}.docx"
                val target = File(cacheDir, name)
                try {
                    contentResolver.openInputStream(data)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    target
                } catch (e: Exception) {
                    LogBus.error("Không thể đọc file DOCX được chia sẻ: $name", source = "DOCX", throwable = e)
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

    private fun loadDocx(file: File) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val content = try {
                withContext(Dispatchers.IO) { parseDocx(file, layoutViewModeEnabled) }
            } catch (e: Exception) {
                LogBus.error("Không đọc được nội dung DOCX: ${file.path}", source = "DOCX", throwable = e)
                null
            }
            binding.progressBar.visibility = View.GONE
            if (isFinishing || isDestroyed) return@launch
            if (content == null) {
                showError()
                return@launch
            }
            binding.tvContent.text = content
        }
    }

    /**
     * Parse thủ công word/document.xml (namespace w:) lấy text kèm bold/italic ở mức run
     * (<w:r>). <w:b/>, <w:i/> là thẻ tự đóng nằm trong <w:rPr> của run - reset cờ ở </w:r>
     * chứ KHÔNG reset ở </w:b> (thẻ tự đóng đóng ngay sau khi mở).
     *
     * Khi [layoutMode] bật, còn đọc <w:pPr> của mỗi đoạn (<w:p>) để lấy căn lề
     * (<w:jc w:val="center|right|both".../>) và thụt lề dòng đầu (<w:ind w:firstLine="..."/>),
     * áp AlignmentSpan/LeadingMarginSpan cho toàn đoạn - gần với bố cục gốc hơn thay vì
     * chỉ chảy trái đều như chế độ tắt.
     */
    private fun parseDocx(file: File, layoutMode: Boolean): CharSequence {
        val ssb = SpannableStringBuilder()
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("word/document.xml")
                ?: throw IllegalStateException("Không tìm thấy word/document.xml (file có thể không phải .docx hợp lệ)")
            zip.getInputStream(entry).use { input ->
                val parser = Xml.newPullParser()
                parser.setInput(input, "UTF-8")
                var bold = false
                var italic = false
                var inParagraphProps = false
                var paragraphStart = 0
                var alignment: Layout.Alignment? = null
                var firstLineIndentPx = 0
                val density = resources.displayMetrics.density
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        XmlPullParser.START_TAG -> {
                            when (localName(parser.name)) {
                                "p" -> {
                                    paragraphStart = ssb.length
                                    alignment = null
                                    firstLineIndentPx = 0
                                }
                                "pPr" -> inParagraphProps = true
                                "jc" -> if (layoutMode && inParagraphProps) {
                                    alignment = when (parser.getAttributeValue(null, "val")) {
                                        "center" -> Layout.Alignment.ALIGN_CENTER
                                        "right", "end" -> Layout.Alignment.ALIGN_OPPOSITE
                                        else -> null
                                    }
                                }
                                "ind" -> if (layoutMode && inParagraphProps) {
                                    val firstLineTwips = parser.getAttributeValue(null, "firstLine")?.toIntOrNull()
                                    if (firstLineTwips != null && firstLineTwips > 0) {
                                        // 1 twip = 1/20 pt; đổi pt -> px theo mật độ màn hình hiện tại
                                        firstLineIndentPx = ((firstLineTwips / 20f) * density).toInt()
                                    }
                                }
                                "b" -> bold = true
                                "i" -> italic = true
                                "tab" -> ssb.append('\t')
                                "br" -> ssb.append('\n')
                                "t" -> {
                                    val text = parser.nextText()
                                    val start = ssb.length
                                    ssb.append(text)
                                    if (bold) ssb.setSpan(StyleSpan(Typeface.BOLD), start, ssb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                                    if (italic) ssb.setSpan(StyleSpan(Typeface.ITALIC), start, ssb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                                    // nextText() đã đưa parser tới END_TAG "t" - đồng bộ lại event rồi continue,
                                    // tránh gọi parser.next() lần nữa bên dưới làm nhảy quá 1 sự kiện
                                    event = parser.eventType
                                    continue
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            when (localName(parser.name)) {
                                "r" -> { bold = false; italic = false }
                                "pPr" -> inParagraphProps = false
                                "p" -> {
                                    if (layoutMode) {
                                        val end = ssb.length
                                        if (alignment != null && end > paragraphStart) {
                                            ssb.setSpan(AlignmentSpan.Standard(alignment!!), paragraphStart, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                                        }
                                        if (firstLineIndentPx > 0 && end > paragraphStart) {
                                            ssb.setSpan(
                                                LeadingMarginSpan.Standard(firstLineIndentPx, 0),
                                                paragraphStart, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                            )
                                        }
                                    }
                                    ssb.append("\n\n")
                                }
                            }
                        }
                    }
                    event = parser.next()
                }
            }
        }
        return ssb
    }

    private fun localName(name: String) = name.substringAfterLast(':')

    private fun showError() {
        binding.layoutError.visibility = View.VISIBLE
        binding.scrollContent.visibility = View.GONE
    }

    override fun onDestroy() {
        deleteIfTempCacheFile(intent.getStringExtra(EXTRA_FILE_PATH))
        super.onDestroy()
    }

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"
    }
}

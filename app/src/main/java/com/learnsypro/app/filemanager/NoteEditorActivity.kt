package com.learnsypro.app.filemanager
import com.learnsypro.app.R

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.text.HtmlCompat
import androidx.core.text.getSpans
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.learnsypro.app.databinding.ActivityNoteEditorBinding
import com.learnsypro.app.databinding.ItemNotePageBinding
import com.learnsypro.app.filemanager.notes.NoteFileStore
import com.learnsypro.app.filemanager.notes.NotePaperStyle
import com.learnsypro.app.filemanager.notes.NotePaperView
import com.learnsypro.app.filemanager.util.ActivityTransitions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Màn hình soạn ghi chú — "bê" đủ bộ công cụ định dạng như 1 app Note thật: in đậm/nghiêng/gạch
 * chân, tô màu chữ, cỡ chữ, gạch đầu dòng, checklist, chèn ảnh. Dùng thẳng Spannable của
 * EditText (không cần thư viện rich-text ngoài) — mỗi nút áp/gỡ 1 loại Span lên đúng vùng bôi
 * đen hiện tại.
 *
 * NHIỀU TRANG + NỀN GIẤY (giống Samsung Notes/Apple Notes): ghi chú có thể có nhiều "trang", mỗi
 * trang là 1 EditText riêng (item_note_page.xml) xếp DỌC nối tiếp trong pages_container — cuộn
 * dọc liên tục qua các trang (kiểu Apple Notes/Notability), KHÔNG vật ngang từng trang. Nền giấy
 * (Trống/Kẻ dòng/Ô vuông caro) áp dụng CHUNG cho toàn bộ ghi chú, chọn 1 lần qua nút bảng màu —
 * mỗi trang tự vẽ lại nền theo đúng style đó (NotePaperView đặt sau EditText trong FrameLayout).
 *
 * Toàn bộ hàm định dạng (đậm/nghiêng/màu/cỡ chữ/dán/copy/cuộn-theo-con-trỏ) trước đây thao tác
 * thẳng lên 1 EditText cố định (et_content) — giờ thao tác lên activeEditText(), tức EditText của
 * TRANG ĐANG CÓ FOCUS, để bấm nút định dạng luôn áp đúng lên trang đang gõ dở, không cố định vào
 * 1 trang nào.
 *
 * Lưu = convert từng trang (Spannable) -> HTML thật, nối các trang bằng marker
 * <!--LEARNSY_PAGE_BREAK--> trong 1 file .html duy nhất (không dùng database riêng) — ghi chú vì
 * vậy vẫn xem được, mở được bằng bất kỳ trình duyệt/HtmlViewerActivity nào (marker chỉ là HTML
 * comment, trình duyệt bỏ qua, hiện liền mạch các trang nối tiếp — đúng với kiểu cuộn dọc).
 */
class NoteEditorActivity : LearnsyFileManagerActivity() {

    private lateinit var binding: ActivityNoteEditorBinding
    private var existingFile: File? = null
    private var hasUnsavedChanges = false
    private var paperStyle: NotePaperStyle = NotePaperStyle.BLANK

    /** 1 trang = binding của item_note_page.xml (EditText + NotePaperView) đã inflate vào pages_container. */
    private val pages = mutableListOf<ItemNotePageBinding>()

    /** Separator (đường kẻ mỏng) đặt NGAY TRƯỚC mỗi trang, ánh xạ theo cùng chỉ số với pages —
     * pages[0] không có separator (null), pages[i] với i>0 có separator ở separators[i]. Giữ
     * tham chiếu trực tiếp để removePageAt() xoá đúng separator của đúng trang, không phải đoán
     * qua vị trí trong pagesContainer (dễ xoá nhầm root của trang liền trước). */
    private val pageSeparators = mutableListOf<View?>()

    /** EditText của trang đang có focus — mọi nút định dạng/dán/copy/cuộn thao tác lên đúng trang này.
     * Fallback về trang cuối cùng nếu chưa trang nào có focus (vd. lúc mới mở, trước khi người dùng bấm vào). */
    private fun activeEditText(): EditText =
        pages.firstOrNull { it.etPageContent.hasFocus() }?.etPageContent ?: pages.last().etPageContent

    /** EditText của trang đang gõ TẠI THỜI ĐIỂM bấm nút chèn ảnh — chốt lại ở đây vì khi trình
     * chọn ảnh hệ thống đưa app xuống nền rồi quay lại, focus có thể đã đổi (Activity resume);
     * dùng activeEditText() lại trong callback dễ chèn nhầm ảnh vào trang khác với trang người
     * dùng đang thao tác lúc bấm nút. */
    private var pendingImageTarget: EditText? = null

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val target = pendingImageTarget
        if (uri != null && target != null) insertImageAtCursor(uri, target)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        com.learnsypro.app.filemanager.util.WindowInsetsUtils.applyTopInsetHeight(binding.statusBarSpacer)

        binding.toolbar.title = getString(R.string.notes_new)
        binding.toolbar.setNavigationOnClickListener { confirmBackIfNeeded() }
        binding.toolbar.setOnMenuItemClickListener { onMenuItemSelected(it) }
        // Menu "Xóa" chỉ có ý nghĩa với ghi chú ĐÃ TỒN TẠI trên đĩa — ẩn khi đang tạo mới, vì
        // chưa có file nào để xóa (bấm sẽ không làm gì, dễ gây hiểu nhầm là lỗi).
        binding.toolbar.menu.findItem(R.id.action_delete)?.isVisible = false

        setupFormattingToolbar()
        setupPageControls()
        trackUnsavedChanges()

        val path = intent.getStringExtra(EXTRA_FILE_PATH)
        if (path != null) {
            loadExistingNote(File(path))
        } else {
            // Ghi chú mới: luôn bắt đầu với đúng 1 trang trống.
            addPage(initialText = null, focus = true)
            updatePageIndicator()
        }

        onBackPressedDispatcher.addCallback(this) { confirmBackIfNeeded() }
    }

    /**
     * Tạo 1 trang mới (item_note_page.xml), gắn watcher/focus-listener/nền giấy, thêm vào cuối
     * pages_container — kèm 1 separator mỏng phía trên nếu không phải trang đầu tiên, để phân
     * biệt ranh giới giữa các trang khi cuộn dọc qua (giống đường ngăn trang của Notability).
     */
    private fun addPage(initialText: CharSequence?, focus: Boolean): ItemNotePageBinding {
        var separator: View? = null
        if (pages.isNotEmpty()) {
            separator = View(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    (2 * resources.displayMetrics.density).toInt()
                ).apply { topMargin = (8 * resources.displayMetrics.density).toInt(); bottomMargin = topMargin }
                setBackgroundColor(getColorCompat(R.color.outline_variant))
            }
            binding.pagesContainer.addView(separator)
        }
        pageSeparators.add(separator)

        val pageBinding = ItemNotePageBinding.inflate(LayoutInflater.from(this), binding.pagesContainer, false)
        pageBinding.notePaper.style = paperStyle
        if (initialText != null) pageBinding.etPageContent.setText(initialText)
        binding.pagesContainer.addView(pageBinding.root)
        pages.add(pageBinding)

        pageBinding.etPageContent.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { hasUnsavedChanges = true }
            // FIX (bàn phím che chỗ đang gõ): mỗi trang tự theo dõi con trỏ CỦA CHÍNH NÓ, cuộn
            // ScrollView cha tới đúng dòng đang gõ trong trang đó — xem scrollToCursor(EditText).
            override fun afterTextChanged(s: Editable?) { scrollToCursor(pageBinding.etPageContent) }
        })
        pageBinding.etPageContent.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) scrollToCursor(pageBinding.etPageContent) }

        if (focus) pageBinding.etPageContent.requestFocus()
        return pageBinding
    }

    private fun getColorCompat(resId: Int): Int = androidx.core.content.ContextCompat.getColor(this, resId)

    /** Nút "+ Thêm trang" / "Xoá trang này" / "Kiểu nền giấy" — nằm trên thanh chỉ số trang. */
    private fun setupPageControls() {
        binding.btnAddPage.setOnClickListener {
            addPage(initialText = null, focus = true)
            updatePageIndicator()
            hasUnsavedChanges = true
            binding.scrollContent.post { binding.scrollContent.fullScroll(android.view.View.FOCUS_DOWN) }
        }
        binding.btnDeletePage.setOnClickListener { confirmDeleteCurrentPage() }
        binding.btnPaperStyle.setOnClickListener { showPaperStylePicker() }
    }

    private fun updatePageIndicator() {
        binding.tvPageIndicator.text = getString(R.string.notes_page_indicator_format, currentPageIndex() + 1, pages.size)
        // Không cho xoá trang cuối cùng còn lại — ghi chú luôn phải có ít nhất 1 trang.
        binding.btnDeletePage.isEnabled = pages.size > 1
        binding.btnDeletePage.alpha = if (pages.size > 1) 1f else 0.35f
    }

    /** Vị trí (0-based) của trang đang có focus trong danh sách pages — dùng để hiện "Trang X/Y". */
    private fun currentPageIndex(): Int {
        val focused = pages.indexOfFirst { it.etPageContent.hasFocus() }
        return if (focused >= 0) focused else pages.lastIndex.coerceAtLeast(0)
    }

    private fun confirmDeleteCurrentPage() {
        if (pages.size <= 1) {
            Toast.makeText(this, getString(R.string.notes_cannot_delete_last_page), Toast.LENGTH_SHORT).show()
            return
        }
        val index = currentPageIndex()
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.notes_delete_page_confirm_title))
            .setMessage(getString(R.string.notes_delete_page_confirm_message))
            .setPositiveButton(getString(R.string.btn_delete)) { _, _ -> removePageAt(index) }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun removePageAt(index: Int) {
        if (index !in pages.indices || pages.size <= 1) return
        val pageBinding = pages.removeAt(index)
        val separator = pageSeparators.removeAt(index)
        // Separator của trang bị xoá gắn NGAY TRƯỚC nó — xoá theo đúng tham chiếu, không đoán
        // qua vị trí. Nếu xoá trang 0 (không có separator, vì pages[0] luôn null) mà vẫn còn
        // trang khác phía sau, trang mới thành index 0 đó lại đang "thừa" 1 separator phía trước
        // nó (separator nối nó với trang 0 cũ) — phải xoá luôn separator đó để trang đầu mới
        // không có đường kẻ mồ côi phía trên.
        separator?.let { binding.pagesContainer.removeView(it) }
        binding.pagesContainer.removeView(pageBinding.root)
        if (index == 0 && pageSeparators.isNotEmpty()) {
            pageSeparators[0]?.let { binding.pagesContainer.removeView(it) }
            pageSeparators[0] = null
        }
        hasUnsavedChanges = true
        updatePageIndicator()
        pages.lastOrNull()?.etPageContent?.requestFocus()
    }

    private fun showPaperStylePicker() {
        val styles = NotePaperStyle.entries.toTypedArray()
        val names = arrayOf(
            getString(R.string.notes_paper_blank),
            getString(R.string.notes_paper_ruled),
            getString(R.string.notes_paper_grid)
        )
        val checkedIndex = styles.indexOf(paperStyle).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.notes_paper_style))
            .setSingleChoiceItems(names, checkedIndex) { dialog, which ->
                paperStyle = styles[which]
                pages.forEach { it.notePaper.style = paperStyle }
                hasUnsavedChanges = true
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun trackUnsavedChanges() {
        // Chỉ cần theo dõi tiêu đề ở đây — mỗi trang (EditText riêng) đã tự gắn watcher đặt
        // hasUnsavedChanges=true ngay trong addPage(), vì mỗi trang thêm/xoá động về sau.
        binding.etTitle.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { hasUnsavedChanges = true }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    // ---------------- đọc ghi chú cũ (nếu sửa) ----------------

    private fun loadExistingNote(file: File) {
        existingFile = file
        binding.toolbar.menu.findItem(R.id.action_delete)?.isVisible = true
        lifecycleScope.launch {
            val html = withContext(Dispatchers.IO) { try { file.readText() } catch (e: Exception) { "" } }
            val title = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1)?.trim().orEmpty()
            val bodyHtml = Regex("<body[^>]*>(.*?)</body>", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1) ?: html

            // FIX tương thích ngược: file ghi chú tạo TRƯỚC khi có tính năng nhiều trang/nền giấy
            // không có <meta name="paper"> — NotePaperStyle.fromStorageValue(null) tự trả về BLANK
            // (đúng hành vi cũ: không có nền kẻ). Tương tự, file cũ không có marker
            // LEARNSY_PAGE_BREAK -> split() trả về đúng 1 phần tử -> mở lại thành 1 trang duy
            // nhất, y hệt hành vi trước khi có tính năng nhiều trang.
            paperStyle = NotePaperStyle.fromStorageValue(
                Regex("<meta\\s+name=\"paper\"\\s+content=\"(.*?)\"").find(html)?.groupValues?.get(1)
            )
            val pageHtmlList = bodyHtml.split(PAGE_BREAK_MARKER)

            binding.etTitle.setText(HtmlCompat.fromHtml(title, HtmlCompat.FROM_HTML_MODE_LEGACY).toString())
            pages.clear()
            pageSeparators.clear()
            binding.pagesContainer.removeAllViews()
            pageHtmlList.forEachIndexed { index, pageHtml ->
                // FROM_HTML_MODE_LEGACY hiểu đúng <b>/<i>/<u>/<span style="color/font-size">/<img
                // src="data:...">: giữ nguyên toàn bộ định dạng đã lưu trước đó khi mở lại để sửa.
                val spanned = HtmlCompat.fromHtml(pageHtml, HtmlCompat.FROM_HTML_MODE_LEGACY, { source ->
                    // Ảnh nhúng base64 (data:image/...) -> giải mã trực tiếp thành Drawable để
                    // hiện trong EditText, KHÔNG cần tải file ngoài vì ảnh đã nằm sẵn trong chính
                    // file HTML.
                    decodeBase64Image(source)
                }, null)
                addPage(initialText = spanned, focus = index == pageHtmlList.lastIndex)
            }
            updatePageIndicator()
            binding.toolbar.title = binding.etTitle.text.toString().ifBlank { getString(R.string.notes_untitled) }
            hasUnsavedChanges = false
        }
    }

    private fun decodeBase64Image(dataUri: String): android.graphics.drawable.Drawable? {
        return try {
            val base64 = dataUri.substringAfter("base64,", missingDelimiterValue = "")
            if (base64.isEmpty()) return null
            val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val drawable = android.graphics.drawable.BitmapDrawable(resources, bitmap)
            // Ảnh chèn giữ tối đa chiều rộng màn hình, không tràn khung soạn thảo.
            val maxWidth = (resources.displayMetrics.widthPixels - 64).coerceAtLeast(200)
            val scale = if (bitmap.width > maxWidth) maxWidth.toFloat() / bitmap.width else 1f
            drawable.setBounds(0, 0, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1))
            drawable
        } catch (e: Exception) {
            null
        }
    }

    // ---------------- thanh công cụ định dạng ----------------

    private fun setupFormattingToolbar() {
        binding.btnBold.setOnClickListener { toggleStyleSpan(Typeface.BOLD) }
        binding.btnItalic.setOnClickListener { toggleStyleSpan(Typeface.ITALIC) }
        binding.btnUnderline.setOnClickListener { toggleUnderline() }
        binding.btnColor.setOnClickListener { showColorPicker() }
        binding.btnFontSize.setOnClickListener { showSizePicker() }
        binding.btnBulletList.setOnClickListener { insertLinePrefix("•  ") }
        binding.btnChecklist.setOnClickListener { insertLinePrefix("☐  ") }
        binding.btnInsertImage.setOnClickListener {
            pendingImageTarget = activeEditText()
            imagePickerLauncher.launch("image/*")
        }
        binding.btnPaste.setOnClickListener { pasteFromClipboard() }
        binding.btnCopyAll.setOnClickListener { copyAllContent() }
    }

    private val cursorScrollPaddingPx: Int by lazy {
        (CURSOR_SCROLL_PADDING_DP * resources.displayMetrics.density).toInt()
    }

    /**
     * Cuộn ScrollView tới đúng vị trí con trỏ đang gõ trong TRANG được truyền vào — gọi mỗi khi
     * nội dung hoặc vị trí con trỏ của 1 trang đổi (afterTextChanged/focus của đúng EditText đó,
     * xem addPage()). requestRectangleOnScreen tính đúng toạ độ dòng chứa con trỏ (dùng
     * layout.getLineForOffset + getLineBottom của chính EditText) rồi yêu cầu ScrollView cha cuộn
     * tới, tránh tình trạng gõ xuống cuối trang bị bàn phím che mất chỗ đang viết mà không hay.
     *
     * FIX (con trỏ chỉ hở ra một xíu sát mép bàn phím, chưa thấy được chữ đang gõ):
     * requestRectangleOnScreen chỉ cuộn VỪA ĐỦ để rect lọt vào vùng nhìn thấy — nếu rect chỉ cao
     * đúng 1 dòng, ScrollView dừng cuộn ngay khi mép dưới dòng đó chạm mép trên bàn phím, để lại
     * đúng khoảng hở bằng 1 dòng như trong ảnh chụp. Mở rộng rect thêm cursorScrollPaddingPx
     * (quy đổi từ CURSOR_SCROLL_PADDING_DP) phía dưới dòng con trỏ — khoảng không gian giả định
     * của vài dòng kế tiếp — để ScrollView phải cuộn xa hơn, đẩy dòng đang gõ lên hẳn phía trên,
     * cách mép bàn phím 1 khoảng thoải mái thay vì nằm sát rịn.
     *
     * NHIỀU TRANG: request tính theo toạ độ CỤC BỘ của content trong hệ toạ độ của chính nó —
     * requestRectangleOnScreen tự cộng dồn offset của tất cả các View cha (kể cả các trang khác
     * xếp phía trên nó trong pagesContainer) khi lan lên tới ScrollView, nên không cần tự tính vị
     * trí trang thứ mấy ở đây.
     */
    private fun scrollToCursor(content: EditText) {
        val layout = content.layout ?: return
        val cursor = content.selectionStart.coerceIn(0, content.text?.length ?: 0)
        val line = layout.getLineForOffset(cursor)
        val top = layout.getLineTop(line)
        val bottom = layout.getLineBottom(line) + cursorScrollPaddingPx
        val rect = android.graphics.Rect(0, top, content.width, bottom)
        content.requestRectangleOnScreen(rect, false)
    }

    /** Dán nội dung clipboard vào đúng vị trí con trỏ trong etContent — thay cho việc phải
     * long-press mở menu ngữ cảnh mặc định (dễ bấm nhầm Cắt/Chọn tất cả). */
    private fun pasteFromClipboard() {
        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = clipboard.primaryClip
        if (clip == null || clip.itemCount == 0) {
            Toast.makeText(this, getString(R.string.notes_clipboard_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val pasted = clip.getItemAt(0).coerceToText(this)
        if (pasted.isNullOrEmpty()) {
            Toast.makeText(this, getString(R.string.notes_clipboard_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val editable = activeEditText().text
        val start = activeEditText().selectionStart.coerceAtLeast(0)
        val end = activeEditText().selectionEnd.coerceAtLeast(start)
        editable.replace(start, end, pasted)
        activeEditText().setSelection(start + pasted.length)
        hasUnsavedChanges = true
        Toast.makeText(this, getString(R.string.notes_pasted), Toast.LENGTH_SHORT).show()
    }

    /** Copy toàn bộ nội dung ghi chú (chữ thuần, không kèm định dạng) — nối TẤT CẢ các trang lại
     * với nhau (cách nhau bằng 2 dấu xuống dòng) vào clipboard, copy nhanh nguyên ghi chú ra
     * ngoài mà không cần tự bôi đen từ đầu tới cuối rồi long-press Copy. */
    private fun copyAllContent() {
        val text = pages.joinToString("\n\n") { it.etPageContent.text?.toString().orEmpty() }
        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(getString(R.string.notes_new), text))
        Toast.makeText(this, getString(R.string.notes_copied_all), Toast.LENGTH_SHORT).show()
    }

    /** true nếu người dùng có bôi đen 1 đoạn TRONG TRANG ĐANG FOCUS — bold/nghiêng/gạch chân/
     * màu/cỡ chữ CHỈ áp lên đúng đoạn đó, giống mọi app Note thật. */
    private fun requireSelection(): IntRange? {
        val target = activeEditText()
        val start = target.selectionStart
        val end = target.selectionEnd
        if (start == end) {
            Toast.makeText(this, getString(R.string.notes_select_text_first), Toast.LENGTH_SHORT).show()
            return null
        }
        return minOf(start, end) until maxOf(start, end)
    }

    /**
     * Bật/tắt kiểu chữ (đậm/nghiêng) trên vùng bôi đen — kiểm tra span đã có ở NGAY ĐẦU vùng chọn
     * để quyết định thêm hay gỡ, giống hành vi toggle thật của mọi trình soạn thảo (bôi đen đoạn
     * đã đậm rồi bấm lại nút Đậm -> hết đậm, thay vì luôn cộng dồn thêm span mới mỗi lần bấm).
     */
    private fun toggleStyleSpan(style: Int) {
        val range = requireSelection() ?: return
        val editable = activeEditText().text
        val existing = editable.getSpans<StyleSpan>(range.first, range.first + 1).firstOrNull { it.style == style }
        if (existing != null) {
            removeSpanFromRange(editable, StyleSpan::class.java, range) { it.style == style }
        } else {
            editable.setSpan(StyleSpan(style), range.first, range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        hasUnsavedChanges = true
    }

    private fun toggleUnderline() {
        val range = requireSelection() ?: return
        val editable = activeEditText().text
        val hasUnderline = editable.getSpans<UnderlineSpan>(range.first, range.first + 1).isNotEmpty()
        if (hasUnderline) {
            removeSpanFromRange(editable, UnderlineSpan::class.java, range) { true }
        } else {
            editable.setSpan(UnderlineSpan(), range.first, range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        hasUnsavedChanges = true
    }

    /**
     * Gỡ đúng phần span nằm TRONG vùng bôi đen — nếu span cũ trải dài RỘNG HƠN vùng đang chọn
     * (vd. cả câu đã đậm, giờ chỉ bôi đen 1 từ để bỏ đậm riêng từ đó), phải CẮT span cũ thành 2
     * đoạn còn lại (trước và sau vùng chọn) thay vì xóa nguyên span, để phần chữ ngoài vùng chọn
     * không bị mất định dạng theo.
     */
    private fun <T : Any> removeSpanFromRange(editable: Editable, type: Class<T>, range: IntRange, matches: (T) -> Boolean) {
        val spans = editable.getSpans(range.first, range.last + 1, type)
        for (span in spans) {
            if (!matches(span)) continue
            val spanStart = editable.getSpanStart(span)
            val spanEnd = editable.getSpanEnd(span)
            editable.removeSpan(span)
            if (spanStart < range.first) {
                val newSpan = cloneSpan(span) ?: continue
                editable.setSpan(newSpan, spanStart, range.first, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (spanEnd > range.last + 1) {
                val newSpan = cloneSpan(span) ?: continue
                editable.setSpan(newSpan, range.last + 1, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    private fun <T : Any> cloneSpan(span: T): Any? = when (span) {
        is StyleSpan -> StyleSpan(span.style)
        is UnderlineSpan -> UnderlineSpan()
        is ForegroundColorSpan -> ForegroundColorSpan(span.foregroundColor)
        is RelativeSizeSpan -> RelativeSizeSpan(span.sizeChange)
        else -> null
    }

    private fun showColorPicker() {
        val range = requireSelection() ?: return
        val target = activeEditText() // chốt ngay lúc mở dialog — callback không gọi lại activeEditText() để tránh lệch nếu focus đổi trong lúc dialog đang mở
        val colors = intArrayOf(
            android.graphics.Color.parseColor("#1F2937"), // đen (mặc định)
            android.graphics.Color.parseColor("#EF4444"), // đỏ
            android.graphics.Color.parseColor("#F59E0B"), // cam
            android.graphics.Color.parseColor("#10B981"), // xanh lá
            android.graphics.Color.parseColor("#3B82F6"), // xanh dương
            android.graphics.Color.parseColor("#8B5CF6")  // tím
        )
        val names = arrayOf("Đen", "Đỏ", "Cam", "Xanh lá", "Xanh dương", "Tím")
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.notes_pick_color))
            .setItems(names) { _, which ->
                val editable = target.text
                removeSpanFromRange(editable, ForegroundColorSpan::class.java, range) { true }
                editable.setSpan(ForegroundColorSpan(colors[which]), range.first, range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                hasUnsavedChanges = true
            }
            .show()
    }

    private fun showSizePicker() {
        val range = requireSelection() ?: return
        val target = activeEditText() // chốt ngay lúc mở dialog, cùng lý do showColorPicker()
        val sizes = floatArrayOf(0.8f, 1f, 1.4f, 1.8f)
        val names = arrayOf(
            getString(R.string.notes_size_small), getString(R.string.notes_size_normal),
            getString(R.string.notes_size_large), getString(R.string.notes_size_huge)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.notes_pick_size))
            .setItems(names) { _, which ->
                val editable = target.text
                removeSpanFromRange(editable, RelativeSizeSpan::class.java, range) { true }
                editable.setSpan(RelativeSizeSpan(sizes[which]), range.first, range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                hasUnsavedChanges = true
            }
            .show()
    }

    /** Chèn ký hiệu gạch đầu dòng/checklist vào ĐẦU DÒNG hiện tại (nơi con trỏ đang đứng, trong
     * trang đang focus), không cần bôi đen. */
    private fun insertLinePrefix(prefix: String) {
        val target = activeEditText()
        val editable = target.text
        val cursor = target.selectionStart.coerceAtLeast(0)
        val lineStart = editable.toString().lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
        editable.insert(lineStart, prefix)
        target.setSelection(cursor + prefix.length)
        hasUnsavedChanges = true
    }

    /**
     * Nén ảnh trước khi nhúng base64 vào HTML.
     * - Giới hạn cạnh dài nhất 800px (đủ nét trên điện thoại, nhẹ hơn 1080).
     * - Ưu tiên WebP (API 30+) vì nhỏ hơn JPEG ~25-40% với cùng chất lượng.
     * - Chất lượng adaptive: ảnh lớn nén mạnh hơn.
     * - Recycle bitmap để tránh OOM khi chèn nhiều ảnh.
     * - Báo kích thước sau nén để người dùng biết.
     */
    private fun insertImageAtCursor(uri: Uri, target: EditText) {
        Toast.makeText(this, "Đang xử lý ảnh…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val input = contentResolver.openInputStream(uri) ?: return@withContext null
                    val original = android.graphics.BitmapFactory.decodeStream(input)
                    input.close()
                    if (original == null) return@withContext null

                    val maxDim = 800
                    val scale = if (maxOf(original.width, original.height) > maxDim) {
                        maxDim.toFloat() / maxOf(original.width, original.height)
                    } else 1f

                    val resized = if (scale < 1f) {
                        android.graphics.Bitmap.createScaledBitmap(
                            original,
                            (original.width * scale).toInt().coerceAtLeast(1),
                            (original.height * scale).toInt().coerceAtLeast(1),
                            true
                        ).also {
                            if (it !== original) original.recycle()
                        }
                    } else original

                    // Adaptive quality: ảnh càng lớn (sau resize) thì nén mạnh hơn một chút
                    val quality = when {
                        resized.byteCount > 2_000_000 -> 65
                        resized.byteCount > 1_000_000 -> 72
                        else -> 78
                    }

                    val output = ByteArrayOutputStream()
                    val useWebp = android.os.Build.VERSION.SDK_INT >= 30
                    val format = if (useWebp) {
                        android.graphics.Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        android.graphics.Bitmap.CompressFormat.JPEG
                    }
                    val mime = if (useWebp) "image/webp" else "image/jpeg"
                    resized.compress(format, quality, output)
                    if (resized !== original && !resized.isRecycled) {
                        // original đã recycle ở trên nếu có resize
                    } else if (scale < 1f && !original.isRecycled) {
                        // đã xử lý
                    }
                    // An toàn: recycle resized nếu không còn cần
                    // (bitmap đã encode xong)
                    val bytes = output.toByteArray()
                    if (!resized.isRecycled) resized.recycle()

                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    Triple(base64, mime, bytes.size)
                } catch (e: Exception) {
                    null
                }
            }

            if (result == null) {
                Toast.makeText(this@NoteEditorActivity, getString(R.string.error_generic), Toast.LENGTH_SHORT).show()
                return@launch
            }
            val (base64, mime, sizeBytes) = result
            val dataUri = "data:$mime;base64,$base64"
            val drawable = decodeBase64Image(dataUri) ?: return@launch
            val editable = target.text
            val cursor = target.selectionStart.coerceAtLeast(0)
            val span = ImageSpan(drawable)
            // Marker ẩn để toHtml xuất đúng <img src="data:...">
            val marker = "\u200B[[IMG:$dataUri]]\u200B"
            editable.insert(cursor, " $marker ")
            editable.setSpan(span, cursor, cursor + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            target.setSelection(cursor + marker.length + 2)
            hasUnsavedChanges = true

            val sizeKb = sizeBytes / 1024
            Toast.makeText(
                this@NoteEditorActivity,
                "Đã chèn ảnh (~${sizeKb} KB)",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ---------------- lưu / xóa ----------------

    private fun onMenuItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_save -> saveNote()
            R.id.action_delete -> confirmDelete()
        }
        return true
    }

    private fun saveNote() {
        val title = binding.etTitle.text.toString().trim().ifBlank { getString(R.string.notes_untitled) }
        val html = buildHtmlDocument(title)
        lifecycleScope.launch {
            val target = existingFile ?: File(NoteFileStore.notesDir, NoteFileStore.suggestFileName(title))
            withContext(Dispatchers.IO) {
                NoteFileStore.notesDir.mkdirs()
                target.writeText(html)
            }
            existingFile = target
            hasUnsavedChanges = false
            binding.toolbar.menu.findItem(R.id.action_delete)?.isVisible = true
            binding.toolbar.title = title
            Toast.makeText(this@NoteEditorActivity, getString(R.string.notes_saved), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Ghép toàn bộ tài liệu HTML: <meta name="paper"> lưu kiểu nền giấy chung của ghi chú, rồi
     * nối nội dung TỪNG TRANG (mỗi trang qua buildPageBodyHtml()) bằng PAGE_BREAK_MARKER — marker
     * chỉ là HTML comment nên trình duyệt/HtmlViewerActivity bỏ qua, hiện các trang liền mạch nối
     * tiếp nhau, đúng với cách hiển thị cuộn dọc trong app. loadExistingNote() split() lại theo
     * đúng marker này để dựng lại từng trang khi mở file để sửa.
     */
    private fun buildHtmlDocument(title: String): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\">")
        sb.append("<meta name=\"paper\" content=\"${paperStyle.storageValue}\">")
        sb.append("<title>${escapeHtml(title)}</title>")
        sb.append("<style>body{font-family:sans-serif;font-size:16px;line-height:1.5;padding:16px;} img{max-width:100%;height:auto;}</style>")
        sb.append("</head><body>")
        pages.forEachIndexed { index, page ->
            if (index > 0) sb.append(PAGE_BREAK_MARKER)
            sb.append(buildPageBodyHtml(page.etPageContent.text))
        }
        sb.append("</body></html>")
        return sb.toString()
    }

    /**
     * Convert Spannable (nội dung đang soạn CỦA 1 TRANG) -> đoạn HTML thân bài tương ứng. Xử lý
     * thủ công thay vì dùng Html.toHtml() có sẵn của Android vì hàm đó KHÔNG hỗ trợ export
     * RelativeSizeSpan/ImageSpan đúng cách (chỉ export được StyleSpan/UnderlineSpan/
     * ForegroundColorSpan cơ bản) — quét từng "đoạn span liên tục" (transition point) và tự bọc
     * thẻ HTML tương ứng cho từng đoạn.
     */
    private fun buildPageBodyHtml(content: Editable): String {
        val sb = StringBuilder()
        val text = content.toString()
        var i = 0
        while (i < text.length) {
            // Placeholder ảnh: nhảy qua nguyên marker, xuất thẳng thẻ <img>, bỏ qua ký tự
            // placeholder hiển thị của ImageSpan (không xuất ra HTML, chỉ dùng để hiện trong lúc soạn).
            val markerMatch = Regex("\u200B\\[\\[IMG:(.*?)]]\u200B").find(text, i)
            if (markerMatch != null && markerMatch.range.first == i) {
                sb.append("<br><img src=\"${markerMatch.groupValues[1]}\"><br>")
                i = markerMatch.range.last + 1
                continue
            }
            val c = text[i]
            if (c == '\n') {
                sb.append("<br>")
                i++
                continue
            }
            // Đoạn liên tục có CÙNG bộ span (đậm/nghiêng/gạch chân/màu/cỡ) -> gộp lại xuất 1 lần
            // thay vì mỗi ký tự 1 thẻ, để HTML xuất ra gọn và dễ đọc lại đúng khi mở file sau này.
            var j = i + 1
            while (j < text.length && text[j] != '\n' && sameSpansAt(content, i, j) &&
                Regex("\u200B\\[\\[IMG:").find(text, j)?.range?.first != j
            ) j++
            val segment = text.substring(i, j)
            sb.append(wrapSegmentWithTags(content, i, segment))
            i = j
        }
        return sb.toString()
    }

    private fun sameSpansAt(content: Editable, posA: Int, posB: Int): Boolean {
        fun spanSetAt(pos: Int): Set<String> {
            val out = mutableSetOf<String>()
            content.getSpans<StyleSpan>(pos, pos + 1).forEach { out.add("style:${it.style}") }
            content.getSpans<UnderlineSpan>(pos, pos + 1).forEach { out.add("u") }
            content.getSpans<ForegroundColorSpan>(pos, pos + 1).forEach { out.add("color:${it.foregroundColor}") }
            content.getSpans<RelativeSizeSpan>(pos, pos + 1).forEach { out.add("size:${it.sizeChange}") }
            return out
        }
        return spanSetAt(posA) == spanSetAt(posB)
    }

    private fun wrapSegmentWithTags(content: Editable, pos: Int, segment: String): String {
        var text = escapeHtml(segment)
        content.getSpans<RelativeSizeSpan>(pos, pos + 1).firstOrNull()?.let {
            text = "<span style=\"font-size:${(it.sizeChange * 16).toInt()}px\">$text</span>"
        }
        content.getSpans<ForegroundColorSpan>(pos, pos + 1).firstOrNull()?.let {
            val hex = String.format("#%06X", 0xFFFFFF and it.foregroundColor)
            text = "<span style=\"color:$hex\">$text</span>"
        }
        content.getSpans<StyleSpan>(pos, pos + 1).forEach {
            text = when (it.style) {
                Typeface.BOLD -> "<b>$text</b>"
                Typeface.ITALIC -> "<i>$text</i>"
                Typeface.BOLD_ITALIC -> "<b><i>$text</i></b>"
                else -> text
            }
        }
        if (content.getSpans<UnderlineSpan>(pos, pos + 1).isNotEmpty()) text = "<u>$text</u>"
        return text
    }

    private fun escapeHtml(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun confirmDelete() {
        val file = existingFile ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.notes_delete_confirm_title))
            .setMessage(getString(R.string.notes_delete_confirm_message))
            .setPositiveButton(getString(R.string.btn_delete)) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { file.delete() }
                    finish()
                    ActivityTransitions.backward(this@NoteEditorActivity)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun confirmBackIfNeeded() {
        if (!hasUnsavedChanges) {
            finish()
            ActivityTransitions.backward(this)
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.notes_saved))
            .setMessage(getString(R.string.notes_unsaved_message))
            .setPositiveButton(getString(R.string.save)) { _, _ -> saveNote(); finish(); ActivityTransitions.backward(this) }
            .setNegativeButton(getString(R.string.notes_discard)) { _, _ -> finish(); ActivityTransitions.backward(this) }
            .show()
    }

    /** Tự động lưu nền khi rời màn hình (không hiện toast để khỏi làm phiền). */
    override fun onPause() {
        super.onPause()
        val hasAnyContent = pages.any { it.etPageContent.text?.isNotBlank() == true }
        if (hasUnsavedChanges && (existingFile != null || hasAnyContent || binding.etTitle.text?.isNotBlank() == true)) {
            val title = binding.etTitle.text.toString().trim().ifBlank { getString(R.string.notes_untitled) }
            val html = buildHtmlDocument(title)
            val target = existingFile ?: File(NoteFileStore.notesDir, NoteFileStore.suggestFileName(title)).also { existingFile = it }
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        NoteFileStore.notesDir.mkdirs()
                        target.writeText(html)
                    }
                    hasUnsavedChanges = false
                } catch (_: Exception) { /* bỏ qua, lần sau lưu lại */ }
            }
        }
    }

    companion object {
        const val EXTRA_FILE_PATH = "extra_note_file_path"
        // Marker phân trang trong file HTML lưu — chỉ là HTML comment nên trình duyệt/
        // HtmlViewerActivity bỏ qua khi hiển thị (các trang hiện liền mạch, đúng kiểu cuộn dọc).
        // loadExistingNote() split() theo đúng chuỗi này để dựng lại từng trang khi mở file cũ.
        private const val PAGE_BREAK_MARKER = "<!--LEARNSY_PAGE_BREAK-->"
        // Khoảng đệm cuộn thêm phía dưới dòng con trỏ khi gõ (xem scrollToCursor()) — 240dp quy
        // đổi ra px lúc dùng. Từng thử 120dp nhưng dòng chữ vẫn nằm hơi thấp, sát mép trên thanh
        // công cụ định dạng — tăng lên 240dp (gấp đôi) để dòng đang gõ trồi lên cao hơn hẳn,
        // nằm thoải mái giữa khoảng trống phía trên bàn phím thay vì chỉ vừa đủ lọt qua mép.
        private const val CURSOR_SCROLL_PADDING_DP = 240
    }
}

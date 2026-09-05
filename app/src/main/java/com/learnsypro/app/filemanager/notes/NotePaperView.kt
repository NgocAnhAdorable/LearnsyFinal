package com.learnsypro.app.filemanager.notes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Kiểu nền giấy cho ghi chú — tương tự Samsung Notes/Apple Notes: chọn 1 lần, áp dụng cho TOÀN
 * BỘ ghi chú (mọi trang), không chọn riêng từng trang. Lưu trong <meta name="paper" content="...">
 * của file HTML để mở lại đúng như lúc lưu (xem buildHtmlDocument()/loadExistingNote() trong
 * NoteEditorActivity).
 */
enum class NotePaperStyle(val storageValue: String) {
    BLANK("blank"),
    RULED("ruled"),
    GRID("grid");

    companion object {
        fun fromStorageValue(value: String?): NotePaperStyle =
            entries.firstOrNull { it.storageValue == value } ?: BLANK
    }
}

/**
 * Vẽ nền giấy (dòng kẻ ngang hoặc lưới caro) phía SAU EditText — đặt trong FrameLayout, View này
 * làm layer dưới, EditText có background trong suốt (@null) làm layer trên, để chữ gõ đè lên
 * đúng ngay trên các đường kẻ. Không vẽ gì khi style = BLANK (giữ nền trắng/màu nền mặc định của
 * Activity, không cần View riêng nhưng vẫn giữ 1 instance rỗng cho đơn giản hoá code gọi).
 *
 * Khoảng cách dòng kẻ LINE_SPACING_DP cố ý khớp với android:lineSpacingExtra + textSize của
 * et_content (16sp, lineSpacingExtra 6dp -> dòng thực tế cao khoảng 28-30dp ở mật độ thường) để
 * chữ viết nằm ngay trên từng đường kẻ, giống vở kẻ ngang thật, không lệch dòng.
 */
class NotePaperView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var style: NotePaperStyle = NotePaperStyle.BLANK
        set(value) {
            field = value
            invalidate()
        }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33000000 // đen mờ 20% — đủ thấy làm hướng dẫn, không lấn át chữ viết
        strokeWidth = 1f
    }

    private val lineSpacingPx: Float
        get() = LINE_SPACING_DP * resources.displayMetrics.density

    private val topPaddingPx: Float
        get() = CONTENT_PADDING_DP * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when (style) {
            NotePaperStyle.BLANK -> return
            NotePaperStyle.RULED -> drawRuledLines(canvas)
            NotePaperStyle.GRID -> drawGrid(canvas)
        }
    }

    private fun drawRuledLines(canvas: Canvas) {
        var y = topPaddingPx + lineSpacingPx
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, linePaint)
            y += lineSpacingPx
        }
    }

    private fun drawGrid(canvas: Canvas) {
        var y = topPaddingPx + lineSpacingPx
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, linePaint)
            y += lineSpacingPx
        }
        var x = 0f
        while (x < width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), linePaint)
            x += lineSpacingPx
        }
    }

    companion object {
        private const val LINE_SPACING_DP = 30f
        // Khớp android:layout_margin của et_content (16dp) trong FrameLayout trang — đường kẻ
        // đầu tiên bắt đầu từ đúng vị trí dòng chữ đầu tiên, không bị lệch lên trên margin.
        private const val CONTENT_PADDING_DP = 16f
    }
}

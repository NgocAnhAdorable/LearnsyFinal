package com.learnsypro.app.filemanager.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Tự động thêm padding/margin ĐÚNG BẰNG chiều cao thanh điều hướng hệ thống thật (navigation
 * bar hoặc gesture bar) cho các view cố định ở đáy màn hình (selection bar, nút toggle server,
 * bottom navigation...) — cách làm này thay thế hoàn toàn cho việc set padding cố định trong
 * XML hoặc dựa vào statusBarColor/navigationBarColor tĩnh trong theme.
 *
 * VÌ SAO CẦN THIẾT RIÊNG CHO ONEUI/HYPEROS: cả 2 hệ thống đều mặc định dùng "Cử chỉ vuốt"
 * (gesture navigation) với thanh cử chỉ MỎNG ở đáy màn hình (khác hẳn thanh 3 nút truyền
 * thống) — chiều cao thanh này KHÁC NHAU giữa từng dòng máy, từng phiên bản OneUI/HyperOS, và
 * khác cả khi người dùng tự đổi giữa "Cử chỉ" và "Nút điều hướng" trong Settings. Không có
 * cách nào biết trước con số chính xác lúc build — BẮT BUỘC phải đọc WindowInsets tại RUNTIME
 * bằng API này, mọi con số cố định (padding 16dp, 24dp...) đều có nguy cơ hoặc thừa (khoảng
 * trắng vô lý) hoặc thiếu (bị gesture bar đè/che mất 1 phần nút bấm cuối danh sách) tuỳ máy.
 */
object WindowInsetsUtils {

    /**
     * Thêm padding-bottom ĐỘNG bằng đúng chiều cao navigation/gesture bar vào [view], cộng dồn
     * với [extraBottomPx] (padding gốc mong muốn của chính view đó, đọc từ XML/dp lúc gọi hàm).
     * Dùng cho các thanh nằm CỐ ĐỊNH sát đáy màn hình (selection bar, nút bật/tắt server...).
     */
    fun applyBottomInsetPadding(view: View, extraBottomPx: Int = view.paddingBottom) {
        val originalPaddingLeft = view.paddingLeft
        val originalPaddingTop = view.paddingTop
        val originalPaddingRight = view.paddingRight
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(originalPaddingLeft, originalPaddingTop, originalPaddingRight, extraBottomPx + navBar.bottom)
            insets
        }
        view.requestApplyInsets()
    }

    /**
     * Thêm margin-bottom ĐỘNG bằng đúng chiều cao navigation/gesture bar — dùng cho các view
     * kiểu FloatingActionButton (FAB) nơi margin phù hợp hơn padding (không muốn vùng chạm mở
     * rộng ra ngoài hình dạng nút tròn).
     */
    fun applyBottomInsetMargin(view: View, extraMarginPx: Int) {
        val lp = view.layoutParams as? android.view.ViewGroup.MarginLayoutParams ?: return
        val originalBottomMargin = lp.bottomMargin
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val params = v.layoutParams as android.view.ViewGroup.MarginLayoutParams
            params.bottomMargin = originalBottomMargin + navBar.bottom
            v.layoutParams = params
            insets
        }
        view.requestApplyInsets()
    }

    /** Thêm padding-top động bằng đúng chiều cao status bar — dùng cho toolbar/header cố định trên cùng. */
    fun applyTopInsetPadding(view: View, extraTopPx: Int = view.paddingTop) {
        val originalPaddingLeft = view.paddingLeft
        val originalPaddingRight = view.paddingRight
        val originalPaddingBottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(originalPaddingLeft, extraTopPx + statusBar.top, originalPaddingRight, originalPaddingBottom)
            insets
        }
        view.requestApplyInsets()
    }

    /**
     * Đặt chiều cao của [spacerView] (một View trống, KHÔNG phải Toolbar) đúng bằng chiều cao
     * status bar tại runtime. Dùng khi muốn chừa chỗ cho status bar mà KHÔNG làm Toolbar bị
     * phình cao/to ra: Toolbar giữ nguyên layout_height="?attr/actionBarSize" cố định như bình
     * thường (đặt NGAY SAU spacer này trong layout), spacer lo phần "đệm" phía trên, Toolbar chỉ
     * đơn giản bị đẩy xuống dưới status bar, không tự thay đổi kích thước hay chữ bị bóp.
     */
    fun applyTopInsetHeight(spacerView: View) {
        ViewCompat.setOnApplyWindowInsetsListener(spacerView) { v, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val lp = v.layoutParams
            lp.height = statusBar.top
            v.layoutParams = lp
            insets
        }
        spacerView.requestApplyInsets()
    }
}

package com.learnsypro.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * ── OneUiTokens ──
 * Bộ token thiết kế theo tinh thần One UI 9 (Samsung), áp dụng dần qua
 * từng màn hình thay vì đổi toàn bộ app cùng lúc. Các màn đã redesign:
 * DashboardScreen, TabHome. Màn khác vẫn dùng style cũ cho tới khi được
 * cập nhật theo cùng bộ token này.
 *
 * 7 nguyên tắc áp dụng:
 * 1. Mềm mại/bo tròn nhiều — CornerXL/XXL thay cho góc vuông hoặc bo nhẹ
 * 2. Trong suốt + lớp lang — card nền bán trong suốt, blur nhẹ phía sau
 * 3. Dynamic-color-ish — accent đổi sắc theo dark/light thay vì cố định
 * 4. Glassmorphism nhẹ — chỉ ở header/sheet nổi, không lạm dụng toàn màn
 * 5. Một tay — nội dung chính giữa/thấp màn hình, tiêu đề tách lớp riêng
 * 6. Animation đàn hồi — dùng spring() thay vì tween() tuyến tính cứng
 * 7. Tối giản — giảm số lớp viền/shadow chồng chéo, icon đơn sắc gọn
 */
object OneUiRadius {
    val card = 24.dp        // card nội dung chính (trước đây phổ biến 16-18dp)
    val cardLarge = 28.dp   // card lớn/hero
    val sheet = 32.dp       // bottom sheet, modal — bo cực mạnh kiểu One UI
    val pill = 999.dp       // nút/badge dạng viên thuốc
    val chip = 16.dp        // chip nhỏ, tag
}

object OneUiSpring {
    /** Đàn hồi nhẹ — dùng cho hầu hết chuyển động chạm (bấm, mở card). */
    val gentle = androidx.compose.animation.core.spring<Float>(
        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
    )

    /** Đàn hồi rõ hơn — dùng cho modal/sheet bật lên, nút bấm nhấn mạnh. */
    val bouncy = androidx.compose.animation.core.spring<Float>(
        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
        stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
    )
}

/**
 * Modifier lớp kính One UI: chỉ dùng nền bán trong suốt + bo góc.
 *
 * LƯU Ý: KHÔNG dùng Modifier.blur() ở đây để mô phỏng "kính mờ" — trong
 * Compose, blur() làm mờ toàn bộ nội dung của chính composable được áp
 * modifier (kể cả text/icon con bên trong), khác hẳn CSS backdrop-filter
 * của web (chỉ làm mờ những gì NẰM PHÍA SAU, giữ nguyên nội dung phía
 * trước). Muốn blur đúng nghĩa "kính mờ" trong Compose cần renderEffect
 * kết hợp graphicsLayer ở lớp NỀN riêng biệt (con Box ở dưới cùng, tách
 * khỏi Box chứa nội dung) — phức tạp và dễ sai, nên tạm dùng độ trong
 * suốt của màu nền để tạo cảm giác lớp lang, an toàn hơn.
 */
fun Modifier.oneUiGlass(
    dark: Boolean,
    radius: androidx.compose.ui.unit.Dp = OneUiRadius.card
): Modifier {
    val glassBg = if (dark) Color(0x1FFFFFFF) else Color(0xCCFFFFFF)
    return this
        .clip(RoundedCornerShape(radius))
        .background(glassBg)
}

/** Nền gradient mềm dùng cho card nổi bật (CTA, hero) — thay cho màu phẳng đơn sắc. */
fun oneUiAccentGradient(dark: Boolean): Brush = Brush.linearGradient(
    if (dark) listOf(Color(0xFFEC4899), Color(0xFF8B5CF6))
    else listOf(Color(0xFFF472B6), Color(0xFFA855F7))
)

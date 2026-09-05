package com.learnsypro.app.admin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────
// Modifier dùng chung cho "viền tím mỏng + glow tím nhẹ" — trước đây mỗi
// card tự vẽ glow bằng khối drawBehind lặp lại (xem LessonCard), và phần
// lớn input/dropdown/card khác trong app chỉ dùng border xám/đen mặc định
// của Material3, không đồng bộ phong cách "bánh bèo" (viền tím pastel +
// glow mờ) đã áp cho LessonCard. Tách ra 1 modifier dùng chung để:
//   • áp cho MỌI card/input trong app bằng đúng 1 lời gọi, đảm bảo nhất quán
//   • sửa 1 chỗ là đổi cường độ/màu glow toàn app, không phải sửa N nơi
//
// Cách dùng: modifier = Modifier.cardGlow(colors, dark).background(...)...
//   — gọi cardGlow() TRƯỚC .clip()/.background() để glow vẽ ra ngoài viền,
//   không bị clip cắt mất.

/**
 * Vẽ glow mờ dần quanh 1 khối bo góc — nhẹ hơn hẳn nếu tự khai drawBehind
 * lặp lại mỗi nơi. Gọi trước clip/background trong chain.
 *
 * FIX: darkGlow trước đây là màu ĐEN mờ (0.28f alpha) — trên nền đã tối sẵn
 * của dark mode, glow đen gần như vô hình (khác hẳn light mode, nơi glow
 * tím nổi rõ trên nền sáng). Đổi sang tím sáng — cùng hue với lightGlow
 * nhưng alpha cao hơn — để card ở dark mode cũng "sáng nhẹ viền tím" như
 * bản light, thay vì trông tối om/thiếu sức sống.
 *
 * @param cornerRadius phải khớp với RoundedCornerShape dùng ở .clip() ngay sau,
 *   nếu không glow và viền sẽ lệch bo góc.
 */
fun Modifier.glowShadow(
    dark: Boolean,
    cornerRadius: Dp = 18.dp,
    lightGlow: Color = Color(0xFFA855F7).copy(alpha = 0.14f),
    darkGlow: Color = Color(0xFFC084FC).copy(alpha = 0.30f),
    layers: Int = 4,
    layerSpread: Dp = 3.dp
): Modifier {
    val glowColor = if (dark) darkGlow else lightGlow
    return this.drawBehind {
        val radiusPx = cornerRadius.toPx()
        for (i in layers downTo 1) {
            val spread = (i * layerSpread.value).dp.toPx()
            val alpha = glowColor.alpha * (1f - i.toFloat() / layers) * 0.9f
            drawRoundRect(
                color = glowColor.copy(alpha = alpha),
                topLeft = Offset(-spread, -spread),
                size = Size(size.width + spread * 2, size.height + spread * 2),
                cornerRadius = CornerRadius(radiusPx + spread, radiusPx + spread)
            )
        }
    }
}

/**
 * Viền tím pastel mỏng dùng chung cho card/input — thay border xám/đen mặc
 * định. `active` (vd. đang focus/đang chọn) đổi sang tím đậm hơn.
 */
fun Modifier.lavenderBorder(
    colors: com.learnsypro.app.admin.ui.theme.LearnsyColors,
    shape: Shape,
    active: Boolean = false,
    width: Dp = 1.5.dp
): Modifier = this.border(width, if (active) colors.lav else colors.border2, shape)

/**
 * Kết hợp glow + clip + border tím trong 1 lời gọi — cách dùng khuyến nghị
 * cho card/panel thường. Tự thêm .clip() và .background(bg) luôn, vì glow
 * PHẢI vẽ trước clip (ngoài viền) nên gộp sẵn để tránh gọi sai thứ tự.
 */
fun Modifier.softCard(
    colors: com.learnsypro.app.admin.ui.theme.LearnsyColors,
    dark: Boolean,
    background: Color,
    cornerRadius: Dp = 18.dp,
    active: Boolean = false
): Modifier = this
    .glowShadow(dark = dark, cornerRadius = cornerRadius)
    .clip(RoundedCornerShape(cornerRadius))
    .background(background)
    .lavenderBorder(colors, RoundedCornerShape(cornerRadius), active = active)

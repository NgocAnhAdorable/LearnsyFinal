package com.learnsypro.app.ui.branding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsypro.app.ui.theme.NunitoFontFamily

/**
 * Logo Learnsy: khối chữ "L" bo góc, trắng, trên nền gradient hồng-tím
 * (#F9A8D4 → #C084FC) khớp với bảng màu mặc định của app (accent/accent2
 * trong DashboardColors), có glow mờ + đổ bóng nhẹ phía sau — thay cho logo
 * chữ "Learnsy" tím-hồng + badge nguyên tử cũ, và thay cho bản "L" nền xanh
 * (#DFF7FF → #8DD9FF) trước đó không khớp theme.
 *
 * Chữ ký "Learnsy" vẫn được giữ lại nhưng thu nhỏ, đặt
 * ở góc dưới-phải, đóng vai trò chữ ký thương hiệu thay vì là điểm nhấn
 * chính — đồng bộ với launcher icon (ic_launcher_foreground/background).
 *
 * Dùng cho: các nơi hiển thị logo trong app (password gate, splash/loading,
 * dashboard topbar...). API giữ nguyên tên tham số như bản cũ để không phải
 * sửa lại các màn hình đang gọi LearnsyLogo/AtomBadge.
 */
@Composable
fun LearnsyLogo(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    fontSize: TextUnit = 22.sp,
    animated: Boolean = true,
    showBackground: Boolean = true,
    showSignature: Boolean = true
) {
    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            drawLearnsyMark(
                showBackground = showBackground,
                showSignature = showSignature,
                textMeasurer = textMeasurer
            )
        }
    }
}

/**
 * Mini badge chỉ chứa khối "L" (không chữ ký, nền tuỳ chọn) — dùng ở những
 * nơi cần icon nhỏ gọn: topbar dashboard, vòng tròn loading...
 *
 * Giữ nguyên tên/tham số [badgeColor] và [backgroundColor] như bản cũ:
 * - [badgeColor] tô màu khối "L".
 * - [backgroundColor] tô nền hình tròn phía sau (đặt Color.Transparent để ẩn).
 * [animated] không còn ảnh hưởng hiệu ứng xoay (logo mới tĩnh, tối giản hơn),
 * tham số được giữ lại để tương thích ngược với các lời gọi hiện có.
 */
@Composable
fun AtomBadge(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    badgeColor: Color = Color(0xFFA855F7),
    backgroundColor: Color = Color.White,
    animated: Boolean = true
) {
    Canvas(modifier = modifier.size(size)) {
        if (backgroundColor != Color.Transparent) {
            drawCircle(
                color = backgroundColor,
                radius = this.size.width / 2f,
                center = Offset(this.size.width / 2f, this.size.height / 2f)
            )
        }
        drawLMark(color = badgeColor)
    }
}

/**
 * Vẽ khối chữ "L" bo góc theo đúng toạ độ dùng ở launcher icon
 * (viewport 108x108: thân dọc x 38-48 y 30-70, chân ngang x 38-74 y 60-70),
 * để logo trong app và icon ngoài launcher đồng nhất hình dạng.
 */
private fun DrawScope.drawLMark(color: Color) {
    val scale = this.size.minDimension / 108f
    val path = buildLPath(scale)
    drawPath(path = path, color = color)
}

private fun buildLPath(scale: Float): Path {
    val radius = CornerRadius(10f * scale)
    return Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = 38f * scale, top = 30f * scale,
                right = 48f * scale, bottom = 70f * scale,
                cornerRadius = radius
            )
        )
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = 38f * scale, top = 60f * scale,
                right = 74f * scale, bottom = 70f * scale,
                cornerRadius = radius
            )
        )
    }
}

/**
 * Vẽ đầy đủ logo Learnsy: nền gradient (tuỳ chọn) + glow mờ + đổ bóng nhẹ +
 * khối "L" trắng + chữ ký "Learnsy" mini góc dưới-phải (tuỳ chọn).
 * Dùng chung logic toạ độ với bộ tạo launcher icon để hai nơi khớp nhau.
 */
private fun DrawScope.drawLearnsyMark(
    showBackground: Boolean,
    showSignature: Boolean,
    textMeasurer: TextMeasurer
) {
    val scale = this.size.minDimension / 108f

    if (showBackground) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFF9A8D4), Color(0xFFC084FC))
            )
        )
    }

    val lPath = buildLPath(scale)

    // Glow mờ phía sau khối L
    drawPath(path = lPath, color = Color.White.copy(alpha = 0.30f))

    // Đổ bóng nhẹ, lệch xuống-phải
    val shadowOffset = 0.8f * scale
    translate(left = shadowOffset, top = shadowOffset) {
        drawPath(path = lPath, color = Color(0xFF6B1E4F).copy(alpha = 0.16f))
    }

    // Khối "L" chính
    drawPath(path = lPath, color = Color.White)

    if (showSignature) {
        val textHeightPx = 40f * scale * 0.22f
        val fontSizeSp = (textHeightPx * 1.05f / density).sp

        val layoutResult = textMeasurer.measure(
            text = "Learnsy Pro",
            style = TextStyle(
                fontFamily = NunitoFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = fontSizeSp,
                color = Color(0xFFF8FCFF).copy(alpha = 0.92f)
            )
        )

        val margin = this.size.minDimension * 0.06f
        val x = this.size.width - margin - layoutResult.size.width
        val y = this.size.height - margin - layoutResult.size.height

        drawText(textLayoutResult = layoutResult, topLeft = Offset(x, y))
    }
}

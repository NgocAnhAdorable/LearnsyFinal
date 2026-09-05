package com.learnsypro.app.background

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * ── BackgroundLayer (dùng chung Student / Admin / File Manager) ──
 * Tương đương applyBackground() + #learnsy-bg-overlay trong background-settings.js.
 *
 * Trước đây sống trong ui.dashboard (chỉ Student dùng); chuyển sang package
 * `background` để Admin (Compose) cũng render được cùng 1 Composable.
 *
 * 2 chỗ sửa so với bản cũ (ảnh nền tự upload từng bị lem viền + chữ bị che):
 * 1) Ảnh được scale rộng hơn khung (`overscanFactor`) trước khi Modifier.blur()
 *    — blur lấy mẫu ra ngoài biên ảnh gây viền tối/lem ở mép; phóng to sẵn
 *    một khoảng đệm quanh ảnh (offset âm 2 chiều) để vùng lem đó nằm ngoài
 *    khung nhìn, không còn thấy được.
 * 2) Lớp dim đổi từ 1 màu phẳng phủ đều toàn màn sang gradient dọc: nhạt hơn
 *    ở khoảng giữa (nơi thường đặt nội dung/card), đậm dần về 2 đầu trên
 *    dưới (nơi thường có status bar/bottom nav) — vẫn lấy đúng dimAlphaLight/
 *    dimAlphaDark() làm mốc alpha trung bình nên độ "mờ tổng thể" theo % người
 *    dùng chọn không đổi, chỉ phân bố lại để không che đều lên chữ giữa màn.
 */
@Composable
fun BackgroundLayer(
    settings: BgSettings,
    dark: Boolean,
    liteMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    // blurMode 'off' → tắt hẳn nền, chỉ còn màu nền phẳng (giống bản gốc)
    if (settings.blurMode == "off") {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(if (dark) Color(0xFF12000E) else Color(0xFFFFF5F9))
        )
        return
    }

    val percent = settings.blurPercent
    val blurDp by animateDpAsState(
        targetValue = if (liteMode) 0.dp else blurDpForPercent(percent).dp,
        animationSpec = tween(350),
        label = "bgBlur"
    )

    // Alpha trung bình theo % (giữ nguyên công thức gốc) — dùng làm mốc giữa
    // cho gradient dim bên dưới, thay vì áp thẳng làm màu phẳng.
    val avgAlpha = if (dark) dimAlphaDark(percent) else dimAlphaLight(percent)
    val dimBase = if (dark) Color(10 / 255f, 0f, 12 / 255f) else Color.White

    val topAlpha by animateColorAsState(
        targetValue = dimBase.copy(alpha = (avgAlpha * 1.35f).coerceAtMost(1f)),
        animationSpec = tween(350),
        label = "bgDimTop"
    )
    val midAlpha by animateColorAsState(
        targetValue = dimBase.copy(alpha = (avgAlpha * 0.55f).coerceAtMost(1f)),
        animationSpec = tween(350),
        label = "bgDimMid"
    )
    val bottomAlpha by animateColorAsState(
        targetValue = dimBase.copy(alpha = (avgAlpha * 1.45f).coerceAtMost(1f)),
        animationSpec = tween(350),
        label = "bgDimBottom"
    )

    // Nếu preset sáng nhưng dark mode đang bật → swap sang default_dark (giống bản gốc)
    val resolvedId = if (dark && settings.presetId in LIGHT_PRESET_IDS) "default_dark" else settings.presetId
    val preset = BG_PRESETS.find { it.id == resolvedId } ?: BG_PRESETS.first()

    // Đệm tràn viền: càng blur nhiều càng cần đệm rộng để vùng lem mép nằm
    // ngoài khung nhìn. 1dp blur ước lượng lem ra ~1.6dp mỗi phía, cộng thêm
    // biên an toàn cố định.
    val overscanDp = (blurDp.value * 1.6f + 24f).dp

    Box(modifier = modifier.fillMaxSize()) {
        if (settings.presetId == "custom_image" && !settings.imageUrl.isNullOrBlank()) {
            // FIX: Modifier.offset() trước đây chỉ DỊCH CHUYỂN vị trí vẽ của
            // ảnh đã đo kích thước xong bằng fillMaxSize() — không hề phóng to
            // ảnh, để lộ dải trắng (nền Compose mặc định) ở viền phải/đáy
            // đúng bằng overscanDp. Modifier.padding(-overscanDp) cũng KHÔNG
            // dùng được thay thế — Compose bắt buộc giá trị padding không âm,
            // ném IllegalArgumentException ngay khi chạy.
            //
            // Cách đúng: Modifier.layout{} tự đo (measure) ảnh với constraints
            // LỚN HƠN kích thước khung thật overscanPx mỗi phía, rồi đặt
            // (place) nó lùi vào (-overscanPx, -overscanPx) so với gốc — ảnh
            // THỰC SỰ to hơn khung hiển thị và được căn giữa, nên phần lem
            // biên khi blur() lấy mẫu ra ngoài rơi ra ngoài vùng nhìn thấy.
            AsyncImage(
                model = settings.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .layout { measurable, constraints ->
                        val overscanPx = overscanDp.roundToPx()
                        val placeable = measurable.measure(
                            constraints.copy(
                                minWidth = constraints.maxWidth + overscanPx * 2,
                                maxWidth = constraints.maxWidth + overscanPx * 2,
                                minHeight = constraints.maxHeight + overscanPx * 2,
                                maxHeight = constraints.maxHeight + overscanPx * 2
                            )
                        )
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.place(-overscanPx, -overscanPx)
                        }
                    }
                    .let { if (blurDp.value > 0f) it.blur(blurDp) else it }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .let { if (blurDp.value > 0f) it.blur(blurDp) else it }
                    .background(preset.gradient ?: Brush())
            )
        }
        // Lớp dim gradient dọc — nhạt ở giữa (giữ nội dung dễ đọc mà vẫn thấy
        // nền), đậm dần lên trên/xuống dưới thay vì phủ đều 1 màu phẳng.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to topAlpha,
                            0.22f to midAlpha,
                            0.62f to midAlpha,
                            1.0f to bottomAlpha
                        ),
                        tileMode = TileMode.Clamp
                    )
                )
        )
    }
}

/** Fallback trong trường hợp preset không có gradient (không nên xảy ra). */
private fun Brush(): Brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFFFF5F9))

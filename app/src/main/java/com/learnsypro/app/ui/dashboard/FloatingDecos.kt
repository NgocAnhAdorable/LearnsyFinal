package com.learnsypro.app.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.learnsypro.app.ui.theme.rememberFloatOffset

private data class DecoSpec(
    val icon: String,
    val color: Color,
    val size: androidx.compose.ui.unit.Dp,
    val topFraction: Float,
    val fromLeft: Boolean,
    val sideFraction: Float,
    val delayMillis: Int,
    val durationMillis: Int
)

/**
 * ── FloatingDecos ──
 * Tương đương function FloatingDecos({dark}) trong dashboard.jsx.
 * 6 hình SVG nhỏ (hoa, sao, la bàn, tim, sao 4 cánh, sao) bay lượn nhẹ nhàng
 * ở nền, dùng animation float (đã có sẵn trong Animations.kt của Theme).
 *
 * Lưu ý: Compose không có z-index âm/pointer-events:none built-in đơn giản
 * như CSS — để mô phỏng "không chặn tương tác", các decos này nên được đặt
 * ở lớp Box NGOÀI CÙNG dưới đáy (đặt trước các nội dung khác trong Z-order),
 * và vì chúng không có Modifier.clickable nên mặc định đã không bắt sự kiện
 * chạm — đúng hiệu ứng pointer-events:none của bản gốc.
 */
@Composable
fun FloatingDecos(dark: Boolean, modifier: Modifier = Modifier) {
    val decos = remember {
        listOf(
            DecoSpec("heart", Color(0xFFF472B6), 18.dp, 0.08f, true, 0.05f, 0, 5000),
            DecoSpec("sparkle", Color(0xFFF9A8D4), 14.dp, 0.15f, false, 0.08f, 1200, 4500),
            DecoSpec("target", Color(0xFFF472B6), 16.dp, 0.55f, true, 0.03f, 700, 6000),
            DecoSpec("heart", Color(0xFFC084FC), 13.dp, 0.35f, false, 0.04f, 2000, 5500),
            DecoSpec("star", Color(0xFFFBBF24), 12.dp, 0.72f, true, 0.06f, 1500, 4000),
            DecoSpec("sparkle", Color(0xFF34D399), 15.dp, 0.82f, false, 0.05f, 300, 5800),
        )
    }

    Box(modifier = modifier) {
        decos.forEach { d ->
            val floatState = rememberFloatOffset(
                delayMillis = d.delayMillis,
                reverseRotation = d.durationMillis > 5000
            )
            Box(
                modifier = Modifier
                    .align(if (d.fromLeft) Alignment.TopStart else Alignment.TopEnd)
                    .offset(
                        x = if (d.fromLeft) (d.sideFraction * 100).dp else -(d.sideFraction * 100).dp,
                        y = (d.topFraction * 600).dp // xấp xỉ theo % chiều cao màn hình thường gặp
                    )
                    .graphicsLayer {
                        // Đọc .value ở đây (draw phase) chứ không destructure ở
                        // composition phase — tránh recompose cả cây mỗi frame.
                        translationY = floatState.translateY.value
                        rotationZ = floatState.rotation.value
                        alpha = if (dark) 0.12f else 0.22f
                    }
            ) {
                DashboardIcon(name = d.icon, size = d.size, color = d.color)
            }
        }
    }
}

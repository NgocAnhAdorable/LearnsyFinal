package com.learnsypro.app.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.learnsypro.app.R
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * ── RandomMascotLayer ──
 * Bạn nữ tai mèo (27 pose, xem /res/drawable-nodpi/mascot_girl_XX.png) ĐI BỘ
 * ngang qua màn hình — trượt từ mép trái sang mép phải (hoặc ngược lại), đi
 * NGANG QUA các card ở một độ cao ngẫu nhiên, thay vì chỉ đứng yên ở góc như
 * bản trước. Khi ra khỏi mép màn hình bên kia thì biến mất, nghỉ một khoảng
 * ngẫu nhiên rồi xuất hiện lại ở mép còn lại, độ cao khác, pose khác.
 *
 * Tại một thời điểm chỉ có TỐI ĐA 1 bạn trên màn hình (giữ đúng yêu cầu
 * "cân bằng" — không dày đặc). Được gọi ở tab Trang chủ VÀ tab Tài liệu
 * (xem DashboardScreen.kt). Không chặn tương tác chạm (không có
 * Modifier.clickable / pointerInput nào trên layer này). Tôn trọng cờ
 * `enabled` từ DashboardViewModel.mascotEnabled (toggle "Bạn đồng hành"
 * trong Cài đặt) — tắt là ẩn hẳn, không animate ngầm.
 */
private val MASCOT_DRAWABLES = listOf(
    R.drawable.mascot_girl_01, R.drawable.mascot_girl_02, R.drawable.mascot_girl_03,
    R.drawable.mascot_girl_04, R.drawable.mascot_girl_05, R.drawable.mascot_girl_06,
    R.drawable.mascot_girl_07, R.drawable.mascot_girl_08, R.drawable.mascot_girl_09,
    R.drawable.mascot_girl_10, R.drawable.mascot_girl_11, R.drawable.mascot_girl_12,
    R.drawable.mascot_girl_13, R.drawable.mascot_girl_14,
    // mascot_girl_15 (pose cầm điện thoại) đã BỎ khỏi bộ theo yêu cầu — tránh
    // dính logo/hình dáng giống iPhone.
    R.drawable.mascot_girl_16, R.drawable.mascot_girl_17, R.drawable.mascot_girl_18,
    R.drawable.mascot_girl_19, R.drawable.mascot_girl_20, R.drawable.mascot_girl_21,
    R.drawable.mascot_girl_22, R.drawable.mascot_girl_23, R.drawable.mascot_girl_24,
    R.drawable.mascot_girl_25, R.drawable.mascot_girl_26, R.drawable.mascot_girl_27,
    R.drawable.mascot_girl_28,
)

private data class WalkPlan(
    val drawableRes: Int,
    val leftToRight: Boolean,
    val sizeDp: Int,
    val topDp: Int,
    val durationMs: Int
)

// "Cân bằng": đi bộ qua màn hình mất 5-8s (đủ nhìn rõ, không quá chậm gây
// lì), nghỉ ngẫu nhiên 20-40s giữa các lượt — cùng nhịp với bản đứng-góc
// trước đó, chỉ đổi cách di chuyển.
private const val WALK_MIN_MS = 5_000
private const val WALK_MAX_MS = 8_000
private const val HIDE_MIN_MS = 20_000L
private const val HIDE_MAX_MS = 40_000L

private fun randomPlan(topRangeDp: Int): WalkPlan = WalkPlan(
    drawableRes = MASCOT_DRAWABLES.random(),
    leftToRight = Random.nextBoolean(),
    sizeDp = Random.nextInt(72, 100),
    // Độ cao ngẫu nhiên trong vùng an toàn (đã trừ top bar/bottom nav ở nơi gọi),
    // để mỗi lượt đi qua ở một "làn" khác nhau — có lúc lướt ngang card trên,
    // có lúc lướt ngang card dưới, giống hệt ý "di chuyển qua lại trên các card".
    topDp = if (topRangeDp > 0) Random.nextInt(0, topRangeDp) else 0,
    durationMs = Random.nextInt(WALK_MIN_MS, WALK_MAX_MS)
)

@Composable
fun RandomMascotLayer(enabled: Boolean, modifier: Modifier = Modifier) {
    if (!enabled) return

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val screenWidthPx = with(density) { maxWidth.toPx() }
        // Vùng độ cao cho phép "đi lại" — chừa khoảng trống trên/dưới để
        // không dẫm lên top bar hoặc bottom nav (giống inset cũ 90-170dp,
        // giờ chuyển thành 1 dải để mascot dạo qua nhiều tầng card khác nhau).
        val topRangeDp = with(density) { (maxHeight - 260.dp).value.toInt().coerceAtLeast(0) }

        var visible by remember { mutableStateOf(false) }
        var plan by remember { mutableStateOf(randomPlan(topRangeDp)) }
        val xPx = remember { Animatable(0f) }

        LaunchedEffect(screenWidthPx, topRangeDp) {
            if (screenWidthPx <= 0f) return@LaunchedEffect
            // Lần đầu vào màn hình: chờ chút rồi mới xuất hiện, tránh giật
            // ngay lúc màn hình vừa load xong.
            delay(Random.nextLong(3_000L, 8_000L))
            while (true) {
                plan = randomPlan(topRangeDp)
                val sizePx = with(density) { plan.sizeDp.dp.toPx() }
                val startX = if (plan.leftToRight) -sizePx else screenWidthPx
                val endX = if (plan.leftToRight) screenWidthPx else -sizePx
                xPx.snapTo(startX)
                visible = true
                xPx.animateTo(
                    targetValue = endX,
                    animationSpec = tween(durationMillis = plan.durationMs, easing = LinearEasing)
                )
                visible = false
                delay(Random.nextLong(HIDE_MIN_MS, HIDE_MAX_MS))
            }
        }

        if (visible) {
            val xDp = with(density) { xPx.value.toDp() }
            Image(
                painter = painterResource(id = plan.drawableRes),
                contentDescription = null,
                modifier = Modifier
                    .offset(x = xDp, y = plan.topDp.dp)
                    .size(plan.sizeDp.dp)
                    .graphicsLayer {
                        // Lật ảnh ngang khi đi từ phải sang trái, để mascot luôn
                        // "quay mặt" theo đúng hướng di chuyển thay vì đi giật lùi.
                        scaleX = if (plan.leftToRight) 1f else -1f
                    }
            )
        }
    }
}

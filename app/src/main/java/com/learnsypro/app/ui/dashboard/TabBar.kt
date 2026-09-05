package com.learnsypro.app.ui.dashboard

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsypro.app.ui.theme.NunitoFontFamily

enum class DashboardTab(val id: String, val label: String, val icon: String) {
    HOME("home", "Trang chủ", "home"),
    STATS("stats", "Thống kê", "stats"),
    HISTORY("history", "Lịch sử", "history"),
    FILES("files", "Tài liệu", "folder"),
    SETTINGS("settings", "Cài đặt", "settings")
}

/**
 * ── TabBar ──
 * Tương đương function TabBar({tab,setTab,dark,liteMode,flickerFx}) trong
 * dashboard.jsx. Thanh điều hướng dưới cùng, tab active có nền hồng nhạt +
 * scale phóng to nhẹ + hiệu ứng bounce + chấm tròn báo hiệu bên dưới.
 * flickerFx bật thì icon/label tab nhấp nháy nhẹ liên tục lệch pha nhau
 * (tương đương bb-blink, dùng chung cơ chế với HUD flicker toggle của app).
 */
@Composable
fun TabBar(
    tab: DashboardTab,
    onTabChange: (DashboardTab) -> Unit,
    dark: Boolean,
    liteMode: Boolean,
    flickerFx: Boolean,
    modifier: Modifier = Modifier
) {
    val C = dashboardColors(dark)
    val inactiveColor = if (dark) Color(0x61FFB4D2) else Color(0x73A06080)

    // One UI 9: thanh nav "nổi" tách khỏi mép màn hình (margin quanh), bo
    // tròn toàn bộ 4 góc thay vì dính sát đáy — cảm giác floating pill bar.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .navigationBarsPadding()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.sheet))
            .background(
                if (dark) Color(0xF0140609) else Color(0xF5FFFFFF)
            )
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        DashboardTab.entries.forEachIndexed { idx, t ->
            val active = tab == t
            val color = if (active) C.accent else inactiveColor

            val scale by animateFloatAsState(
                targetValue = if (active) 1.05f else 1f,
                animationSpec = spring(),
                label = "tabScale"
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (active) {
                            if (dark) Color(0x24F472B6) else Color(0x1CF472B6)
                        } else Color.Transparent
                    )
                    .clickable { onTabChange(t) }
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                val flickerState = if (liteMode || !flickerFx) null else rememberBlinkAlpha(idx * 150)

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer { alpha = flickerState?.value ?: 1f },
                    contentAlignment = Alignment.Center
                ) {
                    DashboardIcon(name = t.icon, size = 22.dp, color = color)
                }
                Text(
                    text = t.label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = color,
                    fontFamily = NunitoFontFamily,
                    modifier = Modifier.graphicsLayer { alpha = flickerState?.value ?: 1f }
                )
                if (active) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(C.accent, androidx.compose.foundation.shape.CircleShape)
                    )
                } else {
                    Spacer(modifier = Modifier.size(4.dp))
                }
            }
        }
    }
}

/**
 * Alpha nhấp nháy nhẹ liên tục, lệch pha theo delay — tương đương @keyframes
 * bb-blink. Trả về State<Float> (không unwrap bằng "by" ở đây) để nơi gọi
 * chỉ đọc .value bên trong graphicsLayer{}, tránh recompose cả TabBar (luôn
 * hiện trên màn hình) mỗi frame.
 */
@Composable
internal fun rememberBlinkAlpha(delayMillis: Int): androidx.compose.runtime.State<Float> {
    if (com.learnsypro.app.ui.theme.LocalLiteMode.current) {
        return remember { mutableStateOf(1f) }
    }
    val transition = rememberInfiniteTransition(label = "blink")
    return transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1500
                1f at 0
                0.55f at 750
                1f at 1500
            },
            initialStartOffset = androidx.compose.animation.core.StartOffset(delayMillis)
        ),
        label = "blinkAlpha"
    )
}

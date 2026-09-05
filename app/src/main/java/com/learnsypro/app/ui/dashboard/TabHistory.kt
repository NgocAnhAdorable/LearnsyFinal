package com.learnsypro.app.ui.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsypro.app.ui.theme.Baloo2FontFamily
import com.learnsypro.app.ui.theme.NunitoFontFamily
import com.learnsypro.app.ui.theme.rememberFadeUpState
import com.learnsypro.app.ui.theme.rememberFloatOffset

/**
 * ── TabHistory ──
 * Tương đương function TabHistory({history,onHistDetail,onClearHistory,dark})
 * trong dashboard.jsx. Danh sách lịch sử làm bài với icon kết quả (trophy/
 * star/thumbsup/sad theo điểm), nút "Xoá tất cả" yêu cầu xác nhận 2 bước.
 */
@Composable
fun TabHistory(
    history: List<HistoryEntry>,
    dark: Boolean,
    onHistDetail: (HistoryEntry) -> Unit,
    onClearHistory: () -> Unit,
    /** presetId hiện tại của nền dùng chung — "custom_image" khi đang bật ảnh nền tự upload,
     *  lúc đó card cần chuyển kính mờ để lộ ảnh nền, giống TabHome đã làm (xem
     *  DashboardColors.resolvedCardColor). Trước đây tab này chưa nhận tham số này nên card
     *  vẫn dùng C.card đặc như cũ dù TabHome đã mờ — đúng vấn đề "tab này blur yếu hơn". */
    bgPresetId: String = "default_light",
    modifier: Modifier = Modifier
) {
    val C = dashboardColors(dark)
    val cardColor = resolvedCardColor(dark, bgPresetId)
    val cardBorderColor = resolvedCardBorderColor(dark, bgPresetId)

    if (history.isEmpty()) {
        EmptyHistoryState(dark, cardBorderColor, modifier)
        return
    }

    var confirmClear by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DashboardIcon(name = "history", size = 18.dp, color = C.accent)
                    Text(text = "Lịch sử (${history.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.fg, fontFamily = Baloo2FontFamily)
                }

                AnimatedContent(targetState = confirmClear, label = "clearConfirm") { confirming ->
                    if (!confirming) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(C.danger.copy(alpha = 0.07f))
                                .border(1.dp, C.danger.copy(alpha = 0.27f), RoundedCornerShape(50))
                                .clickable { confirmClear = true }
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(text = "Xoá tất cả", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.danger, fontFamily = NunitoFontFamily)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(AccentDangerGradient)
                                    .clickable {
                                        onClearHistory()
                                        confirmClear = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(text = "Xác nhận", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White, fontFamily = NunitoFontFamily)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (dark) Color(0x1AFFFFFF) else Color(0x12000000))
                                    .clickable { confirmClear = false }
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(text = "Huỷ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.sub, fontFamily = NunitoFontFamily)
                            }
                        }
                    }
                }
            }
        }

        items(history, key = { it.timestampMillis }) { h ->
            HistoryCard(h, dark, cardColor, onClick = { onHistDetail(h) })
        }
    }
}

@Composable
private fun HistoryCard(h: HistoryEntry, dark: Boolean, cardColor: Color, onClick: () -> Unit) {
    val C = dashboardColors(dark)
    val col = pctColor(h.pct)
    val resultIcon = when {
        h.pct >= 90 -> "trophy"
        h.pct >= 70 -> "star"
        h.pct >= 50 -> "thumbsup"
        else -> "sad"
    }
    val floatYState = rememberFloatOffsetLocal()
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.97f else 1f, com.learnsypro.app.ui.theme.OneUiSpring.bouncy, label = "historyCardPress"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .clip(RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.card))
            .background(cardColor)
            .border(1.5.dp, col.copy(alpha = 0.19f), RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.card))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScoreSVG(pct = h.pct, size = 52.dp, dark = dark)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = h.lessonTitle.ifBlank { "Bài quiz" },
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = C.fg,
                fontFamily = Baloo2FontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${h.score}/${h.total} điểm · ${pctLabel(h.pct)}",
                fontSize = 11.sp,
                color = C.sub,
                fontFamily = NunitoFontFamily
            )
            Text(
                text = "${fmtDate(h.timestampMillis)} ${fmtTime(h.timestampMillis)}",
                fontSize = 10.sp,
                color = C.sub.copy(alpha = 0.7f),
                fontFamily = NunitoFontFamily
            )
        }

        Box(modifier = Modifier.graphicsLayer { translationY = floatYState.value }) {
            DashboardIcon(name = resultIcon, size = 22.dp, color = col)
        }
    }
}

@Composable
private fun EmptyHistoryState(dark: Boolean, cardBorderColor: Color, modifier: Modifier = Modifier) {
    val C = dashboardColors(dark)
    Column(
        modifier = modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 100.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DashboardIcon(name = "history", size = 18.dp, color = C.accent)
            Text(text = "Lịch sử", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.fg, fontFamily = Baloo2FontFamily)
        }
        Spacer(modifier = Modifier.height(14.dp))
        val (fadeAlpha, fadeOffsetY) = rememberFadeUpState()
        val floatState = rememberFloatOffset()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = fadeAlpha; translationY = fadeOffsetY }
                .clip(RoundedCornerShape(20.dp))
                .border(1.5.dp, cardBorderColor, RoundedCornerShape(20.dp))
                .padding(vertical = 48.dp, horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.graphicsLayer {
                    translationY = floatState.translateY.value
                    rotationZ = floatState.rotation.value
                }
            ) {
                DashboardIcon(name = "history", size = 52.dp, color = Color(0x80F472B6))
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(text = "Chưa có lịch sử", fontSize = 20.sp, fontWeight = FontWeight.Black, color = C.fg, fontFamily = Baloo2FontFamily)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Làm bài quiz để lưu kết quả nhé~", fontSize = 13.sp, color = C.sub, fontFamily = NunitoFontFamily)
        }
    }
}

/** Nhấp nhô nhẹ liên tục — tương đương @keyframes bb-float dùng riêng cho icon kết quả */
@Composable
private fun rememberFloatOffsetLocal(): androidx.compose.runtime.State<Float> {
    if (com.learnsypro.app.ui.theme.LocalLiteMode.current) {
        return remember { mutableStateOf(0f) }
    }
    val transition = rememberInfiniteTransition(label = "historyFloat")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "historyFloatY"
    )
}

private val AccentDangerGradient = androidx.compose.ui.graphics.Brush.linearGradient(
    listOf(Color(0xFFEF4444), Color(0xFFDC2626))
)

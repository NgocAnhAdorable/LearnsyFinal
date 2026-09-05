package com.learnsypro.app.ui.listening

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsypro.app.ui.dashboard.DashboardIcon
import com.learnsypro.app.ui.dashboard.MascotImage
import com.learnsypro.app.ui.dashboard.MascotPose
import com.learnsypro.app.ui.quiz.quizColors
import com.learnsypro.app.ui.theme.NunitoFontFamily

@Composable
fun ListeningListScreen(
    items: List<ListeningItem>,
    loading: Boolean,
    loadError: Boolean,
    dark: Boolean,
    onBack: () -> Unit,
    onOpenItem: (ListeningItem) -> Unit,
    isOffline: Boolean = false,
    downloadedIds: Set<String> = emptySet(),
    onDownloadItem: (String) -> Unit = {},
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    val C = quizColors(dark)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(C.headerBg)
                .padding(horizontal = 15.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .background(C.navBtn, RoundedCornerShape(50))
                    .border(1.5.dp, C.navBtnBorder, RoundedCornerShape(50))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DashboardIcon(name = "chevronLeft", size = 11.dp, color = C.navBtnText)
                Text(text = "Quay lại", fontSize = 12.sp, fontWeight = FontWeight.Black, color = C.navBtnText, fontFamily = NunitoFontFamily)
            }

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DashboardIcon(name = "book", size = 15.dp, color = C.text)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Listening", fontSize = 14.sp, fontWeight = FontWeight.Black, color = C.text, fontFamily = NunitoFontFamily)
            }

            // Nút refresh — tải lại danh sách Listening từ Supabase mà không cần
            // thoát màn hình; cùng pattern xoay/scale với nút refresh Dashboard.
            val refreshRotation = if (isRefreshing) {
                val transition = rememberInfiniteTransition(label = "listeningRefreshSpin")
                transition.animateFloat(
                    initialValue = 0f, targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing)),
                    label = "listeningRefreshSpinValue"
                ).value
            } else 0f
            val refreshInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val refreshPressed by refreshInteractionSource.collectIsPressedAsState()
            val refreshScale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (refreshPressed) 0.90f else 1f,
                animationSpec = tween(120),
                label = "listeningRefreshBtnScale"
            )
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .graphicsLayer { scaleX = refreshScale; scaleY = refreshScale }
                    .background(if (dark) Color(0x26F59E0B) else Color(0x1AA855F7), RoundedCornerShape(17.dp))
                    .border(1.5.dp, if (dark) Color(0x4DF59E0B) else Color(0x40A855F7), RoundedCornerShape(17.dp))
                    .clickable(
                        enabled = !isRefreshing,
                        interactionSource = refreshInteractionSource,
                        indication = null
                    ) { onRefresh() },
                contentAlignment = Alignment.Center
            ) {
                DashboardIcon(
                    name = "refresh",
                    size = 16.dp,
                    color = if (dark) Color(0xFFF59E0B) else Color(0xFFA855F7),
                    modifier = Modifier.graphicsLayer { rotationZ = refreshRotation }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isOffline) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x1AF59E0B), RoundedCornerShape(14.dp))
                            .border(1.5.dp, Color(0x40F59E0B), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DashboardIcon(name = "wifiOff", size = 14.dp, color = Color(0xFFB45309))
                        Text(text = "Không có mạng — đang xem bài đã lưu offline", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309), fontFamily = NunitoFontFamily)
                    }
                }
            }

            if (loadError) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x14EF4444), RoundedCornerShape(14.dp))
                            .border(1.5.dp, Color(0x40EF4444), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(text = "Không tải được danh sách Listening. Thử lại sau nhé!", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444), fontFamily = NunitoFontFamily)
                    }
                }
            }

            if (loading) {
                items(4) { SkeletonCard(dark) }
            } else if (items.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Mascot đeo tai nghe — khớp ngữ cảnh Luyện nghe, thay icon sách chung.
                        MascotImage(drawableRes = MascotPose.HEADPHONES, sizeDp = 72)
                        Text(text = "Chưa có bài Listening nào. Quay lại sau nhé!", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.textMid, fontFamily = NunitoFontFamily)
                    }
                }
            } else {
                // key = id thật của item (không phải index) — giữ đúng danh tính khi
                // list lọc/sắp xếp lại, tránh Compose recompose nhầm item hoặc mất
                // trạng thái animation/scroll khi thứ tự đổi.
                items(items.size, key = { idx -> items[idx].id }) { idx ->
                    ListeningItemCard(
                        item = items[idx],
                        index = idx,
                        dark = dark,
                        downloaded = downloadedIds.contains(items[idx].id),
                        onClick = { onOpenItem(items[idx]) },
                        onDownload = { onDownloadItem(items[idx].id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ListeningItemCard(
    item: ListeningItem,
    index: Int,
    dark: Boolean,
    downloaded: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit
) {
    val C = quizColors(dark)
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.97f else 1f, com.learnsypro.app.ui.theme.OneUiSpring.bouncy, label = "listeningItemPress"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .background(C.surfaceQ, RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.card))
            .border(1.5.dp, C.borderQ, RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.card))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(Color(0x2EB07CF0), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = (index + 1).toString(), fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFFB07CF0), fontFamily = NunitoFontFamily)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stripHtml(item.text),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = C.text,
                lineHeight = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontFamily = NunitoFontFamily
            )
            Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (item.answers.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .background(Color(0x1A10B981), RoundedCornerShape(50))
                            .border(1.dp, Color(0x4D10B981), RoundedCornerShape(50))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(text = "${item.answers.size} chỗ trống", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF059669), fontFamily = NunitoFontFamily)
                    }
                }
                if (item.statements.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .background(Color(0x14DC2626), RoundedCornerShape(50))
                            .border(1.dp, Color(0x47DC2626), RoundedCornerShape(50))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(text = "${item.statements.size} nhận định", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFFDC2626), fontFamily = NunitoFontFamily)
                    }
                }
            }
        }

        // Nút "Tải về" thủ công — đánh dấu bài chắc chắn dùng offline được.
        // Nội dung thực tế đã tự cache sẵn khi mở danh sách (auto-cache),
        // nút này chủ yếu là tín hiệu UI + tránh học sinh lo lắng mất bài.
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (downloaded) Color(0x1A10B981) else if (dark) Color(0x14FFFFFF) else Color(0x14000000))
                .clickable(enabled = !downloaded, onClick = onDownload),
            contentAlignment = Alignment.Center
        ) {
            DashboardIcon(
                name = if (downloaded) "check" else "download",
                size = 14.dp,
                color = if (downloaded) Color(0xFF10B981) else C.textMid
            )
        }
    }
}

@Composable
private fun SkeletonCard(dark: Boolean) {
    val bg = if (dark) Color(0x0AFFFFFF) else Color(0x0A000000)
    val transition = rememberInfiniteTransition(label = "skeletonShimmer")
    val shimmerX by transition.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "shimmerX"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(bg, RoundedCornerShape(18.dp))
            .border(1.5.dp, if (dark) Color(0x0DFFFFFF) else Color(0x0D000000), RoundedCornerShape(18.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.size(30.dp).graphicsLayer { alpha = 0.5f + 0.3f * (shimmerX + 1f) / 2f }.background(bg, CircleShape))
            Column(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.fillMaxWidth(0.75f).height(14.dp).background(bg, RoundedCornerShape(6.dp)))
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth(0.45f).height(12.dp).background(bg, RoundedCornerShape(6.dp)))
            }
        }
    }
}

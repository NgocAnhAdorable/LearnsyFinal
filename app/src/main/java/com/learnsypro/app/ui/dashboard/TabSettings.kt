package com.learnsypro.app.ui.dashboard


import android.net.Uri
import com.learnsypro.app.background.BgSettings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsypro.app.data.DevicePerfResult
import com.learnsypro.app.data.detectDevicePerformance
import com.learnsypro.app.ui.theme.Baloo2FontFamily
import com.learnsypro.app.ui.theme.NunitoFontFamily
import kotlinx.coroutines.launch

/**
 * ── TabSettings ──
 * Tương đương function TabSettings({...}) trong dashboard.jsx.
 * Profile card (avatar upload), thẻ toggle (dark/shuffle Q/shuffle A/lite/
 * flicker), thẻ "Hiệu năng thiết bị" với nút phát hiện tự động, thẻ giới
 * thiệu app, và đăng xuất có xác nhận.
 *
 * LƯU Ý: window.BgSettingsCard / SparkleSettingsCard / DevIslandSettingsCard
 * trong bản gốc là các card mở rộng do file khác (background-settings.jsx,
 * learnsy-sparkle-settings.jsx, learnsy-dev-island.jsx) tự đăng ký — CHƯA
 * convert (chưa nhận được các file .jsx đó). Khi có, thêm Composable tương
 * ứng vào đúng vị trí đã đánh dấu TODO bên dưới.
 */
@Composable
fun TabSettings(
    student: Student,
    avatarUrl: String?,
    avatarLoading: Boolean,
    onAvatarUpload: suspend (Uri) -> Pair<Boolean, String?>,
    onAvatarRemove: suspend () -> Unit,
    coverUrl: String? = null,
    coverLoading: Boolean = false,
    onCoverUpload: suspend (Uri) -> Pair<Boolean, String?> = { _ -> false to null },
    onCoverRemove: suspend () -> Unit = {},
    history: List<HistoryEntry>,
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit,
    shuffleQ: Boolean,
    onShuffleQChange: (Boolean) -> Unit,
    shuffleA: Boolean,
    onShuffleAChange: (Boolean) -> Unit,
    liteMode: Boolean,
    onLiteModeChange: (Boolean) -> Unit,
    flickerFx: Boolean,
    onFlickerFxChange: (Boolean) -> Unit,
    mascotEnabled: Boolean = true,
    onMascotEnabledChange: (Boolean) -> Unit = {},
    bgSettings: BgSettings,
    bgSyncState: String,
    bgUploading: Boolean,
    onBgPickPreset: (String) -> Unit,
    onBgPickBlurMode: (String) -> Unit,
    onBgPickBlurPercent: (Int) -> Unit,
    onBgPickImage: (Uri) -> Unit,
    onBgRemoveImage: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val C = dashboardColors(dark)
    val cardColor = resolvedCardColor(dark, bgSettings.presetId)
    val cardBorderColor = resolvedCardBorderColor(dark, bgSettings.presetId)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var logoutConfirm by remember { mutableStateOf(false) }
    var detecting by remember { mutableStateOf(false) }
    var detectResult by remember { mutableStateOf<DevicePerfResult?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Profile Card ──
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(cardColor)
                    .border(1.5.dp, cardBorderColor, RoundedCornerShape(22.dp)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AvatarUploader(
                    displayName = student.nameOrUsername,
                    dark = dark,
                    avatarUrl = avatarUrl,
                    loading = avatarLoading,
                    onUpload = onAvatarUpload,
                    onRemove = onAvatarRemove,
                    coverUrl = coverUrl,
                    coverLoading = coverLoading,
                    onCoverUpload = onCoverUpload,
                    onCoverRemove = onCoverRemove
                )
                Text(
                    text = student.nameOrUsername,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    color = C.fg,
                    fontFamily = Baloo2FontFamily
                )
                Text(
                    text = "@${student.username}",
                    fontSize = 12.sp,
                    color = C.sub,
                    fontFamily = NunitoFontFamily,
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                if (history.isNotEmpty()) {
                    val avgPct = history.map { it.pct }.average()
                    val quickStats = listOf(
                        Triple("Đã học", history.size.toString(), "book" to Color(0xFFA855F7)),
                        Triple("Điểm TB", "${fmtScore(avgPct)}/10", "star" to Color(0xFFF472B6)),
                        Triple("Cao nhất", "${fmtScore(history.maxOf { it.pct })}/10", "trophy" to Color(0xFFF59E0B))
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickStats.forEach { (label, value, iconColor) ->
                            val (icon, color) = iconColor
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (dark) Color(0x14FFFFFF) else Color(0x99FFFFFF))
                                    .border(1.dp, Color(0x33F472B6), RoundedCornerShape(14.dp))
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                DashboardIcon(name = icon, size = 16.dp, color = color)
                                Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Black, color = C.accent, fontFamily = Baloo2FontFamily)
                                Text(text = label, fontSize = 9.sp, color = C.sub, fontWeight = FontWeight.Bold, fontFamily = NunitoFontFamily)
                            }
                        }
                    }
                }
            }
        }

        // ── Toggle Settings Card ──
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.card))
                    .background(cardColor)
                    .border(1.5.dp, cardBorderColor, RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.card))
                    .padding(horizontal = 18.dp)
            ) {
                ToggleRow(icon = "moon", label = "Chế độ tối", sub = "Bảo vệ mắt ban đêm", value = dark, dark = dark, onChange = onDarkChange)
                ToggleRow(icon = "shuffle", label = "Xáo câu hỏi", sub = "Trộn thứ tự câu hỏi", value = shuffleQ, dark = dark, onChange = onShuffleQChange)
                ToggleRow(icon = "dice", label = "Xáo đáp án", sub = "Trộn thứ tự đáp án", value = shuffleA, dark = dark, onChange = onShuffleAChange)
                ToggleRow(icon = "feather", label = "Chế độ Lite", sub = "Giảm hiệu ứng, máy chạy mượt hơn", value = liteMode, dark = dark, onChange = onLiteModeChange)
                ToggleRow(
                    icon = "zap", label = "Hiệu ứng nhấp nháy",
                    sub = "Icon & chữ nhấp nháy nhẹ (tab, thẻ bài, thành tích)",
                    value = flickerFx, dark = dark, onChange = onFlickerFxChange
                )
                ToggleRow(
                    icon = "sparkle", label = "Bạn đồng hành",
                    sub = "Bạn nữ tai mèo thấp thoáng ngẫu nhiên ở Trang chủ",
                    value = mascotEnabled, dark = dark, onChange = onMascotEnabledChange, isLast = true
                )
            }
        }

        // ── Lite Mode Detector Card ──
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.card))
                    .background(cardColor)
                    .border(
                        1.5.dp,
                        if (liteMode) Color(0x6634D399) else cardBorderColor,
                        RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.card)
                    )
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (liteMode) Color(0x3334D399) else Color(0x1AF472B6)),
                        contentAlignment = Alignment.Center
                    ) {
                        DashboardIcon(name = "cpu", size = 16.dp, color = if (liteMode) Color(0xFF10B981) else Color(0xFFF472B6))
                    }
                    Text(text = "Hiệu năng thiết bị", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.fg, fontFamily = NunitoFontFamily)
                    if (liteMode) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0x2634D399))
                                .border(1.dp, Color(0x4D34D399), RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(text = "LITE ON", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF10B981), fontFamily = NunitoFontFamily)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (liteMode) {
                        "Chế độ Lite đang bật — animations và hiệu ứng nặng đã được tắt để máy chạy mượt hơn."
                    } else {
                        "Bật chế độ Lite nếu máy bị giật lag. Hoặc dùng nút \"Phát hiện tự động\" để Learnsy tự kiểm tra."
                    },
                    fontSize = 12.sp,
                    color = C.sub,
                    lineHeight = 18.sp,
                    fontFamily = NunitoFontFamily
                )

                detectResult?.let { r ->
                    Spacer(modifier = Modifier.height(10.dp))
                    val statusColor = if (r.isLow) Color(0xFFEF4444) else if (r.score < 80) Color(0xFFF59E0B) else Color(0xFF10B981)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(statusColor.copy(alpha = 0.07f))
                            .border(1.dp, statusColor.copy(alpha = 0.27f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(text = r.label, fontSize = 12.sp, fontWeight = FontWeight.Black, color = statusColor, fontFamily = NunitoFontFamily)
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(text = "Điểm: ${r.score}/100", fontSize = 11.sp, color = C.sub, fontFamily = NunitoFontFamily)
                            r.fps?.let { Text(text = "FPS: ~$it", fontSize = 11.sp, color = C.sub, fontFamily = NunitoFontFamily) }
                        }
                        if (r.reason.isNotEmpty()) {
                            Text(
                                text = r.reason.joinToString(" · "),
                                fontSize = 11.sp,
                                color = C.sub,
                                fontFamily = NunitoFontFamily,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (detecting) Brush.linearGradient(listOf(Color(0x14A855F7), Color(0x14A855F7)))
                            else Brush.linearGradient(listOf(Color(0x1FA855F7), Color(0x14F472B6)))
                        )
                        .border(1.5.dp, Color(0x4DA855F7), RoundedCornerShape(14.dp))
                        .clickable(enabled = !detecting) {
                            detecting = true
                            scope.launch {
                                detectResult = detectDevicePerformance(context)
                                if (detectResult?.isLow == true) onLiteModeChange(true)
                                detecting = false
                            }
                        }
                        .padding(vertical = 11.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (detecting) {
                        val rotation by rememberInfiniteTransition(label = "detectSpin").animateFloat(
                            initialValue = 0f, targetValue = 360f,
                            animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
                            label = "detectSpinRotation"
                        )
                        Box(modifier = Modifier.graphicsLayer { rotationZ = rotation }) {
                            DashboardIcon(name = "spinner", size = 15.dp, color = Color(0xFFA855F7))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Đang đo...", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFFA855F7), fontFamily = NunitoFontFamily)
                    } else {
                        DashboardIcon(name = "cpu", size = 15.dp, color = Color(0xFFA855F7))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Phát hiện tự động", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFFA855F7), fontFamily = NunitoFontFamily)
                    }
                }
            }
        }

        // ── Background Settings Card ──
        item {
            BgSettingsCard(
                settings = bgSettings,
                dark = dark,
                syncState = bgSyncState,
                uploading = bgUploading,
                onPickPreset = onBgPickPreset,
                onPickBlurMode = onBgPickBlurMode,
                onPickBlurPercent = onBgPickBlurPercent,
                onPickImage = onBgPickImage,
                onRemoveImage = onBgRemoveImage
            )
        }

        // TODO: SparkleSettingsCard / DevIslandSettingsCard — chờ convert
        // learnsy-sparkle-settings.jsx, learnsy-dev-island.jsx rồi chèn
        // Composable tương ứng vào đây.

        // ── About Card ──
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.card))
                    .background(cardColor)
                    .border(1.5.dp, cardBorderColor, RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.card))
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFFCE7F3), Color(0xFFEDE9FE))))
                        .border(1.5.dp, Color(0x40F472B6), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    DashboardIcon(name = "book", size = 22.dp, color = Color(0xFFF472B6))
                }
                Column {
                    Text(text = "Learnsy Pro", fontSize = 17.sp, fontWeight = FontWeight.Black, color = C.fg, fontFamily = Baloo2FontFamily)
                    Text(text = "Nền tảng học tập · v24.5", fontSize = 11.sp, color = C.sub, fontFamily = NunitoFontFamily)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(text = "Made with", fontSize = 10.sp, color = C.accent, fontWeight = FontWeight.Bold, fontFamily = NunitoFontFamily)
                        DashboardIcon(name = "heart", size = 10.dp, color = C.accent)
                        Text(text = "for you~", fontSize = 10.sp, color = C.accent, fontWeight = FontWeight.Bold, fontFamily = NunitoFontFamily)
                    }
                }
            }
        }

        // ── Logout ──
        item {
            AnimatedContent(targetState = logoutConfirm, label = "logoutConfirm") { confirming ->
                if (!confirming) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(C.danger.copy(alpha = 0.05f))
                            .border(1.5.dp, C.danger.copy(alpha = 0.27f), RoundedCornerShape(18.dp))
                            .clickable { logoutConfirm = true }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DashboardIcon(name = "logout", size = 17.dp, color = C.danger)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Đăng xuất", fontSize = 14.sp, fontWeight = FontWeight.Black, color = C.danger, fontFamily = NunitoFontFamily)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(C.danger.copy(alpha = 0.06f))
                            .border(1.5.dp, C.danger.copy(alpha = 0.21f), RoundedCornerShape(18.dp))
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Mascot vẫy tay Bye~ — lưu luyến lúc xác nhận đăng xuất,
                        // thay icon "sad" nhỏ trung tính.
                        MascotImage(drawableRes = MascotPose.WAVE_BYE, sizeDp = 64)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "Bạn chắc chắn muốn đăng xuất?", fontSize = 14.sp, fontWeight = FontWeight.Black, color = C.danger, fontFamily = NunitoFontFamily)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val logoutInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            val logoutPressed by logoutInteraction.collectIsPressedAsState()
                            val logoutScale by androidx.compose.animation.core.animateFloatAsState(
                                if (logoutPressed) 0.95f else 1f, com.learnsypro.app.ui.theme.OneUiSpring.bouncy, label = "logoutBtnScale"
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .graphicsLayer { scaleX = logoutScale; scaleY = logoutScale }
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFFDC2626))))
                                    .clickable(interactionSource = logoutInteraction, indication = null, onClick = onLogout)
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "Đăng xuất", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White, fontFamily = NunitoFontFamily)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (dark) Color(0x1AFFFFFF) else Color(0x12000000))
                                    .clickable { logoutConfirm = false }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "Huỷ", fontSize = 14.sp, fontWeight = FontWeight.Black, color = C.fg, fontFamily = NunitoFontFamily)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * ── ToggleRow ──
 * Tương đương function ToggleRow({icon,label,sub,val,onChange}) trong
 * dashboard.jsx. Hàng cài đặt với icon + tên + mô tả + KawaiiToggle.
 */
@Composable
private fun ToggleRow(
    icon: String,
    label: String,
    sub: String,
    value: Boolean,
    dark: Boolean,
    onChange: (Boolean) -> Unit,
    isLast: Boolean = false
) {
    val C = dashboardColors(dark)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (value) C.accent.copy(alpha = 0.16f) else (if (dark) Color(0x14FFFFFF) else Color(0x0F000000))),
                contentAlignment = Alignment.Center
            ) {
                DashboardIcon(name = icon, size = 18.dp, color = if (value) C.accent else C.sub)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.fg, fontFamily = NunitoFontFamily)
                Text(text = sub, fontSize = 10.sp, color = C.sub, fontFamily = NunitoFontFamily)
            }
            KawaiiToggle(value = value, onChange = onChange)
        }
        if (!isLast) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(if (dark) Color(0x14FFFFFF) else Color(0x0D000000))
            )
        }
    }
}

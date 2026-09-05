package com.learnsypro.app.ui.dashboard

import android.net.Uri
import com.learnsypro.app.background.BG_PRESETS
import com.learnsypro.app.background.BLUR_MODES
import com.learnsypro.app.background.BgPreset
import com.learnsypro.app.background.BgSettings
import com.learnsypro.app.background.blurIconName
import com.learnsypro.app.background.clampPercent
import com.learnsypro.app.background.legacyModeToPercent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.learnsypro.app.ui.theme.NunitoFontFamily

/**
 * ── BgSettingsCard ──
 * Tương đương function BgSettingsCard({dark,studentId}) trong
 * background-settings.js. Đặt trong TabSettings.kt tại vị trí TODO đã đánh
 * dấu sẵn.
 *
 * Khác bản web:
 * - Không tự quản state đồng bộ Upstash bằng debounce nội bộ + MutationObserver
 *   theo dõi class 'dark' — thay bằng: (1) callback onSettingsChange đẩy state
 *   lên ViewModel, ViewModel debounce & sync Upstash; (2) dark mode lock xử lý
 *   ngay trong Composable này bằng LaunchedEffect(dark), không cần observer DOM.
 * - Không có preview optimistic base64 khi upload — hiện spinner cho tới khi
 *   Supabase Storage trả URL xong (đơn giản hơn, tránh giữ Bitmap lớn trong RAM).
 *
 * @param syncState "idle" | "saving" | "saved" | "error" — trạng thái sync cloud hiện tại
 */
@Composable
fun BgSettingsCard(
    settings: BgSettings,
    dark: Boolean,
    syncState: String,
    uploading: Boolean,
    onPickPreset: (String) -> Unit,
    onPickBlurMode: (String) -> Unit,
    onPickBlurPercent: (Int) -> Unit,
    onPickImage: (Uri) -> Unit,
    onRemoveImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val C = dashboardColors(dark)
    val cardColor = resolvedCardColor(dark, settings.presetId)
    val cardBorderColor = resolvedCardBorderColor(dark, settings.presetId)

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) onPickImage(uri)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardColor)
            .border(1.5.dp, cardBorderColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        // ── Header ──
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardIcon(name = "picture", size = 20.dp, color = C.accent)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Tùy chỉnh nền", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.fg, fontFamily = NunitoFontFamily)
                Text(text = "Đổi nền & làm mờ", fontSize = 11.sp, color = C.sub, fontFamily = NunitoFontFamily)
            }
            SyncBadge(syncState = syncState, dark = dark)
        }

        Divider(dark)

        // ── Blur modes ──
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            DashboardIcon(name = "sparkle", size = 12.dp, color = C.sub)
            Text(
                text = "CHẾ ĐỘ LÀM MỜ", fontSize = 11.sp, fontWeight = FontWeight.Black,
                color = C.sub, fontFamily = NunitoFontFamily, modifier = Modifier.weight(1f)
            )
            if (dark) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(C.accent.copy(alpha = 0.08f))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    DashboardIcon(name = "lock", size = 9.dp, color = C.accent.copy(alpha = 0.6f))
                    Text(text = "Khoá khi Dark Mode", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = C.accent.copy(alpha = 0.6f), fontFamily = NunitoFontFamily)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            BLUR_MODES.forEach { mode ->
                val isOff = mode.id == "off"
                val isLocked = dark && !isOff
                val isSel = settings.blurMode == mode.id
                BlurModeButton(
                    label = mode.label,
                    iconName = blurIconName(mode.id),
                    selected = isSel,
                    locked = isLocked,
                    dark = dark,
                    onClick = { if (!isLocked) onPickBlurMode(mode.id) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── Slider tùy chỉnh độ mờ ──
        val sliderEnabled = !dark && settings.blurMode != "off"
        Column(modifier = Modifier.padding(top = 10.dp).alpha(if (sliderEnabled) 1f else 0.4f)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Tùy chỉnh độ mờ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.sub, fontFamily = NunitoFontFamily)
                Text(text = "${if (settings.blurMode == "off") 0 else settings.blurPercent}%", fontSize = 12.sp, fontWeight = FontWeight.Black, color = C.accent, fontFamily = NunitoFontFamily)
            }
            Slider(
                value = (if (settings.blurMode == "off") 0 else settings.blurPercent).toFloat(),
                onValueChange = { onPickBlurPercent(clampPercent(it)) },
                valueRange = 0f..100f,
                enabled = sliderEnabled,
                colors = SliderDefaults.colors(
                    thumbColor = C.accent,
                    activeTrackColor = C.accent,
                    inactiveTrackColor = if (dark) Color(0x14FFFFFF) else Color(0x1FF472B6)
                )
            )
        }

        Divider(dark)

        // ── Preset grid ──
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            DashboardIcon(name = "palette", size = 12.dp, color = C.sub)
            Text(text = "MÀU & HÌNH NỀN", fontSize = 11.sp, fontWeight = FontWeight.Black, color = C.sub, fontFamily = NunitoFontFamily)
        }
        Spacer(modifier = Modifier.height(8.dp))

        val rows = BG_PRESETS.chunked(5)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { preset ->
                        PresetSwatch(
                            preset = preset,
                            selected = settings.presetId == preset.id,
                            dark = dark,
                            uploading = uploading,
                            imageUrl = settings.imageUrl,
                            onClick = {
                                if (preset.isImage) pickImageLauncher.launch("image/*") else onPickPreset(preset.id)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Đệm ô trống nếu hàng cuối không đủ 5 preset
                    repeat(5 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }

        // ── Custom image actions ──
        if (settings.presetId == "custom_image" && !settings.imageUrl.isNullOrBlank()) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(C.accent.copy(alpha = if (dark) 0.15f else 0.1f))
                        .border(1.5.dp, C.accent.copy(alpha = 0.33f), RoundedCornerShape(12.dp))
                        .clickable { pickImageLauncher.launch("image/*") }
                        .padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DashboardIcon(name = "camera", size = 13.dp, color = C.accent)
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(text = "Đổi ảnh", fontSize = 12.sp, fontWeight = FontWeight.Black, color = C.accent, fontFamily = NunitoFontFamily)
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFEF4444).copy(alpha = if (dark) 0.12f else 0.08f))
                        .border(1.5.dp, Color(0xFFEF4444).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .clickable { onRemoveImage() }
                        .padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DashboardIcon(name = "trash", size = 13.dp, color = Color(0xFFEF4444))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(text = "Xoá ảnh", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFFEF4444), fontFamily = NunitoFontFamily)
                }
            }
        }
    }
}

@Composable
private fun Divider(dark: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(vertical = 12.dp)
            .background(if (dark) Color(0x12FFFFFF) else Color(0x0D000000))
    )
}

@Composable
private fun SyncBadge(syncState: String, dark: Boolean) {
    val C = dashboardColors(dark)
    val (iconName, label, color) = when (syncState) {
        "saving" -> Triple("spinner", "Đang lưu...", C.sub)
        "saved" -> Triple("cloud", "Đã lưu cloud", Color(0xFF10B981))
        "error" -> Triple("sad", "Lỗi đồng bộ", Color(0xFFEF4444))
        else -> Triple("cloud", "Cloud sync", C.sub)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (syncState == "saving") {
            val rotation by rememberInfiniteTransition(label = "bgSpin").animateFloat(
                initialValue = 0f, targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing)),
                label = "bgSpinRotation"
            )
            Box(modifier = Modifier.graphicsLayer { rotationZ = rotation }) {
                DashboardIcon(name = iconName, size = 12.dp, color = color)
            }
        } else {
            DashboardIcon(name = iconName, size = 12.dp, color = color)
        }
        Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Black, color = color, fontFamily = NunitoFontFamily)
    }
}

@Composable
private fun BlurModeButton(
    label: String,
    iconName: String,
    selected: Boolean,
    locked: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val C = dashboardColors(dark)
    val bg = when {
        locked -> if (dark) Color(0x05FFFFFF) else Color(0x08000000)
        selected -> C.accent.copy(alpha = if (dark) 0.2f else 0.12f)
        else -> if (dark) Color(0x0DFFFFFF) else Color(0x80FFFFFF)
    }
    val border = if (selected) C.accent else (if (dark) Color(0x1AFFFFFF) else C.accent.copy(alpha = 0.2f))
    val fg = when {
        locked -> if (dark) Color(0x33FFFFFF) else Color(0x33000000)
        selected -> C.accent
        else -> C.sub
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(if (selected) 2.dp else 1.5.dp, border, RoundedCornerShape(14.dp))
            .clickable(enabled = !locked, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            DashboardIcon(name = iconName, size = 16.dp, color = fg)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Black, color = fg, fontFamily = NunitoFontFamily, maxLines = 1)
        }
        if (locked) {
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(3.dp)) {
                DashboardIcon(name = "lock", size = 8.dp, color = fg)
            }
        }
    }
}

@Composable
private fun PresetSwatch(
    preset: BgPreset,
    selected: Boolean,
    dark: Boolean,
    uploading: Boolean,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val C = dashboardColors(dark)
    val border = if (selected) C.accent else (if (dark) Color(0x1FFFFFFF) else C.accent.copy(alpha = 0.25f))

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .border(if (selected) 2.5.dp else 1.5.dp, border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        when {
            preset.isImage && !imageUrl.isNullOrBlank() -> {
                AsyncImage(model = imageUrl, contentDescription = preset.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            preset.isImage -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (dark) Color(0x14FFFFFF) else C.accent.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    DashboardIcon(name = if (uploading) "spinner" else "camera", size = 20.dp, color = C.accent)
                }
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize().background(preset.gradient ?: androidx.compose.ui.graphics.SolidColor(Color(0xFFFFF5F9))))
            }
        }

        if (selected) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0x38000000)),
                contentAlignment = Alignment.Center
            ) {
                DashboardIcon(name = "check", size = 16.dp, color = Color.White)
            }
        }
    }
}

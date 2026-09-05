@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.learnsypro.app.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.learnsypro.app.data.LearningFile
import com.learnsypro.app.data.LearningFileRepository
import com.learnsypro.app.ui.dashboard.DashboardIcon
import com.learnsypro.app.ui.dashboard.dashboardColors
import com.learnsypro.app.ui.dashboard.resolvedCardColor
import com.learnsypro.app.ui.dashboard.resolvedCardBorderColor
import com.learnsypro.app.ui.theme.Baloo2FontFamily
import com.learnsypro.app.ui.theme.NunitoFontFamily
import com.learnsypro.app.ui.theme.rememberFadeUpState
import com.learnsypro.app.ui.theme.rememberFloatOffset
import com.learnsypro.app.ui.theme.rememberPulseAlpha
import com.learnsypro.app.ui.theme.rememberSpinRotation
import kotlinx.coroutines.launch

/* ══════════════════════════════════════════════════════════════════════
   FILES-TAB — Tab "Tài liệu" cho học sinh.
   Tương đương files-tab.jsx (TabFiles). Danh sách file admin đã upload
   (bảng Supabase learning_files), preview trong app theo loại file, và
   nút tải về.
════════════════════════════════════════════════════════════════════ */

private val EXT_COLORS: Map<String, Color> = mapOf(
    "pdf" to Color(0xFFEF4444),
    "doc" to Color(0xFF3B82F6), "docx" to Color(0xFF3B82F6),
    "xls" to Color(0xFF22C55E), "xlsx" to Color(0xFF22C55E),
    "ppt" to Color(0xFFF97316), "pptx" to Color(0xFFF97316),
    "zip" to Color(0xFFA855F7), "rar" to Color(0xFFA855F7),
    "mp4" to Color(0xFF06B6D4), "mp3" to Color(0xFF06B6D4),
    "jpg" to Color(0xFFF59E0B), "jpeg" to Color(0xFFF59E0B),
    "png" to Color(0xFFF59E0B), "gif" to Color(0xFFF59E0B), "webp" to Color(0xFFF59E0B),
)
private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "svg")
private val VIDEO_EXTS = setOf("mp4", "webm", "ogg", "mov")
private val AUDIO_EXTS = setOf("mp3", "wav", "m4a")
private val WORD_EXTS = setOf("doc", "docx")
private val EXCEL_EXTS = setOf("xls", "xlsx")

internal fun getExt(name: String?): String = (name ?: "").substringAfterLast('.', "").lowercase()

private fun fmtBytes(n: Long): String = when {
    n <= 0 -> ""
    n < 1024 -> "$n B"
    n < 1024 * 1024 -> "%.1f KB".format(n / 1024.0)
    else -> "%.1f MB".format(n / (1024.0 * 1024.0))
}

private fun fmtDateVN(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        // "2026-08-22T10:30:00Z" → "22/8/2026"
        val datePart = iso.substringBefore('T')
        val (y, m, d) = datePart.split("-")
        "${d.toInt()}/${m.toInt()}/$y"
    } catch (e: Exception) {
        ""
    }
}

/**
 * ── TabFiles ──
 * Composable chính, tương đương function TabFiles({dark,...}) trong
 * files-tab.jsx. Tự tải danh sách khi vào màn, có ô tìm kiếm khi > 3 file,
 * và mở overlay xem trước khi bấm 1 file.
 */
@Composable
fun TabFiles(
    dark: Boolean,
    /** presetId hiện tại của nền dùng chung — "custom_image" khi đang bật ảnh nền tự upload,
     *  lúc đó card cần kính mờ để lộ ảnh nền, đồng bộ với các tab khác của Student (Trang chủ/
     *  Thống kê/Lịch sử). Trước đây tab này chưa từng nhận tham số này. */
    bgPresetId: String = "default_light",
    modifier: Modifier = Modifier
) {
    val C = dashboardColors(dark)
    val cardColor = resolvedCardColor(dark, bgPresetId)
    val cardBorderColor = resolvedCardBorderColor(dark, bgPresetId)
    val scope = rememberCoroutineScope()
    val repo = remember { LearningFileRepository() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val offlineCache = remember {
        com.learnsypro.app.data.OfflineCacheStore(context.applicationContext as android.app.Application)
    }

    var files by remember { mutableStateOf<List<LearningFile>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errMsg by remember { mutableStateOf("") }
    var offline by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var openFile by remember { mutableStateOf<LearningFile?>(null) }

    fun fetchFiles() {
        scope.launch {
            loading = true
            errMsg = ""
            offline = false
            try {
                val fresh = repo.fetchFiles()
                files = fresh
                offlineCache.saveLearningFiles(fresh) // luôn cập nhật bản mới nhất từng thấy
            } catch (e: Exception) {
                // Mất mạng hoặc Supabase lỗi — thử lấy lại danh sách đã cache
                // lần tải thành công gần nhất, để tab Tài liệu vẫn dùng được
                // offline (chỉ danh sách tên/loại/mô tả, không phải nội dung).
                val cached = offlineCache.loadLearningFiles()
                if (cached.isNotEmpty()) {
                    files = cached
                    offline = true
                } else {
                    errMsg = "Không tải được danh sách tài liệu. Thử lại nhé!"
                }
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { fetchFiles() }

    val filtered = remember(files, search) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) files
        else files.filter {
            it.title.lowercase().contains(q) ||
                (it.description ?: "").lowercase().contains(q) ||
                (it.subject ?: "").lowercase().contains(q)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DashboardIcon(name = "folder", size = 18.dp, color = C.accent)
                Text(
                    text = if (files.isNotEmpty()) "Tài liệu (${files.size})" else "Tài liệu",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.fg, fontFamily = Baloo2FontFamily
                )
            }
        }

        if (offline) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x1AF59E0B))
                        .border(1.dp, Color(0x40F59E0B), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DashboardIcon(name = "wifiOff", size = 14.dp, color = Color(0xFFB45309))
                    Text(
                        text = "Không có mạng — đang hiện danh sách đã lưu trước đó",
                        fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309), fontFamily = NunitoFontFamily,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (files.size > 3) {
            item {
                FilesSearchBar(search = search, onChange = { search = it }, dark = dark)
            }
        }

        if (loading) {
            items(3) { idx ->
                val pulseAlpha by rememberPulseAlpha()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(70.dp)
                        .graphicsLayer { alpha = pulseAlpha }
                        .clip(RoundedCornerShape(18.dp))
                        .background(cardColor)
                        .border(1.5.dp, cardBorderColor, RoundedCornerShape(18.dp))
                )
            }
        } else if (errMsg.isNotEmpty()) {
            item {
                Text(
                    text = errMsg, color = Color(0xFFEF4444), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(32.dp)
                )
            }
        } else if (filtered.isEmpty()) {
            item {
                val (fadeAlpha, fadeOffsetY) = rememberFadeUpState()
                val floatState = rememberFloatOffset()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = fadeAlpha; translationY = fadeOffsetY }
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.5.dp, cardBorderColor, RoundedCornerShape(18.dp))
                        .padding(vertical = 40.dp, horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier.graphicsLayer {
                            translationY = floatState.translateY.value
                            rotationZ = floatState.rotation.value
                        }
                    ) {
                        DashboardIcon(name = "folder", size = 52.dp, color = Color(0x80F472B6))
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (search.isNotEmpty()) "Không tìm thấy tài liệu" else "Chưa có tài liệu nào",
                        fontSize = 18.sp, fontWeight = FontWeight.Black, color = C.fg, fontFamily = Baloo2FontFamily
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (search.isNotEmpty()) "Thử từ khoá khác nhé~" else "Giáo viên sẽ đăng tài liệu học tập ở đây",
                        fontSize = 13.sp, color = C.sub, fontFamily = NunitoFontFamily
                    )
                }
            }
        } else {
            itemsIndexed(filtered, key = { _, f -> f.id }) { idx, f ->
                FileCard(f = f, dark = dark, staggerIndex = idx, cardColor = cardColor, cardBorderColor = cardBorderColor, onOpen = { openFile = f })
            }
        }
    }

    openFile?.let { f ->
        FilePreviewOverlay(f = f, onClose = { openFile = null })
    }
}

@Composable
private fun FilesSearchBar(search: String, onChange: (String) -> Unit, dark: Boolean) {
    val C = dashboardColors(dark)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(C.inputBg)
            .border(1.5.dp, C.accent.copy(alpha = if (dark) 0.22f else 0.28f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DashboardIcon(name = "search", size = 15.dp, color = C.sub.copy(alpha = 0.7f))
            Box(modifier = Modifier.weight(1f)) {
                if (search.isEmpty()) {
                    Text(text = "Tìm tài liệu...", fontSize = 13.sp, color = C.sub.copy(alpha = 0.6f), fontFamily = NunitoFontFamily)
                }
                BasicTextField(
                    value = search,
                    onValueChange = onChange,
                    textStyle = TextStyle(fontSize = 13.sp, color = C.fg, fontFamily = NunitoFontFamily, fontWeight = FontWeight.SemiBold),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun FileTypeIcon(ext: String, size: androidx.compose.ui.unit.Dp = 22.dp) {
    val col = EXT_COLORS[ext] ?: Color(0xFF9CA3AF)
    val iconName = if (ext in IMAGE_EXTS) "picture" else "file"
    DashboardIcon(name = iconName, size = size, color = col)
}

@Composable
private fun FileCard(f: LearningFile, dark: Boolean, staggerIndex: Int, cardColor: Color? = null, cardBorderColor: Color? = null, onOpen: () -> Unit) {
    val C = dashboardColors(dark)
    val ext = getExt(f.filename.ifBlank { f.title })
    val col = EXT_COLORS[ext] ?: C.accent
    val (fadeAlpha, fadeOffsetY) = rememberFadeUpState(delayMillis = (staggerIndex % 10) * 25)
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.97f else 1f, com.learnsypro.app.ui.theme.OneUiSpring.bouncy, label = "fileCardPress"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = fadeAlpha; translationY = fadeOffsetY; scaleX = pressScale; scaleY = pressScale }
            .clip(RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.card))
            .background(cardColor ?: C.card)
            .border(1.5.dp, cardBorderColor ?: C.cardBorder, RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.card))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(col.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            FileTypeIcon(ext = ext, size = 22.dp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = f.title, fontWeight = FontWeight.Black, fontSize = 13.5.sp, color = C.fg,
                fontFamily = Baloo2FontFamily, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                text = f.description?.ifBlank { "Tài liệu học tập" } ?: "Tài liệu học tập",
                fontSize = 11.5.sp, color = C.sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontFamily = NunitoFontFamily
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = ext.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Black, color = col,
                    fontFamily = NunitoFontFamily
                )
                if (f.size > 0) {
                    Text(text = "· ${fmtBytes(f.size)}", fontSize = 10.sp, color = C.sub.copy(alpha = 0.75f), fontFamily = NunitoFontFamily)
                }
                if (!f.created_at.isNullOrBlank()) {
                    Text(text = "· ${fmtDateVN(f.created_at)}", fontSize = 10.sp, color = C.sub.copy(alpha = 0.75f), fontFamily = NunitoFontFamily)
                }
            }
        }
        DashboardIcon(name = "chevronRight", size = 16.dp, color = C.sub.copy(alpha = 0.6f))
    }
}

/* ── Trạng thái loading/lỗi dùng chung trong khung preview ── */
@Composable
internal fun PreviewLoading(label: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BbSpinner()
        Text(text = label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA06080), fontFamily = NunitoFontFamily)
    }
}

/** Vòng xoay viền hồng nhạt + 1 cạnh đậm — tương đương @keyframes bb-spin .8s linear infinite trong files-tab.jsx */
@Composable
private fun BbSpinner() {
    val rotation by rememberSpinRotation(durationMillis = 800)
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .size(34.dp)
            .graphicsLayer { rotationZ = rotation }
    ) {
        val strokeWidth = 3.dp.toPx()
        drawCircle(
            color = Color(0x2EF472B6),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
        )
        drawArc(
            color = Color(0xFFF472B6),
            startAngle = -90f,
            sweepAngle = 90f,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
    }
}

@Composable
internal fun PreviewError(label: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.size(46.dp).clip(CircleShape).background(Color(0x1AEF4444)),
            contentAlignment = Alignment.Center
        ) {
            DashboardIcon(name = "alertCircle", size = 22.dp, color = Color(0xFFEF4444))
        }
        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B5A68), fontFamily = NunitoFontFamily)
    }
}

/* ── Overlay xem trước file — tương đương FilePreviewOverlay trong files-tab.jsx ── */
@Composable
private fun FilePreviewOverlay(f: LearningFile, onClose: () -> Unit) {
    val ext = getExt(f.filename.ifBlank { f.title })
    val url = f.path

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val (fadeAlpha, fadeOffsetY) = rememberFadeUpState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x80281C3C))
                .padding(vertical = 60.dp, horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .graphicsLayer { alpha = fadeAlpha; translationY = fadeOffsetY }
                    .clip(RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.sheet))
                    .background(Color(0xFAFFF8FC))
                    .border(1.5.dp, Color(0x40FFA0C8), RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.sheet))
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(listOf(Color(0x59FFC8E1), Color(0x40D2BEFF)))
                        )
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FileTypeIcon(ext = ext, size = 28.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = f.title, fontSize = 14.5.sp, fontWeight = FontWeight.Black, color = Color(0xFF2D1420),
                            maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = Baloo2FontFamily
                        )
                        Text(
                            text = "${ext.uppercase()} · Tài liệu học tập", fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                            color = Color(0xFFA06080), fontFamily = NunitoFontFamily
                        )
                    }
                    val context = LocalContext.current
                    val downloadInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    val downloadPressed by downloadInteraction.collectIsPressedAsState()
                    val downloadScale by androidx.compose.animation.core.animateFloatAsState(
                        if (downloadPressed) 0.93f else 1f, com.learnsypro.app.ui.theme.OneUiSpring.bouncy, label = "downloadBtnScale"
                    )
                    Box(
                        modifier = Modifier
                            .graphicsLayer { scaleX = downloadScale; scaleY = downloadScale }
                            .clip(RoundedCornerShape(50))
                            .background(Brush.linearGradient(listOf(Color(0xFFF472B6), Color(0xFFA855F7))))
                            .clickable(interactionSource = downloadInteraction, indication = null) { downloadFile(context, url, f.title, ext) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            DashboardIcon(name = "download", size = 14.dp, color = Color.White)
                            Text(text = "Tải về", fontSize = 12.5.sp, fontWeight = FontWeight.Black, color = Color.White, fontFamily = NunitoFontFamily)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0x1FFF7096))
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center
                    ) {
                        DashboardIcon(name = "close", size = 15.dp, color = Color(0xFFF43F7E))
                    }
                }

                // Preview content
                Box(
                    modifier = Modifier
                        .background(Brush.verticalGradient(listOf(Color(0x80F8F4FF), Color(0x80FFF0F8))))
                        .padding(horizontal = 16.dp, vertical = 18.dp)
                        .heightIn(min = 140.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    when {
                        ext in IMAGE_EXTS -> ImagePreview(url = url)
                        ext == "pdf" -> PdfPreview(url = url)
                        ext in VIDEO_EXTS -> VideoPreview(url = url)
                        ext in AUDIO_EXTS -> AudioPreview(url = url)
                        ext in WORD_EXTS -> DocxPreview(url = url, ext = ext)
                        ext in EXCEL_EXTS -> XlsxPreview(url = url, ext = ext)
                        else -> UnsupportedPreview(ext = ext)
                    }
                }
            }
        }
    }
}

@Composable
private fun UnsupportedPreview(ext: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 44.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(Color(0x1AF472B6)),
            contentAlignment = Alignment.Center
        ) {
            FileTypeIcon(ext = ext, size = 30.dp)
        }
        Text(
            text = "Không thể xem trước loại file này", fontSize = 14.5.sp, color = Color(0xFF6B5A68),
            fontWeight = FontWeight.Black, fontFamily = Baloo2FontFamily
        )
        Text(text = "Tải về để mở nhé!", fontSize = 12.5.sp, color = Color(0xFF9C8695), fontFamily = NunitoFontFamily)
    }
}

/** Nhấp nhô nhẹ liên tục cho icon rỗng — dùng chung rememberFloatOffset() từ theme/Animations.kt (tương đương @keyframes bb-float) */

private fun sanitizeFileName(raw: String): String {
    // DownloadManager có thể ném IllegalArgumentException với tên file chứa
    // ký tự đặc biệt/dấu / trên một số ROM — thay mọi ký tự không phải
    // chữ-số-khoảng trắng-gạch ngang bằng "_", giữ được tiếng Việt có dấu
    // (Unicode chữ cái vẫn hợp lệ trong tên file Android).
    val cleaned = raw.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim()
    return cleaned.ifBlank { "tai_lieu" }
}

private fun downloadFile(context: android.content.Context, url: String, title: String, ext: String) {
    try {
        val safeName = sanitizeFileName(title)
        val fileName = if (ext.isNotBlank() && !safeName.endsWith(".$ext", ignoreCase = true)) {
            "$safeName.$ext"
        } else safeName

        val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
            .setTitle(title)
            .setMimeType(mimeTypeForExt(ext))
            .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        dm.enqueue(request)
        android.widget.Toast.makeText(context, "Đang tải \"$title\" về Downloads...", android.widget.Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        // Không còn nuốt lỗi im lặng — báo rõ cho người dùng biết vì sao nút
        // "Tải về" không phản hồi (thường do tên file/đường dẫn không hợp lệ,
        // hoặc thiếu quyền lưu trữ trên Android cũ).
        android.widget.Toast.makeText(
            context,
            "Không tải được file: ${e.message ?: "lỗi không xác định"}",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
}

private fun mimeTypeForExt(ext: String): String = when (ext.lowercase()) {
    "pdf" -> "application/pdf"
    "doc" -> "application/msword"
    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    "xls" -> "application/vnd.ms-excel"
    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    "ppt" -> "application/vnd.ms-powerpoint"
    "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "mp4" -> "video/mp4"
    "mp3" -> "audio/mpeg"
    "zip" -> "application/zip"
    else -> "application/octet-stream"
}

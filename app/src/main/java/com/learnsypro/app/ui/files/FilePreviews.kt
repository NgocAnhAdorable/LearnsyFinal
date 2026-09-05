@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.learnsypro.app.ui.files

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.learnsypro.app.ui.dashboard.DashboardIcon
import com.learnsypro.app.ui.theme.NunitoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.net.URL

/* ══════════════════════════════════════════════════════════════════════
   Preview theo từng loại file — tương đương ImagePreview / iframe PDF /
   video/audio tag / DocxPreview (mammoth.js) / XlsxPreview (SheetJS)
   trong files-tab.jsx, viết lại bằng công cụ native Android.
════════════════════════════════════════════════════════════════════ */

private sealed class LoadState<out T> {
    object Loading : LoadState<Nothing>()
    data class Error(val message: String) : LoadState<Nothing>()
    data class Ready<T>(val value: T) : LoadState<T>()
}

/**
 * Thư mục lưu bền nội dung file đã từng xem trước (khác `cacheDir` tạm —
 * hệ điều hành có thể tự xoá cacheDir bất kỳ lúc nào để giải phóng dung
 * lượng, khiến "xem offline" chỉ đúng trong phiên đó rồi mất). Đặt trong
 * `filesDir` (bộ nhớ riêng của app, chỉ mất khi gỡ app hoặc người dùng tự
 * xoá dữ liệu app) để lần preview đầu tiên (có mạng) là lần duy nhất cần
 * tải — các lần mở lại sau, kể cả mất mạng, đọc thẳng từ đây.
 */
private fun previewCacheDir(context: android.content.Context): File =
    File(context.filesDir, "learning_files_cache").apply { mkdirs() }

/** Tên file cache ổn định theo URL, giữ nguyên đuôi mở rộng để thư viện đọc file nhận đúng định dạng. */
private fun cacheFileFor(context: android.content.Context, url: String, suffix: String): File {
    val safeKey = url.hashCode().toString()
    return File(previewCacheDir(context), "$safeKey$suffix")
}

/**
 * Lấy file để preview: nếu đã cache từ lần xem trước (dù đang mất mạng) thì
 * dùng lại ngay, không gọi mạng. Nếu chưa có, tải từ URL rồi lưu vào cache
 * bền để lần sau xem offline được.
 */
private suspend fun downloadToCacheFile(context: android.content.Context, url: String, suffix: String): File =
    withContext(Dispatchers.IO) {
        val cached = cacheFileFor(context, url, suffix)
        if (cached.exists() && cached.length() > 0) return@withContext cached
        val tmp = File.createTempFile("ls_preview_", suffix, context.cacheDir)
        URL(url).openStream().use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        // Ghi xong mới move vào vị trí bền, tránh để lại file cache dở dang
        // (0 byte hoặc cắt giữa chừng) nếu tải bị ngắt giữa chừng.
        tmp.copyTo(cached, overwrite = true)
        tmp.delete()
        cached
    }

/**
 * ── ZoomPanBox ──
 * Khung phóng to/di chuyển bằng 2 ngón (pinch-zoom + pan), giới hạn scale
 * 50%–300%, double-tap để reset về 100%. Dùng chung cho ImagePreview và mỗi
 * trang PdfPreview — khác với zoom "vào một chỗ cố định" của ContentScale,
 * người dùng có thể phóng to bất kỳ vùng nào rồi kéo để xem chi tiết.
 */
@Composable
private fun ZoomPanBox(
    modifier: Modifier = Modifier,
    minScale: Float = 0.5f,
    maxScale: Float = 3f,
    content: @Composable () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(minScale, maxScale)
        scale = newScale
        offsetX += panChange.x
        offsetY += panChange.y
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .transformable(state = transformState)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }
        ) {
            content()
        }
    }
}

// ── Ảnh ──
// FIX "xem file offline": trước đây AsyncImage nạp thẳng từ `url` — Coil có
// cache riêng nhưng theo policy bộ nhớ của Coil (LRU, có thể bị dọn bất kỳ
// lúc nào), không đảm bảo ảnh còn khi mất mạng. Giờ dùng chung cơ chế cache
// bền (previewCacheDir) như PDF/Word/Excel: tải 1 lần, các lần mở lại sau —
// kể cả offline — đọc file cục bộ, Coil chỉ decode chứ không cần mạng.
@Composable
fun ImagePreview(url: String) {
    val context = LocalContext.current
    var localFile by remember(url) { mutableStateOf<File?>(null) }
    var status by remember(url) { mutableStateOf("loading") }

    LaunchedEffect(url) {
        status = "loading"
        try {
            val ext = url.substringAfterLast('.', "img").substringBefore('?').take(5)
            localFile = downloadToCacheFile(context, url, ".$ext")
        } catch (e: Exception) {
            // Chưa cache được (lần đầu mất mạng) — vẫn thử để Coil tự nạp
            // thẳng từ url phòng khi có mạng chập chờn nhưng đủ tải ảnh nhỏ.
            localFile = null
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth().height(420.dp),
        contentAlignment = Alignment.Center
    ) {
        when (status) {
            "loading" -> PreviewLoading("Đang tải ảnh...")
            "error" -> PreviewError("Không tải được ảnh.")
        }
        if (status != "error") {
            ZoomPanBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0x26F472B6), RoundedCornerShape(16.dp))
            ) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(context)
                        .data(localFile ?: url)
                        // ORIGINAL: không giới hạn Coil decode theo kích thước Composable
                        // hiển thị — nếu không, ảnh gốc to hơn khung preview sẽ bị Coil
                        // downsample sẵn về đúng khung, và khi ZoomPanBox phóng to lên
                        // 300% sau đó sẽ bị mờ vì không còn đủ pixel gốc để phóng.
                        .size(coil.size.Size.ORIGINAL)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.High,
                    onSuccess = { status = "ok" },
                    onError = { status = "error" },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// ── PDF (android.graphics.pdf.PdfRenderer — render từng trang thành Bitmap) ──
@Composable
fun PdfPreview(url: String) {
    val context = LocalContext.current
    var state by remember(url) { mutableStateOf<LoadState<List<Bitmap>>>(LoadState.Loading) }

    LaunchedEffect(url) {
        state = LoadState.Loading
        try {
            val file = downloadToCacheFile(context, url, ".pdf")
            val bitmaps = withContext(Dispatchers.IO) {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val pages = mutableListOf<Bitmap>()
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    // scale=4: PDF thường render ở ~72dpi gốc; nhân 4 cho ra
                    // ~288dpi. Cần đủ cao vì ZoomPanBox cho phép phóng tới
                    // 300% — nếu bitmap gốc không đủ pixel, phóng to sẽ mờ dù
                    // FilterQuality.High cũng không "tạo thêm" chi tiết được.
                    // Giới hạn cạnh dài nhất ở 2600px để tránh OOM trên PDF
                    // khổ lớn (A3/tranh khổ rộng) — vẫn đủ nét cho điện thoại.
                    val rawScale = 4
                    val maxEdge = 2600
                    val longestEdge = maxOf(page.width, page.height) * rawScale
                    val scale = if (longestEdge > maxEdge) {
                        (maxEdge.toFloat() / maxOf(page.width, page.height)).let { if (it < 1f) 1f else it }
                    } else rawScale.toFloat()
                    val bmp = Bitmap.createBitmap(
                        (page.width * scale).toInt().coerceAtLeast(1),
                        (page.height * scale).toInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888
                    )
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    pages.add(bmp)
                    page.close()
                }
                renderer.close()
                pfd.close()
                pages
            }
            state = LoadState.Ready(bitmaps)
        } catch (e: Exception) {
            state = LoadState.Error("Không đọc được nội dung file PDF.")
        }
    }

    when (val s = state) {
        is LoadState.Loading -> PreviewLoading("Đang tải PDF...")
        is LoadState.Error -> PreviewError(s.message)
        is LoadState.Ready -> {
            val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { s.value.size })
            Column(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(480.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0x26F472B6), RoundedCornerShape(8.dp))
                ) { pageIndex ->
                    ZoomPanBox(modifier = Modifier.fillMaxSize()) {
                        Image(
                            bitmap = s.value[pageIndex].asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            filterQuality = FilterQuality.High,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                if (s.value.size > 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Trang ${pagerState.currentPage + 1}/${s.value.size} — vuốt ngang để chuyển trang",
                        fontSize = 11.5.sp, color = Color(0xFFA06080), fontFamily = NunitoFontFamily,
                        modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

// ── Video (Media3 ExoPlayer) ──
// FIX "xem file offline": trước đây ExoPlayer trỏ thẳng url mạng, mất mạng
// là không phát được dù đã xem trước đó. Giờ tải qua downloadToCacheFile
// (cache bền theo url) trước khi setMediaItem — lần đầu cần mạng, các lần
// sau (kể cả offline) phát thẳng từ file cục bộ.
@Composable
fun VideoPreview(url: String) {
    val context = LocalContext.current
    var localFile by remember(url) { mutableStateOf<File?>(null) }
    var loadError by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        loadError = false
        try {
            val ext = url.substringAfterLast('.', "mp4").substringBefore('?').take(5)
            localFile = downloadToCacheFile(context, url, ".$ext")
        } catch (e: Exception) {
            loadError = true
        }
    }

    if (loadError) {
        PreviewError("Không tải được video. Cần có mạng để xem lần đầu.")
        return
    }
    val file = localFile ?: run { PreviewLoading("Đang tải video..."); return }

    val exoPlayer = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
            prepare()
        }
    }
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .clip(RoundedCornerShape(12.dp))
    )
}

// ── Audio (Media3 ExoPlayer, chỉ nghe) ──
// FIX "xem file offline": cùng lý do VideoPreview — cache bền trước khi phát.
@Composable
fun AudioPreview(url: String) {
    val context = LocalContext.current
    var localFile by remember(url) { mutableStateOf<File?>(null) }
    var loadError by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        loadError = false
        try {
            val ext = url.substringAfterLast('.', "mp3").substringBefore('?').take(5)
            localFile = downloadToCacheFile(context, url, ".$ext")
        } catch (e: Exception) {
            loadError = true
        }
    }

    if (loadError) {
        PreviewError("Không tải được audio. Cần có mạng để nghe lần đầu.")
        return
    }
    val file = localFile ?: run { PreviewLoading("Đang tải audio..."); return }

    val exoPlayer = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
            prepare()
        }
    }
    var isPlaying by remember { mutableStateOf(false) }
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFFF472B6), Color(0xFFA855F7))))
                .clickable {
                    if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                    isPlaying = !isPlaying
                },
            contentAlignment = Alignment.Center
        ) {
            DashboardIcon(name = if (isPlaying) "pause" else "play", size = 26.dp, color = Color.White)
        }
        Text(
            text = if (isPlaying) "Đang phát..." else "Bấm để nghe",
            fontSize = 12.5.sp, color = Color(0xFFA06080), fontFamily = NunitoFontFamily, fontWeight = FontWeight.Bold
        )
    }
}

// ── Word (.doc/.docx) — Apache POI trích văn bản thuần ──
@Composable
fun DocxPreview(url: String, ext: String) {
    val context = LocalContext.current
    var state by remember(url) { mutableStateOf<LoadState<String>>(LoadState.Loading) }

    LaunchedEffect(url) {
        state = LoadState.Loading
        try {
            val file = downloadToCacheFile(context, url, if (ext == "doc") ".doc" else ".docx")
            val text = withContext(Dispatchers.IO) {
                if (ext == "doc") {
                    HWPFDocument(file.inputStream()).use { doc ->
                        WordExtractor(doc).use { it.text }
                    }
                } else {
                    XWPFDocument(file.inputStream()).use { doc ->
                        XWPFWordExtractor(doc).use { it.text }
                    }
                }
            }
            state = LoadState.Ready(text)
        } catch (e: Exception) {
            state = LoadState.Error("Không đọc được nội dung file Word.")
        }
    }

    when (val s = state) {
        is LoadState.Loading -> PreviewLoading("Đang đọc file Word...")
        is LoadState.Error -> PreviewError(s.message)
        is LoadState.Ready -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0x26F472B6), RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                LazyColumn {
                    item {
                        Text(
                            text = s.value.ifBlank { "(Tài liệu trống)" },
                            fontSize = 14.sp, lineHeight = 22.sp, color = Color(0xFF2D1420),
                            fontFamily = NunitoFontFamily
                        )
                    }
                }
            }
        }
    }
}

// ── Excel (.xls/.xlsx) — Apache POI đọc từng sheet thành bảng ──
private data class SheetPreview(val name: String, val rows: List<List<String>>)

@Composable
fun XlsxPreview(url: String, ext: String) {
    val context = LocalContext.current
    var state by remember(url) { mutableStateOf<LoadState<List<SheetPreview>>>(LoadState.Loading) }
    var activeSheet by remember(url) { mutableStateOf(0) }

    LaunchedEffect(url) {
        state = LoadState.Loading
        activeSheet = 0
        try {
            val file = downloadToCacheFile(context, url, if (ext == "xls") ".xls" else ".xlsx")
            val sheets = withContext(Dispatchers.IO) {
                WorkbookFactory.create(file.inputStream()).use { wb ->
                    (0 until wb.numberOfSheets).map { i ->
                        val sheet = wb.getSheetAt(i)
                        val rows = sheet.mapNotNull { row ->
                            row?.map { cell -> cellToString(cell) }
                        }.take(200) // giới hạn để tránh render quá nặng
                        SheetPreview(sheet.sheetName, rows)
                    }
                }
            }
            state = LoadState.Ready(sheets)
        } catch (e: Exception) {
            state = LoadState.Error("Không đọc được nội dung file Excel.")
        }
    }

    when (val s = state) {
        is LoadState.Loading -> PreviewLoading("Đang đọc file Excel...")
        is LoadState.Error -> PreviewError(s.message)
        is LoadState.Ready -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (s.value.size > 1) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        items(s.value.size) { i ->
                            val selected = i == activeSheet
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (selected) Brush.linearGradient(listOf(Color(0xFFF472B6), Color(0xFFA855F7)))
                                        else Brush.linearGradient(listOf(Color(0x1AF472B6), Color(0x1AF472B6)))
                                    )
                                    .clickable { activeSheet = i }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = s.value[i].name, fontSize = 12.sp, fontWeight = FontWeight.Black,
                                    color = if (selected) Color.White else Color(0xFFA06080), fontFamily = NunitoFontFamily
                                )
                            }
                        }
                    }
                }
                val sheet = s.value.getOrNull(activeSheet)
                if (sheet != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .border(1.dp, Color(0x26F472B6), RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        val hScroll = rememberScrollState()
                        LazyColumn(modifier = Modifier.horizontalScroll(hScroll)) {
                            items(sheet.rows) { row ->
                                Row {
                                    row.forEach { cellText ->
                                        Box(
                                            modifier = Modifier
                                                .widthIn(min = 80.dp)
                                                .border(0.5.dp, Color(0x1A000000))
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Text(text = cellText, fontSize = 11.sp, color = Color(0xFF2D1420), fontFamily = NunitoFontFamily)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun cellToString(cell: Cell): String = try {
    when (cell.cellType) {
        CellType.STRING -> cell.stringCellValue
        CellType.NUMERIC -> {
            val d = cell.numericCellValue
            if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
        }
        CellType.BOOLEAN -> cell.booleanCellValue.toString()
        CellType.FORMULA -> try { cell.stringCellValue } catch (e: Exception) { cell.cellFormula }
        else -> ""
    }
} catch (e: Exception) {
    ""
}

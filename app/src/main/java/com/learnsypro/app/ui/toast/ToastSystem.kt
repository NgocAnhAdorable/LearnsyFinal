package com.learnsypro.app.ui.toast

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import com.learnsypro.app.ui.theme.NunitoFontFamily
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// cubic-bezier(.34,1.3,.64,1) / (.55,0,.45,1) — 2 easing gốc của web,
// dùng chung cho MỌI "Dynamic Island" trong app (ScoreIsland, ScoreToastListening, ToastCard).
private val ToastOpenEasing = CubicBezierEasing(0.34f, 1.3f, 0.64f, 1f)
private val ToastCloseEasing = CubicBezierEasing(0.55f, 0f, 0.45f, 1f)
private const val TOAST_OPEN_MS = 550
private const val TOAST_CLOSE_MS = 450

enum class ToastType(val icon: String, val color: Color) {
    SUCCESS("check", Color(0xFF34D399)),
    ERROR("close", Color(0xFFF87171)),
    WARN("sad", Color(0xFFFBBF24)),
    INFO("sparkle", Color(0xFFA78BFA))
}

data class ToastMessage(
    val id: Long,
    val text: String,
    val type: ToastType,
    val durationMillis: Long = 3500,
    val onUndo: (() -> Unit)? = null,
    val onCommit: (() -> Unit)? = null
)

/**
 * ── ToastController (thay window.showToast / showToastWithUndo trong toast.jsx) ──
 * - Auto-detect type từ từ khóa tiếng Việt/Anh trong nội dung (nếu type=null/auto)
 * - Debounce 500ms: cùng nội dung không hiện 2 lần liên tiếp
 * - Tối đa 2 toast cùng lúc, xoá cái cũ nhất (ưu tiên info/success) khi đầy
 * - Toast trùng nội dung đang hiển thị → bỏ qua, không thêm mới
 * - showUndo(): hiện toast có nút Hoàn tác, tự commit sau `delay` nếu không bấm
 */
object ToastController {
    private var nextId = 0L
    private val recentMessages = mutableMapOf<String, Long>()

    private val _toasts = mutableStateListOf<ToastMessage>()
    val toasts: List<ToastMessage> get() = _toasts

    private val dismissJobs = mutableMapOf<Long, Job>()

    fun show(
        message: String,
        type: ToastType? = null,
        durationMillis: Long = 3500,
        scope: CoroutineScope
    ) {
        val cleaned = message.replace(Regex("<svg[\\s\\S]*?</svg>"), "").replace(Regex("\\s{2,}"), " ").trim()
        if (cleaned.isEmpty()) return

        val now = System.currentTimeMillis()
        val lastShown = recentMessages[cleaned] ?: 0L
        if (now - lastShown < 500) return
        recentMessages[cleaned] = now
        if (recentMessages.size > 20) {
            recentMessages.entries.removeAll { now - it.value > 2000 }
        }

        val resolvedType = type ?: detectType(cleaned)

        if (_toasts.any { it.text == cleaned }) return

        if (_toasts.size >= 2) {
            val toRemove = _toasts.firstOrNull { it.type == ToastType.INFO || it.type == ToastType.SUCCESS }
                ?: _toasts.firstOrNull()
            toRemove?.let { dismiss(it.id) }
        }

        val toast = ToastMessage(id = nextId++, text = cleaned, type = resolvedType, durationMillis = durationMillis)
        _toasts.add(toast)

        val job = scope.launch {
            delay(durationMillis)
            dismiss(toast.id)
        }
        dismissJobs[toast.id] = job
    }

    fun showUndo(
        message: String,
        onUndo: () -> Unit,
        onCommit: () -> Unit,
        delayMillis: Long = 5000,
        scope: CoroutineScope
    ) {
        val toast = ToastMessage(
            id = nextId++,
            text = message,
            type = ToastType.WARN,
            durationMillis = delayMillis,
            onUndo = onUndo,
            onCommit = onCommit
        )
        _toasts.add(toast)
        val job = scope.launch {
            delay(delayMillis)
            commitUndo(toast.id)
        }
        dismissJobs[toast.id] = job
    }

    fun cancelUndo(id: Long, scope: CoroutineScope) {
        val toast = _toasts.find { it.id == id } ?: return
        dismissJobs[id]?.cancel()
        dismissJobs.remove(id)
        _toasts.remove(toast)
        toast.onUndo?.invoke()
        show("Đã hoàn tác!", ToastType.SUCCESS, scope = scope)
    }

    fun commitUndo(id: Long) {
        val toast = _toasts.find { it.id == id } ?: return
        dismissJobs[id]?.cancel()
        dismissJobs.remove(id)
        _toasts.remove(toast)
        toast.onCommit?.invoke()
    }

    fun dismiss(id: Long) {
        dismissJobs[id]?.cancel()
        dismissJobs.remove(id)
        _toasts.removeAll { it.id == id }
    }

    private fun detectType(msg: String): ToastType {
        val m = msg.lowercase()
        return when {
            Regex("lỗi|thất bại|không thể|error|failed|từ chối|sai|invalid").containsMatchIn(m) -> ToastType.ERROR
            Regex("cảnh báo|chú ý|warn|vui lòng|thiếu|chưa").containsMatchIn(m) -> ToastType.WARN
            Regex("thành công|đã lưu|đã thêm|đã xóa|đã cập nhật|đã tạo|đã bật|đã tắt|đã mở|đã đóng|đã gửi|đã reset|đã sao chép|ok|hoàn tất|saved|success").containsMatchIn(m) -> ToastType.SUCCESS
            else -> ToastType.INFO
        }
    }
}

/**
 * ── ToastHost ──
 * Đặt ở gốc app (MainActivity, phủ lên NavHost) để hiện toast bất cứ đâu
 * ToastController.show() được gọi. Tối đa 2 toast xếp chồng dọc.
 *
 * Mỗi toast tự quản lý vòng đời hiển thị riêng (mounted/leaving) giống hệt
 * ScoreIsland và ScoreToastListening — khi controller xoá id, item vẫn ở lại
 * UI đủ lâu để chạy xong animation co lại rồi mới thật sự biến mất.
 */
@Composable
fun ToastHost(dark: Boolean, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val liveIds = remember { mutableStateListOf<Long>() }
    val toastById = remember { mutableStateMapOf<Long, ToastMessage>() }

    LaunchedEffect(ToastController.toasts.map { it.id }) {
        val currentIds = ToastController.toasts.map { it.id }.toSet()
        ToastController.toasts.forEach { t -> toastById[t.id] = t }
        currentIds.filterNot { it in liveIds }.forEach { liveIds.add(it) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 50.dp, start = 14.dp, end = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        liveIds.toList().forEach { id ->
            val toast = toastById[id] ?: return@forEach
            val stillInController = ToastController.toasts.any { it.id == id }
            key(id) {
                ToastCard(
                    toast = toast,
                    dark = dark,
                    visible = stillInController,
                    onDismiss = { ToastController.dismiss(id) },
                    onUndoClick = { ToastController.cancelUndo(id, scope) },
                    onCommitClick = { ToastController.commitUndo(id) },
                    onFullyClosed = {
                        liveIds.remove(id)
                        toastById.remove(id)
                    }
                )
            }
        }
    }
}

/**
 * Dynamic Island pill ↔ card. Cùng chuẩn animation với ScoreIsland (quiz) và
 * ScoreToastListening (listening): Animatable + keyframes{} bám đúng chuỗi
 * @keyframes di-pill-in/di-pill-out của web (pop chấm tròn → nảy giãn ra
 * pill lúc mở, easing êm không nảy lúc đóng) — thay cho 1 spring y hệt cho
 * cả 2 chiều của bản trước, vốn khiến hiệu ứng MỞ không hề chạy animation
 * (component chỉ mount đúng lúc target đã là full-size).
 */
@Composable
private fun ToastCard(
    toast: ToastMessage,
    dark: Boolean,
    visible: Boolean,
    onDismiss: () -> Unit,
    onUndoClick: () -> Unit,
    onCommitClick: () -> Unit,
    onFullyClosed: () -> Unit
) {
    val isUndo = toast.onUndo != null
    val bgColor = if (dark) Color(0xF01E0845) else Color(0xF5FFFFFF)

    var mounted by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }

    // Khai báo TRƯỚC early-return để chỉ khởi tạo 1 lần ở trạng thái "chấm
    // tròn đóng" — animateTo() bên dưới mới thực sự chạy animation mở,
    // thay vì Compose khởi tạo thẳng ở giá trị đích (bug của bản cũ).
    val width = remember { Animatable(36f) }
    val height = remember { Animatable(36f) }
    val radius = remember { Animatable(18f) }
    val contentAlpha = remember { Animatable(0f) }
    val contentScale = remember { Animatable(0.85f) }
    val contentOffsetY = remember { Animatable(3f) }

    val fullWidth = 320f
    val fullHeight = 56f
    val fullRadius = 28f

    LaunchedEffect(visible) {
        if (!visible) {
            if (mounted) {
                leaving = true
                delay(450) // đợi hết animation co lại rồi mới gỡ khỏi Column
            }
            mounted = false
            onFullyClosed()
            return@LaunchedEffect
        }
        leaving = false
        mounted = true
    }

    // Chuỗi MỞ — pop chấm tròn ở 20%, rồi nảy giãn ra pill full-size;
    // nội dung chữ trễ tới 40% mới hiện. Y hệt ScoreIsland/ScoreToastListening.
    LaunchedEffect(mounted) {
        if (!mounted) return@LaunchedEffect
        launch {
            width.animateTo(fullWidth, keyframes {
                durationMillis = TOAST_OPEN_MS
                36f at 0
                36f at 110 using ToastOpenEasing
                296f at 302 using ToastOpenEasing
                fullWidth at TOAST_OPEN_MS using ToastOpenEasing
            })
        }
        launch {
            height.animateTo(fullHeight, keyframes {
                durationMillis = TOAST_OPEN_MS
                36f at 0
                36f at 110 using ToastOpenEasing
                52f at 302 using ToastOpenEasing
                fullHeight at TOAST_OPEN_MS using ToastOpenEasing
            })
        }
        launch {
            radius.animateTo(fullRadius, keyframes {
                durationMillis = TOAST_OPEN_MS
                18f at 0
                18f at 110 using ToastOpenEasing
                26f at 302 using ToastOpenEasing
                fullRadius at TOAST_OPEN_MS using ToastOpenEasing
            })
        }
        launch {
            contentAlpha.animateTo(1f, keyframes {
                durationMillis = TOAST_OPEN_MS
                0f at 0
                0f at 220
                1f at TOAST_OPEN_MS using ToastOpenEasing
            })
        }
        launch {
            contentScale.animateTo(1f, keyframes {
                durationMillis = TOAST_OPEN_MS
                0.85f at 0
                0.85f at 220
                1f at TOAST_OPEN_MS using ToastOpenEasing
            })
        }
        launch {
            contentOffsetY.animateTo(0f, keyframes {
                durationMillis = TOAST_OPEN_MS
                3f at 0
                3f at 220
                0f at TOAST_OPEN_MS using ToastOpenEasing
            })
        }
    }

    // Chuỗi ĐÓNG — easing êm không nảy, y hệt ScoreIsland/ScoreToastListening.
    LaunchedEffect(leaving) {
        if (!leaving) return@LaunchedEffect
        launch {
            width.animateTo(36f, keyframes {
                durationMillis = TOAST_CLOSE_MS
                fullWidth at 0
                296f at 180 using ToastCloseEasing
                36f at 337 using ToastCloseEasing
                36f at TOAST_CLOSE_MS
            })
        }
        launch {
            height.animateTo(36f, keyframes {
                durationMillis = TOAST_CLOSE_MS
                fullHeight at 0
                52f at 180 using ToastCloseEasing
                36f at 337 using ToastCloseEasing
                36f at TOAST_CLOSE_MS
            })
        }
        launch {
            radius.animateTo(18f, keyframes {
                durationMillis = TOAST_CLOSE_MS
                fullRadius at 0
                26f at 180 using ToastCloseEasing
                18f at 337 using ToastCloseEasing
                18f at TOAST_CLOSE_MS
            })
        }
        launch {
            contentAlpha.animateTo(0f, keyframes {
                durationMillis = 160
                1f at 0
                0f at 160 using ToastCloseEasing
            })
        }
    }

    if (!mounted) return

    Box(
        modifier = Modifier
            .width(width.value.dp)
            .height(height.value.dp)
            .clip(RoundedCornerShape(radius.value.dp))
            .background(bgColor)
            .border(1.5.dp, toast.type.color.copy(alpha = 0.25f), RoundedCornerShape(radius.value.dp))
            .clickable(enabled = !isUndo) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        // Luôn giữ Row trong composition, chỉ ẩn/hiện bằng alpha/scale + ngưỡng
        // width — mount/unmount đột ngột giữa lúc đang animate mới gây giật.
        if (width.value > 60f) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = contentAlpha.value
                        scaleX = contentScale.value
                        scaleY = contentScale.value
                        translationY = contentOffsetY.value.dp.toPx()
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DashboardIcon(name = toast.type.icon, size = 18.dp, color = toast.type.color)

                Text(
                    text = toast.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (dark) Color(0xFFF0DCE8) else Color(0xFF3A1830),
                    fontFamily = NunitoFontFamily,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (isUndo) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(toast.type.color.copy(alpha = 0.15f))
                            .clickable { onUndoClick() }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "Hoàn tác", fontSize = 11.sp, fontWeight = FontWeight.Black, color = toast.type.color, fontFamily = NunitoFontFamily)
                    }
                }

                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable { if (isUndo) onCommitClick() else onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    DashboardIcon(name = "close", size = 10.dp, color = if (dark) Color(0x80FFFFFF) else Color(0x66000000))
                }
            }
        }
    }
}

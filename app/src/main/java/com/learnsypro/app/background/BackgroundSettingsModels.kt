package com.learnsypro.app.background

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * ── Background Settings Models (dùng chung Student / Admin / File Manager) ──
 * Trước đây nằm trong ui.dashboard, chỉ Student dùng. Chuyển sang package
 * `background` riêng (không phụ thuộc ui.dashboard) để Admin (Compose) và
 * File Manager (View-based) đều import được mà không kéo theo toàn bộ
 * dependency của dashboard.
 *
 * Khác bản web:
 * - imageUrl thay cho imageDataUrl (base64) — ảnh nền upload lên Supabase
 *   Storage giống avatar, chỉ lưu URL (xem BackgroundSettingsRepository).
 * - blurMode 'off' (tắt hẳn nền, không phải mờ) vẫn giữ tên gốc cho khớp
 *   logic dark-mode-lock, dù dễ nhầm là 1 mức blur.
 */
@Serializable
data class BgSettings(
    val presetId: String = "default_light",
    val blurMode: String = "none", // "none" | "blur50" | "blur85" | "blur100" | "off" | "custom"
    val blurPercent: Int = 0,
    val imageUrl: String? = null
)

/**
 * Key đồng bộ dùng chung cho cả 3 module (Student/Admin/FileManager) — thay
 * vì khoá theo studentId như trước (mỗi học sinh 1 nền riêng, Admin/FileManager
 * không thấy). Nền giờ là 1 cấu hình duy nhất cho toàn app trên máy đó.
 */
const val SHARED_BG_KEY = "shared"

data class BgPreset(
    val id: String,
    val label: String,
    val isImage: Boolean,
    val gradient: Brush? = null
)

/** Gradient chính xác theo mã màu CSS trong bản gốc, giữ đúng góc 135deg. */
val BG_PRESETS: List<BgPreset> = listOf(
    BgPreset("default_light", "Hồng nhạt", false, Brush.linearGradient(listOf(Color(0xFFFFF5F9), Color(0xFFFCE7F3), Color(0xFFF0F4FF), Color(0xFFFDF2FB)))),
    BgPreset("default_dark", "Tím đêm", false, Brush.linearGradient(listOf(Color(0xFF120009), Color(0xFF1A0515), Color(0xFF0D0020), Color(0xFF160A1A)))),
    BgPreset("sunset", "Hoàng hôn", false, Brush.linearGradient(listOf(Color(0xFFFFECD2), Color(0xFFFCB69F), Color(0xFFFF9A9E), Color(0xFFFECFEF)))),
    BgPreset("ocean", "Đại dương", false, Brush.linearGradient(listOf(Color(0xFFA8EDEA), Color(0xFFFED6E3), Color(0xFFA8C8FA)))),
    BgPreset("forest", "Rừng xanh", false, Brush.linearGradient(listOf(Color(0xFFD4FC79), Color(0xFF96E6A1), Color(0xFF84FAB0)))),
    BgPreset("lavender", "Tím oải", false, Brush.linearGradient(listOf(Color(0xFFE9D5FF), Color(0xFFDDD6FE), Color(0xFFC4B5FD)))),
    BgPreset("peach", "Đào phấn", false, Brush.linearGradient(listOf(Color(0xFFFFEAA7), Color(0xFFFAB1A0), Color(0xFFFD79A8)))),
    BgPreset("midnight", "Đêm xanh", false, Brush.linearGradient(listOf(Color(0xFF0F0C29), Color(0xFF302B63), Color(0xFF24243E)))),
    BgPreset("rose_gold", "Vàng hồng", false, Brush.linearGradient(listOf(Color(0xFFF8B4D9), Color(0xFFFCD3A4), Color(0xFFFDE68A)))),
    BgPreset("aurora", "Cực quang", false, Brush.linearGradient(listOf(Color(0xFF43E97B), Color(0xFF38F9D7), Color(0xFF667EEA), Color(0xFF764BA2)))),
    BgPreset("custom_image", "Ảnh của bạn", true)
)

/** Preset "sáng" — tự swap sang default_dark khi bật dark mode. */
val LIGHT_PRESET_IDS: Set<String> = setOf(
    "default_light", "sunset", "ocean", "forest", "lavender", "peach", "rose_gold", "aurora"
)

data class BlurModeOption(val id: String, val label: String)

val BLUR_MODES: List<BlurModeOption> = listOf(
    BlurModeOption("none", "Không mờ"),
    BlurModeOption("blur50", "Mờ 50%"),
    BlurModeOption("blur85", "Mờ 85%"),
    BlurModeOption("blur100", "Mờ 100%"),
    BlurModeOption("off", "Tắt nền")
)

/** Quy đổi mode cũ sang % — tương thích ngược, giống legacyModeToPercent(). */
fun legacyModeToPercent(mode: String): Int = when (mode) {
    "blur50" -> 50
    "blur85" -> 85
    "blur100" -> 100
    else -> 0
}

private val BLUR_DP_ANCHORS = listOf(0 to 0f, 50 to 9f, 85 to 22f, 100 to 28f)
private val DIM_LIGHT_ANCHORS = listOf(0 to 0f, 50 to 0.18f, 85 to 0.45f, 100 to 0.62f)
private val DIM_DARK_ANCHORS = listOf(0 to 0.32f, 50 to 0.42f, 85 to 0.72f, 100 to 0.86f)

/** Nội suy tuyến tính theo mốc (%, giá trị) — tương đương interp() trong bản gốc. */
private fun interp(anchors: List<Pair<Int, Float>>, percent: Int): Float {
    val p = max(0, min(100, percent))
    for (i in 0 until anchors.size - 1) {
        val (x0, y0) = anchors[i]
        val (x1, y1) = anchors[i + 1]
        if (p in x0..x1) {
            val t = (p - x0).toFloat() / (x1 - x0).toFloat()
            return y0 + t * (y1 - y0)
        }
    }
    return anchors.last().second
}

fun blurDpForPercent(percent: Int): Float = interp(BLUR_DP_ANCHORS, percent)
fun dimAlphaLight(percent: Int): Float = interp(DIM_LIGHT_ANCHORS, percent)
fun dimAlphaDark(percent: Int): Float = interp(DIM_DARK_ANCHORS, percent)

fun clampPercent(value: Int): Int = max(0, min(100, value))
fun clampPercent(value: Float): Int = clampPercent(value.roundToInt())

fun blurIconName(modeId: String): String = when (modeId) {
    "blur50" -> "cloud"
    "blur85" -> "cloud"
    "blur100" -> "cloud"
    "off" -> "eyeOff"
    else -> "sun"
}

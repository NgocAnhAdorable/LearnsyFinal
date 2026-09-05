package com.learnsypro.app.ui.dashboard

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bảng màu riêng của Dashboard — tương đương const CL / CD trong dashboard.jsx.
 * Khác với Theme.kt (màu tổng của app), bảng này có thêm card/border/tag riêng
 * cho các thành phần trong màn hình Dashboard.
 */
data class DashboardColors(
    val fg: Color,
    val sub: Color,
    val card: Color,
    val cardBorder: Color,
    val inputBg: Color,
    val navBg: Color,
    val accent: Color,
    val accent2: Color,
    val danger: Color,
    val tagBg: Color
)

val DashboardColorsLight = DashboardColors(
    fg = Color(0xFF2D1420),
    sub = Color(0xFFA06080),
    card = Color(0xD1FFFFFF),          // rgba(255,255,255,0.82)
    cardBorder = Color(0x59FFB6D2),    // rgba(255,182,210,0.35)
    inputBg = Color(0xE6FFFFFF),       // rgba(255,255,255,0.9)
    navBg = Color(0xF5FFF5FA),         // rgba(255,245,250,0.96)
    accent = Color(0xFFF472B6),
    accent2 = Color(0xFFA855F7),
    danger = Color(0xFFEF4444),
    tagBg = Color(0x1FF472B6)          // rgba(244,114,182,0.12)
)

val DashboardColorsDark = DashboardColors(
    fg = Color(0xFFFCE4F0),
    sub = Color(0x9EFFC8DC),           // rgba(255,200,220,0.62)
    card = Color(0x12FFFFFF),          // rgba(255,255,255,0.07)
    cardBorder = Color(0x33F472B6),    // rgba(244,114,182,0.2)
    inputBg = Color(0x14FFFFFF),       // rgba(255,255,255,0.08)
    navBg = Color(0xF5140609),         // rgba(20,6,15,0.96)
    accent = Color(0xFFF472B6),
    accent2 = Color(0xFFC084FC),
    danger = Color(0xFFF87171),
    tagBg = Color(0x24F472B6)          // rgba(244,114,182,0.14)
)

fun dashboardColors(dark: Boolean): DashboardColors =
    if (dark) DashboardColorsDark else DashboardColorsLight

/**
 * Màu card "kính mờ" — nhạt hơn hẳn so với card thường (dùng khi người dùng đang bật ảnh
 * nền tự upload, để card không che kín ảnh/blur phía sau, tạo hiệu ứng glassmorphism).
 *
 * Không dùng chung 1 mức alpha cho mọi trường hợp: card thường (preset gradient có sẵn) vẫn
 * cần khá đặc để chữ dễ đọc trên nền gradient tương phản thấp — chỉ hạ alpha mạnh khi phía
 * sau THỰC SỰ là ảnh đã blur, lúc đó lớp dim của BackgroundLayer đã lo phần tương phản rồi,
 * card chỉ cần đủ mờ để thấy ảnh nền, không cần tự gánh hết việc tương phản nữa.
 */
val DashboardGlassCardLight = Color(0x8FFFFFFF)   // ~56% trắng — thấy rõ ảnh nền qua card
val DashboardGlassCardDark = Color(0x1AFFFFFF)    // ~10% trắng — giữ đúng tinh thần card dark hiện tại, chỉ nhỉnh nhẹ
val DashboardGlassCardBorderLight = Color(0x66FFFFFF)
val DashboardGlassCardBorderDark = Color(0x40F472B6)

/** true nếu đang bật ảnh nền tự upload — nơi duy nhất card cần chuyển sang màu kính mờ. */
fun isCustomImageBackground(presetId: String): Boolean = presetId == "custom_image"

/**
 * Trả về màu card phù hợp theo trạng thái nền hiện tại — dùng thay cho C.card trực tiếp ở
 * mọi nơi cần hiệu ứng "card mờ thấy nền" (QuickStatCard, LessonCard, hero banner...).
 */
fun resolvedCardColor(dark: Boolean, bgPresetId: String): Color {
    val c = dashboardColors(dark)
    return if (isCustomImageBackground(bgPresetId)) {
        if (dark) DashboardGlassCardDark else DashboardGlassCardLight
    } else {
        c.card
    }
}

fun resolvedCardBorderColor(dark: Boolean, bgPresetId: String): Color {
    val c = dashboardColors(dark)
    return if (isCustomImageBackground(bgPresetId)) {
        if (dark) DashboardGlassCardBorderDark else DashboardGlassCardBorderLight
    } else {
        c.cardBorder
    }
}

// ═══ Gradient chính dùng nhiều nơi (nút, badge, progress...) ═══
val AccentGradient = Brush.linearGradient(
    colors = listOf(Color(0xFFF472B6), Color(0xFFA855F7))
)

// ═══ HELPERS — tương đương fmtDate/fmtTime/pctColor/pctLabel/rankBadge/toScore/fmtScore ═══

private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN"))
private val timeFormatter = SimpleDateFormat("HH:mm", Locale("vi", "VN"))

fun fmtDate(ts: Long?): String {
    if (ts == null || ts == 0L) return "—"
    return dateFormatter.format(Date(ts))
}

fun fmtTime(ts: Long?): String {
    if (ts == null || ts == 0L) return ""
    return timeFormatter.format(Date(ts))
}

fun pctColor(p: Double): Color = when {
    p >= 90 -> Color(0xFF10B981)
    p >= 70 -> Color(0xFFF59E0B)
    p >= 50 -> Color(0xFFF472B6)
    else -> Color(0xFFEF4444)
}

fun pctLabel(p: Double): String = when {
    p >= 90 -> "Xuất sắc"
    p >= 70 -> "Giỏi lắm"
    p >= 50 -> "Cố lên nha"
    else -> "Thử lại nhé"
}

data class RankBadge(
    val icon: String,
    val label: String,
    val color: Color,
    val glow: Color,
    val gradient: Brush
)

fun rankBadge(pct: Double): RankBadge = when {
    pct >= 95 -> RankBadge(
        icon = "trophy", label = "Vàng",
        color = Color(0xFFF59E0B), glow = Color(0x66F59E0B),
        gradient = Brush.linearGradient(listOf(Color(0xFFFDE68A), Color(0xFFF59E0B), Color(0xFFD97706)))
    )
    pct >= 80 -> RankBadge(
        icon = "medal", label = "Bạc",
        color = Color(0xFF94A3B8), glow = Color(0x6694A3B8),
        gradient = Brush.linearGradient(listOf(Color(0xFFE2E8F0), Color(0xFF94A3B8), Color(0xFF64748B)))
    )
    pct >= 60 -> RankBadge(
        icon = "ribbon", label = "Đồng",
        color = Color(0xFFCD7C4B), glow = Color(0x66CD7C4B),
        gradient = Brush.linearGradient(listOf(Color(0xFFFED7AA), Color(0xFFCD7C4B), Color(0xFF92400E)))
    )
    else -> RankBadge(
        icon = "star", label = "Tập sự",
        color = Color(0xFF34D399), glow = Color(0x6634D399),
        gradient = Brush.linearGradient(listOf(Color(0xFFA7F3D0), Color(0xFF34D399), Color(0xFF059669)))
    )
}

/** Điểm thang 10, ví dụ pct=85.0 -> 8.5 */
fun toScore(pct: Double): Double = Math.round(pct / 10.0 * 10) / 10.0

/** Format điểm: số nguyên thì bỏ .0, ví dụ 8.0 -> "8", 8.5 -> "8.5" */
fun fmtScore(pct: Double): String {
    val s = toScore(pct)
    return if (s % 1.0 == 0.0) s.toInt().toString() else String.format(Locale.US, "%.1f", s)
}

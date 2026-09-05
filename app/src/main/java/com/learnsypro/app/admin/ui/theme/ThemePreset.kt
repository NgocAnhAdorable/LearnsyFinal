package com.learnsypro.app.admin.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Tương đương THEMES array trong themes.jsx (Mặc định/Princess/Minty Chill/Galaxy).
// Chỉ đổi accent (rose/lav gradient) — các màu nền/text khác giữ theo Light/DarkColors.
enum class ThemePreset(val label: String, val dotColor: Color, val gradStart: Color, val gradEnd: Color) {
    DEFAULT("Mặc định 🌸", Color(0xFFD946EF), Color(0xFFF472B6), Color(0xFFA855F7)),
    PRINCESS("Princess 👑", Color(0xFFE91E8C), Color(0xFFE91E8C), Color(0xFFD4A017)),
    MINTY("Minty Chill 🍃", Color(0xFF10B981), Color(0xFF10B981), Color(0xFF06B6D4)),
    GALAXY("Galaxy 🌌", Color(0xFF818CF8), Color(0xFFE879F9), Color(0xFF818CF8));

    fun gradient(): Brush = Brush.linearGradient(listOf(gradStart, gradEnd))
}

// Áp preset lên LearnsyColors — chỉ thay grad/rose/lav, giữ nguyên phần còn lại của theme.
fun LearnsyColors.withPreset(preset: ThemePreset): LearnsyColors = when (preset) {
    ThemePreset.DEFAULT -> this
    else -> this.copy(
        rose = preset.gradStart,
        lav = preset.gradEnd,
        grad = preset.gradient()
    )
}

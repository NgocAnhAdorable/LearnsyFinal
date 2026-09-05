package com.learnsypro.app.admin.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Tương đương CL (light) trong app.jsx
data class LearnsyColors(
    val bg: Color, val bg2: Color, val surface: Color,
    val rose: Color, val rose2: Color, val roseL: Color, val rosePale: Color,
    val lav: Color, val lav2: Color, val lavL: Color, val lavPale: Color,
    val mint: Color, val mint2: Color, val mintL: Color,
    val peach: Color, val peachL: Color, val peach2: Color,
    val text: Color, val text2: Color, val text3: Color, val text4: Color,
    val border: Color, val border2: Color,
    val grad: Brush, val gradSoft: Brush
)

val LightColors = LearnsyColors(
    bg = Color(0xFFFFF5F9), bg2 = Color(0xFFFEF0F7), surface = Color(0xFFFFFFFF),
    rose = Color(0xFFFF6B95), rose2 = Color(0xFFFF8FAF), roseL = Color(0xFFFFE4ED), rosePale = Color(0xFFFFF0F5),
    lav = Color(0xFFA855F7), lav2 = Color(0xFFC084FC), lavL = Color(0xFFF0E6FF), lavPale = Color(0xFFFAF5FF),
    mint = Color(0xFF10B981), mint2 = Color(0xFF6EE7B7), mintL = Color(0xFFECFDF5),
    peach = Color(0xFFF97316), peachL = Color(0xFFFFF7ED), peach2 = Color(0xFFFED7AA),
    text = Color(0xFF3D1830), text2 = Color(0xFF6B3050), text3 = Color(0xFFA07090), text4 = Color(0xFFC8A0B8),
    border = Color(0xFFF5D5E8), border2 = Color(0xFFE8DCFF),
    grad = Brush.linearGradient(listOf(Color(0xFFF472B6), Color(0xFFA855F7))),
    gradSoft = Brush.linearGradient(listOf(Color(0xFFFFDDED), Color(0xFFEDE9FE)))
)

val DarkColors = LearnsyColors(
    bg = Color(0xFF180A10), bg2 = Color(0xFF1E0D15), surface = Color(0xFF261018),
    rose = Color(0xFFFF6B95), rose2 = Color(0xFFFF8FAF), roseL = Color(0xFF3A0F22), rosePale = Color(0xFF2D0A1A),
    lav = Color(0xFFC084FC), lav2 = Color(0xFFD8A8FF), lavL = Color(0xFF2A1040), lavPale = Color(0xFF200C35),
    mint = Color(0xFF10B981), mint2 = Color(0xFF6EE7B7), mintL = Color(0xFF0A2618),
    peach = Color(0xFFFB923C), peachL = Color(0xFF2A1208), peach2 = Color(0xFF7A3810),
    text = Color(0xFFF0DCE8), text2 = Color(0xFFC898B8), text3 = Color(0xFF8A6080), text4 = Color(0xFF8A6080),
    border = Color(0xFF421526), border2 = Color(0xFF34104E),
    grad = Brush.linearGradient(listOf(Color(0xFFF472B6), Color(0xFFA855F7))),
    gradSoft = Brush.linearGradient(listOf(Color(0xFF3A0F22), Color(0xFF2A1040)))
)

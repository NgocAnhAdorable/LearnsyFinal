package com.learnsypro.app.admin.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
// R class thuộc namespace THẬT của module app (com.learnsypro.app) — cả 2 nguồn
// (Student gốc + Admin gộp vào) build ra CÙNG 1 module Android duy nhất nên chỉ có
// 1 class R.
import com.learnsypro.app.R

// Tương đương font Nunito trong admin.html/ui-components.jsx (@import Google Fonts).
// 5 file .ttf thật đặt ở res/font/, nối theo đúng weight tương ứng.
val LearnsyFontFamily = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
    Font(R.font.nunito_black, FontWeight.Black)
)

// Baloo2 — dùng cho chữ "Learnsy" trên logo header (đồng bộ app học sinh).
val Baloo2FontFamily = FontFamily(
    Font(R.font.baloo2_medium, FontWeight.Medium),
    Font(R.font.baloo2_semibold, FontWeight.SemiBold),
    Font(R.font.baloo2_extrabold, FontWeight.ExtraBold),
    Font(R.font.baloo2_bold, FontWeight.Black)
)

val LearnsyTypography = Typography(
    displayLarge = TextStyle(fontFamily = LearnsyFontFamily, fontWeight = FontWeight.Black),
    headlineLarge = TextStyle(fontFamily = LearnsyFontFamily, fontWeight = FontWeight.Black),
    titleLarge = TextStyle(fontFamily = LearnsyFontFamily, fontWeight = FontWeight.ExtraBold),
    bodyLarge = TextStyle(fontFamily = LearnsyFontFamily, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontFamily = LearnsyFontFamily, fontWeight = FontWeight.Bold)
)

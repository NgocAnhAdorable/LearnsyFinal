package com.learnsypro.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import com.learnsypro.app.R

// ═══ Bảng màu lấy từ gradient CSS gốc (.logo-learnsy) ═══
val PinkPrimary = Color(0xFFF472B6)
val PurplePrimary = Color(0xFFA855F7)
val IndigoAccent = Color(0xFF6366F1)
val CyanAccent = Color(0xFF06B6D4)
val GreenAccent = Color(0xFF10B981)

// Màu nền / bề mặt — Light mode (tương đương body::before trong CSS gốc)
val LightBackground = Color(0xFFFFF8FB)
val LightSurface = Color(0xFFFFFFFF)
val LightOnBackground = Color(0xFF2A1233)

// Màu nền / bề mặt — Dark mode (tương đương body.dark::before trong CSS gốc)
val DarkBackground = Color(0xFF120330)
val DarkSurface = Color(0xFF1E0845)
val DarkOnBackground = Color(0xFFF5E9FF)

// Scrollbar thumb tương đương (không dùng trực tiếp trong Compose, giữ để tham chiếu UI list)
val ScrollThumbLight = Color(0x4DFF8CAA)
val ScrollThumbDark = Color(0x40C85078)

private val LearnsyLightColors = lightColorScheme(
    primary = PinkPrimary,
    secondary = PurplePrimary,
    tertiary = IndigoAccent,
    background = LightBackground,
    surface = LightSurface,
    onBackground = LightOnBackground,
    onSurface = LightOnBackground
)

private val LearnsyDarkColors = darkColorScheme(
    primary = PinkPrimary,
    secondary = PurplePrimary,
    tertiary = CyanAccent,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = DarkOnBackground,
    onSurface = DarkOnBackground
)

// ═══ Font family ═══
// Font Nunito/Dancing Script thật — cần các file .ttf trong res/font/:
//   nunito_regular.ttf, nunito_semibold.ttf, nunito_bold.ttf,
//   nunito_extrabold.ttf, nunito_black.ttf
//   dancing_script_bold.ttf, dancing_script_extrabold.ttf, dancing_script_black.ttf
val NunitoFontFamily = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
    Font(R.font.nunito_black, FontWeight.Black)
)

val DancingScriptFontFamily = FontFamily(
    Font(R.font.dancing_script_bold, FontWeight.Bold),
    Font(R.font.dancing_script_extrabold, FontWeight.ExtraBold),
    Font(R.font.dancing_script_extrabold, FontWeight.Black)
)

// Font chính của giao diện web gốc (mọi nơi web dùng fontFamily:"'Baloo 2',cursive").
// Cần các file .ttf trong res/font/: baloo2_medium.ttf, baloo2_semibold.ttf,
// baloo2_bold.ttf, baloo2_extrabold.ttf (tải từ Google Fonts: Baloo 2).
val Baloo2FontFamily = FontFamily(
    Font(R.font.baloo2_medium, FontWeight.Medium),
    Font(R.font.baloo2_semibold, FontWeight.SemiBold),
    Font(R.font.baloo2_bold, FontWeight.Bold),
    Font(R.font.baloo2_extrabold, FontWeight.ExtraBold)
)

/**
 * Theme chính của app Learnsy.
 * isDarkTheme: đọc từ DataStore (xem DarkModePreference trong MainActivity.kt)
 * tương đương logic đọc localStorage.getItem('learnsy_dark') trong index.html gốc.
 */
@Composable
fun LearnsyTheme(
    isDarkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = if (isDarkTheme) LearnsyDarkColors else LearnsyLightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Đồng bộ màu status bar + navigation bar với nền app
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            // Light status bar = icon tối (dùng khi nền sáng)
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDarkTheme
                isAppearanceLightNavigationBars = !isDarkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}

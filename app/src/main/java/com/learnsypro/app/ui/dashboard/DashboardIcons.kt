package com.learnsypro.app.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.foundation.Image

/**
 * ── Thư viện Icon cho Dashboard ──
 * Tương đương const Icons={...} + function Icon() trong dashboard.jsx.
 *
 * Mỗi icon là 24x24 viewport (giống SVG gốc), stroke-based (fill=none,
 * strokeWidth=2), viền tròn — dùng ImageVector.Builder để build gần đúng
 * path gốc. Với các icon phức tạp hơn (learnsy, dice, feather...) có thể
 * bổ sung dần khi cần — hiện tại đã phủ các icon dùng nhiều nhất:
 * home, stats, history, settings, sun, moon, book, lock, search, shuffle,
 * check, star, trophy, fire, trending, target, logout, sparkle, heart.
 *
 * Cách dùng:
 *   DashboardIcon(name = "trophy", size = 22.dp, color = Color(0xFFF59E0B))
 */
object DashboardIconPaths {

    fun home(): ImageVector = Builder(
        name = "home", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(3f, 9.5f); lineTo(12f, 3f); lineTo(21f, 9.5f)
            lineTo(21f, 21f)
            curveTo(21f, 21.5523f, 20.5523f, 22f, 20f, 22f)
            lineTo(4f, 22f)
            curveTo(3.4477f, 22f, 3f, 21.5523f, 3f, 21f)
            close()
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(9f, 22f); lineTo(9f, 12f); lineTo(15f, 12f); lineTo(15f, 22f)
        }
    }.build()

    fun star(): ImageVector = Builder(
        name = "star", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
            moveTo(12f, 2f); lineTo(15.09f, 8.26f); lineTo(22f, 9.27f)
            lineTo(17f, 14.14f); lineTo(18.18f, 21.02f); lineTo(12f, 17.77f)
            lineTo(5.82f, 21.02f); lineTo(7f, 14.14f); lineTo(2f, 9.27f)
            lineTo(8.91f, 8.26f); close()
        }
    }.build()

    fun heart(): ImageVector = Builder(
        name = "heart", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
            moveTo(20.84f, 4.61f)
            curveTo(19.32f, 3.04f, 17.06f, 2.61f, 15.06f, 3.61f)
            curveTo(13.9f, 4.19f, 12.9f, 5.1f, 12f, 5.67f)
            curveTo(11.1f, 5.1f, 10.1f, 4.19f, 8.94f, 3.61f)
            curveTo(6.94f, 2.61f, 4.68f, 3.04f, 3.16f, 4.61f)
            curveTo(1.11f, 6.72f, 1.11f, 10.1f, 3.16f, 12.22f)
            lineTo(4.22f, 13.28f)
            lineTo(12f, 21.23f)
            lineTo(19.78f, 13.28f)
            lineTo(20.84f, 12.22f)
            curveTo(22.89f, 10.1f, 22.89f, 6.72f, 20.84f, 4.61f)
            close()
        }
    }.build()

    fun sparkle(): ImageVector = Builder(
        name = "sparkle", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
            moveTo(12f, 0f); lineTo(14.59f, 9.41f); lineTo(24f, 12f)
            lineTo(14.59f, 14.59f); lineTo(12f, 24f); lineTo(9.41f, 14.59f)
            lineTo(0f, 12f); lineTo(9.41f, 9.41f); close()
        }
    }.build()

    fun search(): ImageVector = Builder(
        name = "search", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            // Vòng tròn kính lúp
            moveTo(19f, 11f)
            curveTo(19f, 15.4183f, 15.4183f, 19f, 11f, 19f)
            curveTo(6.5817f, 19f, 3f, 15.4183f, 3f, 11f)
            curveTo(3f, 6.5817f, 6.5817f, 3f, 11f, 3f)
            curveTo(15.4183f, 3f, 19f, 6.5817f, 19f, 11f)
            close()
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(21f, 21f); lineTo(16.65f, 16.65f)
        }
    }.build()

    fun check(): ImageVector = Builder(
        name = "check", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2.5f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(20f, 6f); lineTo(9f, 17f); lineTo(4f, 12f)
        }
    }.build()

    fun calendar(): ImageVector = Builder(
        name = "calendar", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(5f, 4f)
            lineTo(19f, 4f)
            curveTo(20.1046f, 4f, 21f, 4.8954f, 21f, 6f)
            lineTo(21f, 20f)
            curveTo(21f, 21.1046f, 20.1046f, 22f, 19f, 22f)
            lineTo(5f, 22f)
            curveTo(3.8954f, 22f, 3f, 21.1046f, 3f, 20f)
            lineTo(3f, 6f)
            curveTo(3f, 4.8954f, 3.8954f, 4f, 5f, 4f)
            close()
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(8f, 2f); lineTo(8f, 6f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(16f, 2f); lineTo(16f, 6f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(3f, 10f); lineTo(21f, 10f)
        }
    }.build()

    fun clock(): ImageVector = Builder(
        name = "clock", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(22f, 12f)
            curveTo(22f, 17.5228f, 17.5228f, 22f, 12f, 22f)
            curveTo(6.4772f, 22f, 2f, 17.5228f, 2f, 12f)
            curveTo(2f, 6.4772f, 6.4772f, 2f, 12f, 2f)
            curveTo(17.5228f, 2f, 22f, 6.4772f, 22f, 12f)
            close()
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 6f); lineTo(12f, 12f); lineTo(16.5f, 14.5f)
        }
    }.build()

    fun notes(): ImageVector = Builder(
        name = "notes", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(9f, 3f)
            lineTo(5f, 3f)
            curveTo(3.8954f, 3f, 3f, 3.8954f, 3f, 5f)
            lineTo(3f, 19f)
            curveTo(3f, 20.1046f, 3.8954f, 21f, 5f, 21f)
            lineTo(19f, 21f)
            curveTo(20.1046f, 21f, 21f, 20.1046f, 21f, 19f)
            lineTo(21f, 5f)
            curveTo(21f, 3.8954f, 20.1046f, 3f, 19f, 3f)
            lineTo(15f, 3f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(9f, 2f)
            lineTo(15f, 2f)
            curveTo(15.5523f, 2f, 16f, 2.4477f, 16f, 3f)
            lineTo(16f, 4f)
            curveTo(16f, 4.5523f, 15.5523f, 5f, 15f, 5f)
            lineTo(9f, 5f)
            curveTo(8.4477f, 5f, 8f, 4.5523f, 8f, 4f)
            lineTo(8f, 3f)
            curveTo(8f, 2.4477f, 8.4477f, 2f, 9f, 2f)
            close()
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(8f, 11f); lineTo(16f, 11f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(8f, 15f); lineTo(13f, 15f)
        }
    }.build()

    fun trophy(): ImageVector = Builder(
        name = "trophy", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(8f, 21f); lineTo(16f, 21f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(12f, 17f); lineTo(12f, 21f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(7f, 4f); lineTo(4f, 4f)
            curveTo(2.8954f, 4f, 2f, 4.8954f, 2f, 6f)
            lineTo(2f, 8f)
            curveTo(2f, 11.3137f, 4.6863f, 14f, 8f, 14f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(17f, 4f); lineTo(20f, 4f)
            curveTo(21.1046f, 4f, 22f, 4.8954f, 22f, 6f)
            lineTo(22f, 8f)
            curveTo(22f, 11.3137f, 19.3137f, 14f, 16f, 14f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(7f, 4f); lineTo(17f, 4f); lineTo(17f, 12f)
            curveTo(17f, 14.7614f, 14.7614f, 17f, 12f, 17f)
            curveTo(9.2386f, 17f, 7f, 14.7614f, 7f, 12f)
            close()
        }
    }.build()

    fun fire(): ImageVector = Builder(
        name = "fire", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(8.5f, 14.5f)
            curveTo(9.8807f, 14.5f, 11f, 13.3807f, 11f, 12f)
            curveTo(11f, 10.62f, 10.5f, 10f, 10f, 9f)
            curveTo(8.928f, 6.857f, 9.776f, 4.946f, 12f, 3f)
            curveTo(12.5f, 5.5f, 14f, 7.9f, 16f, 9.5f)
            curveTo(18f, 11.1f, 19f, 13f, 19f, 15f)
            curveTo(19f, 18.866f, 15.866f, 22f, 12f, 22f)
            curveTo(8.134f, 22f, 5f, 18.866f, 5f, 15f)
            curveTo(5f, 13.847f, 5.433f, 12.706f, 6f, 12f)
            curveTo(6f, 13.381f, 7.1193f, 14.5f, 8.5f, 14.5f)
            close()
        }
    }.build()

    fun lock(): ImageVector = Builder(
        name = "lock", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(5f, 11f)
            lineTo(19f, 11f)
            curveTo(20.1046f, 11f, 21f, 11.8954f, 21f, 13f)
            lineTo(21f, 20f)
            curveTo(21f, 21.1046f, 20.1046f, 22f, 19f, 22f)
            lineTo(5f, 22f)
            curveTo(3.8954f, 22f, 3f, 21.1046f, 3f, 20f)
            lineTo(3f, 13f)
            curveTo(3f, 11.8954f, 3.8954f, 11f, 5f, 11f)
            close()
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(7f, 11f); lineTo(7f, 7f)
            curveTo(7f, 4.2386f, 9.2386f, 2f, 12f, 2f)
            curveTo(14.7614f, 2f, 17f, 4.2386f, 17f, 7f)
            lineTo(17f, 11f)
        }
    }.build()

    fun logout(): ImageVector = Builder(
        name = "logout", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(9f, 21f); lineTo(5f, 21f)
            curveTo(3.8954f, 21f, 3f, 20.1046f, 3f, 19f)
            lineTo(3f, 5f)
            curveTo(3f, 3.8954f, 3.8954f, 3f, 5f, 3f)
            lineTo(9f, 3f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(16f, 17f); lineTo(21f, 12f); lineTo(16f, 7f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(21f, 12f); lineTo(9f, 12f)
        }
    }.build()

    fun sun(): ImageVector = Builder(
        name = "sun", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(17f, 12f)
            curveTo(17f, 14.7614f, 14.7614f, 17f, 12f, 17f)
            curveTo(9.2386f, 17f, 7f, 14.7614f, 7f, 12f)
            curveTo(7f, 9.2386f, 9.2386f, 7f, 12f, 7f)
            curveTo(14.7614f, 7f, 17f, 9.2386f, 17f, 12f)
            close()
        }
    }.build()

    fun moon(): ImageVector = Builder(
        name = "moon", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(21f, 12.79f)
            curveTo(20.02f, 13.6f, 18.5f, 14.1f, 17f, 14.1f)
            curveTo(12.7f, 14.1f, 9.2f, 10.6f, 9.2f, 6.3f)
            curveTo(9.2f, 4.8f, 9.7f, 3.28f, 10.51f, 2.3f)
            curveTo(6.24f, 3.05f, 3f, 6.75f, 3f, 11.21f)
            curveTo(3f, 16.24f, 7.05f, 20.29f, 11.79f, 20.29f)
            curveTo(16.25f, 20.29f, 19.95f, 17.05f, 20.7f, 12.79f)
            lineTo(21f, 12.79f)
            close()
        }
    }.build()

    /**
     * ── Icon: smile (mặt cười) ──
     * Dùng cho FAB gộp 4 nút header (Tệp tin/Admin/Refresh/Dark) — vòng
     * tròn khuôn mặt + 2 chấm mắt + cung miệng cười, stroke-based đồng bộ
     * phong cách các icon khác trong bộ (fill=none, strokeWidth=2, round cap).
     */
    fun smile(): ImageVector = Builder(
        name = "smile", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(21f, 12f)
            curveTo(21f, 16.9706f, 16.9706f, 21f, 12f, 21f)
            curveTo(7.02944f, 21f, 3f, 16.9706f, 3f, 12f)
            curveTo(3f, 7.02944f, 7.02944f, 3f, 12f, 3f)
            curveTo(16.9706f, 3f, 21f, 7.02944f, 21f, 12f)
            close()
        }
        path(
            fill = SolidColor(Color.Black), stroke = null
        ) {
            moveTo(9f, 10f)
            curveTo(9f, 10.5523f, 8.5523f, 11f, 8f, 11f)
            curveTo(7.4477f, 11f, 7f, 10.5523f, 7f, 10f)
            curveTo(7f, 9.4477f, 7.4477f, 9f, 8f, 9f)
            curveTo(8.5523f, 9f, 9f, 9.4477f, 9f, 10f)
            close()
        }
        path(
            fill = SolidColor(Color.Black), stroke = null
        ) {
            moveTo(17f, 10f)
            curveTo(17f, 10.5523f, 16.5523f, 11f, 16f, 11f)
            curveTo(15.4477f, 11f, 15f, 10.5523f, 15f, 10f)
            curveTo(15f, 9.4477f, 15.4477f, 9f, 16f, 9f)
            curveTo(16.5523f, 9f, 17f, 9.4477f, 17f, 10f)
            close()
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(8f, 14f)
            curveTo(8f, 14f, 9.5f, 17f, 12f, 17f)
            curveTo(14.5f, 17f, 16f, 14f, 16f, 14f)
        }
    }.build()

    fun book(): ImageVector = Builder(
        name = "book", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(2f, 3f); lineTo(8f, 3f)
            curveTo(10.2091f, 3f, 12f, 4.7909f, 12f, 7f)
            lineTo(12f, 21f)
            curveTo(12f, 19.3431f, 10.6569f, 18f, 9f, 18f)
            lineTo(2f, 18f); close()
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(22f, 3f); lineTo(16f, 3f)
            curveTo(13.7909f, 3f, 12f, 4.7909f, 12f, 7f)
            lineTo(12f, 21f)
            curveTo(12f, 19.3431f, 13.3431f, 18f, 15f, 18f)
            lineTo(22f, 18f); close()
        }
    }.build()

    /** Tương đương IconHeadphones trong listening-practice.jsx — dùng cho lối vào Listening. */
    fun headphones(): ImageVector = Builder(
        name = "headphones", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(3f, 14f)
            lineTo(3f, 12f)
            arcToRelative(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 18f, dy1 = 0f)
            lineTo(21f, 14f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(21f, 19f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -2f, dy1 = 2f)
            lineTo(18f, 21f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -2f, dy1 = -2f)
            lineTo(16f, 16f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 2f, dy1 = -2f)
            lineTo(21f, 14f)
            lineTo(21f, 19f)
            close()
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(3f, 19f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 2f, dy1 = 2f)
            lineTo(6f, 21f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = 2f, dy1 = -2f)
            lineTo(8f, 16f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, dx1 = -2f, dy1 = -2f)
            lineTo(3f, 14f)
            lineTo(3f, 19f)
            close()
        }
    }.build()

    fun trending(): ImageVector = Builder(
        name = "trending", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(23f, 6f); lineTo(13.5f, 15.5f); lineTo(8.5f, 10.5f); lineTo(1f, 18f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(17f, 6f); lineTo(23f, 6f); lineTo(23f, 12f)
        }
    }.build()

    fun target(): ImageVector = Builder(
        name = "target", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
            moveTo(22f, 12f)
            curveTo(22f, 17.5228f, 17.5228f, 22f, 12f, 22f)
            curveTo(6.4772f, 22f, 2f, 17.5228f, 2f, 12f)
            curveTo(2f, 6.4772f, 6.4772f, 2f, 12f, 2f)
            curveTo(17.5228f, 2f, 22f, 6.4772f, 22f, 12f)
            close()
        }
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
            moveTo(18f, 12f)
            curveTo(18f, 15.3137f, 15.3137f, 18f, 12f, 18f)
            curveTo(8.6863f, 18f, 6f, 15.3137f, 6f, 12f)
            curveTo(6f, 8.6863f, 8.6863f, 6f, 12f, 6f)
            curveTo(15.3137f, 6f, 18f, 8.6863f, 18f, 12f)
            close()
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(14f, 12f)
            curveTo(14f, 13.1046f, 13.1046f, 14f, 12f, 14f)
            curveTo(10.8954f, 14f, 10f, 13.1046f, 10f, 12f)
            curveTo(10f, 10.8954f, 10.8954f, 10f, 12f, 10f)
            curveTo(13.1046f, 10f, 14f, 10.8954f, 14f, 12f)
            close()
        }
    }.build()

    fun ribbon(): ImageVector = Builder(
        name = "ribbon", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
            moveTo(18f, 8f)
            curveTo(18f, 11.3137f, 15.3137f, 14f, 12f, 14f)
            curveTo(8.6863f, 14f, 6f, 11.3137f, 6f, 8f)
            curveTo(6f, 4.6863f, 8.6863f, 2f, 12f, 2f)
            curveTo(15.3137f, 2f, 18f, 4.6863f, 18f, 8f)
            close()
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(15.477f, 12.89f); lineTo(17f, 22f); lineTo(12f, 19f)
            lineTo(7f, 22f); lineTo(8.523f, 12.89f)
        }
    }.build()

    fun medal(): ImageVector = Builder(
        name = "medal", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
            moveTo(18f, 14f)
            curveTo(18f, 17.3137f, 15.3137f, 20f, 12f, 20f)
            curveTo(8.6863f, 20f, 6f, 17.3137f, 6f, 14f)
            curveTo(6f, 10.6863f, 8.6863f, 8f, 12f, 8f)
            curveTo(15.3137f, 8f, 18f, 10.6863f, 18f, 14f)
            close()
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(9f, 2f); lineTo(15f, 2f); lineTo(16f, 9f); lineTo(8f, 9f); close()
        }
    }.build()

    fun shuffle(): ImageVector = Builder(
        name = "shuffle", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(16f, 3f); lineTo(21f, 3f); lineTo(21f, 8f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(4f, 20f); lineTo(21f, 3f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(21f, 16f); lineTo(21f, 21f); lineTo(16f, 21f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(15f, 15f); lineTo(21f, 21f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(4f, 4f); lineTo(9f, 9f)
        }
    }.build()

    fun history(): ImageVector = Builder(
        name = "history", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
            moveTo(5f, 2f); lineTo(19f, 2f)
            curveTo(20.1046f, 2f, 21f, 2.8954f, 21f, 4f)
            lineTo(21f, 20f)
            curveTo(21f, 21.1046f, 20.1046f, 22f, 19f, 22f)
            lineTo(5f, 22f)
            curveTo(3.8954f, 22f, 3f, 21.1046f, 3f, 20f)
            lineTo(3f, 4f)
            curveTo(3f, 2.8954f, 3.8954f, 2f, 5f, 2f)
            close()
        }
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(9f, 7f); lineTo(15f, 7f)
        }
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(9f, 11f); lineTo(15f, 11f)
        }
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(9f, 15f); lineTo(13f, 15f)
        }
    }.build()

    fun zap(): ImageVector = Builder(
        name = "zap", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
            moveTo(13f, 2f); lineTo(3f, 14f); lineTo(12f, 14f)
            lineTo(11f, 22f); lineTo(21f, 10f); lineTo(12f, 10f); close()
        }
    }.build()

    fun sad(): ImageVector = Builder(
        name = "sad", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
            moveTo(22f, 12f)
            curveTo(22f, 17.5228f, 17.5228f, 22f, 12f, 22f)
            curveTo(6.4772f, 22f, 2f, 17.5228f, 2f, 12f)
            curveTo(2f, 6.4772f, 6.4772f, 2f, 12f, 2f)
            curveTo(17.5228f, 2f, 22f, 6.4772f, 22f, 12f)
            close()
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(16f, 16f)
            curveTo(16f, 16f, 14.5f, 14f, 12f, 14f)
            curveTo(9.5f, 14f, 8f, 16f, 8f, 16f)
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(9f, 9f); lineTo(9.01f, 9f)
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(15f, 9f); lineTo(15.01f, 9f)
        }
    }.build()

    fun folder(): ImageVector = Builder(
        name = "folder", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(4f, 4f); lineTo(10f, 4f); lineTo(12f, 7f); lineTo(20f, 7f)
            curveTo(21.1046f, 7f, 22f, 7.8954f, 22f, 9f)
            lineTo(22f, 18f)
            curveTo(22f, 19.1046f, 21.1046f, 20f, 20f, 20f)
            lineTo(4f, 20f)
            curveTo(2.8954f, 20f, 2f, 19.1046f, 2f, 18f)
            lineTo(2f, 6f)
            curveTo(2f, 4.8954f, 2.8954f, 4f, 4f, 4f)
            close()
        }
    }.build()

    /** Icon file/tài liệu chung (tờ giấy gấp góc) — tương đương FileTypeIcon mặc định trong files-tab.jsx */
    fun file(): ImageVector = Builder(
        name = "file", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(14f, 2f); lineTo(6f, 2f)
            curveTo(4.8954f, 2f, 4f, 2.8954f, 4f, 4f)
            lineTo(4f, 20f)
            curveTo(4f, 21.1046f, 4.8954f, 22f, 6f, 22f)
            lineTo(18f, 22f)
            curveTo(19.1046f, 22f, 20f, 21.1046f, 20f, 20f)
            lineTo(20f, 8f)
            close()
            moveTo(14f, 2f); lineTo(14f, 8f); lineTo(20f, 8f)
        }
    }.build()

    /** Icon phát nhạc/video (tam giác) — dùng cho AudioPreview trong tab Tài liệu */
    fun play(): ImageVector = Builder(
        name = "play", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black), stroke = null) {
            moveTo(6f, 4f)
            lineTo(20f, 12f)
            lineTo(6f, 20f)
            close()
        }
    }.build()

    /** Icon tạm dừng (2 thanh dọc) — dùng cho AudioPreview trong tab Tài liệu */
    fun pause(): ImageVector = Builder(
        name = "pause", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black), stroke = null) {
            moveTo(6f, 4f); lineTo(10f, 4f); lineTo(10f, 20f); lineTo(6f, 20f)
            close()
        }
        path(fill = SolidColor(Color.Black), stroke = null) {
            moveTo(14f, 4f); lineTo(18f, 4f); lineTo(18f, 20f); lineTo(14f, 20f)
            close()
        }
    }.build()

    fun thumbsup(): ImageVector = Builder(
        name = "thumbsup", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(7f, 10f); lineTo(3f, 10f); lineTo(3f, 21f); lineTo(7f, 21f); close()
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(7f, 10f)
            lineTo(11.5f, 2f)
            curveTo(12.6046f, 2f, 13.5f, 2.8954f, 13.5f, 4f)
            lineTo(13.5f, 8f); lineTo(19f, 8f)
            curveTo(20.1046f, 8f, 21f, 8.8954f, 21f, 10f)
            lineTo(19f, 19f)
            curveTo(18.7f, 20.16f, 17.66f, 21f, 16.46f, 21f)
            lineTo(7f, 21f)
        }
    }.build()

    fun dice(): ImageVector = Builder(
        name = "dice", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
            moveTo(4f, 4f); lineTo(20f, 4f)
            curveTo(21.1046f, 4f, 22f, 4.8954f, 22f, 6f)
            lineTo(22f, 18f)
            curveTo(22f, 19.1046f, 21.1046f, 20f, 20f, 20f)
            lineTo(4f, 20f)
            curveTo(2.8954f, 20f, 2f, 19.1046f, 2f, 18f)
            lineTo(2f, 6f)
            curveTo(2f, 4.8954f, 2.8954f, 4f, 4f, 4f)
            close()
        }
        path(fill = SolidColor(Color.Black)) { moveTo(7.5f, 7.5f); lineTo(7.51f, 7.5f) }
        path(fill = SolidColor(Color.Black)) { moveTo(16.5f, 7.5f); lineTo(16.51f, 7.5f) }
        path(fill = SolidColor(Color.Black)) { moveTo(12f, 12f); lineTo(12.01f, 12f) }
        path(fill = SolidColor(Color.Black)) { moveTo(7.5f, 16.5f); lineTo(7.51f, 16.5f) }
        path(fill = SolidColor(Color.Black)) { moveTo(16.5f, 16.5f); lineTo(16.51f, 16.5f) }
    }.build()

    fun feather(): ImageVector = Builder(
        name = "feather", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(20.24f, 12.24f)
            curveTo(21.3658f, 11.1142f, 21.9983f, 9.5871f, 21.9983f, 7.995f)
            curveTo(21.9983f, 6.4029f, 21.3658f, 4.8758f, 20.24f, 3.75f)
            curveTo(19.1142f, 2.6242f, 17.5871f, 1.9917f, 15.995f, 1.9917f)
            curveTo(14.4029f, 1.9917f, 12.8758f, 2.6242f, 11.75f, 3.75f)
            lineTo(3f, 12.5f); lineTo(3f, 21f); lineTo(11.5f, 21f); close()
        }
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(16f, 8f); lineTo(2f, 22f)
        }
    }.build()

    fun cpu(): ImageVector = Builder(
        name = "cpu", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineJoin = StrokeJoin.Round) {
            moveTo(4f, 4f); lineTo(20f, 4f); lineTo(20f, 20f); lineTo(4f, 20f); close()
        }
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineJoin = StrokeJoin.Round) {
            moveTo(9f, 9f); lineTo(15f, 9f); lineTo(15f, 15f); lineTo(9f, 15f); close()
        }
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) { moveTo(9f, 1f); lineTo(9f, 4f) }
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) { moveTo(15f, 1f); lineTo(15f, 4f) }
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) { moveTo(9f, 20f); lineTo(9f, 23f) }
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) { moveTo(15f, 20f); lineTo(15f, 23f) }
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) { moveTo(20f, 9f); lineTo(23f, 9f) }
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) { moveTo(20f, 15f); lineTo(23f, 15f) }
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) { moveTo(1f, 9f); lineTo(4f, 9f) }
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) { moveTo(1f, 15f); lineTo(4f, 15f) }
    }.build()

    fun spinner(): ImageVector = Builder(
        name = "spinner", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2.5f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(12f, 2f)
            curveTo(6.4772f, 2f, 2f, 6.4772f, 2f, 12f)
        }
    }.build()

    fun settings(): ImageVector = Builder(
        name = "settings", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
            moveTo(15f, 12f)
            curveTo(15f, 13.6569f, 13.6569f, 15f, 12f, 15f)
            curveTo(10.3431f, 15f, 9f, 13.6569f, 9f, 12f)
            curveTo(9f, 10.3431f, 10.3431f, 9f, 12f, 9f)
            curveTo(13.6569f, 9f, 15f, 10.3431f, 15f, 12f)
            close()
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(19.4f, 15f)
            curveTo(19.1277f, 15.6171f, 19.2231f, 16.3378f, 19.65f, 16.86f)
            lineTo(19.71f, 16.93f)
            curveTo(20.0522f, 17.2716f, 20.2444f, 17.7367f, 20.2444f, 18.22f)
            curveTo(20.2444f, 18.7033f, 20.0522f, 19.1684f, 19.71f, 19.51f)
            curveTo(19.3684f, 19.8522f, 18.9033f, 20.0444f, 18.42f, 20.0444f)
            curveTo(17.9367f, 20.0444f, 17.4716f, 19.8522f, 17.13f, 19.51f)
            lineTo(17.06f, 19.45f)
            curveTo(16.5378f, 19.0231f, 15.8171f, 18.9277f, 15.2f, 19.2f)
            curveTo(14.5949f, 19.4573f, 14.2003f, 20.0507f, 14.2f, 20.71f)
            lineTo(14.2f, 20.9f)
            curveTo(14.2f, 21.8941f, 13.3941f, 22.7f, 12.4f, 22.7f)
            lineTo(11.6f, 22.7f)
            curveTo(10.6059f, 22.7f, 9.8f, 21.8941f, 9.8f, 20.9f)
            lineTo(9.8f, 20.79f)
            curveTo(9.7856f, 20.1105f, 9.3543f, 19.5124f, 8.72f, 19.28f)
            curveTo(8.1029f, 19.0077f, 7.3822f, 19.1031f, 6.86f, 19.53f)
            lineTo(6.79f, 19.59f)
            curveTo(6.4484f, 19.9322f, 5.9833f, 20.1244f, 5.5f, 20.1244f)
            curveTo(5.0167f, 20.1244f, 4.5516f, 19.9322f, 4.21f, 19.59f)
            curveTo(3.8678f, 19.2484f, 3.6756f, 18.7833f, 3.6756f, 18.3f)
            curveTo(3.6756f, 17.8167f, 3.8678f, 17.3516f, 4.21f, 17.01f)
            lineTo(4.27f, 16.94f)
            curveTo(4.6969f, 16.4178f, 4.7923f, 15.6971f, 4.52f, 15.08f)
            curveTo(4.2627f, 14.4749f, 3.6693f, 14.0803f, 3.01f, 14.08f)
            lineTo(2.9f, 14.08f)
            curveTo(1.9059f, 14.08f, 1.1f, 13.2741f, 1.1f, 12.28f)
            lineTo(1.1f, 11.72f)
            curveTo(1.1f, 10.7259f, 1.9059f, 9.92f, 2.9f, 9.92f)
            lineTo(3.01f, 9.92f)
            curveTo(3.6795f, 9.9056f, 4.2776f, 9.4743f, 4.51f, 8.84f)
            curveTo(4.7823f, 8.2229f, 4.6869f, 7.5022f, 4.26f, 6.98f)
            lineTo(4.2f, 6.91f)
            curveTo(3.8578f, 6.5684f, 3.6656f, 6.1033f, 3.6656f, 5.62f)
            curveTo(3.6656f, 5.1367f, 3.8578f, 4.6716f, 4.2f, 4.33f)
            curveTo(4.5416f, 3.9878f, 5.0067f, 3.7956f, 5.49f, 3.7956f)
            curveTo(5.9733f, 3.7956f, 6.4384f, 3.9878f, 6.78f, 4.33f)
            lineTo(6.85f, 4.39f)
            curveTo(7.3722f, 4.8169f, 8.0929f, 4.9123f, 8.71f, 4.64f)
            lineTo(8.8f, 4.64f)
            curveTo(9.4051f, 4.3827f, 9.7997f, 3.7893f, 9.8f, 3.13f)
            lineTo(9.8f, 3f)
            curveTo(9.8f, 2.0059f, 10.6059f, 1.2f, 11.6f, 1.2f)
            lineTo(12.4f, 1.2f)
            curveTo(13.3941f, 1.2f, 14.2f, 2.0059f, 14.2f, 3f)
            lineTo(14.2f, 3.11f)
            curveTo(14.2003f, 3.7693f, 14.5949f, 4.3627f, 15.2f, 4.62f)
            curveTo(15.8171f, 4.8923f, 16.5378f, 4.7969f, 17.06f, 4.37f)
            lineTo(17.13f, 4.31f)
            curveTo(17.4716f, 3.9678f, 17.9367f, 3.7756f, 18.42f, 3.7756f)
            curveTo(18.9033f, 3.7756f, 19.3684f, 3.9678f, 19.71f, 4.31f)
            curveTo(20.0522f, 4.6516f, 20.2444f, 5.1167f, 20.2444f, 5.6f)
            curveTo(20.2444f, 6.0833f, 20.0522f, 6.5484f, 19.71f, 6.89f)
            lineTo(19.65f, 6.96f)
            curveTo(19.2231f, 7.4822f, 19.1277f, 8.2029f, 19.4f, 8.82f)
            lineTo(19.4f, 8.9f)
            curveTo(19.6573f, 9.5051f, 20.2507f, 9.8997f, 20.91f, 9.9f)
            lineTo(21f, 9.9f)
            curveTo(21.9941f, 9.9f, 22.8f, 10.7059f, 22.8f, 11.7f)
            lineTo(22.8f, 12.3f)
            curveTo(22.8f, 13.2941f, 21.9941f, 14.1f, 21f, 14.1f)
            lineTo(20.9f, 14.1f)
            curveTo(20.2407f, 14.1003f, 19.6473f, 14.4949f, 19.4f, 15.1f)
            close()
        }
    }.build()

    fun chevronLeft(): ImageVector = Builder(
        name = "chevronLeft", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2.5f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(15f, 18f); lineTo(9f, 12f); lineTo(15f, 6f)
        }
    }.build()

    fun chevronRight(): ImageVector = Builder(
        name = "chevronRight", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2.5f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(9f, 18f); lineTo(15f, 12f); lineTo(9f, 6f)
        }
    }.build()

    fun graduationCap(): ImageVector = Builder(
        name = "graduationCap", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(2f, 3f); lineTo(8f, 3f)
            curveTo(10.2091f, 3f, 12f, 4.7909f, 12f, 7f)
            lineTo(12f, 21f)
            curveTo(12f, 19.3431f, 10.6569f, 18f, 9f, 18f)
            lineTo(2f, 18f)
            close()
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(22f, 3f); lineTo(16f, 3f)
            curveTo(13.7909f, 3f, 12f, 4.7909f, 12f, 7f)
            lineTo(12f, 21f)
            curveTo(12f, 19.3431f, 13.3431f, 18f, 15f, 18f)
            lineTo(22f, 18f)
            close()
        }
    }.build()

    fun graduationCapFilled(): ImageVector = Builder(
        name = "graduationCapFilled", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2.5f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(22f, 10f); lineTo(12f, 5f); lineTo(2f, 10f); lineTo(12f, 15f); lineTo(22f, 10f)
            close()
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2.5f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(6f, 12f); lineTo(6f, 17f)
            curveTo(9f, 20f, 15f, 20f, 18f, 17f)
            lineTo(18f, 12f)
        }
    }.build()

    fun eye(): ImageVector = Builder(
        name = "eye", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(1f, 12f)
            curveTo(1f, 12f, 5f, 4f, 12f, 4f)
            curveTo(19f, 4f, 23f, 12f, 23f, 12f)
            curveTo(23f, 12f, 19f, 20f, 12f, 20f)
            curveTo(5f, 20f, 1f, 12f, 1f, 12f)
            close()
        }
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
            moveTo(15f, 12f)
            curveTo(15f, 13.6569f, 13.6569f, 15f, 12f, 15f)
            curveTo(10.3431f, 15f, 9f, 13.6569f, 9f, 12f)
            curveTo(9f, 10.3431f, 10.3431f, 9f, 12f, 9f)
            curveTo(13.6569f, 9f, 15f, 10.3431f, 15f, 12f)
            close()
        }
    }.build()

    fun eyeOff(): ImageVector = Builder(
        name = "eyeOff", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(17.94f, 17.94f)
            curveTo(16.2306f, 19.243f, 14.1491f, 19.9691f, 12f, 20f)
            curveTo(5f, 20f, 1f, 12f, 1f, 12f)
            curveTo(2.24f, 9.6819f, 3.9414f, 7.6377f, 6f, 6f)
        }
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(9.9f, 4.24f)
            curveTo(10.5883f, 4.0789f, 11.2931f, 3.9979f, 12f, 4f)
            curveTo(19f, 4f, 23f, 12f, 23f, 12f)
            curveTo(22.393f, 13.1356f, 21.6851f, 14.2151f, 20.88f, 15.19f)
        }
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(14.12f, 14.12f)
            curveTo(13.8454f, 14.4148f, 13.5141f, 14.6512f, 13.1462f, 14.8151f)
            curveTo(12.7782f, 14.9791f, 12.3809f, 15.0673f, 11.9781f, 15.0744f)
            curveTo(11.5753f, 15.0815f, 11.1752f, 15.0074f, 10.8016f, 14.8565f)
            curveTo(10.4281f, 14.7056f, 10.0887f, 14.4811f, 9.8036f, 14.1964f)
            curveTo(9.5185f, 13.9117f, 9.2935f, 13.5726f, 9.1421f, 13.1993f)
            curveTo(8.9906f, 12.8259f, 8.9159f, 12.4259f, 8.9224f, 12.0231f)
            curveTo(8.9289f, 11.6202f, 9.0165f, 11.2228f, 9.18f, 10.8548f)
            curveTo(9.3435f, 10.4867f, 9.5795f, 10.1552f, 9.874f, 9.8804f)
        }
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(1f, 1f); lineTo(23f, 23f)
        }
    }.build()

    fun userCircle(): ImageVector = Builder(
        name = "userCircle", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(20f, 21f); lineTo(20f, 19f)
            curveTo(20f, 16.7909f, 18.2091f, 15f, 16f, 15f)
            lineTo(8f, 15f)
            curveTo(5.7909f, 15f, 4f, 16.7909f, 4f, 19f)
            lineTo(4f, 21f)
        }
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
            moveTo(16f, 7f)
            curveTo(16f, 9.2091f, 14.2091f, 11f, 12f, 11f)
            curveTo(9.7909f, 11f, 8f, 9.2091f, 8f, 7f)
            curveTo(8f, 4.7909f, 9.7909f, 3f, 12f, 3f)
            curveTo(14.2091f, 3f, 16f, 4.7909f, 16f, 7f)
            close()
        }
    }.build()

    fun alertCircle(): ImageVector = Builder(
        name = "alertCircle", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2.5f) {
            moveTo(22f, 12f)
            curveTo(22f, 17.5228f, 17.5228f, 22f, 12f, 22f)
            curveTo(6.4772f, 22f, 2f, 17.5228f, 2f, 12f)
            curveTo(2f, 6.4772f, 6.4772f, 2f, 12f, 2f)
            curveTo(17.5228f, 2f, 22f, 6.4772f, 22f, 12f)
            close()
        }
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2.5f, strokeLineCap = StrokeCap.Round) {
            moveTo(12f, 8f); lineTo(12f, 12f)
        }
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2.5f, strokeLineCap = StrokeCap.Round) {
            moveTo(12f, 16f); lineTo(12.01f, 16f)
        }
    }.build()

    fun shieldLock(): ImageVector = Builder(
        name = "shieldLock", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2.5f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(3f, 11f); lineTo(21f, 11f)
            curveTo(21f, 11f, 21f, 22f, 12f, 22f)
            curveTo(12f, 22f, 3f, 22f, 3f, 11f)
            close()
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2.5f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(7f, 11f); lineTo(7f, 7f)
            curveTo(7f, 4.2386f, 9.2386f, 2f, 12f, 2f)
            curveTo(14.7614f, 2f, 17f, 4.2386f, 17f, 7f)
            lineTo(17f, 11f)
        }
    }.build()

    fun close(): ImageVector = Builder(
        name = "close", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2.4f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(18f, 6f); lineTo(6f, 18f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2.4f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(6f, 6f); lineTo(18f, 18f)
        }
    }.build()

    fun stats(): ImageVector = Builder(
        name = "stats", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(18f, 20f); lineTo(18f, 10f)
        }
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(12f, 20f); lineTo(12f, 4f)
        }
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(6f, 20f); lineTo(6f, 14f)
        }
    }.build()

    fun user(): ImageVector = Builder(
        name = "user", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(20f, 21f); lineTo(20f, 19f)
            curveTo(20f, 16.7909f, 18.2091f, 15f, 16f, 15f)
            lineTo(8f, 15f)
            curveTo(5.7909f, 15f, 4f, 16.7909f, 4f, 19f)
            lineTo(4f, 21f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 11f)
            curveTo(14.2091f, 11f, 16f, 9.2091f, 16f, 7f)
            curveTo(16f, 4.7909f, 14.2091f, 3f, 12f, 3f)
            curveTo(9.7909f, 3f, 8f, 4.7909f, 8f, 7f)
            curveTo(8f, 9.2091f, 9.7909f, 11f, 12f, 11f)
            close()
        }
    }.build()

    /** ── picture (ảnh) — dùng cho BgSettingsCard header ── */
    fun picture(): ImageVector = Builder(
        name = "picture", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(3f, 3f)
            horizontalLineToRelative(18f)
            verticalLineToRelative(18f)
            horizontalLineToRelative(-18f)
            close()
        }
        path(
            fill = SolidColor(Color.Black), stroke = null
        ) {
            moveTo(8.5f, 10f)
            curveTo(9.3284f, 10f, 10f, 9.3284f, 10f, 8.5f)
            curveTo(10f, 7.6716f, 9.3284f, 7f, 8.5f, 7f)
            curveTo(7.6716f, 7f, 7f, 7.6716f, 7f, 8.5f)
            curveTo(7f, 9.3284f, 7.6716f, 10f, 8.5f, 10f)
            close()
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(21f, 15f)
            lineTo(16f, 10f)
            lineTo(5f, 21f)
        }
    }.build()

    /** ── palette (bảng màu) ── */
    fun palette(): ImageVector = Builder(
        name = "palette", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 2f)
            curveTo(6.5f, 2f, 2f, 6.5f, 2f, 12f)
            curveTo(2f, 17.5f, 6.5f, 22f, 12f, 22f)
            curveTo(12.926f, 22f, 13.648f, 21.254f, 13.648f, 20.312f)
            curveTo(13.648f, 19.875f, 13.468f, 19.477f, 13.211f, 19.188f)
            curveTo(12.921f, 18.898f, 12.773f, 18.535f, 12.773f, 18.062f)
            curveTo(12.773f, 17.16f, 13.5f, 16.395f, 14.441f, 16.395f)
            horizontalLineToRelative(1.996f)
            curveTo(19.488f, 16.395f, 21.992f, 13.891f, 21.992f, 10.841f)
            curveTo(21.965f, 6.012f, 17.461f, 2f, 12f, 2f)
            close()
        }
        path(fill = SolidColor(Color.Black), stroke = null) {
            moveTo(13.5f, 6.5f)
            curveTo(13.5f, 6.776f, 13.276f, 7f, 13f, 7f)
            curveTo(12.724f, 7f, 12.5f, 6.776f, 12.5f, 6.5f)
            curveTo(12.5f, 6.224f, 12.724f, 6f, 13f, 6f)
            curveTo(13.276f, 6f, 13.5f, 6.224f, 13.5f, 6.5f)
            close()
        }
        path(fill = SolidColor(Color.Black), stroke = null) {
            moveTo(17.5f, 10.5f)
            curveTo(17.5f, 10.776f, 17.276f, 11f, 17f, 11f)
            curveTo(16.724f, 11f, 16.5f, 10.776f, 16.5f, 10.5f)
            curveTo(16.5f, 10.224f, 16.724f, 10f, 17f, 10f)
            curveTo(17.276f, 10f, 17.5f, 10.224f, 17.5f, 10.5f)
            close()
        }
        path(fill = SolidColor(Color.Black), stroke = null) {
            moveTo(9f, 7.5f)
            curveTo(9f, 7.776f, 8.776f, 8f, 8.5f, 8f)
            curveTo(8.224f, 8f, 8f, 7.776f, 8f, 7.5f)
            curveTo(8f, 7.224f, 8.224f, 7f, 8.5f, 7f)
            curveTo(8.776f, 7f, 9f, 7.224f, 9f, 7.5f)
            close()
        }
        path(fill = SolidColor(Color.Black), stroke = null) {
            moveTo(7f, 12.5f)
            curveTo(7f, 12.776f, 6.776f, 13f, 6.5f, 13f)
            curveTo(6.224f, 13f, 6f, 12.776f, 6f, 12.5f)
            curveTo(6f, 12.224f, 6.224f, 12f, 6.5f, 12f)
            curveTo(6.776f, 12f, 7f, 12.224f, 7f, 12.5f)
            close()
        }
    }.build()

    /** ── camera ── */
    fun camera(): ImageVector = Builder(
        name = "camera", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(23f, 19f)
            curveTo(23f, 20.1046f, 22.1046f, 21f, 21f, 21f)
            horizontalLineTo(3f)
            curveTo(1.8954f, 21f, 1f, 20.1046f, 1f, 19f)
            verticalLineTo(8f)
            curveTo(1f, 6.8954f, 1.8954f, 6f, 3f, 6f)
            horizontalLineToRelative(4f)
            lineTo(9f, 3f)
            horizontalLineToRelative(6f)
            lineTo(17f, 6f)
            horizontalLineToRelative(4f)
            curveTo(22.1046f, 6f, 23f, 6.8954f, 23f, 8f)
            close()
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(16f, 13f)
            curveTo(16f, 15.2091f, 14.2091f, 17f, 12f, 17f)
            curveTo(9.7909f, 17f, 8f, 15.2091f, 8f, 13f)
            curveTo(8f, 10.7909f, 9.7909f, 9f, 12f, 9f)
            curveTo(14.2091f, 9f, 16f, 10.7909f, 16f, 13f)
            close()
        }
    }.build()

    /** ── trash (xoá) ── */
    fun trash(): ImageVector = Builder(
        name = "trash", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(3f, 6f)
            horizontalLineToRelative(2f)
            horizontalLineToRelative(16f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(19f, 6f)
            verticalLineToRelative(14f)
            curveTo(19f, 21.1046f, 18.1046f, 22f, 17f, 22f)
            horizontalLineTo(7f)
            curveTo(5.8954f, 22f, 5f, 21.1046f, 5f, 20f)
            verticalLineTo(6f)
            moveTo(8f, 6f)
            verticalLineTo(4f)
            curveTo(8f, 3.4477f, 8.4477f, 3f, 9f, 3f)
            horizontalLineToRelative(6f)
            curveTo(15.5523f, 3f, 16f, 3.4477f, 16f, 4f)
            verticalLineTo(6f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(10f, 11f)
            verticalLineTo(17f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(14f, 11f)
            verticalLineTo(17f)
        }
    }.build()

    /** ── cloud (dùng cho blur85/blur100 + sync badge) ── */
    fun cloud(): ImageVector = Builder(
        name = "cloud", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2.5f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(18f, 10f)
            horizontalLineToRelative(-1.26f)
            curveTo(16.115f, 6.437f, 12.865f, 3.75f, 9f, 3.75f)
            curveTo(4.582f, 3.75f, 1f, 7.332f, 1f, 11.75f)
            curveTo(1f, 16.168f, 4.582f, 19.75f, 9f, 19.75f)
            horizontalLineToRelative(9f)
            curveTo(20.761f, 19.75f, 23f, 17.511f, 23f, 14.75f)
            curveTo(23f, 11.989f, 20.761f, 9.75f, 18f, 9.75f)
            close()
        }
    }.build()

    /** ── pin (ghim, chú thích nhỏ) ── */
    fun pin(): ImageVector = Builder(
        name = "pin", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(21f, 10f)
            curveTo(21f, 17f, 12f, 23f, 12f, 23f)
            curveTo(12f, 23f, 3f, 17f, 3f, 10f)
            curveTo(3f, 5.0294f, 7.0294f, 1f, 12f, 1f)
            curveTo(16.9706f, 1f, 21f, 5.0294f, 21f, 10f)
            close()
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(15f, 10f)
            curveTo(15f, 11.6569f, 13.6569f, 13f, 12f, 13f)
            curveTo(10.3431f, 13f, 9f, 11.6569f, 9f, 10f)
            curveTo(9f, 8.3431f, 10.3431f, 7f, 12f, 7f)
            curveTo(13.6569f, 7f, 15f, 8.3431f, 15f, 10f)
            close()
        }
    }.build()

    /** ── download (mũi tên xuống + khay, dùng cho nút tải bài offline) ── */
    fun download(): ImageVector = Builder(
        name = "download", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(21f, 15f)
            verticalLineTo(19f)
            curveTo(21f, 19.5304f, 20.7893f, 20.0391f, 20.4142f, 20.4142f)
            curveTo(20.0391f, 20.7893f, 19.5304f, 21f, 19f, 21f)
            horizontalLineTo(5f)
            curveTo(4.4696f, 21f, 3.9609f, 20.7893f, 3.5858f, 20.4142f)
            curveTo(3.2107f, 20.0391f, 3f, 19.5304f, 3f, 19f)
            verticalLineTo(15f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(7f, 10f)
            lineTo(12f, 15f)
            lineTo(17f, 10f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(12f, 15f)
            verticalLineTo(3f)
        }
    }.build()

    /** ── wifiOff (báo trạng thái offline, dùng cho banner "đang xem offline") ── */
    fun wifiOff(): ImageVector = Builder(
        name = "wifiOff", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(1f, 1f)
            lineTo(23f, 23f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(16.72f, 11.06f)
            curveTo(17.9569f, 11.5814f, 19.0796f, 12.3436f, 20f, 13.31f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(5f, 12.55f)
            curveTo(6.1782f, 11.6072f, 7.5322f, 10.9067f, 8.98f, 10.49f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(10.71f, 5.05f)
            curveTo(11.14f, 5f, 11.57f, 5f, 12f, 5f)
            curveTo(15.6301f, 4.9973f, 19.1449f, 6.2635f, 22f, 8.55f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(2f, 8.55f)
            curveTo(3.2325f, 7.5761f, 4.6069f, 6.7963f, 6.06f, 6.23f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(8.53f, 16.11f)
            curveTo(9.6169f, 15.3852f, 10.8945f, 14.9986f, 12.2f, 15f)
            curveTo(13.5055f, 14.9986f, 14.7831f, 15.3852f, 15.87f, 16.11f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 20f)
            horizontalLineToRelative(0.01f)
        }
    }.build()
}

/** Icon âm lượng (bật/tắt tiếng) — tách object riêng để không phải sửa lại toàn bộ
 *  danh sách icon gốc phía trên. */
private object DashboardIconPaths2 {
    fun volumeOn(): ImageVector = Builder(
        name = "volumeOn", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(11f, 5f); lineTo(6f, 9f); lineTo(2f, 9f); lineTo(2f, 15f); lineTo(6f, 15f)
            lineTo(11f, 19f)
            close()
        }
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(15.54f, 8.46f)
            curveTo(16.4774f, 9.3968f, 17.004f, 10.6656f, 17.004f, 11.99f)
            curveTo(17.004f, 13.3144f, 16.4774f, 14.5833f, 15.54f, 15.52f)
        }
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(19.07f, 4.93f)
            curveTo(20.9447f, 6.8054f, 21.9979f, 9.3486f, 21.9979f, 12f)
            curveTo(21.9979f, 14.6514f, 20.9447f, 17.1946f, 19.07f, 19.07f)
        }
    }.build()

    fun volumeOff(): ImageVector = Builder(
        name = "volumeOff", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(11f, 5f); lineTo(6f, 9f); lineTo(2f, 9f); lineTo(2f, 15f); lineTo(6f, 15f)
            lineTo(11f, 19f)
            close()
        }
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(23f, 9f); lineTo(17f, 15f)
        }
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(17f, 9f); lineTo(23f, 15f)
        }
    }.build()

    /** Icon "trả lại" (undo) — mũi tên cong ngược, thay cho chữ "Phát lại" dài dòng. */
    fun undo(): ImageVector = Builder(
        name = "undo", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(3f, 10f); lineTo(3f, 4f); lineTo(9f, 4f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(3f, 10f)
            curveTo(3f, 10f, 6.5f, 4.5f, 12f, 4.5f)
            curveTo(17.5228f, 4.5f, 22f, 8.9772f, 22f, 14.5f)
            curveTo(22f, 20.0228f, 17.5228f, 20.5f, 12f, 20.5f)
            curveTo(8f, 20.5f, 5.2f, 19f, 4f, 17f)
        }
    }.build()

    /** Icon "làm mới" (refresh) — 2 mũi tên cong tạo vòng tròn, dùng cho nút refresh app. */
    fun refresh(): ImageVector = Builder(
        name = "refresh", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(21f, 8f)
            curveTo(19.9828f, 5.6265f, 17.7909f, 3.9028f, 15.1943f, 3.4548f)
            curveTo(12.5977f, 3.0068f, 9.9483f, 3.9008f, 8.1716f, 5.8284f)
            lineTo(3f, 11f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(3f, 4f); lineTo(3f, 11f); lineTo(10f, 11f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(3f, 16f)
            curveTo(4.0172f, 18.3735f, 6.2091f, 20.0972f, 8.8057f, 20.5452f)
            curveTo(11.4023f, 20.9932f, 14.0517f, 20.0992f, 15.8284f, 18.1716f)
            lineTo(21f, 13f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(21f, 20f); lineTo(21f, 13f); lineTo(14f, 13f)
        }
    }.build()

    /** Icon "tệp tin" (file/document) — tờ giấy góc trên-phải gấp lại, dùng cho nút "Sắp ra mắt". */
    fun file(): ImageVector = Builder(
        name = "file", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(14f, 2f)
            lineTo(6f, 2f)
            curveTo(5.4477f, 2f, 5f, 2.4477f, 5f, 3f)
            lineTo(5f, 21f)
            curveTo(5f, 21.5523f, 5.4477f, 22f, 6f, 22f)
            lineTo(18f, 22f)
            curveTo(18.5523f, 22f, 19f, 21.5523f, 19f, 21f)
            lineTo(19f, 7f)
            lineTo(14f, 2f)
            close()
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(14f, 2f); lineTo(14f, 7f); lineTo(19f, 7f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(9f, 13f); lineTo(15f, 13f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(9f, 17f); lineTo(15f, 17f)
        }
    }.build()
    fun rewind15(): ImageVector = Builder(
        name = "rewind15", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(4f, 5f); lineTo(4f, 10f); lineTo(9f, 10f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(4f, 10f)
            curveTo(5.5f, 6.5f, 8.8f, 4f, 13f, 4f)
            curveTo(18.5228f, 4f, 23f, 8.4772f, 23f, 14f)
            curveTo(23f, 19.5228f, 18.5228f, 24f, 13f, 24f)
            curveTo(8.7f, 24f, 5f, 21.3f, 3.7f, 17.6f)
        }
    }.build()

    /** Icon tua tới 15 giây — mũi tên cong xuôi quanh số 15. */
    fun forward15(): ImageVector = Builder(
        name = "forward15", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(20f, 5f); lineTo(20f, 10f); lineTo(15f, 10f)
        }
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(20f, 10f)
            curveTo(18.5f, 6.5f, 15.2f, 4f, 11f, 4f)
            curveTo(5.4772f, 4f, 1f, 8.4772f, 1f, 14f)
            curveTo(1f, 19.5228f, 5.4772f, 24f, 11f, 24f)
            curveTo(15.3f, 24f, 19f, 21.3f, 20.3f, 17.6f)
        }
    }.build()

    /** Icon bút chì viết — dùng cho nút "Kiểm tra viết từ" trong VocabPractice */
    fun edit(): ImageVector = Builder(
        name = "edit", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(17f, 3f)
            curveTo(17.2626f, 2.7374f, 17.5744f, 2.5286f, 17.9176f, 2.3854f)
            curveTo(18.2608f, 2.2423f, 18.6289f, 2.1685f, 19.0006f, 2.1685f)
            curveTo(19.3724f, 2.1685f, 19.7404f, 2.2423f, 20.0836f, 2.3854f)
            curveTo(20.4268f, 2.5286f, 20.7386f, 2.7374f, 21.0012f, 3f)
            curveTo(21.2638f, 3.2626f, 21.4726f, 3.5744f, 21.6158f, 3.9176f)
            curveTo(21.7589f, 4.2608f, 21.8327f, 4.6289f, 21.8327f, 5.0006f)
            curveTo(21.8327f, 5.3724f, 21.7589f, 5.7404f, 21.6158f, 6.0836f)
            curveTo(21.4726f, 6.4268f, 21.2638f, 6.7386f, 21.0012f, 7.0012f)
            lineTo(7.5f, 20.5f)
            lineTo(3f, 21.5f)
            lineTo(4f, 17f)
            close()
        }
    }.build()

    /** Icon nốt nhạc (2 nốt liền path) — dùng cho nút bật/tắt nhạc nền trong QuizPlayer */
    fun music(): ImageVector = Builder(
        name = "music", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(9f, 18f); lineTo(9f, 5f); lineTo(21f, 3f); lineTo(21f, 16f)
        }
        path(
            fill = SolidColor(Color.Black), stroke = null
        ) {
            moveTo(11f, 21f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 4f, dy1 = 0f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -4f, dy1 = 0f)
            close()
        }
        path(
            fill = SolidColor(Color.Black), stroke = null
        ) {
            moveTo(23f, 19f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 4f, dy1 = 0f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -4f, dy1 = 0f)
            close()
        }
    }.build()
}

/** Danh sách tên icon hiện đã hỗ trợ — dùng để fallback về "sparkle" nếu không tìm thấy */
private val iconBuilders: Map<String, () -> ImageVector> = mapOf(
    "volumeOn" to DashboardIconPaths2::volumeOn,
    "speaker" to DashboardIconPaths2::volumeOn,
    "volumeOff" to DashboardIconPaths2::volumeOff,
    "edit" to DashboardIconPaths2::edit,
    "music" to DashboardIconPaths2::music,
    "home" to DashboardIconPaths::home,
    "star" to DashboardIconPaths::star,
    "heart" to DashboardIconPaths::heart,
    "sparkle" to DashboardIconPaths::sparkle,
    "search" to DashboardIconPaths::search,
    "check" to DashboardIconPaths::check,
    "close" to DashboardIconPaths::close,
    "zap" to DashboardIconPaths::zap,
    "sad" to DashboardIconPaths::sad,
    "folder" to DashboardIconPaths::folder,
    "file" to DashboardIconPaths::file,
    "play" to DashboardIconPaths::play,
    "pause" to DashboardIconPaths::pause,
    "thumbsup" to DashboardIconPaths::thumbsup,
    "dice" to DashboardIconPaths::dice,
    "feather" to DashboardIconPaths::feather,
    "cpu" to DashboardIconPaths::cpu,
    "spinner" to DashboardIconPaths::spinner,
    "settings" to DashboardIconPaths::settings,
    "chevronLeft" to DashboardIconPaths::chevronLeft,
    "chevronRight" to DashboardIconPaths::chevronRight,
    "graduationCap" to DashboardIconPaths::graduationCap,
    "graduationCapFilled" to DashboardIconPaths::graduationCapFilled,
    "eye" to DashboardIconPaths::eye,
    "eyeOff" to DashboardIconPaths::eyeOff,
    "userCircle" to DashboardIconPaths::userCircle,
    "alertCircle" to DashboardIconPaths::alertCircle,
    "shieldLock" to DashboardIconPaths::shieldLock,
    "trophy" to DashboardIconPaths::trophy,
    "calendar" to DashboardIconPaths::calendar,
    "clock" to DashboardIconPaths::clock,
    "notes" to DashboardIconPaths::notes,
    "fire" to DashboardIconPaths::fire,
    "lock" to DashboardIconPaths::lock,
    "logout" to DashboardIconPaths::logout,
    "sun" to DashboardIconPaths::sun,
    "moon" to DashboardIconPaths::moon,
    "smile" to DashboardIconPaths::smile,
    "book" to DashboardIconPaths::book,
    "headphones" to DashboardIconPaths::headphones,
    "trending" to DashboardIconPaths::trending,
    "target" to DashboardIconPaths::target,
    "ribbon" to DashboardIconPaths::ribbon,
    "medal" to DashboardIconPaths::medal,
    "shuffle" to DashboardIconPaths::shuffle,
    "history" to DashboardIconPaths::history,
    "stats" to DashboardIconPaths::stats,
    "user" to DashboardIconPaths::user,
    "eye" to DashboardIconPaths::eye,
    "eyeOff" to DashboardIconPaths::eyeOff,
    "picture" to DashboardIconPaths::picture,
    "palette" to DashboardIconPaths::palette,
    "camera" to DashboardIconPaths::camera,
    "trash" to DashboardIconPaths::trash,
    "cloud" to DashboardIconPaths::cloud,
    "pin" to DashboardIconPaths::pin,
    "download" to DashboardIconPaths::download,
    "wifiOff" to DashboardIconPaths::wifiOff,
    "undo" to DashboardIconPaths2::undo,
    "rewind15" to DashboardIconPaths2::rewind15,
    "forward15" to DashboardIconPaths2::forward15,
    "refresh" to DashboardIconPaths2::refresh,
    "file" to DashboardIconPaths2::file,
)

/**
 * ── Icon: tương đương function Icon({name,size,color}) trong dashboard.jsx ──
 * Dùng: DashboardIcon(name = "trophy", size = 22.dp, color = Color(0xFFF59E0B))
 *
 * LƯU Ý: vì ImageVector build sẵn với màu "Color.Black" làm placeholder,
 * ta tint lại bằng ColorFilter khi vẽ để đổi màu runtime mà không cần
 * build lại icon mỗi lần đổi màu.
 */
@Composable
fun DashboardIcon(
    name: String,
    size: Dp = 20.dp,
    color: Color = Color.Black,
    modifier: Modifier = Modifier
) {
    val builder = iconBuilders[name] ?: iconBuilders["sparkle"]!!
    val vector = builder()
    Image(
        painter = rememberVectorPainter(vector),
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(color)
    )
}

/**
 * ── Logo React (nguyên bản, đa sắc) ──
 * Icon các trang khác trong app đều đơn sắc (tint 1 màu) nên không dùng
 * chung cơ chế DashboardIcon được — logo React cần giữ đúng màu xanh
 * cyan đặc trưng (#61DAFB) trên nhân trung tâm + 3 quỹ đạo elip xoay
 * 60° lệch nhau, giống hệt logo chính thức của React.
 */
@Composable
fun ReactLogoIcon(size: Dp = 16.dp, color: Color = Color(0xFF61DAFB), modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val nucleusR = w * 0.09f
        val orbitStroke = w * 0.045f

        drawCircle(color = color, radius = nucleusR, center = androidx.compose.ui.geometry.Offset(cx, cy))

        val ellipseW = w * 0.92f
        val ellipseH = h * 0.36f
        repeat(3) { i ->
            rotate(degrees = 60f * i, pivot = androidx.compose.ui.geometry.Offset(cx, cy)) {
                drawOval(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(cx - ellipseW / 2f, cy - ellipseH / 2f),
                    size = Size(ellipseW, ellipseH),
                    style = DrawStroke(width = orbitStroke)
                )
            }
        }
    }
}

package com.learnsypro.app.admin.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsypro.app.admin.ui.theme.Baloo2FontFamily
import com.learnsypro.app.admin.ui.theme.LearnsyColors

// Tương đương KpiCard trong dashboard.jsx (kawaii card, dark-aware, glow blob + shadow)
@Composable
fun KpiCard(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    sub: String?,
    color: Color,
    dark: Boolean,
    colors: LearnsyColors,
    modifier: Modifier = Modifier
) {
    // rgba(color, alpha) tương đương helper rgba() trong web
    fun tint(alpha: Float) = color.copy(alpha = alpha)

    val bg = if (dark) tint(0.12f) else tint(0.08f)
    val border = if (dark) tint(0.25f) else tint(0.2f)
    val gradientStart = if (dark) tint(0.14f) else tint(0.1f)
    val gradientEnd = if (dark) tint(0.07f) else tint(0.05f)
    val cardShadowColor = if (dark) tint(0.2f) else tint(0.12f)

    Box(
        modifier = modifier
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = cardShadowColor,
                spotColor = cardShadowColor
            )
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(gradientStart, gradientEnd)))
            .border(1.5.dp, border, RoundedCornerShape(20.dp))
    ) {
        // glow blob — góc trên-phải, tương đương <div> tròn mờ trong web
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 16.dp, y = (-16).dp)
                .size(60.dp)
                .clip(CircleShape)
                .background(tint(0.12f))
        )

        Column(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(
                    modifier = Modifier
                        .shadow(
                            elevation = 6.dp,
                            shape = RoundedCornerShape(13.dp),
                            ambientColor = tint(0.3f),
                            spotColor = tint(0.3f)
                        )
                        .size(38.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(bg)
                        .border(1.5.dp, border, RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center
                ) { icon() }
                Text(label, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = colors.text3)
            }
            Text(
                value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = color,
                fontFamily = Baloo2FontFamily
            )
            if (sub != null) {
                Text(sub, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.text3)
            }
        }
    }
}

// Tương đương ProgressBar trong dashboard.jsx — shimmer animation lặp vô hạn
@Composable
fun ShimmerProgressBar(pct: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(9.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(Color.Black.copy(alpha = 0.05f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(pct.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(99.dp))
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.73f), color)))
        )
    }
}

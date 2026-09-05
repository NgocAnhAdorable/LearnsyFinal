package com.learnsypro.app.admin.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsypro.app.admin.ui.theme.LearnsyColors

enum class PillVariant { PRIMARY, PINK, LAV, GREEN, GHOST, DANGER }
enum class PillSize { SM, MD, LG }

// Tương đương Pill trong ui-components.jsx
@Composable
fun Pill(
    onClick: () -> Unit,
    variant: PillVariant = PillVariant.GHOST,
    size: PillSize = PillSize.SM,
    disabled: Boolean = false,
    colors: LearnsyColors,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(120),
        label = "pill-scale"
    )

    val bg: Color
    val fg: Color
    val borderColor: Color?
    when (variant) {
        PillVariant.PRIMARY -> { bg = Color.Transparent; fg = Color.White; borderColor = null }
        PillVariant.PINK -> { bg = colors.roseL; fg = colors.rose; borderColor = colors.border }
        PillVariant.LAV -> { bg = colors.lavL; fg = colors.lav; borderColor = colors.border2 }
        PillVariant.GREEN -> { bg = colors.mintL; fg = colors.mint; borderColor = Color(0xFFBBF7D0) }
        PillVariant.GHOST -> { bg = Color.Transparent; fg = colors.text3; borderColor = colors.border }
        PillVariant.DANGER -> { bg = colors.rosePale; fg = Color(0xFFEF4444); borderColor = Color(0xFFFECDD3) }
    }

    val padH: androidx.compose.ui.unit.Dp
    val padV: androidx.compose.ui.unit.Dp
    val fontSize: androidx.compose.ui.unit.TextUnit
    when (size) {
        PillSize.SM -> { padH = 12.dp; padV = 6.dp; fontSize = 12.sp }
        PillSize.MD -> { padH = 18.dp; padV = 9.dp; fontSize = 13.sp }
        PillSize.LG -> { padH = 24.dp; padV = 12.dp; fontSize = 14.sp }
    }

    var base = Modifier
        .scale(scale)

    // Tương đương boxShadow:'0 3px 12px rgba(168,85,247,0.25)' của variant primary trong web
    if (variant == PillVariant.PRIMARY) {
        base = base.shadow(
            elevation = 6.dp,
            shape = RoundedCornerShape(999.dp),
            ambientColor = Color(0xFFA855F7).copy(alpha = 0.25f),
            spotColor = Color(0xFFA855F7).copy(alpha = 0.25f)
        )
    }
    base = base.clip(RoundedCornerShape(999.dp))

    base = if (variant == PillVariant.PRIMARY) {
        base.background(colors.grad)
    } else {
        val withBg = base.background(bg)
        if (borderColor != null) withBg.border(1.5.dp, borderColor, RoundedCornerShape(999.dp)) else withBg
    }

    Row(
        modifier = modifier
            .then(base)
            .alpha(if (disabled) 0.45f else 1f)
            .clickable(interactionSource = interactionSource, indication = null, enabled = !disabled, onClick = onClick)
            .padding(horizontal = padH, vertical = padV),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides fg
        ) {
            content()
        }
    }
}

// Tương đương Fld trong ui-components.jsx — field label wrapper
@Composable
fun Fld(label: String, colors: LearnsyColors, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(bottom = 11.dp)) {
        Text(
            label.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = colors.text2,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        content()
    }
}

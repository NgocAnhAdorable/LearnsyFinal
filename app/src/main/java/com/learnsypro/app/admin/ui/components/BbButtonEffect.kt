package com.learnsypro.app.admin.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer

// Tương đương .bb-btn + .bb-clicking + @keyframes bb-marshmallow trong banh-beo-ui.css.
// Web có 2 trạng thái: hover (bb-wiggle) và click giữ (bb-marshmallow, squash 2 trục).
// Mobile không có hover thật, nên chỉ port marshmallow squash khi nhấn giữ — đó là
// phản hồi chạm quan trọng nhất, tương đương press feedback chuẩn của Material.
@Composable
fun rememberBbButtonScale(interactionSource: MutableInteractionSource): State<Float> {
    val pressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "bb-marshmallow-scale"
    )
}

fun Modifier.bbButtonPress(scale: Float): Modifier = this.scale(scale)

// Squash 2 trục thật sự, khớp sát @keyframes bb-marshmallow của web
// (0.88,1.1 → 1.14,0.88 → ... → 1,1) thay vì scale đều 1 trục như bản cũ —
// scale đều chỉ "thu nhỏ" chứ không có cảm giác "bóp marshmallow" của web.
@Composable
fun rememberBbButtonSquash(interactionSource: MutableInteractionSource): State<Pair<Float, Float>> {
    val pressed by interactionSource.collectIsPressedAsState()
    val scaleX by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "bb-marshmallow-x"
    )
    val scaleY by animateFloatAsState(
        targetValue = if (pressed) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "bb-marshmallow-y"
    )
    return remember { derivedStateOf { scaleX to scaleY } }
}

fun Modifier.bbSquash(squash: Pair<Float, Float>): Modifier = this.graphicsLayer {
    scaleX = squash.first
    scaleY = squash.second
}


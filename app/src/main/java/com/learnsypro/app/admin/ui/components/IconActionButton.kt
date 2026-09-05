package com.learnsypro.app.admin.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────
// Hệ nút icon dùng chung cho khu vực soạn bài (LessonEditorScreen + QEditor).
//
// TRƯỚC: mỗi cụm nút tự khai size/bo góc riêng — 22/26/30/32/34dp, radius
// từ CircleShape đến RoundedCornerShape(8..9.dp), một số dùng .clickable
// trần (không ripple), một số dùng IconButton (ép min-touch-target 48dp).
// Kết quả: hàng nút cạnh nhau nhìn lệch cỡ, bấm vào thì mỗi cái phản hồi
// một kiểu khác nhau (có cái không có hiệu ứng gì).
//
// SAU: 3 bậc kích thước cố định (Small/Medium/Large) dùng chung 1 tỉ lệ
// bo góc "squircle" (radius ≈ 34% cạnh), cùng 1 hành vi khi nhấn: nền đổi
// màu mượt (animateColorAsState) + ripple mềm giới hạn trong bounds + scale
// nảy nhẹ 0.94, không ép touch-target nên vẫn xếp khít trong Row hẹp.
// ─────────────────────────────────────────────────────────────────────────

enum class IconBtnSize(val box: Dp, val icon: Dp, val radius: Dp) {
    Small(26.dp, 12.dp, 9.dp),
    Medium(30.dp, 14.dp, 10.dp),
    Large(34.dp, 15.dp, 12.dp),
    XLarge(38.dp, 18.dp, 17.dp)
}

/**
 * Nút icon vuông-bo-góc (squircle) chuẩn hoá — dùng cho mọi hành động
 * "một icon, một chức năng" trong khu vực soạn bài: xoá câu hỏi, xoá ý,
 * xoá lựa chọn, thu gọn/mở rộng, chọn đáp án ✓/✗...
 *
 * @param tint màu icon khi ở trạng thái thường (không active)
 * @param activeTint nếu khác null, kèm [active]=true sẽ đổi cả nền lẫn icon —
 *   dùng cho các nút có "trạng thái được chọn" (vd. đáp án đúng ✓).
 */
@Composable
fun IconActionButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: IconBtnSize = IconBtnSize.Small,
    tint: Color,
    background: Color = Color.Transparent,
    borderColor: Color? = null,
    active: Boolean = false,
    activeTint: Color? = null,
    activeBackground: Color? = null,
    enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressSquash by rememberBbButtonSquash(interactionSource)

    val resolvedBg by animateColorAsState(
        targetValue = if (active && activeBackground != null) activeBackground else background,
        animationSpec = tween(160), label = "icon-btn-bg"
    )
    val resolvedTint by animateColorAsState(
        targetValue = if (active && activeTint != null) activeTint else tint,
        animationSpec = tween(160), label = "icon-btn-tint"
    )
    val cornerShape = shape ?: RoundedCornerShape(size.radius)
    val a11yLabel = contentDescription

    Box(
        modifier = modifier
            .size(size.box)
            .bbSquash(pressSquash)
            .clip(cornerShape)
            .background(resolvedBg)
            .then(
                if (borderColor != null)
                    Modifier.border(1.5.dp, borderColor, cornerShape)
                else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick
            )
            .semantics {
                if (a11yLabel != null) {
                    this.contentDescription = a11yLabel
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = resolvedTint, modifier = Modifier.size(size.icon))
    }
}

/**
 * Nút "pill" chuẩn hoá cho toolbar ngang (Soạn / Toolbox / Lưu / trạng thái...)
 * — cùng chiều cao, cùng padding, cùng hành vi nhấn với [IconActionButton],
 * chỉ khác là có icon + label + (tuỳ chọn) icon phụ (chevron) và bo góc pill.
 */
@Composable
fun IconPillButton(
    icon: ImageVector?,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color,
    background: Color,
    borderColor: Color,
    trailingIcon: ImageVector? = null,
    iconSize: Dp = 13.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressSquash by rememberBbButtonSquash(interactionSource)
    val shape = RoundedCornerShape(999.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .heightIn(min = IconBtnSize.Large.box)
            .bbSquash(pressSquash)
            .clip(shape)
            .background(background)
            .border(1.5.dp, borderColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize))
            Spacer(Modifier.width(5.dp))
        }
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Black, color = tint)
        if (trailingIcon != null) {
            Icon(trailingIcon, null, tint = tint, modifier = Modifier.size(iconSize + 1.dp).padding(start = 1.dp))
        }
    }
}

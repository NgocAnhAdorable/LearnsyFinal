package com.learnsypro.app.admin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.learnsypro.app.admin.ui.theme.LearnsyColors

// Tương đương confirm-dialog.jsx bên web — trước đây mỗi màn hình (Student, Lesson,
// File, Listening, Vocabulary, Results, Settings...) tự khai AlertDialog() mặc định
// của Material3: hình chữ nhật bo góc nhỏ, không icon badge, nút chữ nhật màu
// primary hệ thống — hoàn toàn khác phong cách "bánh bèo" (card bo tròn 24dp, icon
// badge màu theo hành động, nút pill bo tròn hết cỡ) mà web dùng nhất quán cho MỌI
// hộp thoại xác nhận. Gộp về 1 component dùng chung để đồng nhất toàn app.

enum class ConfirmIconType { ADD, COPY, DELETE, KEY, WARN }

private fun iconFor(type: ConfirmIconType): ImageVector = when (type) {
    ConfirmIconType.ADD -> Icons.Default.AutoAwesome
    ConfirmIconType.COPY -> Icons.Default.ContentCopy
    ConfirmIconType.DELETE -> Icons.Default.Delete
    ConfirmIconType.KEY -> Icons.Default.Key
    ConfirmIconType.WARN -> Icons.Default.Warning
}

/**
 * Hộp thoại xác nhận kawaii dùng chung — thay thế mọi AlertDialog() mặc định.
 *
 * @param iconType loại icon badge ở đầu dialog (mặc định warn, giống web)
 * @param confirmColor màu chủ đạo cho icon badge + nút xác nhận (mặc định đỏ #EF4444
 *   giống hành động xoá — đúng default của web)
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    colors: LearnsyColors,
    dark: Boolean,
    confirmLabel: String = "Xác nhận",
    dismissLabel: String = "Huỷ",
    confirmColor: Color = Color(0xFFEF4444),
    iconType: ConfirmIconType = ConfirmIconType.WARN
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (dark) Color(0xFF0C0412).copy(alpha = 0.88f) else Color(0xFFFFF0F8).copy(alpha = 0.82f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 310.dp)
                    .fillMaxWidth()
                    .shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = Color(0xFFA855F7).copy(alpha = 0.18f),
                        spotColor = Color(0xFFA855F7).copy(alpha = 0.18f)
                    )
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (dark) Color(0xFF261018).copy(alpha = 0.98f) else Color.White)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // chặn click xuyên qua card xuống scrim
                    )
                    .padding(top = 28.dp, start = 22.dp, end = 22.dp, bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon badge
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(confirmColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(iconFor(iconType), null, tint = confirmColor, modifier = Modifier.size(32.dp))
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = colors.text,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                if (!message.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        message,
                        fontSize = 13.sp,
                        color = colors.text3,
                        lineHeight = 21.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                Spacer(Modifier.height(22.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    // Nút Huỷ — pill viền, nền trong suốt
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.Transparent)
                            .then(Modifier.border(1.5.dp, colors.border, RoundedCornerShape(999.dp)))
                            .clickable(onClick = onDismiss)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(dismissLabel, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = colors.text3)
                    }
                    // Nút xác nhận — pill nền màu, chữ trắng, shadow màu theo confirmColor
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(999.dp),
                                ambientColor = confirmColor.copy(alpha = 0.35f),
                                spotColor = confirmColor.copy(alpha = 0.35f)
                            )
                            .clip(RoundedCornerShape(999.dp))
                            .background(confirmColor)
                            .clickable(onClick = { onConfirm(); onDismiss() })
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(confirmLabel, fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                }
            }
        }
    }
}

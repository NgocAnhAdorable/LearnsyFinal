package com.learnsypro.app.admin.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.learnsypro.app.admin.ui.ToastCenter
import com.learnsypro.app.admin.ui.ToastMessage
import kotlinx.coroutines.delay

// Tương đương DiToast trong dashboard.jsx — pill morph từ tròn nhỏ thành pill dài.
// animateDpAsState thay cho @keyframes di-pill-in/out.
@Composable
fun DiToastHost() {
    val current = ToastCenter.queue.firstOrNull()
    if (current != null) {
        DiToast(msg = current, onClose = { ToastCenter.dismiss(current.id) })
    }
    val undo = ToastCenter.undoQueue.firstOrNull()
    if (undo != null) {
        UndoToast(entry = undo)
    }
}

// Tương đương showToastWithUndo() trong toast.jsx — pill có nút Hoàn tác + progress bar
@Composable
private fun UndoToast(entry: com.learnsypro.app.admin.ui.UndoEntry) {
    var progress by remember(entry.id) { mutableStateOf(1f) }
    LaunchedEffect(entry.id) {
        val steps = 50
        repeat(steps) {
            delay(5000L / steps)
            progress = 1f - (it + 1).toFloat() / steps
        }
        ToastCenter.commitUndo(entry.id)
    }

    Popup(alignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .padding(top = 12.dp)
                .width(280.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0E060E).copy(alpha = 0.97f))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFFF59E0B).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Delete, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp))
                }
                Text(entry.msg, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF0E6FF), modifier = Modifier.weight(1f), maxLines = 1)
                Text(
                    "Hoàn tác", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF60A5FA),
                    modifier = Modifier.clickable { ToastCenter.cancelUndo(entry.id) }
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = 0.1f))) {
                Box(modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().clip(RoundedCornerShape(99.dp)).background(Color(0xFFF59E0B)))
            }
        }
    }
}

@Composable
private fun DiToast(msg: ToastMessage, onClose: () -> Unit) {
    // entered: bắt đầu false (pill nhỏ tròn) rồi bật true ngay sau frame đầu tiên —
    // để animateDpAsState có 1 sự thay đổi giá trị thật sự để animate từ nhỏ->to.
    // Nếu chỉ dùng 1 biến "leaving" như trước, Popup xuất hiện lần đầu đã ở target
    // width=268dp ngay lập tức (animateDpAsState không animate initial composition),
    // nên toast hiện to luôn rồi mới co lại khi đóng — thiếu hẳn hiệu ứng mở ra.
    var entered by remember(msg.id) { mutableStateOf(false) }
    var leaving by remember(msg.id) { mutableStateOf(false) }
    val expanded = entered && !leaving
    val width by animateDpAsState(
        targetValue = if (expanded) 268.dp else 36.dp,
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "toast-width"
    )
    val height by animateDpAsState(
        targetValue = if (expanded) 54.dp else 36.dp,
        animationSpec = tween(450),
        label = "toast-height"
    )

    // Mở ra ngay sau khi vào cây UI, hiện trong 2.8s, rồi thu lại 0.45s trước khi đóng
    // hẳn — khớp đúng trình tự "mở từ giữa -> hiện -> thu về giữa -> mất" của bản web.
    LaunchedEffect(msg.id) {
        // withFrameNanos đảm bảo entered=true xảy ra ở frame render KẾ TIẾP, không
        // cùng lúc với composition đầu tiên — nếu set ngay lập tức có nguy cơ Compose
        // gộp chung thành 1 frame duy nhất và animateDpAsState bỏ qua animation.
        androidx.compose.runtime.withFrameNanos {}
        entered = true
        delay(2800)
        leaving = true
        delay(450)
        onClose()
    }

    Popup(alignment = Alignment.TopCenter) {
        Row(
            modifier = Modifier
                .padding(top = 12.dp)
                .width(width)
                .height(height)
                .clip(RoundedCornerShape(27.dp))
                .background(Color(0xFF0E060E).copy(alpha = 0.97f))
                .clickable { leaving = true }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            if (expanded) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(msg.color.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(iconFor(msg.icon), null, tint = msg.color, modifier = Modifier.size(15.dp))
                }
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        msg.sublabel.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = msg.color
                    )
                    Text(
                        msg.label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFF0E6FF),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// Tương đương DiToastIcon() — map emoji sang icon Material tương đương.
private fun iconFor(icon: String) = when (icon) {
    "✅", "✓", "☑️" -> Icons.Default.Check
    "❌", "✗", "🚫" -> Icons.Default.Close
    "⚠️", "⚠", "🔔" -> Icons.Default.Warning
    "ℹ️", "ℹ" -> Icons.Default.Info
    "💾", "📥" -> Icons.Default.Save
    "📤", "📦", "⬆️" -> Icons.Default.Upload
    "📋", "📄", "📝" -> Icons.Default.Description
    "🗑️", "🗑", "🚮" -> Icons.Default.Delete
    "⭐", "🌟", "🏆" -> Icons.Default.Star
    "🔄", "↩️", "↻" -> Icons.Default.Refresh
    "⚙️", "⚙", "🔧" -> Icons.Default.Settings
    else -> Icons.Default.AutoAwesome
}

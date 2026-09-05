package com.learnsypro.app.admin.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import java.util.UUID

// Tương đương useDiToast() / DiToast trong dashboard.jsx — toast kiểu Dynamic Island.
data class ToastMessage(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val icon: String = "✨",
    val color: Color = Color(0xFFF472B6),
    val sublabel: String = "Learnsy Admin"
)

// Tương đương showToastWithUndo() / cancelUndo() / commitUndo() trong toast.jsx
data class UndoEntry(
    val id: String,
    val msg: String,
    val onUndo: () -> Unit,
    val onCommit: () -> Unit
)

// Singleton queue đơn giản — thay cho window._adminToast global trong JS.
// Composable ToastHost quan sát queue này và hiển thị từng cái một.
object ToastCenter {
    val queue = mutableStateListOf<ToastMessage>()
    val undoQueue = mutableStateListOf<UndoEntry>()

    // Tương đương window._toastRecentMsgs — debounce 500ms cùng nội dung
    private val recentMsgs = mutableMapOf<String, Long>()

    fun show(label: String, icon: String = "✨", color: Color = Color(0xFFF472B6)) {
        val now = System.currentTimeMillis()
        if (now - (recentMsgs[label] ?: 0) < 500) return
        recentMsgs[label] = now
        if (recentMsgs.size > 20) {
            recentMsgs.entries.removeAll { now - it.value > 2000 }
        }
        queue.add(ToastMessage(label = label, icon = icon, color = color))
    }

    // Tương đương showToast(msg,'auto') — tự nhận loại từ từ khoá tiếng Việt/Anh
    fun showAuto(label: String) {
        val m = label.lowercase()
        val (icon, color) = when {
            Regex("lỗi|thất bại|không thể|error|failed|từ chối|sai|invalid").containsMatchIn(m) ->
                "❌" to Color(0xFFEF4444)
            Regex("cảnh báo|chú ý|warn|vui lòng|thiếu|chưa").containsMatchIn(m) ->
                "⚠️" to Color(0xFFF59E0B)
            Regex("thành công|đã lưu|đã thêm|đã xóa|đã cập nhật|đã tạo|đã bật|đã tắt|đã mở|đã đóng|đã gửi|đã reset|đã sao chép|ok|hoàn tất|saved|success").containsMatchIn(m) ->
                "✅" to Color(0xFF10B981)
            else -> "ℹ️" to Color(0xFFA855F7)
        }
        show(label, icon, color)
    }

    fun dismiss(id: String) {
        queue.removeAll { it.id == id }
    }

    // Tương đương showToastWithUndo() — hiện toast kèm nút Hoàn tác, tự commit sau delay
    fun showWithUndo(msg: String, onUndo: () -> Unit, onCommit: () -> Unit) {
        val id = UUID.randomUUID().toString()
        undoQueue.add(UndoEntry(id, msg, onUndo, onCommit))
    }

    fun cancelUndo(id: String) {
        val entry = undoQueue.find { it.id == id } ?: return
        undoQueue.removeAll { it.id == id }
        entry.onUndo()
        showAuto("Đã hoàn tác!")
    }

    fun commitUndo(id: String) {
        val entry = undoQueue.find { it.id == id } ?: return
        undoQueue.removeAll { it.id == id }
        entry.onCommit()
    }
}

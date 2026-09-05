package com.learnsypro.app.filemanager.util

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.learnsypro.app.databinding.DialogProgressBinding
import java.util.Locale

/**
 * Dialog tiến trình dùng chung cho các thao tác chạy lâu và có thể tính % (nén, giải nén,
 * sao chép/di chuyển file). Hiện % hoàn thành + thời gian đã trôi qua dạng mm:ss, tự cập nhật
 * mỗi 200ms bằng Handler trên main thread — an toàn khi gọi update() liên tục từ luồng IO
 * (mọi lệnh gọi được post lên main thread bên trong).
 */
class ProgressDialogHelper(activity: Activity, titleRes: Int) {

    private val binding = DialogProgressBinding.inflate(activity.layoutInflater)
    private val dialog = MaterialAlertDialogBuilder(activity)
        .setView(binding.root)
        .setCancelable(false)
        .create()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val startTime = System.currentTimeMillis()

    init {
        binding.tvProgressTitle.setText(titleRes)
        binding.tvProgressPercent.text = "0%"
        binding.tvProgressTime.text = "00:00"
        dialog.show()
    }

    /** Cập nhật tên file hiện đang xử lý (hiện dưới tiêu đề), gọi an toàn từ bất kỳ luồng nào. */
    fun setCurrentFile(name: String) {
        mainHandler.post {
            if (dialog.isShowing) binding.tvProgressFilename.text = name
        }
    }

    /** Cập nhật % dựa trên [done]/[total] byte, gọi an toàn từ bất kỳ luồng nào (kể cả luồng IO). */
    fun update(done: Long, total: Long) {
        val percent = if (total > 0) ((done.toDouble() / total) * 100).toInt().coerceIn(0, 100) else 0
        mainHandler.post {
            if (!dialog.isShowing) return@post
            binding.progressBar.progress = percent
            binding.tvProgressPercent.text = "$percent%"
            binding.tvProgressTime.text = formatElapsed(System.currentTimeMillis() - startTime)
        }
    }

    /**
     * Cập nhật % + tốc độ mạng + thời gian còn lại (ETA) dựa trên tiến trình byte THẬT của 1
     * lần upload cloud (xem ProgressRequestBody/MediaHttpUploaderProgressListener) — khác
     * update() ở trên (dùng cho nén/copy/move nội bộ, hiện thời gian ĐÃ TRÔI QUA vì không có
     * khái niệm tốc độ mạng). [etaSeconds] null = chưa đủ dữ liệu để ước lượng (vài trăm ms đầu
     * tiên của lần tải) — lúc đó chỉ hiện tốc độ, chưa hiện "Còn lại", đúng cách các app tải lên
     * khác xử lý giai đoạn đầu thay vì hiện "Còn lại 00:00" gây hiểu lầm là sắp xong ngay.
     */
    fun updateBytes(bytesSent: Long, totalBytes: Long, speedBytesPerSec: Double, etaSeconds: Long?) {
        val percent = if (totalBytes > 0) ((bytesSent.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 100) else 0
        mainHandler.post {
            if (!dialog.isShowing) return@post
            binding.progressBar.progress = percent
            binding.tvProgressPercent.text = "$percent%"
            // Dòng chính (tv_progress_time): ETA — "còn bao lâu", giống app tải lên khác ưu
            // tiên hiện trong lúc tác vụ đang chạy, thay vì elapsed time của update() thường.
            binding.tvProgressTime.text = if (etaSeconds != null) formatElapsed(etaSeconds * 1000) else formatElapsed(System.currentTimeMillis() - startTime)
            // Dòng phụ (tv_progress_speed): CHỈ tốc độ mạng — ETA đã có ở dòng chính, không lặp
            // lại ở đây để tránh 2 chỗ cùng hiện 1 con số "còn lại" gây rối mắt.
            binding.tvProgressSpeed.visibility = android.view.View.VISIBLE
            binding.tvProgressSpeed.text = formatSpeed(speedBytesPerSec)
        }
    }

    fun dismiss() {
        mainHandler.post { if (dialog.isShowing) dialog.dismiss() }
    }

    private fun formatElapsed(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    /** "1,2 MB/s" kiểu — cùng cách chia bậc 1024 các nơi khác trong app đã dùng cho dung lượng file. */
    private fun formatSpeed(bytesPerSec: Double): String {
        if (bytesPerSec < 1024) return String.format(Locale.getDefault(), "%.0f B/s", bytesPerSec)
        val units = arrayOf("KB/s", "MB/s", "GB/s")
        var value = bytesPerSec / 1024.0
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.size - 1) {
            value /= 1024.0
            unitIndex++
        }
        return String.format(Locale.getDefault(), "%.1f %s", value, units[unitIndex])
    }
}

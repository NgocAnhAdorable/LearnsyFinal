package com.learnsypro.app.filemanager.cloud

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.Sink
import okio.buffer

/**
 * Tiến trình upload thật theo byte — khác [totalFileCount]/[uploadedIndex] trước đây
 * (CategoryFilesActivity.performUploadToCloud), vốn chỉ đếm SỐ FILE đã xong/tổng số file. Với
 * 1 file duy nhất, cặp đó luôn là 0/1 trong lúc truyền rồi bụp thẳng 1/1 khi request trả về —
 * đúng lỗi "0% rồi nhảy thẳng 100%" đang gặp. [bytesSent]/[totalBytes] ở đây đến từ chính
 * OkHttp trong lúc ghi dữ liệu lên socket, nên phản ánh đúng % thật đã gửi lên mạng.
 *
 * [speedBytesPerSec] và [etaSeconds] (null nếu chưa đủ dữ liệu để ước lượng) để UI hiện thêm
 * tốc độ mạng + thời gian còn lại, giống các app tải lên khác — tính bằng tổng
 * byte/tổng thời gian trôi qua kể từ khi bắt đầu ghi (đơn giản, đủ chính xác cho hiển thị UI,
 * không cần cửa sổ trượt phức tạp).
 */
fun interface UploadProgressListener {
    fun onProgress(bytesSent: Long, totalBytes: Long, speedBytesPerSec: Double, etaSeconds: Long?)
}

/**
 * Bọc quanh 1 [RequestBody] thật (file đọc từ đĩa) để báo tiến trình ghi byte lên mạng qua
 * [listener]. Việc đếm byte xảy ra Ở TẦNG OkHttp (Sink.write), tức là byte ĐÃ THỰC SỰ được đẩy
 * vào socket gửi đi — không phải chỉ đọc xong từ đĩa (đọc từ đĩa gần như tức thời so với gửi
 * qua mạng di động/wifi yếu, nên đếm ở đây phản ánh đúng tốc độ mạng thật hơn nhiều).
 *
 * throttleMs: chỉ gọi listener tối đa 1 lần/khoảng thời gian này (mặc định 200ms, khớp tần suất
 * cập nhật UI của ProgressDialogHelper) — tránh gọi callback dồn dập hàng nghìn lần/giây với
 * file lớn trên mạng nhanh, gây nghẽn Handler.post() phía UI một cách không cần thiết.
 */
class ProgressRequestBody(
    private val delegate: RequestBody,
    private val listener: UploadProgressListener,
    private val throttleMs: Long = 200L
) : RequestBody() {

    override fun contentType(): MediaType? = delegate.contentType()
    override fun contentLength(): Long = delegate.contentLength()
    override fun isOneShot(): Boolean = delegate.isOneShot()

    override fun writeTo(sink: BufferedSink) {
        val countingSink = CountingSink(sink)
        val bufferedCountingSink = countingSink.buffer()
        delegate.writeTo(bufferedCountingSink)
        bufferedCountingSink.flush()
    }

    private inner class CountingSink(delegate: Sink) : ForwardingSink(delegate) {
        private var bytesWritten = 0L
        private val startTimeMs = System.currentTimeMillis()
        private var lastReportMs = 0L
        private val totalBytes = contentLength()

        override fun write(source: Buffer, byteCount: Long) {
            super.write(source, byteCount)
            bytesWritten += byteCount
            val now = System.currentTimeMillis()
            val isDone = bytesWritten >= totalBytes
            // Luôn báo lần cuối (isDone) dù chưa đủ throttleMs kể từ lần báo trước — nếu không,
            // dialog có thể dừng lại ở vd 98% (lần báo áp chót) và không bao giờ thấy đúng 100%
            // thật cho tới khi request hoàn tất hẳn (khoảng trễ nhỏ nhưng gây cảm giác "kẹt").
            if (isDone || now - lastReportMs >= throttleMs) {
                lastReportMs = now
                val elapsedSec = (now - startTimeMs) / 1000.0
                val speed = if (elapsedSec > 0) bytesWritten / elapsedSec else 0.0
                val remainingBytes = (totalBytes - bytesWritten).coerceAtLeast(0)
                val eta = if (speed > 0) (remainingBytes / speed).toLong() else null
                listener.onProgress(bytesWritten, totalBytes, speed, eta)
            }
        }
    }
}

package com.learnsypro.app.filemanager.streamproxy

import com.learnsypro.app.filemanager.util.LogBus
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Server HTTP nội bộ (chỉ lắng nghe 127.0.0.1, KHÔNG phải LAN như MediaStreamServer) — cầu nối
 * duy nhất giữa ExoPlayer và mọi nguồn từ xa (FTP/SFTP/SMB/Cloud). ExoPlayer chỉ cần biết 1 URL
 * dạng http://127.0.0.1:PORT/stream?src=<sourceId> — không còn custom DataSource riêng cho
 * từng giao thức (FtpDataSource/SftpDataSource/SmbDataSource/CloudMediaUri cũ đều bị thay thế
 * hoàn toàn bởi kiến trúc này, xem ghi chú xoá ở cuối quá trình refactor).
 *
 * Vì sao proxy qua HTTP nội bộ thay vì custom DataSource: (1) ExoPlayer's HTTP DataSource đã
 * xử lý sẵn Range/retry/buffer — không cần tự viết lại logic đó cho từng giao thức; (2) dễ
 * debug — log HTTP request/response chuẩn, xem được bằng bất kỳ công cụ nào; (3) 1 nơi DUY
 * NHẤT xử lý HTTP Range đúng chuẩn (206 Partial Content), thay vì rải rác đúng-sai khác nhau
 * ở mỗi *DataSource cũ.
 *
 * Vòng đời: registry ánh xạ sourceId -> RemoteSource, ĐĂNG KÝ khi bắt đầu phát 1 file
 * (register()), TỰ DỌN khi han hết TTL không có request nào tới (một số lần phát không đóng
 * sạch phía ExoPlayer khi app bị kill đột ngột) — không cần Activity nào gọi unregister() thủ
 * công để đảm bảo đúng, dù vẫn có unregister() cho trường hợp dọn chủ động (đóng player bình
 * thường).
 */
class LocalStreamServer private constructor() : NanoHTTPD("127.0.0.1", PORT) {

    private val sources = ConcurrentHashMap<String, RegisteredSource>()

    private data class RegisteredSource(val source: RemoteSource, val registeredAtMs: Long)

    /** Đăng ký 1 RemoteSource, trả về URL nội bộ ExoPlayer sẽ dùng làm MediaItem URI. */
    fun register(source: RemoteSource): String {
        sources[source.sourceId] = RegisteredSource(source, System.currentTimeMillis())
        sweepExpired()
        return "http://127.0.0.1:$PORT/stream?src=${source.sourceId}"
    }

    /** Dọn chủ động khi Activity/Service biết chắc sourceId này không còn cần nữa. */
    fun unregister(sourceId: String) {
        sources.remove(sourceId)?.source?.release()
    }

    /** Xoá các source đăng ký quá lâu (TTL) mà chưa từng bị unregister() — dọn rác trường hợp
     *  ExoPlayer/Activity bị kill đột ngột không kịp gọi unregister(). Chạy mỗi lần register()
     *  mới thay vì có riêng 1 thread định kỳ — đơn giản, đủ dùng vì server chỉ sống trong lúc
     *  app đang mở, không tích tụ rác qua nhiều phiên dùng app. */
    private fun sweepExpired() {
        val now = System.currentTimeMillis()
        val expired = sources.entries.filter { now - it.value.registeredAtMs > SOURCE_TTL_MS }
        expired.forEach { (id, entry) ->
            entry.source.release()
            sources.remove(id)
        }
    }

    override fun serve(session: IHTTPSession): Response {
        if (session.uri != "/stream") {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        }
        val sourceId = session.parameters["src"]?.firstOrNull()
            ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing src")
        val entry = sources[sourceId]
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Unknown source")

        val rangeHeader = session.headers["range"]
        val requestedOffset = parseRangeOffset(rangeHeader)

        // openStream() là suspend (thao tác mạng: mở control channel FTP/SFTP/SMB hoặc gọi API
        // cloud) nhưng NanoHTTPD.serve() chạy trên thread pool riêng của chính nó (mỗi request 1
        // thread, KHÔNG phải main thread) — runBlocking ở đây AN TOÀN, không có nguy cơ ANR như
        // gọi runBlocking trên main thread thường gặp ở nơi khác trong app.
        val streamResult = runBlocking { entry.source.openStream(requestedOffset) }
        val stream = streamResult.getOrElse { e ->
            LogBus.error("LocalStreamServer: mở stream thất bại cho '${entry.source.displayName}': ${e.message}", source = "STREAM")
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Stream error: ${e.message}")
        }

        val remainingLength = if (stream.totalSize >= 0) stream.totalSize - requestedOffset else -1L

        // NanoHTTPD tự đóng InputStream nó nhận sau khi gửi xong response (kể cả khi client
        // ngắt giữa chừng, ví dụ người dùng thoát player/seek sang offset khác trước khi đọc
        // hết) — nhưng closeAll() của RemoteSource còn phải dọn thêm control channel/session
        // bên dưới, không chỉ tự bản thân InputStream. Bọc lại để close() gọi cả 2, thay vì
        // sửa từng RemoteSource implementation phải tự nhớ làm việc này đúng thứ tự.
        val wrappedStream = ClosingInputStream(stream.input, stream.closeAll)

        val response = if (rangeHeader != null && stream.totalSize >= 0) {
            val resp = newFixedLengthResponse(
                Response.Status.PARTIAL_CONTENT, stream.mimeType, wrappedStream, remainingLength
            )
            resp.addHeader("Content-Range", "bytes $requestedOffset-${stream.totalSize - 1}/${stream.totalSize}")
            resp.addHeader("Accept-Ranges", "bytes")
            resp
        } else if (stream.totalSize >= 0) {
            val resp = newFixedLengthResponse(Response.Status.OK, stream.mimeType, wrappedStream, stream.totalSize)
            resp.addHeader("Accept-Ranges", "bytes")
            resp
        } else {
            // Không biết trước tổng kích thước (hiếm) — trả chunked, ExoPlayer vẫn phát được
            // nhưng KHÔNG tua được (không có Content-Length để tính vị trí phần trăm).
            newChunkedResponse(Response.Status.OK, stream.mimeType, wrappedStream)
        }
        return response
    }

    /** InputStream bọc: đóng luồng gốc rồi LUÔN gọi thêm closeAll() (dọn control channel/
     *  session bên dưới) — dùng try/finally để closeAll() vẫn chạy dù input.close() ném lỗi
     *  (kết nối đã rớt sẵn từ phía server từ xa). onClose chỉ được gọi 1 LẦN dù close() bị gọi
     *  nhiều lần (NanoHTTPD có thể gọi close() nhiều hơn 1 lần trong 1 số luồng lỗi). */
    private class ClosingInputStream(
        private val delegate: InputStream,
        private val onClose: () -> Unit
    ) : InputStream() {
        private val closed = java.util.concurrent.atomic.AtomicBoolean(false)
        override fun read(): Int = delegate.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)
        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            try {
                delegate.close()
            } finally {
                onClose()
            }
        }
    }

    /** "bytes=1234-" -> 1234. Không hỗ trợ range dạng "bytes=-500" (500 byte cuối) — ExoPlayer
     *  không dùng dạng này khi phát tuần tự/seek tới offset cụ thể, chỉ trình duyệt tải file
     *  thủ công mới hay dùng, ngoài phạm vi cần cho trường hợp này. */
    private fun parseRangeOffset(rangeHeader: String?): Long {
        if (rangeHeader == null) return 0L
        val match = Regex("""bytes=(\d+)-""").find(rangeHeader) ?: return 0L
        return match.groupValues[1].toLongOrNull() ?: 0L
    }

    companion object {
        private const val PORT = 48219 // cổng cao, ít khả năng đụng app khác trên máy
        private const val SOURCE_TTL_MS = 30 * 60 * 1000L // 30 phút

        @Volatile private var instance: LocalStreamServer? = null

        /** Lấy instance dùng chung toàn app — chỉ khởi động NanoHTTPD 1 lần, tái dùng cho mọi
         *  lần phát media (audio/video) từ FTP/SFTP/SMB/Cloud xuyên suốt vòng đời app. */
        fun getInstance(): LocalStreamServer = instance ?: synchronized(this) {
            instance ?: LocalStreamServer().also {
                it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                instance = it
            }
        }
    }
}

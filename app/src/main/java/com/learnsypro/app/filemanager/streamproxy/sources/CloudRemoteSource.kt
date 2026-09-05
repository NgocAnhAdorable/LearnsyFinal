package com.learnsypro.app.filemanager.streamproxy.sources

import com.learnsypro.app.filemanager.cloud.CloudFileService
import com.learnsypro.app.filemanager.model.RemoteFile
import com.learnsypro.app.filemanager.streamproxy.RemoteSource
import com.learnsypro.app.filemanager.streamproxy.RemoteStream
import com.learnsypro.app.filemanager.util.LogBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLConnection
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * RemoteSource cho cloud (Google Drive/Dropbox/Box) — bọc getStreamRequest() của
 * CloudFileService (URL + header xác thực đã sẵn có, xem CloudFileService.kt) thành 1
 * RemoteSource chuẩn, đi qua ĐÚNG pipeline LocalStreamServer chung với FTP/SFTP/SMB, thay vì
 * đường riêng (CloudMediaUri/DefaultHttpDataSource.setDefaultRequestProperties trước đây) — lý
 * do gộp chung: (1) chỉ 1 nơi xử lý HTTP Range/log/lỗi cho MỌI nguồn, dễ debug hơn nhiều so với
 * mỗi loại nguồn tự xử lý Range theo cách riêng; (2) cache_dir không còn khả năng bị ExoPlayer
 * hay bất kỳ tầng nào âm thầm ghi cả file xuống — chỉ có duy nhất RemoteStream.input được đọc
 * tuần tự rồi vứt, y hệt FTP.
 *
 * openStream() được gọi LẠI mỗi lần ExoPlayer seek — tự gửi request HTTP mới với header Range
 * tương ứng, KHÔNG tải/giữ toàn bộ file trong RAM hay đĩa ở bất kỳ bước nào.
 */
class CloudRemoteSource(
    private val service: CloudFileService,
    private val file: RemoteFile,
    override val displayName: String
) : RemoteSource {

    override val sourceId: String = UUID.randomUUID().toString()

    override suspend fun openStream(offset: Long): Result<RemoteStream> = withContext(Dispatchers.IO) {
        try {
            val streamRequest = service.getStreamRequest(file)
                ?: return@withContext Result.failure(
                    IOException("Provider không hỗ trợ stream trực tiếp file này (có thể là Google Docs/Sheets/Slides gốc, cần export trước)")
                )
            val (url, headers) = streamRequest

            val requestBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            if (offset > 0) {
                requestBuilder.addHeader("Range", "bytes=$offset-")
            }

            val call = httpClient.newCall(requestBuilder.build())
            val response = call.execute()

            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                LogBus.error("CloudRemoteSource: HTTP $code khi mở '$displayName'", source = "STREAM")
                return@withContext Result.failure(IOException("Cloud trả lỗi HTTP $code"))
            }

            val body = response.body ?: run {
                response.close()
                return@withContext Result.failure(IOException("Phản hồi rỗng từ cloud"))
            }

            // Content-Length của RESPONSE (sau khi trừ offset nếu có Range) khác với TỔNG kích
            // thước file thật — RemoteStream.totalSize cần là tổng kích thước file GỐC để
            // LocalStreamServer tính đúng header Content-Range (bytes offset-total/TOTAL), nên
            // phải cộng lại offset đã trừ khi provider trả 206 Partial Content.
            val contentLength = body.contentLength()
            val totalSize = if (response.code == 206 && contentLength >= 0) {
                contentLength + offset
            } else if (contentLength >= 0) {
                contentLength
            } else {
                -1L
            }

            val mimeType = response.header("Content-Type")
                ?: URLConnection.guessContentTypeFromName(file.name)
                ?: "application/octet-stream"

            Result.success(
                RemoteStream(
                    input = body.byteStream(),
                    totalSize = totalSize,
                    mimeType = mimeType,
                    closeAll = {
                        try {
                            response.close()
                        } catch (e: Exception) {
                            // Đóng lỗi (kết nối đã rớt sẵn) không quan trọng, bỏ qua.
                        }
                    }
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun release() {
        // Mỗi openStream() tự đóng response của chính nó qua closeAll() — không có gì thêm để dọn.
    }

    companion object {
        // Client dùng chung cho MỌI lần stream cloud — kết nối HTTP giữ ấm (connection pool)
        // giữa các lần mở, tránh handshake TLS lại từ đầu mỗi lần seek liên tục.
        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

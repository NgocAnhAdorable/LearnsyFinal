package com.learnsypro.app.filemanager.streamproxy.sources

import com.learnsypro.app.filemanager.model.FtpConnectionProfile
import com.learnsypro.app.filemanager.streamproxy.RemoteSource
import com.learnsypro.app.filemanager.streamproxy.RemoteStream
import com.learnsypro.app.filemanager.util.LogBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPClientConfig
import org.apache.commons.net.ftp.FTPReply
import java.io.IOException
import java.net.URLConnection
import java.util.UUID

/**
 * RemoteSource cho FTP — mỗi lần openStream() mở 1 kết nối FTPClient HOÀN TOÀN RIÊNG (không
 * dùng chung control channel với FtpClientManager đang duyệt thư mục trên UI, và không dùng
 * chung giữa các lần seek khác nhau trong cùng 1 lần phát) — 1 control channel FTP chỉ xử lý
 * đúng 1 lệnh RETR tại 1 thời điểm, dùng chung sẽ xung đột với duyệt file hoặc với chính lần
 * seek trước đó chưa đóng kịp.
 */
class FtpRemoteSource(
    private val profile: FtpConnectionProfile,
    private val remotePath: String,
    override val displayName: String
) : RemoteSource {

    override val sourceId: String = UUID.randomUUID().toString()

    override suspend fun openStream(offset: Long): Result<RemoteStream> = withContext(Dispatchers.IO) {
        try {
            val ftp = FTPClient()
            // UTF-8 control encoding + PASV bắt buộc, cùng lý do đã ghi chú chi tiết ở
            // FtpClientManager.kt (mojibake tên file có dấu, và Active Mode/PORT không hoạt
            // động khi client nằm sau NAT/router gia dụng).
            ftp.controlEncoding = "UTF-8"
            ftp.configure(FTPClientConfig(FTPClientConfig.SYST_UNIX))
            ftp.setConnectTimeout(8000)
            ftp.connect(profile.host, profile.port)
            ftp.tcpNoDelay = true

            if (!FTPReply.isPositiveCompletion(ftp.replyCode)) {
                ftp.disconnect()
                return@withContext Result.failure(IOException("Máy chủ FTP từ chối kết nối (mã ${ftp.replyCode})"))
            }
            if (!ftp.login(profile.username, profile.password)) {
                ftp.disconnect()
                return@withContext Result.failure(IOException("Sai tài khoản/mật khẩu FTP"))
            }
            ftp.enterLocalPassiveMode()
            // Cùng lý do đã ghi chú chi tiết ở FtpClientManager.kt: ép dùng lại đúng
            // profile.host thay vì tin theo IP router trả về trong phản hồi PASV — khắc phục
            // lỗi Passive Mode không ổn định trên router/NAS gia dụng.
            ftp.setPassiveNatWorkaroundStrategy { profile.host }
            ftp.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE)
            ftp.bufferSize = 1024 * 256

            // Lấy kích thước file TRƯỚC khi mở RETR — 1 control channel chỉ xử lý được 1 lệnh
            // "đang tiến hành" tại 1 thời điểm, gọi SIZE sau khi đã RETR sẽ vi phạm giao thức.
            val fileSize = ftp.getSize(remotePath)?.toLongOrNull() ?: -1L

            if (offset > 0) {
                ftp.setRestartOffset(offset)
            }
            val rawStream = ftp.retrieveFileStream(remotePath)
            if (rawStream == null) {
                val reply = ftp.replyString.trim()
                ftp.disconnect()
                LogBus.error("FtpRemoteSource: retrieveFileStream trả null cho '$remotePath', reply: $reply", source = "STREAM")
                return@withContext Result.failure(IOException("Không mở được file trên server FTP (reply: $reply)"))
            }

            val mimeType = URLConnection.guessContentTypeFromName(remotePath) ?: "application/octet-stream"

            Result.success(
                RemoteStream(
                    input = rawStream,
                    totalSize = fileSize,
                    mimeType = mimeType,
                    closeAll = {
                        try {
                            // completePendingCommand() BẮT BUỘC gọi sau khi đóng data stream của
                            // FTP để control channel thoát trạng thái "dở dang" trước disconnect().
                            ftp.completePendingCommand()
                        } catch (e: Exception) {
                            // Bỏ qua — quan trọng là disconnect() bên dưới luôn chạy.
                        }
                        try {
                            ftp.disconnect()
                        } catch (e: Exception) {
                            // Ngắt kết nối lỗi không được phép làm crash lúc dừng phát.
                        }
                    }
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun release() {
        // Không giữ tài nguyên nào ở cấp source (mỗi openStream() tự quản lý vòng đời kết nối
        // riêng của nó qua closeAll()) — không có gì để dọn thêm ở đây.
    }
}

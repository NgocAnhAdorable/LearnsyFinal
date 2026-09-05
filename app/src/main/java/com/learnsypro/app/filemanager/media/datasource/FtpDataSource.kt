package com.learnsypro.app.filemanager.media.datasource

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import com.learnsypro.app.filemanager.model.FtpConnectionProfile
import org.apache.commons.net.ftp.FTPClient
import java.io.InputStream

/**
 * DataSource Media3 đọc trực tiếp 1 file từ FTP server theo kiểu "đến đâu phát
 * đến đó" (giống hệt cách DLNA đã hoạt động qua HTTP) — KHÔNG tải nguyên file
 * về cacheDir trước như downloadThenOpen()/RemoteContentDirectoryClient.downloadToFile()
 * ở những chỗ khác trong app. Dữ liệu đọc từ socket FTP được ExoPlayer tiêu thụ
 * ngay rồi giải phóng khỏi RAM, không bao giờ chạm tới ổ đĩa của máy.
 *
 * Mở 1 kết nối FTP RIÊNG cho mỗi lần open() (không dùng lại control channel của
 * FtpClientManager đang duyệt thư mục) — ExoPlayer có thể gọi open() nhiều lần
 * với offset khác nhau (tua/seek), và 1 control channel FTP chỉ xử lý được 1
 * data transfer tại 1 thời điểm; dùng kết nối riêng tránh xung đột với thao tác
 * duyệt file khác đang chạy song song trên UI.
 */
class FtpDataSource(private val profile: FtpConnectionProfile) : BaseDataSource(/* isNetwork= */ true) {

    private var ftp: FTPClient? = null
    private var inputStream: InputStream? = null
    private var remotePath: String = ""
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()

    override fun open(dataSpec: DataSpec): Long {
        remotePath = dataSpec.uri.path.orEmpty()
        transferInitializing(dataSpec)

        try {
            val client = FTPClient()
            client.connectTimeout = 8000
            client.dataTimeout = java.time.Duration.ofSeconds(60)
            client.controlEncoding = "UTF-8" // set TRƯỚC connect() — xem giải thích chi tiết ở FtpClientManager.kt
            client.connect(profile.host, profile.port)
            if (!client.login(profile.username, profile.password)) {
                client.disconnect()
                throw DataSourceException(null as Throwable?, PlaybackException.ERROR_CODE_IO_NO_PERMISSION)
            }
            client.enterLocalPassiveMode()
            client.setPassiveNatWorkaroundStrategy { profile.host }
            client.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE)

            // Seek: REST command báo server bắt đầu gửi dữ liệu từ offset dataSpec.position
            // thay vì từ đầu file — đây là cách FTP hỗ trợ tua/seek khi ExoPlayer cần đọc
            // giữa file (ví dụ người dùng kéo thanh tiến trình video).
            if (dataSpec.position > 0) {
                client.setRestartOffset(dataSpec.position)
            }

            // Lấy kích thước file TRƯỚC khi mở data stream — FTP control channel chỉ xử lý
            // được 1 lệnh "đang tiến hành" tại 1 thời điểm; gọi listFiles() (lệnh LIST) SAU
            // khi retrieveFileStream() (lệnh RETR) đã mở stream sẽ vi phạm giao thức FTP
            // (2 lệnh chồng lên nhau trên cùng control channel), gây lỗi hoặc treo.
            val fileSize = client.listFiles(remotePath).firstOrNull()?.size ?: -1L

            val stream = client.retrieveFileStream(remotePath)
                ?: throw DataSourceException(null as Throwable?, PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)

            ftp = client
            inputStream = stream

            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                dataSpec.length
            } else if (fileSize >= 0) {
                fileSize - dataSpec.position
            } else {
                C.LENGTH_UNSET.toLong()
            }

            transferStarted(dataSpec)
            return bytesRemaining
        } catch (e: DataSourceException) {
            throw e
        } catch (e: Exception) {
            throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val stream = inputStream ?: return C.RESULT_END_OF_INPUT
        val toRead = if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            if (bytesRemaining <= 0) return C.RESULT_END_OF_INPUT
            minOf(length.toLong(), bytesRemaining).toInt()
        } else {
            length
        }
        val read = try {
            stream.read(buffer, offset, toRead)
        } catch (e: Exception) {
            throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        }
        if (read == -1) return C.RESULT_END_OF_INPUT
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri() = null

    override fun close() {
        try {
            inputStream?.close()
        } catch (e: Exception) {
            // Đóng stream lỗi (kết nối đã rớt sẵn) không quan trọng — vẫn phải dọn ftp bên dưới.
        }
        inputStream = null
        try {
            // completePendingCommand() BẮT BUỘC gọi sau khi đóng data stream của FTP, nếu
            // không control channel sẽ ở trạng thái "dở dang" và không dùng lại được cho
            // lần open() kế tiếp (ExoPlayer gọi close() rồi open() lại liên tục khi tua/seek).
            ftp?.completePendingCommand()
        } catch (e: Exception) {
            // Bỏ qua — quan trọng là disconnect() bên dưới luôn chạy để giải phóng socket.
        }
        try {
            ftp?.disconnect()
        } catch (e: Exception) {
            // Ngắt kết nối lỗi không được phép làm crash lúc dừng phát nhạc/video.
        }
        ftp = null
        transferEnded()
    }

    /** Factory để ExoPlayer tự tạo DataSource mới mỗi khi cần mở 1 kết nối (kể cả lúc seek). */
    class Factory(private val profile: FtpConnectionProfile) : DataSource.Factory {
        override fun createDataSource(): DataSource = FtpDataSource(profile)
    }
}

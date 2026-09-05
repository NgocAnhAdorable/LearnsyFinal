package com.learnsypro.app.filemanager.media.datasource

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import com.learnsypro.app.filemanager.model.FtpConnectionProfile
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.sftp.RemoteFile
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.io.File

/**
 * DataSource Media3 đọc trực tiếp 1 file từ SFTP server, không tải về đĩa —
 * xem giải thích tổng quát ở FtpDataSource.kt (cùng mục đích, khác giao thức).
 *
 * SFTP hỗ trợ random-access THẬT SỰ qua RemoteFile.read(fileOffset, ...) — khác
 * FTP (phải REST rồi mở lại data stream mới cho mỗi vị trí seek), nên ở đây
 * KHÔNG cần mở lại kết nối mỗi lần seek trong cùng 1 phiên phát — chỉ cần đổi
 * currentOffset và đọc tiếp từ vị trí mới ngay trên RemoteFile đang mở.
 */
class SftpDataSource(private val profile: FtpConnectionProfile) : BaseDataSource(/* isNetwork= */ true) {

    private var ssh: SSHClient? = null
    private var sftp: SFTPClient? = null
    private var remoteFile: RemoteFile? = null
    private var currentOffset: Long = 0
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        try {
            val client = SSHClient()
            client.addHostKeyVerifier(PromiscuousVerifier())
            client.connectTimeout = 8000
            client.timeout = 60_000
            client.connection.keepAlive.keepAliveInterval = 15
            client.connect(profile.host, profile.port)

            if (profile.sftpPrivateKeyPath.isNotBlank() && File(profile.sftpPrivateKeyPath).exists()) {
                val keys = client.loadKeys(profile.sftpPrivateKeyPath)
                client.authPublickey(profile.username, keys)
            } else {
                client.authPassword(profile.username, profile.password)
            }

            val sftpClient = client.newSFTPClient()
            val path = dataSpec.uri.path.orEmpty()
            val file = sftpClient.open(path)

            ssh = client
            sftp = sftpClient
            remoteFile = file
            currentOffset = dataSpec.position

            val fileSize = try { sftpClient.size(path) } catch (e: Exception) { -1L }
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
        val file = remoteFile ?: return C.RESULT_END_OF_INPUT
        val toRead = if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            if (bytesRemaining <= 0) return C.RESULT_END_OF_INPUT
            minOf(length.toLong(), bytesRemaining).toInt()
        } else {
            length
        }
        val read = try {
            // RemoteFile.read trả về số byte đọc được, hoặc -1 khi hết file — random
            // access thật sự theo currentOffset, không cần "seek" riêng như luồng tuần tự.
            file.read(currentOffset, buffer, offset, toRead)
        } catch (e: Exception) {
            throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        }
        if (read == -1) return C.RESULT_END_OF_INPUT
        currentOffset += read
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri() = null

    override fun close() {
        try {
            remoteFile?.close()
        } catch (e: Exception) {
            // Đóng file lỗi (kết nối đã rớt sẵn) không quan trọng.
        }
        remoteFile = null
        try {
            sftp?.close()
        } catch (e: Exception) {
        }
        sftp = null
        try {
            ssh?.disconnect()
        } catch (e: Exception) {
            // Ngắt kết nối lỗi không được phép làm crash lúc dừng phát nhạc/video.
        }
        ssh = null
        transferEnded()
    }

    class Factory(private val profile: FtpConnectionProfile) : DataSource.Factory {
        override fun createDataSource(): DataSource = SftpDataSource(profile)
    }
}

package com.learnsypro.app.filemanager.media.datasource

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMB2Dialect
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File as SmbFile
import com.learnsypro.app.filemanager.model.FtpConnectionProfile
import java.util.EnumSet

/**
 * DataSource Media3 đọc trực tiếp 1 file từ SMB share, không tải về đĩa —
 * xem giải thích tổng quát ở FtpDataSource.kt (cùng mục đích, khác giao thức).
 *
 * SMB hỗ trợ random-access THẬT SỰ qua File.read(buffer, fileOffset, ...) giống
 * SFTP — không cần mở lại kết nối mỗi lần seek trong cùng 1 phiên phát.
 */
class SmbDataSource(private val profile: FtpConnectionProfile) : BaseDataSource(/* isNetwork= */ true) {

    private var client: SMBClient? = null
    private var connection: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null
    private var file: SmbFile? = null
    private var currentOffset: Long = 0
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        try {
            // Cấu hình timeout/dialect giống hệt SmbClientManager.connect() — xem giải
            // thích chi tiết ở đó, không lặp lại comment ở đây.
            val config = SmbConfig.builder()
                .withTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .withSoTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .withDialects(
                    SMB2Dialect.SMB_2_0_2,
                    SMB2Dialect.SMB_2_1,
                    SMB2Dialect.SMB_3_0,
                    SMB2Dialect.SMB_3_0_2,
                    SMB2Dialect.SMB_3_1_1
                )
                .withMultiProtocolNegotiate(true)
                .withReadBufferSize(1024 * 1024)
                .withWriteBufferSize(1024 * 1024)
                .withTransactBufferSize(1024 * 1024)
                .build()

            val smbClient = SMBClient(config)
            val port = if (profile.port in 1..65535) profile.port else 445
            val conn = smbClient.connect(profile.host, port)
            val authContext = if (profile.username.isBlank()) {
                AuthenticationContext.anonymous()
            } else {
                AuthenticationContext(profile.username, profile.password.toCharArray(), profile.smbDomain.ifBlank { null })
            }
            val sess = conn.authenticate(authContext)
            val connectedShare = sess.connectShare(profile.smbShareName) as? DiskShare
                ?: throw DataSourceException(null as Throwable?, PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)

            val path = dataSpec.uri.path.orEmpty().removePrefix("/")
            // Chữ ký đúng: openFile(path, accessMask, attributes, shareAccesses,
            // createDisposition, createOptions) — attributes và createOptions truyền
            // null là hợp lệ (smbj tự dùng giá trị mặc định phù hợp cho việc mở đọc).
            // QUAN TRỌNG: PHẢI truyền rõ SMB2CreateDisposition.FILE_OPEN — giá trị mặc
            // định nội bộ của smbj cho tham số này là FILE_SUPERSEDE, có thể XOÁ NỘI
            // DUNG FILE ĐANG MỞ nếu vô tình bỏ sót (đã có báo cáo lỗi thật từ nhiều
            // người dùng smbj mất dữ liệu vì thiếu dòng này) — ở đây chỉ ĐỌC để phát
            // media nên phải chỉ định rõ ràng, không được dựa vào mặc định.
            val openedFile = connectedShare.openFile(
                path,
                EnumSet.of(AccessMask.FILE_READ_DATA),
                null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                null
            )

            client = smbClient
            connection = conn
            session = sess
            share = connectedShare
            file = openedFile
            currentOffset = dataSpec.position

            val fileSize = try {
                openedFile.getFileInformation(com.hierynomus.msfscc.fileinformation.FileStandardInformation::class.java).endOfFile
            } catch (e: Exception) { -1L }
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
        val f = file ?: return C.RESULT_END_OF_INPUT
        val toRead = if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            if (bytesRemaining <= 0) return C.RESULT_END_OF_INPUT
            minOf(length.toLong(), bytesRemaining).toInt()
        } else {
            length
        }
        val read = try {
            f.read(buffer, currentOffset, offset, toRead)
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
        try { file?.close() } catch (e: Exception) { }
        file = null
        try { share?.close() } catch (e: Exception) { }
        share = null
        try { session?.close() } catch (e: Exception) { }
        session = null
        try { connection?.close() } catch (e: Exception) { }
        connection = null
        try { client?.close() } catch (e: Exception) { }
        client = null
        transferEnded()
    }

    class Factory(private val profile: FtpConnectionProfile) : DataSource.Factory {
        override fun createDataSource(): DataSource = SmbDataSource(profile)
    }
}

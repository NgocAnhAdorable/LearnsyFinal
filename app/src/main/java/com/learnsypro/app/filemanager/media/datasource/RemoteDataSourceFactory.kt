package com.learnsypro.app.filemanager.media.datasource

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource

/**
 * DataSource.Factory tổng hợp cho ExoPlayer (dùng trong AudioPlaybackService, dùng chung
 * cho cả audio và video — xem giải thích ở đó): mỗi lần ExoPlayer cần đọc dữ liệu, nó gọi
 * createDataSource() rồi open(dataSpec) — Ở ĐÂY mới biết được URI thật (từ dataSpec.uri) nên
 * PHẢI trả về 1 lớp trung gian tự quyết định route lúc open(), không thể quyết định ngay tại
 * createDataSource() như ExoPlayer's DataSource.Factory thường làm.
 *
 * URI dạng learnsyremote://... (xem RemoteMediaUri.kt) -> route tới FtpDataSource/
 * SftpDataSource/SmbDataSource tương ứng, đọc trực tiếp từ server, không tải về đĩa.
 * URI dạng learnsycloud://... (xem CloudMediaUri.kt) -> giải mã ra URL HTTP thật + header xác
 * thực (Authorization Bearer token...) rồi route tới DefaultHttpDataSource kèm đúng header đó —
 * Google Drive/Dropbox/Box đều yêu cầu access token trên chính request tải nội dung, khác DLNA
 * (URL mở thẳng không cần xác thực) nên không thể dùng chung DefaultDataSource trơn ở dưới.
 * URI khác (http://, file://, content://...) -> route tới DefaultDataSource (hành vi gốc
 * của ExoPlayer, dùng cho DLNA vốn đã hoạt động tốt qua HTTP từ trước, không cần header riêng).
 */
class RemoteDataSourceFactory(private val context: Context) : DataSource.Factory {

    private val fallbackFactory = DefaultDataSource.Factory(context)

    override fun createDataSource(): DataSource = RoutingDataSource(context, fallbackFactory)

    private class RoutingDataSource(
        private val context: Context,
        private val fallbackFactory: DataSource.Factory
    ) : androidx.media3.datasource.DataSource {

        private var delegate: androidx.media3.datasource.DataSource? = null
        // ExoPlayer gọi addTransferListener() NGAY SAU createDataSource(), TRƯỚC khi
        // open() được gọi — lúc đó delegate thật sự vẫn còn null (chưa biết route tới
        // đâu vì chưa có dataSpec.uri). Lưu lại listener ở đây, áp dụng cho delegate
        // thật ngay khi nó được tạo trong open() bên dưới.
        private val pendingListeners = mutableListOf<androidx.media3.datasource.TransferListener>()

        override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long {
            val decodedRemote = RemoteMediaUri.decode(dataSpec.uri)
            val decodedCloud = if (decodedRemote == null) CloudMediaUri.decode(dataSpec.uri) else null
            val (ds, effectiveDataSpec) = when {
                decodedRemote != null -> {
                    val (profile, _) = decodedRemote
                    val source = when (profile.type) {
                        com.learnsypro.app.filemanager.model.ConnectionType.FTP -> FtpDataSource(profile)
                        com.learnsypro.app.filemanager.model.ConnectionType.SFTP -> SftpDataSource(profile)
                        com.learnsypro.app.filemanager.model.ConnectionType.SMB -> SmbDataSource(profile)
                    }
                    source to dataSpec
                }
                decodedCloud != null -> {
                    val (realUrl, headers) = decodedCloud
                    val httpFactory = DefaultHttpDataSource.Factory()
                        .setDefaultRequestProperties(headers)
                        .setAllowCrossProtocolRedirects(true)
                    // Thay dataSpec.uri (learnsycloud://...) bằng URL HTTP thật đã giải mã —
                    // DefaultHttpDataSource cần URL thật để mở kết nối, còn header xác thực đã
                    // được gắn sẵn vào factory ở trên qua setDefaultRequestProperties().
                    httpFactory.createDataSource() to dataSpec.buildUpon()
                        .setUri(android.net.Uri.parse(realUrl))
                        .build()
                }
                else -> fallbackFactory.createDataSource() to dataSpec
            }
            pendingListeners.forEach { ds.addTransferListener(it) }
            delegate = ds
            // FtpDataSource/SftpDataSource/SmbDataSource đọc remotePath từ dataSpec.uri.path
            // trực tiếp — path trong learnsyremote:// URI đã đúng là đường dẫn file từ xa
            // (xem RemoteMediaUri.encode()), không cần truyền lại profile path riêng ở đây.
            return ds.open(effectiveDataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            delegate?.read(buffer, offset, length) ?: -1

        override fun getUri() = delegate?.uri

        override fun close() {
            delegate?.close()
            delegate = null
        }

        override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
            val current = delegate
            if (current != null) {
                current.addTransferListener(transferListener)
            } else {
                pendingListeners.add(transferListener)
            }
        }
    }
}

package com.learnsypro.app.filemanager.media.datasource

import android.net.Uri
import com.learnsypro.app.filemanager.model.ConnectionType
import com.learnsypro.app.filemanager.model.FtpConnectionProfile

/**
 * MediaController chỉ truyền được Uri (Parcelable) qua ranh giới IPC tới
 * AudioPlaybackService — không thể truyền thẳng FtpConnectionProfile hay 1
 * DataSource.Factory tùy chỉnh. Đóng gói toàn bộ thông tin kết nối (host, port,
 * tài khoản, mật khẩu, share/domain nếu SMB) VÀO trong chính chuỗi Uri theo 1
 * scheme riêng (learnsyremote://) để phía service tự giải mã lại và dựng đúng
 * DataSource khi ExoPlayer cần mở file — đây là cách duy nhất khả thi để phát
 * trực tiếp (streaming, không tải về đĩa) một file FTP/SFTP/SMB qua kiến trúc
 * MediaController/MediaSessionService hiện có của app.
 *
 * Định dạng: learnsyremote://<type>@<host>:<port>/<remote-path-url-encoded>
 *            ?u=<username>&p=<password>&share=<smbShare>&domain=<smbDomain>&passive=<0|1>
 * Toàn bộ giá trị nhạy cảm (username/password) được URL-encode, KHÔNG mã hoá
 * thêm — Uri này chỉ tồn tại trong bộ nhớ (MediaItem/MediaController), không
 * bao giờ ghi ra đĩa/log/Intent gửi ra ngoài app.
 */
object RemoteMediaUri {

    private const val SCHEME = "learnsyremote"

    fun encode(profile: FtpConnectionProfile, remotePath: String): Uri {
        val type = when (profile.type) {
            ConnectionType.FTP -> "ftp"
            ConnectionType.SFTP -> "sftp"
            ConnectionType.SMB -> "smb"
        }
        // Uri.Builder.path(remotePath) tưởng như sẽ tự URL-encode phần path, nhưng thực tế xử
        // lý ký tự Unicode ngoài ASCII (tiếng Việt có dấu, emoji...) KHÔNG nhất quán giữa các
        // phiên bản Android — có máy giữ nguyên byte thô, có máy encode sai cách. Hệ quả: 1 file
        // tên "Nhạc Tết 2025 Remix.mp4" trên FTP server, khi build Uri qua .path() rồi đọc lại
        // qua uri.path (tự động decode) ở FtpDataSource.open(), path thu được KHÔNG khớp với
        // đường dẫn thật trên server -> retrieveFileStream() không tìm thấy file, trả về null
        // hoặc stream rỗng -> ExoPlayer không có dữ liệu, video/audio chỉ hiện nút play, bấm
        // không chạy được. Sửa bằng cách tự URL-encode remotePath TRƯỚC khi truyền cho .path()
        // — encodedPath() coi input đã encode sẵn nên không encode chồng lần 2, và path() thô
        // sẽ encode lại phần đã encode gây lỗi %-escape kép, nên phải dùng .encodedPath() ở đây.
        val encodedPath = remotePath.split("/").joinToString("/") { segment ->
            if (segment.isEmpty()) segment else Uri.encode(segment)
        }
        return Uri.Builder()
            .scheme(SCHEME)
            .encodedAuthority("$type@${profile.host}:${profile.port}")
            .encodedPath(encodedPath)
            .appendQueryParameter("u", profile.username)
            .appendQueryParameter("p", profile.password)
            .appendQueryParameter("share", profile.smbShareName)
            .appendQueryParameter("domain", profile.smbDomain)
            .appendQueryParameter("passive", if (profile.passiveMode) "1" else "0")
            .appendQueryParameter("key", profile.sftpPrivateKeyPath)
            .build()
    }

    fun isRemoteUri(uri: Uri): Boolean = uri.scheme == SCHEME

    /** Giải mã ngược lại 1 Uri đã encode() thành FtpConnectionProfile + đường dẫn file từ xa. */
    fun decode(uri: Uri): Pair<FtpConnectionProfile, String>? {
        if (uri.scheme != SCHEME) return null
        val authority = uri.authority ?: return null
        val atIndex = authority.indexOf('@')
        if (atIndex < 0) return null
        val typeStr = authority.substring(0, atIndex)
        val hostPort = authority.substring(atIndex + 1)
        val colonIndex = hostPort.lastIndexOf(':')
        if (colonIndex < 0) return null
        val host = hostPort.substring(0, colonIndex)
        val port = hostPort.substring(colonIndex + 1).toIntOrNull() ?: return null
        val type = when (typeStr) {
            "ftp" -> ConnectionType.FTP
            "sftp" -> ConnectionType.SFTP
            "smb" -> ConnectionType.SMB
            else -> return null
        }
        val profile = FtpConnectionProfile(
            name = host,
            host = host,
            port = port,
            username = uri.getQueryParameter("u").orEmpty(),
            password = uri.getQueryParameter("p").orEmpty(),
            type = type,
            passiveMode = uri.getQueryParameter("passive") != "0",
            smbShareName = uri.getQueryParameter("share").orEmpty(),
            smbDomain = uri.getQueryParameter("domain").orEmpty(),
            sftpPrivateKeyPath = uri.getQueryParameter("key").orEmpty()
        )
        // uri.path (không phải getEncodedPath()) TỰ ĐỘNG url-decode đúng ngược lại phần path đã
        // encodedPath() ở encode() phía trên — đây là hành vi chuẩn của android.net.Uri, không
        // phải chỗ thiếu sót. Không đổi thành getEncodedPath() ở đây kẻo FtpDataSource nhận lại
        // path chưa decode, gây trùng lỗi retrieveFileStream() không tìm thấy file như trước.
        return profile to uri.path.orEmpty()
    }
}

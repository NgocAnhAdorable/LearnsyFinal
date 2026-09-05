package com.learnsypro.app.filemanager.media.datasource

import android.net.Uri

/**
 * Tương đương RemoteMediaUri.kt nhưng cho file cloud (Google Drive/Dropbox/Box) thay vì
 * FTP/SFTP/SMB. MediaController chỉ truyền được Uri (Parcelable) qua IPC tới
 * AudioPlaybackService — không truyền thẳng được Map<String,String> header xác thực. Đóng gói
 * URL HTTP thật + toàn bộ header cần gửi kèm (Authorization Bearer token, Dropbox-API-Arg...)
 * VÀO trong 1 Uri theo scheme riêng (learnsycloud://) để RemoteDataSourceFactory tự giải mã lại
 * và tạo đúng DefaultHttpDataSource kèm header khi ExoPlayer cần mở file — nhờ đó phát trực
 * tiếp (streaming) audio/video từ cloud, KHÔNG cần downloadFile() về đĩa trước như trước đây.
 *
 * Định dạng: learnsycloud://<url-thật-đã-encode>?h0k=<header-key>&h0v=<header-value>&h1k=...
 * (header key/value đánh số vì Uri không hỗ trợ nhiều query param cùng tên đáng tin cậy giữa
 * các phiên bản Android). URL thật + mọi giá trị header đều được URL-encode qua Uri.Builder,
 * Uri này chỉ tồn tại trong bộ nhớ (MediaItem/MediaController), không ghi ra đĩa/log.
 */
object CloudMediaUri {

    private const val SCHEME = "learnsycloud"

    fun encode(realUrl: String, headers: Map<String, String>): Uri {
        val builder = Uri.Builder()
            .scheme(SCHEME)
            .encodedAuthority("stream")
            .appendQueryParameter("u", realUrl)
        headers.entries.forEachIndexed { index, (k, v) ->
            builder.appendQueryParameter("h${index}k", k)
            builder.appendQueryParameter("h${index}v", v)
        }
        return builder.build()
    }

    fun isCloudUri(uri: Uri): Boolean = uri.scheme == SCHEME

    /** Giải mã ngược lại 1 Uri đã encode() thành (URL thật, headers). */
    fun decode(uri: Uri): Pair<String, Map<String, String>>? {
        if (uri.scheme != SCHEME) return null
        val realUrl = uri.getQueryParameter("u") ?: return null
        val headers = mutableMapOf<String, String>()
        var index = 0
        while (true) {
            val k = uri.getQueryParameter("h${index}k") ?: break
            val v = uri.getQueryParameter("h${index}v") ?: break
            headers[k] = v
            index++
        }
        return realUrl to headers
    }
}

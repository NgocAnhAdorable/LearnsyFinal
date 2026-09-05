package com.learnsypro.app.filemanager.client

import com.learnsypro.app.filemanager.model.FtpConnectionProfile
import com.learnsypro.app.filemanager.model.RemoteFile
import com.learnsypro.app.filemanager.util.LogBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Bọc Apache Commons Net FTPClient để kết nối tới FTP server khác, duyệt và
 * truyền file. Mọi thao tác mạng chạy trên Dispatchers.IO qua coroutines.
 */
class FtpClientManager : RemoteClient {

    private var client: FTPClient? = null
    // Giữ handle network Wi-Fi sống suốt phiên FTP — release() chỉ gọi ở
    // disconnect(), KHÔNG gọi ngay sau khi lấy được network (xem giải thích
    // trong NetworkUtils.acquireActiveWifiNetwork — đây là bug đã sửa).
    private var wifiNetworkHandle: com.learnsypro.app.filemanager.util.NetworkUtils.WifiNetworkHandle? = null

    override val isConnected: Boolean
        get() = client?.isConnected == true

    override suspend fun connect(profile: FtpConnectionProfile): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val ftp = FTPClient()

            // Ép TOÀN BỘ socket của FTPClient (control channel VÀ data channel PASV/
            // Active sau này) đi ra đúng mạng Wi-Fi/Ethernet đang kết nối, thay vì để
            // hệ thống tự chọn network. Bắt buộc phải làm bước này khi máy vừa bật
            // Wi-Fi vừa bật dữ liệu di động: Android có thể tự route traffic tới IP
            // LAN (192.168.x.x) qua network sai (di động), khiến kết nối tới máy chủ
            // FTP trong cùng mạng LAN bị timeout dù Wi-Fi đang hoạt động bình thường.
            //
            // handle được GIỮ LẠI trong field wifiNetworkHandle (không release ngay) —
            // NetworkCallback phải còn đăng ký suốt phiên FTP để hệ thống không hạ cấp
            // network giữa chừng, xem chi tiết trong NetworkUtils.kt.
            wifiNetworkHandle = try {
                com.learnsypro.app.filemanager.util.NetworkUtils.acquireActiveWifiNetwork(
                    com.learnsypro.app.LearnsyApp.instance
                )
            } catch (e: Exception) {
                null // LearnsyApp.instance chưa sẵn sàng hoặc lỗi lấy network — fallback bên dưới
            }
            val wifiNetwork = wifiNetworkHandle?.network
            if (wifiNetwork != null) {
                ftp.setSocketFactory(object : javax.net.SocketFactory() {
                    override fun createSocket(): java.net.Socket =
                        java.net.Socket().also { wifiNetwork.bindSocket(it) }
                    override fun createSocket(host: String?, port: Int): java.net.Socket =
                        createSocket().apply { connect(java.net.InetSocketAddress(host, port)) }
                    override fun createSocket(host: String?, port: Int, localHost: java.net.InetAddress?, localPort: Int): java.net.Socket =
                        createSocket().apply {
                            bind(java.net.InetSocketAddress(localHost, localPort))
                            connect(java.net.InetSocketAddress(host, port))
                        }
                    override fun createSocket(host: java.net.InetAddress?, port: Int): java.net.Socket =
                        createSocket().apply { connect(java.net.InetSocketAddress(host, port)) }
                    override fun createSocket(address: java.net.InetAddress?, port: Int, localAddress: java.net.InetAddress?, localPort: Int): java.net.Socket =
                        createSocket().apply {
                            bind(java.net.InetSocketAddress(localAddress, localPort))
                            connect(java.net.InetSocketAddress(address, port))
                        }
                })
            } else {
                // Không lấy được network Wi-Fi xác nhận (timeout 4s hoặc lỗi) — giải phóng
                // callback rác nếu có, rồi để FTPClient tự chọn socket mặc định của hệ thống
                // (hành vi gốc trước khi có patch bind network).
                wifiNetworkHandle?.release()
                wifiNetworkHandle = null
            }
            // Ép control channel dùng UTF-8: mặc định Commons Net FTPClient dùng ISO-8859-1 để
            // decode/encode toàn bộ control channel (bao gồm tên file/thư mục trả về từ LIST),
            // trong khi hầu hết router/NAS hiện đại gửi tên file bằng UTF-8 thẳng (không hỗ trợ
            // lệnh "OPTS UTF8 ON" kiểu FTP server chuẩn cũ) -> mỗi byte UTF-8 nhiều-byte (tiếng
            // Việt có dấu, emoji...) bị decode sai thành các ký tự Latin-1 rác kiểu "Nháº¡c
            // Táº¿t" thay vì "Nhạc Tết" — đúng lỗi mojibake đang gặp. Đặt TRƯỚC connect() vì
            // setControlEncoding() cấu hình charset dùng xuyên suốt phiên kết nối, gọi sau khi
            // đã connect() sẽ không áp dụng lại cho các lệnh đã trao đổi lúc bắt tay.
            ftp.controlEncoding = "UTF-8"
            // Gọi trực tiếp setConnectTimeout(int) dạng hàm thay vì cú pháp gán property
            // (ftp.connectTimeout = 8000) — từ Commons Net 3.9.0, SocketClient có CẢ 2 overload
            // cùng tên (setConnectTimeout(int) cũ deprecated, và bên cạnh đó FTPClient thêm các
            // setter kiểu Duration cho control keep-alive/data timeout). Kotlin đôi khi suy luận
            // synthetic property "connectTimeout"/"controlKeepAliveTimeout" theo overload
            // Duration thay vì overload int/long, gây lỗi biên dịch "actual type is Duration!,
            // but Int/Long was expected" dù code hoàn toàn hợp lệ khi gọi tường minh dạng hàm.
            ftp.setConnectTimeout(8000)
            ftp.connect(profile.host, profile.port)
            // Tắt thuật toán Nagle: dữ liệu điều khiển FTP là các lệnh nhỏ, gửi ngay lập tức
            // thay vì đợi gộp gói giúp giảm độ trễ, đặc biệt rõ khi duyệt nhiều thư mục liên tiếp.
            // LƯU Ý: phải gọi SAU connect() — FTPClient chỉ tạo Socket bên trong khi connect(),
            // gọi trước đó làm setTcpNoDelay() thao tác trên Socket null -> NullPointerException,
            // khiến MỌI lần kết nối FTP đều thất bại ngay cả khi server/mật khẩu đều đúng.
            ftp.tcpNoDelay = true

            val reply = ftp.replyCode
            if (!FTPReply.isPositiveCompletion(reply)) {
                ftp.disconnect()
                return@withContext Result.failure(Exception("Máy chủ từ chối kết nối (mã $reply)"))
            }

            val loginOk = ftp.login(profile.username, profile.password)
            if (!loginOk) {
                ftp.disconnect()
                return@withContext Result.failure(Exception("Sai tên đăng nhập hoặc mật khẩu"))
            }

            if (profile.passiveMode) {
                ftp.enterLocalPassiveMode()
                // Ép dùng lại ĐÚNG địa chỉ IP đã dùng để kết nối (profile.host) làm địa chỉ data
                // channel, thay vì tin theo IP mà server trả về trong phản hồi lệnh PASV. Đây là
                // nguyên nhân RẤT PHỔ BIẾN khiến Passive Mode "báo lỗi/treo" trên router/NAS gia
                // dụng: server trả về địa chỉ IP NỘI BỘ của chính nó trong phản hồi PASV (vd
                // 192.168.2.1 hoặc thậm chí 0.0.0.0/127.0.0.1 tuỳ firmware), nhưng đó có thể
                // không khớp với địa chỉ client thực sự cần kết nối tới nếu router có nhiều
                // interface/NAT phức tạp — Commons Net mặc định LUÔN tin theo IP server trả về,
                // khiến client cố kết nối nhầm địa chỉ -> "425 Unable to build data connection"
                // hoặc treo. setPassiveNatWorkaroundStrategy ép dùng lại profile.host (địa chỉ
                // client BIẾT CHẮC là đúng vì vừa dùng nó để connect() thành công) thay vì tin
                // server, khắc phục đúng lớp lỗi PASV không ổn định trên mạng gia đình — ĐÂY mới
                // là hướng sửa đúng, thay vì buộc người dùng bật Active Mode (PORT), vốn còn tệ
                // hơn vì cần router tự mở kết nối NGƯỢC LẠI điện thoại — hầu hết router chặn
                // hẳn chiều đó, không phải "thỉnh thoảng lỗi" như PASV mà là lỗi liên tục ngắt
                // quãng tuỳ thời điểm router xử lý NAT (đúng triệu chứng "lúc được lúc không").
                ftp.setPassiveNatWorkaroundStrategy { profile.host }
            } else {
                ftp.enterLocalActiveMode()
            }
            // Xác nhận Passive Mode THỰC SỰ được server chấp nhận: enterLocalPassiveMode() chỉ
            // đổi cấu hình phía CLIENT (chuẩn bị gửi PASV thay vì PORT ở lệnh LIST/RETR/STOR kế
            // tiếp), không tự kiểm tra hay báo lỗi ngay nếu server từ chối PASV. Nếu router/NAT
            // không hỗ trợ đúng Passive Mode (hiếm nhưng có, nhất là firmware cũ), lệnh LIST sau
            // đó sẽ tự rơi về hành vi lỗi im lặng như đã thấy — nên ở đây log rõ chế độ đang dùng
            // để dễ đối chiếu với log "reply" của listFiles() khi debug.
            LogBus.info(
                "Chế độ dữ liệu FTP: ${if (profile.passiveMode) "Passive (PASV)" else "Active (PORT)"} — Active Mode thường KHÔNG hoạt động khi điện thoại/app ở sau NAT/router vì router đích phải tự mở kết nối ngược lại điện thoại, hầu hết router gia dụng chặn chiều này.",
                source = "FTP"
            )
            ftp.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE)
            // Ép dùng UNIX_FTP_ENTRY_PARSER tường minh thay vì để Commons Net tự đoán
            // (autodetect dựa theo chuỗi SYST server trả về lúc bắt tay). Router/NAS gia dụng
            // (Xiaomi, TP-Link, Asus, các firmware BusyBox...) thường trả về chuỗi SYST không
            // chuẩn hoặc rút gọn, khiến Commons Net autodetect sai loại server -> chọn nhầm
            // parser (VMS/NT/OS2...) trong khi output LIST thực tế vẫn là định dạng UNIX/ls -l
            // -> parser đọc sai dòng thành null, ftp.listFiles() im lặng trả về mảng RỖNG dù
            // server thực sự đang trả về đúng dữ liệu qua control channel (không có exception
            // nào để bắt) -> đúng triệu chứng "kết nối được, không lỗi, nhưng Thư mục trống" dù
            // ứng dụng khác (My Files qua SMB/API riêng của Samsung) vẫn thấy đủ file trên cùng
            // ổ đó. UNIX_FTP_ENTRY_PARSER là lựa chọn an toàn nhất vì đại đa số router/NAS chạy
            // nhân Linux, dùng daemon FTP kiểu vsftpd/proftpd/BusyBox đều xuất định dạng ls -l.
            val ftpConfig = org.apache.commons.net.ftp.FTPClientConfig(
                org.apache.commons.net.ftp.FTPClientConfig.SYST_UNIX
            )
            ftp.configure(ftpConfig)
            // Tăng buffer từ 64KB lên 256KB: giảm số lần round-trip đọc/ghi socket khi
            // truyền file lớn, cải thiện rõ tốc độ upload/download trên mạng LAN nhanh.
            ftp.bufferSize = 1024 * 256
            // setControlKeepAliveTimeout(int giây) đã deprecated từ Commons Net 3.9.0, thay bằng
            // setControlKeepAliveTimeout(Duration) — dùng bản Duration tường minh để tránh đúng
            // lỗi biên dịch "Duration! but Int was expected" gặp phải khi gán qua cú pháp property.
            ftp.setControlKeepAliveTimeout(java.time.Duration.ofSeconds(30)) // gửi NOOP giữ kết nối khi tải file rất lớn/chậm

            client = ftp
            Result.success(Unit)
        } catch (e: Exception) {
            LogBus.error("Kết nối FTP tới ${profile.host}:${profile.port} thất bại", source = "FTP", throwable = e)
            // Connect thất bại — không giữ callback network sống vô ích, tránh leak
            // NetworkRequest treo tới khi app bị kill/GC.
            wifiNetworkHandle?.release()
            wifiNetworkHandle = null
            Result.failure(e)
        }
    }

    override suspend fun disconnect(): Unit = withContext(Dispatchers.IO) {
        try {
            client?.logout()
            client?.disconnect()
        } catch (e: Exception) {
            // ignore
        } finally {
            client = null
            wifiNetworkHandle?.release()
            wifiNetworkHandle = null
        }
        Unit
    }

    override suspend fun listFiles(path: String): Result<List<RemoteFile>> = withContext(Dispatchers.IO) {
        val ftp = client ?: return@withContext Result.failure(Exception("Chưa kết nối"))
        try {
            var files: Array<FTPFile> = ftp.listFiles(path)
            // Fallback: 1 số server (đặc biệt router/NAS gia dụng) trả về LIST rỗng hoặc lỗi
            // khi truyền thẳng path (nhất là "/" hoặc path rỗng ở thư mục gốc) nhưng lại hoạt
            // động đúng nếu CWD vào path đó trước rồi gọi listFiles() không tham số (dùng CWD
            // hiện tại) — 2 cách gọi lệnh LIST khác nhau trên control channel, không phải mọi
            // server đều tương thích cả hai. Chỉ thử fallback khi lần đầu THỰC SỰ rỗng (không
            // phải lỗi), để không che mất lỗi thật (sai quyền, mất kết nối...).
            if (files.isEmpty()) {
                val cwdOk = ftp.changeWorkingDirectory(path.ifBlank { "/" })
                if (cwdOk) {
                    files = ftp.listFiles()
                }
                LogBus.info(
                    "listFiles('$path') rỗng ở lần gọi đầu, thử lại qua CWD (${if (cwdOk) "OK" else "thất bại"}) -> ${files.size} mục, reply: ${ftp.replyString.trim()}",
                    source = "FTP"
                )
            }
            val result = files
                .filter { it.name != "." && it.name != ".." }
                .map { f ->
                    RemoteFile(
                        name = f.name,
                        path = if (path.endsWith("/")) "$path${f.name}" else "$path/${f.name}",
                        isDirectory = f.isDirectory,
                        size = f.size,
                        modifiedTime = f.timestamp?.timeInMillis ?: 0L
                    )
                }
                .sortedWith(compareByDescending<RemoteFile> { it.isDirectory }.thenBy { it.name.lowercase() })
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadFile(localFile: File, remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        val ftp = client ?: return@withContext Result.failure(Exception("Chưa kết nối"))
        try {
            localFile.inputStream().use { input: InputStream ->
                val ok = ftp.storeFile(remotePath, input)
                if (!ok) return@withContext Result.failure(Exception("Tải lên thất bại: ${ftp.replyString}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            LogBus.error("Tải lên thất bại: $remotePath", source = "FTP", throwable = e)
            Result.failure(e)
        }
    }

    override suspend fun downloadFile(remotePath: String, localFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        val ftp = client ?: return@withContext Result.failure(Exception("Chưa kết nối"))
        try {
            localFile.outputStream().use { output: OutputStream ->
                val ok = ftp.retrieveFile(remotePath, output)
                if (!ok) return@withContext Result.failure(Exception("Tải xuống thất bại: ${ftp.replyString}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            LogBus.error("Tải xuống thất bại: $remotePath", source = "FTP", throwable = e)
            Result.failure(e)
        }
    }

    override suspend fun deleteFile(remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        val ftp = client ?: return@withContext Result.failure(Exception("Chưa kết nối"))
        try {
            val ok = ftp.deleteFile(remotePath)
            if (!ok) return@withContext Result.failure(Exception("Xóa thất bại: ${ftp.replyString}"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteDirectory(remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        val ftp = client ?: return@withContext Result.failure(Exception("Chưa kết nối"))
        try {
            val ok = ftp.removeDirectory(remotePath)
            if (!ok) return@withContext Result.failure(Exception("Xóa thư mục thất bại: ${ftp.replyString}"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun makeDirectory(remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        val ftp = client ?: return@withContext Result.failure(Exception("Chưa kết nối"))
        try {
            val ok = ftp.makeDirectory(remotePath)
            if (!ok) return@withContext Result.failure(Exception("Tạo thư mục thất bại: ${ftp.replyString}"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun rename(fromPath: String, toPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        val ftp = client ?: return@withContext Result.failure(Exception("Chưa kết nối"))
        try {
            val ok = ftp.rename(fromPath, toPath)
            if (!ok) return@withContext Result.failure(Exception("Đổi tên thất bại: ${ftp.replyString}"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * FTP chuẩn không có lệnh quota chung, nên ước tính dung lượng ĐÃ DÙNG bằng cách
     * duyệt đệ quy thư mục gốc và cộng dồn kích thước file (giới hạn độ sâu/số lượng
     * để tránh treo với server lớn). Không có khái niệm "tổng dung lượng" nên trả về null cho total.
     */
    override suspend fun estimateUsedSpace(maxDepth: Int, maxEntries: Int): Result<Long> = withContext(Dispatchers.IO) {
        val ftp = client ?: return@withContext Result.failure(Exception("Chưa kết nối"))
        try {
            var total = 0L
            var count = 0
            fun scan(path: String, depth: Int) {
                if (depth > maxDepth || count >= maxEntries) return
                val files = ftp.listFiles(path) ?: return
                for (f in files) {
                    if (f.name == "." || f.name == "..") continue
                    if (count >= maxEntries) return
                    count++
                    if (f.isDirectory) {
                        val childPath = if (path.endsWith("/")) "$path${f.name}" else "$path/${f.name}"
                        scan(childPath, depth + 1)
                    } else {
                        total += f.size
                    }
                }
            }
            scan("/", 0)
            Result.success(total)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

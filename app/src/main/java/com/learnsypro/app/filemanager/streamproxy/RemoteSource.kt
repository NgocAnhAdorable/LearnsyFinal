package com.learnsypro.app.filemanager.streamproxy

import java.io.InputStream

/**
 * Kết quả mở 1 luồng đọc từ nguồn từ xa (FTP/SFTP/SMB/Cloud), bắt đầu từ [offset] (byte).
 *
 * [totalSize]: tổng kích thước file thật (byte), dùng để trả header Content-Length/
 * Content-Range đúng cho client (ExoPlayer) — BẮT BUỘC phải biết trước khi mở stream
 * (không phải đọc xong mới biết) vì HTTP Range response cần khai báo trước cả file lớn
 * bao nhiêu ngay ở header, trước khi gửi byte đầu tiên. -1 nếu nguồn không cung cấp được
 * kích thước trước (hiếm, khi đó proxy sẽ trả 200 thường thay vì 206 Partial Content).
 *
 * [mimeType]: dùng cho header Content-Type — ExoPlayer dùng để chọn extractor phù hợp
 * (tuy đa phần tự đoán được qua đuôi file, nhưng khai báo đúng giúp tránh nhầm lẫn với
 * file không có đuôi rõ ràng).
 */
data class RemoteStream(
    val input: InputStream,
    val totalSize: Long,
    val mimeType: String,
    /** Đóng MỌI tài nguyên bên dưới input (socket điều khiển FTP/SFTP/SMB, session...),
     *  gọi khi proxy server đọc xong hoặc client ngắt kết nối giữa chừng. */
    val closeAll: () -> Unit
)

/**
 * 1 nguồn file từ xa duy nhất mà LocalStreamServer có thể phục vụ qua HTTP nội bộ.
 * Mỗi giao thức (FTP/SFTP/SMB) và mỗi provider cloud (Drive/Dropbox/Box) implement giao
 * diện này 1 lần — LocalStreamServer/ExoPlayer hoàn toàn không cần biết bên dưới là giao
 * thức gì, chỉ gọi openStream() và đọc InputStream trả về.
 *
 * KHÔNG kế thừa/dùng lại các *ClientManager (FtpClientManager, SftpClientManager,
 * SmbClientManager) hiện có trong app — những class đó được thiết kế để DUYỆT THƯ MỤC
 * (list/upload/download/rename...) với vòng đời gắn liền 1 màn hình (FileBrowserActivity),
 * dùng chung 1 control channel cho nhiều thao tác tuần tự. RemoteSource cần vòng đời NGẮN,
 * ĐỘC LẬP (1 kết nối/session riêng cho mỗi lần phát media, có thể mở nhiều lần khi tua/seek
 * — xem SEEK_STRATEGY bên dưới), và không được phép làm ảnh hưởng gì tới control channel
 * đang duyệt thư mục của người dùng trên UI. Trộn chung sẽ tái tạo đúng lớp bug cũ (control
 * channel bị 2 nơi tranh nhau 1 lệnh tại 1 thời điểm).
 */
interface RemoteSource {

    /**
     * Định danh duy nhất cho nguồn này trong 1 phiên phát (không cần bảo mật, không lộ
     * host/path thật ra ngoài) — dùng làm query param "src" trong URL nội bộ mà ExoPlayer
     * sẽ gọi, vd http://127.0.0.1:PORT/stream?src=<sourceId>. Sinh ngẫu nhiên (UUID) mỗi
     * lần tạo RemoteSource, không phải hash cố định theo path — 2 lần phát cùng 1 file vẫn
     * là 2 sourceId khác nhau, tránh nhầm lẫn nếu người dùng mở lại nhanh.
     */
    val sourceId: String

    /** Tên hiển thị (để log/debug), KHÔNG dùng để định danh hay lộ ra URL. */
    val displayName: String

    /**
     * Mở luồng đọc bắt đầu từ [offset]. Gọi lại nhiều lần trong vòng đời 1 sourceId (mỗi
     * lần ExoPlayer seek tới vị trí mới, LocalStreamServer đóng luồng cũ rồi gọi lại hàm
     * này với offset mới) — implementation TỰ mở kết nối/session mới cho mỗi lần gọi, không
     * cố gắng tái sử dụng luồng cũ giữa các lần seek khác nhau (đơn giản, đúng, đổi lại mỗi
     * lần seek có độ trễ kết nối lại — chấp nhận được vì người dùng chỉ seek thỉnh thoảng,
     * không phải liên tục mỗi frame).
     */
    suspend fun openStream(offset: Long): Result<RemoteStream>

    /** Giải phóng mọi tài nguyên nền của sourceId này (gọi khi ExoPlayer đóng hẳn, không
     *  còn khả năng seek lại) — LocalStreamServer gọi khi sourceId bị dọn khỏi registry. */
    fun release()
}

package com.learnsypro.app.filemanager.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/** Tiện ích lấy địa chỉ IP LAN của thiết bị để hiển thị cho người dùng kết nối FTP tới. */
object NetworkUtils {

    fun getLocalIpAddress(context: Context): String? {        // Ưu tiên lấy qua ConnectivityManager (chính xác với mạng đang active: WiFi/Hotspot/Ethernet)
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return fallbackInterfaceIp()
            val linkProperties: LinkProperties = cm.getLinkProperties(network) ?: return fallbackInterfaceIp()
            val ipv4 = linkProperties.linkAddresses
                .mapNotNull { it.address as? Inet4Address }
                .firstOrNull { !it.isLoopbackAddress }
            if (ipv4 != null) return ipv4.hostAddress
        } catch (e: Exception) {
            // fallthrough
        }
        return fallbackInterfaceIp()
    }

    private fun fallbackInterfaceIp(): String? {
        return try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun isWifiOrEthernetConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /**
     * Lấy Network object của Wi-Fi/Ethernet ĐÃ SẴN SÀNG (validated) — dùng để ép
     * socket FTP đi ra đúng mạng LAN này. Trả về cặp (network, cleanup) — cleanup
     * PHẢI được gọi khi phiên FTP kết thúc (disconnect) để gỡ NetworkCallback.
     *
     * QUAN TRỌNG — đây là bug đã sửa so với bản trước: unregisterNetworkCallback()
     * NGAY sau onAvailable() (như bản cũ) làm hệ thống mất lý do giữ network đó ở
     * mức ưu tiên cao, khiến Network object trả về tuy vẫn tồn tại về mặt tham
     * chiếu Kotlin nhưng KHÔNG còn đảm bảo định tuyến đúng khi socket thực sự kết
     * nối vài trăm ms sau đó — Android có thể âm thầm coi network đó là "không
     * còn ai cần", dẫn tới bindSocket() gắn vào 1 network đã bị hạ cấp, timeout
     * xảy ra KHÔNG ĐỀU tuỳ thời điểm hệ thống dọn dẹp — đúng triệu chứng đã gặp
     * (lúc được lúc không, không liên quan độ mạnh Wi-Fi hay khoảng cách router).
     * Giữ callback đăng ký (không unregister) suốt vòng đời phiên FTP đảm bảo
     * network luôn được hệ thống coi là "đang cần dùng", ổn định tới khi cleanup.
     */
    class WifiNetworkHandle(val network: android.net.Network?, private val cleanup: () -> Unit) {
        fun release() = cleanup()
    }

    suspend fun acquireActiveWifiNetwork(context: Context): WifiNetworkHandle =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            // Chấp nhận Wi-Fi HOẶC Ethernet (đúng như tên hàm/comment mô tả). LƯU Ý: gọi
            // addTransportType() nhiều lần trên CÙNG 1 Builder là điều kiện AND (network phải
            // có ĐỦ cả 2 transport cùng lúc — gần như không network nào thoả), không phải OR
            // như nhiều người lầm tưởng. Vì vậy chỉ request TRANSPORT_WIFI ở tầng OS (giữ đúng
            // hành vi cũ, tránh vô tình match nhầm network di động), sau đó chấp nhận thêm
            // Ethernet bằng cách kiểm tra capabilities ngay trong onAvailable() bên dưới —
            // đây là cách chính xác để có ngữ nghĩa OR giữa 2 transport với NetworkRequest.
            val request = android.net.NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            val ethernetRequest = android.net.NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            var resumed = false
            lateinit var wifiCallbackRef: ConnectivityManager.NetworkCallback
            lateinit var ethernetCallbackRef: ConnectivityManager.NetworkCallback
            // Gỡ CẢ 2 callback (wifi lẫn ethernet) khi cleanup, dù chỉ 1 trong 2 network xuất
            // hiện — tránh treo NetworkCallback rác của request còn lại. Đây là fix cho bug
            // thứ 2: bản trước chỉ đăng ký/gỡ 1 callback duy nhất, nhưng vì WifiNetworkHandle
            // mới GHI ĐÈ field wifiNetworkHandle ở FtpClientManager mỗi lần connect() được gọi
            // lại (vd người dùng bấm "Kết nối lại" sau khi thất bại), handle CŨ (nếu đã
            // resume rồi bằng onUnavailable/exception, tức network null) không hề được
            // release() trước khi bị ghi đè -> callback đó vẫn nằm trong ConnectivityManager
            // vĩnh viễn. Qua vài lần thử kết nối lại, số callback rác tích luỹ khiến hệ điều
            // hành bắt đầu định tuyến/ưu tiên network sai cho các request mới -> đúng triệu
            // chứng "lúc kết nối được lúc không, không liên quan sóng hay khoảng cách router".
            fun unregisterBoth() {
                try { cm.unregisterNetworkCallback(wifiCallbackRef) } catch (e: Exception) { /* đã gỡ */ }
                try { cm.unregisterNetworkCallback(ethernetCallbackRef) } catch (e: Exception) { /* đã gỡ */ }
            }

            wifiCallbackRef = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    if (resumed) return
                    resumed = true
                    // KHÔNG unregister ở đây — callback phải sống tới khi FTP disconnect()
                    // gọi release(), giữ network ở mức ưu tiên cao suốt phiên làm việc.
                    cont.resume(WifiNetworkHandle(network) { unregisterBoth() }) {}
                }
            }
            ethernetCallbackRef = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    if (resumed) return
                    resumed = true
                    cont.resume(WifiNetworkHandle(network) { unregisterBoth() }) {}
                }
            }

            cont.invokeOnCancellation { unregisterBoth() }

            try {
                cm.requestNetwork(request, wifiCallbackRef)
                cm.requestNetwork(ethernetRequest, ethernetCallbackRef)
            } catch (e: Exception) {
                if (!resumed) {
                    resumed = true
                    unregisterBoth()
                    cont.resume(WifiNetworkHandle(null) {}) {}
                }
                return@suspendCancellableCoroutine
            }

            // Timeout thủ công 4s: requestNetwork(request, callback) KHÔNG có overload nhận
            // timeout khi dùng 2 request song song (overload timeout chỉ nhận 1 callback), nên
            // tự đặt hẹn giờ — nếu chưa network nào sẵn sàng sau 4s, coi như thất bại và gỡ cả
            // 2 callback để không treo request vô thời hạn (khác bug đã sửa ở trên: ở đây có
            // timeout rõ ràng nên không tích luỹ callback rác qua các lần gọi).
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (!resumed) {
                    resumed = true
                    unregisterBoth()
                    cont.resume(WifiNetworkHandle(null) {}) {}
                }
            }, 4000)
        }
}

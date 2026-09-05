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
     * socket FTP đi ra đúng mạng LAN này, thay vì để hệ thống tự chọn (có thể
     * chọn nhầm mạng di động khi cả 2 cùng bật, khiến kết nối tới IP nội bộ như
     * 192.168.x.x bị timeout im lặng dù Wi-Fi thực sự đang kết nối tốt).
     *
     * ĐỔI SANG requestNetwork() + NetworkCallback thay vì duyệt cm.allNetworks()
     * (cách cũ): allNetworks() trả về TOÀN BỘ network hệ thống đang giữ tham
     * chiếu, kể cả network vừa mất kết nối/đang chuyển kênh/chưa hoàn tất xác
     * thực (NAT/DHCP) — bindSocket() vào 1 network như vậy vẫn "thành công" ở
     * tầng API (không exception) nhưng socket thực tế không gửi được gói tin
     * nào, gây timeout dù đứng ngay cạnh router. requestNetwork() buộc hệ thống
     * PHẢI xác nhận network qua onAvailable() trước khi trả về — network nhận
     * được ở đây chắc chắn đã kết nối xong, không phải suy đoán từ danh sách.
     *
     * Chạy trên coroutine, có timeout riêng (2s) để không cộng dồn vào timeout
     * 8s của ftp.connect() phía sau — nếu quá hạn hoặc lỗi, trả về null và nơi
     * gọi tự fallback về hành vi cũ (không ép network).
     */
    suspend fun getActiveWifiNetwork(context: Context): android.net.Network? =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = android.net.NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            var resumed = false
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    if (resumed) return
                    resumed = true
                    cont.resume(network) {
                        // Coroutine bị hủy trước khi resume kịp — không cần cleanup gì thêm ở đây,
                        // unregisterCallback() bên dưới trong invokeOnCancellation đã lo việc đó.
                    }
                    try {
                        cm.unregisterNetworkCallback(this)
                    } catch (e: Exception) {
                        // Callback có thể đã tự bị gỡ nếu request hết hạn đúng lúc — bỏ qua an toàn.
                    }
                }

                override fun onUnavailable() {
                    if (resumed) return
                    resumed = true
                    cont.resume(null) {}
                }
            }

            cont.invokeOnCancellation {
                try {
                    cm.unregisterNetworkCallback(callback)
                } catch (e: Exception) {
                    // Đã unregister rồi hoặc request không còn hiệu lực — bỏ qua an toàn.
                }
            }

            try {
                // requestNetwork(request, callback, timeoutMs) — tự gọi onUnavailable() nếu
                // không tìm được network hợp lệ trong 2000ms, không cần tự đặt Handler/Timer.
                cm.requestNetwork(request, callback, 2000)
            } catch (e: Exception) {
                if (!resumed) {
                    resumed = true
                    cont.resume(null) {}
                }
            }
        }
}

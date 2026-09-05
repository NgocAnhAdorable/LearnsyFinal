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
            val request = android.net.NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            var resumed = false
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    if (resumed) return
                    resumed = true
                    // KHÔNG unregister ở đây — callback phải sống tới khi FTP disconnect()
                    // gọi release(), giữ network ở mức ưu tiên cao suốt phiên làm việc.
                    cont.resume(
                        WifiNetworkHandle(network) {
                            try {
                                cm.unregisterNetworkCallback(this)
                            } catch (e: Exception) {
                                // Đã unregister rồi (vd gọi release() 2 lần) — bỏ qua an toàn.
                            }
                        }
                    ) {}
                }

                override fun onUnavailable() {
                    if (resumed) return
                    resumed = true
                    cont.resume(WifiNetworkHandle(null) {}) {}
                }
            }

            cont.invokeOnCancellation {
                try {
                    cm.unregisterNetworkCallback(callback)
                } catch (e: Exception) {
                    // Request có thể chưa từng thành công đăng ký — bỏ qua an toàn.
                }
            }

            try {
                cm.requestNetwork(request, callback, 4000)
            } catch (e: Exception) {
                if (!resumed) {
                    resumed = true
                    cont.resume(WifiNetworkHandle(null) {}) {}
                }
            }
        }
}

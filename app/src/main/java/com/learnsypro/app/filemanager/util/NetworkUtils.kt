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
     * Lấy Network object của Wi-Fi/Ethernet đang active — dùng để ép socket FTP
     * đi ra đúng mạng LAN này, thay vì để hệ thống tự chọn (có thể chọn nhầm mạng
     * di động khi cả 2 cùng bật, khiến kết nối tới IP nội bộ như 192.168.x.x bị
     * timeout im lặng dù Wi-Fi thực sự đang kết nối tốt — đây CHÍNH XÁC là
     * nguyên nhân lỗi "failed to connect ... after 8000ms" khi máy vừa có sóng
     * di động vừa có Wi-Fi: Android 10+ có thể route traffic loại "không rõ đích"
     * qua interface có priority cao hơn theo policy riêng của hệ điều hành,
     * không phải lúc nào cũng ưu tiên Wi-Fi dù đó là interface duy nhất biết
     * đường tới 192.168.x.x).
     *
     * Trả về null nếu không tìm thấy Wi-Fi/Ethernet đang active (ví dụ đang ở
     * Hotspot ngược — điện thoại này phát Wi-Fi cho máy khác — hoặc chưa kết nối
     * mạng nào) — nơi gọi nên fallback về hành vi cũ (không ép network) khi null,
     * để không chặn hẳn tính năng nếu phát hiện network thất bại vì lý do khác.
     */
    fun getActiveWifiNetwork(context: Context): android.net.Network? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        // Duyệt TOÀN BỘ network đã biết (không chỉ activeNetwork) — vì khi máy có
        // cả Wi-Fi lẫn di động, activeNetwork có thể đang trỏ vào di động (đúng
        // nguyên nhân lỗi), nên phải tự tìm network nào có transport Wi-Fi/Ethernet
        // trong danh sách, bất kể cái nào đang là "active" theo hệ thống.
        return cm.allNetworks.firstOrNull { net ->
            val caps = cm.getNetworkCapabilities(net) ?: return@firstOrNull false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        }
    }
}

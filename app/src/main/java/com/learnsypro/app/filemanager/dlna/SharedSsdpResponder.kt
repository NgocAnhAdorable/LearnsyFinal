package com.learnsypro.app.filemanager.dlna

import com.learnsypro.app.filemanager.util.LogBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface

/**
 * Advertisement 1 "vai trò" UPnP (MediaServer hoặc MediaRenderer) — mỗi vai trò tự khai báo
 * NT/USN/LOCATION riêng, nhưng KHÔNG tự mở socket UDP của mình.
 */
interface SsdpRole {
    /** true nếu 1 gói M-SEARCH nên được vai trò này trả lời. */
    fun matches(searchTarget: String): Boolean
    /** Danh sách (NT, USN) cần quảng bá khi trả lời M-SEARCH hoặc gửi NOTIFY định kỳ. */
    fun advertisements(): List<Pair<String, String>>
    val location: String
}

/**
 * SSDP responder DÙNG CHUNG 1 MulticastSocket duy nhất cho toàn app, thay vì mỗi vai trò
 * (SsdpResponder cho "Máy chủ Media", RendererSsdpResponder cho "Nhận phát") tự mở
 * MulticastSocket(1900) RIÊNG của mình như trước đây.
 *
 * NGUYÊN NHÂN BUG THẬT SỰ đã tìm ra: khi người dùng bật CẢ HAI tính năng cùng lúc (ảnh chụp màn
 * hình thực tế cho thấy cả "Máy chủ Media" lẫn "Nhận phát" đều đang chạy), có 2 MulticastSocket
 * độc lập cùng bind cổng UDP 1900 (dù đều set reuseAddress=true) — hệ điều hành Android/Linux
 * khi có nhiều socket cùng bind 1 port multicast sẽ giao MỖI gói tin đến chỉ MỘT trong số các
 * socket đó (hành vi không xác định, tùy kernel/thiết bị), không phải cả hai. Vì vậy gói
 * M-SEARCH từ VLC có xác suất bị "socket của RendererSsdpResponder" nhận trước — nếu ST hỏi
 * không khớp vai trò Renderer, gói đó bị bỏ qua hoàn toàn, "socket của SsdpResponder" (vai trò
 * MediaServer, cái LẼ RA phải trả lời) không bao giờ thấy gói đó — kết quả: VLC gửi M-SEARCH
 * đúng chuẩn nhưng không bao giờ nhận được response, dù server MediaServer vẫn chạy tốt và bản
 * thân nó không có lỗi gì. Test qua log DLNA từng thấy "Không gửi được ssdp:alive" lặp lại cũng
 * cùng gốc rễ chung (2 socket tranh nhau interface gửi).
 *
 * Gộp về 1 socket dùng chung loại bỏ hoàn toàn tình trạng tranh chấp này: mọi gói M-SEARCH đến
 * đều được 1 nơi duy nhất nhận, rồi hỏi TỪNG role đã đăng ký xem có muốn trả lời không.
 */
object SharedSsdpResponder {
    private var job: Job? = null
    private var socket: MulticastSocket? = null
    private val roles = mutableMapOf<String, SsdpRole>()
    private val lock = Mutex()
    private var localIp: String = ""

    /** Đăng ký 1 vai trò (key: "media_server" hoặc "renderer") và khởi động socket dùng chung nếu chưa chạy. */
    suspend fun register(key: String, role: SsdpRole, localIp: String) {
        lock.withLock {
            this.localIp = localIp
            roles[key] = role
        }
        ensureStarted()
        sendAliveFor(role)
    }

    /** Gỡ 1 vai trò; nếu không còn vai trò nào thì tự dừng hẳn socket dùng chung. */
    suspend fun unregister(key: String) {
        val shouldStop: Boolean
        lock.withLock {
            roles.remove(key)
            shouldStop = roles.isEmpty()
        }
        if (shouldStop) stopSocket()
    }

    private fun ensureStarted() {
        if (job != null) return
        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val group = InetAddress.getByName(SSDP_ADDRESS)
                val s = MulticastSocket(SSDP_PORT)
                s.reuseAddress = true
                val netIf = findNetworkInterface(localIp)
                if (netIf == null) {
                    // TRƯỚC ĐÂY: âm thầm gọi joinGroup(..., null) rồi vẫn log "success" — để hệ
                    // điều hành tự chọn NIC mặc định cho multicast. Trên máy đang bật song song
                    // Wi-Fi + dữ liệu di động (rất phổ biến), OS có thể chọn nhầm interface data
                    // thay vì Wi-Fi thật — kết quả giống hệt bug đã tìm ra ở trên: server báo
                    // "Đang chia sẻ" bình thường, nhưng M-SEARCH từ VLC/TV trên cùng Wi-Fi không
                    // bao giờ được nhận vì socket đang lắng nghe multicast trên NIC khác.
                    LogBus.warning(
                        "Không tìm thấy network interface khớp IP $localIp — SSDP có thể join nhầm " +
                            "interface (vd dữ liệu di động thay vì Wi-Fi), khiến VLC/TV không tự tìm thấy",
                        source = "DLNA"
                    )
                }
                s.joinGroup(InetSocketAddress(group, SSDP_PORT), netIf)
                if (netIf != null) s.networkInterface = netIf
                s.timeToLive = 4
                socket = s
                if (netIf != null) {
                    LogBus.success("SSDP responder dùng chung đã sẵn sàng trên interface ${netIf.name} ($localIp)", source = "DLNA")
                } else {
                    LogBus.warning("SSDP responder khởi động nhưng KHÔNG xác định được interface — kiểm tra lại kết nối mạng nếu VLC/TV không thấy thiết bị", source = "DLNA")
                }

                val buf = ByteArray(2048)
                var lastAliveAt = System.currentTimeMillis()
                s.soTimeout = ALIVE_INTERVAL_MS.toInt()
                while (isActive) {
                    try {
                        val packet = DatagramPacket(buf, buf.size)
                        s.receive(packet)
                        val text = String(packet.data, 0, packet.length)
                        if (text.startsWith("M-SEARCH", ignoreCase = true)) {
                            handleSearch(s, packet.address, packet.port, text)
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        // "nhịp tim" gửi lại NOTIFY định kỳ, không phải lỗi.
                    } catch (e: Exception) {
                        if (isActive) LogBus.warning("Lỗi khi xử lý gói SSDP", source = "DLNA")
                    }
                    val now = System.currentTimeMillis()
                    if (now - lastAliveAt >= ALIVE_INTERVAL_MS) {
                        lock.withLock { roles.values.toList() }.forEach { sendAliveFor(it, s, group) }
                        lastAliveAt = now
                    }
                }
            } catch (e: Exception) {
                LogBus.error("Không thể khởi động SSDP responder dùng chung", source = "DLNA", throwable = e)
            }
        }
    }

    private suspend fun stopSocket() {
        job?.cancel()
        job = null
        try {
            socket?.leaveGroup(InetSocketAddress(InetAddress.getByName(SSDP_ADDRESS), SSDP_PORT), findNetworkInterface(localIp))
        } catch (e: Exception) {
            // socket có thể đã đóng
        }
        socket?.close()
        socket = null
    }

    private fun requestedSt(request: String): String =
        request.lineSequence().firstOrNull { it.startsWith("ST:", ignoreCase = true) }
            ?.substringAfter(":", "")?.trim().orEmpty()

    private suspend fun handleSearch(socket: MulticastSocket, toAddress: InetAddress, toPort: Int, request: String) {
        val st = requestedSt(request)
        val matchingRoles = lock.withLock { roles.values.toList() }.filter { it.matches(st) }
        matchingRoles.forEach { role ->
            role.advertisements().forEach { (nt, usn) ->
                val response = """
                    HTTP/1.1 200 OK
                    CACHE-CONTROL: max-age=1800
                    EXT:
                    LOCATION: ${role.location}
                    SERVER: Android/UPnP/1.0 LearnsyPro/1.0
                    ST: $nt
                    USN: $usn

                """.trimIndent().replace("\n", "\r\n")
                val bytes = response.toByteArray()
                try {
                    socket.send(DatagramPacket(bytes, bytes.size, toAddress, toPort))
                } catch (e: Exception) {
                    LogBus.warning("Không gửi được phản hồi SSDP", source = "DLNA")
                }
            }
        }
    }

    private fun sendAliveFor(role: SsdpRole) {
        val s = socket ?: return
        val group = try { InetAddress.getByName(SSDP_ADDRESS) } catch (e: Exception) { return }
        sendAliveFor(role, s, group)
    }

    private fun sendAliveFor(role: SsdpRole, s: MulticastSocket, group: InetAddress) {
        role.advertisements().forEach { (nt, usn) ->
            val notify = """
                NOTIFY * HTTP/1.1
                HOST: $SSDP_ADDRESS:$SSDP_PORT
                CACHE-CONTROL: max-age=1800
                LOCATION: ${role.location}
                SERVER: Android/UPnP/1.0 LearnsyPro/1.0
                NT: $nt
                NTS: ssdp:alive
                USN: $usn

            """.trimIndent().replace("\n", "\r\n")
            val bytes = notify.toByteArray()
            try {
                s.send(DatagramPacket(bytes, bytes.size, group, SSDP_PORT))
            } catch (e: Exception) {
                LogBus.warning("Không gửi được thông báo ssdp:alive ($nt)", source = "DLNA")
            }
        }
    }

    private fun findNetworkInterface(ip: String): NetworkInterface? {
        return try {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .firstOrNull { intf ->
                    intf.isUp && !intf.isLoopback &&
                        intf.inetAddresses.asSequence().any { it.hostAddress == ip }
                }
        } catch (e: Exception) {
            null
        }
    }

    private const val SSDP_ADDRESS = "239.255.255.250"
    private const val SSDP_PORT = 1900
    private const val ALIVE_INTERVAL_MS = 20_000L
}

/** Vai trò MediaServer (bật ở "Máy chủ Media") — quảng bá cho control point kiểu VLC/TV tìm thấy để duyệt/phát file. */
class MediaServerSsdpRole(httpPort: Int, private val udn: String, localIp: String) : SsdpRole {
    override val location = "http://$localIp:$httpPort/description.xml"

    override fun matches(searchTarget: String): Boolean =
        searchTarget == "ssdp:all" || searchTarget == "upnp:rootdevice" || searchTarget.startsWith("uuid:$udn") ||
            searchTarget.contains("MediaServer") || searchTarget.contains("ContentDirectory")

    override fun advertisements(): List<Pair<String, String>> = listOf(
        "upnp:rootdevice" to "uuid:$udn::upnp:rootdevice",
        "uuid:$udn" to "uuid:$udn",
        "urn:schemas-upnp-org:device:MediaServer:1" to "uuid:$udn::urn:schemas-upnp-org:device:MediaServer:1",
        "urn:schemas-upnp-org:service:ContentDirectory:1" to "uuid:$udn::urn:schemas-upnp-org:service:ContentDirectory:1"
    )
}

/** Vai trò MediaRenderer (bật ở "Nhận phát từ thiết bị khác") — quảng bá cho control point kiểu BubbleUPnP tìm thấy để "Phát tới". */
class RendererSsdpRole(httpPort: Int, private val udn: String, localIp: String) : SsdpRole {
    override val location = "http://$localIp:$httpPort/renderer/description.xml"

    override fun matches(searchTarget: String): Boolean =
        searchTarget == "ssdp:all" || searchTarget.contains("MediaRenderer") || searchTarget == "upnp:rootdevice" ||
            searchTarget.contains("AVTransport") || searchTarget.contains("RenderingControl") || searchTarget.startsWith("uuid:$udn")

    override fun advertisements(): List<Pair<String, String>> = listOf(
        "upnp:rootdevice" to "uuid:$udn::upnp:rootdevice",
        "urn:schemas-upnp-org:device:MediaRenderer:1" to "uuid:$udn::urn:schemas-upnp-org:device:MediaRenderer:1",
        "urn:schemas-upnp-org:service:AVTransport:1" to "uuid:$udn::urn:schemas-upnp-org:service:AVTransport:1",
        "urn:schemas-upnp-org:service:RenderingControl:1" to "uuid:$udn::urn:schemas-upnp-org:service:RenderingControl:1"
    )
}

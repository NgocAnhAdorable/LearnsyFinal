package com.learnsypro.app.filemanager.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.learnsypro.app.filemanager.MainActivity
import com.learnsypro.app.R
import com.learnsypro.app.filemanager.dlna.MediaServerSsdpRole
import com.learnsypro.app.filemanager.dlna.SharedSsdpResponder
import com.learnsypro.app.filemanager.server.DlnaIds
import com.learnsypro.app.filemanager.util.LogBus
import com.learnsypro.app.filemanager.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Foreground Service giữ MediaStreamServer (HTTP) sống khi app ở nền.
 *
 * Có 2 vai trò dùng chung 1 server HTTP:
 *  1. "Phát lên TV" (DLNA push): đăng ký từng file cụ thể, gửi lệnh AVTransport cho TV tự
 *     kết nối lấy đúng file đó — dùng registerAndGetUrl().
 *  2. "Máy chủ Media" (chia sẻ toàn bộ thư mục): bật kèm SharedSsdpResponder để TV/Smart TV/VLC
 *     TỰ nhìn thấy thiết bị trong danh sách nguồn (DLNA), đồng thời cho phép mở bằng trình
 *     duyệt bất kỳ (laptop, TV có browser) qua đường dẫn /browse/ mà không cần cài app.
 *
 * Dùng SharedSsdpResponder (1 socket UDP dùng chung toàn app) thay vì tự mở MulticastSocket
 * riêng — trước đây khi cả "Máy chủ Media" lẫn "Nhận phát" cùng bật, 2 socket độc lập tranh
 * nhau nhận gói M-SEARCH khiến VLC/TV có xác suất không bao giờ nhận được response dù server
 * chạy đúng (xem giải thích đầy đủ trong SharedSsdpResponder.kt).
 */
class MediaCastService : Service() {

    private val binder = LocalBinder()
    private var streamServer: MediaStreamServer? = null
    private var ssdpRegistered = false

    inner class LocalBinder : Binder() {
        fun getService(): MediaCastService = this@MediaCastService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val rootPath = intent.getStringExtra(EXTRA_ROOT_FOLDER)
                startServer(rootPath?.let { File(it) })
            }
            ACTION_STOP -> stopServer()
        }
        return START_STICKY
    }

    private fun startServer(rootFolder: File?) {
        if (streamServer != null) return
        startForeground(NOTIFICATION_ID, buildNotification())
        try {
            val server = MediaStreamServer(STREAM_PORT, rootFolder)
            server.start(NanoHttpdTimeout.SOCKET_READ_TIMEOUT, false)
            streamServer = server
            isRunningStatic = true
            LogBus.success("Máy chủ phát media LAN đã khởi động trên cổng $STREAM_PORT", source = "STREAM")

            // Chỉ bật DLNA discovery (SSDP) khi có thư mục gốc để duyệt, tức chế độ
            // "Máy chủ Media" chia sẻ toàn bộ thư mục — không bật khi chỉ push 1 file lẻ.
            if (rootFolder != null) {
                val ip = NetworkUtils.getLocalIpAddress(applicationContext)
                if (ip != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        SharedSsdpResponder.register(
                            key = "media_server",
                            role = MediaServerSsdpRole(httpPort = STREAM_PORT, udn = DlnaIds.udn, localIp = ip),
                            localIp = ip
                        )
                    }
                    ssdpRegistered = true
                } else {
                    LogBus.warning("Không lấy được IP LAN, TV sẽ không tự tìm thấy máy chủ (SSDP)", source = "DLNA")
                }
            }
        } catch (e: Exception) {
            LogBus.error("Không thể khởi động máy chủ phát media (cổng $STREAM_PORT)", source = "STREAM", throwable = e)
            isRunningStatic = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopServer() {
        unregisterSsdp()
        streamServer?.stop()
        streamServer = null
        isRunningStatic = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun unregisterSsdp() {
        if (!ssdpRegistered) return
        ssdpRegistered = false
        CoroutineScope(Dispatchers.IO).launch {
            SharedSsdpResponder.unregister("media_server")
        }
    }

    /** Đăng ký 1 file để phát qua LAN, trả về URL đầy đủ (vd http://192.168.1.5:8090/stream/xxx/ten.mp4). */
    fun registerAndGetUrl(file: File): String? {
        val server = streamServer ?: return null
        val ip = NetworkUtils.getLocalIpAddress(applicationContext) ?: return null
        val token = server.register(file)
        return server.urlFor(ip, token, file.name)
    }

    /** URL duyệt toàn bộ thư mục bằng trình duyệt (chỉ hợp lệ nếu server khởi động kèm rootFolder). */
    fun getBrowseUrl(): String? {
        val server = streamServer ?: return null
        val ip = NetworkUtils.getLocalIpAddress(applicationContext) ?: return null
        return server.browseUrlFor(ip)
    }

    fun isServerRunning(): Boolean = streamServer != null

    override fun onDestroy() {
        unregisterSsdp()
        streamServer?.stop()
        streamServer = null
        isRunningStatic = false
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_stream_running))
            .setContentText(getString(R.string.notif_tap_to_open))
            .setSmallIcon(R.drawable.ic_app_logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_stream_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START = "com.learnsypro.app.filemanager.action.STREAM_START"
        const val ACTION_STOP = "com.learnsypro.app.filemanager.action.STREAM_STOP"
        const val EXTRA_ROOT_FOLDER = "extra_root_folder"
        const val STREAM_PORT = 8090
        private const val CHANNEL_ID = "media_cast_channel"
        private const val NOTIFICATION_ID = 1002

        @Volatile private var isRunningStatic: Boolean = false
        fun isRunning(): Boolean = isRunningStatic
    }
}

private object NanoHttpdTimeout {
    const val SOCKET_READ_TIMEOUT = 15000
}

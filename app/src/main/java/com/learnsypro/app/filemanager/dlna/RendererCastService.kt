package com.learnsypro.app.filemanager.dlna

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
import androidx.core.content.ContextCompat
import com.learnsypro.app.filemanager.MainActivity
import com.learnsypro.app.R
import com.learnsypro.app.filemanager.server.DlnaIds
import com.learnsypro.app.filemanager.util.LogBus
import com.learnsypro.app.filemanager.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Foreground Service giữ RendererServer (HTTP/SOAP) sống khi app ở nền — vai trò UPnP
 * MediaRenderer (nhận cast TỪ thiết bị khác), song song và độc lập với MediaCastService
 * (vai trò MediaServer, chia sẻ file CHO thiết bị khác).
 *
 * Dùng SharedSsdpResponder (1 socket UDP dùng chung toàn app) thay vì tự mở MulticastSocket
 * riêng — xem giải thích đầy đủ trong SharedSsdpResponder.kt về bug tranh chấp socket khi cả
 * 2 vai trò cùng bật.
 *
 * Tự khởi động RendererPlaybackService (ExoPlayer) cùng lúc vì renderer vô nghĩa nếu không
 * có gì để phát nhạc/video nhận được.
 */
class RendererCastService : Service() {

    private val binder = LocalBinder()
    private var httpServer: RendererServer? = null
    private var ssdpRegistered = false

    inner class LocalBinder : Binder() {
        fun getService(): RendererCastService = this@RendererCastService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRenderer()
            ACTION_STOP -> stopRenderer()
        }
        return START_STICKY
    }

    private fun startRenderer() {
        if (httpServer != null) return
        startForeground(NOTIFICATION_ID, buildNotification())
        try {
            val server = RendererServer(RENDERER_PORT)
            server.start(SOCKET_READ_TIMEOUT_MS, false)
            httpServer = server
            isRunningStatic = true

            // Khởi động luôn service phát nhạc/video (ExoPlayer) — không phát gì cho tới khi
            // có lệnh SetAVTransportURI từ thiết bị điều khiển gửi tới.
            ContextCompat.startForegroundService(
                this, Intent(this, RendererPlaybackService::class.java)
            )

            val ip = NetworkUtils.getLocalIpAddress(applicationContext)
            if (ip != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    SharedSsdpResponder.register(
                        key = "renderer",
                        role = RendererSsdpRole(httpPort = RENDERER_PORT, udn = DlnaIds.rendererUdn, localIp = ip),
                        localIp = ip
                    )
                }
                ssdpRegistered = true
                LogBus.success("Đã bật nhận phát từ thiết bị khác (DLNA Renderer)", source = "DLNA")
            } else {
                LogBus.warning("Không lấy được IP LAN, thiết bị khác sẽ không tự tìm thấy renderer (SSDP)", source = "DLNA")
            }
        } catch (e: Exception) {
            LogBus.error("Không thể khởi động renderer (cổng $RENDERER_PORT)", source = "DLNA", throwable = e)
            isRunningStatic = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopRenderer() {
        unregisterSsdp()
        httpServer?.stop()
        httpServer = null
        isRunningStatic = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun unregisterSsdp() {
        if (!ssdpRegistered) return
        ssdpRegistered = false
        CoroutineScope(Dispatchers.IO).launch {
            SharedSsdpResponder.unregister("renderer")
        }
    }

    fun isRendererRunning(): Boolean = httpServer != null

    override fun onDestroy() {
        unregisterSsdp()
        httpServer?.stop()
        httpServer = null
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
            .setContentTitle(getString(R.string.notif_renderer_running))
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
                getString(R.string.notif_renderer_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START = "com.learnsypro.app.filemanager.action.RENDERER_START"
        const val ACTION_STOP = "com.learnsypro.app.filemanager.action.RENDERER_STOP"
        const val RENDERER_PORT = 8091
        private const val SOCKET_READ_TIMEOUT_MS = 15000
        private const val CHANNEL_ID = "renderer_cast_channel"
        private const val NOTIFICATION_ID = 1003

        @Volatile private var isRunningStatic: Boolean = false
        fun isRunning(): Boolean = isRunningStatic
    }
}

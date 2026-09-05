package com.learnsypro.app.data

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.view.Choreographer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class DevicePerfResult(
    val score: Int,
    val reason: List<String>,
    val fps: Int? = null,
    val isLow: Boolean = false,
    val label: String = ""
)

/**
 * ── detectDevicePerformance (thay bản JS trong dashboard.jsx) ──
 *
 * Bản web dùng navigator.deviceMemory/hardwareConcurrency/connection + FPS
 * test bằng requestAnimationFrame. Trên Android, các API tương đương chính
 * xác hơn nhiều:
 * - RAM: ActivityManager.MemoryInfo.totalMem (chính xác tuyệt đối, không như
 *   navigator.deviceMemory vốn bị browser làm tròn/giới hạn)
 * - CPU cores: Runtime.getRuntime().availableProcessors()
 * - Network: ConnectivityManager + NetworkCapabilities (phát hiện loại mạng
 *   metered/cellular thay vì effectiveType ước lượng của Chrome)
 * - FPS: Choreographer đo 40 frame thực tế, cùng logic ngưỡng như bản gốc
 *
 * Thang điểm và ngưỡng isLow/label giữ NGUYÊN logic gốc để hành vi nhất quán
 * giữa bản web và bản Android.
 */
/**
 * ── detectChipsetPenalty ──
 * RAM/số core không phản ánh đúng khả năng GPU. Rất nhiều máy MediaTek
 * (Helio G-series, Dimensity tầm trung) và Exynos non-flagship (Exynos 850,
 * 1280, 9xxx đời cũ) dùng GPU Mali — vốn yếu hơn hẳn Adreno (Snapdragon)
 * cùng tầm giá khi phải xử lý nhiều lớp compositing trong suốt / blur, dù
 * RAM và core count nhìn "ổn". Dò trực tiếp qua tên chipset để phạt điểm
 * thêm cho các dòng Mali GPU tầm trung/thấp đã biết.
 *
 * Build.SOC_MODEL/SOC_MANUFACTURER chỉ có từ API 31 — máy cũ hơn (minSdk 26)
 * dùng fallback Build.HARDWARE/Build.BOARD (thường chứa tên chipset thô,
 * vd. "mt6768", "exynos850").
 */
private fun detectChipsetPenalty(): Pair<Int, String?> {
    val socHint = buildString {
        if (Build.VERSION.SDK_INT >= 31) {
            append(Build.SOC_MANUFACTURER).append(' ').append(Build.SOC_MODEL).append(' ')
        }
        append(Build.HARDWARE).append(' ').append(Build.BOARD).append(' ').append(Build.MODEL)
    }.lowercase()

    val isMediatek = socHint.contains("mt6") || socHint.contains("mediatek") || socHint.contains("helio") || socHint.contains("dimensity")
    val isExynos = socHint.contains("exynos") || socHint.contains("universal") || socHint.contains("s5e")

    // Dòng chip tầm trung/thấp đã biết là yếu về GPU (Mali-G52/G57/G68 hoặc cũ hơn)
    val knownWeakMediatek = listOf("mt6768", "mt6769", "mt6765", "mt6762", "mt6785", "helio g", "helio a", "helio p")
    val knownWeakExynos = listOf("exynos850", "exynos 850", "s5e8825", "exynos1280", "exynos 1280", "s5e8535", "exynos9611", "exynos 850")

    return when {
        knownWeakMediatek.any { socHint.contains(it) } -> 22 to "MediaTek tầm trung/thấp (GPU Mali yếu)"
        knownWeakExynos.any { socHint.contains(it) } -> 22 to "Exynos tầm trung/thấp (GPU Mali yếu)"
        isMediatek -> 10 to "Chip MediaTek (Mali GPU, thường yếu hơn Snapdragon cùng tầm)"
        isExynos -> 10 to "Chip Exynos (Mali GPU, thường yếu hơn Snapdragon cùng tầm)"
        else -> 0 to null
    }
}

suspend fun detectDevicePerformance(context: Context): DevicePerfResult {
    var score = 100
    val reasons = mutableListOf<String>()

    // 1. RAM
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memInfo)
    val ramGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
    when {
        ramGb <= 1.5 -> { score -= 40; reasons.add("RAM thấp (${"%.1f".format(ramGb)}GB)") }
        ramGb <= 3.0 -> { score -= 20; reasons.add("RAM hạn chế (${"%.1f".format(ramGb)}GB)") }
    }

    // 1b. Chipset/GPU — Mali GPU (MediaTek/Exynos tầm trung-thấp) yếu hơn RAM/core
    // gợi ý, nên phạt điểm riêng bất kể thông số ở trên.
    val (chipsetPenalty, chipsetReason) = detectChipsetPenalty()
    if (chipsetPenalty > 0) {
        score -= chipsetPenalty
        if (chipsetReason != null) reasons.add(chipsetReason)
    }

    // 2. CPU cores
    val cores = Runtime.getRuntime().availableProcessors()
    when {
        cores <= 2 -> { score -= 25; reasons.add("CPU yếu ($cores core)") }
        cores <= 4 -> score -= 10
    }

    // 3. Network
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork
    val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
    if (capabilities != null) {
        val isMetered = connectivityManager.isActiveNetworkMetered
        val isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        if (!hasInternet) {
            score -= 20
            reasons.add("Mạng chậm hoặc không ổn định")
        } else if (isCellular && isMetered) {
            score -= 8 // gần tương đương "3g" trong bản gốc — cellular thường chậm hơn wifi
        }
    }

    // 4. FPS test — đo 40 frame bằng Choreographer, cùng ngưỡng bản gốc
    val fps = measureFps(frameCount = 40)
    when {
        fps < 30 -> { score -= 35; reasons.add("FPS thấp (~${fps}fps)") }
        fps <= 40 -> { score -= 18; reasons.add("FPS trung bình (~${fps}fps)") }
        fps < 55 -> score -= 6
    }

    val isLow = score < 65 || fps <= 40
    val label = when {
        isLow -> "Máy yếu — nên bật Lite Mode"
        score < 80 -> "Máy trung bình — có thể bật Lite Mode"
        else -> "Máy mạnh — không cần Lite Mode"
    }

    return DevicePerfResult(
        score = score.coerceIn(0, 100),
        reason = reasons,
        fps = fps,
        isLow = isLow,
        label = label
    )
}

/** Đo FPS thực tế bằng Choreographer trong `frameCount` khung hình liên tiếp. */
private suspend fun measureFps(frameCount: Int): Int = suspendCancellableCoroutine { cont ->
    var frames = 0
    var startNanos = 0L
    val choreographer = Choreographer.getInstance()

    val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (frames == 0) startNanos = frameTimeNanos
            frames++
            if (frames < frameCount) {
                choreographer.postFrameCallback(this)
            } else {
                val elapsedSeconds = (frameTimeNanos - startNanos) / 1_000_000_000.0
                val fps = if (elapsedSeconds > 0) (frameCount / elapsedSeconds).toInt() else 60
                if (cont.isActive) cont.resume(fps)
            }
        }
    }
    choreographer.postFrameCallback(callback)

    cont.invokeOnCancellation {
        choreographer.removeFrameCallback(callback)
    }
}

/**
 * ── runLoginPerfCheck ──
 * Tương đương hàm cùng tên trong bản web: chạy 1 lần khi đăng nhập, không
 * chạy lại nếu đã kiểm tra trong phiên này (dùng biến static thay session-
 * Storage vì Android process sống theo app lifecycle chứ không theo tab).
 */
private var perfCheckedThisSession = false

suspend fun runLoginPerfCheck(context: Context, savedLiteMode: Boolean): DevicePerfResult {
    if (perfCheckedThisSession) {
        return DevicePerfResult(score = if (savedLiteMode) 0 else 100, reason = emptyList(), isLow = savedLiteMode)
    }
    val result = detectDevicePerformance(context)
    perfCheckedThisSession = true
    return result
}

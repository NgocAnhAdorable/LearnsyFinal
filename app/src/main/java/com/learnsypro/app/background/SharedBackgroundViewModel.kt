package com.learnsypro.app.background

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.learnsypro.app.data.BackgroundSettingsRepository
import com.learnsypro.app.data.SESSION_STUDENT_ID_KEY
import com.learnsypro.app.data.SESSION_USERNAME_KEY
import com.learnsypro.app.data.SupabaseClientProvider
import com.learnsypro.app.data.UpstashClient
import com.learnsypro.app.data.sessionDataStore
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Rớt về key này CHỈ khi Admin chưa xác định được UID (hiếm — ví dụ đang giữa lúc khôi phục phiên đăng nhập). */
private const val ADMIN_FALLBACK_KEY = "admin_unknown"

/**
 * ── SharedBackgroundViewModel ──
 * Cùng logic load/save/upload nền như phần background-* trong
 * DashboardViewModel (Student), tách ra riêng để Admin và File Manager
 * (view-based) dùng được mà không phải kéo theo toàn bộ DashboardViewModel.
 *
 * ĐÃ SỬA (lần 4): MỖI tài khoản Admin (mỗi email/password Supabase Auth
 * riêng) giờ có ảnh nền RIÊNG của chính mình — key = "admin_<uid>" với uid
 * là UUID duy nhất của tài khoản đó trên Supabase Auth. 2 admin khác tài
 * khoản không còn thấy/ghi đè nền của nhau nữa.
 *   - isAdminContext = true  -> đọc currentUserOrNull().id làm key riêng.
 *   - isAdminContext = false -> đọc sessionDataStore lấy đúng học sinh đang
 *     đăng nhập (File Manager mở ra sau khi học sinh đăng nhập ở Student),
 *     mỗi học sinh vẫn có nền riêng như trước, không đổi gì ở nhánh này.
 */
class SharedBackgroundViewModel(
    application: Application,
    private val isAdminContext: Boolean = false
) : AndroidViewModel(application) {

    /** Factory để truyền cờ isAdminContext — dùng ở AppRoot.kt (Admin: isAdminContext=true). */
    class Factory(
        private val application: Application,
        private val isAdminContext: Boolean
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return SharedBackgroundViewModel(application, isAdminContext) as T
        }
    }

    private val upstash = UpstashClient()
    private val bgRepo = BackgroundSettingsRepository(application, upstash)

    private val _bgSettings = MutableStateFlow(BgSettings())
    val bgSettings: StateFlow<BgSettings> = _bgSettings.asStateFlow()

    private val _bgUploading = MutableStateFlow(false)
    val bgUploading: StateFlow<Boolean> = _bgUploading.asStateFlow()

    private val _bgUploadError = MutableStateFlow<String?>(null)
    val bgUploadError: StateFlow<String?> = _bgUploadError.asStateFlow()

    private val _bgSyncState = MutableStateFlow("idle") // idle | saving | saved | error
    val bgSyncState: StateFlow<String> = _bgSyncState.asStateFlow()

    private var bgSyncJob: Job? = null
    // "Chốt" đánh dấu init{} đã load xong local+remote — dùng CompletableDeferred
    // thay vì Job? thường vì onBgDarkModeChanged() có thể được LaunchedEffect(dark)
    // gọi TRƯỚC khi init{} kịp load xong (dark mode đã bật sẵn lúc AppRoot() mount) —
    // nếu không đợi, init{} load xong SAU sẽ ghi đè _bgSettings bằng preset/blurMode
    // GỐC từ server, đè mất việc "off" hoá vừa làm, khiến bgBlurBackup không còn khớp
    // với những gì đang thực sự hiển thị — lúc tắt dark mode khôi phục sai/preset rớt
    // về default. Đây là nguyên nhân "tắt dark mode xong preset ảnh nền bị mất".
    private var bgLoadedSignal = kotlinx.coroutines.CompletableDeferred<Unit>()
    private var bgBlurBackup: Pair<String, Int>? = null
    private var bgDarkWasOn = false

    // ID để đồng bộ nền — xác định 1 lần từ resolveSyncId() lúc init().
    private var syncId: String = SHARED_BG_KEY

    private suspend fun resolveSyncId(): String {
        if (isAdminContext) {
            val adminUser = try {
                SupabaseClientProvider.client.auth.currentUserOrNull()
            } catch (e: Exception) {
                null
            }
            // uid riêng của TÀI KHOẢN admin đang đăng nhập -> mỗi tài khoản 1 nền riêng.
            return adminUser?.id?.let { "admin_$it" } ?: ADMIN_FALLBACK_KEY
        }
        return try {
            val prefs = getApplication<Application>().sessionDataStore.data.first()
            val studentId = prefs[SESSION_STUDENT_ID_KEY]
            val username = prefs[SESSION_USERNAME_KEY]
            (studentId ?: username)?.ifBlank { null } ?: SHARED_BG_KEY
        } catch (e: Exception) {
            SHARED_BG_KEY
        }
    }

    init {
        viewModelScope.launch {
            syncId = resolveSyncId()
            val local = bgRepo.loadLocal(syncId)
            _bgSettings.value = local
            val remote = bgRepo.loadRemote(syncId)
            if (remote != null) {
                val merged = local.copy(
                    presetId = remote.presetId,
                    blurMode = remote.blurMode,
                    blurPercent = remote.blurPercent,
                    imageUrl = remote.imageUrl ?: local.imageUrl
                )
                _bgSettings.value = merged
                bgRepo.saveLocal(syncId, merged)
            }
            // Nếu dark mode đã bật SẴN từ trước khi load xong, _bgSettings vừa bị
            // ghi đè bằng blurMode GỐC — chưa hề bị ép "off". Áp lại ngay ở đây để
            // đồng bộ đúng với bgDarkWasOn hiện tại (xem giải thích ở khai báo
            // bgLoadedSignal phía trên).
            if (bgDarkWasOn) {
                val s = _bgSettings.value
                if (s.blurMode != "off") {
                    bgBlurBackup = s.blurMode to s.blurPercent
                    applyBgSettings(s.copy(blurMode = "off", blurPercent = 0))
                }
            }
            if (!bgLoadedSignal.isCompleted) bgLoadedSignal.complete(Unit)
        }
    }

    fun clearBgUploadError() { _bgUploadError.value = null }

    // Job riêng để debounce việc ghi DataStore local (đĩa máy) — TÁCH BIỆT với
    // bgSyncJob (debounce lưu Upstash). Trước đây saveLocal() chạy NGAY LẬP TỨC
    // mỗi lần applyBgSettings() được gọi, không debounce gì cả — trong khi
    // Slider.onValueChange (kéo % độ mờ nền) bắn ra hàng chục lần MỖI GIÂY khi
    // kéo, mỗi lần đều gọi applyBgSettings() → ghi thẳng xuống đĩa (DataStore
    // Preferences ghi cả file .preferences_pb mỗi lần edit{}, không phải ghi
    // từng byte thay đổi) hàng chục/trăm lần chỉ trong 1 lần kéo slider — hao
    // mòn TBW của bộ nhớ flash không cần thiết, dù người dùng chỉ có Ý ĐỊNH lưu
    // 1 giá trị cuối cùng. Debounce 500ms: chỉ ghi đĩa sau khi người dùng NGỪNG
    // thay đổi trong 500ms — đúng 1 lần ghi cho mỗi lần kéo/chọn, thay vì một
    // lần ghi cho mỗi frame kéo.
    private var bgLocalSaveJob: Job? = null

    private fun applyBgSettings(next: BgSettings) {
        _bgSettings.value = next
        bgLocalSaveJob?.cancel()
        bgLocalSaveJob = viewModelScope.launch {
            delay(500)
            bgRepo.saveLocal(syncId, next)
        }

        bgSyncJob?.cancel()
        bgSyncJob = viewModelScope.launch {
            _bgSyncState.value = "saving"
            delay(800)
            try {
                bgRepo.saveRemote(syncId, next)
                _bgSyncState.value = "saved"
                delay(2000)
                _bgSyncState.value = "idle"
            } catch (e: Exception) {
                _bgSyncState.value = "error"
                delay(3000)
                _bgSyncState.value = "idle"
            }
        }
    }

    fun pickBgPreset(presetId: String) {
        applyBgSettings(_bgSettings.value.copy(presetId = presetId))
    }

    fun pickBgBlurMode(dark: Boolean, modeId: String) {
        if (dark) return
        val percent = if (modeId == "off") 0 else legacyModeToPercent(modeId)
        bgBlurBackup = modeId to percent
        applyBgSettings(_bgSettings.value.copy(blurMode = modeId, blurPercent = percent))
    }

    fun pickBgBlurPercent(dark: Boolean, percent: Int) {
        if (dark) return
        val p = clampPercent(percent)
        bgBlurBackup = "custom" to p
        applyBgSettings(_bgSettings.value.copy(blurMode = "custom", blurPercent = p))
    }

    suspend fun uploadBgImage(uri: Uri) {
        _bgUploading.value = true
        val result = bgRepo.uploadImage(syncId, uri)
        if (result.ok && result.msg != null) {
            applyBgSettings(_bgSettings.value.copy(presetId = "custom_image", imageUrl = result.msg))
        } else {
            _bgUploadError.value = result.msg
        }
        _bgUploading.value = false
    }

    fun removeBgImage() {
        applyBgSettings(_bgSettings.value.copy(presetId = "default_light", imageUrl = null))
        viewModelScope.launch { bgRepo.deleteImage(syncId) }
    }

    /** Giữ đúng hành vi gốc: dark mode ép blur về 'off', khôi phục lại khi tắt dark. */
    fun onBgDarkModeChanged(nowDark: Boolean) {
        if (nowDark == bgDarkWasOn) return
        bgDarkWasOn = nowDark
        viewModelScope.launch {
            // Đợi init{} load xong local+remote trước khi đọc/ghi _bgSettings — xem
            // giải thích đầy đủ ở khai báo bgLoadedSignal phía trên.
            bgLoadedSignal.await()
            val s = _bgSettings.value
            if (nowDark) {
                if (s.blurMode != "off") bgBlurBackup = s.blurMode to s.blurPercent
                applyBgSettings(s.copy(blurMode = "off", blurPercent = 0))
            } else {
                val (mode, percent) = bgBlurBackup ?: ("none" to 0)
                applyBgSettings(s.copy(blurMode = mode, blurPercent = percent))
            }
        }
    }
}

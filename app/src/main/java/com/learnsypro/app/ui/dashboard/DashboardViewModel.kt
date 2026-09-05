package com.learnsypro.app.ui.dashboard

import android.app.Application
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.learnsypro.app.background.BgSettings
import com.learnsypro.app.background.SHARED_BG_KEY
import com.learnsypro.app.background.clampPercent
import com.learnsypro.app.background.legacyModeToPercent
import com.learnsypro.app.data.AvatarRepository
import com.learnsypro.app.data.BackgroundSettingsRepository
import com.learnsypro.app.data.SupabaseClientProvider
import com.learnsypro.app.data.UpstashClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

// Không private: MainActivity đọc lại đúng DataStore singleton này để cấp
// giá trị cho LocalLiteMode ở gốc cây Compose. DataStore chỉ cho phép 1
// instance active cho mỗi tên file trong toàn process — khai delegate thứ
// hai trỏ cùng "learnsy_dashboard_prefs" sẽ crash lúc runtime, nên phải
// tái dùng chính property này thay vì tạo delegate mới ở MainActivity.
val Application.dashboardPrefsDataStore by preferencesDataStore(name = "learnsy_dashboard_prefs")
val LITE_MODE_KEY = booleanPreferencesKey("bb_lite_mode")
private val FLICKER_FX_KEY = booleanPreferencesKey("bb_flicker_fx")
private val SHUFFLE_Q_KEY = booleanPreferencesKey("bb_shuffle_q")
private val SHUFFLE_A_KEY = booleanPreferencesKey("bb_shuffle_a")
private val MASCOT_ENABLED_KEY = booleanPreferencesKey("bb_mascot_enabled")
private val DARK_MODE_KEY = booleanPreferencesKey("learnsy_dark") // dùng chung key với MainActivity

data class AchievementUnlock(val icon: String, val label: String, val color: androidx.compose.ui.graphics.Color)

@Serializable
private data class StudentRow(
    val id: String? = null,
    val username: String,
    val display_name: String? = null,
    val class_name: String? = null
)

/**
 * ── DashboardViewModel ──
 * Thay toàn bộ state cục bộ + useEffect trong function DashboardEnhanced.
 *
 * Bao gồm:
 * - tab hiện tại, achievement queue (unlock khi làm bài mới, tương đương
 *   useEffect theo dõi normHistory.length)
 * - liteMode/flickerFx/shuffleQ/shuffleA — lưu DataStore, tương đương
 *   localStorage.getItem/setItem trong bản gốc
 * - sync display_name mới nhất từ Supabase (tương đương fetchStudentInfo,
 *   nhưng KHÔNG cần lắng nghe window event 'learnsy:student-saved' vì đó
 *   là cơ chế đặc thù DOM — thay bằng gọi refreshStudentInfo() thủ công
 *   sau khi màn hình chỉnh sửa hồ sơ đóng lại)
 * - avatar (dùng AvatarRepository đã làm ở đợt 3)
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = application.dashboardPrefsDataStore
    private val upstash = UpstashClient()
    private val avatarRepo = AvatarRepository(application)
    private val coverRepo = com.learnsypro.app.data.CoverPhotoRepository(application)
    private val bgRepo = BackgroundSettingsRepository(application, upstash)

    // ── Tab & UI state ──
    private val _tab = MutableStateFlow(DashboardTab.HOME)
    val tab: StateFlow<DashboardTab> = _tab.asStateFlow()

    private val _achievementQueue = MutableStateFlow<List<AchievementUnlock>>(emptyList())
    val achievementQueue: StateFlow<List<AchievementUnlock>> = _achievementQueue.asStateFlow()

    // ── Preferences (tương đương localStorage bb-lite-mode / bb-flicker-fx / ...) ──
    private val _liteMode = MutableStateFlow(false)
    val liteMode: StateFlow<Boolean> = _liteMode.asStateFlow()

    private val _flickerFx = MutableStateFlow(true)
    val flickerFx: StateFlow<Boolean> = _flickerFx.asStateFlow()

    private val _shuffleQ = MutableStateFlow(false)
    val shuffleQ: StateFlow<Boolean> = _shuffleQ.asStateFlow()

    private val _shuffleA = MutableStateFlow(false)
    val shuffleA: StateFlow<Boolean> = _shuffleA.asStateFlow()

    // Bật/tắt bạn nữ tai mèo xuất hiện ngẫu nhiên ở Trang chủ (RandomMascotLayer).
    // Mặc định true — người dùng có thể tắt trong Cài đặt nếu thấy vướng mắt.
    private val _mascotEnabled = MutableStateFlow(true)
    val mascotEnabled: StateFlow<Boolean> = _mascotEnabled.asStateFlow()

    // ── Student info (sync từ Supabase, tương đương liveStudent) ──
    private val _student = MutableStateFlow(Student(username = ""))
    val student: StateFlow<Student> = _student.asStateFlow()

    /** Key đồng bộ hiện tại (studentId ưu tiên, fallback username) — dùng chung cho avatar & background,
     *  phải khớp đúng key đã dùng lúc initForStudent() để tránh lệch dữ liệu khi upload/load lại. */
    private var currentSyncId: String = ""

    // ── Avatar ──
    private val _avatarUrl = MutableStateFlow<String?>(null)
    val avatarUrl: StateFlow<String?> = _avatarUrl.asStateFlow()

    private val _avatarLoading = MutableStateFlow(false)
    val avatarLoading: StateFlow<Boolean> = _avatarLoading.asStateFlow()

    private val _coverUrl = MutableStateFlow<String?>(null)
    val coverUrl: StateFlow<String?> = _coverUrl.asStateFlow()

    private val _coverLoading = MutableStateFlow(false)
    val coverLoading: StateFlow<Boolean> = _coverLoading.asStateFlow()

    // ── Background settings (thay BgSettingsCard state trong background-settings.js) ──
    private val _bgSettings = MutableStateFlow(BgSettings())
    val bgSettings: StateFlow<BgSettings> = _bgSettings.asStateFlow()

    private val _bgSyncState = MutableStateFlow("idle") // "idle"|"saving"|"saved"|"error"
    val bgSyncState: StateFlow<String> = _bgSyncState.asStateFlow()

    private val _bgUploading = MutableStateFlow(false)
    val bgUploading: StateFlow<Boolean> = _bgUploading.asStateFlow()

    // Trước đây khi uploadImage() thất bại (kể cả với message "chỉ dùng được ở
    // website" khi chặn có chủ đích), uploadBgImage() chỉ kiểm tra result.ok mà
    // không làm gì khi false — msg lỗi không có đường nào tới được UI, người
    // dùng bấm tải ảnh xong không thấy phản hồi gì cả. Thêm state riêng cho lỗi
    // để DashboardScreen hiển thị được (Toast/Snackbar).
    private val _bgUploadError = MutableStateFlow<String?>(null)
    val bgUploadError: StateFlow<String?> = _bgUploadError.asStateFlow()

    fun clearBgUploadError() { _bgUploadError.value = null }

    // TRƯỚC ĐÂY (SHARED_BG_KEY cố định): mọi tài khoản trên mọi máy cùng đọc/ghi 1 key
    // "shared" duy nhất -> tài khoản A đổi ảnh nền thì tài khoản B (kể cả trên máy khác)
    // cũng thấy y hệt ảnh đó, vì cả 2 cùng lưu vào backgrounds/shared.jpg và
    // learnsy_bg:shared. Giờ khoá lại theo currentSyncId (studentId/username của
    // TÀI KHOẢN đang đăng nhập) — Student/Admin/FileManager của CÙNG 1 tài khoản vẫn
    // dùng chung 1 ảnh nền như cũ, nhưng khác tài khoản thì tách riêng.
    private var bgSyncId: String = SHARED_BG_KEY
    private var bgSyncJob: Job? = null
    // "Chốt" đánh dấu loadBackgroundSettings() đã chạy xong — dùng CompletableDeferred
    // thay vì Job? thường vì onBgDarkModeChanged() có thể được LaunchedEffect(dark) gọi
    // TRƯỚC khi LaunchedEffect(username, studentId) kịp gọi loadBackgroundSettings() lần
    // đầu (2 LaunchedEffect độc lập, Compose không đảm bảo thứ tự) — lúc đó chưa có Job
    // nào để join(). Deferred giải quyết được vì complete() gọi sau vẫn không sao,
    // và await() luôn đợi đúng cho tới khi complete() được gọi bất kể ai tới trước.
    private var bgLoadedSignal = kotlinx.coroutines.CompletableDeferred<Unit>()
    private var bgBlurBackup: Pair<String, Int>? = null // (mode, percent) lưu trước khi dark mode ép về 'off'
    private var bgDarkWasOn = false

    // Theo dõi số lượng history trước đó để phát hiện bài MỚI (tương đương prevHistLen ref)
    private var prevHistoryLen = 0

    init {
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            _liteMode.value = prefs[LITE_MODE_KEY] ?: false
            _flickerFx.value = prefs[FLICKER_FX_KEY] ?: true
            _shuffleQ.value = prefs[SHUFFLE_Q_KEY] ?: false
            _shuffleA.value = prefs[SHUFFLE_A_KEY] ?: false
            _mascotEnabled.value = prefs[MASCOT_ENABLED_KEY] ?: true
        }
    }

    fun setTab(t: DashboardTab) { _tab.value = t }

    fun setLiteMode(value: Boolean) {
        _liteMode.value = value
        viewModelScope.launch { dataStore.edit { it[LITE_MODE_KEY] = value } }
    }

    fun setFlickerFx(value: Boolean) {
        _flickerFx.value = value
        viewModelScope.launch { dataStore.edit { it[FLICKER_FX_KEY] = value } }
    }

    fun setShuffleQ(value: Boolean) {
        _shuffleQ.value = value
        viewModelScope.launch { dataStore.edit { it[SHUFFLE_Q_KEY] = value } }
    }

    fun setShuffleA(value: Boolean) {
        _shuffleA.value = value
        viewModelScope.launch { dataStore.edit { it[SHUFFLE_A_KEY] = value } }
    }

    fun setMascotEnabled(value: Boolean) {
        _mascotEnabled.value = value
        viewModelScope.launch { dataStore.edit { it[MASCOT_ENABLED_KEY] = value } }
    }

    /**
     * Khởi tạo dashboard cho 1 học sinh: set thông tin cơ bản, sync display_name
     * mới nhất từ Supabase, tải avatar. Gọi 1 lần khi vào DashboardScreen.
     */
    fun initForStudent(username: String, studentId: String? = null) {
        _student.value = Student(username = username)
        currentSyncId = studentId ?: username
        refreshStudentInfo(username, studentId)
        viewModelScope.launch {
            _avatarUrl.value = avatarRepo.getAvatarUrl(currentSyncId)
        }
        viewModelScope.launch {
            _coverUrl.value = coverRepo.getCoverUrl(currentSyncId)
        }
        loadBackgroundSettings()
    }

    /**
     * ── loadBackgroundSettings ──
     * Tương đương initApply() trong background-settings.js: áp local ngay,
     * rồi merge với Upstash nền (không chặn UI).
     *
     * ĐÃ SỬA: dùng currentSyncId (theo tài khoản đang đăng nhập) thay vì
     * SHARED_BG_KEY cố định — mỗi tài khoản có ảnh nền riêng, không còn bị
     * lẫn giữa các tài khoản khác nhau nữa.
     */
    private fun loadBackgroundSettings() {
        bgSyncId = currentSyncId.ifBlank { SHARED_BG_KEY }
        bgLoadedSignal = kotlinx.coroutines.CompletableDeferred() // reset mỗi lần load lại (đổi tài khoản) — deferred cũ đã complete() không dùng lại được để chặn onBgDarkModeChanged() đợi đúng lượt mới
        viewModelScope.launch {
            val local = bgRepo.loadLocal(bgSyncId)
            _bgSettings.value = local

            val remote = bgRepo.loadRemote(bgSyncId)
            if (remote != null) {
                val merged = local.copy(
                    presetId = remote.presetId,
                    blurMode = remote.blurMode,
                    blurPercent = remote.blurPercent,
                    imageUrl = remote.imageUrl ?: local.imageUrl
                )
                _bgSettings.value = merged
                bgRepo.saveLocal(bgSyncId, merged)
            }
            // Nếu dark mode đã bật SẴN từ trước khi load xong (ví dụ app mở lên với
            // dark mode đang bật), _bgSettings vừa bị ghi đè bằng blurMode GỐC từ
            // remote/local — chưa hề bị ép "off". Áp lại ngay ở đây để đồng bộ đúng
            // với bgDarkWasOn hiện tại, tránh việc onBgDarkModeChanged() gọi TRƯỚC
            // khi load xong bị load-xong-sau ghi đè mất, khiến lúc tắt dark mode
            // không có gì đúng để khôi phục lại.
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

    /** Tương đương fetchStudentInfo trong bản web — lấy display_name mới nhất. */
    fun refreshStudentInfo(username: String, studentId: String? = null) {
        viewModelScope.launch {
            try {
                val row = SupabaseClientProvider.client.postgrest["students"]
                    .select {
                        if (studentId != null) {
                            filter { eq("id", studentId) }
                        } else {
                            filter { eq("username", username) }
                        }
                    }
                    .decodeSingleOrNull<StudentRow>()
                if (row != null) {
                    _student.value = Student(username = row.username, displayName = row.display_name)
                }
            } catch (e: Exception) {
                // Silent — giữ nguyên student hiện tại nếu lỗi, giống try/catch rỗng bản gốc
            }
        }
    }

    suspend fun uploadAvatarNow(uri: Uri): Pair<Boolean, String?> {
        _avatarLoading.value = true
        val id = currentSyncId.ifBlank { student.value.username }
        val result = avatarRepo.uploadAvatar(id, uri)
        if (result.ok) _avatarUrl.value = avatarRepo.getAvatarUrl(id)
        _avatarLoading.value = false
        return result.ok to (result.sizeBytes?.let { "${it / 1024}KB" } ?: result.msg)
    }

    suspend fun removeAvatarNow() {
        val id = currentSyncId.ifBlank { student.value.username }
        avatarRepo.removeAvatar(id)
        _avatarUrl.value = null
    }

    suspend fun uploadCoverNow(uri: Uri): Pair<Boolean, String?> {
        _coverLoading.value = true
        val id = currentSyncId.ifBlank { student.value.username }
        val result = coverRepo.uploadCover(id, uri)
        if (result.ok) _coverUrl.value = coverRepo.getCoverUrl(id)
        _coverLoading.value = false
        return result.ok to (result.sizeBytes?.let { "${it / 1024}KB" } ?: result.msg)
    }

    suspend fun removeCoverNow() {
        val id = currentSyncId.ifBlank { student.value.username }
        coverRepo.removeCover(id)
        _coverUrl.value = null
    }

    // Debounce ghi DataStore local — xem giải thích chi tiết ở bgLocalSaveJob
    // trong SharedBackgroundViewModel.kt (Admin dùng chung pattern này): kéo
    // Slider độ mờ nền bắn onValueChange hàng chục lần/giây, mỗi lần applyBgSettings()
    // trước đây ghi thẳng xuống đĩa NGAY LẬP TỨC không debounce — hao mòn TBW
    // không cần thiết. 500ms debounce: chỉ ghi đĩa sau khi ngừng kéo/đổi.
    private var bgLocalSaveJob: Job? = null

    /** Debounce ghi local (500ms) + debounce sync Upstash (800ms) — tách 2 job riêng. */
    private fun applyBgSettings(next: BgSettings) {
        _bgSettings.value = next
        bgLocalSaveJob?.cancel()
        bgLocalSaveJob = viewModelScope.launch {
            delay(500)
            bgRepo.saveLocal(bgSyncId, next)
        }

        bgSyncJob?.cancel()
        bgSyncJob = viewModelScope.launch {
            _bgSyncState.value = "saving"
            delay(800) // SYNC_DEBOUNCE_MS giống bản gốc
            try {
                bgRepo.saveRemote(bgSyncId, next)
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

    /** Bị khoá khi dark mode đang bật, giống pickBlur() bản gốc (return sớm nếu dark). */
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
        val result = bgRepo.uploadImage(bgSyncId, uri)
        if (result.ok && result.msg != null) {
            applyBgSettings(_bgSettings.value.copy(presetId = "custom_image", imageUrl = result.msg))
        } else {
            _bgUploadError.value = result.msg
        }
        _bgUploading.value = false
    }

    fun removeBgImage() {
        applyBgSettings(_bgSettings.value.copy(presetId = "default_light", imageUrl = null))
        viewModelScope.launch { bgRepo.deleteImage(bgSyncId) }
    }

    /**
     * ── onDarkModeChanged (background) ──
     * Tương đương MutationObserver theo dõi class 'dark' trong bản gốc:
     * bật dark → lưu blurMode/blurPercent hiện tại rồi ép về 'off';
     * tắt dark → khôi phục lại giá trị đã lưu. Gọi từ DashboardScreen mỗi
     * khi `dark` đổi (LaunchedEffect(dark)).
     */
    fun onBgDarkModeChanged(nowDark: Boolean) {
        if (nowDark == bgDarkWasOn) return
        bgDarkWasOn = nowDark
        viewModelScope.launch {
            // Đợi loadBackgroundSettings() (dù đã chạy xong, đang chạy, hay CHƯA từng
            // được gọi) hoàn tất trước khi đọc/ghi _bgSettings — nếu không, việc load
            // remote có thể hoàn tất SAU và ghi đè blurMode gốc lên trên, xoá mất việc
            // "off" hoá vừa làm ở đây; đến lúc tắt dark mode thì bgBlurBackup không khớp
            // với những gì đang thực sự hiển thị (đây là nguyên nhân nền không khôi
            // phục lại được ở phía Student khi dark mode đổi ngay lúc mới mở màn hình).
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

    /**
     * ── AchievementUnlock detection ──
     * Tương đương useEffect theo dõi normHistory trong bản gốc: chỉ kiểm tra
     * khi số lượng history TĂNG (bài mới), không chạy lại khi component
     * re-render vì lý do khác.
     */
    fun checkAchievements(history: List<HistoryEntry>) {
        if (history.size <= prevHistoryLen) {
            prevHistoryLen = history.size
            return
        }
        prevHistoryLen = history.size

        val n = history.size
        val best = history.maxOf { it.pct }
        val unlocked = buildList {
            if (n == 1) add(AchievementUnlock("check", "Bắt đầu hành trình!", androidx.compose.ui.graphics.Color(0xFF34D399)))
            if (n == 5) add(AchievementUnlock("book", "5 bài siêng năng!", androidx.compose.ui.graphics.Color(0xFFA855F7)))
            if (n == 10) add(AchievementUnlock("star", "10 bài chăm chỉ!", androidx.compose.ui.graphics.Color(0xFFF59E0B)))
            if (n == 20) add(AchievementUnlock("zap", "Thần đồng 20 bài!", androidx.compose.ui.graphics.Color(0xFF06B6D4)))
            if (n == 50) add(AchievementUnlock("trending", "50 bài siêu anh hùng!", androidx.compose.ui.graphics.Color(0xFFF472B6)))
            if (best >= 70) add(AchievementUnlock("thumbsup", "Đạt điểm Giỏi!", androidx.compose.ui.graphics.Color(0xFFF472B6)))
            if (best >= 90) add(AchievementUnlock("trophy", "Điểm Xuất sắc!", androidx.compose.ui.graphics.Color(0xFFF59E0B)))
        }
        if (unlocked.isNotEmpty()) {
            _achievementQueue.value = _achievementQueue.value + unlocked
        }
    }

    fun dismissTopAchievement() {
        _achievementQueue.value = _achievementQueue.value.drop(1)
    }
}

package com.learnsypro.app.admin.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.learnsypro.app.darkModeDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

enum class CardBlurLevel(val stored: String) { OFF("off"), FIFTY("50"), EIGHTY_FIVE("85") }

// Cùng khóa DataStore mà MainActivity.kt (app chính) dùng — đồng bộ dark
// mode 2 chiều giữa Dashboard và Admin, giống cách module Quản lý tệp
// (filemanager/util/SecurePrefs) đã đọc lại learnsy_dark trước đây.
private val ADMIN_DARK_MODE_KEY = booleanPreferencesKey("learnsy_dark")

// Tương đương các localStorage key trong SettingsPanel: learnsy_admin_name,
// learnsy_school, learnsy_card_blur, và dark mode toggle.
class SettingsStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("learnsy_admin", Context.MODE_PRIVATE)
    // Bridge riêng cho darkMode: nguồn sự thật là DataStore chung của app
    // chính (bất đồng bộ), nhưng AppRoot() cần đọc/ghi darkMode ĐỒNG BỘ
    // (remember { mutableStateOf(settings.darkMode) }) — dùng SharedPreferences
    // làm cache đồng bộ, đồng thời ghi lại DataStore chung ở set() để Dashboard
    // thấy thay đổi ngay khi mở lại.
    private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var adminName: String
        get() = prefs.getString(KEY_NAME, "Admin") ?: "Admin"
        set(value) = prefs.edit().putString(KEY_NAME, value).apply()

    var school: String
        get() = prefs.getString(KEY_SCHOOL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SCHOOL, value).apply()

    var cardBlur: CardBlurLevel
        get() = when (prefs.getString(KEY_BLUR, "off")) {
            "50" -> CardBlurLevel.FIFTY
            "85" -> CardBlurLevel.EIGHTY_FIVE
            else -> CardBlurLevel.OFF
        }
        set(value) = prefs.edit().putString(KEY_BLUR, value.stored).apply()

    // ĐỌC: chỉ từ cache SharedPreferences cục bộ (đồng bộ, KHÔNG block UI thread) —
    // giá trị này có thể trễ 1 nhịp so với DataStore chung nếu Dashboard vừa đổi
    // dark mode ở phiên trước và Admin chưa từng mở lại để đồng bộ. syncDarkModeFromShared()
    // (gọi từ LaunchedEffect trong AppRoot(), xem file đó) mới là nơi cập nhật cache này
    // đúng cách bằng coroutine, không dùng runBlocking chặn main thread như cách cũ.
    var darkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DARK, value).apply()
            bridgeScope.launch {
                context.darkModeDataStore.edit { it[ADMIN_DARK_MODE_KEY] = value }
            }
        }

    // Gọi 1 lần từ LaunchedEffect(Unit) trong AppRoot() lúc khởi tạo — đọc giá trị
    // mới nhất từ DataStore chung (bất đồng bộ, đúng cách) và trả về để AppRoot() tự
    // cập nhật lại state `dark` của nó nếu khác với cache đã đọc đồng bộ ở trên.
    suspend fun syncDarkModeFromShared(): Boolean? {
        val fromShared = context.darkModeDataStore.data
            .map { it[ADMIN_DARK_MODE_KEY] }
            .first()
        if (fromShared != null) {
            prefs.edit().putBoolean(KEY_DARK, fromShared).apply()
        }
        return fromShared
    }

    // Theo dõi LIÊN TỤC (không phải đọc 1 lần như syncDarkModeFromShared()) —
    // dùng để AppRoot() tự cập nhật `dark` ngay khi Student đổi dark mode TRONG LÚC
    // Admin đang mở sẵn (Activity singleTop sống lại từ background, không chạy lại
    // LaunchedEffect(Unit)). Không phát null (bỏ qua lúc key chưa từng được ghi),
    // tương tự cách MainActivity.kt đọc DARK_MODE_KEY cho Dashboard.
    fun darkModeFlow() = context.darkModeDataStore.data
        .map { it[ADMIN_DARK_MODE_KEY] }
        .filterNotNull()
        .onEach { prefs.edit().putBoolean(KEY_DARK, it).apply() }

    // Tương đương localStorage 'bb_admin_theme'
    var themePreset: String
        get() = prefs.getString(KEY_THEME, "default") ?: "default"
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    companion object {
        private const val KEY_NAME = "learnsy_admin_name"
        private const val KEY_SCHOOL = "learnsy_school"
        private const val KEY_BLUR = "learnsy_card_blur"
        private const val KEY_DARK = "learnsy_dark_mode"
        private const val KEY_THEME = "bb_admin_theme"
    }
}

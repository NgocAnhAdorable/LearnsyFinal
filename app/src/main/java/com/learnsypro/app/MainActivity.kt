package com.learnsypro.app

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.learnsypro.app.ui.dashboard.dashboardPrefsDataStore
import com.learnsypro.app.ui.dashboard.LITE_MODE_KEY
import com.learnsypro.app.ui.theme.LearnsyTheme
import com.learnsypro.app.ui.theme.LocalLiteMode
import com.learnsypro.app.ui.toast.ToastHost
import com.learnsypro.app.ui.nav.LearnsyNavHost
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// DataStore tương đương localStorage.getItem('learnsy_dark') trong index.html gốc
val android.content.Context.darkModeDataStore by preferencesDataStore(name = "learnsy_prefs")
// internal (không phải private): module Quản lý tệp (com.learnsypro.app.filemanager,
// xem LearnsyFileManagerActivity.kt) cần đọc đúng key này để đồng bộ dark mode với
// app chính thay vì tự tạo 1 nguồn dark mode riêng theo hệ thống.
internal val DARK_MODE_KEY = booleanPreferencesKey("learnsy_dark")

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val context = LocalContext.current
            val systemDark = isSystemInDarkTheme()
            val scope = rememberCoroutineScope()

            // ═══ Dark mode pre-init ═══
            // Tương đương IIFE ở đầu index.html gốc: đọc giá trị lưu (nếu có),
            // nếu chưa có thì fallback theo prefers-color-scheme của hệ thống.
            // null = đang đọc DataStore, chưa quyết định theme -> tránh flash sai màu.
            var isDarkTheme by remember { mutableStateOf<Boolean?>(null) }

            LaunchedEffect(Unit) {
                context.darkModeDataStore.data
                    .map { prefs -> prefs[DARK_MODE_KEY] }
                    .collect { storedValue ->
                        isDarkTheme = storedValue ?: systemDark
                    }
            }

            // ═══ Lite Mode global ═══
            // Đọc cùng DataStore singleton mà DashboardViewModel dùng, để cấp
            // giá trị cho LocalLiteMode ở gốc cây — mọi composable (kể cả
            // ngoài dashboard: quiz, listening, auth, vocab...) tự tắt bớt
            // animation liên tục khi người dùng bật Lite Mode, không cần
            // truyền tay qua từng tham số hàm.
            val application = context.applicationContext as android.app.Application
            var liteMode by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                application.dashboardPrefsDataStore.data
                    .map { prefs -> prefs[LITE_MODE_KEY] ?: false }
                    .collect { storedValue -> liteMode = storedValue }
            }

            // Chưa xác định theme xong thì không vẽ UI (tránh flash sai giao diện,
            // đúng tinh thần của đoạn script chạy trước mọi thứ khác trong bản web)
            val resolvedDark = isDarkTheme
            if (resolvedDark == null) return@setContent

            fun toggleDarkMode(newValue: Boolean) {
                isDarkTheme = newValue
                scope.launch {
                    context.darkModeDataStore.edit { prefs ->
                        prefs[DARK_MODE_KEY] = newValue
                    }
                }
            }

            CompositionLocalProvider(LocalLiteMode provides liteMode) {
                LearnsyTheme(isDarkTheme = resolvedDark) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                            LearnsyNavHost(isDarkTheme = resolvedDark, onToggleDarkMode = ::toggleDarkMode)

                            // ToastHost phủ lên trên cùng — hiển thị toast bất kể màn hình nào đang mở,
                            // tương đương #toastContainer cố định trong <body> của bản web.
                            ToastHost(dark = resolvedDark, modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter))
                        }
                    }
                }
            }
        }
    }
}

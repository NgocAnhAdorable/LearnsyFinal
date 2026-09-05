package com.learnsypro.app.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.learnsypro.app.admin.ui.AppRoot
import com.learnsypro.app.admin.ui.theme.LearnsyTypography

/**
 * Cờ process-level: splash Admin chỉ chạy 1 lần trong vòng đời process.
 * Thoát Admin rồi vào lại → bỏ splash, hết nhấp nháy.
 */
object AdminSplashGate {
    @Volatile
    var alreadyShown: Boolean = false
}

/**
 * ── AdminActivity ──
 * Điểm vào module Admin (trước đây là app Learnsy Standard Admin độc lập,
 * package com.learnsy.admin), gộp vào LearnsyPro theo đúng mô hình đã dùng
 * cho module Quản lý tệp (filemanager/HomeActivity.kt): Activity Compose
 * riêng, mở bằng Intent từ Dashboard, KHÔNG chèn vào AppRoute của
 * LearnsyNavHost (Admin có bottom-nav/state nội bộ riêng biệt, phức tạp
 * hơn 1 route đơn giản).
 *
 * Dark mode đồng bộ với app chính qua AdminDarkModeBridge (đọc/ghi cùng
 * DataStore learnsy_prefs/learnsy_dark của MainActivity.kt gốc) thay vì
 * SharedPreferences learnsy_admin_mode riêng như bản Admin độc lập cũ.
 *
 * launchMode=singleTop + AdminSplashGate giúp vào lại mượt, không hiện lại
 * hologram splash (nguyên nhân nhấp nháy khi thoát rồi mở lại Admin).
 */
class AdminActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Vào lại (process còn sống): fade nhẹ tránh flash 1 frame trước Compose.
        if (AdminSplashGate.alreadyShown) {
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        setContent {
            LearnsyAdminApp()
        }
    }

    override fun finish() {
        super.finish()
        // Thoát Admin: fade kiểu One UI thay vì slide cứng mặc định hệ thống.
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}

@Composable
fun LearnsyAdminApp() {
    MaterialTheme(typography = LearnsyTypography) {
        Surface(modifier = Modifier.fillMaxSize()) {
            AppRoot()
        }
    }
}

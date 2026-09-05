package com.learnsypro.app.filemanager

// ═══════════════════════════════════════════════════════════════════════════
// FileManagerCore.kt — Lõi của module Quản lý tệp (Learnsy Pro)
//
// Gộp 3 thành phần trước đây nằm rời rạc ở 3 file riêng thành 1 codebase
// duy nhất, theo đúng thứ tự phụ thuộc:
//   1) LearnsyFileManagerActivity — base Activity dùng chung (đồng bộ dark mode)
//   2) HomeActivity               — màn hình "index" (entry point module)
//   3) MainActivity               — màn "admin" (Server/Client/Cloud/Settings)
//
// Giữ nguyên 3 class top-level riêng biệt (Kotlin cho phép nhiều class
// top-level trong 1 file) để KHÔNG đổi tên class đủ điều kiện — AndroidManifest.xml
// và Intent(this, MainActivity::class.java) / Intent(this, HomeActivity::class.java)
// ở 28 Activity khác trong module này vẫn trỏ đúng, không cần sửa gì thêm.
// ═══════════════════════════════════════════════════════════════════════════

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.StatFs
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.learnsypro.app.DARK_MODE_KEY
import com.learnsypro.app.R
import com.learnsypro.app.background.BackgroundLayer
import com.learnsypro.app.background.SharedBackgroundViewModel
import com.learnsypro.app.darkModeDataStore
import com.learnsypro.app.databinding.ActivityHomeBinding
import com.learnsypro.app.databinding.ItemCategoryBinding
import com.learnsypro.app.filemanager.fragments.ClientFragment
import com.learnsypro.app.filemanager.fragments.CloudFragment
import com.learnsypro.app.filemanager.fragments.ServerFragment
import com.learnsypro.app.filemanager.fragments.SettingsFragment
import com.learnsypro.app.filemanager.util.ActivityTransitions
import com.learnsypro.app.filemanager.util.LogBus
import com.learnsypro.app.filemanager.util.SdCardUtils
import com.learnsypro.app.filemanager.util.WindowInsetsUtils
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.text.DecimalFormat

// ─────────────────────────────────────────────────────────────────────────
// 1) BASE — LearnsyFileManagerActivity
// ─────────────────────────────────────────────────────────────────────────

/**
 * Base Activity dùng chung cho TOÀN BỘ module Quản lý tệp (trước đây là app
 * MyFile Manager độc lập — mọi Activity ở đây từng kế thừa trực tiếp
 * AppCompatActivity, tự chuyển sáng/tối theo Theme.Material3.DayNight =
 * theo cài đặt HỆ THỐNG).
 *
 * Sau khi gộp vào Learnsy Pro, module này cần đồng bộ theo dark mode CỦA APP
 * (bật/tắt trong Cài đặt, lưu ở DataStore "learnsy_prefs"/"learnsy_dark")
 * thay vì theo hệ thống, để trải nghiệm nhất quán khi người dùng bấm icon
 * Tệp tin ở header Dashboard: nếu app đang ở chế độ tối, màn Quản lý tệp mở
 * ra cũng phải tối ngay, bất kể điện thoại đang để sáng/tối gì.
 *
 * AppCompatDelegate.setDefaultNightMode() phải gọi TRƯỚC super.onCreate() để
 * có hiệu lực đúng lúc Activity resolve theme lần đầu (gọi sau sẽ không kịp,
 * phải recreate() mới áp dụng, gây nháy sai màu 1 khung hình).
 *
 * Đọc DataStore là API bất đồng bộ (Flow/suspend), nhưng theme phải quyết
 * định NGAY LẬP TỨC trước khung hình đầu tiên — dùng runBlocking ở đây CHỈ
 * để đọc 1 giá trị boolean nhỏ, cục bộ trên máy (không qua mạng), nên độ trễ
 * không đáng kể (vài micro-giây) và an toàn để chặn ngắn tại đây, khác với
 * runBlocking cho tác vụ mạng/IO nặng (điều KHÔNG nên làm trên main thread).
 */
abstract class LearnsyFileManagerActivity : AppCompatActivity() {

    // Lưu lại kết quả đọc DataStore ở applyLearnsyDarkModePreference() để
    // BackgroundLayer (chèn ở setContentView bên dưới) dùng đúng cùng giá trị
    // dark mode, không phải đọc DataStore lần 2.
    private var resolvedIsDark: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        applyLearnsyDarkModePreference()
        super.onCreate(savedInstanceState)
    }

    private fun applyLearnsyDarkModePreference() {
        try {
            // darkModeDataStore là EXTENSION PROPERTY trên android.content.Context (khai ở
            // package cha com.learnsypro.app) — không phải top-level val truy cập được qua
            // tên gói đủ, PHẢI gọi trên 1 Context thật sự. Activity chính LÀ Context nên
            // dùng "this" trực tiếp là đủ, không cần applicationContext.
            val isDark = runBlocking {
                this@LearnsyFileManagerActivity.darkModeDataStore.data.first()[DARK_MODE_KEY]
            }
            resolvedIsDark = isDark ?: false
            // null = người dùng chưa từng đổi trong Cài đặt -> chưa có gì để đồng bộ,
            // giữ nguyên hành vi mặc định của hệ thống (AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            // thay vì ép về 1 chiều cụ thể.
            if (isDark != null) {
                AppCompatDelegate.setDefaultNightMode(
                    if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                )
            }
        } catch (e: Exception) {
            // Đọc preference thất bại (lỗi DataStore/IO hiếm gặp) KHÔNG được phép chặn
            // Activity mở lên — chấp nhận rớt về theme hệ thống mặc định còn hơn crash.
            LogBus.error(
                "Không đọc được dark mode preference của Learnsy Pro, dùng theme hệ thống mặc định",
                "THEME_SYNC",
                e
            )
        }
    }

    /**
     * ── Nền tuỳ chỉnh dùng chung (BackgroundLayer) ──
     * Toàn bộ ~25 Activity của module Quản lý tệp đều là View-based (XML +
     * ViewBinding), không phải Compose — không thể đặt <BackgroundLayer/>
     * trực tiếp trong layout XML như bên Student/Admin (Compose).
     *
     * Giải pháp: override CẢ 2 overload setContentView() ở base class này —
     * mọi Activity con gọi setContentView(binding.root) hoặc
     * setContentView(R.layout.xxx) đều tự động đi qua đây, không phải sửa
     * từng file Activity riêng lẻ. super.setContentView() chạy trước (add
     * layout thật của Activity vào android.R.id.content như bình thường),
     * sau đó installSharedBackground() chèn ComposeView chứa BackgroundLayer
     * vào ĐÚNG INDEX 0 của cùng container — dưới cùng về thứ tự vẽ (index 0
     * vẽ trước, các view sau đè lên trên) dù được add sau về thời gian, nên
     * nền tuỳ chỉnh luôn nằm dưới layout thật của Activity, không che mất gì.
     *
     * Điều kiện để nền thấy được: layout XML gốc của mỗi Activity phải
     * KHÔNG còn android:background="@color/background" opaque (đã gỡ ở toàn
     * bộ activity_*.xml/fragment_*.xml của module này) — nếu còn, layout đó
     * vẫn che kín ComposeView nền phía dưới dù đã chèn đúng.
     */
    private fun installSharedBackground() {
        val root = window.decorView.findViewById<ViewGroup>(android.R.id.content)
        // Tránh chèn trùng khi Activity gọi setContentView() nhiều lần (hiếm
        // nhưng có thể xảy ra, vd sau khi permission callback tái tạo view).
        if (root.findViewWithTag<View>(BG_COMPOSE_TAG) != null) return

        val bgVm = ViewModelProvider(
            this,
            SharedBackgroundViewModel.Factory(application, isAdminContext = false)
        )[SharedBackgroundViewModel::class.java]
        val composeView = ComposeView(this).apply {
            tag = BG_COMPOSE_TAG
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val bgSettings by bgVm.bgSettings.collectAsState()
                BackgroundLayer(settings = bgSettings, dark = resolvedIsDark)
                androidx.compose.runtime.LaunchedEffect(bgSettings.presetId) {
                    // Chạy sau khi Compose đã ghi nhận giá trị mới nhất của bgSettings — an
                    // toàn để đụng vào View thật (post() đảm bảo chạy trên main thread, tránh
                    // trường hợp hiếm gọi ngay trong lúc View tree của Activity con vẫn đang
                    // được attach dở dang lúc setContentView() vừa xong).
                    // Dùng `this@apply` (chính ComposeView này) thay vì tên biến ngoài
                    // `composeView`: bên trong apply{} thì `this` đã là ComposeView nên tên
                    // biến ngoài bị che khuất — tham chiếu `composeView` ở đây trước kia bị
                    // Kotlin coi là tự tham chiếu tới chính nó lúc còn đang khởi tạo dở dang,
                    // gây lỗi biên dịch "Unresolved reference 'composeView'".
                    this@apply.post { applyGlassCardsIfNeeded(bgSettings.presetId == "custom_image") }
                }
            }
        }
        root.addView(
            composeView,
            0,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    /**
     * ── Card "kính mờ" khi dùng ảnh nền tự upload ──
     * Cùng tinh thần với DashboardColors.resolvedCardColor()/AppRoot.kt bên Student/Admin
     * (Compose): khi bgSettings.presetId == "custom_image", MỌI MaterialCardView thật của
     * Activity (không phải BackgroundLayer, cái đó luôn trong suốt) cần chuyển từ nền đặc
     * @color/surface sang bán trong suốt, để lộ ảnh nền + blur (BackgroundLayer) phía sau
     * thay vì che kín — đúng vấn đề "card đặc che mất ảnh nền" đang gặp ở các màn XML này.
     *
     * Không sửa từng file layout: ~25 Activity của module dùng chung style="@style/App.CardView"
     * (cardBackgroundColor=@color/surface) qua MaterialCardView — duyệt view tree TÌM THEO
     * KIỂU (MaterialCardView), không theo id/tag riêng lẻ, nên áp dụng được cho MỌI card ở
     * MỌI màn hình của module tự động, kể cả các Activity thêm sau này không cần sửa gì thêm.
     *
     * Gọi lại mỗi lần bgSettings đổi (LaunchedEffect trong installSharedBackground ở trên) để
     * card cập nhật ngay khi người dùng bật/tắt ảnh nền tự upload trong lúc đang đứng ở màn
     * hình đó, không cần thoát ra vào lại Activity mới thấy hiệu ứng.
     */
    private fun applyGlassCardsIfNeeded(isCustomImageBg: Boolean) {
        val root = window.decorView.findViewById<ViewGroup>(android.R.id.content)
        val glassColor = if (resolvedIsDark) glassCardDark else glassCardLight
        val normalColor = ContextCompat.getColor(this, R.color.surface)
        fun walk(view: View) {
            if (view is com.google.android.material.card.MaterialCardView) {
                view.setCardBackgroundColor(if (isCustomImageBg) glassColor else normalColor)
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) walk(view.getChildAt(i))
            }
        }
        walk(root)
    }

    override fun setContentView(view: View?) {
        super.setContentView(view)
        installSharedBackground()
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        installSharedBackground()
    }

    private companion object {
        const val BG_COMPOSE_TAG = "learnsy_shared_background_layer"
    }

    // Khớp giá trị đã dùng ở DashboardGlassCardLight/Dark (Compose, Student/Admin) — cùng 1 tông
    // "kính mờ" xuyên suốt toàn app, dù đây là code View-based khác tầng UI. Dùng
    // android.graphics.Color.argb(...) thay vì literal hex dài (0x8FFFFFFF...) để tránh mọi nhập
    // nhằng về suy luận kiểu Int/Long của hex vượt Int.MAX_VALUE.
    private val glassCardLight = android.graphics.Color.argb(143, 255, 255, 255) // ~56% trắng
    private val glassCardDark = android.graphics.Color.argb(26, 255, 255, 255)   // ~10% trắng

    /**
     * Xoá 1 file tạm NGAY khi màn hình xem đóng lại, thay vì để dọn tự động
     * (LearnsyApp.cleanupStaleTempCacheFiles) sau tối đa 24 giờ mới dọn — các
     * viewer (Pdf/Docx/Xlsx/Html/MediaViewer/AudioPlayer...) đều dùng chung 1
     * cacheDir cho CẢ file tạm (preview cloud/DLNA, tải về từ archive) LẪN việc
     * chỉ đơn giản mở file thật đã có sẵn trên máy — nên KHÔNG được xoá vô điều
     * kiện mọi path truyền vào, chỉ xoá nếu path đó thực sự nằm trong cacheDir
     * của chính app (an toàn tuyệt đối, không đụng tới file thật của người dùng
     * ở bộ nhớ trong/thẻ nhớ dù activity con gọi nhầm với path khác).
     * Gọi trong onDestroy() của activity con, truyền vào path đã dùng để mở file.
     */
    protected fun deleteIfTempCacheFile(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            val file = File(path)
            if (file.absolutePath.startsWith(cacheDir.absolutePath)) {
                file.delete()
            }
        } catch (e: Exception) {
            // Xoá file tạm thất bại không được phép làm crash lúc đóng màn hình.
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// 2) INDEX — HomeActivity
// ─────────────────────────────────────────────────────────────────────────

/**
 * Màn hình chính của app, phỏng theo giao diện Samsung "My Files":
 * tìm kiếm, file gần đây, lưới thể loại, danh sách lưu trữ (bao gồm "Lưu trữ
 * mạng" dẫn vào FTP Server/Client/Cloud của app), và tiện ích (thùng rác).
 * Tự chuyển sáng/tối theo dark mode CỦA LEARNSY PRO (đồng bộ qua
 * LearnsyFileManagerActivity ở trên), không còn theo cài đặt hệ thống riêng
 * như lúc còn là app MyFile Manager độc lập.
 */
class HomeActivity : LearnsyFileManagerActivity() {

    private lateinit var binding: ActivityHomeBinding

    // Chờ người dùng chọn 1 file bất kỳ trong máy để mở bằng Trình soạn thảo mã.
    private val pickCodeFileLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { openPickedFile(it, forEditor = true) }
    }

    // Chờ người dùng chọn 1 file .html để chạy trực tiếp trong app.
    private val pickHtmlFileLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { openPickedFile(it, forEditor = false) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestRuntimePermissions()
        setupCategoryGrid()
        setupStorageInfo()
        setupClickRows()
        setupFabScrollBehavior()
        // FAB "Lưu trữ mạng" đặt margin cố định 20dp trong XML — trên máy dùng thanh điều
        // hướng cử chỉ (OneUI/HyperOS) hoặc thanh 3 nút, phần dưới FAB có thể bị chính
        // thanh đó che một phần. applyBottomInsetMargin tự đọc margin gốc (20dp) từ layoutParams
        // và cộng thêm đúng chiều cao system bar đọc tại runtime, nên tham số truyền vào là 0 —
        // không cộng thêm khoảng dư nào ngoài phần margin gốc + inset thật.
        // Nội dung cuộn (danh sách "Thùng rác", "Quản lý lưu trữ"...) có paddingBottom cố định
        // 96dp trong XML chỉ đủ chừa chỗ cho FAB trên máy KHÔNG có thanh điều hướng hệ thống —
        // cộng thêm đúng chiều cao system bar tại runtime để 2 dòng cuối luôn cuộn lên được
        // hẳn, không bị FAB lẫn thanh điều hướng che dù dùng cử chỉ hay 3 nút.
        WindowInsetsUtils.applyBottomInsetPadding(binding.homeContentContainer)
        WindowInsetsUtils.applyBottomInsetMargin(binding.fabQuickConnect, 0)
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    override fun onResume() {
        super.onResume()
        // Cập nhật lại dung lượng mỗi khi quay lại màn hình — vd sau khi copy/xóa file ở
        // màn khác rồi back về đây, số liệu và thanh dung lượng phải phản ánh đúng ngay,
        // không đợi mở lại app từ đầu. Trước đây setupSdCardRow() chỉ chạy 1 lần lúc
        // onCreate() nên thanh Thẻ nhớ SD bị đứng yên dù đã dùng thêm/xóa bớt dung lượng.
        setupStorageInfo()
        setupSdCardRow()
    }

    private fun setupCategoryGrid() {
        bindCategory(ItemCategoryBinding.bind(binding.catPhoto.root), R.drawable.ic_cat_photo, R.drawable.bg_cat_circle_photo, getString(R.string.home_category_photo))
        bindCategory(ItemCategoryBinding.bind(binding.catVideo.root), R.drawable.ic_cat_video, R.drawable.bg_cat_circle_video, getString(R.string.home_category_video))
        bindCategory(ItemCategoryBinding.bind(binding.catAudio.root), R.drawable.ic_cat_audio, R.drawable.bg_cat_circle_audio, getString(R.string.home_category_audio))
        bindCategory(ItemCategoryBinding.bind(binding.catDoc.root), R.drawable.ic_cat_doc, R.drawable.bg_cat_circle_doc, getString(R.string.home_category_doc))
        bindCategory(ItemCategoryBinding.bind(binding.catDownload.root), R.drawable.ic_cat_download, R.drawable.bg_cat_circle_download, getString(R.string.home_category_download))
        bindCategory(ItemCategoryBinding.bind(binding.catApk.root), R.drawable.ic_cat_apk, R.drawable.bg_cat_circle_apk, getString(R.string.home_category_apk))
        bindCategory(ItemCategoryBinding.bind(binding.catNote.root), R.drawable.ic_edit, R.drawable.bg_cat_circle_note, getString(R.string.home_category_note))

        binding.catPhoto.root.setOnClickListener { openCategoryBrowser(CategoryType.IMAGE) }
        binding.catVideo.root.setOnClickListener { openCategoryBrowser(CategoryType.VIDEO) }
        binding.catAudio.root.setOnClickListener { openCategoryBrowser(CategoryType.AUDIO) }
        binding.catDoc.root.setOnClickListener { openCategoryBrowser(CategoryType.DOCUMENT) }
        binding.catDownload.root.setOnClickListener { openCategoryBrowser(CategoryType.DOWNLOAD) }
        binding.catApk.root.setOnClickListener { openCategoryBrowser(CategoryType.APK) }
        binding.catNote.root.setOnClickListener {
            ActivityTransitions.startForward(this, Intent(this, NotesListActivity::class.java))
        }
    }

    private fun bindCategory(itemBinding: ItemCategoryBinding, iconRes: Int, badgeRes: Int, name: String) {
        itemBinding.ivCatIcon.setImageResource(iconRes)
        itemBinding.iconBadge.setBackgroundResource(badgeRes)
        itemBinding.tvCatName.text = name
    }

    /** Mở màn hình duyệt file nội bộ đã lọc sẵn theo thể loại, giống hành vi của Samsung My Files. */
    private fun openCategoryBrowser(type: CategoryType) {
        val intent = Intent(this, CategoryFilesActivity::class.java).apply {
            putExtra(CategoryFilesActivity.EXTRA_CATEGORY, type.name)
        }
        ActivityTransitions.startForward(this, intent)
    }

    /**
     * Thu gọn FAB "Lưu trữ mạng" thành hình tròn chỉ icon khi cuộn xuống (giống hành vi
     * FAB của One UI), để không che nội dung và vẫn nằm gọn trong tầm ngón cái.
     */
    private fun setupFabScrollBehavior() {
        var extended = true
        binding.scrollHome.setOnScrollChangeListener(
            androidx.core.widget.NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                if (scrollY > oldScrollY && extended) {
                    binding.fabQuickConnect.shrink()
                    extended = false
                } else if (scrollY < oldScrollY && !extended) {
                    binding.fabQuickConnect.extend()
                    extended = true
                }
            }
        )
    }

    private fun setupStorageInfo() {
        try {
            val stat = StatFs(android.os.Environment.getDataDirectory().path)
            val totalBytes = stat.totalBytes
            val availableBytes = stat.availableBytes
            val usedBytes = totalBytes - availableBytes
            // Pill giờ tự vẽ CẢ chữ lẫn 2 màu tỉ lệ trong 1 view duy nhất — không còn TextView
            // (tvStorageDetail) riêng đè lên nữa, tránh lỗi đo layout vòng lặp trước đó khiến
            // pill tràn full-width che kín màn hình.
            binding.storagePillInternalBg.setUsage(
                usedBytes, totalBytes,
                getString(R.string.home_storage_detail, formatBytes(usedBytes), formatBytes(totalBytes))
            )
        } catch (e: Exception) {
            binding.storagePillInternalBg.setUsage(0, 0, "")
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val safeGroup = digitGroups.coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, safeGroup.toDouble())
        return DecimalFormat("#,##0.#").format(value) + " " + units[safeGroup]
    }

    /** Hiện hàng "Thẻ nhớ SD" chỉ khi thiết bị thực sự có gắn thẻ SD tháo rời được. */
    private fun setupSdCardRow() {
        val sdPath = SdCardUtils.findSdCardPath(this)
        if (sdPath != null) {
            binding.rowSdcard.visibility = android.view.View.VISIBLE
            binding.dividerSdcard.visibility = android.view.View.VISIBLE
            binding.rowSdcard.setOnClickListener { openCategoryBrowser(CategoryType.SDCARD) }
            try {
                val stat = StatFs(sdPath)
                val totalBytes = stat.totalBytes
                val usedBytes = totalBytes - stat.availableBytes
                binding.storagePillSdBg.setUsage(
                    usedBytes, totalBytes,
                    getString(R.string.home_storage_detail, formatBytes(usedBytes), formatBytes(totalBytes))
                )
                binding.storagePillSdBg.visibility = android.view.View.VISIBLE
            } catch (e: Exception) {
                binding.storagePillSdBg.visibility = android.view.View.GONE
            }
        } else {
            binding.rowSdcard.visibility = android.view.View.GONE
            binding.dividerSdcard.visibility = android.view.View.GONE
        }
    }

    private fun setupClickRows() {
        binding.rowNetworkStorage.setOnClickListener {
            ActivityTransitions.startForward(this, Intent(this, MainActivity::class.java))
        }
        // FAB kết nối nhanh: cùng đích đến Lưu trữ mạng, nhưng nằm trong tầm ngón cái khi thao tác 1 tay
        binding.fabQuickConnect.setOnClickListener {
            ActivityTransitions.startForward(this, Intent(this, MainActivity::class.java))
        }
        binding.rowInternalStorage.setOnClickListener {
            openCategoryBrowser(CategoryType.INTERNAL)
        }
        setupSdCardRow()
        binding.rowRecent.setOnClickListener {
            openCategoryBrowser(CategoryType.RECENT)
        }
        binding.rowBookmarks.setOnClickListener {
            openCategoryBrowser(CategoryType.BOOKMARKS)
        }
        binding.rowTrash.setOnClickListener {
            ActivityTransitions.startForward(this, Intent(this, TrashActivity::class.java))
        }
        binding.rowStorageManager.setOnClickListener {
            ActivityTransitions.startForward(this, Intent(this, StorageManagerActivity::class.java))
        }
        binding.btnSearch.setOnClickListener {
            ActivityTransitions.startForward(this, Intent(this, SearchActivity::class.java))
        }
        binding.btnMore.setOnClickListener { showHomeMenu(it) }
    }

    /** Menu 3 chấm ở Home: Thông tin bộ nhớ (giống Samsung Storage Manager), Cài đặt, Giới thiệu. */
    private fun showHomeMenu(anchor: android.view.View) {
        val popup = android.widget.PopupMenu(
            android.view.ContextThemeWrapper(this, R.style.ThemeOverlay_App_PopupMenu),
            anchor
        )
        popup.menu.add(getString(R.string.menu_code_editor_home))
        popup.menu.add(getString(R.string.menu_html_viewer_home))
        popup.menu.add(getString(R.string.menu_settings))
        popup.menu.add(getString(R.string.menu_about))
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                getString(R.string.menu_code_editor_home) ->
                    pickCodeFileLauncher.launch(arrayOf("text/*", "application/json", "application/xml", "application/javascript", "text/html"))
                getString(R.string.menu_html_viewer_home) ->
                    pickHtmlFileLauncher.launch(arrayOf("text/html"))
                getString(R.string.menu_settings) ->
                    ActivityTransitions.startForward(this, Intent(this, MainActivity::class.java))
                getString(R.string.menu_about) -> showAboutDialog()
            }
            true
        }
        popup.show()
    }

    /**
     * SAF (Storage Access Framework) trả về content:// chứ không phải đường dẫn file thật,
     * và CodeEditorActivity/HtmlViewerActivity cần 1 File thật để đọc/ghi trực tiếp — nên
     * copy nội dung sang thư mục nội bộ của app trước khi mở.
     */
    private fun openPickedFile(uri: android.net.Uri, forEditor: Boolean) {
        val name = queryDisplayName(uri) ?: "file_${System.currentTimeMillis()}"
        val target = File(filesDir, name)
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            LogBus.error("Không thể mở file đã chọn: $name", source = "APP", throwable = e)
            return
        }
        val intent = if (forEditor) {
            Intent(this, CodeEditorActivity::class.java).putExtra(CodeEditorActivity.EXTRA_FILE_PATH, target.absolutePath)
        } else {
            Intent(this, HtmlViewerActivity::class.java).putExtra(HtmlViewerActivity.EXTRA_FILE_PATH, target.absolutePath)
        }
        ActivityTransitions.startForward(this, intent)
    }

    private fun queryDisplayName(uri: android.net.Uri): String? {
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.app_name))
            .setMessage(getString(R.string.app_version_info))
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

    private fun requestRuntimePermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest.toTypedArray(), REQ_PERMISSIONS)
        }

        // MANAGE_EXTERNAL_STORAGE (Android 11+) cần xin qua Settings riêng, không qua runtime permission thường.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !android.os.Environment.isExternalStorageManager()) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = android.net.Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        }
    }

    companion object {
        private const val REQ_PERMISSIONS = 200
    }
}

// ─────────────────────────────────────────────────────────────────────────
// 3) ADMIN — MainActivity
// ─────────────────────────────────────────────────────────────────────────

/**
 * Màn "admin" của module: quản lý Server/Client/Cloud/Settings qua bottom
 * navigation + fragment. Đích đến của "Lưu trữ mạng" và FAB kết nối nhanh
 * ở HomeActivity phía trên.
 */
class MainActivity : LearnsyFileManagerActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            title = getString(R.string.app_name)
            setNavigationIcon(R.drawable.ic_back)
            setNavigationOnClickListener { finishWithAnimation() }
        }
        // Activity vẽ edge-to-edge -> spacer riêng (status_bar_spacer) chừa đúng chiều cao status
        // bar phía trên Toolbar tại runtime, Toolbar giữ nguyên actionBarSize cố định (không bị
        // phình to như khi cộng padding-top thẳng vào Toolbar).
        WindowInsetsUtils.applyTopInsetHeight(findViewById(R.id.status_bar_spacer))

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishWithAnimation()
            }
        })

        requestRuntimePermissions()

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_server -> ServerFragment()
                R.id.nav_client -> ClientFragment()
                R.id.nav_cloud -> CloudFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> ServerFragment()
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
            true
        }

        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_server
        }
    }

    private fun requestRuntimePermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest.toTypedArray(), REQ_PERMISSIONS)
        }
    }

    private fun finishWithAnimation() {
        finish()
        ActivityTransitions.backward(this)
    }

    companion object {
        private const val REQ_PERMISSIONS = 100
    }
}

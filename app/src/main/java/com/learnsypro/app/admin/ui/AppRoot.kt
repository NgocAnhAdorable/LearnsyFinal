package com.learnsypro.app.admin.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.learnsypro.app.admin.data.SettingsStore
import com.learnsypro.app.admin.data.SupabaseConfig
import com.learnsypro.app.admin.ui.branding.AtomBadge
import com.learnsypro.app.admin.ui.components.DiToastHost
import com.learnsypro.app.admin.ui.screens.*
import com.learnsypro.app.admin.ui.theme.Baloo2FontFamily
import com.learnsypro.app.admin.ui.theme.DarkColors
import com.learnsypro.app.admin.ui.theme.LightColors
import com.learnsypro.app.background.BackgroundLayer
import com.learnsypro.app.background.SharedBackgroundViewModel
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class MainTab(val label: String) {
    LESSONS("Bài học"), LISTENING("Listening"), VOCABULARY("Từ vựng"), FILES("Tài liệu"), STUDENTS("Học sinh"), DASHBOARD("Dashboard")
}

// FIX: cùng lý do với ListeningTabSaver trong ListeningManagerScreen.kt — Saver
// tường minh lưu bằng String thay vì dựa vào Serializable ngầm định của enum,
// đảm bảo currentTab luôn khôi phục đúng qua onSaveInstanceState trên mọi OEM.
private val MainTabSaver = Saver<MainTab, String>(
    save = { it.name },
    restore = { name -> runCatching { MainTab.valueOf(name) }.getOrDefault(MainTab.LESSONS) }
)

// Tương đương phần điều phối chính của admin.html + app.jsx (auth gate rồi vào app),
// gộp thêm bottom navigation giữa các module vì bản web dùng tab trên header
// còn Compose Material3 quy ước bottom nav cho mobile.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context) }
    // Đọc đồng bộ từ cache SharedPreferences trước (không block UI thread) —
    // có thể trễ 1 nhịp so với DataStore chung, sẽ được đồng bộ lại ngay bên dưới.
    var dark by remember { mutableStateOf(settings.darkMode) }
    // Đồng bộ dark mode với Dashboard (DataStore chung learnsy_dark) — LIÊN TỤC,
    // không phải đọc 1 lần lúc khởi tạo. Trước đây chỉ gọi syncDarkModeFromShared()
    // một lần trong LaunchedEffect(Unit) rồi dừng: nếu Student đổi dark mode TRONG
    // LÚC Admin đang mở sẵn ở background (Activity singleTop, quay lại không tạo
    // lại AppRoot()), state `dark` ở đây bị kẹt lại giá trị cũ — nút toggle + nền
    // Admin không tự cập nhật theo, dù bên Student đã đổi xong. collect() ở đây
    // sống suốt vòng đời AppRoot() nên nhận được thay đổi bất cứ lúc nào nó xảy ra.
    LaunchedEffect(Unit) {
        settings.darkModeFlow().collect { fromShared ->
            if (fromShared != dark) dark = fromShared
        }
    }
    // Splash chỉ hiện 1 lần / process (lần mở Admin đầu sau khi process sống).
    // Trước: remember { true } → mỗi lần thoát rồi vào lại đều hiện HoloSplash
    // → nhấp nháy. Dùng AdminSplashGate process-level để lần vào lại bỏ splash.
    var showSplash by remember { mutableStateOf(!com.learnsypro.app.admin.AdminSplashGate.alreadyShown) }
    if (showSplash) com.learnsypro.app.admin.AdminSplashGate.alreadyShown = true

    var authed by remember { mutableStateOf<Boolean?>(null) }
    // FIX: currentTab/showDashboard/lessonEditorOpen trước đây dùng remember
    // thường -> khi rời app (vd. mở Trình chụp màn hình) hệ thống có thể kill
    // tiến trình nền để giải phóng RAM; lúc quay lại, Activity được tạo lại
    // và mọi state trong remember{} mất sạch, luôn rơi về giá trị khởi tạo
    // ban đầu (MainTab.LESSONS) dù người dùng đang ở tab Listening. Đổi sang
    // rememberSaveable để Compose tự lưu/khôi phục qua onSaveInstanceState,
    // sống sót qua process death giống các app Android bình thường khác.
    var currentTab by rememberSaveable(stateSaver = MainTabSaver) { mutableStateOf(MainTab.LESSONS) }
    var showDashboard by rememberSaveable { mutableStateOf(false) }
    // Tương đương nút refresh trong header (ảnh mẫu student app) — tăng để trigger reload
    var refreshTick by remember { mutableStateOf(0) }
    // FIX: LessonListScreen không còn tự render LessonEditorScreen bên trong
    // nó nữa (đã tách ra dùng onOpenLesson callback + lessonId thay vì Lesson
    // object đầy đủ) — AppRoot giờ phải tự giữ id bài đang soạn và tự quyết
    // định render Editor hay List. rememberSaveable để giữ đúng bài đang mở
    // qua Activity recreate/resume.
    var editingLessonId by rememberSaveable { mutableStateOf<String?>(null) }
    // Tương đương ẩn header tab/toolbar khi vào màn soạn bài (URL admin#e/...) trong app.jsx
    val lessonEditorOpen = editingLessonId != null
    val scope = rememberCoroutineScope()

    // FIX nhấp nháy tab Tệp tin: AnimatedContent(targetState = currentTab) bên dưới dispose
    // hoàn toàn nội dung tab cũ mỗi khi chuyển tab — trước đây FileManagerScreen tự tạo
    // ViewModel bằng `viewModel()` bên trong nó, nên mỗi lần rời tab Files rồi quay lại,
    // Composable bị dispose/tạo mới -> ViewModel cũ (cùng uiState: danh sách, ô tìm kiếm,
    // vị trí cuộn) mất sạch, cấp lại instance rỗng (loading=true, files=[]) -> LaunchedEffect
    // gọi lại vm.load() -> toàn bộ list biến mất rồi hiện lại từ đầu (nhấp nháy), đồng thời
    // mất luôn từ khoá đang tìm và vị trí cuộn. Giữ ViewModel ở NGOÀI AnimatedContent (tại
    // AppRoot, sống theo vòng đời của AppRoot chứ không theo tab đang hiển thị) rồi truyền
    // xuống FileManagerScreen, để chuyển tab qua lại chỉ ẩn/hiện UI chứ không huỷ state.
    // FIX: trước đây viewModel() không truyền factory -> FileManagerViewModel
    // không có SavedStateHandle -> mất cache danh sách tài liệu + vị trí cuộn
    // khi process bị kill (khác 3 tab kia vốn đã có factory từ trước).
    val fileManagerVm: FileManagerViewModel = viewModel(
        factory = viewModelFactory {
            initializer { FileManagerViewModel(savedStateHandle = createSavedStateHandle()) }
        }
    )

    // FIX THẬT SỰ (bug: chọn file trong "Tài liệu" xong quay lại không thấy gì):
    // filePickerLauncher (rememberLauncherForActivityResult) trước đây nằm NGAY
    // TRONG FileManagerScreen — mà FileManagerScreen lại được compose bên trong
    // AnimatedContent(targetState = currentTab) ở dưới. Picker "*/*" mở Storage
    // Access Framework (vd. Samsung My Files) là 1 Activity ngoài nặng hơn nhiều
    // so với Google Photo Picker (ảnh dùng "image/*" ở AvatarUiHelper, vẫn hoạt
    // động bình thường) — hệ thống nhiều khả năng thu hồi tiến trình nền hơn.
    // Khi đó, launcher sống trong nhánh AnimatedContent có thể chưa kịp đăng ký
    // lại đúng lúc ActivityResultRegistry redeliver kết quả -> uri rơi mất, quay
    // lại app vẫn thấy "Bấm để chọn file", không có toast/lỗi gì (đúng hiện tượng
    // report). Cùng nguyên nhân gốc mà comment cũ trong AdminFileManager.kt đã
    // nói tới (process-death), nhưng chưa fix triệt để vì launcher vẫn nằm dưới
    // AnimatedContent. Hoist launcher + state file đã chọn lên đây — NGANG HÀNG
    // fileManagerVm, ngoài AnimatedContent — để nó đăng ký ngay khi AppRoot mount
    // và không bị ảnh hưởng bởi việc chuyển tab hay Activity resume.
    val fileManagerPicker = com.learnsypro.app.admin.ui.screens.rememberFileManagerPicker()

    // Cùng lý do và cùng cách fix như fileManagerVm ở trên — hoist các
    // ViewModel còn lại của 4 tab kia (Bài học/Listening/Từ vựng/Học sinh) lên
    // NGOÀI AnimatedContent, để chuyển tab qua lại không dispose/tạo lại
    // ViewModel, giữ đúng danh sách/vị trí cuộn/bộ lọc/ô tìm kiếm đang có thay
    // vì phải load lại và nhấp nháy mỗi lần quay lại tab.
    val lessonListVm: LessonListViewModel = viewModel(
        factory = viewModelFactory {
            initializer { LessonListViewModel(savedStateHandle = createSavedStateHandle()) }
        }
    )
    val listeningListVm: ListeningListViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ListeningListViewModel(savedStateHandle = createSavedStateHandle()) }
        }
    )
    val listeningFormVm: ListeningFormViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ListeningFormViewModel(savedStateHandle = createSavedStateHandle()) }
        }
    )
    // FIX: trước đây viewModel() không truyền factory -> VocabularyViewModel
    // không có SavedStateHandle -> mất cache courses + vị trí cuộn khi process
    // bị kill, khác với 3 tab kia đã có sẵn factory createSavedStateHandle().
    val vocabularyVm: VocabularyViewModel = viewModel(
        factory = viewModelFactory {
            initializer { VocabularyViewModel(savedStateHandle = createSavedStateHandle()) }
        }
    )
    val studentListVm: StudentListViewModel = viewModel(
        factory = viewModelFactory {
            initializer { StudentListViewModel(savedStateHandle = createSavedStateHandle()) }
        }
    )

    // FIX: trước đây `authed = status is SessionStatus.Authenticated` coi MỌI
    // trạng thái khác Authenticated là "chưa đăng nhập" — kể cả
    // LoadingFromStorage (supabase-kt đang đọc session đã lưu từ đĩa lúc app
    // khởi động lại) và NetworkError (lỗi mạng tạm thời, không phải logout).
    // Hậu quả: mỗi lần app bị hệ thống kill tiến trình rồi mở lại, người dùng
    // luôn thấy màn Login chớp qua trước khi vào app — dù phiên đăng nhập
    // vẫn còn hợp lệ — giống hệt "refresh lại từ đầu" thay vì tiếp tục phiên
    // cũ như các app bình thường. Tệ hơn, vì lúc đó tab Listening/Bài học có
    // thể đã kịp render và tự gọi load() trong đúng khe hở
    // LoadingFromStorage→Authenticated, request Postgrest bay đi mà access
    // token chưa gắn xong -> RLS ẩn hết dữ liệu, hiện "0 câu/0 bài" (bug đã
    // vá thêm 1 lớp phòng thủ retry ở từng List ViewModel).
    // Giờ chỉ coi là "chưa đăng nhập thật" khi NotAuthenticated; các trạng
    // thái tạm thời (LoadingFromStorage/NetworkError) giữ nguyên `authed =
    // null` (đang kiểm tra, không hiện gì) thay vì nhảy sang màn Login.
    LaunchedEffect(Unit) {
        SupabaseConfig.client.auth.sessionStatus.collectLatest { status ->
            authed = when (status) {
                is SessionStatus.Authenticated -> true
                is SessionStatus.NotAuthenticated -> false
                else -> null
            }
        }
    }

    // Nền tuỳ chỉnh riêng cho Admin — MỌI admin dùng chung 1 nền cố định
    // (isAdminContext = true), tách biệt hoàn toàn với nền riêng của từng
    // học sinh (File Manager dùng Factory mặc định isAdminContext = false).
    // Đọc TRƯỚC khi dựng `colors`, vì colors.surface (nền card dùng ở khắp
    // Admin qua colors.surface) cần biết ngay bgSettings.presetId để quyết
    // định dùng màu card đặc hay card kính mờ.
    val bgVm: SharedBackgroundViewModel = viewModel(
        factory = SharedBackgroundViewModel.Factory(
            context.applicationContext as android.app.Application,
            isAdminContext = true
        )
    )
    val bgSettings by bgVm.bgSettings.collectAsState()
    LaunchedEffect(dark) { bgVm.onBgDarkModeChanged(dark) }

    // Ảnh nền tự upload đang bật (custom_image): thay surface đặc bằng bản bán trong suốt để
    // MỌI card trong Admin (đều lấy màu nền qua colors.surface — 17 màn hình dùng chung 1 chỗ
    // này) tự động lộ ảnh nền + blur (BackgroundLayer) phía sau, không phải sửa từng màn hình.
    // Không đổi các màu khác (rose/lav/border...) — chỉ surface là nền "phủ kín" cần mờ đi.
    val baseColors = if (dark) DarkColors else LightColors
    val colors = if (bgSettings.presetId == "custom_image") {
        baseColors.copy(
            surface = if (dark) Color(0x1AFFFFFF) else Color(0x8FFFFFFF)
        )
    } else {
        baseColors
    }

    fun toggleDark() {
        dark = !dark
        settings.darkMode = dark
    }

    Box(modifier = Modifier.fillMaxSize()) {
    BackgroundLayer(settings = bgSettings, dark = dark, modifier = Modifier.matchParentSize())
    when (authed) {
        null -> {
            // Đang kiểm tra session — tránh nháy màn login rồi lại vào app
        }
        false -> {
            LoginScreen(dark = dark, onAuth = { })
        }
        true -> {
            // FIX: DiToastHost() trước đây chỉ được đặt bên trong
            // AdminDashboardScreen, nên toast (Dynamic Island) chỉ hiện khi
            // đang ở màn Dashboard — mọi ToastCenter.show() gọi từ các tab
            // khác (Bài học, Listening, Học sinh) vẫn ghi vào queue nhưng
            // không có gì render ra màn hình. Bọc cả 2 nhánh trong Box và
            // đặt DiToastHost() ở đây để nó luôn có mặt trong cây UI, không
            // phụ thuộc đang ở tab/màn nào.
            Box(modifier = Modifier.fillMaxSize()) {
            // Dashboard mở như modal trượt từ dưới lên (giống bottom sheet native),
            // đóng lại trượt xuống — thay vì cắt cứng như trước.
            AnimatedContent(
                targetState = showDashboard,
                transitionSpec = {
                    // One UI 9: trượt + fade mềm, hơi scale nhẹ (0.96→1)
                    if (targetState) {
                        (slideInVertically(
                            animationSpec = tween(380, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                        ) { it } + fadeIn(tween(320)) +
                            androidx.compose.animation.scaleIn(
                                initialScale = 0.96f,
                                animationSpec = tween(380, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                            )) togetherWith
                            (fadeOut(tween(160)) + androidx.compose.animation.scaleOut(
                                targetScale = 0.98f,
                                animationSpec = tween(160)
                            ))
                    } else {
                        (fadeIn(tween(240)) + androidx.compose.animation.scaleIn(
                            initialScale = 0.98f,
                            animationSpec = tween(280)
                        )) togetherWith
                            (slideOutVertically(
                                animationSpec = tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                            ) { it } + fadeOut(tween(280)) + androidx.compose.animation.scaleOut(
                                targetScale = 0.96f,
                                animationSpec = tween(280)
                            ))
                    }
                },
                label = "dashboard-toggle"
            ) { isDashboard ->
            if (isDashboard) {
                val lessonsVm: LessonListViewModel = viewModel()
                val lessonsState by lessonsVm.uiState.collectAsState()
                LaunchedEffect(Unit) { lessonsVm.load() }
                AdminDashboardScreen(
                    lessons = lessonsState.lessons,
                    dark = dark,
                    colors = colors,
                    adminName = settings.adminName,
                    onDarkToggle = ::toggleDark,
                    onClose = { showDashboard = false },
                    bgVm = bgVm,
                    onLogout = {
                        showDashboard = false
                        scope.launch {
                            SupabaseConfig.client.auth.signOut()
                            currentTab = MainTab.LESSONS
                        }
                    }
                )
            } else {
                Scaffold(
                    // FIX: containerColor = colors.bg trước đây tô ĐẶC toàn bộ
                    // vùng nội dung Scaffold (mọi tab Bài học/Listening/Từ vựng/
                    // Tài liệu/Học sinh đều nằm trong Scaffold này) — đè hoàn
                    // toàn lên BackgroundLayer đặt ở root AppRoot, nên nền tuỳ
                    // chỉnh chỉ thấy được ở Login/Dashboard (2 nhánh không đi
                    // qua Scaffold này). Đổi sang Color.Transparent để nền
                    // custom xuyên được qua mọi tab; topBar/bottomBar bên dưới
                    // đã tự có surface riêng (colors.surface) nên không bị ảnh
                    // hưởng, chỉ vùng nội dung giữa 2 thanh đó là trong suốt.
                    containerColor = Color.Transparent,
                    topBar = {
                        if (!lessonEditorOpen) {
                            // Header nâng cấp — khớp phong cách "bánh bèo" đã dùng ở
                            // LessonCard/QEditor: glow màu mờ dần phía dưới thay vì
                            // đường kẻ phẳng, để header "nổi" nhẹ trên nội dung thay vì
                            // dán cứng vào nền.
                            // FIX: glow đen mờ ở dark mode gần như vô hình trên nền
                            // đã tối sẵn — đổi sang tím sáng (cùng hue lightGlow,
                            // alpha cao hơn) để header cũng "sáng nhẹ viền tím" như
                            // bản light thay vì trông tối om.
                            val headerGlow = if (dark) Color(0xFFC084FC).copy(alpha = 0.26f) else Color(0xFFA855F7).copy(alpha = 0.12f)
                            Box(
                                modifier = Modifier.drawBehind {
                                    val layers = 4
                                    for (i in layers downTo 1) {
                                        val spread = (i * 2.5).dp.toPx()
                                        val alpha = headerGlow.alpha * (1f - i.toFloat() / layers)
                                        drawRect(
                                            color = headerGlow.copy(alpha = alpha),
                                            topLeft = androidx.compose.ui.geometry.Offset(0f, size.height),
                                            size = androidx.compose.ui.geometry.Size(size.width, spread)
                                        )
                                    }
                                }
                            ) {
                            TopAppBar(
                                title = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        // Logo — khối "L" trên nền gradient hồng-tím bo góc,
                                        // đồng bộ với header index (app học sinh). Thêm glow
                                        // màu để logo có chiều sâu thay vì mảng màu phẳng.
                                        Box(
                                            modifier = Modifier
                                                .shadow(4.dp, RoundedCornerShape(9.dp), ambientColor = Color(0xFFC084FC), spotColor = Color(0xFFC084FC))
                                                .size(30.dp)
                                                .background(
                                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                                        listOf(Color(0xFFF9A8D4), Color(0xFFC084FC))
                                                    ),
                                                    RoundedCornerShape(9.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AtomBadge(size = 17.dp, badgeColor = Color.White, backgroundColor = Color.Transparent)
                                        }
                                        Text(
                                            "Learnsy Pro",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 15.sp,
                                            fontFamily = Baloo2FontFamily,
                                            color = colors.text
                                        )
                                        Spacer(Modifier.width(3.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(
                                                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                        listOf(colors.lavL, colors.lavPale)
                                                    )
                                                )
                                                .border(1.dp, colors.border2, RoundedCornerShape(999.dp))
                                                .padding(horizontal = 9.dp, vertical = 3.dp)
                                        ) {
                                            Text("Admin", fontSize = 10.sp, fontWeight = FontWeight.Black, color = colors.lav)
                                        }
                                    }
                                },
                                actions = {
                                    // Refresh + Dark/Light — IconActionButton chuẩn hoá (cùng
                                    // hệ nút đã dùng ở khu vực soạn bài): scale nảy nhẹ khi
                                    // nhấn + ripple mượt. Nút refresh thêm xoay 360° khi bấm.
                                    val refreshRotation = remember { Animatable(0f) }
                                    LaunchedEffect(refreshTick) {
                                        if (refreshTick > 0) {
                                            refreshRotation.animateTo(
                                                refreshRotation.value + 360f,
                                                animationSpec = tween(500, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                                            )
                                        }
                                    }
                                    com.learnsypro.app.admin.ui.components.IconActionButton(
                                        icon = Icons.Default.Refresh,
                                        contentDescription = "Làm mới",
                                        onClick = { refreshTick++ },
                                        size = com.learnsypro.app.admin.ui.components.IconBtnSize.XLarge,
                                        tint = if (dark) Color(0xFFF59E0B) else Color(0xFFA855F7),
                                        background = if (dark) Color(0x26F59E0B) else Color(0x1AA855F7),
                                        borderColor = if (dark) Color(0x4DF59E0B) else Color(0x40A855F7),
                                        shape = RoundedCornerShape(17.dp),
                                        modifier = Modifier.rotate(refreshRotation.value)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    com.learnsypro.app.admin.ui.components.IconActionButton(
                                        icon = if (dark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                        contentDescription = "Đổi giao diện",
                                        onClick = { toggleDark() },
                                        size = com.learnsypro.app.admin.ui.components.IconBtnSize.XLarge,
                                        tint = if (dark) Color(0xFFF59E0B) else Color(0xFFA855F7),
                                        background = if (dark) Color(0x26F59E0B) else Color(0x1AA855F7),
                                        borderColor = if (dark) Color(0x4DF59E0B) else Color(0x40A855F7),
                                        shape = RoundedCornerShape(17.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = colors.surface,
                                    titleContentColor = colors.text
                                )
                            )
                            }
                        }
                    },
                    bottomBar = {
                        if (!lessonEditorOpen) {
                            AdminBottomBar(
                                colors = colors,
                                dark = dark,
                                currentTab = currentTab,
                                onSelectTab = { currentTab = it },
                                onDashboardTap = { showDashboard = true }
                            )
                        }
                    }
                ) { padding ->
                    Box(modifier = Modifier.padding(if (lessonEditorOpen) androidx.compose.foundation.layout.PaddingValues(0.dp) else padding)) {
                        // One UI 9: chuyển tab = fade + scale nhẹ (không slide ngang để tab bar ổn định).
                        AnimatedContent(
                            targetState = currentTab,
                            transitionSpec = {
                                (fadeIn(tween(280, easing = androidx.compose.animation.core.FastOutSlowInEasing)) +
                                    androidx.compose.animation.scaleIn(
                                        initialScale = 0.97f,
                                        animationSpec = tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                                    )) togetherWith
                                    (fadeOut(tween(160)) + androidx.compose.animation.scaleOut(
                                        targetScale = 0.98f,
                                        animationSpec = tween(160)
                                    ))
                            },
                            label = "tab-switch"
                        ) { tab ->
                        when (tab) {
                            MainTab.LESSONS -> {
                                val openId = editingLessonId
                                // One UI 9: mở editor trượt + scale mềm, đóng tương tự.
                                AnimatedContent(
                                    targetState = openId,
                                    transitionSpec = {
                                        if (targetState != null) {
                                            (slideInHorizontally(
                                                animationSpec = tween(340, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                                            ) { it / 4 } + fadeIn(tween(300)) +
                                                androidx.compose.animation.scaleIn(
                                                    initialScale = 0.96f,
                                                    animationSpec = tween(340, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                                                )) togetherWith
                                                (fadeOut(tween(140)) + androidx.compose.animation.scaleOut(
                                                    targetScale = 0.98f,
                                                    animationSpec = tween(140)
                                                ))
                                        } else {
                                            (fadeIn(tween(220)) + androidx.compose.animation.scaleIn(
                                                initialScale = 0.98f,
                                                animationSpec = tween(260)
                                            )) togetherWith
                                                (slideOutHorizontally(
                                                    animationSpec = tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                                                ) { it / 4 } + fadeOut(tween(260)) +
                                                    androidx.compose.animation.scaleOut(
                                                        targetScale = 0.96f,
                                                        animationSpec = tween(260)
                                                    ))
                                        }
                                    },
                                    label = "lesson-editor-toggle"
                                ) { id ->
                                if (id != null) {
                                    LessonEditorScreen(
                                        colors = colors,
                                        dark = dark,
                                        lessonId = id,
                                        onBack = { editingLessonId = null; refreshTick++ }
                                    )
                                } else {
                                    LessonListScreen(
                                        colors = colors,
                                        dark = dark,
                                        refreshKey = refreshTick,
                                        onOpenLesson = { lid -> editingLessonId = lid },
                                        listVm = lessonListVm
                                    )
                                }
                                }
                            }
                            MainTab.LISTENING -> ListeningManagerScreen(colors = colors, dark = dark, refreshKey = refreshTick, listVm = listeningListVm, formVm = listeningFormVm)
                            MainTab.VOCABULARY -> VocabularyManagerScreen(colors = colors, dark = dark, refreshKey = refreshTick, vm = vocabularyVm)
                            // vm = fileManagerVm (hoist từ AppRoot ở trên) thay vì để
                            // FileManagerScreen tự tạo bằng viewModel() mặc định — giữ
                            // đúng danh sách/vị trí cuộn/từ khoá tìm kiếm khi thoát tab
                            // rồi quay lại, không nhấp nháy load lại từ đầu.
                            MainTab.FILES -> FileManagerScreen(colors = colors, dark = dark, refreshKey = refreshTick, vm = fileManagerVm, picker = fileManagerPicker)
                            MainTab.STUDENTS -> StudentManagerScreen(colors = colors, dark = dark, refreshKey = refreshTick, vm = studentListVm)
                            MainTab.DASHBOARD -> { }
                        }
                        }
                    }
                }
            }
            }
            DiToastHost()
            }
        }
    }
    if (showSplash) HoloSplash(isReady = authed != null, onFinished = { showSplash = false })
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Thanh tab dưới cùng nâng cấp — thay NavigationBar mặc định (indicator
// chữ nhật cứng, xuất hiện/biến mất tức thì) bằng thanh tự vẽ:
//   • pill nền gradient TRƯỢT mượt theo tab đang chọn (animateFloatAsState
//     trên tỉ lệ vị trí 0..3, nhân với bề rộng đo được của thanh)
//   • glow tím/hồng mờ viền trên, cùng tông với header và LessonCard
//   • mỗi item dùng scale-press riêng (rememberBbButtonScale) — bấm vào có
//     phản hồi ngay, không chỉ đổi màu
// Bố cục 4 slot giữ nguyên như bản gốc (Bài học / Đang bảo trì / Học sinh /
// Dashboard) — chỉ nâng cấp thị giác, không đổi tab hay hành vi.
@Composable
private fun AdminBottomBar(
    colors: com.learnsypro.app.admin.ui.theme.LearnsyColors,
    dark: Boolean,
    currentTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    onDashboardTap: () -> Unit
) {
    // FIX: thêm 2 tab mới (Từ vựng / Tài liệu) từ bản admin web — mở rộng
    // thanh tab từ 4 lên 6 slot co giãn đều thay vì slotWidth cố định = 1/4,
    // Dashboard vẫn luôn là slot cuối cùng (mở modal riêng, không giữ currentTab).
    val tabSlots = listOf(MainTab.LESSONS, MainTab.LISTENING, MainTab.VOCABULARY, MainTab.FILES, MainTab.STUDENTS)
    val slotCount = tabSlots.size + 1 // +1 cho Dashboard
    // Vị trí "được chọn" tính theo slot — Dashboard không giữ currentTab
    // (nó mở modal/toast riêng) nên pill chỉ trượt tới tab đang chọn trong
    // tabSlots; khi ở Dashboard thì pill ẩn (alpha 0).
    val selectedIndex = tabSlots.indexOf(currentTab).let { if (it < 0) -1 else it }
    val pillFraction by animateFloatAsState(
        targetValue = if (selectedIndex >= 0) selectedIndex.toFloat() else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "bottombar-pill-position"
    )
    val pillAlpha by animateFloatAsState(if (selectedIndex >= 0) 1f else 0f, label = "bottombar-pill-alpha")

    // FIX: cùng vấn đề header — glow đen ở dark mode gần như vô hình. Đổi
    // sang tím sáng để thanh tab cũng "sáng nhẹ viền tím" nhất quán với header.
    val glowColor = if (dark) Color(0xFFC084FC).copy(alpha = 0.26f) else Color(0xFFA855F7).copy(alpha = 0.12f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val layers = 4
                for (i in layers downTo 1) {
                    val spread = (i * 2.5).dp.toPx()
                    val alpha = glowColor.alpha * (1f - i.toFloat() / layers)
                    drawRect(
                        color = glowColor.copy(alpha = alpha),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, -spread),
                        size = androidx.compose.ui.geometry.Size(size.width, spread)
                    )
                }
            }
            .background(colors.surface)
            // FIX: thanh tab tự vẽ này (không phải NavigationBar chuẩn của M3)
            // không tự cộng inset cho vùng điều hướng hệ thống như NavigationBar
            // gốc vẫn làm — kết quả là icon/label hàng cuối bị thanh gesture bar
            // che mất một phần. Nền/glow ở trên vẫn phủ tới sát đáy màn hình,
            // chỉ nội dung (icon+label) bên trong được đẩy lên qua padding này.
            .navigationBarsPadding()
    ) {
        // FIX: BoxWithConstraints trước đây không có chiều cao xác định trong
        // ngữ cảnh Scaffold.bottomBar (Row/Column cha không set height) —
        // maxHeight đo được là "infinity", nên .fillMaxHeight() của pill bên
        // dưới kéo dài xuống HẾT màn hình thay vì chỉ phủ 1 hàng tab (đúng
        // như ảnh lỗi: dải tím kéo dài từ header tới đáy). Set chiều cao cố
        // định 64dp cho cả khối — pill giờ chỉ cao bằng đúng 1 hàng tab.
        val barHeight = 64.dp
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            val slotWidth = maxWidth / slotCount
            // Pill gradient trượt — chỉ vẽ khi có tab thật sự active trong các slot.
            Box(
                modifier = Modifier
                    .offset(x = slotWidth * pillFraction)
                    .width(slotWidth)
                    .fillMaxHeight()
                    .padding(horizontal = 6.dp)
                    .graphicsLayer { alpha = pillAlpha }
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(colors.lav.copy(alpha = if (dark) 0.22f else 0.14f), colors.rose.copy(alpha = if (dark) 0.16f else 0.08f))
                        )
                    )
            )
            Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                BottomBarItem(
                    label = "Bài học", icon = Icons.Default.MenuBook,
                    selected = currentTab == MainTab.LESSONS,
                    colors = colors,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = { onSelectTab(MainTab.LESSONS) }
                )
                BottomBarItem(
                    label = "Listening",
                    icon = Icons.Default.Headphones,
                    selected = currentTab == MainTab.LISTENING,
                    colors = colors,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = { onSelectTab(MainTab.LISTENING) }
                )
                BottomBarItem(
                    label = "Từ vựng", icon = Icons.Default.Translate,
                    selected = currentTab == MainTab.VOCABULARY,
                    colors = colors,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = { onSelectTab(MainTab.VOCABULARY) }
                )
                BottomBarItem(
                    label = "Tài liệu", icon = Icons.Default.Folder,
                    selected = currentTab == MainTab.FILES,
                    colors = colors,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = { onSelectTab(MainTab.FILES) }
                )
                BottomBarItem(
                    label = "Học sinh", icon = Icons.Default.Groups,
                    selected = currentTab == MainTab.STUDENTS,
                    colors = colors,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = { onSelectTab(MainTab.STUDENTS) }
                )
                BottomBarItem(
                    label = "Dashboard", icon = Icons.Default.Dashboard,
                    selected = false,
                    colors = colors,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = onDashboardTap
                )
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    colors: com.learnsypro.app.admin.ui.theme.LearnsyColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    overlayIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    dimmed: Boolean = false
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressScale by com.learnsypro.app.admin.ui.components.rememberBbButtonScale(interactionSource)
    val tint by animateColorAsState(
        targetValue = when {
            dimmed -> colors.text4
            selected -> colors.lav
            else -> colors.text3
        },
        label = "bottombar-item-tint"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .scale(pressScale)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(vertical = 6.dp)
    ) {
        Box {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            if (overlayIcon != null) {
                Icon(overlayIcon, null, tint = tint, modifier = Modifier.size(11.dp).align(Alignment.Center))
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label, fontSize = if (dimmed) 8.sp else 9.sp,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
            color = tint, maxLines = 1
        )
    }
}
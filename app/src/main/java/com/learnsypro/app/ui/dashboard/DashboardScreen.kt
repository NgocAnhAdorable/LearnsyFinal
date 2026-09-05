package com.learnsypro.app.ui.dashboard

import com.learnsypro.app.background.BackgroundLayer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.learnsypro.app.ui.quiz.Lesson
import com.learnsypro.app.ui.quiz.shuffled
import com.learnsypro.app.ui.theme.Baloo2FontFamily
import kotlinx.coroutines.launch

/**
 * ── DashboardScreen ──
 * Tương đương function DashboardEnhanced({...}) trong dashboard.jsx — điểm
 * ghép nối chính của toàn bộ Dashboard, thay cho return(...) JSX gốc.
 *
 * Props đến từ app.jsx trong bản web (student, lessons, loading, fetchError,
 * history, dark, onPlay, onLogout...) — ở đây nhận qua tham số hàm tương ứng,
 * vì Compose không có prop-drilling qua React Context giống hệt cách JSX
 * làm; NavHost cấp trên (app.jsx tương lai) sẽ truyền các callback này vào.
 *
 * Đã thêm LessonPreviewSheet (intercept onPlay để hiện bottom sheet xác
 * nhận trước khi vào quiz thật) — không tìm thấy file JSX nguồn tương ứng
 * trong repo tại thời điểm convert, UI được tái tạo lại theo ảnh chụp
 * thực tế từ bản deploy (xem ghi chú chi tiết trong LessonPreviewSheet.kt).
 * ExportSheet (xuất HTML offline) đã CHÍNH THỨC BỎ theo yêu cầu — không
 * cần thay thế bằng tính năng nào khác.
 */
@Composable
fun DashboardScreen(
    username: String,
    studentId: String?,
    lessons: List<Lesson>,
    loading: Boolean,
    fetchError: Boolean,
    history: List<HistoryEntry>,
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit,
    onPlay: (Lesson, Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onHistDetail: (HistoryEntry) -> Unit,
    onLogout: () -> Unit,
    onOpenListening: () -> Unit = {},
    onOpenVocab: () -> Unit = {},
    onOpenFileManager: () -> Unit = {},
    // Mở module Admin (trước đây là app Learnsy Standard Admin độc lập, nay
    // gộp chung mã nguồn/APK) — cùng mô hình Intent tới Activity riêng như
    // onOpenFileManager phía trên.
    onOpenAdmin: () -> Unit = {},
    isOffline: Boolean = false,
    downloadedLessonIds: Set<String> = emptySet(),
    onDownloadLesson: (Lesson) -> Unit = {},
    onRefresh: () -> Unit = {},
    isRefreshing: Boolean = false,
    viewModel: DashboardViewModel = viewModel()
) {
    val C = dashboardColors(dark)
    val scope = rememberCoroutineScope()
    val tab by viewModel.tab.collectAsState()
    val liteMode by viewModel.liteMode.collectAsState()
    val flickerFx by viewModel.flickerFx.collectAsState()
    val shuffleQ by viewModel.shuffleQ.collectAsState()
    val shuffleA by viewModel.shuffleA.collectAsState()
    val student by viewModel.student.collectAsState()
    val avatarUrl by viewModel.avatarUrl.collectAsState()
    val avatarLoading by viewModel.avatarLoading.collectAsState()
    val coverUrl by viewModel.coverUrl.collectAsState()
    val coverLoading by viewModel.coverLoading.collectAsState()
    val achievementQueue by viewModel.achievementQueue.collectAsState()
    val bgSettings by viewModel.bgSettings.collectAsState()
    val bgSyncState by viewModel.bgSyncState.collectAsState()
    val bgUploading by viewModel.bgUploading.collectAsState()
    val bgUploadError by viewModel.bgUploadError.collectAsState()
    val mascotEnabled by viewModel.mascotEnabled.collectAsState()

    // Hiển thị Toast khi upload ảnh nền thất bại (bao gồm thông báo chặn tính
    // năng "chỉ dùng được ở website") — trước đây result.msg khi ok=false
    // không có đường nào tới UI, người dùng bấm xong không thấy phản hồi gì.
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(bgUploadError) {
        bgUploadError?.let { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearBgUploadError()
        }
    }

    // State cho LessonPreviewSheet — bấm "Học!" mở sheet xác nhận trước,
    // chỉ gọi onPlay(lesson) thật khi bấm "Bắt đầu học!" trong sheet.
    // "starting" chặn double-tap: AnimatedVisibility giữ nội dung sheet
    // (kể cả nút) trong composition suốt animation exit (~180ms) sau khi
    // previewLesson đã về null, nên nút vẫn bấm được thêm 1 lần trong lúc
    // đó nếu không có cờ này — dẫn tới onPlay() (và do đó lưu kết quả bài
    // học) có thể bị kích hoạt 2 lần cho cùng một lượt vào bài.
    var previewLesson by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Lesson?>(null) }
    var startingLesson by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    LaunchedEffect(username, studentId) {
        viewModel.initForStudent(username, studentId)
    }

    LaunchedEffect(history) {
        viewModel.checkAchievements(history)
    }

    // Tương đương MutationObserver theo dõi class 'dark' trong background-settings.js
    LaunchedEffect(dark) {
        viewModel.onBgDarkModeChanged(dark)
    }

    // Tab "Trang chủ" đã được tô sáng ở thanh điều hướng dưới, nên tiêu đề
    // "Trang chủ" ở giữa topbar là thừa — thay bằng lời chào theo tên học
    // sinh để topbar có ích hơn; các tab khác vẫn giữ tiêu đề rõ ràng.
    // Tab "Trang chủ" đã tô sáng ở thanh điều hướng dưới, và hero banner
    // trong TabHome đã có lời chào theo giờ trong ngày riêng (icon động +
    // sub-text như "Ngày mới tươi đẹp nè!") — nên để trống ở đây tránh lặp
    // thông điệp 2 lần trên cùng một màn hình. Các tab khác vẫn giữ tiêu đề.
    val tabTitle = when (tab) {
        DashboardTab.HOME -> ""
        DashboardTab.STATS -> "Thống kê"
        DashboardTab.HISTORY -> "Lịch sử"
        DashboardTab.FILES -> "Tài liệu"
        DashboardTab.SETTINGS -> "Cài đặt"
    }

    // FAB header: gộp 4 nút (Tệp tin/Admin/Refresh/Sáng-Tối) vào 1 nút tròn
    // icon mặt cười ở góc phải header — nhấn để bung ra 4 nút theo hàng
    // ngang, nhấn lần nữa (hoặc bấm chính nút mặt cười) để thu lại.
    var fabExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Nền tuỳ chỉnh (gradient/ảnh + blur) — tương đương #learnsy-bg-overlay ──
        BackgroundLayer(settings = bgSettings, dark = dark, liteMode = liteMode, modifier = Modifier.fillMaxSize())

        // Nền trang trí bay lượn — tắt khi liteMode (đúng logic gốc !liteMode&&<FloatingDecos>)
        if (!liteMode) {
            FloatingDecos(dark = dark, modifier = Modifier.fillMaxSize())
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top Bar ── (One UI 9: lớp kính nổi trên nội dung — trong
            // suốt + blur nhẹ phía sau, bo góc dưới để tách lớp mềm mại
            // thay vì đường viền cứng như trước)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (dark) Color(0xE6140609) else Color(0xE6FFFFFF)
                    )
                    .padding(horizontal = 18.dp, vertical = 11.dp)
            ) {
                // Logo — bên trái
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    val logoFloatState = rememberTopBarFloat()
                    Box(
                        modifier = Modifier
                            .graphicsLayer { translationY = logoFloatState.value }
                            .size(30.dp)
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(Color(0xFFF9A8D4), Color(0xFFC084FC))
                                ),
                                androidx.compose.foundation.shape.RoundedCornerShape(9.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        com.learnsypro.app.ui.branding.AtomBadge(
                            size = 17.dp,
                            badgeColor = Color.White,
                            backgroundColor = Color.Transparent
                        )
                    }
                    Text(
                        text = "Learnsy Pro",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = C.fg,
                        fontFamily = Baloo2FontFamily
                    )
                }

                // Hàng "hôm nay thứ mấy, ngày/tháng/năm" — giữa header, thay
                // cho chữ tiêu đề tab cũ. Tự ẩn (fade) khi FAB bung 4 nút ra
                // (tránh đè lên hàng nút dài), tự hiện lại khi thu FAB.
                //
                // FIX (dính sát logo "Learnsy Pro"): trước đây dùng
                // align(Alignment.Center) tuyệt đối trong cùng 1 Box với logo
                // (align CenterStart) — Box căn giữa không biết logo chiếm bao
                // nhiêu chỗ bên trái, nên trên header hẹp, mép trái của chữ
                // ngày/thứ chạm luôn vào mép phải của "Learnsy Pro". Thêm
                // padding(start) đủ rộng hơn bề ngang thực tế của logo (icon
                // 30dp + spacing 6dp + text "Learnsy Pro" ~92dp ở 15sp Black
                // ≈ 128dp, làm tròn lên 132dp cho an toàn) để chữ ngày luôn bắt
                // đầu SAU logo, không còn dính vào nhau ở màn hình hẹp.
                androidx.compose.animation.AnimatedVisibility(
                    visible = !fabExpanded,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(start = 132.dp),
                    enter = androidx.compose.animation.fadeIn(tween(180)),
                    exit = androidx.compose.animation.fadeOut(tween(120))
                ) {
                    Text(
                        text = todayLabelVi(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = C.sub
                    )
                }

                // FAB mặt cười — bên phải: nhấn để bung/thu 4 nút hành động
                // (Tệp tin / Admin / Làm mới / Sáng-Tối), thay cho 4 icon rời
                // trước đây. Khi bung, các nút hiện theo hàng ngang bên trái
                // FAB; hàng ngày/thứ ở giữa tự ẩn trong lúc đó.
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    val actionTint = if (dark) Color(0xFFF59E0B) else Color(0xFFA855F7)
                    val actionBg = if (dark) Color(0x26F59E0B) else Color(0x1AA855F7)

                    // 4 nút bung ra — cùng 1 Row con, fade + expand/shrink
                    // theo chiều ngang khi fabExpanded đổi trạng thái.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = fabExpanded,
                        enter = androidx.compose.animation.fadeIn(tween(160)) +
                            androidx.compose.animation.expandHorizontally(tween(220)),
                        exit = androidx.compose.animation.fadeOut(tween(120)) +
                            androidx.compose.animation.shrinkHorizontally(tween(180))
                    ) {
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            // Nút "Tệp tin"
                            val fileInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            val filePressed by fileInteractionSource.collectIsPressedAsState()
                            val fileScale by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = if (filePressed) 0.88f else 1f,
                                animationSpec = com.learnsypro.app.ui.theme.OneUiSpring.bouncy,
                                label = "fileBtnScale"
                            )
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .graphicsLayer { scaleX = fileScale; scaleY = fileScale }
                                    .background(actionBg, androidx.compose.foundation.shape.CircleShape)
                                    .clickable(
                                        interactionSource = fileInteractionSource,
                                        indication = null
                                    ) {
                                        onOpenFileManager()
                                        fabExpanded = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                DashboardIcon(name = "file", size = 18.dp, color = actionTint)
                            }

                            // Nút "Admin"
                            val adminInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            val adminPressed by adminInteractionSource.collectIsPressedAsState()
                            val adminScale by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = if (adminPressed) 0.88f else 1f,
                                animationSpec = com.learnsypro.app.ui.theme.OneUiSpring.bouncy,
                                label = "adminBtnScale"
                            )
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .graphicsLayer { scaleX = adminScale; scaleY = adminScale }
                                    .background(actionBg, androidx.compose.foundation.shape.CircleShape)
                                    .clickable(
                                        interactionSource = adminInteractionSource,
                                        indication = null
                                    ) {
                                        onOpenAdmin()
                                        fabExpanded = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                DashboardIcon(name = "shieldLock", size = 18.dp, color = actionTint)
                            }

                            // Nút refresh
                            val refreshRotation = if (isRefreshing) {
                                val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "refreshSpin")
                                transition.animateFloat(
                                    initialValue = 0f,
                                    targetValue = 360f,
                                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                                        androidx.compose.animation.core.tween(700, easing = androidx.compose.animation.core.LinearEasing)
                                    ),
                                    label = "refreshSpinValue"
                                ).value
                            } else 0f
                            val refreshInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            val refreshPressed by refreshInteractionSource.collectIsPressedAsState()
                            val refreshScale by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = if (refreshPressed) 0.88f else 1f,
                                animationSpec = com.learnsypro.app.ui.theme.OneUiSpring.bouncy,
                                label = "refreshBtnScale"
                            )
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .graphicsLayer { scaleX = refreshScale; scaleY = refreshScale }
                                    .background(actionBg, androidx.compose.foundation.shape.CircleShape)
                                    .clickable(
                                        enabled = !isRefreshing,
                                        interactionSource = refreshInteractionSource,
                                        indication = null
                                    ) {
                                        onRefresh()
                                        fabExpanded = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                DashboardIcon(
                                    name = "refresh",
                                    size = 18.dp,
                                    color = actionTint,
                                    modifier = Modifier.graphicsLayer { rotationZ = refreshRotation }
                                )
                            }

                            // Dark/Light toggle
                            val darkToggleInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            val darkTogglePressed by darkToggleInteractionSource.collectIsPressedAsState()
                            val darkToggleScale by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = if (darkTogglePressed) 0.88f else 1f,
                                animationSpec = com.learnsypro.app.ui.theme.OneUiSpring.bouncy,
                                label = "darkToggleBtnScale"
                            )
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .graphicsLayer { scaleX = darkToggleScale; scaleY = darkToggleScale }
                                    .background(actionBg, androidx.compose.foundation.shape.CircleShape)
                                    .clickable(
                                        interactionSource = darkToggleInteractionSource,
                                        indication = null
                                    ) { onDarkChange(!dark) },
                                contentAlignment = Alignment.Center
                            ) {
                                DashboardIcon(
                                    name = if (dark) "sun" else "moon",
                                    size = 18.dp,
                                    color = actionTint
                                )
                            }
                        }
                    }

                    // Nút FAB chính — icon mặt cười, xoay 45° khi mở để gợi
                    // ý trạng thái "đang mở" (giống pattern FAB Material).
                    val fabInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    val fabPressed by fabInteractionSource.collectIsPressedAsState()
                    val fabScale by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (fabPressed) 0.88f else 1f,
                        animationSpec = com.learnsypro.app.ui.theme.OneUiSpring.bouncy,
                        label = "fabScale"
                    )
                    val fabRotation by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (fabExpanded) 45f else 0f,
                        animationSpec = com.learnsypro.app.ui.theme.OneUiSpring.bouncy,
                        label = "fabRotation"
                    )
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .graphicsLayer {
                                scaleX = fabScale; scaleY = fabScale
                                rotationZ = fabRotation
                            }
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(Color(0xFFF9A8D4), Color(0xFFC084FC))
                                ),
                                androidx.compose.foundation.shape.CircleShape
                            )
                            .clickable(
                                interactionSource = fabInteractionSource,
                                indication = null
                            ) { fabExpanded = !fabExpanded },
                        contentAlignment = Alignment.Center
                    ) {
                        DashboardIcon(
                            name = "smile",
                            size = 20.dp,
                            color = Color.White
                        )
                    }
                }
            }

            // ── Tab Content ──
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        (androidx.compose.animation.fadeIn(tween(200)) +
                            androidx.compose.animation.scaleIn(initialScale = 0.97f, animationSpec = tween(200))) togetherWith
                            (androidx.compose.animation.fadeOut(tween(120)) +
                                androidx.compose.animation.scaleOut(targetScale = 0.97f, animationSpec = tween(120)))
                    },
                    label = "dashboardTabContent"
                ) { currentTab ->
                    when (currentTab) {
                        DashboardTab.HOME -> TabHome(
                            student = student,
                            lessons = lessons,
                            loading = loading,
                            fetchError = fetchError,
                            history = history,
                            dark = dark,
                            liteMode = liteMode,
                            mascotEnabled = mascotEnabled,
                            flickerFx = flickerFx,
                            avatarUrl = avatarUrl,
                            shuffleQ = shuffleQ,
                            shuffleA = shuffleA,
                            onShuffleQChange = viewModel::setShuffleQ,
                            onShuffleAChange = viewModel::setShuffleA,
                            onPlay = { lesson -> previewLesson = lesson; startingLesson = false },
                            onGoToStatsTab = { viewModel.setTab(DashboardTab.STATS) },
                            onOpenListening = onOpenListening,
                            onOpenVocab = onOpenVocab,
                            isOffline = isOffline,
                            downloadedLessonIds = downloadedLessonIds,
                            onDownloadLesson = onDownloadLesson,
                            bgPresetId = bgSettings.presetId
                        )
                        DashboardTab.STATS -> TabStats(history = history, dark = dark, studentId = studentId, bgPresetId = bgSettings.presetId)
                        DashboardTab.HISTORY -> TabHistory(
                            history = history,
                            dark = dark,
                            onHistDetail = onHistDetail,
                            onClearHistory = onClearHistory,
                            bgPresetId = bgSettings.presetId
                        )
                        DashboardTab.FILES -> com.learnsypro.app.ui.files.TabFiles(dark = dark, bgPresetId = bgSettings.presetId)
                        DashboardTab.SETTINGS -> TabSettings(
                            student = student,
                            avatarUrl = avatarUrl,
                            avatarLoading = avatarLoading,
                            onAvatarUpload = { uri -> viewModel.uploadAvatarNow(uri) },
                            onAvatarRemove = { viewModel.removeAvatarNow() },
                            coverUrl = coverUrl,
                            coverLoading = coverLoading,
                            onCoverUpload = { uri -> viewModel.uploadCoverNow(uri) },
                            onCoverRemove = { viewModel.removeCoverNow() },
                            history = history,
                            dark = dark,
                            onDarkChange = onDarkChange,
                            shuffleQ = shuffleQ,
                            onShuffleQChange = viewModel::setShuffleQ,
                            shuffleA = shuffleA,
                            onShuffleAChange = viewModel::setShuffleA,
                            liteMode = liteMode,
                            onLiteModeChange = viewModel::setLiteMode,
                            flickerFx = flickerFx,
                            onFlickerFxChange = viewModel::setFlickerFx,
                            mascotEnabled = mascotEnabled,
                            onMascotEnabledChange = viewModel::setMascotEnabled,
                            bgSettings = bgSettings,
                            bgSyncState = bgSyncState,
                            bgUploading = bgUploading,
                            onBgPickPreset = viewModel::pickBgPreset,
                            onBgPickBlurMode = { mode -> viewModel.pickBgBlurMode(dark, mode) },
                            onBgPickBlurPercent = { percent -> viewModel.pickBgBlurPercent(dark, percent) },
                            onBgPickImage = { uri -> scope.launch { viewModel.uploadBgImage(uri) } },
                            onBgRemoveImage = viewModel::removeBgImage,
                            onLogout = onLogout
                        )
                    }
                }
            }

            // ── Tab Bar ──
            TabBar(
                tab = tab,
                onTabChange = viewModel::setTab,
                dark = dark,
                liteMode = liteMode,
                flickerFx = flickerFx
            )
        }

        // ── Bạn nữ tai mèo xuất hiện ngẫu nhiên (Trang chủ + Tài liệu, tắt
        // được trong Cài đặt → "Bạn đồng hành") — theo yêu cầu: KHÔNG dày đặc
        // như bản cũ, chỉ 1 bạn thấp thoáng ở góc màn hình theo chu kỳ ngẫu
        // nhiên. Không hiện ở Thống kê/Lịch sử/Cài đặt — những tab đó có
        // bảng số liệu/danh sách sát mép, dễ bị che nếu mascot rơi đúng góc.
        if (tab == DashboardTab.HOME || tab == DashboardTab.FILES) {
            RandomMascotLayer(enabled = mascotEnabled, modifier = Modifier.fillMaxSize())
        }

        // ── Achievement Toast (bản đơn giản — xem ghi chú TODO ở đầu file) ──
        achievementQueue.firstOrNull()?.let { achievement ->
            SimpleAchievementBanner(
                achievement = achievement,
                dark = dark,
                onDismiss = viewModel::dismissTopAchievement
            )
        }

        // ── Lesson Preview Sheet ──
        LessonPreviewSheet(
            lesson = previewLesson,
            dark = dark,
            onDismiss = { previewLesson = null },
            onStart = { speedMode ->
                if (!startingLesson) {
                    startingLesson = true
                    val lesson = previewLesson
                    previewLesson = null
                    if (lesson != null) onPlay(lesson.shuffled(shuffleQ, shuffleA), speedMode)
                }
            }
        )
    }
}

/**
 * ── todayLabelVi ──
 * Trả về chuỗi "Thứ Bảy, 29/08/2026" (thứ trong tuần + ngày/tháng/năm)
 * hiện ở giữa header thay cho tiêu đề tab cũ.
 */
private fun todayLabelVi(): String {
    val cal = java.util.Calendar.getInstance()
    val weekdayNames = arrayOf(
        "", "Chủ Nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy"
    )
    val weekday = weekdayNames[cal.get(java.util.Calendar.DAY_OF_WEEK)]
    val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
    val month = cal.get(java.util.Calendar.MONTH) + 1
    val year = cal.get(java.util.Calendar.YEAR)
    return "$weekday, ${day.toString().padStart(2, '0')}/${month.toString().padStart(2, '0')}/$year"
}

@Composable
private fun rememberTopBarFloat(): androidx.compose.runtime.State<Float> {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "topBarFloat")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = -3f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(2800, easing = androidx.compose.animation.core.EaseInOut),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "topBarFloatY"
    )
}

/**
 * ── SimpleAchievementBanner ──
 * Bản TẠM THỜI thay cho AchievementToast component thật của bản web (chưa
 * đọc được source — chỉ thấy nơi gọi). Hiện confetti + animation phức tạp
 * hơn trong bản gốc; đây là banner đơn giản đủ dùng, sẽ nâng cấp khi có
 * thêm thông tin về AchievementToast thật.
 */
@Composable
private fun SimpleAchievementBanner(
    achievement: AchievementUnlock,
    dark: Boolean,
    onDismiss: () -> Unit
) {
    val C = dashboardColors(dark)
    LaunchedEffect(achievement) {
        kotlinx.coroutines.delay(3000)
        onDismiss()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 60.dp)
    ) {
        // Mascot tay tim — chúc mừng, đặt ngay phía trên pill thành tích.
        Column(
            modifier = Modifier.align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MascotImage(drawableRes = MascotPose.HEART_HANDS, sizeDp = 56)
            Box(
                modifier = Modifier
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(achievement.color, achievement.color.copy(alpha = 0.8f))
                        ),
                        androidx.compose.foundation.shape.RoundedCornerShape(50)
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                DashboardIcon(name = achievement.icon, size = 18.dp, color = androidx.compose.ui.graphics.Color.White)
                Text(
                    text = achievement.label,
                    color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp
                )
            }
            }
        }
    }
}

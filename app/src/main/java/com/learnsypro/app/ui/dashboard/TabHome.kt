package com.learnsypro.app.ui.dashboard

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.learnsypro.app.R
import com.learnsypro.app.ui.quiz.Lesson
import com.learnsypro.app.ui.theme.rememberSpinRotation
import androidx.compose.ui.unit.sp
import com.learnsypro.app.ui.theme.Baloo2FontFamily
import com.learnsypro.app.ui.theme.NunitoFontFamily
import java.util.Calendar

private data class Greeting(val text: String, val icon: String, val sub: String)

/** Tính lời chào theo giờ trong ngày — tương đương IIFE greet trong TabHome. */
private fun currentGreeting(): Greeting {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 5 -> Greeting("Khuya rồi nè", "moon", "Đi ngủ thôi bé ơi~")
        hour < 11 -> Greeting("Chào buổi sáng", "sun", "Ngày mới tươi đẹp nè!")
        hour < 13 -> Greeting("Buổi trưa rồi", "sun", "Ăn cơm chưa bé?")
        hour < 18 -> Greeting("Buổi chiều xinh", "sparkle", "Học tiếp nào~")
        else -> Greeting("Tối rồi nè", "moon", "Chăm chỉ ghê á!")
    }
}

/**
 * Tâm trạng mascot theo streak hiện tại — chỉ 3 mức đơn giản, dùng pose có
 * sẵn, không thêm ảnh mới:
 *  - streak >= 7 ngày: rất vui (PEACE_SIGN)
 *  - streak 1-6 ngày: vui (SMILE_WINK)
 *  - streak == 0 nhưng CÓ lịch sử làm bài (nghĩa là vừa mất streak hôm qua
 *    trở về trước): buồn nhẹ (CRYING) — nhắc khéo quay lại học, không phải
 *    dọa nạt.
 *  - Chưa có lịch sử gì: null, không hiện bubble tâm trạng (SLEEPING đã
 *    dùng ở nơi khác cho trạng thái trống, tránh trùng ý nghĩa).
 */
private fun streakMoodPose(streak: Int, hasHistory: Boolean): Int? = when {
    streak >= 7 -> MascotPose.PEACE_SIGN
    streak in 1..6 -> MascotPose.SMILE_WINK
    streak == 0 && hasHistory -> MascotPose.CRYING
    else -> null
}

/** Tính streak ngày liên tiếp làm bài — tương đương logic streak trong TabHome. */
private fun calcStreak(history: List<HistoryEntry>): Int {
    if (history.isEmpty()) return 0
    val zone = java.time.ZoneId.systemDefault()
    val days = history
        .map { java.time.Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate() }
        .distinct()
        .sortedDescending()
    val today = java.time.LocalDate.now(zone)
    if (days.firstOrNull() != today) return 0
    var streak = 1
    for (i in 1 until days.size) {
        val diff = java.time.temporal.ChronoUnit.DAYS.between(days[i], days[i - 1])
        if (diff == 1L) streak++ else break
    }
    return streak
}

/**
 * Streak dài nhất TỪNG đạt được (không nhất thiết đang còn hiệu lực) — quét
 * toàn bộ chuỗi ngày liên tiếp trong history, không chỉ chuỗi tính từ hôm
 * nay như calcStreak(). Dùng cho hiển thị "kỷ lục" thay vì streak hiện tại.
 */
private fun calcBestStreak(history: List<HistoryEntry>): Int {
    if (history.isEmpty()) return 0
    val zone = java.time.ZoneId.systemDefault()
    val days = history
        .map { java.time.Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate() }
        .distinct()
        .sorted()
    var best = 1
    var running = 1
    for (i in 1 until days.size) {
        val diff = java.time.temporal.ChronoUnit.DAYS.between(days[i - 1], days[i])
        running = if (diff == 1L) running + 1 else 1
        if (running > best) best = running
    }
    return best
}

/**
 * ── TabHome ──
 * Tương đương function TabHome({...}) trong dashboard.jsx.
 * Bao gồm: hero banner (avatar + tên + streak/badge + điểm TB), quick stats
 * cuộn ngang, ô tìm kiếm HUD, subject filter pills, toggle xáo câu hỏi/đáp án,
 * và danh sách bài học.
 */
@Composable
fun TabHome(
    student: Student,
    lessons: List<Lesson>,
    loading: Boolean,
    fetchError: Boolean,
    history: List<HistoryEntry>,
    dark: Boolean,
    liteMode: Boolean,
    flickerFx: Boolean,
    avatarUrl: String?,
    shuffleQ: Boolean,
    shuffleA: Boolean,
    onShuffleQChange: (Boolean) -> Unit,
    onShuffleAChange: (Boolean) -> Unit,
    onPlay: (Lesson) -> Unit,
    onGoToStatsTab: () -> Unit,
    onOpenListening: () -> Unit = {},
    onOpenVocab: () -> Unit = {},
    isOffline: Boolean = false,
    downloadedLessonIds: Set<String> = emptySet(),
    onDownloadLesson: (Lesson) -> Unit = {},
    mascotEnabled: Boolean = true,
    /** presetId hiện tại của nền dùng chung (BgSettings.presetId) — "custom_image" khi người
     *  dùng đang bật ảnh nền tự upload, lúc đó card cần chuyển màu bán trong suốt (kính mờ) để
     *  lộ ảnh nền đã blur phía sau thay vì che kín như card thường. */
    bgPresetId: String = "default_light",
    modifier: Modifier = Modifier
) {
    val C = dashboardColors(dark)
    val cardColor = resolvedCardColor(dark, bgPresetId)
    val cardBorderColor = resolvedCardBorderColor(dark, bgPresetId)
    var search by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("all") }
    var showIdCard by remember { mutableStateOf(false) }

    val subjects = remember(lessons) {
        listOf("all") + lessons.map { it.subject ?: "Tiếng Anh" }.distinct()
    }
    val filtered = remember(lessons, search, subject) {
        lessons.filter { l ->
            val matchesQuery = search.isBlank() || l.title.lowercase().contains(search.lowercase())
            val matchesSubject = subject == "all" || l.subject == subject
            matchesQuery && matchesSubject
        }
    }

    val avgPct = if (history.isNotEmpty()) history.map { it.pct }.average() else 0.0
    val streak = remember(history) { calcStreak(history) }
    val bestStreak = remember(history) { calcBestStreak(history) }
    val greet = remember { currentGreeting() }
    val badge = rankBadge(avgPct)
    // XP luôn derive từ history (không lưu state riêng) — mỗi entry cũ cộng
    // XP theo cùng công thức xpForResult() dùng lúc nộp bài.
    val levelState = remember(history) {
        levelForXp(history.sumOf { xpForResult(it.score, it.total) })
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        // ── Hero Banner ──
        item {
            Box(
                modifier = Modifier
                    .padding(start = 14.dp, end = 14.dp, top = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (isCustomImageBackground(bgPresetId)) {
                            // Ảnh nền tự upload đang bật: hạ hẳn alpha của gradient hero để lộ
                            // ảnh + blur phía sau (BackgroundLayer), thay vì phủ kín như trước.
                            if (dark) {
                                Brush.linearGradient(listOf(Color(0x1AF472B6), Color(0x14A855F7)))
                            } else {
                                Brush.linearGradient(listOf(Color(0x59FCE7F3), Color(0x59F5D0FE), Color(0x59E9D5FF)))
                            }
                        } else if (dark) {
                            Brush.linearGradient(listOf(Color(0x2EF472B6), Color(0x24A855F7)))
                        } else {
                            Brush.linearGradient(listOf(Color(0xFFFCE7F3), Color(0xFFF5D0FE), Color(0xFFE9D5FF)))
                        }
                    )
                    .border(
                        1.5.dp,
                        cardBorderColor,
                        RoundedCornerShape(24.dp)
                    )
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(modifier = Modifier.clickable { showIdCard = true }) {
                        AcrylicAvatarCard(
                            name = student.nameOrUsername,
                            avatarUrl = avatarUrl,
                            dark = dark,
                            size = 58.dp,
                            liteMode = liteMode
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val wiggleState = rememberWiggleRotation()
                            Box(modifier = Modifier.graphicsLayer { rotationZ = wiggleState.value }) {
                                DashboardIcon(name = greet.icon, size = 14.dp, color = C.accent)
                            }
                            Text(
                                text = greet.text,
                                color = C.accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = NunitoFontFamily
                            )
                        }
                        Text(
                            text = student.nameOrUsername,
                            color = C.fg,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = Baloo2FontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = greet.sub,
                            color = C.sub,
                            fontSize = 11.sp,
                            fontFamily = NunitoFontFamily
                        )
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (streak > 0) {
                                StickerChip(
                                    text = "$streak ngày",
                                    icon = "fire",
                                    gradient = Brush.linearGradient(listOf(Color(0xFFFDE68A), Color(0xFFFBBF24))),
                                    textColor = Color(0xFF92400E)
                                )
                            }
                            if (history.isNotEmpty()) {
                                StickerChip(
                                    text = "Cấp ${levelState.level}",
                                    icon = "sparkle",
                                    gradient = Brush.linearGradient(listOf(Color(0xFFDDD6FE), Color(0xFFA855F7))),
                                    textColor = Color(0xFF3B0764)
                                )
                            }
                            if (history.isNotEmpty()) {
                                StickerChip(
                                    text = badge.label,
                                    icon = badge.icon,
                                    gradient = badge.gradient,
                                    textColor = Color.White
                                )
                            }
                        }
                    }

                    if (history.isNotEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable(onClick = onGoToStatsTab)
                        ) {
                            ScoreSVG(pct = avgPct, size = 58.dp, dark = dark, liteMode = liteMode)
                            Text(text = "Điểm TB", fontSize = 9.sp, color = C.sub, fontFamily = NunitoFontFamily)
                        }
                    }
                }

                if (mascotEnabled && history.isNotEmpty()) {
                    val moodPose = streakMoodPose(streak, hasHistory = true)
                    if (moodPose != null) {
                        Row(
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (dark) Color(0x14FFFFFF) else Color(0xFFF7F2FA))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MascotImage(drawableRes = moodPose, sizeDp = 34)
                            Text(
                                text = when {
                                    streak >= 7 -> "Streak $streak ngày! Bạn đỉnh quá!"
                                    streak in 1..6 -> "Streak $streak ngày rồi, giữ vững nhé!"
                                    else -> "Streak vừa bị mất rồi, học lại hôm nay để bắt đầu chuỗi mới nhé!"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = C.fg,
                                fontFamily = NunitoFontFamily
                            )
                        }
                    }
                }
            }
        }

        // ── Quick Stats ──
        if (history.isNotEmpty()) {
            item {
                val bestPct = history.maxOf { it.pct }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickStatCard("Bài đã làm", history.size.toString(), "book", Color(0xFFA855F7), dark, onGoToStatsTab, cardColor)
                    QuickStatCard("Điểm TB", "${fmtScore(avgPct)}/10", "star", Color(0xFFF472B6), dark, onGoToStatsTab, cardColor)
                    QuickStatCard("Cao nhất", "${fmtScore(bestPct)}/10", "trophy", Color(0xFF10B981), dark, onGoToStatsTab, cardColor)
                    if (bestStreak > 1) {
                        QuickStatCard("Kỷ lục streak", "$bestStreak ngày", "fire", Color(0xFFFBBF24), dark, onGoToStatsTab, cardColor)
                    }
                }
            }

            // ── XP bar ──
            item {
                Column(
                    modifier = Modifier
                        .padding(start = 14.dp, end = 14.dp, top = 6.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardColor, RoundedCornerShape(16.dp))
                        .border(1.5.dp, cardBorderColor, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cấp ${levelState.level}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = C.fg,
                            fontFamily = NunitoFontFamily
                        )
                        Text(
                            text = "${levelState.xpIntoLevel}/${levelState.xpNeededForLevel} XP",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = C.sub,
                            fontFamily = NunitoFontFamily
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    ProgressBar(pct = levelState.progressPct, color = Color(0xFFA855F7), dark = dark)
                }
            }
        }

        // ── Search ──
        item {
            Box(modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 4.dp)) {
                HudSearchInput(value = search, onChange = { search = it }, dark = dark, liteMode = liteMode)
            }
        }

        // ── Listening CTA ── (tương đương lối vào ListeningPractice trong app.jsx)
        item {
            Row(
                modifier = Modifier
                    .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.card))
                    .background(
                        Brush.linearGradient(
                            if (dark) listOf(Color(0xFF3730A3), Color(0xFF6D28D9))
                            else listOf(Color(0xFF6366F1), Color(0xFF9333EA))
                        )
                    )
                    .border(1.5.dp, Color(0x33FFFFFF), RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.card))
                    .clickable(onClick = onOpenListening)
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0x33FFFFFF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    DashboardIcon(name = "headphones", size = 18.dp, color = Color.White)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Luyện nghe",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        fontFamily = NunitoFontFamily
                    )
                    Text(
                        text = "Điền từ + nhận định Đúng/Sai/NM",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xE6FFFFFF),
                        fontFamily = NunitoFontFamily
                    )
                }
                DashboardIcon(name = "chevronRight", size = 14.dp, color = Color.White)
            }
        }

        // ── Vocabulary CTA ── (tương đương lối vào VocabPractice trong app.jsx)
        item {
            Row(
                modifier = Modifier
                    .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.card))
                    .background(
                        Brush.linearGradient(
                            if (dark) listOf(Color(0xFFBE185D), Color(0xFF7E22CE))
                            else listOf(Color(0xFFF472B6), Color(0xFFA855F7))
                        )
                    )
                    .border(1.5.dp, Color(0x33FFFFFF), RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.card))
                    .clickable(onClick = onOpenVocab)
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0x33FFFFFF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    DashboardIcon(name = "book", size = 18.dp, color = Color.White)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Từ vựng",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        fontFamily = NunitoFontFamily
                    )
                    Text(
                        text = "Học từ mới + kiểm tra viết",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xE6FFFFFF),
                        fontFamily = NunitoFontFamily
                    )
                }
                DashboardIcon(name = "chevronRight", size = 14.dp, color = Color.White)
            }
        }

        // ── Subject Pills ──
        if (subjects.size > 2) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    subjects.forEach { s ->
                        SubjectPill(
                            label = if (s == "all") "Tất cả" else s,
                            active = subject == s,
                            dark = dark,
                            onClick = { subject = s },
                            cardColor = cardColor
                        )
                    }
                }
            }
        }

        // ── Shuffle Toggles ──
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShuffleToggleButton(
                    label = "Xáo câu hỏi", icon = "shuffle", active = shuffleQ, dark = dark,
                    onClick = { onShuffleQChange(!shuffleQ) }, cardColor = cardColor, modifier = Modifier.weight(1f)
                )
                ShuffleToggleButton(
                    label = "Xáo đáp án", icon = "zap", active = shuffleA, dark = dark,
                    onClick = { onShuffleAChange(!shuffleA) }, cardColor = cardColor, modifier = Modifier.weight(1f)
                )
            }
        }

        // ── Banner offline ──
        if (isOffline) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                        .background(Color(0x1AF59E0B), RoundedCornerShape(14.dp))
                        .border(1.5.dp, Color(0x40F59E0B), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DashboardIcon(name = "wifiOff", size = 14.dp, color = Color(0xFFB45309))
                    Text(text = "Không có mạng — đang xem bài đã lưu offline", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309), fontFamily = NunitoFontFamily)
                }
            }
        }

        // ── Lesson List / Loading / Error / Empty ──
        when {
            loading -> item { LoadingState(dark) }
            fetchError -> item { ErrorState(dark) }
            else -> {
                if (filtered.isEmpty()) {
                    item { EmptySearchState(dark) }
                } else {
                    items(filtered, key = { it.id }) { lesson ->
                        val done = history.find { it.lessonTitle == lesson.title }
                        LessonCard(
                            lesson = lesson,
                            done = done,
                            dark = dark,
                            liteMode = liteMode,
                            flickerFx = flickerFx,
                            downloaded = downloadedLessonIds.contains(lesson.id),
                            onDownload = { onDownloadLesson(lesson) },
                            onClick = { onPlay(lesson) },
                            cardColor = cardColor,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }

    if (showIdCard) {
        IdCardFullModal(
            studentName = student.nameOrUsername,
            avatarUrl = avatarUrl,
            dark = dark,
            liteMode = liteMode,
            avgPct = avgPct,
            streak = streak,
            badge = badge,
            totalDone = history.size,
            username = student.username,
            onClose = { showIdCard = false }
        )
    }
}

// ═══════════════════════ Sub-components ═══════════════════════

@Composable
private fun StickerChip(text: String, icon: String, gradient: Brush, textColor: Color) {
    Box(
        modifier = Modifier
            .background(gradient, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DashboardIcon(name = icon, size = 12.dp, color = textColor)
            Text(text = text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor, fontFamily = NunitoFontFamily)
        }
    }
}

@Composable
private fun QuickStatCard(label: String, value: String, icon: String, color: Color, dark: Boolean, onClick: () -> Unit, cardColor: Color? = null) {
    val C = dashboardColors(dark)
    Column(
        modifier = Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor ?: C.card)
            .border(1.5.dp, color.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DashboardIcon(name = icon, size = 20.dp, color = color)
        Text(text = value, fontSize = 17.sp, fontWeight = FontWeight.Black, color = color, fontFamily = Baloo2FontFamily)
        Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = C.sub, fontFamily = NunitoFontFamily)
    }
}

@Composable
private fun SubjectPill(label: String, active: Boolean, dark: Boolean, onClick: () -> Unit, cardColor: Color? = null) {
    val C = dashboardColors(dark)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) AccentGradient else Brush.linearGradient(listOf(cardColor ?: C.card, cardColor ?: C.card)))
            .border(
                if (active) 0.dp else 1.dp,
                if (dark) Color(0x2EF472B6) else Color(0x40F472B6),
                RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = if (active) Color.White else if (dark) Color(0xD9FFC8DC) else Color(0xFFBE4E8A),
            fontFamily = NunitoFontFamily
        )
    }
}

@Composable
private fun ShuffleToggleButton(label: String, icon: String, active: Boolean, dark: Boolean, onClick: () -> Unit, cardColor: Color? = null, modifier: Modifier = Modifier) {
    val C = dashboardColors(dark)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) (if (dark) Color(0x38A855F7) else Color(0x21A855F7)) else (cardColor ?: C.card))
            .border(
                1.5.dp,
                if (active) Color(0xFFA855F7) else Color(0x38A855F7),
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val spinState = if (active) rememberSpinRotation(3000) else null
        Box(modifier = Modifier.graphicsLayer { rotationZ = spinState?.value ?: 0f }) {
            DashboardIcon(name = icon, size = 13.dp, color = if (active) Color(0xFFA855F7) else C.sub)
        }
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) Color(0xFFA855F7) else C.sub,
            fontFamily = NunitoFontFamily
        )
    }
}

@Composable
private fun LessonCard(
    lesson: Lesson,
    done: HistoryEntry?,
    dark: Boolean,
    liteMode: Boolean,
    flickerFx: Boolean,
    downloaded: Boolean = false,
    onDownload: () -> Unit = {},
    onClick: () -> Unit,
    cardColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val C = dashboardColors(dark)
    val col = done?.let { pctColor(it.pct) } ?: Color(0xFFF472B6)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.card))
            .background(cardColor ?: C.card)
            .border(
                1.5.dp,
                if (done != null) col.copy(alpha = 0.25f) else (if (dark) Color(0x2EF472B6) else Color(0x33F472B6)),
                RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.card)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (done != null) Brush.linearGradient(listOf(col.copy(alpha = 0.19f), col.copy(alpha = 0.09f)))
                    else Brush.linearGradient(listOf(Color(0x1FF472B6), Color(0x1FF472B6)))
                ),
            contentAlignment = Alignment.Center
        ) {
            val iconName = when {
                !lesson.password.isNullOrBlank() -> "lock"
                done != null -> "check"
                else -> "book"
            }
            val iconColor = when {
                !lesson.password.isNullOrBlank() -> if (dark) Color(0x99FFB4D2) else Color(0xFFBE4E8A)
                done != null -> col
                else -> Color(0xFFF472B6)
            }
            DashboardIcon(name = iconName, size = 20.dp, color = iconColor)
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    GlitchText(
                        text = lesson.title,
                        color = C.fg,
                        fontSize = 14.sp,
                        liteMode = liteMode,
                        flickerFx = flickerFx
                    )
                }
                // Nút "Tải về" thủ công — chỉ đánh dấu, nội dung đã tự cache
                // sẵn mỗi lần Dashboard tải danh sách bài thành công.
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (downloaded) Color(0x1A10B981) else (if (dark) Color(0x14FFFFFF) else Color(0x14000000)))
                        .clickable(enabled = !downloaded, onClick = onDownload),
                    contentAlignment = Alignment.Center
                ) {
                    DashboardIcon(
                        name = if (downloaded) "check" else "download",
                        size = 10.dp,
                        color = if (downloaded) Color(0xFF10B981) else C.sub
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(text = lesson.subject ?: "Tiếng Anh", fontSize = 11.sp, color = C.sub, fontFamily = NunitoFontFamily)
                Text(text = "·", fontSize = 11.sp, color = C.sub)
                Text(text = "${lesson.questionCount} câu", fontSize = 11.sp, color = C.sub, fontFamily = NunitoFontFamily)
                if (done != null) {
                    Text(text = "·", fontSize = 11.sp, color = col)
                    ScoreBadgeInline(pct = done.pct, fontSize = 10.sp)
                }
            }
        }

        if (done != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ScoreSVG(pct = done.pct, size = 52.dp, dark = dark, liteMode = liteMode)
                Text(text = fmtDate(done.timestampMillis), fontSize = 9.sp, color = C.sub, fontFamily = NunitoFontFamily)
            }
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(AccentGradient)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(text = "Học!", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White, fontFamily = NunitoFontFamily)
            }
        }
    }
}

/**
 * ── Mascot (pose cố định theo ngữ cảnh) ──
 * Khác RandomMascotLayer (random vị trí/pose/thời điểm, chỉ dùng làm nền
 * trang trí) — các hằng số MASCOT_POSE_* bên dưới là ID cố định, dùng khi
 * pose phải khớp Ý NGHĨA màn hình (vui/buồn/ngủ/đọc sách...), gọi trực tiếp
 * trong nội dung (không phải overlay nền).
 */
@Composable
fun MascotImage(
    @androidx.annotation.DrawableRes drawableRes: Int,
    sizeDp: Int = 88,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(id = drawableRes),
        contentDescription = null,
        modifier = modifier.size(sizeDp.dp)
    )
}

// ID pose cố định (map tay từ bộ 27 ảnh — girl_15 cầm điện thoại đã BỎ khỏi
// bộ theo yêu cầu, không có mascot_girl_15 trong resource).
//
// CỐ Ý dùng `val` thường thay vì `const val`: đã xác nhận qua decompile bytecode
// (app-release.apk build 24.5) rằng R8 bundled trong AGP 9.0.1 không parse được
// Kotlin metadata của Kotlin 2.4.0 (build log có hàng trăm dòng "R8: An error
// occurred when parsing kotlin metadata" cho MỌI class — không phải warning vô
// hại) và const-fold nhầm các hằng số @DrawableRes này thành 0 tại nơi gọi
// (MascotImage(drawableRes = MascotPose.PEEK, ...) bị biên dịch thành
// painterResource(id = 0) — literal cứng, không phải đọc từ tham số) → crash
// Resources$NotFoundException ngay ở LoadingState (màn hình đầu tiên sau login).
// `val` thường không thể bị compile-time const-fold nên loại bỏ hoàn toàn khả
// năng này, bất kể R8 hiểu đúng Kotlin metadata hay không. Giữ nguyên đến khi
// AGP 9.0.28+ (theo developer.android.com/build/kotlin-support) được xác nhận
// chạy sạch (không còn warning parse-metadata) trên project này.
object MascotPose {
    val HEART_HANDS = R.drawable.mascot_girl_03      // tay tim — chúc mừng / điểm cao
    val HUG_CAT = R.drawable.mascot_girl_07          // ôm mèo bông — trạng thái trống/chờ
    val SMILE_WINK = R.drawable.mascot_girl_08       // cười tủm tỉm
    val PEACE_SIGN = R.drawable.mascot_girl_09       // tay chữ V — điểm cao
    val CRYING = R.drawable.mascot_girl_10           // khóc — điểm thấp / lỗi tải
    val PEEK = R.drawable.mascot_girl_12             // nấp ló đầu — loading
    val READING_BOOK = R.drawable.mascot_girl_14     // đọc sách — tab Từ vựng
    val LAPTOP = R.drawable.mascot_girl_16           // laptop — tab Tài liệu/Cài đặt
    val SIGN_LOVE_YOU = R.drawable.mascot_girl_17    // bảng "Yêu bạn!" — trước khi vào học
    val SLEEPING = R.drawable.mascot_girl_20         // ngủ zzz — trống lịch sử
    val HEADPHONES = R.drawable.mascot_girl_23       // tai nghe — tab Luyện nghe
    val ARMS_CROSSED = R.drawable.mascot_girl_26     // khoanh tay — lỗi tải dữ liệu
    val WAVE_BYE = R.drawable.mascot_girl_27         // vẫy tay Bye~ — đăng xuất
    val IN_BOX = R.drawable.mascot_girl_28           // trong hộp carton — trống Tài liệu
}

@Composable
private fun LoadingState(dark: Boolean) {
    val C = dashboardColors(dark)
    Column(
        modifier = Modifier.fillMaxWidth().padding(50.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Pose "nấp ló đầu" — khớp cảm giác đang chờ/đang tải, thay icon
        // sparkle xoay tròn trung tính bằng mascot có ngữ cảnh.
        MascotImage(drawableRes = MascotPose.PEEK, sizeDp = 76)
        Text(text = "Đang tải bài học...", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.sub, fontFamily = NunitoFontFamily)
    }
}

@Composable
private fun ErrorState(dark: Boolean) {
    val C = dashboardColors(dark)
    Column(
        modifier = Modifier.fillMaxWidth().padding(50.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Pose khoanh tay "giận dỗi nhẹ" — hợp cảm giác lỗi tải hơn icon buồn chung chung.
        MascotImage(drawableRes = MascotPose.ARMS_CROSSED, sizeDp = 84)
        Text(text = "Không tải được bài học", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.sub, fontFamily = NunitoFontFamily)
        Text(text = "Thử lại nhé bé ơi~", fontSize = 12.sp, color = C.sub, fontFamily = NunitoFontFamily)
    }
}

@Composable
private fun EmptySearchState(dark: Boolean) {
    val C = dashboardColors(dark)
    Column(
        modifier = Modifier.fillMaxWidth().padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Pose ôm mèo bông — nhẹ nhàng hơn icon kính lúp khi không có kết quả.
        MascotImage(drawableRes = MascotPose.HUG_CAT, sizeDp = 64)
        Text(text = "Không tìm thấy bài nào", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.sub, fontFamily = NunitoFontFamily)
    }
}

/** Xoay lắc nhẹ qua lại — tương đương @keyframes bb-wiggle 3s ease-in-out infinite */
@Composable
private fun rememberWiggleRotation(): androidx.compose.runtime.State<Float> {
    if (com.learnsypro.app.ui.theme.LocalLiteMode.current) {
        return remember { mutableStateOf(0f) }
    }
    val transition = rememberInfiniteTransition(label = "wiggle")
    return transition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wiggleRotation"
    )
}

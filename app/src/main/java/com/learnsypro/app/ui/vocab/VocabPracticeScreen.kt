package com.learnsypro.app.ui.vocab

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.learnsypro.app.data.VocabCourse
import com.learnsypro.app.data.VocabUnit
import com.learnsypro.app.data.VocabWord
import com.learnsypro.app.ui.dashboard.DashboardIcon
import com.learnsypro.app.ui.dashboard.MascotImage
import com.learnsypro.app.ui.dashboard.MascotPose
import com.learnsypro.app.ui.dashboard.dashboardColors
import com.learnsypro.app.ui.theme.Baloo2FontFamily
import com.learnsypro.app.ui.theme.NunitoFontFamily
import com.learnsypro.app.ui.theme.rememberFadeUpState
import com.learnsypro.app.ui.theme.rememberFloatOffset
import com.learnsypro.app.ui.theme.rememberPopState
import kotlinx.coroutines.launch

/* ══════════════════════════════════════════════════════════════════════
   VOCAB-PRACTICE — Tương đương vocab-practice.jsx (VocabPractice).
   Luồng: Danh sách khóa học → Danh sách unit → Học từ (thẻ lật) →
   Kiểm tra viết từ (tuỳ chọn) → Hoàn thành.
════════════════════════════════════════════════════════════════════ */

private val POS_LABELS = mapOf(
    "noun" to "Danh từ (n)", "n" to "Danh từ (n)",
    "verb" to "Động từ (v)", "v" to "Động từ (v)",
    "adjective" to "Tính từ (adj)", "adj" to "Tính từ (adj)",
    "adverb" to "Trạng từ (adv)", "adv" to "Trạng từ (adv)",
    "preposition" to "Giới từ (prep)", "prep" to "Giới từ (prep)",
    "conjunction" to "Liên từ (conj)", "conj" to "Liên từ (conj)",
    "pronoun" to "Đại từ (pron)", "pron" to "Đại từ (pron)",
    "interjection" to "Thán từ (intj)", "intj" to "Thán từ (intj)",
    "phrase" to "Cụm từ"
)
private fun getPosLabel(pos: String?): String = POS_LABELS[pos] ?: pos ?: "Từ vựng"

/** Levenshtein distance — dung sai gõ nhầm 1 ký tự khi kiểm tra viết. */
private fun levenshtein(a: String, b: String): Int {
    if (a.isEmpty() || b.isEmpty()) return maxOf(a.length, b.length)
    val dp = Array(a.length + 1) { i -> IntArray(b.length + 1) { j -> if (i == 0) j else if (j == 0) i else 0 } }
    for (i in 1..a.length) {
        for (j in 1..b.length) {
            dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
            else 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
        }
    }
    return dp[a.length][b.length]
}

@Composable
fun VocabPracticeScreen(
    dark: Boolean,
    studentId: String?,
    onBack: () -> Unit,
    viewModel: VocabViewModel = viewModel()
) {
    val C = dashboardColors(dark)
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(C.card)) {
        when {
            state.activeUnit != null -> {
                VocabHeader(title = state.activeUnit!!.title, onBack = { viewModel.exitLearning() }, dark = dark)
                LearningView(
                    unit = state.activeUnit!!,
                    dark = dark,
                    onSpeak = { w, r -> viewModel.speak(w, r) },
                    onExit = { viewModel.exitLearning() },
                    onProgressSaved = { unit, count -> viewModel.saveProgress(studentId, unit, count) }
                )
            }
            state.openCourseId != null -> {
                val course = state.courses.find { it.id == state.openCourseId }
                if (course != null) {
                    VocabHeader(title = course.title, onBack = { viewModel.closeCourse() }, dark = dark)
                    UnitPickerScreen(
                        course = course,
                        dark = dark,
                        mastery = state.mastery,
                        onPickUnit = { viewModel.pickUnit(it) }
                    )
                }
            }
            else -> {
                VocabHeader(title = "Từ vựng", onBack = onBack, dark = dark)
                CourseListScreen(
                    loading = state.loading,
                    loadError = state.loadError,
                    courses = state.courses,
                    dark = dark,
                    onOpenCourse = { viewModel.openCourse(it) }
                )
            }
        }
    }
}

@Composable
private fun VocabHeader(title: String, onBack: () -> Unit, dark: Boolean) {
    val C = dashboardColors(dark)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(C.card)
            .padding(horizontal = 15.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .border(1.5.dp, C.cardBorder, RoundedCornerShape(50))
                .background(C.inputBg)
                .clickable(onClick = onBack)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DashboardIcon(name = "chevronLeft", size = 11.dp, color = C.accent)
                Text(text = "Quay lại", fontSize = 12.sp, fontWeight = FontWeight.Black, color = C.accent, fontFamily = NunitoFontFamily)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DashboardIcon(name = "book", size = 15.dp, color = C.fg)
            Text(
                text = title, fontSize = 14.sp, fontWeight = FontWeight.Black, color = C.fg, fontFamily = NunitoFontFamily,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(70.dp))
    }
}

// ── Danh sách khoá học ──
@Composable
private fun CourseListScreen(
    loading: Boolean,
    loadError: Boolean,
    courses: List<VocabCourse>,
    dark: Boolean,
    onOpenCourse: (String) -> Unit
) {
    val C = dashboardColors(dark)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (loadError) {
            item {
                Text(
                    text = "Không tải được danh sách từ vựng. Thử lại sau nhé!",
                    color = Color(0xFFEF4444), fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                    fontFamily = NunitoFontFamily,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x14EF4444))
                        .border(1.5.dp, Color(0x40EF4444), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
        }

        if (loading) {
            items(3) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(C.card)
                        .border(1.5.dp, C.cardBorder, RoundedCornerShape(18.dp))
                )
            }
        } else if (courses.isEmpty()) {
            item {
                val (fadeAlpha, fadeOffsetY) = rememberFadeUpState()
                val floatState = rememberFloatOffset()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = fadeAlpha; translationY = fadeOffsetY }
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.5.dp, C.cardBorder, RoundedCornerShape(18.dp))
                        .padding(vertical = 40.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(if (dark) Color(0x1AC4B5FD) else Color(0x14A855F7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier.graphicsLayer {
                                translationY = floatState.translateY.value
                                rotationZ = floatState.rotation.value
                            }
                        ) {
                            // Mascot đọc sách — khớp ngữ cảnh Từ vựng, thay icon sách tròn.
                            MascotImage(drawableRes = MascotPose.READING_BOOK, sizeDp = 56)
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(text = "Chưa có khóa học nào", fontSize = 14.5.sp, fontWeight = FontWeight.Black, color = C.fg, fontFamily = Baloo2FontFamily)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Quay lại sau nhé, giáo viên sẽ đăng bài sớm thôi!",
                        fontSize = 12.5.sp, color = C.sub, fontFamily = NunitoFontFamily, textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            itemsIndexed(courses, key = { _, c -> c.id }) { idx, course ->
                CourseCard(course = course, dark = dark, staggerIndex = idx, onClick = { onOpenCourse(course.id) })
            }
        }
    }
}

@Composable
private fun CourseCard(course: VocabCourse, dark: Boolean, staggerIndex: Int, onClick: () -> Unit) {
    val C = dashboardColors(dark)
    val (fadeAlpha, fadeOffsetY) = rememberFadeUpState(delayMillis = (staggerIndex % 8) * 40)
    val unitCount = course.units.size
    val wordCount = course.units.sumOf { it.vocab.size }
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.97f else 1f, com.learnsypro.app.ui.theme.OneUiSpring.bouncy, label = "courseCardPress"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = fadeAlpha; translationY = fadeOffsetY; scaleX = pressScale; scaleY = pressScale }
            .clip(RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.card))
            .background(C.card)
            .border(1.5.dp, C.cardBorder, RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.card))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFF472B6), Color(0xFFA855F7)))),
            contentAlignment = Alignment.Center
        ) {
            DashboardIcon(name = "book", size = 19.dp, color = Color.White)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = course.title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = C.fg,
                fontFamily = NunitoFontFamily, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                text = course.description?.ifBlank { null } ?: "$unitCount unit · $wordCount từ",
                fontSize = 12.sp, color = C.sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontFamily = NunitoFontFamily
            )
        }
        DashboardIcon(name = "chevronRight", size = 16.dp, color = C.sub)
    }
}

// ── Danh sách unit trong 1 khoá học ──
@Composable
private fun UnitPickerScreen(
    course: VocabCourse,
    dark: Boolean,
    mastery: Map<String, Int>,
    onPickUnit: (VocabUnit) -> Unit
) {
    val C = dashboardColors(dark)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (course.units.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.5.dp, C.cardBorder, RoundedCornerShape(16.dp))
                        .padding(vertical = 32.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MascotImage(drawableRes = MascotPose.READING_BOOK, sizeDp = 56)
                    Text(
                        text = "Chưa có bài học nào trong khóa học này.",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.sub, fontFamily = NunitoFontFamily,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            itemsIndexed(course.units, key = { _, u -> u.id }) { idx, unit ->
                UnitCard(unit = unit, dark = dark, staggerIndex = idx, mastered = mastery[unit.id] ?: 0, onClick = { onPickUnit(unit) })
            }
        }
    }
}

@Composable
private fun UnitCard(unit: VocabUnit, dark: Boolean, staggerIndex: Int, mastered: Int, onClick: () -> Unit) {
    val C = dashboardColors(dark)
    val (fadeAlpha, fadeOffsetY) = rememberFadeUpState(delayMillis = (staggerIndex % 8) * 40)
    val total = unit.vocab.size
    val pct = if (total > 0) (mastered * 100 / total) else 0
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.97f else 1f, com.learnsypro.app.ui.theme.OneUiSpring.bouncy, label = "unitCardPress"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = fadeAlpha; translationY = fadeOffsetY; scaleX = pressScale; scaleY = pressScale }
            .clip(RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.card))
            .background(C.card)
            .border(1.5.dp, C.cardBorder, RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.card))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x2EB07CF0)),
            contentAlignment = Alignment.Center
        ) {
            DashboardIcon(name = "book", size = 17.dp, color = Color(0xFFB07CF0))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = unit.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.fg,
                fontFamily = NunitoFontFamily, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!unit.level.isNullOrBlank()) {
                    Text(text = "Level ${unit.level} ·", fontSize = 11.5.sp, color = C.sub, fontFamily = NunitoFontFamily)
                }
                DashboardIcon(name = "book", size = 11.dp, color = C.sub)
                Text(text = "$total từ", fontSize = 11.5.sp, color = C.sub, fontFamily = NunitoFontFamily)
                if (pct > 0) {
                    Text(text = "· $pct%", fontSize = 11.5.sp, fontWeight = FontWeight.Black, color = Color(0xFF10B981), fontFamily = NunitoFontFamily)
                }
            }
            if (total > 0) {
                Spacer(modifier = Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(C.cardBorder)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(pct / 100f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFF10B981), Color(0xFF38BDF8))))
                    )
                }
            }
        }
        if (pct == 100) {
            DashboardIcon(name = "check", size = 17.dp, color = Color(0xFF10B981))
        } else {
            Text(text = "Học ngay →", fontSize = 11.5.sp, fontWeight = FontWeight.Black, color = Color(0xFFB07CF0), fontFamily = NunitoFontFamily)
        }
    }
}

// ── Màn học từ: flashcard + kiểm tra viết ──
private data class WritingFeedback(val ok: Boolean, val exact: Boolean, val correctWord: String)

@Composable
private fun LearningView(
    unit: VocabUnit,
    dark: Boolean,
    onSpeak: (String, Float) -> Unit,
    onExit: () -> Unit,
    onProgressSaved: (VocabUnit, Int) -> Unit
) {
    val C = dashboardColors(dark)
    val allVocab = unit.vocab
    val total = allVocab.size

    var learnedIdx by remember(unit.id) { mutableStateOf<List<Int>>(emptyList()) }
    var unlearned by remember(unit.id) { mutableStateOf(allVocab.indices.toList()) }
    var cursor by remember(unit.id) { mutableIntStateOf(0) }
    var isWriting by remember(unit.id) { mutableStateOf(false) }
    var inputVal by remember(unit.id) { mutableStateOf("") }
    var feedback by remember(unit.id) { mutableStateOf<WritingFeedback?>(null) }
    var done by remember(unit.id) { mutableStateOf(false) }
    var savedOnce by remember(unit.id) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    val progressPct = if (total > 0) (learnedIdx.size * 100 / total) else 0
    val currentVocabIdx = unlearned.getOrNull(cursor)
    val vocab = currentVocabIdx?.let { allVocab.getOrNull(it) }

    LaunchedEffect(unlearned, learnedIdx) {
        if (unlearned.isEmpty() && learnedIdx.size == total && total > 0) {
            done = true
        }
    }
    LaunchedEffect(done, savedOnce) {
        if (done && !savedOnce) {
            savedOnce = true
            onProgressSaved(unit, learnedIdx.size)
        }
    }
    LaunchedEffect(isWriting, cursor) {
        if (isWriting) {
            kotlinx.coroutines.delay(80)
            try { focusRequester.requestFocus() } catch (e: Exception) { /* input chưa mount kịp — bỏ qua */ }
        }
    }

    fun startWritingTest() {
        isWriting = true; feedback = null; inputVal = ""
    }

    fun checkWriting() {
        val v = vocab ?: return
        val userAnswer = inputVal.trim().lowercase()
        val correctAnswer = v.word.trim().lowercase()
        val ok = userAnswer == correctAnswer || levenshtein(userAnswer, correctAnswer) <= 1
        val exact = userAnswer == correctAnswer
        feedback = WritingFeedback(ok, exact, v.word)

        if (ok) {
            val savedCursor = cursor
            val savedIdx = currentVocabIdx!!
            learnedIdx = learnedIdx + savedIdx
            val nextUnlearned = unlearned.filterIndexed { i, _ -> i != savedCursor }
            // Delay 1.5s giữ nguyên tinh thần JSX: cho học sinh thấy phản hồi
            // "Chính xác!" trước khi chuyển sang từ tiếp theo. scope tự huỷ
            // theo vòng đời Composable — không rò rỉ khi rời màn hình giữa chừng.
            scope.launch {
                kotlinx.coroutines.delay(1500)
                unlearned = nextUnlearned
                cursor = if (nextUnlearned.isNotEmpty()) savedCursor % nextUnlearned.size else 0
                isWriting = false; feedback = null; inputVal = ""
            }
        } else {
            scope.launch {
                kotlinx.coroutines.delay(2000)
                isWriting = false; feedback = null; inputVal = ""
            }
        }
    }

    fun skip() {
        if (unlearned.isEmpty()) return
        cursor = (cursor + 1) % unlearned.size
        isWriting = false; feedback = null
    }

    fun restart() {
        learnedIdx = emptyList()
        unlearned = allVocab.indices.toList()
        cursor = 0; isWriting = false; feedback = null; done = false; savedOnce = false
    }

    when {
        total == 0 -> {
            Box(modifier = Modifier.fillMaxSize().padding(60.dp, 20.dp), contentAlignment = Alignment.Center) {
                Text(text = "Unit này chưa có từ vựng nào.", fontSize = 14.sp, color = C.sub, fontFamily = NunitoFontFamily, textAlign = TextAlign.Center)
            }
        }
        done -> {
            val floatState = rememberFloatOffset()
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp, 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Mascot tay tim — chúc mừng học xong hết từ vựng trong bài, giữ
                // hiệu ứng bồng bềnh (floatState) như icon trophy cũ.
                Box(modifier = Modifier.graphicsLayer { translationY = floatState.translateY.value }) {
                    MascotImage(drawableRes = MascotPose.HEART_HANDS, sizeDp = 96)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Hoàn thành!", fontSize = 24.sp, fontWeight = FontWeight.Black, color = C.fg, fontFamily = Baloo2FontFamily)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Bạn đã học xong tất cả $total từ vựng trong bài này!",
                    fontSize = 14.sp, color = C.sub, fontWeight = FontWeight.SemiBold, fontFamily = NunitoFontFamily,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 20.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VocabPrimaryButton(text = "Học lại", icon = "refresh", onClick = ::restart)
                    VocabGhostButton(text = "← Bài khác", dark = dark, onClick = onExit)
                }
            }
        }
        vocab == null -> {
            // Trạng thái chuyển tiếp: vừa học xong từ cuối, chờ chuyển sang màn Hoàn thành
            val floatState = rememberFloatOffset()
            Box(modifier = Modifier.fillMaxSize().padding(20.dp, 40.dp), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.graphicsLayer { translationY = floatState.translateY.value }) {
                    DashboardIcon(name = "trophy", size = 64.dp, color = Color(0xFFF59E0B))
                }
            }
        }
        else -> {
            Column(modifier = Modifier.fillMaxSize()) {
                // Progress bar
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(C.cardBorder)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressPct / 100f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(99.dp))
                                .background(Brush.horizontalGradient(listOf(Color(0xFF10B981), Color(0xFF38BDF8))))
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${learnedIdx.size} / $total từ đã học", fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                        color = C.sub, fontFamily = NunitoFontFamily, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(20.dp, 20.dp), contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = isWriting,
                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                        label = "flashcardMode"
                    ) { writing ->
                        if (!writing) {
                            FlashcardFace(vocab = vocab, dark = dark, onSpeak = onSpeak, onStartWriting = ::startWritingTest, onSkip = ::skip)
                        } else {
                            WritingFace(
                                vocab = vocab, dark = dark, inputVal = inputVal, feedback = feedback,
                                focusRequester = focusRequester,
                                onInputChange = { inputVal = it },
                                onSpeak = onSpeak, onSubmit = ::checkWriting
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VocabPrimaryButton(text: String, icon: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.linearGradient(listOf(Color(0xFFFF6B95), Color(0xFFA855F7))))
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        if (icon != null) DashboardIcon(name = icon, size = 14.dp, color = Color.White)
        Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White, fontFamily = NunitoFontFamily)
    }
}

@Composable
private fun VocabGhostButton(text: String, dark: Boolean, bg: Color? = null, fg: Color? = null, border: Color? = null, onClick: () -> Unit) {
    val C = dashboardColors(dark)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg ?: C.inputBg)
            .border(1.5.dp, border ?: C.cardBorder, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 12.dp)
    ) {
        Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = fg ?: C.fg, fontFamily = NunitoFontFamily)
    }
}

/**
 * ── FlashcardFace ──
 * Thẻ lật thật (trước đây chỉ hiện sẵn cả nghĩa/ví dụ cùng lúc, không phải
 * "lật" đúng nghĩa). Mặt trước: từ + loại từ + IPA + nút nghe. Chạm vào thẻ
 * (hoặc nút "Lật thẻ") xoay 3D sang mặt sau: nghĩa + ví dụ. Dùng
 * graphicsLayer{ rotationY } + cameraDistance để có độ sâu phối cảnh thật,
 * không phải crossfade giả lập lật thẻ.
 */
@Composable
private fun FlashcardFace(
    vocab: VocabWord,
    dark: Boolean,
    onSpeak: (String, Float) -> Unit,
    onStartWriting: () -> Unit,
    onSkip: () -> Unit
) {
    val C = dashboardColors(dark)
    val (popScale, popAlpha) = rememberPopState()

    // flipped reset về false mỗi khi đổi từ (unit.id đổi ở tầng gọi cũng
    // remount composable này qua key trong AnimatedContent/vocab.id bên dưới).
    var flipped by remember(vocab.id) { mutableStateOf(false) }
    val rotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = androidx.compose.animation.core.tween(420, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "flashcardFlip"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .graphicsLayer { scaleX = popScale; scaleY = popScale; alpha = popAlpha },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 280.dp)
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 12f * density
                }
                .clip(RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.sheet))
                .background(if (dark) Color(0x99190D15) else Color.White)
                .border(1.5.dp, C.cardBorder, RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.sheet))
                .clickable { flipped = !flipped }
                .padding(24.dp, 32.dp)
        ) {
            if (rotation <= 90f) {
                // Mặt TRƯỚC — chỉ từ + loại từ + IPA + nghe âm thanh, KHÔNG lộ nghĩa.
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = vocab.word, fontSize = 44.sp, fontWeight = FontWeight.Black, color = Color(0xFFE8547A), fontFamily = NunitoFontFamily)
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFFEF3C7))
                            .padding(horizontal = 16.dp, vertical = 5.dp)
                    ) {
                        Text(text = getPosLabel(vocab.pos), fontSize = 12.5.sp, fontWeight = FontWeight.Black, color = Color(0xFF78350F), fontFamily = NunitoFontFamily)
                    }
                    if (!vocab.ipa.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "/${vocab.ipa.trim('/')}/", fontSize = 18.sp, color = C.sub, fontFamily = NunitoFontFamily)
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (dark) Color(0x14C4B5FD) else Color(0x12A855F7))
                            .border(1.5.dp, if (dark) Color(0x2EC4B5FD) else Color(0x28A855F7), RoundedCornerShape(50))
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(Triple(0.6f, "0.5x", "Nghe chậm"), Triple(1f, "1x", "Nghe bình thường"), Triple(1.4f, "2x", "Nghe nhanh")).forEach { (rate, label, _) ->
                            Box(
                                modifier = Modifier
                                    .size(width = 58.dp, height = 46.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Brush.linearGradient(listOf(Color(0xFFFF6B95), Color(0xFFA855F7))))
                                    .clickable(onClick = { onSpeak(vocab.word, rate) }),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    DashboardIcon(name = "speaker", size = 15.dp, color = Color.White)
                                    Text(text = label, fontSize = 9.5.sp, fontWeight = FontWeight.Black, color = Color.White, fontFamily = NunitoFontFamily)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DashboardIcon(name = "refresh", size = 12.dp, color = C.sub)
                        Text(text = "Chạm để lật thẻ", fontSize = 11.5.sp, color = C.sub, fontFamily = NunitoFontFamily)
                    }
                }
            } else {
                // Mặt SAU — chỉ render khi đã xoay quá 90°, và tự xoay ngược lại
                // rotationY thêm 180° ở lớp graphicsLayer riêng để chữ không bị
                // lật ngược/soi gương khi nhìn từ góc "phía sau" của mặt trước.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { rotationY = 180f },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = vocab.word, fontSize = 22.sp, fontWeight = FontWeight.Black, color = C.sub, fontFamily = NunitoFontFamily)
                    Spacer(modifier = Modifier.height(14.dp))
                    if (!vocab.meaning.isNullOrBlank()) {
                        Text(
                            text = vocab.meaning, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = C.fg, fontFamily = NunitoFontFamily,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 14.dp)
                        )
                    }
                    if (!vocab.example.isNullOrBlank()) {
                        Text(
                            text = "\"${vocab.example}\"", fontSize = 13.5.sp, color = C.sub, fontFamily = NunitoFontFamily,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (dark) Color(0x14C4B5FD) else Color(0x0FA855F7))
                                .padding(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DashboardIcon(name = "refresh", size = 12.dp, color = C.sub)
                        Text(text = "Chạm để lật lại", fontSize = 11.5.sp, color = C.sub, fontFamily = NunitoFontFamily)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VocabPrimaryButton(text = "Kiểm tra viết từ", icon = "edit", onClick = onStartWriting)
            VocabGhostButton(
                text = "→ Bỏ qua", dark = dark,
                bg = Color(0xFFFEF3C7), fg = Color(0xFF92400E), border = Color(0xFFFDE68A),
                onClick = onSkip
            )
        }
    }
}

@Composable
private fun WritingFace(
    vocab: VocabWord,
    dark: Boolean,
    inputVal: String,
    feedback: WritingFeedback?,
    focusRequester: FocusRequester,
    onInputChange: (String) -> Unit,
    onSpeak: (String, Float) -> Unit,
    onSubmit: () -> Unit
) {
    val C = dashboardColors(dark)
    val (popScale, popAlpha) = rememberPopState()
    val borderColor = when {
        feedback == null -> C.cardBorder
        feedback.ok -> Color(0xFF10B981)
        else -> Color(0xFFEF4444)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .graphicsLayer { scaleX = popScale; scaleY = popScale; alpha = popAlpha }
            .clip(RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.sheet))
            .background(if (dark) Color(0x99190D15) else Color.White)
            .border(1.5.dp, C.cardBorder, RoundedCornerShape(com.learnsypro.app.ui.theme.OneUiRadius.sheet))
            .padding(24.dp, 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!vocab.ipa.isNullOrBlank()) {
            Text(text = "/${vocab.ipa.trim('/')}/", fontSize = 18.sp, color = C.sub, fontFamily = NunitoFontFamily, modifier = Modifier.padding(bottom = 22.dp))
        }
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFFFF6B95), Color(0xFFA855F7))))
                .clickable { onSpeak(vocab.word, 1f) },
            contentAlignment = Alignment.Center
        ) {
            DashboardIcon(name = "speaker", size = 16.dp, color = Color.White)
        }
        Spacer(modifier = Modifier.height(22.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 380.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(C.inputBg)
                .border(2.dp, borderColor, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 13.dp)
        ) {
            BasicTextField(
                value = inputVal,
                onValueChange = onInputChange,
                textStyle = TextStyle(fontSize = 17.sp, color = C.fg, fontFamily = NunitoFontFamily, textAlign = TextAlign.Center),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        if (feedback != null) {
            val fbBg = if (feedback.ok) Color(0x1F10B981) else Color(0x14EF4444)
            val fbFg = if (feedback.ok) Color(0xFF10B981) else Color(0xFFEF4444)
            val fbBorder = if (feedback.ok) Color(0x6610B981) else Color(0x66EF4444)
            val fbText = if (feedback.ok) {
                "${if (feedback.exact) "Chính xác!" else "Gần đúng!"} \"${feedback.correctWord}\""
            } else {
                "Sai! Đáp án đúng: \"${feedback.correctWord}\""
            }
            Text(
                text = fbText, fontSize = 14.5.sp, fontWeight = FontWeight.Black, color = fbFg, fontFamily = NunitoFontFamily,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(fbBg)
                    .border(1.5.dp, fbBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 11.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        VocabPrimaryButton(text = "Kiểm tra", icon = "check", onClick = onSubmit)
    }
}

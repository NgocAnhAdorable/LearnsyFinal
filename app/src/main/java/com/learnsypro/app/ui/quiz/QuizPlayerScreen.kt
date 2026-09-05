package com.learnsypro.app.ui.quiz

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.lifecycle.viewmodel.compose.viewModel
import com.learnsypro.app.ui.dashboard.DashboardIcon
import com.learnsypro.app.ui.theme.NunitoFontFamily
import kotlinx.coroutines.launch

/**
 * ── QuizPlayerScreen ──
 * Tương đương function QuizPlayer({lesson,onBack,dark,setDark,onSaveHistory})
 * trong quiz-player.jsx — điểm ghép nối chính toàn bộ màn hình làm quiz.
 *
 * Vuốt trái/phải để chuyển câu dùng detectHorizontalDragGestures (thay
 * touchstart/touchmove/touchend thủ công của bản web). Bàn phím vật lý
 * (A/B/C/D, mũi tên) không convert vì Android chủ yếu dùng cảm ứng.
 *
 * CHƯA convert: Export bottom sheet (đã bỏ theo yêu cầu), lưu kết quả lên
 * Supabase thật trong onSaveHistory (hiện chỉ nhận HistoryRecord, nơi gọi
 * màn hình này cần tự lưu Supabase + cập nhật Dashboard history list).
 */
@Composable
fun QuizPlayerScreen(
    lesson: Lesson,
    dark: Boolean,
    onToggleDark: () -> Unit,
    onBack: () -> Unit,
    currentTotalXp: Int = 0,
    speedMode: Boolean = false,
    onSaveHistory: (HistoryRecord, List<PerQuestionResult>) -> Unit,
    viewModel: QuizViewModel = viewModel()
) {
    val C = quizColors(dark)
    val questions by viewModel.questions.collectAsState()
    val answers by viewModel.answers.collectAsState()
    val flags by viewModel.flags.collectAsState()
    val cur by viewModel.cur.collectAsState()
    val submitted by viewModel.submitted.collectAsState()
    val timeLeft by viewModel.timeLeft.collectAsState()
    val streak by viewModel.streak.collectAsState()
    val bestStreak by viewModel.bestStreak.collectAsState()
    val answerTimesSec by viewModel.answerTimesSec.collectAsState()
    val scoreModalVisible by viewModel.scoreModalVisible.collectAsState()
    val warnModal by viewModel.warnModal.collectAsState()
    val showStats by viewModel.showStats.collectAsState()
    val confettiOn by viewModel.confettiOn.collectAsState()
    val xpEarned by viewModel.xpEarned.collectAsState()
    val levelUpEvent by viewModel.levelUpEvent.collectAsState()
    val practiceMode by viewModel.practiceMode.collectAsState()
    val bgmOn by viewModel.bgmOn.collectAsState()
    val muted by viewModel.muted.collectAsState()
    val (score, total) = viewModel.score.collectAsState().value

    // ── Xác nhận thoát giữa chừng ──
    // Chỉ cần chưa nộp bài (dù chưa trả lời câu nào) là hỏi xác nhận trước
    // khi thoát, vì thoát sẽ xoá tiến độ (resetQuiz + clearSavedState).
    // Đã nộp bài rồi thì không còn gì để mất, thoát thẳng luôn.
    var showExitConfirm by remember { mutableStateOf(false) }
    var toolsOpen by remember { mutableStateOf(false) } // nút gộp cute → bung ra mute/bgm/dark/layout

    // ── Giao diện: false = 'single' (mặc định, mỗi lần 1 câu, vuốt/nút
    // chuyển), true = 'scroll' (tất cả câu xếp dọc, cuộn từ trên xuống).
    // Nhớ lựa chọn qua SharedPreferences (khớp localStorage 'qp_viewmode'
    // bên bản web) — áp dụng chung mọi bài, không riêng bài đang làm.
    val appContext = LocalContext.current
    val prefs = remember { appContext.getSharedPreferences("learnsy_prefs", 0) }
    var scrollMode by remember { mutableStateOf(prefs.getBoolean("qp_scroll_mode", false)) }
    val toggleViewMode: () -> Unit = {
        scrollMode = !scrollMode
        prefs.edit().putBoolean("qp_scroll_mode", scrollMode).apply()
    }
    val attemptExit: () -> Unit = {
        if (!submitted) {
            showExitConfirm = true
        } else {
            onBack()
        }
    }
    androidx.activity.compose.BackHandler(onBack = attemptExit)

    LaunchedEffect(lesson) {
        viewModel.initQuiz(lesson, currentTotalXp, speedMode, onSaveHistory)
    }

    if (questions.isEmpty()) return
    val q = questions.getOrNull(cur) ?: return

    val pct = if (total > 0) score.toDouble() / total else 0.0
    val rc = resultColor(pct)

    val answeredCur = remember(cur, answers) {
        when (q.type) {
            QuestionType.MULTIPLE -> (answers.getOrNull(cur) as? Answer.Single)?.index != null
            QuestionType.MULTI_SELECT -> (answers.getOrNull(cur) as? Answer.Multi)?.indices?.isNotEmpty() == true
            QuestionType.FILL_BLANK -> !(answers.getOrNull(cur) as? Answer.Text)?.value.isNullOrBlank()
            QuestionType.TRUE_FALSE -> (answers.getOrNull(cur) as? Answer.TrueFalseAnswer)?.values?.any { it != null } == true
        }
    }
    val questionRevealed = submitted || (practiceMode && answeredCur)

    val dotStates = remember(answers, submitted, practiceMode) {
        questions.mapIndexed { i, question ->
            val ans = answers.getOrElse(i) { Answer.Empty }
            val isAnswered = when (question.type) {
                QuestionType.MULTIPLE -> (ans as? Answer.Single)?.index != null
                QuestionType.MULTI_SELECT -> (ans as? Answer.Multi)?.indices?.isNotEmpty() == true
                QuestionType.FILL_BLANK -> !(ans as? Answer.Text)?.value.isNullOrBlank()
                QuestionType.TRUE_FALSE -> (ans as? Answer.TrueFalseAnswer)?.values?.none { it == null } == true
            }
            if (!isAnswered) DotState.UNANSWERED
            else if (submitted) {
                if (isAnswerCorrect(question, ans)) DotState.CORRECT else DotState.WRONG
            } else DotState.ANSWERED
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ══════ Header ══════ (One UI 9: lớp kính — nền bán trong suốt
            // đã đủ hiệu ứng "kính". Không dùng Modifier.blur() ở đây vì nó
            // sẽ làm mờ luôn timer/nút/chữ bên trong Column này — Compose
            // blur() áp lên toàn bộ nội dung composable, không tách lớp nền
            // riêng như CSS backdrop-filter của web.)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(C.headerBg)
                    .padding(horizontal = 15.dp, vertical = 11.dp)
            ) {
                if (!submitted) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        if (speedMode) {
                            Row(
                                modifier = Modifier
                                    .background(Color(0x1AF59E0B), RoundedCornerShape(50))
                                    .border(1.5.dp, Color(0x66F59E0B), RoundedCornerShape(50))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                DashboardIcon(name = "zap", size = 11.dp, color = Color(0xFFF59E0B))
                                Text(
                                    text = "Tốc độ",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFF59E0B),
                                    fontFamily = NunitoFontFamily
                                )
                            }
                        }
                        timeLeft?.let { TimerBadge(secondsLeft = it) }
                        if (streak >= 2) {
                            Row(
                                modifier = Modifier
                                    .background(Color(0x1AFCD34D), RoundedCornerShape(50))
                                    .border(1.5.dp, Color(0x66FCD34D), RoundedCornerShape(50))
                                    .padding(horizontal = 11.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DashboardIcon(name = "fire", size = 11.dp, color = Color(0xFFF59E0B))
                                Text(text = " $streak liên tiếp", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFFC89700), fontFamily = NunitoFontFamily)
                            }
                        }
                        Row(
                            modifier = Modifier
                                .background(if (practiceMode) Color(0x1F6EE7B7) else C.navBtn, RoundedCornerShape(50))
                                .border(1.5.dp, if (practiceMode) Color(0x736EE7B7) else C.navBtnBorder, RoundedCornerShape(50))
                                .clickable { viewModel.togglePracticeMode() }
                                .padding(horizontal = 11.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DashboardIcon(name = "book", size = 12.dp, color = if (practiceMode) Color(0xFF10B981) else C.navBtnText)
                            Text(
                                text = " " + (if (practiceMode) "Luyện tập" else "Thi cử"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = if (practiceMode) Color(0xFF10B981) else C.navBtnText,
                                fontFamily = NunitoFontFamily
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    HeaderIconButton(icon = "chevronLeft", onClick = attemptExit, colors = C, label = "Quay lại", showArrow = true)

                    Text(
                        text = lesson.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = C.text,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = NunitoFontFamily,
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                    )

                    Box {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    Brush.linearGradient(listOf(Color(0xFFF9A8D4), Color(0xFFB07CF0))),
                                    CircleShape
                                )
                                .clickable { toolsOpen = !toolsOpen },
                            contentAlignment = Alignment.Center
                        ) {
                            DashboardIcon(name = "sparkle", size = 16.dp, color = Color.White)
                        }

                        if (toolsOpen) {
                            Popup(
                                alignment = Alignment.TopEnd,
                                offset = IntOffset(0, 100),
                                onDismissRequest = { toolsOpen = false }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .background(C.surface, RoundedCornerShape(18.dp))
                                        .border(1.5.dp, C.border, RoundedCornerShape(18.dp))
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    HeaderIconButton(icon = if (muted) "volumeOff" else "volumeOn", onClick = { viewModel.toggleMuted() }, colors = C)
                                    HeaderIconButton(
                                        icon = "music",
                                        onClick = { viewModel.toggleBgm() },
                                        colors = C,
                                        activeColor = if (bgmOn) Color(0xFF10B981) else null
                                    )
                                    HeaderIconButton(icon = if (dark) "sun" else "moon", onClick = onToggleDark, colors = C)
                                    HeaderIconButton(
                                        icon = "notes",
                                        onClick = toggleViewMode,
                                        colors = C,
                                        activeColor = if (scrollMode) Color(0xFF10B981) else null
                                    )
                                }
                            }
                        }
                    }
                }

                if (submitted) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Box(
                            modifier = Modifier
                                .background(Brush.linearGradient(listOf(rc, rc.copy(alpha = 0.7f))), RoundedCornerShape(50))
                                .padding(horizontal = 13.dp, vertical = 5.dp)
                        ) {
                            Text(text = "${fmtS(pct * 10)}/10", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White, fontFamily = NunitoFontFamily)
                        }
                    }
                }

                // Progress bar
                val progressFraction by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = (cur + 1) / questions.size.coerceAtLeast(1).toFloat(),
                    animationSpec = androidx.compose.animation.core.tween(450, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    label = "quizProgress"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(C.progressBg, RoundedCornerShape(99))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(listOf(Color(0xFFF472B6), Color(0xFFA855F7), Color(0xFF818CF8))),
                                RoundedCornerShape(99)
                            )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val info = questionTypeInfo(q.type)
                    Box(
                        modifier = Modifier
                            .background(C.optBg, RoundedCornerShape(50))
                            .border(1.dp, info.color.copy(alpha = 0.13f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text(text = info.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = info.color, fontFamily = NunitoFontFamily)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        val isFlagged1 = flags.getOrElse(cur) { false }
                        val flagScale1 by androidx.compose.animation.core.animateFloatAsState(
                            if (isFlagged1) 1.18f else 1f,
                            androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy),
                            label = "flagScale1"
                        )
                        val flagAlpha1 by androidx.compose.animation.core.animateFloatAsState(if (isFlagged1) 1f else 0.32f, label = "flagAlpha1")
                        Box(
                            modifier = Modifier
                                .graphicsLayer { scaleX = flagScale1; scaleY = flagScale1; alpha = flagAlpha1 }
                                .clickable { viewModel.toggleFlag(cur) },
                        ) {
                            DashboardIcon(name = "star", size = 14.dp, color = if (isFlagged1) Color(0xFFF87171) else C.textMid)
                        }
                        Text(text = "${cur + 1} / ${questions.size}", fontSize = 11.sp, color = C.textMid, fontWeight = FontWeight.Bold, fontFamily = NunitoFontFamily)
                    }
                }
            }

            // ══════ Question area (scroll + swipe) ══════
            if (!scrollMode) {
            var dragOffsetX by remember { mutableStateOf(0f) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .pointerInput(submitted) {
                        if (submitted) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (dragOffsetX <= -64f && cur < questions.size - 1) viewModel.goNext()
                                else if (dragOffsetX >= 64f && cur > 0) viewModel.goPrev()
                                dragOffsetX = 0f
                            }
                        ) { _, dragAmount ->
                            dragOffsetX += dragAmount
                        }
                    }
                    .padding(14.dp)
            ) {
                var prevCur by remember { mutableIntStateOf(cur) }
                val isForward = cur >= prevCur
                SideEffect { prevCur = cur }
                AnimatedContent(
                    targetState = cur,
                    transitionSpec = {
                        if (isForward) {
                            (slideInHorizontally(tween(220)) { w -> w / 3 } + fadeIn(tween(200))) togetherWith
                                (slideOutHorizontally(tween(180)) { w -> -w / 3 } + fadeOut(tween(120)))
                        } else {
                            (slideInHorizontally(tween(220)) { w -> -w / 3 } + fadeIn(tween(200))) togetherWith
                                (slideOutHorizontally(tween(180)) { w -> w / 3 } + fadeOut(tween(120)))
                        }
                    },
                    label = "questionContent"
                ) { _ ->
                    Column {
                        if (q.type == QuestionType.TRUE_FALSE) {
                            if (q.passage.isNotBlank()) {
                                PassageCard(passage = q.passage, source = q.source.ifBlank { null }, dark = dark)
                                Spacer(modifier = Modifier.height(13.dp))
                            }
                            TrueFalseQuestionView(
                                question = q,
                                values = (answers.getOrNull(cur) as? Answer.TrueFalseAnswer)?.values ?: q.items.map { null as Boolean? },
                                submitted = submitted,
                                practiceMode = practiceMode,
                                dark = dark,
                                onValueChange = { ii, v ->
                                    if (submitted) return@TrueFalseQuestionView
                                    val cur0 = (answers.getOrNull(cur) as? Answer.TrueFalseAnswer)?.values?.toMutableList()
                                        ?: q.items.map { null as Boolean? }.toMutableList()
                                    cur0[ii] = v
                                    viewModel.setAnswer(cur, Answer.TrueFalseAnswer(cur0))
                                }
                            )
                        } else {
                            QuestionTextCard(question = q, dark = dark)
                            Spacer(modifier = Modifier.height(13.dp))
                            when (q.type) {
                                QuestionType.FILL_BLANK -> FillBlankQuestionView(
                                    question = q,
                                    value = (answers.getOrNull(cur) as? Answer.Text)?.value ?: "",
                                    submitted = submitted,
                                    revealed = questionRevealed,
                                    dark = dark,
                                    onValueChange = { viewModel.setAnswer(cur, Answer.Text(it)) },
                                    onSubmitNext = {
                                        viewModel.playClickSound()
                                        if (cur < questions.size - 1) viewModel.goNext() else viewModel.handleSubmit()
                                    }
                                )
                                else -> ChoiceQuestionView(
                                    question = q,
                                    singleSelected = (answers.getOrNull(cur) as? Answer.Single)?.index,
                                    multiSelected = (answers.getOrNull(cur) as? Answer.Multi)?.indices ?: emptyList(),
                                    submitted = submitted,
                                    revealed = questionRevealed,
                                    dark = dark,
                                    onSingleChange = { viewModel.setAnswer(cur, Answer.Single(it)) },
                                    onMultiChange = { viewModel.setAnswer(cur, Answer.Multi(it)) }
                                )
                            }
                        }

                        if (questionRevealed && !q.explanation.isNullOrBlank()) {
                            ExplanationCard(explanation = q.explanation, dark = dark)
                        }
                    }
                }
            }
            // Dải mũi tên + chấm tròn CỐ ĐỊNH ngoài vùng cuộn câu hỏi — giữ
            // nguyên vị trí dù nội dung câu dài hay ngắn, không bị đẩy trôi
            // theo khi cuộn xem hết câu. Bọc trong khung bo góc + viền tím
            // glow y hệt thanh chấm ở chế độ cuộn, để 2 giao diện đồng bộ.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .background(C.stickyBg, RoundedCornerShape(16.dp))
                    // Viền tím mềm, không dùng Modifier.shadow (elevation) vì
                    // shadow có hướng sáng cố định từ trên xuống nên không
                    // đối xứng, lại dễ bị LazyColumn/scroll container cắt
                    // bớt phần tràn ra ngoài — gây lệch quầng sáng so với
                    // bản web. Viền đơn giản thì đối xứng tuyệt đối, không
                    // bao giờ bị cắt.
                    .border(1.5.dp, Color(0xFF9B59F5).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NavArrowButton(enabled = cur > 0, direction = -1) { viewModel.goPrev() }
                    Box(modifier = Modifier.weight(1f)) {
                        NavDots(total = questions.size, current = cur, states = dotStates, flags = flags, dark = dark, onDotClick = { viewModel.navTo(it) })
                    }
                    NavArrowButton(enabled = cur < questions.size - 1, direction = 1) { viewModel.goNext() }
                }
            }
            } else {
                // ── Chế độ cuộn: toàn bộ câu hỏi xếp dọc trong LazyColumn.
                // Dải NavDots đặt CỐ ĐỊNH phía trên (ngoài LazyColumn) để
                // nhảy nhanh — không dùng stickyHeader vì API đó không có
                // sẵn ở phiên bản Compose Foundation của dự án (lỗi build
                // "Unresolved reference: stickyHeader"). Đặt ngoài LazyColumn
                // đơn giản hơn và không phụ thuộc phiên bản. Câu đang hiện
                // trong khung nhìn được đồng bộ ngược vào `cur` qua
                // firstVisibleItemIndex — dùng setCurQuietly (không tiếng
                // "nav", không reset giờ tính thời gian trả lời).
                val listState = rememberLazyListState()
                val scrollScope = androidx.compose.runtime.rememberCoroutineScope()
                LaunchedEffect(listState, scrollMode) {
                    // Trước đây dùng firstVisibleItemIndex — chỉ đổi khi câu
                    // trước đó cuộn HẲN ra khỏi khung nhìn, nên nếu câu trước
                    // còn dính 1 mẩu nhỏ ở đỉnh thì dot vẫn đứng yên dù câu
                    // sau đã chiếm gần hết màn hình. Giờ tính theo mốc gần
                    // đỉnh khung nhìn (20% chiều cao) — câu nào đã cuộn qua
                    // mốc đó (đỉnh câu nằm phía trên mốc) mới tính là "đang
                    // xem", giống IntersectionObserver bên bản web.
                    snapshotFlow { listState.layoutInfo }
                        .collect { layoutInfo ->
                            val viewportH = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                            val thresholdLine = (viewportH * 0.2f).toInt()
                            val target = layoutInfo.visibleItemsInfo
                                .lastOrNull { it.offset <= thresholdLine }
                                ?: layoutInfo.visibleItemsInfo.firstOrNull()
                            target?.let { viewModel.setCurQuietly(it.index.coerceIn(0, questions.size - 1)) }
                        }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .background(C.stickyBg, RoundedCornerShape(16.dp))
                        .border(1.5.dp, Color(0xFF9B59F5).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .padding(vertical = 6.dp)
                ) {
                    NavDots(
                        total = questions.size,
                        current = cur,
                        states = dotStates,
                        flags = flags,
                        dark = dark,
                        onDotClick = { idx ->
                            scrollScope.launch { listState.animateScrollToItem(idx) }
                        }
                    )
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(14.dp)
                ) {
                    itemsIndexed(questions, key = { i, _ -> i }) { i, qq ->
                        val ansI = answers.getOrElse(i) { Answer.Empty }
                        val answeredI = when (qq.type) {
                            QuestionType.MULTIPLE -> (ansI as? Answer.Single)?.index != null
                            QuestionType.MULTI_SELECT -> (ansI as? Answer.Multi)?.indices?.isNotEmpty() == true
                            QuestionType.FILL_BLANK -> !(ansI as? Answer.Text)?.value.isNullOrBlank()
                            QuestionType.TRUE_FALSE -> (ansI as? Answer.TrueFalseAnswer)?.values?.any { it != null } == true
                        }
                        val revealedI = submitted || (practiceMode && answeredI)
                        val info = questionTypeInfo(qq.type)

                        Column(modifier = Modifier.padding(bottom = 16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(C.optBg, RoundedCornerShape(50))
                                        .border(1.dp, info.color.copy(alpha = 0.13f), RoundedCornerShape(50))
                                        .padding(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Text(text = "Câu ${i + 1} · ${info.label}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = info.color, fontFamily = NunitoFontFamily)
                                }
                                val isFlagged2 = flags.getOrElse(i) { false }
                                val flagScale2 by androidx.compose.animation.core.animateFloatAsState(
                                    if (isFlagged2) 1.18f else 1f,
                                    androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy),
                                    label = "flagScale2"
                                )
                                val flagAlpha2 by androidx.compose.animation.core.animateFloatAsState(if (isFlagged2) 1f else 0.32f, label = "flagAlpha2")
                                Box(
                                    modifier = Modifier
                                        .graphicsLayer { scaleX = flagScale2; scaleY = flagScale2; alpha = flagAlpha2 }
                                        .clickable { viewModel.toggleFlag(i) }
                                ) {
                                    DashboardIcon(name = "star", size = 14.dp, color = if (isFlagged2) Color(0xFFF87171) else C.textMid)
                                }
                            }

                            if (qq.type == QuestionType.TRUE_FALSE) {
                                if (qq.passage.isNotBlank()) {
                                    PassageCard(passage = qq.passage, source = qq.source.ifBlank { null }, dark = dark)
                                    Spacer(modifier = Modifier.height(13.dp))
                                }
                                TrueFalseQuestionView(
                                    question = qq,
                                    values = (ansI as? Answer.TrueFalseAnswer)?.values ?: qq.items.map { null as Boolean? },
                                    submitted = submitted,
                                    practiceMode = practiceMode,
                                    dark = dark,
                                    onValueChange = { ii, v ->
                                        if (submitted) return@TrueFalseQuestionView
                                        val cur0 = (ansI as? Answer.TrueFalseAnswer)?.values?.toMutableList()
                                            ?: qq.items.map { null as Boolean? }.toMutableList()
                                        cur0[ii] = v
                                        viewModel.setAnswer(i, Answer.TrueFalseAnswer(cur0))
                                    }
                                )
                            } else {
                                QuestionTextCard(question = qq, dark = dark)
                                Spacer(modifier = Modifier.height(13.dp))
                                when (qq.type) {
                                    QuestionType.FILL_BLANK -> FillBlankQuestionView(
                                        question = qq,
                                        value = (ansI as? Answer.Text)?.value ?: "",
                                        submitted = submitted,
                                        revealed = revealedI,
                                        dark = dark,
                                        onValueChange = { viewModel.setAnswer(i, Answer.Text(it)) },
                                        onSubmitNext = { viewModel.playClickSound() }
                                    )
                                    else -> ChoiceQuestionView(
                                        question = qq,
                                        singleSelected = (ansI as? Answer.Single)?.index,
                                        multiSelected = (ansI as? Answer.Multi)?.indices ?: emptyList(),
                                        submitted = submitted,
                                        revealed = revealedI,
                                        dark = dark,
                                        onSingleChange = { viewModel.setAnswer(i, Answer.Single(it)) },
                                        onMultiChange = { viewModel.setAnswer(i, Answer.Multi(it)) }
                                    )
                                }
                            }

                            if (revealedI && !qq.explanation.isNullOrBlank()) {
                                ExplanationCard(explanation = qq.explanation, dark = dark)
                            }
                        }
                    }
                }
            }

            // ══════ Submit bar ══════
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(C.stickyBg)
                    .padding(horizontal = 15.dp, vertical = 14.dp)
            ) {
                if (!submitted) {
                    val submitInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    val submitPressed by submitInteraction.collectIsPressedAsState()
                    val submitScale by androidx.compose.animation.core.animateFloatAsState(
                        if (submitPressed) 0.96f else 1f, com.learnsypro.app.ui.theme.OneUiSpring.bouncy, label = "submitScale"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { scaleX = submitScale; scaleY = submitScale }
                            .background(Brush.linearGradient(listOf(Color(0xFFF472B6), Color(0xFFA855F7))), RoundedCornerShape(50))
                            .clickable(interactionSource = submitInteraction, indication = null) { viewModel.handleSubmit() }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DashboardIcon(name = "heart", size = 14.dp, color = Color.White)
                            Text(text = "Nộp bài", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White, fontFamily = NunitoFontFamily)
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                        val retryInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        val retryPressed by retryInteraction.collectIsPressedAsState()
                        val retryScale by androidx.compose.animation.core.animateFloatAsState(
                            if (retryPressed) 0.96f else 1f, com.learnsypro.app.ui.theme.OneUiSpring.bouncy, label = "retryScale"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer { scaleX = retryScale; scaleY = retryScale }
                                .border(1.5.dp, Color(0x47FF96C8), RoundedCornerShape(50))
                                .clickable(interactionSource = retryInteraction, indication = null) { viewModel.resetQuiz() }
                                .padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "Làm lại", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFFFBAFCE), fontFamily = NunitoFontFamily)
                        }
                        val statsInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        val statsPressed by statsInteraction.collectIsPressedAsState()
                        val statsScale by androidx.compose.animation.core.animateFloatAsState(
                            if (statsPressed) 0.96f else 1f, com.learnsypro.app.ui.theme.OneUiSpring.bouncy, label = "statsScale"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer { scaleX = statsScale; scaleY = statsScale }
                                .background(Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF6EE7B7))), RoundedCornerShape(50))
                                .clickable(interactionSource = statsInteraction, indication = null) { viewModel.openStats() }
                                .padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "Thống kê", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White, fontFamily = NunitoFontFamily)
                        }
                    }
                }
            }
        }

        // ══════ Confetti overlay ══════
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            ConfettiCanvas(
                active = confettiOn,
                widthPx = with(density) { maxWidth.toPx() },
                heightPx = with(density) { maxHeight.toPx() }
            )
        }

        // ══════ Modals ══════
        StatsModal(
            visible = showStats && submitted,
            score = score,
            total = total,
            pct = pct,
            bestStreak = bestStreak,
            answerTimesSec = answerTimesSec,
            questions = questions,
            answers = answers,
            dark = dark,
            onDismiss = { viewModel.closeStats() },
            onRetry = { viewModel.resetQuiz() }
        )

        WarnModal(
            unansweredQuestionNumbers = warnModal,
            dark = dark,
            onReview = { viewModel.dismissWarnAndReview() },
            onSubmitAnyway = { viewModel.dismissWarnAndSubmitAnyway() },
            onDismiss = { }
        )

        ExitConfirmModal(
            visible = showExitConfirm,
            dark = dark,
            onKeepGoing = { showExitConfirm = false },
            onConfirmExit = {
                showExitConfirm = false
                // Xoá sạch tiến độ (đáp án, streak, câu hiện tại) + auto-save
                // đã lưu trên máy, để lần sau vào lại bài này phải làm lại
                // từ đầu thay vì tiếp tục dở dang.
                viewModel.resetQuiz()
                onBack()
            }
        )

        val correctCount = questions.count { isAnswerCorrect(it, answers.getOrElse(questions.indexOf(it)) { Answer.Empty }) }
        ScoreIsland(
            visible = scoreModalVisible,
            pct = pct,
            correctCount = score,
            wrongCount = total - score,
            onClose = { viewModel.closeScoreModal() }
        )

        // Level-up hiện sau ScoreIsland (delay nhẹ để không đè lên animation
        // mở pill kết quả đang chạy — pill mở mất 550ms, đợi 700ms cho chắc).
        var showLevelUp by remember { mutableStateOf(false) }
        LaunchedEffect(scoreModalVisible, levelUpEvent) {
            if (scoreModalVisible && levelUpEvent != null) {
                kotlinx.coroutines.delay(700)
                showLevelUp = true
            }
        }
        LevelUpModal(
            levelState = if (showLevelUp) levelUpEvent else null,
            dark = dark,
            onDismiss = {
                showLevelUp = false
                viewModel.consumeLevelUpEvent()
            }
        )
    }
}

@Composable
private fun HeaderIconButton(
    icon: String,
    onClick: () -> Unit,
    colors: QuizColors,
    label: String? = null,
    showArrow: Boolean = false,
    activeColor: Color? = null
) {
    if (label != null) {
        Row(
            modifier = Modifier
                .background(colors.navBtn, RoundedCornerShape(50))
                .border(1.5.dp, colors.navBtnBorder, RoundedCornerShape(50))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (showArrow) DashboardIcon(name = icon, size = 11.dp, color = colors.navBtnText)
            Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Black, color = colors.navBtnText, fontFamily = NunitoFontFamily)
        }
    } else {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(colors.navBtn, CircleShape)
                .border(1.5.dp, activeColor?.copy(alpha = 0.5f) ?: colors.navBtnBorder, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            DashboardIcon(name = icon, size = 13.dp, color = activeColor ?: colors.navBtnText)
        }
    }
}

@Composable
private fun NavArrowButton(enabled: Boolean, direction: Int, onClick: () -> Unit) {
    val C = quizColors(false) // màu nav dùng chung, alpha xử lý riêng qua enabled
    val alpha by androidx.compose.animation.core.animateFloatAsState(if (enabled) 1f else 0.28f, label = "navArrowAlpha")
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(C.navBtn, CircleShape)
            .border(1.5.dp, C.navBtnBorder, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        DashboardIcon(
            name = if (direction < 0) "chevronLeft" else "chevronRight",
            size = 13.dp,
            color = C.navBtnText.copy(alpha = alpha)
        )
    }
}

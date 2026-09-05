package com.learnsypro.app.ui.listening

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsypro.app.ui.dashboard.DashboardIcon
import com.learnsypro.app.ui.dashboard.ReactLogoIcon
import com.learnsypro.app.ui.quiz.quizColors
import com.learnsypro.app.ui.theme.NunitoFontFamily
import kotlin.math.roundToInt

private data class StAnsUi(val color: Color, val bg: Color, val border: Color, val label: String)

private fun statementAnswerUi(a: StatementAnswer): StAnsUi = when (a) {
    StatementAnswer.TRUE -> StAnsUi(Color(0xFF16A34A), Color(0x1A16A34A), Color(0x5916A34A), "Đúng")
    StatementAnswer.FALSE -> StAnsUi(Color(0xFFDC2626), Color(0x14DC2626), Color(0x52DC2626), "Sai")
    StatementAnswer.NOT_MENTIONED -> StAnsUi(Color(0xFF6366F1), Color(0x146366F1), Color(0x526366F1), "NM")
}

/** Định dạng giây thành mm:ss, ví dụ 95.3f -> "01:35". */
private fun fmtMmSs(totalSeconds: Float): String {
    val s = totalSeconds.roundToInt().coerceAtLeast(0)
    val m = s / 60
    val r = s % 60
    return "%02d:%02d".format(m, r)
}

@androidx.compose.foundation.layout.ExperimentalLayoutApi
@Composable
fun ListeningDetailScreen(
    item: ListeningItem,
    wordBoxDisplay: List<String>,
    blanks: List<String>,
    stmtSel: List<StatementAnswer?>,
    submitted: Boolean,
    isPlaying: Boolean,
    speechRate: Float,
    isRestarting: Boolean,
    showScoreToast: Boolean,
    correct: Int,
    total: Int,
    dark: Boolean,
    muted: Boolean,
    elapsedSeconds: Float,
    estimatedDurationSeconds: Float,
    activeBlankIndex: Int?,
    usedWords: Set<String>,
    onToggleMuted: () -> Unit,
    onBackToList: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onRateChange: (Float) -> Unit,
    onRestart: () -> Unit,
    onSeek: (Float) -> Unit,
    onReshuffleWordBox: () -> Unit,
    onBlankChange: (Int, String) -> Unit,
    onBlankFocus: (Int?) -> Unit,
    onWordTap: (String) -> Unit,
    onStatementChange: (Int, StatementAnswer) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    onDismissScoreToast: () -> Unit
) {
    val C = quizColors(dark)

    // Câu Đúng/Sai/NM đang làm dở (câu đầu tiên chưa chọn đáp án) — hiển thị "x/y".
    val currentStatementNum = (stmtSel.indexOfFirst { it == null }.let { if (it < 0) stmtSel.size else it + 1 })
        .coerceAtMost(stmtSel.size)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(C.headerBg)
                .padding(horizontal = 15.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .background(C.navBtn, RoundedCornerShape(50))
                    .border(1.5.dp, C.navBtnBorder, RoundedCornerShape(50))
                    .clickable(onClick = onBackToList)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DashboardIcon(name = "chevronLeft", size = 11.dp, color = C.navBtnText)
                Text(text = "Danh sách", fontSize = 12.sp, fontWeight = FontWeight.Black, color = C.navBtnText, fontFamily = NunitoFontFamily)
            }

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DashboardIcon(name = "graduationCap", size = 15.dp, color = Color(0xFF6366F1))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Learnsy Pro", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF6366F1), fontFamily = NunitoFontFamily)
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (submitted) {
                    Box(
                        modifier = Modifier
                            .background(Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF34D399))), RoundedCornerShape(50))
                            .padding(horizontal = 13.dp, vertical = 5.dp)
                    ) {
                        Text(text = "$correct/$total", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White, fontFamily = NunitoFontFamily)
                    }
                } else if (item.statements.isNotEmpty()) {
                    // Hiển thị "câu đang làm" (ví dụ "2/5") thay cho khoảng trắng rỗng.
                    Box(
                        modifier = Modifier
                            .background(Color(0x146366F1), RoundedCornerShape(50))
                            .border(1.5.dp, Color(0x386366F1), RoundedCornerShape(50))
                            .padding(horizontal = 11.dp, vertical = 5.dp)
                    ) {
                        Text(text = "$currentStatementNum/${stmtSel.size}", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF6366F1), fontFamily = NunitoFontFamily)
                    }
                } else {
                    Spacer(modifier = Modifier.width(30.dp))
                }
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(
                            if (muted) Color(0x1AEF4444) else Color(0x146366F1),
                            CircleShape
                        )
                        .border(1.5.dp, if (muted) Color(0x52EF4444) else Color(0x386366F1), CircleShape)
                        .clickable(onClick = onToggleMuted)
                        .semantics { contentDescription = if (muted) "Bật âm thanh" else "Tắt âm thanh" },
                    contentAlignment = Alignment.Center
                ) {
                    DashboardIcon(
                        name = if (muted) "volumeOff" else "volumeOn",
                        size = 13.dp,
                        color = if (muted) Color(0xFFEF4444) else Color(0xFF6366F1)
                    )
                }
                ReactLogoIcon(size = 18.dp)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 16.dp),
            // Giảm khoảng trắng giữa các card (13dp -> 10dp) để hiện thêm nội dung
            // trên màn hình, theo feedback "khoảng cách giữa các card hơi nhiều".
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AudioControlCard(
                C = C,
                dark = dark,
                isPlaying = isPlaying,
                isRestarting = isRestarting,
                speechRate = speechRate,
                elapsedSeconds = elapsedSeconds,
                estimatedDurationSeconds = estimatedDurationSeconds,
                onTogglePlayPause = onTogglePlayPause,
                onRateChange = onRateChange,
                onRestart = onRestart,
                onSeek = onSeek
            )

            if (wordBoxDisplay.isNotEmpty()) {
                WordBoxCard(
                    wordBoxDisplay = wordBoxDisplay,
                    usedWords = usedWords,
                    shuffleEnabled = item.shuffleWordBox,
                    submitted = submitted,
                    onReshuffleWordBox = onReshuffleWordBox,
                    onWordTap = onWordTap
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(C.surfaceQ, RoundedCornerShape(16.dp))
                    .border(1.5.dp, C.borderQ, RoundedCornerShape(16.dp))
                    .padding(horizontal = 17.dp, vertical = 15.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    DashboardIcon(name = "book", size = 11.dp, color = Color(0xFFB07CF0))
                    Text(text = "ĐOẠN VĂN", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFFB07CF0), letterSpacing = 1.2.sp, fontFamily = NunitoFontFamily)
                }
                Spacer(modifier = Modifier.height(8.dp))
                PassageWithBlanks(
                    item = item, blanks = blanks, submitted = submitted, dark = dark,
                    activeBlankIndex = activeBlankIndex,
                    onBlankChange = onBlankChange,
                    onBlankFocus = onBlankFocus
                )
            }

            if (item.statements.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.statements.forEachIndexed { i, st ->
                        StatementRow(
                            index = i, statement = st, selected = stmtSel.getOrNull(i),
                            submitted = submitted, dark = dark, onSelect = { onStatementChange(i, it) }
                        )
                    }
                }
            }

            if (!submitted) {
                // Nút Nộp bài — hành động chính, làm nổi bật hơn bằng viền sáng,
                // bóng đổ rõ và cỡ chữ lớn hơn để tách biệt hẳn với các nút phụ khác.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Color(0xFFF472B6), Color(0xFFA855F7))), RoundedCornerShape(50))
                        .border(2.dp, Color(0x66FFFFFF), RoundedCornerShape(50))
                        .clickable(onClick = onSubmit)
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        DashboardIcon(name = "check", size = 15.dp, color = Color.White)
                        Text(text = "NỘP BÀI", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 0.5.sp, fontFamily = NunitoFontFamily)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(C.navBtn, RoundedCornerShape(50))
                        .border(1.5.dp, C.navBtnBorder, RoundedCornerShape(50))
                        .clickable(onClick = onRetry)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DashboardIcon(name = "chevronLeft", size = 13.dp, color = C.navBtnText)
                        Text(text = "Làm lại", fontSize = 14.sp, fontWeight = FontWeight.Black, color = C.navBtnText, fontFamily = NunitoFontFamily)
                    }
                }
            }
        }
    }

    ScoreToastListening(visible = showScoreToast, correct = correct, total = total, onClose = onDismissScoreToast)
}

/** Thẻ điều khiển audio: play/pause, tua 15s lùi/tới, thời lượng mm:ss, tốc độ đọc, phát lại. */
@Composable
private fun AudioControlCard(
    C: com.learnsypro.app.ui.quiz.QuizColors,
    dark: Boolean,
    isPlaying: Boolean,
    isRestarting: Boolean,
    speechRate: Float,
    elapsedSeconds: Float,
    estimatedDurationSeconds: Float,
    onTogglePlayPause: () -> Unit,
    onRateChange: (Float) -> Unit,
    onRestart: () -> Unit,
    onSeek: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(C.surfaceQ, RoundedCornerShape(18.dp))
            .border(1.5.dp, if (isRestarting) Color(0xFFF59E0B) else C.borderQ, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Tua lùi 15 giây
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(C.optBg, CircleShape)
                    .border(1.5.dp, C.optBorder, CircleShape)
                    .clickable { onSeek(-15f) }
                    .semantics { contentDescription = "Lùi 15 giây" },
                contentAlignment = Alignment.Center
            ) {
                DashboardIcon(name = "rewind15", size = 15.dp, color = C.text2)
            }

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        Brush.linearGradient(
                            if (isPlaying) listOf(Color(0xFFF59E0B), Color(0xFFF97316))
                            else listOf(Color(0xFF10B981), Color(0xFF34D399))
                        ),
                        CircleShape
                    )
                    .clickable(onClick = onTogglePlayPause),
                contentAlignment = Alignment.Center
            ) {
                DashboardIcon(name = if (isPlaying) "close" else "chevronRight", size = 16.dp, color = Color.White)
            }

            // Tua tới 15 giây
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(C.optBg, CircleShape)
                    .border(1.5.dp, C.optBorder, CircleShape)
                    .clickable { onSeek(15f) }
                    .semantics { contentDescription = "Tới 15 giây" },
                contentAlignment = Alignment.Center
            ) {
                DashboardIcon(name = "forward15", size = 15.dp, color = C.text2)
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "0.8x", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = C.textMuted, fontFamily = NunitoFontFamily)
                    Text(text = "%.1fx".format(speechRate), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = C.text, fontFamily = NunitoFontFamily)
                    Text(text = "1.2x", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = C.textMuted, fontFamily = NunitoFontFamily)
                }
                Slider(
                    value = speechRate,
                    onValueChange = onRateChange,
                    valueRange = 0.8f..1.2f,
                    steps = 7,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFB07CF0),
                        activeTrackColor = Color(0xFFB07CF0),
                        inactiveTrackColor = if (dark) Color(0x26FFFFFF) else Color(0x1F000000)
                    ),
                    // Slider cao hơn để dễ kéo trên điện thoại.
                    modifier = Modifier.height(28.dp)
                )
            }

            Row(
                modifier = Modifier
                    .background(
                        if (isRestarting) Color(0x26F59E0B) else Color.Transparent,
                        CircleShape
                    )
                    .border(1.5.dp, if (isRestarting) Color(0xFFF59E0B) else C.borderQ, CircleShape)
                    .clickable(onClick = onRestart)
                    .size(34.dp)
                    .semantics { contentDescription = "Phát lại từ đầu" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isRestarting) {
                    val rotation by rememberInfiniteTransition(label = "restartSpin").animateFloat(
                        0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "restartSpinRotation"
                    )
                    Box(modifier = Modifier.graphicsLayer { rotationZ = rotation }) {
                        DashboardIcon(name = "spinner", size = 14.dp, color = Color(0xFFF59E0B))
                    }
                } else {
                    // Icon Undo thay cho chữ "Phát lại" — gọn hơn theo feedback.
                    DashboardIcon(name = "undo", size = 15.dp, color = C.text2)
                }
            }
        }

        // Thời lượng ước lượng mm:ss + thanh tiến trình (audio TTS không có
        // duration thật nên ước lượng theo số từ ở tốc độ đọc trung bình).
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val progress = if (estimatedDurationSeconds > 0f) (elapsedSeconds / estimatedDurationSeconds).coerceIn(0f, 1f) else 0f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(C.progressBg, RoundedCornerShape(50))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(Brush.horizontalGradient(listOf(Color(0xFFB07CF0), Color(0xFF6366F1))), RoundedCornerShape(50))
                )
            }
            Text(
                text = "${fmtMmSs(elapsedSeconds)} / ${fmtMmSs(estimatedDurationSeconds)}",
                fontSize = 10.sp, fontWeight = FontWeight.Bold, color = C.textMuted, fontFamily = NunitoFontFamily
            )
        }
    }
}

/** Thẻ Word Box: hỗ trợ bấm để tự điền vào ô trống đang chọn, tô mờ từ đã dùng, hiện số từ còn lại. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun WordBoxCard(
    wordBoxDisplay: List<String>,
    usedWords: Set<String>,
    shuffleEnabled: Boolean,
    submitted: Boolean,
    onReshuffleWordBox: () -> Unit,
    onWordTap: (String) -> Unit
) {
    val remaining = wordBoxDisplay.count { normAnswer(it) !in usedWords }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x0F6366F1), RoundedCornerShape(16.dp))
            .border(1.5.dp, Color(0x386366F1), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                DashboardIcon(name = "folder", size = 11.dp, color = Color(0xFF6366F1))
                Text(text = "WORD BOX", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF6366F1), letterSpacing = 1.sp, fontFamily = NunitoFontFamily)
                // Hiển thị số từ còn lại chưa dùng.
                Box(
                    modifier = Modifier
                        .background(Color(0x1F6366F1), RoundedCornerShape(50))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(text = "còn $remaining", fontSize = 9.5.sp, fontWeight = FontWeight.Black, color = Color(0xFF4338CA), fontFamily = NunitoFontFamily)
                }
            }
            if (shuffleEnabled && wordBoxDisplay.size > 1) {
                Row(
                    modifier = Modifier
                        .background(Color(0x1A6366F1), RoundedCornerShape(50))
                        .border(1.5.dp, Color(0x596366F1), RoundedCornerShape(50))
                        .clickable(onClick = onReshuffleWordBox)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DashboardIcon(name = "shuffle", size = 9.dp, color = Color(0xFF4338CA))
                    Text(text = "Tráo lại", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF4338CA), fontFamily = NunitoFontFamily)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            wordBoxDisplay.forEach { w ->
                val isUsed = normAnswer(w) in usedWords
                Box(
                    modifier = Modifier
                        .background(if (isUsed) Color(0x0F6366F1) else Color(0x1F6366F1), RoundedCornerShape(50))
                        .border(1.5.dp, if (isUsed) Color(0x2E6366F1) else Color.Transparent, RoundedCornerShape(50))
                        .clickable(enabled = !submitted) { onWordTap(w) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = w, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                        // Từ đã dùng tô mờ hơn để phân biệt trực quan với từ còn lại.
                        color = if (isUsed) Color(0x8A4338CA) else Color(0xFF4338CA),
                        fontFamily = NunitoFontFamily
                    )
                }
            }
        }
    }
}

@androidx.compose.foundation.layout.ExperimentalLayoutApi
@Composable
private fun PassageWithBlanks(
    item: ListeningItem,
    blanks: List<String>,
    submitted: Boolean,
    dark: Boolean,
    activeBlankIndex: Int?,
    onBlankChange: (Int, String) -> Unit,
    onBlankFocus: (Int?) -> Unit
) {
    val C = quizColors(dark)
    val density = LocalDensity.current
    val parts = splitPassage(item.text, item.answers.size)

    val annotatedText = buildAnnotatedString {
        parts.forEach { part ->
            when (part) {
                is PassagePart.TextPart -> {
                    append(part.content.replace(Regex("\\s+"), " "))
                }
                is PassagePart.BlankPart -> {
                    val bi = part.index
                    if (bi >= item.answers.size) {
                        append("___ ")
                    } else {
                        appendInlineContent(id = "blank_$bi", alternateText = "[...]")
                    }
                }
            }
        }
    }

    val inlineContentMap = mutableMapOf<String, InlineTextContent>()
    parts.forEach { part ->
        if (part is PassagePart.BlankPart) {
            val bi = part.index
            if (bi < item.answers.size) {
                val value = blanks.getOrNull(bi) ?: ""
                val isOk = submitted && normAnswer(value) == normAnswer(item.answers[bi])
                val isBad = submitted && !isOk
                val isActive = activeBlankIndex == bi && !submitted
                val color = if (isOk) Color(0xFF059669) else if (isBad) Color(0xFFDC2626) else C.text
                val bg = if (isOk) Color(0x1A059669) else if (isBad) Color(0x14DC2626) else if (isActive) Color(0x1F6366F1) else C.optBg
                val border = if (isOk) Color(0xFF059669) else if (isBad) Color(0xFFDC2626) else if (isActive) Color(0xFF6366F1) else C.optBorder

                val answerLen = item.answers[bi].length
                val calcWidthDp = (answerLen * 8 + 28).coerceIn(44, 160)

                inlineContentMap["blank_$bi"] = InlineTextContent(
                    placeholder = Placeholder(
                        width = with(density) { calcWidthDp.dp.toSp() },
                        // Ô điền cao hơn (26dp -> 32dp) để dễ chạm và có chỗ cho
                        // hiệu ứng focus, theo feedback "ô điền nên cao hơn".
                        height = with(density) { 32.dp.toSp() },
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()
                        val animatedBorder by animateColorAsState(border, label = "blankBorder")
                        val animatedBg by animateColorAsState(bg, label = "blankBg")

                        androidx.compose.runtime.LaunchedEffect(isFocused) {
                            if (isFocused) onBlankFocus(bi)
                        }

                        BasicTextField(
                            value = value,
                            onValueChange = { onBlankChange(bi, it) },
                            enabled = !submitted,
                            singleLine = true,
                            interactionSource = interactionSource,
                            textStyle = TextStyle(
                                fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = color, fontFamily = NunitoFontFamily
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(C.text),
                            modifier = Modifier
                                .fillMaxSize()
                                .semantics { contentDescription = "Ô điền từ số ${bi + 1}" },
                            decorationBox = { inner ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(animatedBg, RoundedCornerShape(8.dp))
                                        // Viền dày hơn khi focus để rõ hiệu ứng lựa chọn.
                                        .border(if (isActive) 2.dp else 1.5.dp, animatedBorder, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (value.isEmpty()) {
                                        // Placeholder "..." dùng textMuted để tương phản tốt hơn trên nền tím.
                                        Text(text = "...", fontSize = 13.5.sp, color = C.textMuted, fontFamily = NunitoFontFamily)
                                    }
                                    inner()
                                }
                            }
                        )
                        if (submitted && isBad) {
                            Text(
                                text = item.answers[bi], fontSize = 9.sp, fontWeight = FontWeight.Black,
                                color = Color(0xFFFCA5A5), fontFamily = NunitoFontFamily,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .offset(y = 14.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    Text(
        text = annotatedText,
        inlineContent = inlineContentMap,
        color = C.text2,
        fontSize = 13.5.sp,
        // Tăng line-height (26sp -> 24sp base * 1.7 hệ số ~ tương đương 23sp cũ
        // quy đổi theo tỉ lệ font); dùng trực tiếp 23.sp -> tỉ lệ so với font 13.5sp
        // xấp xỉ 1.7, đúng khoảng feedback yêu cầu (1.6–1.8).
        lineHeight = 23.sp,
        fontFamily = NunitoFontFamily
    )
}

@Composable
private fun StatementRow(
    index: Int,
    statement: Statement,
    selected: StatementAnswer?,
    submitted: Boolean,
    dark: Boolean,
    onSelect: (StatementAnswer) -> Unit
) {
    val C = quizColors(dark)
    val ok = submitted && selected == statement.answer
    val bad = submitted && selected != statement.answer && selected != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (ok) Color(0x1A16A34A) else if (bad) Color(0x14DC2626) else C.surfaceQ, RoundedCornerShape(16.dp))
            .border(1.5.dp, if (ok) Color(0xFF16A34A) else if (bad) Color(0xFFDC2626) else C.borderQ, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(
                modifier = Modifier.size(22.dp).background(Color(0x2EB07CF0), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = (index + 1).toString(), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFFB07CF0), fontFamily = NunitoFontFamily)
            }
            Text(
                text = statement.text.replace(Regex("</?u>"), ""),
                // Line-height của câu Đúng/Sai/NM cũng tăng nhẹ (22sp -> 24sp) để đồng bộ độ dễ đọc.
                color = C.text2, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                fontFamily = NunitoFontFamily, modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(11.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(StatementAnswer.TRUE, StatementAnswer.FALSE, StatementAnswer.NOT_MENTIONED).forEach { ans ->
                val ui = statementAnswerUi(ans)
                val isSel = selected == ans
                val animatedBg by animateColorAsState(if (isSel) ui.color else ui.bg, label = "stAnsBg")
                val animatedBorder by animateColorAsState(if (isSel) ui.color else ui.border, label = "stAnsBorder")
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(animatedBg, RoundedCornerShape(11.dp))
                        .border(1.5.dp, animatedBorder, RoundedCornerShape(11.dp))
                        .clickable(enabled = !submitted) { onSelect(ans) }
                        // Nút cao hơn (8dp -> 13dp padding dọc, ~50dp tổng chiều cao)
                        // để dễ bấm hơn, theo feedback 48–52dp.
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = ui.label, fontSize = 11.5.sp, fontWeight = FontWeight.Black, color = if (isSel) Color.White else ui.color, fontFamily = NunitoFontFamily)
                }
            }
        }

        if (submitted && bad) {
            Spacer(modifier = Modifier.height(7.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(modifier = Modifier.background(Color(0x26C4B5FD), RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 2.dp)) {
                    Text(text = "Đáp án: ${statementAnswerUi(statement.answer).label}", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFFC084FC), fontFamily = NunitoFontFamily)
                }
            }
        }
    }
}

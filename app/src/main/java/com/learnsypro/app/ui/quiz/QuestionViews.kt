package com.learnsypro.app.ui.quiz

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsypro.app.ui.dashboard.DashboardIcon
import com.learnsypro.app.ui.theme.NunitoFontFamily

private val letters = listOf("A", "B", "C", "D", "E", "F")

data class QuestionTypeInfo(val label: String, val color: Color)

fun questionTypeInfo(type: QuestionType): QuestionTypeInfo = when (type) {
    QuestionType.TRUE_FALSE -> QuestionTypeInfo("Đúng / Sai", Color(0xFFC084FC))
    QuestionType.MULTIPLE -> QuestionTypeInfo("Trắc nghiệm", Color(0xFFF9A8D4))
    QuestionType.MULTI_SELECT -> QuestionTypeInfo("Chọn nhiều", Color(0xFF6EE7B7))
    QuestionType.FILL_BLANK -> QuestionTypeInfo("Điền chỗ trống", Color(0xFFFED7AA))
}

/** ── PassageCard: đoạn tư liệu cho câu true_false (nếu có) ── */
@Composable
fun PassageCard(passage: String, source: String?, dark: Boolean) {
    val C = quizColors(dark)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(C.tfPassageBg, RoundedCornerShape(18.dp))
            .border(1.5.dp, C.borderQ, RoundedCornerShape(18.dp))
            .padding(horizontal = 17.dp, vertical = 15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DashboardIcon(name = "book", size = 12.dp, color = Color(0xFFB07CF0))
            Text(text = "ĐOẠN TƯ LIỆU", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFFB07CF0), letterSpacing = 1.2.sp, fontFamily = NunitoFontFamily)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = richText(passage), fontStyle = FontStyle.Italic, color = C.text2, lineHeight = 24.sp, fontSize = 13.sp, fontFamily = NunitoFontFamily)
        if (!source.isNullOrBlank()) {
            Text(
                text = source,
                fontSize = 11.sp,
                color = C.textMid,
                fontWeight = FontWeight.Bold,
                fontFamily = NunitoFontFamily,
                modifier = Modifier.padding(top = 5.dp)
            )
        }
    }
}

/** ── QuestionTextCard: khung hiển thị đề bài (không dùng cho true_false) ── */
@Composable
fun QuestionTextCard(question: Question, dark: Boolean) {
    val C = quizColors(dark)
    val info = questionTypeInfo(question.type)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(C.surfaceQ, RoundedCornerShape(18.dp))
            .border(1.5.dp, C.borderQ, RoundedCornerShape(18.dp))
            .padding(horizontal = 17.dp, vertical = 15.dp)
    ) {
        Text(
            text = info.label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            color = info.color.copy(alpha = 0.8f),
            letterSpacing = 1.1.sp,
            fontFamily = NunitoFontFamily
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = richText(question.displayText), fontWeight = FontWeight.Bold, color = C.text, lineHeight = 24.sp, fontSize = 14.sp, fontFamily = NunitoFontFamily)
        if (question.type == QuestionType.MULTI_SELECT) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .background(Color(0x1A6EE7B7), RoundedCornerShape(50))
                    .border(1.dp, Color(0x4D6EE7B7), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(text = "Chọn nhiều đáp án", fontSize = 11.sp, color = Color(0xFF5CB893), fontWeight = FontWeight.Black, fontFamily = NunitoFontFamily)
            }
        }
    }
}

/** ── TrueFalseQuestionView ── */
@Composable
fun TrueFalseQuestionView(
    question: Question,
    values: List<Boolean?>,
    submitted: Boolean,
    practiceMode: Boolean,
    dark: Boolean,
    onValueChange: (Int, Boolean?) -> Unit
) {
    val C = quizColors(dark)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        question.items.forEachIndexed { ii, item ->
            val sv = values.getOrNull(ii)
            val rowRevealed = submitted || (practiceMode && sv != null)
            val ok = rowRevealed && sv == item.correct
            val bad = rowRevealed && sv != null && sv != item.correct

            val bgTarget = when {
                ok -> Color(0x1A10B981)
                bad -> Color(0x14EF4444)
                sv == true -> Color(0x126EE7B7)
                sv == false -> Color(0x12FCA5A5)
                else -> C.optBg
            }
            val borderTarget = when {
                ok -> Color(0xFF10B981)
                bad -> Color(0xFFEF4444)
                sv == true -> Color(0xFF6EE7B7)
                sv == false -> Color(0xFFFCA5A5)
                else -> C.optBorder
            }
            val bg by animateColorAsState(bgTarget, label = "tfBg")
            val border by animateColorAsState(borderTarget, label = "tfBorder")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bg, RoundedCornerShape(16.dp))
                    .border(1.5.dp, border, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 13.dp)
            ) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(Color(0x2EB07CF0), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = ('a' + ii).uppercaseChar().toString(), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFFB07CF0), fontFamily = NunitoFontFamily)
                    }
                    Text(text = richText(item.text), color = C.text2, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, fontFamily = NunitoFontFamily, modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(11.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TfChoiceButton(
                        label = "Đúng", selected = sv == true, ok = ok, bad = bad, revealed = rowRevealed,
                        baseColor = Color(0xFF10B981), icon = "check",
                        onClick = { if (!submitted) onValueChange(ii, if (sv == true) null else true) },
                        modifier = Modifier.weight(1f)
                    )
                    TfChoiceButton(
                        label = "Sai", selected = sv == false, ok = ok, bad = bad, revealed = rowRevealed,
                        baseColor = Color(0xFFEF4444), icon = "close",
                        onClick = { if (!submitted) onValueChange(ii, if (sv == false) null else false) },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (rowRevealed) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .background(Color(0x26C4B5FD), RoundedCornerShape(50))
                            .padding(horizontal = 9.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "Đáp án:", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFFC084FC), fontFamily = NunitoFontFamily)
                        DashboardIcon(name = if (item.correct) "check" else "close", size = 10.dp, color = if (item.correct) Color(0xFF10B981) else Color(0xFFEF4444))
                        Text(text = if (item.correct) "Đúng" else "Sai", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFFC084FC), fontFamily = NunitoFontFamily)
                    }
                }
            }
        }
    }
}

@Composable
private fun TfChoiceButton(
    label: String,
    selected: Boolean,
    ok: Boolean,
    bad: Boolean,
    revealed: Boolean,
    baseColor: Color,
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgTarget = when {
        selected && revealed -> if (ok) Color(0x4010B981) else Color(0x33EF4444)
        selected -> baseColor.copy(alpha = 0.18f)
        else -> baseColor.copy(alpha = 0.07f)
    }
    val borderTarget = when {
        selected && revealed -> if (ok) Color(0xFF10B981) else Color(0xFFEF4444)
        selected -> baseColor
        else -> baseColor.copy(alpha = 0.35f)
    }
    val textColorTarget = when {
        selected && revealed -> if (ok) Color(0xFF10B981) else Color(0xFFEF4444)
        selected -> baseColor
        else -> baseColor.copy(alpha = 0.75f)
    }
    val bg by animateColorAsState(bgTarget, label = "tfChoiceBg")
    val border by animateColorAsState(borderTarget, label = "tfChoiceBorder")
    val textColor by animateColorAsState(textColorTarget, label = "tfChoiceText")

    Row(
        modifier = modifier
            .background(bg, RoundedCornerShape(12.dp))
            .border(1.5.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DashboardIcon(name = icon, size = 13.dp, color = textColor)
        Spacer(modifier = Modifier.width(5.dp))
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Black, color = textColor, fontFamily = NunitoFontFamily)
    }
}

/** ── ChoiceQuestionView: dùng chung cho MULTIPLE và MULTI_SELECT ── */
@Composable
fun ChoiceQuestionView(
    question: Question,
    singleSelected: Int?,
    multiSelected: List<Int>,
    submitted: Boolean,
    revealed: Boolean,
    dark: Boolean,
    onSingleChange: (Int?) -> Unit,
    onMultiChange: (List<Int>) -> Unit
) {
    val C = quizColors(dark)
    val isMulti = question.type == QuestionType.MULTI_SELECT

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        question.options.forEachIndexed { i, opt ->
            val isSel = if (isMulti) multiSelected.contains(i) else singleSelected == i
            val isCor = if (isMulti) question.correctIndices.contains(i) else question.correctIndex == i
            val ok = revealed && isSel && isCor
            val bad = revealed && isSel && !isCor
            val missed = revealed && !isSel && isCor

            val bgTarget = when {
                ok -> Color(0x1F10B981)
                bad -> Color(0x1AEF4444)
                missed -> Color(0x1AF59E0B)
                isSel -> C.optSel
                else -> C.optBg
            }
            val borderTarget = when {
                ok -> Color(0xFF10B981)
                bad -> Color(0xFFEF4444)
                missed -> Color(0xFFF59E0B)
                isSel -> Color(0xFFB07CF0)
                else -> C.optBorder
            }
            val bg by animateColorAsState(bgTarget, label = "optionBg")
            val border by animateColorAsState(borderTarget, label = "optionBorder")
            val scale by androidx.compose.animation.core.animateFloatAsState(
                if (isSel && !revealed) 1.02f else 1f, spring(), label = "optionScale"
            )
            val badgeBg = if (isSel) {
                if (revealed) {
                    when {
                        ok -> Color(0xFF10B981)
                        bad -> Color(0xFFEF4444)
                        else -> Color(0xFF8B5CF6)
                    }
                } else Color(0xFF8B5CF6)
            } else Color(0x24B07CF0)
            val badgeText = if (isSel) Color.White else Color(0xFFB07CF0)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .background(bg, RoundedCornerShape(16.dp))
                    .border(1.5.dp, border, RoundedCornerShape(16.dp))
                    .clickable(enabled = !submitted) {
                        if (isMulti) {
                            onMultiChange(if (multiSelected.contains(i)) multiSelected - i else multiSelected + i)
                        } else {
                            onSingleChange(if (singleSelected == i) null else i)
                        }
                    }
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(badgeBg, if (isMulti) RoundedCornerShape(9.dp) else CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = letters.getOrElse(i) { "?" }, fontSize = 12.sp, fontWeight = FontWeight.Black, color = badgeText, fontFamily = NunitoFontFamily)
                }
                Text(text = richText(opt), fontSize = 13.sp, lineHeight = 22.sp, color = C.text2, fontWeight = FontWeight.SemiBold, fontFamily = NunitoFontFamily, modifier = Modifier.weight(1f))
                if (revealed && isCor) DashboardIcon(name = "check", size = 16.dp, color = Color(0xFF10B981))
                if (revealed && bad) DashboardIcon(name = "close", size = 16.dp, color = Color(0xFFEF4444))
            }
        }
    }
}

/** ── FillBlankQuestionView ── */
@Composable
fun FillBlankQuestionView(
    question: Question,
    value: String,
    submitted: Boolean,
    revealed: Boolean,
    dark: Boolean,
    onValueChange: (String) -> Unit,
    onSubmitNext: () -> Unit
) {
    val C = quizColors(dark)
    val isCorrect = revealed && question.answer.trim().equals(value.trim(), ignoreCase = true)

    Column {
        if (question.hint.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                DashboardIcon(name = "sparkle", size = 12.dp, color = Color(0xFFB07CF0))
                Text(text = "Gợi ý: ${question.hint}", fontSize = 12.sp, color = Color(0xFFB07CF0), fontFamily = NunitoFontFamily, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            }
        }

        OutlinedTextField(
            value = value,
            onValueChange = { if (!submitted) onValueChange(it) },
            enabled = !submitted,
            placeholder = { Text("Nhập câu trả lời... (Enter để tiếp theo)", fontFamily = NunitoFontFamily, fontSize = 13.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (value.trim().isNotEmpty()) onSubmitNext() }),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (revealed) (if (isCorrect) Color(0xFF10B981) else Color(0xFFEF4444)) else C.optBorder,
                unfocusedBorderColor = if (revealed) (if (isCorrect) Color(0xFF10B981) else Color(0xFFEF4444)) else C.optBorder,
                focusedContainerColor = if (revealed) (if (isCorrect) Color(0x1A10B981) else Color(0x14EF4444)) else C.optBg,
                unfocusedContainerColor = if (revealed) (if (isCorrect) Color(0x1A10B981) else Color(0x14EF4444)) else C.optBg
            )
        )

        if (revealed) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                DashboardIcon(name = if (isCorrect) "check" else "close", size = 13.dp, color = if (isCorrect) Color(0xFF6EE7B7) else Color(0xFFFCA5A5))
                Text(
                    text = if (isCorrect) "Chính xác!" else "Đáp án đúng: ${question.answer.ifBlank { "—" }}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isCorrect) Color(0xFF6EE7B7) else Color(0xFFFCA5A5),
                    fontFamily = NunitoFontFamily
                )
            }
        }
    }
}

/** ── ExplanationCard: hiện sau khi trả lời/nộp bài ── */
@Composable
fun ExplanationCard(explanation: String, dark: Boolean) {
    val C = quizColors(dark)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 13.dp)
            .background(Color(0x12B07CF0), RoundedCornerShape(16.dp))
            .border(1.5.dp, Color(0x47B07CF0), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            DashboardIcon(name = "sparkle", size = 13.dp, color = Color(0xFFB07CF0))
            Text(text = "GIẢI THÍCH", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFFB07CF0), letterSpacing = 1.sp, fontFamily = NunitoFontFamily)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = explanation, fontSize = 13.sp, color = C.text2, lineHeight = 22.sp, fontFamily = NunitoFontFamily)
    }
}

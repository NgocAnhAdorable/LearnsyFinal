package com.learnsypro.app.admin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsypro.app.admin.ui.theme.LearnsyColors

// Tương đương RichInp trong ui-components.jsx.
// Khác biệt chủ đích: web dùng contentEditable + execCommand (áp style vào DOM
// trực tiếp); Compose không có API này nên áp SpanStyle lên vùng `selection` của
// TextFieldValue rồi merge vào AnnotatedString. Dữ liệu ra/vào vẫn là HTML
// (<b>/<i>/<u>/<s>) qua RichTextMarkup để tương thích 2 chiều với bản web.
@Composable
fun RichInp(
    valueHtml: String,
    onChange: (String) -> Unit,
    placeholder: String,
    colors: LearnsyColors,
    modifier: Modifier = Modifier
) {
    // Không dùng remember(valueHtml) — điều đó khiến fieldValue (và vị trí con trỏ)
    // bị reset lại từ đầu MỖI LẦN gõ chữ: onChange -> onQuestionChange cập nhật q ở
    // parent -> valueHtml mới truyền xuống -> remember re-key -> mất state. Với nhiều
    // RichInp/MiniRichInp trên cùng màn hình (danh sách items), việc reset liên tục
    // này gây recomposition hỏng, làm layout co lại/thiếu field như đã gặp.
    // Thay vào đó: giữ state ổn định qua remember{} thường, và chỉ đồng bộ lại từ
    // valueHtml khi nó thực sự khác với HTML mà field này vừa emit ra (tức là do
    // nguồn bên ngoài đổi — ví dụ chuyển sang câu hỏi khác — chứ không phải echo
    // lại giá trị mình vừa gõ).
    var fieldValue by remember { mutableStateOf(TextFieldValue(RichTextMarkup.htmlToAnnotated(valueHtml))) }
    var lastEmitted by remember { mutableStateOf(valueHtml) }
    LaunchedEffect(valueHtml) {
        if (valueHtml != lastEmitted) {
            fieldValue = TextFieldValue(RichTextMarkup.htmlToAnnotated(valueHtml))
            lastEmitted = valueHtml
        }
    }
    var focused by remember { mutableStateOf(false) }

    fun applyStyle(style: SpanStyle) {
        val sel = fieldValue.selection
        if (sel.collapsed) return
        val newAnnotated = buildStyledText(fieldValue.annotatedString, sel, style)
        fieldValue = fieldValue.copy(annotatedString = newAnnotated)
        val html = RichTextMarkup.annotatedToHtml(newAnnotated)
        lastEmitted = html
        onChange(html)
    }

    Column(modifier = modifier) {
        Row(modifier = Modifier.padding(bottom = 5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FormatButton("B", FontWeight.Black, colors) { applyStyle(SpanStyle(fontWeight = FontWeight.Black)) }
            FormatButton("I", FontWeight.Normal, colors, italic = true) { applyStyle(SpanStyle(fontStyle = FontStyle.Italic)) }
            FormatButton("U", FontWeight.Normal, colors, underline = true) { applyStyle(SpanStyle(textDecoration = TextDecoration.Underline)) }
            FormatButton("S", FontWeight.Normal, colors, strike = true) { applyStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) }
            Text("← bôi đen rồi bấm", fontSize = 10.sp, color = colors.text4, modifier = Modifier.align(Alignment.CenterVertically))
        }
        BasicTextField(
            value = fieldValue,
            onValueChange = { new ->
                fieldValue = new
                val html = RichTextMarkup.annotatedToHtml(new.annotatedString)
                lastEmitted = html
                onChange(html)
            },
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.text),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .border(1.5.dp, if (focused) colors.lav2 else colors.border, RoundedCornerShape(12.dp))
                .padding(10.dp, 10.dp)
                .heightIn(min = 70.dp)
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                if (fieldValue.text.isEmpty()) {
                    Text(placeholder, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.text4)
                }
                inner()
            }
        )
    }
}

// Tương đương MiniRichInp — toolbar chỉ hiện khi focus
@Composable
fun MiniRichInp(
    valueHtml: String,
    onChange: (String) -> Unit,
    placeholder: String,
    colors: LearnsyColors,
    modifier: Modifier = Modifier
) {
    // Cùng lý do như RichInp ở trên — không remember(valueHtml).
    var fieldValue by remember { mutableStateOf(TextFieldValue(RichTextMarkup.htmlToAnnotated(valueHtml))) }
    var lastEmitted by remember { mutableStateOf(valueHtml) }
    LaunchedEffect(valueHtml) {
        if (valueHtml != lastEmitted) {
            fieldValue = TextFieldValue(RichTextMarkup.htmlToAnnotated(valueHtml))
            lastEmitted = valueHtml
        }
    }
    var focused by remember { mutableStateOf(false) }

    fun applyStyle(style: SpanStyle) {
        val sel = fieldValue.selection
        if (sel.collapsed) return
        val newAnnotated = buildStyledText(fieldValue.annotatedString, sel, style)
        fieldValue = fieldValue.copy(annotatedString = newAnnotated)
        val html = RichTextMarkup.annotatedToHtml(newAnnotated)
        lastEmitted = html
        onChange(html)
    }

    Column(modifier = modifier) {
        if (focused) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp, 10.dp, 0.dp, 0.dp))
                    .background(colors.lavL)
                    .border(1.5.dp, colors.lav2, RoundedCornerShape(10.dp, 10.dp, 0.dp, 0.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                MiniFormatButton("B", colors) { applyStyle(SpanStyle(fontWeight = FontWeight.Black)) }
                MiniFormatButton("I", colors) { applyStyle(SpanStyle(fontStyle = FontStyle.Italic)) }
                MiniFormatButton("U", colors) { applyStyle(SpanStyle(textDecoration = TextDecoration.Underline)) }
                MiniFormatButton("S", colors) { applyStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) }
            }
        }
        BasicTextField(
            value = fieldValue,
            onValueChange = { new ->
                fieldValue = new
                val html = RichTextMarkup.annotatedToHtml(new.annotatedString)
                lastEmitted = html
                onChange(html)
            },
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.text),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(if (focused) 0.dp else 10.dp, if (focused) 0.dp else 10.dp, 10.dp, 10.dp))
                .background(colors.surface)
                .border(
                    1.5.dp,
                    if (focused) colors.lav2 else colors.border,
                    RoundedCornerShape(if (focused) 0.dp else 10.dp, if (focused) 0.dp else 10.dp, 10.dp, 10.dp)
                )
                .padding(10.dp, 8.dp)
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                if (fieldValue.text.isEmpty()) {
                    Text(placeholder, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.text4)
                }
                inner()
            }
        )
    }
}

// Tương đương ô nhận định T/F/NM trong listening-panel.jsx: chỉ hỗ trợ gạch chân
// (không B/I/S như RichInp đầy đủ) — khớp toggleUnderlineStatement() + sanitizeStmtHTML()
// chỉ giữ lại thẻ <u> trong dữ liệu lưu.
@Composable
fun UnderlineOnlyInp(
    valueHtml: String,
    onChange: (String) -> Unit,
    placeholder: String,
    colors: LearnsyColors,
    modifier: Modifier = Modifier
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(RichTextMarkup.htmlToAnnotated(valueHtml))) }
    var lastEmitted by remember { mutableStateOf(valueHtml) }
    LaunchedEffect(valueHtml) {
        if (valueHtml != lastEmitted) {
            fieldValue = TextFieldValue(RichTextMarkup.htmlToAnnotated(valueHtml))
            lastEmitted = valueHtml
        }
    }
    var focused by remember { mutableStateOf(false) }

    fun toggleUnderline() {
        val sel = fieldValue.selection
        if (sel.collapsed) return
        val newAnnotated = buildStyledText(fieldValue.annotatedString, sel, SpanStyle(textDecoration = TextDecoration.Underline))
        fieldValue = fieldValue.copy(annotatedString = newAnnotated)
        val html = RichTextMarkup.annotatedToHtml(newAnnotated)
        lastEmitted = html
        onChange(html)
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicTextField(
            value = fieldValue,
            onValueChange = { new ->
                fieldValue = new
                val html = RichTextMarkup.annotatedToHtml(new.annotatedString)
                lastEmitted = html
                onChange(html)
            },
            textStyle = LocalTextStyle.current.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = colors.text),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surface)
                .border(1.5.dp, if (focused) colors.lav2 else colors.border2, RoundedCornerShape(10.dp))
                .padding(10.dp, 8.dp)
                .heightIn(min = 36.dp)
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                if (fieldValue.text.isEmpty()) {
                    Text(placeholder, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = colors.text4)
                }
                inner()
            }
        )
        // Nút gạch chân — bôi đen rồi bấm, khớp title="Bôi đen chữ rồi bấm để gạch chân" bên web
        // FIX: IconButton Material giữ touch-target tối thiểu 48dp dù set size(26.dp),
        // cùng vấn đề với nút ↑↓− ở StatementRow — đổi sang Box+clickable để gọn đúng
        // kích thước hiển thị, khớp thiết kế web thay vì trông to bất thường.
        Box(
            modifier = Modifier.size(26.dp).clip(RoundedCornerShape(8.dp))
                .background(colors.bg2).border(1.5.dp, colors.border2, RoundedCornerShape(8.dp))
                .clickable { toggleUnderline() },
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                Icons.Default.FormatUnderlined, "Gạch chân",
                tint = colors.text3, modifier = Modifier.size(13.dp)
            )
        }
    }
}

// Áp SpanStyle vào 1 vùng selection, merge với các style đã có.
private fun buildStyledText(base: AnnotatedString, range: TextRange, style: SpanStyle): AnnotatedString {
    val start = range.min; val end = range.max
    val alreadyStyled = base.spanStyles.any { it.start <= start && it.end >= end && spanEquals(it.item, style) }

    return buildAnnotatedString {
        append(base.text)
        base.spanStyles.forEach { addStyle(it.item, it.start, it.end) }
        if (!alreadyStyled) {
            addStyle(style, start, end)
        }
        // Ghi chú: Compose AnnotatedString.Builder không hỗ trợ gỡ 1 style cụ thể khỏi
        // vùng đã áp (chỉ có thể chồng thêm) — khác execCommand có thể toggle-off.
        // Người dùng bôi đen + bấm lại sẽ không gỡ style, chỉ có thể chọn vùng mới.
    }
}

private fun spanEquals(a: SpanStyle, b: SpanStyle): Boolean =
    a.fontWeight == b.fontWeight && a.fontStyle == b.fontStyle && a.textDecoration == b.textDecoration

@Composable
private fun FormatButton(
    label: String,
    weight: FontWeight,
    colors: LearnsyColors,
    italic: Boolean = false,
    underline: Boolean = false,
    strike: Boolean = false,
    onClick: () -> Unit
) {
    // FIX: TextButton (M3) áp ButtonDefaults.MinHeight = 48dp bất kể
    // contentPadding đặt bao nhiêu — 4 nút B/I/U/S vì vậy to hơn hẳn chữ cái
    // 12sp bên trong, chiếm quá nhiều diện tích so với "← bôi đen rồi bấm".
    // Tự vẽ bằng Box + clickable để kiểm soát đúng kích thước mong muốn.
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.lavL)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, fontSize = 11.sp, fontWeight = weight, color = colors.lav,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = when {
                underline -> TextDecoration.Underline
                strike -> TextDecoration.LineThrough
                else -> TextDecoration.None
            }
        )
    }
}

@Composable
private fun MiniFormatButton(label: String, colors: LearnsyColors, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.lavL)
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Black, color = colors.lav)
    }
}

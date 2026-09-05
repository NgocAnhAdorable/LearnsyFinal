package com.learnsypro.app.admin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnsypro.app.admin.ui.theme.LearnsyColors

// Tương ứng `inputStyle` trong listening-panel.jsx:
//   width:'100%', padding:'9px 11px', borderRadius:10, border:1.5px, fontSize:13
// OutlinedTextField mặc định của Material3 áp min-height ~56dp + đệm cho
// label nổi, khiến mọi ô input trong form Listening trông "to, lệch tỉ lệ"
// so với thiết kế web (đã thấy rõ trong ảnh chụp thật trên máy). Component
// này thay thế OutlinedTextField ở những chỗ cần khớp đúng kích thước gọn
// của bản gốc — dùng BasicTextField để tự kiểm soát padding/height, không
// bị Material3 áp thêm khoảng đệm ẩn.
@Composable
fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    colors: LearnsyColors,
    accentColor: Color? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp
) {
    var focused by remember { mutableStateOf(false) }
    val active = accentColor ?: colors.lav
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface)
            .border(1.5.dp, if (focused) active else colors.border2, RoundedCornerShape(10.dp))
            .padding(horizontal = 11.dp, vertical = 9.dp)
    ) {
        if (value.isEmpty()) {
            Text(placeholder, fontSize = fontSize, color = colors.text4)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            textStyle = TextStyle(fontSize = fontSize, color = colors.text),
            singleLine = singleLine,
            minLines = minLines,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(active)
        )
    }
}

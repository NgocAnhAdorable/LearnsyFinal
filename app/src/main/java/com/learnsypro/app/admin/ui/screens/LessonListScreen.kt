package com.learnsypro.app.admin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.learnsypro.app.admin.data.Lesson
import com.learnsypro.app.admin.data.LessonFilter
import com.learnsypro.app.admin.data.SortBy
import com.learnsypro.app.admin.ui.components.lavenderBorder
import com.learnsypro.app.admin.data.CardBlur
import com.learnsypro.app.admin.ui.LessonListViewModel
import com.learnsypro.app.admin.ui.ToastCenter
import com.learnsypro.app.admin.ui.theme.LearnsyColors

// Tương đương phần list chính của app.jsx, ghép LessonListViewModel với
// LessonEditorScreen (soạn bài) đã port trước đó.
@Composable
fun LessonListScreen(
    colors: LearnsyColors,
    dark: Boolean,
    refreshKey: Any = Unit,
    onOpenLesson: (String) -> Unit,
    listVm: LessonListViewModel = viewModel(
        factory = viewModelFactory {
            initializer { LessonListViewModel(savedStateHandle = createSavedStateHandle()) }
        }
    )
) {
    val listState by listVm.uiState.collectAsState()
    var deleteTarget by remember { mutableStateOf<Lesson?>(null) }
    // Tương đương confirm_({title:'Tạo bài tập mới?',...}) trong app.jsx
    var showCreateConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) { listVm.loadIfNeeded(refreshKey) }
    // Trước đây listState.error được ghi nhưng không nơi nào đọc — lỗi tải bài
    // học (vd. sai Supabase URL/key, RLS chặn, mất mạng) bị nuốt câm, màn hình
    // chỉ hiện "Chưa có bài tập nào" giống hệt trường hợp thật sự trống dữ liệu.
    LaunchedEffect(listState.error) {
        listState.error?.let { msg -> ToastCenter.show("Lỗi tải bài học: $msg", "❌", Color(0xFFEF4444)) }
    }

    if (showCreateConfirm) {
        com.learnsypro.app.admin.ui.components.ConfirmDialog(
            title = "Tạo bài tập mới?",
            message = "Bài tập mới sẽ được tạo và lưu vào Supabase.",
            onDismiss = { showCreateConfirm = false },
            onConfirm = {
                listVm.createLesson(
                    onCreated = { id ->
                        onOpenLesson(id)
                        ToastCenter.show("Đã tạo bài mới! Nhập tên bài tập nhé", "✨", Color(0xFF10B981))
                    },
                    onError = { msg -> ToastCenter.show(msg, "❌", Color(0xFFEF4444)) }
                )
            },
            colors = colors, dark = dark,
            confirmLabel = "Tạo ngay", confirmColor = colors.lav,
            iconType = com.learnsypro.app.admin.ui.components.ConfirmIconType.ADD
        )
    }

    deleteTarget?.let { lesson ->
        com.learnsypro.app.admin.ui.components.ConfirmDialog(
            title = "Xoá bài học?",
            message = lesson.title.ifBlank { "(Chưa đặt tên)" },
            onDismiss = { deleteTarget = null },
            onConfirm = {
                listVm.deleteLesson(
                    lesson.id,
                    onSuccess = { ToastCenter.show("Đã xoá bài học", "🗑️", Color(0xFFEF4444)) },
                    onError = { msg -> ToastCenter.show(msg, "❌", Color(0xFFEF4444)) }
                )
            },
            colors = colors, dark = dark,
            confirmLabel = "Xoá", iconType = com.learnsypro.app.admin.ui.components.ConfirmIconType.DELETE
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp, 14.dp, 12.dp, 100.dp)) {
        // Tương đương khối "Bài học" + nút thêm trong app.jsx (dòng ~1012-1032):
        // tiêu đề lớn "Bài học" + phụ đề tổng số bài/câu hỏi, viền dưới phân cách.
        Column(
            modifier = Modifier.fillMaxWidth()
                .drawBehind {
                    drawLine(
                        color = colors.border,
                        start = androidx.compose.ui.geometry.Offset(0f, size.height),
                        end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }.padding(bottom = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Bài học", fontSize = 18.sp, fontWeight = FontWeight.Black, color = colors.text)
                    val totalQ = listState.lessons.sumOf { it.questions.size }
                    Text(
                        "${listState.lessons.size} bài · $totalQ câu hỏi",
                        fontSize = 12.sp, color = colors.text3,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (listState.lessons.isNotEmpty()) {
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(colors.grad)
                            .clickable { showCreateConfirm = true }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Thêm bài mới", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White)
                        }
                    }
                }
            }
        }

        if (listState.lessons.isNotEmpty()) {
            // FIX: OutlinedTextField mặc định M3 khá cao (~56dp min-height,
            // không cho tùy chỉnh contentPadding trực tiếp) và trước đây icon
            // kính lúp là emoji "🔍" nhúng thẳng trong text placeholder — phụ
            // thuộc font hệ thống có hỗ trợ emoji hay không, hiển thị không
            // đồng nhất giữa các máy/theme. Đổi sang BasicTextField tự vẽ
            // (cùng pattern đã dùng ở StudentManagerScreen/ListeningManagerScreen)
            // — nhỏ gọn hơn hẳn nhờ padding tường minh, và Icon vector Material
            // (Icons.Default.Search) luôn nét, luôn đúng màu theo theme.
            var searchFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.surface)
                    .lavenderBorder(colors, RoundedCornerShape(999.dp), active = searchFocused)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = listState.searchQuery,
                    onValueChange = listVm::setSearchQuery,
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = colors.text),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.lav),
                    modifier = Modifier.fillMaxWidth()
                        .onFocusChanged { searchFocused = it.isFocused },
                    decorationBox = { inner ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Search, "Tìm kiếm", tint = colors.text4, modifier = Modifier.size(15.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (listState.searchQuery.isEmpty()) {
                                    Text("Tìm kiếm bài tập...", fontSize = 13.sp, color = colors.text4)
                                }
                                inner()
                            }
                            if (listState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { listVm.setSearchQuery("") }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.Close, "Xoá tìm kiếm", tint = colors.text3, modifier = Modifier.size(13.dp))
                                }
                            }
                        }
                    }
                )
            }
            Spacer(Modifier.height(10.dp))

            // Tương đương hàng "Bộ lọc + sắp xếp" gộp trong app.jsx (dòng ~1044-1113):
            // filter segmented (flex:1) cạnh dropdown sort+blur (flex-shrink:0), CÙNG 1 hàng.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(999.dp))
                        .background(colors.bg).border(1.5.dp, colors.border, RoundedCornerShape(999.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val allCount = listState.lessons.size
                    val enCount = listState.lessons.count { it.subject == "Tiếng Anh" }
                    val otherCount = allCount - enCount
                    listOf(
                        Triple(LessonFilter.ALL, "Tất cả", allCount),
                        Triple(LessonFilter.ENGLISH, "Tiếng Anh", enCount),
                        Triple(LessonFilter.OTHER, "Các môn", otherCount)
                    ).forEach { (f, label, count) ->
                        val active = listState.filter == f
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(999.dp))
                                .then(if (active) Modifier.background(colors.grad) else Modifier)
                                .clickable { listVm.setFilter(f) }
                                .padding(horizontal = 4.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "$label · $count", fontSize = 11.5.sp, fontWeight = FontWeight.Black,
                                color = if (active) Color.White else colors.text3,
                                maxLines = 1
                            )
                        }
                    }
                }

                SortAndBlurDropdown(
                    colors = colors,
                    sortBy = listState.sortBy, onSortSelect = listVm::setSortBy,
                    cardBlur = listState.cardBlur, onBlurSelect = listVm::setCardBlur
                )
            }
        }

        if (listState.loading) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.rose)
            }
        } else {
            val display by remember(
                listState.lessons, listState.filter, listState.searchQuery, listState.sortBy
            ) {
                derivedStateOf { listVm.filteredSortedLessons() }
            }
            if (display.isEmpty() && listState.lessons.isEmpty()) {
                // Tương đương empty state trong app.jsx (dòng ~1117-1130):
                // dashed border, icon hoa trong vòng tròn gradient, tiêu đề + phụ đề, nút CTA riêng.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(colors.surface)
                        .border(1.5.dp, colors.border2, RoundedCornerShape(24.dp))
                        .padding(vertical = 40.dp, horizontal = 20.dp)
                ) {
                    Box(
                        modifier = Modifier.size(76.dp).clip(RoundedCornerShape(999.dp))
                            .background(colors.gradSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        com.learnsypro.app.admin.ui.components.FlowerIcon(size = 40, color = Color(0xFFFFB7C9))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Chưa có bài tập nào", fontSize = 16.sp, fontWeight = FontWeight.Black, color = colors.text2)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Bấm nút bên dưới để tạo bài tập\nđầu tiên cho lớp của bạn nhé!",
                        fontSize = 12.5.sp, color = colors.text3, lineHeight = 20.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(colors.grad)
                            .clickable { showCreateConfirm = true }
                            .padding(horizontal = 26.dp, vertical = 11.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Thêm bài đầu tiên", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White)
                        }
                    }
                }
            } else if (display.isEmpty()) {
                // Tương đương thông báo rỗng theo filter trong app.jsx
                val msg = when (listState.filter) {
                    LessonFilter.ENGLISH -> "Chưa có bài tập Tiếng Anh nào"
                    LessonFilter.OTHER -> "Chưa có bài tập các môn nào"
                    LessonFilter.ALL -> "Không tìm thấy bài học phù hợp"
                }
                Text(msg, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.text3, modifier = Modifier.padding(vertical = 24.dp))
            } else {
                LazyColumn(
                    state = listVm.listState,
                    modifier = Modifier.weight(1f),
                    // contentPadding vertical nhỏ để glow (vẽ tràn ra ngoài bounds
                    // của card, offset âm) của card đầu/cuối không bị Lazy Column
                    // cắt cụt thành vệt sáng tím "cứng" ngay mép trên danh sách
                    contentPadding = PaddingValues(top = 6.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    items(display, key = { it.id }) { lesson ->
                        LessonCard(
                            lesson = lesson, colors = colors, dark = dark, cardBlur = listState.cardBlur,
                            onOpen = { onOpenLesson(lesson.id) },
                            onDuplicate = {
                                listVm.duplicateLesson(lesson) { ok ->
                                    if (ok) ToastCenter.show("Đã sao chép bài tập!", "➕", Color(0xFF10B981))
                                    else ToastCenter.show("Không sao chép được bài tập", "❌", Color(0xFFEF4444))
                                }
                            },
                            onDelete = { deleteTarget = lesson }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SortAndBlurDropdown(
    colors: LearnsyColors,
    sortBy: SortBy, onSortSelect: (SortBy) -> Unit,
    cardBlur: CardBlur, onBlurSelect: (CardBlur) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val sortOptions = listOf(
        SortBy.NEWEST to "Mới nhất", SortBy.OLDEST to "Cũ nhất",
        SortBy.NAME to "Tên A-Z", SortBy.COUNT to "Nhiều câu nhất"
    )
    val sortLabel = sortOptions.find { it.first == sortBy }?.second ?: "Mới nhất"
    val blurOptions = listOf(CardBlur.OFF to "Tắt", CardBlur.FIFTY to "50%", CardBlur.EIGHTY_FIVE to "85%")

    Box {
        Row(
            modifier = Modifier.clip(RoundedCornerShape(999.dp))
                .background(if (expanded) colors.lavL else colors.bg2)
                .border(1.5.dp, if (expanded) colors.lav else colors.border, RoundedCornerShape(999.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Sort, null, tint = if (expanded) colors.lav else colors.text3, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            Text(sortLabel, fontSize = 11.5.sp, fontWeight = FontWeight.Black, color = if (expanded) colors.lav else colors.text3)
            Spacer(Modifier.width(3.dp))
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null, tint = if (expanded) colors.lav else colors.text3, modifier = Modifier.size(14.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Text(
                "SẮP XẾP THEO", fontSize = 10.sp, fontWeight = FontWeight.Black, color = colors.text4,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
            sortOptions.forEach { (value, text) ->
                val active = sortBy == value
                DropdownMenuItem(
                    text = { Text(text, fontWeight = if (active) FontWeight.Black else FontWeight.Bold, color = if (active) colors.lav else colors.text2) },
                    trailingIcon = { if (active) Icon(Icons.Default.Check, null, tint = colors.lav, modifier = Modifier.size(14.dp)) },
                    onClick = { onSortSelect(value) }
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = colors.border)
            Text(
                "ĐỘ MỜ THẺ", fontSize = 10.sp, fontWeight = FontWeight.Black, color = colors.text4,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                blurOptions.forEach { (value, text) ->
                    val active = cardBlur == value
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                            .background(if (active) colors.lavL else Color.Transparent)
                            .border(1.5.dp, if (active) colors.lav else colors.border, RoundedCornerShape(8.dp))
                            .clickable { onBlurSelect(value) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Black, color = if (active) colors.lav else colors.text3)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniBadge(text: String, color: Color, bg: Color) {
    Box(modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(bg).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text(text, fontSize = 9.sp, fontWeight = FontWeight.Black, color = color)
    }
}

@Composable
private fun LessonCard(
    lesson: Lesson, colors: LearnsyColors, dark: Boolean, cardBlur: CardBlur,
    onOpen: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit
) {
    val blurRadius = when (cardBlur) {
        CardBlur.OFF -> 0.dp
        CardBlur.FIFTY -> 3.dp
        CardBlur.EIGHTY_FIVE -> 7.dp
    }
    var menuOpen by remember { mutableStateOf(false) }
    // Glow tím quanh card — light mode khớp box-shadow web gốc
    // '0 4px 18px rgba(168,85,247,0.10)'. Dark mode trước đây dùng glow ĐEN
    // (rgba(0,0,0,0.28)) — gần như vô hình trên nền đã tối sẵn, khiến card
    // trông "chìm" thay vì nổi nhẹ như bản light. Đổi sang tím sáng cùng
    // hue, alpha cao hơn để card ở dark mode cũng có viền sáng tím rõ ràng.
    val glowColor = if (dark) Color(0xFFC084FC).copy(alpha = 0.16f) else Color(0xFFA855F7).copy(alpha = 0.08f)
    Row(
        modifier = Modifier.fillMaxWidth()
            .drawBehind {
                val radiusPx = 24.dp.toPx()
                val layers = 3
                for (i in layers downTo 1) {
                    val spread = (i * 2).dp.toPx()
                    val alpha = glowColor.alpha * (1f - i.toFloat() / layers) * 0.9f
                    drawRoundRect(
                        color = glowColor.copy(alpha = alpha),
                        topLeft = androidx.compose.ui.geometry.Offset(-spread, -spread),
                        size = androidx.compose.ui.geometry.Size(size.width + spread * 2, size.height + spread * 2),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radiusPx + spread, radiusPx + spread)
                    )
                }
            }
            .clip(RoundedCornerShape(24.dp)).background(colors.surface)
            .clickable(onClick = onOpen)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Icon — đúng 2 tông web: gradient pastel hồng-tím (light) / tím mờ trên nền tối (dark)
        Box(
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).let { base ->
                if (dark) base.background(Color(0xFFC084FC).copy(alpha = 0.14f))
                else base.background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFFDEBF3), Color(0xFFF3ECFC))))
            },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Description, null,
                tint = if (dark) Color(0xFFD8B4FE) else Color(0xFFC4A0F0),
                modifier = Modifier.size(24.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f).let {
                if (blurRadius > 0.dp) it.blur(blurRadius) else it
            }
        ) {
            Text(lesson.title.ifBlank { "Chưa đặt tên" }, fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold, color = colors.text, maxLines = 1)
            Text(
                "${lesson.subject.ifBlank { "Tiếng Anh" }} · ${lesson.questions.size} câu",
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.text3,
                modifier = Modifier.padding(top = 4.dp)
            )
            val tfCount = lesson.questions.count { it is com.learnsypro.app.admin.data.Question.TrueFalse }
            val tnCount = lesson.questions.count { it is com.learnsypro.app.admin.data.Question.Multiple || it is com.learnsypro.app.admin.data.Question.MultiSelect }
            val dtCount = lesson.questions.count { it is com.learnsypro.app.admin.data.Question.FillBlank }
            if (lesson.password.isNotBlank() || tfCount > 0 || tnCount > 0 || dtCount > 0) {
                Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (lesson.password.isNotBlank()) MiniBadge("Mật khẩu", Color(0xFF8B93F0), Color(0xFFEEF2FF))
                    if (tfCount > 0) MiniBadge("$tfCount ĐS", Color(0xFFC4A0F0), Color(0xFFF3ECFC))
                    if (tnCount > 0) MiniBadge("$tnCount TN", Color(0xFF6EC9A0), Color(0xFFEAFAF3))
                    if (dtCount > 0) MiniBadge("$dtCount ĐT", Color(0xFFF0A870), Color(0xFFFFF3E8))
                }
            }
        }
        // Actions — nút ⋮ mở menu Sao chép/Xoá, nút › mở bài (giữ 2 nút riêng).
        // FIX: IconButton (M3) tự áp minimumInteractiveComponentSize() = 48dp
        // BẤT KỂ Modifier.size(22.dp) truyền vào — đây là ràng buộc cứng của
        // M3 để đảm bảo vùng chạm tối thiểu cho accessibility, khiến vòng
        // tròn viền tím thực tế to hơn hẳn icon 12dp bên trong (đúng như ảnh:
        // viền cách xa icon một khoảng lớn). Thay bằng Box + clickable tự vẽ
        // để kích thước hiển thị đúng bằng 22dp như khai báo, không bị M3 nới
        // rộng ngầm.
        Box {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (menuOpen) colors.lavL else Color.Transparent)
                    .border(1.dp, colors.lav.copy(alpha = if (menuOpen) 0f else 0.25f), RoundedCornerShape(999.dp))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = androidx.compose.foundation.LocalIndication.current
                    ) { menuOpen = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MoreVert, "Tuỳ chọn", tint = if (menuOpen) colors.lav else colors.text3, modifier = Modifier.size(12.dp))
            }
            // FIX: DropdownMenu (Material3) tự bọc content trong 1 Surface nội
            // bộ có shape mặc định gần vuông (MaterialTheme.shapes.extraSmall),
            // và modifier truyền vào chỉ áp dụng NGOÀI Surface đó — nên clip
            // bo góc 16dp trước đây không có tác dụng lên khối nội dung thật,
            // dù glow vẽ ngoài vẫn hiện đúng (khớp đúng ảnh: viền tròn nhưng
            // khối bên trong vuông cứng). Dùng Popup gốc tự viết để toàn quyền
            // kiểm soát Surface/shape/glow, không phụ thuộc internal của
            // DropdownMenu.
            if (menuOpen) {
                val menuGlow = colors.lav.copy(alpha = 0.22f)
                androidx.compose.ui.window.Popup(
                    alignment = Alignment.TopEnd,
                    offset = androidx.compose.ui.unit.IntOffset(0, 40),
                    onDismissRequest = { menuOpen = false },
                    properties = androidx.compose.ui.window.PopupProperties(focusable = true)
                ) {
                    Column(
                        modifier = Modifier
                            .drawBehind {
                                val radiusPx = 16.dp.toPx()
                                val layers = 4
                                for (i in layers downTo 1) {
                                    val spread = (i * 3).dp.toPx()
                                    val alpha = menuGlow.alpha * (1f - i.toFloat() / layers) * 0.9f
                                    drawRoundRect(
                                        color = menuGlow.copy(alpha = alpha),
                                        topLeft = androidx.compose.ui.geometry.Offset(-spread, -spread),
                                        size = androidx.compose.ui.geometry.Size(size.width + spread * 2, size.height + spread * 2),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radiusPx + spread, radiusPx + spread)
                                    )
                                }
                            }
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surface)
                            .width(IntrinsicSize.Max)
                            .padding(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .clickable { menuOpen = false; onDuplicate() }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, null, tint = colors.lav, modifier = Modifier.size(15.dp))
                            Text("Sao chép", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = colors.text2)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .clickable { menuOpen = false; onDelete() }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(15.dp))
                            Text("Xoá", fontWeight = FontWeight.Black, fontSize = 12.5.sp, color = Color(0xFFEF4444))
                        }
                    }
                }
            }
        }
        // Mũi tên điều hướng — nền tròn nhạt, khớp app.jsx
        Box(
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(999.dp)).background(colors.lavPale),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colors.lav2, modifier = Modifier.size(18.dp))
        }
    }
}

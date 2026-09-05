package com.learnsypro.app.admin.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.SavedStateHandle

// FIX "admin không giữ nguyên vị trí khi thoát ra vào lại": các ViewModel danh
// sách (LessonListViewModel, ListeningListViewModel, VocabularyViewModel,
// StudentListViewModel) đều hoist `val listState = LazyListState()` ra ngoài
// AnimatedContent để sống qua việc chuyển tab — nhưng LazyListState() khởi tạo
// thô này KHÔNG lưu vào SavedStateHandle, nên khi hệ thống kill tiến trình nền
// (RAM thấp) rồi người dùng mở lại app, danh sách (lessons/items/students) đã
// được khôi phục đúng từ cache JSON trong SavedStateHandle, nhưng vị trí cuộn
// luôn bị reset về đầu — tạo cảm giác "vào lại bị mất vị trí" dù dữ liệu vẫn còn.
//
// restoredLazyListState() đọc index/offset đã lưu (nếu có) để khởi tạo
// LazyListState ngay từ đúng vị trí cũ, rồi tự lưu lại index/offset hiện tại
// xuống SavedStateHandle mỗi khi ViewModel bị onCleared() hoặc app bị kill
// (thông qua setUpSaving() gọi 1 lần trong init{} — dùng snapshotFlow ở phía
// gọi vì đây là file thuần Kotlin, không phải @Composable).
fun SavedStateHandle?.restoredLazyListState(key: String): LazyListState {
    val savedIndex = this?.get<Int>("${key}_index") ?: 0
    val savedOffset = this?.get<Int>("${key}_offset") ?: 0
    return LazyListState(firstVisibleItemIndex = savedIndex, firstVisibleItemScrollOffset = savedOffset)
}

fun SavedStateHandle.saveLazyListState(key: String, state: LazyListState) {
    set("${key}_index", state.firstVisibleItemIndex)
    set("${key}_offset", state.firstVisibleItemScrollOffset)
}

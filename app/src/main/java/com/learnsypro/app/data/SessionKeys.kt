package com.learnsypro.app.data

import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/**
 * ── SessionKeys ──
 * Trước đây `sessionDataStore`/`USERNAME_KEY`/`STUDENT_ID_KEY` là `private`
 * bên trong LearnsyNavHost.kt — chỉ NavHost (màn Student) đọc được session
 * đang đăng nhập. Tách ra file dùng chung này để bất kỳ module nào khác
 * (File Manager, Admin) cũng đọc được ĐÚNG CÙNG 1 tài khoản học sinh đang
 * đăng nhập, thay vì phải tự có 1 khái niệm "tài khoản" riêng của nó.
 *
 * Đây là nguyên nhân của bug "ảnh nền lẫn giữa các tài khoản trong màn Quản
 * lý tệp": SharedBackgroundViewModel dùng SHARED_BG_KEY cố định vì bản thân
 * module File Manager/Admin không có cách nào biết học sinh nào đang đăng
 * nhập — giờ đọc thẳng từ đây.
 */
val android.content.Context.sessionDataStore by preferencesDataStore(name = "learnsy_session")
val SESSION_USERNAME_KEY = stringPreferencesKey("session_username")
val SESSION_STUDENT_ID_KEY = stringPreferencesKey("session_student_id")

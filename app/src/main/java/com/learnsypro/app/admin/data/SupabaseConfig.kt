package com.learnsypro.app.admin.data

import com.learnsypro.app.BuildConfig
import com.learnsypro.app.data.SupabaseClientProvider

/**
 * ── SupabaseConfig (module Admin) ──
 * TRƯỚC KHI GỘP: Admin tự tạo 1 Supabase client riêng (createSupabaseClient
 * với Postgrest/Auth/Functions/Storage + JSON ignoreUnknownKeys) song song
 * với client riêng của app Student.
 *
 * SAU KHI GỘP: Admin và Student giờ chạy trong CÙNG 1 tiến trình app, nên
 * dùng CHUNG 1 Supabase client duy nhất (SupabaseClientProvider.client) —
 * tránh 2 kết nối/2 session GoTrue độc lập cùng lúc (dễ gây lệch trạng thái
 * đăng nhập, tốn thêm socket/Realtime channel không cần thiết). Toàn bộ
 * cấu hình JSON ignoreUnknownKeys + module Functions cần cho Admin đã được
 * chuyển lên SupabaseClientProvider dùng chung, xem file đó để biết lý do
 * từng phần.
 *
 * object này CHỈ còn giữ vai trò re-export để KHÔNG phải sửa lại "SupabaseConfig.client"
 * ở toàn bộ ~15 file ViewModel/Repository khác của Admin — nếu sau này dọn dẹp thêm,
 * có thể thay trực tiếp bằng SupabaseClientProvider.client và xoá file này.
 */
object SupabaseConfig {
    val client get() = SupabaseClientProvider.client

    // Tương đương ADMIN_API_KEY web — dùng cho header x-admin-secret
    // khi gọi Edge Function student-set-password.
    const val ADMIN_API_KEY: String = BuildConfig.ADMIN_API_KEY
}

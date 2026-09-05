package com.learnsypro.app.data

import com.learnsypro.app.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.functions.Functions
import kotlinx.serialization.json.Json

/**
 * Khởi tạo Supabase client cho app Android.
 *
 * KHÁC VỚI BẢN WEB (index.html gốc):
 * Bản web dùng /api/config trả về JSON mã hóa AES-256-GCM, rồi giải mã bằng
 * __ENC_KEY hardcode ngay trong client JS — cách này KHÔNG bảo mật thật sự
 * vì key vẫn nằm trong source gửi cho browser, ai xem source cũng lấy được.
 *
 * Với Android, ta dùng cách chuẩn của Supabase: SUPA_URL và anon SUPA_KEY
 * (public anon key) được thiết kế để đặt an toàn ở phía client — bảo mật
 * thực sự nằm ở Row Level Security (RLS) policies trên Supabase, không phải
 * ở việc giấu key. Không cần bước mã hóa/giải mã nào thêm.
 *
 * Thiết lập:
 * 1. Thêm vào local.properties (KHÔNG commit file này lên git):
 *      SUPA_URL=https://xxxx.supabase.co
 *      SUPA_KEY=eyJhbGciOi...
 *      ADMIN_API_KEY=xxxx (chỉ cần cho module Admin — header x-admin-secret)
 * 2. Trong app/build.gradle.kts, đọc local.properties và đưa vào BuildConfig
 *    (xem hướng dẫn cuối file).
 *
 * ═══ Functions + JSON ignoreUnknownKeys — thêm khi gộp module Admin ═══
 * install(Functions): trước đây chỉ module Admin cần (Edge Function
 * student-set-password), Student app không gọi Edge Function nào — thêm
 * vào đây vô hại, không ảnh hưởng phần Student.
 *
 * defaultSerializer với ignoreUnknownKeys/isLenient: nguyên bản từ
 * SupabaseConfig.kt của Admin — dữ liệu cũ trên Supabase (lưu từ bản web
 * trước đây) còn field thừa (vd. "timeLimit" bên cạnh "timerLimit" hiện
 * tại) khiến Postgrest fail-fast nếu không bật cờ này. Áp dụng luôn cho
 * client dùng chung để CẢ Admin lẫn Student đều chịu được field lạ, thay vì
 * chỉ vá riêng phía Admin như bản gốc — không có lý do gì để Student kém
 * chịu lỗi hơn Admin trên cùng 1 nguồn dữ liệu.
 */
object SupabaseClientProvider {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    val client by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPA_URL,
            supabaseKey = BuildConfig.SUPA_KEY
        ) {
            defaultSerializer = io.github.jan.supabase.serializer.KotlinXSerializer(json)
            install(Postgrest)
            install(Auth)
            install(Realtime)
            install(Storage)
            install(Functions)
        }
    }
}

/*
═══ Hướng dẫn thêm vào app/build.gradle.kts để BuildConfig.SUPA_URL / SUPA_KEY hoạt động ═══

import java.util.Properties

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    defaultConfig {
        buildConfigField("String", "SUPA_URL", "\"${localProps.getProperty("SUPA_URL", "")}\"")
        buildConfigField("String", "SUPA_KEY", "\"${localProps.getProperty("SUPA_KEY", "")}\"")
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(platform("io.github.jan-tennert.supabase:bom:2.6.0"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:gotrue-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.ktor:ktor-client-android:2.3.11")
}
*/

import java.util.Properties

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

plugins {
    id("com.android.application")
    // gradle.properties đang đặt android.builtInKotlin=false — cố ý giữ
    // plugin Kotlin cũ thay vì Kotlin built-in của AGP 9, nên PHẢI apply
    // plugin này (bỏ nó là nguyên nhân lỗi "Unresolved reference:
    // compilerOptions" ở bản build trước).
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.learnsypro.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.learnsypro.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 24
        versionName = "24.5"

        // AppAuth (net.openid.appauth.RedirectUriReceiverActivity trong Manifest) cần scheme này
        // để thay vào placeholder ${appAuthRedirectScheme} — phải khớp với scheme đã đăng ký ở
        // Dropbox/Box console và redirectUri trong OAuthManager.kt (com.learnsypro.app://callback).
        manifestPlaceholders["appAuthRedirectScheme"] = "com.learnsypro.app"

        // ═══ Đọc từ local.properties — KHÔNG commit file đó lên git ═══
        buildConfigField("String", "SUPA_URL", "\"${localProps.getProperty("SUPA_URL", "")}\"")
        buildConfigField("String", "SUPA_KEY", "\"${localProps.getProperty("SUPA_KEY", "")}\"")
        buildConfigField("String", "UPSTASH_URL", "\"${localProps.getProperty("UPSTASH_URL", "")}\"")
        buildConfigField("String", "UPSTASH_TOKEN", "\"${localProps.getProperty("UPSTASH_TOKEN", "")}\"")
        // ═══ Module Admin (trước đây là app Learnsy Standard Admin độc lập) ═══
        // Header x-admin-secret khi gọi Edge Function student-set-password.
        buildConfigField("String", "ADMIN_API_KEY", "\"${localProps.getProperty("ADMIN_API_KEY", "")}\"")
        buildConfigField("String", "DROPBOX_APP_KEY", "\"${localProps.getProperty("DROPBOX_APP_KEY", "")}\"")
        buildConfigField("String", "BOX_CLIENT_ID", "\"${localProps.getProperty("BOX_CLIENT_ID", "")}\"")
        buildConfigField("String", "BOX_CLIENT_SECRET", "\"${localProps.getProperty("BOX_CLIENT_SECRET", "")}\"")
    }

    signingConfigs {
        // ═══ Ký release thật ═══
        // Ưu tiên đọc từ local.properties (build tay trên máy/Termux); nếu
        // không có thì đọc biến môi trường (CI — GitHub Actions sẽ set các
        // biến này từ Secrets, xem .github/workflows/build-apk.yml).
        // KHÔNG hardcode path/password ở đây, và KHÔNG commit keystore hay
        // local.properties lên git.
        create("release") {
            val storeFilePath = localProps.getProperty("RELEASE_STORE_FILE")
                ?: System.getenv("RELEASE_STORE_FILE")
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
                    ?: System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
                    ?: System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
                    ?: System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 shrink + obfuscate — giảm size APK, class load nhanh hơn khi
            // cold-start (rõ nhất trên CPU yếu / storage chậm). Xem cảnh báo
            // ⚠️ ở đầu proguard-rules.pro trước khi phát hành.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Dùng keystore thật nếu đã cấu hình (RELEASE_STORE_FILE có giá
            // trị); chưa cấu hình thì tạm rớt về debug keystore để vẫn build
            // + cài thử được, không chặn CI của người mới setup.
            signingConfig = if (signingConfigs.getByName("release").storeFile != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        // Sinh ActivityXxxBinding/ItemXxxBinding cho các Activity XML-view của module Quản lý tệp
        // (AppLockActivity, ArchivePreviewActivity, ...) — chưa bật nên các binding class không tồn tại.
        viewBinding = true
    }

    // POI kéo theo nhiều jar có cùng file META-INF (LICENSE, NOTICE, module-info...)
    // → không loại trừ sẽ lỗi "More than one file was found with OS independent path"
    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "META-INF/*.SF",
                "META-INF/*.DSA",
                "META-INF/*.RSA",
                "module-info.class",
                "**/module-info.class"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Từ Kotlin 2.0+, Compose Compiler tách khỏi Compose BOM và được cấu hình
// qua Compose Compiler Gradle Plugin (áp dụng ở plugins{} phía trên) thay
// cho composeOptions{kotlinCompilerExtensionVersion} kiểu cũ. Strong
// skipping mode (trước đây bật thủ công qua freeCompilerArgs) giờ đã BẬT
// MẶC ĐỊNH từ Compose Compiler 2.0.20+ nên không cần cấu hình gì thêm ở
// đây — chỉ cần block này nếu muốn tùy biến (report path, feature flags...).
composeCompiler {
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // Thư viện Material (XML views) — module Quản lý tệp dùng theme Theme.Material3.DayNight.NoActionBar
    // và các style Widget.Material3.* (Button, CardView, TextInputLayout) từ thư viện này, KHÔNG phải
    // từ androidx.compose.material3 (đó chỉ cấp Composable, không cấp resource style XML).
    implementation("com.google.android.material:material:1.12.0")
    // AppAuth — OAuth2 Authorization Code + PKCE cho Dropbox/Box trong OAuthManager.kt
    implementation("net.openid:appauth:0.11.1")
    // Biometric — mở khoá vân tay/Face trong AppLockActivity.kt
    implementation("androidx.biometric:biometric:1.1.0")
    // Apache MINA FtpServer — server FTP cục bộ trong FtpServerManager.kt
    implementation("org.apache.ftpserver:ftpserver-core:1.2.0")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")

    // DataStore — lưu preference dark mode
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Supabase Kotlin SDK
    implementation(platform("io.github.jan-tennert.supabase:bom:2.6.0"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:gotrue-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    // ═══ Module Admin (trước đây là app Learnsy Standard Admin độc lập) ═══
    // Cần Functions cho Edge Function student-set-password (xem SupabaseClient.kt).
    // KHÔNG thêm androidx.navigation:navigation-compose — bản Admin gốc khai báo
    // dependency này trong build.gradle.kts nhưng không dùng ở đâu (AppRoot.kt điều
    // hướng bằng state machine tab thường, không dùng NavHost thật), nên bỏ qua khi gộp.
    implementation("io.github.jan-tennert.supabase:functions-kt")
    implementation("io.ktor:ktor-client-android:2.3.11")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.11")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.11")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Coil — load ảnh avatar từ URL
    implementation("io.coil-kt:coil-compose:2.6.0")
    // coil-base: cần cho MemoryCache/DiskCache builder khi cấu hình
    // ImageLoader tùy chỉnh (cache RAM/đĩa lớn hơn) trong LearnsyApp.kt
    implementation("io.coil-kt:coil-base:2.6.0")
    // coil-gif: cần cho GifDecoder/ImageDecoderDecoder dùng trong LearnsyApp.kt (preview ảnh GIF)
    implementation("io.coil-kt:coil-gif:2.6.0")

    // Media3 ExoPlayer — preview video/audio trong tab Tài liệu
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-common:1.3.1")
    // Media3 Session — MediaSessionService/MediaSession/MediaController dùng trong
    // AudioPlaybackService.kt, RendererPlaybackService.kt, AudioPlayerController.kt
    implementation("androidx.media3:media3-session:1.3.1")

    // Apache POI — đọc nội dung Word (.doc/.docx) và Excel (.xls/.xlsx) để preview trong tab Tài liệu
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml:5.2.5") {
        exclude(group = "org.apache.xmlbeans", module = "xmlbeans")
    }
    implementation("org.apache.xmlbeans:xmlbeans:5.2.0")
    implementation("org.apache.poi:poi-scratchpad:5.2.5")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Retrofit + OkHttp + Gson — client HTTP cho Dropbox/Box API (xem CloudApis.kt, DropboxService.kt, BoxService.kt)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // OkHttp logging-interceptor — HttpLoggingInterceptor dùng trong RetrofitFactory.kt
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // Google Sign-In + Drive API v3 — đăng nhập và thao tác Google Drive trong GoogleDriveService.kt/CloudFragment.kt
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.api-client:google-api-client-android:2.6.0") {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.apis:google-api-services-drive:v3-rev20240914-2.0.0")
    implementation("com.google.http-client:google-http-client-gson:1.44.2")

    // ZXing — sinh/đọc mã QR cho chia sẻ cấu hình kết nối FTP/SFTP/SMB (xem QrCodeUtils.kt)
    implementation("com.google.zxing:core:3.5.3")

    // GridLayout — dùng app:columnCount/rowCount/layout_columnWeight trong activity_home.xml
    implementation("androidx.gridlayout:gridlayout:1.0.0")

    // NanoHTTPD — HTTP server nhẹ dùng trong MediaStreamServer.kt (stream video/audio qua LAN)
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // Zip4j — đọc/tạo file nén .zip (hỗ trợ mật khẩu, AES) trong ArchiveUtils.kt
    implementation("net.lingala.zip4j:zip4j:2.11.5")

    // Junrar — đọc file nén .rar trong ArchiveUtils.kt
    implementation("com.github.junrar:junrar:7.5.5")

    // AndroidX Security-Crypto — EncryptedSharedPreferences/MasterKey trong SecurePrefs.kt
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // SMBJ — client SMB/CIFS trong SmbClientManager.kt
    implementation("com.hierynomus:smbj:0.13.0")
    // uCrop — màn hình crop ảnh tương tác (pan/zoom) cho avatar/ảnh bìa. Phân phối qua JitPack
    // (đã có sẵn ở settings.gradle.kts), thay cho center-crop cứng tự động trước đây.
    implementation("com.github.yalantis:ucrop:2.2.10")

    // SSHJ — client SFTP/SSH trong SftpClientManager.kt
    implementation("com.hierynomus:sshj:0.38.0")

    // CameraX — quét mã QR trong QrScannerActivity.kt
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // ML Kit Barcode Scanning — giải mã QR trong QrScannerActivity.kt
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // PhotoView — pinch-to-zoom trong MediaPagerAdapter.kt
    implementation("com.github.chrisbanes:PhotoView:2.3.0")

    // AndroidX Webkit — WebViewAssetLoader trong HtmlViewerActivity.kt
    implementation("androidx.webkit:webkit:1.11.0")

    // Apache Commons Compress — đọc archive .7z trong ArchiveUtils.kt
    implementation("org.apache.commons:commons-compress:1.26.2")
    // XZ — codec bắt buộc cho Commons Compress khi đọc 7z dùng LZMA2
    implementation("org.tukaani:xz:1.9")

    // Apache Commons Net — FTPClient dùng trong FtpClientManager.kt
    implementation("commons-net:commons-net:3.11.1")

    // RecyclerView — khai tường minh để đảm bảo có bindingAdapterPosition (>= 1.2.0),
    // tránh phụ thuộc version cũ kéo theo transitive từ appcompat/material
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}

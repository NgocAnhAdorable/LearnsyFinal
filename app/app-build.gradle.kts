import java.util.Properties

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

plugins {
    id("com.android.application")
    // Từ AGP 9.x, Kotlin compilation được tích hợp sẵn trong
    // com.android.application — plugin org.jetbrains.kotlin.android
    // riêng không còn tương thích và đã bị bỏ ở đây.
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Chỉ áp dụng plugin Google Services nếu đã có app/google-services.json — tránh lỗi build
// cứng "File google-services.json is missing". File này KHÔNG được commit kèm theo lúc gộp
// module Quản lý tệp vào Learnsy Pro (file gốc chứa API key/OAuth client ID thật, đăng ký
// cho package com.myfile.ui + chữ ký APK riêng — sẽ KHÔNG hoạt động cho package
// com.learnsypro.app dù có copy nguyên văn sang, vì Google Sign-In đối chiếu package name +
// SHA-1 chữ ký lúc runtime). Toàn bộ tính năng khác (FTP/SFTP/SMB, Dropbox, Box, OneDrive)
// build và chạy bình thường không cần file này — CHỈ đăng nhập Google Drive bị tắt cho tới
// khi tự tạo project Firebase/Google Cloud mới cho package com.learnsypro.app và đặt
// google-services.json thật vào thư mục app/.
val hasGoogleServicesJson = file("google-services.json").exists()
if (hasGoogleServicesJson) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.learnsypro.app"
    // GIỮ compileSdk 34 (giá trị gốc của Learnsy Pro) thay vì 37 (giá trị của app MyFile
    // Manager gốc) — không có bằng chứng cụ thể thư viện nào ở đây (CameraX 1.3.4, ML Kit
    // 17.3.0, Media3 1.4.1...) bắt buộc compileSdk 37 mới build được; nếu Gradle báo lỗi
    // "androidx.X requires compileSdk >= Y" khi build thật, nâng compileSdk lên giá trị Y đó
    // (chỉ đúng bằng mức tối thiểu bị yêu cầu, không cần nhảy thẳng lên 37).
    compileSdk = 34

    defaultConfig {
        applicationId = "com.learnsypro.app"
        minSdk = 26
        // GIỮ targetSdk 34 (giá trị gốc của Learnsy Pro) — khác với compileSdk, targetSdk
        // THỰC SỰ thay đổi hành vi runtime (permission, foreground service, giới hạn chạy
        // nền...). Không nâng lên 37 như app MyFile Manager gốc chỉ vì đang gộp mã nguồn;
        // việc đó cần test riêng, không nên làm ngầm kèm theo việc gộp module này.
        targetSdk = 34
        versionCode = 24
        versionName = "24.5"

        // ═══ Đọc từ local.properties — KHÔNG commit file đó lên git ═══
        buildConfigField("String", "SUPA_URL", "\"${localProps.getProperty("SUPA_URL", "")}\"")
        buildConfigField("String", "SUPA_KEY", "\"${localProps.getProperty("SUPA_KEY", "")}\"")
        buildConfigField("String", "UPSTASH_URL", "\"${localProps.getProperty("UPSTASH_URL", "")}\"")
        buildConfigField("String", "UPSTASH_TOKEN", "\"${localProps.getProperty("UPSTASH_TOKEN", "")}\"")

        // ═══ Module Quản lý tệp (FTP/SFTP/SMB/Cloud) — trước đây là app MyFile Manager
        // độc lập. Client ID/secret dưới đây được GIỮ NGUYÊN từ app gốc (đăng ký cho
        // package com.myfile.ui) — KHÔNG phải placeholder, là ID thật đang hoạt động
        // cho Dropbox/Box. Google Drive KHÔNG dùng buildConfigField này lúc runtime (xem
        // ghi chú ở khối google-services.json phía trên) — giữ lại chỉ để tham chiếu.
        // ⚠️ BOX_CLIENT_SECRET bị nhúng thẳng vào APK (đọc được nếu decompile) — đây là hạn
        // chế đã tồn tại sẵn từ app gốc, không phát sinh do việc gộp module; cân nhắc chuyển
        // luồng Box OAuth sang PKCE thuần (không cần client secret ở phía app di động, đúng
        // khuyến nghị OAuth2 cho "public client") nếu muốn xử lý dứt điểm.
        buildConfigField("String", "GOOGLE_DRIVE_CLIENT_ID", "\"664326481029-5nl3olg60oiqvm14gt316orododf06aq.apps.googleusercontent.com\"")
        buildConfigField("String", "DROPBOX_APP_KEY", "\"2tznxguscwvir9n\"")
        buildConfigField("String", "BOX_CLIENT_ID", "\"tq8ju2pw919xsm8tg790v8bb3bu7601l\"")
        buildConfigField("String", "BOX_CLIENT_SECRET", "\"tFuhtDHbSNvEcFlcJwCKoi3K26xSVwQN\"")
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
        debug {
            isMinifyEnabled = false
        }
        release {
            // R8 shrink + obfuscate — giảm size APK, class load nhanh hơn khi
            // cold-start (rõ nhất trên CPU yếu / storage chậm).
            //
            // Đã XÁC NHẬN (không phải suy đoán) crash Resources$NotFoundException
            // (Resource ID #0x0) trước đây do R8/AGP 9.0.1 + Kotlin 2.4.0 const-fold
            // nhầm các hằng số MascotPose.* thành 0 — build thử với isMinifyEnabled=false
            // hết crash ngay, xác nhận đúng nguyên nhân. Đã vá 2 lớp: (1) MascotPose
            // đổi const val -> val (TabHome.kt, không thể const-fold), (2) rule ProGuard
            // tường minh -keep cho MascotPose + R$drawable ở cuối proguard-rules.pro.
            // Bật lại minify ở đây — ĐÃ TEST xác nhận không còn crash với 2 lớp vá trên.
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
        viewBinding = true
        buildConfig = true
    }

    // POI kéo theo nhiều jar có cùng file META-INF (LICENSE, NOTICE, module-info...), và
    // các thư viện server FTP/SFTP (Apache MINA) cũng vậy → không loại trừ sẽ lỗi "More
    // than one file was found with OS independent path".
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
                "META-INF/{AL2.0,LGPL2.1}",
                "META-INF/*.kotlin_module",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "module-info.class",
                "**/module-info.class"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Bắt buộc cho SFTP server (org.apache.sshd) dùng java.nio.file.Path/Paths/Files —
        // các API này chỉ có sẵn từ Android API 26 trở lên. minSdk của Learnsy Pro đã LÀ 26
        // (cao hơn minSdk 24 của app MyFile Manager gốc) nên về lý thuyết không bắt buộc như
        // ở app gốc — nhưng GIỮ desugaring bật vì vô hại (không ảnh hưởng gì tới phần
        // Dashboard/Quiz hiện có) và phòng trường hợp sau này hạ minSdk trở lại.
        isCoreLibraryDesugaringEnabled = true
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
    // ═══ Core AndroidX + Material 3 — dùng chung cho cả Dashboard (Compose) lẫn module
    // Quản lý tệp (View/XML) ═══
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.gridlayout:gridlayout:1.0.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // DataStore — lưu preference dark mode (Dashboard) VÀ được module Quản lý tệp đọc lại
    // để đồng bộ theme (xem LearnsyFileManagerActivity.kt)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Supabase Kotlin SDK (Dashboard/Quiz/Vocab)
    implementation(platform("io.github.jan-tennert.supabase:bom:2.6.0"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:gotrue-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.ktor:ktor-client-android:2.3.11")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.11")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.11")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // ═══ Coil — load ảnh. 1 PHIÊN BẢN DUY NHẤT (2.7.0) dùng chung cho avatar/ảnh nền
    // Dashboard (coil-compose, AsyncImage) VÀ thumbnail file/GIF/HEIC trong module Quản lý
    // tệp (coil-gif) — xem LearnsyApp.kt để biết lý do phải gộp làm 1 ImageLoader.
    //
    // ĐÃ SỬA LỖI BUILD (log CI 88968806788): trước đây ở đây có ghi chú "không cần khai
    // io.coil-kt:coil riêng vì coil-compose đã kéo theo coil-base" rồi CHỦ Ý bỏ dòng
    // implementation("io.coil-kt:coil:2.7.0") — suy luận đó sai. App MyFile Manager gốc (xem
    // build.gradle.kts gốc, package com.myfile.ui) khai CẢ HAI io.coil-kt:coil:2.7.0 VÀ
    // io.coil-kt:coil-gif:2.7.0 cùng lúc — thiếu artifact "coil" thuần (chỉ có coil-compose +
    // coil-gif) khiến Kotlin compiler báo "Unresolved reference 'GifDecoder'"/"'ImageDecoderDecoder'"
    // trong LearnsyApp.kt dù coil-gif đã có trong classpath, vì coil-gif không tự đủ để resolve
    // 2 class đó — cần io.coil-kt:coil làm artifact gốc. Khôi phục đúng bộ dependency của app
    // gốc, không tự suy luận "artifact nào có vẻ dư thừa" nữa.
    implementation("io.coil-kt:coil:2.7.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")

    // ═══ Media3 ExoPlayer + MediaSession — 1 PHIÊN BẢN DUY NHẤT (1.4.1) cho cả preview
    // video/audio trong tab Tài liệu (Dashboard) VÀ player nhạc nền + DLNA cast (module Quản
    // lý tệp). Các module con của media3 PHẢI cùng version với nhau, không thể trộn. ═══
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")

    // Apache POI — đọc nội dung Word (.doc/.docx) và Excel (.xls/.xlsx) để preview trong tab
    // Tài liệu (Dashboard)
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml:5.2.5") {
        exclude(group = "org.apache.xmlbeans", module = "xmlbeans")
    }
    implementation("org.apache.xmlbeans:xmlbeans:5.2.0")
    implementation("org.apache.poi:poi-scratchpad:5.2.5")

    // ═══ Module Quản lý tệp (FTP/SFTP/SMB/Cloud) — trước đây là app MyFile Manager
    // độc lập, các thư viện dưới đây phục vụ RIÊNG module này ═══

    // FTP Server (Apache MINA FTPServer)
    implementation("org.apache.ftpserver:ftpserver-core:1.2.0")

    // HTTP Stream Server (NanoHTTPD) — phát file qua LAN cho TV/điện thoại khác
    // (mở bằng trình duyệt/VLC) và làm nền tảng để DLNA cast trỏ TV về lấy file.
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // FTP Client (Apache Commons Net)
    implementation("commons-net:commons-net:3.11.1")

    // SFTP Client (SSHJ - hỗ trợ SFTP qua SSH)
    implementation("com.hierynomus:sshj:0.38.0")

    // SMB2/SMB3 Client (smbj - duyệt/tải lên/xuống chia sẻ mạng Windows/NAS)
    implementation("com.hierynomus:smbj:0.13.0")

    // SFTP Server (Apache MINA sshd - dùng chung engine với ftpserver-core)
    // sshd-sftp là artifact RIÊNG kể từ sshd 2.0 trở đi, chứa SftpSubsystemFactory —
    // thiếu nó sẽ không có class này dù đã có sshd-core.
    implementation("org.apache.sshd:sshd-core:2.13.2")
    implementation("org.apache.sshd:sshd-common:2.13.2")
    implementation("org.apache.sshd:sshd-sftp:2.13.2")

    // Networking cho cloud APIs (Google Drive, OneDrive, Box, Dropbox)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // AppAuth cho OAuth2 (OneDrive, Box, Dropbox - authorization code flow)
    implementation("net.openid:appauth:0.11.1")

    // Google Sign-In + Drive API — build được bình thường dù chưa có google-services.json;
    // chỉ THỰC SỰ đăng nhập được sau khi tự thêm file đó cho package com.learnsypro.app
    // (xem ghi chú ở khối áp dụng plugin Google Services phía trên).
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.api-client:google-api-client-android:2.7.0") {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.apis:google-api-services-drive:v3-rev20240914-2.0.0") {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.http-client:google-http-client-gson:1.45.0") {
        exclude(group = "org.apache.httpcomponents")
    }

    // QR code hiển thị địa chỉ FTP để kết nối nhanh
    implementation("com.google.zxing:core:3.5.3")

    // Quét mã QR bằng camera để kết nối nhanh (CameraX + ML Kit Barcode Scanning)
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Nén/giải nén ZIP và 7Z (dùng chung cho Bộ nhớ trong, FTP client, Cloud)
    implementation("org.apache.commons:commons-compress:1.26.2")
    // androidx.biometric: khoá app bằng vân tay/khuôn mặt (BiometricPrompt chuẩn hệ thống,
    // tự động fallback sang màn hình khoá thiết bị PIN/mẫu hình/mật khẩu nếu máy không có cảm
    // biến sinh trắc học hoặc người dùng chưa đăng ký vân tay nào).
    implementation("androidx.biometric:biometric:1.1.0")
    // junrar: đọc/giải nén file .rar (hỗ trợ RAR lên tới v7, kể cả file có mật khẩu và archive
    // nhiều phần .partN.rar). Thư viện CHỈ giải nén, không thể TẠO file .rar mới — tạo RAR đòi
    // hỏi giấy phép thương mại từ RARLAB, không thư viện mã nguồn mở nào được phép làm việc đó.
    // BẮT BUỘC dùng >= 7.5.10: các bản 7.5.7 trở xuống có 2 lỗ hổng path-traversal nghiêm
    // trọng trong LocalFolderExtractor (CVE-2026-28208, CVE-2026-41245) — 1 file .rar độc hại
    // có thể ghi đè file TÙY Ý ngoài thư mục đích khi giải nén (kể cả trên máy đã áp dụng
    // safeDestFile() chống Zip Slip trong ArchiveUtils.kt, vì lỗ hổng nằm bên TRONG code giải
    // nén của chính thư viện, xảy ra trước khi code của app kiểm tra được đường dẫn).
    implementation("com.github.junrar:junrar:7.5.10")
    // zip4j: đọc VÀ TẠO file .zip có mật khẩu (AES-256 hoặc ZipCrypto chuẩn cũ) — thư viện
    // java.util.zip có sẵn trong JDK KHÔNG hỗ trợ zip mã hoá dưới bất kỳ hình thức nào, nên cần
    // thư viện riêng cho cả 2 chiều: giải nén file .zip có pass tải từ mạng về, và tạo file
    // .zip có pass khi người dùng muốn bảo vệ dữ liệu trước khi chia sẻ. 100% Java (không code
    // native), hoạt động ổn định trên Android. Bản >= 2.10.0 đã vá CVE-2018-1002202 (Zip Slip)
    // và CVE-2022-24615 (uncaught exception khi parse zip cố tình lỗi định dạng).
    implementation("net.lingala.zip4j:zip4j:2.11.5")
    implementation("org.tukaani:xz:1.9") // cần cho giải nén 7z dùng LZMA2

    // WebView mở rộng: an toàn hơn khi nạp file HTML local (WebViewAssetLoader)
    implementation("androidx.webkit:webkit:1.11.0")

    implementation("androidx.viewpager2:viewpager2:1.1.0")
    // PhotoView: pinch-to-zoom + double-tap zoom + kéo ảnh cho MediaViewerActivity. Lấy qua
    // jitpack.io (đã khai ở settings.gradle.kts) vì đây là thư viện nhỏ, ổn định, không còn
    // maintain bản mới trên Maven Central nhưng vẫn hoạt động tốt, được rất nhiều app dùng.
    implementation("com.github.chrisbanes:PhotoView:2.3.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    // Bắt buộc để dùng java.nio.file.* (Path, Paths, Files) — thư viện SFTP server
    // (sshd-sftp) dùng các API này trực tiếp.
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")
}

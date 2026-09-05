// Top-level build file
plugins {
    id("com.android.application") version "9.0.1" apply false
    // Kotlin CỐ Ý PIN Ở 2.3.21, KHÔNG PHẢI 2.4.0:
    // Xác nhận qua decompile bytecode APK build 24.5 (3 lần crash liên tiếp,
    // mapping ID khác nhau mỗi lần → mỗi lần đều là build mới thật, không phải
    // cache) rằng R8 bundled trong AGP 9.0.1 xử lý sai tham số hàm khi build với
    // Kotlin 2.4.0 — cụ thể: MascotImage(drawableRes: Int, sizeDp: Int, modifier)
    // bị inline sai tại 3 call site loại-trừ-lẫn-nhau (LoadingState/ErrorState/
    // EmptySearchState trong TabHome.kt), R8 loại bỏ hẳn tham số drawableRes khỏi
    // signature đã inline và hardcode = 0 → painterResource(id = 0) →
    // Resources$NotFoundException ngay khi LoadingState hiện ra sau login.
    // Build log gốc có ~200 dòng "R8: An error occurred when parsing kotlin
    // metadata... newer version of kotlin than the kotlin version released when
    // this version of R8 was created" cho MỌI class — xác nhận R8 không đọc được
    // Kotlin 2.4 metadata. Tài liệu chính thức developer.android.com/build/
    // releases/about-agp minh hoạ dòng AGP 9.x pin cùng Kotlin 2.3.21, không phải
    // 2.4.0 — đổi `const val`→`val` (thử trước, KHÔNG đủ, vẫn crash y hệt ở build
    // kế tiếp) không giải quyết được vì bug nằm ở tầng inline/loại-tham-số của R8,
    // không phải Kotlin compile-time const-fold. Chỉ nâng lại lên 2.4.0 khi AGP đã
    // lên bản chính thức hỗ trợ (9.0.28+ theo developer.android.com/build/
    // kotlin-support) VÀ đã xác nhận build log không còn warning parse-metadata.
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21" apply false
    // Từ Kotlin 2.0+, Compose Compiler tách khỏi Compose BOM và đi kèm
    // đúng version Kotlin — thay cho composeOptions{kotlinCompilerExtensionVersion}
    // kiểu cũ (chỉ áp dụng cho Kotlin 1.9 trở xuống).
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}

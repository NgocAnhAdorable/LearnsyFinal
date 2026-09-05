pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Cần cho thư viện PhotoView (module Quản lý tệp — pinch-to-zoom trong
        // MediaViewerActivity) và junrar (giải nén .rar): cả 2 chỉ publish qua JitPack,
        // không có trên Maven Central.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "LearnsyPro"
include(":app")

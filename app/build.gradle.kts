plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "ai.nexus"
    compileSdk = 36

    defaultConfig {
        applicationId = "ai.nexus"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    buildFeatures { compose = true }

    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // ── Compose UI ──────────────────────────────────────
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.compose)
    debugImplementation(libs.compose.ui.tooling)

    // ── 核心 Android ─────────────────────────────────────
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.service)         // LifecycleService

    // ── 依赖注入 ─────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // ── 数据库 Room ───────────────────────────────────────
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ── 后台任务 WorkManager ──────────────────────────────
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)

    // ── 序列化 ────────────────────────────────────────────
    implementation(libs.kotlinx.serialization.json)

    // ── 协程 ──────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)

    // ── 网络 OkHttp（工具层用）────────────────────────────
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
}

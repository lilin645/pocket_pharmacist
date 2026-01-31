// app/build.gradle.kts
plugins {

    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
}

android {
    namespace = "com.contest.pocketpharmacist"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.contest.pocketpharmacist"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // ... buildConfigField ...
    }

    // ... compileOptions, kotlinOptions 保持原样 ...
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // 本地 Jar (确保 libs 下没有同名 aar)
    implementation(files("libs/Msc.jar"))

    // === 基础库 (使用 libs 引用) ===
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // === CameraX (使用 libs 引用) ===
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // === 网络 & 协程 (使用 libs 引用) ===
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // === Room (关键部分) ===
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    // 使用 kapt 引用编译器
    kapt(libs.androidx.room.compiler)

    // === 测试 ===
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
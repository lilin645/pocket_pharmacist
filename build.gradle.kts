
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
}

allprojects {
    val properties = java.util.Properties().apply {
        load(rootProject.file("local.properties").inputStream())
    }
    ext["DASHSCOPE_API_KEY"] = properties.getProperty("dashscope.api.key")
}
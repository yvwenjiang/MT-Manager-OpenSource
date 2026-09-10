plugins {
    id("mtmanager.android.application")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.mtopensource.mtmanager"

    defaultConfig {
        // 版本名同时写入 APK 的 BuildConfig，便于插件校验宿主版本
        buildConfigField("String", "PLUGIN_API_VERSION", "\"1\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:filemanager"))
    implementation(project(":core:editor"))
    implementation(project(":core:ui"))

    // 插件系统：宿主侧加载器
    implementation(project(":plugin-system:api"))
    implementation(project(":plugin-system:bridge"))
    implementation(project(":plugin-system:host"))

    // 内置插件随主 APK 一起分发，实现开箱即用
    implementation(project(":plugins:apk-editor"))
    implementation(project(":plugins:dex-editor"))
    implementation(project(":plugins:root-support"))
    implementation(project(":plugins:remote-storage"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.foundation)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // 归档解析（内置插件在运行时需要）
    implementation(libs.commons.compress)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.json:json:20240303")
}

plugins {
    id("mtmanager.android.library")
    // 版本由 build-logic 的 classpath 提供，故此处不带版本
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mtopensource.ui"

    buildFeatures {
        compose = true
    }
}

dependencies {
    api(project(":core:common"))
    api(project(":core:filemanager"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // Compose
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.ui.tooling.preview)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.compose.runtime)
    api(libs.androidx.compose.foundation)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}

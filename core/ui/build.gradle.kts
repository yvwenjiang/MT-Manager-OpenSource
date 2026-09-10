plugins {
    id("mtmanager.android.library")
    alias(libs.plugins.kotlin.compose)
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

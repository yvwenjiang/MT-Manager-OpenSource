plugins {
    id("mtmanager.android.library")
}

android {
    namespace = "com.mtopensource.plugins.apkeditor"
}

dependencies {
    api(project(":plugin-system:api"))
    implementation(libs.androidx.annotation)
    // APK / ZIP 解包依赖
    implementation(libs.commons.compress)

    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
}

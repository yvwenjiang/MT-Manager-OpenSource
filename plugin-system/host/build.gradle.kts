plugins {
    id("mtmanager.android.library")
}

android {
    namespace = "com.mtopensource.plugin.host"
}

dependencies {
    api(project(":plugin-system:bridge"))
    implementation(project(":core:ui"))
    implementation(libs.androidx.annotation)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.json:json:20240303")
}

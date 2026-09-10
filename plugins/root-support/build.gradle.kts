plugins {
    id("mtmanager.android.library")
}

android {
    namespace = "com.mtopensource.plugins.rootsupport"
}

dependencies {
    api(project(":plugin-system:api"))
    implementation(libs.androidx.annotation)

    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
}

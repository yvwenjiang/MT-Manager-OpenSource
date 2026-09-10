plugins {
    id("mtmanager.android.library")
}

android {
    namespace = "com.mtopensource.plugins.remotestorage"
}

dependencies {
    api(project(":plugin-system:api"))
    implementation(libs.androidx.annotation)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.json:json:20240303")
}

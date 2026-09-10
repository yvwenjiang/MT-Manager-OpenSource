plugins {
    id("mtmanager.android.library")
}

android {
    namespace = "com.mtopensource.plugin.api"
}

dependencies {
    api(project(":core:common"))
    api(project(":core:filemanager"))
    implementation(libs.androidx.annotation)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

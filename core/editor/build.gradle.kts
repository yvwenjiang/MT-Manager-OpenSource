plugins {
    id("mtmanager.android.library")
}

android {
    namespace = "com.mtopensource.editor"
}

dependencies {
    api(project(":core:common"))
    implementation(libs.androidx.annotation)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

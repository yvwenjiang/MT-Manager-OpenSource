plugins {
    id("mtmanager.android.library")
}

android {
    namespace = "com.mtopensource.filemanager"
}

dependencies {
    api(project(":core:common"))
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.documentfile)
    implementation(libs.commons.compress)
    implementation(libs.zip4j)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

plugins {
    `kotlin-dsl`
}

group = "com.mtopensource.buildlogic"

if (providers.gradleProperty("useChinaMirrors").getOrElse("false") == "true") {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
    }
} else {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencies {
    // 约定插件在编译期需要 AGP / Kotlin / Detekt 的 API。
    // 使用 version catalog 的 [plugins] 段别名（libs.plugins.*），
    // 它们会被解析为对应的 Gradle plugin marker 依赖。
    implementation(libs.plugins.android.application)
    implementation(libs.plugins.android.library)
    implementation(libs.plugins.kotlin.android)
    implementation(libs.plugins.kotlin.compose)
    implementation(libs.plugins.detekt)
}

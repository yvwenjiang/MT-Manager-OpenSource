plugins {
    `kotlin-dsl`
}

group = "com.mtopensource.buildlogic"

// AGP 只发布在 Google Maven 上，Kotlin/Detekt 在 Maven Central 与
// Gradle Plugin Portal 上，因此两类仓库都必须配置。
if (providers.gradleProperty("useChinaMirrors").getOrElse("false") == "true") {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
    }
} else {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencies {
    // 约定插件在编译期需要 AGP / Kotlin / Detekt 的 API 类型。
    // 这里必须用普通库坐标，不能用 libs.plugins.*
    //（后者是 Provider<PluginDependency>，无法作为 Dependency 传入）。
    implementation(libs.agp)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin.api)
    implementation(libs.detekt.gradle.plugin)
}

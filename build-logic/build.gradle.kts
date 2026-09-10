plugins {
    `kotlin-dsl`
}

group = "com.mtopensource.buildlogic"

dependencies {
    // 约定插件在编译期需要 AGP / Kotlin / Detekt 的 API 类型，
    // 但它们运行时由消费方项目的 plugins {} 块提供，因此必须用 compileOnly。
    //
    // 若改为 implementation：这些 jar 会被塞进 build-logic 的运行时 classpath，
    // 导致根项目 build.gradle.kts 里的插件声明报
    // "plugin is already on the classpath with an unknown version"。
    //
    // 另外这里必须用普通库坐标，不能写 libs.plugins.*
    //（后者是 Provider<PluginDependency>，无法作为 Dependency 传入）。
    compileOnly(libs.agp)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin.api)
    compileOnly(libs.detekt.gradle.plugin)
}

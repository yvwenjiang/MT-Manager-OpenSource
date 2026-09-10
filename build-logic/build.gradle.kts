plugins {
    `kotlin-dsl`
}

group = "com.mtopensource.buildlogic"

dependencies {
    // 约定插件里 plugins { id("com.android.application") } 需要 Gradle 在
    // 生成类型安全访问器时真实解析并应用该插件，因此插件必须在
    // build-logic 自身的 classpath 上，必须用 implementation（compileOnly 不足）。
    //
    // 相应约束：根项目 build.gradle.kts 不能再用 alias(libs.plugins.*) apply false
    // 重复声明这些插件，否则会报
    // "plugin is already on the classpath with an unknown version"。
    //
    // 此处必须使用普通库坐标，不能用 libs.plugins.*
    //（后者是 Provider<PluginDependency>，无法作为 Dependency 传入）。
    implementation(libs.agp)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin.api)
    implementation(libs.detekt.gradle.plugin)
}

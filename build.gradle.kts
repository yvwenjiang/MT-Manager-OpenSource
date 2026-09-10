// build-logic 是独立的 composite build，其 classpath 不与根构建共享，
// 因此这里可以安全地声明插件版本：子模块需要它来解析 plugins { id(...) }。
// AGP / Detekt 已由约定插件（mtmanager.*）内部应用，无需在这里重复声明。
plugins {
    alias(libs.plugins.kotlin.compose) apply false
    id("mtmanager.detekt")
}

// 所有工程统一使用 UTF-8，避免中文源码在 CI 环境出现乱码
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

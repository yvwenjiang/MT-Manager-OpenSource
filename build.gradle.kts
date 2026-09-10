// AGP / Kotlin / Detekt 的版本与坐标统一由 build-logic 管理
// （见 build-logic/build.gradle.kts 的 dependencies），
// 这里不能再通过 alias(libs.plugins.*) apply false 重复声明，
// 否则会报 "plugin is already on the classpath with an unknown version"。
plugins {
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

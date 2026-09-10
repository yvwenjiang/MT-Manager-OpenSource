// 是否启用阿里云镜像（默认关闭，面向国内开发者在 gradle.properties 中打开）。
//
// 注意：Gradle 不会把 gradle.properties 里的键自动暴露为 Kotlin DSL 顶层变量，
// 必须通过 providers.gradleProperty 显式读取，否则脚本编译期报 Unresolved reference。
// build-logic/build.gradle.kts 使用同样的读取方式。
pluginManagement {
    repositories {
        if (providers.gradleProperty("useChinaMirrors").getOrElse("false").toBoolean()) {
            maven("https://maven.aliyun.com/repository/gradle-plugin")
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
        }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (providers.gradleProperty("useChinaMirrors").getOrElse("false").toBoolean()) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "MT-Manager-OpenSource"

// 约定插件所在构建。使用 composite build（而非 buildSrc）的原因：
// buildSrc 的产物会与根项目的 plugins {} 解析竞争，
// 导致 "plugin is already on the classpath with an unknown version"。
includeBuild("build-logic")

include(
    ":core:common",
    ":core:filemanager",
    ":core:editor",
    ":core:ui",
    ":core:app",
    ":plugin-system:api",
    ":plugin-system:bridge",
    ":plugin-system:host",
    ":plugins:apk-editor",
    ":plugins:dex-editor",
    ":plugins:root-support",
    ":plugins:remote-storage",
)

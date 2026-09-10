import org.gradle.api.artifacts.VersionCatalogsExtension

rootProject.name = "build-logic"

// build-logic 是一个独立构建（composite build），看不到根项目的仓库与
// version catalog，因此这里需要各自声明一次。

// 1) 先声明 version catalog，供下方读取插件版本使用
//    （注意：[versions] 与 [libraries] 段存在同名键，用 Properties 之类的
//     简易解析会被后者覆盖，必须交给 Gradle 的 catalog 解析器处理）
dependencyResolutionManagement {
    repositories {
        if (providers.gradleProperty("useChinaMirrors").getOrElse("false").toBoolean()) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
        }
        google()
        mavenCentral()
    }

    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

// 2) 约定插件内部的 plugins { id("com.android.application") } 不带版本，
//    必须在 pluginManagement 中补上，否则预编译脚本阶段报 Plugin was not found。
//    版本统一取自 version catalog，避免与根项目出现版本漂移。
pluginManagement {
    repositories {
        if (providers.gradleProperty("useChinaMirrors").getOrElse("false").toBoolean()) {
            maven("https://maven.aliyun.com/repository/gradle-plugin")
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    plugins {
        val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
        id("com.android.application") version catalog.findVersion("agp").get().requiredVersion
        id("com.android.library") version catalog.findVersion("agp").get().requiredVersion
        id("org.jetbrains.kotlin.android") version catalog.findVersion("kotlin").get().requiredVersion
        id("org.jetbrains.kotlin.plugin.compose") version catalog.findVersion("kotlin").get().requiredVersion
        id("io.gitlab.arturbosch.detekt") version catalog.findVersion("detekt").get().requiredVersion
    }
}

rootProject.name = "build-logic"

// build-logic 是独立构建（composite build），看不到根项目的仓库与
// version catalog，需要自己声明一份。
//
// 为什么不在这里直接读 ../gradle/libs.versions.toml 取版本号：
//  1. pluginManagement {} 在 settings 求值最早期执行，此时
//     VersionCatalogsExtension 尚未注册（实测报
//     "Extension of type 'VersionCatalogsExtension' does not exist"）；
//  2. 用简易 Properties 解析会被 catalog 中 [libraries] 段的同名键覆盖；
//  3. Kotlin DSL 分阶段编译，顶层 val 无法被 pluginManagement {} 内的
//     代码引用（报 Unresolved reference）。
//
// 因此版本写在此处。为避免与 gradle/libs.versions.toml 漂移，
// CI 的 "Verify plugin versions" 步骤会对两处做一致性校验。
pluginManagement {
    val agpVersion = "8.7.3"
    val kotlinVersion = "2.1.0"
    val detektVersion = "1.23.7"

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
        id("com.android.application") version agpVersion
        id("com.android.library") version agpVersion
        id("org.jetbrains.kotlin.android") version kotlinVersion
        id("org.jetbrains.kotlin.plugin.compose") version kotlinVersion
        id("io.gitlab.arturbosch.detekt") version detektVersion
    }
}

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

rootProject.name = "build-logic"

// build-logic 是一个独立构建（composite build），
// 看不到根项目的仓库与 version catalog，需要自己声明一份。
//
// 为什么这里不能直接读 ../gradle/libs.versions.toml 里的版本号：
// pluginManagement {} 在 settings 执行的最早期就被求值，
// 此时 VersionCatalogsExtension 尚未注册（实测报
// "Extension of type 'VersionCatalogsExtension' does not exist"），
// 而简易的 Properties 解析又会被 catalog 中 [libraries] 段的同名键覆盖。
// 因此这里显式声明版本，且与 gradle/libs.versions.toml 中的
// versions.agp / versions.kotlin / versions.detekt 保持一致。
//
// 一致性由 build-logic/settings.gradle.kts 末尾的校验负责，
// 版本漂移会直接导致构建失败并给出明确提示。
val agpVersion = "8.7.3"
val kotlinVersion = "2.1.0"
val detektVersion = "1.23.7"

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

// 校验上面的版本常量与 catalog 一致，避免两处版本漂移而无人察觉。
run {
    val toml = file("../gradle/libs.versions.toml").readText()
    val versions = Regex("""(?m)^\s*(agp|kotlin|detekt)\s*=\s*"([^"]+)"""")
        .findAll(toml)
        .associate { it.groupValues[1] to it.groupValues[2] }

    val mismatches = buildList {
        versions["agp"]?.takeIf { it != agpVersion }?.let {
            add("agp: build-logic=$agpVersion, libs.versions.toml=$it")
        }
        versions["kotlin"]?.takeIf { it != kotlinVersion }?.let {
            add("kotlin: build-logic=$kotlinVersion, libs.versions.toml=$it")
        }
        versions["detekt"]?.takeIf { it != detektVersion }?.let {
            add("detekt: build-logic=$detektVersion, libs.versions.toml=$it")
        }
    }

    require(mismatches.isEmpty()) {
        "build-logic/settings.gradle.kts 中的插件版本与 gradle/libs.versions.toml 不一致：\n" +
            mismatches.joinToString("\n") { "  - $it" }
    }
}

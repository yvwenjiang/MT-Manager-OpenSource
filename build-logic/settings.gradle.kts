rootProject.name = "build-logic"

// build-logic 是独立构建（composite build），看不到根项目的仓库与
// version catalog，需要自己声明一份。
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
    // 说明：这里刻意不写 plugins { id(...) version ... }。
    // 约定插件内的 plugins { id("com.android.application") } 依赖的是
    // 本构建的 classpath，而 AGP / Kotlin / Detekt 已由
    // build-logic/build.gradle.kts 的 implementation 依赖提供，
    // 因此无需（也不能依赖）此处的版本声明。
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

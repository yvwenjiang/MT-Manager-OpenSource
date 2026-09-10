pluginManagement {
    repositories {
        if (useChinaMirrors) {
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
        if (useChinaMirrors) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "MT-Manager-OpenSource"

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

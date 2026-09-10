rootProject.name = "build-logic"

// buildSrc 是一个独立构建，默认看不到根项目的 version catalog，
// 因此必须在这里显式声明一次，否则 build.gradle.kts 里的 libs.* 别名
// 会在脚本编译期报 Unresolved reference。
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

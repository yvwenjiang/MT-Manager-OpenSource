plugins {
    id("mtmanager.android.library")
}

android {
    namespace = "com.mtopensource.plugin.bridge"

    defaultConfig {
        // 插件加载依赖 DexClassLoader，需保证 API 26+ 行为一致
        minSdk = 26
    }
}

dependencies {
    api(project(":plugin-system:api"))
    implementation(libs.androidx.annotation)
    // org.json 在 Android 平台由系统提供；这里显式声明仅为让 JVM 单元测试可运行
    testImplementation("org.json:json:20240303")
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

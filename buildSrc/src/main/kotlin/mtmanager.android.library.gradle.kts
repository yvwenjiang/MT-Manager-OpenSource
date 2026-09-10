import com.mtopensource.buildlogic.AppConfig

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    compileSdk = AppConfig.COMPILE_SDK

    defaultConfig {
        minSdk = AppConfig.MIN_SDK
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = AppConfig.JAVA_VERSION
        targetCompatibility = AppConfig.JAVA_VERSION
    }

    // 部分模块经 Kotlin 插件自行配置 sourceSets，这里只保证测试源集存在
    sourceSets {
        getByName("test") {
            resources.srcDir("src/test/resources")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<Test>().configureEach {
    maxHeapSize = "1g"
}

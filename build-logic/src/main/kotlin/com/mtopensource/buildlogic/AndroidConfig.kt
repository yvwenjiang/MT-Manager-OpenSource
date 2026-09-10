package com.mtopensource.buildlogic

import org.gradle.api.JavaVersion

/**
 * 全局 Android 构建配置常量。
 *
 * 所有模块的 SDK 版本、JVM 目标版本在这里统一定义，
 * 避免各模块配置漂移。
 */
object AppConfig {
    const val NAMESPACE_PREFIX = "com.mtopensource"
    const val APPLICATION_ID = "com.mtopensource.mtmanager"

    const val COMPILE_SDK = 35
    const val MIN_SDK = 26
    const val TARGET_SDK = 35

    const val VERSION_CODE = 1
    const val VERSION_NAME = "0.1.0"

    val JAVA_VERSION = JavaVersion.VERSION_17
}

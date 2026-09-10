// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.plugin.bridge

import java.net.URL
import java.net.URLClassLoader

/**
 * 插件类加载器。
 *
 * 关键设计：**父加载器优先（parent-first）** 策略，
 * 但把 `com.mtopensource.plugin.api` 强制委托给宿主加载器。
 *
 * 原因：插件 API 是宿主与插件之间的类型契约，若插件自带一份 API 类，
 * 两份 `Plugin` 接口会是不同的 `Class` 对象，导致强转失败
 * （`ClassCastException`）。因此必须保证 API 类由同一个加载器加载。
 */
class PluginClassLoader(
    /** 插件包路径（APK / JAR / DEX）。 */
    private val pluginPath: String,
    /** 宿主类加载器，用于查找共享的 API 类。 */
    private val hostClassLoader: ClassLoader,
    /** 额外的优化目录（DEX 优化输出目录），仅 Android 平台使用。 */
    private val optimizedDirectory: String? = null,
) : ClassLoader(hostClassLoader) {

    private var delegate: ClassLoader? = null

    /** 插件之间必须相互隔离的包前缀。 */
    private val hostSharedPrefixes = listOf(
        "com.mtopensource.plugin.api.",
        "com.mtopensource.common.",
        "com.mtopensource.filemanager.",
        // Kotlin 运行时必须共享，否则插件内的 Kotlin 类型无法与宿主交互
        "kotlin.",
        "kotlinx.",
        "java.",
        "javax.",
        "android.",
        "androidx.",
    )

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        synchronized(getClassLoadingLock(name)) {
            // 1. 已加载过直接返回
            findLoadedClass(name)?.let {
                if (resolve) resolveClass(it)
                return it
            }

            // 2. 共享类一律由宿主加载，保证类型一致
            if (hostSharedPrefixes.any { name.startsWith(it) }) {
                val shared = hostClassLoader.loadClass(name)
                if (resolve) resolveClass(shared)
                return shared
            }

            // 3. 其余类优先从插件包内加载，实现插件间隔离
            return try {
                val pluginClass = pluginClassLoader().loadClass(name)
                if (resolve) resolveClass(pluginClass)
                pluginClass
            } catch (e: ClassNotFoundException) {
                hostClassLoader.loadClass(name)
            }
        }
    }

    /**
     * 获取真正加载插件字节码的加载器。
     *
     * Android 平台应返回 `DexClassLoader`；此处为保持模块可在 JVM 上编译与测试，
     * 通过反射尝试创建 DexClassLoader，失败则退化为 [URLClassLoader]。
     */
    private fun pluginClassLoader(): ClassLoader {
        delegate?.let { return it }

        val created = createDexClassLoader() ?: URLClassLoader(
            arrayOf(URL("file:$pluginPath")),
            hostClassLoader,
        )
        delegate = created
        return created
    }

    private fun createDexClassLoader(): ClassLoader? = runCatching {
        val clazz = Class.forName("dalvik.system.DexClassLoader")
        val constructor = clazz.getConstructor(
            String::class.java,
            String::class.java,
            String::class.java,
            ClassLoader::class.java,
        )
        constructor.newInstance(pluginPath, optimizedDirectory.orEmpty(), null, hostClassLoader) as ClassLoader
    }.getOrNull()

    /** 释放插件占用的资源（Android 上会关闭 DexFile 句柄）。 */
    fun close() {
        (delegate as? java.io.Closeable)?.let { runCatching { it.close() } }
        delegate = null
    }
}

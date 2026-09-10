// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.plugin.host

import com.mtopensource.common.utils.Logger
import com.mtopensource.plugin.api.Plugin
import com.mtopensource.plugin.api.PluginCapability
import com.mtopensource.plugin.api.PluginConfiguration
import com.mtopensource.plugin.api.PluginInfo
import com.mtopensource.plugin.bridge.PluginClassLoader
import com.mtopensource.plugin.bridge.PluginDescriptorReader
import com.mtopensource.plugin.bridge.PluginManifest
import com.mtopensource.plugin.bridge.PluginRecord
import com.mtopensource.plugin.bridge.PluginState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 插件管理器。
 *
 * 负责完整的插件生命周期：
 * 1. **发现**：扫描插件目录中所有合法载体
 * 2. **校验**：检查 API 版本、能力声明与 id 唯一性
 * 3. **加载**：通过 [PluginClassLoader] 实例化入口类并调用 `onLoad`
 * 4. **卸载**：调用 `onUnload` 并回滚插件注册的全部资源
 *
 * 通过 [plugins] 暴露只读状态流，UI 层可直接观察插件列表变化。
 */
class PluginManager(
    private val services: HostServices,
    private val hostClassLoader: ClassLoader,
) {

    private companion object {
        const val TAG = "PluginManager"
    }

    private val loadMutex = Mutex()

    private val _plugins = MutableStateFlow<List<PluginRecord>>(emptyList())

    /** 当前已发现的所有插件及其状态。 */
    val plugins: StateFlow<List<PluginRecord>> = _plugins.asStateFlow()

    /** 已激活的插件实例，key 为插件 id。 */
    private val activePlugins = mutableMapOf<String, LoadedPlugin>()

    /** 单个已加载插件的内部记录。 */
    private data class LoadedPlugin(
        val instance: Plugin,
        val context: HostedPluginContext,
        val classLoader: PluginClassLoaderHandle,
    )

    /**
     * 扫描并加载插件目录。
     *
     * @param pluginDir 插件存放目录
     * @param autoLoad 是否在发现后立即加载（默认仅发现，由 UI 决定何时激活）
     */
    suspend fun scan(pluginDir: String, autoLoad: Boolean = false): List<PluginRecord> = loadMutex.withLock {
        val discovered = PluginDescriptorReader.scanDirectory(pluginDir)
        Logger.i(TAG, "发现 ${discovered.size} 个插件")

        // 去重：同一个 id 只保留第一个（按名称排序后的顺序稳定）
        val deduped = discovered.distinctBy { it.manifest.id }

        _plugins.value = deduped

        if (autoLoad) {
            deduped.filter { it.state == PluginState.DISCOVERED }.forEach { record ->
                loadInternal(record.packagePath)
            }
        }
        _plugins.value
    }

    /**
     * 加载指定路径的插件。
     *
     * @return 加载成功返回插件信息，失败返回 null
     */
    suspend fun load(packagePath: String): PluginInfo? = loadMutex.withLock {
        loadInternal(packagePath)?.instance?.info
    }

    /**
     * 加载随宿主一起分发的内置插件。
     *
     * 与 [load] 的区别：内置插件位于宿主 ClassLoader 中，无需 DexClassLoader，
     * 因此不创建新的类加载器，加载开销极低。
     *
     * @param manifest 插件元信息
     * @param entryClass 入口类的全限定名
     * @return 加载成功返回 true
     */
    suspend fun loadBuiltin(manifest: PluginManifest, entryClass: String): Boolean = loadMutex.withLock {
        val validationError = PluginDescriptorReader.validate(manifest)
        if (validationError != null) {
            Logger.w(TAG, "内置插件校验失败：$validationError")
            upsertRecord(manifest, builtinPath(manifest.id), PluginState.INCOMPATIBLE, validationError)
            return@withLock false
        }

        if (activePlugins.containsKey(manifest.id)) {
            Logger.w(TAG, "插件已激活，跳过重复加载：${manifest.id}")
            return@withLock true
        }

        updateState(manifest.id) { it.copy(state = PluginState.LOADING, errorMessage = null) }

        return@withLock try {
            val pluginClass = hostClassLoader.loadClass(entryClass)
            val instance = pluginClass.getDeclaredConstructor().newInstance() as? Plugin
                ?: throw IllegalStateException("入口类未实现 Plugin 接口：$entryClass")

            val granted = PluginDescriptorReader.parseCapabilities(manifest.capabilities)
            val context = HostedPluginContext(manifest.toPluginInfo(granted), services, granted)

            instance.onLoad(context)

            // 内置插件没有独立类加载器，用空实现占位以复用统一的卸载流程
            activePlugins[manifest.id] = LoadedPlugin(instance, context, NoOpClassLoaderHandle())

            upsertRecord(manifest, builtinPath(manifest.id), PluginState.ACTIVE, null)
            Logger.i(TAG, "内置插件加载成功：${manifest.id} v${manifest.version}")
            true
        } catch (e: Throwable) {
            Logger.e(TAG, "内置插件加载失败：${manifest.id}", e)
            upsertRecord(manifest, builtinPath(manifest.id), PluginState.ERROR, e.message ?: e.javaClass.simpleName)
            false
        }
    }

    /** 内置插件的伪路径，便于 UI 区分来源。 */
    private fun builtinPath(pluginId: String) = "builtin:$pluginId"

    /**
     * 卸载指定插件。
     *
     * @return 卸载成功返回 true；插件未加载返回 false
     */
    suspend fun unload(pluginId: String): Boolean = loadMutex.withLock {
        val loaded = activePlugins.remove(pluginId) ?: return@withLock false

        runCatching { loaded.instance.onUnload() }
            .onFailure { Logger.e(TAG, "插件 $pluginId 卸载时异常", it) }

        // 回滚插件注册的资源（文件系统、菜单项）
        loaded.context.cleanup()
        loaded.classLoader.close()

        updateState(pluginId) { it.copy(state = PluginState.UNLOADED, errorMessage = null) }
        Logger.i(TAG, "插件已卸载：$pluginId")
        true
    }

    /** 卸载全部插件（应用退出时调用）。 */
    suspend fun unloadAll() {
        activePlugins.keys.toList().forEach { unload(it) }
    }

    /** 查询已加载的插件实例。 */
    fun activePlugin(pluginId: String): Plugin? = activePlugins[pluginId]?.instance

    /** 当前处于激活状态的插件数量。 */
    val activeCount: Int get() = activePlugins.size

    /**
     * 汇总所有插件注册的上下文菜单项。
     */
    fun collectContextMenus(): List<com.mtopensource.plugin.api.PluginMenuItem> =
        activePlugins.values.flatMap { it.context.registeredMenus() }.sortedBy { it.order }

    /**
     * 通知所有插件配置发生变化。
     */
    fun notifyConfigurationChanged(configuration: PluginConfiguration) {
        activePlugins.values.forEach { loaded ->
            runCatching { loaded.instance.onConfigurationChanged(configuration) }
                .onFailure { Logger.w(TAG, "插件 ${loaded.instance.info.id} 配置回调异常", it) }
        }
    }

    // ---------------------------------------------------------------------
    // 内部实现
    // ---------------------------------------------------------------------

    private fun loadInternal(packagePath: String): LoadedPlugin? {
        val manifest = PluginDescriptorReader.read(packagePath)
        if (manifest == null) {
            Logger.w(TAG, "无法读取插件描述文件：$packagePath")
            return null
        }

        val validationError = PluginDescriptorReader.validate(manifest)
        if (validationError != null) {
            Logger.w(TAG, "插件校验失败：$validationError")
            upsertRecord(manifest, packagePath, PluginState.INCOMPATIBLE, validationError)
            return null
        }

        if (activePlugins.containsKey(manifest.id)) {
            Logger.w(TAG, "插件 id 已加载，忽略重复加载：${manifest.id}")
            return activePlugins[manifest.id]
        }

        updateState(manifest.id) { it.copy(state = PluginState.LOADING, errorMessage = null) }

        val classLoader = PluginClassLoader(
            pluginPath = packagePath,
            hostClassLoader = hostClassLoader,
            optimizedDirectory = services.pluginOptimizedDir(manifest.id),
        )

        return try {
            val pluginClass = classLoader.loadClass(manifest.entryClass)
            val instance = pluginClass.getDeclaredConstructor().newInstance() as? Plugin
                ?: throw IllegalStateException("入口类未实现 Plugin 接口：${manifest.entryClass}")

            val granted = PluginDescriptorReader.parseCapabilities(manifest.capabilities)
            val context = HostedPluginContext(manifest.toPluginInfo(granted), services, granted)

            instance.onLoad(context)

            val loaded = LoadedPlugin(instance, context, ExternalClassLoaderHandle(classLoader))
            activePlugins[manifest.id] = loaded

            upsertRecord(manifest, packagePath, PluginState.ACTIVE, null)
            Logger.i(TAG, "插件加载成功：${manifest.id} v${manifest.version}")
            loaded
        } catch (e: Throwable) {
            // 加载失败必须释放类加载器，避免文件句柄泄漏
            classLoader.close()
            Logger.e(TAG, "插件加载失败：${manifest.id}", e)
            upsertRecord(manifest, packagePath, PluginState.ERROR, e.message ?: e.javaClass.simpleName)
            null
        }
    }

    private fun PluginManifest.toPluginInfo(granted: Set<PluginCapability>) = PluginInfo(
        id = id,
        name = name,
        version = version,
        author = author,
        description = description,
        requiredApiVersion = requiredApiVersion,
        capabilities = granted,
    )

    private fun upsertRecord(
        manifest: PluginManifest,
        packagePath: String,
        state: PluginState,
        errorMessage: String?,
    ) {
        val record = PluginRecord(manifest, packagePath, state, errorMessage)
        val current = _plugins.value.toMutableList()
        val index = current.indexOfFirst { it.manifest.id == manifest.id }
        if (index >= 0) current[index] = record else current += record
        _plugins.value = current
    }

    private fun updateState(pluginId: String, transform: (PluginRecord) -> PluginRecord) {
        _plugins.value = _plugins.value.map { record ->
            if (record.manifest.id == pluginId) transform(record) else record
        }
    }
}

/**
 * 类加载器句柄。
 *
 * 统一「外部插件（真实 [PluginClassLoader]）」与「内置插件（无独立加载器）」
 * 两种情况的资源释放路径，使 [PluginManager.unload] 无需分支判断。
 */
private sealed interface PluginClassLoaderHandle {
    fun close()
}

/** 包装外部插件的类加载器。 */
private class ExternalClassLoaderHandle(
    private val loader: PluginClassLoader,
) : PluginClassLoaderHandle {
    override fun close() = loader.close()
}

/** 内置插件不需要释放类加载器，保持空实现即可。 */
private class NoOpClassLoaderHandle : PluginClassLoaderHandle {
    override fun close() = Unit
}

// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.mtmanager.plugin

import android.app.Application
import com.mtopensource.common.utils.Logger
import com.mtopensource.plugin.bridge.PluginRecord
import com.mtopensource.plugin.bridge.PluginState
import com.mtopensource.plugin.host.PluginManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 插件运行时门面。
 *
 * 职责是把「插件来自哪里」这一问题从 [PluginManager] 中剥离出来：
 * - **内置插件**：随主 APK 打包，其入口类直接位于宿主 ClassLoader 中，
 *   无需 DexClassLoader，加载速度快且无安全风险
 * - **外部插件**：用户放置在 `Android/data/<pkg>/files/plugins/` 下的插件包，
 *   由 [PluginManager] 通过独立的 ClassLoader 加载
 *
 * 之所以区分这两类，是因为内置插件是可信的（随应用一起签名分发），
 * 而外部插件需要类加载器隔离。
 */
class PluginRuntime(
    private val application: Application,
    private val bridge: PluginHostBridge,
) {

    private companion object {
        const val TAG = "PluginRuntime"
    }

    private val manager = PluginManager(
        services = bridge,
        hostClassLoader = PluginRuntime::class.java.classLoader
            ?: ClassLoader.getSystemClassLoader(),
    )

    /** 插件状态流，UI 直接订阅。 */
    val plugins: StateFlow<List<PluginRecord>> get() = manager.plugins

    private val initMutex = Mutex()
    private var initialized = false

    /** 内置插件描述信息（资源路径 + 入口类名）。 */
    private val builtinPlugins = listOf(
        BuiltinPlugin(
            id = "com.mtopensource.plugin.apkeditor",
            name = "APK 编辑器",
            version = "0.1.0",
            description = "APK 签名校验、包信息解析与重打包",
            entryClass = "com.mtopensource.plugins.apkeditor.ApkEditorPlugin",
            capabilities = setOf("APK_EDIT", "CONTEXT_MENU", "APP_DATA"),
        ),
        BuiltinPlugin(
            id = "com.mtopensource.plugin.dexeditor",
            name = "DEX 编辑器",
            version = "0.1.0",
            description = "解析 DEX 文件结构，查看方法/类数量与 smali 结果",
            entryClass = "com.mtopensource.plugins.dexeditor.DexEditorPlugin",
            capabilities = setOf("DEX_EDIT", "CONTEXT_MENU", "APP_DATA"),
        ),
        BuiltinPlugin(
            id = "com.mtopensource.plugin.rootsupport",
            name = "Root 支持",
            version = "0.1.0",
            description = "以 root 权限浏览与修改系统分区文件",
            entryClass = "com.mtopensource.plugins.rootsupport.RootSupportPlugin",
            capabilities = setOf("ROOT_FILESYSTEM", "CUSTOM_FILESYSTEM", "APP_DATA"),
        ),
        BuiltinPlugin(
            id = "com.mtopensource.plugin.remotestorage",
            name = "远程存储",
            version = "0.1.0",
            description = "通过 FTP / SFTP / WebDAV 挂载远程目录",
            entryClass = "com.mtopensource.plugins.remotestorage.RemoteStoragePlugin",
            capabilities = setOf("REMOTE_STORAGE", "CUSTOM_FILESYSTEM", "NETWORK", "APP_DATA"),
        ),
    )

    /**
     * 初始化插件系统。
     *
     * 幂等：重复调用只执行一次。
     * 整个过程在 IO 线程完成，避免阻塞冷启动。
     */
    suspend fun initialize() {
        initMutex.withLock {
            if (initialized) return
            initialized = true
        }

        withContext(Dispatchers.IO) {
            try {
                ensureExternalPluginDir()

                // 外部插件：扫描用户目录
                manager.scan(bridge.externalPluginRoot)
                Logger.i(TAG, "外部插件扫描完成，共 ${manager.plugins.value.size} 个")

                // 内置插件：直接注册并激活
                val builtinCount = loadBuiltinPlugins()
                Logger.i(TAG, "内置插件激活 $builtinCount 个")
            } catch (e: Exception) {
                Logger.e(TAG, "插件系统初始化失败：${e.message}", e)
            }
        }
    }

    /** 加载全部内置插件，返回成功数量。 */
    private suspend fun loadBuiltinPlugins(): Int {
        var success = 0
        builtinPlugins.forEach { plugin ->
            val ok = manager.loadBuiltin(plugin.toManifest(), plugin.entryClass)
            if (ok) success++ else Logger.w(TAG, "内置插件加载失败：${plugin.id}")
        }
        return success
    }

    /** 确保外部插件目录存在，便于用户自行放入插件包。 */
    private fun ensureExternalPluginDir() {
        val dir = File(bridge.externalPluginRoot)
        if (!dir.exists() && !dir.mkdirs()) {
            Logger.w(TAG, "无法创建外部插件目录：${dir.absolutePath}")
        }
    }

    /**
     * 重新扫描外部插件目录。
     */
    suspend fun rescan(): List<PluginRecord> = withContext(Dispatchers.IO) {
        manager.scan(bridge.externalPluginRoot, autoLoad = true)
        manager.plugins.value
    }

    /** 加载单个外部插件包。 */
    suspend fun loadExternal(packagePath: String): Boolean =
        withContext(Dispatchers.IO) { manager.load(packagePath) != null }

    /** 停用某个插件。 */
    suspend fun unload(pluginId: String): Boolean =
        withContext(Dispatchers.IO) { manager.unload(pluginId) }

    /** 停用全部插件（应用退出时调用）。 */
    suspend fun unloadAll() {
        withContext(Dispatchers.IO) { manager.unloadAll() }
    }

    /** 把当前配置变化通知给插件。 */
    suspend fun notifyConfigurationChanged() {
        withContext(Dispatchers.Default) {
            manager.notifyConfigurationChanged(bridge.configuration)
        }
    }

    /** 查询某插件当前是否处于激活态。 */
    fun isActive(pluginId: String): Boolean = manager.activePlugin(pluginId) != null

    /** 收集所有插件注入的右键菜单项。 */
    fun contextMenuItems() = manager.collectContextMenus()

    /** 内置插件在代码中的静态描述。 */
    private data class BuiltinPlugin(
        val id: String,
        val name: String,
        val version: String,
        val description: String,
        val entryClass: String,
        val capabilities: Set<String>,
    ) {
        /**
         * 转换为标准 [PluginManifest]。
         *
         * 内置插件的 `packagePath` 用 `builtin:<id>` 标记，便于在 UI 上区分来源，
         * 也避免它们被误当作磁盘文件处理。
         */
        fun toManifest() = com.mtopensource.plugin.bridge.PluginManifest(
            id = id,
            name = name,
            version = version,
            author = "MT-Manager-OpenSource Contributors",
            description = description,
            entryClass = entryClass,
            requiredApiVersion = 1,
            capabilities = capabilities,
        )
    }
}

/** 该插件是否随宿主一起分发的内置插件。 */
val PluginRecord.isBuiltin: Boolean get() = packagePath.startsWith(BUILTIN_PATH_PREFIX)

/** 插件是否处于激活态。 */
val PluginRecord.isActive: Boolean get() = state == PluginState.ACTIVE

/** 内置插件伪路径的前缀，与 PluginManager 中的约定保持一致。 */
private const val BUILTIN_PATH_PREFIX = "builtin:"

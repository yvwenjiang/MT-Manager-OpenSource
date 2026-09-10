// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.plugin.host

import com.mtopensource.filemanager.filesystem.FileSystemProvider
import com.mtopensource.filemanager.filesystem.FileSystemRouter
import com.mtopensource.plugin.api.HostAction
import com.mtopensource.plugin.api.PluginCapability
import com.mtopensource.plugin.api.PluginConfiguration
import com.mtopensource.plugin.api.PluginContext
import com.mtopensource.plugin.api.PluginInfo
import com.mtopensource.plugin.api.PluginLogger
import com.mtopensource.plugin.api.PluginMenuItem
import com.mtopensource.plugin.api.PrefixedPluginLogger

/**
 * 宿主能力提供者。
 *
 * 宿主（app 模块）实现该接口，向插件暴露受控的系统能力。
 * 通过接口隔离，插件管理器无需依赖任何 Android UI 组件，
 * 从而可被单元测试直接驱动。
 */
interface HostServices {

    /** 全局文件系统路由表。 */
    val fileSystemRouter: FileSystemRouter

    /** 当前配置快照。 */
    val configuration: PluginConfiguration

    /**
     * 查询宿主是否具备某项能力。
     *
     * 例如 [PluginCapability.ROOT_FILESYSTEM] 取决于设备是否已 Root。
     */
    fun hasCapability(capability: PluginCapability): Boolean

    /** 执行宿主动作（提示、打开编辑器等）。 */
    fun performAction(action: HostAction)

    /** 插件私有数据目录。 */
    fun pluginDataDir(pluginId: String): String

    /** 插件缓存目录。 */
    fun pluginCacheDir(pluginId: String): String

    /** 插件优化目录（Android 上供 DexClassLoader 使用）。 */
    fun pluginOptimizedDir(pluginId: String): String
}

/**
 * 插件运行时上下文实现。
 *
 * 每个已加载插件持有一个实例，负责把插件的调用转发给 [HostServices]，
 * 同时做能力校验，防止插件越权。
 */
class HostedPluginContext(
    override val pluginInfo: PluginInfo,
    private val services: HostServices,
    /** 该插件已获授权的能力集合。 */
    private val grantedCapabilities: Set<PluginCapability>,
) : PluginContext {

    override val configuration: PluginConfiguration get() = services.configuration

    override val dataDir: String by lazy { services.pluginDataDir(pluginInfo.id) }

    override val cacheDir: String by lazy { services.pluginCacheDir(pluginInfo.id) }

    override val logger: PluginLogger = PrefixedPluginLogger(pluginInfo.id)

    /** 本插件注册过的文件系统 scheme，卸载时用于回滚。 */
    private val registeredSchemes = mutableSetOf<String>()

    /** 本插件注册过的菜单项 id，卸载时用于回滚。 */
    private val registeredMenuItems = mutableSetOf<String>()

    override fun registerFileSystem(provider: FileSystemProvider): Boolean {
        if (!requireCapability(PluginCapability.CUSTOM_FILESYSTEM)) return false

        // scheme 不允许与已有实现冲突，避免插件覆盖宿主的本地文件系统
        val existing = services.fileSystemRouter.registeredSchemes()
        if (provider.scheme.lowercase() in existing) {
            logger.warn("文件系统 scheme 已被占用，注册被拒绝：${provider.scheme}")
            return false
        }

        services.fileSystemRouter.register(provider)
        registeredSchemes += provider.scheme.lowercase()
        logger.info("已注册文件系统：${provider.scheme}")
        return true
    }

    override fun unregisterFileSystem(scheme: String) {
        services.fileSystemRouter.unregister(scheme)
        registeredSchemes -= scheme.lowercase()
    }

    override fun registerContextMenuItem(item: PluginMenuItem): Boolean {
        if (!requireCapability(PluginCapability.CONTEXT_MENU)) return false

        // 加插件前缀，避免不同插件的 id 冲突
        val qualifiedId = "${pluginInfo.id}:${item.id}"
        val previous = menuRegistry.putIfAbsent(qualifiedId, item.copy(id = qualifiedId))
        if (previous != null) {
            logger.warn("菜单项 id 重复：$qualifiedId")
            return false
        }
        registeredMenuItems += qualifiedId
        return true
    }

    override fun unregisterContextMenuItem(id: String) {
        val qualifiedId = if (id.startsWith("${pluginInfo.id}:")) id else "${pluginInfo.id}:$id"
        menuRegistry.remove(qualifiedId)
        registeredMenuItems -= qualifiedId
    }

    override fun hasHostCapability(capability: PluginCapability): Boolean = services.hasCapability(capability)

    override fun performHostAction(action: HostAction) {
        // 打开编辑器需要 APP_DATA / 基础读写能力
        val required = when (action) {
            is HostAction.OpenInEditor -> PluginCapability.APP_DATA
            else -> null
        }
        if (required != null && !requireCapability(required)) return
        services.performAction(action)
    }

    override fun reportProgress(message: String, percent: Int) {
        logger.debug("进度 $percent% - $message")
    }

    /** 插件卸载时清理其注册的全部资源。 */
    fun cleanup() {
        registeredSchemes.toList().forEach { services.fileSystemRouter.unregister(it) }
        registeredSchemes.clear()

        registeredMenuItems.toList().forEach { menuRegistry.remove(it) }
        registeredMenuItems.clear()
    }

    /** 当前由本插件注册的菜单项（供宿主注入 UI）。 */
    fun registeredMenus(): List<PluginMenuItem> = registeredMenuItems.mapNotNull { menuRegistry[it] }

    /**
     * 能力校验：未声明的能力一律拒绝，并记录告警日志。
     */
    private fun requireCapability(capability: PluginCapability): Boolean {
        if (capability in grantedCapabilities) return true
        logger.warn("插件未声明能力 ${capability.name}，操作被拒绝")
        return false
    }

    private companion object {
        /**
         * 全局菜单项注册表。
         *
         * 放在伴生对象中是因为菜单需要跨插件聚合展示；
         * 键为 `pluginId:itemId`，天然避免冲突。
         */
        val menuRegistry = java.util.concurrent.ConcurrentHashMap<String, PluginMenuItem>()
    }
}

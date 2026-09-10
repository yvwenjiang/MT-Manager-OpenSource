// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.mtmanager.plugin

import android.app.Application
import com.mtopensource.common.utils.Logger
import com.mtopensource.filemanager.filesystem.FileSystemRouter
import com.mtopensource.mtmanager.data.SettingsRepository
import com.mtopensource.mtmanager.data.ThemeMode
import com.mtopensource.plugin.api.HostAction
import com.mtopensource.plugin.api.PluginCapability
import com.mtopensource.plugin.api.PluginConfiguration
import com.mtopensource.plugin.host.HostServices
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File

/**
 * [HostServices] 的 Android 实现。
 *
 * 把插件的请求翻译为具体的系统操作，并在此集中做能力校验与目录隔离：
 * - 每个插件的私有目录位于 `filesDir/plugins/<id>/`，插件之间无法互相访问
 * - [hasCapability] 根据真实设备状态作答（例如是否已 Root）
 */
class PluginHostBridge(
    private val application: Application,
    private val router: FileSystemRouter,
    private val settingsRepository: SettingsRepository,
) : HostServices {

    private companion object {
        const val TAG = "PluginHostBridge"

        /** `su` 二进制的常见位置。 */
        val SU_PATHS = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/magisk/.core/bin/su",
        )
    }

    override val fileSystemRouter: FileSystemRouter get() = router

    override val configuration: PluginConfiguration
        get() = PluginConfiguration(
            language = java.util.Locale.getDefault().toLanguageTag(),
            darkTheme = settingsRepository.themeMode.value == ThemeMode.DARK,
            batchModeEnabled = false,
        )

    /**
     * 宿主动作事件流。
     *
     * 插件调用 [performAction] 时把动作投递到这里，
     * 由 UI 层订阅并呈现（提示条、对话框等）。
     * 使用 `replay = 0` 避免 UI 重建时重复消费历史动作。
     */
    private val _actions = MutableSharedFlow<HostAction>(extraBufferCapacity = 16)
    val actions: SharedFlow<HostAction> = _actions.asSharedFlow()

    /**
     * 设备是否已 Root。
     *
     * 判定方式：检查常见路径下是否存在可执行的 `su`。
     * 真正的授权确认由 Root 支持插件在执行命令时完成，
     * 这里只做粗粒度判定，避免每次查询都 fork 一个进程。
     */
    private val isDeviceRooted: Boolean by lazy {
        SU_PATHS.any { path -> File(path).let { it.exists() && it.canExecute() } }
    }

    override fun hasCapability(capability: PluginCapability): Boolean = when (capability) {
        // Root 能力完全取决于设备状态
        PluginCapability.ROOT_FILESYSTEM -> isDeviceRooted

        // 远程存储与网络能力在本应用中始终可用
        PluginCapability.REMOTE_STORAGE,
        PluginCapability.NETWORK,
        -> true

        // 其余能力属于纯软件能力，宿主默认提供
        PluginCapability.APK_EDIT,
        PluginCapability.DEX_EDIT,
        PluginCapability.APP_DATA,
        PluginCapability.CUSTOM_FILESYSTEM,
        PluginCapability.CONTEXT_MENU,
        -> true
    }

    override fun performAction(action: HostAction) {
        val accepted = _actions.tryEmit(action)
        if (!accepted) {
            Logger.w(TAG, "宿主动作队列已满，丢弃动作：$action")
        }
    }

    override fun pluginDataDir(pluginId: String): String = pluginDir(pluginId, "data")

    override fun pluginCacheDir(pluginId: String): String = pluginDir(pluginId, "cache")

    override fun pluginOptimizedDir(pluginId: String): String = pluginDir(pluginId, "opt")

    /**
     * 计算插件私有目录，并确保上级目录存在。
     *
     * `pluginId` 会被做安全化处理，防止插件通过构造恶意 id（如 `../../`）
     * 逃逸出宿主为它划定的目录。
     */
    private fun pluginDir(pluginId: String, category: String): String {
        val safeId = sanitizePluginId(pluginId)
        val dir = File(application.filesDir, "plugins/$category/$safeId")

        if (!dir.exists() && !dir.mkdirs()) {
            Logger.w(TAG, "无法创建插件目录：${dir.absolutePath}")
        }
        return dir.absolutePath
    }

    /** 把插件 id 归一化为安全的目录名。 */
    private fun sanitizePluginId(pluginId: String): String {
        val sanitized = pluginId
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('.', '_')
        return sanitized.ifEmpty { "unknown_plugin" }
    }

    /** 插件内置目录的根路径（随应用卸载一并清除）。 */
    val builtinPluginRoot: String
        get() = File(application.filesDir, "plugins").absolutePath

    /** 从外部存储加载插件的目录（用户手动放置插件包的位置）。 */
    val externalPluginRoot: String
        get() = File(application.getExternalFilesDir(null), "plugins").absolutePath
}

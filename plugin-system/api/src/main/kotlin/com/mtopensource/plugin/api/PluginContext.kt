// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.plugin.api

import com.mtopensource.common.utils.Logger
import com.mtopensource.filemanager.filesystem.FileSystemProvider

/**
 * 宿主提供给插件的运行时上下文。
 *
 * 这是插件与宿主交互的唯一入口，插件不允许直接依赖宿主的内部实现，
 * 从而保证插件在宿主版本升级时不轻易失效。
 */
interface PluginContext {

    /** 当前插件自身的元信息。 */
    val pluginInfo: PluginInfo

    /** 当前配置（语言、主题等）。 */
    val configuration: PluginConfiguration

    /** 插件专属的私有数据目录（可读写）。 */
    val dataDir: String

    /** 插件专属的缓存目录。 */
    val cacheDir: String

    /** 日志器，日志会带上插件 id 前缀便于排查。 */
    val logger: PluginLogger

    /**
     * 注册自定义文件系统。
     *
     * @param provider 提供者，其 scheme 不能被宿主或其他插件已占用
     * @return 注册成功返回 true；scheme 冲突返回 false
     */
    fun registerFileSystem(provider: FileSystemProvider): Boolean

    /** 注销此前注册的文件系统。 */
    fun unregisterFileSystem(scheme: String)

    /**
     * 注册文件列表的上下文菜单项。
     *
     * @param item 菜单项定义
     * @return 注册成功返回 true
     */
    fun registerContextMenuItem(item: PluginMenuItem): Boolean

    /** 注销上下文菜单项。 */
    fun unregisterContextMenuItem(id: String)

    /** 查询宿主是否具备某项能力（例如设备是否已 Root）。 */
    fun hasHostCapability(capability: PluginCapability): Boolean

    /**
     * 发起一次宿主支持的交互动作（例如弹出 Toast、打开文件选择器）。
     *
     * 采用字符串信令而非强类型回调，以免 API 版本演进时破坏二进制兼容。
     */
    fun performHostAction(action: HostAction)

    /**
     * 遍历宿主的插件上下文时传入的进度提示。
     */
    fun reportProgress(message: String, percent: Int) = Unit
}

/**
 * 插件日志器。
 */
interface PluginLogger {
    fun debug(message: String)

    fun info(message: String)

    fun warn(message: String, throwable: Throwable? = null)

    fun error(message: String, throwable: Throwable? = null)
}

/**
 * 默认日志器实现：为日志加上插件前缀。
 */
class PrefixedPluginLogger(private val pluginId: String) : PluginLogger {

    private val tag = "Plugin/$pluginId"

    override fun debug(message: String) = Logger.d(tag, message)

    override fun info(message: String) = Logger.i(tag, message)

    override fun warn(message: String, throwable: Throwable?) {
        if (throwable == null) Logger.w(tag, message) else Logger.w(tag, message, throwable)
    }

    override fun error(message: String, throwable: Throwable?) {
        if (throwable == null) Logger.e(tag, message) else Logger.e(tag, message, throwable)
    }
}

/**
 * 插件可注入的上下文菜单项。
 */
data class PluginMenuItem(
    /** 菜单项唯一 id（在插件内唯一即可，宿主会自动加插件前缀）。 */
    val id: String,
    /** 展示标题。 */
    val title: String,
    /** 排序权重，数值越小越靠前。 */
    val order: Int = 100,
    /**
     * 该菜单项适用的文件类型；
     * 为空表示对任意文件均显示。
     */
    val applicableExtensions: Set<String> = emptySet(),
    /** 是否仅对目录显示。 */
    val directoriesOnly: Boolean = false,
    /** 点击回调，参数为被选中的路径列表。 */
    val onClick: (List<String>) -> Unit,
)

/**
 * 宿主动作类型。
 */
sealed class HostAction {

    /** 显示提示信息。 */
    data class ShowToast(val message: String) : HostAction()

    /** 打开内置文本编辑器。 */
    data class OpenInEditor(val path: String) : HostAction()

    /** 在默认文件管理器中定位到某个路径。 */
    data class RevealInFileManager(val path: String) : HostAction()

    /** 请求刷新当前文件列表。 */
    object RefreshFileList : HostAction()

    /** 自定义动作，由宿主按 [key] 分发。 */
    data class Custom(val key: String, val payload: Map<String, String> = emptyMap()) : HostAction()
}

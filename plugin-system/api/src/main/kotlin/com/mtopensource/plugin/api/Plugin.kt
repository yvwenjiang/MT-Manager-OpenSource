// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.plugin.api

/**
 * 插件元信息。
 *
 * 每个插件必须暴露唯一的 [id]，宿主据此管理生命周期。
 */
data class PluginInfo(
    /** 全局唯一标识，建议使用反向域名，例如 `com.example.apkeditor`。 */
    val id: String,
    /** 展示名称。 */
    val name: String,
    /** 插件版本（语义化版本）。 */
    val version: String,
    /** 插件作者。 */
    val author: String = "",
    /** 功能描述。 */
    val description: String = "",
    /** 依赖的宿主 API 最低版本，用于兼容性校验。 */
    val requiredApiVersion: Int = PluginApi.API_VERSION,
    /** 插件声明的能力集合。 */
    val capabilities: Set<PluginCapability> = emptySet(),
)

/**
 * 插件能力声明。
 *
 * 宿主通过能力声明做权限控制与菜单注入，
 * 避免插件获得未声明的系统能力。
 */
enum class PluginCapability {
    /** 解析/编辑 APK。 */
    APK_EDIT,

    /** 解析/编辑 DEX。 */
    DEX_EDIT,

    /** 访问 Root 文件系统。 */
    ROOT_FILESYSTEM,

    /** 访问远程存储（FTP/SFTP/SMB/WebDAV）。 */
    REMOTE_STORAGE,

    /** 读写应用私有目录。 */
    APP_DATA,

    /** 发起网络请求。 */
    NETWORK,

    /** 注册自定义文件系统 scheme。 */
    CUSTOM_FILESYSTEM,

    /** 向文件列表注入自定义菜单项。 */
    CONTEXT_MENU,
}

/**
 * 插件生命周期接口。
 *
 * 实现类需保留无参构造函数，宿主通过反射实例化。
 */
interface Plugin {

    /** 插件元信息。 */
    val info: PluginInfo

    /**
     * 插件被加载时调用。
     *
     * 此时可以注册文件系统、菜单项、事件监听等。
     */
    fun onLoad(context: PluginContext)

    /**
     * 插件被卸载时调用，用于释放资源。
     */
    fun onUnload() = Unit

    /** 宿主语言/主题等配置变化时回调。 */
    fun onConfigurationChanged(config: PluginConfiguration) = Unit
}

/**
 * 插件配置快照。
 */
data class PluginConfiguration(
    /** 界面语言，例如 `zh-CN`、`en-US`。 */
    val language: String = "zh-CN",
    /** 是否处于暗色主题。 */
    val darkTheme: Boolean = false,
    /** 全局批量操作是否默认启用。 */
    val batchModeEnabled: Boolean = false,
)

/**
 * 宿主 API 版本常量。
 */
object PluginApi {
    /**
     * 当前 API 版本。
     *
     * 破坏性变更时递增；插件通过 [PluginInfo.requiredApiVersion] 声明依赖。
     */
    const val API_VERSION: Int = 1
}

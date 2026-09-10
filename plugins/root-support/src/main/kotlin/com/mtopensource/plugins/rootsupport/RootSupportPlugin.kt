// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.plugins.rootsupport

import com.mtopensource.plugin.api.HostAction
import com.mtopensource.plugin.api.Plugin
import com.mtopensource.plugin.api.PluginCapability
import com.mtopensource.plugin.api.PluginContext
import com.mtopensource.plugin.api.PluginInfo

/**
 * Root 支持插件。
 *
 * 加载时探测设备 Root 状态，并注册 `root://` 文件系统，
 * 使宿主文件管理器能够浏览 `/system`、`/data/data` 等受保护目录。
 */
class RootSupportPlugin : Plugin {

    override val info: PluginInfo = PluginInfo(
        id = PLUGIN_ID,
        name = "Root 支持",
        version = "0.1.0",
        author = "MT-Manager-OpenSource Contributors",
        description = "以 root 权限浏览与修改系统分区文件",
        capabilities = setOf(
            PluginCapability.ROOT_FILESYSTEM,
            PluginCapability.CUSTOM_FILESYSTEM,
            PluginCapability.APP_DATA,
        ),
    )

    private var context: PluginContext? = null

    /** Root 是否可用，供外部查询。 */
    var isRootAvailable: Boolean = false
        private set

    override fun onLoad(context: PluginContext) {
        this.context = context

        // 宿主是否具备 Root 能力是前置条件：设备未 Root 时直接提示并放弃注册
        if (!context.hasHostCapability(PluginCapability.ROOT_FILESYSTEM)) {
            context.logger.warn("宿主机未授予 Root 能力，插件功能不可用")
            context.performHostAction(HostAction.ShowToast("设备未获得 Root 权限，Root 功能不可用"))
            return
        }

        val shell = RootShell()
        isRootAvailable = shell.isRootAvailable()

        if (!isRootAvailable) {
            context.logger.warn("su 不可用，未注册 root 文件系统")
            context.performHostAction(HostAction.ShowToast("未检测到可用的 su，Root 功能不可用"))
            return
        }

        val registered = context.registerFileSystem(RootFileSystemProvider(shell))
        if (registered) {
            context.logger.info("root 文件系统注册成功")
        } else {
            context.logger.error("root 文件系统注册失败（scheme 冲突）")
        }
    }

    override fun onUnload() {
        // 文件系统的注销由宿主在卸载时统一回滚，这里无需重复处理
        context?.logger?.info("Root 支持插件已卸载")
        context = null
    }

    companion object {
        const val PLUGIN_ID = "com.mtopensource.plugin.rootsupport"
    }
}

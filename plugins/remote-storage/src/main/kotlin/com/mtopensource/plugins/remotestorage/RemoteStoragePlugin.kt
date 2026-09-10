// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.plugins.remotestorage

import com.mtopensource.plugin.api.HostAction
import com.mtopensource.plugin.api.Plugin
import com.mtopensource.plugin.api.PluginCapability
import com.mtopensource.plugin.api.PluginContext
import com.mtopensource.plugin.api.PluginInfo
import com.mtopensource.plugin.api.PluginMenuItem

/**
 * 远程存储插件。
 *
 * 注册 `remote://` 文件系统，每个连接映射为 `remote://<连接名>/<远端路径>`。
 *
 * 本期实现范围：
 * - 连接的增删改查与校验（[RemoteConnection]）
 * - 连接配置的持久化（[RemoteConnectionStore]）
 * - 向宿主注册 `remote://` scheme 与相关菜单项
 *
 * 实际的 FTP/SFTP 协议实现需要引入第三方库（如 commons-net / jsch），
 * 属于后续迭代；[RemoteFileSystemProvider] 已按接口预留实现位置。
 */
class RemoteStoragePlugin : Plugin {

    override val info: PluginInfo = PluginInfo(
        id = PLUGIN_ID,
        name = "远程存储",
        version = "0.1.0",
        author = "MT-Manager-OpenSource Contributors",
        description = "通过 FTP / SFTP / WebDAV 挂载远程目录",
        capabilities = setOf(
            PluginCapability.REMOTE_STORAGE,
            PluginCapability.CUSTOM_FILESYSTEM,
            PluginCapability.NETWORK,
            PluginCapability.APP_DATA,
        ),
    )

    private var context: PluginContext? = null

    /** 连接配置存储。 */
    private var store: RemoteConnectionStore? = null

    override fun onLoad(context: PluginContext) {
        this.context = context
        this.store = RemoteConnectionStore(context.dataDir)

        context.logger.info("远程存储插件已加载，配置目录：${context.dataDir}")

        val registered = context.registerFileSystem(RemoteFileSystemProvider(store!!))
        if (!registered) {
            context.logger.error("remote 文件系统注册失败（scheme 冲突）")
            return
        }

        context.registerContextMenuItem(
            PluginMenuItem(
                id = "manage-connections",
                title = "管理远程连接",
                order = 200,
                onClick = { showConnectionList() },
            ),
        )
    }

    override fun onUnload() {
        context?.logger?.info("远程存储插件已卸载")
        store = null
        context = null
    }

    /** 生成当前所有连接的摘要，供宿主展示。 */
    private fun showConnectionList() {
        val ctx = context ?: return
        val connections = store?.loadAll().orEmpty()

        val content = if (connections.isEmpty()) {
            "尚未配置任何远程连接。\n\n支持的协议：\n" +
                RemoteProtocol.entries.joinToString("\n") { "  · ${it.displayName}" }
        } else {
            connections.joinToString("\n\n") { connection ->
                buildString {
                    appendLine("名称：${connection.name}")
                    appendLine("地址：${connection.displayAddress()}")
                    appendLine("认证：${connection.authType.name}")
                    appendLine("初始路径：${connection.normalizedInitialPath()}")
                    append("状态：${if (connection.isComplete) "配置完整" else connection.validationError()}")
                }
            }
        }

        ctx.performHostAction(
            HostAction.Custom(
                key = "show-text-dialog",
                payload = mapOf("title" to "远程连接（${connections.size}）", "content" to content),
            ),
        )
    }

    companion object {
        const val PLUGIN_ID = "com.mtopensource.plugin.remotestorage"
    }
}

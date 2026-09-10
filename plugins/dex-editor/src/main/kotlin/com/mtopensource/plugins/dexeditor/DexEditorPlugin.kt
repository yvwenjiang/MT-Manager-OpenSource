// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.plugins.dexeditor

import com.mtopensource.plugin.api.HostAction
import com.mtopensource.plugin.api.Plugin
import com.mtopensource.plugin.api.PluginCapability
import com.mtopensource.plugin.api.PluginContext
import com.mtopensource.plugin.api.PluginInfo
import com.mtopensource.plugin.api.PluginMenuItem

/**
 * DEX 编辑器插件。
 *
 * 以「示例插件 + 可扩展骨架」的定位提供：
 * - 在文件列表中为 `.dex` 文件注入「查看 DEX 结构」菜单项
 * - 扫描 DEX 头部信息并把结果通过 [HostAction] 回传给宿主展示
 *
 * 解析逻辑集中在 [DexParser]，与插件生命周期解耦，便于单独测试与后续替换为
 * 完整的 smali 反汇编实现。
 */
class DexEditorPlugin : Plugin {

    override val info: PluginInfo = PluginInfo(
        id = PLUGIN_ID,
        name = "DEX 编辑器",
        version = "0.1.0",
        author = "MT-Manager-OpenSource Contributors",
        description = "解析 DEX 文件结构，支持方法/类列表浏览与 smali 反汇编结果查看",
        capabilities = setOf(
            PluginCapability.DEX_EDIT,
            PluginCapability.CONTEXT_MENU,
            PluginCapability.APP_DATA,
        ),
    )

    private var context: PluginContext? = null

    override fun onLoad(context: PluginContext) {
        this.context = context
        context.logger.info("DEX 编辑器插件已加载")

        context.registerContextMenuItem(
            PluginMenuItem(
                id = "inspect-dex",
                title = "查看 DEX 结构",
                order = 20,
                applicableExtensions = setOf("dex"),
                onClick = { paths -> onInspectRequested(paths) },
            ),
        )
    }

    override fun onUnload() {
        context?.logger?.info("DEX 编辑器插件已卸载")
        context = null
    }

    /**
     * 处理「查看 DEX 结构」点击事件。
     *
     * 仅处理第一个文件：批量查看 DEX 结构在 UI 上意义不大，
     * 且可避免一次点击触发大量 IO。
     */
    private fun onInspectRequested(paths: List<String>) {
        val ctx = context ?: return
        val target = paths.firstOrNull { it.endsWith(".dex", ignoreCase = true) }
        if (target == null) {
            ctx.performHostAction(HostAction.ShowToast("请选择一个 .dex 文件"))
            return
        }

        ctx.reportProgress("正在解析 $target", 10)

        when (val result = DexParser.parseHeader(target)) {
            is DexParseResult.Success -> {
                ctx.reportProgress("解析完成", 100)
                ctx.performHostAction(
                    HostAction.Custom(
                        key = "show-text-dialog",
                        payload = mapOf(
                            "title" to "DEX 结构 - ${target.substringAfterLast('/')}",
                            "content" to result.summary,
                        ),
                    ),
                )
            }

            is DexParseResult.Failure ->
                ctx.performHostAction(HostAction.ShowToast("解析失败：${result.reason}"))
        }
    }

    companion object {
        const val PLUGIN_ID = "com.mtopensource.plugin.dexeditor"
    }
}

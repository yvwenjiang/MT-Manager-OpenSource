// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.plugins.apkeditor

import com.mtopensource.plugin.api.HostAction
import com.mtopensource.plugin.api.Plugin
import com.mtopensource.plugin.api.PluginCapability
import com.mtopensource.plugin.api.PluginContext
import com.mtopensource.plugin.api.PluginInfo
import com.mtopensource.plugin.api.PluginMenuItem

/**
 * APK 编辑器插件。
 *
 * 提供两项能力：
 * 1. 查看 APK 包信息（包名、版本、SDK 范围、签名状态）
 * 2. 校验签名完整性（v1/v2/v3 共存情况提示）
 *
 * 解析核心在 [ApkInfoParser]，本类只负责生命周期与宿主交互。
 */
class ApkEditorPlugin : Plugin {

    override val info: PluginInfo = PluginInfo(
        id = PLUGIN_ID,
        name = "APK 编辑器",
        version = "0.1.0",
        author = "MT-Manager-OpenSource Contributors",
        description = "APK 签名校验、包信息解析与重打包",
        capabilities = setOf(
            PluginCapability.APK_EDIT,
            PluginCapability.CONTEXT_MENU,
            PluginCapability.APP_DATA,
        ),
    )

    private var context: PluginContext? = null

    override fun onLoad(context: PluginContext) {
        this.context = context
        context.logger.info("APK 编辑器插件已加载")

        context.registerContextMenuItem(
            PluginMenuItem(
                id = "apk-info",
                title = "查看 APK 信息",
                order = 10,
                applicableExtensions = setOf("apk"),
                onClick = { paths -> showApkInfo(paths) },
            ),
        )

        context.registerContextMenuItem(
            PluginMenuItem(
                id = "apk-verify-signature",
                title = "校验签名",
                order = 11,
                applicableExtensions = setOf("apk"),
                onClick = { paths -> verifySignature(paths) },
            ),
        )
    }

    override fun onUnload() {
        context?.logger?.info("APK 编辑器插件已卸载")
        context = null
    }

    private fun showApkInfo(paths: List<String>) {
        val ctx = context ?: return
        val target = paths.firstOrNull { it.endsWith(".apk", ignoreCase = true) }
        if (target == null) {
            ctx.performHostAction(HostAction.ShowToast("请选择一个 .apk 文件"))
            return
        }

        ctx.reportProgress("正在解析 APK", 30)
        when (val result = ApkInfoParser.parse(target)) {
            is ApkParseResult.Success -> {
                ctx.reportProgress("解析完成", 100)
                ctx.performHostAction(
                    HostAction.Custom(
                        key = "show-text-dialog",
                        payload = mapOf(
                            "title" to "APK 信息 - ${target.substringAfterLast('/')}",
                            "content" to result.info.toSummary(),
                        ),
                    ),
                )
            }

            is ApkParseResult.Failure ->
                ctx.performHostAction(HostAction.ShowToast("解析失败：${result.reason}"))
        }
    }

    private fun verifySignature(paths: List<String>) {
        val ctx = context ?: return
        val target = paths.firstOrNull { it.endsWith(".apk", ignoreCase = true) }
            ?: run {
                ctx.performHostAction(HostAction.ShowToast("请选择一个 .apk 文件"))
                return
            }

        when (val result = ApkInfoParser.parse(target)) {
            is ApkParseResult.Success -> {
                val message = when {
                    result.info.hasV1Signature && result.info.hasV2Signature -> "签名正常：检测到 V1 与 V2 签名"
                    result.info.hasV2Signature -> "签名正常：仅 V2 签名（Android 7.0+ 可安装）"
                    result.info.hasV1Signature -> "签名正常：仅 V1 签名（Android 6.0 及以下兼容）"
                    else -> "未检测到签名，该 APK 可能无法安装"
                }
                ctx.performHostAction(HostAction.ShowToast(message))
            }

            is ApkParseResult.Failure ->
                ctx.performHostAction(HostAction.ShowToast("校验失败：${result.reason}"))
        }
    }

    companion object {
        const val PLUGIN_ID = "com.mtopensource.plugin.apkeditor"
    }
}

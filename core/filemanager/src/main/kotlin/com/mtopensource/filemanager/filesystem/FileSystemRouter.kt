// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.filemanager.filesystem

import com.mtopensource.common.utils.PathUtils
import com.mtopensource.filemanager.model.FileItem

/**
 * 文件系统路由表。
 *
 * UI 层只持有 [FileSystemRouter]，由它根据路径前缀挑选合适的
 * [FileSystemProvider]（本地 / 压缩包 / Root / 远程插件）。
 * 插件在运行时注册新的 scheme，无需修改 UI 代码。
 */
class FileSystemRouter(
    initialProviders: List<FileSystemProvider> = emptyList(),
) {

    private val providers = LinkedHashMap<String, FileSystemProvider>()

    init {
        initialProviders.forEach { register(it) }
    }

    /** 注册（或覆盖）一个文件系统实现。 */
    fun register(provider: FileSystemProvider) {
        providers[provider.scheme.lowercase()] = provider
    }

    /** 注销文件系统（插件卸载时调用）。 */
    fun unregister(scheme: String) {
        providers.remove(scheme.lowercase())
    }

    /** 当前已注册的所有 scheme。 */
    fun registeredSchemes(): Set<String> = providers.keys.toSet()

    /**
     * 根据路径解析出对应的文件系统。
     *
     * 解析规则：
     * - `xxx://...` 使用显式 scheme；
     * - 其余一律落到 `file`（本地文件系统）。
     */
    fun resolve(path: String): FileSystemProvider {
        val scheme = schemeOf(path)
        return providers[scheme]
            ?: providers[FileItem.SCHEME_FILE]
            ?: throw IllegalStateException("未注册的文件系统 scheme：$scheme")
    }

    /** 从路径中提取 scheme，本地路径返回 `file`。 */
    fun schemeOf(path: String): String {
        val index = path.indexOf("://")
        if (index <= 0) return FileItem.SCHEME_FILE
        return path.substring(0, index).lowercase()
    }

    /** 去除 `scheme://` 前缀，得到供 provider 使用的纯路径。 */
    fun stripScheme(path: String): String {
        val index = path.indexOf("://")
        return if (index <= 0) PathUtils.normalize(path) else path.substring(index + 3)
    }
}

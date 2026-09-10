// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.plugin.bridge

import com.mtopensource.plugin.api.PluginCapability
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

/**
 * 插件描述文件读取与校验工具。
 *
 * 支持从以下载体读取 `plugin.json`：
 * - 目录（开发调试时直接指向 build 输出目录）
 * - JAR / APK / ZIP 压缩包
 *
 * 本类基于 `java.util.zip` 与 `java.io` 实现，不依赖 Android API，
 * 因此插件发现逻辑可在 JVM 单元测试中完整验证。
 */
object PluginDescriptorReader {

    /** 单个 plugin.json 的最大读取长度，防止恶意超大文件耗尽内存。 */
    private const val MAX_MANIFEST_BYTES = 256 * 1024

    /**
     * 从插件载体中读取描述文件。
     *
     * @return 描述模型；载体不存在、缺少 plugin.json 或格式非法时返回 null
     */
    fun read(packagePath: String): PluginManifest? {
        val file = File(packagePath)
        if (!file.exists()) return null

        val json = if (file.isDirectory) {
            readFromDirectory(file)
        } else {
            readFromArchive(file)
        } ?: return null

        return PluginManifest.parse(json)
    }

    /**
     * 校验插件是否可被当前宿主加载。
     *
     * @return 校验通过返回 null，否则返回面向用户的错误描述
     */
    fun validate(manifest: PluginManifest, hostApiVersion: Int = com.mtopensource.plugin.api.PluginApi.API_VERSION): String? {
        if (manifest.id.isBlank()) return "插件缺少 id"
        if (manifest.entryClass.isBlank()) return "插件缺少入口类 entryClass"
        if (manifest.requiredApiVersion > hostApiVersion) {
            return "插件「${manifest.name}」要求 API v${manifest.requiredApiVersion}，" +
                "当前宿主为 v$hostApiVersion"
        }
        val unknownCapabilities = manifest.capabilities.filterNot { it in KNOWN_CAPABILITIES }
        if (unknownCapabilities.isNotEmpty()) {
            return "插件声明了未知能力：${unknownCapabilities.joinToString()}"
        }
        return null
    }

    /** 解析能力字符串为枚举集合，未知项会被忽略。 */
    fun parseCapabilities(raw: Set<String>): Set<PluginCapability> =
        raw.mapNotNull { name -> PluginCapability.entries.firstOrNull { it.name == name } }.toSet()

    private val KNOWN_CAPABILITIES: Set<String> = PluginCapability.entries.map { it.name }.toSet()

    private fun readFromDirectory(dir: File): String? {
        val manifest = File(dir, PluginManifest.FILE_NAME)
        if (!manifest.isFile) return null
        if (manifest.length() > MAX_MANIFEST_BYTES) return null
        return runCatching { manifest.readText(StandardCharsets.UTF_8) }.getOrNull()
    }

    private fun readFromArchive(archive: File): String? = runCatching {
        ZipFile(archive).use { zip ->
            val entry = zip.getEntry(PluginManifest.FILE_NAME)
                ?: zip.entries().asSequence().firstOrNull { it.name.endsWith("/${PluginManifest.FILE_NAME}") }
                ?: return null
            if (entry.size > MAX_MANIFEST_BYTES) return null
            zip.getInputStream(entry).use { input ->
                InputStreamReader(input, StandardCharsets.UTF_8).readText()
            }
        }
    }.getOrNull()

    /**
     * 扫描目录下所有插件载体。
     *
     * @param directory 插件存放目录
     * @return 解析成功的插件记录列表，附带各自的加载状态
     */
    fun scanDirectory(
        directory: String,
        hostApiVersion: Int = com.mtopensource.plugin.api.PluginApi.API_VERSION,
    ): List<PluginRecord> {
        val dir = File(directory)
        if (!dir.isDirectory) return emptyList()

        return dir.listFiles()
            ?.filter { it.isDirectory || it.extension.lowercase() in SUPPORTED_ARCHIVE_EXTENSIONS }
            ?.mapNotNull { candidate ->
                val manifest = read(candidate.absolutePath) ?: return@mapNotNull null
                val error = validate(manifest, hostApiVersion)
                PluginRecord(
                    manifest = manifest,
                    packagePath = candidate.absolutePath,
                    state = if (error == null) PluginState.DISCOVERED else PluginState.INCOMPATIBLE,
                    errorMessage = error,
                )
            }
            ?.sortedBy { it.manifest.name }
            ?: emptyList()
    }

    private val SUPPORTED_ARCHIVE_EXTENSIONS = setOf("apk", "jar", "zip", "dex")
}

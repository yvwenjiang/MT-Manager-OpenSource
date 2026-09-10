// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.plugin.bridge

import com.mtopensource.plugin.api.PluginApi
import org.json.JSONObject

/**
 * 插件描述文件（`plugin.json`）对应的数据模型。
 *
 * 每个插件包（APK 或 jar）根目录需包含该文件：
 * ```json
 * {
 *   "id": "com.example.apkeditor",
 *   "name": "APK 编辑器",
 *   "version": "1.0.0",
 *   "author": "Example",
 *   "description": "签名校验与重打包",
 *   "entryClass": "com.example.apkeditor.ApkEditorPlugin",
 *   "requiredApiVersion": 1,
 *   "capabilities": ["APK_EDIT", "APP_DATA"]
 * }
 * ```
 */
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    /** 插件入口类，必须实现 [com.mtopensource.plugin.api.Plugin]。 */
    val entryClass: String,
    val requiredApiVersion: Int,
    val capabilities: Set<String>,
) {

    /** 校验当前宿主是否满足插件要求。 */
    fun isCompatibleWithHost(): Boolean = requiredApiVersion <= PluginApi.API_VERSION

    /** 转为可读的校验错误信息，兼容时返回 null。 */
    fun compatibilityError(): String? = if (isCompatibleWithHost()) {
        null
    } else {
        "插件「$name」需要 API v$requiredApiVersion，当前宿主仅支持 v${PluginApi.API_VERSION}"
    }

    companion object {
        const val FILE_NAME = "plugin.json"

        /**
         * 解析插件描述文件。
         *
         * @return 解析成功返回模型，字段缺失或格式错误返回 null
         */
        fun parse(json: String): PluginManifest? = try {
            val obj = JSONObject(json)
            val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
            val name = obj.optString("name").takeIf { it.isNotBlank() } ?: return null
            val entryClass = obj.optString("entryClass").takeIf { it.isNotBlank() } ?: return null

            val capabilities = obj.optJSONArray("capabilities")?.let { array ->
                (0 until array.length()).mapNotNull { array.optString(it).takeIf { s -> s.isNotBlank() } }.toSet()
            } ?: emptySet()

            PluginManifest(
                id = id,
                name = name,
                version = obj.optString("version", "0.0.0"),
                author = obj.optString("author", ""),
                description = obj.optString("description", ""),
                entryClass = entryClass,
                requiredApiVersion = obj.optInt("requiredApiVersion", PluginApi.API_VERSION),
                capabilities = capabilities,
            )
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * 插件加载状态。
 */
enum class PluginState {
    /** 已发现但尚未加载。 */
    DISCOVERED,

    /** 正在加载。 */
    LOADING,

    /** 已加载并激活。 */
    ACTIVE,

    /** 加载或运行过程中出错。 */
    ERROR,

    /** 已被卸载。 */
    UNLOADED,

    /** 与当前宿主不兼容。 */
    INCOMPATIBLE,
}

/**
 * 已发现插件的运行时记录。
 */
data class PluginRecord(
    val manifest: PluginManifest,
    /** 插件包文件路径。 */
    val packagePath: String,
    val state: PluginState = PluginState.DISCOVERED,
    /** 加载失败时的错误信息。 */
    val errorMessage: String? = null,
)

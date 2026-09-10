// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.plugins.remotestorage

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * 远程连接配置存储。
 *
 * 以 JSON 文件形式保存在插件私有目录下。
 *
 * 说明：密码字段在存储时仅做 Base64 编码（防止肉眼直接可见），
 * **并非加密**。生产环境应改用宿主的加密存储（如 Android Keystore / EncryptedSharedPreferences），
 * 当前实现属于 MVP 阶段的取舍，已在文档中标注。
 */
class RemoteConnectionStore(private val dataDir: String) {

    private val storeFile: File get() = File(dataDir, FILE_NAME)

    /**
     * 读取全部连接配置。
     *
     * 文件不存在或损坏时返回空列表（不抛异常）。
     */
    fun loadAll(): List<RemoteConnection> {
        val file = storeFile
        if (!file.isFile) return emptyList()

        return runCatching {
            val json = file.readText(StandardCharsets.UTF_8)
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { parseConnection(it) }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * 保存全部连接配置（覆盖写）。
     *
     * @return 保存成功返回 true
     */
    fun saveAll(connections: List<RemoteConnection>): Boolean = runCatching {
        storeFile.parentFile?.mkdirs()
        val array = JSONArray()
        connections.forEach { array.put(toJson(it)) }
        storeFile.writeText(array.toString(2), StandardCharsets.UTF_8)
        true
    }.getOrDefault(false)

    /**
     * 新增或更新一个连接（按名称匹配）。
     *
     * @return 更新后的完整列表
     */
    fun upsert(connection: RemoteConnection): List<RemoteConnection> {
        val current = loadAll().toMutableList()
        val index = current.indexOfFirst { it.name == connection.name }
        if (index >= 0) current[index] = connection else current += connection
        saveAll(current)
        return current
    }

    /**
     * 按名称删除连接。
     *
     * @return 删除成功返回 true；未找到返回 false
     */
    fun delete(name: String): Boolean {
        val current = loadAll().toMutableList()
        val removed = current.removeAll { it.name == name }
        if (removed) saveAll(current)
        return removed
    }

    /** 按名称查找连接。 */
    fun find(name: String): RemoteConnection? = loadAll().firstOrNull { it.name == name }

    /** 清空全部配置。 */
    fun clear(): Boolean = runCatching { storeFile.delete() }.getOrDefault(false)

    // ---------------------------------------------------------------------
    // 序列化
    // ---------------------------------------------------------------------

    internal fun toJson(connection: RemoteConnection): JSONObject = JSONObject().apply {
        put("name", connection.name)
        put("protocol", connection.protocol.name)
        put("host", connection.host)
        put("port", connection.port)
        put("authType", connection.authType.name)
        put("username", connection.username)
        // Base64 编码，避免明文直读；真正的加密留待接入 Keystore
        put("password", encode(connection.password))
        put("privateKey", encode(connection.privateKey))
        put("initialPath", connection.initialPath)
        put("passiveMode", connection.passiveMode)
        put("charset", connection.charset)
    }

    internal fun parseConnection(json: JSONObject): RemoteConnection? {
        val name = json.optString("name").takeIf { it.isNotBlank() } ?: return null
        val host = json.optString("host").takeIf { it.isNotBlank() } ?: return null

        val protocol = RemoteProtocol.entries.firstOrNull { it.name == json.optString("protocol") }
            ?: RemoteProtocol.FTP
        val authType = RemoteAuthType.entries.firstOrNull { it.name == json.optString("authType") }
            ?: RemoteAuthType.PASSWORD

        return RemoteConnection(
            name = name,
            protocol = protocol,
            host = host,
            port = json.optInt("port", protocol.defaultPort),
            authType = authType,
            username = json.optString("username", ""),
            password = decode(json.optString("password", "")),
            privateKey = decode(json.optString("privateKey", "")),
            initialPath = json.optString("initialPath", "/"),
            passiveMode = json.optBoolean("passiveMode", true),
            charset = json.optString("charset", "UTF-8"),
        )
    }

    private fun encode(value: String): String =
        if (value.isEmpty()) "" else java.util.Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decode(value: String): String = runCatching {
        if (value.isEmpty()) "" else String(java.util.Base64.getDecoder().decode(value), StandardCharsets.UTF_8)
    }.getOrDefault("")

    companion object {
        const val FILE_NAME = "connections.json"
    }
}

// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.plugins.remotestorage

/**
 * 支持的远程协议。
 */
enum class RemoteProtocol(
    val displayName: String,
    /** 默认端口。 */
    val defaultPort: Int,
    /** 是否为加密连接。 */
    val isSecure: Boolean,
) {
    FTP("FTP", 21, false),
    FTPS("FTPS (隐式 TLS)", 990, true),
    SFTP("SFTP (SSH)", 22, true),
    WEBDAV("WebDAV", 80, false),
    WEBDAV_HTTPS("WebDAV (HTTPS)", 443, true),
    SMB("SMB / CIFS", 445, false),
}

/**
 * 认证方式。
 */
enum class RemoteAuthType {
    /** 用户名 + 密码。 */
    PASSWORD,

    /** 匿名访问（仅 FTP/WebDAV 支持）。 */
    ANONYMOUS,

    /** SSH 私钥（仅 SFTP 支持）。 */
    PRIVATE_KEY,
}

/**
 * 远程连接配置。
 *
 * 注意：`password` 与 `privateKey` 属于敏感字段，
 * 持久化时应交由宿主的加密存储处理，本模型只负责承载。
 */
data class RemoteConnection(
    /** 用户可见的连接名称。 */
    val name: String,
    val protocol: RemoteProtocol,
    val host: String,
    val port: Int = protocol.defaultPort,
    val authType: RemoteAuthType = RemoteAuthType.PASSWORD,
    val username: String = "",
    val password: String = "",
    /** 私钥内容（仅 SFTP + PRIVATE_KEY 使用）。 */
    val privateKey: String = "",
    /** 初始进入的远端目录。 */
    val initialPath: String = "/",
    /** 是否启用被动模式（FTP）。 */
    val passiveMode: Boolean = true,
    /** 字符集（部分老 FTP 服务器需要 GBK）。 */
    val charset: String = "UTF-8",
) {

    /** 该配置是否完备到可以发起连接。 */
    val isComplete: Boolean
        get() = when {
            name.isBlank() -> false
            host.isBlank() -> false
            port !in 1..65535 -> false
            authType == RemoteAuthType.PASSWORD && username.isBlank() -> false
            authType == RemoteAuthType.PRIVATE_KEY && privateKey.isBlank() -> false
            else -> true
        }

    /** 校验并返回错误描述，完备时返回 null。 */
    fun validationError(): String? = when {
        name.isBlank() -> "连接名称不能为空"
        host.isBlank() -> "主机地址不能为空"
        port !in 1..65535 -> "端口号必须在 1-65535 之间"
        authType == RemoteAuthType.PASSWORD && username.isBlank() -> "用户名不能为空"
        authType == RemoteAuthType.PRIVATE_KEY && privateKey.isBlank() -> "私钥内容不能为空"
        authType == RemoteAuthType.PRIVATE_KEY && protocol != RemoteProtocol.SFTP -> "仅 SFTP 支持私钥认证"
        authType == RemoteAuthType.ANONYMOUS && protocol !in ANONYMOUS_PROTOCOLS -> "${protocol.displayName} 不支持匿名访问"
        else -> null
    }

    /** 用于日志展示的脱敏地址。 */
    fun displayAddress(): String = "${protocol.displayName}://$host:$port"

    /** 归一化后的初始路径，保证以 `/` 开头。 */
    fun normalizedInitialPath(): String {
        val trimmed = initialPath.trim()
        if (trimmed.isEmpty()) return "/"
        return if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    }

    companion object {
        private val ANONYMOUS_PROTOCOLS = setOf(RemoteProtocol.FTP, RemoteProtocol.WEBDAV, RemoteProtocol.WEBDAV_HTTPS)
    }
}

/**
 * 连接状态。
 */
sealed class ConnectionState {
    object Disconnected : ConnectionState()

    data class Connecting(val progress: Int = 0) : ConnectionState()

    data class Connected(
        /** 远端根路径。 */
        val rootPath: String,
    ) : ConnectionState()

    data class Failed(val reason: String) : ConnectionState()
}

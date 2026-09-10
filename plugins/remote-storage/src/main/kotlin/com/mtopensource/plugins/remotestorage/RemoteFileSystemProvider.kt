// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.plugins.remotestorage

import com.mtopensource.common.result.AppResult
import com.mtopensource.common.result.FileError
import com.mtopensource.filemanager.filesystem.FileSystemProvider
import com.mtopensource.filemanager.model.FileItem
import java.io.InputStream
import java.io.OutputStream

/**
 * 远程文件系统提供者（scheme = `remote`）。
 *
 * 路径格式：`remote://<连接名>/<远端路径>`
 * 例如 `remote://我的服务器/var/log/app.log`
 *
 * 本期状态：**骨架实现**。
 * 已完成的部分：
 * - scheme 注册与路径解析（[parseRemotePath]）
 * - 连接查找与错误分类
 *
 * 待完成的部分：真正的协议客户端（FTP/SFTP/WebDAV）。
 * 之所以先交付骨架而不引入协议库，是因为：
 * 1. 协议库体积较大，需要与维护者确认选型
 * 2. 宿主与插件的交互契约（凭据存储、进度上报）需要先稳定下来
 *
 * [RemoteTransport] 抽象了传输层，接入任何协议库只需实现该接口。
 */
class RemoteFileSystemProvider(
    private val store: RemoteConnectionStore,
    /** 传输层工厂，未提供时使用默认的空实现。 */
    private val transportFactory: (RemoteConnection) -> RemoteTransport = { UnsupportedTransport(it) },
) : FileSystemProvider {

    override val scheme: String = SCHEME

    /** 是否处于已连接状态（由传输层决定）。 */
    override val isWritable: Boolean get() = false

    override val requiresPermission: Boolean get() = true

    override suspend fun list(path: String): AppResult<List<FileItem>, FileError> {
        val parsed = parseRemotePath(path)
            ?: return AppResult.failure(FileError.IllegalOperation("非法远程路径：$path"))

        val connection = store.find(parsed.connectionName)
            ?: return AppResult.failure(FileError.NotFound("未找到连接配置：${parsed.connectionName}"))

        return transportFactory(connection).list(parsed.remotePath)
    }

    override suspend fun exists(path: String): Boolean {
        val parsed = parseRemotePath(path) ?: return false
        val connection = store.find(parsed.connectionName) ?: return false
        return transportFactory(connection).exists(parsed.remotePath)
    }

    override suspend fun isDirectory(path: String): Boolean = false

    override suspend fun length(path: String): Long = 0L

    override suspend fun lastModified(path: String): Long = 0L

    override suspend fun createFile(path: String): AppResult<Unit, FileError> = notImplemented("创建文件")

    override suspend fun createDirectory(path: String): AppResult<Unit, FileError> = notImplemented("创建目录")

    override suspend fun delete(path: String): AppResult<Unit, FileError> = notImplemented("删除")

    override suspend fun rename(path: String, newName: String): AppResult<Unit, FileError> = notImplemented("重命名")

    override suspend fun copy(
        source: String,
        target: String,
        onProgress: FileSystemProvider.ProgressCallback?,
    ): AppResult<Unit, FileError> = notImplemented("复制")

    override suspend fun move(
        source: String,
        target: String,
        onProgress: FileSystemProvider.ProgressCallback?,
    ): AppResult<Unit, FileError> = notImplemented("移动")

    override suspend fun openInputStream(path: String): AppResult<InputStream, FileError> = notImplemented("读取")

    override suspend fun openOutputStream(path: String): AppResult<OutputStream, FileError> = notImplemented("写入")

    private fun <T> notImplemented(operation: String): AppResult<T, FileError> =
        AppResult.failure(FileError.IllegalOperation("远程$operation 功能尚未实现，等待接入协议库"))

    /**
     * 解析 `remote://<连接名>/<远端路径>`。
     *
     * @return 解析结果；格式非法返回 null
     */
    fun parseRemotePath(path: String): RemotePath? {
        val prefix = "$SCHEME://"
        if (!path.startsWith(prefix)) return null

        val remainder = path.substring(prefix.length)
        if (remainder.isEmpty()) return null

        val connectionName = remainder.substringBefore('/')
        if (connectionName.isEmpty()) return null

        val remotePath = remainder.substringAfter('/', "").let { if (it.isEmpty()) "/" else "/$it" }
        return RemotePath(connectionName, remotePath)
    }

    /** 解析后的远程路径。 */
    data class RemotePath(val connectionName: String, val remotePath: String) {
        /** 还原为完整路径。 */
        fun toFullPath(): String = "$SCHEME://$connectionName${remotePath.removePrefix("/").let { "/$it" }}"

        private companion object {
            const val SCHEME = "remote"
        }
    }

    companion object {
        const val SCHEME = "remote"
    }
}

/**
 * 远程传输层抽象。
 *
 * 接入 FTP/SFTP/WebDAV 时实现本接口即可，
 * 上层 [RemoteFileSystemProvider] 无需改动。
 */
interface RemoteTransport {

    /** 连接并列出目录。 */
    suspend fun list(path: String): AppResult<List<FileItem>, FileError>

    /** 判断远端路径是否存在。 */
    suspend fun exists(path: String): Boolean

    /** 断开连接并释放资源。 */
    fun disconnect()
}

/**
 * 尚未接入协议库时的占位实现。
 *
 * 返回明确的功能未实现提示，而不是静默失败，
 * 便于使用者在界面上得到准确反馈。
 */
class UnsupportedTransport(private val connection: RemoteConnection) : RemoteTransport {

    override suspend fun list(path: String): AppResult<List<FileItem>, FileError> =
        AppResult.failure(
            FileError.IllegalOperation(
                "${connection.protocol.displayName} 协议客户端尚未接入，" +
                    "请参考 docs/PLUGIN-SYSTEM.md 完成 RemoteTransport 实现",
            ),
        )

    override suspend fun exists(path: String): Boolean = false

    override fun disconnect() = Unit
}

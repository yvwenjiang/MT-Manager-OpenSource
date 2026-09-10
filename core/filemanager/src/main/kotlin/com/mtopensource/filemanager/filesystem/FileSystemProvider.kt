// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.filemanager.filesystem

import com.mtopensource.common.result.AppResult
import com.mtopensource.common.result.FileError
import com.mtopensource.filemanager.model.FileItem
import java.io.InputStream
import java.io.OutputStream

/**
 * 文件系统抽象接口。
 *
 * 通过统一接口屏蔽底层差异，使 UI 层无需关心当前是本地存储、
 * 压缩包内部、Root 文件系统还是远程存储（插件实现）。
 *
 * 所有方法均为 `suspend`，实现方负责切到 IO 调度器。
 */
interface FileSystemProvider {

    /** 该提供者处理的 scheme，例如 `file`、`archive`、`root`、`remote`。 */
    val scheme: String

    /** 该文件系统是否支持写入（只读文件系统如部分压缩包返回 false）。 */
    val isWritable: Boolean

    /** 该文件系统是否需要特殊权限（如 Root / SAF 授权）。 */
    val requiresPermission: Boolean get() = false

    /** 列出目录内容。 */
    suspend fun list(path: String): AppResult<List<FileItem>, FileError>

    /** 判断路径是否存在。 */
    suspend fun exists(path: String): Boolean

    /** 是否为目录。 */
    suspend fun isDirectory(path: String): Boolean

    /** 获取文件大小（目录返回 0）。 */
    suspend fun length(path: String): Long

    /** 获取最后修改时间。 */
    suspend fun lastModified(path: String): Long

    /** 创建空文件。 */
    suspend fun createFile(path: String): AppResult<Unit, FileError>

    /** 创建目录（含父目录）。 */
    suspend fun createDirectory(path: String): AppResult<Unit, FileError>

    /** 删除文件或目录。 */
    suspend fun delete(path: String): AppResult<Unit, FileError>

    /** 重命名。 */
    suspend fun rename(path: String, newName: String): AppResult<Unit, FileError>

    /** 复制，[onProgress] 可用于展示进度。 */
    suspend fun copy(source: String, target: String, onProgress: ProgressCallback? = null): AppResult<Unit, FileError>

    /** 移动（跨文件系统时实现方应退化为复制+删除）。 */
    suspend fun move(source: String, target: String, onProgress: ProgressCallback? = null): AppResult<Unit, FileError>

    /** 打开输入流，调用方负责关闭。 */
    suspend fun openInputStream(path: String): AppResult<InputStream, FileError>

    /** 打开输出流，调用方负责关闭。 */
    suspend fun openOutputStream(path: String): AppResult<OutputStream, FileError>

    /**
     * 进度回调。
     */
    interface ProgressCallback {
        /** @param bytesProcessed 已处理字节数；@param bytesTotal 总字节数，未知时为 -1。 */
        fun onProgress(bytesProcessed: Long, bytesTotal: Long)

        /** 是否已被取消，实现方应定期检查并中止。 */
        fun isCancelled(): Boolean = false
    }
}

/**
 * 便于实现类的通用能力：把阻塞式文件操作包装为结果类型。
 */
internal inline fun <T> runIo(block: () -> T): AppResult<T, FileError> = try {
    AppResult.Success(block())
} catch (e: java.io.FileNotFoundException) {
    AppResult.Failure(FileError.NotFound(e.message ?: "未知路径"))
} catch (e: SecurityException) {
    AppResult.Failure(FileError.PermissionDenied(e.message ?: "未知路径"))
} catch (e: java.io.IOException) {
    val message = e.message.orEmpty()
    when {
        message.contains("ENOSPC", ignoreCase = true) -> AppResult.Failure(FileError.NoSpace(0))
        message.contains("EROFS", ignoreCase = true) -> AppResult.Failure(FileError.ReadOnly(message))
        message.contains("EACCES", ignoreCase = true) ||
            message.contains("EPERM", ignoreCase = true) -> AppResult.Failure(FileError.PermissionDenied(message))
        else -> AppResult.Failure(FileError.Io(message, e))
    }
} catch (e: Exception) {
    AppResult.Failure(FileError.Io(e.message ?: e.javaClass.simpleName, e))
}

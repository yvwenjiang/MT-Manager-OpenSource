// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.filemanager.operation

/**
 * 文件操作类型。
 */
enum class FileOperationType {
    COPY,
    MOVE,
    DELETE,
    RENAME,
    CREATE_DIRECTORY,
    CREATE_FILE,
    COMPRESS,
    EXTRACT,
}

/**
 * 单个文件操作任务。
 */
data class FileOperation(
    val type: FileOperationType,
    /** 源路径列表（删除、复制、压缩可能涉及多个）。 */
    val sources: List<String>,
    /** 目标路径（重命名时为父目录 + 新名称）。 */
    val target: String? = null,
    /** 新名称（仅重命名使用）。 */
    val newName: String? = null,
    /** 是否覆盖已存在的目标。 */
    val overwrite: Boolean = false,
    /** 压缩格式，例如 `zip`。 */
    val archiveFormat: String? = null,
)

/**
 * 操作进度快照，供 UI 层渲染进度条。
 */
data class OperationProgress(
    val operationId: Long,
    val type: FileOperationType,
    /** 当前正在处理的文件名。 */
    val currentFile: String = "",
    /** 已处理文件数。 */
    val processedFiles: Int = 0,
    /** 总文件数。 */
    val totalFiles: Int = 0,
    /** 已处理字节数。 */
    val processedBytes: Long = 0L,
    /** 总字节数，未知为 -1。 */
    val totalBytes: Long = -1L,
) {
    /** 完成百分比（0..100）。 */
    val percent: Int
        get() = when {
            totalBytes > 0 -> ((processedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
            totalFiles > 0 -> (processedFiles * 100 / totalFiles).coerceIn(0, 100)
            else -> 0
        }
}

/**
 * 操作执行结果。
 */
sealed class OperationResult {
    data class Success(val operationId: Long, val affectedCount: Int) : OperationResult()

    data class Failure(
        val operationId: Long,
        val failedPath: String,
        val reason: String,
        /** 失败前已成功的条目数。 */
        val succeededCount: Int,
    ) : OperationResult()

    data class Cancelled(val operationId: Long, val succeededCount: Int) : OperationResult()
}

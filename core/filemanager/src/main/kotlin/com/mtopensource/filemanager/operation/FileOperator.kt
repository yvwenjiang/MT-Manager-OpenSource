// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.filemanager.operation

import com.mtopensource.common.result.AppResult
import com.mtopensource.common.result.FileError
import com.mtopensource.common.utils.Logger
import com.mtopensource.common.utils.PathUtils
import com.mtopensource.filemanager.filesystem.FileSystemProvider
import com.mtopensource.filemanager.filesystem.FileSystemRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/**
 * 文件操作引擎。
 *
 * 职责：
 * - 把 UI 的复制/移动/删除等请求转换为对 [FileSystemProvider] 的调用
 * - 统一处理冲突重命名、跨文件系统复制、进度广播与取消
 * - 通过 [progressFlow] 向 UI 推送实时进度
 *
 * 该类不直接依赖 Android，可在 JVM 测试中用临时目录完整验证。
 */
class FileOperator(
    private val router: FileSystemRouter,
    private val scope: CoroutineScope,
) {

    private companion object {
        const val TAG = "FileOperator"

        /** 冲突重命名的最大尝试次数，避免极端情况下长时间循环。 */
        const val MAX_CONFLICT_ATTEMPTS = 10_000
    }

    private val idGenerator = AtomicLong(0)

    /** 串行化写操作，避免并发修改同一目录导致状态错乱。 */
    private val operationMutex = Mutex()

    private val _progressFlow = MutableSharedFlow<OperationProgress>(extraBufferCapacity = 64)

    /** 进度事件流，UI 层收集后更新进度条。 */
    val progressFlow: SharedFlow<OperationProgress> = _progressFlow.asSharedFlow()

    private var currentJob: Job? = null

    /**
     * 异步执行一个文件操作。
     *
     * @param onResult 主线程回调最终结果（实际线程取决于调用方传入的 scope）。
     */
    fun execute(
        operation: FileOperation,
        onResult: (OperationResult) -> Unit = {},
    ): Job {
        val operationId = idGenerator.incrementAndGet()
        val job = scope.launch {
            val result = operationMutex.withLock {
                runOperation(operationId, operation)
            }
            onResult(result)
        }
        currentJob = job
        return job
    }

    /** 取消当前正在执行的操作。 */
    fun cancelCurrent() {
        currentJob?.cancel()
    }

    private suspend fun runOperation(operationId: Long, operation: FileOperation): OperationResult {
        Logger.i(TAG, "开始操作 $operationId: ${operation.type}, 共 ${operation.sources.size} 项")
        return try {
            when (operation.type) {
                FileOperationType.COPY -> transfer(operationId, operation, move = false)
                FileOperationType.MOVE -> transfer(operationId, operation, move = true)
                FileOperationType.DELETE -> deleteAll(operationId, operation)
                FileOperationType.RENAME -> rename(operationId, operation)
                FileOperationType.CREATE_FILE -> createFile(operationId, operation)
                FileOperationType.CREATE_DIRECTORY -> createDirectory(operationId, operation)
                FileOperationType.COMPRESS, FileOperationType.EXTRACT ->
                    OperationResult.Failure(operationId, "", "压缩操作由压缩模块处理", 0)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            Logger.w(TAG, "操作 $operationId 被取消")
            OperationResult.Cancelled(operationId, 0)
        }
    }

    private suspend fun transfer(
        operationId: Long,
        operation: FileOperation,
        move: Boolean,
    ): OperationResult {
        val targetRoot = operation.target ?: return OperationResult.Failure(operationId, "", "缺少目标路径", 0)
        var succeeded = 0

        for (source in operation.sources) {
            val name = PathUtils.fileName(source)
            var destination = PathUtils.join(targetRoot, name)

            // 目标已存在且允许覆盖时先删除；否则自动生成副本名
            if (operation.overwrite) {
                val targetFs = router.resolve(destination)
                if (targetFs.exists(destination)) targetFs.delete(destination)
            } else if (router.resolve(destination).exists(destination)) {
                destination = resolveConflictSuspend(destination)
            }

            val sourceFs = router.resolve(source)
            val targetFs = router.resolve(destination)

            val callback = progressCallback(operationId, operation.type, destination, succeeded, operation.sources.size)
            val result = if (sourceFs === targetFs) {
                if (move) sourceFs.move(source, destination, callback) else sourceFs.copy(source, destination, callback)
            } else {
                // 跨文件系统：统一走 流复制（provider 内部流可能不同）
                crossFileSystemTransfer(sourceFs, targetFs, source, destination, callback, move)
            }

            when (result) {
                is AppResult.Success -> succeeded++
                is AppResult.Failure -> {
                    val reason = result.error.displayMessage()
                    Logger.e(TAG, "操作失败：$source -> $destination, $reason")
                    return OperationResult.Failure(operationId, source, reason, succeeded)
                }
            }
        }

        return OperationResult.Success(operationId, succeeded)
    }

    private suspend fun crossFileSystemTransfer(
        sourceFs: FileSystemProvider,
        targetFs: FileSystemProvider,
        source: String,
        destination: String,
        callback: FileSystemProvider.ProgressCallback,
        move: Boolean,
    ): AppResult<Unit, FileError> {
        val input = sourceFs.openInputStream(source)
        if (input is AppResult.Failure) return AppResult.Failure(input.error)

        val output = targetFs.openOutputStream(destination)
        if (output is AppResult.Failure) {
            (input as AppResult.Success).data.close()
            return AppResult.Failure(output.error)
        }

        return try {
            (input as AppResult.Success).data.use { ins ->
                (output as AppResult.Success).data.use { outs ->
                    val buffer = ByteArray(64 * 1024)
                    var processed = 0L
                    while (true) {
                        if (callback.isCancelled()) throw java.io.InterruptedIOException("操作已取消")
                        val read = ins.read(buffer)
                        if (read <= 0) break
                        outs.write(buffer, 0, read)
                        processed += read
                        callback.onProgress(processed, -1)
                    }
                    outs.flush()
                }
            }
            if (move) sourceFs.delete(source)
            AppResult.success(Unit)
        } catch (e: Exception) {
            AppResult.Failure(FileError.Io(e.message ?: "跨文件系统传输失败", e))
        }
    }

    private suspend fun deleteAll(operationId: Long, operation: FileOperation): OperationResult {
        var succeeded = 0
        for (source in operation.sources) {
            when (val result = router.resolve(source).delete(source)) {
                is AppResult.Success -> succeeded++
                is AppResult.Failure ->
                    return OperationResult.Failure(operationId, source, result.error.displayMessage(), succeeded)
            }
        }
        return OperationResult.Success(operationId, succeeded)
    }

    private suspend fun rename(operationId: Long, operation: FileOperation): OperationResult {
        val source = operation.sources.firstOrNull()
            ?: return OperationResult.Failure(operationId, "", "缺少源路径", 0)
        val newName = operation.newName
            ?: return OperationResult.Failure(operationId, source, "缺少新名称", 0)

        return when (val result = router.resolve(source).rename(source, newName)) {
            is AppResult.Success -> OperationResult.Success(operationId, 1)
            is AppResult.Failure -> OperationResult.Failure(operationId, source, result.error.displayMessage(), 0)
        }
    }

    private suspend fun createFile(operationId: Long, operation: FileOperation): OperationResult {
        val target = operation.target
            ?: return OperationResult.Failure(operationId, "", "缺少目标路径", 0)
        return when (val result = router.resolve(target).createFile(target)) {
            is AppResult.Success -> OperationResult.Success(operationId, 1)
            is AppResult.Failure -> OperationResult.Failure(operationId, target, result.error.displayMessage(), 0)
        }
    }

    private suspend fun createDirectory(operationId: Long, operation: FileOperation): OperationResult {
        val target = operation.target
            ?: return OperationResult.Failure(operationId, "", "缺少目标路径", 0)
        return when (val result = router.resolve(target).createDirectory(target)) {
            is AppResult.Success -> OperationResult.Success(operationId, 1)
            is AppResult.Failure -> OperationResult.Failure(operationId, target, result.error.displayMessage(), 0)
        }
    }

    /**
     * 生成不冲突的目标路径。
     *
     * 与 [PathUtils.resolveConflict] 的区别在于存在性判断需要走 provider，
     * 因此这里用挂起循环实现，最多尝试 [MAX_CONFLICT_ATTEMPTS] 次。
     */
    private suspend fun resolveConflictSuspend(targetPath: String): String {
        val dir = PathUtils.parent(targetPath) ?: "/"
        val base = PathUtils.baseName(targetPath)
        val ext = PathUtils.extension(targetPath)
        val suffix = if (ext.isEmpty()) "" else ".$ext"

        var index = 1
        while (index < MAX_CONFLICT_ATTEMPTS) {
            val candidate = PathUtils.join(dir, "$base ($index)$suffix")
            if (!router.resolve(candidate).exists(candidate)) return candidate
            index++
        }
        return PathUtils.join(dir, "$base (${System.currentTimeMillis()})$suffix")
    }

    private fun progressCallback(
        operationId: Long,
        type: FileOperationType,
        currentFile: String,
        processedFiles: Int,
        totalFiles: Int,
    ) = object : FileSystemProvider.ProgressCallback {
        override fun onProgress(bytesProcessed: Long, bytesTotal: Long) {
            _progressFlow.tryEmit(
                OperationProgress(
                    operationId = operationId,
                    type = type,
                    currentFile = currentFile,
                    processedFiles = processedFiles,
                    totalFiles = totalFiles,
                    processedBytes = bytesProcessed,
                    totalBytes = bytesTotal,
                ),
            )
        }
    }
}

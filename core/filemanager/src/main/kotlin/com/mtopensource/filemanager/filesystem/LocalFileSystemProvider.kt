// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.filemanager.filesystem

import com.mtopensource.common.result.AppResult
import com.mtopensource.common.result.FileError
import com.mtopensource.filemanager.model.FileItem
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * 本地文件系统实现。
 *
 * 该类不依赖任何 Android API，因此可直接在 JVM 单元测试中使用临时目录验证
 * 复制/移动/删除/重命名等全部语义，Android 端行为与测试保持一致。
 */
class LocalFileSystemProvider(
    /** 允许访问的根目录白名单，为空表示不限制（仅测试用）。 */
    private val allowedRoots: List<String> = emptyList(),
) : FileSystemProvider {

    override val scheme: String = FileItem.SCHEME_FILE

    override val isWritable: Boolean = true

    override suspend fun list(path: String): AppResult<List<FileItem>, FileError> = runIo {
        val dir = File(path)
        requireInsideSandbox(dir)
        if (!dir.exists()) throw java.io.FileNotFoundException(path)
        if (!dir.isDirectory) throw java.io.IOException("不是目录：$path")
        dir.listFiles()?.map { FileItem.fromFile(it) } ?: emptyList()
    }

    override suspend fun exists(path: String): Boolean = File(path).exists()

    override suspend fun isDirectory(path: String): Boolean = File(path).isDirectory

    override suspend fun length(path: String): Long = File(path).let { if (it.isDirectory) 0L else it.length() }

    override suspend fun lastModified(path: String): Long = File(path).lastModified()

    override suspend fun createFile(path: String): AppResult<Unit, FileError> = runIo {
        val file = File(path)
        // 新建路径同样必须做沙箱校验，否则可绕过白名单在任意位置落文件
        requireInsideSandbox(file)
        file.parentFile?.mkdirs()
        if (file.exists()) throw java.io.IOException("文件已存在：$path")
        if (!file.createNewFile()) throw java.io.IOException("创建文件失败：$path")
    }

    override suspend fun createDirectory(path: String): AppResult<Unit, FileError> = runIo {
        val dir = File(path)
        requireInsideSandbox(dir)
        if (dir.exists()) throw java.io.IOException("目录已存在：$path")
        if (!dir.mkdirs()) throw java.io.IOException("创建目录失败：$path")
    }

    override suspend fun delete(path: String): AppResult<Unit, FileError> = runIo {
        val file = File(path)
        requireInsideSandbox(file)
        if (!file.exists()) throw java.io.FileNotFoundException(path)
        if (!file.deleteRecursively()) throw java.io.IOException("删除失败：$path")
    }

    override suspend fun rename(path: String, newName: String): AppResult<Unit, FileError> = runIo {
        val source = File(path)
        requireInsideSandbox(source)
        if (!source.exists()) throw java.io.FileNotFoundException(path)
        if (newName.contains('/') || newName.contains('\\')) {
            throw java.io.IOException("新名称不能包含路径分隔符")
        }
        val target = File(source.parentFile, newName)
        if (target.exists()) throw java.io.IOException("目标已存在：${target.absolutePath}")
        if (!source.renameTo(target)) throw java.io.IOException("重命名失败：$path")
    }

    override suspend fun copy(
        source: String,
        target: String,
        onProgress: FileSystemProvider.ProgressCallback?,
    ): AppResult<Unit, FileError> = runIo {
        val src = File(source)
        requireInsideSandbox(src)
        if (!src.exists()) throw java.io.FileNotFoundException(source)

        val dst = File(target)
        if (dst.exists()) throw java.io.IOException("目标已存在：$target")
        if (src.isDirectory && dst.absolutePath.startsWith(src.absolutePath + File.separator)) {
            throw java.io.IOException("不能将目录复制到自身子目录")
        }

        copyRecursively(src, dst, onProgress)
    }

    override suspend fun move(
        source: String,
        target: String,
        onProgress: FileSystemProvider.ProgressCallback?,
    ): AppResult<Unit, FileError> = runIo {
        val src = File(source)
        val dst = File(target)
        requireInsideSandbox(src)
        if (!src.exists()) throw java.io.FileNotFoundException(source)
        if (dst.exists()) throw java.io.IOException("目标已存在：$target")

        // 同分区优先尝试原子重命名，失败再退化为复制+删除
        if (src.renameTo(dst)) {
            return@runIo
        }
        copyRecursively(src, dst, onProgress)
        if (!src.deleteRecursively()) throw java.io.IOException("移动后清理源路径失败：$source")
    }

    override suspend fun openInputStream(path: String): AppResult<InputStream, FileError> = runIo {
        val file = File(path)
        requireInsideSandbox(file)
        if (!file.exists()) throw java.io.FileNotFoundException(path)
        if (file.isDirectory) throw java.io.IOException("不能读取目录：$path")
        FileInputStream(file)
    }

    override suspend fun openOutputStream(path: String): AppResult<OutputStream, FileError> = runIo {
        val file = File(path)
        requireInsideSandbox(file)
        if (file.isDirectory) throw java.io.IOException("不能写入目录：$path")
        file.parentFile?.mkdirs()
        FileOutputStream(file)
    }

    /** 校验路径是否越出允许的根目录。 */
    private fun requireInsideSandbox(file: File) {
        if (allowedRoots.isEmpty()) return
        val target = file.canonicalPath
        val allowed = allowedRoots.any { root -> target == root || target.startsWith(root + File.separator) }
        if (!allowed) {
            throw SecurityException("路径不在允许范围内：$target")
        }
    }

    private fun copyRecursively(
        source: File,
        target: File,
        onProgress: FileSystemProvider.ProgressCallback?,
    ) {
        if (source.isDirectory) {
            if (!target.mkdirs() && !target.isDirectory) {
                throw java.io.IOException("创建目录失败：${target.absolutePath}")
            }
            source.listFiles()?.forEach { child ->
                copyRecursively(File(child, "").let { child }, File(target, child.name), onProgress)
            }
            return
        }

        target.parentFile?.mkdirs()
        var processed = 0L
        val total = source.length()
        FileInputStream(source).use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    if (onProgress?.isCancelled() == true) {
                        throw java.io.InterruptedIOException("操作已取消")
                    }
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    processed += read
                    onProgress?.onProgress(processed, total)
                }
                output.flush()
            }
        }
    }

    private companion object {
        const val DEFAULT_BUFFER_SIZE = 64 * 1024
    }
}

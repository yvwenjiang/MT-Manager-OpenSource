// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.filemanager.archive

import com.mtopensource.common.result.AppResult
import com.mtopensource.common.result.FileError
import com.mtopensource.common.utils.PathUtils
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

/**
 * 压缩包条目描述。
 */
data class ArchiveEntry(
    /** 压缩包内的相对路径，例如 `res/layout/main.xml`。 */
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val compressedSize: Long,
    val lastModified: Long,
    /** CRC32 校验值，用于完整性校验。 */
    val crc: Long = 0L,
)

/**
 * 压缩包管理：读取、创建与解压 ZIP。
 *
 * 基于 Apache Commons Compress 实现，支持：
 * - 列出条目（不完整解压，仅读取中央目录）
 * - 解压指定条目 / 全量解压
 * - 从目录创建 ZIP
 * - 路径穿越防护（Zip Slip）
 *
 * 本类不依赖 Android API，可在 JVM 单元测试中完整验证。
 */
class ArchiveManager {

    /**
     * 列出压缩包内所有条目。
     */
    fun listEntries(archivePath: String): AppResult<List<ArchiveEntry>, FileError> {
        val file = File(archivePath)
        if (!file.exists()) return AppResult.failure(FileError.NotFound(archivePath))
        if (!file.isFile) return AppResult.failure(FileError.IllegalOperation("不是文件：$archivePath"))

        return try {
            val entries = mutableListOf<ArchiveEntry>()
            ZipFile.builder().setFile(file).get().use { zipFile ->
                val enumeration = zipFile.entries
                while (enumeration.hasMoreElements()) {
                    val entry = enumeration.nextElement()
                    entries += entry.toArchiveEntry()
                }
            }
            AppResult.success(entries)
        } catch (e: Exception) {
            AppResult.failure(FileError.Io("解析压缩包失败：${e.message}", e))
        }
    }

    /**
     * 解压整个压缩包到目标目录。
     *
     * @param listener 进度回调，参数为（已完成条目数, 总条目数, 当前条目名）。
     */
    fun extract(
        archivePath: String,
        targetDir: String,
        listener: ((Int, Int, String) -> Unit)? = null,
    ): AppResult<Int, FileError> {
        val file = File(archivePath)
        if (!file.exists()) return AppResult.failure(FileError.NotFound(archivePath))

        val target = File(targetDir)
        if (!target.exists() && !target.mkdirs()) {
            return AppResult.failure(FileError.Io("无法创建目标目录：$targetDir"))
        }

        return try {
            var count = 0
            ZipArchiveInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
                var entry: ZipArchiveEntry? = input.nextEntry
                // 先统计总数以便计算进度
                while (entry != null) {
                    val safeName = sanitizeEntryName(entry.name)
                    if (safeName == null) {
                        entry = input.nextEntry
                        continue
                    }

                    val destination = File(target, safeName)
                    if (entry.isDirectory) {
                        destination.mkdirs()
                    } else {
                        destination.parentFile?.mkdirs()
                        FileOutputStream(destination).use { output ->
                            copyStream(input, output)
                        }
                    }
                    count++
                    listener?.invoke(count, -1, safeName)
                    entry = input.nextEntry
                }
            }
            AppResult.success(count)
        } catch (e: Exception) {
            AppResult.failure(FileError.Io("解压失败：${e.message}", e))
        }
    }

    /**
     * 解压压缩包中的单个条目。
     */
    fun extractEntry(
        archivePath: String,
        entryName: String,
        destinationFile: String,
    ): AppResult<Unit, FileError> {
        val file = File(archivePath)
        if (!file.exists()) return AppResult.failure(FileError.NotFound(archivePath))

        return try {
            var found = false
            ZipArchiveInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
                var entry: ZipArchiveEntry? = input.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name == entryName) {
                        val destination = File(destinationFile)
                        destination.parentFile?.mkdirs()
                        FileOutputStream(destination).use { output -> copyStream(input, output) }
                        found = true
                        break
                    }
                    entry = input.nextEntry
                }
            }
            if (found) {
                AppResult.success(Unit)
            } else {
                AppResult.failure(FileError.NotFound(entryName))
            }
        } catch (e: Exception) {
            AppResult.failure(FileError.Io("解压条目失败：${e.message}", e))
        }
    }

    /**
     * 读取压缩包内某个条目的文本内容（用于在编辑器中直接打开压缩包里的文件）。
     */
    fun readEntryAsText(archivePath: String, entryName: String, maxBytes: Int = 2 * 1024 * 1024): AppResult<String, FileError> {
        val file = File(archivePath)
        if (!file.exists()) return AppResult.failure(FileError.NotFound(archivePath))

        return try {
            ZipFile.builder().setFile(file).get().use { zipFile ->
                val entry = zipFile.getEntry(entryName)
                    ?: return AppResult.failure(FileError.NotFound(entryName))
                if (entry.isDirectory) {
                    return AppResult.failure(FileError.IllegalOperation("$entryName 是目录"))
                }
                if (entry.size > maxBytes) {
                    return AppResult.failure(
                        FileError.IllegalOperation("文件过大（${entry.size} 字节），超过编辑器上限"),
                    )
                }
                val text = zipFile.getInputStream(entry).use { it.readBytes().decodeToString() }
                AppResult.success(text)
            }
        } catch (e: Exception) {
            AppResult.failure(FileError.Io("读取条目失败：${e.message}", e))
        }
    }

    /**
     * 把若干文件/目录打包为 ZIP。
     *
     * @param sources 要打包的路径列表
     * @param archivePath 输出的压缩包路径
     * @param baseDir 用于计算条目相对路径的基准目录，为空时使用父目录
     */
    fun compress(
        sources: List<String>,
        archivePath: String,
        baseDir: String? = null,
        listener: ((Int, String) -> Unit)? = null,
    ): AppResult<Int, FileError> {
        if (sources.isEmpty()) return AppResult.failure(FileError.IllegalOperation("没有需要压缩的文件"))

        val output = File(archivePath)
        output.parentFile?.mkdirs()

        return try {
            var count = 0
            ZipArchiveOutputStream(output).use { zipOut ->
                zipOut.setLevel(6)
                for (sourcePath in sources) {
                    val source = File(sourcePath)
                    if (!source.exists()) return AppResult.failure(FileError.NotFound(sourcePath))
                    val base = baseDir?.let { File(it) } ?: source.parentFile ?: File("/")
                    count += addToZip(zipOut, source, base, listener)
                }
                zipOut.finish()
            }
            AppResult.success(count)
        } catch (e: Exception) {
            output.delete()
            AppResult.failure(FileError.Io("压缩失败：${e.message}", e))
        }
    }

    private fun addToZip(
        zipOut: ZipArchiveOutputStream,
        file: File,
        base: File,
        listener: ((Int, String) -> Unit)?,
    ): Int {
        val relative = PathUtils.relativePath(base.absolutePath, file.absolutePath)
        var count = 0

        if (file.isDirectory) {
            // 显式写入目录条目，保证空目录也能被保留
            val dirEntry = ZipArchiveEntry("$relative/")
            dirEntry.time = file.lastModified()
            zipOut.putArchiveEntry(dirEntry)
            zipOut.closeArchiveEntry()
            count++
            listener?.invoke(count, relative)

            file.listFiles()?.forEach { child ->
                count += addToZip(zipOut, child, base, listener)
            }
        } else {
            val entry = ZipArchiveEntry(relative)
            entry.time = file.lastModified()
            zipOut.putArchiveEntry(entry)
            FileInputStream(file).use { input -> copyStream(input, zipOut) }
            zipOut.closeArchiveEntry()
            count++
            listener?.invoke(count, relative)
        }
        return count
    }

    /**
     * 防 Zip Slip：拒绝绝对路径与 `..` 穿越，返回安全相对路径。
     */
    private fun sanitizeEntryName(rawName: String): String? {
        val name = rawName.replace('\\', '/').trimStart('/')
        if (name.isEmpty()) return null
        val normalized = PathUtils.normalize(name)
        if (normalized.startsWith("/") || normalized.startsWith("..") || normalized.contains("/../")) {
            return null
        }
        return normalized
    }

    private fun ZipArchiveEntry.toArchiveEntry() = ArchiveEntry(
        name = name.trimEnd('/'),
        isDirectory = isDirectory,
        size = size,
        compressedSize = compressedSize,
        lastModified = time,
        crc = crc,
    )

    private fun copyStream(input: InputStream, output: java.io.OutputStream) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            output.write(buffer, 0, read)
        }
    }
}

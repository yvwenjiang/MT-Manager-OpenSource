// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.filemanager.model

import com.mtopensource.common.utils.Formatters
import java.io.File

/**
 * 文件列表中使用的统一数据模型。
 *
 * 说明：本模型刻意使用普通字符串路径而非 `android.net.Uri`，
 * 以便在纯 JVM 单元测试中直接构造与断言；
 * 需要 Uri 的场合由 [com.mtopensource.filemanager.filesystem.FileSystemProvider] 自行转换。
 */
data class FileItem(
    /** 完整路径（本地路径或 `scheme://path` 形式）。 */
    val path: String,
    /** 显示名称。 */
    val name: String,
    /** 是否为目录。 */
    val isDirectory: Boolean,
    /** 文件大小（字节），目录为 0。 */
    val size: Long = 0L,
    /** 最后修改时间（epoch millis）。 */
    val lastModified: Long = 0L,
    /** 是否为符号链接。 */
    val isSymlink: Boolean = false,
    /** 是否为隐藏文件（名称以点开头）。 */
    val isHidden: Boolean = false,
    /** Unix 权限位（如 0755），未知时为 null。 */
    val permissions: Int? = null,
    /** 所属文件系统 scheme，例如 `file`、`archive`、`root`。 */
    val scheme: String = SCHEME_FILE,
) {

    /** 扩展名（小写，不含点）。 */
    val extension: String get() = if (isDirectory) "" else name.substringAfterLast('.', "").lowercase()

    /** 是否可被识别为压缩包。 */
    val isArchive: Boolean get() = extension in ARCHIVE_EXTENSIONS

    /** 是否可被文本编辑器打开。 */
    val isTextLike: Boolean get() = extension in TEXT_EXTENSIONS

    /** 易读的大小描述。 */
    val readableSize: String get() = if (isDirectory) "" else Formatters.formatSize(size)

    /** 易读的修改时间。 */
    val readableModified: String get() = Formatters.formatDateTime(lastModified)

    /** 易读的权限描述。 */
    val readablePermissions: String get() = permissions?.let { Formatters.formatPermissions(it) } ?: "-"

    companion object {
        const val SCHEME_FILE = "file"
        const val SCHEME_ARCHIVE = "archive"
        const val SCHEME_ROOT = "root"
        const val SCHEME_REMOTE = "remote"

        val ARCHIVE_EXTENSIONS = setOf("zip", "jar", "apk", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz")
        val TEXT_EXTENSIONS = setOf(
            "txt", "md", "log", "json", "xml", "yml", "yaml", "ini", "conf", "properties",
            "kt", "java", "js", "ts", "html", "css", "sh", "py", "c", "cpp", "h", "gradle", "pro",
        )

        /** 由 [java.io.File] 构造（纯 JVM，可用于测试）。 */
        fun fromFile(file: File, scheme: String = SCHEME_FILE): FileItem = FileItem(
            path = file.absolutePath,
            name = file.name,
            isDirectory = file.isDirectory,
            size = if (file.isDirectory) 0L else file.length(),
            lastModified = file.lastModified(),
            isSymlink = runCatching { java.nio.file.Files.isSymbolicLink(file.toPath()) }.getOrDefault(false),
            isHidden = file.name.startsWith('.'),
            permissions = null,
            scheme = scheme,
        )
    }
}

// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.ui.component

import androidx.compose.ui.graphics.Color

/**
 * 文件类型分类，用于挑选图标与配色。
 */
enum class FileCategory(
    val displayName: String,
    val color: Color,
) {
    DIRECTORY("文件夹", Color(0xFFF9A825)),
    APK("安装包", Color(0xFF43A047)),
    ARCHIVE("压缩包", Color(0xFFFB8C00)),
    IMAGE("图片", Color(0xFF8E24AA)),
    VIDEO("视频", Color(0xFFE53935)),
    AUDIO("音频", Color(0xFF00ACC1)),
    DOCUMENT("文档", Color(0xFF3949AB)),
    CODE("代码", Color(0xFF00897B)),
    TEXT("文本", Color(0xFF546E7A)),
    DATABASE("数据库", Color(0xFF6D4C41)),
    UNKNOWN("其他", Color(0xFF9E9E9E)),
}

/**
 * 根据扩展名/目录标记推断文件分类。
 *
 * 抽成独立对象是为了让「图标选择」这一逻辑可被单元测试覆盖，
 * 而不必依赖 Compose 运行时。
 */
object FileCategoryResolver {

    private val APK = setOf("apk", "xapk", "apks")
    private val ARCHIVE = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz", "jar", "war", "iso")
    private val IMAGE = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp", "svg", "heic", "ico", "tiff")
    private val VIDEO = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m4v", "ts")
    private val AUDIO = setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "wma", "opus", "mid")
    private val DOCUMENT = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "epub")
    private val CODE = setOf(
        "kt", "kts", "java", "smali", "dex", "so", "c", "cpp", "h", "hpp", "cs", "go", "rs", "swift",
        "js", "ts", "jsx", "tsx", "py", "rb", "php", "lua", "dart", "gradle", "kts", "cmake", "mk",
        "html", "css", "scss", "xml", "json", "yml", "yaml", "toml", "proto",
    )
    private val TEXT = setOf("txt", "md", "log", "ini", "conf", "cfg", "properties", "csv", "sh", "bat")
    private val DATABASE = setOf("db", "sqlite", "sqlite3", "sql", "realm")

    /** 推断文件分类。 */
    fun resolve(extension: String, isDirectory: Boolean): FileCategory {
        if (isDirectory) return FileCategory.DIRECTORY
        val ext = extension.lowercase()
        return when (ext) {
            in APK -> FileCategory.APK
            in ARCHIVE -> FileCategory.ARCHIVE
            in IMAGE -> FileCategory.IMAGE
            in VIDEO -> FileCategory.VIDEO
            in AUDIO -> FileCategory.AUDIO
            in DOCUMENT -> FileCategory.DOCUMENT
            in CODE -> FileCategory.CODE
            in TEXT -> FileCategory.TEXT
            in DATABASE -> FileCategory.DATABASE
            else -> FileCategory.UNKNOWN
        }
    }

    /** 是否可在内置文本编辑器中打开。 */
    fun isEditable(extension: String): Boolean =
        extension.lowercase() in (TEXT + CODE + setOf("json", "xml", "yml", "yaml", "html", "css", "js"))
}

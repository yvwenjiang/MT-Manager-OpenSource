// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.filemanager.model

/**
 * 文件列表排序方式。
 */
enum class SortBy {
    NAME,
    SIZE,
    MODIFIED,
    TYPE,
}

/**
 * 排序配置：排序字段 + 是否升序 + 目录是否优先。
 *
 * 目录始终置顶是文件管理器的通用交互约定（MT 管理器同样如此）。
 */
data class FileSortConfig(
    val sortBy: SortBy = SortBy.NAME,
    val ascending: Boolean = true,
    val directoriesFirst: Boolean = true,
    /** 是否显示隐藏文件。 */
    val showHidden: Boolean = false,
) {

    /** 对文件列表应用当前排序配置。 */
    fun apply(items: List<FileItem>): List<FileItem> {
        val comparator: Comparator<FileItem> = when (sortBy) {
            SortBy.NAME -> compareBy { it.name.lowercase() }
            SortBy.SIZE -> compareBy { it.size }
            SortBy.MODIFIED -> compareBy { it.lastModified }
            SortBy.TYPE -> compareBy<FileItem> { it.extension }.thenBy { it.name.lowercase() }
        }

        val filtered = if (showHidden) items else items.filterNot { it.isHidden }
        val ordered = if (ascending) filtered.sortedWith(comparator) else filtered.sortedWith(comparator.reversed())

        return if (!directoriesFirst) {
            ordered
        } else {
            ordered.filter { it.isDirectory } + ordered.filterNot { it.isDirectory }
        }
    }
}

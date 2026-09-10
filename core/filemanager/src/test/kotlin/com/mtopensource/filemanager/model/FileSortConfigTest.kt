// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.filemanager.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FileSortConfigTest {

    private val items = listOf(
        FileItem(path = "/sdcard/b.txt", name = "b.txt", isDirectory = false, size = 200, lastModified = 200),
        FileItem(path = "/sdcard/zdir", name = "zdir", isDirectory = true),
        FileItem(path = "/sdcard/a.txt", name = "a.txt", isDirectory = false, size = 100, lastModified = 300),
        FileItem(path = "/sdcard/.hidden", name = ".hidden", isDirectory = false, isHidden = true),
    )

    @Test
    fun `默认按名称升序且目录优先，隐藏文件被过滤`() {
        val result = FileSortConfig().apply(items)
        assertEquals(listOf("zdir", "a.txt", "b.txt"), result.map { it.name })
    }

    @Test
    fun `显示隐藏文件时包含点文件`() {
        val result = FileSortConfig(showHidden = true).apply(items)
        assertEquals(listOf("zdir", ".hidden", "a.txt", "b.txt"), result.map { it.name })
    }

    @Test
    fun `按大小降序排序`() {
        val result = FileSortConfig(sortBy = SortBy.SIZE, ascending = false).apply(items)
        assertEquals(listOf("zdir", "b.txt", "a.txt"), result.map { it.name })
    }

    @Test
    fun `按修改时间升序排序`() {
        val result = FileSortConfig(sortBy = SortBy.MODIFIED, ascending = true).apply(items)
        assertEquals(listOf("zdir", "b.txt", "a.txt"), result.map { it.name })
    }

    @Test
    fun `关闭目录优先后目录不再前置，按类型排序`() {
        val result = FileSortConfig(sortBy = SortBy.TYPE, directoriesFirst = false).apply(items)
        // directoriesFirst=false 时目录不参与「目录优先」分组，
        // zdir 无扩展名所以排在扩展名相同的 a.txt/b.txt 之前
        assertEquals(listOf("zdir", "a.txt", "b.txt"), result.map { it.name })
    }

    @Test
    fun `开启目录优先时目录始终排在最前`() {
        val result = FileSortConfig(sortBy = SortBy.TYPE, directoriesFirst = true).apply(items)
        assertEquals(listOf("zdir", "a.txt", "b.txt"), result.map { it.name })
    }

    @Test
    fun `按类型排序时同类型按名称升序`() {
        val mixed = listOf(
            FileItem(path = "/sdcard/z.txt", name = "z.txt", isDirectory = false),
            FileItem(path = "/sdcard/a.txt", name = "a.txt", isDirectory = false),
        )
        val result = FileSortConfig(sortBy = SortBy.TYPE, directoriesFirst = false).apply(mixed)
        assertEquals(listOf("a.txt", "z.txt"), result.map { it.name })
    }

    @Test
    fun `降序时目录优先分组仍然生效`() {
        val result = FileSortConfig(sortBy = SortBy.NAME, ascending = false).apply(items)
        // 降序只影响组内顺序，目录始终在前
        assertEquals(listOf("zdir", "b.txt", "a.txt"), result.map { it.name })
    }
}

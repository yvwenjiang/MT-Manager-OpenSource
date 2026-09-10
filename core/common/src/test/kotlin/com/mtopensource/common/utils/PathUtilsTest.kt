// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.common.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PathUtilsTest {

    @Test
    fun `normalize 折叠多余分隔符与点段`() {
        assertEquals("/sdcard/a/c", PathUtils.normalize("/sdcard//a/./b/../c/"))
        assertEquals("/", PathUtils.normalize(""))
        assertEquals("/", PathUtils.normalize("/"))
        assertEquals("/a", PathUtils.normalize("/a/b/.."))
    }

    @Test
    fun `normalize 保留相对路径的前导双点`() {
        assertEquals("../a", PathUtils.normalize("../a/b/.."))
        assertEquals("..", PathUtils.normalize(".."))
    }

    @Test
    fun `parent 与根目录边界`() {
        assertEquals("/a/b", PathUtils.parent("/a/b/c"))
        assertEquals("/", PathUtils.parent("/a"))
        assertNull(PathUtils.parent("/"))
    }

    @Test
    fun `fileName 与 extension 处理隐藏文件`() {
        assertEquals("c.txt", PathUtils.fileName("/a/b/c.txt"))
        assertEquals("txt", PathUtils.extension("/a/b/c.TXT"))
        assertEquals("", PathUtils.extension("/a/b/.gitignore"))
        assertEquals("", PathUtils.extension("/a/b/noext"))
        // 多段扩展名取最后一段，与 baseName 的行为保持一致
        assertEquals("gz", PathUtils.extension("/a/b/archive.tar.gz"))
        assertEquals(".gitignore", PathUtils.fileName("/a/.gitignore"))
    }

    @Test
    fun `baseName 去掉扩展名`() {
        assertEquals("archive.tar", PathUtils.baseName("/a/archive.tar.gz"))
        assertEquals(".gitignore", PathUtils.baseName("/a/.gitignore"))
        assertEquals("plain", PathUtils.baseName("/a/plain"))
    }

    @Test
    fun `join 自动补分隔符`() {
        assertEquals("/a/b/c", PathUtils.join("/a/", "b", "/c"))
        assertEquals("/a", PathUtils.join("/a", "", ""))
        assertEquals("a/b", PathUtils.join("a", "b"))
    }

    @Test
    fun `isUnder 判断子孙关系`() {
        assertTrue(PathUtils.isUnder("/sdcard", "/sdcard/a/b"))
        assertTrue(PathUtils.isUnder("/sdcard", "/sdcard"))
        assertFalse(PathUtils.isUnder("/sdcard", "/sdcardx/a"))
        assertFalse(PathUtils.isUnder("/sdcard/a", "/sdcard"))
    }

    @Test
    fun `resolveConflict 生成递增副本名`() {
        val existing = setOf("/tmp/a.txt", "/tmp/a (1).txt")
        val result = PathUtils.resolveConflict("/tmp/a.txt") { it in existing }
        assertEquals("/tmp/a (2).txt", result)
    }

    @Test
    fun `resolveConflict 无冲突时返回原路径`() {
        val result = PathUtils.resolveConflict("/tmp/new.txt") { false }
        assertEquals("/tmp/new.txt", result)
    }

    @Test
    fun `resolveConflict 处理无扩展名文件`() {
        val existing = setOf("/tmp/readme")
        assertEquals("/tmp/readme (1)", PathUtils.resolveConflict("/tmp/readme") { it in existing })
    }

    @Test
    fun `relativePath 计算相对路径`() {
        assertEquals("c/d", PathUtils.relativePath("/a/b", "/a/b/c/d"))
        assertEquals("../x", PathUtils.relativePath("/a/b/c", "/a/b/x"))
        assertEquals(".", PathUtils.relativePath("/a/b", "/a/b"))
    }
}

// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileCategoryResolverTest {

    @Test
    fun `目录优先于扩展名判断`() {
        assertEquals(FileCategory.DIRECTORY, FileCategoryResolver.resolve("apk", isDirectory = true))
        assertEquals(FileCategory.DIRECTORY, FileCategoryResolver.resolve("", isDirectory = true))
    }

    @Test
    fun `识别安装包`() {
        assertEquals(FileCategory.APK, FileCategoryResolver.resolve("apk", false))
        assertEquals(FileCategory.APK, FileCategoryResolver.resolve("APK", false))
        assertEquals(FileCategory.APK, FileCategoryResolver.resolve("xapk", false))
    }

    @Test
    fun `识别压缩包`() {
        listOf("zip", "rar", "7z", "tar", "gz", "jar").forEach {
            assertEquals(it, FileCategory.ARCHIVE, FileCategoryResolver.resolve(it, false))
        }
    }

    @Test
    fun `识别图片视频音频`() {
        assertEquals(FileCategory.IMAGE, FileCategoryResolver.resolve("png", false))
        assertEquals(FileCategory.VIDEO, FileCategoryResolver.resolve("mp4", false))
        assertEquals(FileCategory.AUDIO, FileCategoryResolver.resolve("flac", false))
    }

    @Test
    fun `识别代码与文本`() {
        assertEquals(FileCategory.CODE, FileCategoryResolver.resolve("kt", false))
        assertEquals(FileCategory.CODE, FileCategoryResolver.resolve("smali", false))
        assertEquals(FileCategory.TEXT, FileCategoryResolver.resolve("log", false))
    }

    @Test
    fun `识别文档与数据库`() {
        assertEquals(FileCategory.DOCUMENT, FileCategoryResolver.resolve("pdf", false))
        assertEquals(FileCategory.DATABASE, FileCategoryResolver.resolve("sqlite", false))
    }

    @Test
    fun `未知扩展名归类为其他`() {
        assertEquals(FileCategory.UNKNOWN, FileCategoryResolver.resolve("xyz123", false))
        assertEquals(FileCategory.UNKNOWN, FileCategoryResolver.resolve("", false))
    }

    @Test
    fun `isEditable 判断可编辑类型`() {
        assertTrue(FileCategoryResolver.isEditable("kt"))
        assertTrue(FileCategoryResolver.isEditable("json"))
        assertTrue(FileCategoryResolver.isEditable("txt"))
        assertFalse(FileCategoryResolver.isEditable("apk"))
        assertFalse(FileCategoryResolver.isEditable("png"))
    }
}

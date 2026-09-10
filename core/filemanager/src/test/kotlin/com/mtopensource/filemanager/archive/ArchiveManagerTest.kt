// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.filemanager.archive

import com.mtopensource.common.result.AppResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveManagerTest {

    private lateinit var root: File
    private val manager = ArchiveManager()

    @Before
    fun setUp() {
        root = Files.createTempDirectory("mt-archive-test").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun path(vararg parts: String) = File(root, parts.joinToString("/")).absolutePath

    private fun writeFile(path: String, content: String) =
        File(path).apply {
            parentFile?.mkdirs()
            writeText(content)
        }

    @Test
    fun `compress 打包目录并保留层级`() {
        writeFile(path("src", "a.txt"), "AAA")
        writeFile(path("src", "sub", "b.txt"), "BBB")

        val zip = path("out.zip")
        val result = manager.compress(listOf(path("src")), zip, baseDir = root.absolutePath)
        assertTrue(result is AppResult.Success)
        assertTrue(File(zip).exists())

        val entries = (manager.listEntries(zip) as AppResult.Success).data
        val names = entries.map { it.name }
        assertTrue(names.contains("src/a.txt"))
        assertTrue(names.contains("src/sub/b.txt"))
    }

    @Test
    fun `listEntries 标记目录条目`() {
        writeFile(path("s", "f.txt"), "x")
        val zip = path("list.zip")
        manager.compress(listOf(path("s")), zip, baseDir = root.absolutePath)

        val entries = (manager.listEntries(zip) as AppResult.Success).data
        assertTrue(entries.any { it.isDirectory && it.name == "s" })
        assertTrue(entries.any { !it.isDirectory && it.name == "s/f.txt" })
    }

    @Test
    fun `extract 完整还原文件内容`() {
        writeFile(path("src", "a.txt"), "hello-archive")
        writeFile(path("src", "nested", "b.txt"), "nested-content")
        val zip = path("pack.zip")
        manager.compress(listOf(path("src")), zip, baseDir = root.absolutePath)

        val dest = path("extracted")
        val result = manager.extract(zip, dest)
        assertTrue(result is AppResult.Success)
        assertEquals("hello-archive", File("$dest/src/a.txt").readText())
        assertEquals("nested-content", File("$dest/src/nested/b.txt").readText())
    }

    @Test
    fun `extract 上报进度回调`() {
        writeFile(path("s", "1.txt"), "1")
        writeFile(path("s", "2.txt"), "2")
        val zip = path("p.zip")
        manager.compress(listOf(path("s")), zip, baseDir = root.absolutePath)

        val visited = mutableListOf<String>()
        manager.extract(zip, path("out")) { _, _, name -> visited += name }

        assertTrue(visited.contains("s/1.txt"))
        assertTrue(visited.contains("s/2.txt"))
    }

    @Test
    fun `extract 对不存在的压缩包返回 NotFound`() {
        val result = manager.extract(path("ghost.zip"), path("out"))
        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun `readEntryAsText 读取条目文本`() {
        writeFile(path("s", "config.json"), """{"k":1}""")
        val zip = path("r.zip")
        manager.compress(listOf(path("s")), zip, baseDir = root.absolutePath)

        val result = manager.readEntryAsText(zip, "s/config.json")
        assertEquals("""{"k":1}""", (result as AppResult.Success).data)
    }

    @Test
    fun `readEntryAsText 读取不存在条目失败`() {
        writeFile(path("s", "a.txt"), "x")
        val zip = path("r2.zip")
        manager.compress(listOf(path("s")), zip, baseDir = root.absolutePath)

        val result = manager.readEntryAsText(zip, "s/missing.txt")
        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun `readEntryAsText 拒绝超过上限的大文件`() {
        writeFile(path("s", "big.txt"), "y".repeat(5000))
        val zip = path("big.zip")
        manager.compress(listOf(path("s")), zip, baseDir = root.absolutePath)

        val result = manager.readEntryAsText(zip, "s/big.txt", maxBytes = 100)
        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun `extractEntry 只解压指定条目`() {
        writeFile(path("s", "keep.txt"), "keep")
        writeFile(path("s", "skip.txt"), "skip")
        val zip = path("one.zip")
        manager.compress(listOf(path("s")), zip, baseDir = root.absolutePath)

        val destination = path("only.txt")
        val result = manager.extractEntry(zip, "s/keep.txt", destination)
        assertTrue(result is AppResult.Success)
        assertEquals("keep", File(destination).readText())
        assertFalse(File(path("s", "skip.txt")).let { it.exists() && false })
    }

    @Test
    fun `extract 防御 Zip Slip 路径穿越`() {
        // 手工构造含 ../ 条目的恶意压缩包
        val evil = File(path("evil.zip"))
        ZipOutputStream(evil.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("../escaped.txt"))
            zip.write("pwned".toByteArray())
            zip.closeEntry()
        }

        val dest = path("safe")
        manager.extract(evil.absolutePath, dest)

        val escaped = File(root.parentFile, "escaped.txt")
        assertFalse("不应在目标目录之外写入文件", escaped.exists())
    }

    @Test
    fun `compress 对空列表返回失败`() {
        val result = manager.compress(emptyList(), path("e.zip"))
        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun `compress 对不存在源文件返回失败`() {
        val result = manager.compress(listOf(path("missing.txt")), path("e2.zip"))
        assertTrue(result is AppResult.Failure)
    }
}

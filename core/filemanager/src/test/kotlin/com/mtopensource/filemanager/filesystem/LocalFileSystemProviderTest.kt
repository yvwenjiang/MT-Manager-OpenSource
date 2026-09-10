// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.filemanager.filesystem

import com.mtopensource.common.result.AppResult
import com.mtopensource.common.result.FileError
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LocalFileSystemProviderTest {

    private lateinit var root: File
    private lateinit var fs: LocalFileSystemProvider

    @Before
    fun setUp() {
        root = Files.createTempDirectory("mt-fs-test").toFile()
        fs = LocalFileSystemProvider(allowedRoots = listOf(root.canonicalPath))
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun path(vararg parts: String) = File(root, parts.joinToString("/")).absolutePath

    @Test
    fun `createFile 后 exists 为真`() = runTest {
        val file = path("a.txt")
        assertTrue(fs.createFile(file) is AppResult.Success)
        assertTrue(fs.exists(file))
        assertFalse(fs.isDirectory(file))
    }

    @Test
    fun `createFile 对已存在文件返回失败`() = runTest {
        val file = path("dup.txt")
        fs.createFile(file)
        val result = fs.createFile(file)
        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun `createDirectory 自动创建父目录`() = runTest {
        val dir = path("x", "y", "z")
        assertTrue(fs.createDirectory(dir) is AppResult.Success)
        assertTrue(fs.isDirectory(dir))
    }

    @Test
    fun `list 返回目录内容并标记目录`() = runTest {
        fs.createDirectory(path("sub"))
        fs.createFile(path("f1.txt"))

        val result = fs.list(root.absolutePath)
        val items = (result as AppResult.Success).data
        assertEquals(2, items.size)
        assertEquals(1, items.count { it.isDirectory })
    }

    @Test
    fun `list 对不存在路径返回 NotFound`() = runTest {
        val result = fs.list(path("nope"))
        assertTrue((result as AppResult.Failure).error is FileError.NotFound)
    }

    @Test
    fun `copy 目录时递归复制全部内容`() = runTest {
        fs.createDirectory(path("src", "nested"))
        writeFile(path("src", "a.txt"), "hello")
        writeFile(path("src", "nested", "b.txt"), "world")

        val result = fs.copy(path("src"), path("dst"))
        assertTrue(result is AppResult.Success)
        assertEquals("hello", File(path("dst", "a.txt")).readText())
        assertEquals("world", File(path("dst", "nested", "b.txt")).readText())
    }

    @Test
    fun `copy 到自身子目录被拒绝`() = runTest {
        fs.createDirectory(path("src"))
        val result = fs.copy(path("src"), path("src", "inner"))
        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun `copy 报告进度`() = runTest {
        val content = "x".repeat(200_000)
        writeFile(path("big.bin"), content)

        val progress = mutableListOf<Pair<Long, Long>>()
        fs.copy(path("big.bin"), path("big-copy.bin"), object : FileSystemProvider.ProgressCallback {
            override fun onProgress(bytesProcessed: Long, bytesTotal: Long) {
                progress += bytesProcessed to bytesTotal
            }
        })

        assertTrue(progress.isNotEmpty())
        assertEquals(content.length.toLong(), progress.last().first)
        assertEquals(content.length.toLong(), progress.last().second)
    }

    @Test
    fun `move 之后源路径消失`() = runTest {
        writeFile(path("m.txt"), "data")
        val result = fs.move(path("m.txt"), path("moved.txt"))
        assertTrue(result is AppResult.Success)
        assertFalse(File(path("m.txt")).exists())
        assertEquals("data", File(path("moved.txt")).readText())
    }

    @Test
    fun `rename 修改文件名`() = runTest {
        writeFile(path("old.txt"), "1")
        val result = fs.rename(path("old.txt"), "new.txt")
        assertTrue(result is AppResult.Success)
        assertTrue(File(path("new.txt")).exists())
    }

    @Test
    fun `rename 拒绝包含分隔符的新名称`() = runTest {
        writeFile(path("old2.txt"), "1")
        val result = fs.rename(path("old2.txt"), "a/b.txt")
        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun `delete 递归删除目录`() = runTest {
        fs.createDirectory(path("del", "inner"))
        writeFile(path("del", "inner", "f.txt"), "1")

        assertTrue(fs.delete(path("del")) is AppResult.Success)
        assertFalse(File(path("del")).exists())
    }

    @Test
    fun `沙箱外路径被拒绝`() = runTest {
        val outside = File(System.getProperty("java.io.tmpdir"), "outside-mt-test.txt").absolutePath
        val result = fs.createFile(outside)
        assertTrue((result as AppResult.Failure).error is FileError.PermissionDenied)
    }

    @Test
    fun `openInputStream 与 openOutputStream 可往返读写`() = runTest {
        val file = path("io.txt")
        val output = (fs.openOutputStream(file) as AppResult.Success).data
        output.use { it.write("stream-data".toByteArray()) }

        val input = (fs.openInputStream(file) as AppResult.Success).data
        val text = input.use { it.readBytes().decodeToString() }
        assertEquals("stream-data", text)
    }

    private fun writeFile(path: String, content: String) {
        File(path).apply {
            parentFile?.mkdirs()
            writeText(content)
        }
    }
}

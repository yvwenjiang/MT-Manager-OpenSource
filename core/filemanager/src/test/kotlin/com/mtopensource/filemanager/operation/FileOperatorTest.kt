// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.filemanager.operation

import com.mtopensource.filemanager.filesystem.FileSystemRouter
import com.mtopensource.filemanager.filesystem.LocalFileSystemProvider
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileOperatorTest {

    private lateinit var root: File
    private lateinit var router: FileSystemRouter

    @Before
    fun setUp() {
        root = Files.createTempDirectory("mt-op-test").toFile()
        router = FileSystemRouter(listOf(LocalFileSystemProvider(allowedRoots = listOf(root.canonicalPath))))
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun path(vararg parts: String) = File(root, parts.joinToString("/")).absolutePath

    private fun writeFile(path: String, content: String = "data") =
        File(path).apply {
            parentFile?.mkdirs()
            writeText(content)
        }

    @Test
    fun `复制文件到目标目录`() = runTest {
        writeFile(path("a.txt"), "hello")
        val operator = FileOperator(router, TestScope(StandardTestDispatcher(testScheduler)))

        var result: OperationResult? = null
        operator.execute(FileOperation(FileOperationType.COPY, listOf(path("a.txt")), target = root.absolutePath)) {
            result = it
        }

        testScheduler.advanceUntilIdle()
        assertEquals(OperationResult.Success(0, 1).copy(operationId = 0), (result as OperationResult.Success).copy(operationId = 0))
        assertTrue(File(path("a.txt")).exists())
    }

    @Test
    fun `复制时遇到同名文件自动生成副本名`() = runTest {
        writeFile(path("a.txt"), "new")
        writeFile(path("dst", "a.txt"), "old")

        val operator = FileOperator(router, TestScope(StandardTestDispatcher(testScheduler)))
        operator.execute(FileOperation(FileOperationType.COPY, listOf(path("a.txt")), target = path("dst")))
        testScheduler.advanceUntilIdle()

        assertTrue(File(path("dst", "a.txt")).exists())
        assertTrue(File(path("dst", "a (1).txt")).exists())
        assertEquals("old", File(path("dst", "a.txt")).readText())
    }

    @Test
    fun `覆盖模式会替换已存在文件`() = runTest {
        writeFile(path("a.txt"), "new")
        writeFile(path("dst", "a.txt"), "old")

        val operator = FileOperator(router, TestScope(StandardTestDispatcher(testScheduler)))
        operator.execute(
            FileOperation(FileOperationType.COPY, listOf(path("a.txt")), target = path("dst"), overwrite = true),
        )
        testScheduler.advanceUntilIdle()

        assertEquals("new", File(path("dst", "a.txt")).readText())
    }

    @Test
    fun `批量删除并回报成功数量`() = runTest {
        writeFile(path("1.txt"))
        writeFile(path("2.txt"))

        val operator = FileOperator(router, TestScope(StandardTestDispatcher(testScheduler)))
        var result: OperationResult? = null
        operator.execute(FileOperation(FileOperationType.DELETE, listOf(path("1.txt"), path("2.txt")))) { result = it }
        testScheduler.advanceUntilIdle()

        assertEquals(2, (result as OperationResult.Success).affectedCount)
        assertFalse(File(path("1.txt")).exists())
    }

    @Test
    fun `删除不存在的文件返回失败并保留已成功计数`() = runTest {
        writeFile(path("ok.txt"))

        val operator = FileOperator(router, TestScope(StandardTestDispatcher(testScheduler)))
        var result: OperationResult? = null
        operator.execute(
            FileOperation(FileOperationType.DELETE, listOf(path("ok.txt"), path("missing.txt"))),
        ) { result = it }
        testScheduler.advanceUntilIdle()

        val failure = result as OperationResult.Failure
        assertEquals(1, failure.succeededCount)
        assertEquals(path("missing.txt"), failure.failedPath)
    }

    @Test
    fun `重命名操作生效`() = runTest {
        writeFile(path("old.txt"))

        val operator = FileOperator(router, TestScope(StandardTestDispatcher(testScheduler)))
        operator.execute(
            FileOperation(FileOperationType.RENAME, listOf(path("old.txt")), newName = "renamed.txt"),
        )
        testScheduler.advanceUntilIdle()

        assertTrue(File(path("renamed.txt")).exists())
        assertFalse(File(path("old.txt")).exists())
    }

    @Test
    fun `创建目录操作生效`() = runTest {
        val operator = FileOperator(router, TestScope(StandardTestDispatcher(testScheduler)))
        operator.execute(FileOperation(FileOperationType.CREATE_DIRECTORY, emptyList(), target = path("new", "dir")))
        testScheduler.advanceUntilIdle()

        assertTrue(File(path("new", "dir")).isDirectory)
    }

    @Test
    fun `移动文件后源文件消失`() = runTest {
        writeFile(path("m.txt"), "x")

        val operator = FileOperator(router, TestScope(StandardTestDispatcher(testScheduler)))
        operator.execute(FileOperation(FileOperationType.MOVE, listOf(path("m.txt")), target = path("dst")))
        testScheduler.advanceUntilIdle()

        assertTrue(File(path("dst", "m.txt")).exists())
        assertFalse(File(path("m.txt")).exists())
    }

    @Test
    fun `进度百分比在总字节未知时按文件数计算`() {
        val progress = OperationProgress(
            operationId = 1,
            type = FileOperationType.COPY,
            processedFiles = 3,
            totalFiles = 4,
            totalBytes = -1,
        )
        assertEquals(75, progress.percent)
    }

    @Test
    fun `进度百分比在总字节已知时按字节计算`() {
        val progress = OperationProgress(
            operationId = 1,
            type = FileOperationType.COPY,
            processedBytes = 512,
            totalBytes = 1024,
        )
        assertEquals(50, progress.percent)
    }
}

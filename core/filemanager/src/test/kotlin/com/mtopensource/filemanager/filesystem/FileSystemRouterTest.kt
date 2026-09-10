// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.filemanager.filesystem

import com.mtopensource.common.result.AppResult
import com.mtopensource.common.result.FileError
import com.mtopensource.filemanager.model.FileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream

class FileSystemRouterTest {

    /** 仅用于路由测试的哑实现。 */
    private class FakeProvider(override val scheme: String) : FileSystemProvider {
        override val isWritable = true
        override suspend fun list(path: String): AppResult<List<FileItem>, FileError> = AppResult.success(emptyList())
        override suspend fun exists(path: String) = false
        override suspend fun isDirectory(path: String) = false
        override suspend fun length(path: String) = 0L
        override suspend fun lastModified(path: String) = 0L
        override suspend fun createFile(path: String): AppResult<Unit, FileError> = AppResult.success(Unit)
        override suspend fun createDirectory(path: String): AppResult<Unit, FileError> = AppResult.success(Unit)
        override suspend fun delete(path: String): AppResult<Unit, FileError> = AppResult.success(Unit)
        override suspend fun rename(path: String, newName: String): AppResult<Unit, FileError> = AppResult.success(Unit)
        override suspend fun copy(source: String, target: String, onProgress: FileSystemProvider.ProgressCallback?) =
            AppResult.success(Unit)

        override suspend fun move(source: String, target: String, onProgress: FileSystemProvider.ProgressCallback?) =
            AppResult.success(Unit)

        override suspend fun openInputStream(path: String): AppResult<InputStream, FileError> =
            AppResult.failure(FileError.Io("不支持"))

        override suspend fun openOutputStream(path: String): AppResult<OutputStream, FileError> =
            AppResult.failure(FileError.Io("不支持"))
    }

    @Test
    fun `schemeOf 解析显式协议`() {
        val router = FileSystemRouter()
        assertEquals("archive", router.schemeOf("archive:///sdcard/a.zip!/inner"))
        assertEquals("root", router.schemeOf("root:///system/build.prop"))
        assertEquals(FileItem.SCHEME_FILE, router.schemeOf("/sdcard/Download"))
        assertEquals(FileItem.SCHEME_FILE, router.schemeOf("relative/path"))
    }

    @Test
    fun `resolve 优先匹配注册的 scheme`() {
        val local = FakeProvider(FileItem.SCHEME_FILE)
        val archive = FakeProvider(FileItem.SCHEME_ARCHIVE)
        val router = FileSystemRouter(listOf(local, archive))

        assertEquals(archive, router.resolve("archive:///a.zip"))
        assertEquals(local, router.resolve("/sdcard/x"))
    }

    @Test
    fun `未注册 scheme 回退到本地文件系统`() {
        val local = FakeProvider(FileItem.SCHEME_FILE)
        val router = FileSystemRouter(listOf(local))
        assertEquals(local, router.resolve("remote://server/file"))
    }

    @Test
    fun `注册与注销动态生效`() {
        val local = FakeProvider(FileItem.SCHEME_FILE)
        val router = FileSystemRouter(listOf(local))

        val remote = FakeProvider(FileItem.SCHEME_REMOTE)
        router.register(remote)
        assertEquals(remote, router.resolve("remote://host/f"))
        assertTrue(router.registeredSchemes().contains(FileItem.SCHEME_REMOTE))

        router.unregister(FileItem.SCHEME_REMOTE)
        assertEquals(local, router.resolve("remote://host/f"))
    }

    @Test
    fun `stripScheme 去除协议前缀`() {
        val router = FileSystemRouter()
        assertEquals("/sdcard/a.zip", router.stripScheme("archive:///sdcard/a.zip"))
        assertEquals("/sdcard/a", router.stripScheme("/sdcard//a"))
    }
}

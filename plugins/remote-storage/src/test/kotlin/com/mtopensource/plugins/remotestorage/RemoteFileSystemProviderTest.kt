// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.plugins.remotestorage

import com.mtopensource.common.result.AppResult
import com.mtopensource.common.result.FileError
import com.mtopensource.filemanager.model.FileItem
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RemoteFileSystemProviderTest {

    private lateinit var dataDir: File
    private lateinit var store: RemoteConnectionStore
    private lateinit var provider: RemoteFileSystemProvider

    @Before
    fun setUp() {
        dataDir = Files.createTempDirectory("mt-remote-fs").toFile()
        store = RemoteConnectionStore(dataDir.absolutePath)
        provider = RemoteFileSystemProvider(store)
    }

    @After
    fun tearDown() {
        dataDir.deleteRecursively()
    }

    @Test
    fun `scheme 为 remote`() {
        assertEquals("remote", provider.scheme)
    }

    @Test
    fun `解析标准远程路径`() {
        val parsed = provider.parseRemotePath("remote://我的服务器/var/log/app.log")
        assertNotNull(parsed)
        assertEquals("我的服务器", parsed!!.connectionName)
        assertEquals("/var/log/app.log", parsed.remotePath)
    }

    @Test
    fun `解析省略路径的远程地址`() {
        val parsed = provider.parseRemotePath("remote://服务器")!!
        assertEquals("服务器", parsed.connectionName)
        assertEquals("/", parsed.remotePath)
    }

    @Test
    fun `不含 scheme 的路径解析失败`() {
        assertNull(provider.parseRemotePath("/sdcard/file"))
        assertNull(provider.parseRemotePath("ftp://host/path"))
    }

    @Test
    fun `缺少连接名解析失败`() {
        assertNull(provider.parseRemotePath("remote://"))
        assertNull(provider.parseRemotePath("remote:///var/log"))
    }

    @Test
    fun `RemotePath 可还原为完整路径`() {
        val parsed = provider.parseRemotePath("remote://srv/var/log")!!
        assertEquals("remote://srv/var/log", parsed.toFullPath())
    }

    @Test
    fun `未配置连接时返回 NotFound`() = runTest {
        val result = provider.list("remote://不存在的连接/var")
        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is FileError.NotFound)
    }

    @Test
    fun `非法路径返回 IllegalOperation`() = runTest {
        val result = provider.list("/local/path")
        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is FileError.IllegalOperation)
    }

    @Test
    fun `已配置连接但协议未接入时给出明确提示`() = runTest {
        store.upsert(
            RemoteConnection(
                name = "srv",
                protocol = RemoteProtocol.SFTP,
                host = "example.com",
                username = "root",
            ),
        )

        val result = provider.list("remote://srv/var/log")
        assertTrue(result is AppResult.Failure)
        val error = (result as AppResult.Failure).error as FileError.IllegalOperation
        assertTrue(error.reason.contains("SFTP"))
        assertTrue(error.reason.contains("尚未接入"))
    }

    @Test
    fun `写操作明确提示未实现`() = runTest {
        val result = provider.createDirectory("remote://srv/newdir")
        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is FileError.IllegalOperation)
    }

    @Test
    fun `exists 对未知连接返回 false`() = runTest {
        assertEquals(false, provider.exists("remote://ghost/path"))
    }

    @Test
    fun `自定义传输层可被注入使用`() = runTest {
        store.upsert(
            RemoteConnection(name = "srv", protocol = RemoteProtocol.FTP, host = "h", username = "u"),
        )

        var listCalledWith: String? = null
        val custom = RemoteFileSystemProvider(store) { connection ->
            assertNotNull(connection)
            object : RemoteTransport {
                override suspend fun list(path: String): AppResult<List<FileItem>, FileError> {
                    listCalledWith = path
                    return AppResult.success(emptyList())
                }

                override suspend fun exists(path: String) = true

                override fun disconnect() = Unit
            }
        }

        val result = custom.list("remote://srv/var")
        assertTrue(result is AppResult.Success)
        assertEquals("/var", listCalledWith)
    }
}

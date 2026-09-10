// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.plugins.remotestorage

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RemoteConnectionStoreTest {

    private lateinit var dataDir: File
    private lateinit var store: RemoteConnectionStore

    @Before
    fun setUp() {
        dataDir = Files.createTempDirectory("mt-remote-test").toFile()
        store = RemoteConnectionStore(dataDir.absolutePath)
    }

    @After
    fun tearDown() {
        dataDir.deleteRecursively()
    }

    private fun sampleConnection(
        name: String = "我的服务器",
        protocol: RemoteProtocol = RemoteProtocol.SFTP,
        authType: RemoteAuthType = RemoteAuthType.PASSWORD,
    ) = RemoteConnection(
        name = name,
        protocol = protocol,
        host = "192.168.1.100",
        port = protocol.defaultPort,
        authType = authType,
        username = "admin",
        password = "secret123",
        initialPath = "/var/log",
    )

    @Test
    fun `空存储返回空列表`() {
        assertTrue(store.loadAll().isEmpty())
    }

    @Test
    fun `保存后可完整读回`() {
        val connection = sampleConnection()
        assertTrue(store.saveAll(listOf(connection)))

        val loaded = store.loadAll()
        assertEquals(1, loaded.size)
        assertEquals(connection.name, loaded.first().name)
        assertEquals(connection.host, loaded.first().host)
        assertEquals(connection.protocol, loaded.first().protocol)
        assertEquals(connection.username, loaded.first().username)
        assertEquals(connection.password, loaded.first().password)
    }

    @Test
    fun `密码不以明文写入磁盘`() {
        store.saveAll(listOf(sampleConnection()))
        val raw = File(dataDir, RemoteConnectionStore.FILE_NAME).readText()
        assertFalse("磁盘上不应出现明文密码", raw.contains("secret123"))
    }

    @Test
    fun `upsert 新增连接`() {
        val result = store.upsert(sampleConnection(name = "A"))
        assertEquals(1, result.size)
        assertEquals("A", store.find("A")?.name)
    }

    @Test
    fun `upsert 更新同名连接而不新增`() {
        store.upsert(sampleConnection(name = "A"))
        val updated = sampleConnection(name = "A").copy(host = "10.0.0.1")
        store.upsert(updated)

        assertEquals(1, store.loadAll().size)
        assertEquals("10.0.0.1", store.find("A")?.host)
    }

    @Test
    fun `delete 按名称删除`() {
        store.upsert(sampleConnection(name = "A"))
        store.upsert(sampleConnection(name = "B"))

        assertTrue(store.delete("A"))
        assertEquals(1, store.loadAll().size)
        assertNull(store.find("A"))
        assertNotNull(store.find("B"))
    }

    @Test
    fun `delete 不存在时返回 false`() {
        assertFalse(store.delete("不存在的连接"))
    }

    @Test
    fun `clear 清空全部配置`() {
        store.upsert(sampleConnection(name = "A"))
        assertTrue(store.clear())
        assertTrue(store.loadAll().isEmpty())
    }

    @Test
    fun `损坏的 JSON 文件不会抛异常`() {
        File(dataDir, RemoteConnectionStore.FILE_NAME).writeText("{ 这不是合法 JSON")
        assertTrue(store.loadAll().isEmpty())
    }

    @Test
    fun `缺失必填字段的记录被跳过`() {
        File(dataDir, RemoteConnectionStore.FILE_NAME).writeText(
            """[{"name":"缺少主机"},{"host":"1.2.3.4"},{"name":"完整","host":"5.6.7.8"}]""",
        )
        val loaded = store.loadAll()
        assertEquals(1, loaded.size)
        assertEquals("完整", loaded.first().name)
    }

    @Test
    fun `未知协议回退为 FTP`() {
        File(dataDir, RemoteConnectionStore.FILE_NAME).writeText(
            """[{"name":"x","host":"h","protocol":"TELNET"}]""",
        )
        assertEquals(RemoteProtocol.FTP, store.loadAll().first().protocol)
    }

    @Test
    fun `空密码往返不产生异常`() {
        val connection = sampleConnection().copy(password = "", privateKey = "")
        store.saveAll(listOf(connection))
        assertEquals("", store.loadAll().first().password)
    }

    @Test
    fun `多种协议均可正确往返`() {
        val connections = RemoteProtocol.entries.map { protocol ->
            sampleConnection(name = protocol.name).copy(protocol = protocol, port = protocol.defaultPort)
        }
        store.saveAll(connections)

        val loaded = store.loadAll()
        assertEquals(RemoteProtocol.entries.size, loaded.size)
        RemoteProtocol.entries.forEach { protocol ->
            assertTrue(loaded.any { it.protocol == protocol })
        }
    }
}

class RemoteConnectionTest {

    @Test
    fun `完备配置校验通过`() {
        val connection = RemoteConnection(
            name = "srv",
            protocol = RemoteProtocol.SFTP,
            host = "example.com",
            username = "root",
        )
        assertTrue(connection.isComplete)
        assertNull(connection.validationError())
    }

    @Test
    fun `缺少名称校验失败`() {
        val connection = RemoteConnection(name = "", protocol = RemoteProtocol.FTP, host = "h")
        assertFalse(connection.isComplete)
        assertTrue(connection.validationError()!!.contains("名称"))
    }

    @Test
    fun `端口越界校验失败`() {
        val connection = RemoteConnection(
            name = "srv",
            protocol = RemoteProtocol.FTP,
            host = "h",
            port = 70000,
            username = "u",
        )
        assertTrue(connection.validationError()!!.contains("端口"))
    }

    @Test
    fun `密码认证缺少用户名校验失败`() {
        val connection = RemoteConnection(
            name = "srv",
            protocol = RemoteProtocol.FTP,
            host = "h",
            authType = RemoteAuthType.PASSWORD,
            username = "",
        )
        assertTrue(connection.validationError()!!.contains("用户名"))
    }

    @Test
    fun `私钥认证仅支持 SFTP`() {
        val connection = RemoteConnection(
            name = "srv",
            protocol = RemoteProtocol.FTP,
            host = "h",
            authType = RemoteAuthType.PRIVATE_KEY,
            privateKey = "KEY",
        )
        assertTrue(connection.validationError()!!.contains("SFTP"))
    }

    @Test
    fun `匿名认证仅支持 FTP 与 WebDAV`() {
        val sftpAnonymous = RemoteConnection(
            name = "srv",
            protocol = RemoteProtocol.SFTP,
            host = "h",
            authType = RemoteAuthType.ANONYMOUS,
        )
        assertTrue(sftpAnonymous.validationError()!!.contains("匿名"))

        val ftpAnonymous = RemoteConnection(
            name = "srv",
            protocol = RemoteProtocol.FTP,
            host = "h",
            authType = RemoteAuthType.ANONYMOUS,
        )
        assertNull(ftpAnonymous.validationError())
    }

    @Test
    fun `初始路径归一化补全斜杠`() {
        val connection = RemoteConnection(name = "s", protocol = RemoteProtocol.FTP, host = "h")
        assertEquals("/", connection.copy(initialPath = "").normalizedInitialPath())
        assertEquals("/var", connection.copy(initialPath = "var").normalizedInitialPath())
        assertEquals("/var", connection.copy(initialPath = "/var").normalizedInitialPath())
    }

    @Test
    fun `连接摘要包含协议与地址`() {
        val connection = RemoteConnection(
            name = "srv",
            protocol = RemoteProtocol.SFTP,
            host = "example.com",
            port = 2222,
        )
        assertEquals("SFTP (SSH)://example.com:2222", connection.displayAddress())
    }

    @Test
    fun `默认端口取自协议`() {
        assertEquals(21, RemoteProtocol.FTP.defaultPort)
        assertEquals(22, RemoteProtocol.SFTP.defaultPort)
        assertEquals(445, RemoteProtocol.SMB.defaultPort)
        assertEquals(443, RemoteProtocol.WEBDAV_HTTPS.defaultPort)
    }
}

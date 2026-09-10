// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.plugin.host

import com.mtopensource.filemanager.filesystem.FileSystemRouter
import com.mtopensource.plugin.api.HostAction
import com.mtopensource.plugin.api.PluginCapability
import com.mtopensource.plugin.api.PluginConfiguration
import com.mtopensource.plugin.bridge.PluginManifest
import com.mtopensource.plugin.bridge.PluginState
import com.mtopensource.plugin.host.testfixtures.TestPlugin
import kotlinx.coroutines.test.runTest
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

/**
 * 测试用宿主服务实现。
 */
private class TestHostServices(
    override val fileSystemRouter: FileSystemRouter = FileSystemRouter(),
    var rootDir: File,
) : HostServices {

    override val configuration: PluginConfiguration = PluginConfiguration()

    val performedActions = mutableListOf<HostAction>()

    /** 可动态设置的宿主能力（例如设备是否 Root）。 */
    val hostCapabilities = mutableSetOf<PluginCapability>()

    override fun hasCapability(capability: PluginCapability): Boolean = capability in hostCapabilities

    override fun performAction(action: HostAction) {
        performedActions += action
    }

    override fun pluginDataDir(pluginId: String): String = File(rootDir, "data/$pluginId").apply { mkdirs() }.absolutePath

    override fun pluginCacheDir(pluginId: String): String = File(rootDir, "cache/$pluginId").apply { mkdirs() }.absolutePath

    override fun pluginOptimizedDir(pluginId: String): String = File(rootDir, "opt/$pluginId").apply { mkdirs() }.absolutePath
}

class PluginManagerTest {

    private lateinit var root: File
    private lateinit var pluginDir: File
    private lateinit var services: TestHostServices
    private lateinit var manager: PluginManager

    @Before
    fun setUp() {
        root = Files.createTempDirectory("mt-plugin-host").toFile()
        pluginDir = File(root, "plugins").apply { mkdirs() }
        services = TestHostServices(rootDir = root)
        manager = PluginManager(services, javaClass.classLoader)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    /** 创建一个内含有效描述文件的插件目录。 */
    private fun createTestPlugin(
        id: String = "com.test.plugin",
        name: String = "测试插件",
        capabilities: List<String> = listOf("CUSTOM_FILESYSTEM", "CONTEXT_MENU"),
        requiredApiVersion: Int = 1,
    ): File {
        val dir = File(pluginDir, id).apply { mkdirs() }
        File(dir, PluginManifest.FILE_NAME).writeText(
            """
            {
              "id": "$id",
              "name": "$name",
              "version": "1.0.0",
              "entryClass": "${TestPlugin::class.java.name}",
              "requiredApiVersion": $requiredApiVersion,
              "capabilities": ${capabilities.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }}
            }
            """.trimIndent(),
        )
        return dir
    }

    @Test
    fun `扫描目录发现插件`() = runTest {
        createTestPlugin(id = "com.a", name = "插件 A")
        createTestPlugin(id = "com.b", name = "插件 B")

        val records = manager.scan(pluginDir.absolutePath)
        assertEquals(2, records.size)
        assertTrue(records.all { it.state == PluginState.DISCOVERED })
        assertEquals(0, manager.activeCount)
    }

    @Test
    fun `扫描时自动加载可立即激活插件`() = runTest {
        createTestPlugin(id = "com.auto")
        manager.scan(pluginDir.absolutePath, autoLoad = true)

        assertEquals(1, manager.activeCount)
        assertEquals(PluginState.ACTIVE, manager.plugins.value.single().state)
    }

    @Test
    fun `加载插件调用 onLoad 并注入上下文`() = runTest {
        val dir = createTestPlugin(id = "com.loaded")
        val plugin = manager.load(dir.absolutePath) ?: error("加载失败")

        // 返回的是插件实例自身声明的 info（而非描述文件里的 id），
        // 二者在真实场景中应保持一致，测试夹具未刻意对齐。
        assertEquals("com.mtopensource.test.fixture", plugin.id)
        assertEquals(PluginState.ACTIVE, manager.plugins.value.single().state)

        val loaded = manager.activePlugin("com.loaded") as TestPlugin
        assertTrue(loaded.loadCalled)
    }

    @Test
    fun `插件记录中保存的是描述文件声明的 id`() = runTest {
        val dir = createTestPlugin(id = "com.loaded")
        manager.load(dir.absolutePath)

        val record = manager.plugins.value.single()
        assertEquals("com.loaded", record.manifest.id)
    }

    @Test
    fun `插件在 onLoad 中注册的文件系统可被路由使用`() = runTest {
        val dir = createTestPlugin(id = "com.fs", capabilities = listOf("CUSTOM_FILESYSTEM"))
        manager.load(dir.absolutePath)

        val loaded = manager.activePlugin("com.fs") as TestPlugin
        assertTrue(loaded.loadCalled)
        assertTrue("custom" in services.fileSystemRouter.registeredSchemes())
    }

    @Test
    fun `未声明能力时注册文件系统被拒绝`() = runTest {
        val dir = createTestPlugin(id = "com.nocap", capabilities = listOf("APP_DATA"))
        manager.load(dir.absolutePath)

        val loaded = manager.activePlugin("com.nocap") as TestPlugin
        assertFalse(loaded.fileSystemRegisterSucceeded)
        assertFalse("custom" in services.fileSystemRouter.registeredSchemes())
    }

    @Test
    fun `未声明能力时注册菜单项被拒绝`() = runTest {
        val dir = createTestPlugin(id = "com.nomenu", capabilities = listOf("APP_DATA"))
        manager.load(dir.absolutePath)

        val loaded = manager.activePlugin("com.nomenu") as TestPlugin
        assertFalse(loaded.menuRegisterSucceeded)
        assertTrue(manager.collectContextMenus().isEmpty())
    }

    @Test
    fun `卸载插件回滚已注册资源`() = runTest {
        val dir = createTestPlugin(id = "com.rollback")
        manager.load(dir.absolutePath)

        assertTrue("custom" in services.fileSystemRouter.registeredSchemes())
        assertEquals(1, manager.collectContextMenus().size)

        assertTrue(manager.unload("com.rollback"))

        assertFalse("custom" in services.fileSystemRouter.registeredSchemes())
        assertTrue(manager.collectContextMenus().isEmpty())
        assertEquals(0, manager.activeCount)
        assertEquals(PluginState.UNLOADED, manager.plugins.value.single().state)
    }

    @Test
    fun `卸载未加载插件返回 false`() = runTest {
        assertFalse(manager.unload("com.ghost"))
    }

    @Test
    fun `API 版本不兼容的插件不会被加载`() = runTest {
        val dir = createTestPlugin(id = "com.future", requiredApiVersion = 999)
        assertNull(manager.load(dir.absolutePath))

        val record = manager.plugins.value.single()
        assertEquals(PluginState.INCOMPATIBLE, record.state)
        assertNotNull(record.errorMessage)
    }

    @Test
    fun `入口类不存在时记录 ERROR 状态`() = runTest {
        val dir = File(pluginDir, "com.broken").apply { mkdirs() }
        File(dir, PluginManifest.FILE_NAME).writeText(
            """
            {
              "id": "com.broken",
              "name": "损坏插件",
              "entryClass": "com.does.not.Exist",
              "capabilities": []
            }
            """.trimIndent(),
        )

        assertNull(manager.load(dir.absolutePath))
        val record = manager.plugins.value.single()
        assertEquals(PluginState.ERROR, record.state)
        assertNotNull(record.errorMessage)
    }

    @Test
    fun `重复加载同一插件不会创建第二个实例`() = runTest {
        val dir = createTestPlugin(id = "com.dup")
        manager.load(dir.absolutePath)
        manager.load(dir.absolutePath)

        assertEquals(1, manager.activeCount)
        assertEquals(1, manager.plugins.value.size)
    }

    @Test
    fun `scan 对重复 id 去重`() = runTest {
        createTestPlugin(id = "com.same", name = "第一个")
        createTestPlugin(id = "com.same", name = "第二个")

        val records = manager.scan(pluginDir.absolutePath)
        assertEquals(1, records.size)
    }

    @Test
    fun `菜单项按 order 排序且带插件前缀`() = runTest {
        val dir = createTestPlugin(id = "com.menu", capabilities = listOf("CONTEXT_MENU"))
        manager.load(dir.absolutePath)

        val menus = manager.collectContextMenus()
        assertEquals(1, menus.size)
        assertTrue(menus.first().id.startsWith("com.menu:"))
    }

    @Test
    fun `hasHostCapability 转发给宿主服务`() = runTest {
        val dir = createTestPlugin(id = "com.cap", capabilities = listOf("APP_DATA"))
        manager.load(dir.absolutePath)
        val loaded = manager.activePlugin("com.cap") as TestPlugin

        // 宿主授予 Root 能力时，插件查询应得到 true
        services.hostCapabilities += PluginCapability.ROOT_FILESYSTEM
        loaded.checkRootCapability()
        assertTrue(loaded.rootCapabilityDetected)

        // 宿主撤销能力后，再次查询应得到 false
        services.hostCapabilities.clear()
        loaded.checkRootCapability()
        assertFalse(loaded.rootCapabilityDetected)
    }

    @Test
    fun `unloadAll 卸载全部插件`() = runTest {
        createTestPlugin(id = "com.one")
        createTestPlugin(id = "com.two")
        manager.scan(pluginDir.absolutePath, autoLoad = true)
        assertEquals(2, manager.activeCount)

        manager.unloadAll()
        assertEquals(0, manager.activeCount)
    }

    @Test
    fun `配置变化通知到所有插件`() = runTest {
        val dir = createTestPlugin(id = "com.conf")
        manager.load(dir.absolutePath)

        manager.notifyConfigurationChanged(PluginConfiguration(darkTheme = true))
        val loaded = manager.activePlugin("com.conf") as TestPlugin
        assertTrue(loaded.lastConfiguration?.darkTheme == true)
    }
}

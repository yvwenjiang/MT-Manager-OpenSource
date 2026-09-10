// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.plugin.bridge

import com.mtopensource.plugin.api.PluginCapability
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PluginDescriptorReaderTest {

    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("mt-plugin-test").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun sampleJson(
        id: String = "com.example.demo",
        name: String = "演示插件",
        requiredApiVersion: Int = 1,
    ) = """
        {
          "id": "$id",
          "name": "$name",
          "version": "1.0.0",
          "entryClass": "com.example.DemoPlugin",
          "requiredApiVersion": $requiredApiVersion,
          "capabilities": ["APK_EDIT"]
        }
    """.trimIndent()

    private fun createPluginDir(name: String, json: String): File =
        File(root, name).apply {
            mkdirs()
            File(this, PluginManifest.FILE_NAME).writeText(json)
        }

    private fun createPluginArchive(name: String, json: String, entryPrefix: String = ""): File {
        val archive = File(root, name)
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("$entryPrefix${PluginManifest.FILE_NAME}"))
            zip.write(json.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("${entryPrefix}classes.dex"))
            zip.write(ByteArray(16))
            zip.closeEntry()
        }
        return archive
    }

    @Test
    fun `从目录读取描述文件`() {
        val dir = createPluginDir("demo-plugin", sampleJson())
        val manifest = PluginDescriptorReader.read(dir.absolutePath)
        assertNotNull(manifest)
        assertEquals("com.example.demo", manifest!!.id)
    }

    @Test
    fun `从 APK 压缩包读取描述文件`() {
        val apk = createPluginArchive("demo.apk", sampleJson(name = "APK 插件"))
        val manifest = PluginDescriptorReader.read(apk.absolutePath)
        assertNotNull(manifest)
        assertEquals("APK 插件", manifest!!.name)
    }

    @Test
    fun `压缩包内嵌套目录的 plugin_json 也能找到`() {
        val apk = createPluginArchive("nested.jar", sampleJson(id = "com.nested"), entryPrefix = "assets/plugin/")
        val manifest = PluginDescriptorReader.read(apk.absolutePath)
        assertNotNull(manifest)
        assertEquals("com.nested", manifest!!.id)
    }

    @Test
    fun `载体不存在返回 null`() {
        assertNull(PluginDescriptorReader.read(File(root, "ghost.apk").absolutePath))
    }

    @Test
    fun `目录缺少 plugin_json 返回 null`() {
        val dir = File(root, "empty").apply { mkdirs() }
        assertNull(PluginDescriptorReader.read(dir.absolutePath))
    }

    @Test
    fun `压缩包缺少 plugin_json 返回 null`() {
        val archive = File(root, "no-manifest.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("classes.dex"))
            zip.write(ByteArray(4))
            zip.closeEntry()
        }
        assertNull(PluginDescriptorReader.read(archive.absolutePath))
    }

    @Test
    fun `校验通过返回 null`() {
        val manifest = PluginManifest.parse(sampleJson())!!
        assertNull(PluginDescriptorReader.validate(manifest))
    }

    @Test
    fun `API 版本过高时校验失败`() {
        val manifest = PluginManifest.parse(sampleJson(requiredApiVersion = 42))!!
        val error = PluginDescriptorReader.validate(manifest)
        assertNotNull(error)
        assertTrue(error!!.contains("v42"))
    }

    @Test
    fun `未知能力声明校验失败`() {
        val manifest = PluginManifest.parse(sampleJson())!!.copy(capabilities = setOf("APK_EDIT", "MIND_READ"))
        val error = PluginDescriptorReader.validate(manifest)
        assertNotNull(error)
        assertTrue(error!!.contains("MIND_READ"))
    }

    @Test
    fun `parseCapabilities 过滤未知能力`() {
        val capabilities = PluginDescriptorReader.parseCapabilities(setOf("APK_EDIT", "UNKNOWN", "ROOT_FILESYSTEM"))
        assertEquals(setOf(PluginCapability.APK_EDIT, PluginCapability.ROOT_FILESYSTEM), capabilities)
    }

    @Test
    fun `扫描目录返回全部插件并标记兼容性`() {
        createPluginDir("ok-plugin", sampleJson(id = "com.ok", name = "正常插件"))
        createPluginDir("future-plugin", sampleJson(id = "com.future", name = "未来插件", requiredApiVersion = 999))

        val records = PluginDescriptorReader.scanDirectory(root.absolutePath)
        assertEquals(2, records.size)

        val ok = records.first { it.manifest.id == "com.ok" }
        assertEquals(PluginState.DISCOVERED, ok.state)
        assertNull(ok.errorMessage)

        val future = records.first { it.manifest.id == "com.future" }
        assertEquals(PluginState.INCOMPATIBLE, future.state)
        assertNotNull(future.errorMessage)
    }

    @Test
    fun `扫描不存在的目录返回空列表`() {
        assertTrue(PluginDescriptorReader.scanDirectory(File(root, "missing").absolutePath).isEmpty())
    }
}

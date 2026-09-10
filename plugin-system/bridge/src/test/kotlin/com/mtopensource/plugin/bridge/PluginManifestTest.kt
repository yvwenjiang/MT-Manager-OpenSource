// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.plugin.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginManifestTest {

    private val validJson = """
        {
          "id": "com.example.apkeditor",
          "name": "APK 编辑器",
          "version": "1.2.0",
          "author": "Example",
          "description": "签名校验与重打包",
          "entryClass": "com.example.apkeditor.ApkEditorPlugin",
          "requiredApiVersion": 1,
          "capabilities": ["APK_EDIT", "APP_DATA"]
        }
    """.trimIndent()

    @Test
    fun `解析完整描述文件`() {
        val manifest = PluginManifest.parse(validJson)
        assertNotNull(manifest)
        manifest!!
        assertEquals("com.example.apkeditor", manifest.id)
        assertEquals("APK 编辑器", manifest.name)
        assertEquals("1.2.0", manifest.version)
        assertEquals("com.example.apkeditor.ApkEditorPlugin", manifest.entryClass)
        assertEquals(1, manifest.requiredApiVersion)
        assertEquals(setOf("APK_EDIT", "APP_DATA"), manifest.capabilities)
    }

    @Test
    fun `缺少必填字段返回 null`() {
        assertNull(PluginManifest.parse("""{"name":"x","entryClass":"Y"}"""))
        assertNull(PluginManifest.parse("""{"id":"a","entryClass":"Y"}"""))
        assertNull(PluginManifest.parse("""{"id":"a","name":"x"}"""))
    }

    @Test
    fun `非法 JSON 返回 null 而不抛异常`() {
        assertNull(PluginManifest.parse("{ not json "))
        assertNull(PluginManifest.parse(""))
    }

    @Test
    fun `可选字段使用默认值`() {
        val manifest = PluginManifest.parse(
            """{"id":"a","name":"b","entryClass":"C"}""",
        )!!
        assertEquals("0.0.0", manifest.version)
        assertEquals("", manifest.author)
        assertTrue(manifest.capabilities.isEmpty())
    }

    @Test
    fun `兼容性校验按 requiredApiVersion 判断`() {
        val manifest = PluginManifest.parse(validJson)!!
        assertTrue(manifest.isCompatibleWithHost())
        assertNull(manifest.compatibilityError())

        val future = manifest.copy(requiredApiVersion = 99)
        assertFalse(future.isCompatibleWithHost())
        assertNotNull(future.compatibilityError())
        assertTrue(future.compatibilityError()!!.contains("99"))
    }

    @Test
    fun `capabilities 中空白项被忽略`() {
        val manifest = PluginManifest.parse(
            """{"id":"a","name":"b","entryClass":"C","capabilities":["APK_EDIT","","  "]}""",
        )!!
        assertEquals(setOf("APK_EDIT"), manifest.capabilities)
    }
}

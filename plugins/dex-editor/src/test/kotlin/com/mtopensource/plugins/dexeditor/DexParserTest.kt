// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.plugins.dexeditor

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

class DexParserTest {

    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("mt-dex-test").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    /**
     * 构造一个最小但结构合法的 DEX 头部（0x70 = 112 字节）。
     *
     * 字段顺序严格对齐官方规范，否则解析结果会整体错位。
     */
    private fun buildDexBytes(
        version: String = "035",
        fileSize: Int = 0x1000,
        stringIds: Int = 100,
        typeIds: Int = 50,
        protoIds: Int = 30,
        fieldIds: Int = 20,
        methodIds: Int = 200,
        classDefs: Int = 10,
    ): ByteArray {
        val buffer = ByteBuffer.allocate(0x70).order(ByteOrder.LITTLE_ENDIAN)

        // magic: "dex\n035\0"
        val magic = "dex\n$version\u0000".toByteArray(Charsets.ISO_8859_1)
        buffer.put(magic.copyOf(8))                        // 0x00 magic (8)

        buffer.putInt(0x1A2B3C4D)                          // 0x08 checksum
        buffer.put(ByteArray(20) { (it + 1).toByte() })    // 0x0C signature (SHA-1)
        buffer.putInt(fileSize)                            // 0x20 file_size
        buffer.putInt(0x70)                                // 0x24 header_size
        buffer.putInt(0x12345678)                          // 0x28 endian_tag
        buffer.putInt(0); buffer.putInt(0)                 // 0x2C link_size, link_off
        buffer.putInt(0)                                   // 0x34 map_off
        buffer.putInt(stringIds); buffer.putInt(0)         // 0x38 string_ids_size, off
        buffer.putInt(typeIds); buffer.putInt(0)           // 0x40 type_ids_size, off
        buffer.putInt(protoIds); buffer.putInt(0)          // 0x48 proto_ids_size, off
        buffer.putInt(fieldIds); buffer.putInt(0)          // 0x50 field_ids_size, off
        buffer.putInt(methodIds); buffer.putInt(0)         // 0x58 method_ids_size, off
        buffer.putInt(classDefs); buffer.putInt(0)         // 0x60 class_defs_size, off
        buffer.putInt(0); buffer.putInt(0)                 // 0x68 data_size, data_off

        return buffer.array()
    }

    private fun writeDex(name: String, bytes: ByteArray): String =
        File(root, name).apply { writeBytes(bytes) }.absolutePath

    @Test
    fun `解析合法 DEX 头部各字段`() {
        val path = writeDex("classes.dex", buildDexBytes())
        val result = DexParser.parseHeader(path)

        assertTrue(result is DexParseResult.Success)
        val header = (result as DexParseResult.Success).header
        assertEquals("035", header.version)
        assertEquals("dex\n035\u0000", header.magic)
        assertEquals(0x1000L, header.fileSize)
        assertEquals(0x70L, header.headerSize)
        assertEquals(100L, header.stringIdsSize)
        assertEquals(50L, header.typeIdsSize)
        assertEquals(30L, header.protoIdsSize)
        assertEquals(20L, header.fieldIdsSize)
        assertEquals(200L, header.methodIdsSize)
        assertEquals(10L, header.classDefsSize)
        assertTrue(header.isValid)
    }

    @Test
    fun `校验和与签名被正确解析`() {
        val header = DexParser.parseHeaderBytes(buildDexBytes())
        assertEquals(0x1A2B3C4DL, header.checksum)
        // 签名应为 40 个十六进制字符（20 字节）
        // 测试数据填充的是 1..20，因此十六进制为首尾分别是 01 与 14
        assertEquals(40, header.signature.length)
        assertTrue("实际：${header.signature}", header.signature.startsWith("01020304"))
        assertTrue("实际：${header.signature}", header.signature.endsWith("14"))
    }

    @Test
    fun `识别小端标记`() {
        val header = DexParser.parseHeaderBytes(buildDexBytes())
        assertEquals(0x12345678L, header.endianTag)
        assertTrue(header.toSummary().contains("小端"))
    }

    @Test
    fun `摘要包含全部关键指标`() {
        val result = DexParser.parseHeader(writeDex("a.dex", buildDexBytes())) as DexParseResult.Success
        val summary = result.summary
        assertTrue(summary.contains("DEX 版本"))
        assertTrue(summary.contains("035"))
        assertTrue(summary.contains("方法数量"))
        assertTrue(summary.contains("类数量"))
    }

    @Test
    fun `版本 039 被识别为受支持`() {
        val header = DexParser.parseHeaderBytes(buildDexBytes(version = "039"))
        assertTrue(DexParser.isSupportedVersion(header))
    }

    @Test
    fun `未知版本不被支持`() {
        val header = DexParser.parseHeaderBytes(buildDexBytes(version = "999"))
        assertFalse(DexParser.isSupportedVersion(header))
    }

    @Test
    fun `魔数非法时解析失败`() {
        val bytes = buildDexBytes()
        // 篡改魔数首字节
        bytes[0] = 'z'.code.toByte()
        val path = writeDex("invalid.dex", bytes)

        val result = DexParser.parseHeader(path)
        assertTrue(result is DexParseResult.Failure)
        assertTrue((result as DexParseResult.Failure).reason.contains("魔数"))
    }

    @Test
    fun `文件不存在返回失败`() {
        val result = DexParser.parseHeader(File(root, "ghost.dex").absolutePath)
        assertTrue(result is DexParseResult.Failure)
        assertTrue((result as DexParseResult.Failure).reason.contains("不存在"))
    }

    @Test
    fun `文件过小返回失败`() {
        val path = writeDex("tiny.dex", ByteArray(10))
        val result = DexParser.parseHeader(path)
        assertTrue(result is DexParseResult.Failure)
        assertTrue((result as DexParseResult.Failure).reason.contains("过小"))
    }

    @Test
    fun `目录路径返回失败`() {
        val dir = File(root, "adir").apply { mkdirs() }
        val result = DexParser.parseHeader(dir.absolutePath)
        assertTrue(result is DexParseResult.Failure)
    }

    @Test
    fun `大尺寸计数不会因符号位产生负数`() {
        // 0xFFFFFFFF 作为有符号 int 是 -1，解析后应还原为无符号值
        val bytes = buildDexBytes(stringIds = -1)
        val header = DexParser.parseHeaderBytes(bytes)
        assertEquals(0xFFFFFFFFL, header.stringIdsSize)
    }
}

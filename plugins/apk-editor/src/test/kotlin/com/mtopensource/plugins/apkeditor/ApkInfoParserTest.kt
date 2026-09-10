// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.plugins.apkeditor

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * APK 解析测试。
 *
 * 测试用「合成 APK」而非真实 APK：手工构造一个最小但结构合法的
 * 二进制 AndroidManifest.xml（AXML），可以精确控制每个字段，
 * 从而验证解析器的行为而不是依赖某个具体的 APK 文件。
 */
class ApkInfoParserTest {

    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("mt-apk-test").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    /**
     * 构造最小 AXML。
     *
     * 结构：文件头(8) + 字符串池 chunk + START_TAG chunk
     */
    private fun buildAxml(strings: List<String>): ByteArray {
        val stringPool = buildStringPool(strings)
        val startTag = buildStartTag(strings)

        val totalSize = 8 + stringPool.size + startTag.size
        val out = ByteArrayOutputStream()

        // 文件头
        out.write(le32(0x00080003))
        out.write(le32(totalSize))
        out.write(stringPool)
        out.write(startTag)
        return out.toByteArray()
    }

    /** 构造字符串池 chunk。 */
    private fun buildStringPool(strings: List<String>): ByteArray {
        val encoded = strings.map { encodeUtf16(it) }
        val headerSize = 28
        val offsetsSize = strings.size * 4
        val dataStart = headerSize + offsetsSize

        // 按 AXML 规范，字符串偏移量是相对 strings_start 的，而不是相对 chunk 起始。
        // 这里 offsets 从 0 开始累加，与真实 AXML 保持一致。
        var cursor = 0
        val offsets = encoded.map { bytes ->
            val current = cursor
            cursor += bytes.size
            current
        }

        // chunk 总长度 = 头部 + 偏移表 + 字符串数据
        val chunkSize = dataStart + cursor
        val out = ByteArrayOutputStream()

        out.write(le16(0x0001))          // type = STRING_POOL
        out.write(le16(headerSize))
        out.write(le32(chunkSize))       // chunk size
        out.write(le32(strings.size))    // string count
        out.write(le32(0))               // style count
        out.write(le32(0))               // flags（UTF-16）
        out.write(le32(dataStart))       // strings start
        out.write(le32(0))               // styles start

        offsets.forEach { out.write(le32(it)) }
        encoded.forEach { out.write(it) }

        return out.toByteArray()
    }

    /** 构造 START_TAG chunk，包含 package / versionName 等属性。 */
    private fun buildStartTag(allStrings: List<String>): ByteArray {
        // 属性名与值都必须在字符串池中，这里按顺序建立索引映射
        val attributes = listOf(
            Triple("package", "com.example.demo", TYPE_STRING),
            Triple("versionName", "1.2.3", TYPE_STRING),
            Triple("versionCode", "42", TYPE_INT_DEC),
            Triple("minSdkVersion", "26", TYPE_INT_DEC),
            Triple("targetSdkVersion", "35", TYPE_INT_DEC),
        )

        val headerSize = 36
        val attributeSize = 20
        val chunkSize = headerSize + attributes.size * attributeSize

        val out = ByteArrayOutputStream()
        out.write(le16(0x0102))                       // type = START_TAG
        out.write(le16(headerSize))
        out.write(le32(chunkSize))
        out.write(le32(1))                            // line number
        out.write(le32(0xFFFFFFFF.toInt()))           // comment
        out.write(le32(0xFFFFFFFF.toInt()))           // ns
        out.write(le32(indexOf(allStrings, "manifest"))) // name
        out.write(le16(0x0014))                       // attribute start
        out.write(le16(attributeSize))                // attribute size
        out.write(le16(attributes.size))              // attribute count
        out.write(le16(0))                            // id index
        out.write(le16(0))                            // class index
        out.write(le16(0))                            // style index

        attributes.forEach { (name, value, type) ->
            // INT 类型没有 raw value，用 0xFFFFFFFF 表示「无」
            val rawValueIndex = if (type == TYPE_STRING) indexOf(allStrings, value) else -1
            out.write(le32(0xFFFFFFFF.toInt()))         // ns
            out.write(le32(indexOf(allStrings, name)))  // name
            out.write(le32(rawValueIndex))              // raw value
            out.write(le16(8))                          // value size
            out.write(0)                                // res0
            out.write(type)                             // data type
            // data 字段：字符串存字符串池下标，整数直接存数值本身
            val data = if (type == TYPE_STRING) indexOf(allStrings, value) else value.toInt()
            out.write(le32(data))
        }

        return out.toByteArray()
    }

    /** 编码 UTF-16 字符串（1 字节长度前缀）。 */
    private fun encodeUtf16(value: String): ByteArray {
        val out = ByteArrayOutputStream()
        val chars = value.length
        if (chars > 0x7F) {
            out.write((chars shr 8) or 0x80)
        }
        out.write(chars and 0xFF)
        value.forEach { ch ->
            out.write(ch.code and 0xFF)
            out.write((ch.code shr 8) and 0xFF)
        }
        return out.toByteArray()
    }

    private fun indexOf(strings: List<String>, value: String): Int =
        strings.indexOf(value).let { if (it >= 0) it else 0 }

    private fun le16(v: Int): ByteArray = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort()).array()

    private fun le32(v: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()

    /** 把合成内容打包为 APK。 */
    private fun buildApk(
        name: String = "app.apk",
        withV1: Boolean = true,
        strings: List<String> = listOf(
            "manifest", "package", "com.example.demo", "versionName", "1.2.3",
            "versionCode", "42", "minSdkVersion", "26", "targetSdkVersion", "35",
        ),
    ): String {
        val apk = File(root, name)
        ZipOutputStream(apk.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write(buildAxml(strings))
            zip.closeEntry()

            if (withV1) {
                zip.putNextEntry(ZipEntry("META-INF/CERT.RSA"))
                zip.write(ByteArray(32))
                zip.closeEntry()
            }

            zip.putNextEntry(ZipEntry("classes.dex"))
            zip.write(ByteArray(16) { 0x64 })
            zip.closeEntry()
        }
        return apk.absolutePath
    }

    @Test
    fun `解析合成 APK 的各项元信息`() {
        val path = buildApk()
        val result = ApkInfoParser.parse(path)

        assertTrue("解析应成功，实际：$result", result is ApkParseResult.Success)
        val info = (result as ApkParseResult.Success).info

        assertEquals("com.example.demo", info.packageName)
        assertEquals("1.2.3", info.versionName)
        assertEquals(42L, info.versionCode)
        assertEquals(26, info.minSdkVersion)
        assertEquals(35, info.targetSdkVersion)
        assertEquals(3, info.entryCount)
        assertTrue(info.hasV1Signature)
    }

    @Test
    fun `无 v1 签名时标记为 false`() {
        val path = buildApk(name = "unsigned.apk", withV1 = false)
        val info = (ApkInfoParser.parse(path) as ApkParseResult.Success).info
        // 合成文件不含真实 APK Signing Block
        assertEquals(false, info.hasV1Signature)
        assertEquals(false, info.hasV2Signature)
    }

    @Test
    fun `摘要文本包含关键字段`() {
        val info = (ApkInfoParser.parse(buildApk()) as ApkParseResult.Success).info
        val summary = info.toSummary()

        assertTrue(summary.contains("com.example.demo"))
        assertTrue(summary.contains("1.2.3"))
        assertTrue(summary.contains("最低 SDK"))
        assertTrue(summary.contains("V1 签名"))
    }

    @Test
    fun `文件不存在返回失败`() {
        val result = ApkInfoParser.parse(File(root, "ghost.apk").absolutePath)
        assertTrue(result is ApkParseResult.Failure)
        assertTrue((result as ApkParseResult.Failure).reason.contains("不存在"))
    }

    @Test
    fun `缺少 AndroidManifest 的压缩包返回失败`() {
        val apk = File(root, "nomanifest.apk")
        ZipOutputStream(apk.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("classes.dex"))
            zip.write(ByteArray(8))
            zip.closeEntry()
        }

        val result = ApkInfoParser.parse(apk.absolutePath)
        assertTrue(result is ApkParseResult.Failure)
        assertTrue((result as ApkParseResult.Failure).reason.contains("AndroidManifest"))
    }

    @Test
    fun `非压缩包文件返回失败`() {
        val bogus = File(root, "bogus.apk").apply { writeText("这不是一个 APK") }
        val result = ApkInfoParser.parse(bogus.absolutePath)
        assertTrue(result is ApkParseResult.Failure)
    }

    @Test
    fun `目录路径返回失败`() {
        val dir = File(root, "dir.apk").apply { mkdirs() }
        val result = ApkInfoParser.parse(dir.absolutePath)
        assertTrue(result is ApkParseResult.Failure)
    }

    @Test
    fun `文件大小被正确填充`() {
        val path = buildApk()
        val info = (ApkInfoParser.parse(path) as ApkParseResult.Success).info
        assertEquals(File(path).length(), info.fileSize)
        assertTrue(info.fileSize > 0)
    }

    private companion object {
        const val TYPE_STRING = 0x03
        const val TYPE_INT_DEC = 0x10
    }
}

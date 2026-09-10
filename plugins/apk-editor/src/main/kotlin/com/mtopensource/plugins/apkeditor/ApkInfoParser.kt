// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.plugins.apkeditor

import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.InputStream

/**
 * APK 包信息。
 */
data class ApkInfo(
    /** 包名。 */
    val packageName: String,
    /** 版本名（versionName）。 */
    val versionName: String,
    /** 版本号（versionCode）。 */
    val versionCode: Long,
    /** 最低 SDK 版本。 */
    val minSdkVersion: Int,
    /** 目标 SDK 版本。 */
    val targetSdkVersion: Int,
    /** 应用显示名称（可能为空，因资源未解析）。 */
    val label: String,
    /** APK 文件大小。 */
    val fileSize: Long,
    /** 是否包含 v1 (JAR) 签名。 */
    val hasV1Signature: Boolean,
    /** 是否包含 v2/v3 (APK Signing Block) 签名。 */
    val hasV2Signature: Boolean,
    /** 压缩包内条目数量。 */
    val entryCount: Int,
) {

    /** 面向用户的摘要文本。 */
    fun toSummary(): String = buildString {
        appendLine("包名          : $packageName")
        appendLine("版本名        : ${versionName.ifEmpty { "-" }}")
        appendLine("版本号        : $versionCode")
        appendLine("最低 SDK      : $minSdkVersion")
        appendLine("目标 SDK      : $targetSdkVersion")
        appendLine("应用名称      : ${label.ifEmpty { "(需解析资源)" }}")
        appendLine("文件大小      : ${"%.2f".format(fileSize / 1024.0 / 1024.0)} MB")
        appendLine("压缩包条目    : $entryCount")
        appendLine("--------------------------------")
        appendLine("V1 签名 (JAR) : ${if (hasV1Signature) "是" else "否"}")
        appendLine("V2 签名 (块)  : ${if (hasV2Signature) "是" else "否"}")
    }
}

/**
 * APK 解析结果。
 */
sealed class ApkParseResult {
    data class Success(val info: ApkInfo) : ApkParseResult()

    data class Failure(val reason: String) : ApkParseResult()
}

/**
 * APK 元信息解析器。
 *
 * 直接解析二进制 AndroidManifest.xml（AXML 格式），不依赖 Android 的
 * `PackageManager`，因此：
 * 1. 可在任意平台（含单元测试）解析任意 APK
 * 2. 可解析未安装的 APK
 *
 * AXML 结构参考：
 * - 头部：magic 0x00080003 + 文件大小
 * - 之后为若干 chunk，这里只需处理 STRING_POOL(0x0001) 与 START_TAG(0x0102)
 */
object ApkInfoParser {

    /** AXML magic。 */
    private const val AXML_MAGIC = 0x00080003

    private const val CHUNK_STRING_POOL = 0x0001
    private const val CHUNK_START_TAG = 0x0102

    /**
     * 解析 APK 元信息。
     */
    fun parse(apkPath: String): ApkParseResult {
        val file = java.io.File(apkPath)
        if (!file.exists()) return ApkParseResult.Failure("文件不存在：$apkPath")
        if (!file.isFile) return ApkParseResult.Failure("不是文件：$apkPath")

        return try {
            ZipFile.builder().setFile(file).get().use { zip ->
                val manifestEntry = zip.getEntry("AndroidManifest.xml")
                    ?: return ApkParseResult.Failure("APK 中缺少 AndroidManifest.xml")

                val manifestBytes = zip.getInputStream(manifestEntry).use { it.readBytes() }
                val attributes = parseManifestAttributes(manifestBytes)

                val hasV1 = zip.getEntry("META-INF/CERT.RSA") != null ||
                    zip.getEntry("META-INF/CERT.SF") != null
                val hasV2 = hasApkSigningBlock(file)

                ApkParseResult.Success(
                    ApkInfo(
                        packageName = attributes["package"] ?: "",
                        versionName = attributes["versionName"] ?: "",
                        versionCode = attributes["versionCode"]?.toLongOrNull() ?: 0L,
                        minSdkVersion = attributes["minSdkVersion"]?.toIntOrNull() ?: 0,
                        targetSdkVersion = attributes["targetSdkVersion"]?.toIntOrNull() ?: 0,
                        label = attributes["label"] ?: "",
                        fileSize = file.length(),
                        hasV1Signature = hasV1,
                        hasV2Signature = hasV2,
                        entryCount = countEntries(zip),
                    ),
                )
            }
        } catch (e: Exception) {
            ApkParseResult.Failure("解析 APK 失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * 统计压缩包内条目数量。
     *
     * commons-compress 的 `entries()` 返回 `Enumeration`，
     * 这里用显式迭代避免 Kotlin 侧的类型推断歧义。
     */
    private fun countEntries(zip: ZipFile): Int {
        var count = 0
        val entries = zip.entries
        while (entries.hasMoreElements()) {
            entries.nextElement()
            count++
        }
        return count
    }

    /**
     * 从二进制 AndroidManifest.xml 中提取属性键值。
     *
     * 只做「扫描字符串池 + 匹配 START_TAG 属性」的最小实现，
     * 目的是提取 package / versionName 等关键字段。
     */
    private fun parseManifestAttributes(bytes: ByteArray): Map<String, String> {
        if (bytes.size < 8) return emptyMap()

        val magic = readInt(bytes, 0)
        if (magic != AXML_MAGIC) return emptyMap()

        val stringPool = extractStringPool(bytes) ?: return emptyMap()

        // 依次读取 chunk，寻找 START_TAG 并解析其属性。
        // 注意：AXML 的 chunk 头是 type(u16) + headerSize(u16) + chunkSize(u32)，
        // type 必须按 2 字节读取，否则与真实 DEX/APK 文件不兼容。
        var offset = 8
        while (offset + 8 <= bytes.size) {
            val type = readShort(bytes, offset)
            val chunkSize = readInt(bytes, offset + 4)
            if (chunkSize <= 0 || offset + chunkSize > bytes.size) break

            if (type == CHUNK_START_TAG) {
                return parseStartTagAttributes(bytes, offset, stringPool)
            }
            offset += chunkSize
        }
        return emptyMap()
    }

    /** 提取字符串池（前 [limit] 条，避免恶意文件占用过多内存）。 */
    private fun extractStringPool(bytes: ByteArray, limit: Int = 512): List<String>? {
        var offset = 8
        while (offset + 8 <= bytes.size) {
            // 与 chunk 遍历保持一致，chunk type 为 u16
            val type = readShort(bytes, offset)
            val chunkSize = readInt(bytes, offset + 4)
            if (chunkSize <= 0 || offset + chunkSize > bytes.size) return null

            if (type == CHUNK_STRING_POOL) {
                val stringCount = readInt(bytes, offset + 8)
                // strings_start 位于 chunk 头 0x14 处，字符串偏移量相对它计算
                val stringsStart = readInt(bytes, offset + 20)
                val offsetsStart = offset + 28
                val strings = ArrayList<String>(minOf(stringCount, limit))

                for (i in 0 until minOf(stringCount, limit)) {
                    val stringOffset = readInt(bytes, offsetsStart + i * 4)
                    // 部分打包器会写出 0xFFFFFFFF 表示空字符串
                    if (stringOffset < 0) {
                        strings += ""
                        continue
                    }
                    val absolute = offset + stringsStart + stringOffset
                    if (absolute + 2 > bytes.size) break
                    strings += readUtf16String(bytes, absolute)
                }
                return strings
            }
            offset += chunkSize
        }
        return null
    }

    /** 解析 START_TAG 的属性列表。 */
    private fun parseStartTagAttributes(bytes: ByteArray, chunkStart: Int, stringPool: List<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        // START_TAG 布局：chunkHeader(8) + lineNumber(4) + comment(4) + ns(4) + name(4) + attributeStart(2)
        //                + attributeSize(2) + attributeCount(2) + idIndex(2) + classIndex(2) + styleIndex(2)
        val attributeCount = readShort(bytes, chunkStart + 28)
        var attributeOffset = chunkStart + 36

        for (i in 0 until attributeCount) {
            if (attributeOffset + 20 > bytes.size) break
            val nameIndex = readInt(bytes, attributeOffset + 4)
            val rawValueIndex = readInt(bytes, attributeOffset + 8)
            val typedValueType = bytes[attributeOffset + 15].toInt() and 0xFF
            val typedValueData = readInt(bytes, attributeOffset + 16)

            val name = stringPool.getOrNull(nameIndex) ?: continue
            val value = when {
                rawValueIndex >= 0 -> stringPool.getOrNull(rawValueIndex)
                typedValueType == TYPE_STRING -> stringPool.getOrNull(typedValueData)
                typedValueType == TYPE_INT_DEC -> typedValueData.toString()
                typedValueType == TYPE_INT_BOOLEAN -> (typedValueData != 0).toString()
                else -> null
            }
            if (value != null) result[name] = value

            attributeOffset += 20
        }
        return result
    }

    /**
     * 判断 APK 是否包含 v2/v3 签名。
     *
     * 通过查找 APK Signing Block 的魔数 `APK Sig Block 42` 实现。
     */
    private fun hasApkSigningBlock(file: java.io.File): Boolean = runCatching {
        val magic = "APK Sig Block 42".toByteArray(Charsets.US_ASCII)
        file.inputStream().use { stream ->
            // 签名块位于中央目录之前，这里在文件尾部 128KB 内查找即可
            val searchSize = minOf(file.length(), 128L * 1024).toInt()
            val tail = ByteArray(searchSize)
            stream.skip(file.length() - searchSize)
            var read = 0
            while (read < searchSize) {
                val n = stream.read(tail, read, searchSize - read)
                if (n < 0) break
                read += n
            }
            indexOfTail(tail, magic) >= 0
        }
    }.getOrDefault(false)

    private fun indexOfTail(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    /** 读取以 UTF-16 长度前缀存储的字符串。 */
    private fun readUtf16String(bytes: ByteArray, offset: Int): String {
        var cursor = offset
        var length = bytes[cursor].toInt() and 0xFF
        cursor++
        if (length and 0x80 != 0) {
            // 长字符串：两字节长度
            length = (length and 0x7F shl 8) or (bytes[cursor].toInt() and 0xFF)
            cursor++
        }
        val builder = StringBuilder(length)
        for (i in 0 until length) {
            if (cursor + 1 >= bytes.size) break
            val ch = ((bytes[cursor + 1].toInt() and 0xFF) shl 8) or (bytes[cursor].toInt() and 0xFF)
            builder.append(ch.toChar())
            cursor += 2
        }
        return builder.toString()
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readShort(bytes: ByteArray, offset: Int): Int {
        if (offset + 2 > bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private const val TYPE_STRING = 0x03
    private const val TYPE_INT_DEC = 0x10
    private const val TYPE_INT_BOOLEAN = 0x12

    /** 供测试与外部使用的流式读取入口。 */
    fun readAll(input: InputStream): ByteArray = input.use { it.readBytes() }
}

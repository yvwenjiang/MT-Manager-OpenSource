// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.plugins.dexeditor

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * DEX 解析结果。
 */
sealed class DexParseResult {

    data class Success(
        /** 面向用户的结构摘要文本。 */
        val summary: String,
        val header: DexHeader,
    ) : DexParseResult()

    data class Failure(val reason: String) : DexParseResult()
}

/**
 * DEX 文件头信息。
 *
 * 字段定义参考 Android 官方 DEX 格式：
 * https://source.android.com/docs/core/runtime/dex-format
 */
data class DexHeader(
    /** 魔数，标准值为 `dex\n035\0` ~ `dex\n039\0`。 */
    val magic: String,
    /** 校验和（Adler-32）。 */
    val checksum: Long,
    /** SHA-1 签名（20 字节）。 */
    val signature: String,
    /** 文件总大小。 */
    val fileSize: Long,
    /** 头部大小，固定 0x70。 */
    val headerSize: Long,
    /** 大小端标记。 */
    val endianTag: Long,
    /** 字符串数量。 */
    val stringIdsSize: Long,
    /** 类型数量。 */
    val typeIdsSize: Long,
    /** 方法原型数量。 */
    val protoIdsSize: Long,
    /** 字段数量。 */
    val fieldIdsSize: Long,
    /** 方法数量。 */
    val methodIdsSize: Long,
    /** 类数量。 */
    val classDefsSize: Long,
) {

    /** DEX 版本号，例如 `035`。 */
    val version: String get() = magic.substringAfter("dex\n").trimEnd('\u0000')

    /** 是否为合法的 DEX（魔数以 dex 开头且版本号已知）。 */
    val isValid: Boolean get() = magic.startsWith("dex\n")

    /** 面向用户的结构摘要。 */
    fun toSummary(): String = buildString {
        appendLine("DEX 版本      : ${version.ifEmpty { "未知" }}")
        appendLine("文件大小      : ${fileSize} 字节")
        appendLine("校验和 (Adler): 0x${checksum.toString(16).uppercase()}")
        appendLine("SHA-1 签名    : $signature")
        appendLine("大小端        : ${if (endianTag == 0x12345678L) "小端 (Little Endian)" else "大端 (Big Endian)"}")
        appendLine("--------------------------------")
        appendLine("字符串数量    : $stringIdsSize")
        appendLine("类型数量      : $typeIdsSize")
        appendLine("方法原型数量  : $protoIdsSize")
        appendLine("字段数量      : $fieldIdsSize")
        appendLine("方法数量      : $methodIdsSize")
        appendLine("类数量        : $classDefsSize")
    }
}

/**
 * 轻量 DEX 解析器。
 *
 * 当前实现只解析文件头（前 0x70 字节），原因：
 * 1. 文件头已能提供「这是否是有效 DEX」「规模多大」的关键信息
 * 2. 完整的 class_defs / method_ids 解析需要数百行代码，属于后续迭代内容
 *
 * 这样的分层让插件在本期即可交付可用功能，同时把扩展点清晰留在
 * [parseHeader] 之外（后续可增加 `parseClasses` / `parseMethods`）。
 */
object DexParser {

    /** DEX 头部固定长度。 */
    private const val HEADER_SIZE = 0x70

    /** 小端标记常量。 */
    private const val ENDIAN_CONSTANT = 0x12345678L

    /**
     * 解析 DEX 文件头。
     */
    fun parseHeader(path: String): DexParseResult {
        val file = File(path)
        if (!file.exists()) return DexParseResult.Failure("文件不存在：$path")
        if (!file.isFile) return DexParseResult.Failure("不是文件：$path")
        if (file.length() < HEADER_SIZE) {
            return DexParseResult.Failure("文件过小（${file.length()} 字节），不可能是合法 DEX")
        }

        return try {
            val bytes = ByteArray(HEADER_SIZE)
            file.inputStream().use { input ->
                var read = 0
                while (read < HEADER_SIZE) {
                    val n = input.read(bytes, read, HEADER_SIZE - read)
                    if (n < 0) break
                    read += n
                }
                if (read < HEADER_SIZE) return DexParseResult.Failure("读取头部失败，仅读到 $read 字节")
            }

            val header = parseHeaderBytes(bytes)
            if (!header.isValid) {
                return DexParseResult.Failure("魔数不匹配（${header.magic}），不是合法 DEX 文件")
            }
            DexParseResult.Success(summary = header.toSummary(), header = header)
        } catch (e: Exception) {
            DexParseResult.Failure("解析异常：${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * 从字节数组解析头部，独立出来以便单元测试。
     */
    fun parseHeaderBytes(bytes: ByteArray): DexHeader {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // 魔数：8 字节，含末尾的版本号与 \0
        val magicBytes = ByteArray(8)
        buffer.get(magicBytes)
        val magic = magicBytes.toString(Charsets.ISO_8859_1)

        val checksum = buffer.int.toLong() and 0xFFFFFFFFL

        // SHA-1：20 字节
        val signatureBytes = ByteArray(20)
        buffer.get(signatureBytes)
        val signature = signatureBytes.joinToString("") { "%02x".format(it) }

        val fileSize = buffer.int.toLong() and 0xFFFFFFFFL
        val headerSize = buffer.int.toLong() and 0xFFFFFFFFL
        val endianTag = buffer.int.toLong() and 0xFFFFFFFFL

        // 头部字段顺序严格遵循官方规范：
        // link_size(4) link_off(4) map_off(4)
        //   string_ids_size(4) string_ids_off(4)
        //   type_ids_size(4)   type_ids_off(4)
        //   proto_ids_size(4)  proto_ids_off(4)
        //   field_ids_size(4)  field_ids_off(4)
        //   method_ids_size(4) method_ids_off(4)
        //   class_defs_size(4) class_defs_off(4)
        //   data_size(4)       data_off(4)
        buffer.int; buffer.int // link_size, link_off
        buffer.int            // map_off

        val stringIdsSize = readSizePair(buffer)
        val typeIdsSize = readSizePair(buffer)
        val protoIdsSize = readSizePair(buffer)
        val fieldIdsSize = readSizePair(buffer)
        val methodIdsSize = readSizePair(buffer)
        val classDefsSize = readSizePair(buffer)

        // data_size / data_off 暂不需要，但需保证读取完整个头部
        buffer.int; buffer.int

        return DexHeader(
            magic = magic,
            checksum = checksum,
            signature = signature,
            fileSize = fileSize,
            headerSize = headerSize,
            endianTag = endianTag,
            stringIdsSize = stringIdsSize,
            typeIdsSize = typeIdsSize,
            protoIdsSize = protoIdsSize,
            fieldIdsSize = fieldIdsSize,
            methodIdsSize = methodIdsSize,
            classDefsSize = classDefsSize,
        )
    }

    /** 判断魔数是否对应已知的 DEX 版本。 */
    fun isSupportedVersion(header: DexHeader): Boolean = header.version in SUPPORTED_VERSIONS

    private val SUPPORTED_VERSIONS = setOf("035", "036", "037", "038", "039", "040")

    /**
     * 读取 `xxx_ids_size` 并跳过紧随其后的 `xxx_ids_off`。
     *
     * DEX 头部中每张索引表都是 (size, offset) 成对出现，
     * 这里统一封装，避免逐个手写 `buffer.int` 造成错位。
     */
    private fun readSizePair(buffer: ByteBuffer): Long {
        val size = buffer.int.toLong() and 0xFFFFFFFFL
        buffer.int // 对应的 offset 字段，本期不解析索引表
        return size
    }

    /** 头部各字段的字节偏移，供后续扩展读取索引表时使用。 */
    internal object Offsets {
        const val MAGIC = 0x00
        const val CHECKSUM = 0x08
        const val SIGNATURE = 0x0C
        const val FILE_SIZE = 0x20
        const val HEADER_SIZE = 0x24
        const val ENDIAN_TAG = 0x28
        const val LINK_SIZE = 0x2C
        const val LINK_OFF = 0x30
        const val MAP_OFF = 0x34
        const val STRING_IDS_SIZE = 0x38
        const val STRING_IDS_OFF = 0x3C
        const val TYPE_IDS_SIZE = 0x40
        const val TYPE_IDS_OFF = 0x44
        const val PROTO_IDS_SIZE = 0x48
        const val PROTO_IDS_OFF = 0x4C
        const val FIELD_IDS_SIZE = 0x50
        const val FIELD_IDS_OFF = 0x54
        const val METHOD_IDS_SIZE = 0x58
        const val METHOD_IDS_OFF = 0x5C
        const val CLASS_DEFS_SIZE = 0x60
        const val CLASS_DEFS_OFF = 0x64
        const val DATA_SIZE = 0x68
        const val DATA_OFF = 0x6C
    }
}

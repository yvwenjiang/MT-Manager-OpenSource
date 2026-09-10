// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.common.utils

import java.io.File

/**
 * 路径处理工具集合。
 *
 * 所有方法均为纯字符串/`java.io.File` 运算，不触及 Android API，
 * 便于在 JVM 单元测试中覆盖各种边界情况。
 */
object PathUtils {

    private const val SEPARATOR = "/"

    /**
     * 规范化路径：统一分隔符、折叠 `.` 与 `..`、去除多余斜杠。
     *
     * 例如 `"/sdcard//a/./b/../c/"` -> `/sdcard/a/c`
     */
    fun normalize(path: String): String {
        if (path.isEmpty()) return SEPARATOR
        if (path == SEPARATOR) return SEPARATOR

        val isAbsolute = path.startsWith(SEPARATOR)
        val segments = path.split(SEPARATOR)
        val stack = ArrayDeque<String>()

        for (segment in segments) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (stack.isNotEmpty() && stack.last() != "..") {
                    stack.removeLast()
                } else if (!isAbsolute) {
                    stack.addLast("..")
                }
                else -> stack.addLast(segment)
            }
        }

        val joined = stack.joinToString(SEPARATOR)
        return when {
            isAbsolute -> SEPARATOR + joined
            joined.isEmpty() -> "."
            else -> joined
        }.let { if (it.length > 1 && it.endsWith(SEPARATOR)) it.dropLast(1) else it }
    }

    /** 取路径的父目录，根目录返回 null。 */
    fun parent(path: String): String? {
        val normalized = normalize(path)
        if (normalized == SEPARATOR) return null
        val index = normalized.lastIndexOf(SEPARATOR)
        return when {
            index < 0 -> null
            index == 0 -> SEPARATOR
            else -> normalized.substring(0, index)
        }
    }

    /** 取路径最后一段作为文件名。 */
    fun fileName(path: String): String {
        val normalized = normalize(path)
        if (normalized == SEPARATOR) return SEPARATOR
        return normalized.substringAfterLast(SEPARATOR)
    }

    /** 取扩展名（小写，不含点），无扩展名返回空串。 */
    fun extension(path: String): String {
        val name = fileName(path)
        val dot = name.lastIndexOf('.')
        // 以点开头的隐藏文件（如 .gitignore）不算扩展名
        if (dot <= 0 || dot == name.length - 1) return ""
        return name.substring(dot + 1).lowercase()
    }

    /** 取不含扩展名的文件名。 */
    fun baseName(path: String): String {
        val name = fileName(path)
        val dot = name.lastIndexOf('.')
        return if (dot <= 0) name else name.substring(0, dot)
    }

    /** 拼接路径，自动处理分隔符。 */
    fun join(base: String, vararg parts: String): String {
        val builder = StringBuilder(normalize(base))
        for (part in parts) {
            if (part.isEmpty()) continue
            if (builder.isNotEmpty() && !builder.endsWith(SEPARATOR)) builder.append(SEPARATOR)
            builder.append(part.trim(SEPARATOR.toCharArray()[0]))
        }
        return normalize(builder.toString())
    }

    /** 判断 [child] 是否位于 [parent] 目录之下（含自身）。 */
    fun isUnder(parent: String, child: String): Boolean {
        val p = normalize(parent)
        val c = normalize(child)
        if (p == c) return true
        val prefix = if (p.endsWith(SEPARATOR)) p else p + SEPARATOR
        return c.startsWith(prefix)
    }

    /** 生成不冲突的目标路径，例如 `a.txt` 已存在则返回 `a (1).txt`。 */
    fun resolveConflict(targetPath: String, exists: (String) -> Boolean): String {
        if (!exists(targetPath)) return targetPath

        val dir = parent(targetPath) ?: SEPARATOR
        val base = baseName(targetPath)
        val ext = extension(targetPath)
        val suffix = if (ext.isEmpty()) "" else ".$ext"

        var index = 1
        while (index < MAX_CONFLICT_RETRY) {
            val candidate = join(dir, "$base ($index)$suffix")
            if (!exists(candidate)) return candidate
            index++
        }
        return join(dir, "$base (${System.currentTimeMillis()})$suffix")
    }

    private const val MAX_CONFLICT_RETRY = 10_000

    /** 使用 java.io.File 的简便重载。 */
    fun resolveConflict(target: File): File = File(resolveConflict(target.absolutePath) { it2 ->
        File(it2).exists()
    }.let { path -> path })

    /** 计算相对路径，例如 from=/a/b to=/a/b/c/d -> c/d。 */
    fun relativePath(from: String, to: String): String {
        val fromParts = normalize(from).split(SEPARATOR).filter { it.isNotEmpty() }
        val toParts = normalize(to).split(SEPARATOR).filter { it.isNotEmpty() }

        var common = 0
        while (common < fromParts.size && common < toParts.size && fromParts[common] == toParts[common]) {
            common++
        }

        val up = List(fromParts.size - common) { ".." }
        val down = toParts.drop(common)
        return (up + down).joinToString(SEPARATOR).ifEmpty { "." }
    }
}

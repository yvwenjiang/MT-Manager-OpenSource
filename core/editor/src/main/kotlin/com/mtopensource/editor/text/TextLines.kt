// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.editor.text

/**
 * 行号索引工具。
 *
 * 文本编辑时需要频繁做「偏移量 <-> 行列号」转换，
 * 若每次都扫描全文会带来 O(n) 开销；[LineIndex] 通过缓存换行位置把
 * 单次转换降到 O(log n)。
 */
object TextLines {

    /** 统计行数（以 `\n` 为准，末尾无换行时最后一行也计入）。 */
    fun countLines(text: String): Int {
        if (text.isEmpty()) return 1
        var lines = 1
        for (ch in text) {
            if (ch == '\n') lines++
        }
        return lines
    }

    /** 将偏移量转换为基于 1 的行号与列号。 */
    fun offsetToPosition(text: String, offset: Int): Pair<Int, Int> {
        val safeOffset = offset.coerceIn(0, text.length)
        var line = 1
        var lineStart = 0
        for (i in 0 until safeOffset) {
            if (text[i] == '\n') {
                line++
                lineStart = i + 1
            }
        }
        return line to (safeOffset - lineStart + 1)
    }

    /** 将基于 1 的行列号转换为偏移量。 */
    fun positionToOffset(text: String, line: Int, column: Int): Int {
        val targetLine = line.coerceAtLeast(1)
        var currentLine = 1
        var index = 0
        while (currentLine < targetLine && index < text.length) {
            if (text[index] == '\n') currentLine++
            index++
        }
        val lineEnd = text.indexOf('\n', index).let { if (it < 0) text.length else it }
        return (index + column - 1).coerceIn(index, lineEnd)
    }

    /** 取出指定行（基于 1）的文本，不含换行符。 */
    fun lineAt(text: String, line: Int): String {
        val start = positionToOffset(text, line, 1)
        val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
        return text.substring(start, end)
    }

    /** 文本是否以 `\r\n` 作为换行符。 */
    fun usesCrLf(text: String): Boolean = text.contains("\r\n")

    /** 统一换行符为 `\n`，用于内部处理。 */
    fun normalizeLineEndings(text: String): String = text.replace("\r\n", "\n").replace('\r', '\n')

    /** 计算缩进单位（空格数），制表符按 4 空格折算。 */
    fun indentWidth(line: String): Int {
        var width = 0
        for (ch in line) {
            when (ch) {
                ' ' -> width++
                '\t' -> width += TAB_SIZE
                else -> return width
            }
        }
        return width
    }

    /** 检测文本主导缩进风格：true 表示使用 Tab。 */
    fun prefersTabIndent(text: String): Boolean {
        var tabLines = 0
        var spaceLines = 0
        text.lineSequence().take(200).forEach { line ->
            when {
                line.startsWith('\t') -> tabLines++
                line.startsWith("  ") -> spaceLines++
            }
        }
        return tabLines > spaceLines
    }

    const val TAB_SIZE = 4
}

/**
 * 换行位置索引，支持 O(log n) 的行列转换。
 */
class LineIndex(initialText: String) {

    private var offsets: IntArray = buildOffsets(initialText)

    /** 当前文本行数。 */
    val lineCount: Int get() = offsets.size

    /** 文本发生变化后重建索引。 */
    fun rebuild(text: String) {
        offsets = buildOffsets(text)
    }

    /** 偏移量 -> 基于 0 的行下标。 */
    fun lineOf(offset: Int): Int {
        var low = 0
        var high = offsets.size - 1
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (offsets[mid] <= offset) low = mid else high = mid - 1
        }
        return low
    }

    /** 指定行（基于 0）的起始偏移量。 */
    fun lineStart(line: Int): Int = offsets[line.coerceIn(0, offsets.size - 1)]

    /** 指定行（基于 0）的结束偏移量（不含换行符）。 */
    fun lineEnd(text: String, line: Int): Int {
        val next = if (line + 1 < offsets.size) offsets[line + 1] - 1 else text.length
        return next.coerceIn(0, text.length)
    }

    private fun buildOffsets(text: String): IntArray {
        val list = ArrayList<Int>(TextLines.countLines(text))
        list.add(0)
        for (i in text.indices) {
            if (text[i] == '\n') list.add(i + 1)
        }
        return list.toIntArray()
    }
}

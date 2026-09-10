// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.editor.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextLinesTest {

    private val sample = "line1\nline2\nline3"

    @Test
    fun `countLines 统计行数`() {
        assertEquals(3, TextLines.countLines(sample))
        assertEquals(1, TextLines.countLines(""))
        assertEquals(1, TextLines.countLines("single"))
        assertEquals(3, TextLines.countLines("a\nb\n"))
    }

    @Test
    fun `offsetToPosition 转换行列号`() {
        assertEquals(1 to 1, TextLines.offsetToPosition(sample, 0))
        assertEquals(1 to 5, TextLines.offsetToPosition(sample, 4))
        assertEquals(2 to 1, TextLines.offsetToPosition(sample, 6))
        // sample 长 17，第 3 行从偏移 12 开始，因此末尾位于第 6 列
        assertEquals(3 to 6, TextLines.offsetToPosition(sample, sample.length))
    }

    @Test
    fun `offsetToPosition 对越界偏移量做钳制`() {
        assertEquals(3 to 6, TextLines.offsetToPosition(sample, 9999))
        assertEquals(1 to 1, TextLines.offsetToPosition(sample, -5))
    }

    @Test
    fun `positionToOffset 转换偏移量`() {
        assertEquals(0, TextLines.positionToOffset(sample, 1, 1))
        assertEquals(6, TextLines.positionToOffset(sample, 2, 1))
        // 第 3 行从偏移 12 开始，第 5 列即偏移 16（'3' 所在位置）
        assertEquals(16, TextLines.positionToOffset(sample, 3, 5))
        // 第 6 列是行尾之后的位置，等于文本长度
        assertEquals(sample.length, TextLines.positionToOffset(sample, 3, 6))
    }

    @Test
    fun `positionToOffset 与 offsetToPosition 互为逆运算`() {
        for (offset in 0..sample.length) {
            val (line, column) = TextLines.offsetToPosition(sample, offset)
            assertEquals("offset=$offset", offset, TextLines.positionToOffset(sample, line, column))
        }
    }

    @Test
    fun `positionToOffset 不超过行长边界`() {
        // 第 1 行只有 5 个字符，列号 100 应被钳制到行尾
        val offset = TextLines.positionToOffset(sample, 1, 100)
        assertEquals(5, offset)
    }

    @Test
    fun `lineAt 返回指定行内容且不含换行符`() {
        assertEquals("line1", TextLines.lineAt(sample, 1))
        assertEquals("line3", TextLines.lineAt(sample, 3))
        assertEquals("line2", TextLines.lineAt(sample, 2))
    }

    @Test
    fun `normalizeLineEndings 统一换行符`() {
        assertEquals("a\nb", TextLines.normalizeLineEndings("a\r\nb"))
        assertEquals("a\nb", TextLines.normalizeLineEndings("a\rb"))
        assertTrue(TextLines.usesCrLf("a\r\nb"))
        assertFalse(TextLines.usesCrLf("a\nb"))
    }

    @Test
    fun `indentWidth 处理空格与制表符`() {
        assertEquals(4, TextLines.indentWidth("    code"))
        assertEquals(4, TextLines.indentWidth("\tcode"))
        assertEquals(0, TextLines.indentWidth("code"))
        assertEquals(6, TextLines.indentWidth("  \tcode"))
    }

    @Test
    fun `prefersTabIndent 判断主导缩进风格`() {
        assertTrue(TextLines.prefersTabIndent("\ta\n\tb\n\tc"))
        assertFalse(TextLines.prefersTabIndent("  a\n  b\n  c"))
    }

    @Test
    fun `LineIndex 支持日志复杂度行列查询`() {
        val text = "a\nbb\nccc\ndddd"
        val index = LineIndex(text)

        assertEquals(4, index.lineCount)
        assertEquals(0, index.lineOf(0))
        assertEquals(1, index.lineOf(2))
        assertEquals(3, index.lineOf(text.length - 1))
        assertEquals(0, index.lineStart(0))
        assertEquals(2, index.lineStart(1))
        assertEquals(1, index.lineEnd(text, 0))
    }

    @Test
    fun `LineIndex 重建后反映新文本`() {
        val index = LineIndex("a\nb")
        assertEquals(2, index.lineCount)
        index.rebuild("a\nb\nc\nd")
        assertEquals(4, index.lineCount)
    }
}

class TextRangeTest {

    @Test
    fun `findAll 定位所有匹配`() {
        // 忽略大小写时 "abcABCabc" 中共有 3 处匹配
        val ranges = TextRange.findAll("abcABCabc", "abc", ignoreCase = true)
        assertEquals(3, ranges.size)
        assertEquals(TextRange(0, 3), ranges[0])
        assertEquals(TextRange(3, 6), ranges[1])
        assertEquals(TextRange(6, 9), ranges[2])
    }

    @Test
    fun `findAll 区分大小写`() {
        val ranges = TextRange.findAll("abcABCabc", "abc", ignoreCase = false)
        assertEquals(2, ranges.size)
    }

    @Test
    fun `findAll 空关键字返回空`() {
        assertTrue(TextRange.findAll("abc", "").isEmpty())
    }

    @Test
    fun `contains 与 overlaps 语义正确`() {
        val range = TextRange(5, 10)
        assertTrue(range.contains(5))
        assertTrue(range.contains(9))
        assertFalse(range.contains(10))
        assertTrue(range.overlaps(TextRange(8, 12)))
        assertFalse(range.overlaps(TextRange(10, 12)))
    }

    @Test
    fun `区间按起点排序`() {
        val sorted = listOf(TextRange(5, 6), TextRange(1, 2), TextRange(3, 9)).sorted()
        assertEquals(listOf(TextRange(1, 2), TextRange(3, 9), TextRange(5, 6)), sorted)
    }
}

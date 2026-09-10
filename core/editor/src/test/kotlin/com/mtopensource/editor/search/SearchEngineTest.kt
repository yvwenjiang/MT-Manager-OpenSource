// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.editor.search

import com.mtopensource.editor.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {

    private val engine = SearchEngine()
    private val text = "fun main() {\n    println(\"hello\")\n    val funCount = 1\n}"

    @Test
    fun `普通文本搜索返回全部匹配`() {
        val matches = engine.findAll(text, "fun")
        assertEquals(2, matches.size)
    }

    @Test
    fun `忽略大小写可匹配不同形式`() {
        val matches = engine.findAll("Hello HELLO hello", "hello", SearchOptions(ignoreCase = true))
        assertEquals(3, matches.size)
    }

    @Test
    fun `区分大小写时只匹配一致形式`() {
        val matches = engine.findAll("Hello HELLO hello", "hello", SearchOptions(ignoreCase = false))
        assertEquals(1, matches.size)
    }

    @Test
    fun `全词匹配排除子串`() {
        val matches = engine.findAll(text, "fun", SearchOptions(wholeWord = true))
        // "funCount" 中的 fun 不应命中
        assertEquals(1, matches.size)
        assertEquals(0, matches.first().range.start)
    }

    @Test
    fun `搜索结果包含行号与整行文本`() {
        val matches = engine.findAll(text, "println")
        assertEquals(1, matches.size)
        assertEquals(2, matches.first().line)
        assertTrue(matches.first().lineText.contains("println"))
    }

    @Test
    fun `正则模式匹配数字序列`() {
        val matches = engine.findAll("a1 b22 c333", "\\d+", SearchOptions(useRegex = true))
        assertEquals(listOf("1", "22", "333"), matches.map { "a1 b22 c333".substring(it.range.start, it.range.end) })
    }

    @Test
    fun `非法正则返回空结果而不抛异常`() {
        val matches = engine.findAll("abc", "([unclosed", SearchOptions(useRegex = true))
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `正则零宽匹配被过滤`() {
        val matches = engine.findAll("abc", "(?=b)", SearchOptions(useRegex = true))
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `仅在选区内搜索`() {
        val selection = TextRange(0, 12)
        val matches = engine.findAll(text, "fun", SearchOptions(searchInSelection = true), selection)
        assertEquals(1, matches.size)
        assertEquals(0, matches.first().range.start)
    }

    @Test
    fun `findNext 从给定偏移向后查找并循环`() {
        val first = engine.findNext(text, "fun", fromOffset = 0)
        assertEquals(0, first!!.range.start)

        val second = engine.findNext(text, "fun", fromOffset = 5)
        assertTrue(second!!.range.start > 0)

        // 超出末尾后回到第一个
        val wrapped = engine.findNext(text, "fun", fromOffset = text.length)
        assertEquals(0, wrapped!!.range.start)
    }

    @Test
    fun `findPrevious 从给定偏移向前查找并循环`() {
        val last = engine.findPrevious(text, "fun", fromOffset = text.length)
        assertTrue(last!!.range.start > 0)

        // 在第一个匹配之前查找则回到最后一个
        val wrapped = engine.findPrevious(text, "fun", fromOffset = 0)
        assertTrue(wrapped!!.range.start > 0)
    }

    @Test
    fun `空查询返回空结果`() {
        assertTrue(engine.findAll(text, "").isEmpty())
        assertNull(engine.findNext(text, "", 0))
        assertNull(engine.findPrevious(text, "", 0))
    }

    @Test
    fun `countMatches 统计数量`() {
        assertEquals(2, engine.countMatches(text, "fun"))
    }

    @Test
    fun `多行搜索能定位到正确行`() {
        val matches = engine.findAll(text, "val")
        assertEquals(1, matches.size)
        assertEquals(3, matches.first().line)
    }
}

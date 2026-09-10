// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.editor.syntax

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LexerTest {

    private val kotlin = Lexer(SyntaxDefinition.forFileName("Main.kt"))

    @Test
    fun `识别 Kotlin 关键字与类型`() {
        val tokens = kotlin.tokenize("class Foo : Any")
        val types = tokens.filter { it.type == TokenType.KEYWORD }.map { it.type }
        assertTrue(types.isNotEmpty())
        assertTrue(tokens.any { it.type == TokenType.TYPE })
    }

    @Test
    fun `识别行注释到行尾`() {
        val text = "val a = 1 // 这是注释\nval b = 2"
        val comment = kotlin.tokenize(text).first { it.type == TokenType.COMMENT }
        assertEquals("// 这是注释", text.substring(comment.range.start, comment.range.end))
    }

    @Test
    fun `识别块注释并跨行`() {
        val text = "/* 第一行\n第二行 */ val x = 1"
        val comment = kotlin.tokenize(text).first { it.type == TokenType.COMMENT }
        assertTrue(text.substring(comment.range.start, comment.range.end).contains("第二行"))
    }

    @Test
    fun `识别字符串并跳过转义引号`() {
        val text = """val s = "a\"b" + 1"""
        val string = kotlin.tokenize(text).first { it.type == TokenType.STRING }
        assertEquals(""""a\"b"""", text.substring(string.range.start, string.range.end))
    }

    @Test
    fun `未闭合字符串止于行尾而不吞掉后续内容`() {
        val text = "val s = \"unterminated\nval next = 1"
        val tokens = kotlin.tokenize(text)
        val string = tokens.first { it.type == TokenType.STRING }
        assertFalse(text.substring(string.range.start, string.range.end).contains("next"))
        // 后续内容仍能被识别为关键字
        assertTrue(tokens.any { it.type == TokenType.KEYWORD && text.substring(it.range.start, it.range.end) == "val" })
    }

    @Test
    fun `识别整数与浮点数`() {
        val text = "val a = 123 val b = 3.14"
        val numbers = kotlin.tokenize(text)
            .filter { it.type == TokenType.NUMBER }
            .map { text.substring(it.range.start, it.range.end) }
        assertEquals(listOf("123", "3.14"), numbers)
    }

    @Test
    fun `数字中的小数点不误判为运算符`() {
        val text = "val v = 1.5"
        val tokens = kotlin.tokenize(text)
        assertTrue(tokens.none { it.type == TokenType.OPERATOR && text.substring(it.range.start, it.range.end) == "." })
    }

    @Test
    fun `识别注解`() {
        val text = "@Test fun f() {}"
        val annotation = kotlin.tokenize(text).first { it.type == TokenType.ANNOTATION }
        assertEquals("@Test", text.substring(annotation.range.start, annotation.range.end))
    }

    @Test
    fun `Token 之间互不重叠且按顺序排列`() {
        val text = """
            class Foo(val x: Int = 1) {
                // 注释
                fun bar(): String = "hi"
            }
        """.trimIndent()

        val tokens = kotlin.tokenize(text)
        for (i in 1 until tokens.size) {
            assertTrue(
                "Token 顺序错乱：${tokens[i - 1]} 与 ${tokens[i]}",
                tokens[i - 1].range.end <= tokens[i].range.start,
            )
        }
    }

    @Test
    fun `isInsideLiteralOrComment 判定偏移位置`() {
        val text = """val s = "content" // note"""
        val insideString = text.indexOf("content")
        val insideComment = text.indexOf("note")
        val outside = text.indexOf("val")

        assertTrue(kotlin.isInsideLiteralOrComment(text, insideString))
        assertTrue(kotlin.isInsideLiteralOrComment(text, insideComment))
        assertFalse(kotlin.isInsideLiteralOrComment(text, outside))
    }

    @Test
    fun `纯文本定义不产生 Token`() {
        val plain = Lexer(SyntaxDefinition.PLAIN)
        assertTrue(plain.tokenize("anything at all").isEmpty())
    }

    @Test
    fun `增量扫描仅处理指定区间`() {
        val text = "val a = 1\nval b = 2\nval c = 3"
        val secondLineStart = text.indexOf("val b")
        val tokens = kotlin.tokenize(text, from = secondLineStart, to = text.indexOf("val c"))

        // 区间内只应出现第 2 行的内容，不应泄漏第 1/3 行的 Token
        val literals = tokens.map { text.substring(it.range.start, it.range.end) }
        assertTrue("实际：$literals", literals.all { it in setOf("val", "=", "2") })
        assertTrue(literals.contains("val"))

        val keywords = tokens.filter { it.type == TokenType.KEYWORD }
            .map { text.substring(it.range.start, it.range.end) }
        assertEquals(listOf("val"), keywords)
    }

    @Test
    fun `XML 定义识别标签内字符串与注释`() {
        val lexer = Lexer(SyntaxDefinition.forFileName("layout.xml"))
        val text = """<view attr="value" /><!-- note -->"""
        val tokens = lexer.tokenize(text)
        assertTrue(tokens.any { it.type == TokenType.STRING })
        assertTrue(tokens.any { it.type == TokenType.COMMENT })
    }

    @Test
    fun `Shell 定义使用井号注释`() {
        val lexer = Lexer(SyntaxDefinition.forFileName("run.sh"))
        val text = "echo hi # 注释"
        assertTrue(lexer.tokenize(text).any { it.type == TokenType.COMMENT })
    }
}

// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.editor.syntax

import com.mtopensource.editor.text.TextRange

/**
 * 轻量词法分析器。
 *
 * 逐字符扫描文本，识别注释、字符串、数字、标识符与运算符，
 * 输出按出现顺序排列的 [SyntaxToken] 列表。
 *
 * 设计取舍：不追求编译级精度（不做完整语法分析），
 * 但保证单次扫描 O(n) 且不会产生重叠 Token，足以支撑编辑器高亮。
 */
class Lexer(private val definition: SyntaxDefinition) {

    /**
     * 对整段文本进行词法扫描。
     *
     * @param from 起始偏移（增量高亮时可只处理可视区域）
     * @param to 结束偏移
     */
    fun tokenize(text: String, from: Int = 0, to: Int = text.length): List<SyntaxToken> {
        if (definition === SyntaxDefinition.PLAIN && definition.keywords.isEmpty()) return emptyList()

        val tokens = mutableListOf<SyntaxToken>()
        var index = from.coerceIn(0, text.length)
        val end = to.coerceIn(index, text.length)

        // 若起始位置位于块注释内部，先跳到注释结束
        index = skipLeadingBlockComment(text, index, end)

        while (index < end) {
            val ch = text[index]

            // 1. 行注释
            val linePrefix = definition.lineCommentPrefixes.firstOrNull { text.startsWith(it, index) }
            if (definition.supportsLineComment && linePrefix != null) {
                val lineEnd = text.indexOf('\n', index).let { if (it < 0 || it > end) end else it }
                tokens += SyntaxToken(TextRange(index, lineEnd), TokenType.COMMENT)
                index = lineEnd
                continue
            }

            // 2. 块注释
            val blockPair = definition.blockCommentDelimiters.firstOrNull { text.startsWith(it.first, index) }
            if (blockPair != null) {
                val close = text.indexOf(blockPair.second, index + blockPair.first.length)
                val commentEnd = if (close < 0 || close > end) end else close + blockPair.second.length
                tokens += SyntaxToken(TextRange(index, commentEnd.coerceAtMost(end)), TokenType.COMMENT)
                index = commentEnd
                continue
            }

            // 3. 字符串
            if (ch in definition.stringDelimiters) {
                val stringEnd = scanString(text, index, end, ch)
                tokens += SyntaxToken(TextRange(index, stringEnd), TokenType.STRING)
                index = stringEnd
                continue
            }

            // 4. 数字
            if (ch.isDigit()) {
                val numberEnd = scanNumber(text, index, end)
                tokens += SyntaxToken(TextRange(index, numberEnd), TokenType.NUMBER)
                index = numberEnd
                continue
            }

            // 5. 标识符 / 关键字
            if (isIdentifierStart(ch)) {
                var cursor = index + 1
                while (cursor < end && isIdentifierPart(text[cursor])) cursor++
                val word = text.substring(index, cursor)
                classifyWord(word)?.let { type ->
                    tokens += SyntaxToken(TextRange(index, cursor), type)
                }
                index = cursor
                continue
            }

            // 6. 注解（Kotlin/Java 的 @Foo）
            if (ch == '@' && index + 1 < end && isIdentifierStart(text[index + 1])) {
                var cursor = index + 2
                while (cursor < end && isIdentifierPart(text[cursor])) cursor++
                tokens += SyntaxToken(TextRange(index, cursor), TokenType.ANNOTATION)
                index = cursor
                continue
            }

            // 7. 运算符
            if (ch in OPERATORS) {
                var cursor = index + 1
                while (cursor < end && text[cursor] in OPERATORS) {
                    // 运算符连续扫描时可能跨越注释起始符（例如 XML 的 `<!--`），
                    // 一旦遇到注释起点立即停下，交给下一轮循环的注释分支处理，
                    // 否则会把 `<!--` 误判为一串运算符。
                    if (definition.blockCommentDelimiters.any { text.startsWith(it.first, cursor) }) break
                    cursor++
                }
                tokens += SyntaxToken(TextRange(index, cursor), TokenType.OPERATOR)
                index = cursor
                continue
            }

            index++
        }

        return tokens
    }

    /** 判断某个偏移是否处于字符串或注释内部（用于括号匹配等场景）。 */
    fun isInsideLiteralOrComment(text: String, offset: Int): Boolean =
        tokenize(text).any { (it.type == TokenType.STRING || it.type == TokenType.COMMENT) && it.range.contains(offset) }

    private fun classifyWord(word: String): TokenType? = when {
        word in definition.keywords -> TokenType.KEYWORD
        word in definition.types -> TokenType.TYPE
        word in definition.annotations -> TokenType.ANNOTATION
        else -> null
    }

    private fun scanString(text: String, start: Int, end: Int, quote: Char): Int {
        var cursor = start + 1
        while (cursor < end) {
            val ch = text[cursor]
            if (ch == '\\') {
                // 跳过转义字符
                cursor += 2
                continue
            }
            if (ch == quote) return cursor + 1
            if (ch == '\n') return cursor // 未闭合字符串止于行尾
            cursor++
        }
        return end
    }

    private fun scanNumber(text: String, start: Int, end: Int): Int {
        var cursor = start
        var sawDot = false
        while (cursor < end) {
            val ch = text[cursor]
            when {
                ch.isDigit() -> cursor++
                ch == '.' && !sawDot && cursor + 1 < end && text[cursor + 1].isDigit() -> {
                    sawDot = true
                    cursor++
                }
                ch == '_' -> cursor++
                ch in "xXbBoO" && cursor == start + 1 -> cursor++
                ch in "fFdDlL" && cursor == end - 1 -> cursor++
                else -> return cursor
            }
        }
        return cursor
    }

    private fun skipLeadingBlockComment(text: String, from: Int, end: Int): Int {
        var index = from
        for ((open, close) in definition.blockCommentDelimiters) {
            val openIndex = text.lastIndexOf(open, from)
            if (openIndex < 0) continue
            val closeIndex = text.indexOf(close, openIndex + open.length)
            if (closeIndex in 0 until from) {
                index = maxOf(index, closeIndex + close.length)
            } else if (closeIndex >= from && openIndex < from) {
                index = maxOf(index, (closeIndex + close.length).coerceAtMost(end))
            }
        }
        return index.coerceAtMost(end)
    }

    private fun isIdentifierStart(ch: Char): Boolean = ch.isLetter() || ch == '_' || ch == '$'

    private fun isIdentifierPart(ch: Char): Boolean = ch.isLetterOrDigit() || ch == '_' || ch == '$'

    private companion object {
        const val OPERATORS = "+-*/%=<>!&|^~?:."
    }
}

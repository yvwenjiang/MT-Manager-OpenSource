// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.editor.search

import com.mtopensource.editor.text.LineIndex
import com.mtopensource.editor.text.TextLines
import com.mtopensource.editor.text.TextRange

/**
 * 搜索选项。
 */
data class SearchOptions(
    val ignoreCase: Boolean = true,
    /** 是否把查询串当作正则表达式。 */
    val useRegex: Boolean = false,
    /** 是否全词匹配（仅非正则模式生效）。 */
    val wholeWord: Boolean = false,
    /** 是否仅搜索选区。 */
    val searchInSelection: Boolean = false,
)

/**
 * 单条搜索结果。
 */
data class SearchMatch(
    /** 匹配区间。 */
    val range: TextRange,
    /** 所在行号（基于 1）。 */
    val line: Int,
    /** 所在行的文本内容。 */
    val lineText: String,
)

/**
 * 文本搜索引擎。
 *
 * 支持普通文本与正则两种模式，并提供「查找下一个 / 上一个」的
 * 游标式导航；结果按文档顺序返回，便于 UI 高亮全部命中。
 */
class SearchEngine {

    /**
     * 在 [text] 中查找全部匹配。
     *
     * @param selection 当 [SearchOptions.searchInSelection] 为真时限定搜索范围
     */
    fun findAll(
        text: String,
        query: String,
        options: SearchOptions = SearchOptions(),
        selection: TextRange? = null,
    ): List<SearchMatch> {
        if (query.isEmpty()) return emptyList()

        val (regionStart, regionEnd) = run {
            if (options.searchInSelection && selection != null) {
                selection.start to selection.end
            } else {
                0 to text.length
            }
        }
        if (regionStart >= regionEnd) return emptyList()

        val haystack = text.substring(regionStart, regionEnd)
        val rawRanges = if (options.useRegex) {
            regexRanges(haystack, query, options.ignoreCase)
        } else {
            textRanges(haystack, query, options.ignoreCase, options.wholeWord)
        }

        if (rawRanges.isEmpty()) return emptyList()

        val lineIndex = LineIndex(text)
        return rawRanges.map { range ->
            // 把相对区间映射回文档绝对偏移
            val absolute = TextRange(range.start + regionStart, range.end + regionStart)
            val line = lineIndex.lineOf(absolute.start) + 1
            SearchMatch(range = absolute, line = line, lineText = TextLines.lineAt(text, line))
        }
    }

    /**
     * 查找下一个匹配（循环）。
     *
     * @param fromOffset 从该偏移量之后开始查找
     */
    fun findNext(
        text: String,
        query: String,
        fromOffset: Int,
        options: SearchOptions = SearchOptions(),
        selection: TextRange? = null,
    ): SearchMatch? {
        val all = findAll(text, query, options, selection)
        if (all.isEmpty()) return null
        return all.firstOrNull { it.range.start >= fromOffset } ?: all.first()
    }

    /**
     * 查找上一个匹配（循环）。
     */
    fun findPrevious(
        text: String,
        query: String,
        fromOffset: Int,
        options: SearchOptions = SearchOptions(),
        selection: TextRange? = null,
    ): SearchMatch? {
        val all = findAll(text, query, options, selection)
        if (all.isEmpty()) return null
        return all.lastOrNull { it.range.end <= fromOffset } ?: all.last()
    }

    /** 统计匹配数量。 */
    fun countMatches(
        text: String,
        query: String,
        options: SearchOptions = SearchOptions(),
        selection: TextRange? = null,
    ): Int = findAll(text, query, options, selection).size

    private fun textRanges(haystack: String, query: String, ignoreCase: Boolean, wholeWord: Boolean): List<TextRange> {
        val result = mutableListOf<TextRange>()
        var index = haystack.indexOf(query, 0, ignoreCase)
        while (index >= 0) {
            val end = index + query.length
            if (!wholeWord || isWholeWord(haystack, index, end)) {
                result += TextRange(index, end)
            }
            index = haystack.indexOf(query, index + query.length, ignoreCase)
        }
        return result
    }

    private fun regexRanges(haystack: String, pattern: String, ignoreCase: Boolean): List<TextRange> {
        val regex = runCatching {
            if (ignoreCase) Regex(pattern, RegexOption.IGNORE_CASE) else Regex(pattern)
        }.getOrNull() ?: return emptyList()

        return regex.findAll(haystack)
            // 跳过零宽匹配，避免死循环与无意义高亮
            .filter { it.value.isNotEmpty() }
            .map { TextRange(it.range.first, it.range.last + 1) }
            .toList()
    }

    private fun isWholeWord(text: String, start: Int, end: Int): Boolean {
        val beforeOk = start == 0 || !isWordChar(text[start - 1])
        val afterOk = end >= text.length || !isWordChar(text[end])
        return beforeOk && afterOk
    }

    private fun isWordChar(ch: Char): Boolean = ch.isLetterOrDigit() || ch == '_'
}

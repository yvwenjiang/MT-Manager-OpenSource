// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.editor.text

/**
 * 不可变的文本区间，用于高亮、搜索与选择。
 *
 * 采用左闭右开区间 `[start, end)`，与 Kotlin 的 `substring` 语义一致。
 */
data class TextRange(val start: Int, val end: Int) : Comparable<TextRange> {

    init {
        require(start <= end) { "区间起点不能大于终点：[$start, $end)" }
    }

    val length: Int get() = end - start

    val isEmpty: Boolean get() = start == end

    /** 是否包含指定下标。 */
    fun contains(index: Int): Boolean = index in start until end

    /** 与另一区间是否重叠。 */
    fun overlaps(other: TextRange): Boolean = start < other.end && other.start < end

    override fun compareTo(other: TextRange): Int =
        if (start != other.start) start - other.start else end - other.end

    companion object {
        fun empty(at: Int) = TextRange(at, at)

        /** 从文本中查找所有匹配区间。 */
        fun findAll(text: String, keyword: String, ignoreCase: Boolean = true): List<TextRange> {
            if (keyword.isEmpty()) return emptyList()
            val result = mutableListOf<TextRange>()
            var index = text.indexOf(keyword, 0, ignoreCase)
            while (index >= 0) {
                result += TextRange(index, index + keyword.length)
                index = text.indexOf(keyword, index + keyword.length, ignoreCase)
            }
            return result
        }
    }
}

// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.editor.document

import com.mtopensource.editor.syntax.Lexer
import com.mtopensource.editor.syntax.SyntaxDefinition
import com.mtopensource.editor.syntax.SyntaxToken
import com.mtopensource.editor.text.LineIndex
import com.mtopensource.editor.text.TextLines
import com.mtopensource.editor.text.TextRange

/**
 * 文本编辑缓冲区。
 *
 * 这是编辑器的核心状态容器，负责：
 * - 持有文本内容与光标位置
 * - 提供插入/删除/替换等编辑操作并记录撤销历史
 * - 维护行索引与语法高亮缓存
 * - 管理脏标记（是否已修改）
 *
 * 该类不依赖任何 Android 或 UI 组件，可在 JVM 单元测试中完整驱动，
 * UI 层（Compose / View）只负责渲染 [lines] 与 [highlightTokens] 并转发输入事件。
 */
class TextDocument(
    content: String = "",
    val filePath: String? = null,
    val syntaxDefinition: SyntaxDefinition = filePath?.let { SyntaxDefinition.forFileName(it) }
        ?: SyntaxDefinition.PLAIN,
) {

    /** 编辑后的完整文本。 */
    var text: String = TextLines.normalizeLineEndings(content)
        private set

    /** 光标偏移量。 */
    var cursorOffset: Int = 0
        private set

    /** 选区起点，等于 [cursorOffset] 表示无选区。 */
    var selectionStart: Int = 0
        private set

    /** 是否被修改过（未保存）。 */
    var isDirty: Boolean = false
        private set

    /** 是否只读。 */
    var isReadOnly: Boolean = false

    private val lineIndex = LineIndex(text)
    private val undoStack = UndoStack()
    private val lexer = Lexer(syntaxDefinition)
    private var cachedTokens: List<SyntaxToken>? = null

    /** 行数。 */
    val lineCount: Int get() = lineIndex.lineCount

    /** 文本总长度。 */
    val length: Int get() = text.length

    /** 是否可撤销。 */
    val canUndo: Boolean get() = undoStack.canUndo

    /** 是否可重做。 */
    val canRedo: Boolean get() = undoStack.canRedo

    /** 是否有选区。 */
    val hasSelection: Boolean get() = selectionStart != cursorOffset

    /** 当前选区（已归一化）。 */
    val selection: TextRange
        get() = if (selectionStart <= cursorOffset) {
            TextRange(selectionStart, cursorOffset)
        } else {
            TextRange(cursorOffset, selectionStart)
        }

    /** 当前光标所在行列（基于 1）。 */
    val cursorPosition: Pair<Int, Int>
        get() = TextLines.offsetToPosition(text, cursorOffset)

    /**
     * 语法高亮结果（惰性计算并缓存）。
     */
    val highlightTokens: List<SyntaxToken>
        get() = cachedTokens ?: lexer.tokenize(text).also { cachedTokens = it }

    /**
     * 按行拆分文本，供渲染使用。
     */
    val lines: List<String>
        get() = text.split('\n')

    // ---------------------------------------------------------------------
    // 编辑操作
    // ---------------------------------------------------------------------

    /** 在光标处插入文本。 */
    fun insert(newText: String): Boolean = applyEdits(listOf(TextEdit(cursorOffset, "", newText)))

    /** 删除选区，若无选区则删除光标前一个字符。 */
    fun deleteBackward(): Boolean {
        return if (hasSelection) {
            deleteSelection()
        } else {
            if (cursorOffset <= 0) return false
            // 处理 CRLF 情形：删除时向前多吞一个字符
            val start = if (cursorOffset >= 2 && text[cursorOffset - 1] == '\n' && text[cursorOffset - 2] == '\r') {
                cursorOffset - 2
            } else {
                cursorOffset - 1
            }
            applyEdits(listOf(TextEdit(start, text.substring(start, cursorOffset), "")))
        }
    }

    /** 删除选区，若无选区则删除光标后一个字符。 */
    fun deleteForward(): Boolean {
        return if (hasSelection) {
            deleteSelection()
        } else {
            if (cursorOffset >= text.length) return false
            val end = if (text[cursorOffset] == '\r' && cursorOffset + 1 < text.length && text[cursorOffset + 1] == '\n') {
                cursorOffset + 2
            } else {
                cursorOffset + 1
            }
            applyEdits(listOf(TextEdit(cursorOffset, text.substring(cursorOffset, end), "")))
        }
    }

    /** 删除当前选区。 */
    fun deleteSelection(): Boolean {
        if (!hasSelection) return false
        val range = selection
        return applyEdits(listOf(TextEdit(range.start, text.substring(range.start, range.end), "")))
    }

    /** 用给定文本替换指定区间。 */
    fun replace(range: TextRange, replacement: String): Boolean =
        applyEdits(listOf(TextEdit(range.start, text.substring(range.start, range.end), replacement)))

    /**
     * 批量替换所有匹配项（全局搜索替换）。
     *
     * @return 替换次数
     */
    fun replaceAll(keyword: String, replacement: String, ignoreCase: Boolean = true): Int {
        if (isReadOnly) return 0
        if (keyword.isEmpty()) return 0
        val matches = TextRange.findAll(text, keyword, ignoreCase)
        if (matches.isEmpty()) return 0

        // 从后往前替换，避免偏移量失效
        val edits = matches.asReversed().map { range ->
            TextEdit(range.start, text.substring(range.start, range.end), replacement)
        }
        applyEdits(edits)
        return matches.size
    }

    /** 全部选中。 */
    fun selectAll() {
        selectionStart = 0
        cursorOffset = text.length
    }

    /** 移动光标并清除选区。 */
    fun moveCursorTo(offset: Int) {
        val target = offset.coerceIn(0, text.length)
        cursorOffset = target
        selectionStart = target
    }

    /** 设置选区。 */
    fun setSelection(start: Int, end: Int) {
        val s = start.coerceIn(0, text.length)
        val e = end.coerceIn(0, text.length)
        selectionStart = s
        cursorOffset = e
    }

    /** 撤销。 */
    fun undo(): Boolean {
        val edits = undoStack.undo() ?: return false
        text = undoStack.applyUndo(text, edits)
        afterContentChanged(markDirty = true)
        // 光标回到本次编辑起点
        edits.firstOrNull()?.let { moveCursorTo(it.offset) }
        return true
    }

    /** 重做。 */
    fun redo(): Boolean {
        val edits = undoStack.redo() ?: return false
        text = undoStack.applyRedo(text, edits)
        afterContentChanged(markDirty = true)
        edits.lastOrNull()?.let { moveCursorTo(it.cursorAfter) }
        return true
    }

    /** 保存后清除脏标记。 */
    fun markSaved() {
        isDirty = false
    }

    /** 用新内容整体重置文档（例如重新加载文件）。 */
    fun reset(newContent: String) {
        text = TextLines.normalizeLineEndings(newContent)
        undoStack.clear()
        isDirty = false
        moveCursorTo(0)
        afterContentChanged(markDirty = false)
    }

    // ---------------------------------------------------------------------
    // 内部实现
    // ---------------------------------------------------------------------

    /**
     * 应用一组编辑。
     *
     * 所有公共编辑入口都汇聚到这里，保证：
     * 1. 只读模式下的写操作被统一拒绝
     * 2. 每次编辑都进入撤销栈
     * 3. 索引与高亮缓存同步失效
     */
    private fun applyEdits(edits: List<TextEdit>): Boolean {
        if (edits.isEmpty()) return false
        if (isReadOnly) return false

        var result = text
        var cursor = cursorOffset

        for (edit in edits) {
            val start = edit.offset
            val end = start + edit.before.length
            if (start < 0 || end > result.length) return false
            result = result.substring(0, start) + edit.after + result.substring(end)
        }

        // 光标落在最后一个编辑之后
        edits.lastOrNull()?.let { cursor = it.cursorAfter }

        text = result
        undoStack.record(edits)
        moveCursorTo(cursor)
        afterContentChanged(markDirty = true)
        return true
    }

    private fun afterContentChanged(markDirty: Boolean) {
        lineIndex.rebuild(text)
        cachedTokens = null
        cursorOffset = cursorOffset.coerceIn(0, text.length)
        selectionStart = selectionStart.coerceIn(0, text.length)
        if (markDirty) isDirty = true
    }
}

// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.editor.document

/**
 * 撤销/重做栈。
 *
 * 采用「命令模式」记录每次编辑的正反操作：
 * - undo：对 [TextEdit] 执行反向替换
 * - redo：重新执行正向替换
 *
 * 通过 [maxDepth] 限制栈深度，避免大文件长时间编辑占用过多内存。
 */
class UndoStack(private val maxDepth: Int = 200) {

    private val undoStack = ArrayDeque<List<TextEdit>>()
    private val redoStack = ArrayDeque<List<TextEdit>>()

    /** 是否可撤销。 */
    val canUndo: Boolean get() = undoStack.isNotEmpty()

    /** 是否可重做。 */
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** 撤销栈深度。 */
    val undoDepth: Int get() = undoStack.size

    /** 重做栈深度。 */
    val redoDepth: Int get() = redoStack.size

    /**
     * 记录一组编辑（同一批次的连续输入合并为一个撤销单元）。
     */
    fun record(edits: List<TextEdit>) {
        if (edits.isEmpty()) return
        undoStack.addLast(edits)
        if (undoStack.size > maxDepth) undoStack.removeFirst()
        // 产生新编辑后，原有的重做链失效
        redoStack.clear()
    }

    /**
     * 弹出最近一次编辑以执行撤销。
     * @return 需要反向应用的编辑列表，无可撤销时返回 null
     */
    fun undo(): List<TextEdit>? {
        val edits = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(edits)
        return edits
    }

    /**
     * 弹出最近一次撤销以执行重做。
     * @return 需要正向应用的编辑列表，无可重做时返回 null
     */
    fun redo(): List<TextEdit>? {
        val edits = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(edits)
        return edits
    }

    /** 清空全部历史。 */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    /**
     * 依据编辑列表把文本还原到编辑前状态。
     *
     * 注意必须逆序应用，因为后面的编辑起点基于前面编辑后的文本。
     */
    fun applyUndo(text: String, edits: List<TextEdit>): String {
        var result = text
        for (edit in edits.asReversed()) {
            val start = edit.offset
            val end = start + edit.after.length
            if (start < 0 || end > result.length) continue
            result = result.substring(0, start) + edit.before + result.substring(end)
        }
        return result
    }

    /** 依据编辑列表把文本推进到编辑后状态。 */
    fun applyRedo(text: String, edits: List<TextEdit>): String {
        var result = text
        for (edit in edits) {
            val start = edit.offset
            val end = start + edit.before.length
            if (start < 0 || end > result.length) continue
            result = result.substring(0, start) + edit.after + result.substring(end)
        }
        return result
    }
}

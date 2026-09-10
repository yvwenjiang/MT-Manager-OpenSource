// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.editor.document

import com.mtopensource.editor.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextDocumentTest {

    private fun doc(content: String = "") = TextDocument(content)

    @Test
    fun `新建文档初始状态`() {
        val document = doc("hello")
        assertEquals(5, document.length)
        assertEquals(1, document.lineCount)
        assertFalse(document.isDirty)
        assertFalse(document.canUndo)
        assertFalse(document.hasSelection)
    }

    @Test
    fun `插入文本后光标前进且标记为脏`() {
        val document = doc("ab")
        document.moveCursorTo(2)
        assertTrue(document.insert("cd"))

        assertEquals("abcd", document.text)
        assertEquals(4, document.cursorOffset)
        assertTrue(document.isDirty)
    }

    @Test
    fun `deleteBackward 删除前一个字符`() {
        val document = doc("abc")
        document.moveCursorTo(3)
        assertTrue(document.deleteBackward())
        assertEquals("ab", document.text)
        assertEquals(2, document.cursorOffset)
    }

    @Test
    fun `deleteBackward 在文首无效`() {
        val document = doc("abc")
        document.moveCursorTo(0)
        assertFalse(document.deleteBackward())
        assertEquals("abc", document.text)
    }

    @Test
    fun `deleteForward 删除后一个字符`() {
        val document = doc("abc")
        document.moveCursorTo(0)
        assertTrue(document.deleteForward())
        assertEquals("bc", document.text)
        assertEquals(0, document.cursorOffset)
    }

    @Test
    fun `有选区时删除操作作用于选区`() {
        val document = doc("hello world")
        document.setSelection(0, 5)
        assertTrue(document.deleteBackward())
        assertEquals(" world", document.text)
    }

    @Test
    fun `undo 与 redo 可完整往返`() {
        val document = doc("start")
        document.moveCursorTo(5)
        document.insert("-middle")
        document.insert("-end")

        assertEquals("start-middle-end", document.text)

        assertTrue(document.undo())
        assertEquals("start-middle", document.text)
        assertTrue(document.undo())
        assertEquals("start", document.text)
        assertFalse(document.canUndo)

        assertTrue(document.redo())
        assertEquals("start-middle", document.text)
        assertTrue(document.redo())
        assertEquals("start-middle-end", document.text)
    }

    @Test
    fun `新编辑会清空重做栈`() {
        val document = doc("a")
        document.moveCursorTo(1)
        document.insert("b")
        document.undo()
        assertTrue(document.canRedo)

        document.insert("c")
        assertFalse(document.canRedo)
    }

    @Test
    fun `replaceAll 统计并替换全部匹配`() {
        val document = doc("foo bar foo baz foo")
        val count = document.replaceAll("foo", "QUX")
        assertEquals(3, count)
        assertEquals("QUX bar QUX baz QUX", document.text)
    }

    @Test
    fun `replaceAll 忽略大小写时不破坏原文本长度假设`() {
        val document = doc("Aa Aa")
        val count = document.replaceAll("aa", "b", ignoreCase = true)
        assertEquals(2, count)
        assertEquals("b b", document.text)
    }

    @Test
    fun `replaceAll 无匹配时返回零且不改动文本`() {
        val document = doc("abc")
        assertEquals(0, document.replaceAll("zzz", "x"))
        assertEquals("abc", document.text)
        assertFalse(document.isDirty)
    }

    @Test
    fun `选区归一化与替换`() {
        val document = doc("abcdef")
        document.setSelection(4, 1)
        assertEquals(TextRange(1, 4), document.selection)

        document.replace(document.selection, "X")
        assertEquals("aXef", document.text)
    }

    @Test
    fun `只读模式拒绝所有写操作`() {
        val document = doc("abc")
        document.isReadOnly = true
        document.moveCursorTo(3)

        assertFalse(document.insert("d"))
        assertFalse(document.deleteBackward())
        // replaceAll 返回替换处数（Int），只读模式下应为 0
        assertEquals(0, document.replaceAll("a", "z"))
        assertEquals("abc", document.text)
        assertFalse(document.isDirty)
    }

    @Test
    fun `reset 清空历史与脏标记`() {
        val document = doc("old")
        document.moveCursorTo(3)
        document.insert("!")
        document.reset("brand new")

        assertEquals("brand new", document.text)
        assertFalse(document.isDirty)
        assertFalse(document.canUndo)
        assertEquals(0, document.cursorOffset)
    }

    @Test
    fun `光标行列位置随编辑更新`() {
        val document = doc("line1\nline2")
        document.moveCursorTo(6)
        assertEquals(2 to 1, document.cursorPosition)

        document.insert("X")
        assertEquals("line1\nXline2", document.text)
        assertEquals(2 to 2, document.cursorPosition)
    }

    @Test
    fun `行拆分包含末尾空行`() {
        val document = doc("a\nb\n")
        assertEquals(listOf("a", "b", ""), document.lines)
        assertEquals(3, document.lineCount)
    }

    @Test
    fun `高亮 Token 在编辑后失效并重算`() {
        val document = TextDocument("val a = 1", filePath = "A.kt")
        val before = document.highlightTokens
        assertTrue(before.isNotEmpty())

        document.moveCursorTo(0)
        document.insert("//")

        val after = document.highlightTokens
        assertTrue(after.any { it.type == com.mtopensource.editor.syntax.TokenType.COMMENT })
    }

    @Test
    fun `统一 CRLF 为 LF`() {
        val document = doc("a\r\nb")
        assertEquals("a\nb", document.text)
    }

    @Test
    fun `删除 CRLF 换行时一次删除两个字符`() {
        val document = doc("ab\r\ncd")
        // 文本已归一化为 "ab\ncd"
        document.moveCursorTo(3)
        assertTrue(document.deleteBackward())
        assertEquals("abcd", document.text)
    }

    @Test
    fun `selectAll 选中全文`() {
        val document = doc("hello")
        document.selectAll()
        assertTrue(document.hasSelection)
        assertEquals(TextRange(0, 5), document.selection)
    }

    @Test
    fun `连续插入可逐次撤销`() {
        val document = doc("")
        document.insert("你")
        document.insert("好")
        assertEquals("你好", document.text)

        assertTrue(document.undo())
        assertEquals("你", document.text)
        assertTrue(document.undo())
        assertEquals("", document.text)
    }
}

class UndoStackTest {

    @Test
    fun `超过最大深度后丢弃最旧记录`() {
        val stack = UndoStack(maxDepth = 3)
        repeat(5) { index ->
            stack.record(listOf(TextEdit(index, "", "x")))
        }
        assertEquals(3, stack.undoDepth)
    }

    @Test
    fun `applyUndo 逆序应用编辑`() {
        val stack = UndoStack()
        // 依次在偏移 0 处插入 a、在偏移 1 处插入 b
        val edits = listOf(
            TextEdit(0, "", "a"),
            TextEdit(1, "", "b"),
        )
        val after = "ab"
        val before = stack.applyUndo(after, edits)
        assertEquals("", before)
    }

    @Test
    fun `applyRedo 正序应用编辑`() {
        val stack = UndoStack()
        val edits = listOf(
            TextEdit(0, "", "a"),
            TextEdit(1, "", "b"),
        )
        assertEquals("ab", stack.applyRedo("", edits))
    }

    @Test
    fun `clear 清空两侧栈`() {
        val stack = UndoStack()
        stack.record(listOf(TextEdit(0, "", "a")))
        stack.undo()
        stack.clear()
        assertFalse(stack.canUndo)
        assertFalse(stack.canRedo)
    }
}

class TextEditTest {

    @Test
    fun `识别编辑类型`() {
        assertTrue(TextEdit(0, "", "a").isInsertion)
        assertTrue(TextEdit(0, "a", "").isDeletion)
        assertTrue(TextEdit(0, "a", "b").isReplacement)
    }

    @Test
    fun `lengthDelta 计算长度变化`() {
        assertEquals(1, TextEdit(0, "a", "ab").lengthDelta)
        assertEquals(-1, TextEdit(0, "ab", "a").lengthDelta)
    }

    @Test
    fun `cursorAfter 指向插入文本之后`() {
        assertEquals(5, TextEdit(2, "", "abc").cursorAfter)
    }
}

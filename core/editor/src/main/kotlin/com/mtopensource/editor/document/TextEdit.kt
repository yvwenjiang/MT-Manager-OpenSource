// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.editor.document

/**
 * 一次文本编辑动作。
 *
 * [before] 与 [after] 记录了被替换文本，使撤销/重做可以
 * 在无需保存全文快照的情况下精确还原。
 */
data class TextEdit(
    /** 编辑起始偏移量。 */
    val offset: Int,
    /** 被删除的原文（插入操作时为空串）。 */
    val before: String,
    /** 新插入的文本（删除操作时为空串）。 */
    val after: String,
) {

    /** 是否为纯插入。 */
    val isInsertion: Boolean get() = before.isEmpty() && after.isNotEmpty()

    /** 是否为纯删除。 */
    val isDeletion: Boolean get() = before.isNotEmpty() && after.isEmpty()

    /** 是否为替换。 */
    val isReplacement: Boolean get() = before.isNotEmpty() && after.isNotEmpty()

    /** 编辑后的文本长度变化量。 */
    val lengthDelta: Int get() = after.length - before.length

    /** 编辑后光标应处的偏移量。 */
    val cursorAfter: Int get() = offset + after.length
}

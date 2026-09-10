// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.mtmanager.ui

import com.mtopensource.filemanager.operation.OperationResult

/**
 * 把 [OperationResult] 翻译为面向用户的中文文案。
 *
 * `FileError` 已在 `core:common` 中自带 `displayMessage()`，
 * 这里只补充文件操作结果的文案，保持错误文案的展示入口收敛在少数几处。
 */
fun OperationResult.displayMessage(): String = when (this) {
    is OperationResult.Success -> "操作完成，共处理 $affectedCount 项"
    is OperationResult.Failure -> "操作失败：$reason"
    is OperationResult.Cancelled -> "操作已取消"
}

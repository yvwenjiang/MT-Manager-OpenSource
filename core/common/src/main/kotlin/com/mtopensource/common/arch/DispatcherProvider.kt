// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.common.arch

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * 调度器提供者。
 *
 * 直接在业务代码里写 `Dispatchers.IO` 会导致单元测试难以控制线程，
 * 统一通过本接口注入，测试时可替换为 `UnconfinedTestDispatcher`。
 */
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val default: CoroutineDispatcher
    val io: CoroutineDispatcher

    companion object {
        val Default: DispatcherProvider = object : DispatcherProvider {
            override val main: CoroutineDispatcher = Dispatchers.Main
            override val default: CoroutineDispatcher = Dispatchers.Default
            override val io: CoroutineDispatcher = Dispatchers.IO
        }
    }
}

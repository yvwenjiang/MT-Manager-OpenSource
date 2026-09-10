// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.common.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun `formatSize 覆盖各量级`() {
        assertEquals("0 B", Formatters.formatSize(0))
        assertEquals("512 B", Formatters.formatSize(512))
        assertEquals("1 KB", Formatters.formatSize(1024))
        assertEquals("1.5 KB", Formatters.formatSize(1536))
        assertEquals("1 MB", Formatters.formatSize(1024L * 1024))
        assertEquals("2.5 GB", Formatters.formatSize((2.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun `formatSize 对负数与超大值做保护`() {
        assertEquals("0 B", Formatters.formatSize(-1))
        assertEquals("1 TB", Formatters.formatSize(1024L * 1024 * 1024 * 1024))
    }

    @Test
    fun `formatPermissions 输出 rwx 字符串`() {
        assertEquals("rwxr-xr-x", Formatters.formatPermissions(0b111_101_101))
        assertEquals("rw-r--r--", Formatters.formatPermissions(0b110_100_100))
        assertEquals("---------", Formatters.formatPermissions(0))
        assertEquals("rwxrwxrwx", Formatters.formatPermissions(0b111_111_111))
    }

    @Test
    fun `formatDateTime 处理非法时间戳`() {
        assertEquals("-", Formatters.formatDateTime(0))
        assertEquals("-", Formatters.formatDateTime(-100))
    }
}

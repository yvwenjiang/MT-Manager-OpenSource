// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.plugins.rootsupport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `ls -la` 输出解析测试。
 *
 * 这些解析逻辑是 Root 文件系统正确性的关键，
 * 因此单独测试而不依赖真实设备。
 */
class RootFileSystemProviderTest {

    private val provider = RootFileSystemProvider()

    @Test
    fun `解析普通文件行`() {
        val line = "-rw-r--r-- 1 root root     1234 2026-01-02 03:04 build.prop"
        val item = provider.parseLsLine(line, "/system")

        assertNotNull(item)
        item!!
        assertEquals("build.prop", item.name)
        assertEquals("/system/build.prop", item.path)
        assertFalse(item.isDirectory)
        assertEquals(1234L, item.size)
        assertEquals("root", item.scheme)
    }

    @Test
    fun `解析目录行并清零大小`() {
        val line = "drwxr-xr-x 2 root root     4096 2026-01-02 03:04 app"
        val item = provider.parseLsLine(line, "/system")!!

        assertTrue(item.isDirectory)
        assertEquals(0L, item.size)
        assertEquals("app", item.name)
    }

    @Test
    fun `解析符号链接并剥离箭头目标`() {
        val line = "lrwxrwxrwx 1 root root       21 2026-01-02 03:04 sdcard -> /storage/self/primary"
        val item = provider.parseLsLine(line, "/")!!

        assertTrue(item.isSymlink)
        assertEquals("sdcard", item.name)
        assertEquals("/sdcard", item.path)
    }

    @Test
    fun `解析八进制权限位`() {
        val rwxr_xr_x = provider.parseLsLine("-rwxr-xr-x 1 root root 100 2026-01-02 03:04 a", "/x")!!
        assertEquals(0b111_101_101, rwxr_xr_x.permissions)

        val rw_r__r__ = provider.parseLsLine("-rw-r--r-- 1 root root 100 2026-01-02 03:04 b", "/x")!!
        assertEquals(0b110_100_100, rw_r__r__ .permissions)
    }

    @Test
    fun `识别隐藏文件`() {
        val line = "-rw-r--r-- 1 root root     10 2026-01-02 03:04 .hidden"
        val item = provider.parseLsLine(line, "/data")!!
        assertTrue(item.isHidden)
    }

    @Test
    fun `解析含空格的文件名`() {
        val line = "-rw-r--r-- 1 root root     10 2026-01-02 03:04 my document.txt"
        val item = provider.parseLsLine(line, "/sdcard")!!
        assertEquals("my document.txt", item.name)
    }

    @Test
    fun `total 汇总行被忽略`() {
        assertNull(provider.parseLsLine("total 48", "/system"))
    }

    @Test
    fun `空行被忽略`() {
        assertNull(provider.parseLsLine("", "/system"))
        assertNull(provider.parseLsLine("   ", "/system"))
    }

    @Test
    fun `字段不足的行被忽略`() {
        assertNull(provider.parseLsLine("-rw-r--r-- 1 root", "/system"))
    }

    @Test
    fun `偏移量与路径拼接正确`() {
        val line = "-rw-r--r-- 1 root root 1 2026-01-02 03:04 etc"
        val item = provider.parseLsLine(line, "/system")!!
        assertEquals("/system/etc", item.path)
    }

    @Test
    fun `根目录下的条目路径不回退`() {
        val line = "-rw-r--r-- 1 root root 1 2026-01-02 03:04 init.rc"
        val item = provider.parseLsLine(line, "/")!!
        assertEquals("/init.rc", item.path)
    }

    @Test
    fun `scheme 与权限标记正确`() {
        assertEquals("root", provider.scheme)
        assertTrue(provider.requiresPermission)
    }
}

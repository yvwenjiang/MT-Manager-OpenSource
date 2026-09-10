// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.common.utils

import java.util.Locale

/**
 * 文件大小与时间的格式化工具。
 *
 * 之所以做成纯 Kotlin 实现（不依赖 Android API），
 * 是为了能在 JVM 单元测试中直接验证边界值。
 */
object Formatters {

    private const val UNIT_B = 1L
    private const val UNIT_KB = 1024L
    private const val UNIT_MB = 1024L * 1024
    private const val UNIT_GB = 1024L * 1024 * 1024
    private const val UNIT_TB = 1024L * 1024 * 1024 * 1024

    /**
     * 将字节数格式化为易读字符串，例如 `1.5 MB`。
     *
     * - 小于 1KB 时显示整数 `B`
     * - 其余单位保留两位小数，并去掉多余的 `.00`
     */
    fun formatSize(bytes: Long): String {
        if (bytes < 0) return "0 B"
        return when {
            bytes < UNIT_KB -> "$bytes B"
            bytes < UNIT_MB -> format(bytes, UNIT_KB, "KB")
            bytes < UNIT_GB -> format(bytes, UNIT_MB, "MB")
            bytes < UNIT_TB -> format(bytes, UNIT_GB, "GB")
            else -> format(bytes, UNIT_TB, "TB")
        }
    }

    private fun format(bytes: Long, unit: Long, suffix: String): String {
        val value = bytes.toDouble() / unit
        val text = String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
        return "$text $suffix"
    }

    /**
     * 格式化时间戳为 `yyyy-MM-dd HH:mm`，本地时区。
     * 纯 JVM 实现，避免依赖 android.text.format.DateFormat。
     */
    fun formatDateTime(epochMillis: Long): String {
        if (epochMillis <= 0L) return "-"
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = epochMillis
        return buildString {
            append(calendar.get(java.util.Calendar.YEAR))
            append('-')
            append(pad(calendar.get(java.util.Calendar.MONTH) + 1))
            append('-')
            append(pad(calendar.get(java.util.Calendar.DAY_OF_MONTH)))
            append(' ')
            append(pad(calendar.get(java.util.Calendar.HOUR_OF_DAY)))
            append(':')
            append(pad(calendar.get(java.util.Calendar.MINUTE)))
        }
    }

    private fun pad(value: Int): String = if (value < 10) "0$value" else value.toString()

    /**
     * 将 Unix 权限位（如 0755）转换为 `rwxr-xr-x` 形式。
     */
    fun formatPermissions(mode: Int): String {
        val chars = CharArray(9)
        val flags = intArrayOf(
            0b100_000_000, // 所有者读
            0b010_000_000, // 所有者写
            0b001_000_000, // 所有者执行
            0b000_100_000, // 组读
            0b000_010_000, // 组写
            0b000_001_000, // 组执行
            0b000_000_100, // 其他读
            0b000_000_010, // 其他写
            0b000_000_001, // 其他执行
        )
        val letters = "rwxrwxrwx"
        for (i in flags.indices) {
            chars[i] = if (mode and flags[i] != 0) letters[i] else '-'
        }
        return String(chars)
    }
}

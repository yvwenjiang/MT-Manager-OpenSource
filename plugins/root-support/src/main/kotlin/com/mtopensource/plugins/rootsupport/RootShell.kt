// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.plugins.rootsupport

import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 单条命令执行结果。
 */
data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val isSuccess: Boolean get() = exitCode == 0
}

/**
 * Root Shell 封装。
 *
 * 通过 `su -c` 执行单条命令，避免维持常驻交互式 shell
 * （常驻 shell 在多进程并发下容易出现输出错乱）。
 *
 * 所有命令执行都设有超时，防止 `su` 二进制在无授权时阻塞 UI 线程。
 */
class RootShell(
    /** su 可执行文件路径，部分定制 ROM 位于 /system/xbin/su。 */
    private val suPath: String = "su",
    /** 单条命令超时时间（毫秒）。 */
    private val timeoutMillis: Long = 15_000L,
) {

    /**
     * 检测设备是否已 Root（即 `su` 是否可用且已授权）。
     */
    fun isRootAvailable(): Boolean {
        if (!isSuBinaryPresent()) return false
        return execute("id").stdout.contains("uid=0")
    }

    /** 检查 su 二进制是否存在于常见路径。 */
    fun isSuBinaryPresent(): Boolean = SU_CANDIDATES.any { candidate ->
        java.io.File(candidate).let { it.exists() && it.canExecute() }
    }

    /**
     * 执行命令。
     *
     * @param command 待执行命令，会被原样传给 `su -c`
     */
    fun execute(command: String): ShellResult {
        val actualSu = SU_CANDIDATES.firstOrNull { java.io.File(it).let { f -> f.exists() && f.canExecute() } } ?: suPath

        return try {
            val process = ProcessBuilder(actualSu, "-c", command)
                .redirectErrorStream(false)
                .start()

            // 异步读取输出，避免管道缓冲区写满导致进程阻塞
            val stdout = StringBuilder()
            val stderr = StringBuilder()

            val outThread = Thread { readStream(process.inputStream.bufferedReader(), stdout) }
            val errThread = Thread { readStream(process.errorStream.bufferedReader(), stderr) }
            outThread.start()
            errThread.start()

            val finished = process.waitFor(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                return ShellResult(-1, stdout.toString(), "命令执行超时（${timeoutMillis}ms）")
            }

            outThread.join(1_000)
            errThread.join(1_000)

            ShellResult(process.exitValue(), stdout.toString().trim(), stderr.toString().trim())
        } catch (e: Exception) {
            ShellResult(-1, "", e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * 以 root 身份挂载指定路径为可读写。
     *
     * 系统分区默认为只读，修改前必须先 `mount -o remount,rw`。
     */
    fun remountReadWrite(path: String): ShellResult {
        // 先尝试获取挂载点，再重新挂载为读写
        val mountPoint = execute("mount | grep ' $path ' | awk '{print \$1}'").stdout
        val target = mountPoint.ifEmpty { path }
        return execute("mount -o remount,rw '$target' '$path'")
    }

    /** 读文件内容（借助 root 权限，绕过普通进程的权限限制）。 */
    fun readFile(path: String): ShellResult = execute("cat '$path'")

    /** 写文件内容（借助 root 权限）。 */
    fun writeFile(path: String, content: String): ShellResult {
        // 使用 base64 编码传输，避免内容中的特殊字符破坏 shell 语法
        val encoded = java.util.Base64.getEncoder().encodeToString(content.toByteArray())
        return execute("echo '$encoded' | base64 -d > '$path'")
    }

    /** 修改文件权限。 */
    fun chmod(path: String, mode: String): ShellResult = execute("chmod $mode '$path'")

    private fun readStream(reader: BufferedReader, sink: StringBuilder) {
        try {
            reader.useLines { lines -> lines.forEach { sink.appendLine(it) } }
        } catch (e: Exception) {
            // 进程被强制终止时读取会抛异常，忽略即可
        }
    }

    companion object {
        /** su 二进制的常见位置，按优先级排列。 */
        val SU_CANDIDATES = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/magisk/.core/bin/su",
            "su",
        )
    }
}

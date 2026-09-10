// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.common.utils

/**
 * 统一日志门面。
 *
 * 设计目标：
 * 1. 全项目通过 [Logger] 输出日志，禁止直接调用 `android.util.Log`，
 *    便于后续接入文件日志、日志上报或在测试中替换实现。
 * 2. Release 构建下由 [minLevel] 过滤掉调试日志，避免性能损耗。
 */
object Logger {

    /** Android Log 的等级常量，避免本模块强依赖 android.util.Log。 */
    private const val VERBOSE = 2
    private const val DEBUG = 3
    private const val INFO = 4
    private const val WARN = 5
    private const val ERROR = 6

    /** 单条日志最大长度，超出部分会被分片输出。 */
    private const val MAX_LOG_LENGTH = 4000

    /** 当前生效的最低日志等级，可运行时调整。 */
    @Volatile
    var minLevel: Int = VERBOSE

    /** 可替换的底层输出器，默认走 android.util.Log（通过 [LogSink] 延迟绑定）。 */
    @Volatile
    var sink: LogSink = DefaultLogSink

    fun v(tag: String, message: String) = log(VERBOSE, tag, message, null)

    fun d(tag: String, message: String) = log(DEBUG, tag, message, null)

    fun i(tag: String, message: String) = log(INFO, tag, message, null)

    fun w(tag: String, message: String) = log(WARN, tag, message, null)

    fun w(tag: String, message: String, throwable: Throwable) = log(WARN, tag, message, throwable)

    fun e(tag: String, message: String) = log(ERROR, tag, message, null)

    fun e(tag: String, message: String, throwable: Throwable) = log(ERROR, tag, message, throwable)

    /** 打印耗时日志，便于定位性能瓶颈。 */
    inline fun <T> trace(tag: String, label: String, block: () -> T): T {
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            val costMs = (System.nanoTime() - start) / 1_000_000.0
            d(tag, "$label 耗时 ${"%.2f".format(costMs)}ms")
        }
    }

    private fun log(level: Int, tag: String, message: String, throwable: Throwable?) {
        if (level < minLevel) return

        // 超长日志分片，避免被 logcat 截断
        if (message.length <= MAX_LOG_LENGTH) {
            sink.log(level, tag, message, throwable)
        } else {
            var index = 0
            while (index < message.length) {
                val end = minOf(index + MAX_LOG_LENGTH, message.length)
                sink.log(level, tag, message.substring(index, end), null)
                index = end
            }
            throwable?.let { sink.log(level, tag, "异常堆栈：", it) }
        }
    }

    /**
     * 日志输出抽象，便于单元测试与替换实现。
     */
    interface LogSink {
        fun log(level: Int, tag: String, message: String, throwable: Throwable?)
    }

    private object DefaultLogSink : LogSink {
        override fun log(level: Int, tag: String, message: String, throwable: Throwable?) {
            // 通过反射调用 android.util.Log，使本文件在纯 JVM 单元测试中可直接编译运行
            runCatching {
                val cls = Class.forName("android.util.Log")
                when (level) {
                    VERBOSE -> cls.getMethod("v", String::class.java, String::class.java)
                    DEBUG -> cls.getMethod("d", String::class.java, String::class.java)
                    INFO -> cls.getMethod("i", String::class.java, String::class.java)
                    WARN -> cls.getMethod("w", String::class.java, String::class.java, Throwable::class.java)
                    else -> cls.getMethod("e", String::class.java, String::class.java, Throwable::class.java)
                }.invoke(null, tag, message, throwable)
            }.onFailure {
                // 运行在无 Android 环境（单元测试）时降级到标准输出
                println("[$level] $tag: $message")
                throwable?.printStackTrace()
            }
        }
    }
}

/** 便捷扩展：直接对类实例打日志。 */
inline fun <reified T> T.logTag(): String = T::class.java.simpleName

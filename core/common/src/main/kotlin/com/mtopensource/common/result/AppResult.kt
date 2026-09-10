// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.common.result

/**
 * 项目统一的轻量结果类型。
 *
 * 相比 `kotlin.Result`，本类型要求显式声明错误类型 [E]，
 * 便于上层对不同错误分类处理（例如权限不足 vs 磁盘已满）。
 */
sealed class AppResult<out T, out E> {

    /** 成功。 */
    data class Success<out T>(val data: T) : AppResult<T, Nothing>()

    /** 失败，携带结构化错误。 */
    data class Failure<out E>(val error: E) : AppResult<Nothing, E>()

    val isSuccess: Boolean get() = this is Success

    val isFailure: Boolean get() = this is Failure

    /** 成功时取值，失败时返回 null。 */
    fun getOrNull(): T? = (this as? Success)?.data

    /** 失败时取错误，成功时返回 null。 */
    fun errorOrNull(): E? = (this as? Failure)?.error

    /** 成功时映射内部值。 */
    inline fun <R> map(transform: (T) -> R): AppResult<R, E> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
    }

    /** 失败时映射错误类型。 */
    inline fun <F> mapError(transform: (E) -> F): AppResult<T, F> = when (this) {
        is Success -> this
        is Failure -> Failure(transform(error))
    }

    /** 分支处理。 */
    inline fun <R> fold(onSuccess: (T) -> R, onFailure: (E) -> R): R = when (this) {
        is Success -> onSuccess(data)
        is Failure -> onFailure(error)
    }

    companion object {
        fun <T> success(value: T): AppResult<T, Nothing> = Success(value)

        fun <E> failure(error: E): AppResult<Nothing, E> = Failure(error)
    }
}

/**
 * 文件系统操作的标准错误定义。
 */
sealed class FileError {

    /** 目标不存在。 */
    data class NotFound(val path: String) : FileError()

    /** 权限不足。 */
    data class PermissionDenied(val path: String) : FileError()

    /** 已存在同名文件。 */
    data class AlreadyExists(val path: String) : FileError()

    /** 非法路径（例如把目录移动到自身子目录）。 */
    data class IllegalOperation(val reason: String) : FileError()

    /** 磁盘空间不足。 */
    data class NoSpace(val requiredBytes: Long) : FileError()

    /** 被只读文件系统拒绝。 */
    data class ReadOnly(val path: String) : FileError()

    /** 其他 IO 异常。 */
    data class Io(val message: String, val cause: Throwable? = null) : FileError()

    /** 用户主动取消。 */
    object Cancelled : FileError()

    /** 面向用户的描述。 */
    fun displayMessage(): String = when (this) {
        is NotFound -> "文件不存在：$path"
        is PermissionDenied -> "没有访问权限：$path"
        is AlreadyExists -> "目标已存在：$path"
        is IllegalOperation -> reason
        is NoSpace -> "存储空间不足，需要 ${requiredBytes / 1024 / 1024} MB"
        is ReadOnly -> "文件系统为只读：$path"
        is Io -> message
        Cancelled -> "操作已取消"
    }
}

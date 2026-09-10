// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.plugins.rootsupport

import com.mtopensource.common.result.AppResult
import com.mtopensource.common.result.FileError
import com.mtopensource.common.utils.PathUtils
import com.mtopensource.filemanager.filesystem.FileSystemProvider
import com.mtopensource.filemanager.model.FileItem
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Root 文件系统提供者（scheme = `root`）。
 *
 * 通过 `su` 执行 shell 命令访问普通进程无权读取的路径
 * （`/data/data`、`/system` 等）。
 *
 * 性能取舍：相比直接 File API，每次操作都要 fork 一个 su 进程，
 * 开销明显更高。因此：
 * - 列表操作使用单次 `ls -la` 批量获取全部属性，而不是逐文件 stat
 * - 只读缓存交由上层 UI 负责，本类不做缓存
 */
class RootFileSystemProvider(
    private val shell: RootShell = RootShell(),
) : FileSystemProvider {

    override val scheme: String = "root"

    override val isWritable: Boolean get() = shell.isRootAvailable()

    override val requiresPermission: Boolean get() = true

    /**
     * 列出目录内容。
     *
     * 使用 `ls -la` 解析文本输出，比逐文件调用 stat 快一个数量级。
     */
    override suspend fun list(path: String): AppResult<List<FileItem>, FileError> {
        if (!shell.isRootAvailable()) {
            return AppResult.failure(FileError.PermissionDenied("设备未获得 Root 权限"))
        }

        val result = shell.execute("ls -la '$path'")
        if (!result.isSuccess) {
            return AppResult.failure(classifyError(path, result))
        }

        val items = result.stdout.lineSequence()
            .mapNotNull { parseLsLine(it, path) }
            .filterNot { it.name == "." || it.name == ".." }
            .toList()

        return AppResult.success(items)
    }

    override suspend fun exists(path: String): Boolean =
        shell.execute("test -e '$path' && echo Y || echo N").stdout == "Y"

    override suspend fun isDirectory(path: String): Boolean =
        shell.execute("test -d '$path' && echo Y || echo N").stdout == "Y"

    override suspend fun length(path: String): Long {
        val output = shell.execute("stat -c %s '$path' 2>/dev/null").stdout
        return output.toLongOrNull() ?: 0L
    }

    override suspend fun lastModified(path: String): Long {
        // stat 输出秒级时间戳，转换为毫秒
        val output = shell.execute("stat -c %Y '$path' 2>/dev/null").stdout
        return (output.toLongOrNull() ?: 0L) * 1000
    }

    override suspend fun createFile(path: String): AppResult<Unit, FileError> {
        val result = shell.execute("touch '$path'")
        return if (result.isSuccess) AppResult.success(Unit) else AppResult.failure(classifyError(path, result))
    }

    override suspend fun createDirectory(path: String): AppResult<Unit, FileError> {
        val result = shell.execute("mkdir -p '$path'")
        return if (result.isSuccess) AppResult.success(Unit) else AppResult.failure(classifyError(path, result))
    }

    override suspend fun delete(path: String): AppResult<Unit, FileError> {
        val result = shell.execute("rm -rf '$path'")
        return if (result.isSuccess) AppResult.success(Unit) else AppResult.failure(classifyError(path, result))
    }

    override suspend fun rename(path: String, newName: String): AppResult<Unit, FileError> {
        if (newName.contains('/')) {
            return AppResult.failure(FileError.IllegalOperation("新名称不能包含路径分隔符"))
        }
        val parent = PathUtils.parent(path)
            ?: return AppResult.failure(FileError.IllegalOperation("无法重命名根目录"))
        val target = PathUtils.join(parent, newName)

        val result = shell.execute("mv '$path' '$target'")
        return if (result.isSuccess) AppResult.success(Unit) else AppResult.failure(classifyError(path, result))
    }

    override suspend fun copy(
        source: String,
        target: String,
        onProgress: FileSystemProvider.ProgressCallback?,
    ): AppResult<Unit, FileError> {
        // 目录复制需要 -r；进度回调在这里无法细分到字节级，只在开始与结束各通知一次
        onProgress?.onProgress(0, -1)
        val result = shell.execute("cp -r '$source' '$target'")
        if (result.isSuccess) onProgress?.onProgress(1, 1)
        return if (result.isSuccess) AppResult.success(Unit) else AppResult.failure(classifyError(source, result))
    }

    override suspend fun move(
        source: String,
        target: String,
        onProgress: FileSystemProvider.ProgressCallback?,
    ): AppResult<Unit, FileError> {
        onProgress?.onProgress(0, -1)
        val result = shell.execute("mv '$source' '$target'")
        if (result.isSuccess) onProgress?.onProgress(1, 1)
        return if (result.isSuccess) AppResult.success(Unit) else AppResult.failure(classifyError(source, result))
    }

    override suspend fun openInputStream(path: String): AppResult<InputStream, FileError> {
        val result = shell.readFile(path)
        return if (result.isSuccess) {
            // 通过 su 读取的内容已在内存中，包装为流返回
            AppResult.success(ByteArrayInputStream(result.stdout.toByteArray()))
        } else {
            AppResult.failure(classifyError(path, result))
        }
    }

    override suspend fun openOutputStream(path: String): AppResult<OutputStream, FileError> {
        // Root 写入需要完整内容后一次性通过 base64 传输，
        // 因此这里返回一个「收集全部字节后再写入」的延迟输出流。
        return AppResult.success(RootOutputStream(path, shell))
    }

    /** 修改文件权限（0755 等）。 */
    fun chmod(path: String, mode: String): AppResult<Unit, FileError> {
        val result = shell.chmod(path, mode)
        return if (result.isSuccess) AppResult.success(Unit) else AppResult.failure(classifyError(path, result))
    }

    /** 重新挂载为可读写。 */
    fun remountReadWrite(path: String): AppResult<Unit, FileError> {
        val result = shell.remountReadWrite(path)
        return if (result.isSuccess) AppResult.success(Unit) else AppResult.failure(classifyError(path, result))
    }

    /**
     * 解析 `ls -la` 的一行。
     *
     * 典型输出：
     * `-rw-r--r-- 1 root root   1234 2026-01-02 03:04 file.txt`
     * `lrwxrwxrwx 1 root root     10 2026-01-02 03:04 link -> /target`
     *
     * 声明为 public 是为了让单元测试可以脱离真实设备验证解析逻辑。
     */
    @androidx.annotation.VisibleForTesting
    fun parseLsLine(line: String, parentPath: String): FileItem? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null

        // `total N` 是 ls -l 的汇总行，不是文件
        if (trimmed.startsWith("total ")) return null
        if (trimmed.length < 10) return null

        // 注意：以 '-'/'d'/'l' 开头的行在按空白切分后首元素为空串，
        // 必须先 filter 掉，否则所有列都会错位一位。
        val parts = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.size < 8) return null

        val permissionString = parts[0]
        if (permissionString.length != PERMISSION_STRING_LENGTH) return null

        val isDirectory = permissionString[0] == 'd'
        val isSymlink = permissionString[0] == 'l'

        // 列布局：0=权限 1=硬链接数 2=属主 3=属组 4=大小 5=日期 6=时间 7..=文件名
        val size = parts[4].toLongOrNull() ?: 0L
        val lastModified = parseLsDate(parts.getOrNull(5), parts.getOrNull(6))

        // 符号链接形如 "name -> target"，只取 name。
        // 注意：空白已被合并为单个空格，因此这里按 " -> " 切分而不是按空白列取。
        val rawName = parts.drop(FIRST_NAME_COLUMN).joinToString(" ")
        val name = if (isSymlink && rawName.contains(ARROW_MARKER)) {
            rawName.substringBefore(ARROW_MARKER)
        } else {
            rawName
        }
        if (name.isEmpty()) return null

        val permissions = parsePermissions(permissionString)

        return FileItem(
            path = PathUtils.join(parentPath, name),
            name = name,
            isDirectory = isDirectory,
            size = if (isDirectory) 0L else size,
            lastModified = lastModified,
            isSymlink = isSymlink,
            isHidden = name.startsWith('.'),
            permissions = permissions,
            scheme = scheme,
        )
    }

    /** 把 `rwxr-xr-x` 转回八进制权限位。 */
    private fun parsePermissions(permissionString: String): Int {
        var mode = 0
        val letters = "rwx"
        for (i in 0 until 9) {
            val ch = permissionString.getOrNull(i + 1) ?: continue
            if (ch == letters[i % 3]) {
                mode = mode or (1 shl (8 - i))
            }
        }
        return mode
    }

    /** 解析 `ls` 的日期列，返回 epoch millis；失败返回 0。 */
    private fun parseLsDate(datePart: String?, timePart: String?): Long {
        if (datePart == null || timePart == null) return 0L
        return runCatching {
            val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
            format.parse("$datePart $timePart")?.time ?: 0L
        }.getOrDefault(0L)
    }

    private fun classifyError(path: String, result: ShellResult): FileError {
        val message = result.stderr.ifEmpty { result.stdout }
        return when {
            message.contains("No such file", ignoreCase = true) -> FileError.NotFound(path)
            message.contains("Permission denied", ignoreCase = true) -> FileError.PermissionDenied(path)
            message.contains("Read-only file system", ignoreCase = true) -> FileError.ReadOnly(path)
            message.contains("No space left", ignoreCase = true) -> FileError.NoSpace(0)
            else -> FileError.Io(message.ifEmpty { "Root 命令执行失败（退出码 ${result.exitCode}）" })
        }
    }

    companion object {
        /** `ls -l` 权限串固定为 10 个字符（含首位类型字符）。 */
        private const val PERMISSION_STRING_LENGTH = 10

        /** `ls -l` 输出中文件名的起始列下标。 */
        private const val FIRST_NAME_COLUMN = 7

        /** 符号链接在 `ls -l` 输出中的箭头标记。 */
        private const val ARROW_MARKER = " -> "
    }
}

/**
 * Root 输出流。
 *
 * 收集写入的全部字节，在 [close] 时通过 `su` 一次性落盘。
 * 这种做法牺牲了流式写入的内存效率，换来的是：
 * - 避免为每次 write 都 fork 一个 su 进程
 * - 避免部分写入导致文件损坏
 */
private class RootOutputStream(
    private val path: String,
    private val shell: RootShell,
) : OutputStream() {

    private val buffer = java.io.ByteArrayOutputStream()
    private var closed = false

    override fun write(b: Int) {
        buffer.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        buffer.write(b, off, len)
    }

    override fun flush() {
        // 内容在 close 时统一写入
    }

    override fun close() {
        if (closed) return
        closed = true
        val content = buffer.toByteArray().toString(Charsets.UTF_8)
        val result = shell.writeFile(path, content)
        if (!result.isSuccess) {
            throw java.io.IOException("Root 写入失败：${result.stderr.ifEmpty { result.stdout }}")
        }
    }
}

// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.plugin.host.testfixtures

import com.mtopensource.common.result.AppResult
import com.mtopensource.common.result.FileError
import com.mtopensource.filemanager.filesystem.FileSystemProvider
import com.mtopensource.filemanager.model.FileItem
import com.mtopensource.plugin.api.Plugin
import com.mtopensource.plugin.api.PluginCapability
import com.mtopensource.plugin.api.PluginConfiguration
import com.mtopensource.plugin.api.PluginContext
import com.mtopensource.plugin.api.PluginInfo
import com.mtopensource.plugin.api.PluginMenuItem
import java.io.InputStream
import java.io.OutputStream

/**
 * 插件系统测试夹具。
 *
 * 该类会被 [com.mtopensource.plugin.host.PluginManagerTest] 通过反射实例化，
 * 因此必须保留公开的无参构造函数。
 *
 * 它把每一次与宿主交互的结果记录到公开字段，供测试断言使用。
 */
class TestPlugin : Plugin {

    override val info: PluginInfo = PluginInfo(
        id = "com.mtopensource.test.fixture",
        name = "测试夹具插件",
        version = "1.0.0",
    )

    /** onLoad 是否被调用。 */
    var loadCalled = false

    /** onUnload 是否被调用。 */
    var unloadCalled = false

    /** 是否成功注册了文件系统。 */
    var fileSystemRegisterSucceeded = false

    /** 是否成功注册了菜单项。 */
    var menuRegisterSucceeded = false

    /** 最近一次收到的配置。 */
    var lastConfiguration: PluginConfiguration? = null

    /** 最近一次查询 Root 能力的结果。 */
    var rootCapabilityDetected = false

    /** 保存上下文以便后续触发动作。 */
    private var pluginContext: PluginContext? = null

    override fun onLoad(context: PluginContext) {
        loadCalled = true
        pluginContext = context

        fileSystemRegisterSucceeded = context.registerFileSystem(TestFileSystemProvider())
        menuRegisterSucceeded = context.registerContextMenuItem(
            PluginMenuItem(
                id = "test-action",
                title = "测试动作",
                order = 10,
                onClick = { paths -> context.reportProgress("处理 ${paths.size} 个文件", 50) },
            ),
        )
    }

    override fun onUnload() {
        unloadCalled = true
    }

    override fun onConfigurationChanged(config: PluginConfiguration) {
        lastConfiguration = config
    }

    /** 主动查询宿主的 Root 能力。 */
    fun checkRootCapability() {
        rootCapabilityDetected = pluginContext?.hasHostCapability(PluginCapability.ROOT_FILESYSTEM) == true
    }
}

/**
 * 一个仅用于测试注册流程的哑文件系统实现。
 */
private class TestFileSystemProvider : FileSystemProvider {

    override val scheme: String = "custom"

    override val isWritable: Boolean = true

    override suspend fun list(path: String): AppResult<List<FileItem>, FileError> = AppResult.success(emptyList())

    override suspend fun exists(path: String): Boolean = false

    override suspend fun isDirectory(path: String): Boolean = false

    override suspend fun length(path: String): Long = 0L

    override suspend fun lastModified(path: String): Long = 0L

    override suspend fun createFile(path: String): AppResult<Unit, FileError> = AppResult.success(Unit)

    override suspend fun createDirectory(path: String): AppResult<Unit, FileError> = AppResult.success(Unit)

    override suspend fun delete(path: String): AppResult<Unit, FileError> = AppResult.success(Unit)

    override suspend fun rename(path: String, newName: String): AppResult<Unit, FileError> = AppResult.success(Unit)

    override suspend fun copy(
        source: String,
        target: String,
        onProgress: FileSystemProvider.ProgressCallback?,
    ): AppResult<Unit, FileError> = AppResult.success(Unit)

    override suspend fun move(
        source: String,
        target: String,
        onProgress: FileSystemProvider.ProgressCallback?,
    ): AppResult<Unit, FileError> = AppResult.success(Unit)

    override suspend fun openInputStream(path: String): AppResult<InputStream, FileError> =
        AppResult.failure(FileError.Io("测试实现不支持输入流"))

    override suspend fun openOutputStream(path: String): AppResult<OutputStream, FileError> =
        AppResult.failure(FileError.Io("测试实现不支持输出流"))
}

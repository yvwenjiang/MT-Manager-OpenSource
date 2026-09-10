// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.mtmanager

import android.app.Application
import com.mtopensource.common.utils.Logger
import com.mtopensource.filemanager.filesystem.FileSystemRouter
import com.mtopensource.filemanager.filesystem.LocalFileSystemProvider
import com.mtopensource.mtmanager.data.SettingsRepository
import com.mtopensource.mtmanager.plugin.PluginHostBridge
import com.mtopensource.mtmanager.plugin.PluginRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 应用入口。
 *
 * 承担三件事：
 * 1. 构建全局单例（服务定位器），避免为这个规模的项目引入 DI 框架
 * 2. 装配文件系统路由表（本地 + 插件提供的 scheme）
 * 3. 在后台线程完成插件发现与加载，不阻塞冷启动
 */
class MtManagerApplication : Application() {

    private companion object {
        const val TAG = "MtManagerApp"
    }

    /**
     * 应用级协程作用域。
     *
     * 使用 [SupervisorJob] 保证单个子任务失败不会连带取消整个应用作用域。
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 全局服务容器，延迟初始化以避免拖慢冷启动。 */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        // Release 构建下调高日志门槛，避免调试日志影响性能
        Logger.minLevel = if (BuildConfig.DEBUG) 2 else 4

        container = AppContainer(this)
        Logger.i(TAG, "应用启动完成，版本 ${BuildConfig.VERSION_NAME}")

        applicationScope.launch {
            container.pluginRuntime.initialize()
        }
    }
}

/**
 * 轻量服务定位器。
 *
 * 显式持有各依赖并保证初始化顺序，比注解式 DI 更易读，
 * 也便于在测试中逐个替换。
 */
class AppContainer(private val application: Application) {

    /** 用户设置（主题、排序方式等）。 */
    val settingsRepository: SettingsRepository = SettingsRepository(application)

    /** 全局文件系统路由表：先注册本地实现，插件在运行时追加。 */
    val fileSystemRouter: FileSystemRouter = FileSystemRouter(
        listOf(LocalFileSystemProvider()),
    )

    /** 宿主与插件之间的桥接实现。 */
    val pluginHostBridge: PluginHostBridge = PluginHostBridge(
        application = application,
        router = fileSystemRouter,
        settingsRepository = settingsRepository,
    )

    /** 插件运行时（发现、加载、卸载）。 */
    val pluginRuntime: PluginRuntime = PluginRuntime(
        application = application,
        bridge = pluginHostBridge,
    )
}

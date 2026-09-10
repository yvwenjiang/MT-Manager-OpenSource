// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.mtmanager

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mtopensource.filemanager.operation.FileOperator
import com.mtopensource.mtmanager.data.ThemeMode as SettingsThemeMode
import com.mtopensource.mtmanager.ui.files.FilesScreen
import com.mtopensource.mtmanager.ui.files.FilesViewModel
import com.mtopensource.mtmanager.ui.plugins.PluginsScreen
import com.mtopensource.mtmanager.ui.plugins.PluginsViewModel
import com.mtopensource.mtmanager.ui.settings.SettingsScreen
import com.mtopensource.mtmanager.ui.settings.SettingsViewModel
import com.mtopensource.ui.theme.MtManagerTheme
import com.mtopensource.ui.theme.ThemeMode as UiThemeMode

/**
 * 应用唯一 Activity。
 *
 * 采用单 Activity + Compose 结构：底部导航承载三个顶层页面
 * （文件 / 插件 / 设置），页面内部各自维护导航栈
 * （例如文件浏览器的目录层级）。
 *
 * 之所以不引入 Navigation-Compose：本项目的页面层级很浅，
 * 用一个枚举表示当前页即可，还能少一个依赖及其版本兼容负担。
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as MtManagerApplication).container
        // 把持久化的主题偏好写回 AppCompat，保证 Activity 重建后保持一致
        container.settingsRepository.applyTheme()

        setContent {
            val settingsThemeMode by container.settingsRepository.themeMode.collectAsStateWithLifecycle()
            MtManagerTheme(themeMode = settingsThemeMode.toUiThemeMode()) {
                MtManagerApp(container)
            }
        }
    }
}

/**
 * 将「设置层的主题模式」转换为「UI 层的主题模式」。
 *
 * 两个枚举刻意分开：设置层的枚举带 AppCompat 夜间模式映射，
 * UI 层只关心亮度策略，不依赖 AppCompat，因而可被插件复用。
 */
private fun SettingsThemeMode.toUiThemeMode(): UiThemeMode = when (this) {
    SettingsThemeMode.SYSTEM -> UiThemeMode.SYSTEM
    SettingsThemeMode.LIGHT -> UiThemeMode.LIGHT
    SettingsThemeMode.DARK -> UiThemeMode.DARK
}

/** 底部导航的三个顶层页面。 */
private enum class TopDestination(
    val label: String,
    val icon: ImageVector,
) {
    FILES("文件", Icons.Default.Folder),
    PLUGINS("插件", Icons.Default.Extension),
    SETTINGS("设置", Icons.Default.Settings),
}

@Composable
private fun MtManagerApp(container: AppContainer) {
    val context = LocalContext.current
    val application = context.applicationContext as MtManagerApplication
    var destination by rememberSaveable { mutableStateOf(TopDestination.FILES) }

    // 文件操作引擎需要应用级作用域，随容器创建一次并复用
    val fileOperator = remember(container) {
        FileOperator(container.fileSystemRouter, application.applicationScope)
    }

    val filesViewModel: FilesViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                FilesViewModel(
                    router = container.fileSystemRouter,
                    operator = fileOperator,
                    settings = container.settingsRepository,
                )
            }
        },
    )
    val pluginsViewModel: PluginsViewModel = viewModel(
        factory = viewModelFactory { initializer { PluginsViewModel(container.pluginRuntime) } },
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SettingsViewModel(container.settingsRepository, container.pluginRuntime) }
        },
    )

    // 一次性提示：ViewModel 通过 SharedFlow 推送文案，这里统一以 Toast 呈现
    LaunchedEffect(filesViewModel) {
        filesViewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopDestination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (destination) {
                TopDestination.FILES -> FilesScreen(
                    viewModel = filesViewModel,
                    onOpenFile = { item ->
                        // 目录/文件的分发由 ViewModel 负责，避免 UI 重复判断
                        filesViewModel.open(item) { file ->
                            Toast.makeText(
                                context,
                                "打开文件：${file.name}",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )

                TopDestination.PLUGINS -> PluginsScreen(viewModel = pluginsViewModel)

                TopDestination.SETTINGS -> SettingsScreen(viewModel = settingsViewModel)
            }
        }
    }
}

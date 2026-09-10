// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.mtmanager.ui.plugins

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtopensource.plugin.bridge.PluginRecord
import com.mtopensource.plugin.bridge.PluginState
import com.mtopensource.mtmanager.plugin.PluginRuntime
import com.mtopensource.mtmanager.plugin.isBuiltin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 插件管理界面状态。
 */
data class PluginsUiState(
    val plugins: List<PluginRecord> = emptyList(),
    val isLoading: Boolean = true,
    /** 是否显示内置插件（默认隐藏，避免与用户安装的插件混淆）。 */
    val showBuiltin: Boolean = false,
) {

    /** 按当前过滤条件展示的插件列表。 */
    val visiblePlugins: List<PluginRecord>
        get() = plugins.filter { showBuiltin || !it.isBuiltin }

    val activeCount: Int get() = plugins.count { it.state == PluginState.ACTIVE }

    val hasAnyPlugin: Boolean get() = visiblePlugins.isNotEmpty()
}

/**
 * 插件管理 ViewModel。
 *
 * 直接订阅 [PluginRuntime] 的插件状态流，插件加载完成会自动反映到界面，
 * 不需要轮询或手动刷新。
 */
class PluginsViewModel(
    private val runtime: PluginRuntime,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PluginsUiState())
    val uiState: StateFlow<PluginsUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            runtime.plugins.collect { records ->
                _uiState.update { it.copy(plugins = records, isLoading = false) }
            }
        }
    }

    /** 重新扫描外部插件目录。 */
    fun rescan() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runtime.rescan()
            _uiState.update { it.copy(isLoading = false) }
            _messages.tryEmit("扫描完成")
        }
    }

    /** 启用/停用插件。 */
    fun togglePlugin(record: PluginRecord) {
        viewModelScope.launch {
            val pluginId = record.manifest.id
            if (record.state == PluginState.ACTIVE) {
                runtime.unload(pluginId)
                _messages.tryEmit("已停用：${record.manifest.name}")
            } else {
                // 内置插件无法从磁盘路径重新加载，需提示用户重启应用
                _messages.tryEmit("请重启应用以重新加载：${record.manifest.name}")
            }
        }
    }

    /** 切换是否展示内置插件。 */
    fun toggleShowBuiltin() {
        _uiState.update { it.copy(showBuiltin = !it.showBuiltin) }
    }
}

// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.mtmanager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtopensource.filemanager.model.SortBy
import com.mtopensource.mtmanager.data.SettingsRepository
import com.mtopensource.mtmanager.data.ThemeMode
import com.mtopensource.mtmanager.plugin.PluginRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 设置界面状态。
 */
data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val sortBy: SortBy = SortBy.NAME,
    val ascending: Boolean = true,
    val directoriesFirst: Boolean = true,
    val showHiddenFiles: Boolean = false,
)

/**
 * 设置 ViewModel。
 *
 * 所有修改立即持久化（[SettingsRepository] 内部完成），
 * 因此这里不提供「保存」按钮，符合移动端设置页的交互习惯。
 */
class SettingsViewModel(
    private val settings: SettingsRepository,
    private val runtime: PluginRuntime,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settings.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            settings.sortConfig.collect { config ->
                _uiState.update {
                    it.copy(
                        sortBy = config.sortBy,
                        ascending = config.ascending,
                        directoriesFirst = config.directoriesFirst,
                    )
                }
            }
        }
        viewModelScope.launch {
            settings.showHiddenFiles.collect { show ->
                _uiState.update { it.copy(showHiddenFiles = show) }
            }
        }
    }

    /** 切换主题模式。 */
    fun setThemeMode(mode: ThemeMode) {
        settings.setThemeMode(mode)
        notifyPlugins()
    }

    /** 切换目录优先。 */
    fun setDirectoriesFirst(enabled: Boolean) {
        settings.setSortConfig(settings.sortConfig.value.copy(directoriesFirst = enabled))
    }

    /** 切换默认显示隐藏文件。 */
    fun setShowHiddenFiles(enabled: Boolean) {
        settings.setShowHiddenFiles(enabled)
    }

    /**
     * 把配置变化通知给已加载的插件。
     *
     * 插件可能根据主题或语言调整自身行为，因此每次配置变更都要广播。
     */
    private fun notifyPlugins() {
        viewModelScope.launch { runtime.notifyConfigurationChanged() }
    }
}

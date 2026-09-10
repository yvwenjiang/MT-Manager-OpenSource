// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.mtmanager.data

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import com.mtopensource.filemanager.model.FileSortConfig
import com.mtopensource.filemanager.model.SortBy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 主题模式。
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    /** 映射到 AppCompat 的夜间模式常量。 */
    val nightMode: Int
        get() = when (this) {
            SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }

    companion object {
        fun fromName(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/**
 * 用户设置仓库。
 *
 * 以 SharedPreferences 为持久化后端，同时用 [StateFlow] 暴露响应式状态，
 * 使 Compose 界面与插件配置通知都能直接订阅变化。
 *
 * 之所以不用 DataStore：本项目需要插件侧同步读取配置（`PluginConfiguration`），
 * 而 DataStore 的挂起式 API 在插件回调中会增加不必要的复杂度。
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(ThemeMode.fromName(prefs.getString(KEY_THEME, null)))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _sortConfig = MutableStateFlow(loadSortConfig())
    val sortConfig: StateFlow<FileSortConfig> = _sortConfig.asStateFlow()

    private val _showHiddenFiles = MutableStateFlow(prefs.getBoolean(KEY_SHOW_HIDDEN, false))
    val showHiddenFiles: StateFlow<Boolean> = _showHiddenFiles.asStateFlow()

    /** 记录最后一次浏览的目录，用于冷启动后恢复现场。 */
    var lastPath: String
        get() = prefs.getString(KEY_LAST_PATH, null) ?: DEFAULT_PATH
        set(value) = prefs.edit().putString(KEY_LAST_PATH, value).apply()

    /** 更新主题模式并立即应用。 */
    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }

    /** 更新排序配置。 */
    fun setSortConfig(config: FileSortConfig) {
        _sortConfig.value = config
        prefs.edit()
            .putString(KEY_SORT_BY, config.sortBy.name)
            .putBoolean(KEY_SORT_ASCENDING, config.ascending)
            .putBoolean(KEY_DIRECTORIES_FIRST, config.directoriesFirst)
            .apply()
    }

    /** 切换「显示隐藏文件」。 */
    fun setShowHiddenFiles(show: Boolean) {
        _showHiddenFiles.value = show
        prefs.edit().putBoolean(KEY_SHOW_HIDDEN, show).apply()
    }

    /**
     * 应用启动时调用，把已保存的主题写回 AppCompat。
     */
    fun applyTheme() {
        AppCompatDelegate.setDefaultNightMode(_themeMode.value.nightMode)
    }

    private fun loadSortConfig(): FileSortConfig {
        val sortBy = prefs.getString(KEY_SORT_BY, null)
            ?.let { name -> SortBy.entries.firstOrNull { it.name == name } }
            ?: SortBy.NAME
        return FileSortConfig(
            sortBy = sortBy,
            ascending = prefs.getBoolean(KEY_SORT_ASCENDING, true),
            directoriesFirst = prefs.getBoolean(KEY_DIRECTORIES_FIRST, true),
        )
    }

    companion object {
        private const val PREF_NAME = "mt_manager_settings"

        private const val KEY_THEME = "theme_mode"
        private const val KEY_SORT_BY = "sort_by"
        private const val KEY_SORT_ASCENDING = "sort_ascending"
        private const val KEY_DIRECTORIES_FIRST = "directories_first"
        private const val KEY_SHOW_HIDDEN = "show_hidden_files"
        private const val KEY_LAST_PATH = "last_path"

        /** 无历史记录时的默认起始目录。 */
        const val DEFAULT_PATH = "/sdcard"
    }
}

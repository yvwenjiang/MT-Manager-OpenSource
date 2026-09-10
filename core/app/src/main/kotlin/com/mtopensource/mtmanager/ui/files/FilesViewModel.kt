// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.mtmanager.ui.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtopensource.common.result.AppResult
import com.mtopensource.common.result.FileError
import com.mtopensource.common.utils.PathUtils
import com.mtopensource.filemanager.filesystem.FileSystemRouter
import com.mtopensource.filemanager.model.FileItem
import com.mtopensource.filemanager.model.FileSortConfig
import com.mtopensource.filemanager.model.SortBy
import com.mtopensource.filemanager.operation.FileOperation
import com.mtopensource.filemanager.operation.FileOperationType
import com.mtopensource.filemanager.operation.FileOperator
import com.mtopensource.filemanager.operation.OperationResult
import com.mtopensource.mtmanager.data.SettingsRepository
import com.mtopensource.mtmanager.ui.displayMessage
import com.mtopensource.plugin.api.PluginMenuItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 文件列表界面状态。
 */
data class FilesUiState(
    /** 当前目录路径。 */
    val currentPath: String = SettingsRepository.DEFAULT_PATH,
    /** 当前目录下的条目（已排序、已过滤隐藏文件）。 */
    val items: List<FileItem> = emptyList(),
    /** 是否正在加载。 */
    val isLoading: Boolean = false,
    /** 加载错误信息。 */
    val error: String? = null,
    /** 当前处于多选模式的选中路径集合。 */
    val selectedPaths: Set<String> = emptySet(),
    /** 是否处于多选模式。 */
    val isSelectionMode: Boolean = false,
    /** 排序配置。 */
    val sortConfig: FileSortConfig = FileSortConfig(),
) {

    /** 是否能返回上级目录（根目录无法再上级）。 */
    val canGoUp: Boolean get() = PathUtils.parent(currentPath) != null

    /** 工具栏标题：取当前目录名，根目录时回退为完整路径。 */
    val title: String get() = PathUtils.fileName(currentPath).ifEmpty { currentPath }

    /** 当前选中的条目数量。 */
    val selectionCount: Int get() = selectedPaths.size
}

/**
 * 文件列表 ViewModel。
 *
 * 与 UI 解耦：只依赖 [FileSystemRouter] 与 [FileOperator]，
 * 因此可在纯 JVM 测试中驱动完整的浏览与操作流程。
 */
class FilesViewModel(
    private val router: FileSystemRouter,
    private val operator: FileOperator,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FilesUiState())
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()

    /** 一次性提示消息（Toast 文案），UI 收集后展示。 */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** 插件注入的上下文菜单项。 */
    private var pluginMenus: List<PluginMenuItem> = emptyList()

    /** 当前加载任务，切换目录时用于取消上一次未完成的加载。 */
    private var loadJob: Job? = null

    init {
        observeSettings()
        navigateTo(settings.lastPath)
    }

    // ---------------------------------------------------------------------
    // 目录导航
    // ---------------------------------------------------------------------

    /** 进入指定目录并加载内容。 */
    fun navigateTo(path: String) {
        // 取消上一次加载，避免快速连续切换目录时旧结果覆盖新结果
        loadJob?.cancel()

        _uiState.update {
            it.copy(
                currentPath = path,
                isLoading = true,
                error = null,
                selectedPaths = emptySet(),
                isSelectionMode = false,
            )
        }
        settings.lastPath = path

        loadJob = viewModelScope.launch {
            when (val result = router.resolve(path).list(path)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(items = result.data, isLoading = false, error = null) }
                    applySort()
                }

                is AppResult.Failure -> _uiState.update {
                    it.copy(items = emptyList(), isLoading = false, error = result.error.displayMessage())
                }
            }
        }
    }


    /** 刷新当前目录。 */
    fun refresh() = navigateTo(_uiState.value.currentPath)

    /** 返回上级目录；已在根目录时不做任何事。 */
    fun navigateUp() {
        val parent = PathUtils.parent(_uiState.value.currentPath) ?: return
        navigateTo(parent)
    }

    /** 打开条目：目录进入，文件交给调用方处理。 */
    fun open(item: FileItem, onOpenFile: (FileItem) -> Unit) {
        if (item.isDirectory) navigateTo(item.path) else onOpenFile(item)
    }

    // ---------------------------------------------------------------------
    // 选择
    // ---------------------------------------------------------------------

    /** 切换条目选中状态。 */
    fun toggleSelection(path: String) {
        _uiState.update { state ->
            val selected = state.selectedPaths.toMutableSet()
            if (!selected.remove(path)) selected += path
            state.copy(
                selectedPaths = selected,
                // 选中集合清空时自动退出多选模式，符合常见交互习惯
                isSelectionMode = selected.isNotEmpty(),
            )
        }
    }

    /** 进入多选模式并选中首个条目。 */
    fun enterSelectionMode(path: String) {
        _uiState.update { it.copy(isSelectionMode = true, selectedPaths = setOf(path)) }
    }

    /** 退出多选模式。 */
    fun clearSelection() {
        _uiState.update { it.copy(isSelectionMode = false, selectedPaths = emptySet()) }
    }

    /** 全选当前列表。 */
    fun selectAll() {
        _uiState.update {
            it.copy(isSelectionMode = true, selectedPaths = it.items.map { item -> item.path }.toSet())
        }
    }

    /** 选中条目对应的 [FileItem] 列表。 */
    private fun selectedItems(): List<FileItem> {
        val selected = _uiState.value.selectedPaths
        return _uiState.value.items.filter { it.path in selected }
    }

    // ---------------------------------------------------------------------
    // 文件操作
    // ---------------------------------------------------------------------

    /** 删除选中的条目。 */
    fun deleteSelected() {
        val targets = selectedItems()
        if (targets.isEmpty()) {
            _messages.tryEmit("未选择任何文件")
            return
        }
        execute(FileOperation(type = FileOperationType.DELETE, sources = targets.map { it.path }))
    }

    /** 重命名单个条目。 */
    fun rename(item: FileItem, newName: String) {
        if (newName.isBlank()) {
            _messages.tryEmit("名称不能为空")
            return
        }
        if (newName.contains('/')) {
            _messages.tryEmit("名称不能包含 /")
            return
        }
        execute(
            FileOperation(
                type = FileOperationType.RENAME,
                sources = listOf(item.path),
                newName = newName,
            ),
        )
    }

    /** 在当前目录新建文件夹。 */
    fun createFolder(name: String) {
        if (name.isBlank()) {
            _messages.tryEmit("名称不能为空")
            return
        }
        execute(
            FileOperation(
                type = FileOperationType.CREATE_DIRECTORY,
                sources = listOf(PathUtils.join(_uiState.value.currentPath, name)),
            ),
        )
    }

    /** 在当前目录新建空文件。 */
    fun createFile(name: String) {
        if (name.isBlank()) {
            _messages.tryEmit("名称不能为空")
            return
        }
        execute(
            FileOperation(
                type = FileOperationType.CREATE_FILE,
                sources = listOf(PathUtils.join(_uiState.value.currentPath, name)),
            ),
        )
    }

    /** 把选中条目复制到目标目录。 */
    fun copySelectedTo(targetDirectory: String) {
        val targets = selectedItems()
        if (targets.isEmpty()) {
            _messages.tryEmit("未选择任何文件")
            return
        }
        execute(
            FileOperation(
                type = FileOperationType.COPY,
                sources = targets.map { it.path },
                target = targetDirectory,
            ),
        )
    }

    /** 把选中条目移动到目标目录。 */
    fun moveSelectedTo(targetDirectory: String) {
        val targets = selectedItems()
        if (targets.isEmpty()) {
            _messages.tryEmit("未选择任何文件")
            return
        }
        execute(
            FileOperation(
                type = FileOperationType.MOVE,
                sources = targets.map { it.path },
                target = targetDirectory,
            ),
        )
    }

    /**
     * 提交文件操作并统一处理结果。
     *
     * 操作完成后总是刷新列表，保证 UI 与实际文件系统一致。
     */
    private fun execute(operation: FileOperation) {
        operator.execute(operation) { result ->
            _messages.tryEmit(result.displayMessage())
            clearSelection()
            viewModelScope.launch { refresh() }
        }
    }

    // ---------------------------------------------------------------------
    // 排序与显示
    // ---------------------------------------------------------------------

    /** 更新排序方式；重复点击同一字段时切换升降序。 */
    fun setSortBy(sortBy: SortBy) {
        val current = _uiState.value.sortConfig
        val next = if (current.sortBy == sortBy) {
            current.copy(ascending = !current.ascending)
        } else {
            current.copy(sortBy = sortBy, ascending = true)
        }
        settings.setSortConfig(next)
    }

    /** 切换「显示隐藏文件」。 */
    fun toggleShowHidden() {
        settings.setShowHiddenFiles(!settings.showHiddenFiles.value)
    }

    // ---------------------------------------------------------------------
    // 插件集成
    // ---------------------------------------------------------------------

    /** 更新插件注入的菜单项（插件加载/卸载后由 Activity 调用）。 */
    fun updatePluginMenus(menus: List<PluginMenuItem>) {
        pluginMenus = menus
    }

    /** 查询某个条目可用的插件菜单项。 */
    fun applicablePluginMenus(item: FileItem): List<PluginMenuItem> = pluginMenus.filter { menu ->
        val extensionMatches = menu.applicableExtensions.isEmpty() ||
            item.extension.lowercase() in menu.applicableExtensions.map { ext -> ext.lowercase() }
        val directoryMatches = !menu.directoriesOnly || item.isDirectory
        extensionMatches && directoryMatches
    }

    /** 是否安装了任何插件菜单。 */
    val hasPluginMenus: Boolean get() = pluginMenus.isNotEmpty()

    /** 执行插件菜单项，参数为待处理的路径列表。 */
    fun invokePluginMenu(menu: PluginMenuItem, paths: List<String>) {
        // 插件代码不可信，异常必须在此拦截，不能让宿主崩溃
        runCatching { menu.onClick(paths) }
            .onFailure { _messages.tryEmit("插件操作失败：${it.message ?: it.javaClass.simpleName}") }
    }

    // ---------------------------------------------------------------------
    // 内部
    // ---------------------------------------------------------------------

    private fun observeSettings() {
        viewModelScope.launch {
            settings.sortConfig.collect { config ->
                _uiState.update { it.copy(sortConfig = config.copy(showHidden = settings.showHiddenFiles.value)) }
                applySort()
            }
        }
        viewModelScope.launch {
            settings.showHiddenFiles.collect { show ->
                _uiState.update { it.copy(sortConfig = it.sortConfig.copy(showHidden = show)) }
                applySort()
            }
        }
    }

    /** 按当前排序配置重新排列列表。 */
    private fun applySort() {
        _uiState.update { state -> state.copy(items = state.sortConfig.apply(state.items)) }
    }

    override fun onCleared() {
        loadJob?.cancel()
        super.onCleared()
    }
}

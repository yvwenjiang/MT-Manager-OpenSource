# 核心代码示例

> 本文档展示 MT管理器开源版 的关键代码实现，供开发者参考。

## 目录

1. [DualPaneLayout - 双窗口布局](#dualpanelayout)
2. [FileListPane - 文件列表面板](#filelistpane)
3. [DragDropController - 拖拽控制器](#dragdropcontroller)
4. [FileSystemProvider - 文件系统接口](#filesystemprovider)
5. [PluginHost - 插件宿主](#pluginhost)

---

## DualPaneLayout

双窗口布局的核心实现，管理左右两个面板的尺寸和交互。

```kotlin
// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.ui.dualpane

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.mtopensource.common.utils.Logger

/**
 * 双窗口布局容器
 * 
 * 管理左右两个文件列表面板，支持：
 * - 动态调整面板比例（拖拽分隔线）
 * - 跨窗口拖拽文件
 * - 手势识别与分发
 */
class DualPaneLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "DualPaneLayout"
        const val DEFAULT_RATIO = 0.5f
        const val MIN_RATIO = 0.2f
        const val MAX_RATIO = 0.8f
        const val DIVIDER_WIDTH_DP = 8
    }

    private val dividerWidth: Int
    private val leftPane: FileListPane
    private val rightPane: FileListPane
    private val divider: View

    private val dragDropController: DragDropController
    private val gestureHandler: GestureHandler

    /**
     * 左右面板比例 (0.2 - 0.8)
     */
    var paneRatio: Float = DEFAULT_RATIO
        set(value) {
            field = value.coerceIn(MIN_RATIO, MAX_RATIO)
            requestLayout()
        }

    /**
     * 当前活跃的面板
     */
    var activePane: FileListPane = leftPane
        private set

    init {
        dividerWidth = (DIVIDER_WIDTH_DP * resources.displayMetrics.density).toInt()

        // 创建左面板
        leftPane = FileListPane(context).apply {
            id = View.generateViewId()
            tag = "left"
        }

        // 创建分隔线
        divider = View(context).apply {
            id = View.generateViewId()
            setBackgroundColor(context.getColor(android.R.color.darker_gray))
        }

        // 创建右面板
        rightPane = FileListPane(context).apply {
            id = View.generateViewId()
            tag = "right"
        }

        addView(leftPane)
        addView(divider)
        addView(rightPane)

        // 初始化控制器
        dragDropController = DragDropController(this, leftPane, rightPane)
        gestureHandler = GestureHandler(this, leftPane, rightPane, divider)

        // 设置面板激活状态监听
        leftPane.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) activePane = leftPane
        }
        rightPane.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) activePane = rightPane
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left
        val height = bottom - top
        val leftWidth = (width * paneRatio).toInt()

        leftPane.layout(0, 0, leftWidth, height)
        divider.layout(leftWidth, 0, leftWidth + dividerWidth, height)
        rightPane.layout(leftWidth + dividerWidth, 0, width, height)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return gestureHandler.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureHandler.onTouchEvent(event)
    }

    /**
     * 设置左面板路径
     */
    fun setLeftPath(path: String) {
        leftPane.navigateTo(path)
    }

    /**
     * 设置右面板路径
     */
    fun setRightPath(path: String) {
        rightPane.navigateTo(path)
    }

    /**
     * 获取对面板
     */
    fun getOppositePane(pane: FileListPane): FileListPane {
        return if (pane === leftPane) rightPane else leftPane
    }

    /**
     * 交换两个面板的内容
     */
    fun swapPanes() {
        val leftPath = leftPane.currentPath
        val rightPath = rightPane.currentPath
        leftPane.navigateTo(rightPath)
        rightPane.navigateTo(leftPath)
    }

    /**
     * 同步两个面板到同一目录
     */
    fun syncPanes() {
        val targetPath = activePane.currentPath
        getOppositePane(activePane).navigateTo(targetPath)
    }
}
```

---

## FileListPane

文件列表面板，显示单个目录的内容。

```kotlin
// SPDX-License-Identifier: Apache-2.0

package com.mtopensource.ui.dualpane

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleCoroutineScope
nimport androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mtopensource.common.utils.Logger
import com.mtopensource.filemanager.model.FileItem
import com.mtopensource.filemanager.FileSystemProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 文件列表面板
 * 
 * 显示单个目录的文件列表，支持：
 * - 多种视图模式（列表/网格/详细）
 * - 文件选择和多选
 * - 排序和筛选
 * - 路径导航
 */
class FileListPane @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "FileListPane"
    }

    private val recyclerView: RecyclerView
    private val pathBar: PathBarView
    private val adapter: FileListAdapter
    private val selectionTracker: SelectionTracker

    private var fileSystem: FileSystemProvider? = null
    private val scope: LifecycleCoroutineScope?
        get() = findViewTreeLifecycleOwner()?.lifecycleScope

    private val _currentPath = MutableStateFlow("")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _selectedFiles = MutableStateFlow<Set<String>>(emptySet())
    val selectedFiles: StateFlow<Set<String>> = _selectedFiles.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    var onItemClickListener: ((FileItem) -> Unit)? = null
    var onItemLongClickListener: ((FileItem) -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.file_list_pane, this, true)

        pathBar = findViewById(R.id.path_bar)
        recyclerView = findViewById(R.id.recycler_view)

        adapter = FileListAdapter().apply {
            onItemClick = { item ->
                if (item.isDirectory) {
                    navigateTo(item.path)
                } else {
                    onItemClickListener?.invoke(item)
                }
            }
            onItemLongClick = { item ->
                onItemLongClickListener?.invoke(item)
            }
        }

        selectionTracker = SelectionTracker(adapter) { selected ->
            _selectedFiles.value = selected
        }

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
    }

    /**
     * 设置文件系统提供者
     */
    fun setFileSystem(fileSystem: FileSystemProvider) {
        this.fileSystem = fileSystem
    }

    /**
     * 导航到指定路径
     */
    fun navigateTo(path: String) {
        val fs = fileSystem ?: run {
            Logger.w(TAG, "FileSystem not set")
            return
        }

        _currentPath.value = path
        pathBar.setPath(path)
        _isLoading.value = true

        scope?.launch {
            try {
                val uri = Uri.parse(path)
                val files = fs.list(uri)
                adapter.submitList(files.sortedWith(compareByDescending<FileItem> { it.isDirectory }
                    .thenBy { it.name.lowercase() }))
                _isLoading.value = false
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to load directory: $path", e)
                _isLoading.value = false
            }
        }
    }

    /**
     * 刷新当前目录
     */
    fun refresh() {
        navigateTo(_currentPath.value)
    }

    /**
     * 获取当前选中的文件
     */
    fun getSelectedItems(): List<FileItem> {
        return adapter.currentList.filter { 
            _selectedFiles.value.contains(it.path) 
        }
    }

    /**
     * 清除选择
     */
    fun clearSelection() {
        selectionTracker.clearSelection()
        _selectedFiles.value = emptySet()
    }

    /**
     * 全选/取消全选
     */
    fun toggleSelectAll() {
        if (_selectedFiles.value.size == adapter.currentList.size) {
            clearSelection()
        } else {
            val allPaths = adapter.currentList.map { it.path }.toSet()
            selectionTracker.selectAll(allPaths)
            _selectedFiles.value = allPaths
        }
    }
}
```

---

## DragDropController

跨窗口拖拽控制器，处理文件在左右面板之间的拖拽操作。

```kotlin
// SPDX-License-Identifier: Apache-2.0

package com.mtopensource.ui.dragdrop

import android.content.ClipData
import android.view.DragEvent
import android.view.View
import com.mtopensource.common.utils.Logger
import com.mtopensource.filemanager.model.FileItem
import com.mtopensource.ui.dualpane.DualPaneLayout
import com.mtopensource.ui.dualpane.FileListPane

/**
 * 跨窗口拖拽控制器
 * 
 * 处理文件在左右面板之间的拖拽操作，支持：
 * - 复制（跨分区或按住Ctrl）
 * - 移动（同分区）
 * - 解压（拖到压缩包外）
 * - 压缩（拖到压缩包内）
 */
class DragDropController(
    private val layout: DualPaneLayout,
    private val leftPane: FileListPane,
    private val rightPane: FileListPane
) {

    companion object {
        private const val TAG = "DragDropController"
        private const val MIME_TYPE_FILES = "application/vnd.mtopensource.files"
    }

    /**
     * 拖拽操作类型
     */
    enum class DropOperation {
        COPY,      // 复制
        MOVE,      // 移动
        EXTRACT,   // 解压
        ARCHIVE    // 压缩
    }

    /**
     * 开始拖拽
     */
    fun startDrag(pane: FileListPane, items: List<FileItem>) {
        if (items.isEmpty()) return

        Logger.d(TAG, "开始拖拽 ${items.size} 个文件")

        val dragData = ClipData.newPlainText("files", items.toJson())
        val shadow = createDragShadow(items)
        val localState = DragLocalState(pane, items)

        pane.startDragAndDrop(dragData, shadow, localState, 
            View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_OPAQUE)
    }

    /**
     * 处理拖拽事件
     */
    fun handleDragEvent(targetPane: FileListPane, event: DragEvent): Boolean {
        return when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                // 高亮可放置区域
                targetPane.alpha = 0.8f
                true
            }
            DragEvent.ACTION_DRAG_ENTERED -> {
                targetPane.alpha = 0.6f
                true
            }
            DragEvent.ACTION_DRAG_LOCATION -> {
                // 更新放置位置指示器
                updateDropIndicator(targetPane, event)
                true
            }
            DragEvent.ACTION_DRAG_EXITED -> {
                targetPane.alpha = 0.8f
                true
            }
            DragEvent.ACTION_DROP -> {
                targetPane.alpha = 1.0f
                handleDrop(targetPane, event)
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                targetPane.alpha = 1.0f
                true
            }
            else -> false
        }
    }

    private fun handleDrop(targetPane: FileListPane, event: DragEvent): Boolean {
        val localState = event.localState as? DragLocalState ?: return false
        val sourcePane = localState.sourcePane
        val items = localState.items

        // 确定操作类型
        val operation = determineOperation(sourcePane, targetPane, items)
        val targetPath = targetPane.currentPath

        Logger.i(TAG, "执行拖拽操作: $operation, 目标: $targetPath")

        // 执行操作
        when (operation) {
            DropOperation.COPY -> executeCopy(items, targetPath)
            DropOperation.MOVE -> executeMove(items, targetPath)
            DropOperation.EXTRACT -> executeExtract(items, targetPath)
            DropOperation.ARCHIVE -> executeArchive(items, targetPath)
        }

        return true
    }

    private fun determineOperation(
        sourcePane: FileListPane,
        targetPane: FileListPane,
        items: List<FileItem>
    ): DropOperation {
        val sourcePath = sourcePane.currentPath
        val targetPath = targetPane.currentPath

        return when {
            // 拖到压缩包内 -> 压缩
            targetPath.endsWith(".zip") || targetPath.endsWith(".rar") -> DropOperation.ARCHIVE
            // 从压缩包拖出 -> 解压
            sourcePath.endsWith(".zip") || sourcePath.endsWith(".rar") -> DropOperation.EXTRACT
            // 同分区 -> 移动
            isSamePartition(sourcePath, targetPath) -> DropOperation.MOVE
            // 跨分区 -> 复制
            else -> DropOperation.COPY
        }
    }

    private fun isSamePartition(path1: String, path2: String): Boolean {
        // 简化实现：检查根路径是否相同
        return path1.substringBefore("/", "") == path2.substringBefore("/", "")
    }

    private fun executeCopy(items: List<FileItem>, targetPath: String) {
        // 调用文件管理器的复制操作
        Logger.d(TAG, "复制 ${items.size} 个文件到 $targetPath")
    }

    private fun executeMove(items: List<FileItem>, targetPath: String) {
        Logger.d(TAG, "移动 ${items.size} 个文件到 $targetPath")
    }

    private fun executeExtract(items: List<FileItem>, targetPath: String) {
        Logger.d(TAG, "解压 ${items.size} 个文件到 $targetPath")
    }

    private fun executeArchive(items: List<FileItem>, targetPath: String) {
        Logger.d(TAG, "压缩 ${items.size} 个文件到 $targetPath")
    }

    private fun createDragShadow(items: List<FileItem>): View.DragShadowBuilder {
        // 创建拖拽阴影视图
        return object : View.DragShadowBuilder() {
            override fun onDrawShadow(canvas: android.graphics.Canvas) {
                // 绘制拖拽阴影
                canvas.drawText("${items.size} 个文件", 20f, 40f, 
                    android.graphics.Paint().apply { color = android.graphics.Color.WHITE; textSize = 30f })
            }
        }
    }

    private fun updateDropIndicator(pane: FileListPane, event: DragEvent) {
        // 更新放置位置指示器
    }

    private fun List<FileItem>.toJson(): String {
        // 序列化为JSON
        return "[]" // 简化实现
    }

    private data class DragLocalState(
        val sourcePane: FileListPane,
        val items: List<FileItem>
    )
}
```

---

## FileSystemProvider

文件系统抽象接口，统一访问本地文件、压缩包、远程文件等。

```kotlin
// SPDX-License-Identifier: Apache-2.0

package com.mtopensource.filemanager.filesystem

import android.net.Uri
import com.mtopensource.filemanager.model.FileItem
import com.mtopensource.filemanager.model.FilePermissions
import java.io.InputStream
import java.io.OutputStream

/**
 * 文件系统提供者接口
 * 
 * 抽象不同存储位置的文件访问，支持：
 * - 本地文件系统
 * - 压缩包虚拟文件系统
 * - 远程文件系统（通过插件）
 * - Root 文件系统
 */
interface FileSystemProvider {

    /**
     * URI Scheme
     */
    val scheme: String

    /**
     * 列出目录内容
     */
    suspend fun list(uri: Uri): List<FileItem>

    /**
     * 检查文件是否存在
     */
    suspend fun exists(uri: Uri): Boolean

    /**
     * 检查是否为目录
     */
    suspend fun isDirectory(uri: Uri): Boolean

    /**
     * 获取文件大小
     */
    suspend fun length(uri: Uri): Long

    /**
     * 获取最后修改时间
     */
    suspend fun lastModified(uri: Uri): Long

    /**
     * 创建文件
     */
    suspend fun createFile(uri: Uri): Boolean

    /**
     * 创建目录
     */
    suspend fun createDirectory(uri: Uri): Boolean

    /**
     * 删除文件或目录
     */
    suspend fun delete(uri: Uri): Boolean

    /**
     * 重命名
     */
    suspend fun rename(uri: Uri, newName: String): Boolean

    /**
     * 复制文件
     */
    suspend fun copy(source: Uri, target: Uri, progress: ProgressCallback? = null): Boolean

    /**
     * 移动文件
     */
    suspend fun move(source: Uri, target: Uri, progress: ProgressCallback? = null): Boolean

    /**
     * 打开输入流
     */
    suspend fun openInputStream(uri: Uri): InputStream?

    /**
     * 打开输出流
     */
    suspend fun openOutputStream(uri: Uri): OutputStream?

    /**
     * 获取权限信息
     */
    suspend fun getPermissions(uri: Uri): FilePermissions?

    /**
     * 设置权限
     */
    suspend fun setPermissions(uri: Uri, permissions: FilePermissions): Boolean

    /**
     * 进度回调接口
     */
    interface ProgressCallback {
        fun onProgress(bytesProcessed: Long, bytesTotal: Long)
        fun onComplete(success: Boolean)
    }
}
```

---

## PluginHost

插件宿主管理器，负责插件的发现、加载和生命周期管理。

```kotlin
// SPDX-License-Identifier: Apache-2.0

package com.mtopensource.plugin.host

import android.content.Context
import android.content.pm.PackageManager
import com.mtopensource.common.utils.Logger
import com.mtopensource.plugin.api.PluginEntry
import com.mtopensource.plugin.api.PluginInfo
import dalvik.system.PathClassLoader
import java.io.File

/**
 * 插件宿主管理器
 * 
 * 负责：
 * - 发现已安装的插件
 * - 加载插件APK
 * - 管理插件生命周期
 * - 处理插件权限
 */
class PluginHost(private val context: Context) {

    companion object {
        private const val TAG = "PluginHost"
        private const val META_PLUGIN_ID = "mt_plugin_id"
        private const val META_PLUGIN_VERSION = "mt_plugin_version"
        private const val META_PLUGIN_NAME = "mt_plugin_name"
        private const val META_PLUGIN_AUTHOR = "mt_plugin_author"
        private const val META_PLUGIN_LICENSE = "mt_plugin_license"
        private const val META_PLUGIN_ENTRY = "mt_plugin_entry"
        private const val META_PLUGIN_MIN_HOST = "mt_plugin_min_host"
    }

    private val loadedPlugins = mutableMapOf<String, LoadedPlugin>()
    private val pluginDir = File(context.filesDir, "plugins")

    init {
        pluginDir.mkdirs()
    }

    /**
     * 扫描已安装的插件
     */
    fun scanInstalledPlugins(): List<PluginMeta> {
        val plugins = mutableListOf<PluginMeta>()
        val pm = context.packageManager

        // 查询所有带有插件标记的应用
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        for (app in installedApps) {
            val metaData = app.metaData ?: continue
            val pluginId = metaData.getString(META_PLUGIN_ID) ?: continue

            plugins.add(PluginMeta(
                id = pluginId,
                name = metaData.getString(META_PLUGIN_NAME) ?: "Unknown",
                version = metaData.getString(META_PLUGIN_VERSION) ?: "0.0.0",
                author = metaData.getString(META_PLUGIN_AUTHOR) ?: "Unknown",
                license = metaData.getString(META_PLUGIN_LICENSE) ?: "Unknown",
                packageName = app.packageName,
                entryClass = metaData.getString(META_PLUGIN_ENTRY) ?: "",
                minHostVersion = metaData.getString(META_PLUGIN_MIN_HOST) ?: "0.0.0"
            ))
        }

        return plugins
    }

    /**
     * 加载插件
     */
    fun loadPlugin(meta: PluginMeta): Result<LoadedPlugin> {
        return try {
            // 检查是否已加载
            loadedPlugins[meta.id]?.let {
                return Result.success(it)
            }

            // 获取插件APK路径
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(meta.packageName, 0)
            val apkPath = appInfo.sourceDir

            // 创建独立ClassLoader
            val classLoader = PathClassLoader(apkPath, context.classLoader)

            // 加载入口类
            val entryClass = classLoader.loadClass(meta.entryClass)
            val pluginInstance = entryClass.getDeclaredConstructor()
                .newInstance() as PluginEntry

            // 创建插件上下文
            val pluginContext = PluginContextImpl(context, meta, pluginDir)

            // 初始化插件
            pluginInstance.onAttach(pluginContext)

            val loadedPlugin = LoadedPlugin(
                meta = meta,
                instance = pluginInstance,
                classLoader = classLoader,
                context = pluginContext
            )

            loadedPlugins[meta.id] = loadedPlugin
            Logger.i(TAG, "插件加载成功: ${meta.name} v${meta.version}")

            Result.success(loadedPlugin)
        } catch (e: Exception) {
            Logger.e(TAG, "插件加载失败: ${meta.name}", e)
            Result.failure(e)
        }
    }

    /**
     * 卸载插件
     */
    fun unloadPlugin(pluginId: String) {
        loadedPlugins[pluginId]?.let { plugin ->
            plugin.instance.onDetach()
            loadedPlugins.remove(pluginId)
            Logger.i(TAG, "插件已卸载: ${plugin.meta.name}")
        }
    }

    /**
     * 获取已加载的插件
     */
    fun getLoadedPlugin(pluginId: String): LoadedPlugin? {
        return loadedPlugins[pluginId]
    }

    /**
     * 获取所有已加载的插件
     */
    fun getAllLoadedPlugins(): List<LoadedPlugin> {
        return loadedPlugins.values.toList()
    }

    /**
     * 检查插件是否已加载
     */
    fun isPluginLoaded(pluginId: String): Boolean {
        return loadedPlugins.containsKey(pluginId)
    }
}

/**
 * 已加载的插件信息
 */
data class LoadedPlugin(
    val meta: PluginMeta,
    val instance: PluginEntry,
    val classLoader: ClassLoader,
    val context: PluginContextImpl
)

/**
 * 插件元数据
 */
data class PluginMeta(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val license: String,
    val packageName: String,
    val entryClass: String,
    val minHostVersion: String,
    val isEnabled: Boolean = true
)
```

---

*这些代码示例展示了核心架构的关键部分，实际实现可能更加复杂和完善。*

*最后更新：2026-09-07*

# 技术架构文档

> 本文档详细描述 MT管理器开源版 的技术架构设计，包括核心模块、插件系统、数据流和关键技术决策。

## 目录

1. [架构概览](#架构概览)
2. [核心模块设计](#核心模块设计)
3. [双窗口UI框架](#双窗口ui框架)
4. [文件管理引擎](#文件管理引擎)
5. [插件系统架构](#插件系统架构)
6. [APK编辑工作流](#apk编辑工作流)
7. [安全与权限模型](#安全与权限模型)
8. [性能优化策略](#性能优化策略)

---

## 架构概览

### 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                        用户界面层 (UI Layer)                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │   左窗口      │  │   右窗口      │  │   全局工具栏      │  │
│  │  (FileList)   │  │  (FileList)   │  │ (Toolbar/Menu)   │  │
│  └──────────────┘  └──────────────┘  └──────────────────┘  │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│                      业务逻辑层 (Business Layer)              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │  文件操作服务  │  │  插件管理器   │  │   任务调度器      │  │
│  │(FileService) │  │(PluginManager)│  │ (TaskScheduler)  │  │
│  └──────────────┘  └──────────────┘  └──────────────────┘  │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│                      数据访问层 (Data Layer)                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │  本地文件系统  │  │  压缩包虚拟FS │  │   插件进程FS     │  │
│  │(LocalFileSys)│  │(ArchiveVFS)  │  │ (PluginVFS)      │  │
│  └──────────────┘  └──────────────┘  └──────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 架构原则

1. **单一职责**：每个模块只负责一个明确的功能领域
2. **插件隔离**：高风险/许可证不兼容的功能通过插件实现，与核心隔离
3. **进程隔离**：插件运行在独立进程中，通过 AIDL/LocalSocket 通信
4. **响应式编程**：使用 Kotlin Flow 处理异步数据流
5. **零拷贝优先**：大文件操作使用内存映射和通道传输

---

## 核心模块设计

### 模块依赖关系

```
app (主应用)
├── filemanager (文件管理引擎) [Apache-2.0]
│   ├── common (公共库)
│   └── vfs (虚拟文件系统)
├── ui (双窗口UI框架) [Apache-2.0]
│   ├── common
│   └── filemanager
├── plugin-system (插件系统SDK) [Apache-2.0]
│   └── common
└── editor (内置编辑器) [Apache-2.0]
    ├── common
    └── filemanager
```

### 模块说明

#### 1. common (公共库)
- **职责**：提供全项目共享的基础工具类、常量、扩展函数
- **关键类**：
  - `FileUtils`：文件操作工具（复制、移动、删除、校验）
  - `PathUtils`：路径解析与规范化
  - `MimeTypeUtils`：MIME类型识别
  - `CoroutineUtils`：协程作用域管理
  - `Logger`：统一日志接口

#### 2. filemanager (文件管理引擎)
- **职责**：文件浏览、操作、搜索、属性管理
- **关键类**：
  - `FileSystemProvider`：文件系统抽象接口
  - `LocalFileSystem`：本地文件系统实现
  - `ArchiveFileSystem`：压缩包虚拟文件系统
  - `FileOperation`：文件操作原子任务
  - `FileOperationQueue`：批量操作队列管理
  - `FileSearcher`：文件搜索引擎

#### 3. ui (双窗口UI框架)
- **职责**：双窗口界面渲染、手势处理、拖拽系统
- **关键类**：
  - `DualPaneLayout`：双面板容器布局
  - `FileListView`：文件列表视图（RecyclerView优化版）
  - `DragDropController`：跨窗口拖拽控制器
  - `GestureHandler`：手势识别与分发
  - `PathBarView`：路径导航栏
  - `QuickActionMenu`：九宫格快捷菜单

#### 4. plugin-system (插件系统SDK)
- **职责**：插件发现、加载、生命周期管理、IPC通信
- **关键类**：
  - `PluginHost`：插件宿主管理器
  - `PluginLoader`：插件APK动态加载器
  - `PluginApi`：插件API接口定义
  - `PluginBridge`：跨进程通信桥
  - `PluginPermissionManager`：插件权限管理

#### 5. editor (内置编辑器)
- **职责**：文本/十六进制/图片基础预览和编辑
- **关键类**：
  - `TextEditor`：高性能文本编辑器
  - `HexEditor`：十六进制编辑器
  - `ImageViewer`：图片预览器
  - `SyntaxHighlighter`：语法高亮引擎

---

## 双窗口UI框架

### 设计理念

双窗口是 MT 管理器的核心交互范式，我们采用以下设计：

1. **对称性**：左右窗口功能完全对等，任何操作可在任一窗口发起
2. **上下文感知**：拖拽目标根据源窗口内容自动推断操作（复制/移动/解压）
3. **手势优先**：减少点击操作，通过滑动手势完成常用功能

### 核心组件

#### DualPaneLayout

```kotlin
class DualPaneLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val leftPane: FileListPane
    private val rightPane: FileListPane
    private val divider: PaneDivider

    // 窗口比例调整
    var paneRatio: Float = 0.5f
        set(value) {
            field = value.coerceIn(0.2f, 0.8f)
            requestLayout()
        }

    // 拖拽控制器
    private val dragDropController = DragDropController(this)

    // 手势分发器
    private val gestureDispatcher = GestureDispatcher()

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left
        val leftWidth = (width * paneRatio).toInt()

        leftPane.layout(0, 0, leftWidth, bottom - top)
        divider.layout(leftWidth, 0, leftWidth + dividerWidth, bottom - top)
        rightPane.layout(leftWidth + dividerWidth, 0, width, bottom - top)
    }
}
```

#### FileListPane

每个窗口包含：
- **PathBar**：面包屑导航，支持点击跳转和路径编辑
- **FileList**：文件列表，支持多种视图模式（列表/网格/详细信息）
- **StatusBar**：显示选中项数、总大小、可用空间
- **QuickAction**：长按/右键弹出的九宫格操作菜单

#### DragDropController

```kotlin
class DragDropController(private val layout: DualPaneLayout) {

    // 拖拽状态机
    sealed class DragState {
        object Idle : DragState()
        data class Dragging(
            val sourcePane: FileListPane,
            val items: List<FileItem>,
            val touchX: Float,
            val touchY: Float
        ) : DragState()
        data class Hovering(
            val targetPane: FileListPane,
            val targetFolder: FileItem?,
            val operation: DropOperation
        ) : DragState()
    }

    // 拖拽操作类型
    enum class DropOperation {
        COPY,      // 复制（跨分区或Ctrl按下）
        MOVE,      // 移动（同分区）
        EXTRACT,   // 解压（拖到压缩包外）
        ARCHIVE    // 压缩（拖到压缩包内）
    }

    fun startDrag(pane: FileListPane, items: List<FileItem>, x: Float, y: Float) {
        // 创建拖拽阴影
        val shadow = createDragShadow(items)
        // 启动系统拖拽
        pane.startDragAndDrop(
            ClipData.newPlainText("files", items.toJson()),
            shadow,
            DragLocalState(pane, items),
            View.DRAG_FLAG_GLOBAL
        )
    }

    fun handleDrop(targetPane: FileListPane, event: DragEvent) {
        val localState = event.localState as? DragLocalState ?: return
        val operation = determineOperation(localState.sourcePane, targetPane, event)

        when (operation) {
            DropOperation.COPY -> fileManager.copy(localState.items, targetPane.currentPath)
            DropOperation.MOVE -> fileManager.move(localState.items, targetPane.currentPath)
            DropOperation.EXTRACT -> archiveManager.extract(localState.items, targetPane.currentPath)
            DropOperation.ARCHIVE -> archiveManager.addToArchive(localState.items, targetPane.currentPath)
        }
    }
}
```

#### GestureHandler

支持的手势：

| 手势 | 操作 | 触发区域 |
|------|------|---------|
| 左滑 | 多选/取消选择 | 文件列表项 |
| 右滑 | 快速操作菜单 | 文件列表项 |
| 双指缩放 | 调整字体/图标大小 | 任意区域 |
| 下拉 | 刷新当前目录 | 文件列表顶部 |
| 长按拖拽 | 开始拖拽 | 文件列表项 |
| 边缘侧滑 | 打开书签/历史 | 屏幕左右边缘 |

---

## 文件管理引擎

### 虚拟文件系统 (VFS)

采用统一的 URI 方案访问所有位置：

```
file:///storage/emulated/0/Documents    # 本地文件
archive:///storage/emulated/0/test.zip/inner/file.txt  # 压缩包内文件
plugin://apk-editor/12345/smali/com/example/MainActivity.smali  # 插件虚拟文件
root:///system/build.prop               # Root文件系统
ftp://user@host:21/path/to/file        # FTP远程文件
```

### FileSystemProvider 接口

```kotlin
interface FileSystemProvider {
    val scheme: String

    suspend fun list(uri: Uri): List<FileItem>
    suspend fun exists(uri: Uri): Boolean
    suspend fun isDirectory(uri: Uri): Boolean
    suspend fun length(uri: Uri): Long
    suspend fun lastModified(uri: Uri): Long
    suspend fun createFile(uri: Uri): Boolean
    suspend fun createDirectory(uri: Uri): Boolean
    suspend fun delete(uri: Uri): Boolean
    suspend fun rename(uri: Uri, newName: String): Boolean
    suspend fun copy(source: Uri, target: Uri, progress: ProgressCallback? = null): Boolean
    suspend fun move(source: Uri, target: Uri, progress: ProgressCallback? = null): Boolean
    suspend fun openInputStream(uri: Uri): InputStream?
    suspend fun openOutputStream(uri: Uri): OutputStream?
    suspend fun getPermissions(uri: Uri): FilePermissions?
    suspend fun setPermissions(uri: Uri, permissions: FilePermissions): Boolean
}
```

### 批量操作引擎

```kotlin
class FileOperationQueue {

    private val operationChannel = Channel<FileOperation>(Channel.UNLIMITED)
    private val _progress = MutableStateFlow<OperationProgress?>(null)
    val progress: StateFlow<OperationProgress?> = _progress.asStateFlow()

    // 操作队列，支持暂停/恢复/取消
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            for (operation in operationChannel) {
                executeOperation(operation)
            }
        }
    }

    suspend fun enqueue(operation: FileOperation): OperationResult {
        operationChannel.send(operation)
        return operation.awaitResult()
    }

    private suspend fun executeOperation(operation: FileOperation) {
        _progress.value = OperationProgress(
            totalItems = operation.items.size,
            completedItems = 0,
            currentItem = operation.items.first(),
            bytesTotal = operation.items.sumOf { it.size },
            bytesProcessed = 0,
            status = OperationStatus.RUNNING
        )

        try {
            when (operation.type) {
                OperationType.COPY -> performCopy(operation)
                OperationType.MOVE -> performMove(operation)
                OperationType.DELETE -> performDelete(operation)
                OperationType.COMPRESS -> performCompress(operation)
                OperationType.EXTRACT -> performExtract(operation)
            }
        } catch (e: Exception) {
            _progress.value = _progress.value?.copy(
                status = OperationStatus.FAILED,
                error = e.message
            )
        }
    }
}
```

### 大文件传输优化

- **内存映射**：使用 `FileChannel.map()` 处理 >100MB 文件
- **分块传输**：每块 8MB，避免内存溢出
- **校验恢复**：传输中断后支持断点续传（基于 MD5 校验）
- **并发控制**：最多 4 个并发传输任务

---

## 插件系统架构

### 为什么需要插件系统？

1. **许可证隔离**：GPL 组件（如 Dalvikus）作为独立插件，避免污染核心代码
2. **功能隔离**：APK编辑等高风险功能作为插件，降低主应用法律风险
3. **动态扩展**：用户按需安装，减少主应用体积
4. **独立更新**：插件可独立发布更新，无需更新主应用

### 插件类型

| 类型 | 说明 | 示例 |
|------|------|------|
| **功能插件** | 扩展文件管理功能 | 远程存储、Root支持 |
| **编辑器插件** | 提供特定文件类型的编辑能力 | DEX编辑器、ARSC编辑器 |
| **工具插件** | 提供独立工具 | APK反编译、签名工具 |
| **主题插件** | 提供UI主题和图标包 | 深色主题、图标包 |

### 插件生命周期

```
[未安装] → 下载APK → [已安装] → 加载Dex → [已加载] → 初始化 → [运行中]
                                              ↓
                                        [禁用] ← 用户禁用
                                              ↓
                                        [已卸载] ← 用户卸载
```

### 插件加载机制

```kotlin
class PluginLoader(private val context: Context) {

    // 插件目录
    private val pluginDir = File(context.filesDir, "plugins")

    // 已加载的插件
    private val loadedPlugins = mutableMapOf<String, LoadedPlugin>()

    fun loadPlugin(pluginApk: File): Result<LoadedPlugin> {
        return try {
            // 1. 创建独立ClassLoader
            val classLoader = PathClassLoader(
                pluginApk.absolutePath,
                context.classLoader
            )

            // 2. 读取插件配置
            val packageInfo = context.packageManager
                .getPackageArchiveInfo(pluginApk.absolutePath, 
                    PackageManager.GET_META_DATA)!!

            val pluginMeta = parsePluginMeta(packageInfo.applicationInfo.metaData)

            // 3. 验证插件签名
            if (!verifyPluginSignature(pluginApk, pluginMeta)) {
                return Result.failure(SecurityException("插件签名验证失败"))
            }

            // 4. 加载插件入口类
            val entryClass = classLoader.loadClass(pluginMeta.entryClass)
            val pluginInstance = entryClass.getDeclaredConstructor()
                .newInstance() as PluginEntry

            // 5. 创建插件上下文
            val pluginContext = createPluginContext(pluginApk, pluginMeta)

            // 6. 初始化插件
            pluginInstance.onAttach(pluginContext)

            // 7. 注册到宿主
            val loadedPlugin = LoadedPlugin(
                meta = pluginMeta,
                instance = pluginInstance,
                classLoader = classLoader,
                context = pluginContext
            )
            loadedPlugins[pluginMeta.id] = loadedPlugin

            Result.success(loadedPlugin)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

### 跨进程通信 (IPC)

对于需要独立进程运行的插件（如 GPL 组件）：

```kotlin
// 插件端 Service
class PluginService : Service() {

    private val binder = object : IPluginInterface.Stub() {
        override fun executeCommand(command: String, params: Bundle): Bundle {
            return when (command) {
                "decompile_apk" -> decompileApk(params)
                "compile_apk" -> compileApk(params)
                "edit_dex" -> editDex(params)
                else -> Bundle().apply { putString("error", "未知命令") }
            }
        }

        override fun registerCallback(callback: IPluginCallback?) {
            this@PluginService.callback = callback
        }
    }

    override fun onBind(intent: Intent): IBinder = binder
}

// 宿主端调用
class PluginBridge(private val context: Context) {

    private var pluginInterface: IPluginInterface? = null

    suspend fun connect(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val intent = Intent().apply {
            component = ComponentName(packageName, "$packageName.PluginService")
        }

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                pluginInterface = IPluginInterface.Stub.asInterface(service)
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                pluginInterface = null
            }
        }

        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)

        // 等待连接
        var retries = 50
        while (pluginInterface == null && retries-- > 0) {
            delay(100)
        }

        pluginInterface != null
    }

    suspend fun execute(command: String, params: Bundle): Bundle = withContext(Dispatchers.IO) {
        pluginInterface?.executeCommand(command, params)
            ?: throw IllegalStateException("插件未连接")
    }
}
```

### 插件权限模型

```kotlin
enum class PluginPermission {
    READ_STORAGE,       // 读取本地文件
    WRITE_STORAGE,      // 写入本地文件
    NETWORK,            // 网络访问
    ROOT_ACCESS,        // Root权限（需用户单独授权）
    APK_EDIT,           // APK编辑能力
    SYSTEM_ALERT,       // 悬浮窗
    BACKGROUND_TASK,    // 后台任务
}

class PluginPermissionManager {

    // 权限申请对话框
    fun requestPermissions(plugin: PluginMeta, permissions: List<PluginPermission>) {
        // 显示权限说明对话框
        // 用户同意后记录到数据库
        // 拒绝则插件无法加载对应功能
    }

    // 运行时权限检查
    fun checkPermission(pluginId: String, permission: PluginPermission): Boolean {
        return getGrantedPermissions(pluginId).contains(permission)
    }
}
```

---

## APK编辑工作流

### 基础流程（非插件化）

核心应用提供基础的 APK 查看和提取功能：

```
用户选择APK → 解析APK结构 → 显示文件列表 → 提取/查看文件 → 签名APK
```

使用组件：
- **Apktool**（命令行调用，Apache-2.0）
- **apksig**（Android官方库，Apache-2.0）
- **zipalign**（命令行调用，Apache-2.0）

### 高级编辑流程（插件化）

通过 "APK编辑器插件" 提供高级功能：

```
用户选择APK → 调用插件 → 插件解包APK → 显示项目结构
                                    ↓
用户编辑Smali/XML → 插件增量编译 → 插件自动签名 → 输出新APK
```

插件内部可集成：
- **Dalvikus**（GPL-3.0，独立进程）
- **JADX**（Apache-2.0）
- **自研 Smali 编辑器**

### 工作流状态机

```kotlin
sealed class ApkEditState {
    object Idle : ApkEditState()
    data class Loading(val apkPath: String, val progress: Int) : ApkEditState()
    data class Loaded(val project: ApkProject) : ApkEditState()
    data class Editing(val project: ApkProject, val modifiedFiles: Set<String>) : ApkEditState()
    data class Compiling(val project: ApkProject, val progress: Int) : ApkEditState()
    data class Compiled(val outputPath: String) : ApkEditState()
    data class Signing(val outputPath: String) : ApkEditState()
    data class Completed(val finalApk: String) : ApkEditState()
    data class Error(val message: String, val recoverable: Boolean) : ApkEditState()
}

class ApkEditWorkflow(private val plugin: ApkEditorPlugin?) {

    private val _state = MutableStateFlow<ApkEditState>(ApkEditState.Idle)
    val state: StateFlow<ApkEditState> = _state.asStateFlow()

    suspend fun openApk(apkPath: String) {
        _state.value = ApkEditState.Loading(apkPath, 0)

        try {
            // 1. 解包APK
            val project = if (plugin != null) {
                plugin.decompile(apkPath) // 通过插件解包
            } else {
                basicDecompile(apkPath)   // 基础解包（仅提取）
            }

            _state.value = ApkEditState.Loaded(project)
        } catch (e: Exception) {
            _state.value = ApkEditState.Error(e.message ?: "未知错误", true)
        }
    }

    suspend fun saveChanges() {
        val current = _state.value as? ApkEditState.Editing ?: return

        _state.value = ApkEditState.Compiling(current.project, 0)

        // 增量编译：只编译修改过的文件
        val modified = current.modifiedFiles
        val result = plugin?.compile(current.project, modified) 
            ?: basicCompile(current.project)

        _state.value = ApkEditState.Compiled(result.outputPath)

        // 自动签名
        _state.value = ApkEditState.Signing(result.outputPath)
        val signedApk = signApk(result.outputPath)

        _state.value = ApkEditState.Completed(signedApk)
    }
}
```

---

## 安全与权限模型

### 存储权限策略

| Android 版本 | 策略 |
|-------------|------|
| API 26-29 (Android 8-10) | 申请 `READ/WRITE_EXTERNAL_STORAGE` |
| API 30-32 (Android 11-12) | 使用 `MANAGE_EXTERNAL_STORAGE` + Storage Access Framework |
| API 33+ (Android 13+) | 细分媒体权限 + `MANAGE_EXTERNAL_STORAGE` |

### Root 权限策略

- Root 功能通过独立插件实现
- 插件通过 `libsu` 库与 Magisk/SuperSU 通信
- 每次使用 Root 功能前弹出确认对话框
- 记录所有 Root 操作日志

### 沙箱机制

- 插件运行在自己的用户 ID 下（通过 `android:sharedUserId` 隔离）
- 插件无法直接访问主应用私有数据
- 所有文件访问通过宿主提供的 VFS 接口

---

## 性能优化策略

### 文件列表渲染

- **虚拟化**：RecyclerView + DiffUtil，只渲染可见项
- **异步加载**：图标/缩略图使用 Glide 异步加载
- **预加载**：滑动时预加载下一页数据
- **缓存**：文件列表缓存最近 10 个目录结构

### 大文件处理

- **内存映射**：>10MB 文件使用 MappedByteBuffer
- **流式处理**：文本编辑器使用分页加载，每页 1MB
- **后台任务**：文件操作在独立 Service 中执行，支持后台继续

### 启动优化

- **懒加载**：插件按需加载，启动时不初始化
- **预编译**：使用 Baseline Profiles 优化 ART 编译
- **资源精简**：使用 Android App Bundle 按需分发资源

---

## 关键技术决策记录 (ADRs)

### ADR-001: 使用 Jetpack Compose 作为主 UI 框架

**决策**：采用 Compose 构建双窗口UI
**原因**：
- 声明式UI更适合复杂的双窗口状态管理
- 减少 XML 布局文件数量
- 更好的动画支持

**风险**：
- 大列表性能可能不如原生 RecyclerView
- **缓解**：文件列表仍使用 RecyclerView，Compose 用于框架布局

### ADR-002: 插件采用独立 APK 而非内置

**决策**：插件作为独立 APK 分发
**原因**：
- 解决许可证隔离问题
- 支持独立更新
- 用户按需安装

**风险**：
- 安装流程较复杂
- **缓解**：提供内置插件市场，一键安装

### ADR-003: 使用 AIDL 而非 ContentProvider 作为 IPC

**决策**：插件与宿主使用 AIDL 接口通信
**原因**：
- 双向通信更灵活
- 支持大数据传输（通过 ParcelFileDescriptor）
- 类型安全

### ADR-004: 文件操作使用 Kotlin Coroutines 而非 RxJava

**决策**：异步操作使用 Coroutines + Flow
**原因**：
- 更轻量，减少依赖
- 与 Android 官方推荐一致
- 更好的取消支持

---

*最后更新：2026-09-07*

# 开发指南

> 本文档为希望参与 MT管理器开源版 开发的贡献者提供详细的开发指引。

## 目录

1. [开发环境搭建](#开发环境搭建)
2. [项目结构说明](#项目结构说明)
3. [编码规范](#编码规范)
4. [提交规范](#提交规范)
5. [插件开发指南](#插件开发指南)
6. [调试技巧](#调试技巧)
7. [常见问题](#常见问题)

---

## 开发环境搭建

### 系统要求

- **操作系统**：Windows 10/11、macOS 12+、Linux (Ubuntu 20.04+)
- **内存**：至少 8GB（推荐 16GB）
- **磁盘空间**：至少 20GB 可用空间

### 必需工具

| 工具 | 版本 | 用途 |
|------|------|------|
| Android Studio | Ladybug (2024.2.1) 或更高 | 主要IDE |
| JDK | 17 | 编译环境 |
| Android SDK | API 26-36 | 目标平台 |
| Git | 2.30+ | 版本控制 |

### 可选工具

| 工具 | 用途 |
|------|------|
| Detekt | Kotlin 静态分析 |
| ktlint | Kotlin 代码格式化 |
| adb | Android 调试桥 |
| scrcpy | 屏幕镜像调试 |

### 环境配置步骤

1. **安装 Android Studio**
   ```bash
   # 下载地址
   https://developer.android.com/studio
   ```

2. **克隆项目**
   ```bash
   git clone https://github.com/your-org/MT-Manager-OpenSource.git
   cd MT-Manager-OpenSource
   ```

3. **配置本地属性**
   ```bash
   # 创建 local.properties
   echo "sdk.dir=/path/to/your/Android/Sdk" > local.properties
   ```

4. **首次构建**
   ```bash
   ./gradlew :core:app:assembleDebug
   ```

5. **运行测试**
   ```bash
   ./gradlew test
   ```

---

## 项目结构说明

```
MT-Manager-OpenSource/
├── build.gradle.kts              # 根构建脚本
├── settings.gradle.kts           # 项目设置
├── gradle.properties             # Gradle 属性
├── local.properties              # 本地配置（不提交到Git）
│
├── core/                         # 核心模块
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/mtopensource/
│       │   ├── app/              # 主应用入口
│       │   │   ├── MainActivity.kt
│       │   │   └── MTApplication.kt
│       │   ├── ui/               # UI框架
│       │   │   ├── dualpane/     # 双窗口布局
│       │   │   ├── filelist/     # 文件列表
│       │   │   ├── gesture/      # 手势处理
│       │   │   └── theme/        # 主题系统
│       │   ├── filemanager/      # 文件管理引擎
│       │   │   ├── filesystem/   # 文件系统抽象
│       │   │   ├── operations/   # 文件操作
│       │   │   ├── search/       # 搜索功能
│       │   │   └── archive/      # 压缩包支持
│       │   ├── editor/           # 内置编辑器
│       │   │   ├── text/         # 文本编辑器
│       │   │   ├── hex/          # 十六进制编辑器
│       │   │   └── image/        # 图片查看器
│       │   ├── plugin/           # 插件系统
│       │   │   ├── api/          # 插件API
│       │   │   ├── host/         # 宿主实现
│       │   │   └── bridge/       # IPC通信
│       │   └── common/           # 公共工具
│       │       ├── utils/        # 工具类
│       │       ├── model/        # 数据模型
│       │       └── extensions/   # Kotlin扩展
│       └── res/                  # 资源文件
│
├── plugin-sdk/                   # 插件开发SDK
│   ├── build.gradle.kts
│   └── src/main/
│       └── java/com/mtopensource/plugin/
│           ├── PluginEntry.kt
│           ├── PluginContext.kt
│           ├── PluginApi.kt
│           └── aidl/             # AIDL接口
│
├── plugins/                      # 官方插件
│   ├── apk-editor/               # APK编辑器插件
│   ├── dex-editor/               # DEX编辑器插件
│   ├── root-support/             # Root支持插件
│   └── remote-storage/           # 远程存储插件
│
├── docs/                         # 项目文档
│   ├── ARCHITECTURE.md
│   ├── PLUGIN-SYSTEM.md
│   ├── LICENSE-COMPATIBILITY.md
│   ├── LEGAL.md
│   ├── ROADMAP.md
│   └── DEVELOPMENT.md
│
└── tools/                        # 开发工具
    ├── check-license.sh          # 许可证检查脚本
    ├── setup-hooks.sh            # Git钩子安装
    └── templates/                # 代码模板
```

---

## 编码规范

### Kotlin 代码风格

遵循 [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html) 和以下项目特定规则：

#### 命名规范

```kotlin
// 类名：PascalCase
class FileListPane : FrameLayout { }
class DragDropController { }

// 函数名：camelCase
fun handleDragEvent(event: DragEvent) { }
fun createDragShadow(items: List<FileItem>): View { }

// 常量：UPPER_SNAKE_CASE
const val MAX_CONCURRENT_OPERATIONS = 4
const val DEFAULT_PANE_RATIO = 0.5f

// 变量名：camelCase
private val operationQueue = mutableListOf<FileOperation>()
private var currentPane: FileListPane? = null
```

#### 文件组织

```kotlin
// 文件头部：SPDX 许可证标识
// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 MT-Manager-OpenSource Contributors

package com.mtopensource.ui.dualpane

// 导入按以下顺序分组，每组之间空一行
import android.content.Context
import android.view.View

import androidx.recyclerview.widget.RecyclerView

import com.mtopensource.common.utils.FileUtils
import com.mtopensource.filemanager.model.FileItem

// 类声明
class DualPaneLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // 伴生对象放最前面
    companion object {
        const val DEFAULT_RATIO = 0.5f
        const val MIN_RATIO = 0.2f
        const val MAX_RATIO = 0.8f
    }

    // 属性声明
    private val leftPane: FileListPane
    private val rightPane: FileListPane

    // 公开属性
    var paneRatio: Float = DEFAULT_RATIO
        set(value) {
            field = value.coerceIn(MIN_RATIO, MAX_RATIO)
            requestLayout()
        }

    // 初始化块
    init {
        leftPane = FileListPane(context)
        rightPane = FileListPane(context)
        addView(leftPane)
        addView(rightPane)
    }

    // 重写方法
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        // 实现
    }

    // 公开方法
    fun setLeftPath(path: String) {
        leftPane.navigateTo(path)
    }

    // 私有方法
    private fun calculatePaneWidths(width: Int): Pair<Int, Int> {
        val leftWidth = (width * paneRatio).toInt()
        val rightWidth = width - leftWidth - dividerWidth
        return leftWidth to rightWidth
    }
}
```

#### 协程使用规范

```kotlin
// 使用自定义协程作用域，避免使用 GlobalScope
class FileOperationQueue {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun enqueue(operation: FileOperation) {
        scope.launch {
            executeOperation(operation)
        }
    }

    // 在不需要时取消
    fun cleanup() {
        scope.cancel()
    }
}

// Flow 用于状态观察
class FileManagerViewModel : ViewModel() {
    private val _currentPath = MutableStateFlow("/")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _fileList = MutableStateFlow<List<FileItem>>(emptyList())
    val fileList: StateFlow<List<FileItem>> = _fileList.asStateFlow()

    fun loadFiles(path: String) {
        viewModelScope.launch {
            fileSystem.list(path)
                .catch { e -> _error.value = e.message }
                .collect { files -> _fileList.value = files }
        }
    }
}
```

#### 空安全规范

```kotlin
// 优先使用空安全类型
fun processFile(file: FileItem?) {  // 可空参数明确标记
    file?.let { 
        // 处理非空情况
    } ?: run {
        // 处理空情况
    }
}

// 避免 !! 操作符
// 错误
val name = file!!.name

// 正确
val name = file?.name ?: return

// 使用 requireNotNull 在初始化时检查
val adapter = requireNotNull(recyclerView.adapter) { "Adapter must be set" }
```

### 架构规范

#### MVVM 模式

```kotlin
// ViewModel 负责业务逻辑
class FileListViewModel(
    private val fileSystem: FileSystemProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileListUiState())
    val uiState: StateFlow<FileListUiState> = _uiState.asStateFlow()

    fun loadDirectory(path: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val files = fileSystem.list(Uri.parse(path))
                _uiState.update { 
                    it.copy(isLoading = false, files = files, error = null) 
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(isLoading = false, error = e.message) 
                }
            }
        }
    }
}

// UI 层只负责渲染
data class FileListUiState(
    val isLoading: Boolean = false,
    val files: List<FileItem> = emptyList(),
    val selectedFiles: Set<String> = emptySet(),
    val error: String? = null
)
```

#### 依赖注入

使用 Hilt 进行依赖注入：

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object FileSystemModule {

    @Provides
    @Singleton
    fun provideLocalFileSystem(@ApplicationContext context: Context): FileSystemProvider {
        return LocalFileSystem(context)
    }
}

@HiltViewModel
class FileListViewModel @Inject constructor(
    private val fileSystem: FileSystemProvider
) : ViewModel() { }
```

---

## 提交规范

### 提交信息格式

采用 [Conventional Commits](https://www.conventionalcommits.org/) 规范：

```
<type>(<scope>): <subject>

<body>

<footer>
```

#### Type 类型

| 类型 | 说明 |
|------|------|
| `feat` | 新功能 |
| `fix` | Bug修复 |
| `docs` | 文档更新 |
| `style` | 代码格式调整（不影响功能） |
| `refactor` | 代码重构 |
| `perf` | 性能优化 |
| `test` | 测试相关 |
| `chore` | 构建/工具/依赖更新 |
| `license` | 许可证相关 |

#### Scope 范围

| 范围 | 说明 |
|------|------|
| `core` | 核心模块 |
| `ui` | 用户界面 |
| `filemanager` | 文件管理引擎 |
| `plugin` | 插件系统 |
| `editor` | 编辑器 |
| `docs` | 文档 |

#### 示例

```
feat(ui): 实现双窗口拖拽传输功能

- 添加 DragDropController 处理跨窗口拖拽
- 支持复制/移动/解压三种拖拽操作
- 添加拖拽时的视觉反馈动画

Closes #123
```

```
fix(filemanager): 修复大文件复制时的内存溢出问题

使用 FileChannel 替代内存缓冲，支持 >2GB 文件传输

Fixes #456
```

```
license: 添加第三方组件许可证声明

- 添加 Apache Commons Compress 的 LICENSE 文件
- 更新 LICENSE-COMPATIBILITY.md

Refs #789
```

### 分支策略

```
main          # 稳定分支，只接受合并请求
  │
  ├── develop # 开发分支，日常开发
  │     │
  │     ├── feature/dual-pane-drag  # 功能分支
  │     ├── feature/plugin-system
  │     └── fix/memory-leak
  │
  └── hotfix/crash-fix  # 紧急修复分支
```

### 合并请求规范

1. **标题格式**：`[类型] 简要描述`
   - 例：`[Feature] 实现双窗口拖拽传输`

2. **描述模板**：
   ```markdown
   ## 变更内容
   - 实现了什么功能/修复了什么问题

   ## 测试情况
   - [ ] 单元测试通过
   - [ ] 手动测试通过
   - [ ] 在真机上测试通过

   ## 检查清单
   - [ ] 代码遵循编码规范
   - [ ] 新文件包含 SPDX 许可证标识
   - [ ] 文档已更新（如需要）
   - [ ] 没有引入新的许可证冲突
   ```

3. **审查要求**：
   - 至少 1 名维护者审查通过
   - CI 检查全部通过
   - 没有未解决的讨论

---

## 插件开发指南

### 快速开始

1. **创建插件项目**
   ```bash
   # 使用模板创建
   ./tools/create-plugin.sh my-plugin
   ```

2. **配置 build.gradle**
   ```kotlin
   plugins {
       id("com.android.application")
       id("org.jetbrains.kotlin.android")
   }

   android {
       namespace = "com.example.myplugin"
       compileSdk = 36

       defaultConfig {
           minSdk = 26
           targetSdk = 36
           versionCode = 1
           versionName = "1.0.0"
       }
   }

   dependencies {
       implementation("com.mtopensource:plugin-sdk:1.0.0")
   }
   ```

3. **实现插件入口**
   ```kotlin
   class MyPlugin : PluginEntry {
       override fun onAttach(context: PluginContext) {
           // 注册功能
       }

       override fun onDetach() {
           // 清理资源
       }

       override fun getInfo(): PluginInfo {
           return PluginInfo(
               id = "com.example.myplugin",
               name = "我的插件",
               version = "1.0.0",
               // ...
           )
       }
   }
   ```

4. **配置 AndroidManifest**
   ```xml
   <application>
       <meta-data
           android:name="mt_plugin_id"
           android:value="com.example.myplugin" />
       <meta-data
           android:name="mt_plugin_entry"
           android:value="com.example.myplugin.MyPlugin" />
   </application>
   ```

### 调试插件

```bash
# 1. 构建插件
./gradlew :plugins:my-plugin:assembleDebug

# 2. 安装插件
adb install plugins/my-plugin/build/outputs/apk/debug/my-plugin-debug.apk

# 3. 在宿主应用中启用插件
# 打开应用 -> 设置 -> 插件 -> 启用 "我的插件"

# 4. 查看日志
adb logcat -s "PluginHost" "MyPlugin"
```

---

## 调试技巧

### 日志系统

使用项目统一的日志接口：

```kotlin
class FileManager {
    companion object {
        private const val TAG = "FileManager"
    }

    fun copyFile(source: Uri, target: Uri) {
        Logger.d(TAG, "开始复制: $source -> $target")

        try {
            // 操作
            Logger.i(TAG, "复制完成")
        } catch (e: Exception) {
            Logger.e(TAG, "复制失败", e)
        }
    }
}
```

日志级别：
- `VERBOSE`：详细调试信息
- `DEBUG`：开发调试信息
- `INFO`：一般信息
- `WARN`：警告信息
- `ERROR`：错误信息

### 性能分析

```kotlin
// 使用 Trace 标记
fun loadLargeDirectory(path: String) {
    Trace.beginSection("loadLargeDirectory")

    val files = fileSystem.list(path)
    // 处理

    Trace.endSection()
}
```

在 Android Studio 的 CPU Profiler 中查看跟踪结果。

### 内存调试

```kotlin
// 检查内存泄漏
class FileListPane @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val scope = CoroutineScope(SupervisorJob())

    // 在视图销毁时取消协程
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()  // 防止协程泄漏
    }
}
```

---

## 常见问题

### Q: 构建失败，提示 "SDK location not found"

A: 创建 `local.properties` 文件并指定 SDK 路径：
```bash
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
```

### Q: 插件无法加载，提示 "Plugin not found"

A: 检查以下几点：
1. 插件 APK 已安装
2. AndroidManifest.xml 中包含正确的 `mt_plugin_id` 和 `mt_plugin_entry`
3. 插件的 `minHostVersion` 不大于宿主应用版本

### Q: 如何测试 Root 功能？

A: 
1. 使用已 Root 的模拟器或真机
2. 安装 Root 支持插件
3. 在应用设置中启用 Root 访问
4. 尝试访问 `/system` 目录

### Q: 如何添加新的文件系统支持？

A:
1. 实现 `FileSystemProvider` 接口
2. 在 `FileSystemRegistry` 中注册
3. 添加对应的 URI scheme 处理

### Q: 许可证检查脚本报错了怎么办？

A:
```bash
# 运行许可证检查
./tools/check-license.sh

# 查看详细报告
cat build/reports/licenses/report.txt

# 如果引入了新组件，更新许可证清单
# 编辑 docs/LICENSE-COMPATIBILITY.md
```

---

## 资源链接

- [项目仓库](https://github.com/your-org/MT-Manager-OpenSource)
- [Issue 跟踪](https://github.com/your-org/MT-Manager-OpenSource/issues)
- [文档站点](https://your-org.github.io/MT-Manager-OpenSource)
- [讨论区](https://github.com/your-org/MT-Manager-OpenSource/discussions)

---

*最后更新：2026-09-07*

# 插件系统设计文档

> 本文档详细描述 MT管理器开源版 的插件系统设计，包括插件API、开发规范、发布流程和许可证隔离策略。

## 目录

1. [设计目标](#设计目标)
2. [插件架构](#插件架构)
3. [插件API](#插件api)
4. [开发规范](#开发规范)
5. [许可证隔离策略](#许可证隔离策略)
6. [官方插件清单](#官方插件清单)
7. [插件发布流程](#插件发布流程)

---

## 设计目标

1. **许可证隔离**：GPL 组件与核心代码完全隔离，避免许可证污染
2. **功能隔离**：高风险功能（如APK编辑）作为插件，降低主应用法律风险
3. **动态扩展**：用户按需安装，减少主应用体积
4. **独立更新**：插件可独立发布更新
5. **安全沙箱**：插件运行在受限环境中，防止恶意行为

---

## 插件架构

### 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                      宿主应用 (Host App)                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │  PluginManager│  │  PluginBridge │  │  PluginPermission │  │
│  │  (插件管理器)  │  │  (IPC通信桥)  │  │   (权限管理)      │  │
│  └──────────────┘  └──────────────┘  └──────────────────┘  │
└────────────────────────┬────────────────────────────────────┘
                         │ AIDL / LocalSocket
┌────────────────────────▼────────────────────────────────────┐
│                      插件进程 (Plugin Process)                │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │  PluginService│  │  PluginEntry │  │  PluginFeature   │  │
│  │  (服务入口)   │  │  (插件入口)  │  │   (功能实现)     │  │
│  └──────────────┘  └──────────────┘  └──────────────────┘  │
│                                                             │
│  可包含GPL组件：Dalvikus、Amaze File Manager等               │
└─────────────────────────────────────────────────────────────┘
```

### 进程模型

| 场景 | 进程模型 | 说明 |
|------|---------|------|
| 同进程插件 | 插件代码加载到宿主进程 | 适用于 MIT/Apache 插件，性能更好 |
| 独立进程插件 | 插件运行在自己的进程中 | 适用于 GPL 插件，必须隔离 |
| 远程服务插件 | 插件作为独立应用运行 | 最安全的隔离方式 |

---

## 插件API

### 基础接口

```kotlin
// 插件入口接口
interface PluginEntry {
    fun onAttach(context: PluginContext)
    fun onDetach()
    fun getInfo(): PluginInfo
}

// 插件上下文
interface PluginContext {
    val hostContext: Context
    val pluginDir: File
    val cacheDir: File
    val dataDir: File

    // 向宿主注册功能
    fun registerFileHandler(handler: FileHandler)
    fun registerEditor(editor: FileEditor)
    fun registerTool(tool: ToolProvider)
    fun registerMenuItem(item: MenuItem)

    // 调用宿主服务
    fun openFile(uri: Uri): InputStream?
    fun saveFile(uri: Uri): OutputStream?
    fun showToast(message: String)
    fun showDialog(dialog: AlertDialog.Builder)
    fun startActivity(intent: Intent)

    // 权限请求
    fun requestPermission(permission: PluginPermission): Boolean
    fun checkPermission(permission: PluginPermission): Boolean
}

// 插件信息
data class PluginInfo(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val license: String,
    val licenseUrl: String,
    val icon: Drawable?,
    val permissions: List<PluginPermission>,
    val minHostVersion: String,
    val features: List<PluginFeature>
)

// 插件功能类型
sealed class PluginFeature {
    data class FileHandler(
        val scheme: String,
        val mimeTypes: List<String>,
        val handler: FileHandlerImpl
    ) : PluginFeature()

    data class FileEditor(
        val name: String,
        val supportedExtensions: List<String>,
        val editorFactory: EditorFactory
    ) : PluginFeature()

    data class ToolProvider(
        val name: String,
        val description: String,
        val icon: Drawable?,
        val action: () -> Unit
    ) : PluginFeature()

    data class MenuItem(
        val title: String,
        val icon: Drawable?,
        val condition: (FileItem) -> Boolean,
        val action: (List<FileItem>) -> Unit
    ) : PluginFeature()
}
```

### 文件处理接口

```kotlin
// 文件处理器
interface FileHandler {
    suspend fun canHandle(uri: Uri): Boolean
    suspend fun list(uri: Uri): List<FileItem>
    suspend fun open(uri: Uri): InputStream?
    suspend fun create(uri: Uri): Boolean
    suspend fun delete(uri: Uri): Boolean
    suspend fun rename(uri: Uri, newName: String): Boolean
    suspend fun copy(source: Uri, target: Uri): Boolean
    suspend fun move(source: Uri, target: Uri): Boolean
}

// 文件编辑器
interface FileEditor {
    fun createView(context: Context): View
    fun openFile(uri: Uri)
    fun saveFile(): Boolean
    fun canSave(): Boolean
    fun getTitle(): String
    fun getModified(): Boolean
}
```

### AIDL 接口定义

```java
// IPluginInterface.aidl
package com.mtopensource.plugin;

import com.mtopensource.plugin.IPluginCallback;

interface IPluginInterface {
    Bundle executeCommand(String command, in Bundle params);
    void registerCallback(IPluginCallback callback);
    void unregisterCallback(IPluginCallback callback);
}

// IPluginCallback.aidl
package com.mtopensource.plugin;

interface IPluginCallback {
    void onProgress(String taskId, int progress, String message);
    void onComplete(String taskId, Bundle result);
    void onError(String taskId, String error);
}
```

---

## 开发规范

### 插件项目结构

```
apk-editor-plugin/
├── build.gradle.kts
├── src/
│   └── main/
│       ├── AndroidManifest.xml
│       ├── java/
│       │   └── com/
│       │       └── example/
│       │           └── apkeditor/
│       │               ├── ApkEditorPlugin.kt
│       │               ├── ApkDecompiler.kt
│       │               ├── SmaliEditor.kt
│       │               └── ...
│       └── res/
│           ├── values/
│           │   └── plugin_config.xml
│           └── drawable/
│               └── ic_plugin.xml
└── LICENSE
```

### AndroidManifest.xml 配置

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.apkeditor">

    <application
        android:label="@string/plugin_name"
        android:icon="@drawable/ic_plugin">

        <meta-data
            android:name="mt_plugin_id"
            android:value="com.example.apkeditor" />
        <meta-data
            android:name="mt_plugin_version"
            android:value="1.0.0" />
        <meta-data
            android:name="mt_plugin_name"
            android:value="APK编辑器" />
        <meta-data
            android:name="mt_plugin_author"
            android:value="Example Team" />
        <meta-data
            android:name="mt_plugin_license"
            android:value="GPL-3.0" />
        <meta-data
            android:name="mt_plugin_entry"
            android:value="com.example.apkeditor.ApkEditorPlugin" />
        <meta-data
            android:name="mt_plugin_min_host"
            android:value="1.0.0" />
        <meta-data
            android:name="mt_plugin_permissions"
            android:value="READ_STORAGE,WRITE_STORAGE,APK_EDIT" />

        <service
            android:name=".PluginService"
            android:process=":plugin_apk_editor"
            android:exported="true"
            android:permission="com.mtopensource.permission.BIND_PLUGIN">
            <intent-filter>
                <action android:name="com.mtopensource.plugin.ACTION_BIND" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

### 插件入口类模板

```kotlin
class ApkEditorPlugin : PluginEntry {

    private lateinit var context: PluginContext
    private lateinit var decompiler: ApkDecompiler

    override fun onAttach(context: PluginContext) {
        this.context = context
        this.decompiler = ApkDecompiler(context.cacheDir)

        context.registerFileHandler(ApkFileHandler())
        context.registerEditor(SmaliEditorProvider())
        context.registerMenuItem(
            PluginFeature.MenuItem(
                title = "反编译APK",
                icon = context.getDrawable(R.drawable.ic_decompile),
                condition = { it.extension == "apk" },
                action = { files -> decompileApks(files) }
            )
        )
    }

    override fun onDetach() {
        decompiler.cleanup()
    }

    override fun getInfo(): PluginInfo {
        return PluginInfo(
            id = "com.example.apkeditor",
            name = "APK编辑器",
            version = "1.0.0",
            author = "Example Team",
            description = "提供APK反编译、Smali编辑、重新打包功能",
            license = "GPL-3.0",
            licenseUrl = "https://www.gnu.org/licenses/gpl-3.0.html",
            icon = context.getDrawable(R.drawable.ic_plugin),
            permissions = listOf(
                PluginPermission.READ_STORAGE,
                PluginPermission.WRITE_STORAGE,
                PluginPermission.APK_EDIT
            ),
            minHostVersion = "1.0.0",
            features = listOf(
                PluginFeature.FileEditor(
                    name = "Smali编辑器",
                    supportedExtensions = listOf("smali"),
                    editorFactory = { SmaliEditor() }
                )
            )
        )
    }

    private fun decompileApks(files: List<FileItem>) {
        if (!context.checkPermission(PluginPermission.APK_EDIT)) {
            context.showToast("需要APK编辑权限")
            return
        }

        files.forEach { file ->
            context.showToast("正在反编译: ${file.name}")
            decompiler.decompile(file.uri)
        }
    }
}
```

---

## 许可证隔离策略

### 核心原则

1. **核心代码（Apache-2.0）不得直接链接 GPL 代码**
2. **GPL 代码只能通过 IPC（进程间通信）调用**
3. **每个插件独立声明自己的许可证**
4. **用户安装插件时必须明确知晓插件许可证**

### 隔离矩阵

| 组件 | 许可证 | 集成方式 | 说明 |
|------|--------|---------|------|
| 核心应用 | Apache-2.0 | 主应用 | 完全自研或Apache/MIT组件 |
| Material Files | Apache-2.0 | 直接集成/改造 | 文件管理核心 |
| Apktool | Apache-2.0 | 命令行调用 | APK解包/打包 |
| JADX | Apache-2.0 | 命令行调用 | Java反编译参考 |
| Dalvikus | GPL-3.0 | **独立插件** | DEX/Smali编辑 |
| Amaze File Manager | GPL-3.0 | **独立插件** | 双窗口参考实现 |
| AppManager | GPL-3.0 | **独立插件** | 应用管理功能 |
| 7-Zip-JBinding | LGPL | 动态链接 | 压缩包支持 |

### IPC 隔离实现

```kotlin
class GplComponentBridge(private val context: Context) {

    suspend fun decompileWithGplPlugin(apkPath: String): Result<String> {
        val plugin = PluginManager.getPlugin("com.example.dalvikus")
            ?: return Result.failure(IllegalStateException("Dalvikus插件未安装"))

        val bridge = PluginBridge(context)
        if (!bridge.connect(plugin.packageName)) {
            return Result.failure(IllegalStateException("无法连接插件服务"))
        }

        val params = Bundle().apply {
            putString("apk_path", apkPath)
            putString("output_dir", context.cacheDir.absolutePath)
        }

        return try {
            val result = bridge.execute("decompile", params)
            val outputPath = result.getString("output_path")
            Result.success(outputPath!!)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            bridge.disconnect()
        }
    }
}
```

### 用户告知机制

安装插件时必须显示许可证信息：

```kotlin
class PluginInstallDialog(private val context: Context) {

    fun show(pluginApk: File, onConfirm: () -> Unit, onCancel: () -> Unit) {
        val meta = extractPluginMeta(pluginApk)

        val message = buildString {
            appendLine("作者: ${meta.author}")
            appendLine("版本: ${meta.version}")
            appendLine("许可证: ${meta.license}")
            appendLine()
            appendLine("许可证说明:")
            appendLine(getLicenseWarning(meta.license))
            appendLine()
            appendLine("所需权限:")
            meta.permissions.forEach { appendLine("- $it") }
            appendLine()
            appendLine("是否继续安装?")
        }

        AlertDialog.Builder(context)
            .setTitle("安装插件: ${meta.name}")
            .setMessage(message)
            .setPositiveButton("安装") { _, _ -> onConfirm() }
            .setNegativeButton("取消") { _, _ -> onCancel() }
            .setCancelable(false)
            .show()
    }

    private fun getLicenseWarning(license: String): String {
        return when (license) {
            "GPL-3.0", "GPL-2.0" -> 
                "此插件采用 GPL 许可证。如果您修改并分发此插件，必须公开修改后的源代码。"
            "LGPL" -> 
                "此插件采用 LGPL 许可证。您可以将其作为插件使用，但修改后需开源修改部分。"
            "Apache-2.0" -> 
                "此插件采用 Apache-2.0 许可证，可自由使用和修改。"
            "MIT" -> 
                "此插件采用 MIT 许可证，限制最少，可自由使用。"
            else -> 
                "请查看插件详情了解许可证要求。"
        }
    }
}
```

---

## 官方插件清单

### 已规划插件

| 插件ID | 名称 | 功能 | 计划许可证 | 依赖组件 |
|--------|------|------|-----------|---------|
| com.mtopensource.plugin.apk-editor | APK编辑器 | 反编译/编辑/重新打包 | Apache-2.0 | Apktool, JADX |
| com.mtopensource.plugin.dex-editor | DEX编辑器 | Smali编辑、DEX修改 | GPL-3.0 | Dalvikus |
| com.mtopensource.plugin.root-support | Root支持 | 系统目录访问 | Apache-2.0 | libsu |
| com.mtopensource.plugin.remote-storage | 远程存储 | FTP/SFTP/WebDAV/SMB | Apache-2.0 | Apache Commons Net |
| com.mtopensource.plugin.image-tools | 图片工具 | 批量重命名/压缩/转换 | Apache-2.0 | 自研 |
| com.mtopensource.plugin.text-tools | 文本工具 | 编码转换/批量替换/对比 | Apache-2.0 | java-diff-utils |
| com.mtopensource.plugin.terminal | 终端模拟器 | Shell脚本执行 | GPL-3.0 | Termux |

### 插件开发优先级

Phase 1 (MVP):
- apk-editor (基础APK操作)
- root-support (Root文件访问)

Phase 2:
- dex-editor (高级APK编辑)
- remote-storage (远程文件管理)
- text-tools (文本处理)

Phase 3:
- image-tools (图片处理)
- terminal (终端模拟)
- theme-pack (主题系统)

---

## 插件发布流程

### 发布渠道

1. GitHub Releases：源码 + 签名APK
2. F-Droid：开源插件仓库
3. 内置插件市场：主应用内提供下载（需审核）

### 发布检查清单

- [ ] 代码通过静态分析（Lint、Detekt）
- [ ] 包含完整的 LICENSE 文件
- [ ] AndroidManifest.xml 包含所有必需的 meta-data
- [ ] 插件图标和描述完整
- [ ] 权限申请合理且最小化
- [ ] 不包含恶意代码或隐私侵犯行为
- [ ] 签名证书安全保管

### 版本管理

采用语义化版本控制 (SemVer)：

版本格式: 主版本号.次版本号.修订号

主版本号: 不兼容的API修改
次版本号: 向下兼容的功能新增
修订号: 向下兼容的问题修正

示例: 1.2.3

### 兼容性策略

- 插件声明 minHostVersion
- 宿主应用维护插件API版本
- 重大API变更时主版本号递增
- 提供插件API兼容性层

---

*最后更新：2026-09-07*

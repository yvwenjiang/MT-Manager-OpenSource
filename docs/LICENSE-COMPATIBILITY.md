# 许可证兼容性指南

> 本文档详细说明 MT管理器开源版 项目中各组件的许可证兼容性策略，确保法律合规。

## 目录

1. [核心许可证策略](#核心许可证策略)
2. [许可证兼容性矩阵](#许可证兼容性矩阵)
3. [组件许可证清单](#组件许可证清单)
4. [GPL 隔离方案](#gpl-隔离方案)
5. [合规检查清单](#合规检查清单)
6. [常见问题](#常见问题)

---

## 核心许可证策略

### 主应用许可证：Apache License 2.0

选择 Apache-2.0 的原因：
- **商业友好**：允许闭源修改和分发
- **专利保护**：包含明确的专利授权条款
- **社区接受度高**：Android 生态主流许可证
- **与 GPL 兼容**：可以组合使用 GPL 组件（但需遵循 GPL 规则）

### 关键原则

1. **核心代码保持 Apache-2.0**：所有直接集成到主应用的代码必须是 Apache-2.0、MIT 或 BSD
2. **GPL 组件必须隔离**：任何 GPL 组件只能通过插件机制使用，不得直接链接
3. **明确标注许可证**：每个文件头部包含 SPDX 许可证标识
4. **保留版权声明**：所有第三方代码保留原始版权声明

---

## 许可证兼容性矩阵

### 直接集成兼容性（用于核心代码）

| 许可证 A | 许可证 B | 兼容性 | 结果许可证 | 说明 |
|---------|---------|--------|-----------|------|
| Apache-2.0 | MIT | 兼容 | Apache-2.0 | MIT 代码可纳入 Apache-2.0 项目 |
| Apache-2.0 | BSD-3 | 兼容 | Apache-2.0 | BSD 代码可纳入 Apache-2.0 项目 |
| Apache-2.0 | Apache-2.0 | 兼容 | Apache-2.0 | 同许可证 |
| Apache-2.0 | LGPL-3.0 | 兼容 | Apache-2.0 | LGPL 代码可动态链接 |
| Apache-2.0 | GPL-3.0 | 兼容 | GPL-3.0 | 直接集成后整体变为 GPL-3.0 |
| Apache-2.0 | GPL-2.0 only | 不兼容 | - | 不能组合使用 |
| MIT | GPL-3.0 | 兼容 | GPL-3.0 | 直接集成后整体变为 GPL-3.0 |
| MIT | GPL-2.0 only | 不兼容 | - | 不能组合使用 |

### 插件隔离兼容性（推荐方案）

| 核心许可证 | 插件许可证 | 兼容性 | 说明 |
|-----------|-----------|--------|------|
| Apache-2.0 | GPL-3.0 | 兼容 | 通过 IPC 隔离，核心保持 Apache-2.0 |
| Apache-2.0 | GPL-2.0 | 兼容 | 通过 IPC 隔离，核心保持 Apache-2.0 |
| Apache-2.0 | LGPL | 兼容 | 通过动态链接或 IPC 隔离 |
| Apache-2.0 | MIT | 兼容 | 可直接集成或作为插件 |
| Apache-2.0 | Apache-2.0 | 兼容 | 可直接集成或作为插件 |

---

## 组件许可证清单

### 核心应用直接集成组件

| 组件 | 用途 | 许可证 | 集成方式 | 状态 |
|------|------|--------|---------|------|
| Material Files | 文件管理架构参考 | Apache-2.0 | 代码改造 | 计划中 |
| Fossify File Manager | 文件操作逻辑参考 | GPL-3.0 | **不直接集成** | 仅参考 |
| libsu | Root权限管理 | Apache-2.0 | Gradle依赖 | 已确认 |
| Glide | 图片加载 | BSD | Gradle依赖 | 已确认 |
| ExoPlayer | 媒体播放 | Apache-2.0 | Gradle依赖 | 已确认 |
| zip4j | ZIP文件处理 | Apache-2.0 | Gradle依赖 | 已确认 |
| Apache Commons Compress | 压缩格式支持 | Apache-2.0 | Gradle依赖 | 已确认 |
| Apache Commons Net | FTP客户端 | Apache-2.0 | Gradle依赖 | 已确认 |
| JSch | SFTP客户端 | BSD | Gradle依赖 | 已确认 |
| Sardine | WebDAV客户端 | Apache-2.0 | Gradle依赖 | 已确认 |
| Squircle CE | 文本编辑器参考 | Apache-2.0 | 代码改造 | 计划中 |
| java-diff-utils | 文本对比 | Apache-2.0 | Gradle依赖 | 已确认 |
| apksig | APK签名 | Apache-2.0 | Gradle依赖 | 已确认 |

### 插件化组件

| 组件 | 用途 | 许可证 | 插件类型 | 状态 |
|------|------|--------|---------|------|
| Apktool | APK反编译/打包 | Apache-2.0 | APK编辑器插件 | 计划中 |
| JADX | Java反编译 | Apache-2.0 | APK编辑器插件 | 计划中 |
| Dalvikus | DEX/Smali编辑 | GPL-3.0 | DEX编辑器插件 | 计划中 |
| smali/baksmali | Smali汇编/反汇编 | BSD-3 | DEX编辑器插件 | 计划中 |
| Termux | 终端模拟器 | GPL-3.0 | 终端插件 | 规划中 |
| AppManager | 应用管理 | GPL-3.0 | 应用管理插件 | 规划中 |

### 独立工具调用

| 工具 | 用途 | 许可证 | 调用方式 | 状态 |
|------|------|--------|---------|------|
| zipalign | APK对齐优化 | Apache-2.0 | Runtime.exec | 已确认 |
| aapt2 | 资源编译 | Apache-2.0 | Runtime.exec | 已确认 |
| keytool | 密钥管理 | GPL-2.0+ | Runtime.exec | 已确认 |

---

## GPL 隔离方案

### 为什么必须隔离 GPL 组件？

GPL 许可证的核心要求（传染性）：
- 任何与 GPL 代码链接/组合的程序，必须整体采用 GPL 许可证
- 必须提供完整的源代码
- 修改后的代码必须同样以 GPL 发布

如果我们直接集成 GPL 代码到核心应用：
- 核心应用必须从 Apache-2.0 变为 GPL-3.0
- 所有基于核心应用的修改都必须开源
- 失去商业友好性

### 隔离技术方案

#### 方案一：独立进程插件（推荐）

```
核心应用 (Apache-2.0)          插件应用 (GPL-3.0)
     |                                |
     |---- AIDL/LocalSocket ---->     |
     |                                |
     |<--- 返回结果 -----------------  |
```

**实现方式**：
1. GPL 组件打包为独立 APK 插件
2. 插件运行在自己的进程中
3. 核心应用通过 AIDL 接口调用插件功能
4. 插件返回处理结果

**法律效果**：
- 核心应用和插件是两个独立程序
- 通过标准 IPC 机制通信
- 不构成 "衍生作品"
- 核心应用保持 Apache-2.0

**代码示例**：

```kotlin
// 核心应用中的调用封装
class GplPluginBridge(private val context: Context) {

    private var serviceConnection: ServiceConnection? = null
    private var pluginInterface: IPluginInterface? = null

    suspend fun connect(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val intent = Intent().apply {
            component = ComponentName(packageName, "$packageName.PluginService")
        }

        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                pluginInterface = IPluginInterface.Stub.asInterface(service)
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                pluginInterface = null
            }
        }

        context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        serviceConnection = conn

        // 等待连接
        var retries = 50
        while (pluginInterface == null && retries-- > 0) {
            delay(100)
        }

        pluginInterface != null
    }

    suspend fun execute(command: String, params: Bundle): Bundle {
        return pluginInterface?.executeCommand(command, params)
            ?: throw IllegalStateException("插件未连接")
    }

    fun disconnect() {
        serviceConnection?.let { context.unbindService(it) }
        serviceConnection = null
        pluginInterface = null
    }
}
```

#### 方案二：命令行调用

```
核心应用 (Apache-2.0)          独立工具 (GPL-3.0)
     |                                |
     |---- Runtime.exec() ----->     |
     |                                |
     |<--- 标准输出/文件 ----------   |
```

**实现方式**：
1. GPL 工具作为独立可执行文件
2. 核心应用通过 Runtime.exec() 调用
3. 通过标准输入输出或文件交换数据

**适用场景**：
- Apktool（命令行工具）
- JADX（命令行工具）
- zipalign（命令行工具）

**代码示例**：

```kotlin
class ApktoolWrapper(private val context: Context) {

    private val apktoolJar = File(context.filesDir, "tools/apktool.jar")

    suspend fun decompile(apkPath: String, outputDir: String): Result<Unit> {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf(
                "java", "-jar", apktoolJar.absolutePath,
                "d", apkPath,
                "-o", outputDir,
                "-f"
            ))

            val exitCode = process.waitFor()
            if (exitCode == 0) {
                Result.success(Unit)
            } else {
                val error = process.errorStream.bufferedReader().readText()
                Result.failure(RuntimeException("Apktool 失败: $error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

#### 方案三：动态链接（仅适用于 LGPL）

```
核心应用 (Apache-2.0)          动态库 (LGPL)
     |                                |
     |---- dlopen()/System.load() --> |
     |                                |
```

**适用场景**：
- 7-Zip-JBinding（LGPL）
- 其他 LGPL 原生库

**要求**：
- 用户必须能够替换 LGPL 库为修改版本
- 库文件必须单独分发
- 不能静态链接

---

## 合规检查清单

### 新组件引入检查

引入任何第三方组件前必须完成以下检查：

- [ ] 确认组件的准确许可证（SPDX 标识）
- [ ] 检查许可证是否与核心应用兼容
- [ ] 检查组件的依赖树许可证
- [ ] 确认组件的专利条款
- [ ] 保留组件的版权声明和 LICENSE 文件
- [ ] 在文档中记录组件使用情况
- [ ] 如果是 GPL 组件，确认已设计隔离方案

### 代码提交检查

每次提交前检查：

- [ ] 新文件包含 SPDX 许可证标识
- [ ] 第三方代码保留原始版权声明
- [ ] 没有意外引入 GPL 代码到核心模块
- [ ] 插件代码正确标注自己的许可证

### 发布检查

每次发布前检查：

- [ ] 所有第三方组件的 LICENSE 文件包含在发布包中
- [ ] 源代码中包含所有必要的版权声明
- [ ] 插件的许可证信息正确显示
- [ ] 用户协议中包含许可证说明

---

## 常见问题

### Q: 为什么不能把 GPL 代码直接复制到核心应用中？

A: GPL 的传染性要求：任何与 GPL 代码组合/链接的程序必须整体采用 GPL。这意味着核心应用将从 Apache-2.0 变为 GPL-3.0，所有基于核心应用的修改都必须开源。

### Q: 通过 AIDL 调用 GPL 插件是否合法？

A: 是的。FSF（自由软件基金会）明确说明：通过标准 IPC 机制（如管道、socket、命令行）通信的两个独立程序不构成衍生作品。AIDL 属于标准的 Android IPC 机制。

### Q: 如果插件是 GPL，用户修改插件后需要开源吗？

A: 是的。如果用户修改了 GPL 插件并分发给他人，必须提供修改后的源代码。但这不影响核心应用的 Apache-2.0 许可证。

### Q: 可以在核心应用中使用 GPL 工具的命令行版本吗？

A: 可以。通过 Runtime.exec() 调用 GPL 工具属于 "聚合"（aggregation），不构成衍生作品。核心应用保持原有许可证。

### Q: LGPL 和 GPL 有什么区别？

A: LGPL（Lesser GPL）允许非 GPL 程序通过动态链接使用 LGPL 库。但修改 LGPL 库本身仍需开源。GPL 则要求任何链接/组合的程序都必须是 GPL。

### Q: 如果我发现有人违反了许可证要求怎么办？

A: 首先联系违规方要求纠正。如果无效，可以联系版权持有者（通常是项目维护者）采取法律行动。作为开源项目，我们应优先通过社区沟通解决。

---

## 参考资源

- [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- [GNU GPL v3.0](https://www.gnu.org/licenses/gpl-3.0.html)
- [GNU LGPL v3.0](https://www.gnu.org/licenses/lgpl-3.0.html)
- [MIT License](https://opensource.org/licenses/MIT)
- [FSF: GPL FAQ](https://www.gnu.org/licenses/gpl-faq.html)
- [OSI: Open Source Licenses](https://opensource.org/licenses)
- [SPDX License List](https://spdx.org/licenses/)

---

*最后更新：2026-09-07*
*维护者：MT管理器开源版法务团队*

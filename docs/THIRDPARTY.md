# 第三方开源项目清单

> 本文档详细列出 MT管理器开源版 项目计划使用的所有第三方开源项目，包括用途、许可证、集成方式和获取地址。

## 目录

1. [核心依赖（直接集成）](#核心依赖直接集成)
2. [UI/UX 组件](#uiux-组件)
3. [文件处理库](#文件处理库)
4. [网络/远程存储库](#网络远程存储库)
5. [媒体处理库](#媒体处理库)
6. [开发工具库](#开发工具库)
7. [插件化组件（独立进程/命令行）](#插件化组件独立进程命令行)
8. [参考项目（仅学习，不直接集成代码）](#参考项目仅学习不直接集成代码)
9. [已排除的组件（许可证不兼容或功能冲突）](#已排除的组件许可证不兼容或功能冲突)

---

## 核心依赖（直接集成）

### 1. AndroidX / Jetpack（Google）

| 组件 | 用途 | 许可证 | 版本 |
|------|------|--------|------|
| `androidx.core:core-ktx` | Kotlin 扩展 | Apache-2.0 | 1.13+ |
| `androidx.appcompat:appcompat` | 兼容支持 | Apache-2.0 | 1.7+ |
| `androidx.lifecycle:lifecycle-*` | 生命周期管理 | Apache-2.0 | 2.8+ |
| `androidx.recyclerview:recyclerview` | 列表视图 | Apache-2.0 | 1.3+ |
| `androidx.fragment:fragment-ktx` | Fragment 支持 | Apache-2.0 | 1.8+ |
| `androidx.preference:preference-ktx` | 设置界面 | Apache-2.0 | 1.2+ |
| `androidx.documentfile:documentfile` | 文档文件访问 | Apache-2.0 | 1.0+ |
| `androidx.security:security-crypto` | 加密存储 | Apache-2.0 | 1.1+ |

**获取地址**：https://developer.android.com/jetpack

---

### 2. Kotlin 标准库与协程（JetBrains）

| 组件 | 用途 | 许可证 | 版本 |
|------|------|--------|------|
| `org.jetbrains.kotlin:kotlin-stdlib` | Kotlin 标准库 | Apache-2.0 | 2.0+ |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 协程支持 | Apache-2.0 | 1.9+ |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | JSON 序列化 | Apache-2.0 | 1.7+ |

**获取地址**：https://github.com/JetBrains/kotlin

---

### 3. Material Design 组件（Google）

| 组件 | 用途 | 许可证 | 版本 |
|------|------|--------|------|
| `com.google.android.material:material` | Material 组件 | Apache-2.0 | 1.12+ |

**获取地址**：https://github.com/material-components/material-components-android

---

## UI/UX 组件

### 4. Jetpack Compose（Google）

| 组件 | 用途 | 许可证 | 版本 |
|------|------|--------|------|
| `androidx.compose.ui:ui` | Compose UI 核心 | Apache-2.0 | 1.7+ |
| `androidx.compose.material3:material3` | Material3 组件 | Apache-2.0 | 1.3+ |
| `androidx.compose.runtime:runtime` | Compose 运行时 | Apache-2.0 | 1.7+ |
| `androidx.compose.foundation:foundation` | Compose 基础 | Apache-2.0 | 1.7+ |

**获取地址**：https://developer.android.com/jetpack/compose

---

### 5. Glide（bumptech）

| 属性 | 内容 |
|------|------|
| **用途** | 图片加载与缓存，用于文件列表缩略图、图片预览 |
| **许可证** | BSD-3-Clause |
| **版本** | 4.16+ |
| **Gradle 依赖** | `com.github.bumptech.glide:glide:4.16.0` |
| **获取地址** | https://github.com/bumptech/glide |
| **集成方式** | Gradle 依赖，直接集成到核心应用 |

---

### 6. ExoPlayer（Google）

| 属性 | 内容 |
|------|------|
| **用途** | 媒体播放器，用于音频/视频文件预览 |
| **许可证** | Apache-2.0 |
| **版本** | 1.4+ (Media3) |
| **Gradle 依赖** | `androidx.media3:media3-exoplayer:1.4.0` |
| **获取地址** | https://github.com/androidx/media |
| **集成方式** | Gradle 依赖，直接集成到核心应用 |

---

## 文件处理库

### 7. Apache Commons Compress（Apache Software Foundation）

| 属性 | 内容 |
|------|------|
| **用途** | 压缩格式支持（ZIP、TAR、7Z、BZIP2、GZIP等） |
| **许可证** | Apache-2.0 |
| **版本** | 1.27+ |
| **Gradle 依赖** | `org.apache.commons:commons-compress:1.27.0` |
| **获取地址** | https://commons.apache.org/proper/commons-compress/ |
| **集成方式** | Gradle 依赖，直接集成到核心应用 |

---

### 8. zip4j（Srikanth Reddy Lingala）

| 属性 | 内容 |
|------|------|
| **用途** | ZIP 文件处理（加密、分卷、注释等高级功能） |
| **许可证** | Apache-2.0 |
| **版本** | 2.11+ |
| **Gradle 依赖** | `net.lingala.zip4j:zip4j:2.11.5` |
| **获取地址** | https://github.com/srikanth-lingala/zip4j |
| **集成方式** | Gradle 依赖，直接集成到核心应用 |

---

### 9. Junrar（Junrar Team）

| 属性 | 内容 |
|------|------|
| **用途** | RAR 文件解压支持 |
| **许可证** | Apache-2.0 |
| **版本** | 7.5+ |
| **Gradle 依赖** | `com.github.junrar:junrar:7.5.5` |
| **获取地址** | https://github.com/junrar/junrar |
| **集成方式** | Gradle 依赖，直接集成到核心应用 |

---

### 10. 7-Zip-JBinding（Boris Brodski）

| 属性 | 内容 |
|------|------|
| **用途** | 7Z 格式支持（通过 JNI 调用 7-Zip） |
| **许可证** | LGPL-2.1（可动态链接） |
| **版本** | 16.02-2.01 |
| **获取地址** | https://github.com/borisbrodski/7-Zip-JBinding |
| **集成方式** | 动态链接（JNI），作为可选功能模块 |
| **注意事项** | LGPL 要求用户可替换库文件，需单独分发 so 文件 |

---

### 11. DocumentFile 扩展（AndroidX）

| 属性 | 内容 |
|------|------|
| **用途** | Scoped Storage 下的文件访问（Android 11+） |
| **许可证** | Apache-2.0 |
| **版本** | 1.0+ |
| **Gradle 依赖** | `androidx.documentfile:documentfile:1.0.1` |
| **获取地址** | AndroidX 官方仓库 |
| **集成方式** | Gradle 依赖，直接集成到核心应用 |

---

## 网络/远程存储库

### 12. Apache Commons Net（Apache Software Foundation）

| 属性 | 内容 |
|------|------|
| **用途** | FTP 客户端实现 |
| **许可证** | Apache-2.0 |
| **版本** | 3.11+ |
| **Gradle 依赖** | `commons-net:commons-net:3.11.1` |
| **获取地址** | https://commons.apache.org/proper/commons-net/ |
| **集成方式** | Gradle 依赖，集成到远程存储插件 |

---

### 13. JSch（MWiede Fork）

| 属性 | 内容 |
|------|------|
| **用途** | SFTP/SSH 客户端 |
| **许可证** | BSD-3-Clause |
| **版本** | 0.2+ |
| **Gradle 依赖** | `com.github.mwiede:jsch:0.2.18` |
| **获取地址** | https://github.com/mwiede/jsch |
| **集成方式** | Gradle 依赖，集成到远程存储插件 |

---

### 14. Sardine（David Kocher / Lookfirst）

| 属性 | 内容 |
|------|------|
| **用途** | WebDAV 客户端 |
| **许可证** | Apache-2.0 |
| **版本** | 5.9+ |
| **Gradle 依赖** | `com.github.lookfirst:sardine:5.9` |
| **获取地址** | https://github.com/lookfirst/sardine |
| **集成方式** | Gradle 依赖，集成到远程存储插件 |

---

### 15. SMBJ（Hierynomus）

| 属性 | 内容 |
|------|------|
| **用途** | SMB/CIFS 客户端（局域网文件共享） |
| **许可证** | Apache-2.0 |
| **版本** | 0.13+ |
| **Gradle 依赖** | `com.hierynomus:smbj:0.13.0` |
| **获取地址** | https://github.com/hierynomus/smbj |
| **集成方式** | Gradle 依赖，集成到远程存储插件 |

---

## 媒体处理库

### 16. AndroidSVG（Paul LeBeau）

| 属性 | 内容 |
|------|------|
| **用途** | SVG 图片渲染（用于图标和矢量图预览） |
| **许可证** | Apache-2.0 |
| **版本** | 1.4+ |
| **Gradle 依赖** | `com.caverock:androidsvg:1.4` |
| **获取地址** | https://github.com/BigBadaboom/androidsvg |
| **集成方式** | Gradle 依赖，直接集成到核心应用 |

---

## 开发工具库

### 17. Hilt（Google）

| 属性 | 内容 |
|------|------|
| **用途** | 依赖注入框架 |
| **许可证** | Apache-2.0 |
| **版本** | 2.52+ |
| **Gradle 依赖** | `com.google.dagger:hilt-android:2.52` |
| **获取地址** | https://github.com/google/dagger |
| **集成方式** | Gradle 依赖 + KSP 插件，直接集成到核心应用 |

---

### 18. Room（Google）

| 属性 | 内容 |
|------|------|
| **用途** | 本地数据库（书签、历史、设置存储） |
| **许可证** | Apache-2.0 |
| **版本** | 2.6+ |
| **Gradle 依赖** | `androidx.room:room-runtime:2.6.1` |
| **获取地址** | https://developer.android.com/jetpack/androidx/releases/room |
| **集成方式** | Gradle 依赖，直接集成到核心应用 |

---

### 19. DataStore（Google）

| 属性 | 内容 |
|------|------|
| **用途** | 类型安全偏好设置存储 |
| **许可证** | Apache-2.0 |
| **版本** | 1.1+ |
| **Gradle 依赖** | `androidx.datastore:datastore-preferences:1.1.1` |
| **获取地址** | https://developer.android.com/topic/libraries/architecture/datastore |
| **集成方式** | Gradle 依赖，直接集成到核心应用 |

---

### 20. java-diff-utils（Wolfgang Meyers）

| 属性 | 内容 |
|------|------|
| **用途** | 文本差异对比算法 |
| **许可证** | Apache-2.0 |
| **版本** | 4.12+ |
| **Gradle 依赖** | `io.github.java-diff-utils:java-diff-utils:4.12` |
| **获取地址** | https://github.com/java-diff-utils/java-diff-utils |
| **集成方式** | Gradle 依赖，集成到文本工具插件 |

---

## 插件化组件（独立进程/命令行）

### 21. Apktool（Connor Tumbleson / iBotPeaches）

| 属性 | 内容 |
|------|------|
| **用途** | APK 反编译和回编译（Smali/资源提取） |
| **许可证** | Apache-2.0 |
| **版本** | 2.9+ |
| **获取地址** | https://github.com/iBotPeaches/Apktool |
| **集成方式** | 命令行调用（`Runtime.exec`），打包在 APK 的 assets 中 |
| **使用场景** | APK 编辑器插件的基础引擎 |
| **注意事项** | 需要 Java 运行时环境（Android 自带 ART，需适配） |

---

### 22. JADX（Skylot）

| 属性 | 内容 |
|------|------|
| **用途** | APK 反编译为 Java 源码（GUI + CLI） |
| **许可证** | Apache-2.0 |
| **版本** | 1.4+ |
| **获取地址** | https://github.com/skylot/jadx |
| **集成方式** | 命令行调用或作为独立插件 APK |
| **使用场景** | APK 编辑器插件的 Java 源码查看功能 |

---

### 23. apksig（Google / AOSP）

| 属性 | 内容 |
|------|------|
| **用途** | APK 签名（V1/V2/V3）和验证 |
| **许可证** | Apache-2.0 |
| **版本** | 随 Android SDK 版本更新 |
| **获取地址** | https://android.googlesource.com/platform/tools/apksig/ |
| **集成方式** | Gradle 依赖（`com.android.tools.build:apksig`） |
| **使用场景** | APK 编辑器插件的签名功能 |

---

### 24. zipalign（Google / AOSP）

| 属性 | 内容 |
|------|------|
| **用途** | APK 字节对齐优化 |
| **许可证** | Apache-2.0 |
| **版本** | 随 Android SDK 版本更新 |
| **获取地址** | Android SDK Build Tools |
| **集成方式** | 命令行调用（`Runtime.exec`） |
| **使用场景** | APK 编辑器插件的优化功能 |

---

### 25. Dalvikus（Loerting）

| 属性 | 内容 |
|------|------|
| **用途** | DEX/Smali 编辑器（Compose Multiplatform） |
| **许可证** | GPL-3.0 |
| **版本** | 最新 |
| **获取地址** | https://github.com/loerting/dalvikus |
| **集成方式** | **独立插件 APK**，通过 AIDL IPC 调用 |
| **使用场景** | DEX 编辑器插件的核心编辑功能 |
| **注意事项** | GPL-3.0，必须作为独立进程插件，不能与核心代码直接链接 |

---

### 26. smali / baksmali（Google / JesusFreke）

| 属性 | 内容 |
|------|------|
| **用途** | Smali 汇编器和反汇编器 |
| **许可证** | BSD-3-Clause |
| **版本** | 3.0+ |
| **获取地址** | https://github.com/google/smali |
| **集成方式** | 命令行调用或 Gradle 依赖 |
| **使用场景** | DEX 编辑器插件的 Smali 转换功能 |

---

### 27. libsu（John Wu / topjohnwu）

| 属性 | 内容 |
|------|------|
| **用途** | Root 权限管理（Magisk 作者出品） |
| **许可证** | Apache-2.0 |
| **版本** | 5.3+ |
| **Gradle 依赖** | `com.github.topjohnwu.libsu:core:5.3.0` |
| **获取地址** | https://github.com/topjohnwu/libsu |
| **集成方式** | Gradle 依赖，集成到 Root 支持插件 |
| **使用场景** | Root 支持插件与 Magisk/SuperSU 通信 |

---

### 28. Termux（Termux Team）

| 属性 | 内容 |
|------|------|
| **用途** | 终端模拟器和 Linux 环境 |
| **许可证** | GPL-3.0 |
| **版本** | 0.118+ |
| **获取地址** | https://github.com/termux/termux-app |
| **集成方式** | **独立插件 APK** 或作为可选安装 |
| **使用场景** | 终端模拟器插件的 Shell 执行环境 |
| **注意事项** | GPL-3.0，必须作为独立应用/插件 |

---

## 参考项目（仅学习，不直接集成代码）

以下项目提供了重要的架构参考和灵感，但**不会直接复制其代码**：

### 29. Material Files（Hai Zhang）

| 属性 | 内容 |
|------|------|
| **用途** | 文件管理架构、Material Design 实现参考 |
| **许可证** | Apache-2.0 |
| **获取地址** | https://github.com/zhanghai/MaterialFiles |
| **参考内容** | VFS 架构、Root 文件系统实现、FTP/SFTP 客户端设计 |
| **使用方式** | 阅读源码学习架构，不直接复制代码 |

---

### 30. Amaze File Manager（TeamAmaze）

| 属性 | 内容 |
|------|------|
| **用途** | 双窗口文件管理器设计参考 |
| **许可证** | GPL-3.0 |
| **获取地址** | https://github.com/TeamAmaze/AmazeFileManager |
| **参考内容** | 双面板交互、压缩包处理、云存储扩展架构 |
| **使用方式** | 仅参考交互设计，不集成代码（GPL-3.0 不兼容） |

---

### 31. Fossify File Manager（FossifyOrg）

| 属性 | 内容 |
|------|------|
| **用途** | 简洁文件管理器设计参考 |
| **许可证** | GPL-3.0 |
| **获取地址** | https://github.com/FossifyOrg/File-Manager |
| **参考内容** | 隐私导向设计、文件夹加密功能 |
| **使用方式** | 仅参考功能设计，不集成代码 |

---

### 32. Squircle CE（Massive Madness）

| 属性 | 内容 |
|------|------|
| **用途** | 代码编辑器设计参考 |
| **许可证** | Apache-2.0 |
| **获取地址** | https://github.com/massivemadness/SquircleCE |
| **参考内容** | 文本编辑器架构、语法高亮实现、大文件处理 |
| **使用方式** | 学习编辑器架构，部分组件可改造后集成 |

---

### 33. MP Manager（Abdurazaaq Mohammed）

| 属性 | 内容 |
|------|------|
| **用途** | APK 编辑器设计参考 |
| **许可证** | 未知（需确认） |
| **获取地址** | https://github.com/AbdurazaaqMohammed/MP-Manager |
| **参考内容** | APK 编辑工作流、反编译/编译流程设计 |
| **使用方式** | 参考功能设计和工作流 |

---

### 34. MT2（AutFeng）

| 属性 | 内容 |
|------|------|
| **用途** | 仿 MT 管理器 UI 框架参考 |
| **许可证** | 未知（需确认） |
| **获取地址** | https://github.com/AutFeng/MT2 |
| **参考内容** | 双列表滑动、下拉刷新、九宫格菜单、手势系统 |
| **使用方式** | 参考 UI 交互设计，XML 控件实现思路 |

---

### 35. ZenFile（l930203811）

| 属性 | 内容 |
|------|------|
| **用途** | 双面板文件管理器参考 |
| **许可证** | 未知（需确认） |
| **获取地址** | https://github.com/l930203811/ZenFile |
| **参考内容** | 双面板浏览、多标签页、远程服务器、玻璃拟态 UI |
| **使用方式** | 参考双面板交互和远程存储设计 |

---

## 已排除的组件（许可证不兼容或功能冲突）

### 排除清单

| 组件 | 排除原因 | 替代方案 |
|------|---------|---------|
| **Amaze File Manager（代码）** | GPL-3.0，直接集成会污染核心许可证 | 仅参考设计，自研实现 |
| **Fossify File Manager（代码）** | GPL-3.0，直接集成会污染核心许可证 | 仅参考设计，自研实现 |
| **AppManager（代码）** | GPL-3.0，直接集成会污染核心许可证 | 作为独立插件使用 |
| **Ghost Commander（代码）** | GPL-3.0，直接集成会污染核心许可证 | 仅参考双面板设计 |
| **QuickEdit（代码）** | MIT 但功能重叠 | 自研编辑器或 Squircle CE |
| **HexEdit（代码）** | MIT 但功能简单 | 自研十六进制编辑器 |
| **Recaf（代码）** | MIT 但过于重量级 | 仅参考字节码编辑设计 |
| **bytecode-viewer（代码）** | 未知许可证 | 仅参考反编译集成方案 |
| **ApkToolPlus（代码）** | 未知许可证 | 使用官方 Apktool |
| **ARSC Editor（代码）** | MIT 但功能简单 | 自研 ARSC 编辑器 |
| **AXML Editor（代码）** | MIT 但功能简单 | 自研 AXML 编辑器 |

---

## 依赖关系图

```
核心应用 (Apache-2.0)
├── AndroidX / Jetpack (Apache-2.0)
├── Kotlin 标准库 (Apache-2.0)
├── Material Components (Apache-2.0)
├── Jetpack Compose (Apache-2.0)
├── Glide (BSD-3)
├── ExoPlayer (Apache-2.0)
├── Apache Commons Compress (Apache-2.0)
├── zip4j (Apache-2.0)
├── Junrar (Apache-2.0)
├── 7-Zip-JBinding (LGPL) [动态链接]
├── Hilt (Apache-2.0)
├── Room (Apache-2.0)
├── DataStore (Apache-2.0)
├── AndroidSVG (Apache-2.0)
└── 插件系统 SDK (Apache-2.0)
    ├── APK 编辑器插件 (Apache-2.0)
    │   ├── Apktool (Apache-2.0) [命令行]
    │   ├── JADX (Apache-2.0) [命令行]
    │   ├── apksig (Apache-2.0)
    │   └── zipalign (Apache-2.0) [命令行]
    ├── DEX 编辑器插件 (GPL-3.0) [独立进程]
    │   ├── Dalvikus (GPL-3.0) [独立进程]
    │   └── smali (BSD-3) [命令行]
    ├── Root 支持插件 (Apache-2.0)
    │   └── libsu (Apache-2.0)
    ├── 远程存储插件 (Apache-2.0)
    │   ├── Apache Commons Net (Apache-2.0)
    │   ├── JSch (BSD-3)
    │   ├── Sardine (Apache-2.0)
    │   └── SMBJ (Apache-2.0)
    ├── 文本工具插件 (Apache-2.0)
    │   └── java-diff-utils (Apache-2.0)
    └── 终端插件 (GPL-3.0) [独立进程]
        └── Termux (GPL-3.0) [独立进程]
```

---

## 许可证统计

| 许可证 | 组件数量 | 集成方式 |
|--------|---------|---------|
| Apache-2.0 | 28 | 直接集成 / Gradle 依赖 |
| BSD-3-Clause | 3 | 直接集成 / Gradle 依赖 |
| MIT | 0（已排除直接集成） | 仅参考 |
| LGPL-2.1 | 1 | 动态链接 |
| GPL-3.0 | 3 | 独立进程插件 |
| 未知/待确认 | 3 | 仅参考设计 |

---

## 维护说明

### 新增组件流程

1. 在本文档中添加组件条目
2. 在 `LICENSE-COMPATIBILITY.md` 中评估许可证兼容性
3. 如果是 GPL 组件，设计隔离方案（插件/命令行）
4. 更新依赖关系图
5. 运行许可证检查脚本验证

### 版本更新

- 每季度审查一次第三方组件版本
- 关注安全漏洞公告
- 及时更新到最新稳定版本

---

*最后更新：2026-09-07*
*维护者：MT管理器开源版开发团队*

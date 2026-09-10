# MT管理器开源版 (MT-Manager-OpenSource)

> 🚀 一个由社区驱动、完全免费、功能丰富的 Android 文件管理器，致敬 MT 管理器的设计理念，但走完全开源路线。

## 项目理念

- **纯热爱驱动**：没有任何 VIP 功能，所有核心功能完全免费
- **开源透明**：核心代码完全开源，接受社区审计
- **插件扩展**：通过插件系统解决许可证兼容性问题，保持核心代码的许可证纯净
- **赞助自愿**：仅提供捐赠入口，不影响任何功能使用

## 核心特性

| 模块 | 状态 | 说明 |
|------|------|------|
| 双窗口文件管理 | 🚧 开发中 | 左右双面板、拖拽传输、手势操作 |
| 压缩包管理 | 🚧 开发中 | ZIP/RAR/7Z/TAR 内直接编辑 |
| 文本编辑器 | 🚧 开发中 | 大文件支持、语法高亮、正则搜索 |
| APK 基础操作 | 🚧 开发中 | 查看/提取/签名/优化 |
| 插件系统 | 🚧 开发中 | 支持第三方插件扩展 |
| Root 支持 | 📋 规划中 | 系统目录访问、权限修改 |
| 远程存储 | 📋 规划中 | FTP/SFTP/WebDAV/SMB |
| 媒体预览 | 📋 规划中 | 图片/音乐/视频/字体预览 |

## 技术栈

- **语言**：Kotlin（Android）+ Java（部分底层）
- **最低 API**：Android 8.0 (API 26)
- **目标 API**：Android 16 (API 36)
- **架构**：MVVM + Repository + 插件化架构
- **UI**：Jetpack Compose（主界面）+ 原生 View（高性能场景）

## 项目结构

```
MT-Manager-OpenSource/
├── core/                    # 核心应用（Apache-2.0）
│   ├── app/                # 主应用模块
│   ├── filemanager/        # 文件管理引擎
│   ├── ui/                 # 双窗口UI框架
│   └── common/             # 公共工具库
├── plugin-system/          # 插件系统SDK（Apache-2.0）
│   ├── api/                # 插件API定义
│   ├── host/               # 宿主加载器
│   └── bridge/             # 进程间通信桥
├── plugins/                # 官方插件（各插件独立许可证）
│   ├── apk-editor/         # APK编辑插件
│   ├── dex-editor/         # DEX编辑插件
│   ├── remote-storage/     # 远程存储插件
│   └── root-support/       # Root支持插件
└── docs/                   # 项目文档
```

## 快速开始

### 环境要求
- Android Studio Ladybug (2024.2.1) 或更高版本
- JDK 17+
- Android SDK API 26-36

### 构建步骤
```bash
git clone https://github.com/your-org/MT-Manager-OpenSource.git
cd MT-Manager-OpenSource
./gradlew :core:app:assembleDebug
```

### 安装插件
1. 下载官方插件 APK 或从源码构建
2. 在应用设置中安装插件
3. 重启应用即可使用插件功能

## 许可证

本项目核心代码采用 **Apache License 2.0**，详见 [LICENSE](LICENSE) 文件。

各插件采用各自独立的许可证，详见各插件目录下的 LICENSE 文件。

## 赞助

如果你认可这个项目，可以通过以下方式支持开发：
- [爱发电](https://afdian.net/)
- [GitHub Sponsors](https://github.com/sponsors/)

**所有赞助者将在项目 README 中列出（可选匿名）。**

## 贡献

欢迎提交 Issue 和 PR！请阅读 [CONTRIBUTING.md](docs/CONTRIBUTING.md) 了解贡献规范。

## 致谢

感谢以下开源项目为本项目提供灵感和技术参考：
- [Material Files](https://github.com/zhanghai/MaterialFiles) - 文件管理架构参考
- [Apktool](https://github.com/iBotPeaches/Apktool) - APK反编译引擎
- [JADX](https://github.com/skylot/jadx) - Java反编译参考
- [MT管理器](https://mt2.cn) - 产品设计理念参考

## 免责声明

本项目仅用于学习研究、个人应用修改和界面美化等合法用途。用户需自行承担使用本工具的法律后果。详见 [LEGAL.md](docs/LEGAL.md)。

---

**Made with ❤️ by the open source community.**

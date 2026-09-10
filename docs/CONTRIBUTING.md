# 贡献指南

感谢您对 MT管理器开源版 的兴趣！本项目欢迎各种形式的贡献，包括但不限于代码、文档、测试、设计、翻译等。

## 目录

1. [行为准则](#行为准则)
2. [如何贡献](#如何贡献)
3. [代码贡献流程](#代码贡献流程)
4. [文档贡献](#文档贡献)
5. [翻译贡献](#翻译贡献)
6. [问题报告](#问题报告)
7. [安全漏洞报告](#安全漏洞报告)

---

## 行为准则

参与本项目即表示您同意遵守以下行为准则：

- **尊重他人**：对所有参与者保持礼貌和尊重
- **包容多样**：欢迎不同背景、经验和观点的贡献者
- **建设性沟通**：提供有建设性的反馈，避免人身攻击
- **专注技术**：讨论保持技术导向，避免政治、宗教等无关话题
- **遵守法律**：所有贡献必须遵守相关法律法规

---

## 如何贡献

### 您不需要是编程专家

以下方式都可以为项目做出贡献：

- 🐛 **报告 Bug**：使用应用时发现问题？告诉我们！
- 💡 **提出建议**：有新功能想法？开 Issue 讨论！
- 📝 **完善文档**：发现文档有误或不完善？提交修改！
- 🧪 **测试反馈**：帮助测试新版本，提供反馈！
- 🎨 **UI/UX 设计**：提供界面设计稿或改进建议！
- 🌐 **翻译**：帮助将应用和文档翻译成其他语言！
- 📢 **推广**：向更多人介绍这个项目！
- 💰 **赞助**：通过捐赠支持开发！

---

## 代码贡献流程

### 1. 准备工作

1. **Fork 仓库**
   ```bash
   # 在 GitHub 上点击 Fork 按钮
   ```

2. **克隆您的 Fork**
   ```bash
   git clone https://github.com/YOUR_USERNAME/MT-Manager-OpenSource.git
   cd MT-Manager-OpenSource
   ```

3. **添加上游仓库**
   ```bash
   git remote add upstream https://github.com/your-org/MT-Manager-OpenSource.git
   ```

### 2. 创建分支

```bash
# 从 develop 分支创建功能分支
git checkout develop
git pull upstream develop
git checkout -b feature/your-feature-name
```

分支命名规范：
- `feature/xxx`：新功能
- `fix/xxx`：Bug 修复
- `docs/xxx`：文档更新
- `refactor/xxx`：代码重构
- `license/xxx`：许可证相关

### 3. 开发

1. **遵循编码规范**
   - 阅读 [DEVELOPMENT.md](DEVELOPMENT.md) 了解编码规范
   - 使用 ktlint 格式化代码
   - 确保 Detekt 检查通过

2. **编写测试**
   - 新功能必须包含单元测试
   - Bug 修复必须包含回归测试
   - 测试覆盖率不低于 60%

3. **更新文档**
   - 新功能必须更新相关文档
   - API 变更必须更新 API 文档

### 4. 提交

```bash
# 添加更改
git add .

# 提交（遵循 Conventional Commits 规范）
git commit -m "feat(ui): 添加双窗口拖拽传输功能

- 实现 DragDropController 处理跨窗口拖拽
- 支持复制/移动/解压三种操作
- 添加视觉反馈动画

Closes #123"

# 推送到您的 Fork
git push origin feature/your-feature-name
```

### 5. 创建合并请求 (Pull Request)

1. 在 GitHub 上点击 "New Pull Request"
2. 选择 `your-org/develop` <- `YOUR_USERNAME/feature/your-feature-name`
3. 填写 PR 描述（使用模板）
4. 等待 CI 检查和代码审查

### 6. 代码审查

- 维护者会在 7 个工作日内进行审查
- 根据反馈修改代码
- 审查通过后会被合并到 develop 分支

---

## 文档贡献

### 文档位置

```
docs/
├── ARCHITECTURE.md          # 架构文档
├── PLUGIN-SYSTEM.md         # 插件系统
├── LICENSE-COMPATIBILITY.md # 许可证兼容性
├── LEGAL.md                 # 法律合规
├── ROADMAP.md               # 路线图
└── DEVELOPMENT.md           # 开发指南
```

### 文档规范

- 使用 Markdown 格式
- 中文文档使用全角标点
- 代码块标明语言类型
- 图片使用相对路径

### 提交文档修改

与代码贡献流程相同，但分支名使用 `docs/xxx`。

---

## 翻译贡献

### 应用翻译

1. 在 `core/src/main/res/values-XX/` 目录下创建翻译文件
2. 复制 `values/strings.xml` 并进行翻译
3. 提交 PR

### 文档翻译

1. 在 `docs/translations/` 目录下创建语言子目录
2. 翻译对应的 Markdown 文件
3. 提交 PR

### 支持的语言

| 语言 | 代码 | 状态 |
|------|------|------|
| 简体中文 | zh-CN | 主语言 |
| 繁体中文 | zh-TW | 招募中 |
| English | en | 招募中 |
| 日本語 | ja | 招募中 |
| 한국어 | ko | 招募中 |

---

## 问题报告

### 报告 Bug

使用 GitHub Issues 的 "Bug Report" 模板：

```markdown
## 问题描述
清晰简洁地描述 Bug

## 复现步骤
1. 打开应用
2. 点击 '...'
3. 滚动到 '...'
4. 出现错误

## 期望行为
描述你期望发生的行为

## 实际行为
描述实际发生的行为

## 环境信息
- 设备型号：
- Android 版本：
- 应用版本：
- 是否 Root：

## 截图/日志
如有必要，添加截图或日志
```

### 功能建议

使用 GitHub Issues 的 "Feature Request" 模板：

```markdown
## 功能描述
清晰简洁地描述你想要的功能

## 使用场景
描述这个功能会在什么场景下使用

## 可能的实现方案
如果你有实现思路，请描述

## 替代方案
你是否考虑过其他替代方案？

## 附加信息
任何其他相关信息或截图
```

---

## 安全漏洞报告

**请不要在公开 Issue 中报告安全漏洞！**

### 报告方式

发送加密邮件至：security@mtopensource.org

邮件内容：
- 漏洞描述
- 复现步骤
- 影响范围
- 建议修复方案（如有）

### 处理流程

1. 维护者在 48 小时内确认收到报告
2. 评估漏洞严重性和影响
3. 制定修复方案
4. 在修复完成后公开披露（给予报告者署名）

### 安全漏洞定义

包括但不限于：
- 远程代码执行
- 权限提升
- 数据泄露
- 路径遍历
- 插件沙箱逃逸

---

## 贡献者荣誉

所有贡献者将在以下位置被列出：

- 项目 README 的 "Contributors" 部分
- 发布说明 (Release Notes)
- 项目官网的贡献者页面

### 贡献等级

| 等级 | 条件 | 荣誉 |
|------|------|------|
| 🌱 新芽 | 第一次贡献 | 感谢名单 |
| 🌿 绿叶 | 5+ 次贡献 | 核心贡献者名单 |
| 🌳 大树 | 20+ 次贡献或重大功能 | 维护者提名 |
| ⭐ 明星 | 成为项目维护者 | 项目决策权 |

---

## 赞助者

感谢以下赞助者对项目的支持：

> 赞助者名单将在项目 README 中展示

### 赞助方式

- [爱发电](https://afdian.net/)
- [GitHub Sponsors](https://github.com/sponsors/)
- 加密货币（BTC/ETH，地址见项目页面）

### 赞助用途

所有赞助将用于：
- 开发者时间投入
- 测试设备采购
- 基础设施费用（服务器、域名等）
- 社区活动组织

**赞助者不会获得任何特权，所有功能对所有用户平等开放。**

---

## 联系方式

- **一般问题**：GitHub Discussions
- **Bug 报告**：GitHub Issues
- **安全漏洞**：security@mtopensource.org
- **商务合作**：contact@mtopensource.org

---

再次感谢您对 MT管理器开源版 的支持！

*最后更新：2026-09-07*

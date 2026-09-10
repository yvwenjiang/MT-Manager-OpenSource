# GitHub Actions

## `android-build.yml`

在 GitHub 托管的 `ubuntu-latest` 上构建 Android APK 并运行单元测试。

### 触发方式

| 事件 | 说明 |
|------|------|
| `push` 到 `main` / `master` | 每次提交自动构建 |
| 针对 `main` / `master` 的 PR | 校验改动可构建、测试通过 |
| `workflow_dispatch` | 在 Actions 页面手动点击运行 |

### 产物（Artifacts）

- `mt-manager-debug-apk` — 调试版 APK（可直接安装）
- `mt-manager-release-apk-unsigned` — release 未签名 APK（用于验证 R8 混淆链路）
- `test-reports` — 单元测试 HTML 报告（仅测试失败时保留）

### 为什么不用 `./gradlew`

本仓库刻意**不提交** `gradle/wrapper/gradle-wrapper.jar`（二进制文件），
因此 CI 通过 `gradle/actions/setup-gradle` 提供指定版本的 Gradle（当前为 8.11.1）。

如需在本地使用 wrapper，执行一次即可生成 jar：

```bash
gradle wrapper --gradle-version 8.11.1
./gradlew :core:app:assembleDebug
```

### 镜像源

`gradle.properties` 默认 `useChinaMirrors=true`（面向国内开发者）。
CI runner 位于海外，使用阿里云镜像反而更慢，因此工作流中会先把它改为 `false`。

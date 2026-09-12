# 发布规范（Release Process）

本文档定义 YunmeiVibe 工程（应用显示名「云莓氛围」）的标准化发布流程，涵盖版本号、构建变体、
签名、产物命名、更新日志、GitHub Actions 自动化与开源许可证。

## 目录

1. [版本号规范](#1-版本号规范)
2. [构建变体（Debug / Release）](#2-构建变体debug--release)
3. [签名配置](#3-签名配置)
4. [构建命令](#4-构建命令)
5. [产物命名规则](#5-产物命名规则)
6. [更新日志规范](#6-更新日志规范)
7. [GitHub Actions 自动化](#7-github-actions-自动化)
8. [开源许可证](#8-开源许可证)
9. [发布检查清单](#9-发布检查清单)

## 1. 版本号规范

采用语义化版本 **SemVer**：`主版本.次版本.修订号`（`MAJOR.MINOR.PATCH`）。

| 段 | 含义 |
| --- | --- |
| MAJOR | 不兼容的 API / 协议 / 界面重大变更 |
| MINOR | 向后兼容的新功能 |
| PATCH | 向后兼容的 Bug 修复 |

对应到 Gradle（`app/build.gradle.kts`）：

- `versionName`：语义化版本，如 `0.4.0`。
- `versionCode`：每次发布严格递增 1 的正整数（当前为 `22`）。

**发布前必须**同步修改 `versionName` 与 `versionCode`，并打上 `v{versionName}` 标签
（例如 `v0.4.0`），GitHub Actions 会在标签推送后自动构建并发布。

## 2. 构建变体（Debug / Release）

| 变体 | 用途 | 签名 | 产物 |
| --- | --- | --- | --- |
| `debug` | 开发、内部调试 | SDK 自带的 debug keystore 自动签名 | `app-debug.apk` |
| `release` | 对外分发、上架 | 需手动配置 release 签名（见下） | `app-release.apk` |

说明：

- `debug` 无需任何额外配置即可安装运行。
- `release` 若未配置签名，产物为 `app-release-unsigned.apk`（未签名，**不可安装**）。
  本项目已在 `app/build.gradle.kts` 中支持从 `keystore.properties` 或环境变量读取签名。

## 3. 签名配置

### 3.1 生成密钥（一次性）

```bash
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias yunmei \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

> 请妥善备份 `release.keystore` 与口令。**丢失后无法对已发布版本做增量升级。**

### 3.2 本地签名（`keystore.properties`）

在项目根目录创建 `keystore.properties`（已加入 `.gitignore`，**切勿提交**）：

```properties
storeFile=release.keystore
storePassword=你的store密码
keyAlias=yunmei
keyPassword=你的key密码
```

`storeFile` 支持相对项目根目录的路径。

### 3.3 CI 签名（环境变量）

`app/build.gradle.kts` 在缺少 `keystore.properties` 时回退读取环境变量：

| 环境变量 | 说明 |
| --- | --- |
| `RELEASE_KEYSTORE_FILE` | keystore 文件路径 |
| `RELEASE_KEYSTORE_PASSWORD` | store 密码 |
| `RELEASE_KEY_ALIAS` | 别名 |
| `RELEASE_KEY_PASSWORD` | key 密码 |

GitHub Actions 使用同名 secret 注入（见第 7 节）。

## 4. 构建命令

环境要求见 `README.md`（JDK 21、Android SDK 37）。

```bash
# Debug 变体
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

# Release 变体（已配置签名时）
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk

# 可选：上架 Google Play 用 AAB
./gradlew bundleRelease
# 产物：app/build/outputs/bundle/release/app-release.aab
```

提交前建议先跑校验：

```bash
./gradlew lintDebug assembleDebug
```

## 5. 产物命名规则

统一命名为：

```
YunmeiVibe-v{versionName}-{variant}.apk
```

| 示例 | 说明 |
| --- | --- |
| `YunmeiVibe-v0.4.0-debug.apk` | Debug 变体 |
| `YunmeiVibe-v0.4.0-release.apk` | Release 变体 |

额外约定：

- 每次 Release 同时提供两个 APK 的 **SHA256 校验和**（GitHub Actions 会自动生成 `SHA256SUMS.txt`）。
- 版本号信息以 APK 内的 `versionName` / `versionCode` 为准，文件名只做易读标识。

## 6. 更新日志规范

- 提交信息遵循 **Conventional Commits**：
  `feat:`（新功能）、`fix:`（修复）、`chore:`（杂项）、`docs:`（文档）、`refactor:`（重构）、`perf:`（性能）。
- 更新日志维护在 `CHANGELOG.md`，格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。
- 每个版本条目需附 GitHub 版本对比链接 `https://github.com/JustJoy122/yunmei_vibe/compare/v{上一个版本号}...v{当前版本号}`（首个版本除外），
  并在文件末尾维护 `[x.y.z]: ...` 链接定义，保证 Markdown 渲染时版本号可点击。
- 每次发布将 `[Unreleased]` 段内容归入对应版本号，并注明日期与 `versionCode`。
- 发布说明应面向用户，按 **Added / Changed / Fixed / Removed / Security** 分类。

## 7. GitHub Actions 自动化

本项目有两个工作流，职责严格分离：

| 工作流 | 触发 | 用途 |
| --- | --- | --- |
| `.github/workflows/release.yml` | 推送 `v*` **Tag**、手动 | 正式发布（Debug + Release APK + `SHA256SUMS.txt`） |
| `.github/workflows/build-debug.yml` | push `main`（忽略 `**.md`、`docs/**`）、手动 | 日常 Debug 构建 + `ci-YYYYMMDD-<run_number>` Pre-release（Debug 更新渠道，历史全部保留） |

> [!IMPORTANT] 硬性约束
> `release.yml` **只允许监听 Tag，绝不监听分支**，保证日常 push 永远不会产出正式 Release。
> 日常开发流程、失败日志提取方式与禁止事项见 [开发相关](DEVELOPMENT.md) 的「Git 与 CI 工作流」。

### 7.1 触发方式（release.yml）

- **自动**：推送 `v*` 标签（如 `v0.4.0`）即触发。
- **手动**：仓库 Actions 页 → *Release* → *Run workflow*。

### 7.2 需要配置的 Secrets

在仓库 *Settings → Secrets and variables → Actions* 中添加：

| Secret | 内容 |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | `release.keystore` 的 Base64 编码 |
| `RELEASE_KEYSTORE_PASSWORD` | store 密码 |
| `RELEASE_KEY_ALIAS` | 别名（如 `yunmei`） |
| `RELEASE_KEY_PASSWORD` | key 密码 |

生成 Base64（PowerShell）：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore"))
```

生成 Base64（Git Bash）：

```bash
base64 -w0 release.keystore
```

### 7.3 流程说明

1. 检出代码，配置 JDK 21 与 Gradle 缓存；
2. 接受 Android SDK 许可并安装 `platforms;android-37.0`、`build-tools;36.0.0`；
3. 从 secret 解码 keystore；
4. 构建 `assembleDebug` 与 `assembleRelease`；
5. 按命名规则重命名两个 APK，并生成 `SHA256SUMS.txt`；
6. 通过 `softprops/action-gh-release` 创建 Release 并上传产物。

> 推送标签前请确认 `versionName` 已与标签一致，且签名相关 secret 已配置完成。

## 8. 开源许可证

### 8.1 项目性质与上游约束

本项目的代码来源：

| 来源 | 许可证 | 用途 |
| --- | --- | --- |
| `yunmei_unintelligent-master` | MIT | 后端 / BLE 协议（1:1 移植） |
| `KernelSU-Style-UI-Kit-main` | GPL-3.0 | UI 组件模板 |
| `ui-reference/InstallerX-Revived-main` | GPL-3.0 | 关于页 / 状态卡 / 渐变背景 |

### 8.2 许可证对比

| 许可证 | 类型 | 可否闭源/商用 | 专利条款 | 对本项目是否可行 |
| --- | --- | --- | --- | --- |
| MIT | 宽松 | 可（仅需保留版权声明） | 无 | ❌ 不可（会违反 GPL-3.0 上游） |
| Apache-2.0 | 宽松 + 专利保护 | 可 | 有明确专利授权 | ❌ 不可（不能把 GPL-3.0 代码改发为 Apache-2.0） |
| GPL-3.0 | 强 Copyleft | 衍生作品必须同样 GPL-3.0 开源 | 有（含反 TiVo 化条款） | ✅ **必须选择** |

### 8.3 结论

**本项目必须使用 GPL-3.0**：因为直接复用了 `KernelSU-Style-UI-Kit` 与
`InstallerX-Revived` 的 GPL-3.0 代码，Copyleft 要求整个衍生作品以 GPL-3.0 发布；
而原项目的 MIT 代码可以并入 GPL-3.0 项目（MIT → GPL-3.0 单向兼容）。

建议在仓库根目录添加 `LICENSE` 文件，内容取
[GNU GPL v3 官方文本](https://www.gnu.org/licenses/gpl-3.0.txt)，
SPDX 标识使用 `GPL-3.0-only`。

> 以上为工程层面的许可证兼容性分析，不构成法律意见；如需法律确定性请咨询法务。

## 9. 发布检查清单

- [ ] `app/build.gradle.kts` 中 `versionCode` 已递增、`versionName` 已更新
- [ ] `CHANGELOG.md` 已更新本次变更
- [ ] 本地 `./gradlew lintDebug assembleDebug` 通过
- [ ] 本地 `./gradlew assembleRelease` 通过并产出已签名 APK
- [ ] 已打标签 `v{versionName}` 并推送
- [ ] GitHub Actions Release 构建成功，两个 APK + `SHA256SUMS.txt` 已上传
- [ ] 仓库已包含 `LICENSE`（GPL-3.0）

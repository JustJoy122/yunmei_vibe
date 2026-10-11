# 开发相关

## 技术栈

### 语言与 UI

| 类别 | 技术 | 版本 |
| --- | --- | --- |
| 语言 | Kotlin | 2.4.10 |
| UI | Jetpack Compose (BOM) | 2026.08.00 |
| UI | Material 3 | 1.5.0-alpha26 |
| UI | Miuix（`top.yukonga.miuix.kmp`） | 0.9.3 |
| 取色 | materialKolor（Monet 动态取色） | 5.0.0 |
| 导航 | miuix-nav（`top.yukonga.miuix.kmp:miuix-nav-android`） / NavigationEvent | 0.9.4-103b737b-SNAPSHOT / 1.1.2 |
| 架构组件 | AndroidX Lifecycle / Activity Compose / Core KTX | 2.10.0 / 1.13.0 / 1.16.0 |

### 数据与网络

| 类别 | 技术 | 版本 |
| --- | --- | --- |
| 网络 | Retrofit + OkHttp + kotlinx-serialization | 2.11.0 / 4.12.0 / 1.11.0 |
| 并发 | kotlinx-coroutines | 1.11.0 |
| 加密存储 | AndroidX Security Crypto | 1.1.0 |
| 偏好存储 | DataStore Preferences | 1.1.1 |
| 蓝牙 | FastBleLib（本地 AAR） | 2.3.4 |
| 二维码 | ZXing Core（静态图解码 + 二维码生成） | 3.5.3 |
| 系统反射 | HiddenApiBypass | 6.1 |

### 构建工具链

| 项目 | 版本 |
| --- | --- |
| Gradle | 9.7.1 |
| Android Gradle Plugin (AGP) | 9.3.2 |
| JDK | 21 |

## 软件运行要求

### 系统要求

| 项目 | 值 | 说明 |
| --- | --- | --- |
| 最低系统版本（`minSdk`） | **API 26（Android 8.0）** | 低于该版本无法安装 |
| 目标系统版本（`targetSdk`） | **API 37** | 应用以该版本的行为运行 |
| 编译版本（`compileSdk`） | **API 37** | 构建时使用的 SDK 版本 |

### 硬件要求

| 硬件 | 是否必需 | 用途 |
| --- | --- | --- |
| 蓝牙 BLE | 必需 | 连接门锁、开门 |
| 相机 | 可选 | 扫码添加门锁 |
| NFC | 可选（预留） | 近场通信 |

### 权限说明

| 权限 | 用途 |
| --- | --- |
| 网络（`INTERNET` / `ACCESS_NETWORK_STATE`） | 登录、拉取门锁、取码、打卡 |
| 定位（`ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`） | BLE 扫描（Android 11 及以下）、定位打卡 |
| 蓝牙（`BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT`） | Android 12+ 运行时蓝牙授权 |
| 相机（`CAMERA`） | 扫码添加门锁 |
| NFC（`NFC`） | 预留 |

## 构建说明

### 环境准备

- **JDK 21**（Android Studio 自带的 `jbr` 即可）。
- **Android SDK 37**。

### 构建命令

Git Bash：

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew assembleDebug
```

Windows PowerShell：

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```

产物输出至 `app/build/outputs/apk/debug/app-debug.apk`。

> 提交前建议先跑 `./gradlew lintDebug assembleDebug` 校验。

### 版本基线说明

版本基线：**AGP 9.3.2 / Kotlin 2.4.10 / compileSdk 37 / minSdk 26**。

UI 依赖组合（compose-bom 2026.08.00 → Compose 1.12.0 稳定版、material3 1.5.0-alpha26、
miuix 0.9.4-103b737b-SNAPSHOT、materialKolor 5.0.0、NavigationEvent 1.1.2）为设备实测可用组合，模板组件零修改编译。

> [!WARNING] 警告
> 不要回退到 compose-bom 2026.05.01：其解析出的 Compose 1.12.0-alpha03 预发布版与 miuix 存在二进制不兼容，会导致启动白屏闪退

## Git 与 CI 工作流
### GitHub Packages 令牌

官方 Miuix 组件（`top.yukonga.miuix.kmp:*`：`miuix-ui` / `miuix-icons` / `miuix-blur` / `miuix-preference` / `miuix-nav`）从 GitHub Packages 拉取，仓库声明见 `settings.gradle.kts` 的 `GitHubPackagesMiuix`（`https://maven.pkg.github.com/compose-miuix-ui/miuix`）。

凭据取值顺序：

1. Gradle 属性 `gpr.user` / `gpr.key`，建议写入本机 `%USERPROFILE%\.gradle\gradle.properties`，不要写进项目；
2. 环境变量 `GITHUB_ACTOR` / `GITHUB_TOKEN`，供 CI 使用；令牌需具备 `read:packages` 权限。

令牌失效时的表现：

- `Received status code 401 from server: Unauthorized`：凭据缺失、拼写错误或令牌已过期/被撤销；
- `Received status code 403 from server: Forbidden`：令牌有效但缺少 `read:packages` 权限，或账号无权访问该包；
- 伴随 `Could not resolve top.yukonga.miuix.kmp:...:0.9.4-103b737b-SNAPSHOT`；
- 本机 Gradle 缓存完整时可能暂时构建成功，清理缓存后必然复现；
- 无状态码的 `Could not find ...-SNAPSHOT` 属于快照在服务端被替换或清理，与令牌无关。

重新配置步骤：

1. 新建具备 `read:packages` 的令牌；
2. 本机写入 `gpr.user` / `gpr.key`；
3. CI 注入 `GITHUB_TOKEN`，`release.yml` 已声明 `permissions: packages: read`；
4. 持续报错时执行 `./gradlew --refresh-dependencies :app:assembleDebug`，必要时清理 `~/.gradle/caches/modules-2/files-2.1/top.yukonga.miuix.kmp` 与对应 `metadata-*` 目录；
5. 用 `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` 或 `:app:assembleRelease` 验证不再出现 401/403。


本项目采用「**本地不构建、Push 后由 GitHub Actions 自动构建**」的流程：日常提交由 CI 产出 Debug APK，
只有**手动打 Tag** 才会触发正式发布。

### 日常开发

```bash
git add -A
git commit -m "fix: ..."
git push          # 推送 main 即触发 Build Debug
```

| 项目 | 说明 |
| --- | --- |
| 触发条件 | push 到 `main`；仅改 `**.md` 与 `docs/**` 时不触发 |
| 工作流 | `.github/workflows/build-debug.yml` |
| 构建 | 配置 JDK 21 + Gradle 缓存（`gradle/actions/setup-gradle`）→ `./gradlew --no-daemon assembleDebug -PbuildStamp=<run_number>` |
| 产物 | Actions 页 Artifacts：`YunmeiVibe-debug-ci<YYYYMMDD>-run<N>`（APK + `SHA256SUMS.txt`，保留 7 天） |
| 失败日志 | 失败时额外上传 `build-logs-run<N>`：`errors-summary.txt`（聚合核心报错）、`gradle-build.log`、`**/build/reports/**`、Gradle daemon 日志 |
| Debug 更新渠道 | 构建成功后自动发布 Pre-release，tag 为 `ci-YYYYMMDD-<run_number>`（如 `ci-20260912-14`），带 `prerelease` + `latest=false`；**历史标签全部保留**，可回溯任意一次构建 |
| 更新检查 | Debug 包读 `/releases` 列表中 run_number 最大的 `ci-*`，与本地 `BuildConfig.BUILD_STAMP`（CI 由 `-PbuildStamp` 注入，本地构建为 `0`）比较，**远端更大才提示更新**；同一天多次构建也能正确收到提示 |

失败排查：下载 `build-logs-run<N>` → 先看 `errors-summary.txt`（已聚合 `e:` / `FAILURE:` /
`* What went wrong` / `Caused by:` / `Execution failed for task` 等关键行），需要上下文时再查 `gradle-build.log`。

### 发布正式版本

正式版本**只由用户手动打 Tag** 触发，CI 不会自动打 Tag、也不会按分支发布：

```bash
git tag v0.4.3
git push origin v0.4.3    # 只推单个 tag → 触发 Release 工作流
```

`.github/workflows/release.yml` 的触发条件只有 `v*` Tag 与 `workflow_dispatch`，不含任何 `branches:`。

### 禁止事项

- ❌ **`git push --tags`**：会一次性推送本地全部 Tag，可能误触发 Release 工作流并发布半成品；
  请始终使用 `git push origin vX.Y.Z` 只推单个 Tag。
- ❌ 给 `release.yml` 添加 `branches:` 触发条件：日常 push 必须永远不会产出正式 Release。
- ❌ 改了 `versionCode` / `versionName` 却忘记补 `CHANGELOG.md` 对应版本段：Release 说明由 CI 从
  `CHANGELOG.md` 抽取，缺失时会提示"未找到对应条目"。

### 工作流一览

| 工作流 | 触发 | 产物 |
| --- | --- | --- |
| `build-debug.yml` | push `main`（忽略 `**.md`、`docs/**`）、手动 | Debug APK artifact（7 天）+ Pre-release `ci-YYYYMMDD-<run_number>` |
| `release.yml` | Tag `v*`、手动 | Debug / Release APK + `SHA256SUMS.txt` → 正式 Release |

## 项目结构

```
app/src/main/java/com/yunmei/vibe/
├── YunMeiApp.kt             # Application：崩溃捕获、AppContainer、快捷方式同步
├── core/di/AppContainer.kt  # 轻量手动依赖容器
├── data/
│   ├── network/             # Retrofit 接口（login / userschool / getuserlock / sign / lockpassword）
│   ├── model/               # 数据模型（Lock、登录与打卡相关 DTO）
│   ├── repository/          # YunMeiRepository：鉴权、会话、业务聚合
│   ├── ble/UnlockManager.kt # BLE 开门状态机与进度回调
│   ├── local/               # SecureStore（EncryptedSharedPreferences）/ AccountStore / LockStore
│   ├── location/            # LocationManager 封装（打卡定位）
│   ├── qrcode/              # ZXing 二维码编解码
│   ├── preferences/         # DataStore 偏好与 SharedPreferences 设置
│   ├── security/Md5.kt      # 与服务端约定一致的 MD5
│   └── backup/              # 备份与还原（口令加密的备份文件）
└── ui/
    ├── MainActivity.kt      # 主导航容器（miuix-nav 返回栈 + tab Pager + 预测性返回过渡）
    ├── theme/               # TemplateTheme 与 UiMode（Material / Miuix）、语义色
    ├── component/           # 通用组件（material / miuix / setting / scan / backup / liquid / filter 等）
    ├── navigation/          # Route 定义（@Serializable）与 Navigator，构建在 miuix-nav 之上
    ├── animation/           # 预测性返回过渡
    ├── util/                # 权限、日志、模糊、角标等工具
    ├── viewmodel/           # MainActivityViewModel、SettingsViewModel 等
    ├── sign/                # 打卡：快捷方式、跳板、定位方式弹窗、通知、前台服务
    ├── unlock/              # 开门：快捷方式、令牌校验、通知、前台服务、小米超级岛
    └── screen/              # 各页面（home / locks / settings / themesettings / login / lockdetail / about / license）
```

页面遵循 `Screen + UiState/Actions + Material/Miuix 双实现 + ViewModel` 的分层；跨页共享状态由 ViewModel 与 CompositionLocal（`LocalUiMode`、`LocalEnableBlur` 等）承担。

## 架构

- **导航**：使用 `miuix-nav`（`top.yukonga.miuix.kmp:miuix-nav-android`）提供返回栈与预测性返回过渡；路由是 `@Serializable` 的类型安全定义（`ui/navigation/Routes.kt`）。Activity 重建后由 `PendingRouteHolder` 恢复所在页面。
- **主题**：`UiMode`（Material / Miuix）经 `LocalUiMode` 与 `TemplateTheme` 分派；切换主题会重建 `content` 子树，因此导航器由 `MainActivity` 持有，位于主题之外。
- **弹窗**：Material 与 Miuix 共用同一份选项行组件 `NavigationItemWidget`（`ui/component/setting/NavigationItemWidget.kt`），外壳按主题分派；打卡定位弹窗是 Compose 原生 `Dialog`（无默认宽度约束）+ 全屏 Box 底部对齐 + 顶部视觉把手。
- **通知**：开门与打卡各持固定通知 ID（1001 / 1002）与独立渠道；进行中、失败与结束状态统一使用实况渠道；实况短文本用 `setShortCriticalText`，小米超级岛用 `MiIslandNotification.extras(...)` 的短文本槽位。
- **快捷方式与跳板**：动态快捷方式（开门 / 打卡）与通知动作都经带令牌校验的透明跳板 Activity 启动前台服务，跳板在前台调用 `startForegroundService`。

## 与 [原项目](https://github.com/zxy19/yunmei_unintelligent) 的兼容性

- 分享协议：`https://yunmeiui.xypp.cc/#/lock_info/<Base64>`（9 段 `|` 格式）可双向解析，
  支持 `addlock/`、`lock_id/`、`lock_info/`、`lockInfo/` 前缀与 4 段旧格式。
- 开门帧：`0xD0 | 总长(secret+14) | secret | 0xA5 | 6位密码逆序 | "ID01" | 0xA7`；
  notify 特征 = 写特征 UUID 替换 `6E400002 → 6E400003`；电量按 `0xAA`（直接百分比）
  与 `0xAB`（`round(100*(ab-40)/24)`，优先）解析；扫描模式开门成功后写回真实 MAC。
- 接口：`https://base.yunmeitech.com/` + 学校 `serverUrl`，鉴权头
  `x-requested-with / token_data / token_userId / tokenUserId`，密码一律 MD5。
- 本地存储：加密文件沿用 `yunmei_secure`（AES256_SIV/GCM），功能开关存 DataStore
  （键语义与原 `storage` SharedPreferences 一致）。

## 开发约定

### 通知实况与渠道纪律

- **同一通知全程使用同一 ID、同一渠道**：开门固定 `UNLOCK_NOTIFICATION_ID = 1001`、打卡固定 `SIGN_NOTIFICATION_ID = 1002`；进行中、失败、结束都留在各自的 `*_live_channel`。
- **禁止中途换渠道**：渠道是通知身份的一部分，"同 ID 换渠道"会被系统视为另一条通知，已提升的实况（流体云）随即消失。进行中、失败与结束状态都使用实况渠道。
- `*_progress_channel` / `*_result_channel` 仅用于 `ensureChannel()` 建渠道，不作为发送渠道；新增状态时使用实况渠道。
- 实况相关属性每帧都要带上：`setOngoing(true)`（进行中与失败态）、`setOnlyAlertOnce(true)`（进行中）、以及 `EXTRA_REQUEST_PROMOTED_ONGOING = true`（androidx 的 `setRequestPromotedOngoing` 在本工程解析不到，统一用 extra 写法）。
- 等待用户操作时，实况短文本用 `setShortCriticalText(getString(R.string.notification_short_text_pending))`（"待处理"）；超级岛走 `MiIslandNotification.extras(..., shortText = ...)`。

### 跳板 Activity 模式

- 凡是由**通知动作**或**快捷方式**触发的后台动作（开门、打卡、重试、完成），一律用 `PendingIntent.getActivity` 打开一个无 UI 的跳板 Activity，再由跳板在前台 `ContextCompat.startForegroundService(...)`，然后立即 `finish()`。
- 原因：Android 12+ 禁止应用在后台启动前台服务，`PendingIntent.getForegroundService` 会被系统拒绝且不报错，表现为"点了没反应"。
- 跳板列表：`ui/sign/SignTrampolineActivity`、`ui/unlock/UnlockTrampolineActivity`；后者与快捷方式跳板一样做令牌校验（`UnlockShortcut.EXTRA_TOKEN`）。
- 需要运行时权限的动作（如打卡的定位权限、开门的蓝牙权限）在**跳板里**申请：用 `shouldShowRequestPermissionRationale` 区分"可再次请求"与"已被永久拒绝"，后者引导到应用详情设置页；被拒则发失败通知，不启动服务。

### 打卡定位弹窗的取舍

- 当前实现：`ui/sign/SignLocationActivity` 的 `SignLocationDialog` = Compose 原生 `Dialog`（`usePlatformDefaultWidth = false`）+ 全屏 `Box` 底部对齐 + 顶部 32dp×4dp 视觉把手；选项行使用移植自 InstallerX 的 `NavigationItemWidget`。
- **弹窗容器选择**：使用 Compose 原生 `Dialog` 可保证两套界面风格下选项点击行为稳定；改用 `ModalBottomSheet` / `BottomSheetDialog` 前需要先验证两端的点击行为。
- 因此该弹窗**没有下滑关闭手势**，只支持点击外部或返回键关闭（`onDismissRequest`）。

## 待办与技术债

### 未在真机验证

- **Android 10（API 29）启动稳定性**：已为此加了两处版本守卫（`ui/component/liquid/InnerShadow.kt` 的 `RenderEffect`/`BlurEffect` 仅 API 31+ 使用、`ui/component/liquid/Lens.kt` 改用本机版本判断以避开 minSdk 33 的 `miuix-blur`），但**没有设备验证**是否已彻底解决启动崩溃。
- **小米超级岛**：`ui/unlock/MiIslandNotification.kt` 的整条路径（设备判定、`miui.focus.param` 渲染、短文本槽位）未在小米机型上验证；打卡侧尚无超级岛路径。
- **通知实况结束态**：见下方「已知问题」，结束状态仍会回落为普通通知。
- **开门通知的「重试」**：在部分 ROM 上"通知动作启动 Activity"可能被拦截；「完成」按钮行为正常。

### 已知问题

- **流体云结束态回落**：通知在成功或失败的结束状态会从流体云回落为普通通知；进行中与失败阶段保持实况。

### 已知风险与妥协


- **未使用标准 ModalBottomSheet**：原因见上文「打卡定位弹窗的取舍」；若要换容器，必须安排一次真机点击验证。
- **`miuix-blur` 在 API < 33 未完全隔离**：`ui/component/miuix/effect/BgEffectBackground.kt`、`ui/screen/about/AboutMiuix.kt`、`ui/component/miuix/effect/BgEffectPainter.kt` 仍直接引用该库（minSdk 33，靠清单 `tools:overrideLibrary` 放行）。仓储层已把 `enableBlur` / `enableFloatingBottomBarBlur` 在低版本强制为 `false`，但彻底方案是把这些调用抽到仅 33+ 加载的独立类。
- **miuix 快照版依赖的可重现性**：`gradle/libs.versions.toml` 中 `miuix = "0.9.4-103b737b-SNAPSHOT"`，快照可能被服务端替换或清理，导致构建不可复现；且依赖走 GitHub Packages，需要有效令牌（见 `docs/DEPENDENCIES.md`）。建议后续锁定正式版本或落地到本地仓库。

### 技术债

- **跳板 Activity 重复逻辑**：`ui/sign/SignShortcutActivity`、`ui/sign/SignTrampolineActivity`、`ui/unlock/UnlockShortcutActivity`、`ui/unlock/UnlockTrampolineActivity` 的"令牌校验 + 前台启动服务 + finish"结构高度相似，可抽公共基类。
- **占位通知写法重复**：`ui/sign/SignService` 与 `ui/unlock/UnlockService` 各自手写"启动前台服务前先贴一条占位通知"的逻辑，可抽公共工具。
- **备份格式版本**：`CURRENT_SCHEMA_VERSION` 为 2；备份功能尚未上线，暂不提供版本迁移逻辑。
- **孤儿偏好键**：旧版本写入的 `record_object` 键可能仍残留在 DataStore 中（代码已无引用），无害但未清理。

## 说明

- 门锁/账号存储为 JSON 序列化（旧客户端升级不迁移旧格式数据，分享二维码互通）。
- 模板库的 markdown 正文渲染被替换为纯文本实现（本应用弹窗不使用 Markdown），
  其余组件均为模板库原文件（仅包名迁移）。
- `ui/component/miuix/EditText.kt` 增加可选 `visualTransformation` 参数（默认与原版一致），
  用于登录页密码遮罩。

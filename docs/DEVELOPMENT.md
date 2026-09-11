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
| 导航 | Navigation3 / NavigationEvent | 1.1.2 / 1.1.1 |
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
miuix 0.9.3、materialKolor 5.0.0、navigation3 1.1.2）为设备实测可用组合，模板组件零修改编译。

> [!WARNING] 警告
> 不要回退到 compose-bom 2026.05.01：其解析出的 Compose 1.12.0-alpha03 预发布版与 miuix 存在二进制不兼容，会导致启动白屏闪退

## 项目结构

```
app/src/main/java/com/yunmei/client/
├── YunMeiApp.kt             # Application：崩溃兜底 + BleManager 初始化 + AppContainer
├── MainActivity.kt          # 主壳：3 tab Pager + 悬浮底栏/横屏侧栏 + navigation3 二级页
├── core/di/AppContainer.kt  # 手动依赖容器
├── data/                    # 后端数据层（旧项目 1:1 移植）
│   ├── network/             #   Retrofit：login / userschool / getuserlock / sign / lockpassword
│   ├── model/               #   Lock（兼容 |+Base64 分享协议）、登录模型、单位编号表
│   ├── repository/          #   YunMeiRepository（学校 token 切换、重登取码/打卡）
│   ├── ble/UnlockManager.kt #   BLE 开门：快速连接→扫描回退→notify→写 0xD0 帧→电量解析
│   ├── local/               #   SecureStore(EncryptedSharedPreferences)/AccountStore/LockStore
│   ├── location/            #   LocationManager 单次定位（"经度,纬度"）
│   ├── qrcode/              #   ZXing 生成二维码（UTF-8/H/margin2，与原项目一致）
│   ├── preferences/         #   DataStore：quickConnect/autoConnect/autoExit/... 全部功能开关
│   └── security/Md5.kt      #   小写 32 位 MD5
└── ui/
    ├── theme/               # 模板主题系统：TemplateTheme 按 UiMode 分派 Material/Miuix
    ├── component/           # 模板组件（原样复用）：SegmentedList/ExpressiveSwitch/TonalCard/
    │                        #   SnackBar/DropdownItem/EditText/WarningCard/FloatingBottomBar/
    │                        #   dialog(rememberConfirmDialog/rememberLoadingDialog) …
    ├── navigation3/         # 模板导航：Route/Navigator
    ├── viewmodel/           # MainActivityViewModel（主题热切换）、SettingsViewModel
    └── screen/
        ├── home/            # 首页：状态 Banner + 开门/取码/打卡 + 开门相关开关
        ├── locks/           # 门锁：列表/设默认/删除/扫码添加/登录拉取
        ├── settings/        # 主设置页：检查更新/账号/界面风格/主题设置/发送日志/关于
        ├── themesettings/   # 主题设置二级页：样机预览/莫奈/AMOLED/预测性返回/缩放/毛玻璃等
        ├── login/           # 登录：学校选择→门锁选择→保存账号
        ├── lockdetail/      # 门锁详情：UUID/分享二维码/复制链接/设默认/删除
        └── about/           # 关于页（Material / Miuix 两套）
```

每个页面遵循模板库的 `Screen → UiState/Actions → Material/Miuix` 分层，
两个风格变体使用完全相同的 ViewModel 与状态。

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

## 说明

- 门锁/账号存储为 JSON 序列化（旧客户端升级不迁移旧格式数据，分享二维码互通）。
- 模板库的 markdown 正文渲染被替换为纯文本实现（本应用弹窗不使用 Markdown），
  其余组件均为模板库原文件（仅包名迁移）。
- `ui/component/miuix/EditText.kt` 增加可选 `visualTransformation` 参数（默认与原版一致），
  用于登录页密码遮罩。

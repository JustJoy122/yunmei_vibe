# 项目依赖

版本与 `gradle/libs.versions.toml` 保持一致；只列出当前实际编译进应用的条目。

## 构建工具链

| 项目 | 版本 | 说明 |
| --- | --- | --- |
| [Android Gradle Plugin](https://developer.android.com/build) | 9.5.0 | `compileSdk` / `targetSdk` 37 |
| [Kotlin Gradle Plugin](https://github.com/JetBrains/kotlin) | 2.4.10 | Compose Compiler、Serialization、Parcelize 插件同版本 |
| Gradle | 9.8.1 | JDK 21 |

## UI 与主题

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| [Jetpack Compose BOM](https://developer.android.com/develop/ui/compose/bom) | 2026.08.00 | `ui` / `ui-graphics` / `material-icons-extended` 由 BOM 统一 |
| [Material 3](https://developer.android.com/develop/ui/compose/designsystems/material3) | 1.5.0-alpha26 | Material 3 Expressive 界面 |
| [Miuix](https://github.com/compose-miuix-ui/miuix) | 0.9.4 | `miuix-ui` / `miuix-icons` / `miuix-blur` / `miuix-preference` / `miuix-nav`（GitHub Packages） |
| [materialKolor](https://github.com/jordond/materialkolor) | 5.0.0 | Monet 动态取色与配色方案生成 |

## 导航与架构组件

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| [NavigationEvent](https://developer.android.com/jetpack/androidx/releases/navigationevent) | 1.1.1 | 预测性返回手势运行时 |
| [Lifecycle](https://developer.android.com/jetpack/androidx/releases/lifecycle) | 2.10.0 | `lifecycle-runtime-ktx` / `runtime-compose` / `viewmodel-compose` |
| [Activity Compose](https://developer.android.com/jetpack/androidx/releases/activity) | 1.13.0 | `ComponentActivity` 与 `setContent` |
| [Core KTX](https://developer.android.com/jetpack/androidx/releases/core) | 1.19.1 | AndroidX 基础扩展 |
| [DataStore Preferences](https://developer.android.com/jetpack/androidx/releases/datastore) | 1.1.1 | 功能开关与主题偏好 |

> `androidx.navigation3` 1.1.2 的版本目录条目保留（与上游模板同形态），当前代码不引用，导航层由 `miuix-nav` 承担。

## 数据与网络

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| [Retrofit](https://github.com/square/retrofit) | 3.0.0 | `retrofit` + `converter-kotlinx-serialization` |
| [OkHttp](https://github.com/square/okhttp) | 5.5.1 | Retrofit 底层传输与头像图片加载 |
| [kotlinx-serialization](https://github.com/Kotlin/kotlinx.serialization) | 1.12.0 | JSON 解析与路由序列化 |
| [kotlinx-coroutines](https://github.com/Kotlin/kotlinx.coroutines) | 1.11.0 | `kotlinx-coroutines-android` |

## 安全、存储与能力

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| [AndroidX Security Crypto](https://developer.android.com/jetpack/androidx/releases/security) | 1.1.0 | `EncryptedSharedPreferences` 加密存储账号与门锁 |
| [AndroidHiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass) | 6.2 | 调用 `ApplicationInfo.setEnableOnBackInvokedCallback` |
| [ZXing Core](https://github.com/zxing/zxing) | 3.5.4 | 分享二维码的生成与静态图解码 |
| [Focus API](https://github.com/xzakota/HyperNotificationFocus) | 1.4 | 小米超级岛（澎湃 OS 焦点通知 V3）参数构建 |

## 蓝牙

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| [FastBle](https://github.com/Jasonchenlijian/FastBle) | 2.3.4 | 本地 AAR（`app/libs/FastBleLib-2.3.4.aar`），提供扫描、连接、读写与通知回调 |

## 许可页

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| [AboutLibraries](https://github.com/mikepenz/AboutLibraries) | 15.2.0 | `aboutlibraries-core` / `aboutlibraries-compose-m3`，驱动「开放源代码许可」页 |

## 设计参考与历史致谢

以下项目**不作为依赖引入**，仅在开发过程中作为 UI 或协议实现的参考：

- [KernelSU](https://github.com/tiann/KernelSU) 与 [KernelSU-Style-UI-Kit](https://github.com/chenaizhang/KernelSU-Style-UI-Kit)：Material / Miuix 双端组件形态与取色 Token 的对照实现。
- [InstallerX Revived](https://github.com/wxxsfxyzm/InstallerX-Revived)：跳板 Activity、通知渠道与预测性返回过渡的早期实现参考。
- [yunmei_unintelligent](https://github.com/zxy19/yunmei_unintelligent)：项目起步阶段的灵感来源；当前实现已完全重写。
- [@PhoenixTreeZh](https://github.com/PhoenixTreeZh) 的 [云莓 Lite](https://github.com/PhoenixTreeZh/yunmei-lite)：早期 BLE 协议的逆向分析参考；当前 BLE 模块独立实现。

> 各库对应的许可证类型（GPL-3.0 / MIT / Apache-2.0）已在应用内「设置 → 关于 → 开放源代码许可」页中展示。

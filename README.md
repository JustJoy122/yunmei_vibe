# 云莓智能 (YunmeiNew)

基于 **KernelSU-Style-UI-Kit** 组件库重写的「云莓智能」Android 客户端。
UI 完全复用模板库组件（Material 3 Expressive + Miuix 双风格可切换），
后端逻辑 1:1 移植自已验证的旧项目 `yunmei_unintelligent-master`（Java）。

## 构建

```bash
# 环境：JDK 21（Android Studio 自带 jbr 即可）、Android SDK 37
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew assembleDebug        # 产物 app/build/outputs/apk/debug/app-debug.apk
```

版本基线：AGP 9.3.2 / Kotlin 2.4.10 / compileSdk 37 / minSdk 26。
UI 依赖（compose-bom 2026.08.00 → Compose 1.12.0 稳定版、material3 1.5.0-alpha26、
miuix 0.9.3、materialKolor 5.0.0、navigation3 1.1.2）为设备实测可用组合，
模板组件零修改编译。注意不要回退到 compose-bom 2026.05.01（其解析出的
Compose 1.12.0-alpha03 预发布版与 miuix 存在二进制不兼容，会导致启动白屏闪退）。

## 架构

```
app/src/main/java/com/yunmei/client/
├── YunMeiApp.kt             # Application：BleManager 初始化 + AppContainer
├── MainActivity.kt          # 主壳：4 tab Pager + 悬浮底栏 + navigation3 二级页
├── core/di/AppContainer.kt  # 手动依赖容器
├── data/                    # 后端数据层（旧项目 1:1 移植）
│   ├── network/             #   Retrofit：login / userschool / getuserlock / sign / lockpassword
│   ├── model/               #   Lock（兼容 |+Base64 分享协议）、登录模型
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
    ├── navigation3/         # 模板导航：Route/Navigator（navigateForResult 回传扫码结果）
    ├── viewmodel/           # MainActivityViewModel（主题热切换）、SettingsViewModel
    └── screen/
        ├── home/            # 首页：当前门锁卡片 + 快捷操作
        ├── locks/           # 门锁：列表/设默认/删除/扫码添加/登录拉取
        ├── unlock/          # 开门：进度环/快速连接/电量/取码/打卡(ask·lst·rel)/自动退出
        ├── settings/        # 主设置页（模板精简布局）：检查更新/账号/界面风格/主题设置/发送日志/关于
        ├── themesettings/  # 主题设置二级页：手机样机预览/深浅色图标栏/莫奈/AMOLED/
        │                   #   预测性返回/色彩风格·标准/界面缩放/模糊·悬浮底栏/云莓功能开关
        ├── login/           # 登录：学校选择→门锁选择→保存账号
        ├── scan/            # 扫码添加（zxing-android-embedded 内嵌 DecodeView，模板顶栏外壳）
        └── lockdetail/      # 门锁详情：UUID/分享二维码/复制链接/设默认/删除
```

每个页面遵循模板库的 `Screen → UiState/Actions → Material/Miuix` 分层，
两个风格变体使用完全相同的 ViewModel 与状态。

## 功能分配

| 页面 | 功能 |
| --- | --- |
| 首页 | 当前（默认）门锁概览；开门/取码/打卡（可被设置隐藏）/扫码添加/门锁管理入口 |
| 门锁 | 门锁列表、设为默认（星标）、删除（确认弹窗）、扫码添加、登录拉取、详情 |
| 开门 | BLE 开门（进度/电量/快速连接开关）、获取开门密码（alwaysCode 回退）、打卡（定位三模式：每次询问/重新定位并保存/使用上次位置）、开门成功自动退出（autoExit） |
| 设置 | 界面风格（Miuix/Material）、深色模式（含莫奈/AMOLED）、背景毛玻璃、悬浮底栏、快速连接、自动开门（与自动退出冲突时三重确认）、自动获取密码、隐藏打卡/密码按钮、强制竖屏、尝试上报开门结果、打卡定位方式、已保存账号管理、关于 |

## 与原项目的兼容性

- 分享协议：`https://yunmeiui.xypp.cc/#/lock_info/<Base64>`（9 段 `|` 格式）可双向解析，
  支持 `addlock/`、`lock_id/`、`lockInfo/` 前缀与 4 段旧格式。
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

# Changelog - 云莓智能

所有值得注意的项目变更都会记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

---
## [Unreleased]

## [0.4.0]

### 添加
- 发布规范文档 `docs/RELEASE.md`：版本号、签名、构建变体、产物命名、更新日志、CI 发布与许可证说明
- GitHub Actions 自动发布工作流 `.github/workflows/release.yml`：推送 `v*` 标签自动构建 Debug / Release 两个 APK，生成 SHA256 校验和并发布，发布说明取自 CHANGELOG
- `CHANGELOG.md`：采用 Keep a Changelog 格式，回填 0.1.0–0.3.9 变更历史
- `LICENSE`：GPL-3.0（受上游 KernelSU-Style-UI-Kit / InstallerX 的 GPL-3.0 约束）
- Release 签名支持：`app/build.gradle.kts` 支持从 `keystore.properties` 或环境变量读取签名

### 更改
- 重写 `README.md`：补齐技术栈、软件运行要求（minSdk 26 / targetSdk 37）、功能分配与项目结构
- `HomeViewModel` 提示文案资源化：硬编码中文移入 `strings.xml`（新增 5 个 `unlock_*` 键）

### 移除
- 删除本地构建日志与依赖树目录 `log/`、`txt/`（可再生，不入库）

### 安全
- `.gitignore` 补充忽略 `log/`、`txt/`、`keystore.properties`、`*.jks`、`*.keystore`
- 发布前安全复查：确认源码不含账号、密码、令牌、门锁凭据等敏感信息

## [0.3.9]

### 添加
- 关于页：Material 主题下 Logo 跟随 Monet 动态取色（使用 `ic_launcher_monochrome` + `primary` tint）
- AboutStatusCard：基于 InstallerX Revived 的状态卡片组件（含动态渐变背景）

### 更改
- Material 关于页完全重写为 InstallerX Revived 实现：
  - 卡片背景使用 `primaryContainer.copy(alpha = 0.15f)` 更淡颜色 + `AnimatedFluidBackground` 动态渐变
  - 卡片边缘阴影使用 `elevatedCardElevation()` 默认档
  - 排版：应用名 `titleMedium`（16sp/24sp/Medium），版本号 `bodyMedium`（14sp/20sp/Normal）
- 桌面图标前景与 monochrome 层再次缩小至上一版的 75%（scale 0.13621，主体约 46.5×42.3dp，位于 66dp 安全区内）
- 版本号表述统一为 `通道 版本名 (版本号)` 格式（如"测试版 0.3.9 (22)"）

### 移除
- Material 关于页的 PackageManager 位图加载代码
- 未使用的 `AppLogo.kt` 与两个占位 drawable

---

## [0.3.8]

### 添加
- `AnimatedFluidBackground`：InstallerX Revived 动态渐变背景组件（原样移植）
- `AboutStatusCard`：InstallerX 状态卡片初始实现
- 新增 `about_channel` / `about_version_info_format` 字符串

### 更改
- Material 关于页重构：ElevatedCard + 动态渐变 + tonal elevation 阴影
- Miuix 关于页保持无卡片、monochrome 蒙版融合全局彩虹背景
- 桌面图标前景与 monochrome 层调整为 `scale 0.18161`（进入 66dp 安全区）

---

## [0.3.7]

### 更改
- 桌面图标复查：确认 `mipmap-anydpi-v26/ic_launcher.xml` 中前景层严格位于 66dp 安全区内
- Miuix 关于页：无外框无卡片，`ic_launcher_monochrome` + `textureBlur` 蒙版融合全局彩虹背景
- Material 关于页：改用卡片 + `AppLogo` 组件居中展示
- 两主题关于页视觉完全区分

---

## [0.3.6]

### 添加
- 应用内图标动态取色逻辑：
  - Material 与 Miuix（莫奈开）：使用系统动态取色
  - Miuix（莫奈关）：回退静态色板（#D4E3FF 底 / #004784 线条）
- `AppLogo` 组件：桌面图标同源的可染色矢量图层

### 更改
- 关于页 Logo 替换为 `AppLogo` 组件，随主题与莫奈开关变化
- 桌面图标安全区修正：前景 Logo 严格限制在 66dp 安全区内（scale 0.18161）
- 前景/背景/monochrome 三层统一为 VectorDrawable XML，无位图

---

## [0.3.5]

### 添加
- 自适应桌面图标（AdaptiveIconDrawable）完整实现：
  - `foreground`：云莓 Logo 路径（#004784 线条）
  - `background`：静态浅色阶背景（#D4E3FF）
  - `monochrome`：纯白图层（支持 Android 13+ 动态取色）
- 基于 lawnicons 源码的 MaterialColorUtilities 色阶算法：
  - 种子色 #5690DA → Tone 90（背景 #D4E3FF）/ Tone 30（线条 #004784）

---

## [0.3.4]

### 更改
- 首页状态 Banner UI 精简：
  - 绿色：主标题"默认门锁已就绪"，副标题显示单位名称，右侧显示门锁号
  - 红色未默认：主标题"未设置默认门锁"，右侧"去设置"
  - 红色无门锁：主标题"未添加门锁"，右侧"去添加"
- 删除旧版门锁信息卡（LockCard）及关联字符串

### 移除
- 旧版首页锁信息卡片（带锁图标、"默认·门锁号"、"未添加门锁"等旧内容）

---

## [0.3.3]

### 添加
- 首页状态 Banner：基于模板 PermissionCard/PermissionCardMiuix 结构移植
- 三态逻辑：
  - 绿色（已设默认）：标题"默认门锁已就绪"，点击进详情
  - 红色（未设默认）：标题"未设置默认门锁"，点击跳门锁管理
  - 红色（无门锁）：标题"尚未添加门锁"，点击跳门锁管理
- `HomeUiState.defaultLock`：仅在门锁管理显式设置时非空（不再用首把锁兜底）

### 更改
- 设为默认逻辑修正：只有门锁管理中明确设置默认门锁，Banner 才显示绿色

---

## [0.3.2]

### 更改
- "隐藏辅助按钮"拆分为两个独立子项：
  - 隐藏打卡按钮（LocationOff 图标）
  - 隐藏密码按钮（KeyOff 图标）
- 两开关独立读写 `hideSign` / `hideCode` 偏好

---

## [0.3.1]

### 更改
- 版本号递增规范：versionCode 14 / versionName 0.3.1（同时递增）

### 修复
- 版本号不同步导致系统无法识别更新、覆盖安装异常的问题

---

## [0.3.0]

### 添加
- 关于页：复用 KernelSU-Style-UI-Kit 完整 UI（动态渐变背景、Logo、版本号、卡片列表）
- `Route.About` 导航路由
- `BgEffectBackground` 与 effect 包七个文件（RuntimeShader 动态渐变，API 35+ 启用，低版本降级）

### 更改
- 关于页入口从"打开 URL"改为 `navigator.push(Route.About)`
- 设置页"关于"列表项删除副标题"打开云莓智能官网"

### 移除
- 无用频道入口（仅保留"查看源代码"）

---

## [0.2.4]

### 添加
- Miuix 设置分栏标题：复用 InstallerX Revived 的 `SmallTitle` 官方组件（14sp Bold + `onBackgroundVariant`）

### 更改
- 功能可见性修正：
  - 悬浮底栏 / 液态玻璃 / 模糊 → 仅 Miuix 模式显示（符合模板实际逻辑）
  - AMOLED → 仅 Material 模式显示
- 设置页分组标题统一：6 组全加标题（通用 / 账号 / 个性化 / 开门行为 / 界面显示 / 其他）

### 移除
- "尝试上报开门结果"功能（后端无实际接口，死代码）
- 所有"（Material 风格生效）"类限定提示语

---

## [0.2.3]

### 修复
- 冷启动底栏切换卡顿：将 ViewModel 存储读取移入 `Dispatchers.IO`
- Miuix 莫奈开关偶发自动返回上一级：
  - `NavDisplay` 全部参数稳定化（`onBack`/`entryDecorators`/`sceneStrategies`/`entryProvider` 跨重组引用稳定）
  - `ThemeController` 添加 `remember` 缓存

---

## [0.2.2]

### 更改
- AMOLED 开关：Material 模式下始终可见且可调（移除 `enabled = isDark` 限制）
- 界面风格描述："选择界面组件风格，立即生效" → "选择界面组件风格"
- 预览图底栏：2 个色块 → 4 个真实图标（首页/门锁/开门/设置），玻璃悬浮形态与液态玻璃开关联动
- 限定显示逻辑：Miuix 模式下隐藏 AMOLED / 模糊 / 悬浮底栏 / 液态玻璃；Material 模式下隐藏莫奈

---

## [0.2.1]

### 更改
- 业务功能开关从"主题设置"二级页全部移回一级设置页
- 一级设置页按功能分组：开门行为 / 界面显示 / 实验功能
- "主题设置"二级页仅保留主题/外观相关设置

---

## [0.2.0]

### 添加
- 主题设置二级页：完整 ColorPaletteScreen（模板原样移植）：
  - 手机样机预览图（实时反映主题变化）
  - 色彩风格 / 色彩标准下拉（TonalSpot/Vibrant/Expressive/Rainbow/... / SPEC_2021/SPEC_2025）
  - 界面缩放滑动条（80%–110%）
  - 预测性返回手势（API 34+）
  - 莫奈取色 / AMOLED 纯黑独立开关
- 模糊 / 悬浮底栏 / 液态玻璃开关迁入主题设置二级页
- 深色模式横排图标选择栏（跟随系统 / 浅色 / 深色）

### 更改
- 主设置页精简为 6 项：检查更新 / 登录切换账号 / 界面风格 / 主题设置 / 发送日志 / 关于
- 深色模式重构：下拉菜单 → 横排图标选择栏（Material `ToggleButton` / Miuix `TabRow`）

---

## [0.1.3]

### 添加
- 游客模式：启动直接进主界面，无门锁不阻塞
- 登录页新增"先看看，暂不登录"入口
- 空状态 UI：首页/门锁页/开门页均显示中性空状态提示，不阻塞导航
- 多学校/多门锁选择列表底部新增"暂不添加，先进入主界面"项

### 更改
- 登录页返回键：使用 `replaceAll([Route.Main])` 强清栈直回主界面
- 登录成功：后台返回空列表也放行，不再报"不存在任何门锁"

### 修复
- 返回键卡死：`NavDisplay` 全局返回兜底，栈只剩登录页时强清回主界面

---

## [0.1.2]

### 修复
- `ClassNotFoundException: MainActivity`：清单中 `.MainActivity` → `.ui.MainActivity`
- 恢复 `MissingClass` lint 检查

---

## [0.1.1]

### 修复
- Compose BOM `2026.05.01` 预发布版本导致白屏闪退：升级至 `2026.08.00`（全栈稳定版 1.12.0）
- `material3` 1.4.0 → 1.5.0-alpha26
- `miuix` 0.9.2 → 0.9.3
- 新增崩溃捕获器：堆栈写入 `crash-latest.txt`

---

## [0.1.0]

### 添加
- 基于 KernelSU-Style-UI-Kit 的全新 UI 框架（Material 3 Expressive / Miuix 双主题）
- 四页架构：首页 / 门锁 / 开门 / 设置
- 模板组件复用：`SegmentedList` 全家桶、`ExpressiveSwitch`、`TonalCard`、`FloatingBottomBar`、`SnackBarHost` 等
- 后端 1:1 移植自 `yunmei_unintelligent-master`：
  - 登录 / 学校列表 / 门锁列表 / 打卡 / 密码获取 5 个接口
  - `0xD0` 开门帧（密码逆序 + `ID01` 尾标）
  - `0xAA/0xAB` 电量解析
  - `|`+Base64 分享协议（兼容多种格式）
  - FastBle 蓝牙库（本地 AAR 依赖）
- 门锁列表管理（设默认 / 删除 / 扫码添加 / 手动导入）
- ZXing 二维码扫码（内嵌取景器）
- `yunmei_secure` 加密存储（EncryptedSharedPreferences）
- 设置页：快速连接 / 自动开门 / 自动退出 / 自动取码 / 隐藏按钮 / 强制竖屏 / 打卡定位方式
- 关于页（初始版本）

### 修复
- `SecureStore.kt`：`inline` 函数访问私有成员编译错误
- `Lock.kt`：可空类型安全调用
- 门锁列表解析：兼容裸数组 / `data` / `list` / `result` 包装格式
- FastBle JCenter 依赖 404：改为本地 AAR 文件依赖
- Miuix 官方依赖不可用：改为 Material3 + MIUI 风格 token 近似
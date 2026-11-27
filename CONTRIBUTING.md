# 贡献指南

感谢你对「云莓氛围」的关注！本项目基于 Vibe Coding 与 AI 辅助开发，非常欢迎任何形式的贡献（Bug 反馈、代码提交、UI 优化等）。

## 开发环境要求
* JDK: 21
* Android SDK: 37
* IDE: Android Studio 或 IntelliJ IDEA

## 本地构建

编译 Debug 包：

./gradlew :app:assembleDebug

编译并验证代码规范（提交前必做）：

./gradlew :app:lintDebug

## 提交流程
1. 将本仓库 Fork 到你的账号下。
2. 基于 main 分支切出新的功能分支：git checkout -b feat/your-feature 或 fix/your-bug。
3. 代码提交请尽量遵循 Conventional Commits 规范（如 feat:, fix:, chore: 等）。
4. 发起 Pull Request 时，请填写 PR 模板中的检查清单。

## 测试与验证
* **UI 改动**：本项目包含 Material 3 和 Miuix 两套主题，任何 UI 改动必须在两套主题下均确认无异常。
* **BLE 与打卡**：涉及底层蓝牙开锁或定位打卡的改动，必须在真机上验证。如果你没有测试条件，请在 PR 中注明。
* **演示数据测试**：如需测试界面，可使用 ./gradlew :app:assembleDebug -Pdemo 构建包含假数据的演示包，避免污染真实账号。
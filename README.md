
<div align="center">
    
# 云莓氛围

**YunmeiVibe**

[![Stars](https://img.shields.io/github/stars/JustJoy122/yunmei_vibe?label=Stars)](https://github.com/JustJoy122/yunmei_vibe)
[![Release](https://img.shields.io/github/v/release/JustJoy122/yunmei_vibe?style=flat-square&color=%233fb950&label=Release)](https://github.com/JustJoy122/yunmei_vibe/releases/latest)
[![下载量](https://img.shields.io/github/downloads/JustJoy122/yunmei_vibe/total?style=social&label=下载量&logo=github)](https://github.com/JustJoy122/yunmei_vibe/releases/latest)<br/>
![GitHub Repo size](https://img.shields.io/github/repo-size/JustJoy122/yunmei_vibe?style=flat-square&color=3cb371)
[![GitHub Repo Languages](https://img.shields.io/github/languages/top/JustJoy122/yunmei_vibe?style=flat-square)](https://github.com/JustJoy122/yunmei_vibe/search?l=c%23)<br/>
[<img alt="下载应用，请到 GitHub" src="https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png" width="180">](https://github.com/JustJoy122/yunmei_vibe/releases/latest/)

</div>

云莓氛围是一款：
- [Java 原项目](https://github.com/zxy19/yunmei_unintelligent) 为主菜
- [DeepSeek API](https://platform.deepseek.com) 为燃料
- [Deepseek 网页端](https://chat.deepseek.com/) 任产品经理
- [DeepSeek Harness](https://www.deepseek.com/harness/) 掌勺
- [Miuix](https://github.com/compose-miuix-ui/miuix) / Material 3 Expressive 双 UI 风格摆盘

的云莓智能第三方 Android 客户端

## 功能特性

- [x] **三页信息架构**：首页 / 门锁 / 设置，开门操作已并入首页。
- [x] **一键开门**：BLE 快速连接 / 扫描自动回退、进度环、门锁电量解析。
- [x] **云端取码与打卡**：获取 6 位开门密码；定位打卡支持「每次询问 / 重新定位并保存 / 使用上次位置」三种方式。
- [x] **门锁管理**：列表、设为默认、删除、详情、扫码添加、登录拉取。
- [x] **账号管理**：登录、保存账号、切换账号。
- [x] **双 UI 风格与主题**：Material 3 Expressive / Miuix 实时切换；Monet 动态取色、AMOLED 纯黑、页面缩放、背景毛玻璃、悬浮底栏、液态玻璃等。
- [x] **兼容旧客户端**：分享二维码 / 链接双向解析。

## 相关文档

- [发布规范](docs/RELEASE.md)：版本号、签名、产物命名、更新日志、CI 发布与许可证说明
- [更新日志](CHANGELOG.md)：v0.1.0-v0.3.9为历史回填，从 v0.4.0 开始为git tag
- [发布规范](docs/DEVELOPMENT.md)：技术栈、软件运行要求、构建说明、项目结构、兼容性

## 功能分配

| 页面 | 功能 |
| --- | --- |
| 首页 | 默认门锁状态 Banner；一键开门（进度环 + 电量）；获取开门密码；打卡（定位三模式）；快速连接 / 自动开门 / 自动退出 / 自动获取密码开关 |
| 门锁 | 门锁列表、设为默认、删除、详情、扫码添加、登录拉取 |
| 设置 | 检查更新；账号（登录 + 已保存账号管理）；界面风格与主题设置；取码与打卡（无绑定账号用首个账号、打卡定位方式）；界面显示（隐藏打卡 / 密码按钮）；发送日志；关于 |

二级页面：登录、扫码、门锁详情、主题设置、关于。

## 致谢
- 感谢 [zxy19](https://github.com/zxy19/yunmei_unintelligent) 大佬和 [团队](https://github.com/zxy19/yunmei_unintelligent/graphs/contributors?all=1) 的 [原项目] (https://github.com/zxy19/yunmei_unintelligent)，本项目的核心逻辑全部依赖于此
- 感谢 [KernelSU-Style-UI-Kit](https://github.com/chenaizhang/KernelSU-Style-UI-Kit) 、[KernelSU](https://github.com/tiann/KernelSU) 和 [InstallerX-Revived](https://github.com/wxxsfxyzm/InstallerX-Revived) 的前端框架，没有你们本项目的 UI 无以成型

## 隐私处理

我们不会处理您的任何数据。您的个人信息（账号密码，位置等）将直接发送给云莓智能或存储与 SharedPreferences 中

## 声明
- 本项目基于 [GNU General Public License v3.0](LICENSE.txt) 获得许可，并基于该许可证开源
- 本项目代码使用人工智能技术辅助生成，根据中华人民共和国[《人工智能生成合成内容标识办法》](https://www.cac.gov.cn/2025-03/14/c_1743654684782215.htm)及 [GB 45438-2025《网络安全技术 人工智能生成合成内容标识方法》](https://openstd.samr.gov.cn/bzgk/std/newGbInfo?hcno=F32EA2A561F1886CD8D606513512D547)的规定，特此进行显式标识。代码内容仅供参考
- 本项目并非任何设备制造商、软件开发者、教育科研机构、品牌方或服务提供商的官方项目
- 除非相关页面另有明确说明，本项目未获得任何相关主体的赞助、授权、委托运营或官方认可，与其不存在代理、隶属、合资、雇佣、合作伙伴关系或其他形式的从属、关联关系。本项目发布的内容亦不代表相关品牌方、厂商或服务提供商的立场
- Android、DeepSeek、云莓智能，以及本站提及的其他品牌名称、产品名称、服务名称、商标、标识与图形，均可能为其各自权利人所有的商标或注册商标
- 本项目对上述名称、商标及标识的使用，仅限于对相关产品或服务进行识别、介绍、说明或提供兼容性信息，不表示本项目拥有相关商标权，也不意味着相关权利人对本项目提供任何形式的认可、授权、赞助或背书
- 本项目与 GitHub, Inc. 、Google Inc. 、Microsoft Corp. 、杭州深度求索人工智能基础技术研究有限公司以及杭州云莓科技有限公司没有从属关系
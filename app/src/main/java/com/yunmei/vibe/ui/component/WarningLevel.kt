package com.yunmei.vibe.ui.component

/**
 * 提示条的语义级别（照 KernelSU-Style-UI-Kit `ui/component/WarningLevel.kt`）。
 *
 * - [Error]：请求失败、存储不可用等需要用户注意的问题，用 `errorContainer` 一族；
 * - [Notice]：数据来源、使用提示等中性说明，用 `tertiaryContainer` 一族。
 *
 * Material 与 Miuix 两套 WarningCard 共用这一个枚举，保证同语义在两套主题下取同一族颜色。
 */
enum class WarningLevel { Error, Notice }
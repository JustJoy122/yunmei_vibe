package com.yunmei.vibe.ui.sign

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat

/**
 * 打卡的**前台跳板** Activity：不显示任何 UI，唯一职责是在"用户主动操作"的前台上下文中
 * 启动 [SignService]，然后立刻 finish()。
 *
 * 为什么需要它：Android 12+ 禁止应用在后台启动前台服务，
 * 直接把这些动作做成 `PendingIntent.getForegroundService`（打卡通知上的按钮）
 * 会被系统拒绝——服务根本不会启动，用户看到的就是"点了没反应"。
 * 改成 `PendingIntent.getActivity` 打开本页后，启动服务发生在**前台**，系统放行。
 *
 * 入口（全部经本页）：
 *  - 打卡通知的「使用上次位置 / 重试 / 完成」按钮；
 *  - 打卡定位方式弹窗（[SignLocationActivity]）的三个选项。
 */
class SignTrampolineActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent?.getStringExtra(EXTRA_ACTION) ?: SignService.ACTION_START
        val subMode = intent?.getStringExtra(SignService.EXTRA_SUB_MODE)

        val serviceIntent = Intent(this, SignService::class.java).setAction(action)
        if (subMode != null) {
            serviceIntent.putExtra(SignService.EXTRA_SUB_MODE, subMode)
        }
        // 此时本 Activity 处于前台，startForegroundService 不会被后台启动限制拒绝。
        runCatching { ContextCompat.startForegroundService(this, serviceIntent) }
        finish()
    }

    companion object {
        /** 要转发给 [SignService] 的 action（默认 ACTION_START）。 */
        const val EXTRA_ACTION = "sign_trampoline_action"
    }
}
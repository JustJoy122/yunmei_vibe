package com.yunmei.vibe.ui.unlock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat

/**
 * 开门的**前台跳板** Activity：不显示 UI，在用户主动操作的前台上下文中
 * 启动 [UnlockService] 并立即 finish()。
 *
 * 用途：开门失败通知上的「重试 / 完成」按钮。此前它们用
 * `PendingIntent.getForegroundService`，在 Android 12+ 会因"后台启动前台服务"被拒绝，
 * 服务不启动 → 用户点重试毫无反应。改为 getActivity 打开本页后即可正常启动。
 *
 * 安全：沿用 [UnlockShortcutActivity] 的 token 校验，只有携带正确令牌的 Intent 才会执行。
 */
class UnlockTrampolineActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tokenOk = intent?.getStringExtra(UnlockShortcut.EXTRA_TOKEN) == UnlockShortcut.token(this)
        if (!tokenOk) {
            finish()
            return
        }
        val action = intent?.getStringExtra(EXTRA_ACTION) ?: UnlockService.ACTION_RETRY
        val serviceIntent = Intent(this, UnlockService::class.java).setAction(action)
        runCatching { ContextCompat.startForegroundService(this, serviceIntent) }
        finish()
    }

    companion object {
        /** 要转发给 [UnlockService] 的 action（默认 ACTION_RETRY）。 */
        const val EXTRA_ACTION = "unlock_trampoline_action"
    }
}
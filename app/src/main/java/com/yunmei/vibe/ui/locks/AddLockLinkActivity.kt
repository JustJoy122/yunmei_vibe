package com.yunmei.vibe.ui.locks

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.yunmei.vibe.ui.MainActivity
import com.yunmei.vibe.ui.navigation.PendingLockImport

/**
 * 「添加门锁」深链的**入口 Activity**：不显示任何 UI。
 *
 * 与学长项目（`cc.xypp.yunmeiui.addLockActivity`）的做法一致：由系统按
 * `yunmeiui://lock_info/<Base64>`（或 `lock_id/`）唤起，本类只做一件事——
 * 把 URL 原文交给 [PendingLockImport] 暂存，然后启动 [MainActivity] 并立刻 finish()。
 *
 * 职责边界：**不解析、不写库、不提示**。解析统一由 `Lock.from` 在门锁页的
 * `LocksViewModel.importFrom` 中完成，并弹确认框等待用户决定。
 */
class AddLockLinkActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val raw = intent?.data?.toString()
        if (raw.isNullOrBlank() ||
            (!raw.contains("lock_info/") && !raw.contains("lock_id/"))
        ) {
            finish()
            return
        }
        PendingLockImport.post(raw)
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }
}
package com.yunmei.vibe.ui.sign

import com.yunmei.vibe.ui.util.TrampolineActivity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings

/**
 * 「去开启定位」的**前台跳板** Activity：不显示任何 UI（透明主题 + excludeFromRecents）。
 *
 * 为什么需要它：失败通知里的「去开启定位」原先用隐式 Intent
 * （`Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)`）构造 PendingIntent，
 * CodeQL 会报「隐式 PendingIntent（CWE-927）」。本类把该跳转收敛到显式组件：
 * PendingIntent 指向本 Activity（显式），再由本 Activity 在前台启动系统定位设置页。
 *
 * 职责边界：不校验 token、不启动服务、不读写任何状态；仅转发一次跳转后立刻 finish()。
 */
class SignSettingsTrampolineActivity : TrampolineActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchAndFinish(
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
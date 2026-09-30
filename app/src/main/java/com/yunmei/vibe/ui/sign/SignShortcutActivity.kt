package com.yunmei.vibe.ui.sign

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.yunmei.vibe.R

/**
 * 打卡快捷方式的跳板 Activity：不做任何界面，校验令牌后直接把前台服务拉起来。
 *
 * 之所以需要它，是因为 Android 10+ 不允许后台直接启动前台服务/后台流程，
 * 而快捷方式的启动是用户主动行为，可以合法地走到这里再启动服务。
 *
 * 「重新定位并保存」模式下如果系统定位未开启，会先拉起系统定位设置页，
 * 返回后无论结果如何都交给服务（服务会再判断一次并给出明确通知）。
 */
class SignShortcutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = intent?.getStringExtra(SignShortcut.EXTRA_TOKEN)
        if (token.isNullOrBlank() || token != SignShortcut.token(this)) {
            finish()
            return
        }
        startSignService()
        finish()
    }

    private fun startSignService() {
        val intent = Intent(this, SignService::class.java).setAction(SignService.ACTION_START)
        runCatching { ContextCompat.startForegroundService(this, intent) }
    }


}

package com.yunmei.vibe.ui.tile

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.unlock.UnlockShortcut
import com.yunmei.vibe.ui.unlock.UnlockShortcutActivity

/**
 * 控制中心「开门」磁贴。
 *
 * 点击后只做一件事：拼出与桌面快捷方式**逐字相同**的 Intent（action + 校验令牌），
 * 交给 [UnlockShortcutActivity]，后续的通知权限、蓝牙权限、蓝牙开关、8 秒冷却、前台服务
 * 全部复用既有实现，磁贴侧没有任何业务判断。
 */
class UnlockTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = getString(R.string.tile_unlock_label)
            icon = Icon.createWithResource(this@UnlockTileService, R.drawable.ic_tile_unlock)
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, UnlockShortcutActivity::class.java).apply {
            action = UnlockShortcut.ACTION
            putExtra(UnlockShortcut.EXTRA_TOKEN, UnlockShortcut.token(this@UnlockTileService))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        TileLaunch.launch(this, intent)
    }
}

package com.yunmei.vibe.ui.tile

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.sign.SignShortcut
import com.yunmei.vibe.ui.sign.SignShortcutActivity

/**
 * 控制中心「打卡」磁贴。
 *
 * 与「开门」磁贴同构：把与桌面快捷方式相同的 Intent（action + 校验令牌）交给
 * [SignShortcutActivity]，定位方式、权限、前台服务全部沿用既有实现。
 */
class SignTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = getString(R.string.tile_sign_label)
            icon = Icon.createWithResource(this@SignTileService, R.drawable.ic_tile_sign)
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, SignShortcutActivity::class.java).apply {
            action = SignShortcut.ACTION
            putExtra(SignShortcut.EXTRA_TOKEN, SignShortcut.token(this@SignTileService))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        TileLaunch.launch(this, intent)
    }
}

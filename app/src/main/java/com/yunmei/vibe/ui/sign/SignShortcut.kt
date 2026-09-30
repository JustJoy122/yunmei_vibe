package com.yunmei.vibe.ui.sign

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.yunmei.vibe.R
import com.yunmei.vibe.data.preferences.SettingsPrefs
import java.util.UUID

/**
 * 「打卡」动态快捷方式。
 *
 * 与「开门」快捷方式是同构实现（同一个可用性判据：是否存在默认门锁）。
 * 图标使用彩色品牌图标 drawable/ic_shortcut_check_in.xml（主 Logo 配色 + 打卡主体），
 * 不依赖主题反色，因此这里不需要任何深色模式逻辑。
 */
object SignShortcut {

    const val ID = "sign"
    const val ACTION = "com.yunmei.vibe.action.SIGN"
    const val EXTRA_TOKEN = "sign_shortcut_token"

    private const val TAG = "SignShortcut"

    /** 按默认门锁是否存在，同步快捷方式可用状态（需在主线程调用）。 */
    fun sync(context: Context, hasDefaultLock: Boolean) {
        runCatching {
            val shortcut = buildShortcut(context)
            if (hasDefaultLock) {
                ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
                ShortcutManagerCompat.enableShortcuts(context, listOf(shortcut))
            } else {
                ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
                ShortcutManagerCompat.disableShortcuts(
                    context,
                    listOf(ID),
                    context.getString(R.string.shortcut_unlock_disabled),
                )
            }
        }.onFailure { error ->
            Log.w(TAG, "sync sign shortcut failed", error)
        }
    }

    /** 与「开门」快捷方式共用同一个校验令牌，防止第三方直接启动跳板 Activity。 */
    fun token(context: Context): String {
        val prefs = SettingsPrefs.of(context)
        val saved = prefs.getString(SettingsPrefs.SHORTCUT_TOKEN, null)
        if (!saved.isNullOrBlank()) return saved
        val generated = UUID.randomUUID().toString().replace("-", "")
        prefs.edit().putString(SettingsPrefs.SHORTCUT_TOKEN, generated).apply()
        return generated
    }

    private fun buildShortcut(context: Context): ShortcutInfoCompat {
        val intent = Intent(context, SignShortcutActivity::class.java).apply {
            action = ACTION
            putExtra(EXTRA_TOKEN, token(context))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return ShortcutInfoCompat.Builder(context, ID)
            .setShortLabel(context.getString(R.string.shortcut_sign_short))
            .setLongLabel(context.getString(R.string.shortcut_sign_long))
            // 彩色品牌图标：主 Logo 配色（#D4E3FF 底 + #004784 主体）+ 打卡主体。
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_check_in))
            .setIntent(intent)
            .setRank(0)
            .build()
    }
}

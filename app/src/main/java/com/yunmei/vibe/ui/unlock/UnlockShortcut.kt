package com.yunmei.vibe.ui.unlock

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.yunmei.vibe.R
import com.yunmei.vibe.data.preferences.SettingsPrefs
import java.util.UUID

/**
 * 「开门」动态快捷方式。
 *
 * 只负责快捷方式本身的创建 / 启用 / 禁用，以及给快捷方式 Intent 签发校验令牌；
 * 可用性完全由「是否存在默认门锁」决定（无默认门锁时调用 disableShortcuts 隐藏）。
 *
 * 图标：快捷方式图标由启动器绘制，无法跟随应用主题自动反色，因此准备深浅两套单色剪影，
 * 由 [isNightMode] 读系统深色模式选择（浅色模式用深色剪影、深色模式用白色剪影）。
 * 造型取自 App 内「获取开门密码」使用的 Icons.Rounded.Key（Material Icons）。
 */
object UnlockShortcut {

    const val ID = "unlock"
    const val ACTION = "com.yunmei.vibe.action.UNLOCK"
    const val EXTRA_TOKEN = "unlock_shortcut_token"

    private const val TAG = "UnlockShortcut"

    /** 按默认门锁是否存在，同步快捷方式的可用状态。需在主线程调用。 */
    fun sync(context: Context, hasDefaultLock: Boolean) {
        runCatching {
            val shortcut = buildShortcut(context)
            if (hasDefaultLock) {
                ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
                // 之前被 disableShortcuts() 隐藏过，这里显式重新启用。
                ShortcutManagerCompat.enableShortcuts(context, listOf(shortcut))
            } else {
                // 需要先让该快捷方式存在，disableShortcuts 才有对象可禁用。
                ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
                ShortcutManagerCompat.disableShortcuts(
                    context,
                    listOf(ID),
                    context.getString(R.string.shortcut_unlock_disabled),
                )
            }
        }.onFailure { error ->
            Log.w(TAG, "sync unlock shortcut failed", error)
        }
    }

    /**
     * 快捷方式 Intent 携带的校验令牌。
     *
     * 快捷方式必然由启动器以第三方身份启动 Activity（exported=true），因此额外校验一个
     * 只有本应用写入过的随机令牌，避免任意第三方应用直接启动该 Activity 触发开门。
     */
    fun token(context: Context): String {
        val prefs = SettingsPrefs.of(context)
        val saved = prefs.getString(SettingsPrefs.SHORTCUT_TOKEN, null)
        if (!saved.isNullOrBlank()) return saved
        val generated = UUID.randomUUID().toString().replace("-", "")
        prefs.edit().putString(SettingsPrefs.SHORTCUT_TOKEN, generated).apply()
        return generated
    }

    /** 按系统深色模式选择快捷方式图标（浅色模式 -> 深色剪影；深色模式 -> 白色剪影）。 */
    private fun shortcutIconRes(context: Context): Int =
        if (isNightMode(context)) {
            R.drawable.ic_shortcut_unlock_dark
        } else {
            R.drawable.ic_shortcut_unlock_light
        }

    /** 当前是否处于系统深色模式（快捷方式图标只跟随系统，不跟随应用内主题开关）。 */
    fun isNightMode(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun buildShortcut(context: Context): ShortcutInfoCompat {
        val intent = Intent(context, UnlockShortcutActivity::class.java).apply {
            action = ACTION
            putExtra(EXTRA_TOKEN, token(context))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return ShortcutInfoCompat.Builder(context, ID)
            .setShortLabel(context.getString(R.string.shortcut_unlock_short))
            .setLongLabel(context.getString(R.string.shortcut_unlock_long))
            // 造型对应 App 内「获取开门密码」的 Icons.Rounded.Key；
            // 深浅两套剪影由系统深色模式决定，避免深色背景下深色图标不可见。
            .setIcon(IconCompat.createWithResource(context, shortcutIconRes(context)))
            .setIntent(intent)
            .setRank(1)
            .build()
    }
}

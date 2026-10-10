package com.yunmei.vibe.ui.sign

import android.content.Intent
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.twotone.History
import androidx.compose.material.icons.twotone.LocationOn
import androidx.compose.material.icons.twotone.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.res.stringResource
import androidx.core.location.LocationManagerCompat
import com.yunmei.vibe.R
import com.yunmei.vibe.data.preferences.SettingsPrefs
import com.yunmei.vibe.ui.LocalUiMode
import com.yunmei.vibe.ui.UiMode
import com.yunmei.vibe.ui.component.ActionMenuItem
import com.yunmei.vibe.ui.component.material.ActionMenuBottomSheet
import com.yunmei.vibe.ui.component.miuix.ActionMenuDialog
import com.yunmei.vibe.ui.theme.TemplateTheme
import com.yunmei.vibe.ui.theme.ThemeController

/**
 * 打卡「选择定位方式」轻量弹窗页（无主界面）。
 *
 * 由打卡通知的「选择定位方式」动作（`PendingIntent.getActivity`）打开；
 * 选完方式后经 [SignTrampolineActivity] 在前台启动 [SignService]，随后本页立即结束。
 *
 * 交互与结构完全对齐「扫码添加门锁」弹窗（`ScanAddLockMenu`）：
 * 两套主题共用同一份选项列表，仅图标族按主题切换；Miuix 用 `ActionMenuDialog`，
 * Material 用 `ActionMenuBottomSheet`（不再使用 `AlertDialog` + `SegmentedColumn`）。
 */
class SignLocationActivity : ComponentActivity() {

    private var pendingSubMode: SignService.SignSubMode = SignService.SignSubMode.USE_LAST

    /** 从系统定位设置返回后：启动打卡并结束本页（此时才关闭弹窗）。 */
    private val locationSettings =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            startSignService(pendingSubMode)
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appSettings = ThemeController.getAppSettings(this)
        val uiMode = UiMode.fromValue(
            SettingsPrefs.of(this).getString(SettingsPrefs.UI_MODE, UiMode.DEFAULT_VALUE)
                ?: UiMode.DEFAULT_VALUE
        )
        setContent {
            CompositionLocalProvider(LocalUiMode provides uiMode) {
                TemplateTheme(appSettings = appSettings, uiMode = uiMode) {
                    SignLocationDialog(
                        onChoice = { subMode -> choose(subMode) },
                        onDismiss = { finish() },
                    )
                }
            }
        }
    }

    /**
     * 选项点击的统一入口。交互顺序对齐 `ScanAddLockMenu`：**先关闭弹窗，再执行动作**。
     *
     * 唯一例外：需要用户去系统设置里打开定位时，必须保持本页存活
     * （否则跳转目标 Activity 会被一起销毁），等 onActivityResult 回来后再启动打卡并关闭。
     */
    private fun choose(subMode: SignService.SignSubMode) {
        val needsLocation = subMode != SignService.SignSubMode.USE_LAST
        if (needsLocation && !isLocationEnabled()) {
            pendingSubMode = subMode
            locationSettings.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }
        finish()
        startSignService(subMode)
    }

    /**
     * 启动打卡。**不直接 startForegroundService**：改为打开无 UI 的前台跳板 Activity，
     * 由跳板在前台上下文启动 [SignService]，绕开 Android 12+ 的后台启动前台服务限制。
     */
    private fun startSignService(subMode: SignService.SignSubMode) {
        val trampoline = Intent(this, SignTrampolineActivity::class.java)
            .putExtra(SignTrampolineActivity.EXTRA_ACTION, SignService.ACTION_WITH_MODE)
            .putExtra(SignService.EXTRA_SUB_MODE, subMode.value)
        val started = runCatching { startActivity(trampoline) }.isSuccess
        if (!started) {
            Toast.makeText(this, R.string.sign_start_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun isLocationEnabled(): Boolean {
        val lm = getSystemService(LOCATION_SERVICE) as? LocationManager ?: return false
        return runCatching { LocationManagerCompat.isLocationEnabled(lm) }.getOrDefault(false)
    }
}

/**
 * 打卡定位方式弹窗。结构与交互对齐 `ScanAddLockMenu`：
 *  - 两套主题共用同一份 items，仅图标族按主题切换（Material TwoTone / Miuix Rounded）；
 *  - Miuix 走 `ActionMenuDialog`，Material 走 `ActionMenuBottomSheet`；
 *  - 点击后的收尾统一交给 [SignLocationActivity.choose]（先关闭、再执行；跳系统设置时例外）。
 */
@Composable
private fun SignLocationDialog(
    onChoice: (SignService.SignSubMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val uiMode = LocalUiMode.current
    val useTwoToneIcons = uiMode == UiMode.Material
    val items = listOf(
        ActionMenuItem(
            if (useTwoToneIcons) Icons.TwoTone.History else Icons.Rounded.History,
            stringResource(R.string.unlock_sign_use_last),
        ) { onChoice(SignService.SignSubMode.USE_LAST) },
        ActionMenuItem(
            if (useTwoToneIcons) Icons.TwoTone.LocationOn else Icons.Rounded.LocationOn,
            stringResource(R.string.unlock_sign_locate),
        ) { onChoice(SignService.SignSubMode.LOCATE_ONLY) },
        ActionMenuItem(
            if (useTwoToneIcons) Icons.TwoTone.Save else Icons.Rounded.Save,
            stringResource(R.string.unlock_sign_relocate),
        ) { onChoice(SignService.SignSubMode.LOCATE_SAVE) },
    )

    when (uiMode) {
        UiMode.Miuix -> ActionMenuDialog(
            show = true,
            title = stringResource(R.string.unlock_sign_ask_title),
            summary = stringResource(R.string.sign_choose_location),
            items = items,
            onDismissRequest = onDismiss,
        )

        UiMode.Material -> ActionMenuBottomSheet(
            items = items,
            onDismiss = onDismiss,
        )
    }
}
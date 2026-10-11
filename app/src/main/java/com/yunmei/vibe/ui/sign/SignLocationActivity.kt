package com.yunmei.vibe.ui.sign

import com.yunmei.vibe.ui.component.material.SegmentedListItem
import com.yunmei.vibe.ui.component.material.SegmentedColumn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
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
 * 打卡「选择定位方式」弹窗。
 *
 * 结构对齐 InstallerX 的选项弹窗：标题 + 说明条 + 一组「主标题 + 右箭头」选项 + 取消按钮；
 * 选项区使用 InstallerX 移植过来的 `SegmentedColumn`（`ui/component/setting/SegmentedColumn.kt`）。
 *
 * **两套主题统一走同一个 `AlertDialog` 外壳**，原因（有实测依据）：
 *  - Material 侧原本就是 `AlertDialog + SegmentedColumn`，用户实测**点击正常**；
 *  - Miuix 侧原本走我方移植的 `ActionMenuDialog`（内部是 Miuix `OverlayDialog` + `ArrowPreference`），
 *    用户实测**弹窗能打开但点击选项无响应**；
 *  - 因此这里不再按主题分派两套外壳，统一采用已被验证可点击的那一套，
 *    避免再出现"只有 Miuix 点不动"的问题。
 *
 * 点击后的业务逻辑保持不变：[SignLocationActivity.choose]（先关闭、再启动打卡；
 * 需要跳系统定位设置时不关闭）。
 */
@Composable
private fun SignLocationDialog(
    onChoice: (SignService.SignSubMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.unlock_sign_ask_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.sign_choose_location),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(12.dp))
                SegmentedColumn(
                    content = listOf(
                        {
                            SegmentedListItem(
                                onClick = { onChoice(SignService.SignSubMode.USE_LAST) },
                                headlineContent = { Text(stringResource(R.string.unlock_sign_use_last)) },
                                leadingContent = { Icon(Icons.Rounded.History, null) },
                            )
                        },
                        {
                            SegmentedListItem(
                                onClick = { onChoice(SignService.SignSubMode.LOCATE_ONLY) },
                                headlineContent = { Text(stringResource(R.string.unlock_sign_locate)) },
                                leadingContent = { Icon(Icons.Rounded.LocationOn, null) },
                            )
                        },
                        {
                            SegmentedListItem(
                                onClick = { onChoice(SignService.SignSubMode.LOCATE_SAVE) },
                                headlineContent = { Text(stringResource(R.string.unlock_sign_relocate)) },
                                leadingContent = { Icon(Icons.Rounded.Save, null) },
                            )
                        },
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
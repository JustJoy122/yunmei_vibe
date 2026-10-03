package com.yunmei.vibe.ui.sign

import android.content.Intent
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import android.widget.Toast
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.yunmei.vibe.R
import com.yunmei.vibe.data.preferences.SettingsPrefs
import com.yunmei.vibe.ui.LocalUiMode
import com.yunmei.vibe.ui.UiMode
import com.yunmei.vibe.ui.component.ActionMenuItem
import com.yunmei.vibe.ui.component.material.SegmentedColumn
import com.yunmei.vibe.ui.component.material.SegmentedListItem
import com.yunmei.vibe.ui.component.miuix.ActionMenuDialog
import com.yunmei.vibe.ui.theme.TemplateTheme
import com.yunmei.vibe.ui.theme.ThemeController

/**
 * 打卡的轻量定位方式弹窗（每次询问模式的「选择定位方式」动作打开的就是这里）。
 *
 * 需要用户多选一时的交互：由通知动作/通知点击产生的 `PendingIntent.getActivity` 打开本页
 * （用户主动触发，不受 Android 10+ 后台启动 Activity 限制），不打开主界面——
 * 本 Activity 使用对话框主题，界面上只有一个对话框，复用项目自带的
 * Material `AlertDialog` + `SegmentedColumn` 与 Miuix `ActionMenuDialog`。
 *
 * 选中需要新定位的选项而系统定位未开启时，先拉起系统定位设置页；
 * 返回后无论结果如何都把选择交回 [SignService]（服务会再判断并给出明确通知）。
 */
class SignLocationActivity : ComponentActivity() {

    private var pendingSubMode: SignService.SignSubMode = SignService.SignSubMode.USE_LAST

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

    /** 需要新定位但定位未开启时，先请求开启；否则直接把选择交回服务。 */
    private fun choose(subMode: SignService.SignSubMode) {
        val needsLocation = subMode != SignService.SignSubMode.USE_LAST
        if (needsLocation && !isLocationEnabled()) {
            pendingSubMode = subMode
            locationSettings.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        } else {
            startSignService(subMode)
            finish()
        }
    }

    private fun startSignService(subMode: SignService.SignSubMode) {
        val intent = Intent(this, SignService::class.java)
            .setAction(SignService.ACTION_WITH_MODE)
            .putExtra(SignService.EXTRA_SUB_MODE, subMode.value)
        // Android 12+ 在后台启动前台服务可能被系统拒绝：此处不再静默吞掉异常，
        // 失败时给出可见反馈，避免用户以为"点了没反应"。
        val started = runCatching { ContextCompat.startForegroundService(this, intent) }.isSuccess
        if (!started) {
            Toast.makeText(this, R.string.sign_start_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun isLocationEnabled(): Boolean {
        val lm = getSystemService(LOCATION_SERVICE) as? LocationManager ?: return false
        return runCatching { LocationManagerCompat.isLocationEnabled(lm) }.getOrDefault(false)
    }
}

/** 三种定位方式，文案与图标沿用 App 内打卡询问弹窗的同一套。 */
@Composable
private fun SignLocationDialog(
    onChoice: (SignService.SignSubMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val uiMode = LocalUiMode.current
    if (uiMode == UiMode.Miuix) {
        ActionMenuDialog(
            show = true,
            title = stringResource(R.string.unlock_sign_ask_title),
            summary = stringResource(R.string.sign_choose_location),
            items = listOf(
                ActionMenuItem(Icons.Rounded.History, stringResource(R.string.unlock_sign_use_last)) {
                    onChoice(SignService.SignSubMode.USE_LAST)
                },
                ActionMenuItem(Icons.Rounded.LocationOn, stringResource(R.string.unlock_sign_locate)) {
                    onChoice(SignService.SignSubMode.LOCATE_ONLY)
                },
                ActionMenuItem(Icons.Rounded.Save, stringResource(R.string.unlock_sign_relocate)) {
                    onChoice(SignService.SignSubMode.LOCATE_SAVE)
                },
            ),
            onDismissRequest = onDismiss,
        )
    } else {
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
}


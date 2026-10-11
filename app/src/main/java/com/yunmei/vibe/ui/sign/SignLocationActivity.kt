package com.yunmei.vibe.ui.sign

import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.basic.TextButton
import com.yunmei.vibe.ui.component.setting.NavigationItemWidget
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
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
 * 结构对齐 InstallerX 的选项弹窗（截图）：顶部标题、说明条、若干「图标 + 主标题 + 副标题 + 右箭头」选项。
 * 选项行使用 InstallerX 原版移植组件 [NavigationItemWidget]
 * （`ui/component/setting/NavigationItemWidget.kt`，文件头注明 "Reused verbatim from InstallerX Revived"），
 * 两套主题共用同一份选项定义。
 *
 * 外壳按主题分派：
 *  - Miuix：Compose 原生 [Dialog]（`androidx.compose.ui.window.Dialog`），
 *    背景/文字沿用 MiuixTheme 语义色（`colorScheme.surface` / `onSurface` 等），字体沿用 Miuix 文本样式；
 *  - Material：`AlertDialog`（沿用已验证可点击的实现）。
 *
 * 点击后的业务逻辑不变：[SignLocationActivity.choose]（先关闭、再启动打卡；需跳系统定位设置时不关闭）。
 */
@Composable
private fun SignLocationDialog(
    onChoice: (SignService.SignSubMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val uiMode = LocalUiMode.current
    val title = stringResource(R.string.unlock_sign_ask_title)
    val summary = stringResource(R.string.sign_choose_location)

    // 两套主题共用同一份选项定义（InstallerX 的 NavigationItemWidget：图标 + 主标题 + 副标题 + 右箭头）
    val optionRows: @Composable () -> Unit = {
        SegmentedColumn(
            content = listOf(
                {
                    NavigationItemWidget(
                        icon = Icons.Rounded.History,
                        iconPlaceholder = false,
                        title = stringResource(R.string.unlock_sign_use_last),
                        description = stringResource(R.string.sign_choose_location),
                        onClick = { onChoice(SignService.SignSubMode.USE_LAST) },
                    )
                },
                {
                    NavigationItemWidget(
                        icon = Icons.Rounded.LocationOn,
                        iconPlaceholder = false,
                        title = stringResource(R.string.unlock_sign_locate),
                        description = stringResource(R.string.sign_choose_location),
                        onClick = { onChoice(SignService.SignSubMode.LOCATE_ONLY) },
                    )
                },
                {
                    NavigationItemWidget(
                        icon = Icons.Rounded.Save,
                        iconPlaceholder = false,
                        title = stringResource(R.string.unlock_sign_relocate),
                        description = stringResource(R.string.sign_choose_location),
                        onClick = { onChoice(SignService.SignSubMode.LOCATE_SAVE) },
                    )
                },
            )
        )
    }

    // Miuix：Compose 原生 Dialog 外壳；内容/颜色/字体与之前完全一致，只换外层承载
    if (uiMode == UiMode.Miuix) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = colorScheme.surface,
                contentColor = colorScheme.onSurface,
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
                        text = title,
                        fontSize = MiuixTheme.textStyles.title4.fontSize,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                        text = summary,
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        textAlign = TextAlign.Center,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                    optionRows()
                    TextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp).padding(horizontal = 24.dp),
                    )
                }
            }
        }
        return
    }

    // Material：已验证可点击的 AlertDialog 外壳
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(12.dp))
                optionRows()
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
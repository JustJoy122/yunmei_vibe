package com.yunmei.vibe.ui.screen.subsettings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.component.dialog.SteppedWarningDialog
import com.yunmei.vibe.ui.component.dialog.WarningStepText

/**
 * 「自动开门」与「开门后自动退出」冲突时的三级危险确认弹窗。
 *
 * 纯状态驱动：由调用方的 `level`（0=不显示，1/2/3=第几级）决定渲染哪一级文案，
 * 点「确定」/「放弃」分别回调 [onConfirm] / [onDismiss]，**不再使用挂起式 `awaitConfirm`**。
 *
 * 之所以不用 `rememberConfirmDialog()` 的链式挂起：三个 `awaitConfirm` 连续调用时，
 * 上一级收起（`visible = false`）与下一级弹出（`show()`）分属两个协程，宿主又分
 * Material / Miuix 两套独立窗口实现，任一侧调度落后都会让后一级根本弹不出来。
 * 改成「一个 Int 状态 + 两个回调」后，弹窗的显示完全由 Composable 重组驱动，不存在竞态。
 *
 * 窗口与样式统一由 [SteppedWarningDialog] 提供（与「注销账号」警告同一套），
 * 三级之间只做标题 + 正文的交叉淡入，不重建窗口。
 */
@Composable
internal fun DangerConfirmDialog(
    level: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (level <= 0) return

    // 文案在组合期一次算好，交给 SteppedWarningDialog 按级取用：
    // 过渡期间淡出的旧一级仍显示旧文字，不会出现「旧位置立刻变新字」。
    val texts = listOf(
        WarningStepText(
            title = stringResource(R.string.settings_danger_title),
            message = stringResource(R.string.settings_danger_msg),
        ),
        WarningStepText(
            title = stringResource(R.string.settings_danger_again_title),
            message = stringResource(R.string.settings_danger_again_msg),
        ),
        WarningStepText(
            title = stringResource(R.string.settings_danger_final_title),
            message = stringResource(R.string.settings_danger_final_msg),
            isStrong = true,
        ),
    )

    SteppedWarningDialog(
        step = level,
        texts = { step -> texts[(step - 1).coerceIn(0, texts.lastIndex)] },
        confirmText = stringResource(R.string.settings_danger_confirm),
        dismissText = stringResource(R.string.settings_danger_give_up),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** 第一级（标准警告）。 */
internal const val DANGER_LEVEL_FIRST = 1

/** 第二级（再次提醒）。 */
internal const val DANGER_LEVEL_SECOND = 2

/** 第三级（最后提醒）：确认即执行。 */
internal const val DANGER_LEVEL_FINAL = 3

package com.yunmei.vibe.ui.component.dialog

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yunmei.vibe.ui.LocalUiMode
import com.yunmei.vibe.ui.UiMode
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.nav.gesture.WindowNavigationEventBridge
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 多级警告弹窗的统一样式：同一处定义标题 / 正文 / 按钮，两个调用方（开锁页的三级危险确认、
 * 个人中心的注销账号）都走这里，避免两处各写一套导致字号、对齐、动画不一致。
 *
 * 内容以 [step] 为 key 做交叉淡入（180ms，自下 1/4 高度滑入），**窗口本身不重建**：
 * 重建会重新走一遍暗场与位移动画，看着像闪了一下。
 *
 * 标题样式对齐各平台既有惯例——Material 用 `titleLarge` 居中，Miuix 用 `title4` 字号 +
 * Medium 字重居中（与 miuix `WindowDialog` 的标题完全一致）。
 *
 * @param step 当前级数；`<= 0` 表示不显示。
 */
@Composable
fun SteppedWarningDialog(
    step: Int,
    texts: (Int) -> WarningStepText,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (step <= 0) return

    if (LocalUiMode.current == UiMode.Miuix) {
        OverlayDialog(
            show = true,
            title = null,
            onDismissRequest = onDismiss,
            content = {
                SteppedWarningText(step = step, texts = texts, miuix = true)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MiuixTextButton(
                        modifier = Modifier.weight(1f),
                        text = dismissText,
                        onClick = onDismiss,
                    )
                    Spacer(modifier = Modifier.width(20.dp))
                    MiuixTextButton(
                        modifier = Modifier.weight(1f),
                        text = confirmText,
                        onClick = onConfirm,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = null,
            text = {
                // 独立窗口里的返回事件要桥接给 miuix-nav，否则按返回键关不掉这个弹窗。
                WindowNavigationEventBridge()
                SteppedWarningText(step = step, texts = texts, miuix = false)
            },
            confirmButton = {
                TextButton(onClick = onConfirm) { Text(text = confirmText) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(text = dismissText) }
            },
        )
    }
}

/** 标题 + 正文：以 [step] 为 key 交叉淡入，窗口不重建。 */
@Composable
private fun SteppedWarningText(
    step: Int,
    texts: (Int) -> WarningStepText,
    miuix: Boolean,
) {
    AnimatedContent(
        targetState = step,
        transitionSpec = {
            val spec = tween<Float>(durationMillis = STEP_TRANSITION_MS)
            (
                fadeIn(animationSpec = spec) +
                    slideInVertically(animationSpec = tween(durationMillis = STEP_TRANSITION_MS)) { it / 4 }
                ).togetherWith(fadeOut(animationSpec = spec))
        },
        label = "warningStep",
    ) { current ->
        // 关键：文案按 current 取，不能直接用外部传入的“当前级文案”——
        // 否则过渡期间淡出的旧内容会立刻显示新文字。
        val stepText = texts(current)
        val title = stepText.title
        val message = stepText.message
        Column {
            Text(
                text = title,
                style = if (miuix) {
                    MaterialTheme.typography.titleLarge.copy(
                        fontSize = MiuixTheme.textStyles.title4.fontSize,
                        fontWeight = FontWeight.Medium,
                    )
                } else {
                    MaterialTheme.typography.titleLarge
                },
                textAlign = TextAlign.Center,
                color = if (miuix) MiuixTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = if (miuix) {
                    MaterialTheme.typography.bodyMedium.copy(fontSize = MiuixTheme.textStyles.body1.fontSize)
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                textAlign = TextAlign.Center,
                color = when {
                    stepText.isStrong && miuix -> MiuixTheme.colorScheme.error
                    stepText.isStrong -> MaterialTheme.colorScheme.error
                    miuix -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 某一级的标题与正文。
 *
 * @param isStrong 正文用错误色（红字）。用于「不可逆 / 数据会丢」这类强警告级。
 */
data class WarningStepText(
    val title: String,
    val message: String,
    val isStrong: Boolean = false,
)

/** 一级 → 下一级的过渡时长。 */
internal const val STEP_TRANSITION_MS = 180

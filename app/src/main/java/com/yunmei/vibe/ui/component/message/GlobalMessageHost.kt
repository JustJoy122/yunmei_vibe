package com.yunmei.vibe.ui.component.message

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar as MaterialSnackbar
import androidx.compose.material3.SnackbarHostState as MaterialSnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.yunmei.vibe.ui.LocalUiMode
import com.yunmei.vibe.ui.UiMode
import com.yunmei.vibe.ui.component.ObserveAsEvents
import com.yunmei.vibe.ui.component.UiMessageBus
import com.yunmei.vibe.ui.component.UiMessageTone
import com.yunmei.vibe.ui.component.material.SnackBarHost as TemplateSnackBarHost
import top.yukonga.miuix.kmp.basic.Snackbar as MiuixSnackbar
import top.yukonga.miuix.kmp.basic.SnackbarDefaults as MiuixSnackbarDefaults
import top.yukonga.miuix.kmp.basic.SnackbarHost as MiuixSnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState as MiuixSnackbarHostState
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 全局跨页面提示宿主。
 *
 * 两套主题各自使用**本方原生** Snackbar 组件，圆角、背景、字体全部由各自主题决定，
 * 不出现只适配一套主题的情况：
 * - Material：模板 `ui/component/material/SnackBar.kt` 的 `SnackBarHost`
 *   （KernelSU-Style-UI-Kit 原样复用，含滑动消除）+ Material 3 `Snackbar`；
 * - Miuix：Miuix 官方 `SnackbarHost` / `Snackbar`（`top.yukonga.miuix.kmp.basic`）。
 *
 * 颜色只按 [UiMessageTone] 语义取色，全部来自各主题的 ColorScheme，无任何硬编码色值。
 * 提示由 [UiMessageBus] 缓冲投递，因此「先发提示、再返回」也能在目标页弹出。
 *
 * @param modifier 由宿主 Scaffold 的 `snackbarHost` 槽位传入（Scaffold 会自行让开底栏与系统栏）。
 */
@Composable
fun GlobalMessageHost(modifier: Modifier = Modifier) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixMessageHost(modifier)
        UiMode.Material -> MaterialMessageHost(modifier)
    }
}

@Composable
private fun MaterialMessageHost(modifier: Modifier = Modifier) {
    val hostState = remember { MaterialSnackbarHostState() }
    var tone by remember { mutableStateOf(UiMessageTone.Success) }

    // 复用模板的事件收集器：生命周期感知 + 配合 Channel 缓冲，先发后显示也不会丢。
    ObserveAsEvents(UiMessageBus.messages) { message ->
        tone = message.tone
        hostState.showSnackbar(message.text)
    }

    TemplateSnackBarHost(
        hostState = hostState,
        modifier = modifier,
        snackBar = { data ->
            val (containerColor, contentColor) = materialToneColors(tone)
            MaterialSnackbar(
                snackbarData = data,
                containerColor = containerColor,
                contentColor = contentColor,
            )
        },
    )
}

@Composable
private fun materialToneColors(tone: UiMessageTone): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (tone) {
        UiMessageTone.Error -> scheme.error to scheme.onError
        // 警告色与 Miuix 保持一致，统一取 tertiaryContainer 一族（两套主题表现一致）。
        UiMessageTone.Warning -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        UiMessageTone.Success -> scheme.primary to scheme.onPrimary
    }
}

@Composable
private fun MiuixMessageHost(modifier: Modifier = Modifier) {
    val hostState = remember { MiuixSnackbarHostState() }
    var tone by remember { mutableStateOf(UiMessageTone.Success) }

    ObserveAsEvents(UiMessageBus.messages) { message ->
        tone = message.tone
        hostState.showSnackbar(message.text)
    }

    MiuixSnackbarHost(
        state = hostState,
        modifier = modifier,
        content = { data ->
            val (containerColor, contentColor) = miuixToneColors(tone)
            MiuixSnackbar(
                data = data,
                colors = MiuixSnackbarDefaults.snackbarColors(
                    containerColor = containerColor,
                    contentColor = contentColor,
                    actionContentColor = contentColor,
                    dismissActionContentColor = contentColor,
                ),
            )
        },
    )
}

@Composable
private fun miuixToneColors(tone: UiMessageTone): Pair<Color, Color> {
    val scheme = MiuixTheme.colorScheme
    return when (tone) {
        UiMessageTone.Error -> scheme.error to scheme.onError
        // Miuix 的 ColorScheme 没有裸 tertiary，只有 tertiaryContainer / onTertiaryContainer。
        UiMessageTone.Warning -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        UiMessageTone.Success -> scheme.primary to scheme.onPrimary
    }
}

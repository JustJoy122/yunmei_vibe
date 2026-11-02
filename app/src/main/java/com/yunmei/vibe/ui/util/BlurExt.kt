package com.yunmei.vibe.ui.util

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import com.yunmei.vibe.ui.theme.LocalUseProgressiveBlur
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun rememberBlurBackdrop(enableBlur: Boolean): LayerBackdrop? {
    if (!enableBlur || !isRenderEffectSupported()) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

/** 高斯档：模糊半径与覆盖色，与移植前保持一致。 */
private const val GAUSSIAN_BLUR_RADIUS = 25f

/** 渐进档：模糊半径与顶部覆盖色，取自 Eta 的同名实现。 */
private const val PROGRESSIVE_BLUR_RADIUS = 10f

@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    blurActive: Boolean = true,
    content: @Composable () -> Unit,
) {
    val surfaceColor = MiuixTheme.colorScheme.surface
    // 渐进模糊内部依赖 android.graphics.RuntimeShader（API 33+，miuix-blur 的 progressiveTextureBlur
    // 由 BackdropEffect 用 RuntimeShader 实现）。API 31/32 上直接调用会在类加载阶段抛
    // NoClassDefFoundError，因此这里显式降级回高斯：两个档位在低版本都仍有模糊，只是形状相同。
    val useProgressive = LocalUseProgressiveBlur.current && isRuntimeShaderSupported()
    Box(
        modifier = when {
            !blurActive || backdrop == null -> Modifier
            useProgressive -> Modifier.progressiveTextureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                gradient = ProgressiveBlur.Top.copy(curve = 2.2f),
                blurRadius = PROGRESSIVE_BLUR_RADIUS,
                colors = BlurColors(
                    blendColors = listOf(
                        BlendColorEntry(color = surfaceColor.copy(0.3f)),
                    ),
                ),
            )
            else -> Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = GAUSSIAN_BLUR_RADIUS,
                colors = BlurColors(
                    blendColors = listOf(
                        BlendColorEntry(color = surfaceColor.copy(0.87f)),
                    ),
                ),
            )
        },
    ) {
        content()
    }
}

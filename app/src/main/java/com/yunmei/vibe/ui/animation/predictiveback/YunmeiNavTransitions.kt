// SPDX-License-Identifier: GPL-3.0-only
// 返回动画的视觉公式全部取自上游：
// - InstallerX Revived（GPL-3.0）ui/animation/predictiveback/*：
//   ScaleNavTransition.kt（被返回页 0.85→1 缩放、按 exitDirection 决定退出方向、200ms CubicBezier(0.2,0,0,1)）、
//   ClassicNavTransition.kt（被返回页 0.9→1 缩放 + alpha、当前页按 coverProgress 整屏滑出）。
//   上游这些实现基于 miuix-nav 的 NavTransition（NavTransitionScope/NavMotion），本项目使用
//   androidx navigation3，无法直接搬运类型，因此这里按「同样的图形公式 + 同样的数值」翻译到
//   navigation3 的 ContentTransform（transitionSpec / popTransitionSpec / predictivePopTransitionSpec）上。
// - miuix 上游（Apache-2.0）miuix-nav/src/commonMain/.../nav/transition/NavTransitions.kt 的
//   NavTransitions.MiuixDefault：进入页自尾部整屏滑入，被覆盖页向前端视差 1/4 宽 + alpha 1→0.9。
package com.yunmei.vibe.ui.animation.predictiveback

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.defaultPopTransitionSpec
import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import androidx.navigation3.ui.defaultTransitionSpec
import androidx.navigationevent.NavigationEvent

/**
 * 一次返回动画需要的三条 ContentTransform，对应 androidx navigation3 `NavDisplay` 的三个钩子：
 * 前进（transitionSpec）、普通返回（popTransitionSpec）、预测性返回（predictivePopTransitionSpec）。
 */
class PredictiveBackSpecs<T : Any>(
    val transition: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform,
    val pop: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform,
    val predictivePop: AnimatedContentTransitionScope<Scene<T>>.(Int) -> ContentTransform,
)

/** 统一过渡时长：取自 InstallerX 各档的 programmatic/commit Tween（200ms）。 */
private const val DURATION_MS = 200

/** InstallerX `ScaleNavTransition` / `ClassicNavTransition` 使用的 CubicBezier(0.2, 0, 0, 1)。 */
private val ExitEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** miuix `NavTransitions.MiuixDefault`：被覆盖页向前端视差 1/4 宽。 */
private const val MIUIX_COVERED_PARALLAX = 0.25f

/** miuix `NavTransitions.MiuixDefault`：被覆盖页不透明度 1 → 0.9。 */
private const val MIUIX_COVERED_ALPHA = 0.9f

/** InstallerX `ScaleNavTransition`：被返回页最小缩放 0.85。 */
private const val SCALE_MIN_SCALE = 0.85f

/** InstallerX `ClassicNavTransition`：被返回页最小缩放 0.9。 */
private const val CLASSIC_MIN_SCALE = 0.9f

/**
 * 按设置装配返回动画。
 *
 * @param enabled 「预测性返回手势」开关；关闭即不启用返回动画（等价 InstallerX 的 `NoPredictiveBackTransition`：
 *   push/pop 用 Miuix 默认过渡，手势由 `MainActivity` 的 `NavigationBackHandler` 拦截，不再驱动页面位移）。
 * @param layoutDirection 用于复刻 miuix 过渡的 RTL 镜像行为。
 */
fun <T : Any> predictiveBackSpecs(
    enabled: Boolean,
    animation: PredictiveBackAnimation,
    exitDirection: PredictiveBackExitDirection,
    layoutDirection: LayoutDirection,
): PredictiveBackSpecs<T> {
    val rtl = layoutDirection == LayoutDirection.Rtl
    return when {
        !enabled || animation == PredictiveBackAnimation.MIUIX -> miuixSpecs(rtl)
        animation == PredictiveBackAnimation.SCALE -> scaleSpecs(exitDirection, rtl)
        animation == PredictiveBackAnimation.CLASSIC -> classicSpecs(rtl)
        else -> aospSpecs()
    }
}

/**
 * AOSP 档：直接使用 androidx navigation3 的默认过渡
 * （前瞻/普通返回 = 700ms 交叉淡入淡出；预测性返回 = fadeIn(spring 1600/1.0) + scaleOut(0.7f)），
 * 即本项目改造前的既有观感，作为默认档不改变用户现有体验。
 */
private fun <T : Any> aospSpecs(): PredictiveBackSpecs<T> = PredictiveBackSpecs(
    transition = defaultTransitionSpec<T>(),
    pop = defaultPopTransitionSpec<T>(),
    predictivePop = { edge -> defaultPredictivePopTransitionSpec<T>().invoke(this, edge) },
)

/** Miuix 档：提前 push/pop 都用 miuix 默认过渡（与上游 `NavTransitions.MiuixDefault` 一致）。 */
private fun <T : Any> miuixSpecs(rtl: Boolean): PredictiveBackSpecs<T> = PredictiveBackSpecs(
    transition = miuixPush(rtl),
    pop = miuixPop(rtl),
    predictivePop = { _ -> miuixPop<T>(rtl).invoke(this) },
)

/**
 * 照抄 miuix `NavTransitions.MiuixDefault` 的进入段：
 * `translationX = (+width → 0)`（RTL 镜像），被覆盖页 `translationX = 0 → ∓0.25width`、`alpha 1 → 0.9`。
 * 上游把圆角裁剪与暗化放在独立的 `NavDisplayEffects` 层，androidx navigation3 没有对应钩子，故此处不含。
 */
private fun <T : Any> miuixPush(rtl: Boolean): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform {
    val enterSign = if (rtl) -1f else 1f
    val coverSign = if (rtl) 1f else -1f
    return {
        ContentTransform(
            targetContentEnter = slideInHorizontally(
                animationSpec = tween(DURATION_MS, easing = FastOutSlowInEasing),
                initialOffsetX = { full -> (enterSign * full).toInt() },
            ),
            initialContentExit = slideOutHorizontally(
                animationSpec = tween(DURATION_MS, easing = FastOutSlowInEasing),
                targetOffsetX = { full -> (coverSign * full * MIUIX_COVERED_PARALLAX).toInt() },
            ) + fadeOut(
                targetAlpha = MIUIX_COVERED_ALPHA,
                animationSpec = tween(DURATION_MS, easing = FastOutSlowInEasing),
            ),
        )
    }
}

/** miuix `NavTransitions.MiuixDefault` 的退出段：被覆盖页从视差位回到 0，顶部页整屏滑出（RTL 镜像）。 */
private fun <T : Any> miuixPop(rtl: Boolean): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform {
    val enterSign = if (rtl) -1f else 1f
    val coverSign = if (rtl) 1f else -1f
    return {
        ContentTransform(
            targetContentEnter = slideInHorizontally(
                animationSpec = tween(DURATION_MS, easing = FastOutSlowInEasing),
                initialOffsetX = { full -> (coverSign * full * MIUIX_COVERED_PARALLAX).toInt() },
            ),
            initialContentExit = slideOutHorizontally(
                animationSpec = tween(DURATION_MS, easing = FastOutSlowInEasing),
                targetOffsetX = { full -> (enterSign * full).toInt() },
            ),
        )
    }
}

/**
 * 缩放档：翻译自 InstallerX `ScaleNavTransition`。
 * 被返回页 `scale = 0.85 → 1`（transformOrigin 的 X 取手势边沿的对侧：左边沿 → 0.8，否则 0.2）、
 * 并自退出方向整屏滑入；被覆盖页保持不变（上游该档只变换顶部页）。
 */
private fun <T : Any> scaleSpecs(
    exitDirection: PredictiveBackExitDirection,
    rtl: Boolean,
): PredictiveBackSpecs<T> {
    // 普通返回没有手势边沿信息，按「始终向右」处理（上游非手势路径同为右向）。
    val defaultSign = if (exitDirection == PredictiveBackExitDirection.ALWAYS_LEFT) -1f else 1f
    return PredictiveBackSpecs(
        transition = miuixPush(rtl),
        pop = { scalePopTransform(defaultSign, pivotFractionX = 0.2f) },
        predictivePop = { edge ->
            scalePopTransform(
                sign = exitDirectionSign(exitDirection, edge),
                pivotFractionX = if (edge == NavigationEvent.EDGE_LEFT) 0.8f else 0.2f,
            )
        },
    )
}

private fun scalePopTransform(sign: Float, pivotFractionX: Float): ContentTransform = ContentTransform(
    targetContentEnter = slideInHorizontally(
        animationSpec = tween(DURATION_MS, easing = ExitEasing),
        initialOffsetX = { full -> (sign * full).toInt() },
    ) + scaleIn(
        animationSpec = tween(DURATION_MS, easing = ExitEasing),
        initialScale = SCALE_MIN_SCALE,
        transformOrigin = TransformOrigin(pivotFractionX, 0.5f),
    ),
    initialContentExit = ExitTransition.None,
)

/** 经典档：翻译自 InstallerX `ClassicNavTransition.ClassicScalePop`（0.9 → 1 缩放 + 淡入，当前页整屏滑出）。 */
private fun <T : Any> classicSpecs(rtl: Boolean): PredictiveBackSpecs<T> = PredictiveBackSpecs(
    transition = miuixPush(rtl),
    pop = { classicPopTransform() },
    predictivePop = { classicPopTransform() },
)

private fun classicPopTransform(): ContentTransform = ContentTransform(
    targetContentEnter = scaleIn(
        animationSpec = tween(DURATION_MS, easing = ExitEasing),
        initialScale = CLASSIC_MIN_SCALE,
    ) + fadeIn(animationSpec = tween(DURATION_MS, easing = ExitEasing)),
    initialContentExit = slideOutHorizontally(
        animationSpec = tween(DURATION_MS, easing = ExitEasing),
        targetOffsetX = { full -> -full },
    ),
)

/** 与 InstallerX `ScaleNavTransition.exitDirectionSign()` 一致：跟随手势时「左边沿滑动 → 页面向右退出」。 */
private fun exitDirectionSign(direction: PredictiveBackExitDirection, swipeEdge: Int): Float = when (direction) {
    PredictiveBackExitDirection.FOLLOW_GESTURE -> if (swipeEdge == NavigationEvent.EDGE_LEFT) 1f else -1f
    PredictiveBackExitDirection.ALWAYS_RIGHT -> 1f
    PredictiveBackExitDirection.ALWAYS_LEFT -> -1f
}

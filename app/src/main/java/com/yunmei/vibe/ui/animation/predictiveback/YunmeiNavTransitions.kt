// SPDX-License-Identifier: GPL-3.0-only
// 本文件是 InstallerX Revived（GPL-3.0）返回动画在本项目导航框架下的复刻。
//
// 上游实现位于 ui/animation/predictiveback/：
//   - AospNavTransition.kt（ClassicActivityOpen / ClassicActivityClose / CrossActivityPredictive）
//   - ScaleNavTransition.kt（scaleNavTransition：0.85 → 1 缩放、exitDirection、200ms）
//   - ClassicNavTransition.kt（ClassicScalePop：0.9 → 1 缩放 + 淡入、当前页整屏滑出、200ms）
//   - NavTransitionEasing.kt（FastOutExtraSlowIn / BackGestureEasing，本文件逐字复刻）
//   - NavTransitionGeometry.kt（topProgress / coverProgress 语义）
// Miuix 档照抄 miuix 上游 miuix-nav/src/commonMain/.../nav/transition/NavTransitions.kt 的
// NavTransitions.MiuixDefault 公式。
//
// 上游跑在 miuix-nav 的 NavTransition/NavTransitionScope 上（依赖 relativeDepth、gesture.touchY、
// settle.elapsedMillis/releaseVelocity 等**逐帧量**），本项目导航是 androidx navigation3，
// 只有 ContentTransform（transitionSpec / popTransitionSpec / predictivePopTransitionSpec）这一层钩子。
// 因此这里逐字搬运上游的常量、公式与缓动曲线并表达为 ContentTransform；依赖逐帧量的部分
// （release-velocity 弹跳 bounceScale、touchY 跟随 crossActivityYShift、settle.elapsedMillis 驱动的
//  scrim、NavDisplayEffects 的暗化层）无法在 ContentTransform 层表达，已在各档注释中明确标注。
package com.yunmei.vibe.ui.animation.predictiveback

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.scene.Scene
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

// ---------------------------------------------------------------------------
// InstallerX NavTransitionEasing.kt 的逐字复刻
// ---------------------------------------------------------------------------

/** 复刻 InstallerX `NavTransitionEasing.FastOutExtraSlowIn`（两段 CubicBezier 拼接）。 */
private val FastOutExtraSlowIn: Easing = run {
    val knotX = 0.166666f
    val knotY = 0.4f
    val first = CubicBezierEasing(0.05f / knotX, 0f, 0.133333f / knotX, 0.06f / knotY)
    val second = CubicBezierEasing(
        (0.208333f - knotX) / (1f - knotX),
        (0.82f - knotY) / (1f - knotY),
        (0.25f - knotX) / (1f - knotX),
        (1f - knotY) / (1f - knotY),
    )
    Easing { fraction ->
        if (fraction < knotX) {
            knotY * first.transform(fraction / knotX)
        } else {
            knotY + (1f - knotY) * second.transform((fraction - knotX) / (1f - knotX))
        }
    }
}

/** 复刻 InstallerX `NavTransitionEasing.BackGestureEasing`。 */
private val BackGestureEasing: Easing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

/**
 * 复刻 InstallerX `shapedTopProgress`：手势路径下 `1 - BackGestureEasing(1 - progress)`，
 * 无手势时为线性。这里把它表达成一个 Easing，供 ContentTransform 使用。
 */
private val AospGestureShaped: Easing = Easing { fraction ->
    1f - BackGestureEasing.transform((1f - fraction).coerceIn(0f, 1f))
}

// ---------------------------------------------------------------------------
// AOSP 档：InstallerX AospNavTransition.kt
// ---------------------------------------------------------------------------

/** InstallerX `ClassicActivityMotion` / `CrossActivityPredictive` 的时长。 */
private const val AOSP_DURATION_MS = 450

/** InstallerX `CROSS_ACTIVITY_MIN_SCALE`。 */
private const val AOSP_MIN_SCALE = 0.9f

/** InstallerX `CrossActivityDrift`。 */
private val AospDrift = 96.dp

/** InstallerX `CrossActivityEdgeMargin`。 */
private val AospEdgeMargin = 8.dp

/** InstallerX `ClassicActivityOpen` 的 alpha 曲线常量。 */
private const val AOSP_OPEN_FADE_START = 0.12f
private const val AOSP_OPEN_FADE_SPAN = 0.71f

/** InstallerX `ClassicActivityClose` 的 alpha 曲线常量。 */
private const val AOSP_CLOSE_FADE_START = 0.21f
private const val AOSP_CLOSE_FADE_SPAN = 0.74f

/**
 * 顶部页 alpha 曲线：上游用 `(progress - start) / span`，而 `progress` 已被 motion 缓动，
 * 故把 motion 缓动与曲线合成同一个 Easing（等价于 `alpha = ((FastOutExtraSlowIn(t) - start) / span)`）。
 */
private fun aospRampEasing(start: Float, span: Float): Easing = Easing { fraction ->
    val eased = FastOutExtraSlowIn.transform(fraction)
    ((eased - start) / span).coerceIn(0f, 1f)
}

/** InstallerX `CrossActivityPredictive` 进入段 alpha：`(progress / 0.2).coerceIn(0, 1)`。 */
private val AospEnterFastFade: Easing = Easing { fraction ->
    (FastOutExtraSlowIn.transform(fraction) / 0.2f).coerceIn(0f, 1f)
}

/** 离开段 alpha：上游 `1 - post * 3.5`（前 28.6% 内淡出完毕）。 */
private val AospExitFastFade: Easing = Easing { fraction ->
    (FastOutExtraSlowIn.transform(fraction) * 3.5f).coerceIn(0f, 1f)
}

/**
 * AOSP 档：复刻 InstallerX `AospNavTransition`（push = ClassicActivityOpen、pop = ClassicActivityClose、
 * predictivePop = CrossActivityPredictive）中可表达的常量与公式：
 * - 时长 450ms + 缓动 FastOutExtraSlowIn（逐字复刻的 Easing）；
 * - 顶部/被返回页缩放 0.9 → 1（`CROSS_ACTIVITY_MIN_SCALE`）；
 * - 位移上限 96dp（`CrossActivityDrift`），顶部页另用 hug 公式
 *   `width * (1 - 0.9) / 2 - 8dp`（`CrossActivityEdgeMargin`）向内收；
 * - alpha 曲线 `(progress - 0.12) / 0.71`（open）/ `(progress - 0.21) / 0.74`（close）/
 *   快速淡入 `progress / 0.2` 与离开 `1 - 3.5 * post`（predictive）。
 * 未包含（依赖 miuix 逐帧量）：`bounceScale` 的 release-velocity 弹跳、`crossActivityYShift` 的 touchY 跟随、
 *  `settle.elapsedMillis` 驱动的 scrim。
 */
private fun <T : Any> aospSpecs(density: Density): PredictiveBackSpecs<T> {
    val driftPx = with(density) { AospDrift.toPx() }
    val marginPx = with(density) { AospEdgeMargin.toPx() }

    // hugMax = widthPx * (1 - minScale) / 2 - edgeMarginPx（InstallerX CrossActivityPredictive 原式）
    val hugOffset: (Int) -> Int = { fullWidth ->
        (fullWidth * (1f - AOSP_MIN_SCALE) / 2f - marginPx).coerceAtLeast(0f).toInt()
    }

    val openAlpha = aospRampEasing(AOSP_OPEN_FADE_START, AOSP_OPEN_FADE_SPAN)
    val closeAlpha = aospRampEasing(AOSP_CLOSE_FADE_START, AOSP_CLOSE_FADE_SPAN)

    // push：ClassicActivityOpen（进入页自 +drift 滑入并按 OPEN 曲线淡入；被覆盖页向 -drift 让位）
    val push: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
        ContentTransform(
            targetContentEnter = slideInHorizontally(
                animationSpec = tween(AOSP_DURATION_MS, easing = FastOutExtraSlowIn),
                initialOffsetX = { driftPx.toInt() },
            ) + fadeIn(animationSpec = tween(AOSP_DURATION_MS, easing = openAlpha)),
            initialContentExit = slideOutHorizontally(
                animationSpec = tween(AOSP_DURATION_MS, easing = FastOutExtraSlowIn),
                targetOffsetX = { -driftPx.toInt() },
            ),
        )
    }

    // pop：ClassicActivityClose（当前页沿 CLOSE 曲线淡出后滑出；被返回页自 -drift 归位）
    val pop: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
        ContentTransform(
            targetContentEnter = slideInHorizontally(
                animationSpec = tween(AOSP_DURATION_MS, easing = FastOutExtraSlowIn),
                initialOffsetX = { -driftPx.toInt() },
            ) + fadeIn(animationSpec = tween(AOSP_DURATION_MS, easing = closeAlpha)),
            initialContentExit = slideOutHorizontally(
                animationSpec = tween(AOSP_DURATION_MS, easing = FastOutExtraSlowIn),
                targetOffsetX = { driftPx.toInt() },
            ) + fadeOut(
                targetAlpha = 0f,
                animationSpec = tween(AOSP_DURATION_MS, easing = AospExitFastFade),
            ),
        )
    }

    // predictivePop：CrossActivityPredictive（被返回页 0.9 → 1 且 hug 归位；当前页缩放 + 位移 + 快速淡出）
    val predictive: AnimatedContentTransitionScope<Scene<T>>.(Int) -> ContentTransform = {
        ContentTransform(
            targetContentEnter = slideInHorizontally(
                animationSpec = tween(AOSP_DURATION_MS, easing = AospGestureShaped),
                initialOffsetX = hugOffset,
            ) + scaleIn(
                animationSpec = tween(AOSP_DURATION_MS, easing = AospGestureShaped),
                initialScale = AOSP_MIN_SCALE,
                transformOrigin = TransformOrigin(0.5f, 0.5f),
            ) + fadeIn(animationSpec = tween(AOSP_DURATION_MS, easing = AospEnterFastFade)),
            initialContentExit = slideOutHorizontally(
                animationSpec = tween(AOSP_DURATION_MS, easing = FastOutExtraSlowIn),
                targetOffsetX = { driftPx.toInt() },
            ) + scaleOut(
                animationSpec = tween(AOSP_DURATION_MS, easing = FastOutExtraSlowIn),
                targetScale = AOSP_MIN_SCALE,
                transformOrigin = TransformOrigin(0.5f, 0.5f),
            ) + fadeOut(
                targetAlpha = 0f,
                animationSpec = tween(AOSP_DURATION_MS, easing = AospExitFastFade),
            ),
        )
    }

    return PredictiveBackSpecs(transition = push, pop = pop, predictivePop = predictive)
}

// ---------------------------------------------------------------------------
// Miuix 档：miuix 上游 NavTransitions.MiuixDefault
// ---------------------------------------------------------------------------

/** miuix `NavTransitions.MiuixDefault`：被覆盖页向前端视差 1/4 宽。 */
private const val MIUIX_COVERED_PARALLAX = 0.25f

/** miuix `NavTransitions.MiuixDefault`：被覆盖页不透明度 1 → 0.9。 */
private const val MIUIX_COVERED_ALPHA = 0.9f

/**
 * miuix 默认过渡的收敛动效：上游 `NavTransitions` 的 KDoc 明示「同一 d → 视觉映射复用于 spring 收敛路径
 * 与手势路径」，因此这里用 spring（参数取 androidx navigation3 默认预测性返回所引用的 material3
 * `motionScheme.defaultEffectsSpec()`：stiffness 1600 / dampingRatio 1.0），而不是自行编造 tween 时长。
 */
private val MiuixMotion = spring<Float>(dampingRatio = 1.0f, stiffness = 1600.0f)

/**
 * Miuix 档：push/pop 都用 miuix 默认过渡。进入页自尾部整屏滑入（RTL 镜像）；被覆盖页向前端视差 1/4 宽、
 * alpha 1 → 0.9；返回时二者反向（被覆盖页 alpha 0.9 → 1）。上游把圆角裁剪与暗化放在独立的
 * `NavDisplayEffects` 层（圆角裁剪见 MainActivity 的页面效果层实现）。
 */
private fun <T : Any> miuixSpecs(rtl: Boolean): PredictiveBackSpecs<T> = PredictiveBackSpecs(
    transition = miuixPush(rtl),
    pop = miuixPop(rtl),
    predictivePop = { _ -> miuixPop<T>(rtl).invoke(this) },
)

private fun <T : Any> miuixPush(rtl: Boolean): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform {
    val enterSign = if (rtl) -1f else 1f
    val coverSign = if (rtl) 1f else -1f
    return {
        ContentTransform(
            targetContentEnter = slideInHorizontally(
                animationSpec = MiuixMotion,
                initialOffsetX = { full -> (enterSign * full).toInt() },
            ),
            initialContentExit = slideOutHorizontally(
                animationSpec = MiuixMotion,
                targetOffsetX = { full -> (coverSign * full * MIUIX_COVERED_PARALLAX).toInt() },
            ) + fadeOut(targetAlpha = MIUIX_COVERED_ALPHA, animationSpec = MiuixMotion),
        )
    }
}

private fun <T : Any> miuixPop(rtl: Boolean): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform {
    val enterSign = if (rtl) -1f else 1f
    val coverSign = if (rtl) 1f else -1f
    return {
        ContentTransform(
            targetContentEnter = slideInHorizontally(
                animationSpec = MiuixMotion,
                initialOffsetX = { full -> (coverSign * full * MIUIX_COVERED_PARALLAX).toInt() },
            ) + fadeIn(initialAlpha = MIUIX_COVERED_ALPHA, animationSpec = MiuixMotion),
            initialContentExit = slideOutHorizontally(
                animationSpec = MiuixMotion,
                targetOffsetX = { full -> (enterSign * full).toInt() },
            ),
        )
    }
}

// ---------------------------------------------------------------------------
// 缩放档 / 经典档：InstallerX ScaleNavTransition.kt / ClassicNavTransition.kt
// ---------------------------------------------------------------------------

/** InstallerX `ScaleExitMotion` / `ClassicScaleMotion` 的时长（commit 与 programmatic 均为 200ms）。 */
private const val EXIT_DURATION_MS = 200

/** InstallerX `ClassicScaleMotion` 的缓动（commit / programmatic 均为 CubicBezier(0.2, 0, 0, 1)）。 */
private val ExitEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** InstallerX `ScaleExitMotion.commit` 的缓动。 */
private val ScaleCommitEasing = FastOutSlowInEasing

/** InstallerX `ScaleNavTransition` 的顶部页最小缩放。 */
private const val SCALE_MIN_SCALE = 0.85f

/** InstallerX `ClassicNavTransition` 的被返回页最小缩放。 */
private const val CLASSIC_MIN_SCALE = 0.9f

/**
 * 缩放档：复刻 InstallerX `scaleNavTransition`。被返回页 `0.85 → 1` 并自退出方向整屏滑入，
 * transformOrigin 的 X 取手势边沿对侧（左边沿 0.8，否则 0.2）；被覆盖页在该档不参与变换
 * （上游此档只变换顶部页，对应 `ExitTransition.None`）。动效取上游 commit 段
 * （200ms + FastOutSlowInEasing）；cancel 段的 Spring(1500) 与 commit 在 ContentTransform 里
 * 共用同一条曲线，无法分别指定。
 */
private fun <T : Any> scaleSpecs(
    exitDirection: PredictiveBackExitDirection,
    rtl: Boolean,
): PredictiveBackSpecs<T> {
    val defaultSign = if (exitDirection == PredictiveBackExitDirection.ALWAYS_LEFT) -1f else 1f
    return PredictiveBackSpecs(
        // 上游 push 即 NavTransitions.MiuixDefault
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
        animationSpec = tween(EXIT_DURATION_MS, easing = ScaleCommitEasing),
        initialOffsetX = { full -> (sign * full).toInt() },
    ) + scaleIn(
        animationSpec = tween(EXIT_DURATION_MS, easing = ScaleCommitEasing),
        initialScale = SCALE_MIN_SCALE,
        transformOrigin = TransformOrigin(pivotFractionX, 0.5f),
    ),
    initialContentExit = ExitTransition.None,
)

/**
 * 经典档：复刻 InstallerX `ClassicNavTransition.ClassicScalePop`（被返回页 `0.9 → 1` 缩放 + 淡入；
 * 当前页按 coverProgress 向离开方向整屏滑出；200ms + CubicBezier(0.2, 0, 0, 1)）。
 */
private fun <T : Any> classicSpecs(rtl: Boolean): PredictiveBackSpecs<T> = PredictiveBackSpecs(
    transition = miuixPush(rtl),
    pop = { classicPopTransform() },
    predictivePop = { classicPopTransform() },
)

private fun classicPopTransform(): ContentTransform = ContentTransform(
    targetContentEnter = scaleIn(
        animationSpec = tween(EXIT_DURATION_MS, easing = ExitEasing),
        initialScale = CLASSIC_MIN_SCALE,
    ) + fadeIn(animationSpec = tween(EXIT_DURATION_MS, easing = ExitEasing)),
    initialContentExit = slideOutHorizontally(
        animationSpec = tween(EXIT_DURATION_MS, easing = ExitEasing),
        targetOffsetX = { full -> -full },
    ),
)

/** 与 InstallerX `ScaleNavTransition.exitDirectionSign()` 一致：跟随手势时「左边沿滑动 → 页面向右退出」。 */
private fun exitDirectionSign(direction: PredictiveBackExitDirection, swipeEdge: Int): Float = when (direction) {
    PredictiveBackExitDirection.FOLLOW_GESTURE -> if (swipeEdge == NavigationEvent.EDGE_LEFT) 1f else -1f
    PredictiveBackExitDirection.ALWAYS_RIGHT -> 1f
    PredictiveBackExitDirection.ALWAYS_LEFT -> -1f
}

// ---------------------------------------------------------------------------
// 装配与效果层参数
// ---------------------------------------------------------------------------

/**
 * 按设置装配返回动画。
 *
 * @param enabled 「预测性返回手势」开关；关闭即不启用返回动画
 *   （等价 InstallerX 的 `NoPredictiveBackTransition`：push/pop 用 Miuix 默认过渡，
 *   手势由 `MainActivity` 的 `NavigationBackHandler` 拦截，不再驱动页面位移）。
 * @param layoutDirection 用于复刻 miuix 过渡的 RTL 镜像行为。
 * @param density 用于把上游的 dp 常量（96dp 漂移 / 8dp 边距）换算为像素。
 */
fun <T : Any> predictiveBackSpecs(
    enabled: Boolean,
    animation: PredictiveBackAnimation,
    exitDirection: PredictiveBackExitDirection,
    layoutDirection: LayoutDirection,
    density: Density,
): PredictiveBackSpecs<T> {
    val rtl = layoutDirection == LayoutDirection.Rtl
    return when {
        !enabled || animation == PredictiveBackAnimation.MIUIX -> miuixSpecs(rtl)
        animation == PredictiveBackAnimation.SCALE -> scaleSpecs(exitDirection, rtl)
        animation == PredictiveBackAnimation.CLASSIC -> classicSpecs(rtl)
        else -> aospSpecs(density)
    }
}

/**
 * 页面圆角裁剪半径：复刻 InstallerX `NavDisplayEffects` 在设备圆角不可用时使用的兜底值 32dp。
 * 圆角模式对应上游 `NavCornerClipMode`：AOSP / 缩放 / 经典档为 All（`roundAllCorners`），
 * Miuix 档为 Leading。
 */
val PredictiveBackCornerRadius = 32.dp

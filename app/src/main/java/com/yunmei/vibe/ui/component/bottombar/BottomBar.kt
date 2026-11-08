package com.yunmei.vibe.ui.component.bottombar

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import com.yunmei.vibe.ui.LocalUiMode
import com.yunmei.vibe.ui.UiMode
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import android.util.Log
import kotlin.math.abs
import kotlinx.coroutines.CancellationException

class MainPagerState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    var isNavigating by mutableStateOf(false)
        private set

    private var navJob: Job? = null

    fun animateToPage(targetIndex: Int) {
        // 以 Pager 真实页为准判断「已经在目标页」：selectedPage 只是镜像状态，
        // 若拿它做早退条件，镜像与真实页错位时点击会被静默吞掉（首页 Banner / 底栏都受影响）。
        val target = targetIndex.coerceIn(0, (pagerState.pageCount - 1).coerceAtLeast(0))
        val alreadyThere = target == pagerState.currentPage && pagerState.currentPageOffsetFraction == 0f
        if (alreadyThere) {
            selectedPage = target
            return
        }

        navJob?.cancel()

        selectedPage = target
        isNavigating = true

        // 时长沿用「距离越远越久」的手感，但滚动交给官方按页吸附的 animateScrollToPage：
        // 自己按像素累加（layoutInfo.pageSize × 页数）在页宽变化、密度变化、动画被打断这几种
        // 情况下会多滚或少滚，表现出来就是「点门锁先闪一下设置页再弹回来」。
        val distance = abs(target - pagerState.currentPage)
        val duration = 100 * (distance + 1)

        navJob = coroutineScope.launch {
            val myJob = coroutineContext.job
            try {
                pagerState.animateScrollToPage(
                    page = target,
                    animationSpec = tween(easing = EaseInOut, durationMillis = duration),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // 动画被外部手势/状态变化打断时不让调用方感知失败，交给下面的兜底纠正。
                Log.i("YunMei", "animateToPage interrupted: " + error.message)
            } finally {
                // 只有「本次仍是最后一次导航」时才收尾：被后来的 animateToPage 顶掉的任务
                // 不能再硬切页面，否则会把新任务的滚动动画瞬间打断（表现为切页没有过渡）。
                if (navJob == myJob) {
                    // 兜底：动画结束仍不在目标页（被打断、页宽为 0 等）就硬切过去。
                    // 否则点击 Banner/底栏会「看起来没反应」——这是首页跳转最隐蔽的失败路径。
                    if (pagerState.currentPage != target) {
                        runCatching { pagerState.scrollToPage(target) }
                    }
                    isNavigating = false
                    selectedPage = pagerState.currentPage
                }
            }
        }
    }
    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
        }
    }
}

@Composable
fun rememberMainPagerState(
    pagerState: PagerState,
    coroutineScope: CoroutineScope = rememberCoroutineScope()
): MainPagerState {
    return remember(pagerState, coroutineScope) {
        MainPagerState(pagerState, coroutineScope)
    }
}

@Composable
fun BottomBar(
    blurBackdrop: LayerBackdrop?,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> BottomBarMiuix(blurBackdrop, backdrop, modifier)
        UiMode.Material -> BottomBarMaterial()
    }
}

@Composable
fun SideRail(
    blurBackdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> NavigationRailMiuix(blurBackdrop, modifier)
        UiMode.Material -> NavigationRailMaterial(modifier)
    }
}

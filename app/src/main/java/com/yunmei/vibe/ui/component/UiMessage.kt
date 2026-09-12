package com.yunmei.vibe.ui.component

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * 提示色调：只表达语义，具体颜色由各主题从自身 ColorScheme 取（见 MessageHost）。
 */
enum class UiMessageTone {
    /** 失败/错误：error 语义色。 */
    Error,

    /** 成功但有需要注意的地方（如登录成功但没有门锁）：tertiary / 警告语义色。 */
    Warning,

    /** 正常成功：primary 语义色。 */
    Success,
}

/** 一条跨页面提示。 */
data class UiMessage(
    val text: String,
    val tone: UiMessageTone = UiMessageTone.Success,
)

/**
 * 跨页面提示总线（一次性事件）。
 *
 * 复用本项目模板的既有约定：`ObserveAsEvents` 的 KDoc 明确要求搭配
 * `Channel(BUFFERED).receiveAsFlow()` 使用——事件在接收方尚未开始收集时会被缓冲，
 * 因此「先发提示、再导航」不会丢消息：登录页 `replaceAll` 回主界面后，
 * 主界面的全局提示宿主再收集并弹出，提示不会随登录页一起被销毁。
 *
 * 发送方为各 ViewModel，接收方为 `MainScreen` 各分支 Scaffold 的 snackbarHost
 * （见 [com.yunmei.vibe.ui.component.message.GlobalMessageHost]）。
 */
object UiMessageBus {
    private val channel = Channel<UiMessage>(Channel.BUFFERED)

    /** 单消费者事件流。 */
    val messages: Flow<UiMessage> = channel.receiveAsFlow()

    /** 发送一条提示（非挂起，队列满时丢弃该条，不阻塞调用方）。 */
    fun send(message: UiMessage) {
        channel.trySend(message)
    }
}

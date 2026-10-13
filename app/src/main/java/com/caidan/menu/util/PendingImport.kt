package com.caidan.menu.util

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * 从系统分享面板收进来的内容，暂存在这里等新建菜谱页取走。
 *
 * 用 [Channel] 而不是 `MutableStateFlow`：同一个链接分享两次时，
 * StateFlow 认为「值没变」不会发射，第二次就静默失效了。
 *
 * 内容和导航信号分成两个 channel —— 各只有一个消费者（导航层收 [arrivals]、
 * 菜谱编辑页取 [takeNow]），不会互相抢。
 */
class PendingImport {

    private val incoming = Channel<String>(Channel.BUFFERED)
    private val arrivals = Channel<Unit>(Channel.BUFFERED)

    /** 每次收到分享发射一次，导航层据此跳到新建菜谱页。 */
    val arrivalFlow: Flow<Unit> = arrivals.receiveAsFlow()

    /** 记下分享过来的文字，并通知导航层。空白内容直接忽略。 */
    fun offer(text: String) {
        if (text.isBlank()) return
        incoming.trySend(text)
        arrivals.trySend(Unit)
    }

    /** 取走一次分享内容，没有就返回 null（不阻塞）。 */
    fun takeNow(): String? = incoming.tryReceive().getOrNull()
}

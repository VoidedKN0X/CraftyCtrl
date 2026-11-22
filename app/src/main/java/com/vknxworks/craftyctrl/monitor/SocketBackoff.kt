package com.vknxworks.craftyctrl.monitor

/** How long to wait before opening a WebSocket again after [failures] failed tries: 2, 4, 8, 16, 32, then 60 s. */
object SocketBackoff {
    fun waitMs(failures: Int): Long = (2_000L shl failures.coerceIn(0, 5)).coerceAtMost(60_000L)
}

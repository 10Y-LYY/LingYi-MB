/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 连接层自动重连（系统级稳定性功能）。
 *
 * 场景：BLE 信号抖动 / USB 接触不良导致连接意外断开时，用户不必退回引导页
 * 重新扫描——按指数退避自动重试，全程无感；指针页、预览页等长时使用界面
 * 尤其受益（掉线后自动恢复，见 PointerModeActivity 的「掉线自动恢复」）。
 *
 * 退避策略（[BackoffSchedule]，纯函数、可单测）：
 *  1s → 2s → 4s → 8s → 16s → 30s → 30s …，最多 [MAX_ATTEMPTS] 次；
 *  任一次重连成功即重置计数；用户主动断开（[noteDrop] userInitiated=true）不触发。
 *
 * 线程安全：连接线程打点、UI 线程读状态，走 [lock]；重连动作固定回主线程执行。
 */
object AutoReconnect {

    const val MAX_ATTEMPTS = 10

    enum class Phase { IDLE, WAITING, CONNECTING, GAVE_UP }

    data class Status(
        val phase: Phase,
        val attempt: Int,
        val nextDelayMs: Long,
        val type: String
    ) {
        fun humanText(): String = when (phase) {
            Phase.IDLE -> ""
            Phase.WAITING -> "连接掉线，${nextDelayMs / 1000} 秒后自动重连（第 $attempt/$MAX_ATTEMPTS 次）"
            Phase.CONNECTING -> "正在自动重连（第 $attempt/$MAX_ATTEMPTS 次）…"
            Phase.GAVE_UP -> "自动重连失败（已试 $MAX_ATTEMPTS 次），请手动重新连接"
        }
    }

    private val lock = ReentrantLock()
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var lastType: String = ""

    @Volatile
    private var lastAddress: String? = null

    @Volatile
    private var appCtx: Context? = null

    private var attempt = 0
    private var phase = Phase.IDLE
    private var pending: Runnable? = null

    /**
     * 压制 depth > 0 期间的掉线打点：主动换连接（connect / 手动重连）时
     * 旧连接的 disconnect 会派发「已断开」，不应被误判为意外掉线而触发重连。
     */
    private var suppressDepth = 0

    /** 在压制掉线打点的区间内执行 [block]（主动换连接用）。 */
    fun <T> runSuppressed(block: () -> T): T {
        lock.withLock { suppressDepth++ }
        try {
            return block()
        } finally {
            lock.withLock { suppressDepth-- }
        }
    }

    /** 状态变化监听（界面展示「自动重连中」）。 */
    private val listeners = CopyOnWriteArrayListLite<StatusListener>()

    /** 自动重连状态监听（页面据此显示「正在自动重连…」）。 */
    interface StatusListener {
        fun onAutoReconnectStatus(status: Status)
    }

    fun addListener(l: StatusListener) = listeners.add(l)
    fun removeListener(l: StatusListener) = listeners.remove(l)

    // ---------- 打点（由 ConnectionHub 调用） ----------

    /** 记录一次连接发起（类型 + 已知地址），作为之后重连的目标。 */
    fun noteConnect(type: String, address: String?, context: Context) {
        lock.withLock {
            lastType = type
            lastAddress = address
            appCtx = context.applicationContext
        }
    }

    /** 连接成功：重置退避计数。 */
    fun noteConnected() {
        val st: Status
        lock.withLock {
            attempt = 0
            phase = Phase.IDLE
            cancelPendingLocked()
            st = snapshotLocked()
        }
        notify(st)
    }

    /**
     * 连接断开。userInitiated=true 表示用户主动断开（不重连）；
     * 否则按退避安排下一次重连。返回是否已安排重连。
     */
    fun noteDrop(userInitiated: Boolean): Boolean {
        if (userInitiated) {
            val st: Status
            lock.withLock {
                cancelPendingLocked()
                attempt = 0
                phase = Phase.IDLE
                st = snapshotLocked()
            }
            notify(st)
            return false
        }
        val ctx = appCtx
        val type = lastType
        val address = lastAddress
        val st: Status
        lock.withLock {
            // 主动换连接期间（runSuppressed）不掉线重连
            if (suppressDepth > 0) return false
            if (ctx == null || type.isEmpty()) return false
            if (!Prefs.isAutoReconnect(ctx)) return false
            // 每次意外掉线都推进一次退避（首次掉线 = 第 1 次重连）；
            // 重连过程中再次掉线视为本次失败，继续下一次退避。
            attempt++
            if (attempt > MAX_ATTEMPTS) {
                phase = Phase.GAVE_UP
                cancelPendingLocked()
                st = snapshotLocked()
                notify(st)
                return false
            }
            phase = Phase.WAITING
            cancelPendingLocked()
            val delay = BackoffSchedule.delayFor(attempt)
            val run = Runnable { fire(ctx, type, address) }
            pending = run
            handler.postDelayed(run, delay)
            st = snapshotLocked()
        }
        notify(st)
        return true
    }

    /** 立即取消所有待执行重连（如用户回到引导页手动重连）。 */
    fun cancel() {
        val st: Status
        lock.withLock {
            cancelPendingLocked()
            attempt = 0
            phase = Phase.IDLE
            st = snapshotLocked()
        }
        notify(st)
    }

    fun status(): Status = lock.withLock { snapshotLocked() }

    fun isEnabled(ctx: Context): Boolean = Prefs.isAutoReconnect(ctx)

    // ---------- 内部 ----------

    private fun fire(ctx: Context, type: String, address: String?) {
        val st: Status
        lock.withLock {
            if (phase != Phase.WAITING) return
            phase = Phase.CONNECTING
            pending = null
            st = snapshotLocked()
        }
        notify(st)
        runCatching {
            if (!address.isNullOrBlank()) {
                ConnectionHubProvider.reconnectByAddress(address, ctx)
            } else {
                ConnectionHubProvider.reconnect(type, ctx)
            }
        }.onFailure {
            AppLog.w("AutoReconnect", "第 $attempt 次自动重连发起失败：${it.message}")
        }
    }

    private fun cancelPendingLocked() {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
    }

    private fun snapshotLocked(): Status = Status(
        phase = phase,
        attempt = attempt,
        nextDelayMs = if (phase == Phase.WAITING) BackoffSchedule.delayFor(attempt) else 0L,
        type = lastType
    )

    private fun notify(st: Status) {
        listeners.forEach { runCatching { it.onAutoReconnectStatus(st) } }
    }
}

/**
 * 指数退避时间表（纯函数，独立于 Android，便于单测）。
 * 第 n 次重连的等待时长：1s, 2s, 4s, 8s, 16s, 30s, 30s …
 */
object BackoffSchedule {
    const val BASE_MS = 1_000L
    const val MAX_MS = 30_000L

    fun delayFor(attempt: Int): Long {
        val n = attempt.coerceAtLeast(1)
        var delay = BASE_MS
        repeat(n - 1) { delay = minOf(delay * 2, MAX_MS) }
        return delay
    }
}

/**
 * ConnectionHub 的间接层：避免 AutoReconnect 直接依赖 ConnectionHub
 * 造成初始化环（ConnectionHub 的打点也要引用 AutoReconnect）。
 * 由 ConnectionHub 在首次加载时注册实现。
 */
object ConnectionHubProvider {
    @Volatile
    var reconnect: (String, Context) -> Unit = { _, _ -> }
        private set

    @Volatile
    var reconnectByAddress: (String, Context) -> Unit = { _, _ -> }
        private set

    fun register(
        byType: (String, Context) -> Unit,
        byAddress: (String, Context) -> Unit
    ) {
        reconnect = byType
        reconnectByAddress = byAddress
    }
}

/** 极简写时复制监听集合（避免直接依赖 androidx 集合类，语义与 CopyOnWrite 一致）。 */
private class CopyOnWriteArrayListLite<T> {
    private val lock = ReentrantLock()
    private var items: List<T> = emptyList()

    fun add(item: T) = lock.withLock { items = items + item }
    fun remove(item: T) = lock.withLock { items = items - item }
    fun forEach(action: (T) -> Unit) {
        val snapshot = lock.withLock { items }
        for (it in snapshot) runCatching { action(it) }
    }
}

/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.util

import com.lingyi.mbos.ble.ConnectionHub
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 调试总线：进程级单例，为「悬浮窗调试控制台」与「问题上报」提供现场数据。
 *
 *  - [packets]：协议抓包环形缓冲（App→设备 / 设备→App / 状态变化），由
 *    [ConnectionHub] 在 send / 派发 / 状态变化处打点，UI 侧零侵入读取。
 *  - 抓包默认关闭（[captureEnabled]），由开发者页「协议抓包开关」打开，
 *    避免常驻内存开销；关闭时打点仅做一次布尔判断，可忽略。
 *  - 线程安全：USB 读线程 / BLE Binder 线程高频写入，UI 线程读取，
 *    全部走 [lock] 串行化；环形上限 [MAX_PACKETS] 条，防止长时间抓包涨内存。
 */
object DebugBus {

    private const val MAX_PACKETS = 400

    enum class Dir { TX, RX, STATE }

    data class Packet(
        val dir: Dir,
        val text: String,
        val timeMs: Long
    ) {
        private val stamp: String = fmt.get()!!.format(Date(timeMs))

        fun render(): String {
            val arrow = when (dir) {
                Dir.TX -> "→ 发送"
                Dir.RX -> "← 接收"
                Dir.STATE -> "◆ 状态"
            }
            return "$stamp $arrow $text"
        }

        companion object {
            private val fmt = ThreadLocal.withInitial {
                SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
            }
        }
    }

    private val lock = ReentrantLock()
    private val ring = ArrayDeque<Packet>()

    @Volatile
    var captureEnabled: Boolean = false
        private set

    fun setCaptureEnabled(on: Boolean) {
        captureEnabled = on
        if (!on) lock.withLock { ring.clear() }
    }

    /** 记录一条发送（App → 设备）。 */
    fun tx(line: String) = record(Dir.TX, line)

    /** 记录一条接收（设备 → App）。 */
    fun rx(line: String) = record(Dir.RX, line)

    /** 记录一条连接状态变化。 */
    fun state(text: String) = record(Dir.STATE, text)

    private fun record(dir: Dir, text: String) {
        if (!captureEnabled) return
        lock.withLock {
            ring.addLast(Packet(dir, text.take(240), System.currentTimeMillis()))
            while (ring.size > MAX_PACKETS) ring.removeFirst()
        }
    }

    /** 最近抓包（默认最多 [n] 条，按时间正序）。 */
    fun packets(n: Int = MAX_PACKETS): List<Packet> =
        lock.withLock { ring.toList().takeLast(n) }

    fun clear() = lock.withLock { ring.clear() }

    fun size(): Int = lock.withLock { ring.size }

    // ---------- 连接检查器快照 ----------

    /** 连接层体检快照（调试控制台「连接检查器」页用）。 */
    fun connectionSnapshot(): String = buildString {
        val conn = ConnectionHub.current
        append("类型：").append(if (conn == null) "无" else conn.type.uppercase()).append('\n')
        append("已连接：").append(if (ConnectionHub.isConnected()) "是" else "否").append('\n')
        append("实例：").append(conn?.javaClass?.simpleName ?: "-").append('\n')
        append("可发送：").append(if (ConnectionHub.isConnected()) "是" else "否（未连接）").append('\n')
    }
}

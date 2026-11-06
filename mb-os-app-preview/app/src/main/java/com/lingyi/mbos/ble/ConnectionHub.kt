/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ble

import android.content.Context
import com.lingyi.mbos.util.AppLog
import com.lingyi.mbos.util.AutoReconnect
import com.lingyi.mbos.util.ConnectionHubProvider
import com.lingyi.mbos.util.DebugBus
import com.lingyi.mbos.util.Prefs
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 连接中枢（进程级单例）。
 * 跨 Activity 持有当前连接，引导页建立连接后，控制台直接复用，无需重新扫描。
 *
 * 线程安全说明：连接建立后，USB 读线程 / BLE Binder 线程会高频迭代 listeners
 * 派发数据；而 Activity 的 onCreate/onDestroy 会在 UI 线程增删监听器。
 * 早期用 LinkedHashSet，并发迭代 + 增改会抛 ConcurrentModificationException：
 * USB 读线程侧有 runCatching 兜底（仅丢数据），而 BLE 的 onCharacteristicChanged
 * 没有兜底，异常直接崩掉进程、被系统重启回引导页（LAUNCHER）。
 * 因此 listeners 必须使用写时复制的并发集合：增改走副本，迭代永远安全。
 *
 * 调试打点（4.1.0-preview）：send / 派发 / 状态变化处向 [DebugBus] 打点，
 * 供悬浮窗调试控制台的「协议抓包 / 连接检查器」与「问题上报」使用；
 * 抓包关闭时打点仅一次布尔判断，零开销。
 *
 * 自动重连（4.1.0-preview）：意外掉线时由 [AutoReconnect] 按指数退避重连，
 * 用户主动断开不触发；主动换连接（connect*）期间压制掉线打点，避免误触发。
 */
object ConnectionHub {
    var current: MicrobitConnection? = null
        private set
    private val listeners = CopyOnWriteArraySet<MicrobitConnection.Listener>()

    /** 应用上下文：成功连接后把「连接方式 + 地址」记进 Prefs，供下次进 App 静默重连。 */
    @Volatile
    private var appCtx: Context? = null

    init {
        // 注册自动重连入口（间接层打破初始化环，见 ConnectionHubProvider）
        ConnectionHubProvider.register(
            byType = { type, ctx -> reconnect(type, ctx) },
            byAddress = { address, ctx -> reconnectByAddress(address, ctx) }
        )
    }

    fun connect(type: String, context: Context, listener: MicrobitConnection.Listener) {
        listeners.add(listener)
        appCtx = context.applicationContext
        AutoReconnect.runSuppressed {
            AutoReconnect.noteConnect(type, null, context)
            current?.disconnect()
            val conn: MicrobitConnection = if (type == "usb") UsbConnection() else BleConnection()
            current = conn
            runCatching { conn.connect(context, wrap()) }
                .onFailure { AppLog.e("Conn", "建立 ${type.uppercase()} 连接失败", it) }
        }
    }

    /** 按系统蓝牙地址直连（手动从已配对列表选择时使用）。 */
    fun connectByAddress(address: String, context: Context, listener: MicrobitConnection.Listener) {
        listeners.add(listener)
        appCtx = context.applicationContext
        AutoReconnect.runSuppressed {
            AutoReconnect.noteConnect("ble", address, context)
            current?.disconnect()
            val conn = BleConnection()
            current = conn
            runCatching { conn.connectByAddress(context, address, wrap()) }
                .onFailure { AppLog.e("Conn", "按地址直连失败：$address", it) }
        }
    }

    /**
     * 接入 micro:bit V2 模拟器（开发者模式悬浮窗）：
     * 与真机走完全同一条派发链路，各页面无感知；
     * 后续任何真机连接（connect / connectByAddress）都会先 disconnect 当前连接，
     * 因此真机接入时模拟器自动让位。
     */
    fun connectSimulator(context: Context, listener: MicrobitConnection.Listener) {
        listeners.add(listener)
        appCtx = context.applicationContext
        AutoReconnect.runSuppressed {
            AutoReconnect.noteConnect("sim", null, context)
            current?.disconnect()
            val conn = SimulatorConnection()
            current = conn
            runCatching { conn.connect(context, wrap()) }
                .onFailure { AppLog.e("Conn", "接入模拟器失败", it) }
        }
    }

    private fun wrap(): MicrobitConnection.Listener = object : MicrobitConnection.Listener {
        override fun onState(state: ConnState) {
            AppLog.i("Conn", "连接状态：${if (state.connected) "已连接" else "未连接"} · ${state.message}")
            DebugBus.state("${if (state.connected) "已连接" else "未连接"} · ${state.message}")
            listeners.forEach { runCatching { it.onState(state) } }
            if (state.connected) {
                rememberLastLink()
                AutoReconnect.noteConnected()
            } else {
                AutoReconnect.noteDrop(userInitiated = false)
            }
        }

        override fun onData(data: String) {
            DebugBus.rx(data)
            // 单监听器隔离：任一监听器抛异常都不应中断派发链
            // （未捕获异常会沿 USB 读线程 / BLE Binder 线程直接崩掉进程）
            listeners.forEach { runCatching { it.onData(data) } }
        }
    }

    /** 连接成功后记住「方式 + 地址」：下次进 App 无需用户操作即可静默连回。 */
    private fun rememberLastLink() {
        val conn = current ?: return
        val ctx = appCtx ?: return
        if (!conn.isConnected()) return
        runCatching { Prefs.setLastLink(ctx, conn.type, conn.address) }
            .onFailure { AppLog.w("Conn", "记录上次连接失败：${it.message}") }
    }

    fun addListener(listener: MicrobitConnection.Listener) {
        listeners.add(listener)
        current?.let {
            // 立即补发当前状态（调用方多为 UI 线程；隔离异常，避免单个监听器出错拖垮界面）
            runCatching { listener.onState(ConnState(it.isConnected(), if (it.isConnected()) "已连接" else "未连接")) }
        }
    }

    fun removeListener(listener: MicrobitConnection.Listener) {
        listeners.remove(listener)
    }

    fun send(line: String): Boolean {
        DebugBus.tx(line)
        return current?.send(line) ?: false
    }

    fun isConnected(): Boolean = current?.isConnected() ?: false

    /** 当前连接类型（"usb" / "ble" / "sim" / 空）。 */
    fun type(): String = current?.type ?: ""

    // ---------- 开发者调试 ----------
    /** 向所有监听器广播一条模拟「设备 → App」数据，用于调试接收 / 解析链路（无需真机）。 */
    fun debugEmitData(data: String) {
        DebugBus.rx(data)
        listeners.forEach { runCatching { it.onData(data) } }
    }

    /** 广播一条模拟连接状态变化，用于调试各处状态 UI。 */
    fun debugEmitState(connected: Boolean, msg: String) {
        listeners.forEach { runCatching { it.onState(ConnState(connected, msg)) } }
    }

    /**
     * 模拟「意外断线」：直接断开传输层但不标记为用户主动断开，
     * 让派发的「已断开」状态照常走 AutoReconnect 的意外掉线路径——
     * 用于在开发者页 / 悬浮窗里演练指数退避自动重连是否生效。
     */
    fun debugSimulateDrop() {
        AppLog.event("调试", "模拟意外断线（演练自动重连）")
        runCatching { current?.disconnect() }
    }

    /**
     * 主动断开当前连接（用户意图）：取消自动重连。
     * 注意：不要在这里清空 listeners——各页面自行在 onDestroy 移除；
     * 且连接实现内部会先派发「已断开」状态，清空会让界面收不到更新而卡在“已连接”。
     */
    fun disconnect() {
        AutoReconnect.noteDrop(userInitiated = true)
        current?.disconnect()
        current = null
    }

    /**
     * 进页 / 进功能前「确保已连上」：以 Prefs 中记录的上次连接方式（类型 + 地址）静默重连。
     * 对齐华为运动健康「进 App 即自动连回上次设备」——全程不弹任何对话框，
     * 失败由 [AutoReconnect] 退避重试，界面状态行显示「正在重连…」。
     *
     * @param force true 时忽略「已连接 / 握手中」直接重连（手动点「重新连接」用）；
     *              false 时若当前已连接或有连接进行中则什么都不做（返回 false）。
     * @return 是否真正发起了重连（false = 无需重连，或没有上次连接记录）。
     */
    fun ensureLink(context: Context, force: Boolean = false): Boolean {
        if (!force && (isConnected() || current != null)) return false
        val ctx = context.applicationContext
        appCtx = ctx
        val type = Prefs.getLastLinkType(ctx) ?: return false
        val addr = Prefs.getLastLinkAddress(ctx)
        AutoReconnect.runSuppressed {
            if (addr.isNullOrBlank()) reconnect(type, ctx) else reconnectByAddress(addr, ctx)
        }
        return true
    }

    // ---------- 自动重连入口（AutoReconnect 回调，不新增 listeners） ----------

    /** 按类型重建连接（BLE 重新扫描 / USB 重新 claim / 模拟器重启）。 */
    fun reconnect(type: String, context: Context) {
        AutoReconnect.runSuppressed {
            AutoReconnect.noteConnect(type, null, context)
            current?.disconnect()
            val conn: MicrobitConnection = when (type) {
                "usb" -> UsbConnection()
                "sim" -> SimulatorConnection()
                else -> BleConnection()
            }
            current = conn
            runCatching { conn.connect(context, wrap()) }
                .onFailure { AppLog.e("Conn", "自动重连（${type.uppercase()}）发起失败", it) }
        }
    }

    /** 按已知蓝牙地址重连（跳过扫描，成功率更高）。 */
    fun reconnectByAddress(address: String, context: Context) {
        AutoReconnect.runSuppressed {
            AutoReconnect.noteConnect("ble", address, context)
            current?.disconnect()
            val conn = BleConnection()
            current = conn
            runCatching { conn.connectByAddress(context, address, wrap()) }
                .onFailure { AppLog.e("Conn", "自动重连（BLE 地址）发起失败：$address", it) }
        }
    }
}

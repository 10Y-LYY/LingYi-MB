/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

import android.os.Handler
import android.os.Looper
import com.lingyi.mbos.ble.ConnectionHub
import com.lingyi.mbos.ble.ConnState
import com.lingyi.mbos.ble.MicrobitConnection

/**
 * 一条 READ 回包的解析结果（字段与固件 / 模拟器完全一致：
 * `A:{0|1}|B:{0|1}|T:{温度}|L:{光线}|X:{x}|Y:{y}|Z:{z}|C:{朝向}|G:{手势}`）。
 */
data class SensorSnapshot(
    val a: Boolean,
    val b: Boolean,
    val t: Double?,
    val l: Int?,
    val x: Int?,
    val y: Int?,
    val z: Int?,
    val c: Int?,
    val g: String?
)

/**
 * 传感器轮询器：对话框 / 页面级复用。
 *
 * 打开时按 [intervalMs] 周期性发送 `READ`，收到回包解析成 [SensorSnapshot]
 * 并在主线程回调；关闭时退订并停止轮询。温度计 / 光线计 / 水平仪 /
 * 按钮监视器 / 记录仪 / 骰子摇一摇 / 反应测试 / 猜数字 / 石头剪刀布共用。
 *
 * 线程模型：onData 可能来自 USB 读线程或 BLE Binder 线程，
 * 内部统一 post 到主 Handler 再回调，调用方无需关心线程。
 */
class SensorPoller(private val intervalMs: Long = 800L) {

    private val handler = Handler(Looper.getMainLooper())
    private var listener: MicrobitConnection.Listener? = null
    private var active = false

    /** 收到一条有效读数（主线程）。 */
    var onSnapshot: ((SensorSnapshot) -> Unit)? = null

    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            ConnectionHub.send(Protocol.read())
            handler.postDelayed(this, intervalMs)
        }
    }

    /** 开始轮询（重复调用安全）。 */
    fun start() {
        if (active) return
        active = true
        val l = object : MicrobitConnection.Listener {
            override fun onState(state: ConnState) {}
            override fun onData(data: String) {
                val snap = parse(data) ?: return
                handler.post { onSnapshot?.invoke(snap) }
            }
        }
        listener = l
        ConnectionHub.addListener(l)
        ACTIVE.add(this) // 宿主 Activity 销毁时兜底停止（v4.2 修复对话框泄漏）
        handler.postDelayed(tick, 0)
    }

    /** 停止轮询并退订（重复调用安全；对话框 onDismiss 里必须调用）。 */
    fun stop() {
        active = false
        handler.removeCallbacks(tick)
        listener?.let { ConnectionHub.removeListener(it) }
        listener = null
        ACTIVE.remove(this)
    }

    companion object {
        /**
         * 全部存活轮询器：对话框宿主 Activity 销毁时 onDismiss 不一定回调
         * （AlertDialog 不随宿主销毁自动 dismiss），这里兜底 stop 全部，
         * 防止读线程泄漏 + 后台持续发 READ 耗电（v4.2）。
         */
        private val ACTIVE = java.util.concurrent.CopyOnWriteArraySet<SensorPoller>()

        /** 宿主销毁时调用：停止所有仍在运行的轮询器。 */
        fun stopAll() {
            ACTIVE.toList().forEach { it.stop() }
        }

        /**
         * 解析固件 / 模拟器的 READ 行。非 READ 行（含 ERR: 回显、MOUSE: 增量等）返回 null。
         * 容忍缺字段（老固件未上报的项为 null），但必须含 A: 才算 READ 行。
         */
        fun parse(line: String): SensorSnapshot? {
            val trimmed = line.trim()
            if (!trimmed.startsWith("A:")) return null
            val fields = HashMap<String, String>(9)
            trimmed.split('|').forEach { seg ->
                val p = seg.split(':', limit = 2)
                if (p.size == 2) fields[p[0].trim()] = p[1].trim()
            }
            if (!fields.containsKey("A")) return null
            fun int(key: String): Int? = fields[key]?.toIntOrNull()
            fun dbl(key: String): Double? = fields[key]?.toDoubleOrNull()
            return SensorSnapshot(
                a = fields["A"] == "1",
                b = fields["B"] == "1",
                t = dbl("T"),
                l = int("L"),
                x = int("X"),
                y = int("Y"),
                z = int("Z"),
                c = int("C"),
                g = fields["G"]?.takeIf { it.isNotEmpty() && it != "none" }
            )
        }
    }
}

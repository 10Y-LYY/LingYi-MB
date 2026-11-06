/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ble

import android.content.Context

/** 连接状态。 */
data class ConnState(
    val connected: Boolean,
    val message: String
)

/**
 * micro:bit 连接抽象。两种实现：
 *  - BleConnection：蓝牙 BLE（Nordic UART / micro:bit UART 服务）
 *  - UsbConnection：USB OTG 直连（CDC-ACM 串口）
 */
interface MicrobitConnection {
    val type: String   // "ble" | "usb" | "sim"

    /**
     * 连接目标地址（BLE = 蓝牙 MAC；USB / 模拟器无地址）。
     * 用于「回到 App 后静默重连上次设备」，默认 null 表示无需地址。
     */
    val address: String?
        get() = null

    fun connect(context: Context, listener: Listener)
    fun disconnect()
    fun send(line: String): Boolean
    fun isConnected(): Boolean

    interface Listener {
        fun onState(state: ConnState)
        fun onData(data: String)
    }
}
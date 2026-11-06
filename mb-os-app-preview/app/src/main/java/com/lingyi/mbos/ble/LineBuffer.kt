/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ble

/**
 * 串口行缓冲：把底层按包到达的字节流重组为「完整行」。
 *
 * 为什么需要它：
 *  · USB 批量读一次可能读到多行 / 半行；
 *  · BLE 通知受 ATT MTU 限制（安卓默认仅 23 字节、载荷 20），
 *    固件的 INFO（约 46 字节）/ READ（约 44 字节）响应必然被拆成多个通知，
 *    若不重组，`MB-OS v` 版本识别、传感器读数、MOUSE 数据都会因半行而失效。
 *
 * 线程安全：仅由单一日志读取方（USB 读线程 / BLE Binder 线程）调用。
 */
class LineBuffer {

    private val sb = StringBuilder()

    /** 追加一段新到达的字节流，返回其中已完整的行（不含换行符，已 trim）。 */
    fun append(chunk: String): List<String> {
        if (chunk.isEmpty()) return emptyList()
        sb.append(chunk)
        val lines = mutableListOf<String>()
        var idx: Int
        while (sb.indexOf('\n').also { idx = it } >= 0) {
            val line = sb.substring(0, idx).trim()
            sb.delete(0, idx + 1)
            if (line.isNotEmpty()) lines.add(line)
        }
        return lines
    }

    /** 清空残留半行（连接断开时调用，避免下一条连接的半行污染）。 */
    fun reset() {
        sb.clear()
    }
}

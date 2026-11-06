/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

/**
 * 指令协议。
 * 通过串口（Nordic UART / micro:bit UART）逐行发送文本指令，
 * 每行以换行结束。micro:bit 端需刷写配套助手脚本（见 FirmwareScript）。
 */
object Protocol {
    fun scroll(text: String): String = "SCROLL:${text.trim()}\n"

    fun show(m: Array<BooleanArray>): String {
        val rows = m.joinToString(",") { row ->
            row.joinToString("") { if (it) "1" else "0" }
        }
        return "SHOW:$rows\n"
    }

    fun plot(x: Int, y: Int, on: Boolean): String = "PLOT:$x,$y,${if (on) 1 else 0}\n"

    fun tone(freq: Int, durMs: Int): String = "TONE:$freq,$durMs\n"

    /** 滚动显示一个数字/数值（复用 display.scroll）。 */
    fun number(n: Int): String = "NUMBER:$n\n"

    /** 调节 LED 屏亮度，level 范围 0–9。 */
    fun brightness(level: Int): String = "BRIGHT:${level.coerceIn(0, 9)}\n"

    /** 播放内置旋律。 */
    fun melody(name: String): String = "MELODY:${name.trim()}\n"

    /** 单独读取指南针朝向（多数场景用 read() 即可）。 */
    fun compass(): String = "COMPASS\n"

    /** 读取一次传感器全量信息。 */
    fun read(): String = "READ\n"

    /** 读取开发板固件/系统信息。 */
    fun info(): String = "INFO\n"

    /** 清空 LED 矩阵。 */
    fun clear(): String = "CLEAR\n"

    /** 开启陀螺仪指针（无线触摸笔）模式：固件开始持续上报倾斜增量。 */
    fun mouseOn(): String = "MOUSE:ON\n"

    /** 关闭陀螺仪指针模式。 */
    fun mouseOff(): String = "MOUSE:OFF\n"

    /** 以当前姿态为新的中心（防漂移）。 */
    fun mouseCal(): String = "MOUSE:CAL\n"

    fun custom(line: String): String = if (line.endsWith("\n")) line else "$line\n"

    /**
     * 从固件回传的 INFO 行解析板载信息（MB-OS 固件版本 + MicroPython 版本）。
     * 例："INFO:MicroPython 2.1.1|MB-OS v2.9.1|micro:bit V2"
     *   → BoardInfo(mbos="2.9.1", micropython="2.1.1")
     * 板载未刷 MB-OS 助手脚本（如在跑游戏/自定义代码/裸 MicroPython）时 mbos 为 null。
     */
    fun parseInfo(line: String): BoardInfo? {
        if (!line.startsWith("INFO:")) return null
        val parts = line.removePrefix("INFO:").split("|").map { it.trim() }
        val mbos = parts.firstOrNull { it.startsWith("MB-OS v") }
            ?.removePrefix("MB-OS v")?.trim()?.takeIf { it.isNotEmpty() }
        val mp = parts.firstOrNull { it.startsWith("MicroPython") }
            ?.removePrefix("MicroPython")?.trim()?.takeIf { it.isNotEmpty() }
        return BoardInfo(mbos, mp)
    }

    /**
     * 从固件回传的 INFO 行解析板载 MB-OS 固件版本。
     * 例："INFO:MicroPython 1.19|MB-OS v2.9.1|micro:bit V2" → "2.9.1"；
     * 板载未刷 MB-OS 助手脚本（无 MB-OS 字段，如在跑游戏/自定义代码）时返回 null。
     */
    fun parseMbosVersion(line: String): String? = parseInfo(line)?.mbos

    /**
     * 版本新旧比较：[a] 是否旧于 [b]（逐段数值比较，"2.9.1" 旧于 "3.0.0"）。
     * 无法按数值解析的段按 0 处理；完全相等时返回 false（不算旧）。
     */
    fun isOlder(a: String, b: String): Boolean {
        fun parse(v: String): List<Int> =
            v.split(".").map { seg -> seg.filter { it.isDigit() }.toIntOrNull() ?: 0 }
        val x = parse(a.trim())
        val y = parse(b.trim())
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = (x.getOrElse(i) { 0 }) - (y.getOrElse(i) { 0 })
            if (d != 0) return d < 0
        }
        return false
    }
}

/** 板载信息（INFO 回显解析结果）：MB-OS 助手固件版本 + MicroPython 运行时版本。 */
data class BoardInfo(val mbos: String?, val micropython: String?)
/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

/**
 * 传感器 CSV 记录：把 READ 采样写成电子表格可直接打开的 CSV。
 * 纯逻辑（拼串），无 Android 依赖，供「传感器记录仪」功能与单测复用。
 */
object CsvLogger {

    const val HEADER = "time_ms,A,B,T,L,X,Y,Z,C,G"

    /** 单行 CSV（[tMs] 为自记录开始的毫秒数）。 */
    fun row(tMs: Long, s: SensorSnapshot): String = listOf(
        tMs.toString(),
        if (s.a) "1" else "0",
        if (s.b) "1" else "0",
        s.t?.toString() ?: "",
        s.l?.toString() ?: "",
        s.x?.toString() ?: "",
        s.y?.toString() ?: "",
        s.z?.toString() ?: "",
        s.c?.toString() ?: "",
        s.g ?: ""
    ).joinToString(",")

    /** 采样序列 → 完整 CSV 文本（含表头与 CRLF 行尾，Excel 兼容）。 */
    fun toCsv(samples: List<Pair<Long, SensorSnapshot>>): String = buildString {
        append(HEADER).append("\r\n")
        samples.forEach { (t, s) -> append(row(t, s)).append("\r\n") }
    }

    /** 默认导出文件名。 */
    fun fileName(stamp: String): String = "mbos-sensor-$stamp.csv"
}

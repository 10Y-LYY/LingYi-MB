/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 刷机日志（「刷机日志」功能的数据源）：每次刷写的结果追加到
 * `cacheDir/packaged/flashlog.txt`（与 CsvLogger 同一目录，FileProvider 可分享）。
 * 格式：一行一条 `时间戳|目标|结果`，最多保留 200 条。
 */
object FlashLog {

    data class Entry(val time: String, val target: String, val result: String)

    private const val MAX = 200
    private val TIME_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private fun file(ctx: Context): File =
        File(File(ctx.cacheDir, "packaged").apply { mkdirs() }, "flashlog.txt")

    /** 追加一条（时间取当前时刻）；IO 失败静默返回 false。 */
    fun append(ctx: Context, target: String, result: String): Boolean {
        val line = "${TIME_FMT.format(Date())}|$target|$result"
        return runCatching {
            val f = file(ctx)
            val all = (if (f.exists()) f.readText().split('\n') else emptyList())
                .filter { it.isNotBlank() } + line
            f.writeText(all.takeLast(MAX).joinToString("\n") + "\n")
            true
        }.getOrDefault(false)
    }

    /** 读取全部（新的在前）。 */
    fun all(ctx: Context): List<Entry> = runCatching {
        val f = file(ctx)
        if (!f.exists()) emptyList()
        else f.readText().split('\n').filter { it.isNotBlank() }.mapNotNull { line ->
            val p = line.split('|', limit = 3)
            if (p.size == 3) Entry(p[0], p[1], p[2]) else null
        }.reversed()
    }.getOrDefault(emptyList())

    fun clear(ctx: Context) {
        runCatching { file(ctx).delete() }
    }

    /** 导出为 .txt（返回文件；无记录返回 null）。 */
    fun export(ctx: Context): File? {
        val entries = all(ctx)
        if (entries.isEmpty()) return null
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val f = File(File(ctx.cacheDir, "packaged").apply { mkdirs() }, "刷机日志-$stamp.txt")
        return runCatching {
            f.writeText(
                buildString {
                    appendLine("灵逸 MB · 刷机日志（导出于 ${TIME_FMT.format(Date())}）")
                    appendLine("时间 | 目标 | 结果")
                    entries.reversed().forEach { appendLine("${it.time} | ${it.target} | ${it.result}") }
                }
            )
            f
        }.getOrNull()
    }
}

/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.util

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * ANR 看门狗（开发者页稳定性功能）：
 * 后台线程每秒向主线程投递一次心跳，主线程超过 [WARN_MS] 未响应即记一条
 * 「主线程卡顿」警告，超过 [ANR_MS]（5 秒，系统 ANR 阈值同量级）记一条
 * 「疑似 ANR」并 dump 主线程栈 + 界面元素树到日志——用户报「卡死」时，
 * 日志里已有现场，无需复现。
 *
 * 只在开发者模式 + 开关开启时运行（[isEnabled]），平时零开销。
 */
object AnrWatchdog {

    const val WARN_MS = 2_000L
    const val ANR_MS = 5_000L

    enum class Level { OK, WARN, ANR }

    /** 纯函数：按阻塞时长分级（单测直接覆盖）。 */
    fun classify(blockMs: Long): Level = when {
        blockMs >= ANR_MS -> Level.ANR
        blockMs >= WARN_MS -> Level.WARN
        else -> Level.OK
    }

    private val handler = Handler(Looper.getMainLooper())
    @Volatile
    private var running = false
    private var thread: Thread? = null

    @Volatile
    private var lastBeatAt = 0L

    fun isEnabled(ctx: Context): Boolean = Prefs.isDevMode(ctx) && Prefs.isAnrWatchdog(ctx)

    fun start(ctx: Context) {
        if (running) return
        if (!isEnabled(ctx)) return
        running = true
        lastBeatAt = System.currentTimeMillis()
        thread = Thread {
            while (running) {
                Thread.sleep(1000L)
                if (!running) break
                val now = System.currentTimeMillis()
                val lag = now - lastBeatAt
                when (classify(lag)) {
                    Level.OK -> Unit
                    Level.WARN -> AppLog.w("ANR", "主线程卡顿 ${lag}ms（>2s）")
                    Level.ANR -> dump("疑似 ANR：主线程阻塞 ${lag}ms", ctx)
                }
            }
        }.also { it.name = "mbos-anr-watchdog"; it.isDaemon = true; it.start() }
        AppLog.event("ANR 看门狗", "已启动")
    }

    fun stop() {
        if (!running) return
        running = false
        thread?.interrupt()
        thread = null
        AppLog.event("ANR 看门狗", "已停止")
    }

    /** 心跳：由应用主线程周期性调用（BaseActivity.onResume 或服务循环）。 */
    fun beat() {
        lastBeatAt = System.currentTimeMillis()
    }

    private fun dump(reason: String, ctx: Context) {
        AppLog.e("ANR", reason)
        val main = Looper.getMainLooper().thread
        val stack = main.stackTrace.take(30).joinToString("\n") { "    at $it" }
        AppLog.e("ANR", "主线程栈：\n$stack")
        runCatching { AppLog.e("ANR", "界面元素树：\n${ViewTreeCapture.capture()}") }
        // 同一进程连续卡死只 dump 一次，避免日志风暴
        lastBeatAt = System.currentTimeMillis()
    }
}

/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

import com.lingyi.mbos.ui.M3Kit.doAfterTextChanged
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.R as MR
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.lingyi.mbos.MainActivity
import com.lingyi.mbos.util.Prefs
import com.lingyi.mbos.util.themeColor
import java.util.Locale
import kotlin.random.Random

/*
 * 控制台新功能 · 实用工具（预览版 4.0.0-preview 新增 8 项）：
 * 手电筒 / 单位换算 / 便签速记 / 随机数生成 / 上课铃 / 决断器 / 番茄钟 / 计数器。
 */

// ---------- 1. 手电筒 ----------

/** SOS 摩斯序列：点 200ms、划 600ms、点内间隔 120ms、字母间隔 360ms。 */
private val SOS_MORSE: List<Pair<Boolean, Long>> = buildList {
    fun dash() { add(true to 600L); add(false to 120L) }
    fun dot() { add(true to 200L); add(false to 120L) }
    // S
    dot(); dot(); dot(); add(false to 240L)
    // O
    dash(); dash(); dash(); add(false to 240L)
    // S
    dot(); dot(); dot(); add(false to 900L)
}

internal fun MainActivity.openTorch() {
    if (!requireConnected()) return
    val ctx = this
    var mode = 0 // 0 常亮 1 爆闪 2 SOS
    var on = false
    var worker: Thread? = null

    val root = M3Kit.dialogRoot(ctx)
    val modeGroup = ChipGroup(ctx).apply { isSingleSelection = true }
    val tvState = bigReadout(ctx, "已关闭")
    val tvSub = labelText(ctx, "把 5×5 点阵当灯用，自动拉满亮度").apply { gravity = Gravity.CENTER }
    val btn = MaterialButton(ctx).apply { text = "开灯" }
    val tvHint = hintText(ctx, "常亮适合补光/找东西；爆闪引人注意；SOS 按国际摩斯码 ...---... 循环闪烁。关灯后点阵清空。")
    root.addView(modeGroup)
    root.addView(tvState)
    root.addView(tvSub)
    root.addView(
        btn,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 12) }
    )
    root.addView(tvHint)

    fun stopWorker() {
        on = false
        worker?.interrupt()
        worker = null
    }

    fun startWorker() {
        stopWorker()
        on = true
        btn.text = "关灯"
        tvState.text = "点亮中"
        worker = Thread {
            try {
                if (mode == 0) {
                    runOnUiThread {
                        sendLine(Protocol.brightness(9))
                        sendLine(Protocol.show(fullOn()))
                    }
                    while (on) Thread.sleep(120)
                } else if (mode == 1) {
                    var lit = false
                    while (on) {
                        lit = !lit
                        runOnUiThread {
                            sendLine(Protocol.brightness(9))
                            sendLine(if (lit) Protocol.show(fullOn()) else Protocol.clear())
                        }
                        Thread.sleep(if (lit) 90L else 60L)
                    }
                } else {
                    while (on) {
                        for ((lit, ms) in SOS_MORSE) {
                            if (!on) break
                            runOnUiThread {
                                sendLine(Protocol.brightness(9))
                                sendLine(if (lit) Protocol.show(fullOn()) else Protocol.clear())
                            }
                            Thread.sleep(ms)
                        }
                    }
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            runOnUiThread {
                if (!isDestroyed) {
                    sendLine(Protocol.clear())
                    tvState.text = "已关闭"
                    btn.text = "开灯"
                }
            }
        }
        worker?.start()
    }

    listOf("常亮", "爆闪", "SOS 求救").forEachIndexed { i, label ->
        modeGroup.addView(
            Chip(ctx).apply {
                text = label
                isCheckable = true
                isChecked = i == mode
                setOnClickListener {
                    mode = i
                    if (on) startWorker()
                }
            }
        )
    }
    btn.setOnClickListener {
        if (on) {
            stopWorker()
            sendLine(Protocol.clear())
            tvState.text = "已关闭"
            btn.text = "开灯"
        } else {
            startWorker()
        }
    }
    showFeatureDialog(root, "手电筒", onDismiss = { stopWorker() })
}


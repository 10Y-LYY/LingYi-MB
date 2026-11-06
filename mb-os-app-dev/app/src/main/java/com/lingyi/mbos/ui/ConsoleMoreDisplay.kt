/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

import com.lingyi.mbos.ui.M3Kit.doAfterTextChanged
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.R as MR
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.lingyi.mbos.MainActivity
import com.lingyi.mbos.util.themeColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * 控制台新功能 · 显示与预览（预览版 4.0.0-preview 新增 7 项）：
 * 桌面时钟 / 秒表 / 倒计时 / 走马灯 / 大数字 / 亮度自适应 / 小夜灯。
 * 全部复用既有协议（SHOW / SCROLL / NUMBER / BRIGHT / TONE / READ），老固件免刷写。
 */

internal fun MainActivity.openMarquee() {
    val ctx = this
    var sending = false

    val root = M3Kit.dialogRoot(ctx)
    val et = EditText(ctx).apply {
        hint = "输入要走播报的文字"
        inputType = android.text.InputType.TYPE_CLASS_TEXT
        setText("灵逸 MB · 欢迎使用")
        setTextSize(15f)
    }
    val speedGroup = ChipGroup(ctx).apply { isSingleSelection = true }
    val speeds = listOf("慢" to 320L, "中" to 190L, "快" to 90L)
    var perCharMs = 190L
    speeds.forEach { (label, ms) ->
        speedGroup.addView(
            Chip(ctx).apply {
                text = label
                isCheckable = true
                isChecked = ms == perCharMs
                setOnClickListener { perCharMs = ms }
            }
        )
    }
    val repeatGroup = ChipGroup(ctx).apply { isSingleSelection = true }
    val repeats = listOf("1 遍" to 1, "3 遍" to 3, "无限" to 0)
    var repeatCount = 1
    repeats.forEach { (label, n) ->
        repeatGroup.addView(
            Chip(ctx).apply {
                text = label
                isCheckable = true
                isChecked = n == repeatCount
                setOnClickListener { repeatCount = n }
            }
        )
    }
    val btnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    val btnSend = MaterialButton(ctx).apply { text = "开始走播" }
    val btnStop = MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply {
        text = "停止"
        isEnabled = false
    }
    btnRow.addView(btnSend)
    btnRow.addView(
        btnStop,
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = M3Kit.dp(ctx, 10) }
    )
    val tvHint = hintText(ctx, "一条文字反复滚动，速度三档可调；适合展台标语、寻物告示、课堂口令。")
    root.addView(et)
    root.addView(
        speedGroup,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 6) }
    )
    root.addView(
        repeatGroup,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 6) }
    )
    root.addView(
        btnRow,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 10) }
    )
    root.addView(tvHint)

    btnSend.setOnClickListener {
        val text = et.text.toString().trim()
        if (text.isEmpty()) {
            featureSnack("先输入文字")
            return@setOnClickListener
        }
        if (!requireConnected() || sending) return@setOnClickListener
        sending = true
        btnStop.isEnabled = true
        btnSend.text = "走播中…"
        Thread {
            try {
                var round = 0
                while (sending && !isFinishing && !isDestroyed) {
                    runOnUiThread {
                        sendLine(Protocol.clear())
                        sendLine(Protocol.scroll(text))
                    }
                    Thread.sleep(500L + text.length * perCharMs)
                    round++
                    if (repeatCount > 0 && round >= repeatCount) break
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                sending = false
                runOnUiThread {
                    btnSend.text = "开始走播"
                    btnStop.isEnabled = false
                    sendLine(Protocol.clear())
                }
            }
        }.start()
    }
    btnStop.setOnClickListener {
        sending = false
        sendLine(Protocol.clear())
    }
    showFeatureDialog(root, "走马灯")
}

// ---------- 5. 大数字 ----------

internal fun MainActivity.openBigDigit() {
    val ctx = this
    val text = StringBuilder("2026")
    var playing = false

    val root = M3Kit.dialogRoot(ctx)
    val et = EditText(ctx).apply {
        hint = "输入数字（0-9 与 - . 可用）"
        inputType = android.text.InputType.TYPE_CLASS_TEXT
        setText(text.toString())
        textSize = 15f
    }
    et.doAfterTextChanged { s ->
        text.clear()
        text.append(s.filter { DigitFont.has(it) })
    }
    // App 端预览（3×5 字形放大）
    val preview = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    fun refreshPreview() {
        preview.removeAllViews()
        text.toString().forEach { c ->
            val rows = DigitFont.glyph(c)
            val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            rows.forEach { r ->
                val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
                r.forEach { ch ->
                    val dot = View(ctx).apply {
                        background = android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                            cornerRadius = M3Kit.dp(ctx, 2).toFloat()
                            setColor(
                                if (ch == '1') ctx.themeColor(MR.attr.colorPrimary)
                                else ctx.themeColor(MR.attr.colorSurfaceVariant)
                            )
                        }
                    }
                    row.addView(
                        dot,
                        LinearLayout.LayoutParams(M3Kit.dp(ctx, 14), M3Kit.dp(ctx, 14)).apply { marginStart = M3Kit.dp(ctx, 2) }
                    )
                }
                col.addView(row)
            }
            preview.addView(
                col,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = M3Kit.dp(ctx, 8) }
            )
        }
    }
    refreshPreview()
    val btnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    val btnPlay = MaterialButton(ctx).apply { setText("逐位播放") }
    val btnNow = MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply { setText("显示首位") }
    btnRow.addView(btnPlay)
    btnRow.addView(
        btnNow,
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = M3Kit.dp(ctx, 10) }
    )
    val tvHint = hintText(ctx, "3×5 大字库逐位显示，比 NUMBER 滚动更醒目；可放比分、倒计时秒数、编号。")
    root.addView(et)
    root.addView(
        preview,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 12) }
    )
    root.addView(
        btnRow,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 14) }
    )
    root.addView(tvHint)

    fun showFirst() {
        val c = text.firstOrNull() ?: return
        val rows = DigitFont.glyph(c)
        val m = rowsToMatrix(rows)
        sendLine(Protocol.show(m))
    }

    btnNow.setOnClickListener {
        if (!requireConnected()) return@setOnClickListener
        showFirst()
    }
    btnPlay.setOnClickListener {
        val chars = text.toString()
        if (chars.isEmpty()) {
            featureSnack("先输入数字")
            return@setOnClickListener
        }
        if (!requireConnected() || playing) return@setOnClickListener
        playing = true
        Thread {
            try {
                chars.forEach { c ->
                    if (!playing || isFinishing || isDestroyed) return@forEach
                    val rows = DigitFont.glyph(c)
                    runOnUiThread {
                        sendLine(Protocol.show(rowsToMatrix(rows)))
                    }
                    Thread.sleep(750)
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                playing = false
                runOnUiThread { sendLine(Protocol.clear()) }
            }
        }.start()
    }
    showFeatureDialog(root, "大数字")
}

// ---------- 6. 亮度自适应 ----------

internal fun MainActivity.openBrightAuto() {
    if (!requireConnected()) return
    val ctx = this
    var auto = true
    var lastLevel = -1

    val root = M3Kit.dialogRoot(ctx)
    val tvBig = bigReadout(ctx, "---")
    val tvLevel = labelText(ctx, "亮度档位：--").apply { gravity = Gravity.CENTER }
    val seek = android.widget.SeekBar(ctx).apply {
        max = 9
        progress = 7
    }
    val btnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    val btnAuto = MaterialButton(ctx).apply { text = "自动：开" }
    val btnApply = MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply { text = "手动应用" }
    btnRow.addView(btnAuto)
    btnRow.addView(
        btnApply,
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = M3Kit.dp(ctx, 10) }
    )
    val tvHint = hintText(ctx, "按环境光自动调节 LED 亮度（光线越暗越暗）；也可以关掉自动，用滑杆手动固定亮度。")
    root.addView(tvBig)
    root.addView(tvLevel)
    root.addView(
        seek,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 14) }
    )
    root.addView(
        btnRow,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 12) }
    )
    root.addView(tvHint)

    fun applyLevel(level: Int) {
        if (level == lastLevel) return
        lastLevel = level
        seek.progress = level
        tvLevel.text = "亮度档位：$level"
        sendLine(Protocol.brightness(level))
    }

    val poller = SensorPoller(900)
    poller.onSnapshot = { s ->
        s.l?.let { l ->
            runOnUiThread {
                tvBig.text = l.toString()
                if (auto) {
                    // 0–255 映射到 1–9（0 档全灭，自适应模式不用）
                    applyLevel((1 + l / 255f * 8).toInt().coerceIn(1, 9))
                }
            }
        }
    }
    btnAuto.setOnClickListener {
        auto = !auto
        btnAuto.text = if (auto) "自动：开" else "自动：关"
        if (!auto) applyLevel(seek.progress)
    }
    btnApply.setOnClickListener { applyLevel(seek.progress) }
    seek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(v: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
            if (fromUser) {
                auto = false
                btnAuto.text = "自动：关"
                applyLevel(p)
            }
        }
        override fun onStartTrackingTouch(v: android.widget.SeekBar?) {}
        override fun onStopTrackingTouch(v: android.widget.SeekBar?) {}
    })
    showFeatureDialog(root, "亮度自适应", onShow = { poller.start() }, onDismiss = { poller.stop() })
}

// ---------- 7. 小夜灯 ----------

private val NIGHT_MOON = listOf("01000", "00110", "00001", "00110", "01000")

internal fun MainActivity.openNightLight() {
    val ctx = this
    var running = false
    var breathing = false
    var pattern: List<String> = NIGHT_MOON

    val root = M3Kit.dialogRoot(ctx)
    val patternGroup = ChipGroup(ctx).apply { isSingleSelection = true }
    listOf("月亮" to NIGHT_MOON, "星星" to Patterns.STAR, "爱心" to Patterns.HEART).forEach { (label, rows) ->
        patternGroup.addView(
            Chip(ctx).apply {
                text = label
                isCheckable = true
                isChecked = rows === NIGHT_MOON
                setOnClickListener { pattern = rows }
            }
        )
    }
    val btnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    val btnStart = MaterialButton(ctx).apply { text = "点亮" }
    val btnBreath = MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply { text = "呼吸：关" }
    btnRow.addView(btnStart)
    btnRow.addView(
        btnBreath,
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = M3Kit.dp(ctx, 10) }
    )
    val tvHint = hintText(ctx, "低亮度长亮柔和图案当小夜灯；开「呼吸」后亮度缓慢起伏，陪睡不刺眼。")
    root.addView(patternGroup)
    root.addView(
        btnRow,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 12) }
    )
    root.addView(tvHint)

    btnStart.setOnClickListener {
        if (!running) {
            if (!requireConnected()) return@setOnClickListener
            running = true
            btnStart.text = "熄灭"
            sendLine(Protocol.brightness(2))
            sendLine(Protocol.show(rowsToMatrix(pattern)))
            if (breathing) {
                Thread {
                    try {
                        var up = true
                        var level = 2
                        while (running && !isFinishing && !isDestroyed) {
                            runOnUiThread { sendLine(Protocol.brightness(level)) }
                            Thread.sleep(400)
                            if (up) {
                                level++
                                if (level >= 6) up = false
                            } else {
                                level--
                                if (level <= 1) up = true
                            }
                        }
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }.start()
            }
        } else {
            running = false
            btnStart.text = "点亮"
            sendLine(Protocol.clear())
        }
    }
    btnBreath.setOnClickListener {
        breathing = !breathing
        btnBreath.text = if (breathing) "呼吸：开" else "呼吸：关"
        if (running && breathing) {
            // 运行中开启呼吸：重启一次以热切换效果（先灭再点）
            running = false
            sendLine(Protocol.clear())
            mainHandler.postDelayed({ btnStart.performClick() }, 60)
        }
    }
    showFeatureDialog(root, "小夜灯", onDismiss = { running = false })
}

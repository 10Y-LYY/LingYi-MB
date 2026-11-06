/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
 * 控制台新功能 · 传感器与数据（预览版 4.0.0-preview 新增 7 项）：
 * 指北玫瑰 / 摇动计数 / 手势记录 / 跌落测试 / 温度曲线 / 动静监测 / 传感器体检。
 * 全部基于 READ 轮询，老固件免刷写。
 */

// ---------- 1. 指北玫瑰 ----------

internal fun MainActivity.openCompassRose() {
    if (!requireConnected()) return
    val ctx = this

    val root = M3Kit.dialogRoot(ctx)
    val box = M3Kit.dp(ctx, 220)
    val stage = android.widget.FrameLayout(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(box, box).apply { gravity = Gravity.CENTER_HORIZONTAL }
    }
    // 刻度盘
    val dial = View(ctx).apply {
        background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setStroke(M3Kit.dp(ctx, 2), ctx.themeColor(MR.attr.colorOutline))
            setColor(ctx.themeColor(MR.attr.colorSurfaceVariant))
        }
    }
    stage.addView(dial, android.widget.FrameLayout.LayoutParams(box, box, Gravity.CENTER))
    // 四方位字母
    val marks = listOf("北" to Gravity.TOP, "东" to Gravity.END, "南" to Gravity.BOTTOM, "西" to Gravity.START)
    marks.forEach { (t, g) ->
        stage.addView(
            TextView(ctx).apply {
                text = t
                textSize = 13f
                setTextColor(ctx.themeColor(MR.attr.colorOnSurfaceVariant))
            },
            android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, g
            ).apply { setMargins(M3Kit.dp(ctx, 8), M3Kit.dp(ctx, 8), M3Kit.dp(ctx, 8), M3Kit.dp(ctx, 8)) }
        )
    }
    // 指针（整体旋转的容器：杆 + 头部圆点，子视图随容器一起转）
    val pointer = android.widget.FrameLayout(ctx).apply {
        layoutParams = android.widget.FrameLayout.LayoutParams(box, box, Gravity.CENTER)
    }
    val stem = View(ctx).apply { setBackgroundColor(ctx.themeColor(MR.attr.colorError)) }
    pointer.addView(
        stem,
        android.widget.FrameLayout.LayoutParams(M3Kit.dp(ctx, 4), M3Kit.dp(ctx, 80), Gravity.CENTER)
            .apply { topMargin = -M3Kit.dp(ctx, 25) }
    )
    val needle = View(ctx).apply {
        background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(ctx.themeColor(MR.attr.colorError))
        }
    }
    pointer.addView(
        needle,
        android.widget.FrameLayout.LayoutParams(M3Kit.dp(ctx, 26), M3Kit.dp(ctx, 26), Gravity.CENTER)
            .apply { topMargin = -M3Kit.dp(ctx, 78) }
    )
    stage.addView(pointer)

    val tvAngle = bigReadout(ctx, "---°")
    val tvDir = labelText(ctx, "等待读数…", bold = true).apply { gravity = Gravity.CENTER }
    val tvHint = hintText(
        ctx,
        "指针指向开发板正面朝向；首次使用若读数乱跳，按固件提示空中画 8 字校准。" +
            "靠近磁铁、音箱、钢筋会明显干扰。"
    )
    root.addView(stage)
    root.addView(tvAngle)
    root.addView(tvDir)
    root.addView(tvHint)

    fun dirName(c: Int): String = when (c) {
        in 338..360, in 0..22 -> "北"
        in 23..67 -> "东北"
        in 68..112 -> "东"
        in 113..157 -> "东南"
        in 158..202 -> "南"
        in 203..247 -> "西南"
        in 248..292 -> "西"
        in 293..337 -> "西北"
        else -> "--"
    }

    val poller = SensorPoller(300)
    poller.onSnapshot = { s ->
        s.c?.let { c ->
            runOnUiThread {
                tvAngle.text = "$c°"
                tvDir.text = dirName(c)
                // 指针整体绕圆心旋转 c 度（0° 指北，顺时针）
                pointer.rotation = c.toFloat()
            }
        }
    }
    showFeatureDialog(root, "指北玫瑰", onShow = { poller.start() }, onDismiss = { poller.stop() })
}

// ---------- 2. 摇动计数 ----------

internal fun MainActivity.openShakeCount() {
    if (!requireConnected()) return
    val ctx = this
    var count = 0
    var target = 20
    var reached = false
    val shakeGate = M3Kit.ShakeGate()
    val startAt = System.currentTimeMillis()

    val root = M3Kit.dialogRoot(ctx)
    val tvBig = bigReadout(ctx, "0")
    val tvRate = labelText(ctx, "频率 -- 次/分").apply { gravity = Gravity.CENTER }
    val targetGroup = ChipGroup(ctx).apply { isSingleSelection = true }
    listOf(10, 20, 50).forEach { n ->
        targetGroup.addView(
            Chip(ctx).apply {
                text = "目标 $n"
                isCheckable = true
                isChecked = n == target
                setOnClickListener {
                    target = n
                    reached = false
                }
            }
        )
    }
    val btnReset = MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply { text = "重新计数" }
    val tvHint = hintText(ctx, "摇一摇开发板计数，实时算频率；达标提示音。适合计数游戏、活力排行、课堂抢答热身。")
    root.addView(tvBig)
    root.addView(tvRate)
    root.addView(
        targetGroup,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 8) }
    )
    root.addView(
        btnReset,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 10) }
    )
    root.addView(tvHint)

    val poller = SensorPoller(120)
    poller.onSnapshot = { s ->
        if (s.g == "shake") {
            val now = System.currentTimeMillis()
            if (shakeGate.allow(700, now)) {
                count++
                val minutes = (now - startAt) / 60000.0
                runOnUiThread {
                    tvBig.text = count.toString()
                    tvRate.text = String.format(
                        Locale.US, "频率 %.1f 次/分", if (minutes < 0.05) 0.0 else count / minutes
                    )
                    if (!reached && count >= target) {
                        reached = true
                        sendLine(Protocol.tone(1568, 400))
                        sendLine(Protocol.show(rowsToMatrix(listOf("00000", "01010", "00000", "00100", "01010"))))
                        featureSnack("达成目标 $target 次！")
                    }
                }
            }
        }
    }
    btnReset.setOnClickListener {
        count = 0
        reached = false
        tvBig.text = "0"
        tvRate.text = "频率 -- 次/分"
    }
    showFeatureDialog(root, "摇动计数", onShow = { poller.start() }, onDismiss = { poller.stop() })
}

// ---------- 3. 手势记录 ----------

internal fun MainActivity.openGestureLog() {
    if (!requireConnected()) return
    val ctx = this
    val t0 = System.currentTimeMillis()
    val counts = HashMap<String, Int>()
    val logLines = ArrayDeque<String>(20)

    val root = M3Kit.dialogRoot(ctx)
    val tvBig = bigReadout(ctx, "无")
    val tvCounts = labelText(ctx, "各手势次数：--").apply { gravity = Gravity.CENTER }
    val tvLog = TextView(ctx).apply {
        textSize = 12f
        typeface = android.graphics.Typeface.MONOSPACE
        setTextIsSelectable(true)
        setPadding(0, M3Kit.dp(ctx, 10), 0, 0)
        text = "（在开发板上做手势，这里实时记录）"
    }
    val tvHint = hintText(ctx, "识别 8 种手势：摇一摇 / 点击 / Logo 朝上 / 正面朝下 / 自由落体 / 3g / 6g / 8g；日志保留最近 20 条。")
    root.addView(tvBig)
    root.addView(tvCounts)
    root.addView(tvLog)
    root.addView(tvHint)

    var prev: String? = null
    fun stamp(): String {
        val ms = System.currentTimeMillis() - t0
        return String.format(Locale.US, "%02d:%02d", ms / 60000, ms / 1000 % 60)
    }

    val poller = SensorPoller(200)
    poller.onSnapshot = { s ->
        val g = s.g
        runOnUiThread {
            tvBig.text = Gestures.label(g)
            if (g != null && g != prev) {
                counts[g] = (counts[g] ?: 0) + 1
                logLines.addLast("${stamp()}  ${Gestures.label(g)}")
                while (logLines.size > 20) logLines.removeFirst()
                tvLog.text = logLines.joinToString("\n")
                tvCounts.text = "各手势次数：" + counts.entries.joinToString("  ") { "${Gestures.label(it.key)} ${it.value}" }
            }
            prev = g
        }
    }
    showFeatureDialog(root, "手势记录", onShow = { poller.start() }, onDismiss = { poller.stop() })
}

// ---------- 4. 跌落测试 ----------

internal fun MainActivity.openDropTest() {
    if (!requireConnected()) return
    val ctx = this
    var falling = false
    var fallStart = 0L
    var maxG = 0.0
    val records = ArrayList<String>()

    val root = M3Kit.dialogRoot(ctx)
    val tvBig = bigReadout(ctx, "待机")
    val tvSub = labelText(ctx, "把开发板抛起再接住，记录下落时长与冲击加速度").apply { gravity = Gravity.CENTER }
    val tvLog = TextView(ctx).apply {
        textSize = 13f
        typeface = android.graphics.Typeface.MONOSPACE
        setPadding(0, M3Kit.dp(ctx, 12), 0, 0)
        text = "（记录出现在这里，建议垫软面）"
    }
    val tvHint = hintText(ctx, "freefall 手势标识自由落体；冲击用三轴合成幅值近似。玩法：比谁接得又轻又稳，别真摔。")
    root.addView(tvBig)
    root.addView(tvSub)
    root.addView(tvLog)
    root.addView(tvHint)

    val poller = SensorPoller(100)
    poller.onSnapshot = { s ->
        val g = s.g
        val mag = (s.x ?: 0).toDouble() * (s.x ?: 0) + (s.y ?: 0).toDouble() * (s.y ?: 0) + (s.z ?: 0).toDouble() * (s.z ?: 0)
        val magD = Math.sqrt(mag)
        runOnUiThread {
            if (g == "freefall" && !falling) {
                falling = true
                fallStart = System.currentTimeMillis()
                maxG = 0.0
                tvBig.text = "下落中…"
                sendLine(Protocol.tone(300, 80))
            } else if (g != "freefall" && falling) {
                falling = false
                val dur = System.currentTimeMillis() - fallStart
                val rec = String.format(
                    Locale.US, "下落 %d ms · 冲击 %.0f mg", dur, maxG
                )
                records.add(0, rec)
                tvBig.text = rec
                tvLog.text = records.take(8).joinToString("\n")
                sendLine(Protocol.tone(if (maxG > 2500) 220 else 880, 250))
            }
            if (falling && magD > maxG) maxG = magD
        }
    }
    showFeatureDialog(root, "跌落测试", onShow = { poller.start() }, onDismiss = { poller.stop() })
}

// ---------- 5. 温度曲线 ----------

/** 迷你折线图（温度曲线用）：自动纵轴量程，主线 + 半透明填充。 */
private class SparklineView(context: Context) : View(context) {
    private val points = ArrayDeque<Float>()
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    var minV = 20f
    var maxV = 30f
    var lineColor = Color.parseColor("#0E7C86")
    var gridColor = Color.parseColor("#E4E7EC")

    fun push(v: Float) {
        points.addLast(v)
        while (points.size > 120) points.removeFirst()
        invalidate()
    }

    fun clear() {
        points.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        // 中线参考
        gridPaint.color = gridColor
        canvas.drawLine(0f, h / 2, w, h / 2, gridPaint)
        if (points.size < 2) return
        val lo = minOf(minV, points.minOrNull() ?: minV)
        val hi = maxOf(maxV, points.maxOrNull() ?: maxV)
        val span = (hi - lo).coerceAtLeast(1f)
        val stepX = w / 119f
        fun yOf(v: Float) = h - ((v - lo) / span * (h * 0.9f) + h * 0.05f)
        val path = android.graphics.Path()
        val fillPath = android.graphics.Path()
        points.forEachIndexed { i, v ->
            val x = i * stepX
            val y = yOf(v)
            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        linePaint.color = lineColor
        canvas.drawPath(path, linePaint)
        fillPath.lineTo((points.size - 1) * stepX, h)
        fillPath.lineTo(0f, h)
        fillPath.close()
        fillPaint.color = (lineColor and 0x00FFFFFF) or 0x33000000
        canvas.drawPath(fillPath, fillPaint)
    }
}

internal fun MainActivity.openTempChart() {
    if (!requireConnected()) return
    val ctx = this

    val root = M3Kit.dialogRoot(ctx)
    val tvBig = bigReadout(ctx, "--.- °C")
    val tvStats = labelText(ctx, "窗口 最高 --  最低 --  均值 --").apply { gravity = Gravity.CENTER }
    val chart = SparklineView(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, M3Kit.dp(ctx, 150)
        ).apply { topMargin = M3Kit.dp(ctx, 12) }
        lineColor = ctx.themeColor(MR.attr.colorPrimary)
        gridColor = ctx.themeColor(MR.attr.colorOutlineVariant)
    }
    val tvHint = hintText(ctx, "最近 2 分钟温度曲线（每 1 秒一点）；对着开发板哈气会看到尖峰，适合观察升温/降温过程。")
    root.addView(tvBig)
    root.addView(tvStats)
    root.addView(chart)
    root.addView(tvHint)

    val window = ArrayList<Double>(130)
    val poller = SensorPoller(1000)
    poller.onSnapshot = { s ->
        s.t?.let { t ->
            window.add(t)
            while (window.size > 120) window.removeAt(0)
            runOnUiThread {
                tvBig.text = String.format(Locale.US, "%.1f °C", t)
                tvStats.text = String.format(
                    Locale.US, "窗口 最高 %.1f  最低 %.1f  均值 %.1f",
                    window.maxOrNull() ?: 0.0, window.minOrNull() ?: 0.0, window.average()
                )
                chart.push(t.toFloat())
            }
        }
    }
    showFeatureDialog(root, "温度曲线", onShow = { poller.start() }, onDismiss = { poller.stop() })
}

// ---------- 6. 动静监测 ----------

internal fun MainActivity.openMotionDetector() {
    if (!requireConnected()) return
    val ctx = this
    var threshold = 400
    var triggers = 0
    var lastTriggerAt = 0L
    var prev: Triple<Int, Int, Int>? = null

    val root = M3Kit.dialogRoot(ctx)
    val tvBig = bigReadout(ctx, "0")
    val tvSub = labelText(ctx, "触发次数 · 防误触间隔 1.5 秒").apply { gravity = Gravity.CENTER }
    val track = ProgressTrack(ctx).apply { setFillColorAttr(MR.attr.colorError) }
    val sensGroup = ChipGroup(ctx).apply { isSingleSelection = true }
    listOf("低灵敏" to 900, "中灵敏" to 400, "高灵敏" to 150).forEach { (label, v) ->
        sensGroup.addView(
            Chip(ctx).apply {
                text = label
                isCheckable = true
                isChecked = v == threshold
                setOnClickListener { threshold = v }
            }
        )
    }
    val tvHint = hintText(ctx, "加速度变化超过阈值即报警（响一声 + 计数）。贴在抽屉、门后、设备箱上，有人动就提醒。")
    root.addView(tvBig)
    root.addView(tvSub)
    root.addView(
        track,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, M3Kit.dp(ctx, 10)
        ).apply { topMargin = M3Kit.dp(ctx, 12) }
    )
    root.addView(
        sensGroup,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 8) }
    )
    root.addView(tvHint)

    fun bar(mag: Int) {
        track.setFraction(mag / 3000f)
    }

    val poller = SensorPoller(150)
    poller.onSnapshot = { s ->
        val cur = Triple(s.x ?: 0, s.y ?: 0, s.z ?: 0)
        val p = prev
        prev = cur
        if (p != null) {
            val delta = Math.abs(cur.first - p.first) + Math.abs(cur.second - p.second) + Math.abs(cur.third - p.third)
            runOnUiThread {
                bar(delta)
                if (delta > threshold) {
                    val now = System.currentTimeMillis()
                    if (now - lastTriggerAt > 1500) {
                        lastTriggerAt = now
                        triggers++
                        tvBig.text = triggers.toString()
                        tvBig.setTextColor(ctx.themeColor(MR.attr.colorError))
                        sendLine(Protocol.tone(1600, 120))
                    }
                } else {
                    tvBig.setTextColor(ctx.themeColor(MR.attr.colorPrimary))
                }
            }
        }
    }
    showFeatureDialog(root, "动静监测", onShow = { poller.start() }, onDismiss = { poller.stop() })
}

// ---------- 7. 传感器体检 ----------

internal fun MainActivity.openSensorTest() {
    if (!requireConnected()) return
    val ctx = this
    val steps = listOf("按下按钮 A 或 B", "用手遮住 LED 矩阵", "倾斜开发板（任一轴）", "摇一摇开发板", "板子正面朝下再翻回")
    val done = BooleanArray(5)
    var current = 0
    var sawFaceDown = false

    val root = M3Kit.dialogRoot(ctx)
    val tvBig = bigReadout(ctx, "1/5")
    val rows = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    val statusTexts = ArrayList<TextView>(5)
    steps.forEachIndexed { i, s ->
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val mark = TextView(ctx).apply {
            text = if (i == 0) "▶" else "○"
            textSize = 15f
            setTextColor(ctx.themeColor(if (i == 0) MR.attr.colorPrimary else MR.attr.colorOnSurfaceVariant))
        }
        val label = labelText(ctx, s).apply { textSize = 13f }
        statusTexts.add(mark)
        row.addView(mark)
        row.addView(
            label,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = M3Kit.dp(ctx, 10) }
        )
        rows.addView(
            row,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = M3Kit.dp(ctx, 8) }
        )
    }
    val btnRestart = MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply { text = "重新体检" }
    val tvHint = hintText(ctx, "逐步引导检查按键 / 光线 / 加速度 / 手势是否正常，新板到货、二手验机都能用。")
    root.addView(tvBig)
    root.addView(rows)
    root.addView(
        btnRestart,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 14) }
    )
    root.addView(tvHint)

    fun refresh() {
        tvBig.text = "${done.count { it }}/5"
        statusTexts.forEachIndexed { i, tv ->
            tv.text = when {
                done[i] -> "✔"
                i == current -> "▶"
                else -> "○"
            }
            tv.setTextColor(
                ctx.themeColor(
                    when {
                        done[i] -> MR.attr.colorPrimary
                        i == current -> MR.attr.colorPrimary
                        else -> MR.attr.colorOnSurfaceVariant
                    }
                )
            )
        }
    }

    fun pass(i: Int) {
        if (done[i]) return
        done[i] = true
        sendLine(Protocol.tone(1046, 120))
        if (done.all { it }) {
            sendLine(Protocol.show(rowsToMatrix(listOf("00000", "01010", "00000", "00100", "01110"))))
            sendLine(Protocol.melody("ODE"))
            featureSnack("体检全部通过，板子状态良好")
        }
        refresh()
    }

    val poller = SensorPoller(150)
    poller.onSnapshot = { s ->
        runOnUiThread {
            when (current) {
                0 -> if (s.a || s.b) pass(0)
                1 -> if ((s.l ?: 255) < 30) pass(1)
                2 -> if (Math.abs(s.x ?: 0) > 400 || Math.abs(s.y ?: 0) > 400) pass(2)
                3 -> if (s.g == "shake") pass(3)
                4 -> {
                    // 先正面朝下，再翻回（手势消失）才算完成
                    if (s.g == "facedown") sawFaceDown = true
                    if (sawFaceDown && s.g != "facedown") pass(4)
                }
            }
            if (current < 4 && done[current]) {
                current++
                sawFaceDown = false
                refresh()
            }
        }
    }
    btnRestart.setOnClickListener {
        done.fill(false)
        current = 0
        sawFaceDown = false
        refresh()
    }
    refresh()
    showFeatureDialog(root, "传感器体检", onShow = { poller.start() }, onDismiss = { poller.stop() })
}

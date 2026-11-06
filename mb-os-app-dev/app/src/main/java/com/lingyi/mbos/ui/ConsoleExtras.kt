/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.google.android.material.R as MR
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lingyi.mbos.MainActivity
import com.lingyi.mbos.R
import com.lingyi.mbos.util.themeColor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * 控制台扩展功能（v3.3.0-preview）：全部复用既有串口协议（READ / SHOW / TONE / SCROLL），
 * 老固件（MB-OS v3.0.0）免刷写即可使用；未连接时给出提示，不崩溃。
 *
 * 本文件：传感器与数据类（温度计 / 光线计 / 水平仪 / 按钮监视器 / 传感器记录仪）。
 * 游戏互动见 ConsoleGames.kt，创意与工具见 ConsoleTools.kt。
 */

// ---------- 共用小工具 ----------

internal fun MainActivity.featureSnack(msg: String, long: Boolean = false) {
    com.google.android.material.snackbar.Snackbar.make(
        consoleRoot, msg,
        if (long) com.google.android.material.snackbar.Snackbar.LENGTH_LONG
        else com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
    ).show()
}

/** 带 show / dismiss 生命周期的功能对话框：轮询器等资源在此启停。 */
internal fun MainActivity.showFeatureDialog(
    view: View,
    title: String? = null,
    onShow: () -> Unit = {},
    onDismiss: () -> Unit = {}
): AlertDialog {
    val builder = MaterialAlertDialogBuilder(this).setView(view)
    if (title != null) builder.setTitle(title)
    val dialog = builder.create()
    dialog.setOnShowListener { onShow() }
    dialog.setOnDismissListener { onDismiss() }
    M3Kit.styleButtons(view) // 全树按钮统一 M3 Expressive 规格（v4.2）
    dialog.show()
    return dialog
}

internal fun MainActivity.requireConnected(): Boolean {
    if (com.lingyi.mbos.ble.ConnectionHub.isConnected()) return true
    featureSnack("未连接开发板，请先连接")
    return false
}

internal fun bigReadout(context: Context, text: String = "--"): TextView =
    TextView(context).apply {
        this.text = text
        textSize = 40f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(context.themeColor(MR.attr.colorPrimary))
        gravity = Gravity.CENTER
    }

internal fun hintText(context: Context, text: String): TextView =
    TextView(context).apply {
        this.text = text
        textSize = 12f
        setTextColor(context.themeColor(MR.attr.colorOnSurfaceVariant))
        setPadding(0, M3Kit.dp(context, 10), 0, 0)
    }

internal fun labelText(context: Context, text: String, bold: Boolean = false): TextView =
    TextView(context).apply {
        this.text = text
        textSize = 14f
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(context.themeColor(MR.attr.colorOnSurface))
    }

/** 给程序化创建的视图补 LinearLayout 上间距（未挂载时也能安全调用，返回原类型）。 */
internal fun <T : View> T.topMargin(context: Context, v: Int): T {
    val lp = layoutParams ?: LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )
    (lp as? LinearLayout.LayoutParams)?.topMargin = M3Kit.dp(context, v)
    layoutParams = lp
    return this
}

/** 竖排卡片容器（M3 表面卡）。 */
internal fun featureCard(context: Context): MaterialCardView =
    MaterialCardView(context).apply {
        radius = M3Kit.dp(context, 16).toFloat()
        cardElevation = 0f
        setCardBackgroundColor(context.themeColor(MR.attr.colorSurface))
        strokeColor = context.themeColor(MR.attr.colorOutlineVariant)
        strokeWidth = M3Kit.dp(context, 1)
    }

// ---------- 1. 温度计 ----------

internal fun MainActivity.openThermometer() {
    if (!requireConnected()) return
    val ctx = this
    var fahrenheit = false
    var alarmed = false
    var minT: Double? = null
    var maxT: Double? = null
    var sumT = 0.0
    var count = 0

    val root = M3Kit.dialogRoot(ctx)
    val tvBig = bigReadout(ctx, "--.- °C")
    val tvStats = labelText(ctx, "最低 --  最高 --  均值 --").apply { gravity = Gravity.CENTER }
    val btnUnit = MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply {
        text = "切换为 °F"
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = M3Kit.dp(ctx, 12)
        }
    }
    val tvHint = hintText(
        ctx,
        "micro:bit 温度传感器贴近主板，读数通常比环境温度高 3–6°C，属正常现象；" +
            "连续监测同一姿态下的变化更有意义。超过 45°C 会报警并蜂鸣。"
    )
    root.addView(tvBig)
    root.addView(tvStats)
    root.addView(btnUnit)
    root.addView(tvHint)

    fun render(t: Double) {
        minT = minT?.coerceAtMost(t) ?: t
        maxT = maxT?.coerceAtLeast(t) ?: t
        sumT += t
        count++
        val avg = sumT / count
        tvBig.text = if (fahrenheit) {
            String.format(Locale.US, "%.1f °F", t * 9 / 5 + 32)
        } else {
            String.format(Locale.US, "%.1f °C", t)
        }
        fun fmt(v: Double) = if (fahrenheit) String.format(
            Locale.US, "%.1f°F", v * 9 / 5 + 32
        ) else String.format(Locale.US, "%.1f°C", v)
        tvStats.text = "最低 ${fmt(minT!!)}  最高 ${fmt(maxT!!)}  均值 ${fmt(avg)}"
        val hot = t >= 45.0
        tvBig.setTextColor(
            ctx.themeColor(if (hot) MR.attr.colorError else MR.attr.colorPrimary)
        )
        if (hot && !alarmed) {
            alarmed = true
            sendLine(Protocol.tone(1600, 400))
        }
        if (!hot) alarmed = false
    }

    btnUnit.setOnClickListener {
        fahrenheit = !fahrenheit
        btnUnit.text = if (fahrenheit) "切换为 °C" else "切换为 °F"
    }

    val poller = SensorPoller(800)
    poller.onSnapshot = { s -> s.t?.let { runOnUiThread { render(it) } } }
    showFeatureDialog(root, "温度计", onShow = { poller.start() }, onDismiss = { poller.stop() })
}

// ---------- 2. 光线计 ----------

internal fun MainActivity.openLightMeter() {
    if (!requireConnected()) return
    val ctx = this
    var minL: Int? = null
    var maxL: Int? = null

    val root = M3Kit.dialogRoot(ctx)
    val tvBig = bigReadout(ctx, "---")
    val tvLabel = labelText(ctx, "等待读数…", bold = true).apply { gravity = Gravity.CENTER }
    val tvStats = labelText(ctx, "最低 ---  最高 ---").apply { gravity = Gravity.CENTER }

    // 光强条：M3 进度轨（避免进度条动画在快速轮询下的抖动）
    val track = ProgressTrack(ctx)
    val barLp = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, M3Kit.dp(ctx, 14)
    ).apply { topMargin = M3Kit.dp(ctx, 16) }
    track.layoutParams = barLp

    val tvHint = hintText(
        ctx,
        "读数范围 0–255（micro:bit 用 LED 矩阵测环境光）。用手遮住矩阵可以看到读数骤降，" +
            "移开手电筒会飙升。适合做遮光 / 光照对比小实验。"
    )
    root.addView(tvBig)
    root.addView(tvLabel)
    root.addView(track)
    root.addView(tvStats)
    root.addView(tvHint)

    fun describe(l: Int): String = when {
        l < 20 -> "很暗（接近夜间）"
        l < 80 -> "偏暗"
        l < 160 -> "适中"
        l < 230 -> "明亮"
        else -> "强光 / 直射"
    }

    fun render(l: Int) {
        minL = minL?.coerceAtMost(l) ?: l
        maxL = maxL?.coerceAtLeast(l) ?: l
        tvBig.text = l.toString()
        tvLabel.text = describe(l)
        tvStats.text = "最低 $minL  最高 $maxL"
        track.setFraction(l / 255f)
    }

    val poller = SensorPoller(800)
    poller.onSnapshot = { s ->
        s.l?.let { runOnUiThread { render(it) } }
    }
    showFeatureDialog(root, "光线计", onShow = { poller.start() }, onDismiss = { poller.stop() })
}

// ---------- 3. 水平仪 ----------

internal fun MainActivity.openLevel() {
    if (!requireConnected()) return
    val ctx = this
    var leveled = false

    val box = M3Kit.dp(ctx, 220)
    val bubbleSize = M3Kit.dp(ctx, 30)
    val root = M3Kit.dialogRoot(ctx)
    val stage = FrameLayout(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(box, box).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
    }
    // 外圈
    val ring = View(ctx).apply {
        background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setStroke(M3Kit.dp(ctx, 2), ctx.themeColor(MR.attr.colorOutline))
            setColor(ctx.themeColor(MR.attr.colorSurfaceVariant))
        }
    }
    stage.addView(ring, FrameLayout.LayoutParams(box, box, Gravity.CENTER))
    // 中心十字
    val crossH = View(ctx).apply {
        setBackgroundColor(ctx.themeColor(MR.attr.colorOutline))
    }
    stage.addView(
        crossH,
        FrameLayout.LayoutParams(M3Kit.dp(ctx, 60), 1, Gravity.CENTER)
    )
    val crossV = View(ctx).apply {
        setBackgroundColor(ctx.themeColor(MR.attr.colorOutline))
    }
    stage.addView(
        crossV,
        FrameLayout.LayoutParams(1, M3Kit.dp(ctx, 60), Gravity.CENTER)
    )
    // 目标圈（中心 ±12dp 视为已调平）
    val target = View(ctx).apply {
        background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setStroke(M3Kit.dp(ctx, 1), ctx.themeColor(MR.attr.colorPrimary))
        }
    }
    stage.addView(target, FrameLayout.LayoutParams(M3Kit.dp(ctx, 28), M3Kit.dp(ctx, 28), Gravity.CENTER))
    // 气泡
    val bubble = View(ctx).apply {
        background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(ctx.themeColor(MR.attr.colorPrimary))
        }
    }
    stage.addView(bubble, FrameLayout.LayoutParams(bubbleSize, bubbleSize, Gravity.CENTER))

    val tvAngle = labelText(ctx, "X 轴 0.0°   Y 轴 0.0°", bold = true).apply {
        gravity = Gravity.CENTER
    }.topMargin(ctx, 14)
    val tvState = labelText(ctx, "倾斜中…").apply {
        gravity = Gravity.CENTER
    }.topMargin(ctx, 6)
    val tvHint = hintText(
        ctx,
        "把开发板平放，气泡会随倾角滑动；气泡进入中心小圈即「已调平」。" +
            "桌面调平、支架找平、简单倾角测量都能用。"
    )
    root.addView(stage)
    root.addView(tvAngle)
    root.addView(tvState)
    root.addView(tvHint)

    val maxOffset = (box - bubbleSize) / 2f

    fun render(s: SensorSnapshot) {
        val x = (s.x ?: 0).toDouble()
        val y = (s.y ?: 0).toDouble()
        val z = (s.z ?: 1024).toDouble().coerceAtLeast(1.0)
        val nx = (x / 1200.0).coerceIn(-1.0, 1.0)
        val ny = (y / 1200.0).coerceIn(-1.0, 1.0)
        bubble.translationX = (nx * maxOffset).toFloat()
        bubble.translationY = (ny * maxOffset).toFloat()
        val ax = Math.toDegrees(kotlin.math.atan2(x, z))
        val ay = Math.toDegrees(kotlin.math.atan2(y, z))
        tvAngle.text = String.format(Locale.US, "X 轴 %+.1f°   Y 轴 %+.1f°", ax, ay)
        val flat = kotlin.math.abs(x) < 120 && kotlin.math.abs(y) < 120
        tvState.text = if (flat) "已调平" else "倾斜中…"
        tvState.setTextColor(
            ctx.themeColor(if (flat) MR.attr.colorPrimary else MR.attr.colorOnSurfaceVariant)
        )
        if (flat && !leveled) {
            leveled = true
            sendLine(Protocol.tone(1046, 120))
        }
        if (!flat) leveled = false
    }

    val poller = SensorPoller(250)
    poller.onSnapshot = { s -> runOnUiThread { render(s) } }
    showFeatureDialog(root, "水平仪", onShow = { poller.start() }, onDismiss = { poller.stop() })
}

// ---------- 4. 按钮监视器 ----------

internal fun MainActivity.openButtonMonitor() {
    if (!requireConnected()) return
    val ctx = this
    val t0 = System.currentTimeMillis()
    var prevA: Boolean? = null
    var prevB: Boolean? = null
    var countA = 0
    var countB = 0
    val logLines = ArrayDeque<String>(24)

    val root = M3Kit.dialogRoot(ctx)
    val cards = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }

    /** 一张按键卡：返回「状态文本 + 计数文本 + 卡片」三元组的刷新闭包。 */
    fun keyCard(title: String): (Boolean, Int) -> Unit {
        val card = featureCard(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        val tvState = labelText(ctx, "松开").topMargin(ctx, 6)
        val tvCount = labelText(ctx, "0 次").apply {
            textSize = 12f
        }.topMargin(ctx, 2)
        card.addView(
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(M3Kit.dp(ctx, 16), M3Kit.dp(ctx, 12), M3Kit.dp(ctx, 16), M3Kit.dp(ctx, 12))
                addView(labelText(ctx, title, bold = true))
                addView(tvState)
                addView(tvCount)
            }
        )
        cards.addView(card)
        return { pressed, count ->
            tvState.text = if (pressed) "按下" else "松开"
            tvState.setTextColor(
                ctx.themeColor(
                    if (pressed) MR.attr.colorPrimary else MR.attr.colorOnSurfaceVariant
                )
            )
            tvCount.text = "$count 次"
        }
    }

    val refreshA = keyCard("按钮 A")
    val refreshB = keyCard("按钮 B")
    // 安全取子节点：索引变动时降级为不加间距，而不是 ClassCastException/NPE（v4.2）
    (cards.getChildAt(1)?.layoutParams as? LinearLayout.LayoutParams)?.marginStart = M3Kit.dp(ctx, 12)

    val tvLog = TextView(ctx).apply {
        textSize = 13f
        typeface = android.graphics.Typeface.MONOSPACE
        setTextIsSelectable(true)
        setPadding(0, M3Kit.dp(ctx, 12), 0, 0)
        text = "（在开发板上按 A / B，这里实时显示）"
    }
    val tvHint = hintText(ctx, "按下计数从打开本页起算；日志保留最近 24 条。")

    root.addView(cards)
    root.addView(tvLog)
    root.addView(tvHint)

    fun stamp(): String {
        val ms = System.currentTimeMillis() - t0
        return String.format(Locale.US, "%02d:%02d.%02d", ms / 60000, ms / 1000 % 60, ms / 10 % 100)
    }

    fun pushLog(line: String) {
        logLines.addLast(line)
        while (logLines.size > 24) logLines.removeFirst()
        tvLog.text = logLines.joinToString("\n")
    }

    fun render(s: SensorSnapshot) {
        if (s.a != prevA) {
            prevA = s.a
            if (s.a) countA++
            pushLog("${stamp()}  A ${if (s.a) "按下" else "松开"}")
        }
        if (s.b != prevB) {
            prevB = s.b
            if (s.b) countB++
            pushLog("${stamp()}  B ${if (s.b) "按下" else "松开"}")
        }
        refreshA(s.a, countA)
        refreshB(s.b, countB)
    }

    val poller = SensorPoller(150)
    poller.onSnapshot = { s -> runOnUiThread { render(s) } }
    showFeatureDialog(root, "按钮监视器", onShow = { poller.start() }, onDismiss = { poller.stop() })
}

// ---------- 5. 传感器记录仪（CSV 导出） ----------

internal fun MainActivity.openSensorLogger() {
    val ctx = this
    val samples = ArrayList<Pair<Long, SensorSnapshot>>()
    var recording = false
    var t0 = 0L
    var lastFile: File? = null

    val root = M3Kit.dialogRoot(ctx)
    val tvValues = TextView(ctx).apply {
        textSize = 13f
        typeface = android.graphics.Typeface.MONOSPACE
        text = "未开始记录"
    }
    val tvInfo = labelText(ctx, "采样 0 条 · 时长 00:00").topMargin(ctx, 8)
    val btnStartStop = MaterialButton(ctx).apply { text = "开始记录" }
    val btnExport = MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply {
        text = "导出 CSV"
        isEnabled = false
    }
    val btnClear = MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply { text = "清空数据" }
    val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    row.addView(btnStartStop)
    row.addView(
        btnExport,
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = M3Kit.dp(ctx, 10)
        }
    )
    row.addView(
        btnClear,
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = M3Kit.dp(ctx, 10)
        }
    )
    val tvHint = hintText(
        ctx,
        "按固定间隔采样 READ 全量字段（按键 / 温度 / 光线 / 加速度 / 朝向 / 手势），" +
            "停止后可导出 CSV，用 Excel / WPS 直接打开画图。适合课堂实验与数据作业。"
    )
    root.addView(tvValues)
    root.addView(tvInfo)
    root.addView(
        row,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 12) }
    )
    root.addView(tvHint)

    fun refreshInfo() {
        val dur = if (recording) System.currentTimeMillis() - t0 else 0L
        tvInfo.text = String.format(
            Locale.US, "采样 %d 条 · 时长 %02d:%02d",
            samples.size, dur / 60000, dur / 1000 % 60
        )
    }

    val poller = SensorPoller(1000)
    poller.onSnapshot = { s ->
        runOnUiThread {
            if (recording) samples.add((System.currentTimeMillis() - t0) to s)
            tvValues.text = String.format(
                Locale.US,
                "A:%d B:%d T:%s L:%s X:%s Y:%s Z:%s C:%s G:%s",
                if (s.a) 1 else 0, if (s.b) 1 else 0,
                s.t ?: "-", s.l ?: "-", s.x ?: "-", s.y ?: "-", s.z ?: "-", s.c ?: "-", s.g ?: "-"
            )
            refreshInfo()
        }
    }

    btnStartStop.setOnClickListener {
        if (recording) {
            recording = false
            btnStartStop.text = "开始记录"
            btnExport.isEnabled = samples.isNotEmpty()
            if (samples.isNotEmpty()) {
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val dir = File(ctx.cacheDir, "packaged").apply { mkdirs() }
                val f = File(dir, CsvLogger.fileName(stamp))
                runCatching { f.writeText(CsvLogger.toCsv(samples)) }
                    .onSuccess { lastFile = f }
                    .onFailure { featureSnack("保存失败：${it.message}") }
            }
        } else {
            if (!requireConnected()) return@setOnClickListener
            samples.clear()
            recording = true
            t0 = System.currentTimeMillis()
            btnStartStop.text = "停止并保存"
            btnExport.isEnabled = false
            featureSnack("开始记录传感器数据")
        }
        refreshInfo()
    }
    btnExport.setOnClickListener {
        val f = lastFile ?: return@setOnClickListener
        val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, f.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { ctx.startActivity(Intent.createChooser(intent, "导出传感器数据")) }
            .onFailure { featureSnack("没有可处理 CSV 的应用") }
    }
    btnClear.setOnClickListener {
        samples.clear()
        recording = false
        btnStartStop.text = "开始记录"
        btnExport.isEnabled = false
        refreshInfo()
        featureSnack("已清空本轮数据")
    }

    showFeatureDialog(
        root, "传感器记录仪",
        onShow = { poller.start() },
        onDismiss = { poller.stop() }
    )
}

// ---------- LED 矩阵编辑器（LED 画板与动画工作室共用） ----------

/** micro:bit LED 点亮色（品牌琥珀），历史上两份编辑器各自硬编码，收敛为此常量。 */
internal const val LED_ON_COLOR = 0xFFF2A900.toInt()

/**
 * 5×5 点阵编辑器（程序化构建，LED 画板与动画工作室同款交互）。
 * 往既有 [grid] 填充（先清空）；每个 dot 带 tag `dot_x_y`，
 * 调用方在 [onToggle] 里配 [setMatrixCell] 刷新背景色。
 */
internal fun buildMatrixEditor(
    grid: GridLayout,
    matrix: Array<BooleanArray>,
    onToggle: (Int, Int) -> Unit
) {
    val context = grid.context
    grid.removeAllViews()
    grid.columnCount = 5
    grid.rowCount = 5
    val cell = context.resources.getDimensionPixelSize(R.dimen.led_cell)
    val margin = context.resources.getDimensionPixelSize(R.dimen.led_cell_margin)
    val offColor = context.themeColor(MR.attr.colorSurfaceVariant)
    val stroke = context.themeColor(MR.attr.colorOutlineVariant)
    for (y in 0..4) for (x in 0..4) {
        val dot = View(context).apply {
            tag = "dot_${x}_$y"
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(if (matrix[y][x]) LED_ON_COLOR else offColor)
                setStroke(M3Kit.dp(context, 1), stroke)
            }
        }
        val frame = FrameLayout(context).apply {
            layoutParams = GridLayout.LayoutParams().apply {
                width = cell
                height = cell
                setMargins(margin, margin, margin, margin)
            }
            addView(dot)
            setOnClickListener { onToggle(x, y) }
        }
        grid.addView(frame)
    }
}

/** 刷新单个格子背景色（LED 画板与动画工作室点击后共用）。 */
internal fun setMatrixCell(grid: GridLayout, x: Int, y: Int, on: Boolean) {
    val dot = grid.findViewWithTag<View>("dot_${x}_$y") ?: return
    (dot.background as? android.graphics.drawable.GradientDrawable)?.setColor(
        if (on) LED_ON_COLOR else grid.context.themeColor(MR.attr.colorSurfaceVariant)
    )
}

/** 让 Handler 在主线程延迟执行（对话框内计时用）。 */
internal fun Handler.postDelay(ms: Long, action: () -> Unit) {
    postDelayed(object : Runnable {
        override fun run() = action()
    }, ms)
}

internal val mainHandler: Handler = Handler(Looper.getMainLooper())

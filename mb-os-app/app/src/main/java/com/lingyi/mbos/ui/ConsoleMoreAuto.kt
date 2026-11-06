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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.R as MR
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.lingyi.mbos.MainActivity
import com.lingyi.mbos.ble.ConnectionHub
import com.lingyi.mbos.ble.ConnState
import com.lingyi.mbos.ble.MicrobitConnection
import com.lingyi.mbos.util.Prefs
import com.lingyi.mbos.util.themeColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * 控制台新功能 · 指针与自动化（预览版 4.0.0-preview 新增 7 项）：
 * 摇晃触发器 / 手势锁 / 连接哨兵 / 指令编排 / 快捷面板 / 实时日志 / 空闲机器人。
 */

// ---------- 1. 摇晃触发器 ----------

internal fun MainActivity.openShakeButton() {
    if (!requireConnected()) return
    val ctx = this
    var action = 0 // 0 发指令 1 播放旋律 2 清屏 3 亮爱心
    var command = "SCROLL:收到"
    var cooldownSec = 2
    var triggers = 0
    val shakeGate = M3Kit.ShakeGate()

    val root = M3Kit.dialogRoot(ctx)
    val actionGroup = ChipGroup(ctx).apply { isSingleSelection = true }
    listOf("发指令", "播放旋律", "清屏", "亮爱心").forEachIndexed { i, label ->
        actionGroup.addView(
            Chip(ctx).apply {
                text = label
                isCheckable = true
                isChecked = i == action
                setOnClickListener { action = i }
            }
        )
    }
    val et = EditText(ctx).apply {
        hint = "指令内容，如 SCROLL:收到"
        inputType = android.text.InputType.TYPE_CLASS_TEXT
        setText(command)
        textSize = 14f
    }
    val coolGroup = ChipGroup(ctx).apply { isSingleSelection = true }
    listOf(1, 2, 5).forEach { n ->
        coolGroup.addView(
            Chip(ctx).apply {
                text = "防误触 ${n}s"
                isCheckable = true
                isChecked = n == cooldownSec
                setOnClickListener { cooldownSec = n }
            }
        )
    }
    val tvBig = bigReadout(ctx, "0")
    val tvSub = labelText(ctx, "触发次数").apply { gravity = Gravity.CENTER }
    val tvHint = hintText(ctx, "摇一下开发板就执行选定动作；适合盲操、课堂互动、演示翻页。")
    root.addView(actionGroup)
    root.addView(
        et,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 8) }
    )
    root.addView(
        coolGroup,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 8) }
    )
    root.addView(tvBig)
    root.addView(tvSub)
    root.addView(tvHint)

    val poller = SensorPoller(150)
    poller.onSnapshot = { s ->
        if (s.g == "shake" && shakeGate.allow(cooldownSec * 1000L)) {
            triggers++
            command = et.text.toString().ifBlank { "SCROLL:收到" }
            runOnUiThread {
                tvBig.text = triggers.toString()
                when (action) {
                    0 -> sendLine(Protocol.custom(command))
                    1 -> sendLine(Protocol.melody("POWERUP"))
                    2 -> sendLine(Protocol.clear())
                    3 -> sendLine(Protocol.show(rowsToMatrix(Patterns.HEART)))
                }
            }
        }
    }
    showFeatureDialog(root, "摇晃触发器", onShow = { poller.start() }, onDismiss = { poller.stop() })
}

// ---------- 2. 手势锁 ----------

private val GESTURE_OPTIONS = listOf("shake", "tap", "logoup", "facedown", "freefall")

internal fun MainActivity.openGestureLock() {
    if (!requireConnected()) return
    val ctx = this
    val pattern = ArrayList<String>() // 学习得到的手势序列
    var learning = true
    var pos = 0
    var lockedAction = 0 // 0 发密语 1 清屏

    val root = M3Kit.dialogRoot(ctx)
    val tvBig = bigReadout(ctx, "学习模式")
    val tvSub = labelText(ctx, "依次做 3 个手势设成解锁口令").apply { gravity = Gravity.CENTER }
    val chipRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    val chips = ArrayList<TextView>(3)
    repeat(3) {
        chips.add(
            TextView(ctx).apply {
                text = "○"
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(ctx.themeColor(MR.attr.colorOutline))
            }
        )
    }
    chips.forEach { c ->
        chipRow.addView(
            c,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
    }
    val etSecret = EditText(ctx).apply {
        hint = "解锁后发送的指令，如 SCROLL:芝麻开门"
        inputType = android.text.InputType.TYPE_CLASS_TEXT
        setText("SCROLL:芝麻开门")
        textSize = 14f
    }
    val btnRelearn = MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply { text = "重新学习手势" }
    val tvHint = hintText(
        ctx,
        "防误触玩具：先依次做 3 个手势作为口令，之后只有按顺序复现才会触发密语指令；" +
            "适合挂在包上防止路人乱按。"
    )
    root.addView(tvBig)
    root.addView(tvSub)
    root.addView(
        chipRow,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 10) }
    )
    root.addView(
        etSecret,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 10) }
    )
    root.addView(
        btnRelearn,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 10) }
    )
    root.addView(tvHint)

    fun refreshChips() {
        chips.forEachIndexed { i, c ->
            when {
                i < pattern.size -> {
                    c.text = "●"
                    c.setTextColor(ctx.themeColor(MR.attr.colorPrimary))
                }
                i == pattern.size && learning -> {
                    c.text = "▶"
                    c.setTextColor(ctx.themeColor(MR.attr.colorPrimary))
                }
                else -> {
                    c.text = "○"
                    c.setTextColor(ctx.themeColor(MR.attr.colorOutline))
                }
            }
        }
    }
    refreshChips()

    var lastGesture: String? = null
    var lastAcceptAt = 0L

    val poller = SensorPoller(200)
    poller.onSnapshot = { s ->
        val g = s.g
        val now = System.currentTimeMillis()
        if (g != null && g != lastGesture && now - lastAcceptAt >= 800 && g in GESTURE_OPTIONS) {
            lastGesture = g
            lastAcceptAt = now
            runOnUiThread {
            if (learning) {
                pattern.add(g)
                if (pattern.size >= 3) {
                    learning = false
                    tvBig.text = "已上锁"
                    tvSub.text = "按顺序复现手势解锁"
                } else {
                    tvSub.text = "第 ${pattern.size + 1} 个手势（还要 ${3 - pattern.size} 个）"
                }
                refreshChips()
            } else {
                if (pattern.getOrNull(pos) == g) {
                    pos++
                    refreshChips()
                    if (pos >= pattern.size) {
                        tvBig.text = "解锁！"
                        sendLine(Protocol.custom(etSecret.text.toString().ifBlank { "SCROLL:芝麻开门" }))
                        sendLine(Protocol.tone(1568, 300))
                        featureSnack("手势口令正确，已执行")
                        pos = 0
                        refreshChips()
                    }
                } else {
                    pos = 0
                    refreshChips()
                    sendLine(Protocol.tone(220, 150))
                }
            }
        }
    }
    }
    btnRelearn.setOnClickListener {
        pattern.clear()
        learning = true
        pos = 0
        tvBig.text = "学习模式"
        tvSub.text = "依次做 3 个手势设成解锁口令"
        refreshChips()
    }
    showFeatureDialog(root, "手势锁", onShow = { poller.start() }, onDismiss = { poller.stop() })
}

// ---------- 3. 连接哨兵 ----------

internal fun MainActivity.openLinkMonitor() {
    val ctx = this
    var connectedAt = if (ConnectionHub.isConnected()) System.currentTimeMillis() else 0L
    var drops = 0
    var lastDropAt = 0L
    var lastMsg = ""

    val root = M3Kit.dialogRoot(ctx)
    val tvState = bigReadout(ctx, if (ConnectionHub.isConnected()) "在线" else "离线")
    val tvInfo = labelText(ctx, "").apply { gravity = Gravity.CENTER }
    val tvHint = hintText(ctx, "实时跟踪连接状态、本次在线时长与断线次数；掉线会弹提醒。排查「时不时就连不上」的问题很直观。")
    root.addView(tvState)
    root.addView(tvInfo)
    root.addView(tvHint)

    fun refresh() {
        val type = ConnectionHub.type()
        val typeName = when (type) {
            "usb" -> "USB 串口"
            "ble" -> "蓝牙 BLE"
            "sim" -> "内置模拟器"
            else -> "无"
        }
        val online = if (connectedAt > 0) System.currentTimeMillis() - connectedAt else 0L
        val onlineText = String.format(Locale.US, "%02d:%02d", online / 60000, online / 1000 % 60)
        val dropText = if (lastDropAt > 0) {
            val ago = (System.currentTimeMillis() - lastDropAt) / 1000
            "${ago}s 前"
        } else "从未"
        tvInfo.text = "方式：$typeName\n本次在线：$onlineText\n断线次数：$drops（上次：$dropText）"
    }

    val listener = object : MicrobitConnection.Listener {
        override fun onState(state: ConnState) {
            runOnUiThread {
                lastMsg = state.message
                if (state.connected) {
                    if (connectedAt == 0L) connectedAt = System.currentTimeMillis()
                    tvState.text = "在线"
                    tvState.setTextColor(ctx.themeColor(MR.attr.colorPrimary))
                } else {
                    if (connectedAt > 0L) {
                        drops++
                        lastDropAt = System.currentTimeMillis()
                        featureSnack("连接已断开：$lastMsg", long = true)
                    }
                    connectedAt = 0L
                    tvState.text = "离线"
                    tvState.setTextColor(ctx.themeColor(MR.attr.colorError))
                }
                refresh()
            }
        }
        override fun onData(data: String) {}
    }
    ConnectionHub.addListener(listener)

    val tick = M3Kit.Ticker({ 500L }, mainHandler) {
        refresh()
        true
    }
    refresh()
    showFeatureDialog(
        root, "连接哨兵",
        onShow = { tick.start() },
        onDismiss = {
            tick.stop()
            ConnectionHub.removeListener(listener)
        }
    )
}

// ---------- 4. 指令编排 ----------

internal fun MainActivity.openCommandSeq() {
    val ctx = this
    val steps = ArrayList(CommandSeqStore.fromJson(Prefs.getCommandSeq(ctx)))
    var running = false

    val root = M3Kit.dialogRoot(ctx)
    val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    val scroll = android.widget.ScrollView(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        addView(box)
    }
    val etCmd = EditText(ctx).apply {
        hint = "指令，如 SHOW:00100,01110,11111,01110,00100"
        inputType = android.text.InputType.TYPE_CLASS_TEXT
        textSize = 13f
        typeface = android.graphics.Typeface.MONOSPACE
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }
    val etDelay = EditText(ctx).apply {
        hint = "延时 ms"
        inputType = android.text.InputType.TYPE_CLASS_NUMBER
        setText("500")
        textSize = 13f
    }
    val inputRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    inputRow.addView(etCmd)
    inputRow.addView(
        etDelay,
        LinearLayout.LayoutParams(M3Kit.dp(ctx, 90), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = M3Kit.dp(ctx, 8) }
    )
    val btnAdd = MaterialButton(ctx).apply { text = "＋ 添加步骤" }
    val btnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    val btnRun = MaterialButton(ctx).apply { text = "按序执行" }
    val btnSave = MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply { text = "保存" }
    val btnClear = MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply { text = "清空" }
    btnRow.addView(btnRun)
    btnRow.addView(
        btnSave,
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = M3Kit.dp(ctx, 8) }
    )
    btnRow.addView(
        btnClear,
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = M3Kit.dp(ctx, 8) }
    )
    val tvHint = hintText(ctx, "把多条指令排成带延时的序列一键执行；适合固定演示流程、实验步骤、开场秀。")

    fun renderList() {
        box.removeAllViews()
        if (steps.isEmpty()) {
            box.addView(labelText(ctx, "还没有步骤，先在下面加一条").apply { textSize = 13f })
            return
        }
        steps.forEachIndexed { i, s ->
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            val label = labelText(ctx, "${i + 1}. ${s.command}  ⏱${s.delayMs}ms").apply {
                textSize = 12f
                typeface = android.graphics.Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(label)
            val btnUp = MaterialButton(
                ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = "↑"
                textSize = 11f
                isEnabled = i > 0
                setOnClickListener {
                    val t = steps[i - 1]; steps[i - 1] = steps[i]; steps[i] = t
                    renderList()
                }
            }
            val btnDown = MaterialButton(
                ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = "↓"
                textSize = 11f
                isEnabled = i < steps.size - 1
                setOnClickListener {
                    val t = steps[i + 1]; steps[i + 1] = steps[i]; steps[i] = t
                    renderList()
                }
            }
            val btnDel = MaterialButton(
                ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = "✕"
                textSize = 11f
                setOnClickListener {
                    steps.removeAt(i)
                    renderList()
                }
            }
            row.addView(btnUp)
            row.addView(btnDown)
            row.addView(btnDel)
            box.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = M3Kit.dp(ctx, 6) }
            )
        }
    }
    renderList()

    btnAdd.setOnClickListener {
        val cmd = etCmd.text.toString().trim()
        if (cmd.isEmpty()) {
            featureSnack("先填指令")
            return@setOnClickListener
        }
        val delay = etDelay.text.toString().toIntOrNull()?.coerceIn(0, 60_000) ?: 500
        steps.add(CommandSeqStore.Step(cmd, delay))
        etCmd.setText("")
        renderList()
    }
    btnSave.setOnClickListener {
        Prefs.setCommandSeq(ctx, CommandSeqStore.toJson(steps))
        featureSnack("已保存 ${steps.size} 步编排")
    }
    btnClear.setOnClickListener {
        steps.clear()
        Prefs.setCommandSeq(ctx, CommandSeqStore.toJson(steps))
        renderList()
    }
    btnRun.setOnClickListener {
        if (steps.isEmpty()) {
            featureSnack("先添加步骤")
            return@setOnClickListener
        }
        if (!requireConnected() || running) return@setOnClickListener
        running = true
        btnRun.text = "执行中…"
        val snapshot = steps.toList()
        Thread {
            try {
                snapshot.forEachIndexed { i, s ->
                    if (isFinishing || isDestroyed) return@forEachIndexed
                    runOnUiThread { sendLine(Protocol.custom(s.command)) }
                    val last = i == snapshot.size - 1
                    if (!last) Thread.sleep(s.delayMs.toLong())
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                running = false
                runOnUiThread { btnRun.text = "按序执行" }
            }
        }.start()
    }
    root.addView(scroll)
    root.addView(
        inputRow,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 8) }
    )
    root.addView(
        btnAdd,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 6) }
    )
    root.addView(
        btnRow,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 8) }
    )
    root.addView(tvHint)
    showFeatureDialog(root, "指令编排", onDismiss = {
        Prefs.setCommandSeq(ctx, CommandSeqStore.toJson(steps))
    })
}

// ---------- 5. 快捷面板 ----------

internal fun MainActivity.openQuickPanel() {
    val ctx = this
    val picked = HashSet(Prefs.getQuickPanel(ctx))

    val root = M3Kit.dialogRoot(ctx)
    val tvInfo = labelText(ctx, "").apply { gravity = Gravity.CENTER }
    val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    val scroll = android.widget.ScrollView(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        addView(box)
    }
    val chips = HashMap<String, Chip>()
    fun refreshInfo() {
        tvInfo.text = "已选 ${picked.size} 项，点右下角「打开面板」直接进宫格"
    }
    FeatureCatalog.Category.entries.forEach { category ->
        box.addView(
            labelText(ctx, category.title, bold = true).apply { textSize = 12f },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = M3Kit.dp(ctx, 8) }
        )
        FeatureCatalog.byCategory(category).forEach { f ->
            val chip = Chip(ctx).apply {
                text = f.title
                isCheckable = true
                isChecked = picked.contains(f.id)
                setOnClickListener {
                    if (isChecked) picked.add(f.id) else picked.remove(f.id)
                    Prefs.setQuickPanel(ctx, picked)
                    refreshInfo()
                }
            }
            chips[f.id] = chip
            box.addView(
                chip,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }
    val btnOpen = MaterialButton(ctx).apply { text = "打开面板" }
    val tvHint = hintText(ctx, "从全部功能里挑常用的收进宫格；宫格点按直接打开对应功能。")
    refreshInfo()
    root.addView(tvInfo)
    root.addView(scroll)
    root.addView(
        btnOpen,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 10) }
    )
    root.addView(tvHint)

    btnOpen.setOnClickListener {
        if (picked.isEmpty()) {
            featureSnack("先勾选几个常用功能")
            return@setOnClickListener
        }
        var panelDialog: android.content.DialogInterface? = null
        val panel = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val grid = android.widget.GridLayout(ctx).apply { columnCount = 3 }
        FeatureCatalog.FEATURES.filter { picked.contains(it.id) }.forEach { f ->
            val card = com.google.android.material.card.MaterialCardView(ctx).apply {
                radius = M3Kit.dp(ctx, 12).toFloat()
                cardElevation = 0f
                strokeColor = ctx.themeColor(MR.attr.colorOutlineVariant)
                strokeWidth = M3Kit.dp(ctx, 1)
                setCardBackgroundColor(ctx.themeColor(MR.attr.colorSurface))
            }
            val inner = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(M3Kit.dp(ctx, 6), M3Kit.dp(ctx, 10), M3Kit.dp(ctx, 6), M3Kit.dp(ctx, 10))
            }
            inner.addView(
                android.widget.ImageView(ctx).apply {
                    setImageResource(f.icon)
                    imageTintList = android.content.res.ColorStateList.valueOf(
                        ctx.themeColor(MR.attr.colorPrimary)
                    )
                }
            )
            inner.addView(
                TextView(ctx).apply {
                    text = f.title
                    textSize = 11f
                    gravity = Gravity.CENTER
                }
            )
            card.addView(inner)
            card.setOnClickListener {
                panelDialog?.dismiss()
                onFuncClick(f.id)
            }
            grid.addView(
                card,
                android.widget.GridLayout.LayoutParams().apply {
                    width = 0
                    columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                    setMargins(M3Kit.dp(ctx, 4), M3Kit.dp(ctx, 4), M3Kit.dp(ctx, 4), M3Kit.dp(ctx, 4))
                }
            )
        }
        panel.addView(grid)
        panelDialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle("快捷面板")
            .setView(panel)
            .setNegativeButton("关闭", null)
            .show()
    }
    showFeatureDialog(root, "快捷面板")
}

// ---------- 6. 实时日志 ----------

internal fun MainActivity.openLogStream() {
    val ctx = this
    val lines = ArrayDeque<String>(200)
    var paused = false
    var filter = ""
    val t0 = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    val root = M3Kit.dialogRoot(ctx)
    val etFilter = EditText(ctx).apply {
        hint = "过滤关键字（留空显示全部）"
        inputType = android.text.InputType.TYPE_CLASS_TEXT
        textSize = 13f
    }
    val tvLog = TextView(ctx).apply {
        textSize = 11f
        typeface = android.graphics.Typeface.MONOSPACE
        setTextIsSelectable(true)
    }
    val scroll = android.widget.ScrollView(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        addView(tvLog)
    }
    val btnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    val btnPause = MaterialButton(ctx).apply { text = "暂停滚动" }
    val btnExport = MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply { text = "导出" }
    val btnClear = MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply { text = "清空" }
    btnRow.addView(btnPause)
    btnRow.addView(
        btnExport,
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = M3Kit.dp(ctx, 8) }
    )
    btnRow.addView(
        btnClear,
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = M3Kit.dp(ctx, 8) }
    )
    val tvHint = hintText(ctx, "所有串口收发实时留痕（→ 发出 / ← 收到）；排查指令是否真的送达、回包是否正常。")
    root.addView(etFilter)
    root.addView(scroll)
    root.addView(
        btnRow,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 8) }
    )
    root.addView(tvHint)

    fun push(dir: String, line: String) {
        if (paused) return
        val text = line.trim().replace("\n", " ⏎ ")
        if (filter.isNotEmpty() && !text.contains(filter, true)) return
        lines.addLast("${t0.format(Date())} $dir $text")
        while (lines.size > 200) lines.removeFirst()
        tvLog.text = lines.joinToString("\n")
        scroll.post { scroll.fullScroll(android.view.View.FOCUS_DOWN) }
    }

    val listener = object : MicrobitConnection.Listener {
        override fun onState(state: ConnState) {
            runOnUiThread { push("--", "STATE ${state.connected} ${state.message}") }
        }
        override fun onData(data: String) {
            runOnUiThread { push("←", data) }
        }
    }
    ConnectionHub.addListener(listener)

    etFilter.doAfterTextChanged { filter = it }
    btnPause.setOnClickListener {
        paused = !paused
        btnPause.text = if (paused) "继续滚动" else "暂停滚动"
    }
    btnClear.setOnClickListener {
        lines.clear()
        tvLog.text = ""
    }
    btnExport.setOnClickListener {
        if (lines.isEmpty()) {
            featureSnack("暂无日志")
            return@setOnClickListener
        }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val dir = java.io.File(ctx.cacheDir, "packaged").apply { mkdirs() }
        val f = java.io.File(dir, "串口日志-$stamp.txt")
        runCatching { f.writeText(lines.joinToString("\n")) }
            .onSuccess {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    ctx, "${ctx.packageName}.fileprovider", f
                )
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching { ctx.startActivity(android.content.Intent.createChooser(intent, "导出串口日志")) }
                    .onFailure { featureSnack("没有可处理文本的应用") }
            }
            .onFailure { featureSnack("保存失败：${it.message}") }
    }
    showFeatureDialog(root, "实时日志", onDismiss = { ConnectionHub.removeListener(listener) })
}

// ---------- 7. 空闲机器人 ----------

internal fun MainActivity.openIdleBot() {
    if (!requireConnected()) return
    val ctx = this
    var idleSec = 60
    var lastActiveAt = System.currentTimeMillis()
    var greeting = false

    val root = M3Kit.dialogRoot(ctx)
    val tvBig = bigReadout(ctx, "待机")
    val tvSub = labelText(ctx, "").apply { gravity = Gravity.CENTER }
    val idleGroup = ChipGroup(ctx).apply { isSingleSelection = true }
    listOf(30, 60, 120).forEach { n ->
        idleGroup.addView(
            Chip(ctx).apply {
                text = "${n}s 无操作"
                isCheckable = true
                isChecked = n == idleSec
                setOnClickListener { idleSec = n }
            }
        )
    }
    val tvHint = hintText(
        ctx,
        "开发板闲置超时后自动眨眼 + 播报问候语，有人碰它就复位；展台无人值守时自己会「找存在感」。"
    )
    root.addView(tvBig)
    root.addView(tvSub)
    root.addView(
        idleGroup,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 10) }
    )
    root.addView(tvHint)

    fun blink() {
        Thread {
            try {
                repeat(4) {
                    if (isFinishing || isDestroyed) return@repeat
                    runOnUiThread {
                        sendLine(Protocol.show(rowsToMatrix(listOf("00000", "01010", "00000", "10001", "01110"))))
                    }
                    Thread.sleep(300)
                    runOnUiThread {
                        sendLine(Protocol.show(rowsToMatrix(listOf("00000", "00000", "00000", "10001", "01110"))))
                    }
                    Thread.sleep(200)
                }
                runOnUiThread { sendLine(Protocol.scroll("无聊了，来玩呀")) }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }.start()
    }

    var prev: Triple<Int, Int, Int>? = null
    val poller = SensorPoller(300)
    poller.onSnapshot = { s ->
        val cur = Triple(s.x ?: 0, s.y ?: 0, s.z ?: 0)
        val p = prev
        prev = cur
        val moved = p != null && (
            Math.abs(cur.first - p.first) + Math.abs(cur.second - p.second) + Math.abs(cur.third - p.third) > 120
            )
        val active = moved || s.a || s.b || s.g != null
        if (active) {
            lastActiveAt = System.currentTimeMillis()
            if (greeting) {
                greeting = false
                runOnUiThread {
                    tvBig.text = "待机"
                    sendLine(Protocol.clear())
                }
            }
        }
        val idle = (System.currentTimeMillis() - lastActiveAt) / 1000
        runOnUiThread {
            tvSub.text = "已空闲 ${idle}s（阈值 ${idleSec}s）"
            if (!greeting && idle >= idleSec) {
                greeting = true
                tvBig.text = "打招呼中"
                sendLine(Protocol.tone(660, 120))
                blink()
            }
        }
    }
    showFeatureDialog(root, "空闲机器人", onShow = { poller.start() }, onDismiss = { poller.stop() })
}

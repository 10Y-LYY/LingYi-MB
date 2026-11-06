/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

import com.lingyi.mbos.ui.M3Kit.doAfterTextChanged
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.R as MR
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lingyi.mbos.MainActivity
import com.lingyi.mbos.util.themeColor

/*
 * 控制台创意与工具（v3.3.0-preview）：LED 动画工作室 / 电子琴 / 摩斯电码 / 弹幕留言 / 寻物铃。
 */

// ---------- 11. LED 动画工作室 ----------

internal fun MainActivity.openAnimationStudio() {
    val ctx = this
    val frames = ArrayList<AnimationStore.Frame>()
    var current = 0
    var playing = false

    val root = M3Kit.dialogRoot(ctx)
    val tvTip = labelText(ctx, "帧序列（点击缩略图编辑，长按删除）", bold = true)
    val stripScroll = HorizontalScrollView(ctx).apply {
        isHorizontalScrollBarEnabled = false
    }
    val strip = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    stripScroll.addView(strip)
    val editorCard = featureCard(ctx)
    val editorInner = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(M3Kit.dp(ctx, 14), M3Kit.dp(ctx, 12), M3Kit.dp(ctx, 14), M3Kit.dp(ctx, 12))
    }
    editorCard.addView(editorInner)
    val etDur = EditText(ctx).apply {
        inputType = InputType.TYPE_CLASS_NUMBER
        setText("200")
        hint = "每帧停留（毫秒）"
        textSize = 14f
    }
    val nameRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    val etName = EditText(ctx).apply {
        inputType = InputType.TYPE_CLASS_TEXT
        hint = "动画名字"
        textSize = 14f
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }
    val btnSaveAnim = MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply { text = "保存" }
    nameRow.addView(etName)
    nameRow.addView(
        btnSaveAnim,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = M3Kit.dp(ctx, 8) }
    )
    val playRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    val btnAddFrame = MaterialButton(ctx).apply { text = "＋ 添加当前帧" }
    val btnPlay = MaterialButton(
        ctx
    ).apply { text = "播放" }
    val btnClearFrames = MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply { text = "清空帧" }
    playRow.addView(btnAddFrame)
    playRow.addView(
        btnPlay,
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = M3Kit.dp(ctx, 8)
        }
    )
    playRow.addView(
        btnClearFrames,
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = M3Kit.dp(ctx, 8)
        }
    )
    val savedLabel = labelText(ctx, "已保存的动画（点击载入，长按删除）", bold = true).topMargin(ctx, 14)
    val savedBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    val tvHint = hintText(
        ctx,
        "逐帧绘制 5×5 图案，设定每帧停留时长后播放；可保存多组动画随时调用。" +
            "做闪烁标志、流水灯、简单表情动画都靠它。"
    )
    root.addView(tvTip)
    root.addView(stripScroll)
    root.addView(
        editorCard,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 10) }
    )
    root.addView(
        playRow,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 10) }
    )
    root.addView(nameRow.apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 8) }
    })
    root.addView(savedLabel)
    root.addView(savedBox)
    root.addView(tvHint)

    // ---------- 帧编辑器（槽位式：换帧时整格重挂，刷新格子只改背景色） ----------
    val editing = Array(5) { BooleanArray(5) }
    val editorSlot = FrameLayout(ctx)
    val editorTitle = labelText(ctx, "当前帧", bold = true)

    fun mountEditor() {
        editorSlot.removeAllViews()
        val grid = GridLayout(ctx)
        buildMatrixEditor(grid, editing) { x, y ->
            editing[y][x] = !editing[y][x]
            setMatrixCell(grid, x, y, editing[y][x])
        }
        editorSlot.addView(
            grid,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL
            )
        )
    }
    mountEditor()

    editorInner.addView(editorTitle)
    editorInner.addView(
        editorSlot,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 6) }
    )
    editorInner.addView(
        etDur,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 8) }
    )

    // ---------- 缩略图条 ----------
    fun miniGrid(rows: List<String>): GridLayout {
        val g = GridLayout(ctx).apply { columnCount = 5; rowCount = 5 }
        val cell = M3Kit.dp(ctx, 6)
        for (y in 0..4) for (x in 0..4) {
            val dot = View(ctx).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(
                        if (rows.getOrNull(y)?.getOrNull(x) == '1') LED_ON_COLOR
                        else ctx.themeColor(MR.attr.colorSurfaceVariant)
                    )
                }
            }
            g.addView(
                dot,
                GridLayout.LayoutParams().apply {
                    width = cell; height = cell; setMargins(1, 1, 1, 1)
                }
            )
        }
        return g
    }

    fun refreshStrip() {
        strip.removeAllViews()
        frames.forEachIndexed { i, f ->
            val item = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(M3Kit.dp(ctx, 8), M3Kit.dp(ctx, 8), M3Kit.dp(ctx, 8), M3Kit.dp(ctx, 8))
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = M3Kit.dp(ctx, 10).toFloat()
                    setColor(
                        if (i == current) ctx.themeColor(MR.attr.colorSecondaryContainer)
                        else ctx.themeColor(MR.attr.colorSurfaceVariant)
                    )
                }
                setOnClickListener {
                    current = i
                    for (y in 0..4) for (x in 0..4) {
                        editing[y][x] = f.rows.getOrNull(y)?.getOrNull(x) == '1'
                    }
                    etDur.setText(f.durMs.toString())
                    mountEditor()
                    refreshStrip()
                }
                setOnLongClickListener {
                    frames.removeAt(i)
                    if (current >= frames.size) current = (frames.size - 1).coerceAtLeast(0)
                    refreshStrip()
                    true
                }
            }
            item.addView(miniGrid(f.rows))
            item.addView(
                TextView(ctx).apply {
                    text = "${f.durMs}ms"
                    textSize = 10f
                    setTextColor(ctx.themeColor(MR.attr.colorOnSurfaceVariant))
                }
            )
            strip.addView(
                item,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = M3Kit.dp(ctx, 8) }
            )
        }
        if (frames.isEmpty()) {
            strip.addView(
                labelText(ctx, "还没有帧，先画一帧点「添加」").apply {
                    setPadding(M3Kit.dp(ctx, 6), M3Kit.dp(ctx, 10), M3Kit.dp(ctx, 6), M3Kit.dp(ctx, 10))
                }
            )
        }
    }

    fun refreshSaved() {
        savedBox.removeAllViews()
        val saved = AnimationStore.loadAll(ctx)
        if (saved.isEmpty()) {
            savedBox.addView(labelText(ctx, "（保存过的动画会出现在这里）").apply { textSize = 12f })
            return
        }
        saved.forEach { anim ->
            savedBox.addView(
                MaterialButton(
                    ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
                ).apply {
                    text = "${anim.name}（${anim.frames.size} 帧）"
                    setOnClickListener {
                        frames.clear()
                        frames.addAll(anim.frames)
                        current = 0
                        refreshStrip()
                        featureSnack("已载入「${anim.name}」")
                    }
                    setOnLongClickListener {
                        AnimationStore.delete(ctx, anim.name)
                        refreshSaved()
                        featureSnack("已删除「${anim.name}」")
                        true
                    }
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = M3Kit.dp(ctx, 6) }
            )
        }
    }

    btnAddFrame.setOnClickListener {
        val dur = etDur.text.toString().toIntOrNull()?.coerceIn(50, 60_000) ?: 200
        val rows = (0 until 5).map { y ->
            (0 until 5).joinToString("") { x -> if (editing[y][x]) "1" else "0" }
        }
        frames.add(AnimationStore.Frame(rows, dur))
        current = frames.size - 1
        refreshStrip()
    }
    btnClearFrames.setOnClickListener {
        frames.clear()
        current = 0
        refreshStrip()
    }
    btnPlay.setOnClickListener {
        if (frames.isEmpty()) {
            featureSnack("先添加至少一帧")
            return@setOnClickListener
        }
        if (playing) return@setOnClickListener
        playing = true
        btnPlay.text = "停止"
        Thread {
            try {
                do {
                    frames.forEach { f ->
                        if (!playing || isFinishing || isDestroyed) return@forEach
                        runOnUiThread { sendLine(Protocol.show(f.toMatrix())) }
                        Thread.sleep(f.durMs.toLong())
                    }
                } while (playing && !isFinishing && !isDestroyed)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                playing = false
                runOnUiThread { btnPlay.text = "播放" }
            }
        }.start()
    }
    btnPlay.setOnLongClickListener {
        playing = false
        true
    }
    btnSaveAnim.setOnClickListener {
        val name = etName.text.toString().trim()
        if (name.isEmpty()) {
            featureSnack("先给动画起个名字")
            return@setOnClickListener
        }
        if (frames.isEmpty()) {
            featureSnack("还没有帧可保存")
            return@setOnClickListener
        }
        AnimationStore.save(ctx, name, frames.toList())
        etName.text.clear()
        refreshSaved()
        featureSnack("已保存「$name」")
    }

    refreshStrip()
    refreshSaved()
    showFeatureDialog(root, "LED 动画工作室")
}

// ---------- 12. 电子琴 ----------

internal fun MainActivity.openPiano() {
    if (!requireConnected()) return
    val ctx = this
    var octave = 4
    val recorded = ArrayList<Pair<Long, Int>>() // (延时 ms, midi)
    val recT0 = longArrayOf(0L)
    var recording = false
    var playingBack = false

    val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(M3Kit.dp(ctx, 20), M3Kit.dp(ctx, 14), M3Kit.dp(ctx, 20), M3Kit.dp(ctx, 8))
    }
    val tvInfo = labelText(ctx, "中央 C 起 · 第 4 组 · 点按键发声，长按录音回放").apply {
        gravity = Gravity.CENTER
    }
    val kb = FrameLayout(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, M3Kit.dp(ctx, 170)
        ).apply { topMargin = M3Kit.dp(ctx, 10) }
    }
    val whiteRow = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
    val whiteNames = listOf("C", "D", "E", "F", "G", "A", "B")
    val whiteOffsets = listOf(0, 2, 4, 5, 7, 9, 11)
    val blackAfterWhite = listOf(0, 1, 3, 4, 5) // C# D# F# G# A#

    fun midiOf(offset: Int): Int = (octave + 1) * 12 + offset
    fun freqOf(midi: Int): Double = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)

    val whiteBtns = ArrayList<MaterialButton>(7)
    whiteNames.forEachIndexed { i, name ->
        val btn = MaterialButton(ctx).apply {
            text = name
            setTextColor(Color.parseColor("#161A23"))
            setBackgroundColor(Color.WHITE)
            strokeColor = android.content.res.ColorStateList.valueOf(
                ctx.themeColor(MR.attr.colorOutlineVariant)
            )
            strokeWidth = M3Kit.dp(ctx, 1)
            isAllCaps = false
        }
        whiteBtns.add(btn)
        whiteRow.addView(btn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
    }
    kb.addView(whiteRow)

    fun playNote(offset: Int) {
        val midi = midiOf(offset)
        sendLine(Protocol.tone(freqOf(midi).toInt(), 380))
        if (recording) recorded.add((System.currentTimeMillis() - recT0[0]) to midi)
    }

    val blackBtns = ArrayList<MaterialButton>(5)
    blackAfterWhite.forEach { wi ->
        val offset = whiteOffsets[wi] + 1
        val btn = MaterialButton(ctx).apply {
            text = ""
            setBackgroundColor(Color.parseColor("#161A23"))
            strokeWidth = 0
            isAllCaps = false
            setOnClickListener { playNote(offset) }
        }
        blackBtns.add(btn)
        kb.addView(
            btn,
            FrameLayout.LayoutParams(0, M3Kit.dp(ctx, 105)).apply { gravity = Gravity.TOP }
        )
    }
    // 布局完成后按白键实际位置摆黑键（精确对齐键缝）
    kb.post {
        val w = whiteBtns[0].width.takeIf { it > 0 } ?: return@post
        val bw = (w * 0.62f).toInt()
        blackBtns.forEachIndexed { i, btn ->
            val lp = btn.layoutParams as FrameLayout.LayoutParams
            lp.width = bw
            btn.layoutParams = lp
            val anchor = whiteBtns[blackAfterWhite[i]]
            btn.translationX = anchor.x + anchor.width - bw / 2f
        }
    }
    whiteBtns.forEachIndexed { i, btn ->
        btn.setOnClickListener { playNote(whiteOffsets[i]) }
    }

    val octRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    val btnOctDown = MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply { text = "低八度" }
    val btnOctUp = MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply { text = "高八度" }
    octRow.addView(btnOctDown)
    octRow.addView(
        btnOctUp,
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = M3Kit.dp(ctx, 10)
        }
    )
    val recRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    val btnRec = MaterialButton(ctx).apply { text = "开始录音" }
    val btnPlayRec = MaterialButton(
        ctx
    ).apply {
        text = "回放"
        isEnabled = false
    }
    recRow.addView(btnRec)
    recRow.addView(
        btnPlayRec,
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = M3Kit.dp(ctx, 10)
        }
    )
    val tvHint = hintText(
        ctx,
        "频率按十二平均律换算（A4=440Hz），音域第 2–6 组；" +
            "录音记录按键与时值，回放时原样复现。micro:bit 喇叭是单声道蜂鸣器，听个响、练手感足够。"
    )
    root.addView(tvInfo)
    root.addView(kb)
    root.addView(
        octRow,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 12) }
    )
    root.addView(
        recRow,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 8) }
    )
    root.addView(tvHint)

    btnOctDown.setOnClickListener {
        if (octave > 2) octave--
        tvInfo.text = "中央 C 起 · 第 $octave 组 · 点按键发声，长按录音回放"
    }
    btnOctUp.setOnClickListener {
        if (octave < 6) octave++
        tvInfo.text = "中央 C 起 · 第 $octave 组 · 点按键发声，长按录音回放"
    }
    btnRec.setOnClickListener {
        if (recording) {
            recording = false
            btnRec.text = "开始录音"
            btnPlayRec.isEnabled = recorded.isNotEmpty()
            featureSnack("录音结束，共 ${recorded.size} 个音")
        } else {
            if (playingBack) return@setOnClickListener
            recorded.clear()
            recT0[0] = System.currentTimeMillis()
            recording = true
            btnRec.text = "停止录音"
            featureSnack("开始录音，弹奏吧")
        }
    }
    btnPlayRec.setOnClickListener {
        if (recorded.isEmpty() || playingBack) return@setOnClickListener
        playingBack = true
        val notes = recorded.toList()
        val playT0 = System.currentTimeMillis()
        Thread {
            try {
                notes.forEach { (at, midi) ->
                    val wait = at - (System.currentTimeMillis() - playT0)
                    if (wait > 0) Thread.sleep(wait)
                    if (isFinishing || isDestroyed) return@forEach
                    runOnUiThread { sendLine(Protocol.tone(freqOf(midi).toInt(), 320)) }
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                playingBack = false
            }
        }.start()
    }

    showFeatureDialog(root, "电子琴")
}

// ---------- 13. 摩斯电码 ----------

private val MORSE_DOT = listOf("00000", "00000", "00100", "00000", "00000")
private val MORSE_DASH = listOf("00000", "00000", "11100", "00000", "00000")

internal fun MainActivity.openMorse() {
    val ctx = this
    var playing = false

    val root = M3Kit.dialogRoot(ctx)
    val et = EditText(ctx).apply {
        hint = "输入文字，如 SOS"
        inputType = InputType.TYPE_CLASS_TEXT
        setText("SOS")
        textSize = 15f
    }
    val tvPreview = TextView(ctx).apply {
        textSize = 13f
        typeface = android.graphics.Typeface.MONOSPACE
        setTextIsSelectable(true)
        setPadding(0, M3Kit.dp(ctx, 10), 0, 0)
    }
    fun refresh() {
        val text = et.text.toString()
        tvPreview.text = if (text.isBlank()) "（输入文字后显示编码）" else Morse.encode(text)
    }
    val speedGroup = ChipGroup(ctx).apply { isSingleSelection = true }
    val speeds = listOf(10, 15, 20, 25)
    var wpm = 15
    speeds.forEach { s ->
        speedGroup.addView(
            Chip(ctx).apply {
                text = "$s WPM"
                isCheckable = true
                isChecked = s == wpm
                setOnClickListener { wpm = s; refresh() }
            }
        )
    }
    val btnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    val btnPlay = MaterialButton(ctx).apply { text = "声光播放" }
    val btnStop = MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply {
        text = "停止"
        isEnabled = false
    }
    btnRow.addView(btnPlay)
    btnRow.addView(
        btnStop,
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = M3Kit.dp(ctx, 10)
        }
    )
    val tvHint = hintText(
        ctx,
        "按国际摩斯电码把文本编成滴（·）答（—），用板载喇叭发声、" +
            "LED 点 / 横线同步显示。速度单位 WPM 越大越快。"
    )
    root.addView(et)
    root.addView(tvPreview)
    root.addView(
        speedGroup,
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

    et.doAfterTextChanged { refresh() }
    refresh()

    btnPlay.setOnClickListener {
        val text = et.text.toString().trim()
        if (text.isEmpty()) {
            featureSnack("先输入要播放的文字")
            return@setOnClickListener
        }
        if (playing) return@setOnClickListener
        playing = true
        btnStop.isEnabled = true
        val beats = Morse.beats(text, wpm)
        Thread {
            try {
                beats.forEach { b ->
                    if (!playing || isFinishing || isDestroyed) return@forEach
                    // DOT / DASH 同一发送路径，仅图案与时长不同（v4.2 合并重复分支）
                    when (b.kind) {
                        Morse.Kind.DOT, Morse.Kind.DASH -> {
                            runOnUiThread {
                                sendLine(
                                    Protocol.show(
                                        rowsToMatrix(if (b.kind == Morse.Kind.DOT) MORSE_DOT else MORSE_DASH)
                                    )
                                )
                                sendLine(Protocol.tone(880, b.ms.toInt()))
                            }
                            Thread.sleep(b.ms)
                        }
                        else -> Thread.sleep(b.ms)
                    }
                }
                runOnUiThread { sendLine(Protocol.clear()) }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                playing = false
                runOnUiThread {
                    btnStop.isEnabled = false
                    sendLine(Protocol.clear())
                }
            }
        }.start()
    }
    btnStop.setOnClickListener {
        playing = false
        sendLine(Protocol.clear())
    }

    showFeatureDialog(root, "摩斯电码")
}

// ---------- 14. 弹幕留言 ----------

internal fun MainActivity.openBarrage() {
    val ctx = this
    var sending = false

    val root = M3Kit.dialogRoot(ctx)
    val et = EditText(ctx).apply {
        hint = "每行一条消息，依次滚动"
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        minLines = 4
        gravity = Gravity.TOP
        setText("大家好\n欢迎使用灵逸 MB\n今天也要开心呀")
        textSize = 15f
    }
    val presetRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    listOf("＋ 打招呼", "＋ 倒计时", "＋  today").forEach { label ->
        presetRow.addView(
            MaterialButton(
                ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = label
                textSize = 11f
                setOnClickListener {
                    val add = when (label) {
                        "＋ 打招呼" -> "你好呀！"
                        "＋ 倒计时" -> "3 2 1"
                        else -> "2026-10-21"
                    }
                    et.append("\n$add")
                }
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (label != "＋ 打招呼") marginStart = M3Kit.dp(ctx, 6)
            }
        )
    }
    val btnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    val btnSend = MaterialButton(ctx).apply { text = "开始播报" }
    val btnStop = MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply {
        text = "停止"
        isEnabled = false
    }
    btnRow.addView(btnSend)
    btnRow.addView(
        btnStop,
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = M3Kit.dp(ctx, 10)
        }
    )
    val tvHint = hintText(
        ctx,
        "适合班会 / 展台连续播报：每条消息独占一次滚动，条与条之间以提示音分隔。" +
            "长消息会自动滚动较长时间，请耐心等待。"
    )
    root.addView(et)
    root.addView(
        presetRow,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 8) }
    )
    root.addView(
        btnRow,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 10) }
    )
    root.addView(tvHint)

    btnSend.setOnClickListener {
        val lines = et.text.toString().split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            featureSnack("先写点什么")
            return@setOnClickListener
        }
        if (sending) return@setOnClickListener
        sending = true
        btnStop.isEnabled = true
        btnSend.text = "播报中…"
        Thread {
            try {
                lines.forEach { line ->
                    if (!sending || isFinishing || isDestroyed) return@forEach
                    runOnUiThread {
                        sendLine(Protocol.clear())
                        sendLine(Protocol.scroll(line))
                        sendLine(Protocol.tone(880, 90))
                    }
                    // 滚动时长与字符数相关，留足余量再发下一条
                    Thread.sleep(600L + line.length * 190L)
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                sending = false
                runOnUiThread {
                    btnSend.text = "开始播报"
                    btnStop.isEnabled = false
                }
            }
        }.start()
    }
    btnStop.setOnClickListener {
        sending = false
        sendLine(Protocol.clear())
    }

    showFeatureDialog(root, "弹幕留言")
}

// ---------- 15. 寻物铃 ----------

private val BELL_RING = listOf("01110", "10101", "10101", "10101", "01110")

internal fun MainActivity.openFindBell() {
    val ctx = this
    var ringing = false

    val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(M3Kit.dp(ctx, 24), M3Kit.dp(ctx, 16), M3Kit.dp(ctx, 24), M3Kit.dp(ctx, 8))
        gravity = Gravity.CENTER_HORIZONTAL
    }
    val tvState = bigReadout(ctx, "待机")
    val tvSub = labelText(ctx, "开发板会间歇响铃 + 闪光").apply {
        gravity = Gravity.CENTER
    }
    val btn = MaterialButton(ctx).apply { text = "开始寻物" }
    val tvHint = hintText(
        ctx,
        "把 micro:bit 落在沙发缝 / 书包角落时，点「开始寻物」让它响一声闪一下，" +
            "顺着声音找过去即可；找到后点「停止」。注意控制音量，别吓到室友。"
    )
    root.addView(tvState)
    root.addView(tvSub)
    root.addView(
        btn,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 14) }
    )
    root.addView(tvHint)

    btn.setOnClickListener {
        if (!ringing) {
            if (!requireConnected()) return@setOnClickListener
            ringing = true
            btn.text = "停止"
            tvState.text = "寻物中"
            tvState.setTextColor(ctx.themeColor(MR.attr.colorError))
            Thread {
                try {
                    var on = false
                    while (ringing && !isFinishing && !isDestroyed) {
                        on = !on
                        runOnUiThread {
                            sendLine(Protocol.tone(if (on) 2000 else 1500, 130))
                            sendLine(
                                Protocol.show(
                                    if (on) rowsToMatrix(BELL_RING)
                                    else Array(5) { BooleanArray(5) }
                                )
                            )
                        }
                        Thread.sleep(620)
                    }
                    runOnUiThread {
                        sendLine(Protocol.clear())
                        ringing = false
                        btn.text = "开始寻物"
                        tvState.text = "待机"
                        tvState.setTextColor(ctx.themeColor(MR.attr.colorPrimary))
                    }
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }.start()
        } else {
            ringing = false
        }
    }

    showFeatureDialog(root, "寻物铃")
}

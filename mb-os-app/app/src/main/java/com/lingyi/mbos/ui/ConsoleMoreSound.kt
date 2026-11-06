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
import com.lingyi.mbos.util.Melodies
import com.lingyi.mbos.util.themeColor
import java.util.Locale
import kotlin.random.Random

/*
 * 控制台新功能 · 声音与演示（预览版 4.0.0-preview 新增 7 项）：
 * 节拍器 / 门铃 / 警笛 / 音效板 / 谱曲台 / 旋律串烧 / 闹钟。
 * 发声全部走 TONE（固件蜂鸣器），与模拟器本地 TonePlayer 自动兼容。
 */

// ---------- 1. 节拍器 ----------

internal fun MainActivity.openMetronome() {
    if (!requireConnected()) return
    val ctx = this
    var bpm = 120
    var beatsPerBar = 4
    var running = false
    var beatInBar = 0

    val root = M3Kit.dialogRoot(ctx)
    val tvBig = bigReadout(ctx, "120")
    val tvSub = labelText(ctx, "BPM · 4/4 拍").apply { gravity = Gravity.CENTER }
    val seek = android.widget.SeekBar(ctx).apply {
        max = 200
        progress = bpm - 40
    }
    seek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(v: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
            bpm = 40 + p
            tvBig.text = bpm.toString()
        }
        override fun onStartTrackingTouch(v: android.widget.SeekBar?) {}
        override fun onStopTrackingTouch(v: android.widget.SeekBar?) {}
    })
    val barGroup = ChipGroup(ctx).apply { isSingleSelection = true }
    listOf(2, 3, 4, 6).forEach { n ->
        barGroup.addView(
            Chip(ctx).apply {
                text = "$n 拍"
                isCheckable = true
                isChecked = n == beatsPerBar
                setOnClickListener {
                    beatsPerBar = n
                    beatInBar = 0
                    tvSub.text = "BPM · $n 拍"
                }
            }
        )
    }
    val btn = MaterialButton(ctx).apply { text = "开始" }
    val tvHint = hintText(ctx, "首拍加重音并亮一行 LED，其余拍轻音；速度 40–240 BPM。练琴、跑步、实验节拍都能用。")
    root.addView(tvBig)
    root.addView(tvSub)
    root.addView(
        seek,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 10) }
    )
    root.addView(
        barGroup,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 6) }
    )
    root.addView(
        btn,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 12) }
    )
    root.addView(tvHint)

    val tick = M3Kit.Ticker({ 60_000L / bpm }, mainHandler) {
        if (!running) {
            false
        } else {
            val accent = beatInBar == 0
            sendLine(Protocol.tone(if (accent) 1320 else 880, if (accent) 60 else 40))
            // 拍点 LED：当前拍所在列全亮一行
            val m = Array(5) { BooleanArray(5) }
            for (y in 0..4) m[y][beatInBar.coerceIn(0, 4)] = true
            sendLine(Protocol.show(m))
            beatInBar = (beatInBar + 1) % beatsPerBar
            true
        }
    }
    btn.setOnClickListener {
        running = !running
        btn.text = if (running) "停止" else "开始"
        if (running) {
            beatInBar = 0
            tick.start()
        } else {
            tick.stop()
            sendLine(Protocol.clear())
        }
    }
    showFeatureDialog(root, "节拍器", onDismiss = { tick.stop() })
}

// ---------- 2. 门铃 ----------

private val DOORBELL_DINGDONG = listOf(659 to 350, 523 to 650)
private val DOORBELL_ELECTRONIC = listOf(988 to 120, 988 to 120, 784 to 400)

internal fun MainActivity.openDoorbell() {
    if (!requireConnected()) return
    val ctx = this
    var seq: List<Pair<Int, Int>> = DOORBELL_DINGDONG
    var times = 1
    var playing = false

    val root = M3Kit.dialogRoot(ctx)
    val typeGroup = ChipGroup(ctx).apply { isSingleSelection = true }
    listOf("经典叮咚" to DOORBELL_DINGDONG, "电子音" to DOORBELL_ELECTRONIC).forEach { (label, s) ->
        typeGroup.addView(
            Chip(ctx).apply {
                text = label
                isCheckable = true
                isChecked = s === DOORBELL_DINGDONG
                setOnClickListener { seq = s }
            }
        )
    }
    val timesGroup = ChipGroup(ctx).apply { isSingleSelection = true }
    listOf(1, 3, 5).forEach { n ->
        timesGroup.addView(
            Chip(ctx).apply {
                text = "$n 声"
                isCheckable = true
                isChecked = n == times
                setOnClickListener { times = n }
            }
        )
    }
    val btn = MaterialButton(ctx).apply { text = "按铃" }
    val tvHint = hintText(ctx, "两种门铃音色，可连响多声；门口贴个码，来人扫码就能「云按铃」。")
    root.addView(typeGroup)
    root.addView(
        timesGroup,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 6) }
    )
    root.addView(
        btn,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 12) }
    )
    root.addView(tvHint)

    btn.setOnClickListener {
        if (playing) return@setOnClickListener
        playing = true
        M3Kit.ToneSequence(
            notes = seq.map { (f, ms) -> f to ms.toLong() },
            shouldContinue = { !isFinishing && !isDestroyed },
            onTone = { f, ms -> runOnUiThread { sendLine(Protocol.tone(f, ms)) } },
            onFinished = { playing = false },
            times = times,
            gapMs = 500L
        ).start()
    }
    showFeatureDialog(root, "门铃")
}

// ---------- 3. 警笛 ----------

internal fun MainActivity.openSiren() {
    if (!requireConnected()) return
    val ctx = this
    var running = false
    var stepMs = 60L

    val root = M3Kit.dialogRoot(ctx)
    val tvBig = bigReadout(ctx, "600 Hz")
    val seek = android.widget.SeekBar(ctx).apply {
        max = 100
        progress = 50
    }
    seek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(v: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
            stepMs = 120L - p // 20–120ms 每步，越大扫得越快
        }
        override fun onStartTrackingTouch(v: android.widget.SeekBar?) {}
        override fun onStopTrackingTouch(v: android.widget.SeekBar?) {}
    })
    val btn = MaterialButton(ctx).apply { text = "开始扫频" }
    val tvHint = hintText(ctx, "频率在 600–1600Hz 间往复扫频，速度可调；LED 随频率高低闪烁。演示、逗猫、招 attention 均可。")
    root.addView(tvBig)
    root.addView(
        seek,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 10) }
    )
    root.addView(
        btn,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 12) }
    )
    root.addView(tvHint)

    btn.setOnClickListener {
        if (running) {
            running = false
            btn.text = "开始扫频"
            sendLine(Protocol.clear())
            return@setOnClickListener
        }
        running = true
        btn.text = "停止"
        Thread {
            try {
                var f = 600
                var up = true
                var on = false
                while (running && !isFinishing && !isDestroyed) {
                    runOnUiThread {
                        tvBig.text = "$f Hz"
                        sendLine(Protocol.tone(f, stepMs.toInt() + 20))
                        val m = Array(5) { BooleanArray(5) { on } }
                        sendLine(Protocol.show(m))
                    }
                    on = !on
                    if (up) {
                        f += 50
                        if (f >= 1600) up = false
                    } else {
                        f -= 50
                        if (f <= 600) up = true
                    }
                    Thread.sleep(stepMs)
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                running = false
                runOnUiThread {
                    btn.text = "开始扫频"
                    sendLine(Protocol.clear())
                }
            }
        }.start()
    }
    showFeatureDialog(root, "警笛", onDismiss = { running = false })
}

// ---------- 4. 音效板 ----------

private val SOUNDBOARD: List<Triple<String, String, List<Pair<Int, Int>>>> = listOf(
    Triple("欢呼", "上行音阶", listOf(523 to 90, 659 to 90, 784 to 90, 1046 to 220)),
    Triple("错误", "下行双音", listOf(392 to 180, 311 to 260)),
    Triple("胜利", "短胜利曲", listOf(659 to 100, 784 to 100, 988 to 100, 1318 to 260)),
    Triple("失败", "叹气音", listOf(440 to 160, 349 to 160, 262 to 320)),
    Triple("提示", "清脆叮", listOf(1046 to 120, 1568 to 200)),
    Triple("警报", "急促双音", listOf(880 to 120, 660 to 120, 880 to 120, 660 to 120, 880 to 160)),
    Triple("投币", "游戏币", listOf(1318 to 70, 1760 to 280)),
    Triple("出发", "三连升", listOf(523 to 90, 659 to 90, 784 to 220)),
)

internal fun MainActivity.openSoundboard() {
    if (!requireConnected()) return
    val ctx = this

    val root = M3Kit.dialogRoot(ctx)
    val grid = android.widget.GridLayout(ctx).apply { columnCount = 2 }
    var playing = false
    SOUNDBOARD.forEach { (name, desc, seq) ->
        val card = com.google.android.material.card.MaterialCardView(ctx).apply {
            radius = M3Kit.dp(ctx, 14).toFloat()
            cardElevation = 0f
            strokeColor = ctx.themeColor(MR.attr.colorOutlineVariant)
            strokeWidth = M3Kit.dp(ctx, 1)
            setCardBackgroundColor(ctx.themeColor(MR.attr.colorSurface))
        }
        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(M3Kit.dp(ctx, 14), M3Kit.dp(ctx, 12), M3Kit.dp(ctx, 14), M3Kit.dp(ctx, 12))
        }
        inner.addView(labelText(ctx, name, bold = true))
        inner.addView(
            TextView(ctx).apply {
                text = desc
                textSize = 11f
                setTextColor(ctx.themeColor(MR.attr.colorOnSurfaceVariant))
            }
        )
        card.addView(inner)
        card.setOnClickListener {
            if (playing) return@setOnClickListener
            playing = true
            M3Kit.ToneSequence(
                notes = seq.map { (f, ms) -> f to ms.toLong() },
                shouldContinue = { !isFinishing && !isDestroyed },
                onTone = { f, ms -> runOnUiThread { sendLine(Protocol.tone(f, ms)) } },
                onFinished = { playing = false }
            ).start()
        }
        grid.addView(
            card,
            android.widget.GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                setMargins(M3Kit.dp(ctx, 5), M3Kit.dp(ctx, 5), M3Kit.dp(ctx, 5), M3Kit.dp(ctx, 5))
            }
        )
    }
    val tvHint = hintText(ctx, "课堂互动、展台演示、小剧场音效一键播放；蜂鸣器单声道，听个响气氛拉满。")
    root.addView(grid)
    root.addView(tvHint)
    showFeatureDialog(root, "音效板")
}

// ---------- 5. 谱曲台 ----------

internal fun MainActivity.openTunePad() {
    if (!requireConnected()) return
    val ctx = this
    var playing = false

    val root = M3Kit.dialogRoot(ctx)
    val et = EditText(ctx).apply {
        hint = "音符串：c d e f g a b，#升 b降，数字八度，:时值"
        inputType = android.text.InputType.TYPE_CLASS_TEXT
        setText("c d e f g a b c5")
        setTextSize(14f)
        typeface = android.graphics.Typeface.MONOSPACE
        minLines = 2
        gravity = Gravity.TOP
    }
    val exampleGroup = ChipGroup(ctx).apply { isSingleSelection = true }
    val tvInfo = labelText(ctx, "").apply { gravity = Gravity.CENTER }
    fun refreshInfo() {
        val notes = TuneParser.parse(et.text.toString())
        val totalMs = notes.sumOf { TuneParser.durMs(it) } + (notes.size - 1).coerceAtLeast(0) * 10
        tvInfo.text = "${notes.size} 个音 · 约 ${totalMs / 1000}.${totalMs / 100 % 10} 秒"
    }
    TuneParser.EXAMPLES.forEach { (name, tune) ->
        exampleGroup.addView(
            Chip(ctx).apply {
                text = name
                isCheckable = true
                setOnClickListener {
                    et.setText(tune)
                    refreshInfo()
                }
            }
        )
    }
    et.doAfterTextChanged { refreshInfo() }
    refreshInfo()
    val btnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    val btnPlay = MaterialButton(ctx).apply { text = "播放" }
    val btnStop = MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply {
        text = "停止"
        isEnabled = false
    }
    btnRow.addView(btnPlay)
    btnRow.addView(
        btnStop,
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = M3Kit.dp(ctx, 10) }
    )
    val tvHint = hintText(ctx, "语法与 micro:bit music 模块一致：字母+升/降号+八度+:时值，八度与时值曲内延续；r 是休止。写好直接放给固件听。")
    root.addView(et)
    root.addView(
        exampleGroup,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 6) }
    )
    root.addView(
        tvInfo,
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

    btnPlay.setOnClickListener {
        val notes = TuneParser.parse(et.text.toString()).filter { it.freqHz > 0 || it.rest }
        if (notes.isEmpty()) {
            featureSnack("没解析出音符，检查一下写法")
            return@setOnClickListener
        }
        if (playing) return@setOnClickListener
        playing = true
        btnStop.isEnabled = true
        M3Kit.ToneSequence(
            notes = notes.map { n ->
                if (n.rest) 0 to TuneParser.durMs(n).toLong()
                else n.freqHz.toInt() to TuneParser.durMs(n).toLong()
            },
            shouldContinue = { playing && !isFinishing && !isDestroyed },
            onTone = { f, ms -> runOnUiThread { sendLine(Protocol.tone(f, ms)) } },
            onFinished = {
                playing = false
                runOnUiThread { btnStop.isEnabled = false }
            },
            tailMs = 10L
        ).start()
    }
    btnStop.setOnClickListener { playing = false }
    showFeatureDialog(root, "谱曲台", onDismiss = { playing = false })
}

// ---------- 6. 旋律串烧 ----------

internal fun MainActivity.openMelodyMix() {
    if (!requireConnected()) return
    val ctx = this
    val picked = LinkedHashSet<String>() // 保序去重
    var playing = false

    val root = M3Kit.dialogRoot(ctx)
    val scroll = android.widget.ScrollView(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        )
    }
    val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    scroll.addView(list)
    val tvInfo = labelText(ctx, "已选 0 首").apply { gravity = Gravity.CENTER }
    fun refreshInfo() {
        tvInfo.text = if (picked.isEmpty()) "已选 0 首" else "已选 ${picked.size} 首：${picked.joinToString(" → ")}"
    }
    Melodies.NAMES.forEach { name ->
        val chip = Chip(ctx).apply {
            text = name
            isCheckable = true
            setOnClickListener {
                if (isChecked) picked.add(name) else picked.remove(name)
                refreshInfo()
            }
        }
        list.addView(
            chip,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = M3Kit.dp(ctx, 2) }
        )
    }
    val btnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    val btnPlay = MaterialButton(ctx).apply { text = "开始串烧" }
    val btnShuffle = MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply { text = "随机排序" }
    val btnStop = MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply {
        text = "停止"
        isEnabled = false
    }
    btnRow.addView(btnPlay)
    btnRow.addView(
        btnShuffle,
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = M3Kit.dp(ctx, 8) }
    )
    btnRow.addView(
        btnStop,
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = M3Kit.dp(ctx, 8) }
    )
    val tvHint = hintText(ctx, "多选内置旋律排队播放，曲间自动加提示音；时长按各曲真实音符时长计算，点「停止」随时中断。")
    root.addView(tvInfo)
    root.addView(scroll)
    root.addView(
        btnRow,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 10) }
    )
    root.addView(tvHint)

    fun play(names: List<String>) {
        playing = true
        btnStop.isEnabled = true
        Thread {
            try {
                names.forEach { name ->
                    if (!playing || isFinishing || isDestroyed) return@forEach
                    runOnUiThread { sendLine(Protocol.melody(name)) }
                    val notes = Melodies.parse(Melodies.tune(name) ?: "")
                    val dur = notes.sumOf { it.durMs }.toLong() + 400
                    Thread.sleep(dur)
                    if (!playing) return@forEach
                    runOnUiThread { sendLine(Protocol.tone(1046, 80)) }
                    Thread.sleep(300)
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                playing = false
                runOnUiThread { btnStop.isEnabled = false }
            }
        }.start()
    }

    btnPlay.setOnClickListener {
        if (picked.isEmpty()) {
            featureSnack("先勾选要串烧的旋律")
            return@setOnClickListener
        }
        if (playing) return@setOnClickListener
        play(picked.toList())
    }
    btnShuffle.setOnClickListener {
        val names = Melodies.NAMES.shuffled(Random.Default)
        picked.clear()
        picked.addAll(names.take(5))
        // 同步 chips 勾选态
        for (i in 0 until list.childCount) {
            val chip = list.getChildAt(i) as? Chip
            chip?.isChecked = chip?.text?.toString()?.let { picked.contains(it) } == true
        }
        refreshInfo()
        featureSnack("已随机抽 5 首，点「开始串烧」")
    }
    btnStop.setOnClickListener { playing = false }
    showFeatureDialog(root, "旋律串烧", onDismiss = { playing = false })
}


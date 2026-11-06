/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.R as MR
import com.google.android.material.button.MaterialButton
import com.lingyi.mbos.MainActivity
import com.lingyi.mbos.util.Prefs
import com.lingyi.mbos.util.themeColor
import java.util.Locale
import kotlin.random.Random

/*
 * 控制台游戏与互动（v3.3.0-preview）：骰子 / 硬币 / 反应测试 / 猜数字 / 石头剪刀布。
 * 交互全部落在既有协议上：SHOW 出画面、TONE 出声、READ 轮询读按键与手势。
 */


// ---------- 6. 骰子 ----------

internal fun MainActivity.openDice() {
    if (!requireConnected()) return
    val ctx = this
    val counts = IntArray(7)
    val history = ArrayDeque<String>(10)
    var rolling = false
    val shakeGate = M3Kit.ShakeGate()

    val root = M3Kit.dialogRoot(ctx)
    val tvFace = bigReadout(ctx, "?")
    val btnRoll = MaterialButton(ctx).apply { text = "掷骰子" }
    val tvLog = TextView(ctx).apply {
        textSize = 13f
        typeface = android.graphics.Typeface.MONOSPACE
        setPadding(0, M3Kit.dp(ctx, 12), 0, 0)
        text = "点击「掷骰子」，或直接摇一摇开发板"
    }
    root.addView(tvFace)
    root.addView(btnRoll)
    root.addView(tvLog)
    root.addView(
        hintText(ctx, "开发板 LED 会滚动随机点数后停在结果上，并附中奖音效；" +
            "这里保留最近 10 次记录与各面次数统计。")
    )

    fun roll() {
        if (rolling) return
        rolling = true
        val face = DicePatterns.randomFace()
        val rnd = Random.Default
        Thread {
            try {
                repeat(10) { i ->
                    if (isFinishing || isDestroyed) return@Thread
                    val f = rnd.nextInt(6) + 1
                    runOnUiThread { sendLine(Protocol.show(DicePatterns.matrix(f))) }
                    Thread.sleep(50L + i * 10L)
                }
                if (isFinishing || isDestroyed) return@Thread
                runOnUiThread {
                    sendLine(Protocol.show(DicePatterns.matrix(face)))
                    sendLine(Protocol.tone(if (face >= 5) 988 else 659, 240))
                    rolling = false
                    counts[face]++
                    history.addLast("第 ${counts.sum()} 次：$face 点")
                    while (history.size > 10) history.removeFirst()
                    tvFace.text = face.toString()
                    val tally = (1..6).joinToString("  ") { "$it:${counts[it]}" }
                    tvLog.text = history.joinToString("\n") + "\n—— 各面次数：$tally"
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }.start()
    }

    btnRoll.setOnClickListener { roll() }

    val poller = SensorPoller(400)
    poller.onSnapshot = { s ->
        if (s.g == "shake" && shakeGate.allow(1500)) {
            runOnUiThread { roll() }
        }
    }
    showFeatureDialog(root, "骰子", onShow = { poller.start() }, onDismiss = { poller.stop() })
}

// ---------- 7. 硬币抛掷 ----------

internal fun MainActivity.openCoin() {
    if (!requireConnected()) return
    val ctx = this
    var heads = 0
    var tails = 0
    val history = ArrayDeque<String>(10)
    var flipping = false

    val root = M3Kit.dialogRoot(ctx)
    val tvResult = bigReadout(ctx, "？")
    val btnFlip = MaterialButton(ctx).apply { text = "抛硬币" }
    val tvLog = TextView(ctx).apply {
        textSize = 13f
        typeface = android.graphics.Typeface.MONOSPACE
        setPadding(0, M3Kit.dp(ctx, 12), 0, 0)
        text = "点击「抛硬币」看正反面"
    }
    root.addView(tvResult)
    root.addView(btnFlip)
    root.addView(tvLog)
    root.addView(
        hintText(ctx, "LED 先快速翻转两幅图案，再停在结果：圆环为「正」，实心为「反」。" +
            "适合坐庄、抽签、决定谁先来。")
    )

    fun flip() {
        if (flipping) return
        flipping = true
        val isHeads = Random.nextBoolean()
        Thread {
            try {
                repeat(8) { i ->
                    if (isFinishing || isDestroyed) return@Thread
                    val rows = if (i % 2 == 0) Patterns.COIN_HEADS else Patterns.COIN_TAILS
                    runOnUiThread { sendLine(Protocol.show(rowsToMatrix(rows))) }
                    Thread.sleep(70L + i * 20L)
                }
                if (isFinishing || isDestroyed) return@Thread
                runOnUiThread {
                    sendLine(Protocol.show(rowsToMatrix(if (isHeads) Patterns.COIN_HEADS else Patterns.COIN_TAILS)))
                    sendLine(Protocol.tone(784, 200))
                    flipping = false
                    if (isHeads) heads++ else tails++
                    history.addLast("第 ${heads + tails} 次：${if (isHeads) "正" else "反"}")
                    while (history.size > 10) history.removeFirst()
                    tvResult.text = if (isHeads) "正" else "反"
                    tvLog.text = history.joinToString("\n") + "\n—— 正 $heads 次 / 反 $tails 次"
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }.start()
    }

    btnFlip.setOnClickListener { flip() }
    showFeatureDialog(root, "硬币抛掷")
}

// ---------- 8. 反应速度测试 ----------

internal fun MainActivity.openReactionGame() {
    if (!requireConnected()) return
    val ctx = this
    var waiting = false
    var go = false
    var goAt = 0L
    var foul = false

    val root = M3Kit.dialogRoot(ctx)
    val tvState = bigReadout(ctx, "准备")
    val tvBest = labelText(ctx, "最佳成绩：--").apply { gravity = android.view.Gravity.CENTER }
    val tvHint = hintText(
        ctx,
        "点「开始测试」后，开发板会在 1.5–4 秒内的随机时刻全亮点阵并发声，" +
            "看到或听到的瞬间立刻按 A 或 B。抢跑（信号前按键）算犯规。"
    )
    val btnStart = MaterialButton(ctx).apply { text = "开始测试" }
    root.addView(tvState)
    root.addView(tvBest)
    root.addView(btnStart)
    root.addView(tvHint)

    fun refreshBest() {
        val best = Prefs.getReactionBestMs(ctx)
        tvBest.text = if (best > 0) "最佳成绩：$best ms" else "最佳成绩：--"
    }
    refreshBest()

    btnStart.setOnClickListener {
        waiting = true
        go = false
        foul = false
        tvState.text = "准备…"
        sendLine(Protocol.clear())
        val delay = 1500L + Random.nextInt(2500)
        mainHandler.postDelay(delay) {
            if (!waiting) return@postDelay
            go = true
            goAt = System.currentTimeMillis()
            sendLine(Protocol.show(fullOn()))
            sendLine(Protocol.tone(1200, 250))
            tvState.text = "就是现在！"
        }
    }

    val poller = SensorPoller(80)
    poller.onSnapshot = { s ->
        if (waiting) {
            if (!go) {
                if (s.a || s.b) {
                    waiting = false
                    sendLine(Protocol.clear())
                    tvState.text = "犯规！抢跑了"
                    tvState.setTextColor(ctx.themeColor(MR.attr.colorError))
                }
            } else if (s.a || s.b) {
                waiting = false
                val ms = System.currentTimeMillis() - goAt
                Prefs.setReactionBestMs(ctx, ms.toInt())
                refreshBest()
                tvState.text = "$ms ms"
                val grade = when {
                    ms < 250 -> "超人反应！"
                    ms < 350 -> "非常快"
                    ms < 500 -> "不错"
                    ms < 700 -> "再练练"
                    else -> "放松再试"
                }
                tvState.setTextColor(ctx.themeColor(MR.attr.colorPrimary))
                sendLine(Protocol.show(DicePatterns.matrix(1)))
                sendLine(Protocol.tone(880, 150))
                featureSnack("$grade（$ms ms）")
            }
        }
    }
    showFeatureDialog(root, "反应速度测试", onShow = { poller.start() }, onDismiss = { poller.stop() })
}

// ---------- 9. 猜数字 ----------

internal fun MainActivity.openGuessGame() {
    if (!requireConnected()) return
    val ctx = this
    val game = GuessGame()
    var guess = game.start()
    var acted = false // 防止一次按键被多次消费

    val root = M3Kit.dialogRoot(ctx)
    val tvGuess = bigReadout(ctx, guess.toString())
    val tvInfo = labelText(ctx, "还剩 100 个可能 · 第 0 次猜测").apply {
        gravity = android.view.Gravity.CENTER
    }
    val btnRow1 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    val btnHigher = MaterialButton(ctx).apply { text = "我想的更大（A）" }
    val btnLower = MaterialButton(ctx).apply { text = "我想的更小（B）" }
    btnRow1.addView(btnHigher)
    btnRow1.addView(
        btnLower,
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = M3Kit.dp(ctx, 10)
        }
    )
    val btnRow2 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    val btnCorrect = MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply { text = "猜对了" }
    val btnRestart = MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply { text = "重新开局" }
    btnRow2.addView(btnCorrect)
    btnRow2.addView(
        btnRestart,
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = M3Kit.dp(ctx, 10)
        }
    )
    val tvHint = hintText(
        ctx,
        "心里想一个 1–100 的整数（不要说出口）。App 每次猜一个数并显示在开发板 LED 上，" +
            "你在板子上按 A=「我想的更大」、B=「我想的更小」，猜中时一起按下 A+B。" +
            "熟练的话 7 次以内必中。"
    )
    root.addView(tvGuess)
    root.addView(tvInfo)
    root.addView(
        btnRow1,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 12) }
    )
    root.addView(
        btnRow2,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 10) }
    )
    root.addView(tvHint)

    fun showGuess(g: Int?) {
        if (g == null) {
            tvGuess.text = "？"
            tvInfo.text = "按键有矛盾，点数对不上了——点「重新开局」"
            return
        }
        guess = g
        tvGuess.text = g.toString()
        tvInfo.text = "还剩 ${game.remaining()} 个可能 · 第 ${game.rounds} 次猜测"
        sendLine(Protocol.number(g))
        sendLine(Protocol.tone(660, 80))
    }

    fun restart() {
        acted = false
        showGuess(game.start())
        featureSnack("新的一局，想一个 1–100 的数")
    }

    btnHigher.setOnClickListener { acted = true; showGuess(game.answer(true)) }
    btnLower.setOnClickListener { acted = true; showGuess(game.answer(false)) }
    btnCorrect.setOnClickListener {
        val rounds = game.correct()
        tvInfo.text = "用了 $rounds 次猜中！点「重新开局」再来"
        sendLine(Protocol.show(rowsToMatrix(listOf("00000", "01010", "00000", "10001", "01110"))))
        sendLine(Protocol.melody("ODE"))
    }
    btnRestart.setOnClickListener { restart() }

    val poller = SensorPoller(300)
    poller.onSnapshot = { s ->
        val both = s.a && s.b
        if (!s.a && !s.b) {
            acted = false // 松手后允许下一次按键
        } else if (!acted) {
            acted = true
            when {
                both -> btnCorrect.performClick()
                s.a -> btnHigher.performClick()
                else -> btnLower.performClick()
            }
        }
    }
    showFeatureDialog(root, "猜数字", onShow = { poller.start() }, onDismiss = { poller.stop() })
}

// ---------- 10. 石头剪刀布 ----------

internal fun MainActivity.openRps() {
    if (!requireConnected()) return
    val ctx = this
    var youWins = 0
    var devWins = 0
    var matchOver = false
    val shakeGate = M3Kit.ShakeGate()

    val root = M3Kit.dialogRoot(ctx)
    val tvScore = bigReadout(ctx, "0 : 0")
    val tvDevice = labelText(ctx, "开发板：等待出拳").apply {
        gravity = android.view.Gravity.CENTER
    }
    val tvResult = labelText(ctx, "三局两胜").apply {
        gravity = android.view.Gravity.CENTER
        textSize = 16f
    }
    val btnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    val tvHint = hintText(
        ctx,
        "在手机上选拳，开发板同时随机出拳并显示在 LED 上；" +
            "也可以直接摇一摇开发板，双方随机出拳。先胜 2 局者赢得本场。"
    )
    root.addView(tvScore)
    root.addView(tvDevice)
    root.addView(tvResult)
    root.addView(btnRow)
    root.addView(tvHint)

    fun refreshScore() {
        tvScore.text = "$youWins : $devWins"
    }

    fun play(you: Move) {
        if (matchOver) return
        val dev = RpsJudge.randomMove()
        sendLine(Protocol.show(rowsToMatrix(dev.rows)))
        tvDevice.text = "开发板：${dev.label}"
        when (RpsJudge.judge(you, dev)) {
            RpsJudge.WIN -> {
                youWins++
                sendLine(Protocol.tone(988, 200))
                tvResult.text = "你出${you.label}，赢了这一局！"
            }
            RpsJudge.LOSE -> {
                devWins++
                sendLine(Protocol.tone(330, 250))
                tvResult.text = "你出${you.label}，输了这一局"
            }
            else -> {
                sendLine(Protocol.tone(587, 150))
                tvResult.text = "都是${you.label}，平局"
            }
        }
        refreshScore()
        if (youWins == 2 || devWins == 2) {
            matchOver = true
            tvResult.text = if (youWins == 2) "本场你赢了！点「再来一局」" else "本场开发板赢了！点「再来一局」"
        }
    }

    Move.entries.forEach { move ->
        btnRow.addView(
            MaterialButton(ctx).apply {
                text = move.label
                setOnClickListener { play(move) }
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (move != Move.ROCK) marginStart = M3Kit.dp(ctx, 8)
            }
        )
    }
    // 「再来一局」按钮（平局后也需要）
    val btnAgain = MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply {
        text = "再来一局"
        setOnClickListener {
            youWins = 0
            devWins = 0
            matchOver = false
            refreshScore()
            tvResult.text = "三局两胜"
            tvDevice.text = "开发板：等待出拳"
        }
    }
    root.addView(
        btnAgain,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 10) }
    )

    val poller = SensorPoller(500)
    poller.onSnapshot = { s ->
        if (s.g == "shake" && !matchOver && shakeGate.allow(2000)) {
            runOnUiThread { play(RpsJudge.randomMove()) }
        }
    }
    showFeatureDialog(root, "石头剪刀布", onShow = { poller.start() }, onDismiss = { poller.stop() })
}

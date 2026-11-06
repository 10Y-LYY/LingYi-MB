/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

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
 * 控制台新功能 · 游戏与互动（预览版 4.0.0-preview 新增 7 项）：
 * 狂点挑战 / 忍耐挑战 / 击鼓传花 / 轮盘赌 / 真心话大冒险 / 知识问答 / 21 点。
 * 输入全部来自 A / B 键、摇一摇与手势，输出落在 SHOW / NUMBER / TONE。
 */

// ---------- 1. 狂点挑战 ----------

internal fun MainActivity.openTapCount() {
    if (!requireConnected()) return
    val ctx = this
    var running = false
    var score = 0
    var prevA = false
    var prevB = false
    var remainSec = 10

    val root = M3Kit.dialogRoot(ctx)
    val tvBig = bigReadout(ctx, "0")
    val tvSub = labelText(ctx, "10 秒内狂按 A(+1) / B(+2)").apply { gravity = Gravity.CENTER }
    val tvBest = labelText(ctx, "最佳纪录：--").apply { gravity = Gravity.CENTER }
    val btn = MaterialButton(ctx).apply { text = "开始挑战" }
    val tvHint = hintText(ctx, "拼手速也拼策略：B 键分值高但要按稳；LED 进度条随分数增长。")
    root.addView(tvBig)
    root.addView(tvSub)
    root.addView(tvBest)
    root.addView(
        btn,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 12) }
    )
    root.addView(tvHint)

    fun refreshBest() {
        val best = Prefs.getTapBest(ctx)
        tvBest.text = if (best > 0) "最佳纪录：$best 分" else "最佳纪录：--"
    }
    refreshBest()

    val tick = M3Kit.Ticker({ 1000L }, mainHandler) {
        if (!running) {
            false
        } else {
            remainSec--
            tvSub.text = "剩余 $remainSec 秒 · 当前 $score 分"
            if (remainSec <= 0) {
                running = false
                btn.text = "开始挑战"
                val best = Prefs.getTapBest(ctx)
                if (score > best) {
                    Prefs.setTapBest(ctx, score)
                    refreshBest()
                    featureSnack("新纪录 $score 分！")
                }
                sendLine(Protocol.show(rowsToMatrix(listOf("11111", "10001", "10001", "10001", "11111"))))
                sendLine(Protocol.tone(988, 300))
                false
            } else {
                true
            }
        }
    }

    val poller = SensorPoller(60)
    poller.onSnapshot = { s ->
        if (running) {
            var gain = 0
            if (s.a && !prevA) gain += 1
            if (s.b && !prevB) gain += 2
            prevA = s.a
            prevB = s.b
            if (gain > 0) {
                score += gain
                runOnUiThread {
                    tvBig.text = score.toString()
                    // 进度：每 6 分亮一格
                    val lit = (score / 6).coerceIn(0, 5)
                    val m = Array(5) { y -> BooleanArray(5) { x -> y >= 5 - lit } }
                    sendLine(Protocol.show(m))
                }
            }
        } else {
            prevA = s.a
            prevB = s.b
        }
    }
    btn.setOnClickListener {
        running = true
        score = 0
        remainSec = 10
        prevA = false
        prevB = false
        tvBig.text = "0"
        tvSub.text = "剩余 10 秒 · 当前 0 分"
        btn.text = "挑战中…"
        sendLine(Protocol.clear())
        tick.start()
    }
    showFeatureDialog(
        root, "狂点挑战",
        onShow = { poller.start() },
        onDismiss = {
            poller.stop()
            tick.stop()
        }
    )
}

// ---------- 2. 忍耐挑战 ----------

internal fun MainActivity.openHoldGame() {
    if (!requireConnected()) return
    val ctx = this
    var targetSec = 5
    var holding = false
    var holdStart = 0L
    var prevA = false
    var rounds = 0

    val root = M3Kit.dialogRoot(ctx)
    val targetGroup = ChipGroup(ctx).apply { isSingleSelection = true }
    listOf(3, 5, 8).forEach { n ->
        targetGroup.addView(
            Chip(ctx).apply {
                text = "目标 ${n}.0 秒"
                isCheckable = true
                isChecked = n == targetSec
                setOnClickListener { targetSec = n }
            }
        )
    }
    val tvBig = bigReadout(ctx, "准备")
    val tvSub = labelText(ctx, "按住 A 开始，心里默数到目标秒数松手").apply { gravity = Gravity.CENTER }
    val tvHint = hintText(ctx, "考验时间感：误差 0.3 秒内大师级；松手瞬间显示误差与评级。")
    root.addView(targetGroup)
    root.addView(tvBig)
    root.addView(tvSub)
    root.addView(tvHint)

    fun progressLeds(fraction: Float) {
        val lit = (fraction * 25).toInt().coerceIn(0, 25)
        val m = Array(5) { BooleanArray(5) }
        var left = lit
        for (y in 0..4) for (x in 0..4) {
            if (left > 0) {
                m[y][x] = true
                left--
            }
        }
        sendLine(Protocol.show(m))
    }

    val tick = M3Kit.Ticker({ 50L }, mainHandler) {
        if (!holding) {
            false
        } else {
            val held = (System.currentTimeMillis() - holdStart) / 1000.0
            tvBig.text = String.format(Locale.US, "%.2f s", held)
            progressLeds((held / targetSec).toFloat())
            true
        }
    }

    val poller = SensorPoller(50)
    poller.onSnapshot = { s ->
        if (s.a && !prevA) {
            holding = true
            holdStart = System.currentTimeMillis()
            runOnUiThread {
                tvBig.text = "0.00 s"
                tvSub.text = "别松手…"
                tick.start()
            }
        } else if (!s.a && prevA && holding) {
            holding = false
            tick.stop()
            val held = (System.currentTimeMillis() - holdStart) / 1000.0
            val err = Math.abs(held - targetSec)
            rounds++
            val grade = when {
                err < 0.3 -> "大师级时间感！"
                err < 0.8 -> "相当准"
                err < 1.5 -> "还不错"
                else -> "差得有点多"
            }
            runOnUiThread {
                tvBig.text = String.format(Locale.US, "%.2f s", held)
                tvSub.text = "误差 ${String.format(Locale.US, "%.2f", err)}s · $grade（第 $rounds 轮）"
                sendLine(Protocol.number((held * 100).toInt()))
                sendLine(Protocol.tone(if (err < 0.3) 1568 else 660, 250))
            }
        }
        prevA = s.a
    }
    showFeatureDialog(
        root, "忍耐挑战",
        onShow = { poller.start() },
        onDismiss = {
            poller.stop()
            tick.stop()
        }
    )
}

// ---------- 3. 击鼓传花 ----------

internal fun MainActivity.openHotPotato() {
    if (!requireConnected()) return
    val ctx = this
    var armed = false
    var boomAt = 0L

    val root = M3Kit.dialogRoot(ctx)
    val etPenalty = EditText(ctx).apply {
        hint = "惩罚内容，如 表演一个节目"
        inputType = android.text.InputType.TYPE_CLASS_TEXT
        setText("表演一个节目")
        textSize = 14f
    }
    val tvBig = bigReadout(ctx, "待机")
    val tvSub = labelText(ctx, "传给下一个人时按一下 A").apply { gravity = Gravity.CENTER }
    val btn = MaterialButton(ctx).apply { text = "开始（随机 4–15 秒）" }
    val tvHint = hintText(ctx, "音乐停的那一刻「炸」：全亮 + 长鸣 + 滚动惩罚内容；每次间隔随机，无法预判。")
    root.addView(etPenalty)
    root.addView(tvBig)
    root.addView(tvSub)
    root.addView(
        btn,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 12) }
    )
    root.addView(tvHint)

    val tick = M3Kit.Ticker({ 100L }, mainHandler) {
        if (!armed) {
            false
        } else {
            val left = boomAt - System.currentTimeMillis()
            if (left <= 0) {
                armed = false
                tvBig.text = "炸！"
                tvBig.setTextColor(ctx.themeColor(MR.attr.colorError))
                sendLine(Protocol.show(fullOn()))
                sendLine(Protocol.tone(220, 900))
                sendLine(Protocol.scroll("BOOM · ${etPenalty.text}"))
                featureSnack("惩罚：${etPenalty.text}")
                false
            } else {
                tvBig.text = String.format(Locale.US, "%.1f s", left / 1000.0)
                true
            }
        }
    }

    var prevA = false
    val poller = SensorPoller(120)
    poller.onSnapshot = { s ->
        if (armed) {
            // 传花：按 A 不会重置炸弹（否则无限玩），只做提示
            if (s.a && !prevA) {
                runOnUiThread {
                    sendLine(Protocol.tone(880, 60))
                    tvSub.text = "传！"
                }
            }
            prevA = s.a
        }
    }
    btn.setOnClickListener {
        if (armed) return@setOnClickListener
        armed = true
        boomAt = System.currentTimeMillis() + (4000L + Random.nextInt(11000))
        tvBig.setTextColor(ctx.themeColor(MR.attr.colorPrimary))
        tvBig.text = "…"
        tvSub.text = "快传给下一个人！"
        sendLine(Protocol.clear())
        tick.start()
    }
    showFeatureDialog(
        root, "击鼓传花",
        onShow = { poller.start() },
        onDismiss = {
            poller.stop()
            tick.stop()
        }
    )
}

// ---------- 4. 轮盘赌 ----------

internal fun MainActivity.openRoulette() {
    if (!requireConnected()) return
    val ctx = this
    var players = 4
    var bullets = 1
    var chamber = 6
    var loaded = ArrayList<Int>() // 弹膛序号（已装弹位置）
    var round = 0
    var over = false
    var prevA = false

    val root = M3Kit.dialogRoot(ctx)
    val playerGroup = ChipGroup(ctx).apply { isSingleSelection = true }
    listOf(2, 3, 4, 6).forEach { n ->
        playerGroup.addView(
            Chip(ctx).apply {
                text = "$n 人"
                isCheckable = true
                isChecked = n == players
                setOnClickListener { players = n }
            }
        )
    }
    val bulletGroup = ChipGroup(ctx).apply { isSingleSelection = true }
    listOf(1, 2).forEach { n ->
        bulletGroup.addView(
            Chip(ctx).apply {
                text = "$n 弹"
                isCheckable = true
                isChecked = n == bullets
                setOnClickListener { bullets = n }
            }
        )
    }
    val tvBig = bigReadout(ctx, "轮盘")
    val tvSub = labelText(ctx, "轮流按 A 扣扳机").apply { gravity = Gravity.CENTER }
    val btnNew = MaterialButton(ctx).apply { text = "新一局" }
    val tvHint = hintText(ctx, "6 弹膛随机装弹，谁按到空弹谁出局；LED 骷髅 + 哀嚎音。胆量游戏，适可而止。")
    root.addView(playerGroup)
    root.addView(
        bulletGroup,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 6) }
    )
    root.addView(tvBig)
    root.addView(tvSub)
    root.addView(
        btnNew,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 12) }
    )
    root.addView(tvHint)

    fun newGame() {
        chamber = 6
        loaded = ArrayList((0 until chamber).shuffled(Random.Default).take(bullets))
        round = 0
        over = false
        tvBig.text = "装弹完毕"
        tvSub.text = "共 $bullets 弹 · ${players} 人轮流按 A"
        sendLine(Protocol.clear())
    }
    newGame()

    val poller = SensorPoller(100)
    poller.onSnapshot = { s ->
        if (!over && s.a && !prevA) {
            val pos = round % chamber
            round++
            val hit = loaded.contains(pos)
            runOnUiThread {
                if (hit) {
                    over = true
                    val loser = (round - 1) % players + 1
                    tvBig.text = "第 $loser 人中弹！"
                    sendLine(Protocol.show(rowsToMatrix(Patterns.CROSS)))
                    sendLine(Protocol.tone(150, 700))
                    featureSnack("本局结束，点「新一局」重来")
                } else {
                    tvBig.text = "空弹，下一位"
                    sendLine(Protocol.number(round))
                    sendLine(Protocol.tone(1046, 80))
                }
            }
        }
        prevA = s.a
    }
    btnNew.setOnClickListener { newGame() }
    showFeatureDialog(root, "轮盘赌", onShow = { poller.start() }, onDismiss = { poller.stop() })
}

// ---------- 5. 真心话大冒险 ----------

private val TRUTHS = listOf(
    "说一件最近最开心的小事",
    "手机相册里倒数第三张照片是什么？讲讲它",
    "你最尴尬的一次经历是什么？",
    "说一个从没告诉过爸妈的小秘密",
    "最近一次哭是因为什么？",
    "如果可以和任何人换一天人生，你选谁？",
    "说三个自己的缺点",
    "你收到过最难忘的礼物是什么？",
    "做过最后悔的一件事？",
    "现在最想见的一个人是谁？为什么？",
    "说一个你坚持最久的习惯",
    "童年最喜欢的动画片是哪部？",
    "最近一次撒谎是什么时候？",
    "你觉得自己最像哪种动物？",
    "说三个在座的人的优点",
    "如果能回到一年前，你会做什么？",
    "你最害怕失去什么？",
    "说一件你一直想做却没做的事",
    "在座的人里你最佩服谁？",
    "用三个词形容此刻的心情",
)

private val DARES = listOf(
    "模仿一种动物叫声，让大家猜",
    "用夸张的语调朗读一段手机里的文字",
    "原地做五个深蹲",
    "学一段广告台词",
    "给第五个联系人发一个表情，不许解释",
    "用脑袋顶着一个杯子坚持 30 秒",
    "唱两句儿歌",
    "模仿在座任意一个人说话",
    "闭眼转身三圈再走直线",
    "用三种语气说「我爱你」",
    "表演一个「惊讶」的慢动作",
    "双手背后单脚站立 20 秒",
    "讲一个冷笑话，没人笑就再讲一个",
    "用方言数一到十",
    "对着窗户大声说三遍「我今天真好看」",
    "即兴跳一段舞（10 秒）",
    "用左手写自己的名字",
    "模仿一种乐器的声音",
    "说一段rap，押韵就行",
    "做一张「囧」脸自拍",
)

internal fun MainActivity.openTruthDare() {
    val ctx = this
    var lastText = "摇一摇或点按钮抽题"

    val root = M3Kit.dialogRoot(ctx)
    val tvBig = bigReadout(ctx, "？")
    val tvCard = TextView(ctx).apply {
        text = lastText
        textSize = 16f
        gravity = Gravity.CENTER
        setPadding(M3Kit.dp(ctx, 12), M3Kit.dp(ctx, 16), M3Kit.dp(ctx, 12), M3Kit.dp(ctx, 16))
    }
    val btnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    val btnDraw = MaterialButton(ctx).apply { text = "抽一题" }
    val btnAgain = MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply { text = "换一题" }
    btnRow.addView(btnDraw)
    btnRow.addView(
        btnAgain,
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = M3Kit.dp(ctx, 10) }
    )
    val tvHint = hintText(ctx, "摇一摇开发板也能抽题；20 条真心话 + 20 条大冒险，聚会破冰利器。")
    root.addView(tvBig)
    root.addView(
        tvCard,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 10) }
    )
    root.addView(
        btnRow,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 12) }
    )
    root.addView(tvHint)

    var index = 0
    fun draw() {
        val truth = Random.nextBoolean()
        index++
        val text = if (truth) {
            TRUTHS[Random.nextInt(TRUTHS.size)]
        } else {
            DARES[Random.nextInt(DARES.size)]
        }
        lastText = (if (truth) "真心话 · " else "大冒险 · ") + text
        tvBig.text = "第 $index 题"
        tvCard.text = lastText
        sendLine(Protocol.number(index))
        sendLine(Protocol.tone(if (truth) 784 else 988, 150))
    }

    btnDraw.setOnClickListener { draw() }
    btnAgain.setOnClickListener { draw() }

    val shakeGate = M3Kit.ShakeGate()
    val poller = SensorPoller(400)
    poller.onSnapshot = { s ->
        if (s.g == "shake" && shakeGate.allow(2000)) {
            runOnUiThread { draw() }
        }
    }
    showFeatureDialog(root, "真心话大冒险", onShow = { poller.start() }, onDismiss = { poller.stop() })
}

// ---------- 6. 知识问答 ----------

internal fun MainActivity.openQuizShow() {
    if (!requireConnected()) return
    val ctx = this
    var question = QuizBank.random()
    var score = 0
    var streak = 0
    var answered = false
    var prevA = false
    var prevB = false
    val asked = HashSet<Int>()

    val root = M3Kit.dialogRoot(ctx)
    val tvScore = labelText(ctx, "得分 0 · 连对 0").apply { gravity = Gravity.CENTER }
    val tvQ = TextView(ctx).apply {
        textSize = 15f
        setPadding(M3Kit.dp(ctx, 8), M3Kit.dp(ctx, 12), M3Kit.dp(ctx, 8), M3Kit.dp(ctx, 8))
    }
    val tvExplain = TextView(ctx).apply {
        textSize = 12f
        setTextColor(ctx.themeColor(MR.attr.colorOnSurfaceVariant))
        setPadding(M3Kit.dp(ctx, 8), M3Kit.dp(ctx, 4), M3Kit.dp(ctx, 8), 0)
    }
    val btnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    val btnTrue = MaterialButton(ctx).apply { text = "A：说法正确" }
    val btnFalse = MaterialButton(ctx).apply { text = "B：说法错误" }
    btnRow.addView(btnTrue)
    btnRow.addView(
        btnFalse,
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = M3Kit.dp(ctx, 10) }
    )
    val tvHint = hintText(ctx, "题库 20 题，答对连对有加分手感；开发板实时显示得分。摇一摇换下一题也行。")
    root.addView(tvScore)
    root.addView(tvQ)
    root.addView(tvExplain)
    root.addView(
        btnRow,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 10) }
    )
    root.addView(tvHint)

    fun render() {
        tvQ.text = question.text
        tvExplain.text = if (answered) "解析：${question.explain}" else "（在开发板上按 A = 正确 / B = 错误）"
        tvScore.text = "得分 $score · 连对 $streak"
    }
    render()

    fun next() {
        question = QuizBank.random(asked)
        asked.add(question.id)
        answered = false
        render()
        sendLine(Protocol.show(rowsToMatrix(listOf("00000", "00100", "00100", "00000", "00100"))))
    }

    fun answer(yes: Boolean) {
        if (answered) return
        answered = true
        val correct = yes == question.answer
        if (correct) {
            score += 10 + streak * 2
            streak++
            sendLine(Protocol.tone(1046, 150))
        } else {
            streak = 0
            sendLine(Protocol.tone(220, 200))
        }
        sendLine(Protocol.number(score))
        render()
        mainHandler.postDelayed({ next() }, 1500)
    }

    btnTrue.setOnClickListener { answer(true) }
    btnFalse.setOnClickListener { answer(false) }

    val shakeGate = M3Kit.ShakeGate()
    val poller = SensorPoller(150)
    poller.onSnapshot = { s ->
        if (!answered) {
            if (s.a && !prevA) answer(true)
            if (s.b && !prevB) answer(false)
        }
        prevA = s.a
        prevB = s.b
        if (s.g == "shake" && shakeGate.allow(2000)) {
            runOnUiThread { next() }
        }
    }
    showFeatureDialog(root, "知识问答", onShow = { poller.start() }, onDismiss = { poller.stop() })
}

// ---------- 7. 21 点 ----------

internal fun MainActivity.openBlackjack() {
    if (!requireConnected()) return
    val ctx = this
    var deck = BlackjackEngine.newDeck()
    var mine = ArrayList<BlackjackEngine.Card>()
    var dealer = ArrayList<BlackjackEngine.Card>()
    var playing = false
    var prevA = false
    var prevB = false

    val root = M3Kit.dialogRoot(ctx)
    val tvScore = labelText(ctx, "").apply { gravity = Gravity.CENTER }
    val tvMine = bigReadout(ctx, "--")
    val tvDealer = labelText(ctx, "庄家：--").apply { gravity = Gravity.CENTER }
    val tvInfo = labelText(ctx, "点「发牌」开始；游戏中 A = 要牌，B = 停牌").apply { gravity = Gravity.CENTER }
    val btn = MaterialButton(ctx).apply { text = "发牌" }
    val tvHint = hintText(ctx, "标准 21 点规则：A 记 1 或 11、J/Q/K 记 10、庄家 17 以上停；点数会滚动到开发板。")
    root.addView(tvScore)
    root.addView(tvMine)
    root.addView(tvDealer)
    root.addView(tvInfo)
    root.addView(
        btn,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 12) }
    )
    root.addView(tvHint)

    fun handText(cards: List<BlackjackEngine.Card>): String =
        cards.joinToString(" ") { "${it.rank}${it.suit}" }

    fun render() {
        tvMine.text = "${BlackjackEngine.bestTotal(mine)} 点"
        tvDealer.text = "庄家：${handText(dealer)}（${BlackjackEngine.bestTotal(dealer)} 点）"
    }

    fun stand() {
        if (!playing) return
        playing = false
        btn.text = "发牌"
        while (BlackjackEngine.dealerShouldHit(dealer) && deck.isNotEmpty()) {
            dealer.add(deck.removeAt(0))
        }
        render()
        val mineTotal = BlackjackEngine.bestTotal(mine)
        val dealerTotal = BlackjackEngine.bestTotal(dealer)
        val outcome = when {
            BlackjackEngine.isBust(mine) -> BlackjackEngine.Outcome.LOSE
            BlackjackEngine.isBlackjack(mine) && !BlackjackEngine.isBlackjack(dealer) ->
                BlackjackEngine.Outcome.BLACKJACK
            BlackjackEngine.isBust(dealer) -> BlackjackEngine.Outcome.WIN
            mineTotal > dealerTotal -> BlackjackEngine.Outcome.WIN
            mineTotal == dealerTotal -> BlackjackEngine.Outcome.PUSH
            else -> BlackjackEngine.Outcome.LOSE
        }
        tvInfo.text = "${outcome.label}（你 $mineTotal : 庄家 $dealerTotal）"
        when (outcome) {
            BlackjackEngine.Outcome.WIN, BlackjackEngine.Outcome.BLACKJACK -> {
                sendLine(Protocol.melody("ODE"))
                sendLine(Protocol.show(rowsToMatrix(listOf("00000", "01010", "00000", "00100", "01110"))))
            }
            BlackjackEngine.Outcome.PUSH -> sendLine(Protocol.tone(587, 200))
            BlackjackEngine.Outcome.LOSE -> sendLine(Protocol.tone(196, 500))
        }
    }

    fun deal() {
        deck = BlackjackEngine.newDeck()
        mine = ArrayList()
        dealer = ArrayList()
        mine.add(deck.removeAt(0))
        dealer.add(deck.removeAt(0))
        mine.add(deck.removeAt(0))
        dealer.add(deck.removeAt(0))
        playing = true
        btn.text = "游戏中…"
        tvInfo.text = "A = 要牌 · B = 停牌"
        sendLine(Protocol.clear())
        render()
        sendLine(Protocol.number(BlackjackEngine.bestTotal(mine)))
        if (BlackjackEngine.isBlackjack(mine)) {
            stand()
        }
    }

    fun hit() {
        if (!playing) return
        mine.add(deck.removeAt(0))
        render()
        sendLine(Protocol.number(BlackjackEngine.bestTotal(mine)))
        if (BlackjackEngine.isBust(mine)) {
            playing = false
            btn.text = "发牌"
            tvInfo.text = "爆牌了！点「发牌」再来"
            sendLine(Protocol.show(fullOn()))
            sendLine(Protocol.tone(160, 600))
        }
    }

    btn.setOnClickListener { if (!playing) deal() }

    val poller = SensorPoller(120)
    poller.onSnapshot = { s ->
        if (playing) {
            if (s.a && !prevA) runOnUiThread { hit() }
            if (s.b && !prevB) runOnUiThread { stand() }
        }
        prevA = s.a
        prevB = s.b
    }
    showFeatureDialog(root, "21 点", onShow = { poller.start() }, onDismiss = { poller.stop() })
}

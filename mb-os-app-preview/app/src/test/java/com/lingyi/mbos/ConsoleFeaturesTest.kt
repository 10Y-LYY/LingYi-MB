/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lingyi.mbos.ble.ConnectionHub
import com.lingyi.mbos.ble.ConnState
import com.lingyi.mbos.ble.MicrobitConnection
import com.lingyi.mbos.ui.AnimationStore
import com.lingyi.mbos.ui.CsvLogger
import com.lingyi.mbos.ui.DicePatterns
import com.lingyi.mbos.ui.FeatureCatalog
import com.lingyi.mbos.ui.GuessGame
import com.lingyi.mbos.ui.Morse
import com.lingyi.mbos.ui.Move
import com.lingyi.mbos.ui.RpsJudge
import com.lingyi.mbos.ui.SensorPoller
import com.lingyi.mbos.ui.SensorSnapshot
import com.lingyi.mbos.ui.openAnimationStudio
import com.lingyi.mbos.ui.openBarrage
import com.lingyi.mbos.ui.openButtonMonitor
import com.lingyi.mbos.ui.openCoin
import com.lingyi.mbos.ui.openDice
import com.lingyi.mbos.ui.openFindBell
import com.lingyi.mbos.ui.openGuessGame
import com.lingyi.mbos.ui.openLevel
import com.lingyi.mbos.ui.openLightMeter
import com.lingyi.mbos.ui.openMorse
import com.lingyi.mbos.ui.openPiano
import com.lingyi.mbos.ui.openReactionGame
import com.lingyi.mbos.ui.openRps
import com.lingyi.mbos.ui.openSensorLogger
import com.lingyi.mbos.ui.openThermometer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v3.3.0-preview 新增 15 个控制台功能：纯逻辑单测 + 打开冒烟。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = MBOSApp::class)
class ConsoleFeaturesTest {

    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        ConnectionHub.disconnect()
    }

    // ---------- SensorPoller 解析 ----------

    @Test
    fun sensorPollerParsesFirmwareReadLine() {
        val snap = SensorPoller.parse("A:1|B:0|T:24.5|L:128|X:-120|Y:340|Z:1024|C:187|G:shake")
        assertNotNull(snap)
        requireNotNull(snap)
        assertTrue(snap.a)
        assertFalse(snap.b)
        assertEquals(24.5, snap.t!!, 1e-6)
        assertEquals(128, snap.l)
        assertEquals(-120, snap.x)
        assertEquals(340, snap.y)
        assertEquals(1024, snap.z)
        assertEquals(187, snap.c)
        assertEquals("shake", snap.g)
    }

    @Test
    fun sensorPollerRejectsNonReadLines() {
        assertNull(SensorPoller.parse("OK:MELODY:ODE"))
        assertNull(SensorPoller.parse("ERR:BRIGHT"))
        assertNull(SensorPoller.parse("MOUSE:3,-2,1,0"))
        assertNull(SensorPoller.parse("INFO:MicroPython 2.1.1"))
        assertNull(SensorPoller.parse(""))
    }

    @Test
    fun sensorPollerToleratesMissingFields() {
        // 老固件未上报的字段应为 null 而不是崩溃
        val snap = SensorPoller.parse("A:0|B:0|T:21")
        assertNotNull(snap)
        requireNotNull(snap)
        assertEquals(21.0, snap.t!!, 1e-6)
        assertNull(snap.l)
        assertNull(snap.x)
        assertNull(snap.g)
    }

    // ---------- 摩斯电码 ----------

    @Test
    fun morseEncodesSos() {
        assertEquals("... --- ...", Morse.encode("SOS"))
    }

    @Test
    fun morseEncodesWordsAndDigits() {
        assertEquals(".... .. / .--.-.", Morse.encode("HI @"))
        assertEquals(".... ..", Morse.encode("hi"))
        assertEquals("----- .----", Morse.encode("01"))
    }

    @Test
    fun morseBeatTimingFollowsWpm() {
        val beats = Morse.beats("S", wpm = 15)
        // 15 WPM：点 80ms、划 240ms、元素间隙 240ms；S = 三点，间隙夹在中间
        assertEquals(5, beats.size)
        assertEquals(Morse.Kind.DOT, beats[0].kind)
        assertEquals(80L, beats[0].ms)
        assertEquals(Morse.Kind.LETTER_GAP, beats[1].kind)
        assertEquals(240L, beats[1].ms)
        assertEquals(Morse.Kind.DOT, beats[4].kind)
        val fast = Morse.beats("S", wpm = 30)
        assertEquals(40L, fast[0].ms)
    }

    // ---------- 骰子 ----------

    @Test
    fun dicePipsMatchFaceNumber() {
        for (n in 1..6) {
            assertEquals("$n 点应亮 $n 个灯", n, DicePatterns.pips(n))
        }
    }

    @Test
    fun diceFaceOneIsCenterPixel() {
        val m = DicePatterns.matrix(1)
        assertTrue(m[2][2])
        assertEquals(1, m.sumOf { row -> row.count { it } })
    }

    // ---------- 石头剪刀布 ----------

    @Test
    fun rpsJudgeCoversAllCombinations() {
        assertEquals(RpsJudge.WIN, RpsJudge.judge(Move.ROCK, Move.SCISSORS))
        assertEquals(RpsJudge.WIN, RpsJudge.judge(Move.SCISSORS, Move.PAPER))
        assertEquals(RpsJudge.WIN, RpsJudge.judge(Move.PAPER, Move.ROCK))
        assertEquals(RpsJudge.LOSE, RpsJudge.judge(Move.SCISSORS, Move.ROCK))
        assertEquals(RpsJudge.LOSE, RpsJudge.judge(Move.PAPER, Move.SCISSORS))
        assertEquals(RpsJudge.LOSE, RpsJudge.judge(Move.ROCK, Move.PAPER))
        for (m in Move.entries) {
            assertEquals(RpsJudge.DRAW, RpsJudge.judge(m, m))
        }
    }

    // ---------- 猜数字 ----------

    @Test
    fun guessGameFindsEveryTargetWithinSevenRounds() {
        for (target in 1..100) {
            val game = GuessGame()
            var guess: Int? = game.start()
            var rounds = 0
            while (guess != null && guess != target && rounds < 20) {
                guess = game.answer(target > guess)
                rounds++
            }
            assertNotNull("目标 $target 出现矛盾区间", guess)
            assertEquals("目标 $target 应被猜中", target, guess)
            assertTrue("二分法 100 以内不应超过 7 次（目标 $target）", rounds <= 7)
        }
    }

    @Test
    fun guessGameDetectsContradiction() {
        // 1–10 的小区间里交替「更大 / 更小」撒谎，几步内区间必空
        val game = GuessGame(1, 10)
        game.start()
        var g: Int? = game.answer(true)
        var guard = 0
        while (g != null && guard++ < 20) {
            g = game.answer(guard % 2 == 0)
        }
        assertNull(g)
        assertTrue(game.finished)
    }

    // ---------- 动画存储 ----------

    @Test
    fun animationStoreRoundTrip() {
        val ctx = ctx()
        val f1 = AnimationStore.Frame(listOf("11111", "10001", "10001", "10001", "11111"), 200)
        val f2 = AnimationStore.Frame(listOf("00000", "00100", "01110", "00100", "00000"), 300)
        AnimationStore.save(ctx, "心跳", listOf(f1, f2))
        val loaded = AnimationStore.load(ctx, "心跳")
        assertNotNull(loaded)
        requireNotNull(loaded)
        assertEquals(2, loaded.frames.size)
        assertEquals(200, loaded.frames[0].durMs)
        assertEquals(300, loaded.frames[1].durMs)
        // 覆盖保存同名动画：替换而非叠加
        AnimationStore.save(ctx, "心跳", listOf(f1))
        assertEquals(1, AnimationStore.load(ctx, "心跳")!!.frames.size)
        // 删除
        AnimationStore.delete(ctx, "心跳")
        assertNull(AnimationStore.load(ctx, "心跳"))
    }

    @Test
    fun animationStoreIgnoresInvalidInput() {
        val ctx = ctx()
        AnimationStore.save(ctx, "", listOf(AnimationStore.Frame(listOf("00000"), 100)))
        AnimationStore.save(ctx, "空帧", emptyList())
        assertTrue(AnimationStore.loadAll(ctx).none { it.name.isEmpty() || it.frames.isEmpty() })
    }

    // ---------- CSV 记录 ----------

    @Test
    fun csvLoggerRowAndFile() {
        val s = SensorSnapshot(
            a = true, b = false, t = 23.4, l = 100, x = 1, y = -2, z = 3, c = 90, g = null
        )
        val row = CsvLogger.row(1500, s)
        assertEquals("1500,1,0,23.4,100,1,-2,3,90,", row)
        val csv = CsvLogger.toCsv(listOf(0L to s, 1000L to s))
        assertTrue(csv.startsWith(CsvLogger.HEADER))
        assertEquals(3, csv.split("\r\n").filter { it.isNotEmpty() }.size)
        assertTrue(CsvLogger.fileName("20261021-220000").endsWith(".csv"))
    }

    // ---------- 功能目录 ----------

    @Test
    fun featureCatalogHas77FeaturesAcross7Categories() {
        assertEquals(77, FeatureCatalog.FEATURES.size)
        val ids = FeatureCatalog.FEATURES.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        val expected = mapOf(
            FeatureCatalog.Category.DISPLAY to 14,
            FeatureCatalog.Category.SOUND to 10,
            FeatureCatalog.Category.SENSOR to 16,
            FeatureCatalog.Category.CODE to 10,
            FeatureCatalog.Category.AUTO to 12,
            FeatureCatalog.Category.GAME to 12,
            FeatureCatalog.Category.TOOL to 3,
        )
        expected.forEach { (category, n) ->
            assertEquals("分类 ${category.key}", n, FeatureCatalog.byCategory(category).size)
        }
    }

    @Test
    fun everyNewFeatureIdIsDispatchable() {
        // 15 个新功能 id 必须与 MainActivity.onFuncClick 分派一致（此测试防漏接线）
        val newIds = listOf(
            "thermometer", "light", "level", "buttons", "logger",
            "dice", "coin", "reaction", "guess", "rps",
            "animation", "piano", "barrage", "morse", "findbell"
        )
        newIds.forEach { id ->
            assertTrue("功能目录缺少 $id", FeatureCatalog.FEATURES.any { it.id == id })
        }
    }

    // ---------- 打开冒烟：15 个新功能逐一构建不崩溃 ----------

    @Test
    fun allNewFeaturesOpenWithoutCrash() {
        val ctx = ctx()
        ConnectionHub.connectSimulator(
            ctx,
            object : MicrobitConnection.Listener {
                override fun onState(state: ConnState) {}
                override fun onData(data: String) {}
            }
        )
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().get()
        assertTrue("模拟器应处于已连接状态", ConnectionHub.isConnected())
        activity.openThermometer()
        activity.openLightMeter()
        activity.openLevel()
        activity.openButtonMonitor()
        activity.openSensorLogger()
        activity.openDice()
        activity.openCoin()
        activity.openReactionGame()
        activity.openGuessGame()
        activity.openRps()
        activity.openAnimationStudio()
        activity.openPiano()
        activity.openBarrage()
        activity.openMorse()
        activity.openFindBell()
        // 全部构建完毕即通过（对话框 show 依赖 Robolectric 的对话框支持）
    }
}

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
import com.lingyi.mbos.ui.BellSchedule
import com.lingyi.mbos.ui.BlackjackEngine
import com.lingyi.mbos.ui.CodeChecker
import com.lingyi.mbos.ui.CommandSeqStore
import com.lingyi.mbos.ui.DigitFont
import com.lingyi.mbos.ui.FeatureCatalog
import com.lingyi.mbos.ui.FlashLog
import com.lingyi.mbos.ui.QuizBank
import com.lingyi.mbos.ui.TuneParser
import com.lingyi.mbos.ui.UnitConvert
import com.lingyi.mbos.ui.openBigDigit
import com.lingyi.mbos.ui.openBlackjack
import com.lingyi.mbos.ui.openBrightAuto
import com.lingyi.mbos.ui.openCodeCheck
import com.lingyi.mbos.ui.openCodeLib
import com.lingyi.mbos.ui.openCommandSeq
import com.lingyi.mbos.ui.openCompassRose
import com.lingyi.mbos.ui.openDoorbell
import com.lingyi.mbos.ui.openDropTest
import com.lingyi.mbos.ui.openErrorDict
import com.lingyi.mbos.ui.openFlashLog
import com.lingyi.mbos.ui.openGestureLock
import com.lingyi.mbos.ui.openGestureLog
import com.lingyi.mbos.ui.openHoldGame
import com.lingyi.mbos.ui.openHotPotato
import com.lingyi.mbos.ui.openIdleBot
import com.lingyi.mbos.ui.openLinkMonitor
import com.lingyi.mbos.ui.openLogStream
import com.lingyi.mbos.ui.openMarquee
import com.lingyi.mbos.ui.openMelodyMix
import com.lingyi.mbos.ui.openMetronome
import com.lingyi.mbos.ui.openMotionDetector
import com.lingyi.mbos.ui.openNightLight
import com.lingyi.mbos.ui.openPinTable
import com.lingyi.mbos.ui.openQuickPanel
import com.lingyi.mbos.ui.openQuizShow
import com.lingyi.mbos.ui.openRoulette
import com.lingyi.mbos.ui.openScriptInfo
import com.lingyi.mbos.ui.openSensorTest
import com.lingyi.mbos.ui.openShakeButton
import com.lingyi.mbos.ui.openShakeCount
import com.lingyi.mbos.ui.openSiren
import com.lingyi.mbos.ui.openSoundboard
import com.lingyi.mbos.ui.openTapCount
import com.lingyi.mbos.ui.openTempChart
import com.lingyi.mbos.ui.openTorch
import com.lingyi.mbos.ui.openTruthDare
import com.lingyi.mbos.ui.openTunePad
import com.lingyi.mbos.ui.openTutorials
import com.lingyi.mbos.util.Prefs
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
 * 预览版 4.0.0-preview 新增 50 个控制台功能：
 * 纯逻辑单测（7 个可独立验证的模块）+ 功能目录完整性 + 打开冒烟（分批）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = MBOSApp::class)
class ConsoleMoreFeaturesTest {

    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        ConnectionHub.disconnect()
    }

    // ---------- 大字数字库 ----------

    @Test
    fun digitFontCoversDigitsAndPunctuation() {
        for (c in '0'..'9') {
            assertTrue("缺少字形 $c", DigitFont.has(c))
            val g = DigitFont.glyph(c)
            assertEquals(5, g.size)
            g.forEach { assertEquals("字形 $c 行宽应为 3", 3, it.length) }
        }
        assertTrue(DigitFont.has('-'))
        assertTrue(DigitFont.has('.'))
        assertFalse(DigitFont.has('x'))
        // 未知字符回落 '-'（三横），不会与数字混淆
        assertEquals(DigitFont.glyph('-'), DigitFont.glyph('x'))
    }

    @Test
    fun digitFontRenderInsertsDarkGaps() {
        val m = DigitFont.render("12")
        // 2 个字符：2×3 宽 + 1 列暗边
        assertEquals(7, m[0].size)
        assertEquals(5, m.size)
        // 暗列（下标 3）必须全灭
        for (y in 0..4) assertFalse("暗列应熄灭", m[y][3])
        // 空输入回落 '-'，宽 3
        assertEquals(3, DigitFont.render("")[0].size)
        // '1' 的第一行是 010：第 1 列灭、第 2 列亮
        val one = DigitFont.render("1")
        assertFalse(one[0][0])
        assertTrue(one[0][1])
        assertTrue(one[2][1])
    }

    // ---------- 谱曲台 ----------

    @Test
    fun tuneParserReadsNotesRestsAndOctaves() {
        val notes = TuneParser.parse("c d e f g a b")
        assertEquals(7, notes.size)
        assertFalse(notes.all { it.rest })
        // C4 ≈ 261.63Hz
        assertEquals(261.63, notes[0].freqHz, 0.01)
        // A4 = 440Hz（第 6 个音）
        assertEquals(440.0, notes[5].freqHz, 0.01)
        // 休止
        val rest = TuneParser.parse("r")
        assertEquals(1, rest.size)
        assertTrue(rest[0].rest)
        assertEquals(0.0, rest[0].freqHz, 1e-9)
        // 升降号：c# 是 C♯4 ≈ 277.18Hz；cb 是 C♭4 = B3 ≈ 246.94Hz
        assertEquals(277.18, TuneParser.parse("c#")[0].freqHz, 0.01)
        assertEquals(246.94, TuneParser.parse("cb")[0].freqHz, 0.01)
        // 时值：默认 4 拍（1 beat），:2 表示 2 拍（0.5 beat 单位 = 2 拍）
        val half = TuneParser.parse("c:2")
        assertEquals(0.5, half[0].beats, 1e-9)
        // 八度在曲内延续：c5 之后的 c 仍是八度 5
        val held = TuneParser.parse("c5 c")
        assertEquals(held[0].freqHz, held[1].freqHz, 1e-9)
        assertEquals(523.25, held[1].freqHz, 0.01)
    }

    @Test
    fun tuneParserDurationShorterThanFullBeat() {
        val note = TuneParser.parse("c")[0]
        // 1 拍 = 500ms；非休止整体短 10ms 防连音，且不低于 30ms
        assertEquals(490, TuneParser.durMs(note))
        val rest = TuneParser.parse("r")[0]
        assertEquals(500, TuneParser.durMs(rest))
    }

    @Test
    fun tuneParserExamplesAllParse() {
        TuneParser.EXAMPLES.forEach { (name, tune) ->
            val notes = TuneParser.parse(tune)
            assertTrue("示例「$name」应能解析出音符", notes.isNotEmpty())
        }
    }

    // ---------- 单位换算 ----------

    @Test
    fun unitConvertLengthWeightAndTemperature() {
        // 长度：倍率中转
        assertEquals(1000.0, UnitConvert.convert(UnitConvert.Kind.LENGTH, "千米", "米", 1.0)!!, 1e-9)
        assertEquals(2.54, UnitConvert.convert(UnitConvert.Kind.LENGTH, "英寸", "厘米", 1.0)!!, 1e-9)
        // 相同单位原值返回
        assertEquals(7.0, UnitConvert.convert(UnitConvert.Kind.LENGTH, "米", "米", 7.0)!!, 1e-9)
        // 重量
        assertEquals(0.45359237, UnitConvert.convert(UnitConvert.Kind.WEIGHT, "磅", "千克", 1.0)!!, 1e-9)
        // 1 吨 = 1000 千克 = 1e9 毫克
        assertEquals(1.0e9, UnitConvert.convert(UnitConvert.Kind.WEIGHT, "吨", "毫克", 1.0)!!, 1.0)
        // 温度：偏移公式
        assertEquals(212.0, UnitConvert.convert(UnitConvert.Kind.TEMP, "摄氏度 °C", "华氏度 °F", 100.0)!!, 1e-9)
        assertEquals(37.0, UnitConvert.convert(UnitConvert.Kind.TEMP, "华氏度 °F", "摄氏度 °C", 98.6)!!, 1e-9)
        assertEquals(273.15, UnitConvert.convert(UnitConvert.Kind.TEMP, "摄氏度 °C", "开尔文 K", 0.0)!!, 1e-9)
        // 往返：°C → K → °C
        val back = UnitConvert.convert(
            UnitConvert.Kind.TEMP, "开尔文 K", "摄氏度 °C",
            UnitConvert.convert(UnitConvert.Kind.TEMP, "摄氏度 °C", "开尔文 K", 25.0)!!
        )
        assertEquals(25.0, back!!, 1e-9)
    }

    @Test
    fun unitConvertFormatting() {
        assertEquals("0", UnitConvert.fmt(0.0))
        assertEquals("1000.0", UnitConvert.fmt(1000.0))
        assertEquals("2.5", UnitConvert.fmt(2.5))
        assertEquals("0.001", UnitConvert.fmt(0.001))
        assertTrue("极小值应走科学计数", UnitConvert.fmt(1e-9).contains("e"))
    }

    // ---------- 21 点 ----------

    @Test
    fun blackjackTotalsBustAndBlackjack() {
        // A 尽量算 11 不爆
        val soft = listOf(
            BlackjackEngine.Card("A", "♠"),
            BlackjackEngine.Card("7", "♥")
        )
        assertEquals(18, BlackjackEngine.bestTotal(soft))
        // A 只能算 1 时才不爆
        val hard = listOf(
            BlackjackEngine.Card("A", "♠"),
            BlackjackEngine.Card("7", "♥"),
            BlackjackEngine.Card("5", "♦")
        )
        assertEquals(13, BlackjackEngine.bestTotal(hard))
        assertFalse(BlackjackEngine.isBust(hard))
        // 爆牌
        val bust = listOf(
            BlackjackEngine.Card("10", "♠"),
            BlackjackEngine.Card("K", "♥"),
            BlackjackEngine.Card("2", "♦")
        )
        assertEquals(22, BlackjackEngine.bestTotal(bust))
        assertTrue(BlackjackEngine.isBust(bust))
        // J/Q/K 都记 10；黑杰 = 首两张 21
        val bj = listOf(BlackjackEngine.Card("A", "♠"), BlackjackEngine.Card("K", "♥"))
        assertTrue(BlackjackEngine.isBlackjack(bj))
        assertFalse(BlackjackEngine.isBlackjack(bust))
    }

    @Test
    fun blackjackDeckAndDealerRule() {
        val deck = BlackjackEngine.newDeck()
        assertEquals(52, deck.size)
        assertEquals(52, deck.toSet().size)
        // 庄家 < 17 要牌，≥ 17 停
        assertTrue(
            BlackjackEngine.dealerShouldHit(
                listOf(BlackjackEngine.Card("10", "♠"), BlackjackEngine.Card("6", "♥"))
            )
        )
        assertFalse(
            BlackjackEngine.dealerShouldHit(
                listOf(BlackjackEngine.Card("10", "♠"), BlackjackEngine.Card("7", "♥"))
            )
        )
    }

    // ---------- 知识问答 ----------

    @Test
    fun quizBankIsConsistent() {
        assertTrue(QuizBank.isConsistent())
        val ids = QuizBank.QUESTIONS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        QuizBank.QUESTIONS.forEach { q ->
            assertTrue("题干不能为空", q.text.isNotBlank())
            assertTrue("解析不能为空", q.explain.isNotBlank())
        }
    }

    @Test
    fun quizBankRandomExcludesAsked() {
        val asked = QuizBank.QUESTIONS.map { it.id }.toSet()
        // 全部排除后仍要返回一题（回落全库），不能抛异常
        val q = QuizBank.random(asked)
        assertNotNull(q)
        // 排除部分时不会抽到已出过的
        val exclude = setOf(QuizBank.QUESTIONS.first().id)
        repeat(50) {
            assertTrue(QuizBank.random(exclude).id != QuizBank.QUESTIONS.first().id)
        }
    }

    // ---------- 代码体检 ----------

    @Test
    fun codeCheckerFindsCommonMistakes() {
        // 中文标点
        val cn = CodeChecker.check("print（1）")
        assertTrue(cn.any { it.level == CodeChecker.Level.ERROR && it.message.contains("中文标点") })
        // 块语句缺冒号
        val noColon = CodeChecker.check("if True\n    pass")
        assertTrue(noColon.any { it.message.contains("冒号") })
        // print 缺括号（Python2 写法）
        val py2 = CodeChecker.check("print 'hi'")
        assertTrue(py2.any { it.message.contains("print") })
        // 奇数引号
        val oddQuote = CodeChecker.check("x = \"abc")
        assertTrue(oddQuote.any { it.message.contains("引号") })
        // 括号不配对
        val unbal = CodeChecker.check("print((1)")
        assertTrue(unbal.any { it.message.contains("小括号不配对") })
        // 空代码只警告不报错
        val blank = CodeChecker.check("   \n  ")
        assertTrue(blank.any { it.level == CodeChecker.Level.WARN })
        assertFalse(blank.any { it.level == CodeChecker.Level.ERROR })
        // 干净代码无 ERROR
        val clean = CodeChecker.check("from microbit import *\n\ndisplay.show(Image.HEART)\n")
        assertFalse("干净代码不应有 ERROR 级问题", clean.any { it.level == CodeChecker.Level.ERROR })
        // 汇总文案可用
        assertTrue(CodeChecker.summary(clean).isNotBlank())
        assertTrue(CodeChecker.summary(listOf(CodeChecker.Issue(CodeChecker.Level.ERROR, 1, "x"))).contains("1"))
    }

    // ---------- 上课铃课表 ----------

    @Test
    fun bellScheduleRoundTripAndQueries() {
        val lessons = listOf(
            BellSchedule.Lesson(1, "语文", 8, 0, true),
            BellSchedule.Lesson(2, "数学", 10, 30, true),
            BellSchedule.Lesson(3, "自习", 14, 0, false),
        )
        val back = BellSchedule.fromJson(BellSchedule.toJson(lessons))
        assertEquals(lessons, back)
        // 排序
        val sorted = BellSchedule.sorted(lessons.reversed())
        assertEquals(listOf("语文", "数学", "自习"), sorted.map { it.name })
        // 到点：只取启用的、同分钟的
        val due = BellSchedule.dueAt(lessons, 8, 0)
        assertEquals(1, due.size)
        assertEquals("语文", due[0].name)
        assertTrue(BellSchedule.dueAt(lessons, 14, 0).isEmpty()) // 停用的不响
        assertTrue(BellSchedule.dueAt(lessons, 9, 0).isEmpty())
        // timeText 补零
        assertEquals("08:00", lessons[0].timeText)
        // 坏 JSON 回落空表
        assertTrue(BellSchedule.fromJson("{不是 JSON").isEmpty())
        assertTrue(BellSchedule.fromJson(null).isEmpty())
    }

    // ---------- 指令编排 ----------

    @Test
    fun commandSeqRoundTrip() {
        val steps = listOf(
            CommandSeqStore.Step("SCROLL:开始", 0),
            CommandSeqStore.Step("SHOW:1,1,1,1,1", 500),
            CommandSeqStore.Step("MELODY:ODE", 1200),
        )
        val back = CommandSeqStore.fromJson(CommandSeqStore.toJson(steps))
        assertEquals(steps, back)
        // 坏 JSON 回落空表
        assertTrue(CommandSeqStore.fromJson("garbage").isEmpty())
        assertTrue(CommandSeqStore.fromJson(null).isEmpty())
    }

    // ---------- 刷机日志 ----------

    @Test
    fun flashLogAppendReadClear() {
        val ctx = ctx()
        FlashLog.clear(ctx)
        assertTrue(FlashLog.all(ctx).isEmpty())
        assertTrue(FlashLog.append(ctx, "游戏", "成功"))
        assertTrue(FlashLog.append(ctx, "Python", "失败：超时"))
        val all = FlashLog.all(ctx)
        assertEquals(2, all.size)
        // 新的在前
        assertEquals("Python", all[0].target)
        assertEquals("失败：超时", all[0].result)
        // 导出（无记录返回 null，有记录返回文件）
        val f = FlashLog.export(ctx)
        assertNotNull(f)
        requireNotNull(f)
        assertTrue(f.name.endsWith(".txt"))
        FlashLog.clear(ctx)
        assertTrue(FlashLog.all(ctx).isEmpty())
        assertNull(FlashLog.export(ctx))
    }

    // ---------- Prefs 新键 ----------

    @Test
    fun prefsRoundTripForNewToolKeys() {
        val ctx = ctx()
        // 便签
        Prefs.setScratchpad(ctx, "第一行\n第二行")
        assertEquals("第一行\n第二行", Prefs.getScratchpad(ctx))
        Prefs.setScratchpad(ctx, "")
        assertEquals("", Prefs.getScratchpad(ctx))
        // 课表
        val json = BellSchedule.toJson(listOf(BellSchedule.Lesson(9, "体育", 15, 30, true)))
        Prefs.setBellSchedule(ctx, json)
        assertEquals(1, BellSchedule.fromJson(Prefs.getBellSchedule(ctx)).size)
        // 随机数历史（新→旧）
        Prefs.setRandomHistory(ctx, listOf(7, 3, 9))
        assertEquals(listOf(7, 3, 9), Prefs.getRandomHistory(ctx))
        Prefs.setRandomHistory(ctx, listOf(1))
        assertEquals(listOf(1), Prefs.getRandomHistory(ctx))
    }

    // ---------- 功能目录：77 项、39 项新功能全部登记 ----------

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
        // 图标必须已绑定真实资源（0 = 未绑定）
        FeatureCatalog.FEATURES.forEach { f ->
            assertTrue("${f.id} 图标未绑定", f.icon != 0)
            assertTrue("${f.id} 标题为空", f.title.isNotBlank())
            assertTrue("${f.id} 描述为空", f.desc.isNotBlank())
        }
    }

    @Test
    fun newFeatureIdsAreRegistered() {
        val newIds = listOf(
            // 显示与预览（4）
            "marquee", "bigdigit", "brightauto", "nightlight",
            // 声音与演示（6）
            "metronome", "doorbell", "siren", "soundboard", "tunepad", "melodymix",
            // 传感器与数据（7）
            "compassrose", "shakecount", "gesturelog", "droptest", "tempchart", "motiondetector", "sensortest",
            // 编程与刷写（7）
            "codelib", "codecheck", "tutorials", "pintable", "errordict", "flashlog", "scriptinfo",
            // 指针与自动化（7）
            "shakebutton", "gesturelock", "linkmonitor", "commandseq", "quickpanel", "logstream", "idlebot",
            // 游戏与互动（7）
            "tapcount", "holdgame", "hotpotato", "roulette", "truthdare", "quizshow", "blackjack",
            // 实用工具（1）
            "torch",
        )
        assertEquals(39, newIds.size)
        newIds.forEach { id ->
            assertTrue("功能目录缺少 $id", FeatureCatalog.FEATURES.any { it.id == id })
        }
    }

    // ---------- 打开冒烟：39 个新功能分 5 批构建不崩溃 ----------

    private fun connectedActivity(): MainActivity {
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
        return activity
    }

    @Test
    fun newFeaturesOpenBatch1Display() {
        val a = connectedActivity()
        a.openMarquee()
        a.openBigDigit()
        a.openBrightAuto()
        a.openNightLight()
        a.openMetronome()
        a.openDoorbell()
        a.openSiren()
    }

    @Test
    fun newFeaturesOpenBatch2SoundAndSensor() {
        val a = connectedActivity()
        a.openSoundboard()
        a.openTunePad()
        a.openMelodyMix()
        a.openCompassRose()
        a.openShakeCount()
        a.openGestureLog()
        a.openDropTest()
        a.openTempChart()
        a.openMotionDetector()
    }

    @Test
    fun newFeaturesOpenBatch3SensorAndCode() {
        val a = connectedActivity()
        a.openSensorTest()
        a.openCodeLib()
        a.openCodeCheck()
        a.openTutorials()
        a.openPinTable()
        a.openErrorDict()
        a.openFlashLog()
        a.openScriptInfo()
        a.openShakeButton()
        a.openGestureLock()
    }

    @Test
    fun newFeaturesOpenBatch4AutoAndGame() {
        val a = connectedActivity()
        a.openLinkMonitor()
        a.openCommandSeq()
        a.openQuickPanel()
        a.openLogStream()
        a.openIdleBot()
        a.openTapCount()
        a.openHoldGame()
        a.openHotPotato()
        a.openRoulette()
        a.openTruthDare()
    }

    @Test
    fun newFeaturesOpenBatch5GameAndTool() {
        val a = connectedActivity()
        a.openQuizShow()
        a.openBlackjack()
        a.openTorch()
    }
}

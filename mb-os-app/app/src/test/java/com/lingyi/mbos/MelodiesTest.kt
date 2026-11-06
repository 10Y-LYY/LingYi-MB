/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos

import com.lingyi.mbos.util.Melodies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模拟器内置旋律数据与解析（对齐固件 music 模块 _parseNote 语义）：
 * 21 首曲谱全部可解析；频率按十二平均律以 A4=440Hz 换算；
 * 八度与时值在一首曲子内延续，每首播放前重置默认值。
 */
class MelodiesTest {

    @Test
    fun allBuiltInTunesParse() {
        assertEquals("内置旋律应为 21 首", 21, Melodies.NAMES.size)
        Melodies.NAMES.forEach { name ->
            val raw = Melodies.tune(name)
            assertNotNull("$name 应有曲谱数据", raw)
            val notes = Melodies.parse(raw!!)
            assertTrue("$name 应解析出音符，实际 ${notes.size}", notes.isNotEmpty())
            notes.forEach {
                assertTrue("$name 音符时值必须为正：${it.ticks}", it.ticks > 0)
                assertTrue("$name 音符频率必须非负：${it.freqHz}", it.freqHz >= 0.0)
            }
        }
    }

    @Test
    fun knownNamesAreRecognized() {
        assertTrue(Melodies.has("ODE"))
        assertTrue("名称匹配应大小写不敏感", Melodies.has("ode"))
        assertFalse(Melodies.has("NOPE"))
        assertNull(Melodies.tune("NOPE"))
    }

    @Test
    fun noteFrequenciesFollowEqualTemperament() {
        assertEquals("A4 应为基准 440Hz", 440.0, Melodies.parse("a4")[0].freqHz, 0.01)
        assertEquals(261.63, Melodies.parse("c4")[0].freqHz, 0.01) // 中央 C
        assertEquals(329.63, Melodies.parse("e4")[0].freqHz, 0.01)
        assertEquals(466.16, Melodies.parse("a#4")[0].freqHz, 0.01) // 升号
        assertEquals(466.16, Melodies.parse("bb4")[0].freqHz, 0.01) // b 后缀 = 降号（B♭4 = A#4）
        assertEquals(415.30, Melodies.parse("g#4")[0].freqHz, 0.01)
        assertEquals(0.0, Melodies.parse("r:2")[0].freqHz, 0.0) // 休止符
    }

    @Test
    fun octaveAndDurationPersistWithinTune() {
        // "f#5:2 g#"：第二个音继承八度 5 与时值 2（官方文档：记住了八度与时值）
        val notes = Melodies.parse("f#5:2 g#")
        assertEquals(2, notes.size)
        assertEquals(739.99, notes[0].freqHz, 0.01) // F#5
        assertEquals(830.61, notes[1].freqHz, 0.01) // G#5，不是 G#4（415.30）
        assertEquals(2, notes[0].ticks)
        assertEquals(2, notes[1].ticks)
        // 换音后显式改八度，后续再继承新值
        val mixed = Melodies.parse("c5 d e4 f")
        assertEquals(523.25, mixed[0].freqHz, 0.01) // C5
        assertEquals(587.33, mixed[1].freqHz, 0.01) // D5（继承）
        assertEquals(329.63, mixed[2].freqHz, 0.01) // E4（显式切换）
        assertEquals(349.23, mixed[3].freqHz, 0.01) // F4（继承 E 的八度）
    }

    @Test
    fun eachTuneStartsFromDefaults() {
        // 每首播放前八度/时值重置为 4：同一首解析两次结果必须一致
        val a = Melodies.parse(Melodies.tune("NYAN")!!)
        val b = Melodies.parse(Melodies.tune("NYAN")!!)
        assertEquals(a.size, b.size)
        a.indices.forEach {
            assertEquals(a[it].freqHz, b[it].freqHz, 0.0)
            assertEquals(a[it].ticks, b[it].ticks)
        }
    }

    @Test
    fun odeMatchesExpectedOpening() {
        val notes = Melodies.parse(Melodies.tune("ODE")!!)
        // 欢乐颂开头：E4 E F G G F E D …
        assertEquals(329.63, notes[0].freqHz, 0.01)
        assertEquals(349.23, notes[2].freqHz, 0.01) // F4
        assertEquals(392.00, notes[3].freqHz, 0.01) // G4
        // 默认 tempo：120bpm、每拍 4 tick → 每 tick 125ms，默认时值 4 tick = 500ms
        assertEquals(125.0, Melodies.TICK_MS, 0.0)
        assertEquals(500.0, notes[0].durMs, 0.0)
        assertEquals(1000.0, notes[14].durMs, 0.0) // 第 15 个音 "d:8"（8 tick × 125ms）
    }

    @Test
    fun nyanKeepsEveryToken() {
        val raw = Melodies.tune("NYAN")!!
        val tokenCount = raw.split(' ').count { it.isNotEmpty() }
        assertEquals("NYAN 每个记号都应解析出音符", tokenCount, Melodies.parse(raw).size)
    }

    @Test
    fun restsAndAccidentalsCoexist() {
        // DADADADUM：r4:2 g g g eb:8 r:2 f f f d:8
        val notes = Melodies.parse(Melodies.tune("DADADADUM")!!)
        assertEquals(0.0, notes[0].freqHz, 0.0) // 休止
        assertEquals(2, notes[0].ticks)
        assertEquals(392.00, notes[1].freqHz, 0.01) // G4（默认八度）
        assertEquals(2, notes[1].ticks) // 时值从休止符的 :2 延续
        assertEquals(311.13, notes[4].freqHz, 0.01) // E♭4
        assertEquals(8, notes[4].ticks) // eb:8 显式时值
    }
}

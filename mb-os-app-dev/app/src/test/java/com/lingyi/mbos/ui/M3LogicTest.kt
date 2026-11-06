/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

import com.lingyi.mbos.util.Prefs
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v4.2 设计系统纯逻辑组件测试（无 Robolectric，OOM 安全）：
 * ShakeGate 去抖 / snapToStep 滑块网格 / rowsToMatrix 点阵 / Gestures 手势名。
 */
class M3LogicTest {

    // ---------- ShakeGate 手势去抖 ----------
    // 注：last 初值 0，与旧行为一致——真实时钟（currentTimeMillis 远大于间隔）下首次恒放行；
    // 测试用同一基准大时间戳模拟。

    @Test
    fun shakeGate_firstAlwaysPasses() {
        val gate = M3Kit.ShakeGate()
        // 首次放行（now 远大于间隔，与真实时钟一致）
        assertTrue(gate.allow(900, now = 100_000))
    }

    @Test
    fun shakeGate_blocksWithinWindow() {
        val gate = M3Kit.ShakeGate()
        assertTrue(gate.allow(900, now = 100_000))
        // 窗口内（含边界）一律拦截
        assertFalse(gate.allow(900, now = 100_100))
        assertFalse(gate.allow(900, now = 100_899))
        assertFalse(gate.allow(900, now = 100_900))
    }

    @Test
    fun shakeGate_passesAfterWindow() {
        val gate = M3Kit.ShakeGate()
        assertTrue(gate.allow(900, now = 100_000))
        assertFalse(gate.allow(900, now = 100_900))
        assertTrue(gate.allow(900, now = 100_901))
        // 放行后时间戳刷新，重新进入窗口
        assertFalse(gate.allow(900, now = 101_000))
    }

    @Test
    fun shakeGate_supportsDynamicCooldown() {
        // 摇晃触发器场景：冷却档位 1s / 2s / 5s 运行中可调
        val gate = M3Kit.ShakeGate()
        assertTrue(gate.allow(2_000, now = 100_000))
        assertFalse(gate.allow(2_000, now = 101_999))
        assertTrue(gate.allow(1_000, now = 102_500))
        assertFalse(gate.allow(5_000, now = 103_000))
        assertTrue(gate.allow(5_000, now = 107_501))
    }

    // ---------- snapToStep 滑块网格吸附（防 IllegalStateException 崩溃） ----------

    @Test
    fun snapToStep_gridAlignedValuesPassThrough() {
        assertEquals(0f, M3Kit.snapToStep(0f, 0f, 6f, 1f), 1e-6f)
        assertEquals(3f, M3Kit.snapToStep(3f, 0f, 6f, 1f), 1e-6f)
        assertEquals(6f, M3Kit.snapToStep(6f, 0f, 6f, 1f), 1e-6f)
        // 0..0.9 step 0.1（历史上灵敏度野值 0.8333333 的同类网格）
        assertEquals(0.8f, M3Kit.snapToStep(0.8f, 0f, 0.9f, 0.1f), 1e-6f)
    }

    @Test
    fun snapToStep_offGridValuesSnapToNearest() {
        // Material Slider 布局期 validateValues 要求 value == from + n×step；
        // 野值（历史两次滑块崩溃的根因）必须被吸附而非透传
        assertEquals(1f, M3Kit.snapToStep(0.8333333f, 0f, 6f, 1f), 1e-6f)
        assertEquals(1f, M3Kit.snapToStep(1.2666667f, 0f, 6f, 1f), 1e-6f)
        // 0.83 距 0.8 更近（0.03 < 0.07），吸附到 0.8
        assertEquals(0.8f, M3Kit.snapToStep(0.83f, 0f, 0.9f, 0.1f), 1e-6f)
    }

    @Test
    fun snapToStep_extremesAndNaN() {
        assertEquals(0f, M3Kit.snapToStep(Float.NaN, 0f, 6f, 1f), 1e-6f)
        assertEquals(0f, M3Kit.snapToStep(-5f, 0f, 6f, 1f), 1e-6f)
        assertEquals(6f, M3Kit.snapToStep(99f, 0f, 6f, 1f), 1e-6f)
        assertEquals(6f, M3Kit.snapToStep(Float.POSITIVE_INFINITY, 0f, 6f, 1f), 1e-6f)
        // 吸附结果永不超过 to（validateValues 的硬约束）
        val v = M3Kit.snapToStep(5.6f, 0f, 6f, 1f)
        assertTrue("snapped $v must be <= to", v <= 6f)
    }

    @Test
    fun snapToStep_nonZeroFrom() {
        // from=1 to=7 step=2 → 合法值 1/3/5/7
        assertEquals(3f, M3Kit.snapToStep(2.6f, 1f, 7f, 2f), 1e-6f)
        assertEquals(7f, M3Kit.snapToStep(7f, 1f, 7f, 2f), 1e-6f)
        assertEquals(1f, M3Kit.snapToStep(0f, 1f, 7f, 2f), 1e-6f)
    }

    // ---------- Prefs 滑块读写双路径吸附 ----------

    @Test
    fun prefsSnapDeadzone_wildValuesCollapseToGrid() {
        // 脱网野值不得穿透到 Slider（「灵敏度滑块崩溃」同款模式的新键名复现）
        assertEquals(0f, Prefs.snapDeadzone(Float.NaN), 1e-6f)
        assertEquals(0f, Prefs.snapDeadzone(-3f), 1e-6f)
        assertEquals(6f, Prefs.snapDeadzone(99f), 1e-6f)
        assertEquals(6f, Prefs.snapDeadzone(Float.POSITIVE_INFINITY), 1e-6f)
        val v = Prefs.snapDeadzone(3.7f)
        assertTrue("deadzone $v must be on 0..6 step 1 grid", v == Math.round(v).toFloat())
    }

    @Test
    fun prefsSnapSmoothing_wildValuesCollapseToGrid() {
        assertEquals(0f, Prefs.snapSmoothing(Float.NaN), 1e-6f)
        assertEquals(0.9f, Prefs.snapSmoothing(99f), 1e-6f)
        val v = Prefs.snapSmoothing(0.53f)
        // 0..0.9 step 0.1：0.53 → 0.5
        assertEquals(0.5f, v, 1e-6f)
    }

    // ---------- Matrix 点阵单一出处 ----------

    @Test
    fun rowsToMatrix_basicAndDefensive() {
        val m = rowsToMatrix(listOf("11111", "10001", "10001", "10001", "11111"))
        assertEquals(5, m.size)
        assertEquals(5, m[0].size)
        assertTrue(m[0][0])
        assertTrue(m[0][4])
        assertTrue(m[1][0])
        assertFalse(m[1][1])
        assertTrue(m[4][4])
        // 缺行缺列按灭，不崩
        val partial = rowsToMatrix(listOf("11"))
        assertTrue(partial[0][0])
        assertTrue(partial[0][1])
        assertFalse(partial[0][2])
        assertFalse(partial[4][4])
        // 空表全灭
        assertFalse(rowsToMatrix(emptyList())[2][2])
    }

    @Test
    fun rowsToMatrix_patternsAreWellFormed() {
        // 图案库每行 5 字符、共 5 行（喂给 rowsToMatrix 的契约）
        listOf(
            Patterns.COIN_HEADS, Patterns.COIN_TAILS, Patterns.HEART,
            Patterns.SMILE, Patterns.STAR, Patterns.CHECK, Patterns.CROSS
        ).forEach { p ->
            assertEquals(5, p.size)
            p.forEach { row -> assertEquals(5, row.length) }
        }
        val heart = rowsToMatrix(Patterns.HEART)
        assertTrue(heart[1][1] && heart[1][2] && heart[1][3])
        assertFalse(heart[0][0])
    }

    @Test
    fun fullOnOff() {
        assertTrue(fullOn().all { row -> row.all { it } })
        assertFalse(fullOff().any { row -> row.any { it } })
    }

    @Test
    fun rowsToDigits_range() {
        val d = rowsToDigits(listOf("12345", "67890", "00000", "99999", "11111"))
        assertEquals(1, d[0][0])
        assertEquals(5, d[0][4])
        assertEquals(0, d[1][4])
        assertEquals(9, d[3][0])
        // 非数字按 0
        val loose = rowsToDigits(listOf("abcde"))
        assertArrayEquals(intArrayOf(0, 0, 0, 0, 0), loose[0])
    }

    // ---------- Gestures 手势中文名 ----------

    @Test
    fun gesturesLabel_knownValues() {
        assertEquals("摇一摇", Gestures.label("shake"))
        assertEquals("点击", Gestures.label("tap"))
        assertEquals("Logo 朝上", Gestures.label("logoup"))
        assertEquals("正面朝下", Gestures.label("facedown"))
        assertEquals("自由落体", Gestures.label("freefall"))
        assertEquals("3g 加速", Gestures.label("3g"))
        assertEquals("8g 加速", Gestures.label("8g"))
    }

    @Test
    fun gesturesLabel_unknownAndNull() {
        // null / 未知值按「无」展示，不抛
        assertEquals("无", Gestures.label(null))
        assertEquals("无", Gestures.label(""))
        assertEquals("无", Gestures.label("somethingelse"))
    }
}

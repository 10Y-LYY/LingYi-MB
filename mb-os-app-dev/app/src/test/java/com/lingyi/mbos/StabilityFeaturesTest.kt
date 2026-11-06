/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos

import com.lingyi.mbos.ui.StabilityFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 稳定性功能纯逻辑测试（4.1.0-preview）：
 * 自检摘要 / 快照 diff / 时钟偏差分级 / HEX 校验 / 退避重连周期。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StabilityFeaturesTest {

    // ---------- 自检摘要 ----------

    @Test
    fun summarize_allOk() {
        val items = listOf(
            StabilityFeatures.CheckItem("蓝牙", true, "", StabilityFeatures.Fix.NONE),
            StabilityFeatures.CheckItem("存储", true, "", StabilityFeatures.Fix.NONE)
        )
        assertEquals("2/2 项全部通过", StabilityFeatures.summarize(items))
    }

    @Test
    fun summarize_withBad_listsNames() {
        val items = listOf(
            StabilityFeatures.CheckItem("蓝牙", true, "", StabilityFeatures.Fix.NONE),
            StabilityFeatures.CheckItem("权限", false, "未授予", StabilityFeatures.Fix.PERMISSIONS),
            StabilityFeatures.CheckItem("通知", false, "已关闭", StabilityFeatures.Fix.NOTIFICATIONS)
        )
        val text = StabilityFeatures.summarize(items)
        assertTrue(text.startsWith("1/3 项通过"))
        assertTrue(text.contains("权限"))
        assertTrue(text.contains("通知"))
    }

    // ---------- 快照 diff ----------

    @Test
    fun snapshotDiff_detectsChangedAddedRemoved() {
        val old = "温度 = 25 °C\n光线 = 120\n加速度 X = 10"
        val new = "温度 = 26 °C\n光线 = 120\n新增项 = 1"
        val diff = StabilityFeatures.SnapshotDiff.diff(old, new)
        assertTrue(diff.contains("温度"))
        assertTrue(diff.contains("26 °C"))
        assertTrue(diff.contains("新增项"))
        assertTrue(diff.contains("加速度 X"))
    }

    @Test
    fun snapshotDiff_identicalReturnsEmpty() {
        val t = "温度 = 25 °C\n光线 = 120"
        assertEquals("", StabilityFeatures.SnapshotDiff.diff(t, t))
    }

    @Test
    fun snapshotDiff_ignoresNonKeyValueLines() {
        val old = "说明文字不是键值\n温度 = 25 °C"
        val new = "温度 = 25 °C"
        assertEquals("", StabilityFeatures.SnapshotDiff.diff(old, new))
    }

    // ---------- 时钟偏差 ----------

    @Test
    fun clockDrift_belowThreshold() {
        assertFalse(
            StabilityFeatures.ClockDrift.needsCalibration(1_000_000L, 1_000_000L + 60_000L)
        )
        assertEquals("设备时钟快 1 分 0 秒", StabilityFeatures.ClockDrift.describe(60))
    }

    @Test
    fun clockDrift_aboveThreshold() {
        assertTrue(
            StabilityFeatures.ClockDrift.needsCalibration(1_000_000L, 1_000_000L - 300_000L)
        )
        assertEquals("设备时钟慢 5 分 0 秒", StabilityFeatures.ClockDrift.describe(-300))
    }

    @Test
    fun clockDrift_exact() {
        assertEquals("时钟准确", StabilityFeatures.ClockDrift.describe(0))
    }

    // ---------- HEX 校验 ----------

    @Test
    fun hexCheck_valid() {
        val hex = ":10010000214601360121470136007EFE09D2190140\n:00000001FF\n"
        val r = CustomFlashActivity.HexCheck.check(hex)
        assertTrue(r.ok)
        assertEquals(1, r.records)
        assertTrue(r.problems.isEmpty())
    }

    @Test
    fun hexCheck_missingEof() {
        val hex = ":10010000214601360121470136007EFE09D2190140\n"
        val r = CustomFlashActivity.HexCheck.check(hex)
        assertFalse(r.ok)
        assertTrue(r.problems.any { it.contains("EOF") })
    }

    @Test
    fun hexCheck_badChars() {
        val hex = ":10ZZ0000214601360121470136007EFE09D2190140\n:00000001FF\n"
        val r = CustomFlashActivity.HexCheck.check(hex)
        assertFalse(r.ok)
        assertTrue(r.problems.any { it.contains("十六进制") })
    }

    @Test
    fun hexCheck_empty() {
        val r = CustomFlashActivity.HexCheck.check("   \n  ")
        assertFalse(r.ok)
        assertTrue(r.problems.contains("文件为空"))
    }
}

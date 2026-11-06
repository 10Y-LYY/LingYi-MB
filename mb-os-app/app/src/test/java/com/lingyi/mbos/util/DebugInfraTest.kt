/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.util

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 调试控制台 / 问题上报 / 自动重连基础设施测试（4.1.0-preview）：
 * 退避周期、ANR 分级、操作轨迹、界面树捕获、诊断报告结构。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = com.lingyi.mbos.MBOSApp::class)
class DebugInfraTest {

    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        ActionTrail.clear()
        DebugBus.clear()
    }

    // ---------- 退避重连周期 ----------

    @Test
    fun backoffSchedule_doublesUntilCap() {
        assertEquals(1_000L, BackoffSchedule.delayFor(1))
        assertEquals(2_000L, BackoffSchedule.delayFor(2))
        assertEquals(4_000L, BackoffSchedule.delayFor(3))
        assertEquals(8_000L, BackoffSchedule.delayFor(4))
        assertEquals(16_000L, BackoffSchedule.delayFor(5))
        assertEquals(30_000L, BackoffSchedule.delayFor(6))
        assertEquals(30_000L, BackoffSchedule.delayFor(10))
    }

    @Test
    fun backoffSchedule_attemptBelowOneClamps() {
        assertEquals(1_000L, BackoffSchedule.delayFor(0))
        assertEquals(1_000L, BackoffSchedule.delayFor(-5))
    }

    // ---------- ANR 看门狗分级 ----------

    @Test
    fun anrClassify_levels() {
        assertEquals(AnrWatchdog.Level.OK, AnrWatchdog.classify(500L))
        assertEquals(AnrWatchdog.Level.OK, AnrWatchdog.classify(1_999L))
        assertEquals(AnrWatchdog.Level.WARN, AnrWatchdog.classify(2_000L))
        assertEquals(AnrWatchdog.Level.WARN, AnrWatchdog.classify(4_999L))
        assertEquals(AnrWatchdog.Level.ANR, AnrWatchdog.classify(5_000L))
        assertEquals(AnrWatchdog.Level.ANR, AnrWatchdog.classify(30_000L))
    }

    // ---------- 操作轨迹 ----------

    @Test
    fun actionTrail_recordRecentClear() {
        ActionTrail.record("进入页面", "GuideActivity")
        ActionTrail.record("点击按钮", "btnConnect")
        val lines = ActionTrail.recent(10)
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("进入页面"))
        assertTrue(lines[0].contains("GuideActivity"))
        assertTrue(lines[1].contains("点击按钮"))
        ActionTrail.clear()
        assertTrue(ActionTrail.recent().isEmpty())
    }

    @Test
    fun actionTrail_ringBounded() {
        repeat(150) { ActionTrail.record("事件$it") }
        assertEquals(120, ActionTrail.recent().size)
        assertTrue(ActionTrail.recent().last().contains("事件149"))
    }

    @Test
    fun actionTrail_describeView() {
        val tv = TextView(ctx()).apply {
            text = "连接设备"
            id = View.generateViewId()
        }
        val desc = ActionTrail.describe(tv)
        assertTrue(desc.contains("TextView"))
        assertTrue(desc.contains("「连接设备」"))
    }

    // ---------- 界面元素树捕获 ----------

    @Test
    fun viewTreeCapture_noActivity_returnsHint() {
        // 测试环境无 resumed Activity：必须返回提示文本而不是崩溃
        val text = ViewTreeCapture.capture()
        assertTrue(text.contains("无可见 Activity"))
    }

    // ---------- 诊断报告 ----------

    @Test
    fun debugReport_containsAllSections() {
        val report = DebugReport.build(ctx(), "连接后指针不动")
        assertTrue(report.contains("用户描述"))
        assertTrue(report.contains("连接后指针不动"))
        assertTrue(report.contains("设备信息"))
        assertTrue(report.contains("应用信息"))
        assertTrue(report.contains("连接状态"))
        assertTrue(report.contains("最近操作轨迹"))
        assertTrue(report.contains("界面元素树"))
        assertTrue(report.contains("相关日志"))
    }

    @Test
    fun debugReport_blankDescMarked() {
        val report = DebugReport.build(ctx(), "   ")
        assertTrue(report.contains("(未填写)"))
    }

    @Test
    fun debugReport_saveCreatesFile() {
        val report = DebugReport.build(ctx(), "测试保存")
        val f = DebugReport.save(ctx(), report)
        assertNotNull(f)
        assertTrue(f!!.exists())
        assertTrue(f.readText().contains("测试保存"))
    }

    // ---------- 抓包总线 ----------

    @Test
    fun debugBus_captureToggleAndRing() {
        DebugBus.setCaptureEnabled(true)
        assertTrue(DebugBus.captureEnabled)
        DebugBus.tx("READ\n")
        DebugBus.rx("A:0|B:0|T:25")
        DebugBus.state("已连接")
        assertEquals(3, DebugBus.size())
        val ps = DebugBus.packets(10)
        assertTrue(ps.any { it.render().contains("READ") })
        assertTrue(ps.any { it.dir == DebugBus.Dir.RX })
        DebugBus.setCaptureEnabled(false)
        assertFalse(DebugBus.captureEnabled)
        assertEquals(0, DebugBus.size())
    }

    @Test
    fun debugBus_connectionSnapshotHasFields() {
        val snap = DebugBus.connectionSnapshot()
        assertTrue(snap.contains("连接"))
    }
}

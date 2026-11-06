/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos

import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.lingyi.mbos.ble.ConnectionHub
import com.lingyi.mbos.ble.ConnState
import com.lingyi.mbos.ble.MicrobitConnection
import com.lingyi.mbos.ble.SimulatorConnection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * 模拟器悬浮窗服务启动回归（#113）：
 * 服务能正常创建、把面板与折叠气泡加入窗口管理、销毁时干净移除。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = MBOSApp::class)
class SimulatorServiceTest {

    @After
    fun tearDown() {
        // 防止跨测试污染：断开可能残留的模拟器连接
        ConnectionHub.disconnect()
    }

    @Test
    fun serviceAddsOverlayViewsAndCleansUp() {
        val wm = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSystemService(WindowManager::class.java)
        val controller = Robolectric.buildService(SimulatorService::class.java).create()
        val service = controller.get()
        assertTrue("服务创建后应标记为运行中", SimulatorService.isRunning)

        val shadow = shadowOf(wm) as org.robolectric.shadows.ShadowWindowManagerImpl
        assertTrue(
            "应同时加入展开面板与折叠气泡，实际=${shadow.views.size}",
            shadow.views.size >= 2
        )

        controller.destroy()
        assertFalse("销毁后应标记为未运行", SimulatorService.isRunning)
        // 注意：getViews() 返回不可变快照，必须重新读取
        assertTrue("销毁后应移除全部窗口视图，实际=${shadow.views.size}", shadow.views.isEmpty())
    }

    // ---------- v3.3.0：A/B 键按住语义 + 姿态微调面板 ----------

    /** 连上模拟器并启动悬浮窗，返回（展开面板视图, 模拟器连接）。 */
    private fun startWithSimulator(): Pair<View, SimulatorConnection> {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ConnectionHub.connectSimulator(ctx, object : MicrobitConnection.Listener {
            override fun onState(state: ConnState) = Unit
            override fun onData(data: String) = Unit
        })
        Robolectric.buildService(SimulatorService::class.java).create()
        val wm = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSystemService(WindowManager::class.java)
        val shadow = shadowOf(wm) as org.robolectric.shadows.ShadowWindowManagerImpl
        // buildExpanded 先加展开面板，buildBubble 后加气泡
        val panel = shadow.views.first()
        return panel to (ConnectionHub.current as SimulatorConnection)
    }

    private fun touch(v: View, action: Int) {
        val now = SystemClock.uptimeMillis()
        val ev = MotionEvent.obtain(now, now, action, 12f, 12f, 0)
        v.dispatchTouchEvent(ev)
        ev.recycle()
    }

    @Test
    fun abKeyUsesHoldSemanticsWithMinimumHold() {
        val (panel, sim) = startWithSimulator()
        val btnA = panel.findViewById<MaterialButton>(R.id.btnSimA)

        // 按下：立即置位（真机手感：按住即生效）
        touch(btnA, MotionEvent.ACTION_DOWN)
        assertTrue("按下应立即使 A 键生效", sim.snapshot().btnA)

        // 松手：最小保持时长内仍生效——控制台多个功能按 80–150ms 轮询读按键，
        // 瞬时点按会被漏掉，最小保持时长保证快速点按也能被采到
        touch(btnA, MotionEvent.ACTION_UP)
        assertTrue("松手后最小保持时长内 A 键仍生效", sim.snapshot().btnA)

        shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS)
        assertFalse("超过最小保持时长后 A 键应抬起", sim.snapshot().btnA)

        // B 键同理
        val btnB = panel.findViewById<MaterialButton>(R.id.btnSimB)
        touch(btnB, MotionEvent.ACTION_DOWN)
        assertTrue(sim.snapshot().btnB)
        touch(btnB, MotionEvent.ACTION_UP)
        shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS)
        assertFalse(sim.snapshot().btnB)
    }

    @Test
    fun attitudePanelExpandLocksManualMode() {
        val (panel, sim) = startWithSimulator()
        val header = panel.findViewById<View>(R.id.simAttitudeHeader)
        val attitudePanel = panel.findViewById<View>(R.id.simAttitudePanel)
        assertEquals("姿态面板默认应折叠（不占悬浮窗空间）", View.GONE, attitudePanel.visibility)

        // 展开：立即接管姿态（暂停自动漂移）
        header.performClick()
        assertEquals("点击标题应展开姿态面板", View.VISIBLE, attitudePanel.visibility)
        assertTrue("展开即进入手动姿态锁", sim.snapshot().attitudeManual)

        // 恢复自动：退出锁定并收起面板
        panel.findViewById<MaterialButton>(R.id.btnSimAttitudeAuto).performClick()
        assertFalse("恢复自动后应退出手动锁", sim.snapshot().attitudeManual)
        assertEquals("恢复自动后面板应收起", View.GONE, attitudePanel.visibility)

        // 再展开：仍可再次锁定
        header.performClick()
        assertEquals(View.VISIBLE, attitudePanel.visibility)
        assertTrue(sim.snapshot().attitudeManual)
    }
}

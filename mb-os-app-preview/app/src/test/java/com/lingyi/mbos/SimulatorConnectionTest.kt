/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos

import androidx.test.core.app.ApplicationProvider
import com.lingyi.mbos.ble.ConnectionHub
import com.lingyi.mbos.ble.MicrobitConnection
import com.lingyi.mbos.ble.SimulatorConnection
import com.lingyi.mbos.ui.FirmwareScript
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

/**
 * micro:bit V2 模拟器回归（#113）：
 * 指令回包必须与固件逐条一致；MOUSE / 周期上报 / 让位逻辑必须可测。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = MBOSApp::class)
class SimulatorConnectionTest {

    private val emitted = mutableListOf<String>()
    private val states = mutableListOf<Boolean>()

    private val capture = object : MicrobitConnection.Listener {
        override fun onState(state: com.lingyi.mbos.ble.ConnState) {
            states.add(state.connected)
        }

        override fun onData(data: String) {
            emitted.add(data)
        }
    }

    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        ConnectionHub.disconnect()
        ConnectionHub.addListener(capture)
        ConnectionHub.connectSimulator(ctx, capture)
    }

    @After
    fun tearDown() {
        ConnectionHub.disconnect()
        ConnectionHub.removeListener(capture)
        emitted.clear()
        states.clear()
    }

    private fun sim(): SimulatorConnection = ConnectionHub.current as SimulatorConnection

    /** 清空已捕获数据，返回捕获列表便于断言增量。 */
    private fun fresh(): MutableList<String> = emitted.also { it.clear() }

    private fun idleMs(ms: Long) {
        shadowOf(android.os.Looper.getMainLooper()).idleFor(ms, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    // ---------- 指令回包（对齐固件） ----------

    @Test
    fun infoReturnsMbosFirmwareVersion() {
        val out = fresh()
        ConnectionHub.send("INFO\n")
        val line = out.firstOrNull { it.startsWith("INFO:") }
        assertNotNull("INFO 应有回包", line)
        // 与内置固件版本联动（FirmwareScript.VERSION），避免固件升级后测试假失败
        assertTrue(
            "回包应含 MB-OS 版本：$line",
            line!!.contains("MB-OS v${FirmwareScript.VERSION}")
        )
        assertTrue("回包应含 micro:bit V2：$line", line.contains("micro:bit V2"))
    }

    @Test
    fun readReturnsFullSensorLine() {
        val out = fresh()
        ConnectionHub.send("READ\n")
        val line = out.firstOrNull()
        assertNotNull("READ 应有回包", line)
        // 字段顺序与固件一致：A|B|T|L|X|Y|Z|C|G
        val keys = line!!.trim().split("|").map { it.substringBefore(':') }
        assertEquals(
            listOf("A", "B", "T", "L", "X", "Y", "Z", "C", "G"),
            keys
        )
    }

    @Test
    fun showPlotsAndClearDriveLedMatrix() {
        val out = fresh()
        ConnectionHub.send("SHOW:10000,01000,00100,00010,00001\n")
        val s = sim().snapshot()
        assertEquals("对角线应点亮", 9, s.led[0][0])
        assertEquals("非对角线应熄灭", 0, s.led[0][1])
        assertEquals(9, s.led[4][4])

        ConnectionHub.send("PLOT:1,1,1\n")
        assertEquals("PLOT 应点亮 (1,1)", 9, sim().snapshot().led[1][1])

        ConnectionHub.send("CLEAR\n")
        assertTrue(
            "CLEAR 后应全灭",
            sim().snapshot().led.all { row -> row.all { it == 0 } }
        )
        assertTrue("显示类指令无回包（同固件）", out.isEmpty())
    }

    @Test
    fun brightnessOkAndErr() {
        val out = fresh()
        ConnectionHub.send("BRIGHT:5\n")
        ConnectionHub.send("BRIGHT:abc\n")
        assertEquals(listOf("OK:BRIGHT\n", "ERR:BRIGHT\n"), out.toList())
    }

    @Test
    fun unknownCommandReturnsErr() {
        val out = fresh()
        ConnectionHub.send("FROBNICATE\n")
        assertEquals(listOf("ERR:FROBNICATE\n"), out.toList())
    }

    @Test
    fun melodyKnownAndUnknown() {
        val out = fresh()
        ConnectionHub.send("MELODY:ODE\n")
        ConnectionHub.send("MELODY:NOPE\n")
        assertEquals(listOf("OK:MELODY:ODE\n", "ERR:MELODY\n"), out.toList())
    }

    @Test
    fun compassNotImplementedLikeFirmware() {
        // 固件未实现 COMPASS：模拟器如实复现 ERR，便于对齐真机行为
        val out = fresh()
        ConnectionHub.send("COMPASS\n")
        assertEquals(listOf("ERR:COMPASS\n"), out.toList())
    }

    // ---------- MOUSE（陀螺仪指针） ----------

    @Test
    fun mouseOnOffCalAck() {
        val out = fresh()
        ConnectionHub.send("MOUSE:ON\n")
        assertEquals("OK:MOUSE:ON\n", out.first())
        assertTrue(sim().snapshot().mouseOn)

        ConnectionHub.send("MOUSE:CAL\n")
        assertTrue(out.contains("OK:MOUSE:CAL\n"))

        ConnectionHub.send("MOUSE:OFF\n")
        assertTrue(out.contains("OK:MOUSE:OFF\n"))
        assertFalse(sim().snapshot().mouseOn)
    }

    @Test
    fun mouseEmitsTiltIncrements() {
        ConnectionHub.send("MOUSE:ON\n")
        fresh()
        // 虚拟倾斜 240mg：超过死区 60mg，240/48 = 5px/帧
        sim().setTiltBias(240, 0)
        idleMs(120)
        val mouseLines = emitted.filter { it.startsWith("MOUSE:") }
        assertTrue("倾斜应产生 MOUSE 增量行：$emitted", mouseLines.isNotEmpty())
        val parts = mouseLines.first().trim().removePrefix("MOUSE:").split(",")
        assertEquals(5, parts[0].toInt())
        assertEquals("dx=5 时 dy 应为 0", 0, parts[1].toInt())
    }

    @Test
    fun mouseEmitsButtonChanges() {
        ConnectionHub.send("MOUSE:ON\n")
        fresh()
        sim().setButtonA(true)
        idleMs(80)
        val mouseLines = emitted.filter { it.startsWith("MOUSE:") }
        assertTrue("按键变化应触发上报（固件同逻辑）：$emitted", mouseLines.isNotEmpty())
        assertTrue(
            "A 按下应上报 a=1：${mouseLines.first()}",
            mouseLines.first().trim().endsWith("1,0")
        )
    }

    @Test
    fun deadzoneSuppressesSmallTilt() {
        ConnectionHub.send("MOUSE:ON\n")
        fresh()
        // 30mg < 死区 60mg：不应产生任何增量
        sim().setTiltBias(30, 0)
        idleMs(150)
        assertTrue("死区内倾斜不应上报：$emitted", emitted.none { it.startsWith("MOUSE:") })
    }

    // ---------- 周期上报 ----------

    @Test
    fun periodicReportEmitsSensorLines() {
        // v3.1.4 起周期上报默认关闭（避免模拟器连连上就每秒弹窗），需显式开启后才会推送
        sim().periodicReport = true
        fresh()
        idleMs(1200)
        val sensorLines = emitted.filter { it.startsWith("A:") && it.contains("|G:") }
        assertTrue("开启周期上报应推送传感器行：$emitted", sensorLines.isNotEmpty())
    }

    @Test
    fun periodicReportCanBeDisabled() {
        sim().periodicReport = false
        fresh()
        idleMs(2200)
        assertTrue("关闭后不应再周期上报：$emitted", emitted.none { it.startsWith("A:") })
    }

    @Test
    fun gestureInjectionEmitsOnce() {
        // injectGesture 仅在周期上报开启时回显（与传感器行同源），故先开启
        sim().periodicReport = true
        fresh()
        sim().injectGesture("shake")
        val g = emitted.filter { it.contains("G:shake") }
        assertEquals("摇晃手势应上报一次", 1, g.size)
    }

    // ---------- 连接生命周期 / 让位 ----------

    @Test
    fun connectEmitsConnectedState() {
        assertTrue("接入后应立即派发已连接状态", states.contains(true))
        assertTrue(sim().isConnected())
        assertEquals("sim", ConnectionHub.type())
    }

    @Test
    fun disconnectStopsEmittingAndEmitsState() {
        val sim = sim()
        ConnectionHub.disconnect()
        assertFalse(sim.isConnected())
        assertTrue("断开应派发状态", states.contains(false))
        val before = emitted.size
        idleMs(1500)
        assertEquals("断开后不应再有数据", before, emitted.size)
    }

    @Test
    fun realConnectionYieldsSimulator() {
        val sim = sim()
        // 真机（BLE）接入：ConnectionHub.connect 先断开当前连接 → 模拟器让位
        ConnectionHub.connect("ble", ctx, capture)
        assertFalse("模拟器应被真机连接让位", sim.isConnected())
        assertTrue("当前连接不再是模拟器", ConnectionHub.current !is SimulatorConnection)
        assertTrue("让位应派发断开状态", states.contains(false))
    }

    @Test
    fun hubSendRoutesToSimulator() {
        val out = fresh()
        assertTrue("发送应成功", ConnectionHub.send("INFO\n"))
        assertTrue(out.any { it.startsWith("INFO:") })
    }

    // ---------- 本地发声（TONE / MELODY：模拟器无扬声器，手机端还原） ----------

    @Test
    fun tonePlaysLocallyWithoutReply() {
        // 与固件一致：TONE 无回包；非法参数静默忽略（不崩、不回包）
        val out = fresh()
        ConnectionHub.send("TONE:440,200\n")
        ConnectionHub.send("TONE:0,200\n")
        ConnectionHub.send("TONE:abc,def\n")
        idleMs(80)
        assertTrue("TONE 不应有回包（同固件）：$out", out.isEmpty())
    }

    @Test
    fun melodyPlaysLocallyForAllBuiltInTunes() {
        // 21 首内置旋律逐一播放：回包必须全部 OK（播放本身在音频线程，异常静默兜底）
        val out = fresh()
        com.lingyi.mbos.util.Melodies.NAMES.forEach { name ->
            ConnectionHub.send("MELODY:$name\n")
        }
        idleMs(120)
        assertEquals(
            "每首内置旋律都应有 OK 回包",
            com.lingyi.mbos.util.Melodies.NAMES.size,
            out.count { it.startsWith("OK:MELODY:") }
        )
        assertTrue("不应出现 ERR：$out", out.none { it.startsWith("ERR:") })
    }

    // ---------- 姿态微调（手动锁 / 自动漂移） ----------

    @Test
    fun setAttitudeLocksValuesAgainstDrift() {
        val sim = sim()
        sim.setAttitude(500, -300, 0, 90)
        val s = sim.snapshot()
        assertEquals(500, s.accelX)
        assertEquals(-300, s.accelY)
        assertEquals(0, s.accelZ)
        assertEquals(90, s.heading)
        assertTrue("手动设置后应进入锁定态", s.attitudeManual)

        // 锁定期间反复漂移：X/Y/Z/朝向必须保持设定值（温度/光线照常漂）
        repeat(20) { sim.driftSensors() }
        val s2 = sim.snapshot()
        assertEquals("锁定期间 X 不应漂", 500, s2.accelX)
        assertEquals("锁定期间 Y 不应漂", -300, s2.accelY)
        assertEquals("锁定期间 Z 不应漂", 0, s2.accelZ)
        assertEquals("锁定期间朝向不应漂", 90, s2.heading)
    }

    @Test
    fun resumeAutoSensorsRestoresDrift() {
        val sim = sim()
        sim.setAttitude(500, 0, 0, 90)
        repeat(5) { sim.driftSensors() }
        assertEquals("锁定期间朝向不动", 90, sim.snapshot().heading)

        sim.resumeAutoSensors()
        assertFalse("恢复自动后应退出手动锁", sim.snapshot().attitudeManual)
        var moved = false
        repeat(100) {
            sim.driftSensors()
            if (sim.snapshot().heading != 90) moved = true
        }
        assertTrue("恢复自动后朝向应重新漂移", moved)
    }

    @Test
    fun setAttitudeEmitsReadWhenPeriodicOn() {
        sim().periodicReport = true
        fresh()
        sim().setAttitude(777, 0, -1024, 200)
        val line = emitted.firstOrNull { it.startsWith("A:") }
        assertNotNull("周期上报开启时设置姿态应立即推送 READ", line)
        assertTrue("应带 X:777：$line", line!!.contains("X:777"))
        assertTrue("应带 C:200：$line", line.contains("C:200"))
    }
}

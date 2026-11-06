/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos

import com.lingyi.mbos.ble.ConnectionHub
import com.lingyi.mbos.ble.LineBuffer
import com.lingyi.mbos.ble.MicrobitConnection
import com.lingyi.mbos.ble.UsbConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 连接层回归测试：
 *  · LineBuffer 行重组（BLE MTU 23 会把 INFO / READ 长响应拆成多个通知，不重组则全部失效）；
 *  · ConnectionHub.addListener 的立即状态补发必须隔离异常；
 *  · disconnect() 不得清空 listeners（否则界面收不到断开通知）；
 *  · USB 读线程的断开判定（用户报障：进入陀螺仪模式后，连接状态被误刷成「未连接」，
 *    而板子其实一直插着——根因是 bulkTransfer 的「空闲超时」被当成了「读失败」）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = MBOSApp::class)
class ConnectionLayerTest {

    @Test
    fun lineBufferReassemblesBleFragments() {
        val buf = LineBuffer()
        // BLE 默认 ATT MTU 23（载荷 20）：46 字节的 INFO 行必然被拆成三段
        assertTrue("第一段不应产生完整行", buf.append("INFO:MicroPython 1").isEmpty())
        assertTrue("第二段不应产生完整行", buf.append(".19|MB-OS v2.9.1").isEmpty())
        val lines = buf.append("|micro:bit V2\n")
        assertEquals(1, lines.size)
        assertEquals("INFO:MicroPython 1.19|MB-OS v2.9.1|micro:bit V2", lines[0])
    }

    @Test
    fun lineBufferSplitsMultipleAndPartialLines() {
        val buf = LineBuffer()
        assertEquals(listOf("MOUSE:1,2,0,0"), buf.append("MOUSE:1,2,0,0\nMOUSE:3"))
        assertEquals(listOf("MOUSE:3,4,1,0"), buf.append(",4,1,0\n"))
    }

    @Test
    fun lineBufferIgnoresEmptyLinesAndResets() {
        val buf = LineBuffer()
        assertEquals(emptyList<String>(), buf.append("\n\n"))
        assertEquals(listOf("OK:MOUSE:ON"), buf.append("OK:MOUSE:ON\n"))
        buf.append("半行残留")
        buf.reset()
        assertEquals(listOf("MOUSE:0,0,0,0"), buf.append("MOUSE:0,0,0,0\n"))
    }

    @Test
    fun addListenerIsolatesThrowingListener() {
        // 先注入一个模拟已连接的连接，确保 addListener 会立即补发 onState
        val fake = object : MicrobitConnection {
            override val type: String = "usb"
            override fun connect(context: android.content.Context, listener: MicrobitConnection.Listener) {}
            override fun disconnect() {}
            override fun send(line: String): Boolean = true
            override fun isConnected(): Boolean = true
        }
        val currentField =
            ConnectionHub::class.java.getDeclaredField("current").apply { isAccessible = true }
        currentField.set(ConnectionHub, fake)
        try {
            // 注册一个 onState 必抛异常的监听器：addListener 的立即状态补发若不做隔离，
            // 会直接崩掉 UI 线程（表现为页面打不开 / 闪退）。
            val bad = object : MicrobitConnection.Listener {
                override fun onState(state: com.lingyi.mbos.ble.ConnState) {
                    throw RuntimeException("boom")
                }

                override fun onData(data: String) {}
            }
            ConnectionHub.addListener(bad) // 不抛即通过
            ConnectionHub.removeListener(bad)
        } finally {
            currentField.set(ConnectionHub, null)
        }
    }

    @Test
    fun disconnectKeepsListenersRegistered() {
        val probe = object : MicrobitConnection.Listener {
            override fun onState(state: com.lingyi.mbos.ble.ConnState) {}
            override fun onData(data: String) {}
        }
        ConnectionHub.addListener(probe)
        ConnectionHub.disconnect()
        val field = ConnectionHub::class.java.getDeclaredField("listeners").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val set = field.get(ConnectionHub) as Set<MicrobitConnection.Listener>
        assertTrue("断开连接不应清空监听器（否则界面收不到断开通知）", set.contains(probe))
        ConnectionHub.removeListener(probe)
    }

    // ---------- USB 读线程断开判定 ----------

    @Test
    fun bulkTransferResultsAreClassifiedByErrno() {
        // 空闲超时（Linux errno 取负）：设备在线但此刻无数据——绝不能判定断开
        listOf(0, -4 /*EINTR*/, -11 /*EAGAIN*/, -110 /*ETIMEDOUT*/).forEach {
            assertEquals("$it 应归类为空闲超时", UsbConnection.READ_IDLE, UsbConnection.classifyReadResult(it))
        }
        // 设备已移除
        listOf(-2 /*ENOENT*/, -19 /*ENODEV*/, -107 /*ENOTCONN*/, -108 /*ESHUTDOWN*/).forEach {
            assertEquals("$it 应归类为设备已移除", UsbConnection.READ_DEVICE_GONE, UsbConnection.classifyReadResult(it))
        }
        // 其它错误：端点停滞 / 总线抖动，保守累计
        listOf(-1, -5 /*EIO*/, -22 /*EINVAL*/, -32 /*EPIPE*/, -71 /*EPROTO*/, -75 /*EOVERFLOW*/, -84 /*EILSEQ*/).forEach {
            assertEquals("$it 应归类为瞬时错误", UsbConnection.READ_TRANSIENT, UsbConnection.classifyReadResult(it))
        }
    }

    @Test
    fun idleReadTimeoutsNeverDisconnect() {
        // 复现用户报障：进入陀螺仪模式后串口静默（板子插着、连着），bulkTransfer 连续
        // 返回 ETIMEDOUT(-110)。旧实现把任何非正值都当读失败，约 3.5 秒后误判断开。
        var streak = 0
        repeat(2000) { // ≈ 16 分钟空闲
            val v = UsbConnection.evaluateRead(-110, streak)
            streak = v.failStreak
            assertFalse("空闲超时被判为断开", v.disconnected)
        }
        assertEquals("空闲超时应把失败计数归零", 0, streak)
    }

    @Test
    fun deviceGoneDisconnectsAfterTwoConsecutiveErrors() {
        // ENODEV(-19)：拔出后内核立即返回（不等满超时），连续 2 次即判定，防单次抖动
        val first = UsbConnection.evaluateRead(-19, 0)
        assertFalse("第 1 次设备移除不应立即断开", first.disconnected)
        assertTrue(UsbConnection.evaluateRead(-19, first.failStreak).disconnected)
    }

    @Test
    fun transientBusErrorsNeedSustainedFailures() {
        // -EPIPE 等总线抖动：差一次不判断，达到阈值才判断；中途一次空闲超时即归零
        var streak = 0
        repeat(UsbConnection.TRANSIENT_FAIL_LIMIT - 2) {
            streak = UsbConnection.evaluateRead(-32, streak).failStreak
        }
        val oneShort = UsbConnection.evaluateRead(-32, streak)
        assertFalse("瞬时错误差一次不应断开", oneShort.disconnected)
        assertTrue(
            "连续错误达到阈值才应断开",
            UsbConnection.evaluateRead(-32, oneShort.failStreak).disconnected
        )
        assertEquals(
            "中途出现空闲超时说明设备仍在线，计数应归零",
            0,
            UsbConnection.evaluateRead(-110, UsbConnection.TRANSIENT_FAIL_LIMIT - 1).failStreak
        )
    }

    @Test
    fun dataReadResetsFailureStreak() {
        val v = UsbConnection.evaluateRead(42, 19)
        assertEquals(0, v.failStreak)
        assertFalse(v.disconnected)
    }
}

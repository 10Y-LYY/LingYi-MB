/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import androidx.test.core.app.ApplicationProvider
import com.lingyi.mbos.MBOSApp
import com.lingyi.mbos.ble.ConnectionHub
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * v4.2 共享组件回归测试（Robolectric 批）：
 * Ticker 周期任务 / ToneSequence 顺序播音 / ProgressTrack 进度轨 /
 * 5×5 矩阵编辑器 / SensorPoller 兜底停止。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = MBOSApp::class)
class M3ComponentsTest {

    private fun ctx() = ApplicationProvider.getApplicationContext<MBOSApp>()

    private fun mainHandler() = android.os.Handler(android.os.Looper.getMainLooper())

    private fun px(dp: Int): Int =
        (ctx().resources.displayMetrics.density * dp).toInt()

    // ---------- Ticker 周期任务 ----------

    @Test
    fun ticker_runsUntilSelfTerminate() {
        var count = 0
        val tick = M3Kit.Ticker({ 50L }, mainHandler()) {
            count++
            count < 3 // 第 3 拍自终止
        }
        tick.start()
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS)
        assertEquals(3, count)
        // 自终止后不再排拍
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS)
        assertEquals(3, count)
    }

    @Test
    fun ticker_stopBeforeIdleCancelsPending() {
        var count = 0
        val tick = M3Kit.Ticker({ 50L }, mainHandler()) {
            count++
            true
        }
        tick.start()
        tick.stop()
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS)
        assertEquals(0, count)
    }

    @Test
    fun ticker_stopFromInsideTick() {
        var count = 0
        lateinit var tick: M3Kit.Ticker
        tick = M3Kit.Ticker({ 50L }, mainHandler()) {
            count++
            if (count == 2) {
                tick.stop()
                false
            } else {
                true
            }
        }
        tick.start()
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS)
        assertEquals(2, count)
    }

    @Test
    fun ticker_restartDoesNotDoubleSchedule() {
        // start() 必须先清理残留再入队：连点两次「开始」不得出现两条调度链
        var count = 0
        val tick = M3Kit.Ticker({ 50L }, mainHandler()) {
            count++
            count < 1 // 一拍即停，便于计数
        }
        tick.start()
        tick.start()
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS)
        assertEquals(1, count)
    }

    @Test
    fun ticker_dynamicIntervalProvider() {
        // 节拍器场景：间隔由提供者动态计算（改 BPM 下一拍生效），运行中不崩
        var bpm = 120
        var count = 0
        val tick = M3Kit.Ticker({ 60_000L / bpm }, mainHandler()) {
            count++
            count < 3
        }
        tick.start()
        // 60_000/120 = 500ms 一拍，预算需覆盖 3 拍（1500ms）
        ShadowLooper.idleMainLooper(2_000, TimeUnit.MILLISECONDS)
        assertEquals(3, count)
        bpm = 240 // 运行中改速，验证提供者被反复求值
        tick.start()
        ShadowLooper.idleMainLooper(2_000, TimeUnit.MILLISECONDS)
        assertEquals(4, count) // 3 + 重启后 1 拍
    }

    // ---------- ToneSequence 后台顺序播音 ----------

    private fun await(latch: CountDownLatch) {
        assertTrue(" ToneSequence 未在 5s 内完成", latch.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun toneSequence_playsNotesInOrder() {
        val received = ArrayList<Pair<Int, Int>>()
        val done = CountDownLatch(1)
        M3Kit.ToneSequence(
            notes = listOf(440 to 15L, 880 to 15L, 1319 to 15L),
            shouldContinue = { true },
            onTone = { f, ms -> received.add(f to ms) },
            onFinished = { done.countDown() }
        ).start()
        await(done)
        assertEquals(listOf(440 to 15, 880 to 15, 1319 to 15), received)
    }

    @Test
    fun toneSequence_restIsSilent() {
        val received = ArrayList<Int>()
        val done = CountDownLatch(1)
        M3Kit.ToneSequence(
            notes = listOf(0 to 15L, 440 to 15L, 0 to 15L),
            shouldContinue = { true },
            onTone = { f, _ -> received.add(f) },
            onFinished = { done.countDown() }
        ).start()
        await(done)
        assertEquals(listOf(440), received)
    }

    @Test
    fun toneSequence_interruptsMidway() {
        val received = ArrayList<Int>()
        val done = CountDownLatch(1)
        var go = true
        M3Kit.ToneSequence(
            notes = listOf(440 to 15L, 880 to 15L, 1319 to 15L),
            shouldContinue = { go },
            onTone = { f, _ ->
                received.add(f)
                go = false // 第一拍后取消
            },
            onFinished = { done.countDown() }
        ).start()
        await(done)
        assertEquals(listOf(440), received)
    }

    @Test
    fun toneSequence_timesAndGap() {
        val received = ArrayList<Int>()
        val done = CountDownLatch(1)
        val t0 = System.currentTimeMillis()
        M3Kit.ToneSequence(
            notes = listOf(523 to 15L, 659 to 15L),
            shouldContinue = { true },
            onTone = { f, _ -> received.add(f) },
            onFinished = { done.countDown() },
            times = 2,
            gapMs = 20L
        ).start()
        await(done)
        // 两轮各两拍 + 轮间停顿 20ms
        assertEquals(listOf(523, 659, 523, 659), received)
        assertTrue("总时长应覆盖 2×(15+15)+20ms", System.currentTimeMillis() - t0 >= 60)
    }

    // ---------- ProgressTrack 进度轨 ----------

    @Test
    fun progressTrack_fractionCachedUntilLayoutThenApplied() {
        val track = ProgressTrack(ctx())
        track.setFraction(0.5f)
        // 未布局：比例缓存不丢、不崩
        assertEquals(0.5f, track.fraction, 1e-6f)
        assertEquals(0, track.width)

        track.measure(
            View.MeasureSpec.makeMeasureSpec(px(200), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(px(14), View.MeasureSpec.EXACTLY)
        )
        track.layout(0, 0, px(200), px(14))
        // onSizeChanged 重放缓存比例
        val fill = track.getChildAt(0)
        assertEquals(px(100), fill.layoutParams.width)
        assertEquals(px(200), track.width)
    }

    @Test
    fun progressTrack_fractionCoerced() {
        val track = ProgressTrack(ctx())
        track.setFraction(2f)
        assertEquals(1f, track.fraction, 1e-6f)
        track.setFraction(-1f)
        assertEquals(0f, track.fraction, 1e-6f)
    }

    @Test
    fun progressTrack_pillCornersFollowHeight() {
        val track = ProgressTrack(ctx())
        track.measure(
            View.MeasureSpec.makeMeasureSpec(px(200), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(px(14), View.MeasureSpec.EXACTLY)
        )
        track.layout(0, 0, px(200), px(14))
        val bg = track.background as android.graphics.drawable.GradientDrawable
        // 全 Pill 圆角 = 高度一半
        assertEquals(px(14) / 2f, bg.cornerRadius, 1e-3f)
    }

    @Test
    fun progressTrack_setFractionAfterLayoutUpdatesImmediately() {
        val track = ProgressTrack(ctx())
        track.measure(
            View.MeasureSpec.makeMeasureSpec(px(200), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(px(10), View.MeasureSpec.EXACTLY)
        )
        track.layout(0, 0, px(200), px(10))
        track.setFraction(0.25f)
        assertEquals(px(50), track.getChildAt(0).layoutParams.width)
        track.setFraction(1f)
        assertEquals(px(200), track.getChildAt(0).layoutParams.width)
        track.setFraction(0f)
        assertEquals(0, track.getChildAt(0).layoutParams.width)
    }

    // ---------- 5×5 矩阵编辑器 ----------

    @Test
    fun matrixEditor_fillsTaggedCells() {
        val grid = GridLayout(ctx())
        val matrix = Array(5) { BooleanArray(5) }
        buildMatrixEditor(grid, matrix) { _, _ -> }
        assertEquals(25, grid.childCount)
        // 每个 frame 的子 dot 带 tag dot_x_y 契约
        for (y in 0..4) for (x in 0..4) {
            val frame = grid.getChildAt(y * 5 + x)
            assertNotNull("frame($x,$y) 应有子视图", (frame as? ViewGroup)?.getChildAt(0))
            val dot = (frame as ViewGroup).getChildAt(0)
            assertEquals("dot_${x}_$y", dot.tag)
        }
        assertEquals(5, grid.columnCount)
        assertEquals(5, grid.rowCount)
    }

    @Test
    fun matrixEditor_initialMatrixColorsDots() {
        val grid = GridLayout(ctx())
        val matrix = rowsToMatrix(listOf("10000", "01000", "00100", "00010", "00001"))
        buildMatrixEditor(grid, matrix) { _, _ -> }
        fun dotAt(x: Int, y: Int): View =
            (grid.getChildAt(y * 5 + x) as ViewGroup).getChildAt(0)
        fun colorOf(v: View): Int =
            (v.background as android.graphics.drawable.GradientDrawable).color!!.defaultColor
        // 亮格 = micro:bit 琥珀；灭格 = 主题 SurfaceVariant（非琥珀）
        assertEquals(LED_ON_COLOR, colorOf(dotAt(0, 0)))
        assertEquals(LED_ON_COLOR, colorOf(dotAt(4, 4)))
        assertFalse(colorOf(dotAt(1, 0)) == LED_ON_COLOR)
    }

    @Test
    fun matrixEditor_toggleCallbackCoordinates() {
        val grid = GridLayout(ctx())
        val toggles = ArrayList<Pair<Int, Int>>()
        buildMatrixEditor(grid, Array(5) { BooleanArray(5) }) { x, y ->
            toggles.add(x to y)
        }
        // 点击第 3 行第 2 列（index 12 = (2,1)… 索引 12 → y=2, x=2）
        (grid.getChildAt(12) as ViewGroup).performClick()
        assertEquals(listOf(2 to 2), toggles)
    }

    @Test
    fun setMatrixCell_updatesBackground() {
        val grid = GridLayout(ctx())
        buildMatrixEditor(grid, Array(5) { BooleanArray(5) }) { _, _ -> }
        val dot = (grid.getChildAt(0) as ViewGroup).getChildAt(0)
        fun colorOf(): Int =
            (dot.background as android.graphics.drawable.GradientDrawable).color!!.defaultColor
        val off = colorOf()
        assertFalse(off == LED_ON_COLOR)
        setMatrixCell(grid, 0, 0, true)
        assertEquals(LED_ON_COLOR, colorOf())
        setMatrixCell(grid, 0, 0, false)
        assertFalse(colorOf() == LED_ON_COLOR)
    }

    // ---------- SensorPoller 兜底停止 ----------

    @Test
    fun sensorPoller_stopAllUnregisters() {
        val poller = SensorPoller(60_000L) // 长间隔，tick 不干扰本断言
        var snaps = 0
        poller.onSnapshot = { snaps++ }
        poller.start()
        ConnectionHub.debugEmitData("A:1|B:0|T:25.0|L:100")
        ShadowLooper.idleMainLooper()
        assertEquals(1, snaps)

        SensorPoller.stopAll()
        ConnectionHub.debugEmitData("A:0|B:1|T:26.0")
        ShadowLooper.idleMainLooper()
        assertEquals("stopAll 后不得再回调", 1, snaps)
    }

    @Test
    fun sensorPoller_parseReadLine() {
        val snap = SensorPoller.parse("A:1|B:0|T:25.5|L:100|X:10|Y:-20|Z:980|C:180|G:shake")
        assertNotNull(snap)
        assertEquals(true, snap!!.a)
        assertEquals(false, snap.b)
        assertEquals(25.5, snap.t!!, 1e-6)
        assertEquals(100, snap.l)
        assertEquals(10, snap.x)
        assertEquals(-20, snap.y)
        assertEquals(980, snap.z)
        assertEquals(180, snap.c)
        assertEquals("shake", snap.g)
    }

    @Test
    fun sensorPoller_parseRejectsNonReadLines() {
        // 非 READ 行必须返回 null（ERR: 回显 / MOUSE: 增量等不得误判为读数）
        assertEquals(null, SensorPoller.parse("ERR:unknown"))
        assertEquals(null, SensorPoller.parse("MOUSE:1,2,0"))
        assertEquals(null, SensorPoller.parse(""))
        assertEquals(null, SensorPoller.parse("OK"))
        // 容忍缺字段但必须含 A:
        val partial = SensorPoller.parse("A:1|T:20")
        assertNotNull(partial)
        assertEquals(null, partial!!.l)
        assertEquals(null, partial.g)
        // G:none 归一为 null
        assertEquals(null, SensorPoller.parse("A:0|G:none")!!.g)
    }
}

package com.lingyi.mbos.ui

import com.lingyi.mbos.util.PointerProfile
import com.lingyi.mbos.util.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** MouseEngine 纯逻辑单测：死区 / 灵敏度 / 曲线 / 按键映射 / 滚动 / 平滑。 */
class MouseEngineTest {

    private fun eng(p: PointerProfile) = MouseEngine { p }

    private fun moveOf(actions: List<MouseEngine.Action>): MouseEngine.Action.Move =
        actions.filterIsInstance<MouseEngine.Action.Move>().first()

    @Test
    fun deadzoneIgnoresSmallMoves() {
        val e = eng(PointerProfile(deadzone = 2f, sens = 1f))
        assertTrue("增量小于死区应无移动", e.consume(1, 0, false, false).none { it is MouseEngine.Action.Move })
        val m = moveOf(e.consume(5, 0, false, false))
        assertEquals(5f, m.dx, 0.001f)
    }

    @Test
    fun sensitivityMultipliesLinear() {
        val e = eng(PointerProfile(sens = 2f, curve = Prefs.POINTER_CURVE_LINEAR))
        val m = moveOf(e.consume(10, 0, false, false))
        assertEquals(20f, m.dx, 0.001f)
    }

    @Test
    fun exponentialCurveBoostsLargeMovesOnly() {
        val e = eng(PointerProfile(sens = 1f, curve = Prefs.POINTER_CURVE_EXP))
        val small = moveOf(e.consume(2, 0, false, false)).dx
        val big = moveOf(e.consume(20, 0, false, false)).dx
        assertTrue("指数曲线应大幅加速大倾斜，实际=$big", big > 25f)
        assertTrue("小幅移动应保持原速，实际=$small", small <= 2.5f)
    }

    @Test
    fun buttonAMapsToTapAndDouble() {
        val tap = eng(PointerProfile(btnA = Prefs.POINTER_ACTION_TAP)).consume(0, 0, true, false)
        assertTrue("A=单击 按下沿应派发 Tap", tap.any { it is MouseEngine.Action.Tap })

        val dbl = eng(PointerProfile(btnA = Prefs.POINTER_ACTION_DOUBLE)).consume(0, 0, true, false)
        assertTrue("A=双击 按下沿应派发 DoubleTap", dbl.any { it is MouseEngine.Action.DoubleTap })
    }

    @Test
    fun buttonDragTogglesFinger() {
        val e = eng(PointerProfile(btnB = Prefs.POINTER_ACTION_DRAG))
        val down = e.consume(0, 0, false, true)
        assertTrue("B 按下边应开始拖动", down.any { it is MouseEngine.Action.Finger && it.down })
        assertTrue(e.dragging)
        val up = e.consume(0, 0, false, false) // B 抬起边 → 结束拖动
        assertTrue("B 抬起边应结束拖动", up.any { it is MouseEngine.Action.Finger && !it.down })
        assertFalse(e.dragging)
    }

    @Test
    fun scrollModeIgnoresHorizontal() {
        val e = eng(PointerProfile(scrollMode = true, sens = 1f))
        val m = moveOf(e.consume(10, 8, false, false))
        assertEquals(0f, m.dx, 0.001f)
        assertTrue("滚动模式纵向应生效", m.dy != 0f)
    }

    @Test
    fun smoothingLowPassReducesJitter() {
        val e = eng(PointerProfile(smoothing = 0.5f, sens = 1f))
        e.consume(10, 0, false, false)
        val second = moveOf(e.consume(10, 0, false, false))
        assertTrue("低通应让第二帧小于原始值，实际=${second.dx}", second.dx < 10f && second.dx > 5f)
    }
}

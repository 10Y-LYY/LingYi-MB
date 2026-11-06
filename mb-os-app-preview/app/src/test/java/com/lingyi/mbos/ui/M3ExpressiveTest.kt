/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

import android.content.Context
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.lingyi.mbos.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * M3 Expressive 交互测试（4.1.1-preview）：
 * 核心回归——按压反馈的触摸监听不得消费事件（v4.1.0 悬浮窗「点不动」根因：
 * OnTouchListener 返回 true 会吞掉 OnClickListener）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = com.lingyi.mbos.MBOSApp::class)
class M3ExpressiveTest {

    

    private fun tap(view: android.view.View) {
        // 需要真实 attach 到 window，未 attach 的 View 点击会进 RunQueue 不执行
        val activity = org.robolectric.Robolectric
            .buildActivity(android.app.Activity::class.java)
            .setup()
            .get()
        activity.setContentView(view)
        val now = android.os.SystemClock.uptimeMillis()
        view.dispatchTouchEvent(
            MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 20f, 20f, 0)
        )
        view.dispatchTouchEvent(
            MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, 20f, 20f, 0)
        )
        // 排空主线程消息，让 post 的 performClick 执行
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
    }

    @Test
    fun plainTextView_clickWorks() {
        // 基线：不挂任何反馈时点击必须生效（验证 tap() 模拟本身有效）
        val view = TextView(ApplicationProvider.getApplicationContext()).apply { isClickable = true }
        var clicked = false
        view.setOnClickListener { clicked = true }
        tap(view)
        assertTrue(clicked)
    }

    @Test
    fun pressFeedback_doesNotSwallowClick() {
        val view = TextView(ApplicationProvider.getApplicationContext()).apply {
            isClickable = true
            setOnClickListener { }
        }
        var clicked = false
        view.setOnClickListener { clicked = true }
        M3Kit.pressFeedback(view)
        tap(view)
        // 关键断言：挂了按压反馈后点击依然生效（不得被触摸监听吞掉）
        assertTrue("按压反馈不得消费点击事件", clicked)
    }

    @Test
    fun pressFeedback_marksTagForDedup() {
        val view = TextView(ApplicationProvider.getApplicationContext()).apply { isClickable = true }
        M3Kit.pressFeedback(view)
        assertTrue(view.getTag(R.id.tag_press_feedback) == true)
        // 重复挂载不会叠加第二个监听（tag 去重）
        M3Kit.pressFeedback(view)
        assertTrue(view.getTag(R.id.tag_press_feedback) == true)
    }

    @Test
    fun touchListenerConsumingDown_swallowsClick_regressionGuard() {
        // 反证（v4.1.0 悬浮窗「点不动」根因锁死）：
        // OnTouchListener 消费 ACTION_DOWN 时，onTouchEvent 不执行 → OnClickListener 失效。
        // 任何拖动实现都不得回到这个模式。
        val view = TextView(ApplicationProvider.getApplicationContext()).apply { isClickable = true }
        var clicked = false
        view.setOnClickListener { clicked = true }
        view.setOnTouchListener { _, _ -> true }
        tap(view)
        assertFalse(clicked)
    }

    @Test
    fun applyPressFeedback_recursesIntoTree() {
        val root = LinearLayout(ApplicationProvider.getApplicationContext())
        val child = LinearLayout(ApplicationProvider.getApplicationContext()).apply { isClickable = true }
        val grandChild = TextView(ApplicationProvider.getApplicationContext()).apply { isClickable = true }
        val plain = TextView(ApplicationProvider.getApplicationContext()) // 不可点击，不挂
        child.addView(grandChild)
        root.addView(child)
        root.addView(plain)
        M3Kit.applyPressFeedback(root)
        assertFalse(root.getTag(R.id.tag_press_feedback) == true) // 根不可点击，不挂
        assertTrue(child.getTag(R.id.tag_press_feedback) == true)
        assertTrue(grandChild.getTag(R.id.tag_press_feedback) == true)
        assertFalse(plain.getTag(R.id.tag_press_feedback) == true)
    }

    @Test
    fun applyPressFeedback_skipsEditText() {
        val root = LinearLayout(ApplicationProvider.getApplicationContext())
        val edit = android.widget.EditText(ApplicationProvider.getApplicationContext()).apply { isClickable = true }
        root.addView(edit)
        M3Kit.applyPressFeedback(root)
        assertFalse(edit.getTag(R.id.tag_press_feedback) == true)
    }

    @Test
    fun springIn_startsFadedAndScaled() {
        val view = TextView(ApplicationProvider.getApplicationContext())
        Transitions.springIn(view)
        assertEquals(0f, view.alpha, 0.001f)
        assertEquals(0.92f, view.scaleX, 0.001f)
    }

    @Test
    fun springInCards_touchesEveryChild() {
        val root = LinearLayout(ApplicationProvider.getApplicationContext())
        repeat(3) { root.addView(TextView(ApplicationProvider.getApplicationContext())) }
        Transitions.springInCards(root)
        for (i in 0 until root.childCount) {
            assertEquals(0f, root.getChildAt(i).alpha, 0.001f)
        }
    }

    @Test
    fun springIn_respectsAnimationDisabled() {
        com.lingyi.mbos.util.Prefs.setAnimationEnabled(ApplicationProvider.getApplicationContext(), false)
        val view = TextView(ApplicationProvider.getApplicationContext()).apply { alpha = 1f }
        Transitions.springIn(view)
        // 关闭动画时不得改动视图状态
        assertEquals(1f, view.alpha, 0.001f)
        com.lingyi.mbos.util.Prefs.setAnimationEnabled(ApplicationProvider.getApplicationContext(), true)
    }
}

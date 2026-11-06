/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos

import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.lingyi.mbos.ui.MaxWidthFrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider

/**
 * 折叠屏 / 平板大屏适配回归（#112）：
 * MaxWidthFrameLayout 必须在超宽屏上把内容封顶并居中，窄屏则零副作用。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = MBOSApp::class)
class MaxWidthFrameLayoutTest {

    private fun dp(v: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v,
        ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics
    ).toInt()

    /** 构建：容器（封顶 840dp）+ 一个 match_parent 宽的占位子视图（center_horizontal）。 */
    private fun buildProbe(widthDp: Int): Pair<MaxWidthFrameLayout, View> {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val container = MaxWidthFrameLayout(ctx)
        container.setMaxWidth(dp(840f))
        val child = View(ctx)
        container.addView(
            child,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(8f)
            ).apply { gravity = Gravity.CENTER_HORIZONTAL }
        )
        container.measure(
            View.MeasureSpec.makeMeasureSpec(dp(widthDp.toFloat()), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        container.layout(0, 0, container.measuredWidth, container.measuredHeight)
        return container to child
    }

    @Test
    fun capsChildAt840dpOnWideScreen() {
        val (container, child) = buildProbe(1000)
        assertEquals("超宽屏上子内容应封顶 840dp", dp(840f), child.measuredWidth)
        // 封顶后应居中：左侧留白 == 右侧留白
        val gutter = (container.measuredWidth - child.measuredWidth) / 2
        assertEquals("封顶内容应水平居中（左缘）", gutter, child.left)
        assertEquals("封顶内容应水平居中（右缘）", container.measuredWidth - gutter, child.right)
    }

    @Test
    fun containerStillFillsParent() {
        val (container, _) = buildProbe(1000)
        assertEquals(
            "容器本身仍撑满父宽度（留白由容器内居中吸收）",
            dp(1000f),
            container.measuredWidth
        )
    }

    @Test
    fun narrowScreenUnaffected() {
        val (_, child) = buildProbe(400)
        assertEquals("窄屏（<840dp）子内容应原样铺满，不做封顶", dp(400f), child.measuredWidth)
    }

    @Test
    fun atMostSpecNeverNarrowsContainerBelowChild() {
        // 回归（v3.1.0 引导页事故）：AT_MOST 等非 EXACTLY 模式下，容器绝不允许窄于已封顶的子内容，
        // 否则内容会被裁切/错位（引导页曾直接把本容器当根，遇到异常 MeasureSpec 时中招）。
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val container = MaxWidthFrameLayout(ctx)
        container.setMaxWidth(dp(840f))
        val child = View(ctx)
        container.addView(
            child,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(8f)
            ).apply { gravity = Gravity.CENTER_HORIZONTAL }
        )
        container.measure(
            View.MeasureSpec.makeMeasureSpec(dp(1000f), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        container.layout(0, 0, container.measuredWidth, container.measuredHeight)
        assertTrue(
            "AT_MOST 下容器宽度（${container.measuredWidth}）不得窄于子内容（${child.measuredWidth}）",
            container.measuredWidth >= child.measuredWidth
        )
        assertEquals("AT_MOST 下容器即取封顶宽度", dp(840f), container.measuredWidth)
    }

    @Test
    fun zeroMaxWidthDisablesCapping() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val container = MaxWidthFrameLayout(ctx)
        val child = View(ctx)
        container.addView(
            child,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(8f)
            ).apply { gravity = Gravity.CENTER_HORIZONTAL }
        )
        container.measure(
            View.MeasureSpec.makeMeasureSpec(dp(1000f), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        assertEquals("未设置封顶值时不限制子内容宽度", dp(1000f), child.measuredWidth)
    }

    @Test
    fun styleableAttrNameIsStable() {
        // 锁死 XML 属性名：declare-styleable 改名会导致编译失败，防止大屏适配静默失效
        assertTrue(R.styleable.MaxWidthFrameLayout_maxWidth >= 0)
        assertTrue(R.styleable.MaxWidthFrameLayout.size > 0)
    }
}

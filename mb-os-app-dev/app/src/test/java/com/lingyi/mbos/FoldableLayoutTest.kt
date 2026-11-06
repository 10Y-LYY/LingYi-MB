/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import androidx.test.core.app.ApplicationProvider
import com.lingyi.mbos.ui.MaxWidthFrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 折叠屏 / 平板集成回归（#112）：
 * 在「折叠屏内屏 / 平板」尺寸（sw1000dp）下，各页面必须能正常 inflate，
 * 且滚动内容被 MaxWidthFrameLayout 封顶 840dp 居中，而不是拉满全宽。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = MBOSApp::class)
class FoldableLayoutTest {

    private fun dp(v: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v,
        ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics
    ).toInt()

    /** 深度优先查找第一个指定类型的子视图。 */
    private fun <T : View> findFirst(root: View, cls: Class<T>): T? {
        if (cls.isInstance(root)) return cls.cast(root)
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findFirst<T>(root.getChildAt(i), cls)?.let { return it }
            }
        }
        return null
    }

    /** 断言：页面可 inflate，且滚动容器宽度 == 封顶值 840dp（居中留白由容器吸收）。 */
    private fun assertContentCapped(activityClass: Class<out android.app.Activity>) {
        val activity = Robolectric.buildActivity(activityClass).setup().get()
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val cap = findFirst(content, MaxWidthFrameLayout::class.java)
        assertTrue("${activityClass.simpleName} 应包含 MaxWidthFrameLayout 内容封顶容器", cap != null)
        val scroll = findFirst(content, NestedScrollView::class.java)
            ?: findFirst(content, ScrollView::class.java)
        assertTrue("${activityClass.simpleName} 应包含滚动容器", scroll != null)
        // 测量后再断言宽度：折叠内屏 1000dp 宽，内容应封顶 840dp
        cap!!.measure(
            View.MeasureSpec.makeMeasureSpec(dp(1000f), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        assertEquals(
            "${activityClass.simpleName} 滚动内容应封顶 840dp",
            dp(840f),
            scroll!!.measuredWidth
        )
    }

    @Test
    @Config(qualifiers = "sw1000dp-w1000dp-h900dp")
    fun guideContentCappedOnFoldable() = assertContentCapped(GuideActivity::class.java)

    @Test
    @Config(qualifiers = "sw1000dp-w1000dp-h900dp")
    fun settingsContentCappedOnFoldable() = assertContentCapped(SettingsActivity::class.java)

    @Test
    @Config(qualifiers = "sw1000dp-w1000dp-h900dp")
    fun previewContentCappedOnFoldable() = assertContentCapped(PreviewActivity::class.java)

    @Test
    @Config(qualifiers = "sw1000dp-w1000dp-h900dp")
    fun mainContentCappedOnFoldable() = assertContentCapped(MainActivity::class.java)

    @Test
    @Config(qualifiers = "sw1000dp-w1000dp-h900dp")
    fun devContentCappedOnFoldable() = assertContentCapped(DeveloperActivity::class.java)

    @Test
    @Config(qualifiers = "sw840dp-w900dp-h900dp")
    fun ledCellsGrowOnExpandedScreens() {
        // Expanded 档（sw840dp+）LED 画板格子应放大到 64dp，小屏仍是 44dp
        val res = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        assertEquals("expanded 档 LED 格子应为 64dp", dp(64f), res.getDimensionPixelSize(R.dimen.led_cell))
        assertEquals(
            "expanded 档 LED 间距应为 5dp",
            dp(5f),
            res.getDimensionPixelSize(R.dimen.led_cell_margin)
        )
    }

    @Test
    @Config(qualifiers = "sw600dp-w800dp-h900dp")
    fun ledCellsOnMediumScreens() {
        // Medium 档（600–840dp，折叠屏内屏 / 小平板）沿用 sw600dp 档：56dp
        val res = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        assertEquals("medium 档 LED 格子应为 56dp", dp(56f), res.getDimensionPixelSize(R.dimen.led_cell))
    }
}

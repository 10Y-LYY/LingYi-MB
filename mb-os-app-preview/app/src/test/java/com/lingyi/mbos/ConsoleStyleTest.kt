/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos

import android.content.Context
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.card.MaterialCardView
import com.lingyi.mbos.ui.FeatureCatalog
import com.lingyi.mbos.util.Prefs
import com.lingyi.mbos.util.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 控制台展示风格（列表 / 大方格）与预览版（beta）更新通道。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = MBOSApp::class)
class ConsoleStyleTest {

    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    // ---------- 偏好：控制台展示风格 ----------

    @Test
    fun consoleStyleDefaultsToList() {
        assertEquals(Prefs.CONSOLE_STYLE_LIST, Prefs.getConsoleStyle(ctx()))
    }

    @Test
    fun consoleStyleRoundTripAndResetRestoresList() {
        val ctx = ctx()
        Prefs.setConsoleStyle(ctx, Prefs.CONSOLE_STYLE_GRID)
        assertEquals(Prefs.CONSOLE_STYLE_GRID, Prefs.getConsoleStyle(ctx))
        Prefs.resetAppearanceSettings(ctx)
        // 恢复默认 → 列表（不改变既有用户的展示习惯）
        assertEquals(Prefs.CONSOLE_STYLE_LIST, Prefs.getConsoleStyle(ctx))
    }

    @Test
    fun consoleStyleSetterRejectsGarbage() {
        val ctx = ctx()
        Prefs.setConsoleStyle(ctx, "nonsense")
        assertEquals(Prefs.CONSOLE_STYLE_LIST, Prefs.getConsoleStyle(ctx))
    }

    // ---------- 偏好：预览测试通道 ----------

    @Test
    fun betaChannelDefaultsOff() {
        assertEquals("默认应为当前 flavor 的默认通道", com.lingyi.mbos.BuildConfig.DEFAULT_CHANNEL, Prefs.getChannel(ctx()))
    }

    @Test
    fun betaChannelRoundTripAndSurvivesAppearanceReset() {
        val ctx = ctx()
        Prefs.setChannel(ctx, com.lingyi.mbos.util.UpdateChecker.CHANNEL_PREVIEW)
        assertEquals(com.lingyi.mbos.util.UpdateChecker.CHANNEL_PREVIEW, Prefs.getChannel(ctx))
        // 通道属开发者测试开关，不随「恢复默认外观」被清掉
        Prefs.resetAppearanceSettings(ctx)
        assertEquals(com.lingyi.mbos.util.UpdateChecker.CHANNEL_PREVIEW, Prefs.getChannel(ctx))
        Prefs.setChannel(ctx, com.lingyi.mbos.util.UpdateChecker.CHANNEL_STABLE)
        assertEquals(com.lingyi.mbos.util.UpdateChecker.CHANNEL_STABLE, Prefs.getChannel(ctx))
    }

    // ---------- 更新通道 URL ----------

    @Test
    fun stableAndBetaVersionUrlsAreDistinct() {
        assertTrue("两个通道必须是不同文件", UpdateChecker.VERSION_URL != UpdateChecker.VERSION_BETA_URL)
        assertTrue(UpdateChecker.VERSION_URL.endsWith("/version.json"))
        assertTrue(UpdateChecker.VERSION_BETA_URL.endsWith("/version-beta.json"))
    }

    @Test
    fun updateJumpTargetsReleasePageNotRawJson() {
        // 「前往更新」的兜底目标必须是发布页：version.json 是给程序读的，
        // 浏览器直接打开只会看到原始 JSON（v3.3.0 线上实测缺陷的回归锁定）
        assertTrue(
            "PAGE_URL 不应是版本 JSON 文件",
            !UpdateChecker.PAGE_URL.endsWith(".json")
        )
        assertTrue(
            "PAGE_URL 应与版本文件同源同目录",
            UpdateChecker.PAGE_URL.startsWith(
                UpdateChecker.VERSION_URL.removeSuffix("version.json")
            )
        )
        assertTrue("PAGE_URL 应为站点根路径", UpdateChecker.PAGE_URL.endsWith("/"))
        assertTrue(
            "PAGE_URL 不得与版本文件地址相同",
            UpdateChecker.PAGE_URL != UpdateChecker.VERSION_URL
        )
    }

    // ---------- 功能目录 ----------

    @Test
    fun featureCatalogCoversAllCategories() {
        // 77 个功能、7 个分类，每类非空，id 不重复
        assertEquals(77, FeatureCatalog.FEATURES.size)
        val ids = FeatureCatalog.FEATURES.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (category in FeatureCatalog.Category.entries) {
            assertTrue("分类 ${category.key} 为空", FeatureCatalog.byCategory(category).isNotEmpty())
        }
        // 分类计数合计 = 总数
        val sum = FeatureCatalog.Category.entries.sumOf { FeatureCatalog.byCategory(it).size }
        assertEquals(FeatureCatalog.FEATURES.size, sum)
    }

    @Test
    fun categoryOfFallsBackForUnknownKey() {
        assertEquals(FeatureCatalog.Category.DISPLAY, FeatureCatalog.categoryOf(null))
        assertEquals(FeatureCatalog.Category.DISPLAY, FeatureCatalog.categoryOf("garbage"))
        assertEquals(FeatureCatalog.Category.SENSOR, FeatureCatalog.categoryOf("sensor"))
    }

    // ---------- 大方格渲染 ----------

    @Test
    @Config(qualifiers = "sw600dp")
    fun gridStyleRendersOneTilePerCategoryOnTabletWidth() {
        val ctx = ctx()
        Prefs.setConsoleStyle(ctx, Prefs.CONSOLE_STYLE_GRID)
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().get()
        val list = activity.findViewById<LinearLayout>(com.lingyi.mbos.R.id.funcList)
        // 大方格：外层横排 3 列容器（sw600dp 档），列内竖排分类卡
        assertEquals(1, list.childCount)
        val grid = list.getChildAt(0) as LinearLayout
        assertEquals(LinearLayout.HORIZONTAL, grid.orientation)
        assertEquals(3, grid.childCount)
        var tiles = 0
        for (i in 0 until grid.childCount) {
            val col = grid.getChildAt(i) as LinearLayout
            assertEquals(LinearLayout.VERTICAL, col.orientation)
            assertTrue("每列至少一张分类卡", col.childCount > 0)
            for (j in 0 until col.childCount) {
                assertTrue("分类卡应为 MaterialCardView", col.getChildAt(j) is MaterialCardView)
                tiles++
            }
        }
        assertEquals(FeatureCatalog.Category.entries.size, tiles)
    }

    @Test
    @Config(qualifiers = "sw400dp")
    fun gridStyleUsesTwoColumnsOnPhoneWidth() {
        val ctx = ctx()
        Prefs.setConsoleStyle(ctx, Prefs.CONSOLE_STYLE_GRID)
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().get()
        val list = activity.findViewById<LinearLayout>(com.lingyi.mbos.R.id.funcList)
        assertEquals(1, list.childCount)
        val grid = list.getChildAt(0) as LinearLayout
        assertEquals(2, grid.childCount)
        var tiles = 0
        for (i in 0 until grid.childCount) {
            tiles += (grid.getChildAt(i) as LinearLayout).childCount
        }
        assertEquals(FeatureCatalog.Category.entries.size, tiles)
    }

    @Test
    @Config(qualifiers = "sw600dp")
    fun gridTileHasNonZeroWidthInVerticalColumn() {
        // 回归 v3.2.0-preview 缺陷：分类卡根视图曾沿用 width=0dp+weight=1，
        // 被 addView 进纵向列容器后 weight 不作用于宽度，整张卡宽度为 0 不可见
        //（用户看到「功能」区只剩一张巨大空卡）。此后必须显式覆盖 LayoutParams。
        val ctx = ctx()
        Prefs.setConsoleStyle(ctx, Prefs.CONSOLE_STYLE_GRID)
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().get()
        val list = activity.findViewById<LinearLayout>(com.lingyi.mbos.R.id.funcList)
        val grid = list.getChildAt(0) as LinearLayout
        for (i in 0 until grid.childCount) {
            val col = grid.getChildAt(i) as LinearLayout
            for (j in 0 until col.childCount) {
                val lp = col.getChildAt(j).layoutParams as LinearLayout.LayoutParams
                assertEquals(
                    "分类卡在纵向列内容器里宽度必须非 0",
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    lp.width
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "sw600dp")
    fun gridTileShowsCategoryTitleAndCount() {
        val ctx = ctx()
        Prefs.setConsoleStyle(ctx, Prefs.CONSOLE_STYLE_GRID)
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().get()
        val list = activity.findViewById<LinearLayout>(com.lingyi.mbos.R.id.funcList)
        val grid = list.getChildAt(0) as LinearLayout
        val firstCol = grid.getChildAt(0) as LinearLayout
        val tile = firstCol.getChildAt(0) as MaterialCardView
        val title = tile.findViewById<android.widget.TextView>(com.lingyi.mbos.R.id.tvTitle)
        val count = tile.findViewById<android.widget.TextView>(com.lingyi.mbos.R.id.tvCount)
        assertEquals("显示与预览", title.text.toString())
        // 14 个功能（显示与预览类；时钟 / 秒表 / 倒计时已移除）
        assertTrue(count.text.toString().contains("14"))
    }

    // ---------- 二级挑选页 ----------

    @Test
    fun categoryPageListsFeaturesAndReturnsPickedId() {
        // 无 extra 时回落显示与预览类（categoryOf 兜底）
        val activity = Robolectric.buildActivity(ConsoleCategoryActivity::class.java).create().get()
        assertNotNull(activity)
        val list = activity.findViewById<LinearLayout>(com.lingyi.mbos.R.id.funcList)
        // 14 个功能行 + 13 条分隔线
        assertEquals(27, list.childCount)
        // 点第一行（预览模式）→ 回抛 EXTRA_FEATURE_ID 后关闭
        list.getChildAt(0).performClick()
        assertTrue("选中后应关闭二级页", activity.isFinishing)
        val shadow = shadowOf(activity)
        assertEquals(android.app.Activity.RESULT_OK, shadow.resultCode)
        assertEquals("preview", shadow.resultIntent.getStringExtra(ConsoleCategoryActivity.EXTRA_FEATURE_ID))
    }

    @Test
    fun categoryPageTitleReflectsCategory() {
        val activity = Robolectric.buildActivity(ConsoleCategoryActivity::class.java).create().get()
        val toolbar = activity.findViewById<com.google.android.material.appbar.MaterialToolbar>(
            com.lingyi.mbos.R.id.toolbar
        )
        assertEquals("显示与预览", toolbar.title.toString())
        assertNotNull(toolbar.subtitle)
        assertTrue(toolbar.subtitle.toString().contains("14"))
    }
}

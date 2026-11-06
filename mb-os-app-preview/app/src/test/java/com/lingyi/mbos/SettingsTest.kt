/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lingyi.mbos.util.Prefs
import com.lingyi.mbos.util.ThemeManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = MBOSApp::class)
class SettingsTest {

    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    // ---------- 主题模式 ----------

    @Test
    fun nightModeMapsPreferenceToAppCompatConstants() {
        Prefs.setThemeMode(ctx(), ThemeManager.MODE_SYSTEM)
        assertEquals(
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM.toLong(),
            ThemeManager.nightMode(ctx()).toLong()
        )
        Prefs.setThemeMode(ctx(), ThemeManager.MODE_LIGHT)
        assertEquals(
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO.toLong(),
            ThemeManager.nightMode(ctx()).toLong()
        )
        Prefs.setThemeMode(ctx(), ThemeManager.MODE_DARK)
        assertEquals(
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES.toLong(),
            ThemeManager.nightMode(ctx()).toLong()
        )
    }

    @Test
    fun invalidThemeModeFallsBackToSystem() {
        Prefs.setThemeMode(ctx(), "garbage")
        assertEquals(ThemeManager.MODE_SYSTEM, ThemeManager.normalizeMode(Prefs.getThemeMode(ctx())))
    }

    // ---------- 主题色 ----------

    @Test
    fun themeColorNormalizesAndMapsToOverlays() {
        // M3 官方取色为默认与兜底
        assertEquals(ThemeManager.COLOR_M3, ThemeManager.normalizeColor(null))
        assertEquals(ThemeManager.COLOR_M3, ThemeManager.normalizeColor("nope"))
        // M3 官方即基础主题，无需覆盖层
        assertEquals(0, ThemeManager.themeOverlayRes(ThemeManager.COLOR_M3))
        // 青绿降级为覆盖层（老用户保留）
        assertTrue(ThemeManager.themeOverlayRes(ThemeManager.COLOR_TEAL) != 0)
        // 其余配色各有覆盖层
        assertTrue(ThemeManager.themeOverlayRes(ThemeManager.COLOR_GOLD) != 0)
        assertTrue(ThemeManager.themeOverlayRes(ThemeManager.COLOR_INDIGO) != 0)
        assertTrue(ThemeManager.themeOverlayRes(ThemeManager.COLOR_ROSE) != 0)
        assertTrue(ThemeManager.themeOverlayRes(ThemeManager.COLOR_FOREST) != 0)
        // 动态取色不走覆盖层（由 DynamicColors 处理）
        assertEquals(0, ThemeManager.themeOverlayRes(ThemeManager.COLOR_DYNAMIC))
    }

    @Test
    fun themeColorDefaultsToM3AndResetRestoresIt() {
        val ctx = ctx()
        Prefs.setThemeColor(ctx, ThemeManager.COLOR_GOLD)
        assertEquals(ThemeManager.COLOR_GOLD, Prefs.getThemeColor(ctx))
        Prefs.resetAppearanceSettings(ctx)
        // 恢复默认 → M3 官方取色
        assertEquals(ThemeManager.COLOR_M3, Prefs.getThemeColor(ctx))
    }

    // ---------- 主题纪元（全局重建信号） ----------

    @Test
    fun themeEpochBumpsAndPersists() {
        val ctx = ctx()
        val before = Prefs.getThemeEpoch(ctx)
        Prefs.bumpThemeEpoch(ctx)
        assertEquals(before + 1L, Prefs.getThemeEpoch(ctx))
        Prefs.bumpThemeEpoch(ctx)
        assertEquals(before + 2L, Prefs.getThemeEpoch(ctx))
    }

    @Test
    fun animationEnabledByDefaultAndResetRestoresIt() {
        val ctx = ctx()
        assertTrue(Prefs.isAnimationEnabled(ctx)) // 默认开
        Prefs.setAnimationEnabled(ctx, false)
        assertTrue(!Prefs.isAnimationEnabled(ctx))
        Prefs.resetAppearanceSettings(ctx)
        assertTrue(Prefs.isAnimationEnabled(ctx)) // 恢复默认 → 开
    }

    // ---------- 连接 / 通用 ----------

    @Test
    fun readIntervalClampsInvalidValues() {
        Prefs.setReadIntervalMs(ctx(), 2000L)
        assertEquals(2000L, Prefs.getReadIntervalMs(ctx()))
        Prefs.setReadIntervalMs(ctx(), 0L)
        assertEquals(1000L, Prefs.getReadIntervalMs(ctx()))
        Prefs.setReadIntervalMs(ctx(), -5L)
        assertEquals(1000L, Prefs.getReadIntervalMs(ctx()))
    }

    @Test
    fun appearanceSettingsRoundTripAndReset() {
        val ctx = ctx()
        Prefs.setThemeColor(ctx, ThemeManager.COLOR_GOLD)
        Prefs.setKeepScreenOn(ctx, true)
        Prefs.setHapticEnabled(ctx, false)

        assertEquals(ThemeManager.COLOR_GOLD, Prefs.getThemeColor(ctx))
        assertTrue(Prefs.isKeepScreenOn(ctx))
        assertTrue(!Prefs.isHapticEnabled(ctx))

        Prefs.resetAppearanceSettings(ctx)
        assertEquals(ThemeManager.COLOR_M3, Prefs.getThemeColor(ctx))
        assertTrue(!Prefs.isKeepScreenOn(ctx))
        assertTrue(Prefs.isHapticEnabled(ctx))
    }

    // ---------- 页面 ----------

    @Test
    fun settingsActivityLaunchesAndReflectsPreferences() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).create().get()
        assertNotNull(activity)
        // 指针灵敏度滑杆反映偏好值（读取时已吸附到网格）
        val slider = activity.findViewById<com.google.android.material.slider.Slider>(
            com.lingyi.mbos.R.id.sliderPointerSens
        )
        assertTrue(slider.value >= 0.4f && slider.value <= 3.0f)
    }

    @Test
    @Config(qualifiers = "sw600dp")
    fun functionListUsesTwoColumnsOnTabletWidth() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().get()
        val list = activity.findViewById<android.widget.LinearLayout>(com.lingyi.mbos.R.id.funcList)
        assertNotNull(list)
        // 平板宽度：外层包一层横向两列容器，列内再竖排功能行
        assertEquals(1, list.childCount)
        val grid = list.getChildAt(0) as android.widget.LinearLayout
        assertEquals(android.widget.LinearLayout.HORIZONTAL, grid.orientation)
        assertEquals(2, grid.childCount)
        for (i in 0 until grid.childCount) {
            val col = grid.getChildAt(i) as android.widget.LinearLayout
            assertEquals(android.widget.LinearLayout.VERTICAL, col.orientation)
            assertTrue(col.childCount > 0)
        }
    }

    @Test
    @Config(qualifiers = "sw400dp")
    fun functionListUsesSingleColumnOnPhoneWidth() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().get()
        val list = activity.findViewById<android.widget.LinearLayout>(com.lingyi.mbos.R.id.funcList)
        assertNotNull(list)
        // 手机宽度：功能行与分隔线直接铺在容器里（不存在外层两列包装容器）
        assertTrue(list.childCount > 1)
    }
}

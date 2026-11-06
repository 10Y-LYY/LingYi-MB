/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos

import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.lingyi.mbos.util.Prefs
import com.lingyi.mbos.util.ThemeManager
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 覆盖「设置页内切换主题 / 连接 / 屏幕常亮」等交互路径（含 recreate 重建）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = MBOSApp::class)
class SettingsInteractionTest {

    private fun settings(): SettingsActivity =
        Robolectric.buildActivity(SettingsActivity::class.java).setup().get()

    private fun SettingsActivity.mode(): ChipGroup = findViewById(R.id.cgThemeMode)
    private fun SettingsActivity.color(): ChipGroup = findViewById(R.id.cgThemeColor)
    private fun SettingsActivity.connChips(): ChipGroup = findViewById(R.id.cgDefaultConn)
    private fun SettingsActivity.intervalChips(): ChipGroup = findViewById(R.id.cgReadInterval)
    private fun SettingsActivity.keepOnSwitch(): MaterialSwitch = findViewById(R.id.swKeepScreenOn)
    private fun SettingsActivity.hapticSwitch(): MaterialSwitch = findViewById(R.id.swHaptic)
    private fun SettingsActivity.sensSlider(): Slider = findViewById(R.id.sliderPointerSens)

    @Test
    fun switchingThemeModeSurvivesRecreate() {
        val a = settings()
        a.mode().check(R.id.chipModeDark)
        a.mode().check(R.id.chipModeLight)
        a.mode().check(R.id.chipModeSystem)
        assertNotNull(a)
    }

    @Test
    fun switchingThemeColorSurvivesRecreate() {
        val a = settings()
        a.color().check(R.id.chipColorM3)
        a.color().check(R.id.chipColorGold)
        a.color().check(R.id.chipColorDynamic)
        a.color().check(R.id.chipColorForest)
        a.color().check(R.id.chipColorTeal)
        assertNotNull(a)
    }

    @Test
    fun togglingConnectionAndGeneralControls() {
        val a = settings()
        a.connChips().check(R.id.chipConnUsb)
        a.intervalChips().check(R.id.chipInterval5s)
        a.keepOnSwitch().isChecked = true
        a.keepOnSwitch().isChecked = false
        a.hapticSwitch().isChecked = false
        a.sensSlider().value = 2.6f
        assertNotNull(a)
    }

    @Test
    fun resetAppearanceKeepsActivityUsable() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        Prefs.setThemeColor(ctx, ThemeManager.COLOR_ROSE)
        Prefs.setKeepScreenOn(ctx, true)
        val a = settings()
        assertNotNull(a)
        Prefs.resetAppearanceSettings(ctx)
        // 重置后再进一次设置页
        val b = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        assertNotNull(b)
    }

    @Test
    fun legacyOffGridSensitivityDoesNotCrashSettings() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        // 历史 / 异常值：1.53 不在 0.4+n×0.2 网格上（Slider 直接设值会抛异常杀进程）
        ctx.getSharedPreferences("mbos_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putFloat("pointer_sensitivity", 1.53f).apply()
        val a = settings()
        val slider = a.findViewById<Slider>(R.id.sliderPointerSens)
        assertTrue(slider.value >= 0.4f && slider.value <= 3.0f)
        assertNotNull(a)
    }
}

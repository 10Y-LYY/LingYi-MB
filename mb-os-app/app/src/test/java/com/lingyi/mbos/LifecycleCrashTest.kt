/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos

import com.lingyi.mbos.util.Prefs
import com.lingyi.mbos.util.ThemeManager
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 复现「点进设置页 / 其他页面闪退回引导页」：完整生命周期（create → start → resume）
 * 逐一驱动全部页面，覆盖各类外观偏好组合。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = MBOSApp::class)
class LifecycleCrashTest {

    private val activities = listOf(
        MainActivity::class.java,
        SettingsActivity::class.java,
        AboutActivity::class.java,
        PreviewActivity::class.java,
        GuideActivity::class.java,
        MakeCodeActivity::class.java,
        CustomFlashActivity::class.java,
        PointerModeActivity::class.java,
        DeveloperActivity::class.java,
        LicenseActivity::class.java
    )

    private fun driveAll(label: String) {
        activities.forEach { cls ->
            val controller = Robolectric.buildActivity(cls)
            controller.setup() // create + start + resume
            val activity = controller.get()
            assertNotNull("$label: ${cls.simpleName} 启动失败", activity)
        }
    }

    @Test
    fun allActivitiesSurviveFullLifecycleWithDefaults() {
        driveAll("默认偏好")
    }

    @Test
    fun allActivitiesSurviveWithDynamicColor() {
        Prefs.setThemeColor(org.robolectric.RuntimeEnvironment.getApplication(), ThemeManager.COLOR_DYNAMIC)
        driveAll("动态取色")
    }

    @Test
    fun allActivitiesSurviveWithDarkModeAndKeepScreenOn() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        Prefs.setThemeMode(ctx, ThemeManager.MODE_DARK)
        Prefs.setKeepScreenOn(ctx, true)
        driveAll("深色+屏幕常亮")
    }

    @Test
    fun allActivitiesSurviveWithGoldOverlay() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        Prefs.setThemeColor(ctx, ThemeManager.COLOR_GOLD)
        driveAll("鎏金覆盖层")
    }

    @Test
    @Config(qualifiers = "sw600dp")
    fun allActivitiesSurviveTabletWidth() {
        driveAll("平板宽度")
    }

    @Test
    @Config(qualifiers = "sw400dp")
    fun allActivitiesSurvivePhoneWidth() {
        driveAll("手机宽度")
    }
}

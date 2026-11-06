package com.lingyi.mbos

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lingyi.mbos.ui.MicrobitGames
import com.lingyi.mbos.ui.Protocol
import com.lingyi.mbos.util.Prefs
import com.lingyi.mbos.util.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = MBOSApp::class)
class SmokeTest {

    @Test
    fun guideActivityLaunches() {
        val activity = Robolectric.buildActivity(GuideActivity::class.java).create().get()
        assertEquals("com.lingyi.mbos", activity.packageName)
    }

    @Test
    fun mainActivityLaunches() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().get()
        assertEquals("com.lingyi.mbos", activity.packageName)
    }

    @Test
    fun previewActivityLaunches() {
        val activity = Robolectric.buildActivity(PreviewActivity::class.java).create().get()
        assertEquals("com.lingyi.mbos", activity.packageName)
    }

    @Test
    fun customFlashActivityLaunches() {
        val activity = Robolectric.buildActivity(CustomFlashActivity::class.java).create().get()
        assertEquals("com.lingyi.mbos", activity.packageName)
    }

    @Test
    fun aboutActivityLaunches() {
        val activity = Robolectric.buildActivity(AboutActivity::class.java).create().get()
        assertEquals("com.lingyi.mbos", activity.packageName)
    }

    @Test
    fun developerActivityLaunchesWithoutConnection() {
        // 开发者调试模式：无需连接也应能进入
        val activity = Robolectric.buildActivity(DeveloperActivity::class.java).create().get()
        assertEquals("com.lingyi.mbos", activity.packageName)
    }

    @Test
    fun licenseActivityLaunches() {
        // 关于页 → 开源许可证页
        val activity = Robolectric.buildActivity(LicenseActivity::class.java).create().get()
        assertEquals("com.lingyi.mbos", activity.packageName)
    }

    @Test
    fun licenseAssetsAreBundled() {
        // 应用内许可证文本必须随包分发，与仓库根目录 LICENSE / NOTICE.md 一致
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val mit = ctx.assets.open("licenses/mit.txt").bufferedReader().use { it.readText() }
        assertTrue("MIT 许可证文本缺失", mit.contains("MIT License"))
        assertTrue("MIT 许可证缺少版权声明", mit.contains("Copyright (c) 2026 10Y-灵逸云科技"))
        val notice = ctx.assets.open("licenses/notice.txt").bufferedReader().use { it.readText() }
        assertTrue("鸣谢文本缺少 MicroPython", notice.contains("MicroPython"))
        assertTrue("鸣谢文本缺少官方插图署名", notice.contains("micro:bit Educational Foundation"))
    }

    @Test
    fun devModeFlagToggles() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Prefs.setDevMode(ctx, false)
        assertFalse("默认应关闭开发者模式", Prefs.isDevMode(ctx))
        Prefs.setDevMode(ctx, true)
        assertTrue("设置后应开启开发者模式", Prefs.isDevMode(ctx))
        Prefs.setDevMode(ctx, false)
    }

    @Test
    fun microbitGamesPresetsAreValid() {
        val ids = MicrobitGames.presets.map { it.id }
        assertEquals(listOf("plane", "snake", "bricks"), ids)
        // 每款游戏脚本必须可刷入：含 microbit 模块与游戏循环，且不依赖 App 读数兼容
        for (g in MicrobitGames.presets) {
            assertTrue("游戏 ${g.id} 缺少 microbit 模块", g.code.contains("from microbit import"))
            assertTrue("游戏 ${g.id} 未使用 LED 显示", g.code.contains("display."))
            assertTrue("游戏 ${g.id} 缺少 A/B 操控", g.code.contains("button_a") && g.code.contains("button_b"))
        }
    }

    @Test
    fun protocolScrollAppendsNewline() {
        assertEquals("SCROLL:hello\n", Protocol.scroll("hello"))
    }

    @Test
    fun protocolShowEncodesMatrix() {
        val m = Array(5) { BooleanArray(5) }
        m[0][0] = true
        m[4][4] = true
        assertEquals("SHOW:10000,00000,00000,00000,00001\n", Protocol.show(m))
    }

    @Test
    fun applicationContextResolves() {
        val app = ApplicationProvider.getApplicationContext<MBOSApp>()
        assertEquals("灵逸 MB", app.getString(R.string.app_name))
    }

    @Test
    fun updateCheckComparesVersionCodes() {
        // 真实比较逻辑：远端版本号必须严格大于本地才算「有更新」
        assertTrue("远端更高应判定为更新", UpdateChecker.isNewer(BuildConfig.VERSION_CODE + 1))
        assertFalse("同版本不应判定为更新", UpdateChecker.isNewer(BuildConfig.VERSION_CODE))
        assertFalse("远端更低不应判定为更新", UpdateChecker.isNewer(BuildConfig.VERSION_CODE - 1))
    }
}

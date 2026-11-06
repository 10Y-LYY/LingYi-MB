package com.lingyi.mbos

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lingyi.mbos.ble.ConnectionHub
import com.lingyi.mbos.ble.MicrobitConnection
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Field

/** PointerService（前台常驻）单测：连接即 arm、断开即下发 MOUSE:OFF、数据不崩。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = MBOSApp::class)
class PointerServiceTest {

    private val currentField: Field =
        ConnectionHub::class.java.getDeclaredField("current").apply { isAccessible = true }

    private fun injectFakeConnection(): MutableList<String> {
        val sent = mutableListOf<String>()
        val fake = object : MicrobitConnection {
            override val type: String = "usb"
            override fun connect(context: Context, listener: MicrobitConnection.Listener) {}
            override fun disconnect() {}
            override fun send(line: String): Boolean { sent.add(line); return true }
            override fun isConnected(): Boolean = true
        }
        currentField.set(ConnectionHub, fake)
        return sent
    }

    private fun clearFakeConnection() {
        ConnectionHub.disconnect()
        currentField.set(ConnectionHub, null)
    }

    /** 把一个记录 moveBy 调用的子类实例注入 PointerAccessibilityService.current()。 */
    private fun injectFakeAccessibility(): MutableList<Pair<Float, Float>> {
        val moves = mutableListOf<Pair<Float, Float>>()
        val svc = object : PointerAccessibilityService() {
            override fun moveBy(dx: Float, dy: Float) { moves.add(dx to dy) }
        }
        PointerAccessibilityService.setInstanceForTest(svc)
        return moves
    }

    private fun clearFakeAccessibility() {
        PointerAccessibilityService.setInstanceForTest(null)
    }

    @Test
    fun mouseDataDrivesCursorMove() {
        // 回归：MOUSE 数据必须驱动光标移动（真实系统指针跟随），不能因 current() 为 null 静默丢弃
        val sent = injectFakeConnection()
        val moves = injectFakeAccessibility()
        val controller = Robolectric.buildService(PointerService::class.java)
        try {
            controller.create()
            controller.startCommand(0, 1)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertTrue("连接时启动服务应发送 MOUSE:ON", sent.contains("MOUSE:ON\n"))

            // 模拟固件回传一帧倾斜增量
            ConnectionHub.debugEmitData("MOUSE:5,3,0,0")
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertTrue("MOUSE 数据应驱动光标移动，实际 moves=$moves", moves.isNotEmpty())
            assertEquals("增量应原样进入 moveBy", 5f to 3f, moves.first())

            // 连续两帧小增量也应持续移动（验证每帧派发，而非仅拖动模式）
            ConnectionHub.debugEmitData("MOUSE:2,-1,0,0")
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertTrue("后续帧应继续驱动移动，实际 moves=$moves", moves.size >= 2)
        } finally {
            controller.destroy()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            clearFakeConnection()
            clearFakeAccessibility()
        }
    }

    @After
    fun tearDown() {
        // 各测试方法已在 finally 中 destroy 控制器；此处仅兜底清理注入的连接
        clearFakeConnection()
    }

    @Test
    fun floatingWindowDoesNotRequireMaterialTheme() {
        // 回归：v3.1.7 用 MaterialCardView 做悬浮窗，在 Service 上下文触发 Material 主题强校验
        // （ThemeEnforcement），onStartCommand 直接崩溃、进程被杀、连接断开。
        // 现悬浮窗改用普通视图，构造不得依赖 MaterialComponents 主题、也不得抛异常。
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        runCatching { grantOverlay(ctx) }
        // 刻意用「非 Material」主题上下文，模拟 Service 上下文在某些机型上的主题条件
        val themed = android.view.ContextThemeWrapper(ctx, android.R.style.Theme_DeviceDefault)
        val win = FloatingPointerWindow(themed, {}, {}, {}, {})
        val result = runCatching { win.show() }
        assertTrue("悬浮窗构造/显示不得抛异常（不得依赖 Material 主题）", result.isSuccess)
        runCatching { win.hide() }
    }

    /** 尽量授予悬浮窗权限，使 show() 真正走到视图构造路径。 */
    private fun grantOverlay(context: Context) {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val shadow = shadowOf(appOps)
        // OP_SYSTEM_ALERT_WINDOW = 24（系统隐藏常量，值在各版本稳定）
        shadow.setMode(24, android.os.Process.myUid(), context.packageName, android.app.AppOpsManager.MODE_ALLOWED)
    }

    @Test
    fun serviceArmsMouseWhenConnectedAndStopsOnDestroy() {
        val sent = injectFakeConnection()
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val controller = Robolectric.buildService(PointerService::class.java)
        try {
            controller.create()
            controller.startCommand(0, 1) // 触发 onStartCommand（连接已注入）
            shadowOf(android.os.Looper.getMainLooper()).idle()

            assertTrue("连接时启动服务应发送 MOUSE:ON，实际=$sent", sent.contains("MOUSE:ON\n"))
            assertTrue("服务应处于运行状态", PointerService.isRunning())

            // 连接 → 断开跳变：应下发 MOUSE:OFF
            ConnectionHub.debugEmitState(false, "断开")
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertTrue("断开应下发 MOUSE:OFF，实际=$sent", sent.contains("MOUSE:OFF\n"))

            // 断开后返回的数据不应派发（无崩溃）
            ConnectionHub.debugEmitData("MOUSE:3,2,1,0")
            shadowOf(android.os.Looper.getMainLooper()).idle()
        } finally {
            controller.destroy()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertFalse("停止后服务不应运行", PointerService.isRunning())
            clearFakeConnection()
        }
    }

    @Test
    fun serviceArmsOnLateConnect() {
        // 未连接启动服务：不 arm；连接建立后补发 MOUSE:ON
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val controller = Robolectric.buildService(PointerService::class.java)
        controller.create()
        controller.startCommand(0, 1)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        // 启动时不注入连接：不应发送 MOUSE:ON
        val sent = mutableListOf<String>()
        val fake = object : MicrobitConnection {
            override val type: String = "ble"
            override fun connect(c: Context, l: MicrobitConnection.Listener) {}
            override fun disconnect() {}
            override fun send(line: String): Boolean { sent.add(line); return true }
            override fun isConnected(): Boolean = true
        }
        currentField.set(ConnectionHub, fake)
        ConnectionHub.debugEmitState(true, "已连接")
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue("连接建立后应补发 MOUSE:ON，实际=$sent", sent.contains("MOUSE:ON\n"))

        controller.destroy()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        currentField.set(ConnectionHub, null)
    }
}

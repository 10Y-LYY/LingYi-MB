package com.lingyi.mbos

import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.lingyi.mbos.ble.ConnectionHub
import com.lingyi.mbos.ble.MicrobitConnection
import com.lingyi.mbos.databinding.ActivityMainBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Field

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = MBOSApp::class)
class PointerModeTest {

    private val currentField: Field =
        ConnectionHub::class.java.getDeclaredField("current").apply { isAccessible = true }

    /** 注入一个模拟「已连接」的连接对象，返回记录到的已发送指令。 */
    private fun injectFakeConnection(): MutableList<String> {
        val sent = mutableListOf<String>()
        val fake = object : MicrobitConnection {
            override val type: String = "usb"
            override fun connect(context: android.content.Context, listener: MicrobitConnection.Listener) {}
            override fun disconnect() {}
            override fun send(line: String): Boolean {
                sent.add(line)
                return true
            }

            override fun isConnected(): Boolean = true
        }
        currentField.set(ConnectionHub, fake)
        return sent
    }

    private fun clearFakeConnection() {
        ConnectionHub.disconnect()
        currentField.set(ConnectionHub, null)
    }

    @Test
    fun pointerActivityLaunchesWhenConnected() {
        // 已连接进入指针页：作为控制面板应正常展示，不崩、不下发指令（数据循环已搬到 PointerService）
        injectFakeConnection()
        try {
            val activity = Robolectric.buildActivity(PointerModeActivity::class.java)
                .create().start().resume().get()
            assertTrue("已连接也应能进入指针页", !activity.isFinishing && !activity.isDestroyed)

            // 模拟固件上报：页面仅用于展示实时数据，不应崩溃
            ConnectionHub.debugEmitData("MOUSE:3,2,0,0")
            ConnectionHub.debugEmitData("MOUSE:0,0,1,0")
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertTrue("收到数据后指针页不应被销毁", !activity.isFinishing && !activity.isDestroyed)
        } finally {
            clearFakeConnection()
        }
    }

    @Test
    fun pointerActivityLaunchesWhenDisconnected() {
        // 未连接也应能正常进入（只读 UI，不下发指令）
        val activity = Robolectric.buildActivity(PointerModeActivity::class.java)
            .create().start().resume().get()
        assertTrue("未连接也应能进入指针页", !activity.isFinishing && !activity.isDestroyed)
    }

    @Test
    fun mainActivityPointerRowOpensPointerActivity() {
        // 控制台点「陀螺仪指针」行，下一个启动的页面必须是 PointerModeActivity
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().get()
        val shadow = shadowOf(activity)

        val bField = MainActivity::class.java.getDeclaredField("b").apply { isAccessible = true }
        val binding = bField.get(activity) as ActivityMainBinding
        val container = binding.funcList

        val pointerRow = (0 until container.childCount)
            .map { container.getChildAt(it) }
            .firstOrNull { child ->
                child.findViewById<TextView>(R.id.tvTitle)?.text?.toString() == "陀螺仪指针"
            }
        assertNotNull("控制台应存在「陀螺仪指针」行", pointerRow)
        pointerRow?.performClick()

        val next = shadow.nextStartedActivity
        assertNotNull("点击后应启动新页面", next)
        assertEquals(
            "点击「陀螺仪指针」应打开 PointerModeActivity",
            PointerModeActivity::class.java.name,
            next.component?.className
        )
    }

    @Test
    fun pointerSensitivityDefaultsAndClamps() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals(1.0f, com.lingyi.mbos.util.Prefs.getPointerSensitivity(ctx), 0.001f)
        com.lingyi.mbos.util.Prefs.setPointerSensitivity(ctx, 99f)
        assertEquals(3.0f, com.lingyi.mbos.util.Prefs.getPointerSensitivity(ctx), 0.001f)
        com.lingyi.mbos.util.Prefs.setPointerSensitivity(ctx, 0f)
        assertEquals(0.4f, com.lingyi.mbos.util.Prefs.getPointerSensitivity(ctx), 0.001f)
        com.lingyi.mbos.util.Prefs.setPointerSensitivity(ctx, 1.0f)
    }

    /** 回归：日志实锤崩溃值 0.8333333 不在滑块网格（0.4 + n×0.2）上，读取必须吸附。 */
    @Test
    fun pointerSensitivitySnapsOffGridValues() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val sp = ctx.getSharedPreferences("mbos_prefs", android.content.Context.MODE_PRIVATE)
        // 直接写入历史野值（绕过 setter 的吸附），验证读取路径兜底
        sp.edit().putFloat("pointer_sensitivity", 0.8333333f).apply()
        val fixed = com.lingyi.mbos.util.Prefs.getPointerSensitivity(ctx)
        assertTrue("吸附后应回到网格上", fixed == 0.8f || fixed == 0.6f)
        // NaN / Infinity 也不能穿透
        sp.edit().putFloat("pointer_sensitivity", Float.NaN).apply()
        assertEquals(1.0f, com.lingyi.mbos.util.Prefs.getPointerSensitivity(ctx), 0.001f)
        sp.edit().putFloat("pointer_sensitivity", Float.POSITIVE_INFINITY).apply()
        assertEquals(3.0f, com.lingyi.mbos.util.Prefs.getPointerSensitivity(ctx), 0.001f)
        // 网格枚举：0.4 + n×0.2（n=0..13）且不超过 valueTo=3.0
        for (n in 0..13) {
            val expect = 0.4f + n * 0.2f
            val got = com.lingyi.mbos.util.Prefs.snapSensitivity(expect)
            assertTrue("n=$n 应吸附到自身附近", Math.abs(got - expect) < 0.001f)
            assertTrue("不得越过 valueTo=3.0", got <= 3.0f)
        }
        com.lingyi.mbos.util.Prefs.setPointerSensitivity(ctx, 1.0f)
    }

    @Test
    fun protocolMouseCommands() {
        assertEquals("MOUSE:ON\n", com.lingyi.mbos.ui.Protocol.mouseOn())
        assertEquals("MOUSE:OFF\n", com.lingyi.mbos.ui.Protocol.mouseOff())
        assertEquals("MOUSE:CAL\n", com.lingyi.mbos.ui.Protocol.mouseCal())
    }

    @Test
    fun connectionHubListenerIterationIsThreadSafe() {
        // 复现线上 bug：连接后（有数据流）点击「陀螺仪指针」直接跳回引导页。
        // 根因候选：USB 读线程 / BLE Binder 线程在迭代 listeners 的同一时刻，
        // UI 线程正在 addListener / removeListener —— LinkedHashSet 会抛
        // ConcurrentModificationException；USB 侧有 runCatching 兜底，
        // 而 BLE 的 onCharacteristicChanged 没有 → 未捕获异常 → 进程崩溃 →
        // 系统重启回 LAUNCHER（引导页）。未连接时无数据流，故不崩。
        val wrapMethod = ConnectionHub::class.java.getDeclaredMethod("wrap").apply { isAccessible = true }
        val wrap = wrapMethod.invoke(ConnectionHub) as MicrobitConnection.Listener

        // 先挂两个监听器，让迭代有实际内容
        val dummy = object : MicrobitConnection.Listener {
            override fun onState(state: com.lingyi.mbos.ble.ConnState) {}
            override fun onData(data: String) {}
        }
        ConnectionHub.addListener(dummy)
        ConnectionHub.addListener(dummy)

        val errors = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
        val stop = java.util.concurrent.atomic.AtomicBoolean(false)
        val bg = Thread {
            while (!stop.get()) {
                try {
                    // 模拟连接后持续到达的数据行（与真实 USB 读线程行为一致）
                    wrap.onData("A:0|B:0|T:25|L:128|X:0|Y:0|Z:1024|C:180|G:none")
                } catch (t: Throwable) {
                    errors.add(t)
                }
            }
        }
        bg.start()

        // 模拟点击「陀螺仪指针」→ PointerModeActivity.onCreate → addListener
        val probe = object : MicrobitConnection.Listener {
            override fun onState(state: com.lingyi.mbos.ble.ConnState) {}
            override fun onData(data: String) {}
        }
        repeat(200) {
            ConnectionHub.addListener(probe)
            ConnectionHub.removeListener(probe)
        }
        stop.set(true)
        bg.join(3000)

        ConnectionHub.removeListener(dummy)
        assertTrue("监听器并发迭代不应抛异常（实际=$errors）", errors.isEmpty())
    }

    @Test
    fun parseMbosVersionExtractsBoardFirmware() {
        // 真实 INFO 回显行 → 板载 MB-OS 版本
        assertEquals(
            "2.9.1",
            com.lingyi.mbos.ui.Protocol.parseMbosVersion(
                "INFO:MicroPython 1.19|MB-OS v2.9.1|micro:bit V2"
            )
        )
        // 板载未刷 MB-OS 助手（跑游戏/自定义代码，无 MB-OS 字段）→ null
        assertNull(
            com.lingyi.mbos.ui.Protocol.parseMbosVersion("INFO:MicroPython 1.19|micro:bit V2")
        )
        // 非 INFO 行 → null
        assertNull(com.lingyi.mbos.ui.Protocol.parseMbosVersion("A:0|B:0|T:25"))
        assertNull(com.lingyi.mbos.ui.Protocol.parseMbosVersion("MOUSE:0,0,0,0"))
    }
}

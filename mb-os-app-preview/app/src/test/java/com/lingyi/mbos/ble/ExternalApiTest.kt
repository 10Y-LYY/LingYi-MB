/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ble

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lingyi.mbos.BuildConfig
import com.lingyi.mbos.util.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 对外开发者接口核心分发逻辑回归测试：覆盖开关拦截、连接态判定、未知方法、空指令等。
 * 走真实 Prefs（SharedPreferences），与 App 运行期行为一致。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = com.lingyi.mbos.MBOSApp::class)
class ExternalApiTest {

    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        // 隔离其它测试对开关的污染：默认关
        Prefs.setExtApiEnabled(ctx, false)
    }

    @Test
    fun dispatchBlockedWhenExtApiDisabled() {
        val r = ExternalApi.dispatch(ctx, "send", "SCROLL:hi",
            isConnected = { true }, send = { true })
        assertFalse("接口关闭时任何调用都应失败", r.ok)
        assertEquals("", r.sent)
    }

    @Test
    fun statusWorksWhenExtApiDisabledButOnlyReportsState() {
        // status 也受开关保护：关闭时直接失败，不会泄露连接状态
        val r = ExternalApi.dispatch(ctx, "status", null,
            isConnected = { true }, send = { true })
        assertFalse(r.ok)
    }

    @Test
    fun sendSucceedsWhenEnabledAndConnected() {
        Prefs.setExtApiEnabled(ctx, true)
        var sent: String? = null
        val r = ExternalApi.dispatch(ctx, "send", "SCROLL:hello",
            isConnected = { true }, send = { sent = it; true })
        assertTrue(r.ok)
        assertEquals("SCROLL:hello\n", sent)
        assertEquals("SCROLL:hello", r.sent)
    }

    @Test
    fun sendFailsWhenNotConnected() {
        Prefs.setExtApiEnabled(ctx, true)
        val r = ExternalApi.dispatch(ctx, "send", "SCROLL:hi",
            isConnected = { false }, send = { true })
        assertFalse(r.ok)
        assertTrue(r.reason.contains("未连接"))
    }

    @Test
    fun sendFailsOnBlankArg() {
        Prefs.setExtApiEnabled(ctx, true)
        val r = ExternalApi.dispatch(ctx, "send", "   ",
            isConnected = { true }, send = { true })
        assertFalse(r.ok)
    }

    @Test
    fun statusReportsConnectionStateWhenEnabled() {
        Prefs.setExtApiEnabled(ctx, true)
        val connected = ExternalApi.dispatch(ctx, "status", null,
            isConnected = { true }, send = { true })
        assertTrue(connected.ok)
        val disconnected = ExternalApi.dispatch(ctx, "status", null,
            isConnected = { false }, send = { true })
        assertTrue(disconnected.ok)
        assertTrue(disconnected.reason.contains("未连接"))
    }

    @Test
    fun unknownMethodFails() {
        Prefs.setExtApiEnabled(ctx, true)
        val r = ExternalApi.dispatch(ctx, "explode", null,
            isConnected = { true }, send = { true })
        assertFalse(r.ok)
        assertTrue(r.reason.contains("未知方法"))
    }
}

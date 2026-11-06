/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.lingyi.mbos.MBOSApp
import com.lingyi.mbos.R
import com.lingyi.mbos.util.Prefs
import com.lingyi.mbos.util.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 更新通道下拉选择器回归测试（三通道：稳定版 / 预览版 / 开发版）：
 * 默认值 / 通道切换写入 Prefs / 幂等不重复回调 / 文案随通道变化 / 无障碍描述。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = MBOSApp::class)
class UpdateChannelPickerTest {

    private lateinit var app: MBOSApp

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        Prefs.setChannel(app, UpdateChecker.CHANNEL_STABLE)
    }

    private fun pickerCtx() = ContextThemeWrapper(app, R.style.Theme_MBOS)

    private fun newPicker(): UpdateChannelPicker = UpdateChannelPicker(pickerCtx())

    private fun texts(picker: UpdateChannelPicker): List<String> {
        val out = mutableListOf<String>()
        fun walk(v: android.view.View) {
            if (v is TextView) out.add(v.text?.toString() ?: "")
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(picker)
        return out
    }

    @Test
    fun defaultsToStable() {
        val p = newPicker()
        assertEquals(UpdateChecker.CHANNEL_STABLE, p.currentChannel)
        assertTrue("应显示稳定版标题", texts(p).contains(app.getString(R.string.update_channel_stable)))
        assertTrue("应显示稳定版说明", texts(p).contains(app.getString(R.string.update_channel_stable_desc)))
    }

    @Test
    fun refreshPicksUpPersistedPreview() {
        Prefs.setChannel(app, UpdateChecker.CHANNEL_PREVIEW)
        val p = newPicker()
        p.refresh()
        assertEquals(UpdateChecker.CHANNEL_PREVIEW, p.currentChannel)
        assertTrue(texts(p).contains(app.getString(R.string.update_channel_preview)))
        assertTrue(texts(p).contains(app.getString(R.string.update_channel_preview_desc)))
    }

    @Test
    fun selectPreviewPersistsAndCallbacks() {
        val p = newPicker()
        var got: String? = null
        p.onChannelChanged = { got = it }

        p.selectChannel(UpdateChecker.CHANNEL_PREVIEW)

        assertEquals(UpdateChecker.CHANNEL_PREVIEW, p.currentChannel)
        assertEquals(UpdateChecker.CHANNEL_PREVIEW, got)
        assertEquals("选择必须落到 Prefs，供三处更新入口共享", UpdateChecker.CHANNEL_PREVIEW, Prefs.getChannel(app))
        assertTrue(texts(p).contains(app.getString(R.string.update_channel_preview)))
    }

    @Test
    fun selectDevChannelWorks() {
        val p = newPicker()
        var got: String? = null
        p.onChannelChanged = { got = it }
        p.selectChannel(UpdateChecker.CHANNEL_DEV)
        assertEquals(UpdateChecker.CHANNEL_DEV, p.currentChannel)
        assertEquals(UpdateChecker.CHANNEL_DEV, got)
        assertEquals(UpdateChecker.CHANNEL_DEV, Prefs.getChannel(app))
        assertTrue(texts(p).contains(app.getString(R.string.update_channel_dev)))
    }

    @Test
    fun selectSameChannelDoesNotCallback() {
        val p = newPicker()
        var count = 0
        p.onChannelChanged = { count++ }

        p.selectChannel(UpdateChecker.CHANNEL_PREVIEW)
        p.selectChannel(UpdateChecker.CHANNEL_PREVIEW) // 重复选同一项
        assertEquals("同值重复选择只回调一次", 1, count)

        p.selectChannel(UpdateChecker.CHANNEL_STABLE)
        assertEquals(2, count)
        assertEquals(UpdateChecker.CHANNEL_STABLE, Prefs.getChannel(app))
    }

    @Test
    fun contentDescriptionReflectsChannel() {
        val p = newPicker()
        assertTrue(p.contentDescription?.contains(app.getString(R.string.update_channel_stable)) == true)
        p.selectChannel(UpdateChecker.CHANNEL_PREVIEW)
        assertTrue(p.contentDescription?.contains(app.getString(R.string.update_channel_preview)) == true)
    }
}

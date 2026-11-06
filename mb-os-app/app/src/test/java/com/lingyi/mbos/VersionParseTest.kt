/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos

import com.lingyi.mbos.ui.FirmwareScript
import com.lingyi.mbos.ui.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 固件 / 软件版本直读与升级提示（v3.1.0）：
 * 覆盖 INFO 行全量解析（MB-OS + MicroPython）与版本新旧比较。
 */
class VersionParseTest {

    @Test
    fun parseInfoExtractsBothVersions() {
        val info = Protocol.parseInfo("INFO:MicroPython 2.1.1|MB-OS v3.0.0|micro:bit V2")
        assertEquals("3.0.0", info?.mbos)
        assertEquals("2.1.1", info?.micropython)
    }

    @Test
    fun parseInfoBareMicropythonHasNoMbos() {
        // 用户实况：板载只刷了裸 MicroPython，未安装 MB-OS 助手脚本
        val info = Protocol.parseInfo("INFO:MicroPython 2.1.1|micro:bit V2")
        assertNull("未安装 MB-OS 时 mbos 必须为 null", info?.mbos)
        assertEquals("2.1.1", info?.micropython)
    }

    @Test
    fun parseInfoRejectsNonInfoLines() {
        assertNull(Protocol.parseInfo("A:0|B:0|T:25"))
        assertNull(Protocol.parseInfo("MOUSE:0,0,0,0"))
        assertNull(Protocol.parseInfo(""))
    }

    @Test
    fun parseMbosVersionStillWorks() {
        assertEquals(
            "3.0.0",
            Protocol.parseMbosVersion("INFO:MicroPython 2.1.1|MB-OS v3.0.0|micro:bit V2")
        )
        assertNull(Protocol.parseMbosVersion("INFO:MicroPython 2.1.1|micro:bit V2"))
    }

    @Test
    fun isOlderComparesNumerically() {
        assertTrue("2.9.1 旧于 3.0.0", Protocol.isOlder("2.9.1", "3.0.0"))
        assertTrue("2.9.1 旧于 2.9.2", Protocol.isOlder("2.9.1", "2.9.2"))
        assertTrue("2.9 旧于 2.9.1（缺段按 0）", Protocol.isOlder("2.9", "2.9.1"))
        assertFalse("相等不算旧", Protocol.isOlder("3.0.0", "3.0.0"))
        assertFalse("更新不算旧", Protocol.isOlder("3.0.1", "3.0.0"))
        assertFalse("3.1.0 不旧于 3.0.0", Protocol.isOlder("3.1.0", "3.0.0"))
    }

    @Test
    fun bundledFirmwareVersionIsExpected() {
        // 锁死内置固件版本：引导页升级提示与合并 HEX 内的 MBOS_VERSION 都以它为准
        assertEquals("3.0.0", FirmwareScript.VERSION)
        assertTrue(
            "内置固件脚本必须声明同一版本",
            FirmwareScript.script.contains("MBOS_VERSION = \"3.0.0\"")
        )
        // 旧版板载（v2.9.1）应被判定为需要升级
        assertTrue(Protocol.isOlder("2.9.1", FirmwareScript.VERSION))
        // 刚刷完新固件的板载不应再被提示
        assertFalse(Protocol.isOlder(FirmwareScript.VERSION, FirmwareScript.VERSION))
    }
}

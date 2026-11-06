/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

/**
 * 指令编排数据（「指令编排」功能用）：若干「指令 + 步间延时」按序执行。
 * 持久化为 JSON 字符串存 Prefs（见 [BellSchedule] 同款容错解析）。
 */
object CommandSeqStore {

    data class Step(val command: String, val delayMs: Int)

    fun toJson(steps: List<Step>): String {
        val arr = org.json.JSONArray()
        steps.forEach { s ->
            arr.put(
                org.json.JSONObject().apply {
                    put("cmd", s.command)
                    put("delay", s.delayMs)
                }
            )
        }
        return arr.toString()
    }

    fun fromJson(raw: String?): List<Step> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Step(
                    command = o.optString("cmd").ifBlank { "SCROLL:hi" },
                    delayMs = o.optInt("delay", 500).coerceIn(0, 60_000),
                )
            }
        }.getOrDefault(emptyList())
    }
}

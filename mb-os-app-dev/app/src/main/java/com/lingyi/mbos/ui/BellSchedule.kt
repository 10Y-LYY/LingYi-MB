/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

import org.json.JSONArray
import org.json.JSONObject

/**
 * 上课铃课程表（「上课铃」功能用）：一天内若干「时刻 + 课程」，
 * 到点由 App 响铃并让开发板滚动课程名。
 */
object BellSchedule {

    data class Lesson(val id: Long, val name: String, val hour: Int, val minute: Int, val enabled: Boolean) {
        val timeText: String get() = String.format(java.util.Locale.US, "%02d:%02d", hour, minute)
        val minutesOfDay: Int get() = hour * 60 + minute
    }

    /** 某一时刻应触发的课程（同一分钟可多节，按添加顺序）。 */
    fun dueAt(lessons: List<Lesson>, hour: Int, minute: Int): List<Lesson> =
        lessons.filter { it.enabled && it.hour == hour && it.minute == minute }

    /** 按时间排序（同刻保持原顺序）。 */
    fun sorted(lessons: List<Lesson>): List<Lesson> =
        lessons.sortedWith(compareBy { it.minutesOfDay })

    // ---------- 持久化（Prefs 里存 JSON 字符串） ----------

    fun toJson(lessons: List<Lesson>): String {
        val arr = JSONArray()
        lessons.forEach { l ->
            arr.put(
                JSONObject().apply {
                    put("id", l.id)
                    put("name", l.name)
                    put("hour", l.hour)
                    put("minute", l.minute)
                    put("enabled", l.enabled)
                }
            )
        }
        return arr.toString()
    }

    /** 解析失败时回落空表（防御手改 Prefs / 老版本数据）。 */
    fun fromJson(raw: String?): List<Lesson> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Lesson(
                    id = o.optLong("id"),
                    name = o.optString("name").ifBlank { "课程" },
                    hour = o.optInt("hour").coerceIn(0, 23),
                    minute = o.optInt("minute").coerceIn(0, 59),
                    enabled = o.optBoolean("enabled", true),
                )
            }
        }.getOrDefault(emptyList())
    }
}

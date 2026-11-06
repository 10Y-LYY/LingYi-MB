/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * LED 动画存储：命名动画（帧序列 + 每帧时长）存进 SharedPreferences。
 * 帧用 5×5 行优先字符串表示（"01110"），与 [Protocol.show] 的输入一致。
 */
object AnimationStore {

    private const val KEY_ANIMATIONS = "led_animations"

    /** 一帧：[rows] 5 行点阵 + [durMs] 停留时长。 */
    data class Frame(val rows: List<String>, val durMs: Int) {
        fun toMatrix(): Array<BooleanArray> = Array(5) { y ->
            BooleanArray(5) { x -> rows.getOrNull(y)?.getOrNull(x) == '1' }
        }

        companion object {
            fun of(matrix: Array<BooleanArray>, durMs: Int): Frame = Frame(
                (0 until 5).map { y ->
                    (0 until 5).joinToString("") { x -> if (matrix[y][x]) "1" else "0" }
                },
                durMs
            )
        }
    }

    data class NamedAnimation(val name: String, val frames: List<Frame>)

    /** 保存 / 覆盖同名动画（空名字或零帧视为无效，忽略）。 */
    fun save(ctx: Context, name: String, frames: List<Frame>) {
        val n = name.trim()
        if (n.isEmpty() || frames.isEmpty()) return
        val arr = JSONArray()
        frames.forEach { f ->
            arr.put(
                JSONObject().apply {
                    put("rows", JSONArray(f.rows))
                    put("dur", f.durMs.coerceIn(50, 60_000))
                }
            )
        }
        val all = listOf(NamedAnimation(n, frames)) +
            loadAll(ctx).filterNot { it.name == n } // 最新在前
        persist(ctx, all)
    }

    /** 全部动画（最新保存的在前）。 */
    fun loadAll(ctx: Context): List<NamedAnimation> {
        val raw = ctx.getSharedPreferences("mbos_prefs", Context.MODE_PRIVATE)
            .getString(KEY_ANIMATIONS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val framesArr = o.optJSONArray("frames") ?: JSONArray()
                val frames = (0 until framesArr.length()).map { j ->
                    val f = framesArr.getJSONObject(j)
                    val rowsArr = f.optJSONArray("rows") ?: JSONArray()
                    Frame(
                        (0 until rowsArr.length()).map { rowsArr.getString(it) },
                        f.optInt("dur", 300).coerceIn(50, 60_000)
                    )
                }
                NamedAnimation(o.optString("name"), frames)
            } // persist 维护「最新在前」，原序返回即可
        }.getOrElse { emptyList() }
    }

    /** 读单个动画（同名取最新保存的那个）。 */
    fun load(ctx: Context, name: String): NamedAnimation? =
        loadAll(ctx).firstOrNull { it.name == name }

    fun delete(ctx: Context, name: String) {
        persist(ctx, loadAll(ctx).filterNot { it.name == name })
    }

    private fun persist(ctx: Context, all: List<NamedAnimation>) {
        val arr = JSONArray()
        all.forEach { anim ->
            val frames = JSONArray()
            anim.frames.forEach { f ->
                frames.put(
                    JSONObject().apply {
                        put("rows", JSONArray(f.rows))
                        put("dur", f.durMs)
                    }
                )
            }
            arr.put(JSONObject().apply { put("name", anim.name); put("frames", frames) })
        }
        ctx.getSharedPreferences("mbos_prefs", Context.MODE_PRIVATE)
            .edit().putString(KEY_ANIMATIONS, arr.toString()).apply()
    }
}

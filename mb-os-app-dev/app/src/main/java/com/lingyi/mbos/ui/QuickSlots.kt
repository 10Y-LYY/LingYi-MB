/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

/**
 * 控制台「快捷入口」两张卡的数据规则（纯函数，便于单测）。
 *
 * 设计要点：
 *  - 固定两个位置，顺序即展示顺序（左 / 右），不做 Set 存储避免顺序丢失；
 *  - 任何未知 / 重复 / 缺失的 id 都收敛为可用值（[normalize]），
 *    因此功能下线、旧版本写入脏数据都不会让控制台出现空格子或崩溃；
 *  - [assign] 允许把某功能放到另一个位置时自动「换位」，不会出现两张卡重复。
 */
object QuickSlots {

    /** 快捷入口个数（与 UI 强绑定：控制台顶部两张卡）。 */
    const val SIZE = 2

    /** 出厂默认：LED 画板 + 表情图标。 */
    val DEFAULTS = listOf("led", "emoji")

    private fun validIds(): Set<String> = FeatureCatalog.FEATURES.mapTo(LinkedHashSet()) { it.id }

    /** 校验并补齐为恰好 [SIZE] 个有效且互不重复的功能 id。 */
    fun normalize(raw: List<String>?): List<String> {
        val valid = validIds()
        val out = ArrayList<String>(SIZE)
        raw.orEmpty().forEach { item ->
            val id = item.trim()
            if (id in valid && id !in out && out.size < SIZE) out.add(id)
        }
        if (out.size < SIZE) {
            val fill = DEFAULTS + FeatureCatalog.FEATURES.map { it.id }
            for (id in fill) {
                if (out.size >= SIZE) break
                if (id in valid && id !in out) out.add(id)
            }
        }
        return out
    }

    /** 把 [id] 放到第 [slot] 个位置；若已在另一位置则两者互换。 */
    fun assign(current: List<String>, slot: Int, id: String): List<String> {
        if (id !in validIds()) return normalize(current)
        val base = normalize(current).toMutableList()
        val i = slot.coerceIn(0, SIZE - 1)
        val other = if (i == 0) 1 else 0
        if (base[other] == id) {
            // 目标功能已在另一位置 → 换位，保证两张卡不重复
            base[other] = base[i]
            base[i] = id
        } else {
            base[i] = id
        }
        return normalize(base)
    }

    /** 按 id 取功能定义（图标 / 标题 / 说明）；未知 id 返回 null。 */
    fun featureOf(id: String): FeatureCatalog.Feature? =
        FeatureCatalog.FEATURES.firstOrNull { it.id == id }
}

/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

import kotlin.random.Random

/**
 * 骰子点阵：1–6 点在 5×5 矩阵上的标准排布（与实体骰子一致）。
 * 纯逻辑，供「骰子」功能与单测复用。
 */
object DicePatterns {

    /** 取 [n]（1–6，越界按 1 处理）对应的 5×5 点阵。 */
    fun matrix(n: Int): Array<BooleanArray> {
        val grid = Array(5) { BooleanArray(5) }
        val pts = when (n.coerceIn(1, 6)) {
            1 -> listOf(2 to 2)
            2 -> listOf(1 to 1, 3 to 3)
            3 -> listOf(1 to 1, 2 to 2, 3 to 3)
            4 -> listOf(1 to 1, 1 to 3, 3 to 1, 3 to 3)
            5 -> listOf(1 to 1, 1 to 3, 2 to 2, 3 to 1, 3 to 3)
            else -> listOf(1 to 1, 1 to 3, 2 to 1, 2 to 3, 3 to 1, 3 to 3)
        }
        pts.forEach { (x, y) -> grid[y][x] = true }
        return grid
    }

    /** 点数（= 亮点数，单测断言用）。 */
    fun pips(n: Int): Int = matrix(n).sumOf { row -> row.count { it } }

    fun randomFace(): Int = Random.nextInt(1, 7)
}

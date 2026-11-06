/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

/**
 * 5×5 点阵工具单一出处（v4.2 合并 ConsoleGames.matrixOf /
 * MainActivity.rowsToMatrix / ConsoleMoreDisplay 内联实现）。
 */

/** 行优先字符串 → 5×5 布尔矩阵（'1' 亮；缺行缺列按灭）。 */
fun rowsToMatrix(rows: List<String>): Array<BooleanArray> =
    Array(5) { y -> BooleanArray(5) { x -> rows.getOrNull(y)?.getOrNull(x) == '1' } }

/** 全亮点阵。 */
fun fullOn(): Array<BooleanArray> = Array(5) { BooleanArray(5) { true } }

/** 全暗点阵。 */
fun fullOff(): Array<BooleanArray> = Array(5) { BooleanArray(5) { false } }

/** 行字符串 → 亮度矩阵（0–9，供 Protocol.show 的数字模式复用）。 */
fun rowsToDigits(rows: List<String>): Array<IntArray> =
    Array(5) { y -> IntArray(5) { x -> rows.getOrNull(y)?.getOrNull(x)?.digitToIntOrNull() ?: 0 } }

/**
 * 内置点阵图案库（行优先字符串，喂给 [rowsToMatrix]）。
 * 合并散落在 ConsoleGames / ConsoleMoreDisplay / ConsoleMoreSound 的字面量。
 */
object Patterns {
    val COIN_HEADS = listOf("01110", "10001", "10001", "10001", "01110")
    val COIN_TAILS = listOf("01110", "11111", "11111", "11111", "01110")
    val HEART = listOf("01010", "11111", "11111", "01110", "00100")
    val SMILE = listOf("01010", "01010", "00000", "10001", "01110")
    val STAR = listOf("00000", "00100", "01110", "00100", "00000")
    val CHECK = listOf("00001", "00010", "10100", "01000", "00000")
    val CROSS = listOf("10001", "01010", "00100", "01010", "10001")
}

/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

/**
 * 3×5 大字库（「大数字」功能用）：每个字符 3 列 5 行，
 * 行优先字符串，'1' = 亮。位宽 3，两个数字之间留 1 列暗边即可并排显示。
 */
object DigitFont {
    private val GLYPHS: Map<Char, List<String>> = mapOf(
        '0' to listOf("111", "101", "101", "101", "111"),
        '1' to listOf("010", "110", "010", "010", "111"),
        '2' to listOf("111", "001", "111", "100", "111"),
        '3' to listOf("111", "001", "111", "001", "111"),
        '4' to listOf("101", "101", "111", "001", "001"),
        '5' to listOf("111", "100", "111", "001", "111"),
        '6' to listOf("111", "100", "111", "101", "111"),
        '7' to listOf("111", "001", "001", "001", "001"),
        '8' to listOf("111", "101", "111", "101", "111"),
        '9' to listOf("111", "101", "111", "001", "111"),
        '-' to listOf("000", "000", "111", "000", "000"),
        '.' to listOf("000", "000", "000", "000", "010"),
    )

    val DIGITS: List<Char> get() = GLYPHS.keys.filter { it.isDigit() }.sorted()

    /** 取字形；未知字符回落 '-'（全竖线不会与数字混淆，且一眼能看出问题）。 */
    fun glyph(c: Char): List<String> = GLYPHS[c] ?: GLYPHS['-']!!

    fun has(c: Char): Boolean = GLYPHS.containsKey(c)

    /** 把一串字符拼成 5×N 矩阵（字符间夹一列暗列）。超出 5 列的调用方自行分页。 */
    fun render(text: String): Array<BooleanArray> {
        val chars = text.filter { it != ' ' }.ifEmpty { "-" }
        val cols = chars.length * 3 + (chars.length - 1)
        return Array(5) { y ->
            BooleanArray(cols) { x ->
                val idx = x / 4
                val col = x % 4
                if (col == 3 || idx >= chars.length) false
                else glyph(chars[idx]).getOrNull(y)?.getOrNull(col) == '1'
            }
        }
    }
}

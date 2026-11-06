/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

import kotlin.random.Random

/** 石头剪刀布：手势 + 判定。纯逻辑，供「石头剪刀布」功能与单测复用。 */
enum class Move(val label: String) {
    ROCK("石头"),
    PAPER("布"),
    SCISSORS("剪刀");

    /** LED 点阵（5×5，行优先字符串）。 */
    val rows: List<String>
        get() = when (this) {
            ROCK -> listOf("00000", "01110", "01110", "01110", "00000")
            PAPER -> listOf("00000", "11111", "10001", "10001", "11111")
            SCISSORS -> listOf("10001", "01010", "00100", "01010", "10001")
        }
}

object RpsJudge {

    const val WIN = 1
    const val DRAW = 0
    const val LOSE = -1

    /** 判定：[you] 对 [device]，返回 1 胜 / 0 平 / -1 负。 */
    fun judge(you: Move, device: Move): Int = when {
        you == device -> DRAW
        (you == Move.ROCK && device == Move.SCISSORS) ||
            (you == Move.SCISSORS && device == Move.PAPER) ||
            (you == Move.PAPER && device == Move.ROCK) -> WIN
        else -> LOSE
    }

    fun randomMove(): Move = Move.entries[Random.nextInt(Move.entries.size)]
}

/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

/**
 * 猜数字（二分查找状态机）：用户心里想一个数，App 猜、设备按键作答。
 *  - 设备按 A = 「我想的比你的猜测大」  → App 猜大一点
 *  - 设备按 B = 「我想的比你的猜测小」  → App 猜小一点
 *  - A+B 同按（或 App 端点「猜对了」）  → 结束
 * 纯逻辑，无 Android 依赖，供「猜数字」功能与单测复用。
 */
class GuessGame(private val lo0: Int = 1, private val hi0: Int = 100) {

    var lo = lo0
        private set
    var hi = hi0
        private set
    var guess = (lo0 + hi0) / 2
        private set
    var rounds = 0
        private set
    var finished = false
        private set

    /** 重新开局，返回第一猜。 */
    fun start(): Int {
        lo = lo0
        hi = hi0
        guess = (lo0 + hi0) / 2
        rounds = 0
        finished = false
        return guess
    }

    /**
     * 根据作答推下一猜。
     * @param higher true = 目标比当前猜测大（A），false = 更小（B）
     * @return 下一猜；若区间已空（用户在「说谎」或按错）返回 null，调用方应提示重新开局
     */
    fun answer(higher: Boolean): Int? {
        if (finished) return null
        rounds++
        if (higher) lo = guess + 1 else hi = guess - 1
        if (lo > hi) {
            finished = true
            return null
        }
        guess = (lo + hi) / 2
        return guess
    }

    /** 命中：结束并返回用了多少轮。 */
    fun correct(): Int {
        rounds++
        finished = true
        return rounds
    }

    /** 剩余候选数（用于界面提示「还剩 N 个可能」）。 */
    fun remaining(): Int = (hi - lo + 1).coerceAtLeast(0)
}

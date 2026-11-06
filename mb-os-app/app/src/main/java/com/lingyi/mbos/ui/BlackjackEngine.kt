/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

import kotlin.random.Random

/**
 * 21 点引擎（「21 点」游戏用）：一副牌、庄家（开发板）按标准规则补牌，
 * A 可当 1 或 11、J/Q/K 记 10。App 侧只做算牌与判定，设备负责显示点数。
 */
object BlackjackEngine {

    enum class Outcome(val label: String) { WIN("你赢了！"), LOSE("爆牌 / 输"), PUSH("平局"), BLACKJACK("21 点！") }

    data class Card(val rank: String, val suit: String) {
        /** 最小点数（A 记 1，J/Q/K 记 10）。 */
        val minValue: Int
            get() = when (rank) {
                "A" -> 1
                "J", "Q", "K" -> 10
                else -> rank.toIntOrNull() ?: 10
            }
    }

    /** 一手牌的最优点数（A 尽量算 11 但不爆）。 */
    fun bestTotal(cards: List<Card>): Int {
        var total = cards.sumOf { it.minValue }
        var aces = cards.count { it.rank == "A" }
        while (aces > 0 && total + 10 <= 21) {
            total += 10
            aces--
        }
        return total
    }

    fun isBust(cards: List<Card>): Boolean = bestTotal(cards) > 21

    /** 是否黑杰克（起手两张且 21 点）。 */
    fun isBlackjack(cards: List<Card>): Boolean =
        cards.size == 2 && bestTotal(cards) == 21

    private val RANKS = listOf("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K")
    private val SUITS = listOf("♠", "♥", "♣", "♦")

    /** 生成一副洗好的牌（每次调用重新洗）。 */
    fun newDeck(random: Random = Random.Default): ArrayList<Card> {
        val deck = ArrayList<Card>(52)
        for (s in SUITS) for (r in RANKS) deck.add(Card(r, s))
        deck.shuffle(random)
        return deck
    }

    /** 庄家策略：不够 17 继续要牌。返回是否还应该要牌。 */
    fun dealerShouldHit(dealerCards: List<Card>): Boolean = bestTotal(dealerCards) < 17
}

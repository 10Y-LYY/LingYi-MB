/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

/**
 * 摩斯电码：文本编码 + 播放节拍表（滴 / 答 / 字母间隙 / 词间隙）。
 * 纯逻辑，无 Android 依赖，供「摩斯电码」功能与单测复用。
 */
object Morse {

    enum class Kind { DOT, DASH, LETTER_GAP, WORD_GAP }

    /** 一个播放节拍：[kind] 类型 + [ms] 持续（间隙类为静默时长）。 */
    data class Beat(val kind: Kind, val ms: Long)

    private val TABLE: Map<Char, String> = buildMap {
        listOf(
            'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".",
            'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---",
            'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---",
            'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
            'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--",
            'Z' to "--..", '0' to "-----", '1' to ".----", '2' to "..---",
            '3' to "...--", '4' to "....-", '5' to ".....", '6' to "-....",
            '7' to "--...", '8' to "---..", '9' to "----.", '.' to ".-.-.-",
            ',' to "--..--", '?' to "..--..", '\'' to ".----.", '!' to "-.-.--",
            '/' to "-..-.", '(' to "-.--.", ')' to "-.--.-", '&' to ".-...",
            ':' to "---...", ';' to "-.-.-.", '=' to "-...-", '+' to ".-.-.",
            '-' to "-....-", '_' to "..--.-", '"' to ".-..-.", '$' to "...-..-",
            '@' to ".--.-."
        ).forEach { (k, v) -> put(k, v) }
    }

    /** 国际摩斯电码标准：PARIS 约定，点长 = 1200 / wpm 毫秒。 */
    fun dotMs(wpm: Int): Long = (1200.0 / wpm.coerceIn(5, 40)).toLong()

    /**
     * 文本 → 摩斯串：字母内不加分隔，字母间一个空格，词间 " / "。
     * 无法编码的字符原样保留（调用方一般已过滤，这里兜底不抛异常）。
     */
    fun encode(text: String): String {
        val words = text.trim().uppercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return words.joinToString(" / ") { word ->
            word.map { ch -> TABLE[ch] ?: ch.toString() }.joinToString(" ")
        }
    }

    /** 文本 → 播放节拍表（[wpm] 决定速度；间隙按标准 1/3/7 点长）。 */
    fun beats(text: String, wpm: Int = 15): List<Beat> {
        val dot = dotMs(wpm)
        val dash = dot * 3
        val letterGap = dot * 3
        val wordGap = dot * 7
        val out = ArrayList<Beat>()
        val words = text.trim().uppercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        words.forEachIndexed { wi, word ->
            if (wi > 0) out.add(Beat(Kind.WORD_GAP, wordGap))
            word.forEachIndexed { ci, ch ->
                if (ci > 0) out.add(Beat(Kind.LETTER_GAP, letterGap))
                val code = TABLE[ch] ?: return@forEachIndexed
                code.forEachIndexed { si, s ->
                    if (si > 0) out.add(Beat(Kind.LETTER_GAP, letterGap))
                    if (s == '.') out.add(Beat(Kind.DOT, dot)) else out.add(Beat(Kind.DASH, dash))
                }
            }
        }
        return out
    }

    /** 节拍 → 串口指令序列（TONE 发声，LED 用 SHOW 点/划线配合，由调用方发送）。 */
    fun toneHz(kind: Kind): Int = when (kind) {
        Kind.DOT -> 880
        Kind.DASH -> 880
        else -> 0
    }
}

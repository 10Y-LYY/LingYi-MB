/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.util

import kotlin.math.pow

/**
 * 一个音符（已解析为频率与时值）。
 *
 * @param freqHz 频率（Hz）；0 表示休止符。
 * @param ticks 时值（tick 数）；tick 毫秒数见 [TICK_MS]。
 */
data class Note(val freqHz: Double, val ticks: Int) {
    val durMs: Double get() = ticks * Melodies.TICK_MS
}

/**
 * micro:bit `music` 模块内置旋律：曲谱数据与固件逐字一致
 * （来源 micropython/ports/nrf/modules/music/musictunes.c，MIT）。
 *
 * 模拟器用它在手机端还原 `MELODY:` 指令的声音；解析规则对齐固件 `_parseNote`：
 *  - 音符 = 字母（a–g，大小写均可）+ 可选升号 `#` / 降号 `b` + 可选八度数字 + 可选 `:tick` 数；
 *  - `r` 为休止符（可带 `:tick`）；
 *  - **八度与时值在一首曲子内延续**（显式改了才变），每首播放前重置为默认 4 / 4
 *    （官方文档："It'll remember the octave and duration values until you next change them"）；
 *  - 频率以 A4=440Hz 为基准按十二平均律换算：`440 * 2^(octave + (offset - 69) / 12)`，
 *    字母半音偏移 A=21 / B=23 / C=12 / D=14 / E=16 / F=17 / G=19（C4 即中央 C）；
 *  - 时值：`60000ms / bpm / ticks_per_beat`，默认 120bpm、每拍 4 tick → 每 tick 125ms。
 */
object Melodies {

    /** 默认 tempo（120 bpm、每拍 4 tick）下单个 tick 的毫秒数：60000 / 120 / 4。 */
    const val TICK_MS: Double = 125.0

    /** 固件 music 模块认可的旋律名（与 [FirmwareScript][com.lingyi.mbos.ui.FirmwareScript] 行为一致）。 */
    val NAMES: List<String> get() = TUNES.keys.toList()

    /** 曲谱原文（空格分隔的音符串）。 */
    fun tune(name: String): String? = TUNES[name.uppercase()]

    fun has(name: String): Boolean = name.uppercase() in TUNES

    /**
     * 解析一首曲谱为音符序列。
     * 无法识别的记号按休止符跳过（曲谱为内置常量，正常不会触发）。
     */
    fun parse(tune: String): List<Note> {
        var octave = DEFAULT_OCTAVE
        var duration = DEFAULT_DURATION
        return tune.split(' ', '\t', '\n').mapNotNull { token ->
            if (token.isEmpty()) return@mapNotNull null
            val letter = token[0].lowercaseChar()
            val rest = letter == 'r'
            var idx = 1
            var accidental = 0
            // 升号 / 降号（仅影响本音符，不延续）
            if (idx < token.length) {
                when (token[idx]) {
                    '#' -> {
                        accidental = 1
                        idx++
                    }
                    'b' -> {
                        accidental = -1
                        idx++
                    }
                }
            }
            // 八度数字（显式指定则延续到后续音符）
            if (idx < token.length && token[idx].isDigit()) {
                octave = token[idx] - '0'
                idx++
            }
            // 时值（显式指定则延续到后续音符）
            if (idx < token.length && token[idx] == ':') {
                val ticks = token.substring(idx + 1).trim().toIntOrNull()
                if (ticks != null && ticks > 0) duration = ticks
            }
            val freq = if (rest) {
                0.0
            } else {
                val offset = NOTE_OFFSET[letter] ?: return@mapNotNull null
                440.0 * 2.0.pow(octave + (offset + accidental - MIDI_A4) / 12.0)
            }
            Note(freq, duration)
        }
    }

    private const val DEFAULT_OCTAVE = 4
    private const val DEFAULT_DURATION = 4
    private const val MIDI_A4 = 69

    /** 字母 → 相对 A4 的半音偏移（A=21 … G=19，见固件 `_NOTE_OFFSET`）。 */
    private val NOTE_OFFSET = mapOf(
        'a' to 21, 'b' to 23, 'c' to 12,
        'd' to 14, 'e' to 16, 'f' to 17, 'g' to 19
    )

    /** 21 首内置旋律（数据来源见类注释；均为公有领域 / 公平教育使用）。 */
    private val TUNES: Map<String, String> = mapOf(
        "DADADADUM" to "r4:2 g g g eb:8 r:2 f f f d:8",
        "ENTERTAINER" to "d4:1 d# e c5:2 e4:1 c5:2 e4:1 c5:3 c:1 d d# e c d e:2 b4:1 d5:2 c:4",
        "PRELUDE" to "c4:1 e g c5 e g4 c5 e c4 e g c5 e g4 c5 e c4 d g d5 f g4 d5 f c4 d g d5 f g4 d5 f b3 d4 g d5 f g4 d5 f b3 d4 g d5 f g4 d5 f c4 e g c5 e g4 c5 e c4 e g c5 e g4 c5 e",
        "ODE" to "e4 e f g g f e d c c d e e:6 d:2 d:8 e:4 e f g g f e d c c d e d:6 c:2 c:8",
        "NYAN" to "f#5:2 g# c#:1 d#:2 b4:1 d5:1 c# b4:2 b c#5 d d:1 c# b4:1 c#5:1 d# f# g# d# f# c# d b4 c#5 b4 d#5:2 f# g#:1 d# f# c# d# b4 d5 d# d c# b4 c#5 d:2 b4:1 c#5 d# f# c# d c# b4 c#5:2 b4 c#5 b4 f#:1 g# b:2 f#:1 g# b c#5 d# b4 e5 d# e f# b4:2 b f#:1 g# b f# e5 d# c# b4 f# d# e f# b:2 f#:1 g# b:2 f#:1 g# b b c#5 d# b4 f# g# f# b:2 b:1 a# b f# g# b e5 d# e f# b4:2 c#5",
        "RINGTONE" to "c4:1 d e:2 g d:1 e f:2 a e:1 f g:2 b c5:4",
        "FUNK" to "c2:2 c d# c:1 f:2 c:1 f:2 f# g c c g c:1 f#:2 c:1 f#:2 f d#",
        "BLUES" to "c2:2 e g a a# a g e c2:2 e g a a# a g e f a c3 d d# d c a2 c2:2 e g a a# a g e g b d3 f f2 a c3 d# c2:2 e g e g f e d",
        "BIRTHDAY" to "c4:3 c:1 d:4 c:4 f e:8 c:3 c:1 d:4 c:4 g f:8 c:3 c:1 c5:4 a4 f e d a#:3 a#:1 a:4 f g f:8",
        "WEDDING" to "c4:4 f:3 f:1 f:8 c:4 g:3 e:1 f:8 c:4 f:3 a:1 c5:4 a4:3 f:1 f:4 e:3 f:1 g:8",
        "FUNERAL" to "c3:4 c:3 c:1 c:4 d#:3 d:1 d:3 c:1 c:3 b2:1 c3:4",
        "PUNCHLINE" to "c4:3 g3:1 f# g g#:3 g r b c4",
        "PYTHON" to "d5:1 b4 r b b a# b g5 r d d r b4 c5 r c c r d e:5 c:1 a4 r a a g# a f#5 r e e r c b4 r b b r c5 d:5 d:1 b4 r b b a# b b5 r g g r d c# r a a r a a:5 g:1 f#:2 a:1 a g# a e:2 a:1 a g# a d r c# d r c# d:2 r:3",
        "BADDY" to "c3:3 r d:2 d# r c r f#:8",
        "CHASE" to "a4:1 b c5 b4 a:2 r a:1 b c5 b4 a:2 r a:2 e5 d# e f e d# e b4:1 c5 d c b4:2 r b:1 c5 d c b4:2 r b:2 e5 d# e f e d# e",
        "BA_DING" to "b5:1 e6:3",
        "WAWAWAWAA" to "e3:3 r:1 d#:3 r:1 d:4 r:1 c#:8",
        "JUMP_UP" to "c5:1 d e f g",
        "JUMP_DOWN" to "g5:1 f e d c",
        "POWER_UP" to "g4:1 c5 e g:2 e:1 g:3",
        "POWER_DOWN" to "g5:1 d# c g4:2 b:1 c5:3"
    )
}

/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

/**
 * 谱曲台（TunePad）的音符串解析：语法与 micro:bit music 模块一致——
 * 字母 a–g（大小写不拘）+ 可选升降号（# 升 / b 降）+ 可选八度数字 + 可选 `:时值`；
 * 八度与时值在一首曲子内延续，默认八度 4、默认时值 4（四分音符）。
 * 例："c d e f g a b"、"c#5:2 e:1 r"。
 *
 * 与 [Melodies] 同一套十二平均律换算（A4=440Hz，偏移 A=21…G=19），
 * 保证「谱曲台」手写的曲子与固件内置旋律音高一致。
 */
object TuneParser {

    data class Note(val freqHz: Double, val beats: Double, val rest: Boolean)

    /** 每秒拍数固定 120bpm、每拍 4 tick、每 tick 125ms → 一拍 500ms（与固件默认一致）。 */
    const val BEAT_MS = 500.0

    private val OFFSETS = mapOf('c' to 12, 'd' to 14, 'e' to 16, 'f' to 17, 'g' to 19, 'a' to 21, 'b' to 23)

    /**
     * 解析音符串；无法识别的 token 被跳过（防御手滑输入）。
     * 返回值不含休止的哑音处理——休止以 [Note.rest] 标记，频率 0。
     */
    fun parse(tune: String): List<Note> {
        var octave = 4
        var duration = 4
        val out = ArrayList<Note>()
        tune.split(' ', '\n', '\t').forEach { raw ->
            val token = raw.trim().lowercase()
            if (token.isEmpty()) return@forEach
            var i = 0
            var accidental = 0
            val letter = token[0]
            i = 1
            if (i < token.length && (token[i] == '#' || token[i] == 'b')) {
                accidental = if (token[i] == '#') 1 else -1
                i++
            }
            while (i < token.length && token[i].isDigit()) {
                octave = token[i] - '0'
                i++
            }
            if (i < token.length && token[i] == ':') {
                val d = token.substring(i + 1).trim().toIntOrNull()
                if (d != null && d > 0) duration = d
            }
            if (letter == 'r') {
                out.add(Note(0.0, duration / 4.0, true))
            } else {
                val offset = OFFSETS[letter] ?: return@forEach
                // 偏移表比标准半音高 12（A=21 即半音 9），换算成标准 MIDI 音高
                val midi = (octave + 1) * 12 + offset - 12 + accidental
                out.add(Note(440.0 * Math.pow(2.0, (midi - 69) / 12.0), duration / 4.0, false))
            }
        }
        return out
    }

    /** 单个音符的发声时长（毫秒）：时值 4 = 一拍 = 500ms；结尾音稍短避免连成一片。 */
    fun durMs(note: Note): Int {
        val total = (note.beats * BEAT_MS).toInt()
        return if (note.rest) total else (total - 10).coerceAtLeast(30)
    }

    /** 示例曲谱（界面预置按钮用）。 */
    val EXAMPLES: List<Pair<String, String>> = listOf(
        "小星星" to "c c g g a a g:2 f f e e d d c:2",
        "欢乐颂片段" to "e e f g g f e d c c d e e:2 d:2",
        "生日歌片段" to "g g a g c b:2 g g a g d c:2",
        "上升音阶" to "c d e f g a b c5",
        "两只老虎" to "c d e c c d e c e f g:2 e f g:2",
    )
}

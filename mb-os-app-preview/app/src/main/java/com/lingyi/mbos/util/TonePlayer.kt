/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import kotlin.math.PI
import kotlin.math.sin

/**
 * 模拟器本地发声：真机的蜂鸣 / 旋律由 micro:bit 扬声器播放，模拟器没有硬件，
 * 由本类在手机端还原 `TONE:` / `MELODY:` 的声音（对齐固件 `music.pitch` / `music.play` 语义）。
 *
 * 设计要点：
 *  - **丢弃式覆盖**：新声音立即打断旧声音后从头播放——摩斯电码 48ms 的点、钢琴快速连奏
 *    若排队播放会严重拖慢节拍，必须「后来者上」；
 *  - 每个声音生成一段静态 PCM（正弦波 + 8ms 淡入淡出防爆音）写入一个 MODE_STATIC
 *    AudioTrack，播完经兜底定时器回收，无常驻播放线程；
 *  - 旋律按固件 `gate_off_time_ms = 10` 的断奏方式拼成整段（发声 时长-10ms + 10ms 静音）；
 *  - **一切异常静默兜底**：音频设备不可用（如 JVM 单测环境、无音频输出的设备）时
 *    自动退化为无声，绝不影响模拟器链路。
 *
 * 非线程安全约束：所有公开方法可在任意线程调用；内部状态由 [lock] 与单线程执行器保护。
 */
class TonePlayer {

    private val lock = Any()
    private val handler = Handler(Looper.getMainLooper())

    /** 单线程执行器：PCM 生成与轨道创建都在这上面跑，不占调用线程（UI 线程）。 */
    private val exec = Executors.newSingleThreadExecutor(ThreadFactory { r ->
        Thread(r, "sim-tone").apply { isDaemon = true }
    })

    /** 当前正在播放（或等待兜底回收）的轨道。 */
    private var current: AudioTrack? = null

    /** 播放一个蜂鸣音（对齐 `music.pitch(freq, durMs)`；时长上限 30s，防病理分配）。 */
    fun tone(freqHz: Int, durMs: Int) {
        if (freqHz <= 0 || durMs <= 0) return
        val dur = durMs.coerceAtMost(MAX_TONE_MS)
        exec.execute {
            runCatching {
                val samples = sineSamples(freqHz.toDouble(), dur.toDouble(), fade = true)
                playBuffer(samples, dur.toLong())
            }
        }
    }

    /** 播放一首内置旋律（[Melodies.parse] 的输出；freqHz = 0 表示休止）。 */
    fun melody(notes: List<Note>) {
        if (notes.isEmpty()) return
        exec.execute {
            runCatching {
                val samples = melodySamples(notes)
                val totalMs = notes.sumOf { it.durMs }.toLong()
                playBuffer(samples, totalMs)
            }
        }
    }

    /** 立即停止当前声音（断开连接 / 悬浮窗销毁时调用）。 */
    fun stop() {
        synchronized(lock) { stopCurrentLocked() }
    }

    // ---------- PCM 生成 ----------

    /** 单音正弦采样；[fade] 为 true 时首尾各 8ms 淡入淡出，避免点击爆音。 */
    private fun sineSamples(freqHz: Double, durMs: Double, fade: Boolean): ShortArray {
        val total = sampleCount(durMs)
        val out = ShortArray(total)
        val fadeLen = if (fade) (SAMPLE_RATE * 0.008).toInt().coerceAtMost(total / 2) else 0
        for (i in 0 until total) {
            val env = when {
                fadeLen <= 0 -> 1.0
                i < fadeLen -> i.toDouble() / fadeLen
                i >= total - fadeLen -> (total - i).toDouble() / fadeLen
                else -> 1.0
            }
            out[i] = pcm(sin(2.0 * PI * freqHz * i / SAMPLE_RATE) * env)
        }
        return out
    }

    /**
     * 旋律拼段：每个音发声 durMs-10ms 后留 10ms 静音（固件 gate_off 断奏），
     * 音头 2ms 淡入、尾 8ms 淡出，相邻音之间不糊成一片。
     */
    private fun melodySamples(notes: List<Note>): ShortArray {
        val chunks = ArrayList<ShortArray>(notes.size)
        var total = 0
        notes.forEach { n ->
            val nSamples = sampleCount(n.durMs)
            val buf = ShortArray(nSamples)
            if (n.freqHz > 0) {
                val gate = (SAMPLE_RATE * GATE_MS / 1000.0).toInt()
                val sound = if (n.durMs > GATE_MS) nSamples - gate else nSamples
                val fadeIn = (SAMPLE_RATE * 0.002).toInt().coerceAtMost(sound / 2)
                val fadeOut = (SAMPLE_RATE * 0.008).toInt().coerceAtMost(sound / 2)
                for (i in 0 until sound) {
                    val env = when {
                        i < fadeIn -> i.toDouble() / fadeIn
                        i >= sound - fadeOut -> (sound - i).toDouble() / fadeOut
                        else -> 1.0
                    }
                    buf[i] = pcm(sin(2.0 * PI * n.freqHz * i / SAMPLE_RATE) * env)
                }
            }
            chunks.add(buf)
            total += nSamples
        }
        val out = ShortArray(total)
        var off = 0
        chunks.forEach { c -> c.copyInto(out, off); off += c.size }
        return out
    }

    private fun sampleCount(durMs: Double): Int = (SAMPLE_RATE * durMs / 1000.0).toInt().coerceAtLeast(1)

    private fun pcm(v: Double): Short = (v * AMPLITUDE * Short.MAX_VALUE).toInt()
        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

    // ---------- 播放与回收 ----------

    private fun playBuffer(samples: ShortArray, totalMs: Long) {
        synchronized(lock) {
            stopCurrentLocked()
            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            }.getOrNull() ?: return
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                runCatching { track.release() }
                return
            }
            val written = runCatching { track.write(samples, 0, samples.size) }.getOrDefault(-1)
            if (written < 0) {
                runCatching { track.release() }
                return
            }
            runCatching { track.play() }
            current = track
            // 兜底回收：部分设备的播放完成回调不可靠，按时长延后释放；
            // 期间若已被新声音替换（引用不等），则不重复释放。
            handler.postDelayed({ releaseIfCurrent(track) }, totalMs + RECLAIM_DELAY_MS)
        }
    }

    private fun releaseIfCurrent(track: AudioTrack) {
        synchronized(lock) {
            if (current === track) {
                runCatching { track.stop() }
                runCatching { track.release() }
                current = null
            }
        }
    }

    private fun stopCurrentLocked() {
        current?.let { t ->
            runCatching { t.stop() }
            runCatching { t.release() }
        }
        current = null
    }

    companion object {
        /** 采样率：16kHz 对最高 4kHz 出头的音阶/蜂鸣足够，且旋律缓冲区更省。 */
        const val SAMPLE_RATE = 16000

        /** 幅度 0.35：手机扬声器中等音量下清晰不刺耳。 */
        private const val AMPLITUDE = 0.35

        /** 固件 gate_off_time_ms：音与音之间留 10ms 静音做断奏。 */
        private const val GATE_MS = 10.0

        /** 兜底回收的额外等待（ms）：略大于实际播放时长，确保播完再释放。 */
        private const val RECLAIM_DELAY_MS = 250L

        /** 单音时长上限（ms）：应用内蜂鸣都在 400ms 以内，上限只防病理长音的缓冲膨胀。 */
        private const val MAX_TONE_MS = 30_000
    }
}

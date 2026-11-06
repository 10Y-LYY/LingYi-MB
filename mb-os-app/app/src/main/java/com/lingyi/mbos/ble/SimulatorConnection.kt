/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ble

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.lingyi.mbos.ui.FirmwareScript
import com.lingyi.mbos.util.Melodies
import com.lingyi.mbos.util.TonePlayer
import kotlin.math.abs
import kotlin.random.Random

/**
 * micro:bit V2 模拟器连接（第三连接类型 "sim"）。
 *
 * 用途：没有真机时完整调试 App——控制台 / 预览 / 指针 / 引导各页
 * 均通过 ConnectionHub 收发数据，接入本实现后与真机走同一条链路。
 *
 * 行为对齐固件脚本（FirmwareScript.script / assets 合并 HEX 中的 main.py）：
 *  - 指令解析与回包逐一对应（含未知指令的 ERR: 回包、BRIGHT 的 OK/ERR）；
 *  - MOUSE 模式按固件相同参数（死区 60mg / 缩放 48 / 20ms 最小间隔）上报增量；
 *  - READ 回包字段与固件完全一致（A|B|T|L|X|Y|Z|C|G）。
 *
 * 模拟器独有（真机没有的辅助能力）：
 *  - 周期上报：每秒推送一次全量传感器行，供「实时读取」页面持续测试；
 *  - 虚拟姿态：setTiltBias 用方向键模拟倾斜，驱动 MOUSE 增量；
 *    setAttitude 则直接写死 X/Y/Z 与朝向（手动锁定，暂停自动漂移）；
 *  - 按键/手势注入：模拟 A/B 按下与摇一摇，供按钮与手势链路测试；
 *  - 本地发声：TONE / MELODY 在手机端还原蜂鸣与内置旋律
 *    （真机由 micro:bit 扬声器发声，模拟器没有硬件，故由 TonePlayer 播放）。
 */
class SimulatorConnection : MicrobitConnection {

    override val type: String = "sim"

    private val handler = Handler(Looper.getMainLooper())
    private var listener: MicrobitConnection.Listener? = null
        @Volatile
    private var connected = false

    // ---------- 板状态 ----------

    /** 5×5 LED 亮度矩阵（0–9）。 */
    private val led = Array(5) { IntArray(5) }
        @Volatile
    private var brightness = 7
        @Volatile
    private var scrollText: String? = null

    /** 传感器模拟值。 */
        @Volatile
    private var temperature = 24
        @Volatile
    private var light = 180
        @Volatile
    private var accelX = 0
        @Volatile
    private var accelY = 0
        @Volatile
    private var accelZ = -1024
        @Volatile
    private var heading = 135
        @Volatile
    private var gesture = "none"
        @Volatile
    private var btnA = false
        @Volatile
    private var btnB = false

    /** 虚拟姿态偏移（mg）：由悬浮窗方向键设置，模拟手持倾斜。 */
        @Volatile
    private var tiltBiasX = 0
        @Volatile
    private var tiltBiasY = 0

    /**
     * 手动姿态锁（悬浮窗「姿态微调」）：置位后 [driftSensors] 暂停 X/Y/Z 与朝向的
     * 随机漂移（温度 / 光线照常漂），直到 [resumeAutoSensors] 恢复。
     * 便于把板子「摆」到指定朝向上稳定测试，而不被漂移打乱。
     */
        @Volatile
    private var attitudeManual = false

    /** 本地蜂鸣 / 旋律播放（模拟器无扬声器硬件，在手机端还原声音）。 */
    private val tonePlayer = TonePlayer()

    // ---------- MOUSE（陀螺仪指针）模式：参数与固件一致 ----------
    private var mouseOn = false
    private var mouseCx = 0
    private var mouseCy = 0
    private var mouseLastA = false
    private var mouseLastB = false
    private var mouseLastSendAt = 0L

    /**
     * 周期上报开关（默认关）。
     * 历史默认开会让控制台每收到一条 READ 就弹一次模态框，模拟器一连上就每秒弹窗、关不掉；
     * 预览页与「实时仪表盘」均会自行轮询读数，不依赖此推送，故默认关闭，需要时再开。
     */
    var periodicReport = false

    // ---------- 连接生命周期 ----------

    override fun connect(context: Context, listener: MicrobitConnection.Listener) {
        this.listener = listener
        connected = true
        listener.onState(ConnState(true, "模拟器已连接"))
        scheduleTicks()
    }

    override fun disconnect() {
        if (!connected) return
        connected = false
        mouseOn = false
        tonePlayer.stop()
        handler.removeCallbacksAndMessages(null)
        listener?.onState(ConnState(false, "模拟器已断开"))
        listener = null
    }

    override fun isConnected(): Boolean = connected

    // ---------- 周期任务 ----------

    private val sensorTick = object : Runnable {
        override fun run() {
            if (!connected) return
            driftSensors()
            if (periodicReport) emit(readLine())
            handler.postDelayed(this, SENSOR_INTERVAL_MS)
        }
    }

    private val mouseTick = object : Runnable {
        override fun run() {
            if (!connected || !mouseOn) return
            // 与固件 _mouse_tick 同参：死区 60mg、缩放 48、最小间隔 20ms
            val dx0 = (accelX + tiltBiasX) - mouseCx
            val dy0 = (accelY + tiltBiasY) - mouseCy
            var dx = if (abs(dx0) < MOUSE_DEADZONE) 0 else dx0 / MOUSE_SCALE
            var dy = if (abs(dy0) < MOUSE_DEADZONE) 0 else dy0 / MOUSE_SCALE
            val a = btnA
            val b = btnB
            val now = SystemClock.uptimeMillis()
            val moved = dx != 0 || dy != 0
            val btnChanged = a != mouseLastA || b != mouseLastB
            if ((moved || btnChanged) && now - mouseLastSendAt >= MOUSE_INTERVAL_MS) {
                emit("MOUSE:$dx,$dy,${if (a) 1 else 0},${if (b) 1 else 0}\n")
                mouseLastSendAt = now
                mouseLastA = a
                mouseLastB = b
            }
            handler.postDelayed(this, MOUSE_TICK_MS)
        }
    }

    private fun scheduleTicks() {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(sensorTick, SENSOR_INTERVAL_MS)
        if (mouseOn) handler.postDelayed(mouseTick, MOUSE_TICK_MS)
    }

    // ---------- 指令解析（对齐固件 parse()） ----------

    override fun send(line: String): Boolean {
        if (!connected) return false
        val cmd = line.trim()
        if (cmd.isEmpty()) return true
        when {
            cmd.startsWith("SCROLL:") -> scrollText = cmd.removePrefix("SCROLL:")
            cmd.startsWith("NUMBER:") -> scrollText = cmd.removePrefix("NUMBER:")
            cmd.startsWith("SHOW:") -> parseShow(cmd.removePrefix("SHOW:"))
            cmd.startsWith("PLOT:") -> parsePlot(cmd.removePrefix("PLOT:"))
            cmd.startsWith("TONE:") -> {
                // 真机由板载扬声器蜂鸣；模拟器在手机端本地还原（对齐 music.pitch，无回包）
                val rest = cmd.removePrefix("TONE:")
                val freq = rest.substringBefore(',').trim().toIntOrNull()
                val dur = rest.substringAfter(',').trim().toIntOrNull()
                if (freq != null && dur != null) tonePlayer.tone(freq, dur)
            }
            cmd.startsWith("MELODY:") -> {
                val name = cmd.removePrefix("MELODY:").trim().uppercase()
                // 固件仅认 music 模块内置旋律名
                val known = setOf(
                    "DADADADUM", "ENTERTAINER", "PRELUDE", "ODE", "NYAN", "RINGTONE",
                    "FUNK", "BLUES", "BIRTHDAY", "WEDDING", "FUNERAL", "PUNCHLINE",
                    "PYTHON", "BADDY", "CHASE", "BA_DING", "WAWAWAWAA", "JUMP_UP",
                    "JUMP_DOWN", "POWER_UP", "POWER_DOWN"
                )
                if (name in known) {
                    Melodies.tune(name)?.let { tonePlayer.melody(Melodies.parse(it)) }
                    emit("OK:MELODY:$name\n")
                } else {
                    emit("ERR:MELODY\n")
                }
            }
            cmd.startsWith("BRIGHT:") -> {
                val v = cmd.removePrefix("BRIGHT:").toIntOrNull()
                if (v == null) {
                    emit("ERR:BRIGHT\n")
                } else {
                    brightness = v.coerceIn(0, 255)
                    emit("OK:BRIGHT\n")
                }
            }
            cmd == "CLEAR" -> clearLed()
            cmd == "READ" -> emit(readLine())
            cmd == "COMPASS" -> emit("ERR:COMPASS\n") // 与固件一致：未实现该指令
            cmd == "INFO" -> emit(
                "INFO:MicroPython v2.1.1|MB-OS v${FirmwareScript.VERSION}|micro:bit V2\n"
            )
            cmd == "MOUSE:ON" -> {
                mouseOn = true
                mouseCx = accelX + tiltBiasX
                mouseCy = accelY + tiltBiasY
                mouseLastA = btnA
                mouseLastB = btnB
                emit("OK:MOUSE:ON\n")
                handler.removeCallbacks(mouseTick)
                handler.postDelayed(mouseTick, MOUSE_TICK_MS)
            }
            cmd == "MOUSE:OFF" -> {
                mouseOn = false
                handler.removeCallbacks(mouseTick)
                emit("OK:MOUSE:OFF\n")
            }
            cmd == "MOUSE:CAL" -> {
                mouseCx = accelX + tiltBiasX
                mouseCy = accelY + tiltBiasY
                emit("OK:MOUSE:CAL\n")
            }
            else -> emit("ERR:$cmd\n")
        }
        return true
    }

    private fun parseShow(rows: String) {
        val parts = rows.split(",")
        for (y in 0 until 5) {
            val r = parts.getOrNull(y) ?: "00000"
            for (x in 0 until 5) {
                led[y][x] = if (x < r.length && r[x] == '1') 9 else 0
            }
        }
    }

    private fun parsePlot(rest: String) {
        val p = rest.split(",")
        if (p.size < 3) return
        val x = p[0].trim().toIntOrNull() ?: return
        val y = p[1].trim().toIntOrNull() ?: return
        val on = p[2].trim() == "1"
        if (x in 0..4 && y in 0..4) led[y][x] = if (on) 9 else 0
    }

    private fun clearLed() {
        for (y in 0 until 5) for (x in 0 until 5) led[y][x] = 0
    }

    /** READ 回包：字段顺序与固件一致。 */
    private fun readLine(): String = buildString {
        append("A:${if (btnA) 1 else 0}")
        append("|B:${if (btnB) 1 else 0}")
        append("|T:$temperature")
        append("|L:$light")
        append("|X:${accelX + tiltBiasX}")
        append("|Y:${accelY + tiltBiasY}")
        append("|Z:$accelZ")
        append("|C:$heading")
        append("|G:$gesture")
        append("\n")
    }

    private fun emit(line: String) {
        listener?.let { runCatching { it.onData(line) } }
    }

    // ---------- 悬浮窗控制面 ----------

    /** 虚拟方向键：按压期间叠加到姿态上，驱动 MOUSE 增量（松手回零由调用方再调一次 0）。 */
    fun setTiltBias(dxMg: Int, dyMg: Int) {
        tiltBiasX = dxMg
        tiltBiasY = dyMg
    }

    /**
     * 手动设置板子姿态（悬浮窗「姿态微调」）：
     * 写死 X/Y/Z（mg）与朝向（度），并锁定手动模式——[driftSensors] 随之暂停
     * X/Y/Z 与朝向的随机漂移；周期上报开启时立即推送一条 READ 供各页面同步。
     * 方向键偏移（[setTiltBias]）作为叠加量保留，二者互不干扰。
     */
    fun setAttitude(xMg: Int, yMg: Int, zMg: Int, headingDeg: Int) {
        if (!connected) return
        accelX = xMg.coerceIn(-2048, 2047)
        accelY = yMg.coerceIn(-2048, 2047)
        accelZ = zMg.coerceIn(-2048, 2047)
        heading = ((headingDeg % 360) + 360) % 360
        attitudeManual = true
        if (periodicReport) emit(readLine())
    }

    /** 恢复传感器自动漂移（退出手动姿态锁）。 */
    fun resumeAutoSensors() {
        attitudeManual = false
    }

    fun setButtonA(down: Boolean) {
        btnA = down
    }

    fun setButtonB(down: Boolean) {
        btnB = down
    }

    /** 注入一次手势（如 "shake"），供预览页「最近手势」链路测试。 */
    fun injectGesture(g: String) {
        gesture = g
        if (periodicReport) emit(readLine())
        gesture = "none"
    }

    /** 快照：供悬浮窗渲染传感器值与 LED 预览。 */
    private val stateLock = Any()

    fun snapshot(): Snapshot = synchronized(stateLock) { Snapshot(
        connected = connected,
        mouseOn = mouseOn,
        periodicReport = periodicReport,
        brightness = brightness,
        scrollText = scrollText,
        temperature = temperature,
        light = light,
        accelX = accelX + tiltBiasX,
        accelY = accelY + tiltBiasY,
        accelZ = accelZ,
        heading = heading,
        gesture = gesture,
        btnA = btnA,
        btnB = btnB,
        attitudeManual = attitudeManual,
            led = Array(5) { y -> IntArray(5) { x -> led[y][x] } }
        )
    }

    /** 传感器模拟值快照（不可变视图）。 */
    data class Snapshot(
        val connected: Boolean,
        val mouseOn: Boolean,
        val periodicReport: Boolean,
        val brightness: Int,
        val scrollText: String?,
        val temperature: Int,
        val light: Int,
        val accelX: Int,
        val accelY: Int,
        val accelZ: Int,
        val heading: Int,
        val gesture: String,
        val btnA: Boolean,
        val btnB: Boolean,
        val attitudeManual: Boolean,
        val led: Array<IntArray>
    ) {
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
    }

    /** 随机漫步：让周期上报的传感器值自然变化（调试时更接近真机观感）。 */
    fun driftSensors() {
        if (!connected) return
        temperature += Random.nextInt(-1, 2)
        light = (light + Random.nextInt(-15, 16)).coerceIn(0, 255)
        // 手动姿态锁开启时，X/Y/Z 与朝向保持用户设定值不漂
        if (!attitudeManual) {
            accelX = (accelX + Random.nextInt(-30, 31)).coerceIn(-2048, 2047)
            accelY = (accelY + Random.nextInt(-30, 31)).coerceIn(-2048, 2047)
            heading = (heading + Random.nextInt(-3, 4) + 360) % 360
        }
    }

    companion object {
        private const val SENSOR_INTERVAL_MS = 1000L
        private const val MOUSE_TICK_MS = 20L
        private const val MOUSE_INTERVAL_MS = 20L
        private const val MOUSE_DEADZONE = 60
        private const val MOUSE_SCALE = 48
    }
}

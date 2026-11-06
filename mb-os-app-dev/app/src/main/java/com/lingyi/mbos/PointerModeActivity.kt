/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.lingyi.mbos.ble.ConnectionHub
import com.lingyi.mbos.ble.ConnState
import com.lingyi.mbos.ble.MicrobitConnection
import com.lingyi.mbos.databinding.ActivityPointerBinding
import com.lingyi.mbos.ui.FirmwareScript
import com.lingyi.mbos.ui.M3Kit
import com.lingyi.mbos.ui.Protocol
import com.lingyi.mbos.util.Prefs

/**
 * 陀螺仪指针（无线触摸笔）控制面板。
 *
 * 数据循环已搬到 PointerService（前台服务）——本页只负责：
 *  · 一键启动 / 停止常驻指针（启动后退出本页甚至退出 App 仍持续工作）；
 *  · 显示连接 / 无障碍服务 / 常驻运行状态；
 *  · 调节灵敏度与高级控制（按键映射 / 滚动模式 / 死区 / 平滑 / 曲线 / 自动回中）；
 *  · 重新校准中心、跳无障碍设置、提示固件过旧。
 *
 * MOUSE 数据流由 PointerService 持有，故本页 onStop 不再下发 MOUSE:OFF，
 * 指针在离开页面后仍由服务持续驱动。
 */
class PointerModeActivity : BaseActivity() {

    private lateinit var b: ActivityPointerBinding

    private var connected = false
    private var lastBtnA = false
    private var lastBtnB = false

    /** 最近一次收到的增量，仅用于界面展示。 */
    private var lastDx = 0
    private var lastDy = 0

    /** 本次进入页面是否已就「固件过旧」提示过（避免重复弹）。 */
    private var fwWarned = false

    private val listener = object : MicrobitConnection.Listener {
        override fun onState(state: ConnState) {
            runOnUiThread {
                connected = state.connected
                renderStatus()
                renderRunState()
            }
        }

        override fun onData(data: String) {
            if (!data.startsWith("MOUSE:")) return
            runOnUiThread { renderLive(data) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPointerBinding.inflate(layoutInflater)
        setContentView(b.root)

        // M3 Expressive：整页可点击元素统一按压反馈（4.1.1-preview）
        M3Kit.applyPressFeedback(b.root)

        b.toolbar.setNavigationOnClickListener { finish() }

        b.btnEnableService.setOnClickListener {
            if (PointerAccessibilityService.isEnabled(this)) {
                Snackbar.make(b.root, R.string.pointer_service_on, Snackbar.LENGTH_SHORT).show()
            } else {
                PointerAccessibilityService.openSettings(this)
            }
        }
        b.btnCalibrate.setOnClickListener { calibrate() }
        b.btnToggleRun.setOnClickListener { toggleRun() }

        // 灵敏度滑块：读取即吸附到合法网格（0.4 + n×0.2），
        // 并把收敛后的干净值回写，彻底杜绝历史野值（如 1.2666667）触发布局期崩溃。
        val rawSens = Prefs.getPointerSensitivity(this)
        val snapped = Prefs.snapSensitivity(rawSens)
        if (snapped != rawSens) Prefs.setPointerSensitivity(this, snapped)
        b.sliderSensitivity.value = snapped
        b.tvSensitivity.text = String.format("%.1f×", snapped)
        b.sliderSensitivity.addOnChangeListener { _, value, _ ->
            Prefs.setPointerSensitivity(this, value)
            b.tvSensitivity.text = String.format("%.1f×", Prefs.getPointerSensitivity(this))
        }

        setupSpinners()
        setupSwitches()
        setupSliders()

        // ===== 稳定性工具（4.1.0-preview）：掉线保护 / 手感一键复位 =====
        b.btnReconnectNow.setOnClickListener { reconnectNow() }
        b.btnResetSens.setOnClickListener { confirmResetFeel() }

        ConnectionHub.addListener(listener)
        connected = ConnectionHub.isConnected()
        renderStatus()
        renderRunState()
        renderAdvanced()
        warnIfFirmwareStale()
    }

    override fun onStart() {
        super.onStart()
        renderRunState()
    }

    override fun onResume() {
        super.onResume()
        renderStatus()
        renderRunState()
    }

    override fun onDestroy() {
        ConnectionHub.removeListener(listener)
        super.onDestroy()
    }

    // ---------- 常驻运行控制 ----------

    private fun toggleRun() {
        if (PointerService.isRunning()) {
            stopService(Intent(this, PointerService::class.java))
            renderRunState()
            return
        }
        if (!PointerAccessibilityService.isEnabled(this)) {
            PointerAccessibilityService.openSettings(this)
            return
        }
        if (!ConnectionHub.isConnected()) {
            Snackbar.make(b.root, R.string.pointer_not_connected, Snackbar.LENGTH_SHORT).show()
            return
        }
        ContextCompat.startForegroundService(this, Intent(this, PointerService::class.java))
        renderRunState()
    }

    // ---------- 高级控制 ----------

    private val actionPairs = listOf(
        "单击" to Prefs.POINTER_ACTION_TAP,
        "双击" to Prefs.POINTER_ACTION_DOUBLE,
        "拖动切换" to Prefs.POINTER_ACTION_DRAG
    )
    private val curvePairs = listOf(
        "线性" to Prefs.POINTER_CURVE_LINEAR,
        "指数" to Prefs.POINTER_CURVE_EXP
    )

    private fun setupSpinners() {
        bindSpinner(b.spinnerBtnA, actionPairs, Prefs.getPointerBtnA(this)) {
            Prefs.setPointerBtnA(this, actionPairs[it].second)
        }
        bindSpinner(b.spinnerBtnB, actionPairs, Prefs.getPointerBtnB(this)) {
            Prefs.setPointerBtnB(this, actionPairs[it].second)
        }
        bindSpinner(b.spinnerCurve, curvePairs, Prefs.getPointerCurve(this)) {
            Prefs.setPointerCurve(this, curvePairs[it].second)
        }
    }

    private fun bindSpinner(spinner: Spinner, pairs: List<Pair<String, String>>, currentKey: String, onPick: (Int) -> Unit) {
        val labels = pairs.map { it.first }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinner.setSelection(maxOf(0, pairs.indexOfFirst { it.second == currentKey }))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = onPick(pos)
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun setupSwitches() {
        b.switchScroll.isChecked = Prefs.isPointerScrollMode(this)
        b.switchScroll.setOnCheckedChangeListener { _, on -> Prefs.setPointerScrollMode(this, on) }
        b.switchAutorecenter.isChecked = Prefs.isPointerAutoRecenter(this)
        b.switchAutorecenter.setOnCheckedChangeListener { _, on -> Prefs.setPointerAutoRecenter(this, on) }
    }

    private fun setupSliders() {
        val dz = Prefs.getPointerDeadzone(this)
        b.sliderDeadzone.value = dz
        renderDeadzone(dz)
        b.sliderDeadzone.addOnChangeListener { _, value, _ ->
            Prefs.setPointerDeadzone(this, value)
            renderDeadzone(value)
        }
        val sm = Prefs.getPointerSmoothing(this)
        b.sliderSmooth.value = sm
        renderSmooth(sm)
        b.sliderSmooth.addOnChangeListener { _, value, _ ->
            Prefs.setPointerSmoothing(this, value)
            renderSmooth(value)
        }
    }

    private fun renderDeadzone(v: Float) {
        b.tvDeadzone.text = if (v <= 0f) getString(R.string.pointer_deadzone_off)
        else getString(R.string.pointer_deadzone_on, v.toInt())
    }

    private fun renderSmooth(v: Float) {
        b.tvSmooth.text = getString(R.string.pointer_smoothing_val, v)
    }

    private fun renderAdvanced() {
        b.spinnerBtnA.setSelection(maxOf(0, actionPairs.indexOfFirst { it.second == Prefs.getPointerBtnA(this) }))
        b.spinnerBtnB.setSelection(maxOf(0, actionPairs.indexOfFirst { it.second == Prefs.getPointerBtnB(this) }))
        b.spinnerCurve.setSelection(maxOf(0, curvePairs.indexOfFirst { it.second == Prefs.getPointerCurve(this) }))
        b.switchScroll.isChecked = Prefs.isPointerScrollMode(this)
        b.switchAutorecenter.isChecked = Prefs.isPointerAutoRecenter(this)
        b.sliderDeadzone.value = Prefs.getPointerDeadzone(this)
        b.sliderSmooth.value = Prefs.getPointerSmoothing(this)
        renderDeadzone(b.sliderDeadzone.value)
        renderSmooth(b.sliderSmooth.value)
    }

    // ---------- UI ----------

    private fun renderStatus() {
        val serviceOn = PointerAccessibilityService.isEnabled(this)
        b.btnEnableService.text = getString(
            if (serviceOn) R.string.pointer_service_on else R.string.pointer_btn_enable
        )
        b.btnEnableService.isEnabled = !serviceOn
        b.tvConnState.text = buildString {
            append(
                if (connected) {
                    getString(R.string.pointer_conn_ok, ConnectionHub.type().uppercase())
                } else {
                    getString(R.string.pointer_not_connected)
                }
            )
            append("\n")
            append(
                if (serviceOn) {
                    getString(R.string.pointer_service_running)
                } else {
                    getString(R.string.pointer_need_service)
                }
            )
        }
        renderDropGuard()
    }

    /**
     * 掉线保护（稳定性功能）：指针服务运行中若连接断开，明确告警并给出
     * 自动重连状态与「立即重连」入口，避免「指针突然不动了」无从下手。
     */
    private fun renderDropGuard() {
        val running = PointerService.isRunning()
        val text = when {
            !running -> "指针服务未运行（启动常驻指针后这里会显示连接保护状态）"
            connected -> "✓ 连接正常，指针数据流持续中"
            else -> {
                val st = com.lingyi.mbos.util.AutoReconnect.status()
                "⚠ 指针服务运行中，但连接已断开\n${st.humanText()}\n可点「立即重连」，或等待自动重连。"
            }
        }
        b.tvDropGuard.text = text
    }

    /** 立即重连：按设置里的默认连接方式重连（自动重连放弃或用户等不及时用）。 */
    private fun reconnectNow() {
        if (ConnectionHub.isConnected()) {
            Snackbar.make(b.root, "当前已连接", Snackbar.LENGTH_SHORT).show()
            return
        }
        com.lingyi.mbos.util.AutoReconnect.cancel()
        ConnectionHub.connect(Prefs.getConnType(this), this, listener)
        Snackbar.make(b.root, "正在重连…", Snackbar.LENGTH_SHORT).show()
    }

    /** 一键复位手感：灵敏度 1.0× / 死区 0 / 平滑 0 / 线性曲线，回到出厂默认。 */
    private fun confirmResetFeel() {
        MaterialAlertDialogBuilder(this)
            .setTitle("复位手感参数")
            .setMessage("将灵敏度复位为 1.0×、死区 0、平滑 0、曲线改为线性。按键映射与滚动模式保持不变。")
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton("复位") { _, _ ->
                Prefs.setPointerSensitivity(this, Prefs.snapSensitivity(1.0f))
                Prefs.setPointerDeadzone(this, 0f)
                Prefs.setPointerSmoothing(this, 0f)
                Prefs.setPointerCurve(this, Prefs.POINTER_CURVE_LINEAR)
                renderAdvanced()
                Snackbar.make(b.root, "已复位为默认手感", Snackbar.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun renderRunState() {
        val running = PointerService.isRunning()
        b.btnToggleRun.setText(if (running) R.string.pointer_stop else R.string.pointer_start)
        b.tvRunState.text = buildString {
            append(if (running) getString(R.string.pointer_run_on) else getString(R.string.pointer_run_idle))
            if (running) {
                append("\n")
                append(
                    if (ConnectionHub.isConnected()) getString(R.string.pointer_run_hint_connected)
                    else getString(R.string.pointer_run_hint_disconnected)
                )
            }
        }
        renderDropGuard()
    }

    private fun renderLive(data: String) {
        val parts = data.removePrefix("MOUSE:").trim().split(",")
        if (parts.size < 4) return
        lastDx = parts[0].toIntOrNull() ?: 0
        lastDy = parts[1].toIntOrNull() ?: 0
        lastBtnA = parts[2].trim() == "1"
        lastBtnB = parts[3].trim() == "1"
        val finger = PointerAccessibilityService.current()?.isFingerDown() == true
        b.tvLive.text = buildString {
            append("增量 X：$lastDx   增量 Y：$lastDy\n")
            append("按钮 A：${if (lastBtnA) "按下" else "松开"}\n")
            append("按钮 B：${if (lastBtnB) "按下" else "松开"}\n")
            append("系统手指：${if (finger) "按下" else "抬起"}")
        }
    }

    private fun calibrate() {
        if (!ConnectionHub.isConnected()) {
            Snackbar.make(b.root, R.string.pointer_not_connected, Snackbar.LENGTH_SHORT).show()
            return
        }
        ConnectionHub.send(Protocol.mouseCal())
        PointerAccessibilityService.current()?.recenter()
        Snackbar.make(b.root, R.string.pointer_cal_done, Snackbar.LENGTH_SHORT).show()
    }

    /**
     * 板载固件旧于内置版本时提示重新刷入。
     * 背景：旧固件 MOUSE 模式不生效，进入本页会「连上了但指针不动」。
     */
    private fun warnIfFirmwareStale() {
        if (fwWarned) return
        val known = Prefs.getKnownFirmwareVersion(this) ?: return
        if (!Protocol.isOlder(known, FirmwareScript.VERSION)) return
        fwWarned = true
        Snackbar.make(
            b.root,
            getString(R.string.pointer_fw_stale, known, FirmwareScript.VERSION),
            Snackbar.LENGTH_LONG
        ).show()
    }
}

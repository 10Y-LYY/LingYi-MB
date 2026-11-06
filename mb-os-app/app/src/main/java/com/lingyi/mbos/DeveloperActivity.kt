/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos

import android.bluetooth.BluetoothAdapter
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.lingyi.mbos.ble.ConnState
import com.lingyi.mbos.ble.ConnectionHub
import com.lingyi.mbos.ble.MicrobitConnection
import com.lingyi.mbos.ble.SimulatorConnection
import com.lingyi.mbos.databinding.ActivityDevBinding
import com.lingyi.mbos.ui.FirmwareScript
import com.lingyi.mbos.ui.M3Kit
import com.lingyi.mbos.ui.MicrobitGames
import com.lingyi.mbos.ui.Protocol
import com.lingyi.mbos.ui.StabilityFeatures
import com.lingyi.mbos.util.AnrWatchdog
import com.lingyi.mbos.util.AppLog
import com.lingyi.mbos.util.CrashLog
import com.lingyi.mbos.util.LogMailer
import com.lingyi.mbos.util.Prefs
import com.lingyi.mbos.util.UpdateChecker

/**
 * 开发者调试模式：集中调试 App 的各项功能。
 * 无需连接即可进入；连接后可直接下发真实指令。
 * 覆盖：应用信息 / 连接与设备诊断 / 指令控制台（协议） / 传感器数据模拟 /
 *       micro:bit V2 模拟器悬浮窗 / 内置内容查看 / 更新检查调试 / 调试状态重置。
 */
class DeveloperActivity : BaseActivity() {

    private lateinit var b: ActivityDevBinding
    private val logLines = ArrayDeque<String>()
    private var connected = false

    /** 是否正在联网检查更新（防止连点造成重复请求）。 */
    private var updateChecking = false

    private val heart = arrayOf(
        booleanArrayOf(false, true, false, true, false),
        booleanArrayOf(true, true, true, true, true),
        booleanArrayOf(true, true, true, true, true),
        booleanArrayOf(false, true, true, true, false),
        booleanArrayOf(false, false, true, false, false)
    )

    private val listener = object : MicrobitConnection.Listener {
        override fun onState(state: ConnState) {
            runOnUiThread {
                connected = state.connected
                renderConn()
                renderSimulatorStatus()
                log("← [状态] ${if (state.connected) "已连接" else state.message}")
            }
        }

        override fun onData(data: String) {
            runOnUiThread { log("← $data") }
        }
    }

    /** 悬浮窗权限申请后要执行的动作（模拟器面板 / 调试控制台二选一）。 */
    private var pendingOverlayAction: (() -> Unit)? = null

    /** 悬浮窗权限申请结果：授权后执行此前登记的动作（默认打开模拟器面板）。 */
    private val overlayPermLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val action = pendingOverlayAction
        pendingOverlayAction = null
        if (Settings.canDrawOverlays(this)) {
            if (action != null) action() else startService(Intent(this, SimulatorService::class.java))
        } else {
            Snackbar.make(b.root, R.string.dev_simulator_perm, Snackbar.LENGTH_LONG).show()
        }
        renderSimulatorStatus()
        renderDebugConsoleSwitch()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDevBinding.inflate(layoutInflater)
        setContentView(b.root)

        // M3 Expressive：整页可点击元素统一按压反馈（4.1.1-preview）
        M3Kit.applyPressFeedback(b.root)

        b.toolbar.setNavigationOnClickListener { finish() }

        ConnectionHub.addListener(listener)
        connected = ConnectionHub.isConnected()

        renderInfo()
        renderConn()
        buildCommandButtons()
        buildGameButtons()

        b.btnCopyDiag.setOnClickListener { copyDiag() }
        b.btnViewCrash.setOnClickListener { showCrashLog() }
        b.btnRefreshConn.setOnClickListener { connected = ConnectionHub.isConnected(); renderConn(); renderInfo() }
        b.btnListUsb.setOnClickListener { listUsb() }
        b.btnListBle.setOnClickListener { listBle() }
        b.btnOpenGuide.setOnClickListener { startActivity(Intent(this, GuideActivity::class.java)) }

        b.btnSend.setOnClickListener { sendRaw() }
        b.btnClearLog.setOnClickListener { logLines.clear(); b.tvLog.setText(R.string.dev_log_empty) }
        b.btnCopyLog.setOnClickListener { copyLog() }

        b.btnInject.setOnClickListener { injectMock() }
        b.btnInjectInfo.setOnClickListener {
            ConnectionHub.debugEmitData(
                "INFO:MicroPython 2.1.1|MB-OS v${FirmwareScript.VERSION}|micro:bit V2"
            )
            Snackbar.make(b.root, R.string.dev_mock_injected, Snackbar.LENGTH_SHORT).show()
        }
        b.btnInjectInfoBare.setOnClickListener {
            // 复现用户实况：板载只刷了裸 MicroPython，未安装 MB-OS 助手脚本
            ConnectionHub.debugEmitData("INFO:MicroPython 2.1.1|micro:bit V2")
            Snackbar.make(b.root, R.string.dev_mock_injected, Snackbar.LENGTH_SHORT).show()
        }

        b.btnViewFirmware.setOnClickListener { showCode("MicroPython 固件脚本", FirmwareScript.script) }

        b.btnDebugUpdate.setOnClickListener { runUpdateCheck() }

        // 更新通道：稳定版 / 预览版（下拉选择，与关于页、设置页共用同一 Prefs 值）
        b.channelPicker.refresh()
        b.channelPicker.onChannelChanged = {
            b.tvUpdateResult.text = "-"
            runUpdateCheck()
        }

        b.btnSelfCheck.setOnClickListener { protocolSelfCheck() }
        b.btnSimConnect.setOnClickListener { simulateState(true) }
        b.btnSimDisconnect.setOnClickListener { simulateState(false) }

        // micro:bit V2 模拟器（悬浮窗）：控制面 + 连接面
        b.btnSimOverlay.setOnClickListener { toggleSimulatorOverlay() }
        b.btnSimConnectReal.setOnClickListener { connectSimulator() }
        b.btnSimDisconnectReal.setOnClickListener {
            if (ConnectionHub.current is SimulatorConnection) ConnectionHub.disconnect()
            renderSimulatorStatus()
        }
        renderSimulatorStatus()

        b.btnOpenConsole.setOnClickListener { startActivity(Intent(this, MainActivity::class.java)) }
        b.btnOpenPreview.setOnClickListener { startActivity(Intent(this, PreviewActivity::class.java)) }
        b.btnOpenCustomFlash.setOnClickListener { startActivity(Intent(this, CustomFlashActivity::class.java)) }
        b.btnOpenAbout.setOnClickListener { startActivity(Intent(this, AboutActivity::class.java)) }

        // 开发者测试版 / 预览版专属「开发者工具箱」入口：仅 devtest 与 preview flavor 可见（stable 隐藏此卡）
        if (com.lingyi.mbos.BuildConfig.IS_DEVTEST) {
            b.cardDevTestTools.visibility = View.VISIBLE
            b.btnOpenDevTools.setOnClickListener {
                startActivity(Intent(this, com.lingyi.mbos.dev.DevToolsActivity::class.java))
            }
        } else {
            b.cardDevTestTools.visibility = View.GONE
        }

        b.btnListAssets.setOnClickListener { listAssets() }

        b.btnResetFirstLaunch.setOnClickListener {
            Prefs.resetFirstLaunch(this)
            Snackbar.make(b.root, R.string.dev_reset_first_done, Snackbar.LENGTH_LONG).show()
        }
        b.btnClearUpdateCache.setOnClickListener {
            Prefs.clearUpdateCache(this)
            Snackbar.make(b.root, R.string.dev_clear_update_done, Snackbar.LENGTH_SHORT).show()
        }
        b.btnDisableDev.setOnClickListener {
            Prefs.setDevMode(this, false)
            Snackbar.make(b.root, R.string.dev_closed, Snackbar.LENGTH_SHORT).show()
            finish()
        }

        // ===== 稳定性与调试（4.1.0-preview） =====
        runCatching {
            b.swDebugConsole.isChecked = DebugConsoleService.isRunning()
            b.swPacketCapture.isChecked = Prefs.isPacketCapture(this)
            b.swAnrWatchdog.isChecked = Prefs.isAnrWatchdog(this)
        }.onFailure { AppLog.e("Developer", "稳定性开关回填失败", it) }

        b.swDebugConsole.setOnCheckedChangeListener { _, on ->
            if (on) openDebugConsoleWithPermission() else DebugConsoleService.stop(this)
        }
        b.swPacketCapture.setOnCheckedChangeListener { _, on ->
            Prefs.setPacketCapture(this, on)
            Snackbar.make(
                b.root,
                if (on) "抓包已开启（悬浮窗「抓包」页签查看）" else "抓包已关闭并清空缓冲",
                Snackbar.LENGTH_SHORT
            ).show()
        }
        b.swAnrWatchdog.setOnCheckedChangeListener { _, on ->
            Prefs.setAnrWatchdog(this, on)
            if (on) AnrWatchdog.start(this) else AnrWatchdog.stop()
            Snackbar.make(
                b.root,
                if (on) "ANR 看门狗已开启" else "ANR 看门狗已关闭",
                Snackbar.LENGTH_SHORT
            ).show()
        }
        b.btnDropDrill.setOnClickListener { StabilityFeatures.showDropDrill(this) }
        b.btnForceCrash.setOnClickListener { confirmForceCrash() }
        // 强制崩溃是内部演练入口：正式发布包隐藏，避免普通用户误触杀进程（v4.2）
        b.btnForceCrash.visibility = if (com.lingyi.mbos.BuildConfig.DEBUG) View.VISIBLE else View.GONE
        b.btnBuildInfo.setOnClickListener { StabilityFeatures.showBuildInfo(this) }
    }

    /** 悬浮窗调试控制台：无悬浮窗权限时先引导授权，授权后自动开启。 */
    private fun openDebugConsoleWithPermission() {
        if (!Settings.canDrawOverlays(this)) {
            pendingOverlayAction = { DebugConsoleService.start(this) }
            Snackbar.make(b.root, R.string.dev_simulator_perm, Snackbar.LENGTH_LONG)
                .setAction(R.string.dev_simulator_overlay_open) {
                    overlayPermLauncher.launch(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
                .show()
            renderDebugConsoleSwitch()
            return
        }
        DebugConsoleService.start(this)
        renderDebugConsoleSwitch()
    }

    /** 同步开关真实态：服务可能被通知栏「停止」关掉。 */
    private fun renderDebugConsoleSwitch() {
        runCatching { b.swDebugConsole.isChecked = DebugConsoleService.isRunning() }
    }

    /** 强制崩溃（演练崩溃恢复链路）：二次确认后延迟抛出未捕获异常。 */
    private fun confirmForceCrash() {
        MaterialAlertDialogBuilder(this)
            .setTitle("强制崩溃")
            .setMessage("将在 1.5 秒后抛出未捕获异常，用于演练「崩溃后自动回引导页 + 崩溃日志 + 一键反馈」。确定继续？")
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton("确定崩溃") { _, _ ->
                AppLog.e("Developer", "强制崩溃（开发者演练）")
                Snackbar.make(b.root, "即将崩溃…", Snackbar.LENGTH_SHORT).show()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    throw RuntimeException("Developer 强制崩溃（开发者演练）")
                }, 1500)
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        ConnectionHub.removeListener(listener)
    }

    override fun onResume() {
        super.onResume()
        // 悬浮窗可能在此期间被关闭（服务销毁），刷新入口状态
        renderSimulatorStatus()
        renderDebugConsoleSwitch()
        b.channelPicker.refresh()
    }

    // ---------- 信息 / 连接状态 ----------
    private fun renderInfo() {
        val v = UpdateChecker.currentVersionName()
        val c = UpdateChecker.currentVersionCode()
        b.tvInfo.text = buildString {
            append("名称：灵逸 MB\n")
            append("软件版本：$v (code $c)\n")
            append("固件版本：MB-OS v${FirmwareScript.VERSION}（板载 MicroPython 版本以 INFO 回读为准）\n")
            append("包名：$packageName\n")
            append("连接：${if (connected) "已连接" else "未连接"} · ${connTypeText()}\n")
            append("开发者模式：${if (Prefs.isDevMode(this@DeveloperActivity)) "已开启" else "未开启"}")
        }
    }

    private fun connTypeText(): String = when (ConnectionHub.type()) {
        "usb" -> "USB 串口"
        "ble" -> "蓝牙 BLE"
        "sim" -> "模拟器（悬浮窗）"
        else -> "-"
    }

    private fun renderConn() {
        b.tvConn.text = "状态：${if (connected) "已连接" else "未连接"}\n方式：${connTypeText()}"
    }

    // ---------- 指令控制台 ----------
    private data class CmdButton(val label: String, val build: () -> String)

    private fun buildCommandButtons() {
        val cmds = listOf(
            CmdButton("SCROLL 滚动文字「你好」") { Protocol.scroll("你好") },
            CmdButton("SHOW 图案「爱心」") { Protocol.show(heart) },
            CmdButton("NUMBER 数字 2026") { Protocol.number(2026) },
            CmdButton("TONE 音调 440Hz/500ms") { Protocol.tone(440, 500) },
            CmdButton("MELODY 旋律「欢乐颂」") { Protocol.melody("ODE") },
            CmdButton("BRIGHT 亮度 5") { Protocol.brightness(5) },
            CmdButton("PLOT 点亮 (2,2)") { Protocol.plot(2, 2, true) },
            CmdButton("COMPASS 指南针") { Protocol.compass() },
            CmdButton("CLEAR 清空屏幕") { Protocol.clear() },
            CmdButton("READ 读取传感器") { Protocol.read() },
            CmdButton("INFO 读取固件信息") { Protocol.info() }
        )
        cmds.forEach { c ->
            val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = c.label
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 6 }
                setOnClickListener { dispatch(c.build()) }
            }
            b.containerCommands.addView(btn)
        }
    }

    private fun sendRaw() {
        val raw = b.etCmd.text.toString().ifBlank { return }
        dispatch(Protocol.custom(raw))
    }

    /** 生成指令：已连接则真实下发，未连接则本地预览。 */
    private fun dispatch(line: String) {
        val pretty = line.trimEnd('\n')
        if (connected && ConnectionHub.send(line)) {
            log("→ $pretty")
            Snackbar.make(b.root, getString(R.string.dev_sent, pretty), Snackbar.LENGTH_SHORT).show()
        } else {
            log("→ (未发送) $pretty")
            Snackbar.make(b.root, getString(R.string.dev_preview, pretty), Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun log(line: String) {
        logLines.addLast(line)
        while (logLines.size > 200) logLines.removeFirst()
        b.tvLog.text = logLines.joinToString("\n")
    }

    // ---------- 协议自检 ----------
    /** 遍历全部指令生成器，校验每条指令格式（非空、以换行结尾）并写入日志。 */
    private fun protocolSelfCheck() {
        val cmds = listOf(
            "SCROLL" to Protocol.scroll("你好"),
            "SHOW" to Protocol.show(heart),
            "PLOT" to Protocol.plot(2, 2, true),
            "TONE" to Protocol.tone(440, 500),
            "NUMBER" to Protocol.number(2026),
            "BRIGHT" to Protocol.brightness(5),
            "MELODY" to Protocol.melody("ODE"),
            "COMPASS" to Protocol.compass(),
            "CLEAR" to Protocol.clear(),
            "READ" to Protocol.read(),
            "INFO" to Protocol.info(),
            "custom" to Protocol.custom("SCROLL:hello")
        )
        var ok = 0
        cmds.forEach { (name, line) ->
            val valid = line.length > 1 && line.endsWith("\n") && line != "\n"
            if (valid) ok++
            log("[自检] $name → ${line.trimEnd('\n')} ${if (valid) "✓" else "✗"}")
        }
        log("[自检] 完成：$ok / ${cmds.size} 条指令格式正确")
        Snackbar.make(b.root, getString(R.string.dev_selfcheck_done, ok, cmds.size), Snackbar.LENGTH_SHORT).show()
    }

    // ---------- 模拟连接状态 ----------
    /** 向全 App 广播模拟的连接状态，验证各页面 UI 响应（不影响真实连接）。 */
    private fun simulateState(on: Boolean) {
        ConnectionHub.debugEmitState(on, if (on) "模拟已连接" else "模拟已断开")
        connected = on
        renderConn()
        renderInfo()
        log("[模拟] 连接状态 → ${if (on) "已连接" else "未连接"}")
        Snackbar.make(b.root, getString(R.string.dev_sim_done, if (on) "已连接" else "未连接"), Snackbar.LENGTH_SHORT).show()
    }

    // ---------- micro:bit V2 模拟器（悬浮窗） ----------

    /** 打开 / 关闭悬浮窗：无悬浮窗权限时先引导用户授权。 */
    private fun toggleSimulatorOverlay() {
        if (SimulatorService.isRunning) {
            stopService(Intent(this, SimulatorService::class.java))
            renderSimulatorStatus()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Snackbar.make(b.root, R.string.dev_simulator_perm, Snackbar.LENGTH_LONG)
                .setAction(R.string.dev_simulator_overlay_open) {
                    overlayPermLauncher.launch(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
                .show()
            return
        }
        startService(Intent(this, SimulatorService::class.java))
        renderSimulatorStatus()
    }

    /** 连接模拟器：与真机同链路，各页面无感知；真机接入时自动让位。 */
    private fun connectSimulator() {
        if (ConnectionHub.isConnected()) {
            Snackbar.make(b.root, R.string.dev_simulator_yield, Snackbar.LENGTH_SHORT).show()
            return
        }
        ConnectionHub.connectSimulator(this, listener)
        renderSimulatorStatus()
    }

    /** 刷新模拟器入口状态（悬浮窗运行态 + 模拟器连接态）。 */
    private fun renderSimulatorStatus() {
        val simState = when {
            ConnectionHub.current is SimulatorConnection && ConnectionHub.isConnected() ->
                getString(R.string.dev_simulator_connected)
            ConnectionHub.isConnected() -> getString(R.string.dev_simulator_yield)
            else -> getString(R.string.dev_simulator_idle)
        }
        val overlayState = if (SimulatorService.isRunning) {
            getString(R.string.dev_simulator_on)
        } else {
            getString(R.string.dev_simulator_off)
        }
        b.tvSimulatorStatus.text = getString(R.string.dev_simulator_status, overlayState, simState)
        b.btnSimOverlay.text = getString(
            if (SimulatorService.isRunning) R.string.dev_simulator_overlay_close
            else R.string.dev_simulator_overlay_open
        )
    }

    // ---------- 传感器数据模拟 ----------
    private fun injectMock() {
        val line = b.etMock.text.toString().trim()
        if (line.isEmpty()) return
        ConnectionHub.debugEmitData(line)
        Snackbar.make(b.root, getString(R.string.dev_mock_injected, line), Snackbar.LENGTH_SHORT).show()
    }

    // ---------- 设备诊断 ----------
    private fun listUsb() {
        val text = runCatching {
            val m = getSystemService(UsbManager::class.java)
            val devs = m?.deviceList?.values ?: emptyList<UsbDevice>()
            if (devs.isEmpty()) "未检测到 USB 设备（请用 OTG 线连接 micro:bit）"
            else devs.joinToString("\n") { d ->
                val mb = if (d.vendorId == 0x0D28) " ← micro:bit" else ""
                "• ${d.deviceName}\n  VID=0x${Integer.toHexString(d.vendorId).uppercase()} " +
                    "PID=0x${Integer.toHexString(d.productId).uppercase()}$mb"
            }
        }.getOrElse { "读取失败：${it.message}" }
        dialog("USB 设备", text)
    }

    private fun listBle() {
        val text = runCatching {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            val devs = adapter?.bondedDevices?.toList() ?: emptyList()
            if (devs.isEmpty()) "系统蓝牙里没有已配对设备"
            else devs.joinToString("\n") { d ->
                val name = runCatching { d.name }.getOrNull() ?: "(无名称)"
                "• $name\n  ${d.address}"
            }
        }.getOrElse { "读取失败：${it.message}" }
        dialog("已配对蓝牙设备", text)
    }

    // ---------- 内容查看 ----------
    private fun buildGameButtons() {
        MicrobitGames.presets.forEach { g ->
            val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = getString(R.string.dev_view_game, g.name)
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 6 }
                setOnClickListener { showCode(g.name, g.code) }
            }
            b.containerGames.addView(btn)
        }
    }

    private fun showCode(title: String, code: String) {
        val tv = android.widget.TextView(this).apply {
            text = code
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(24, 16, 24, 16)
        }
        val sv = android.widget.ScrollView(this).apply { addView(tv) }
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(sv)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.dev_copy_code) { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText(title, code))
                Snackbar.make(b.root, R.string.dev_code_copied, Snackbar.LENGTH_SHORT).show()
            }
            .show()
    }

    // ---------- 更新检查调试 ----------
    private fun runUpdateCheck() {
        if (updateChecking) return
        updateChecking = true
        b.btnDebugUpdate.isEnabled = false
        b.tvUpdateResult.text = getString(R.string.dev_update_running)
        com.lingyi.mbos.ui.Transitions.crossFade(b.pbDebugUpdate, true)
        // 预览版通道下检查 beta 清单，稳定版通道下检查正式清单
        UpdateChecker.check(Prefs.getChannel(this)) { info ->
            if (isDestroyed) return@check
            updateChecking = false
            b.btnDebugUpdate.isEnabled = true
            com.lingyi.mbos.ui.Transitions.crossFade(b.pbDebugUpdate, false)
            b.tvUpdateResult.text = if (info.error != null) {
                "错误：${info.error}"
            } else {
                buildString {
                    append("通道：${if (info.channel == UpdateChecker.CHANNEL_PREVIEW) "预览版（beta）" else "稳定版"}\n")
                    append("本地：v${UpdateChecker.currentVersionName()} (code ${UpdateChecker.currentVersionCode()})\n")
                    append("远端：v${info.remoteVersionName} (code ${info.remoteVersionCode})\n")
                    append("有更新：${if (info.available) "是" else "否"}\n")
                    append("发布时间：${info.publishedAt}\n")
                    append("下载页：${info.pageUrl}\n")
                    append("说明：${info.notes}")
                }
            }
        }
    }

    // ---------- 其它 ----------

    /** 查看最近一次未捕获异常（进程崩溃）日志，用于定位「闪退/跳回引导页」类问题。 */
    private fun showCrashLog() {
        val crash = CrashLog.readLastCrash(this)
        if (crash.isNullOrBlank()) {
            Snackbar.make(b.root, R.string.dev_crash_none, Snackbar.LENGTH_LONG).show()
            return
        }
        val tv = android.widget.TextView(this).apply {
            text = crash
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(24, 16, 24, 16)
        }
        val sv = android.widget.ScrollView(this).apply { addView(tv) }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dev_crash_title)
            .setView(sv)
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.dev_crash_copy) { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("灵逸 MB 崩溃日志", crash))
                Snackbar.make(b.root, R.string.dev_crash_copied, Snackbar.LENGTH_SHORT).show()
            }
            .setPositiveButton("发送日志") { _, _ ->
                CrashLog.markSeen(this)
                LogMailer.sendByEmail(this)
            }
            .show()
    }

    private fun copyLog() {
        val text = if (logLines.isEmpty()) getString(R.string.dev_log_empty) else logLines.joinToString("\n")
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("灵逸 MB 调试日志", text))
        Snackbar.make(b.root, R.string.dev_log_copied, Snackbar.LENGTH_SHORT).show()
    }

    /** 列出 assets/firmware 下内置的固件文件及大小，排查刷机资源缺失。 */
    private fun listAssets() {
        val text = runCatching {
            val files = assets.list("firmware") ?: emptyArray()
            if (files.isEmpty()) "assets/firmware 为空"
            else files.sorted().joinToString("\n") { name ->
                val kb = runCatching {
                    assets.open("firmware/$name").use { it.available() } / 1024
                }.getOrDefault(0)
                "• $name（约 $kb KB）"
            }
        }.getOrElse { "读取失败：${it.message}" }
        dialog("内置固件资源", text)
    }

    private fun copyDiag() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("灵逸 MB 诊断", b.tvInfo.text.toString() + "\n\n" + b.tvConn.text))
        Snackbar.make(b.root, R.string.dev_diag_copied, Snackbar.LENGTH_SHORT).show()
    }

    private fun dialog(title: String, msg: String) {
        MaterialAlertDialogBuilder(this).setTitle(title).setMessage(msg)
            .setPositiveButton(android.R.string.ok, null).show()
    }
}
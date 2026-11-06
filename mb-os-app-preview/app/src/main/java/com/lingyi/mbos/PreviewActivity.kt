/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.R as MR
import com.lingyi.mbos.util.themeColor
import com.lingyi.mbos.util.UpdateChecker
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.lingyi.mbos.ble.ConnState
import com.lingyi.mbos.ble.ConnectionHub
import com.lingyi.mbos.ble.MicrobitConnection
import com.lingyi.mbos.databinding.ActivityPreviewBinding
import com.lingyi.mbos.databinding.ItemInfoRowBinding
import com.lingyi.mbos.ui.Gestures
import com.lingyi.mbos.ui.CsvLogger
import com.lingyi.mbos.ui.M3Kit
import com.lingyi.mbos.ui.Protocol
import com.lingyi.mbos.ui.SensorSnapshot
import com.lingyi.mbos.ui.StabilityFeatures
import com.lingyi.mbos.util.Prefs
import com.lingyi.mbos.util.ThemeManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 预览模式：把 micro:bit V2 的全部信息「拎」出来，实时读取并展示。
 * 大图卡展示连接状态与连接方式；信息卡只放开发板数据（固件 / 温度 / 光线 /
 * 加速度 / 指南针 / 手势 / 按键），避免与大图卡重复。
 * 开启「实时读取」后每秒自动轮询开发板，也可点右上角手动刷新。
 */
class PreviewActivity : BaseActivity() {

    private lateinit var b: ActivityPreviewBinding
    private val handler = Handler(Looper.getMainLooper())
    private var realtimeOn = true

    /** 最近一次发出读数请求的时间；收到响应后清零，用于「无响应」提示。 */
    private var lastReqAt = 0L

    private val info = linkedMapOf(
        "固件版本" to "-",
        "软件版本" to UpdateChecker.currentVersionName(),
        "板载温度" to "-",
        "环境光线" to "-",
        "加速度 X" to "-",
        "加速度 Y" to "-",
        "加速度 Z" to "-",
        "指南针朝向" to "-",
        "最近手势" to "-",
        "按键 A" to "-",
        "按键 B" to "-",
    )

    /** 发出请求后超过该时长仍无任何响应的提示；随轮询一起取消，避免页面暂停后误报。 */
    private val noResponseCheck = Runnable {
        val threshold = ThemeManager.readIntervalMs(this@PreviewActivity) + 1600
        if (realtimeOn && ConnectionHub.isConnected() && lastReqAt != 0L &&
            System.currentTimeMillis() - lastReqAt >= threshold
        ) {
            b.tvHint.text = getString(R.string.preview_no_response)
            // 本次请求已超时：收起「读取中」指示，等下一轮轮询再亮起
            setRefreshing(false)
        }
    }

    private val listener = object : MicrobitConnection.Listener {
        override fun onState(state: ConnState) {
            runOnUiThread { onConnState(state) }
        }

        override fun onData(data: String) {
            runOnUiThread { handleData(data) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(b.root)

        // M3 Expressive：整页可点击元素统一按压反馈（4.1.1-preview）
        M3Kit.applyPressFeedback(b.root)

        b.toolbar.setNavigationOnClickListener { finish() }
        b.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_refresh) {
                refresh()
                true
            } else false
        }

        b.swRealtime.setOnCheckedChangeListener { _, isChecked ->
            realtimeOn = isChecked
            if (isChecked) startPolling() else stopPolling()
        }

        // ===== 稳定性工具（4.1.0-preview）：快照对比 / 读数 CSV 导出 =====
        b.btnSnapshotCompare.setOnClickListener { compareSnapshot() }
        b.btnExportCsv.setOnClickListener { exportCsv() }

        ConnectionHub.addListener(listener)
        onConnState(ConnState(ConnectionHub.isConnected(), "已连接"))
        render()
    }

    override fun onResume() {
        super.onResume()
        if (realtimeOn && ConnectionHub.isConnected()) startPolling()
    }

    override fun onPause() {
        super.onPause()
        stopPolling()
        // 页面不可见：收起「读取中」指示，避免后台空转
        setRefreshing(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPolling()
        ConnectionHub.removeListener(listener)
    }

    // ---------- 实时轮询 ----------
    private val pollTask = object : Runnable {
        override fun run() {
            if (realtimeOn && ConnectionHub.isConnected()) {
                b.tvHint.text = getString(R.string.preview_hint)
                lastReqAt = System.currentTimeMillis()
                setRefreshing(true)
                ConnectionHub.send(Protocol.info())
                ConnectionHub.send(Protocol.read())
                // 间隔 + 1.6 秒仍无响应：提示可能是开发板在跑自定义脚本/游戏，或未刷 MB-OS 固件
                val interval = ThemeManager.readIntervalMs(this@PreviewActivity)
                handler.postDelayed(noResponseCheck, interval + 1600)
                handler.postDelayed(this, interval)
            }
        }
    }

    private fun startPolling() {
        stopPolling()
        handler.post(pollTask)
    }

    private fun stopPolling() {
        handler.removeCallbacks(pollTask)
        handler.removeCallbacks(noResponseCheck)
    }

    private fun refresh() {
        // 先进入“读取中”状态，给用户明确反馈，避免误以为没反应
        info["板载温度"] = "读取中…"
        info["环境光线"] = "读取中…"
        info["加速度 X"] = "读取中…"
        info["加速度 Y"] = "读取中…"
        info["加速度 Z"] = "读取中…"
        info["指南针朝向"] = "读取中…"
        info["最近手势"] = "读取中…"
        info["按键 A"] = "读取中…"
        info["按键 B"] = "读取中…"
        render()
        if (ConnectionHub.isConnected()) {
            lastReqAt = System.currentTimeMillis()
            setRefreshing(true)
            ConnectionHub.send(Protocol.info())
            ConnectionHub.send(Protocol.read())
        }
    }

    /** 读数进行中指示（状态行旁的 M3 不定圆环）：有请求在途即显示。 */
    private fun setRefreshing(on: Boolean) {
        com.lingyi.mbos.ui.Transitions.crossFade(b.pbRefresh, on)
    }

    private fun onConnState(state: ConnState) {
        val connected = state.connected
        val mode = when (ConnectionHub.current?.type) {
            "usb" -> "USB 串口"
            "ble" -> "蓝牙 BLE"
            else -> null
        }
        // 连接状态与连接方式由大图卡下方文字承担，信息卡只展示开发板数据
        b.tvPreviewState.text = when {
            connected && mode != null -> "micro:bit V2 · $mode · 已连接"
            connected -> "micro:bit V2 · 已连接"
            else -> "micro:bit V2 · 未连接"
        }
        b.tvPreviewState.setTextColor(
            themeColor(if (connected) MR.attr.colorPrimary else MR.attr.colorOnSurfaceVariant)
        )
        // 连接就绪后（重新）请求一次全量读数 + 开启实时轮询
        if (connected) {
            refresh()
            if (realtimeOn) startPolling()
        } else {
            stopPolling()
            // 断开后把所有读数复位，避免残留「读取中…」或上一次的旧数据
            info.keys.forEach { info[it] = "-" }
            // 软件版本是本机常量，断联也不清空
            info["软件版本"] = UpdateChecker.currentVersionName()
            lastReqAt = 0L
            setRefreshing(false)
            b.tvHint.text = getString(R.string.preview_hint)
            render()
        }
    }

    private fun handleData(data: String) {
        if (data.startsWith("INFO:")) {
            // 固件版本行：解析板载真实固件版本（MB-OS vX + MicroPython 版本）。
            // 旧固件不带 MB-OS 字段时明确提示未安装 MB-OS，而不是拿 App 版本冒充板载版本
            val payload = data.removePrefix("INFO:")
            val parts = payload.split("|").map { it.trim() }
            val mbos = parts.firstOrNull { it.startsWith("MB-OS v") }?.removePrefix("MB-OS v")
            val mp = parts.firstOrNull { it.startsWith("MicroPython") }?.removePrefix("MicroPython")?.trim()
            info["固件版本"] = if (!mbos.isNullOrBlank()) {
                buildString {
                    append("MB-OS 固件 v$mbos")
                    if (!mp.isNullOrBlank()) append("（MicroPython $mp）")
                }
            } else if (!mp.isNullOrBlank()) {
                "MicroPython $mp（未安装 MB-OS 固件）"
            } else {
                "未安装 MB-OS 固件"
            }
            lastReqAt = 0L
            setRefreshing(false)
            b.tvHint.text = getString(R.string.preview_hint)
            render()
            return
        }
        if (data.contains("A:") && data.contains("T:")) {
            val f = data.split('|').mapNotNull {
                val p = it.split(':', limit = 2)
                if (p.size == 2) p[0] to p[1] else null
            }.toMap()
            info["板载温度"] = "${f["T"] ?: "-"} °C"
            info["环境光线"] = f["L"] ?: "-"
            info["加速度 X"] = f["X"] ?: "-"
            info["加速度 Y"] = f["Y"] ?: "-"
            info["加速度 Z"] = f["Z"] ?: "-"
            info["指南针朝向"] = "${f["C"] ?: "-"}°"
            info["最近手势"] = Gestures.label(f["G"])
            info["按键 A"] = if (f["A"] == "1") "按下" else "松开"
            info["按键 B"] = if (f["B"] == "1") "按下" else "松开"
            recordSample(
                SensorSnapshot(
                    a = f["A"] == "1",
                    b = f["B"] == "1",
                    t = f["T"]?.toDoubleOrNull(),
                    l = f["L"]?.toIntOrNull(),
                    x = f["X"]?.toIntOrNull(),
                    y = f["Y"]?.toIntOrNull(),
                    z = f["Z"]?.toIntOrNull(),
                    c = f["C"]?.toIntOrNull(),
                    g = f["G"]
                )
            )
            lastReqAt = 0L
            setRefreshing(false)
            b.tvHint.text = getString(R.string.preview_hint)
            render()
        }
    }

    private fun render() {
        val list = b.infoList
        list.removeAllViews()
        val dividerH = resources.displayMetrics.density.toInt().coerceAtLeast(1)
        val entries = info.entries.toList()
        entries.forEachIndexed { index, e ->
            val row = ItemInfoRowBinding.inflate(layoutInflater, list, false)
            row.tvLabel.text = e.key
            row.tvValue.text = e.value
            list.addView(row.root)
            if (index < entries.size - 1) {
                val dv = View(this)
                dv.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dividerH)
                dv.setBackgroundColor(themeColor(MR.attr.colorOutlineVariant))
                list.addView(dv)
            }
        }
    }

    // ---------- 稳定性工具：快照对比 / CSV 导出 ----------

    /** 本页采样序列（tMs 相对记录起点），供 CSV 导出回溯问题。 */
    private val samples = mutableListOf<Pair<Long, SensorSnapshot>>()
    private var sampleBaseAt = 0L

    /** 把当前读数整理成「键 = 值」多行文本（SnapshotDiff 的输入格式）。 */
    private fun snapshotText(): String = info.entries.joinToString("\n") { "${it.key} = ${it.value}" }

    /**
     * 快照对比：与上一次快照逐项 diff（新增 / 消失 / 变化），
     * 用于「刚才还好好的，现在哪个读数不对了」类问题定位。
     */
    private fun compareSnapshot() {
        val now = snapshotText()
        val last = Prefs.getPreviewLastSnapshot(this)
        Prefs.setPreviewLastSnapshot(this, now)
        val diff = if (last.isNullOrBlank()) {
            "（这是本机第一次快照，已留存；再次点击即可与本次对比）\n\n$now"
        } else {
            val d = StabilityFeatures.SnapshotDiff.diff(last, now)
            if (d.isBlank()) "两次快照完全一致，没有读数发生变化。\n\n$now" else d
        }
        val tv = android.widget.TextView(this).apply {
            text = diff
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(24, 16, 24, 16)
        }
        val sv = android.widget.ScrollView(this).apply { addView(tv) }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("快照对比")
            .setView(sv)
            .setNegativeButton("关闭", null)
            .setPositiveButton("复制") { _, _ -> StabilityFeatures.copyText(this, diff) }
            .show()
    }

    /** 导出本页采样为 CSV（Excel 可直接打开），写外部私有目录并走系统分享。 */
    private fun exportCsv() {
        if (samples.isEmpty()) {
            android.widget.Toast.makeText(this, "暂无采样（保持实时读取一会儿再来）", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val dir = File(getExternalFilesDir(null), "exports").apply { mkdirs() }
        val f = File(dir, CsvLogger.fileName(stamp))
        val ok = runCatching { f.writeText(CsvLogger.toCsv(samples)) }.isSuccess
        if (ok) {
            android.widget.Toast.makeText(this, "已导出 ${samples.size} 条采样", android.widget.Toast.LENGTH_SHORT).show()
            StabilityFeatures.shareFile(this, f)
        } else {
            android.widget.Toast.makeText(this, "导出失败（存储不可写？）", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /** 记录一条采样（上限 600 条，环形丢弃最旧，防内存膨胀）。 */
    private fun recordSample(s: SensorSnapshot) {
        if (sampleBaseAt == 0L) sampleBaseAt = System.currentTimeMillis()
        samples.add(System.currentTimeMillis() - sampleBaseAt to s)
        while (samples.size > 600) samples.removeAt(0)
    }

}
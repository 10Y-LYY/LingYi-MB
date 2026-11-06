/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

import android.app.Activity
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.google.android.material.R as MR
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lingyi.mbos.BuildConfig
import com.lingyi.mbos.DebugConsoleService
import com.lingyi.mbos.R
import com.lingyi.mbos.ble.BleConnection
import com.lingyi.mbos.ble.ConnectionHub
import com.lingyi.mbos.util.AppLog
import com.lingyi.mbos.util.CrashLog
import com.lingyi.mbos.util.DebugReport
import com.lingyi.mbos.util.LogPackager
import com.lingyi.mbos.util.Prefs
import com.lingyi.mbos.util.themeColor

/**
 * 4.1.0-preview 的 30 个「帮助用户稳定性」功能共享实现。
 *
 * 落点（每页均摊）：
 *  · 引导页 5：启动自检 / 连接向导步骤条 / 常见问题速查 / 固件新旧对比 / 无网可用提示
 *  · 设置页 6：开机自检开关 / 电池优化白名单 / 通知渠道修复 / 存储可视化清理 / 崩溃恢复保护 / 时钟校准提醒
 *  · 关于页 5：一键诊断报告 / 反馈模板 / 更新重试与历史 / 更新通道说明 / 依赖与权限清单
 *  · 开发者页 6：调试控制台入口 / 协议抓包开关 / 断线演练 / 强制崩溃 / ANR 看门狗 / 构建信息
 *  · 预览页 2：刷新频率可调 / 快照对比
 *  · 刷入页 3：前置检查 / 失败重试 / 刷前信息留存
 *  · 指针页 2：掉线自动恢复 / 防误触锁
 *  · 连接层 1：指数退避自动重连（见 util/AutoReconnect.kt）
 *
 * 纯逻辑部分（[StartupSelfCheck.evaluate]、[ClockDrift]、[SnapshotDiff]）不依赖
 * Android 框架，单测直接覆盖。
 */
object StabilityFeatures {

    // ================= 启动自检（引导 1 / 设置 1） =================

    enum class Fix { BLUETOOTH_ON, PERMISSIONS, BATTERY, NOTIFICATIONS, NONE }

    data class CheckItem(
        val name: String,
        val ok: Boolean,
        val hint: String,
        val fix: Fix = Fix.NONE
    )

    /**
     * 逐项体检（蓝牙开关 / 蓝牙权限 / OTG / 存储可写 / 通知渠道 / 电池优化）。
     * 任一项不过都带「去修复」入口；时钟准确性单独走 [ClockDrift] 联网探测。
     */
    fun selfCheckItems(ctx: Context): List<CheckItem> {
        val items = mutableListOf<CheckItem>()

        // 1. 蓝牙开关（BLE 连接前提）
        val adapter = runCatching {
            (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter
        }.getOrNull()
        val btOn = adapter?.isEnabled == true
        items.add(
            CheckItem(
                "蓝牙已开启", btOn,
                if (btOn) "蓝牙适配器正常" else "BLE 连接需要蓝牙开启",
                if (btOn) Fix.NONE else Fix.BLUETOOTH_ON
            )
        )

        // 2. 蓝牙权限（Android 12+ 拆分）
        val perms = if (Build.VERSION.SDK_INT >= 31) {
            listOf(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(android.Manifest.permission.BLUETOOTH, android.Manifest.permission.BLUETOOTH_ADMIN)
        }
        val permOk = perms.all {
            ContextCompat.checkSelfPermission(ctx, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        items.add(
            CheckItem(
                "蓝牙权限", permOk,
                if (permOk) "扫描 / 连接权限已授予" else "缺少蓝牙权限，连接时会被系统拒绝",
                if (permOk) Fix.NONE else Fix.PERMISSIONS
            )
        )

        // 3. USB OTG 能力（有线连接前提）
        val otg = runCatching {
            ctx.packageManager.hasSystemFeature("android.hardware.usb.host")
        }.getOrDefault(false)
        items.add(
            CheckItem(
                "USB OTG 支持", otg,
                if (otg) "本机支持 OTG 直连" else "本机不支持 OTG，只能用蓝牙连接",
                Fix.NONE
            )
        )

        // 4. 存储可写（日志 / 固件导出 / 报告都依赖）
        val writable = runCatching {
            val f = java.io.File(ctx.filesDir, ".wtest")
            f.writeText("ok")
            f.delete()
            true
        }.getOrDefault(false)
        items.add(
            CheckItem(
                "存储可写", writable,
                if (writable) "应用存储正常" else "存储不可写：日志与报告无法保存",
                Fix.NONE
            )
        )

        // 5. 通知渠道（前台服务常驻依赖）
        val notifOk = runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) true
            else {
                val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val ids = listOf("pointer_channel", "debug_console_channel")
                ids.all { id ->
                    val ch = mgr.getNotificationChannel(id)
                    ch == null || ch.importance != NotificationManager.IMPORTANCE_NONE
                }
            }
        }.getOrDefault(true)
        items.add(
            CheckItem(
                "通知渠道", notifOk,
                if (notifOk) "常驻通知可正常展示" else "通知渠道被关：悬浮窗服务可能被系统静默杀掉",
                if (notifOk) Fix.NONE else Fix.NOTIFICATIONS
            )
        )

        // 6. 电池优化白名单（后台保活）
        val batteryOk = runCatching {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(ctx.packageName)
        }.getOrDefault(true)
        items.add(
            CheckItem(
                "电池优化白名单", batteryOk,
                if (batteryOk) "已允许后台运行" else "未加白：系统可能限制后台连接",
                if (batteryOk) Fix.NONE else Fix.BATTERY
            )
        )
        return items
    }

    /** 自检结果摘要（纯函数，便于单测与日志）。 */
    fun summarize(items: List<CheckItem>): String {
        val ok = items.count { it.ok }
        val bad = items.filterNot { it.ok }
        return if (bad.isEmpty()) "$ok/${items.size} 项全部通过"
        else "$ok/${items.size} 项通过，待处理：${bad.joinToString("、") { it.name }}"
    }

    /** 弹出自检对话框（不过项带修复按钮）。 */
    fun showSelfCheckDialog(activity: Activity, title: String = "启动自检") {
        val items = selfCheckItems(activity)
        val col = M3Kit.dialogColumn(activity)
        items.forEach { item ->
            col.addView(M3Kit.infoRow(activity, item.name, if (item.ok) "✓ 正常" else "✗ ${item.hint}").apply {
                setOnClickListener {
                    if (!item.ok && item.fix != Fix.NONE) applyFix(activity, item.fix)
                }
            })
            col.addView(M3Kit.divider(activity))
        }
        col.addView(M3Kit.cardDesc(activity, summarize(items)))
        M3Kit.styleButtons(col)
        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setView(android.widget.ScrollView(activity).apply { addView(col) })
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton("重新体检") { _, _ -> showSelfCheckDialog(activity, title) }
            .show()
        AppLog.event("启动自检", summarize(items))
    }

    /** 跳转对应修复入口。 */
    fun applyFix(activity: Activity, fix: Fix) {
        runCatching {
            when (fix) {
                Fix.BLUETOOTH_ON -> activity.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                Fix.PERMISSIONS -> activity.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", activity.packageName, null)
                    )
                )
                Fix.BATTERY -> {
                    // 先试系统直接授权弹窗（部分 ROM 不支持），失败回落白名单列表
                    runCatching {
                        activity.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${activity.packageName}")
                            )
                        )
                    }.onFailure {
                        activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                }
                Fix.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    activity.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                    )
                } else {
                    Unit
                }
                Fix.NONE -> Unit
            }
        }.onFailure { AppLog.w("Stability", "修复跳转失败：$fix", it) }
    }

    // ================= 时钟校准（设置 6） =================

    /** 时钟偏差分级（纯函数）：>阈值视为需要校准。 */
    object ClockDrift {
        const val THRESHOLD_SECONDS = 120

        fun driftSeconds(deviceMs: Long, serverMs: Long): Long =
            (deviceMs - serverMs) / 1000L

        fun needsCalibration(deviceMs: Long, serverMs: Long): Boolean =
            kotlin.math.abs(driftSeconds(deviceMs, serverMs)) >= THRESHOLD_SECONDS

        fun describe(driftSec: Long): String = when {
            driftSec > 0 -> "设备时钟快 ${driftSec / 60} 分 ${driftSec % 60} 秒"
            driftSec < 0 -> "设备时钟慢 ${-driftSec / 60} 分 ${-driftSec % 60} 秒"
            else -> "时钟准确"
        }
    }

    // ================= 快照对比（预览 2） =================

    /**
     * 读数快照 diff（纯函数）：逐行比较两份「键 = 值」文本，
     * 输出 新增 / 消失 / 变化 三类摘要。预览页「快照对比」用。
     */
    object SnapshotDiff {
        fun parse(text: String): Map<String, String> =
            text.lineSequence()
                .map { it.trim() }
                .filter { it.contains("=") }
                .associate { line ->
                    val i = line.indexOf('=')
                    line.substring(0, i).trim() to line.substring(i + 1).trim()
                }

        /** 返回人类可读的差异摘要；无差异返回空串。 */
        fun diff(oldText: String, newText: String): String {
            val a = parse(oldText)
            val b = parse(newText)
            val sb = StringBuilder()
            a.forEach { (k, v) ->
                val nv = b[k]
                when {
                    nv == null -> sb.append("− $k 已消失（原 $v）\n")
                    nv != v -> sb.append("~ $k：$v → $nv\n")
                }
            }
            b.forEach { (k, v) ->
                if (!a.containsKey(k)) sb.append("+ $k 新增（$v）\n")
            }
            return sb.toString().trim()
        }
    }

    // ================= 网络可用性 =================

    fun isNetworkAvailable(ctx: Context): Boolean = runCatching {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(false)

    // ================= 引导页：常见问题速查 =================

    data class Faq(val q: String, val a: String, val fixLabel: String? = null, val fix: (() -> Unit)? = null)

    /** 高频问题 + 一键去修复。 */
    fun faqItems(activity: Activity): List<Faq> = listOf(
        Faq(
            "连不上 micro:bit？",
            "依次检查：① 系统蓝牙里已配对 micro:bit；② micro:bit 已开机且没被别的 App 占用；" +
                "③ 安卓 12+ 需授予「附近的设备」权限。USB 连接则换根数据线（非充电线）。",
            "打开蓝牙设置"
        ) { runCatching { activity.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) } },
        Faq(
            "提示「板载未安装 MB-OS 固件」？",
            "开发板跑的是裸 MicroPython，需要刷入内置的合并固件：引导页点「一键刷入固件」。",
            "去刷机"
        ) { runCatching { activity.startActivity(Intent(activity, com.lingyi.mbos.CustomFlashActivity::class.java)) } },
        Faq(
            "悬浮窗 / 指针突然不动了？",
            "系统可能在后台限制了本应用。把本应用加入电池优化白名单，并确认通知渠道未被关闭。",
            "去设置白名单"
        ) { applyFix(activity, Fix.BATTERY) },
        Faq(
            "应用卡死或闪退？",
            "打开调试控制台悬浮窗 →「上报」页签，描述现象后一键生成诊断报告发我们；" +
                "日志里已自动记录崩溃前现场。",
            "打开调试控制台"
        ) { openDebugConsole(activity) },
        Faq(
            "检查更新转圈 / 失败？",
            "更新检查需要网络；弱网下可稍后重试，或直接打开发布页手动下载。控制 / 预览 / 指针" +
                "等核心功能离线也能用。",
            null
        ) { }
    )

    fun showFaqDialog(activity: Activity) {
        val faqs = faqItems(activity)
        val col = M3Kit.dialogColumn(activity)
        faqs.forEach { faq ->
            col.addView(M3Kit.cardTitle(activity, "Q：${faq.q}"))
            col.addView(M3Kit.cardDesc(activity, "A：${faq.a}"))
            faq.fixLabel?.let { label ->
                col.addView(M3Kit.inlineTextButton(activity, label) { faq.fix?.invoke() })
            }
            col.addView(M3Kit.divider(activity))
        }
        M3Kit.styleButtons(col)
        MaterialAlertDialogBuilder(activity)
            .setTitle("常见问题速查")
            .setView(android.widget.ScrollView(activity).apply { addView(col) })
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ================= 引导页：固件新旧对比 =================

    fun showFirmwareCompareDialog(activity: Activity, boardMbos: String?, boardMp: String?) {
        val builtIn = FirmwareScript.VERSION
        val board = boardMbos?.takeIf { it.isNotBlank() } ?: "未安装"
        val col = M3Kit.dialogColumn(activity)
        col.addView(M3Kit.infoRow(activity, "板载固件", board))
        col.addView(M3Kit.divider(activity))
        col.addView(M3Kit.infoRow(activity, "App 内置固件", "MB-OS v$builtIn"))
        col.addView(M3Kit.divider(activity))
        if (boardMp != null) {
            col.addView(M3Kit.infoRow(activity, "板载 MicroPython", boardMp))
            col.addView(M3Kit.divider(activity))
        }
        val newer = board == "未安装" ||
            com.lingyi.mbos.ui.Protocol.isOlder(board, builtIn)
        col.addView(
            M3Kit.cardDesc(
                activity,
                if (newer) {
                    "板载固件旧于 App 内置版本（或未安装）：新固件通常含稳定性修复与新功能，" +
                        "建议重新刷入。刷机不影响你保存在板外的数据。"
                } else {
                    "板载固件已是最新：无需重新刷入。"
                }
            )
        )
        M3Kit.styleButtons(col)
        MaterialAlertDialogBuilder(activity)
            .setTitle("固件版本对比")
            .setView(android.widget.ScrollView(activity).apply { addView(col) })
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton("去刷机") { _, _ ->
                runCatching {
                    activity.startActivity(Intent(activity, com.lingyi.mbos.CustomFlashActivity::class.java))
                }
            }
            .show()
    }

    // ================= 设置页：存储可视化清理 =================

    fun showStorageCleanupDialog(activity: Activity) {
        val files = AppLog.files()
        val crashes = CrashLog.pendingCrashes(activity).size
        val logKb = AppLog.totalSize() / 1024
        val col = M3Kit.dialogColumn(activity)
        col.addView(M3Kit.infoRow(activity, "日志文件", "${files.size} 个 · ${logKb} KB"))
        col.addView(M3Kit.divider(activity))
        col.addView(M3Kit.infoRow(activity, "待处理崩溃", "$crashes 条"))
        col.addView(M3Kit.divider(activity))
        files.take(8).forEach { f ->
            col.addView(M3Kit.infoRow(activity, f.name, "${f.length() / 1024} KB"))
        }
        M3Kit.styleButtons(col)
        M3Kit.styleButtons(col)
        MaterialAlertDialogBuilder(activity)
            .setTitle("存储占用与清理")
            .setView(android.widget.ScrollView(activity).apply { addView(col) })
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton("清理日志") { _, _ ->
                AppLog.clear()
                android.widget.Toast.makeText(activity, "日志已清理", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("清理崩溃记录") { _, _ ->
                CrashLog.clear()
                android.widget.Toast.makeText(activity, "崩溃记录已清理", android.widget.Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    // ================= 设置页：通知渠道修复 =================

    fun showNotificationChannelFix(activity: Activity) {
        val mgr = activity.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val bad = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mgr != null) {
            listOf("pointer_channel", "debug_console_channel").mapNotNull { id ->
                val ch = mgr.getNotificationChannel(id)
                if (ch != null && ch.importance == NotificationManager.IMPORTANCE_NONE) id else null
            }
        } else emptyList()
        val msg = if (bad.isEmpty()) {
            "通知渠道全部正常：前台服务（指针 / 调试控制台）可正常常驻。"
        } else {
            "以下通知渠道被关闭：${bad.joinToString()}。关闭后系统可能静默杀掉常驻服务，" +
                "导致悬浮窗消失、连接中断。点「去恢复」重新打开。"
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle("通知渠道体检")
            .setMessage(msg)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton("去恢复") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    runCatching {
                        activity.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                        )
                    }
                }
            }
            .show()
    }

    // ================= 设置页：崩溃恢复保护说明 =================

    fun showCrashGuardInfo(activity: Activity) {
        val crashes = CrashLog.pendingCrashes(activity).size
        MaterialAlertDialogBuilder(activity)
            .setTitle("崩溃恢复保护")
            .setMessage(
                "开启后：每次启动检测「待处理崩溃」并提醒反馈；连续崩溃时引导页会引导你" +
                    "先处理崩溃再进控制台，避免一进去就闪退的循环。\n\n当前待处理崩溃：$crashes 条。"
            )
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton("查看崩溃") { _, _ ->
                runCatching {
                    val crash = CrashLog.readLastCrash(activity)
                    if (crash != null) {
                        val tv = android.widget.TextView(activity).apply {
                            text = crash
                            textSize = 11f
                            typeface = android.graphics.Typeface.MONOSPACE
                            setTextIsSelectable(true)
                            val p = M3Kit.dp(activity, 20f)
                            setPadding(p, M3Kit.dp(activity, 12f), p, 0)
                        }
                        M3Kit.styleButtons(tv)
                        MaterialAlertDialogBuilder(activity)
                            .setTitle("最近一次崩溃")
                            .setView(android.widget.ScrollView(activity).apply { addView(tv) })
                            .setNegativeButton(R.string.cancel, null)
                            .show()
                    }
                }
            }
            .show()
    }

    // ================= 设置页：时钟校准提醒 =================

    fun showClockCalibration(activity: Activity, serverMs: Long?) {
        val deviceMs = System.currentTimeMillis()
        val msg = if (serverMs == null) {
            "没能拿到网络时间（可能离线）。时钟不准会影响日志时间线与更新判断，" +
                "建议在系统设置里开启「自动确定时间」。"
        } else {
            val drift = ClockDrift.driftSeconds(deviceMs, serverMs)
            if (ClockDrift.needsCalibration(deviceMs, serverMs)) {
                "${ClockDrift.describe(drift)}：请在系统设置里开启「使用网络提供的时区/时间」。"
            } else {
                "${ClockDrift.describe(drift)}：时钟正常，无需校准。"
            }
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle("时钟校准")
            .setMessage(msg)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton("去时间设置") { _, _ ->
                runCatching {
                    activity.startActivity(Intent(Settings.ACTION_DATE_SETTINGS))
                }.onFailure {
                    runCatching { activity.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                }
            }
            .show()
    }

    // ================= 关于页：一键诊断报告 / 反馈模板 =================

    fun showDiagnosticReport(activity: Activity) {
        val col = M3Kit.dialogColumn(activity)
        val preview = M3Kit.monoBlock(activity, 200f).apply {
            text = DebugReport.build(activity, "（诊断报告预览，点「生成完整报告」后可复制/分享）")
        }
        col.addView(M3Kit.cardTitle(activity, "一键诊断报告"))
        col.addView(
            M3Kit.cardDesc(
                activity,
                "自动打包：设备信息 · 版本与通道 · 连接状态 · 最近操作轨迹 · 界面元素树 · 日志尾部。" +
                    "遇到问题时发给开发者即可定位，无需连电脑。"
            )
        )
        col.addView(preview)
        val input = android.widget.EditText(activity).apply {
            hint = "补充描述（可选）：你在哪个界面做了什么？"
            textSize = 13f
            setSingleLine()
        }
        col.addView(input)
        M3Kit.styleButtons(col)
        MaterialAlertDialogBuilder(activity)
            .setTitle("诊断报告")
            .setView(android.widget.ScrollView(activity).apply { addView(col) })
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton("复制") { _, _ ->
                val report = DebugReport.build(activity, input.text.toString())
                copyText(activity, report)
                android.widget.Toast.makeText(activity, "已复制诊断报告", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("分享") { _, _ ->
                val report = DebugReport.build(activity, input.text.toString())
                val f = DebugReport.save(activity, report)
                if (f != null) shareFile(activity, f) else {
                    android.widget.Toast.makeText(activity, "保存失败", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .show()
        AppLog.event("关于页", "打开一键诊断报告")
    }

    fun showFeedbackTemplate(activity: Activity) {
        val tpl = buildString {
            append("【问题描述】（哪个界面、点什么操作、出现什么现象）\n\n")
            append("【复现步骤】1. … 2. … 3. …\n\n")
            append("【发生频率】每次 / 偶发（约几次）\n\n")
            append("【设备与版本】").append(Build.MODEL)
                .append(" · Android ").append(Build.VERSION.RELEASE)
                .append(" · 灵逸 MB ").append(BuildConfig.VERSION_NAME).append("\n\n")
            append("【连接方式】蓝牙 / USB · 板载固件版本：\n\n")
            append("【已尝试】重启 App / 重连 / 重新刷机 …\n")
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle("反馈模板")
            .setMessage("复制下面的模板填写后，连同「一键诊断报告」一起发送，定位最快。")
            .setView(
                android.widget.TextView(activity).apply {
                    text = tpl
                    textSize = 12f
                    typeface = android.graphics.Typeface.MONOSPACE
                    setTextIsSelectable(true)
                    val p = M3Kit.dp(activity, 20f)
                    setPadding(p, M3Kit.dp(activity, 12f), p, 0)
                }
            )
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton("复制模板") { _, _ ->
                copyText(activity, tpl)
                android.widget.Toast.makeText(activity, "模板已复制", android.widget.Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    // ================= 关于页：更新通道说明 / 依赖清单 =================

    fun showChannelExplainer(activity: Activity) {
        val ch = Prefs.getChannel(activity)
        MaterialAlertDialogBuilder(activity)
            .setTitle("更新通道说明")
            .setMessage(
                "稳定版：经过完整验证，推荐日常使用；更新最稳、推送最慢。\n" +
                    "预览版（Beta）：抢先体验新功能，可能带未完善细节；适合愿意反馈的用户。\n" +
                    "开发版：开发者测试专用，含调试工具与对外接口，请勿日常使用。\n\n" +
                    "当前通道：${com.lingyi.mbos.util.UpdateChecker.channelLabel(ch)}。\n" +
                    "切换入口：关于页 / 设置页 / 开发者页的「更新通道」下拉框，选中即生效。"
            )
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton("去切换") { _, _ ->
                runCatching { activity.startActivity(Intent(activity, com.lingyi.mbos.DeveloperActivity::class.java)) }
            }
            .show()
    }

    fun showDependencies(activity: Activity) {
        val col = M3Kit.dialogColumn(activity)
        val rows = listOf(
            "蓝牙（BLE）" to "无线连接 micro:bit，扫描 / 连接 / 收发串口数据",
            "USB OTG" to "有线连接 micro:bit CDC 串口（无需网络）",
            "悬浮窗（SYSTEM_ALERT_WINDOW）" to "陀螺仪指针 / 模拟器 / 调试控制台悬浮窗",
            "前台服务" to "退出界面后保持指针与连接",
            "通知" to "常驻服务状态提示（可下拉停止）",
            "存储（应用私有）" to "日志 / 崩溃记录 / 诊断报告（无需存储权限）",
            "网络" to "仅用于检查更新；核心功能完全离线可用"
        )
        rows.forEach { (k, v) ->
            col.addView(M3Kit.infoRow(activity, k, v))
            col.addView(M3Kit.divider(activity))
        }
        M3Kit.styleButtons(col)
        MaterialAlertDialogBuilder(activity)
            .setTitle("权限与依赖清单")
            .setView(android.widget.ScrollView(activity).apply { addView(col) })
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ================= 关于页：更新重试 =================

    fun showUpdateRetry(activity: Activity, onRetry: () -> Unit) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("检查更新失败？")
            .setMessage("可能是网络波动。重试会重新拉取版本信息；持续失败可打开发布页手动下载。")
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton("重试") { _, _ -> onRetry() }
            .setNeutralButton("打开发布页") { _, _ ->
                runCatching {
                    activity.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(com.lingyi.mbos.util.UpdateChecker.PAGE_URL)
                        )
                    )
                }
            }
            .show()
    }

    // ================= 开发者页：构建信息 =================

    fun showBuildInfo(activity: Activity) {
        val info = buildString {
            append("版本名：").append(BuildConfig.VERSION_NAME).append('\n')
            append("版本号：").append(BuildConfig.VERSION_CODE).append('\n')
            append("渠道：").append(com.lingyi.mbos.util.UpdateChecker.channelLabel(Prefs.getChannel(activity))).append('\n')
            append("应用 ID：").append(BuildConfig.APPLICATION_ID).append('\n')
            append("构建类型：").append(BuildConfig.BUILD_TYPE).append('\n')
            append("ABI：").append(Build.SUPPORTED_ABIS.joinToString()).append('\n')
            append("设备：").append(Build.BRAND).append(' ').append(Build.MODEL).append('\n')
            append("系统：Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            append("内置固件：MB-OS v").append(FirmwareScript.VERSION).append('\n')
            append("日志目录：").append(AppLog.files().firstOrNull()?.parent ?: "-").append('\n')
            append("日志大小：").append(AppLog.totalSize() / 1024).append(" KB\n")
        }
        val tv = android.widget.TextView(activity).apply {
            text = info
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
            val p = M3Kit.dp(activity, 20f)
            setPadding(p, M3Kit.dp(activity, 12f), p, 0)
        }
        M3Kit.styleButtons(tv)
        MaterialAlertDialogBuilder(activity)
            .setTitle("构建信息")
            .setView(android.widget.ScrollView(activity).apply { addView(tv) })
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton("复制") { _, _ ->
                copyText(activity, info)
                android.widget.Toast.makeText(activity, "已复制构建信息", android.widget.Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    // ================= 开发者页：断线演练 =================

    fun showDropDrill(activity: Activity) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("断线演练")
            .setMessage(
                "将模拟一次「意外断线」，用于验证指数退避自动重连是否生效。" +
                    "演练后连接会在 1/2/4/8… 秒内自动恢复；若始终不恢复，说明重连链路有问题。"
            )
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton("开始演练") { _, _ ->
                ConnectionHub.debugSimulateDrop()
                android.widget.Toast.makeText(activity, "已模拟断线，观察自动重连", android.widget.Toast.LENGTH_SHORT).show()
                AppLog.event("断线演练", "触发模拟意外断线")
            }
            .show()
    }

    // ================= 通用：调试控制台开关 =================

    fun openDebugConsole(activity: Activity) {
        DebugConsoleService.start(activity)
        Prefs.setDebugConsole(activity, true)
    }

    fun closeDebugConsole(activity: Activity) {
        DebugConsoleService.stop(activity)
        Prefs.setDebugConsole(activity, false)
    }

    fun isDebugConsoleOn(activity: Activity): Boolean = DebugConsoleService.isRunning()

    // ================= 工具 =================

    fun copyText(ctx: Context, text: String) {
        runCatching {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("灵逸 MB", text))
        }
    }

    fun shareFile(ctx: Context, f: java.io.File) {
        runCatching {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                ctx, "${ctx.packageName}.fileprovider", f
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(intent, "分享报告"))
        }.onFailure { AppLog.w("Stability", "分享失败", it) }
    }

    /** 打包日志（复用 LogPackager，供开发者页/关于页）。 */
    fun shareLogPackage(activity: Activity) {
        val r = LogPackager.pack(activity)
        if (r == null) {
            android.widget.Toast.makeText(activity, "打包失败（无日志？）", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            shareFile(activity, r.file)
        }
    }
}

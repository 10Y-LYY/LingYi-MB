/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.util

import android.content.Context
import android.os.Build
import com.lingyi.mbos.BuildConfig
import com.lingyi.mbos.ble.ConnectionHub
import com.lingyi.mbos.ui.FirmwareScript
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 问题上报 / 一键诊断报告生成器。
 *
 * 汇聚七类现场：设备信息、应用信息、连接状态、操作轨迹、相关日志、
 * 界面元素树、用户描述。产出纯文本（复制 / 保存 / 分享三路消费）。
 *
 * 与「关于页 → 错误日志反馈」的区别：那份只发日志；这份是**结构化现场**，
 * 开发者拿到就能定位「用户在哪个界面、做了什么、看到什么、设备什么状态」。
 */
object DebugReport {

    data class Section(val title: String, val body: String)

    /** 生成完整报告文本（同步，纯内存计算 + 读日志尾部，可在后台线程调用）。 */
    fun build(ctx: Context, userDesc: String): String {
        val sections = listOf(
            Section("用户描述", userDesc.ifBlank { "(未填写)" }),
            Section("设备信息", deviceInfo()),
            Section("应用信息", appInfo(ctx)),
            Section("连接状态", DebugBus.connectionSnapshot()),
            Section("最近操作轨迹", trailText()),
            Section("界面元素树", ViewTreeCapture.capture()),
            Section("相关日志", logText()),
        )
        return render(sections)
    }

    fun deviceInfo(): String = buildString {
        append("品牌/型号：").append(Build.BRAND).append(' ').append(Build.MODEL).append('\n')
        append("系统：Android ").append(Build.VERSION.RELEASE)
            .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
        append("ABI：").append(Build.SUPPORTED_ABIS.firstOrNull() ?: "?").append('\n')
    }

    fun appInfo(ctx: Context): String = buildString {
        append("版本：灵逸 MB ").append(BuildConfig.VERSION_NAME)
            .append(" (code ").append(BuildConfig.VERSION_CODE).append(")\n")
        append("通道：").append(com.lingyi.mbos.util.UpdateChecker.channelLabel(Prefs.getChannel(ctx))).append('\n')
        append("内置固件：MB-OS v").append(FirmwareScript.VERSION).append('\n')
        append("连接方式偏好：").append(Prefs.getConnType(ctx)).append('\n')
        append("生成时间：").append(nowText()).append('\n')
    }

    fun trailText(n: Int = 40): String {
        val lines = ActionTrail.recent(n)
        return if (lines.isEmpty()) "(无操作记录)" else lines.joinToString("\n")
    }

    fun logText(maxChars: Int = 4000): String {
        val tail = AppLog.latestTail(maxChars)
        return if (tail.isBlank()) "(无日志文件内容)" else tail
    }

    /** 抓包摘要（若开着抓包）。 */
    fun packetText(n: Int = 60): String {
        val ps = DebugBus.packets(n)
        return if (ps.isEmpty()) "(未开启抓包或无数据)" else ps.joinToString("\n") { it.render() }
    }

    /** 保存报告到日志目录，返回文件（失败返回 null）。 */
    fun save(ctx: Context, report: String): File? = runCatching {
        val dir = File(ctx.getExternalFilesDir(null), "reports").apply { mkdirs() }
            ?: File(ctx.filesDir, "reports").apply { mkdirs() }
        val f = File(dir, "bug-report-${fileStamp()}.txt")
        f.writeText(report)
        f
    }.getOrNull()

    private fun render(sections: List<Section>): String = buildString {
        append("==== 灵逸 MB 问题上报 ====\n")
        sections.forEach { s ->
            append("\n---- ").append(s.title).append(" ----\n")
            append(s.body.trimEnd()).append('\n')
        }
        append("\n==== 报告结束 ====\n")
    }

    private fun nowText(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    private fun fileStamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
}

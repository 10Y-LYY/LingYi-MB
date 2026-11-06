/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.util

import android.content.Context
import android.os.Build
import com.lingyi.mbos.BuildConfig
import com.lingyi.mbos.ui.FirmwareScript
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 日志打包器：把 logs/ 与 crashes/ 两个目录打成一个小包，供邮件发送或分享。
 *
 * 多级打包策略（「通过各种方式打包」）：
 *  1. zip 归档：条目按 `logs/xxx.log`、`crashes/xxx.log` 组织，
 *     并附 `device-info.txt` 设备信息清单 —— 默认方式，体积最小、结构清晰；
 *  2. 纯文本合并：zip 异常（极端存储故障）时回退，
 *     所有日志按顺序拼接成单个 .txt，保证任何环境下都拿得到现场。
 */
object LogPackager {

    data class Result(val file: File, val format: String)

    private val stampFmt = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    /** 打包；无任何日志时返回 null。 */
    fun pack(ctx: Context): Result? {
        val logs = AppLog.files() + CrashLog.files()
        if (logs.isEmpty()) return null

        val outDir = File(ctx.cacheDir, "packaged").apply { mkdirs() }
        val stamp = stampFmt.format(Date())

        // 方式一：zip
        val zip = File(outDir, "mbos-logs-$stamp.zip")
        runCatching { buildZip(zip, logs, ctx) }
            .onFailure { AppLog.e("LogPackager", "zip 打包失败，回退纯文本", it) }
        if (zip.exists() && zip.length() > 0) return Result(zip, "zip")

        // 方式二：纯文本合并
        val txt = File(outDir, "mbos-logs-$stamp.txt")
        runCatching {
            txt.writeText(buildString {
                append(deviceInfo(ctx)).append('\n')
                logs.forEach { f ->
                    append("\n\n===== ${f.parentFile?.name}/${f.name} =====\n")
                    append(runCatching { f.readText() }.getOrElse { "(读取失败：${it.message})" })
                }
            })
        }.onFailure { AppLog.e("LogPackager", "纯文本打包失败", it) }
        return if (txt.exists() && txt.length() > 0) Result(txt, "txt") else null
    }

    private fun buildZip(zip: File, logs: List<File>, ctx: Context) {
        ZipOutputStream(FileOutputStream(zip).buffered()).use { zo ->
            zo.setLevel(6)
            zo.putNextEntry(ZipEntry("device-info.txt"))
            zo.write(deviceInfo(ctx).toByteArray())
            zo.closeEntry()
            logs.forEach { f ->
                zo.putNextEntry(ZipEntry("${f.parentFile?.name}/${f.name}"))
                f.inputStream().buffered().use { it.copyTo(zo) }
                zo.closeEntry()
            }
        }
    }

    /**
     * 设备信息清单：只含排错必需字段，不收集任何个人身份信息
     * （无 IMEI / 无序列号 / 无账号 / 无位置）。
     */
    fun deviceInfo(ctx: Context): String = buildString {
        append("==== 灵逸 MB 日志包设备信息 ====\n")
        append("生成时间：").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())).append('\n')
        append("应用：灵逸 MB ").append(BuildConfig.VERSION_NAME)
            .append(" (code ").append(BuildConfig.VERSION_CODE).append(")\n")
        append("包名：").append(ctx.packageName).append('\n')
        append("固件：MB-OS v").append(FirmwareScript.VERSION).append('\n')
        append("设备：").append(Build.BRAND).append(' ').append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            .append('\n')
        append("系统：Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT)
            .append(") · ABI ").append(Build.SUPPORTED_ABIS.firstOrNull() ?: "?").append('\n')
        append("语言：").append(Locale.getDefault().toLanguageTag()).append('\n')
        append("日志文件：").append(AppLog.files().size).append(" 个 / 约 ")
            .append(AppLog.totalSize() / 1024).append(" KB\n")
        append("崩溃文件：").append(CrashLog.count()).append(" 个")
        CrashLog.lastCrashTime()?.let { append("，最近崩溃：").append(it) }
        append('\n')
    }

    /** 邮件正文摘要：设备信息 + 崩溃概况 + 日志尾部，方便不打开附件也能判断问题。 */
    fun emailBody(ctx: Context): String = buildString {
        append(deviceInfo(ctx))
        append("\n---- 崩溃概况 ----\n")
        if (CrashLog.count() == 0) {
            append("无崩溃记录\n")
        } else {
            CrashLog.files().forEach { f ->
                append("• ").append(f.name).append("（").append(f.length() / 1024).append(" KB）\n")
            }
        }
        append("\n---- 日志尾部（最近 30 行）----\n")
        append(AppLog.recentLines(30).joinToString("\n").ifEmpty { "(内存缓冲为空，详见附件)" })
        append('\n')
    }
}

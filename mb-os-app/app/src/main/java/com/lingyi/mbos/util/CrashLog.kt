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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃捕获与崩溃日志管理。
 *
 * 背景：MBOS 机制下未捕获异常会直接终止进程、系统重启回引导页（LAUNCHER），
 * 用户感知就是「闪退回引导页」。异常沿任意线程（USB 读线程 / BLE Binder 线程 /
 * 主线程）都可能发生，仅靠 Logcat 无法回溯——必须落盘。
 *
 * 行为：
 * 1. [init] 安装默认未捕获异常处理器；崩溃时先把完整现场写进
 *    `crashes/crash-yyyy-MM-dd_HH-mm-ss[-n].log`，再交还上一个处理器
 *    （保留系统重启回引导页的既有行为，日志已安全落盘）。
 * 2. 每份崩溃文件含：时间 / 线程 / 版本 / 固件 / 设备信息 / 完整异常链 / 崩溃前日志尾部。
 * 3. 「待处理崩溃」= 文件时间晚于用户上次查看时间（Prefs.crash_seen_ts），
 *    下次启动时引导页据此弹窗询问「发送日志 / 查看 / 忽略」。
 * 4. 迁移旧版单文件 `filesDir/crash-last.log` 到 crashes/ 目录，升级不丢历史。
 */
object CrashLog {

    private const val DIR_NAME = "crashes"
    private const val FILE_PREFIX = "crash-"
    private const val LEGACY_FILE = "crash-last.log"

    /** 最多保留 20 份崩溃文件（每个独立时间戳，不互相覆盖）。 */
    private const val MAX_CRASH_FILES = 20

    @Volatile
    private var dir: File? = null

    @Volatile
    private var installed = false

    // SimpleDateFormat 非线程安全：崩溃写盘（崩溃线程）与 UI 线程查询会并发使用，
    // 用 ThreadLocal 隔离（与 AppLog/ActionTrail/DebugBus 的既有写法一致，v4.2）
    private val stampFmt = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US) }
    private val humanFmt = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }

    /** 安装崩溃处理器（幂等；Application.onCreate 调用）。 */
    fun init(ctx: Context) {
        resolveDir(ctx)
        if (!installed) {
            installed = true
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { t, e ->
                runCatching { writeCrash(t, e) }
                // 交还系统默认行为（杀进程 → 系统重启回引导页），保证既有恢复机制不变
                previous?.uncaughtException(t, e)
            }
        }
        migrateLegacy(ctx)
    }

    private fun resolveDir(ctx: Context): File? {
        val d = dir?.takeIf { it.exists() }
            ?: runCatching { File(ctx.getExternalFilesDir(null), DIR_NAME).apply { mkdirs() } }.getOrNull()
            ?: runCatching { File(ctx.filesDir, DIR_NAME).apply { mkdirs() } }.getOrNull()
        dir = d
        return d
    }

    /** 旧版单文件崩溃日志迁移：保留历史，迁完即删原件。 */
    private fun migrateLegacy(ctx: Context) {
        val legacy = File(ctx.filesDir, LEGACY_FILE)
        if (!legacy.exists()) return
        runCatching {
            val target = File(resolveDir(ctx), "${FILE_PREFIX}legacy-${stampFmt.get()!!.format(Date(legacy.lastModified()))}.log")
            legacy.copyTo(target, overwrite = true)
            legacy.delete()
            AppLog.i("CrashLog", "已迁移旧版崩溃日志 → ${target.name}")
        }
    }

    /** 崩溃现场落盘（在即将被杀死的进程中同步执行，必须快）。 */
    fun writeCrash(t: Thread, e: Throwable) {
        val d = dir ?: return
        runCatching {
            var idx = 0
            var f = File(d, "$FILE_PREFIX${stampFmt.get()!!.format(Date())}.log")
            while (f.exists()) {
                idx++
                f = File(d, "$FILE_PREFIX${stampFmt.get()!!.format(Date())}-$idx.log")
            }
            f.writeText(buildCrashText(t, e))
            AppLog.e("CrashLog", "未捕获异常已落盘：${f.name}", e)
            prune(d)
        }
    }

    /** 记录一条「非致命异常」：被 runCatching 兜住的错误也留痕，便于发现潜在崩点。 */
    fun nonFatal(tag: String, where: String, tr: Throwable) {
        AppLog.e(tag, "[非致命] $where：${tr.javaClass.simpleName}: ${tr.message}", tr)
    }

    private fun buildCrashText(t: Thread, e: Throwable): String = buildString {
        append("==== 灵逸 MB 未捕获异常（进程崩溃）====\n")
        append("时间：").append(humanFmt.get()!!.format(Date())).append('\n')
        append("线程：").append(t.name).append('\n')
        append("版本：灵逸 MB ").append(BuildConfig.VERSION_NAME)
            .append(" (code ").append(BuildConfig.VERSION_CODE).append(")\n")
        append("固件：MB-OS v").append(FirmwareScript.VERSION).append('\n')
        append("设备：").append(Build.BRAND).append(' ').append(Build.MODEL)
            .append(" · Android ").append(Build.VERSION.RELEASE)
            .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
        append("\n---- 异常链 ----\n")
        var cause: Throwable? = e
        while (cause != null) {
            append(cause.javaClass.name)
            if (!cause.message.isNullOrBlank()) append(": ").append(cause.message)
            append('\n')
            cause.stackTrace.forEach { append("    at ").append(it.toString()).append('\n') }
            cause = cause.cause
            if (cause != null) append("  Caused by:\n")
        }
        append("\n---- 崩溃前日志尾部 ----\n")
        append(AppLog.recentLines(80).joinToString("\n").ifEmpty { "(无)" })
        append('\n')
    }

    /** 超量时删最旧的崩溃文件。 */
    private fun prune(d: File) {
        val all = d.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
        if (all.size <= MAX_CRASH_FILES) return
        all.take(all.size - MAX_CRASH_FILES).forEach { runCatching { it.delete() } }
    }

    // ---------- 查询 / 状态 ----------

    /** 全部崩溃文件（按时间排序）。 */
    fun files(): List<File> = dir?.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()

    /** 待处理崩溃：晚于用户上次查看时间的崩溃文件（下次启动提醒用）。 */
    fun pendingCrashes(ctx: Context): List<File> {
        val seen = Prefs.getCrashSeenTs(ctx)
        return files().filter { it.lastModified() > seen }
    }

    /** 读取最近一次崩溃日志全文（无则 null）。 */
    fun readLastCrash(ctx: Context): String? {
        val f = files().lastOrNull() ?: return null
        return runCatching { f.readText() }.getOrNull()
    }

    /** 标记「已查看/已处理」：清掉待处理角标。 */
    fun markSeen(ctx: Context) = Prefs.setCrashSeenTs(ctx, System.currentTimeMillis())

    /** 崩溃次数（当前保留的文件数）。 */
    fun count(): Int = files().size

    /** 最近崩溃的人类可读时间（无则 null）。 */
    fun lastCrashTime(): String? = files().lastOrNull()?.let { humanFmt.get()!!.format(Date(it.lastModified())) }

    /** 清空全部崩溃文件。 */
    fun clear() {
        dir?.listFiles()?.forEach { runCatching { it.delete() } }
    }
}

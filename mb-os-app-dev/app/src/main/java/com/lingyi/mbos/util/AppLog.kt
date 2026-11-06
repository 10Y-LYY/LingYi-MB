/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.lingyi.mbos.ui.FirmwareScript
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 统一日志收集器（进程级单例）。
 *
 * 设计目标：App 内任何位置（UI 线程 / USB 读线程 / BLE Binder 线程）都能一行调用记录日志；
 * 崩溃复盘时把整目录打包发邮件即可，无需连 adb。
 *
 * - 落盘位置：外部私有目录 `Android/data/<pkg>/files/logs/mbos-log-yyyy-MM-dd[-n].log`
 *   （无需任何存储权限，应用卸载随行删除；FileProvider 对外授权读取）。
 * - 滚动策略：按天分文件；单文件超过 [PER_FILE_MAX_BYTES] 自动追加 `-2`、`-3` 序号；
 *   全部文件总大小超过 [MAX_TOTAL_BYTES] 或文件数超过 [MAX_FILES] 时删最旧的。
 * - 内存缓冲：最近 [MEMORY_LINES] 行常驻内存，崩溃时写入「崩溃前现场」，
 *   也供界面「查看日志」直接展示（文件 IO 失败时有兜底）。
 * - 线程安全：写入串行化（[lock]），时间格式化用 ThreadLocal（SimpleDateFormat 非线程安全）。
 */
object AppLog {

    private const val DIR_NAME = "logs"
    private const val FILE_PREFIX = "mbos-log-"

    /** 单文件上限 512KB：一天内错误风暴也不撑爆单文件。 */
    private const val PER_FILE_MAX_BYTES = 512 * 1024L

    /** 全部日志总大小上限 2MB：邮件附件小巧，旧机邮件客户端也发得动。 */
    private const val MAX_TOTAL_BYTES = 2 * 1024 * 1024L

    /** 最多保留 14 个日志文件。 */
    private const val MAX_FILES = 14

    /** 内存缓冲行数。 */
    private const val MEMORY_LINES = 400

    private val lock = ReentrantLock()
    private val memory = ArrayDeque<String>()

    @Volatile
    private var dir: File? = null

    @Volatile
    private var initialized = false

    private val timeFmt = ThreadLocal.withInitial {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    }
    private val dayFmt = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }

    /**
     * 初始化（Application.onCreate 调用；允许多次调用）。
     * 若日志目录已不存在（外部存储被卸载/测试沙箱重建），自动重新解析。
     */
    fun init(ctx: Context) {
        val d = resolveDir(ctx)
        lock.withLock {
            if (initialized && dir?.exists() == true) return
            dir = d
            initialized = true
        }
        i("AppLog", "日志系统就绪，目录=${d?.absolutePath ?: "(不可用)"}")
    }

    private fun resolveDir(ctx: Context): File? =
        runCatching { File(ctx.getExternalFilesDir(null), DIR_NAME).apply { mkdirs() } }.getOrNull()
            ?: runCatching { File(ctx.filesDir, DIR_NAME).apply { mkdirs() } }.getOrNull()

    // ---------- 对外记录 API ----------

    fun v(tag: String, msg: String) = write("V", tag, msg, null)
    fun d(tag: String, msg: String) = write("D", tag, msg, null)

    fun i(tag: String, msg: String) {
        write("I", tag, msg, null)
        Log.i(tag, msg)
    }

    fun w(tag: String, msg: String, tr: Throwable? = null) {
        write("W", tag, msg, tr)
        Log.w(tag, msg, tr)
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        write("E", tag, msg, tr)
        Log.e(tag, msg, tr)
    }

    /** 记录一条「事件」：生命周期 / 连接状态变化 / 用户关键操作。 */
    fun event(name: String, detail: String = "") = i("Event", if (detail.isEmpty()) name else "$name | $detail")

    // ---------- 读取 / 清理 ----------

    /** 内存缓冲中的最近日志（查看日志弹窗用）。 */
    fun recentLines(n: Int = MEMORY_LINES): List<String> = lock.withLock { memory.toList().takeLast(n) }

    /** 全部日志文件（按名称排序，即时间顺序）。 */
    fun files(): List<File> = dir?.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()

    /** 全部日志总字节数。 */
    fun totalSize(): Long = files().sumOf { it.length() }

    /** 最新日志文件内容尾部（最多 [maxChars] 字符）。 */
    fun latestTail(maxChars: Int = 6000): String {
        val f = files().lastOrNull() ?: return ""
        val text = runCatching { f.readText() }.getOrNull() ?: return ""
        return if (text.length <= maxChars) text else text.takeLast(maxChars)
    }

    /** 清空全部日志文件与内存缓冲。 */
    fun clear() {
        lock.withLock {
            memory.clear()
            dir?.listFiles()?.forEach { runCatching { it.delete() } }
        }
    }

    // ---------- 内部实现 ----------

    private fun write(level: String, tag: String, msg: String, tr: Throwable?) {
        val line = buildLine(level, tag, msg, tr)
        lock.withLock {
            memory.addLast(line)
            while (memory.size > MEMORY_LINES) memory.removeFirst()
            appendToFile(line)
        }
    }

    private fun buildLine(level: String, tag: String, msg: String, tr: Throwable?): String {
        val sb = StringBuilder()
        sb.append(timeFmt.get()!!.format(Date()))
            .append(' ').append(level).append('/').append(tag)
            .append(": ").append(msg)
        var cause: Throwable? = tr
        while (cause != null) {
            sb.append("\n  ↳ ").append(cause.javaClass.name)
            if (!cause.message.isNullOrBlank()) sb.append(": ").append(cause.message)
            cause.stackTrace.take(10).forEach { sb.append("\n      at ").append(it.toString()) }
            cause = cause.cause
            if (cause != null) sb.append("\n  Caused by:")
        }
        return sb.toString()
    }

    private fun appendToFile(line: String) {
        val d = dir ?: return
        runCatching {
            val f = currentFile(d)
            if (!f.exists()) f.writeText(header())
            f.appendText(line + "\n")
            rotateIfNeeded(d)
        }
    }

    /** 取当天当前文件：不存在则新建；超限则追加序号。 */
    private fun currentFile(d: File): File {
        val base = "$FILE_PREFIX${dayFmt.get()!!.format(Date())}"
        var idx = 0
        var f = File(d, "$base.log")
        while (f.exists() && f.length() >= PER_FILE_MAX_BYTES) {
            idx++
            f = File(d, "$base-$idx.log")
        }
        return f
    }

    private fun rotateIfNeeded(d: File) {
        val all = d.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
        if (all.size <= MAX_FILES && all.sumOf { it.length() } <= MAX_TOTAL_BYTES) return
        var total = all.sumOf { it.length() }
        for (f in all) {
            if (all.size <= MAX_FILES && total <= MAX_TOTAL_BYTES) break
            total -= f.length()
            runCatching { f.delete() }
        }
    }

    /** 新日志文件的文件头：设备 + 应用信息，便于收到邮件时直接判断环境。 */
    private fun header(): String = buildString {
        append("==== 灵逸 MB 运行日志 · ").append(dayFmt.get()!!.format(Date())).append(" ====\n")
        append("设备：").append(Build.BRAND).append(' ').append(Build.MODEL)
            .append(" · Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT)
            .append(") · ").append(Build.SUPPORTED_ABIS.firstOrNull() ?: "?").append('\n')
        append("应用：灵逸 MB ").append(com.lingyi.mbos.BuildConfig.VERSION_NAME)
            .append(" (code ").append(com.lingyi.mbos.BuildConfig.VERSION_CODE)
            .append(") · 固件 MB-OS v").append(FirmwareScript.VERSION).append('\n')
        append("================================\n")
    }
}

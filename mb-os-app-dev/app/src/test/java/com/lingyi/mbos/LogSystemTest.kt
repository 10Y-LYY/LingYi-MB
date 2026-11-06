/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lingyi.mbos.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

/**
 * 报错日志系统测试：AppLog 落盘/滚动/清理、CrashLog 捕获/待处理/迁移、
 * LogPackager 打包内容、LogMailer 邮箱常量与 FileProvider 授权。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = com.lingyi.mbos.MBOSApp::class)
class LogSystemTest {

    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    /** AppLog 的日志目录（外部私有目录优先，与实现保持一致地解析一次）。 */
    private fun logDir(): File =
        File(ctx().getExternalFilesDir(null), "logs").apply { mkdirs() }

    private fun crashDir(): File =
        File(ctx().getExternalFilesDir(null), "crashes").apply { mkdirs() }

    @Before
    fun setUp() {
        AppLog.init(ctx())
        CrashLog.init(ctx())
        AppLog.clear()
        CrashLog.clear()
        Prefs.setCrashSeenTs(ctx(), 0L)
    }

    // ---------- AppLog ----------

    @Test
    fun appLog_writesFileWithHeaderAndFormat() {
        AppLog.i("Test", "hello-log-line")
        val files = AppLog.files()
        assertEquals("应只产生 1 个当天日志文件", 1, files.size)
        val text = files[0].readText()
        assertTrue("文件头应含设备信息", text.contains("灵逸 MB 运行日志"))
        assertTrue("文件头应含应用版本", text.contains(BuildConfig.VERSION_NAME))
        assertTrue("应含日志正文", text.contains("I/Test: hello-log-line"))
        assertTrue("内存缓冲应保留该行", AppLog.recentLines().any { it.contains("hello-log-line") })
    }

    @Test
    fun appLog_recordsThrowableChain() {
        val root = IllegalStateException("root-cause")
        val top = RuntimeException("top-msg", root)
        AppLog.e("Test", "boom", top)
        val text = AppLog.latestTail()
        assertTrue(text.contains("E/Test: boom"))
        assertTrue("应含顶层异常", text.contains("RuntimeException: top-msg"))
        assertTrue("应含原因链", text.contains("Caused by"))
        assertTrue("应含根因", text.contains("IllegalStateException: root-cause"))
    }

    @Test
    fun appLog_prunesOldFilesBeyondMax() {
        // 造 15 个旧文件（超过 MAX_FILES=14），再写一条日志触发清理
        val dir = logDir()
        dir.listFiles()?.forEach { it.delete() }
        repeat(15) { i ->
            File(dir, "mbos-log-2020-01-${String.format("%02d", i + 1)}.log").apply {
                writeText("old-$i")
                setLastModified(1_000_000_000_000L + i * 60_000L)
            }
        }
        AppLog.i("Test", "trigger-rotate")
        val files = AppLog.files()
        assertTrue("文件数应被修剪到 ≤14，实际 ${files.size}", files.size <= 14)
        assertTrue("新写的日志必须保留", files.any { it.readText().contains("trigger-rotate") })
        assertTrue("最旧的应被删", files.none { it.readText() == "old-0" })
    }

    @Test
    fun appLog_prunesByTotalSize() {
        val dir = logDir()
        dir.listFiles()?.forEach { it.delete() }
        // 两个 1.2MB 旧文件（总 2.4MB > 2MB 上限）
        repeat(2) { i ->
            File(dir, "mbos-log-2021-0${i + 1}-01.log").apply {
                writeText("x".repeat(1200 * 1024))
                setLastModified(1_600_000_000_000L + i * 60_000L)
            }
        }
        AppLog.i("Test", "trigger-size-rotate")
        assertTrue("总大小应被修剪到 ≤2MB，实际 ${AppLog.totalSize() / 1024}KB", AppLog.totalSize() <= 2 * 1024 * 1024)
    }

    @Test
    fun appLog_clearRemovesEverything() {
        AppLog.i("Test", "to-be-cleared")
        assertTrue(AppLog.files().isNotEmpty())
        AppLog.clear()
        assertTrue(AppLog.files().isEmpty())
        assertTrue(AppLog.recentLines().isEmpty())
        assertEquals(0L, AppLog.totalSize())
    }

    // ---------- CrashLog ----------

    @Test
    fun crashLog_capturesUncaughtExceptionToFile() {
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        assertNotNull("CrashLog 应已安装未捕获异常处理器", handler)
        handler.uncaughtException(Thread.currentThread(), RuntimeException("boom-test"))
        val crashes = CrashLog.files()
        assertEquals("应落盘 1 份崩溃文件", 1, crashes.size)
        val text = crashes[0].readText()
        assertTrue("应含标题", text.contains("未捕获异常"))
        assertTrue("应含异常", text.contains("RuntimeException: boom-test"))
        assertTrue("应含堆栈", text.contains("    at "))
        assertTrue("应含崩溃前日志尾部", text.contains("崩溃前日志尾部"))
        assertTrue("应含版本信息", text.contains(BuildConfig.VERSION_NAME))
        assertTrue("readLastCrash 应可读回", CrashLog.readLastCrash(ctx())!!.contains("boom-test"))
        assertTrue("lastCrashTime 应有值", CrashLog.lastCrashTime() != null)
        assertEquals(1, CrashLog.count())
    }

    @Test
    fun crashLog_pendingFlow_marksSeen() {
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        handler.uncaughtException(Thread.currentThread(), RuntimeException("pending-test"))
        // seen=0 → 有待处理崩溃
        assertTrue("seen=0 时应检测到待处理崩溃", CrashLog.pendingCrashes(ctx()).isNotEmpty())
        CrashLog.markSeen(ctx())
        assertTrue("markSeen 后不应再有待处理崩溃", CrashLog.pendingCrashes(ctx()).isEmpty())
        // 新崩溃（文件时间戳晚于 seen）重新变为待处理
        Thread.sleep(1100) // 崩溃文件名精确到秒，错开避免 -2 后缀干扰
        handler.uncaughtException(Thread.currentThread(), RuntimeException("pending-test-2"))
        assertTrue("新崩溃应重新待处理", CrashLog.pendingCrashes(ctx()).isNotEmpty())
    }

    @Test
    fun crashLog_clearRemovesFiles() {
        Thread.getDefaultUncaughtExceptionHandler()
            .uncaughtException(Thread.currentThread(), RuntimeException("clear-test"))
        assertTrue(CrashLog.files().isNotEmpty())
        CrashLog.clear()
        assertTrue(CrashLog.files().isEmpty())
        assertTrue(CrashLog.readLastCrash(ctx()).isNullOrBlank())
    }

    // ---------- LogPackager ----------

    @Test
    fun logPackager_buildsZipWithEntries() {
        AppLog.i("Test", "for-zip-package")
        Thread.getDefaultUncaughtExceptionHandler()
            .uncaughtException(Thread.currentThread(), RuntimeException("for-zip-crash"))
        val r = LogPackager.pack(ctx())
        assertNotNull("有日志时应能打包", r)
        assertEquals("默认打包方式应为 zip", "zip", r!!.format)
        assertTrue("包名应为 mbos-logs-*.zip", r.file.name.startsWith("mbos-logs-") && r.file.name.endsWith(".zip"))
        ZipFile(r.file).use { zf ->
            val names = zf.entries().toList().map { it.name }
            assertTrue("应含设备信息清单", names.contains("device-info.txt"))
            assertTrue("应含 logs/ 条目", names.any { it.startsWith("logs/") })
            assertTrue("应含 crashes/ 条目", names.any { it.startsWith("crashes/") })
            val info = zf.getInputStream(zf.getEntry("device-info.txt")).bufferedReader().readText()
            assertTrue("设备信息应含型号", info.contains(android.os.Build.MODEL))
            assertTrue("设备信息应含版本", info.contains(BuildConfig.VERSION_NAME))
            assertFalse("设备信息不得含序列号", info.contains("serial"))
        }
    }

    @Test
    fun logPackager_returnsNullWhenEmpty() {
        AppLog.clear()
        CrashLog.clear()
        assertTrue("无日志时打包应返回 null", LogPackager.pack(ctx()) == null)
    }

    @Test
    fun logPackager_emailBodyContainsSummary() {
        AppLog.i("Test", "body-line")
        Thread.getDefaultUncaughtExceptionHandler()
            .uncaughtException(Thread.currentThread(), RuntimeException("body-crash"))
        val body = LogPackager.emailBody(ctx())
        assertTrue(body.contains("崩溃概况"))
        assertTrue(body.contains("RuntimeException: body-crash"))
        assertTrue(body.contains("日志尾部"))
    }

    // ---------- LogMailer ----------

    @Test
    fun logMailer_emailConstantIsFeedbackAddress() {
        assertEquals("lyy10y_log@126.com", LogMailer.LOG_EMAIL)
    }

    /**
     * FileProvider 授权 + 一键发送全流程。
     * 注意：androidx FileProvider 内部按 authority 静态缓存 PathStrategy，
     * 单元测试里跨测试方法会复用到上一个沙箱的路径根，故所有依赖
     * FileProvider 的断言必须放在同一测试方法内（真机单进程无此问题）。
     */
    @Test
    fun logMailer_fileProviderAuthorityResolves_andSendFlow() {
        AppLog.i("Test", "for-uri")
        val r = LogPackager.pack(ctx())!!
        // authority 与 Manifest 中 ${applicationId}.fileprovider 占位符展开结果必须一致，
        // 否则真机上发送日志会直接抛 IllegalArgumentException
        val uri = androidx.core.content.FileProvider.getUriForFile(
            ctx(), "${ctx().packageName}.fileprovider", r.file
        )
        assertNotNull("FileProvider 应能授权日志包", uri)
        assertTrue(uri.toString().contains(r.file.name))

        // 无邮件客户端的模拟器环境下也不应崩溃（内部 runCatching + Toast 兜底）
        LogMailer.sendByEmail(ctx())
        LogMailer.sharePackage(ctx())
        LogMailer.shareText(ctx())
    }
}

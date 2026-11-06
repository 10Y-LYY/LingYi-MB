/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * 一键发送日志到开发者邮箱。
 *
 * 发送链路：
 *  1. [LogPackager] 打包 zip（或回退 txt）；
 *  2. 经 FileProvider 生成临时授权 URI（无需存储权限）；
 *  3. 唤起邮件客户端（ACTION_SEND + chooser），收件人 / 主题 / 正文 / 附件全部预填，
 *     用户点「发送」即完成反馈；
 *  4. 若设备没有邮件客户端，自动降级为普通分享（微信 / QQ / 蓝牙等），
 *     仍可把日志包送达；纯文本分享作为最终兜底。
 */
object LogMailer {

    /** 日志反馈固定收件箱（开发者邮箱）。 */
    const val LOG_EMAIL = "lyy10y_log@126.com"

    /** 打包并一键发邮件（无日志时 Toast 提示）。 */
    fun sendByEmail(ctx: Context) {
        val pkg = LogPackager.pack(ctx)
        if (pkg == null) {
            Toast.makeText(ctx, "暂无日志可发送", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = runCatching { fileUri(ctx, pkg.file) }.getOrNull()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (pkg.format == "zip") "application/zip" else "text/plain"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(LOG_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject(ctx))
            putExtra(Intent.EXTRA_TEXT, LogPackager.emailBody(ctx))
            if (uri != null) putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (uri == null) {
            // 附件 URI 都拿不到时，至少把正文发出去（正文已含日志尾部）
            AppLog.w("LogMailer", "FileProvider 授权失败，改为发送正文摘要")
        }
        launch(ctx, intent)
    }

    /** 打包后通过系统分享发给任意 App（微信 / QQ / 蓝牙 / 网盘等）。 */
    fun sharePackage(ctx: Context) {
        val pkg = LogPackager.pack(ctx)
        if (pkg == null) {
            Toast.makeText(ctx, "暂无日志可分享", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = runCatching { fileUri(ctx, pkg.file) }.getOrNull()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (pkg.format == "zip") "application/zip" else "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject(ctx))
            putExtra(Intent.EXTRA_TEXT, LogPackager.emailBody(ctx))
            if (uri != null) putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launch(ctx, intent)
    }

    /** 最终兜底：纯文本分享（任何支持文本分享的 App 都能用）。 */
    fun shareText(ctx: Context) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject(ctx))
            putExtra(Intent.EXTRA_TEXT, LogPackager.emailBody(ctx))
        }
        launch(ctx, intent)
    }

    private fun subject(ctx: Context): String =
        "[灵逸 MB 日志] v${com.lingyi.mbos.BuildConfig.VERSION_NAME} · ${android.os.Build.MODEL} · 崩溃 ${CrashLog.count()} 次"

    private fun fileUri(ctx: Context, f: File) =
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)

    /** chooser 包裹后启动；没有任何应用能接也绝不崩溃。 */
    private fun launch(ctx: Context, intent: Intent) {
        val chooser = Intent.createChooser(intent, "发送日志").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { ctx.startActivity(chooser) }
            .onFailure {
                AppLog.e("LogMailer", "唤起分享失败", it)
                Toast.makeText(ctx, "没有可用的发送应用", Toast.LENGTH_SHORT).show()
            }
    }
}

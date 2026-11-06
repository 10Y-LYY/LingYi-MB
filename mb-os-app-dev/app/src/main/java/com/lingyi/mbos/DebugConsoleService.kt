/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.lingyi.mbos.util.AppLog
import java.io.File

/**
 * 全局调试控制台前台服务：持有 [DebugFloatingWindow]（悬浮窗）。
 *
 * 与陀螺仪指针悬浮窗同规格：前台服务 + 持久通知，退出 App 仍可用；
 * 由开发者页「调试控制台」开关启动/停止，状态持久化在 Prefs。
 *
 * 强制崩溃在这里执行：先落一条日志，再延迟抛异常，让 CrashLog
 * 捕获到完整现场（验证「崩溃 → 落盘 → 重启回引导页 → 待处理崩溃提醒」链路）。
 */
class DebugConsoleService : Service() {

    companion object {
        const val NOTIF_ID = 1004
        const val CHANNEL_ID = "debug_console_channel"
        private const val ACTION_STOP = "com.lingyi.mbos.debugconsole.STOP"
        private var instance: DebugConsoleService? = null

        /** 当前是否有调试控制台服务在运行。 */
        fun isRunning(): Boolean = instance != null

        fun start(ctx: Context) {
            val intent = Intent(ctx, DebugConsoleService::class.java)
            runCatching { ContextCompat.startForegroundService(ctx, intent) }
                .onFailure { AppLog.e("DebugConsole", "启动调试控制台失败", it) }
        }

        fun stop(ctx: Context) {
            runCatching { ctx.startService(Intent(ctx, DebugConsoleService::class.java).apply { action = ACTION_STOP }) }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var win: DebugFloatingWindow? = null

    private val callbacks = object : DebugFloatingWindow.Callbacks {
        override fun onOpenDevPage() {
            runCatching {
                startActivity(
                    Intent(this@DebugConsoleService, DeveloperActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure { AppLog.e("DebugConsole", "打开开发者页失败", it) }
        }

        override fun onForceCrash() {
            AppLog.e("DebugConsole", "开发者触发强制崩溃（演练崩溃日志链路）")
            Toast.makeText(this@DebugConsoleService, "1.5 秒后强制崩溃…", Toast.LENGTH_SHORT).show()
            handler.postDelayed({
                throw RuntimeException("DebugConsole 强制崩溃（开发者演练）")
            }, 1500L)
        }

        override fun onStopService() {
            stopSelf()
        }

        override fun onToast(msg: String) {
            Toast.makeText(this@DebugConsoleService, msg, Toast.LENGTH_SHORT).show()
        }

        override fun onShare(path: String) {
            val f = File(path)
            if (!f.exists()) {
                Toast.makeText(this@DebugConsoleService, "文件不存在：$path", Toast.LENGTH_SHORT).show()
                return
            }
            runCatching {
                val uri = FileProvider.getUriForFile(
                    this@DebugConsoleService,
                    "${packageName}.fileprovider",
                    f
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "灵逸 MB 问题上报")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(Intent.createChooser(intent, "分享问题报告").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }.onFailure {
                AppLog.e("DebugConsole", "分享报告失败", it)
                Toast.makeText(this@DebugConsoleService, "分享失败：${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIF_ID,
                    buildNotif(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                @Suppress("DEPRECATION")
                startForeground(NOTIF_ID, buildNotif())
            }
        } catch (se: SecurityException) {
            // Android 15+ 上 FGS 类型权限可能需运行时授权；未授权时降级为非前台服务，
            // 保证调试控制台悬浮窗仍可工作且绝不崩溃。
            AppLog.e("DebugConsole", "前台服务类型授权失败，降级为非前台运行", se)
        }
        // 悬浮窗只是调试入口，创建失败不应让前台服务崩溃
        val themed = android.view.ContextThemeWrapper(this, R.style.Theme_MBOS)
        win = runCatching {
            DebugFloatingWindow(themed, callbacks).also { it.show() }
        }.onFailure { AppLog.e("DebugConsole", "悬浮窗创建失败（服务继续运行）", it) }.getOrNull()
        if (win != null) {
            AppLog.event("调试控制台", "悬浮窗已启动")
        } else if (!android.provider.Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "需要悬浮窗权限：设置 → 应用 → 显示在其他应用上层", Toast.LENGTH_LONG).show()
        }
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.debug_console_notif_channel),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.debug_console_notif_channel)
                    setShowBadge(false)
                }
                mgr.createNotificationChannel(ch)
            }
        }
    }

    private fun buildNotif(): Notification {
        val stop = Intent(this, DebugConsoleService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(
            this, 1, stop,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.debug_console_notif_title))
            .setContentText(getString(R.string.debug_console_notif_text))
            .setSmallIcon(R.drawable.ic_pointer)
            .setOngoing(true)
            .addAction(0, getString(R.string.debug_console_stop), stopPi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        win?.hide()
        win = null
        handler.removeCallbacksAndMessages(null)
        instance = null
        AppLog.event("调试控制台", "服务已停止")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

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
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.lingyi.mbos.ble.ConnectionHub
import com.lingyi.mbos.ble.ConnState
import com.lingyi.mbos.ble.MicrobitConnection
import com.lingyi.mbos.ui.MouseEngine
import com.lingyi.mbos.ui.Protocol
import com.lingyi.mbos.util.AppLog
import com.lingyi.mbos.util.Prefs

/**
 * 陀螺仪指针常驻前台服务。
 *
 * 把原本绑死在 PointerModeActivity 里的「MOUSE 数据循环」搬到这里：
 *  · 进程级单例 ConnectionHub 持有连接，本服务挂监听；
 *  · 连接建立即下发 INFO + MOUSE:ON，断开即停止并隐藏指针圆点；
 *  · 每帧 MOUSE 数据经 MouseEngine（死区 / 平滑 / 曲线 / 按键映射 / 滚动模式）
 *    处理后交给 PointerAccessibilityService 派发系统手势；
 *  · 以 startForeground 常驻（持久通知），退出指针界面甚至退出 App 仍持续运行；
 *  · 配套 TYPE_APPLICATION_OVERLAY 可拖动悬浮控制窗。
 *
 * START_STICKY：被系统回收后尽量重启；重连时 onState 会重新 arm 指针。
 */
class PointerService : Service() {

    companion object {
        const val NOTIF_ID = 1003
        const val CHANNEL_ID = "pointer_channel"
        private const val ACTION_STOP = "com.lingyi.mbos.pointer.STOP"
        private var instance: PointerService? = null

        /** 当前是否有指针服务在运行（控制面板据此切换「启动 / 停止」）。 */
        fun isRunning(): Boolean = instance != null
    }

    private val engine = MouseEngine { Prefs.getPointerProfile(this) }
    private val handler = Handler(Looper.getMainLooper())

    private var connected = false
    private var mouseArmed = false
    private var mouseDataSeen = false
    private var armedAt = 0L
    private var lastDataAt = 0L
    private var floatWin: FloatingPointerWindow? = null

    /** 无障碍服务尚未就绪：先到数据无法派发，需等 onConnected 回调补发。 */
    private var accessibilityMissing = false

    private val listener = object : MicrobitConnection.Listener {
        override fun onState(state: ConnState) {
            val was = connected
            connected = state.connected
            if (connected && !was) {
                armMouse()
            } else if (!connected && was) {
                // 连接 → 断开：停止 micro:bit 输出并隐藏指针圆点
                mouseArmed = false
                runCatching { ConnectionHub.send(Protocol.mouseOff()) }
                PointerAccessibilityService.current()?.setCursorVisible(false)
                floatWin?.setConnected(false)
            } else if (connected) {
                PointerAccessibilityService.current()?.setCursorVisible(true)
                floatWin?.setConnected(true)
            }
            updateNotif()
        }

        override fun onData(data: String) {
            if (data.startsWith("INFO:")) return
            if (!data.startsWith("MOUSE:")) return
            val parts = data.removePrefix("MOUSE:").trim().split(",")
            if (parts.size < 4) return
            val dx = parts[0].toIntOrNull() ?: 0
            val dy = parts[1].toIntOrNull() ?: 0
            val a = parts[2].trim() == "1"
            val b = parts[3].trim() == "1"
            val svc = PointerAccessibilityService.current()
            if (svc == null) {
                // 无障碍服务尚未绑定：暂存缺失标记，等 onConnected 回调接管，
                // 期间不静默丢弃（数据仍在持续到达，服务就绪后即恢复跟随）
                if (!accessibilityMissing) {
                    accessibilityMissing = true
                    // 已开启但实例未就绪 → 启动中（绑定延迟）；否则提示用户去系统设置开启
                    val msg = if (PointerAccessibilityService.isEnabled(this@PointerService)) {
                        getString(R.string.pointer_accessibility_starting)
                    } else {
                        getString(R.string.pointer_accessibility_missing)
                    }
                    floatWin?.setWarn(msg)
                }
                return
            }
            if (accessibilityMissing) {
                accessibilityMissing = false
                floatWin?.setWarn(null)
            }
            mouseDataSeen = true
            lastDataAt = SystemClock.uptimeMillis()
            for (act in engine.consume(dx, dy, a, b)) {
                when (act) {
                    is MouseEngine.Action.Move -> svc.moveBy(act.dx, act.dy)
                    is MouseEngine.Action.Tap -> svc.tap()
                    is MouseEngine.Action.DoubleTap -> svc.doubleTap()
                    is MouseEngine.Action.Finger -> svc.setFingerDown(act.down)
                }
            }
        }
    }

    /** 无障碍服务就绪回调：重建光标并补发 MOUSE 流，使晚连场景也能立即跟随。 */
    private val onAccessibilityReady = {
        if (accessibilityMissing) {
            accessibilityMissing = false
            floatWin?.setWarn(null)
        }
        PointerAccessibilityService.current()?.setCursorVisible(true)
        if (ConnectionHub.isConnected()) armMouse()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 注册无障碍就绪回调（弱持有本服务对象，不阻止服务回收）
        PointerAccessibilityService.onConnected = onAccessibilityReady
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Android 14+ 必须显式声明前台服务类型，否则 startForeground 抛
        // MissingForegroundServiceTypeException 崩溃。本服务与已连接的 micro:bit
        // 外设交互，使用 connectedDevice 类型（需 FOREGROUND_SERVICE_CONNECTED_DEVICE 权限）。
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIF_ID,
                    buildNotif(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                @Suppress("DEPRECATION")
                startForeground(NOTIF_ID, buildNotif())
            }
        } catch (se: SecurityException) {
            // Android 15+ 上 FGS 类型权限可能需运行时授权；未授权时降级为非前台服务，
            // 保证指针服务仍可工作且绝不崩溃。
            AppLog.e("Pointer", "前台服务类型授权失败，降级为非前台运行", se)
        }
        ConnectionHub.addListener(listener)
        connected = ConnectionHub.isConnected()
        if (connected) armMouse()
        // 悬浮窗只是可视化控制，非指针必需：即使创建失败也不应让前台服务崩溃
        floatWin = runCatching {
            FloatingPointerWindow(
                this,
                onStop = { stopSelf() },
                onRecenter = { PointerAccessibilityService.current()?.recenter() },
                onSensDown = { adjustSensitivity(-0.2f) },
                onSensUp = { adjustSensitivity(0.2f) }
            ).also { it.show() }
        }.onFailure { AppLog.e("Pointer", "悬浮控制窗创建失败（不影响指针运行）", it) }.getOrNull()
        floatWin?.setConnected(connected)
        // 启动时无障碍实例已就绪则无需等待回调
        if (PointerAccessibilityService.current() != null) {
            accessibilityMissing = false
            floatWin?.setWarn(null)
        }
        updateNotif()
        handler.post(watchdog)
        return START_STICKY
    }

    /** 连接建立后（重连/进入）开启指针数据流并重置引擎状态。 */
    private fun armMouse() {
        if (!ConnectionHub.isConnected()) return
        ConnectionHub.send(Protocol.info())
        ConnectionHub.send(Protocol.mouseOn())
        mouseArmed = true
        mouseDataSeen = false
        armedAt = SystemClock.uptimeMillis()
        engine.reset()
    }

    private fun adjustSensitivity(delta: Float) {
        val cur = Prefs.getPointerSensitivity(this)
        Prefs.setPointerSensitivity(this, cur + delta)
        updateNotif()
    }

    /** 看门狗：空闲自动回中（仅在开启时）。 */
    private val watchdog = object : Runnable {
        override fun run() {
            if (Prefs.isPointerAutoRecenter(this@PointerService) && mouseDataSeen
                && SystemClock.uptimeMillis() - lastDataAt > 1500
            ) {
                PointerAccessibilityService.current()?.recenter()
                lastDataAt = SystemClock.uptimeMillis()
            }
            handler.postDelayed(this, 500)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.pointer_notif_channel),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.pointer_notif_channel_desc)
                    setShowBadge(false)
                }
                mgr.createNotificationChannel(ch)
            }
        }
    }

    private fun buildNotif(): Notification {
        val open = Intent(this, PointerModeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pi = PendingIntent.getActivity(
            this, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = Intent(this, PointerService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(
            this, 1, stop,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.pointer_notif_title))
            .setContentText(getString(R.string.pointer_notif_text))
            .setSmallIcon(R.drawable.ic_pointer)
            .setContentIntent(pi)
            .setOngoing(true)
            .addAction(0, getString(R.string.pointer_float_stop), stopPi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotif() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { mgr.notify(NOTIF_ID, buildNotif()) }
    }

    override fun onDestroy() {
        handler.removeCallbacks(watchdog)
        runCatching { ConnectionHub.send(Protocol.mouseOff()) }
        ConnectionHub.removeListener(listener)
        floatWin?.hide()
        floatWin = null
        PointerAccessibilityService.current()?.setCursorVisible(false)
        // 解绑就绪回调，避免持有已销毁服务实例
        if (PointerAccessibilityService.onConnected == onAccessibilityReady) {
            PointerAccessibilityService.onConnected = null
        }
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

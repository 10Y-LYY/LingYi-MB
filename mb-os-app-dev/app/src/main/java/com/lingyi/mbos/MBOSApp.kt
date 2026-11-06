/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos

import android.app.Application
import com.lingyi.mbos.util.AppLog
import com.lingyi.mbos.util.CrashLog
import com.lingyi.mbos.util.DebugBus
import com.lingyi.mbos.util.Prefs
import com.lingyi.mbos.util.ThemeManager

/**
 * 灵逸 MB-OS 应用入口。
 * 启动顺序：夜间模式 → 日志系统（AppLog）→ 崩溃捕获（CrashLog）。
 *
 * 日志与崩溃机制：
 *  · AppLog：全 App 统一日志（分级 / 按天滚动 / 内存缓冲），任何位置一行调用；
 *  · CrashLog：未捕获异常落盘到 crashes/（含异常链与崩溃前日志尾部），
 *    再交还系统默认行为（杀进程 → 系统重启回引导页），保证既有恢复机制不变；
 *  · 引导页启动时检测「待处理崩溃」，弹窗引导用户一键发邮件反馈。
 */
class MBOSApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 启动即应用用户的主题模式（跟随系统 / 浅色 / 深色）
        ThemeManager.applyNightMode(this)
        // 日志系统先就绪，崩溃处理器才能把「崩溃前日志尾部」写进崩溃文件
        AppLog.init(this)
        CrashLog.init(this)
        // 恢复「协议抓包」开关状态（悬浮窗调试控制台依赖 DebugBus 常驻打点）
        DebugBus.setCaptureEnabled(Prefs.isPacketCapture(this))
        AppLog.event("冷启动", "进程 pid=${android.os.Process.myPid()} · 版本=${com.lingyi.mbos.BuildConfig.VERSION_NAME}")
    }
}

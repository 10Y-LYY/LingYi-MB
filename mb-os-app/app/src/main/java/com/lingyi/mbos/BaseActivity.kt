/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.lingyi.mbos.util.ActionTrail
import com.lingyi.mbos.util.ActivityTopHolder
import com.lingyi.mbos.util.AnrWatchdog
import com.lingyi.mbos.util.AppLog
import com.lingyi.mbos.util.Prefs
import com.lingyi.mbos.util.ThemeManager

/**
 * 全部页面的基类：统一在 super.onCreate() 之前应用用户主题
 * （配色覆盖层 + 动态取色）、屏幕常亮设置与窗口转场动画。
 *
 * 主题必须在 super.onCreate() 之前套好（窗口装饰在此之前创建）。
 *
 * 主题切换的全局一致性：设置页修改主题后会 bump「主题纪元」，
 * 返回栈里的旧页面在 onResume 时发现纪元与创建时不一致即自我重建，
 * 避免「设置页新配色 + 主页面旧配色」的文字与主题不匹配。
 *
 * 过渡动画遵循设置页开关（默认开）：窗口转场为 M3 shared-axis 风格，
 * 组件显隐过渡见 [com.lingyi.mbos.ui.Transitions]；关闭时全部立即到位。
 */
abstract class BaseActivity : AppCompatActivity() {

    /** 本页面创建时可见的主题纪元；onResume 发现变化即重建。 */
    private var themeEpochAtCreate = 0L

    /**
     * ANR 看门狗心跳：页面在前台期间每秒在主线程打一次点。
     * 主线程被阻塞时心跳停摆，看门狗据此判定卡顿/ANR 并 dump 现场
     * （v4.2 修复：此前 beat() 全工程无调用点，开关一开就每秒误报）。
     */
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private val heartbeat = object : Runnable {
        override fun run() {
            AnrWatchdog.beat()
            heartbeatHandler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        themeEpochAtCreate = Prefs.getThemeEpoch(this)
        applyWindowAnimations()
        AppLog.event("页面创建", javaClass.simpleName)
        // 操作轨迹：问题上报时还原「用户当时在哪个页面」
        ActionTrail.record("进入页面", javaClass.simpleName)
        runCatching {
            if (ThemeManager.isKeepScreenOn(this)) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }.onFailure { AppLog.e("BaseActivity", "屏幕常亮设置失败（${javaClass.simpleName}）", it) }
    }

    /**
     * 全屏触摸打点：每次抬手记录「点了哪个控件」（坐标回打视图树识别）。
     * 零侵入——不替换任何控件自己的监听器，只做旁观记录；
     * 供悬浮窗「问题上报」与「界面元素树」提供上下文。
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        runCatching { ActionTrail.captureTouch(this, ev) }
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        ActivityTopHolder.top = this
        // 主题（模式 / 配色）在设置页被修改后，返回栈里的旧页面不会自动重建，
        // 这里用纪元差自我重建，保证任何界面回到前台时都是当前主题。
        if (Prefs.getThemeEpoch(this) != themeEpochAtCreate) {
            recreate()
        }
        heartbeatHandler.removeCallbacks(heartbeat)
        if (AnrWatchdog.isEnabled(this)) heartbeatHandler.post(heartbeat)
    }

    override fun onPause() {
        super.onPause()
        heartbeatHandler.removeCallbacks(heartbeat)
        if (ActivityTopHolder.top === this) ActivityTopHolder.top = null
    }

    /** 按偏好套用 / 停用窗口转场动画（设置页切换开关时也会调用）。 */
    protected fun applyWindowAnimations() {
        runCatching {
            window.setWindowAnimations(
                if (Prefs.isAnimationEnabled(this)) {
                    com.lingyi.mbos.R.style.WindowAnimation_MBOS
                } else {
                    com.lingyi.mbos.R.style.WindowAnimation_MBOS_None
                }
            )
        }.onFailure { AppLog.w("BaseActivity", "窗口转场动画应用失败", it) }
    }
}

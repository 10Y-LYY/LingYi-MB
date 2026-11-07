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
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import com.google.android.material.card.MaterialCardView
import com.lingyi.mbos.util.ActionTrail
import com.lingyi.mbos.util.ActivityTopHolder
import com.lingyi.mbos.util.AnrWatchdog
import com.lingyi.mbos.util.AppLog
import com.lingyi.mbos.util.Prefs
import com.lingyi.mbos.util.ThemeManager
import com.lingyi.mbos.util.voyageStyle

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
        applyContentEnterAnimation()
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
                when {
                    // 「界面过渡动画」总开关优先
                    !Prefs.isAnimationEnabled(this) -> com.lingyi.mbos.R.style.WindowAnimation_MBOS_None
                    // Voyage 风格：300ms 柔和微缩放 + 轻滑
                    voyageStyle() -> com.lingyi.mbos.R.style.WindowAnimation_MBOS_Voyage
                    else -> com.lingyi.mbos.R.style.WindowAnimation_MBOS
                }
            )
        }.onFailure { AppLog.w("BaseActivity", "窗口转场动画应用失败", it) }
    }

    /**
     * 二级页过渡统一升级：分区标题与卡片按顺序错落淡入 + 轻微上滑。
     * 仅预览版开启 Voyage 风格时生效，并遵循设置里的「界面过渡动画」总开关；
     * 主界面（Voyage 皮肤自带骨架屏入场）与引导页跳过，避免重复动画。
     */
    private fun applyContentEnterAnimation() {
        if (!voyageStyle() || !Prefs.isAnimationEnabled(this)) return
        if (this is MainActivity || this is GuideActivity) return
        val content = findViewById<ViewGroup>(android.R.id.content) ?: return
        // 子类的 setContentView 在 super.onCreate 之后才执行，延后到下一帧再找内容列
        content.post {
            if (isFinishing || isDestroyed) return@post
            val root = content.getChildAt(0) ?: return@post
            // XML 页面卡片的圆角/描边写在 style 里，主题覆盖层改不到，运行时统一升级
            applyVoyageCardStyling(root)
            val column = findScrollContent(root) ?: return@post
            val shift = 14f * resources.displayMetrics.density
            for (i in 0 until column.childCount) {
                val child = column.getChildAt(i) ?: continue
                child.alpha = 0f
                child.translationY = shift
                child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(30L + i * 36L)
                    .setDuration(280L)
                    .setInterpolator(DecelerateInterpolator(1.5f))
                    .start()
            }
        }
    }

    /**
     * 二级页排版统一：把 XML 页面卡片的圆角提到 26dp、去掉 1dp 描边。
     * 卡片与页面底的层次由 Voyage 主题覆盖层提供（白卡浮在浅灰绿底上），
     * 此处只处理 style 写死、主题无法覆盖的形状项。
     */
    private fun applyVoyageCardStyling(v: View) {
        if (v is MaterialCardView) {
            v.radius = 26f * resources.displayMetrics.density
            v.strokeWidth = 0
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) applyVoyageCardStyling(v.getChildAt(i))
        }
    }

    /** 找到页面里承载分区标题 / 卡片的滚动内容列。 */
    private fun findScrollContent(v: View): ViewGroup? {
        if ((v is ScrollView || v is NestedScrollView) && v is ViewGroup && v.childCount > 0) {
            return v.getChildAt(0) as? ViewGroup
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                val found = findScrollContent(v.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }
}

/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import com.lingyi.mbos.util.Prefs

/**
 * 组件级过渡动画：显隐淡入淡出 + M3 Expressive 弹簧入场。
 * 遵循设置页「界面过渡动画」开关（默认开），关闭时立即到位、不播动画。
 * 页面级转场由 BaseActivity 的窗口动画负责（M3 shared-axis）。
 */
object Transitions {

    private const val CROSS_MS = 150L
    private const val RISE_DP = 8f
    private const val SPRING_MS = 280L

    /**
     * 显隐过渡：显示时淡入 + 轻微上浮到位，隐藏时淡出。
     * 动画的最终 visibility 由本方法设置（隐藏侧在动画结束时生效），避免状态错位。
     */
    fun crossFade(view: View, visible: Boolean) {
        if (view.visibility == if (visible) View.VISIBLE else View.GONE) return
        if (!Prefs.isAnimationEnabled(view.context)) {
            view.visibility = if (visible) View.VISIBLE else View.GONE
            return
        }
        runCatching {
            if (visible) {
                view.visibility = View.VISIBLE
                view.alpha = 0f
                view.translationY = RISE_DP * view.resources.displayMetrics.density
                view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(CROSS_MS)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            } else {
                view.animate()
                    .alpha(0f)
                    .setDuration(CROSS_MS)
                    .withEndAction { view.visibility = View.GONE }
                    .start()
            }
        }
    }

    /**
     * M3 Expressive 弹簧入场：缩放 0.92→1 + 淡入，Overshoot 回弹。
     * 用于新页面卡片瀑布 / 新露出的工具入口，让界面「长出来」而不是「跳出来」。
     */
    fun springIn(view: View, delayMs: Long = 0L) {
        if (!Prefs.isAnimationEnabled(view.context)) return
        runCatching {
            view.alpha = 0f
            view.scaleX = 0.92f
            view.scaleY = 0.92f
            view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(delayMs)
                .setDuration(SPRING_MS)
                .setInterpolator(OvershootInterpolator(1.4f))
                .start()
        }
    }

    /** 容器内直接子 View 依次弹簧入场（卡片瀑布流，[staggerMs] 为每张卡的间隔）。 */
    fun springInCards(container: ViewGroup, staggerMs: Long = 50L) {
        if (!Prefs.isAnimationEnabled(container.context)) return
        runCatching {
            for (i in 0 until container.childCount) {
                springIn(container.getChildAt(i), i * staggerMs)
            }
        }
    }
}

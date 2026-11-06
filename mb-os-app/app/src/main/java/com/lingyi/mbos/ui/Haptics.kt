/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

import android.view.HapticFeedbackConstants
import android.view.View
import com.lingyi.mbos.util.ThemeManager

/**
 * 触感反馈：遵循设置页开关（默认开）。
 * 参考系统「按压即有反馈」的交互理念，给关键点击补上触觉一层。
 */
object Haptics {

    fun tick(view: View) {
        if (!ThemeManager.isHapticEnabled(view.context)) return
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** 带触感反馈的点击监听（在执行业务回调前给出反馈）。 */
    fun View.setOnClickWithHaptic(action: () -> Unit) {
        setOnClickListener {
            tick(it)
            action()
        }
    }
}

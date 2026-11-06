/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.android.material.R as MR
import com.lingyi.mbos.util.themeColor

/**
 * 模拟器悬浮窗里的 5×5 LED 微预览：按 SimulatorConnection.Snapshot 的
 * 亮度矩阵绘制圆点，验证 SHOW / PLOT / CLEAR 指令的真实效果。
 */
class SimulatorLedView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val onPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.themeColor(MR.attr.colorPrimary)
    }
    private val offPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.themeColor(MR.attr.colorOutlineVariant)
    }

    /** 5×5 亮度矩阵（0–9）。 */
    private var led: Array<IntArray> = Array(5) { IntArray(5) }

    fun setMatrix(m: Array<IntArray>) {
        led = m
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cell = minOf(w, h) / 5f
        val r = cell * 0.32f
        val ox = (w - cell * 5) / 2f
        val oy = (h - cell * 5) / 2f
        for (y in 0 until 5) {
            for (x in 0 until 5) {
                val v = led[y][x]
                val paint = if (v > 0) onPaint else offPaint
                paint.alpha = if (v > 0) (60 + v * 19).coerceIn(0, 255) else 255
                canvas.drawCircle(
                    ox + cell * x + cell / 2f,
                    oy + cell * y + cell / 2f,
                    r,
                    paint
                )
            }
        }
        onPaint.alpha = 255
        offPaint.alpha = 255
    }
}

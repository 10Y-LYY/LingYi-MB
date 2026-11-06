/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * 顶部大图：micro:bit V2 开发板。
 * 纯 Canvas 绘制，支持设置 5×5 LED 矩阵与 A/B 按键状态，
 * 下发指令时画面上的 LED 会同步点亮，增强反馈感。
 */
class MicrobitView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var matrix = Array(5) { BooleanArray(5) }
    private var btnA = false
    private var btnB = false

    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1B1F2A")
    }
    private val ledOnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F2A900")
    }
    private val ledOffPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E9ECF1")
    }
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F2A900")
    }
    private val btnOnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
    }

    fun setMatrix(m: Array<BooleanArray>) {
        this.matrix = m
        invalidate()
    }

    fun setPixel(x: Int, y: Int, on: Boolean) {
        if (x in 0..4 && y in 0..4) {
            matrix[y][x] = on
            invalidate()
        }
    }

    fun clear() {
        for (row in matrix) row.fill(false)
        invalidate()
    }

    fun setButtons(a: Boolean, b: Boolean) {
        btnA = a
        btnB = b
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // 板子
        val pad = w * 0.05f
        val r = w * 0.07f
        canvas.drawRoundRect(RectF(pad, pad, w - pad, h - pad), r, r, boardPaint)

        // LED 矩阵
        val matTop = h * 0.20f
        val matSize = w * 0.60f
        val cell = matSize / 5f
        val startX = (w - matSize) / 2f
        for (y in 0..4) {
            for (x in 0..4) {
                val cx = startX + cell * x + cell / 2f
                val cy = matTop + cell * y + cell / 2f
                canvas.drawCircle(cx, cy, cell * 0.33f, if (matrix[y][x]) ledOnPaint else ledOffPaint)
            }
        }

        // A / B 按键
        val by = h * 0.88f
        val br = w * 0.055f
        canvas.drawCircle(w * 0.33f, by, br, if (btnA) btnOnPaint else btnPaint)
        canvas.drawCircle(w * 0.67f, by, br, if (btnB) btnOnPaint else btnPaint)
    }
}
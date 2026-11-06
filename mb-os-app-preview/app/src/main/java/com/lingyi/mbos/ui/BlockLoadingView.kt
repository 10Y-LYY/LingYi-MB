/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.google.android.material.R as MR
import com.lingyi.mbos.util.themeColor

/**
 * 原生 Material 3 风格的方块加载指示：一排圆角方块以主题主色令牌（colorPrimary）错峰缩放 +
 * 淡入淡出，形成「方块的图形不断变化」的循环动画。可直接放进任意布局，或经 BlockLoadingDialog
 * 作为全屏加载界面使用。颜色始终跟随当前主题（浅色/深色/各动态配色），无需硬编码。
 */
class BlockLoadingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var blockCount = 4
    private var blockSize = 0
    private var gap = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()
    private var phase = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1400
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        val density = resources.displayMetrics.density
        blockSize = (14 * density).toInt()
        gap = (10 * density).toInt()
        paint.color = resolveColor()
    }

    private fun resolveColor(): Int =
        runCatching { context.themeColor(MR.attr.colorPrimary) }.getOrDefault(0xFF6750A4.toInt())

    /** 设置方块数量（2–8），改变后自动重测尺寸。 */
    fun setBlockCount(n: Int) {
        blockCount = n.coerceIn(2, 8)
        requestLayout()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 主题切换后重新取色，保证加载界面跟随动态配色
        paint.color = resolveColor()
        if (!animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val totalW = blockCount * blockSize + (blockCount - 1) * gap
        val minH = (28 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(
            resolveSize(totalW, widthSpec),
            resolveSize(minH, heightSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val totalW = blockCount * blockSize + (blockCount - 1) * gap
        val startX = (width - totalW) / 2f
        val cy = height / 2f
        for (i in 0 until blockCount) {
            // 每个方块相位错开 1/blockCount，整体形成波浪流动
            val p = (phase + i.toFloat() / blockCount) % 1f
            val wave = 0.5f + 0.5f * Math.sin((p * Math.PI * 2).toDouble()).toFloat()
            val scale = 0.45f + 0.55f * wave
            val size = blockSize * scale
            val x = startX + i * (blockSize + gap) + (blockSize - size) / 2f
            val y = cy - size / 2f
            rect.set(x, y, x + size, y + size)
            paint.alpha = (60 + 195 * wave).toInt().coerceIn(0, 255)
            val radius = size / 3.2f
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
    }
}

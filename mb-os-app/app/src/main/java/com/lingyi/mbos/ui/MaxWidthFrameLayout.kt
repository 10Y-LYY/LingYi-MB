/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.lingyi.mbos.R

/**
 * 内容宽度封顶容器（Material 3 大屏规范）：
 *
 * 折叠屏内屏 / 平板等超宽屏幕上，若让内容直接铺满全宽，卡片与正文会被拉成
 * 难以阅读的长条。本容器把子视图的测量宽度限制在 [maxWidth]（默认 840dp，
 * 与 M3「expanded 档单列内容上限」一致），剩余宽度由两侧留白吸收；
 * 子视图配合 `android:layout_gravity="center_horizontal"` 自动居中。
 *
 * 手机等窄屏（可用宽度 < maxWidth）行为与普通 FrameLayout 完全一致，零副作用。
 */
class MaxWidthFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var maxWidthPx: Int

    init {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.MaxWidthFrameLayout)
        maxWidthPx = ta.getDimensionPixelSize(R.styleable.MaxWidthFrameLayout_maxWidth, 0)
        ta.recycle()
    }

    /** 编程式设置内容宽度上限（px）；<=0 表示不封顶。 */
    fun setMaxWidth(px: Int) {
        maxWidthPx = px
        requestLayout()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        if (maxWidthPx > 0) {
            val available = MeasureSpec.getSize(widthSpec)
            if (available > maxWidthPx) {
                val mode = MeasureSpec.getMode(widthSpec)
                super.onMeasure(MeasureSpec.makeMeasureSpec(maxWidthPx, mode), heightSpec)
                // 容器自身仍撑满父宽度（封顶只作用于子内容，居中由子视图 layout_gravity 完成）。
                // EXACTLY：恢复父给的可用全宽；其余模式（AT_MOST 等）：至少保留封顶宽度，
                // 绝不允许容器窄于已测量的子内容——那会导致内容被裁切/错位（引导页事故根因之一）。
                val selfWidth = if (mode == MeasureSpec.EXACTLY) {
                    available
                } else {
                    maxOf(maxWidthPx, measuredWidth)
                }
                setMeasuredDimension(selfWidth, measuredHeight)
                return
            }
        }
        super.onMeasure(widthSpec, heightSpec)
    }
}

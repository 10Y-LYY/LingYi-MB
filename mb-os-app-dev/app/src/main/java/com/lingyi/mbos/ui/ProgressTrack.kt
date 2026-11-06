package com.lingyi.mbos.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.google.android.material.R as MR
import com.lingyi.mbos.util.themeColor

/**
 * M3 线性进度轨：轨道 + 填充条，收敛控制台 4 处手搓的 FrameLayout track+fill。
 *
 * - 轨道 SurfaceVariant 纯色，填充默认 Primary（可换 Error 等），全 Pill 圆角（随高度自适应）
 * - 填充比例 0..1，布局未完成时缓存，onSizeChanged 自动重放，无需调用方 post
 */
class ProgressTrack @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val trackBg = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(context.themeColor(MR.attr.colorSurfaceVariant))
    }

    private val fillBg = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(context.themeColor(MR.attr.colorPrimary))
    }

    private val fill = View(context).apply { background = fillBg }

    /** 当前填充比例 0..1 */
    var fraction: Float = 0f
        private set

    init {
        background = trackBg
        addView(fill, LayoutParams(0, LayoutParams.MATCH_PARENT))
    }

    /** 换填充色（属性形式，如 [MR.attr.colorError]） */
    fun setFillColorAttr(attr: Int) {
        fillBg.setColor(context.themeColor(attr))
    }

    /** 设置填充比例，越界自动钳制；宽度未布局时缓存，布局后自动生效 */
    fun setFraction(f: Float) {
        fraction = f.coerceIn(0f, 1f)
        applyFraction()
    }

    private fun applyFraction() {
        val w = width
        if (w <= 0) return
        val lp = fill.layoutParams
        lp.width = (w * fraction).toInt().coerceIn(0, w)
        fill.layoutParams = lp
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h > 0 && h != oldh) {
            val r = h / 2f
            trackBg.cornerRadius = r
            fillBg.cornerRadius = r
        }
        applyFraction()
    }
}

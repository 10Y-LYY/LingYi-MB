/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

import android.content.Context
import android.os.Handler
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.google.android.material.R as MR
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.lingyi.mbos.R
import com.lingyi.mbos.util.themeColor
import com.lingyi.mbos.util.voyageStyle

/**
 * M3 Expressive 设计系统——全 App 唯一一套程序化组件库。
 *
 * 设计基线（对齐 Google Play 商店风格）：
 * - 形状：卡片 20dp 大圆角、内部容器 12dp、徽标/按钮全圆角（pill）
 * - 表面：卡片 colorSurface 纯色无描边，靠「页面 ContainerLow / 卡片 Surface」的
 *    tonal 色阶分层，不用阴影不用边框
 * - 强调：主色容器卡（colorPrimaryContainer）仅用于唯一主 CTA / hero 卡
 * - 按钮：全宽 52dp、行内 40dp、20dp 圆角、长文案两行不裁切
 * - 动效：按压回弹（scale 0.965 + Overshoot 回弹）、弹簧入场
 *
 * 使用纪律：任何 Activity / Service / 对话框里程序化构建的界面必须走本文件，
 * 不允许各页面再自建 dp()/卡片/按钮助手（v4.2 已清退 ConsoleExtras、
 * DebugFloatingWindow 等四套并行工具箱，只留此一套）。
 *
 * 主题注意：MaterialCardView / MaterialButton / TextInputLayout 构造时会做
 * Material 主题强校验，Service 上下文请先用
 * `ContextThemeWrapper(service, R.style.Theme_MBOS)` 包装（参考 ui/ThemeExt.kt）。
 */
object M3Kit {

    // ---------- 基础换算（全 App 唯一 dp 入口） ----------

    fun dp(ctx: Context, v: Float): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    /** 整型重载：`dp(ctx, 16)` 与 `dp(ctx, 16f)` 等价，方便调用方少写 f 后缀。 */
    fun dp(ctx: Context, v: Int): Int = dp(ctx, v.toFloat())

    // ---------- 形状令牌（M3 Expressive shape scale） ----------

    const val SHAPE_CARD = 20f          // 页面卡片（大圆角，Expressive 基线）
    const val SHAPE_CARD_MEDIUM = 16f   // 次级卡片 / 嵌套容器
    const val SHAPE_INNER = 12f         // 卡内块（等宽区、输入框背景）
    const val SHAPE_PILL = 999f         // 徽标 / 按钮全圆角

    // ---------- 间距令牌 ----------

    const val SP_2 = 2f
    const val SP_4 = 4f
    const val SP_8 = 8f
    const val SP_10 = 10f
    const val SP_12 = 12f
    const val SP_14 = 14f
    const val SP_16 = 16f
    const val SP_24 = 24f
    const val SP_28 = 28f

    // ---------- 尺寸令牌 ----------

    const val BTN_HEIGHT = 52f          // 全宽按钮
    const val BTN_HEIGHT_INLINE = 40f   // 行内按钮
    const val ICON_CIRCLE = 46f         // 圆形图标容器
    const val ICON_SIZE = 24f           // 容器内矢量图标

    // ---------- 界面风格排版令牌（Voyage 风格 / 稳定版 M3） ----------

    /**
     * 二级页排版统一升级的唯一定点：Voyage 风格下采用「纯白卡片浮在浅灰绿底上」、
     * 更大圆角与轻微浮起阴影、更大更醒目的分区标题；切回稳定版 M3 时全部回到原参数。
     * 所有程序化二级页 / 弹窗都走本组件库，故一处改动即全量生效。
     */
    private fun Context.cardRadiusDp(): Float = if (voyageStyle()) 26f else SHAPE_CARD
    private fun Context.cardElevationDp(): Float = if (voyageStyle()) 2f else 0f
    private fun Context.cardBodyPaddingDp(): Float = if (voyageStyle()) 18f else SP_16

    // ---------- 分区标题（卡片外） ----------

    fun sectionHeader(ctx: Context, text: String): TextView =
        TextView(ctx).apply {
            this.text = text
            val voyage = ctx.voyageStyle()
            setTextAppearance(
                if (voyage) MR.attr.textAppearanceTitleLarge
                else MR.attr.textAppearanceTitleMedium
            )
            setTextColor(
                if (voyage) ctx.themeColor(MR.attr.colorPrimary)
                else ctx.themeColor(MR.attr.colorOnSurface)
            )
            setTypeface(null, android.graphics.Typeface.BOLD)
            val m = dp(ctx, if (voyage) SP_12 else SP_8)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = m; bottomMargin = m }
        }

    fun sectionHeader(ctx: Context, @StringRes res: Int): TextView =
        sectionHeader(ctx, ctx.getString(res))

    // ---------- 页面卡片（Play 商店式 tonal 分层，无描边） ----------

    /**
     * 页面内容卡：20dp 大圆角、colorSurface 底、零 elevation、无描边。
     * 与页面根（colorSurfaceContainerLow）形成 tonal 分层。
     */
    fun pageCard(ctx: Context, marginTopDp: Float = SP_12): MaterialCardView =
        MaterialCardView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(ctx, marginTopDp) }
            setCardBackgroundColor(ctx.themeColor(MR.attr.colorSurface))
            radius = dp(ctx, ctx.cardRadiusDp()).toFloat()
            cardElevation = dp(ctx, ctx.cardElevationDp()).toFloat()
            strokeWidth = 0
        }

    /** 次级卡（tonal）：colorSurfaceContainerHigh 底，用于卡中分组。 */
    fun tonalCard(ctx: Context, marginTopDp: Float = SP_8): MaterialCardView =
        MaterialCardView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(ctx, marginTopDp) }
            setCardBackgroundColor(ctx.themeColor(MR.attr.colorSurfaceContainerHigh))
            radius = dp(ctx, if (ctx.voyageStyle()) 20f else SHAPE_CARD).toFloat()
            cardElevation = 0f
            strokeWidth = 0
        }

    /** 主 CTA / hero 卡：主色容器底。每页至多一张。 */
    fun accentCard(ctx: Context, marginTopDp: Float = SP_12): MaterialCardView =
        MaterialCardView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(ctx, marginTopDp) }
            setCardBackgroundColor(ctx.themeColor(MR.attr.colorPrimaryContainer))
            radius = dp(ctx, ctx.cardRadiusDp()).toFloat()
            cardElevation = dp(ctx, ctx.cardElevationDp()).toFloat()
            strokeWidth = 0
        }

    /** 卡片内部纵向容器（Voyage 风格下 18dp，M3 下 16dp 内边距）。 */
    fun cardBody(ctx: Context): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(ctx, ctx.cardBodyPaddingDp())
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

    /** 便捷：新建一张卡并返回（卡, 卡内容器）。 */
    fun card(ctx: Context, marginTopDp: Float = SP_12): Pair<MaterialCardView, LinearLayout> {
        val c = pageCard(ctx, marginTopDp)
        val body = cardBody(ctx)
        c.addView(body)
        return c to body
    }

    // ---------- 卡片内文本 ----------

    fun cardTitle(ctx: Context, text: String): TextView =
        TextView(ctx).apply {
            this.text = text
            setTextAppearance(MR.attr.textAppearanceTitleSmall)
            setTextColor(ctx.themeColor(MR.attr.colorOnSurface))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

    fun cardDesc(ctx: Context, text: String): TextView =
        TextView(ctx).apply {
            this.text = text
            setTextAppearance(MR.attr.textAppearanceBodySmall)
            setTextColor(ctx.themeColor(MR.attr.colorOnSurfaceVariant))
            setLineSpacing(dp(ctx, 3f).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(ctx, SP_2) }
        }

    /** 卡片内状态/数值文本（LabelLarge，随 [good] 着主色/常规色）。 */
    fun statusText(ctx: Context, text: String, good: Boolean? = null): TextView =
        TextView(ctx).apply {
            this.text = text
            setTextAppearance(MR.attr.textAppearanceLabelLarge)
            setTextColor(
                ctx.themeColor(
                    when (good) {
                        true -> MR.attr.colorPrimary
                        false -> MR.attr.colorError
                        else -> MR.attr.colorOnSurfaceVariant
                    }
                )
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(ctx, SP_10) }
        }

    // ---------- 行内信息行（左标签右值，参考 item_info_row.xml） ----------

    fun infoRow(ctx: Context, label: String, value: String, onValueClick: (() -> Unit)? = null): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val p = dp(ctx, SP_16)
            setPadding(p, dp(ctx, SP_12), p, dp(ctx, SP_12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val tvLabel = TextView(ctx).apply {
            text = label
            setTextAppearance(MR.attr.textAppearanceBodyMedium)
            setTextColor(ctx.themeColor(MR.attr.colorOnSurfaceVariant))
        }
        val tvValue = TextView(ctx).apply {
            text = value
            setTextAppearance(MR.attr.textAppearanceBodyMedium)
            setTextColor(ctx.themeColor(MR.attr.colorOnSurface))
            setTypeface(null, android.graphics.Typeface.BOLD)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            if (onValueClick != null) {
                setOnClickListener { onValueClick() }
            }
        }
        row.addView(tvLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(tvValue)
        return row
    }

    // ---------- 按钮（四档单一工厂，M3 Expressive 20dp 圆角） ----------

    enum class ButtonStyle { FILLED, TONAL, OUTLINED, TEXT }

    /**
     * 全宽按钮：52dp 高、20dp 圆角、长文案两行不裁切（maxLines=2 + ellipsize）。
     * [style] 决定填充方式；FILLED 每屏至多一个（主 CTA）。
     */
    fun button(
        ctx: Context,
        text: String,
        style: ButtonStyle = ButtonStyle.TONAL,
        onClick: () -> Unit
    ): MaterialButton = baseButton(ctx, text, onClick).apply {
        when (style) {
            ButtonStyle.FILLED -> {
                setBackgroundColor(ctx.themeColor(MR.attr.colorPrimary))
                setTextColor(ctx.themeColor(MR.attr.colorOnPrimary))
            }
            ButtonStyle.TONAL -> {
                setBackgroundColor(ctx.themeColor(MR.attr.colorSecondaryContainer))
                setTextColor(ctx.themeColor(MR.attr.colorOnSecondaryContainer))
            }
            ButtonStyle.OUTLINED -> {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setTextColor(ctx.themeColor(MR.attr.colorPrimary))
                strokeWidth = dp(ctx, 1f)
                strokeColor = android.content.res.ColorStateList.valueOf(ctx.themeColor(MR.attr.colorOutline))
            }
            ButtonStyle.TEXT -> {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setTextColor(ctx.themeColor(MR.attr.colorPrimary))
            }
        }
        minHeight = dp(ctx, BTN_HEIGHT)
        minimumHeight = dp(ctx, BTN_HEIGHT)
        cornerRadius = dp(ctx, SHAPE_CARD)
        textSize = 15f
        letterSpacing = 0f
    }

    /** 行内等宽小按钮（高度 40dp、20dp 圆角、宽度自适应）。 */
    fun inlineTextButton(ctx: Context, text: String, onClick: () -> Unit): MaterialButton =
        baseButton(ctx, text, onClick).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setTextColor(ctx.themeColor(MR.attr.colorPrimary))
            strokeWidth = dp(ctx, 1f)
            strokeColor = android.content.res.ColorStateList.valueOf(ctx.themeColor(MR.attr.colorOutline))
            height = dp(ctx, BTN_HEIGHT_INLINE)
            minHeight = dp(ctx, BTN_HEIGHT_INLINE)
            minimumHeight = dp(ctx, BTN_HEIGHT_INLINE)
            minWidth = 0
            minimumWidth = 0
            insetTop = 0
            insetBottom = 0
            cornerRadius = dp(ctx, SHAPE_CARD)
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

    /** 横向按钮排（两个按钮平分宽度，间距 8dp）。 */
    fun buttonRow(ctx: Context, left: View, right: View): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(ctx, SP_12) }
        }
        row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(ctx, SP_8)
        })
        return row
    }

    private fun baseButton(ctx: Context, text: String, onClick: () -> Unit): MaterialButton =
        MaterialButton(ctx).apply {
            this.text = text
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            isAllCaps = false
            setOnClickListener { onClick() }
        }

    // ---------- 圆形图标容器（M3 Expressive） ----------

    /**
     * 46dp 圆形图标容器：主色容器底 + 24dp 单色矢量图标。
     * [bgAttr]/[fgAttr] 可换 tonal（colorSurfaceContainerHighest / colorOnSurfaceVariant）。
     */
    fun iconCircle(
        ctx: Context,
        @DrawableRes icon: Int,
        bgAttr: Int = MR.attr.colorPrimaryContainer,
        fgAttr: Int = MR.attr.colorOnPrimaryContainer,
        sizeDp: Float = ICON_CIRCLE
    ): android.widget.FrameLayout {
        val p = dp(ctx, sizeDp)
        val wrap = android.widget.FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(p, p)
            background = MaterialShapeDrawable().apply {
                fillColor = android.content.res.ColorStateList.valueOf(ctx.themeColor(bgAttr))
                shapeAppearanceModel = ShapeAppearanceModel.builder()
                    .setAllCornerSizes(p / 2f)
                    .build()
            }
        }
        val iv = androidx.appcompat.widget.AppCompatImageView(ctx).apply {
            setImageResource(icon)
            val s = dp(ctx, ICON_SIZE)
            layoutParams = android.widget.FrameLayout.LayoutParams(s, s, Gravity.CENTER)
            imageTintList = android.content.res.ColorStateList.valueOf(ctx.themeColor(fgAttr))
        }
        wrap.addView(iv)
        return wrap
    }

    // ---------- 行式工具卡（图标容器 + 标题 + 描述 + 箭头） ----------

    /**
     * 行式条目卡：46dp 圆图标容器 + 标题 + 描述 + 右箭头，整卡可点。
     * 全 App 行式条目（稳定性工具、设置行、关于页入口）统一走此件。
     */
    fun toolRow(
        ctx: Context,
        @DrawableRes icon: Int,
        title: String,
        desc: String,
        onClick: () -> Unit
    ): MaterialCardView {
        val card = MaterialCardView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(ctx, SP_8) }
            radius = dp(ctx, ctx.cardRadiusDp()).toFloat()
            cardElevation = dp(ctx, ctx.cardElevationDp()).toFloat()
            setCardBackgroundColor(ctx.themeColor(MR.attr.colorSurface))
            strokeWidth = 0
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val p = dp(ctx, SP_14)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        row.addView(iconCircle(ctx, icon))
        val texts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(ctx, SP_14)
            }
        }
        texts.addView(TextView(ctx).apply {
            this.text = title
            setTextAppearance(MR.attr.textAppearanceTitleSmall)
            setTextColor(ctx.themeColor(MR.attr.colorOnSurface))
            setTypeface(null, android.graphics.Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        texts.addView(TextView(ctx).apply {
            this.text = desc
            setTextAppearance(MR.attr.textAppearanceBodySmall)
            setTextColor(ctx.themeColor(MR.attr.colorOnSurfaceVariant))
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        row.addView(texts)
        row.addView(androidx.appcompat.widget.AppCompatImageView(ctx).apply {
            setImageResource(R.drawable.ic_chevron_right)
            val s = dp(ctx, 20f)
            layoutParams = LinearLayout.LayoutParams(s, s)
            imageTintList = android.content.res.ColorStateList.valueOf(
                ctx.themeColor(MR.attr.colorOnSurfaceVariant)
            )
        })
        card.addView(row)
        return card
    }

    // ---------- 徽标与等宽块 ----------

    /** 状态徽标胶囊（全圆角 pill，M3 Expressive）。 */
    fun pill(ctx: Context, text: String, bgAttr: Int = MR.attr.colorPrimaryContainer,
             fgAttr: Int = MR.attr.colorOnPrimaryContainer): TextView =
        TextView(ctx).apply {
            this.text = text
            setTextAppearance(MR.attr.textAppearanceLabelMedium)
            setTextColor(ctx.themeColor(fgAttr))
            background = MaterialShapeDrawable().apply {
                fillColor = android.content.res.ColorStateList.valueOf(ctx.themeColor(bgAttr))
                shapeAppearanceModel = ShapeAppearanceModel.builder()
                    .setAllCornerSizes(dp(ctx, SHAPE_PILL).toFloat())
                    .build()
            }
            val ph = dp(ctx, 12f)
            val pv = dp(ctx, 4f)
            setPadding(ph, pv, ph, pv)
            gravity = Gravity.CENTER
        }

    /** 等宽日志/代码块（细描边 + 12dp 圆角 + 12dp 内边距，固定高度内部滚动）。 */
    fun monoBlock(ctx: Context, heightDp: Float = 180f): TextView =
        TextView(ctx).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 12f
            setTextColor(ctx.themeColor(MR.attr.colorOnSurfaceVariant))
            background = MaterialShapeDrawable().apply {
                fillColor = android.content.res.ColorStateList.valueOf(
                    ctx.themeColor(MR.attr.colorSurfaceVariant)
                )
                strokeColor = android.content.res.ColorStateList.valueOf(
                    ctx.themeColor(MR.attr.colorOutline)
                )
                strokeWidth = dp(ctx, 1f).toFloat()
                shapeAppearanceModel = ShapeAppearanceModel.builder()
                    .setAllCornerSizes(dp(ctx, SHAPE_INNER).toFloat())
                    .build()
            }
            val p = dp(ctx, SP_12)
            setPadding(p, p, p, p)
            isVerticalScrollBarEnabled = true
            movementMethod = android.text.method.ScrollingMovementMethod()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, heightDp)
            ).apply { topMargin = dp(ctx, SP_10) }
        }

    /** 1dp 分隔线（colorOutlineVariant）。 */
    fun divider(ctx: Context): View =
        View(ctx).apply {
            setBackgroundColor(ctx.themeColor(MR.attr.colorOutlineVariant))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 1f).coerceAtLeast(1)
            )
        }

    // ---------- 对话框 ----------

    /**
     * 对话框根容器：竖排 + 24/16/24/8 内边距。
     * 替代历史上 64 处复制粘贴的 `setPadding(dp(ctx,24),dp(ctx,16),dp(ctx,24),dp(ctx,8))`。
     */
    fun dialogRoot(ctx: Context): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, SP_24), dp(ctx, SP_16), dp(ctx, SP_24), dp(ctx, SP_8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

    /**
     * 对话框内容列：竖排 + 20/12/20/0 内边距（StabilityFeatures 各对话框同款）。
     * 内容超长时由调用方包 ScrollView。
     */
    fun dialogColumn(ctx: Context): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(ctx, 20f)
            setPadding(p, dp(ctx, SP_12), p, 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

    /**
     * 功能对话框声明式构建器：标题 / 说明 / 输入框 / 主次按钮一站配齐。
     * 替代历史上 60 处复制粘贴的「拼标题 + hint + 输入框 + 确定取消」。
     *
     * 用法：
     * ```
     * FeatureDialogBuilder(ctx)
     *     .title("串口发送")
     *     .hint("向开发板发送一行文本")
     *     .input("内容", prefill)
     *     .onConfirm { send(it) }
     *     .show()
     * ```
     */
    class FeatureDialogBuilder(private val ctx: Context) {
        private var title: String? = null
        private var hint: String? = null
        private var inputHint: String? = null
        private var prefill: String = ""
        private var numeric: Boolean = false
        private var onConfirm: ((String) -> Unit)? = null
        private var onCancel: (() -> Unit)? = null
        private var confirmText: String = "确定"
        private var cancelText: String = "取消"
        private var showCancel: Boolean = true
        private var extra: (LinearLayout.() -> Unit)? = null

        fun title(t: String?) = apply { this.title = t }
        fun hint(t: String?) = apply { this.hint = t }
        fun input(hint: String, prefill: String = "", numeric: Boolean = false) = apply {
            this.inputHint = hint; this.prefill = prefill; this.numeric = numeric
        }
        fun onConfirm(action: ((String) -> Unit)?) = apply { this.onConfirm = action }
        fun onCancel(action: (() -> Unit)?) = apply { this.onCancel = action }
        fun confirmText(t: String) = apply { this.confirmText = t }
        fun cancelText(t: String) = apply { this.cancelText = t }
        fun showCancel(show: Boolean) = apply { this.showCancel = show }
        /** 在按钮排之前插入自定义内容（读数、开关等）。 */
        fun extra(block: LinearLayout.() -> Unit) = apply { this.extra = block }

        fun show(): androidx.appcompat.app.AlertDialog {
            val root = dialogRoot(ctx)
            title?.let { t ->
                root.addView(TextView(ctx).apply {
                    text = t
                    setTextAppearance(MR.attr.textAppearanceTitleLarge)
                    setTextColor(ctx.themeColor(MR.attr.colorOnSurface))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                })
            }
            hint?.let { h ->
                root.addView(TextView(ctx).apply {
                    text = h
                    setTextAppearance(MR.attr.textAppearanceBodyMedium)
                    setTextColor(ctx.themeColor(MR.attr.colorOnSurfaceVariant))
                })
            }
            var edit: EditText? = null
            if (inputHint != null) {
                val til = TextInputLayout(ctx, null, MR.attr.textInputOutlinedStyle).apply {
                    hint = inputHint
                    boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(ctx, SP_12) }
                }
                val et = TextInputEditText(ctx).apply {
                    setText(prefill)
                    inputType = if (numeric) {
                        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    } else {
                        InputType.TYPE_CLASS_TEXT
                    }
                    setSelection(text?.length ?: 0)
                }
                til.addView(et)
                root.addView(til)
                edit = et
            }
            extra?.invoke(root)

            val dialog = MaterialAlertDialogBuilder(ctx)
                .setView(root)
                .setPositiveButton(confirmText) { _, _ ->
                    onConfirm?.invoke(edit?.text?.toString()?.trim().orEmpty())
                }
                .apply {
                    if (showCancel) setNegativeButton(cancelText) { _, _ -> onCancel?.invoke() }
                }
                .create()
            dialog.show()
            return dialog
        }
    }

    // ---------- 输入助手 ----------

    /**
     * EditText 文本变化助手（只关心 afterTextChanged，参数已是非空字符串）。
     * 替代 8 处「匿名 TextWatcher + 两个空 override」复制粘贴。
     */
    fun android.widget.EditText.doAfterTextChanged(action: (String) -> Unit) {
        addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { action(s?.toString() ?: "") }
        })
    }

    // ---------- 按钮批量统一样式 ----------
    /**
     * 递归把视图树里所有 MaterialButton 统一到 M3 Expressive 规格：
     * 52dp 下限高度、20dp 圆角、长文案两行不裁切、按压回弹。
     *
     * 对话框 / 悬浮窗在 show() 前调用一次即可，替代历史上 89 处手写按钮高度
     * 与 42 处 materialButtonOutlinedStyle 的各自为政。
     */
    fun styleButtons(root: View) {
        if (root is MaterialButton) {
            root.minHeight = dp(root.context, BTN_HEIGHT)
            root.minimumHeight = dp(root.context, BTN_HEIGHT)
            root.cornerRadius = dp(root.context, SHAPE_CARD)
            root.maxLines = 2
            root.ellipsize = android.text.TextUtils.TruncateAt.END
            root.isAllCaps = false
            pressFeedback(root)
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) styleButtons(root.getChildAt(i))
        }
    }

    // ---------- 防抖点击 ----------
    /**
     * 防抖点击：同一 View [intervalMs] 内重复点击只生效第一次。
     * 替代历史上 5 处阈值各不相同（500/700/900/1500/2000ms）的 shake 防抖复制。
     */
    fun View.onClickDebounced(intervalMs: Long = 600L, action: () -> Unit) {
        var last = 0L
        setOnClickListener {
            val now = android.os.SystemClock.uptimeMillis()
            if (now - last >= intervalMs) {
                last = now
                action()
            }
        }
    }

    // ---------- 手势事件去抖闸门 ----------

    /**
     * 手势事件去抖闸门：收敛控制台 8 处 `now - lastShakeAt > N` 的手搓防抖复制
     * （决断器 900 / 计数器 500 / 骰子 1500 / 石头剪刀布 2000 / 摇晃触发器动态冷却等）。
     *
     * [allow] 命中时自动记录时间戳；真实时钟（currentTimeMillis 远大于间隔）下首次恒放行，
     * 与旧行为一致。与 [onClickDebounced] 的区别：后者作用于 View 点击，本类作用于轮询事件流。
     */
    class ShakeGate {
        private var last = 0L

        /** 距上次放行超过 [intervalMs] 返回 true 并刷新时间戳，否则 false */
        fun allow(intervalMs: Long, now: Long = System.currentTimeMillis()): Boolean {
            if (now - last > intervalMs) {
                last = now
                return true
            }
            return false
        }
    }

    // ---------- 周期任务 ----------

    /**
     * 周期任务：收敛控制台 14 处 `object : Runnable { run() { …; postDelayed(this, ms) } }` 手搓轮询。
     *
     * - [onTick] 返回 false 即自终止（倒计时归零、闹钟触发等场景），不再排下一拍
     * - [intervalMs] 是提供者而非定值：节拍器运行时改 BPM 下一拍即生效
     * - [start] 先清理残留再入队，杜绝重入导致的双重调度；等价于旧写法的
     *   `handler.post(tick)`（[delayMs] = 0）或 `handler.postDelayed(tick, delayMs)`
     */
    class Ticker(
        private val intervalMs: () -> Long,
        private val handler: Handler,
        private val onTick: () -> Boolean = { true }
    ) {
        private val runnable = object : Runnable {
            override fun run() {
                if (onTick()) handler.postDelayed(this, intervalMs())
            }
        }

        fun start(delayMs: Long = 0L) {
            handler.removeCallbacks(runnable)
            if (delayMs > 0) handler.postDelayed(runnable, delayMs) else handler.post(runnable)
        }

        fun stop() {
            handler.removeCallbacks(runnable)
        }
    }

    // ---------- 后台顺序播音 ----------

    /**
     * 后台顺序播音：收敛门铃 / 音效板 / 谱曲台 / 上课铃 / 番茄钟 5 处
     * `Thread { notes.forEach { runOnUiThread { tone }; sleep } }` 手搓复制。
     *
     * - [notes] 为 (频率 Hz, 时长 ms)；频率 ≤ 0 表示休止符（只睡不发声音）
     * - [shouldContinue] 返回 false 立即中断（Activity 生命周期 / 播放中标志）
     * - [onTone] 需调用方自行切主线程（对话框内即 runOnUiThread）
     * - [times] / [gapMs]：整轮重复次数与轮间停顿（门铃用）
     * - [tailMs]：每个发声音符后的额外停顿（谱曲台防粘音用）
     * - [onFinished] 在后台线程 finally 执行（重置 playing 标志等）
     */
    class ToneSequence(
        private val notes: List<Pair<Int, Long>>,
        private val shouldContinue: () -> Boolean,
        private val onTone: (Int, Int) -> Unit,
        private val onFinished: () -> Unit = {},
        private val times: Int = 1,
        private val gapMs: Long = 0L,
        private val tailMs: Long = 0L
    ) {
        fun start() {
            Thread {
                try {
                    for (round in 0 until times) {
                        if (!shouldContinue()) break
                        for ((f, ms) in notes) {
                            if (!shouldContinue()) break
                            if (f > 0) {
                                onTone(f, ms.toInt())
                                Thread.sleep(ms + tailMs)
                            } else {
                                Thread.sleep(ms)
                            }
                        }
                        if (gapMs > 0) Thread.sleep(gapMs)
                    }
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                } finally {
                    onFinished()
                }
            }.start()
        }
    }

    // ---------- 滑块数值网格吸附 ----------

    /**
     * 把数值吸附到 Slider 合法网格（from + n×step，且 ≤ to）。
     * Material Slider 布局期会 validateValues，脱网值直接抛 IllegalStateException——
     * 历史上「灵敏度滑块野值崩溃」两次都靠这类吸附修复（见 Prefs.snapSensitivity）。
     */
    fun snapToStep(v: Float, from: Float, to: Float, step: Float): Float {
        if (v.isNaN() || step <= 0f) return from
        val clamped = v.coerceIn(from, to)
        val maxSteps = Math.floor(((to - from) / step).toDouble()).toInt()
        val steps = Math.round((clamped - from) / step).toInt().coerceIn(0, maxSteps)
        return (from + steps * step).coerceAtMost(to)
    }

    // ---------- M3 Expressive：按压反馈与弹簧入场 ----------

    /**
     * 按压反馈（Expressive button shape morph 的轻量实现）：按下时缩小 3.5%
     * 并轻微变暗，抬起时带 Overshoot 回弹。
     *
     * ⚠️ 触摸监听必须返回 false（不消费事件）——消费 ACTION_DOWN 会让
     * OnClickListener 永不触发（v4.1.0 悬浮窗「点不动」的根因），此处只加动画。
     * 重复调用安全（tag 去重）。
     */
    fun pressFeedback(view: View) {
        if (view.getTag(R.id.tag_press_feedback) == true) return
        view.setTag(R.id.tag_press_feedback, true)
        view.setOnTouchListener { v, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> v.animate()
                    .scaleX(0.965f).scaleY(0.965f).alpha(0.92f)
                    .setDuration(60L)
                    .start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate()
                    .scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(140L)
                    .setInterpolator(OvershootInterpolator(1.6f))
                    .start()
            }
            false // 关键：不消费，点击照常生效
        }
    }

    /**
     * 递归给整页可点击元素挂按压反馈。
     * 跳过：EditText（输入控件）、CompoundButton（Switch/Chip 自带状态与 ripple）、
     * ViewPager/RecyclerView 类（自带滚动冲突风险，isClickable 一般为 false 也不会挂）。
     */
    fun applyPressFeedback(root: View) {
        if (root is EditText || root is android.widget.CompoundButton) return
        if (root.isClickable) pressFeedback(root)
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) applyPressFeedback(root.getChildAt(i))
        }
    }
}

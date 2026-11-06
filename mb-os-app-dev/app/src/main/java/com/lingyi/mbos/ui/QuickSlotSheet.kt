/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.widget.NestedScrollView
import com.google.android.material.R as MR
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.lingyi.mbos.R
import com.lingyi.mbos.util.themeColor

/**
 * 「自定义快捷入口」底部弹层（M3 bottom sheet）。
 *
 * 交互：先在顶部两个位置卡里选「要改哪一个」，再点下方功能列表里的任一项即写入；
 * 写完自动切到另一个位置，连点两次即可换好两张卡。已占用的功能会自动换位
 * （见 [QuickSlots.assign]），不会出现两张卡重复。
 *
 * 全程序化构建：不新增布局资源，跟随主题色与深色模式。
 */
object QuickSlotSheet {

    fun show(
        activity: AppCompatActivity,
        current: List<String>,
        onSave: (List<String>) -> Unit
    ) {
        val ctx = activity
        val dp = { v: Int -> M3Kit.dp(ctx, v) }
        var slots = QuickSlots.normalize(current)
        var active = 0

        /** 按 M3 字号直接给 TextView 定标（比 setTextAppearance(attr) 可靠）。 */
        fun label(text: String, sizeSp: Float, colorAttr: Int, bold: Boolean = false): TextView =
            TextView(ctx).apply {
                this.text = text
                textSize = sizeSp
                setTextColor(ctx.themeColor(colorAttr))
                if (bold) setTypeface(typeface, Typeface.BOLD)
            }

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(6), dp(20), dp(16))
        }

        // 抓手
        root.addView(
            View(ctx).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(2).toFloat()
                    setColor(ctx.themeColor(MR.attr.colorOutlineVariant))
                }
            },
            LinearLayout.LayoutParams(dp(32), dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(6)
            }
        )

        root.addView(
            label(ctx.getString(R.string.quick_sheet_title), 22f, MR.attr.colorOnSurface, true),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14) }
        )
        root.addView(
            label(ctx.getString(R.string.quick_sheet_desc), 13f, MR.attr.colorOnSurfaceVariant),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3) }
        )

        // ---------- 两个位置卡 ----------
        val slotCards = arrayOfNulls<MaterialCardView>(2)
        val slotTitles = arrayOfNulls<TextView>(2)
        val slotIcons = arrayOfNulls<AppCompatImageView>(2)
        var renderSlots: () -> Unit = {}
        var renderMarks: () -> Unit = {}

        val slotRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        for (i in 0 until QuickSlots.SIZE) {
            val card = MaterialCardView(ctx).apply {
                radius = dp(18).toFloat()
                cardElevation = 0f
                strokeWidth = dp(1)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    active = i
                    renderSlots()
                }
            }
            val body = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
            }
            val icon = AppCompatImageView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
            }
            val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            val title = label("", 14f, MR.attr.colorOnSurface, true).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            col.addView(label(
                ctx.getString(if (i == 0) R.string.quick_slot_1 else R.string.quick_slot_2),
                11f, MR.attr.colorOnSurfaceVariant
            ))
            col.addView(title)
            body.addView(icon)
            body.addView(
                col,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(10)
                }
            )
            card.addView(body)
            slotRow.addView(
                card,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (i > 0) marginStart = dp(10)
                }
            )
            slotCards[i] = card
            slotTitles[i] = title
            slotIcons[i] = icon
        }
        root.addView(
            slotRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(16) }
        )

        val activeHint = label("", 12f, MR.attr.colorPrimary, true)
        root.addView(
            activeHint,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        )

        // ---------- 功能列表（按分类分组） ----------
        val listWrap = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(2), 0, dp(8))
        }
        val checkMarks = HashMap<String, TextView>()

        fun featureRow(f: FeatureCatalog.Feature) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                setPadding(dp(10), dp(11), dp(10), dp(11))
                val tv = TypedValue()
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                setBackgroundResource(tv.resourceId)
            }
            val iv = AppCompatImageView(ctx).apply {
                setImageResource(f.icon)
                imageTintList = ColorStateList.valueOf(ctx.themeColor(MR.attr.colorOnSurfaceVariant))
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
            }
            val name = label(f.title, 15f, MR.attr.colorOnSurface).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            val mark = label("✓", 16f, MR.attr.colorPrimary, true).apply {
                visibility = View.INVISIBLE
            }
            row.addView(iv)
            row.addView(
                name,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(12)
                }
            )
            row.addView(
                mark,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(8) }
            )
            row.setOnClickListener {
                slots = QuickSlots.assign(slots, active, f.id)
                active = if (active == 0) 1 else 0
                onSave(slots)
                renderSlots()
                renderMarks()
            }
            checkMarks[f.id] = mark
            listWrap.addView(row)
        }

        FeatureCatalog.Category.entries.forEach { category ->
            listWrap.addView(
                label(category.title, 12f, MR.attr.colorOnSurfaceVariant, true).apply {
                    setPadding(dp(10), dp(14), dp(10), dp(4))
                }
            )
            FeatureCatalog.byCategory(category).forEach { featureRow(it) }
        }

        val scroll = NestedScrollView(ctx).apply {
            addView(
                listWrap,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        val listHeight = (ctx.resources.displayMetrics.heightPixels * 0.52f).toInt()
        root.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, listHeight).apply {
                topMargin = dp(4)
            }
        )

        // ---------- 完成 ----------
        val done = M3Kit.button(ctx, "完成", M3Kit.ButtonStyle.FILLED) {}
        root.addView(
            done,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        )

        val dialog = BottomSheetDialog(ctx)
        dialog.setContentView(root)
        done.setOnClickListener { dialog.dismiss() }
        dialog.behavior.skipCollapsed = true
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.show()

        renderSlots = {
            for (i in 0 until QuickSlots.SIZE) {
                val selected = i == active
                val f = QuickSlots.featureOf(slots[i])
                slotCards[i]?.apply {
                    setCardBackgroundColor(
                        ctx.themeColor(
                            if (selected) MR.attr.colorPrimaryContainer else MR.attr.colorSurface
                        )
                    )
                    strokeColor = ctx.themeColor(
                        if (selected) MR.attr.colorPrimary else MR.attr.colorOutlineVariant
                    )
                }
                slotTitles[i]?.text = f?.title ?: "-"
                slotIcons[i]?.let { iv ->
                    f?.let { iv.setImageResource(it.icon) }
                    iv.imageTintList = ColorStateList.valueOf(
                        ctx.themeColor(
                            if (selected) MR.attr.colorOnPrimaryContainer
                            else MR.attr.colorOnSurfaceVariant
                        )
                    )
                }
            }
            activeHint.text = ctx.getString(
                R.string.quick_slot_active,
                ctx.getString(if (active == 0) R.string.quick_slot_1 else R.string.quick_slot_2)
            )
        }

        renderMarks = {
            checkMarks.forEach { (id, mark) ->
                mark.visibility = if (id in slots) View.VISIBLE else View.INVISIBLE
            }
        }

        renderSlots()
        renderMarks()
    }
}

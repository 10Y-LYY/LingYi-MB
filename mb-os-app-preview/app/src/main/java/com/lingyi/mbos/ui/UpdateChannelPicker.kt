/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import com.google.android.material.R as MR
import com.google.android.material.button.MaterialButton
import com.lingyi.mbos.R
import com.lingyi.mbos.util.Prefs
import com.lingyi.mbos.util.UpdateChecker
import com.lingyi.mbos.util.themeColor

/**
 * 更新通道下拉选择器（M3）。
 *
 * 形态：左侧「更新通道 + 当前通道说明」，右侧一个浅色胶囊按钮（当前通道名 + 下拉箭头），
 * 点按弹出菜单（稳定版 / 预览版 / 开发版），选中即写入 [Prefs.setChannel] 并回调。
 *
 * 通道是「三选一」枚举（稳定版 / 预览版 / 开发版），下拉能同时表达「当前是哪个通道」
 * 和「可以切到哪个通道」。
 *
 * 全 App 三个更新入口（关于 / 设置 / 开发者）共用本组件，语义只此一份。
 */
class UpdateChannelPicker @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val titleView: TextView
    private val descView: TextView
    private val valueView: MaterialButton

    /** 当前通道：[UpdateChecker.CHANNEL_STABLE] / [UpdateChecker.CHANNEL_PREVIEW] / [UpdateChecker.CHANNEL_DEV]。 */
    var currentChannel: String = UpdateChecker.CHANNEL_STABLE
        private set

    /** 用户切换通道后回调（参数：切换后的通道字符串）。 */
    var onChannelChanged: ((String) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, M3Kit.dp(context, 4), 0, M3Kit.dp(context, 4))

        val textCol = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }

        titleView = TextView(context).apply {
            setTextAppearance(MR.attr.textAppearanceTitleSmall)
            setTextColor(context.themeColor(MR.attr.colorOnSurface))
            text = context.getString(R.string.update_channel_title)
        }

        descView = TextView(context).apply {
            setTextAppearance(MR.attr.textAppearanceBodySmall)
            setTextColor(context.themeColor(MR.attr.colorOnSurfaceVariant))
            setPadding(0, M3Kit.dp(context, 2), 0, 0)
        }

        textCol.addView(titleView)
        textCol.addView(descView)

        valueView = MaterialButton(context).apply {
            isAllCaps = false
            textSize = 13f
            minWidth = 0
            minimumWidth = 0
            insetTop = 0
            insetBottom = 0
            minHeight = 0
            minimumHeight = 0
            height = M3Kit.dp(context, 36)
            setPadding(M3Kit.dp(context, 14), 0, M3Kit.dp(context, 10), 0)
            cornerRadius = M3Kit.dp(context, 18)
            setBackgroundColor(context.themeColor(MR.attr.colorSurfaceContainer))
            setTextColor(context.themeColor(MR.attr.colorOnSurface))
            iconSize = M3Kit.dp(context, 18)
            iconTint = android.content.res.ColorStateList.valueOf(
                context.themeColor(MR.attr.colorOnSurfaceVariant)
            )
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_END
            iconPadding = M3Kit.dp(context, 2)
            setIconResource(R.drawable.ic_chevron_down)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = M3Kit.dp(context, 12)
            }
            setOnClickListener { showMenu() }
        }

        addView(textCol)
        addView(valueView)

        render()
    }

    /** 从 Prefs 重新读取并刷新（跨页面返回时同步，例如在开发者页改过通道）。 */
    fun refresh() {
        currentChannel = Prefs.getChannel(context)
        render()
    }

    private fun channelLabel(channel: String): String = when (channel) {
        UpdateChecker.CHANNEL_PREVIEW -> context.getString(R.string.update_channel_preview)
        UpdateChecker.CHANNEL_DEV -> context.getString(R.string.update_channel_dev)
        else -> context.getString(R.string.update_channel_stable)
    }

    private fun channelDesc(channel: String): String = when (channel) {
        UpdateChecker.CHANNEL_PREVIEW -> context.getString(R.string.update_channel_preview_desc)
        UpdateChecker.CHANNEL_DEV -> context.getString(R.string.update_channel_dev_desc)
        else -> context.getString(R.string.update_channel_stable_desc)
    }

    private fun render() {
        valueView.text = channelLabel(currentChannel)
        descView.text = channelDesc(currentChannel)
        contentDescription = context.getString(
            R.string.update_channel_a11y,
            channelLabel(currentChannel)
        )
    }

    private fun showMenu() {
        val popup = PopupMenu(context, valueView)
        val items = listOf(
            UpdateChecker.CHANNEL_STABLE to R.string.update_channel_stable,
            UpdateChecker.CHANNEL_PREVIEW to R.string.update_channel_preview,
            UpdateChecker.CHANNEL_DEV to R.string.update_channel_dev,
        )
        items.forEachIndexed { idx, (channel, labelRes) ->
            popup.menu.add(0, idx, idx, labelRes).apply {
                isCheckable = true
                isChecked = channel == currentChannel
            }
        }
        popup.menu.setGroupCheckable(0, true, true)
        popup.setOnMenuItemClickListener { item ->
            val channel = items[item.itemId].first
            selectChannel(channel)
            true
        }
        popup.show()
    }

    /**
     * 切换通道（菜单点击与测试共用）：写入 Prefs、刷新文案，值真正改变时才回调。
     * internal 供同包测试直接驱动，避免依赖 PopupMenu 的真实弹出。
     */
    internal fun selectChannel(channel: String) {
        if (channel == currentChannel) return
        currentChannel = channel
        Prefs.setChannel(context, channel)
        render()
        onChannelChanged?.invoke(channel)
    }
}

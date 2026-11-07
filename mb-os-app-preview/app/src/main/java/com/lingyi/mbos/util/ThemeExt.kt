/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.util

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import androidx.annotation.AttrRes
import com.google.android.material.R as MR
import com.lingyi.mbos.BuildConfig

/**
 * 界面风格的唯一判定点：仅预览版、且用户在设置里开启 Voyage 风格时为真。
 * 主界面皮肤挂载、M3Kit 二级页换肤与排版、窗口/内容过渡全部以此为准，
 * 因此设置里一个开关即可让整套界面在「Voyage」与「稳定版 M3」间整体切换。
 * 稳定版 / 开发版（DEFAULT_CHANNEL ≠ "preview"）恒为 false，走原主题逻辑，零变化。
 *
 * 这里是「动态判定」而非编译期常量：切换开关后所有页面重建时立即读到新值。
 */
fun Context.voyageStyle(): Boolean =
    BuildConfig.DEFAULT_CHANNEL == "preview" && Prefs.isVoyageSkin(this)

private fun voyageColor(attr: Int): Int? = when (attr) {
    MR.attr.colorPrimary -> Color.parseColor("#36685D")
    MR.attr.colorOnPrimary -> Color.parseColor("#F7FAF7")
    MR.attr.colorPrimaryContainer -> Color.parseColor("#D2E7E0")
    MR.attr.colorOnPrimaryContainer -> Color.parseColor("#28554B")
    MR.attr.colorSecondary -> Color.parseColor("#36685D")
    MR.attr.colorSecondaryContainer -> Color.parseColor("#D2E7E0")
    MR.attr.colorOnSecondaryContainer -> Color.parseColor("#28554B")
    MR.attr.colorTertiaryContainer -> Color.parseColor("#D3F1CF")
    MR.attr.colorSurface -> Color.parseColor("#FFFFFF")
    MR.attr.colorSurfaceVariant -> Color.parseColor("#E9F1EB")
    // 页面底比卡片浅一档，形成「白色卡片浮在浅灰绿底上」的层次（MIUI / M3 Expressive 语言）
    MR.attr.colorSurfaceContainerLow -> Color.parseColor("#EAEFEB")
    MR.attr.colorSurfaceContainer -> Color.parseColor("#EFF4EF")
    MR.attr.colorSurfaceContainerHigh -> Color.parseColor("#E1E9E2")
    MR.attr.colorSurfaceContainerHighest -> Color.parseColor("#DCE6DE")
    MR.attr.colorOnSurface -> Color.parseColor("#1E2A26")
    MR.attr.colorOnSurfaceVariant -> Color.parseColor("#5E6E68")
    MR.attr.colorOutline -> Color.parseColor("#B9CCC1")
    MR.attr.colorOutlineVariant -> Color.parseColor("#DDE8E0")
    MR.attr.colorError -> Color.parseColor("#B23A3A")
    MR.attr.colorOnError -> Color.parseColor("#FFFFFF")
    else -> null
}

/** 从当前主题解析 M3 颜色令牌（如 colorPrimary / colorSurfaceVariant）。 */
fun Context.themeColor(@AttrRes attr: Int, default: Int = 0): Int {
    if (voyageStyle()) voyageColor(attr)?.let { return it }
    val tv = TypedValue()
    return if (theme.resolveAttribute(attr, tv, true)) tv.data else default
}
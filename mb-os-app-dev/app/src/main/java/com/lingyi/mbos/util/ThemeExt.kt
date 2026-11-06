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
 * 仅预览版：把 M3 主题色令牌映射到 Voyage 设计色板（深青绿 + 浅薄荷），
 * 使所有走 M3Kit 的二级页 / 弹窗整体统一为 Voyage 风。
 * 稳定版 / 开发版（DEFAULT_CHANNEL ≠ "preview"）走原主题逻辑，零变化。
 */
private val VOYAGE = BuildConfig.DEFAULT_CHANNEL == "preview"

private fun voyageColor(attr: Int): Int? = when (attr) {
    MR.attr.colorPrimary -> Color.parseColor("#36685D")
    MR.attr.colorOnPrimary -> Color.parseColor("#F7FAF7")
    MR.attr.colorPrimaryContainer -> Color.parseColor("#D2E7E0")
    MR.attr.colorOnPrimaryContainer -> Color.parseColor("#28554B")
    MR.attr.colorSecondary -> Color.parseColor("#36685D")
    MR.attr.colorSecondaryContainer -> Color.parseColor("#D2E7E0")
    MR.attr.colorOnSecondaryContainer -> Color.parseColor("#28554B")
    MR.attr.colorTertiaryContainer -> Color.parseColor("#D3F1CF")
    MR.attr.colorSurface -> Color.parseColor("#F7FAF7")
    MR.attr.colorSurfaceVariant -> Color.parseColor("#E9F1EB")
    MR.attr.colorOnSurface -> Color.parseColor("#1E2A26")
    MR.attr.colorOnSurfaceVariant -> Color.parseColor("#5E6E68")
    MR.attr.colorOutline -> Color.parseColor("#DDE8E0")
    MR.attr.colorOutlineVariant -> Color.parseColor("#E3ECE6")
    MR.attr.colorError -> Color.parseColor("#B23A3A")
    MR.attr.colorOnError -> Color.parseColor("#FFFFFF")
    else -> null
}

/** 从当前主题解析 M3 颜色令牌（如 colorPrimary / colorSurfaceVariant）。 */
fun Context.themeColor(@AttrRes attr: Int, default: Int = 0): Int {
    if (VOYAGE) voyageColor(attr)?.let { return it }
    val tv = TypedValue()
    return if (theme.resolveAttribute(attr, tv, true)) tv.data else default
}
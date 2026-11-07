/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.util

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import com.lingyi.mbos.R

/**
 * 主题中枢：主题模式（跟随系统 / 浅色 / 深色）与主题色
 * （安卓原生 M3 官方取色 / 青绿 / 鎏金 / 靛蓝 / 绯红 / 松绿）。
 *
 * 主题切换通过「主题纪元」（见 Prefs.bumpThemeEpoch）广播到所有页面，
 * 避免返回栈里的旧页面停留在旧配色造成文字与主题不匹配。
 */
object ThemeManager {

    // 主题模式
    const val MODE_SYSTEM = "system"
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"

    // 主题色
    const val COLOR_DYNAMIC = "dynamic"   // 动态取色（Material You，API 31+，低版本回落 M3 官方色）
    const val COLOR_M3 = "m3"             // 安卓原生 Material 3 官方取色（默认）
    const val COLOR_TEAL = "teal"         // 青绿
    const val COLOR_GOLD = "gold"         // 鎏金
    const val COLOR_INDIGO = "indigo"     // 靛蓝
    const val COLOR_ROSE = "rose"         // 绯红
    const val COLOR_FOREST = "forest"     // 松绿

    /** 主题模式 → AppCompatDelegate 夜间模式常量。 */
    fun nightMode(ctx: Context): Int = when (normalizeMode(Prefs.getThemeMode(ctx))) {
        MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    /** 进程级应用夜间模式（Application.onCreate 调用；设置页变更时再次调用）。 */
    fun applyNightMode(ctx: Context) {
        AppCompatDelegate.setDefaultNightMode(nightMode(ctx))
    }

    /** 归一化主题模式，非法值回落跟随系统。 */
    fun normalizeMode(mode: String?): String = when (mode) {
        MODE_LIGHT, MODE_DARK -> mode
        else -> MODE_SYSTEM
    }

    /** 归一化主题色，非法值回落 M3 官方色；dynamic 仅在 API 31+ 有效（调用方负责回落）。 */
    fun normalizeColor(color: String?): String = when (color) {
        COLOR_DYNAMIC, COLOR_M3, COLOR_TEAL, COLOR_GOLD, COLOR_INDIGO, COLOR_ROSE, COLOR_FOREST -> color
        else -> COLOR_M3
    }

    /**
     * 主题色 → 配色覆盖层资源；M3 官方色即基础主题（0 表示无需覆盖）。
     * dynamic 由 [applyTheme] 另行处理（DynamicColors），此处返回 0。
     */
    fun themeOverlayRes(color: String): Int = when (normalizeColor(color)) {
        COLOR_GOLD -> R.style.ThemeOverlay_MBOS_Gold
        COLOR_INDIGO -> R.style.ThemeOverlay_MBOS_Indigo
        COLOR_ROSE -> R.style.ThemeOverlay_MBOS_Rose
        COLOR_FOREST -> R.style.ThemeOverlay_MBOS_Forest
        COLOR_TEAL -> R.style.ThemeOverlay_MBOS_Teal
        else -> 0
    }

    /** 动态取色在当前设备是否可用（API 31+）。 */
    fun isDynamicColorAvailable(): Boolean =
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

    /**
     * Activity 创建前应用用户主题：先套配色覆盖层，再（可选）叠加系统动态取色。
     * 必须在 super.onCreate() 之前调用。
     *
     * 全部步骤带兜底：动态取色在部分定制 ROM（如荣耀 MagicOS）上会因
     * wallpaper 取色实现差异抛异常，任一步失败都不应中断页面启动。
     */
    fun applyTheme(activity: Activity) {
        runCatching {
            val color = normalizeColor(Prefs.getThemeColor(activity))
            val overlay = themeOverlayRes(color)
            if (overlay != 0) activity.theme.applyStyle(overlay, true)
            if (color == COLOR_DYNAMIC && isDynamicColorAvailable()) {
                // 动态取色在个别 ROM 上读取壁纸可能抛异常，兜底为静态配色并留痕
                runCatching { DynamicColors.applyToActivityIfAvailable(activity) }
                    .onFailure { AppLog.w("ThemeManager", "动态取色失败，回退静态配色", it) }
            }
            // 界面风格：预览版开启 Voyage 时，最后叠加 Voyage 主题覆盖层。
            // 这一步让 XML 布局页（设置等）的 ?attr/colorXxx 也整体换成深青绿薄荷绿，
            // 与 M3Kit 程序化二级页、Voyage 主界面一致；切回「稳定版 M3」则不叠加，
            // 自动回到原 M3 主题。稳定版 / 开发版恒不进入。
            if (activity.voyageStyle()) {
                activity.theme.applyStyle(R.style.ThemeOverlay_MBOS_Voyage, true)
            }
        }.onFailure { AppLog.e("ThemeManager", "主题应用失败（${activity.javaClass.simpleName}）", it) }
    }

    // ---------- 其他设置 ----------

    /** 实时读取轮询间隔（毫秒）。 */
    fun readIntervalMs(ctx: Context): Long = Prefs.getReadIntervalMs(ctx)

    fun isKeepScreenOn(ctx: Context): Boolean = Prefs.isKeepScreenOn(ctx)

    fun isHapticEnabled(ctx: Context): Boolean = Prefs.isHapticEnabled(ctx)
}

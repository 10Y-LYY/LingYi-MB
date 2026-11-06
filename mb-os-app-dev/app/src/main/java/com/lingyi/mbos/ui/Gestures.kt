/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

/**
 * 手势中文名单一出处（v4.2 合并 MainActivity / PreviewActivity /
 * ConsoleMoreSensor / ConsoleMoreAuto 四处重复实现）。
 *
 * 输入为固件 / 模拟器 READ 行的 G 字段值；null 或未知值按「无」展示。
 */
object Gestures {

    fun label(g: String?): String = when (g) {
        "shake" -> "摇一摇"
        "tap" -> "点击"
        "logoup" -> "Logo 朝上"
        "facedown" -> "正面朝下"
        "freefall" -> "自由落体"
        "3g" -> "3g 加速"
        "6g" -> "6g 加速"
        "8g" -> "8g 加速"
        else -> "无"
    }
}

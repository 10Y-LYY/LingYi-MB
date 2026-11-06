/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

/**
 * 单位换算（「单位换算」工具用）：长度 / 重量 / 温度三组，全部以国际单位制为中转。
 */
object UnitConvert {

    enum class Kind(val label: String, val units: List<String>) {
        LENGTH("长度", listOf("米", "厘米", "毫米", "千米", "英寸", "英尺")),
        WEIGHT("重量", listOf("千克", "克", "毫克", "吨", "磅", "盎司")),
        TEMP("温度", listOf("摄氏度 °C", "华氏度 °F", "开尔文 K")),
    }

    /** 各单位对基本单位的倍率（温度不走倍率，单独处理）。 */
    private fun factor(kind: Kind, unit: String): Double = when (kind) {
        Kind.LENGTH -> when (unit) {
            "米" -> 1.0
            "厘米" -> 0.01
            "毫米" -> 0.001
            "千米" -> 1000.0
            "英寸" -> 0.0254
            "英尺" -> 0.3048
            else -> 1.0
        }
        Kind.WEIGHT -> when (unit) {
            "千克" -> 1.0
            "克" -> 0.001
            "毫克" -> 0.000001
            "吨" -> 1000.0
            "磅" -> 0.45359237
            "盎司" -> 0.028349523125
            else -> 1.0
        }
        Kind.TEMP -> 1.0
    }

    /**
     * 换算：[value] 从 [from] 单位转到 [to] 单位。温度用偏移公式，其余用倍率。
     * 非法数字返回 null；相同单位原值返回。
     */
    fun convert(kind: Kind, from: String, to: String, value: Double): Double? {
        if (kind == Kind.TEMP) {
            val c = toCelsius(from, value) ?: return null
            return fromCelsius(to, c)
        }
        val base = value * factor(kind, from)
        return base / factor(kind, to)
    }

    private fun toCelsius(unit: String, v: Double): Double? = when (unit) {
        "摄氏度 °C" -> v
        "华氏度 °F" -> (v - 32) / 1.8
        "开尔文 K" -> v - 273.15
        else -> null
    }

    private fun fromCelsius(unit: String, c: Double): Double? = when (unit) {
        "摄氏度 °C" -> c
        "华氏度 °F" -> c * 1.8 + 32
        "开尔文 K" -> c + 273.15
        else -> null
    }

    /** 结果格式化：大数用科学计数之外的紧凑表示，小数最多 4 位。 */
    fun fmt(v: Double): String {
        val a = Math.abs(v)
        return when {
            a == 0.0 -> "0"
            a >= 1e9 || a < 1e-6 -> String.format(java.util.Locale.US, "%.3e", v)
            a >= 100 -> String.format(java.util.Locale.US, "%.1f", v)
            else -> String.format(java.util.Locale.US, "%.4f", v).trimEnd('0').trimEnd('.')
        }
    }
}

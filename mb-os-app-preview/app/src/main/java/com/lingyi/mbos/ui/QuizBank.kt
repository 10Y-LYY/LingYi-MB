/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

import kotlin.random.Random

/**
 * 知识问答题库（「知识问答」游戏用）：全部为判断题，
 * 开发板只有 A / B 两个键，A =「说法正确」，B =「说法错误」。
 */
object QuizBank {

    data class Question(val id: Int, val text: String, val answer: Boolean, val explain: String)

    val QUESTIONS: List<Question> = listOf(
        Question(1, "micro:bit V2 的 LED 矩阵是 5×5 共 25 颗。", true, "板正面上方就是 25 颗可编程 LED。"),
        Question(2, "声音传感器是 micro:bit V2 新增的。", true, "V2 加了麦克风，可以感知声音大小。"),
        Question(3, "micro:bit 上有且只有 2 个可编程按钮。", true, "正面 A / B 两个按钮，背面还有复位键。"),
        Question(4, "micro:bit 使用 USB 线连接电脑，不能蓝牙连接。", false, "V2 带蓝牙低功耗（BLE），本 App 就靠它无线连接。"),
        Question(5, "指南针使用前需要校准，否则读数会不准。", true, "首次使用按提示转圈校准，远离磁铁。"),
        Question(6, "micro:bit 的处理器是 32 位 ARM。", true, "Nordic nRF52833，主频 64MHz。"),
        Question(7, "光线传感器是靠摄像头感光的。", false, "它是用 LED 矩阵反测环境光，没有摄像头。"),
        Question(8, "micro:bit 可以直接驱动电机。", false, "IO 电流很小，驱动电机需要外接驱动板。"),
        Question(9, " gestures 里的「自由落体」可以在开发板被抛起时触发。", true, "加速度骤降会被判为 freefall。"),
        Question(10, "水的沸点在标准大气压下是 100 摄氏度。", true, "海拔越高沸点越低。"),
        Question(11, "声音在空气中比在水中传播得快。", false, "水中约 1500m/s，空气中约 340m/s。"),
        Question(12, "蝙蝠靠超声波定位。", true, "回声定位，频率高于人耳听力上限。"),
        Question(13, "光速比声速快得多，所以先看到闪电后听到雷声。", true, "雷电同时发生，光几乎瞬间到达。"),
        Question(14, "Python 是一种解释型语言。", true, "MicroPython 也是解释执行。"),
        Question(15, "二进制 1010 等于十进制的 12。", false, "1010 = 8+2 = 10。"),
        Question(16, "CPU 是计算机的「大脑」。", true, "负责执行指令与运算。"),
        Question(17, "地球自转一周大约是 24 小时。", true, "一天由此而来。"),
        Question(18, "植物白天释放二氧化碳、夜间释放氧气。", false, "光合作用白天产氧，说反了。"),
        Question(19, "彩虹通常在雨后面对太阳的方向出现。", false, "彩虹在太阳的反方向。"),
        Question(20, "莫尔斯电码里 SOS 是「三短三长三短」。", true, "... --- ..."),
    )

    /** 随机抽一题（可排除已出过的 id）。 */
    fun random(exclude: Set<Int> = emptySet(), random: Random = Random.Default): Question {
        val pool = QUESTIONS.filter { it.id !in exclude }.ifEmpty { QUESTIONS }
        return pool[random.nextInt(pool.size)]
    }

    /** 题库完整性自检（单测用）。 */
    fun isConsistent(): Boolean {
        val ids = QUESTIONS.map { it.id }
        return ids.size == ids.toSet().size &&
            QUESTIONS.all { it.text.isNotBlank() && it.explain.isNotBlank() && it.id > 0 }
    }
}

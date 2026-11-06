/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

import com.lingyi.mbos.util.PointerProfile
import com.lingyi.mbos.util.Prefs
import kotlin.math.abs
import kotlin.math.sign

/**
 * 高级指针处理引擎（纯逻辑，无 Android 依赖，便于单测）。
 *
 * 把固件每帧上报的原始倾斜增量 (dx, dy) 与按键状态 (a, b) 转换成一组动作意图：
 *   · 死区：增量绝对值小于死区视为静默，消除静止时的微漂移；
 *   · 平滑：低通滤波，抑制高频抖动（smoothing=0 关闭，原值直通）；
 *   · 灵敏度曲线：线性（v×k）或指数（小幅更稳、大幅更快）；
 *   · 滚动模式：仅纵向分量生效、横向忽略，纵向增量放大为页面滚动；
 *   · 按键映射：A / B 可自定义为 单击 / 双击 / 拖动切换。
 *
 * 实时配置通过 [profile] 供给，前台服务每次消费时取最新值，设置页改完立即生效。
 */
class MouseEngine(private val profile: () -> PointerProfile) {

    /** 平滑后的上一帧增量（低通状态）。 */
    private var sx = 0f
    private var sy = 0f

    /** 按键边沿跟踪。 */
    private var lastA = false
    private var lastB = false

    /** 拖动态（由「拖动切换」动作维持，跨帧保留）。 */
    var dragging = false
        private set

    sealed class Action {
        /** 指针移动（已含灵敏度与曲线），单位与固件增量同尺度。 */
        data class Move(val dx: Float, val dy: Float) : Action()
        object Tap : Action()
        object DoubleTap : Action()
        data class Finger(val down: Boolean) : Action()
    }

    /** 处理一帧原始数据，返回本帧应执行的动作列表（可能为空）。 */
    fun consume(rawDx: Int, rawDy: Int, a: Boolean, b: Boolean): List<Action> {
        val p = profile()
        val out = mutableListOf<Action>()

        // 死区
        var dx = rawDx.toFloat()
        var dy = rawDy.toFloat()
        if (abs(dx) < p.deadzone) dx = 0f
        if (abs(dy) < p.deadzone) dy = 0f

        // 平滑（低通）：new = old*(1-a) + raw*a
        val sm = p.smoothing.coerceIn(0f, 1f)
        if (sm > 0f) {
            sx = sx * (1f - sm) + dx * sm
            sy = sy * (1f - sm) + dy * sm
            dx = sx
            dy = sy
        } else {
            sx = dx
            sy = dy
        }

        // 灵敏度曲线
        dx = applyCurve(dx, p)
        dy = applyCurve(dy, p)

        // 滚动模式：仅纵向生效，横向忽略并略微放大纵向
        if (p.scrollMode) {
            dx = 0f
            dy *= 1.4f
        }

        // 按键映射（仅处理按下沿触发，抬起沿用于结束拖动）
        if (a && !lastA) mapButton(p.btnA, downEdge = true)?.let { out += it }
        if (!a && lastA) mapButton(p.btnA, downEdge = false)?.let { out += it }
        if (b && !lastB) mapButton(p.btnB, downEdge = true)?.let { out += it }
        if (!b && lastB) mapButton(p.btnB, downEdge = false)?.let { out += it }
        lastA = a
        lastB = b

        // 移动（死区 + 平滑 + 曲线后仍有量才移动）
        if (dx != 0f || dy != 0f) out += Action.Move(dx, dy)
        return out
    }

    private fun applyCurve(v: Float, p: PointerProfile): Float {
        if (p.curve != Prefs.POINTER_CURVE_EXP) return v * p.sens
        // 指数曲线：基准灵敏 + 超过阈值的额外加速（大幅更跟手），小幅移动保持原速更稳。
        val mag = abs(v)
        val base = mag * p.sens
        val boost = if (mag > 4f) mag * (mag - 4f) * 0.06f * p.sens else 0f
        return sign(v) * (base + boost)
    }

    private fun mapButton(action: String, downEdge: Boolean): Action? {
        if (!downEdge) {
            // 抬起边：仅「拖动切换」在仍按住时结束拖动
            if (action == Prefs.POINTER_ACTION_DRAG && dragging) {
                dragging = false
                return Action.Finger(false)
            }
            return null
        }
        return when (action) {
            Prefs.POINTER_ACTION_TAP -> Action.Tap
            Prefs.POINTER_ACTION_DOUBLE -> Action.DoubleTap
            Prefs.POINTER_ACTION_DRAG -> {
                dragging = !dragging
                Action.Finger(dragging)
            }
            else -> null
        }
    }

    /** 清空内部状态（重连 / 重新校准时调用，避免跨段串扰）。 */
    fun reset() {
        sx = 0f
        sy = 0f
        lastA = false
        lastB = false
        dragging = false
    }
}

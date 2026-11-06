/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.google.android.material.color.MaterialColors

/**
 * 陀螺仪指针的常驻悬浮控制窗（TYPE_APPLICATION_OVERLAY），Material 3 视觉风格。
 *  · 可在任意应用上层拖动（抓住状态文字即可移动）；
 *  · 提供「停止 / 居中 / 灵敏度−+」快捷操作；
 *  · 退出指针界面甚至退出 App 后依旧可见可操作（由前台服务持有）。
 *
 * 视觉：M3 表面卡片（colorSurface + 大圆角 + 高程），状态以「引导圆点 + 文本」表达，
 * 操作按钮为 primaryContainer 色调胶囊。
 *
 * 重要：容器刻意用普通 LinearLayout + GradientDrawable 实现 M3 表面卡片，
 * 而不用 MaterialCardView——MaterialCardView 在构造时会做主题强校验
 * （ThemeEnforcement 要求 MaterialComponents 主题），而前台 Service 的上下文主题
 * 在某些机型上不满足该校验，会直接抛 IllegalArgumentException 使 onStartCommand 崩溃
 * （进程被杀、连接随之断开）。
 *
 * 需要 SYSTEM_ALERT_WINDOW 权限；若未授权则 show() 直接返回 false，
 * 前台服务照常运行（仅缺这个可视窗），不阻塞指针功能。
 */
class FloatingPointerWindow(
    private val context: Context,
    private val onStop: () -> Unit,
    private val onRecenter: () -> Unit,
    private val onSensDown: () -> Unit,
    private val onSensUp: () -> Unit
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var statusText: TextView? = null
    private var dotView: android.view.View? = null

    /** 当前连接态（用于无告警时回退显示）。 */
    private var connected = false

    /** 临时告警文案（如「无障碍服务未运行」）；为 null 时回退到连接态文案。 */
    private var warn: String? = null

    /**
     * M3 色板：MaterialColors.getColor 仅解析属性并带兜底色，不做主题强校验，
     * 在任意上下文（含 Service）都安全；若主题未提供该令牌则回退到硬编码 M3 基色。
     */
    private val colorSurface = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, 0xFFFDF7FF.toInt())
    private val colorOnSurface = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, 0xFF1C1B1F.toInt())
    private val colorOnSurfaceVariant = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF49454F.toInt())
    private val colorPrimary = MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, 0xFF6750A4.toInt())
    private val colorOnPrimaryContainer = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnPrimaryContainer, 0xFF21005D.toInt())
    private val colorPrimaryContainer = MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimaryContainer, 0xFFE9DDFF.toInt())
    private val colorError = MaterialColors.getColor(context, com.google.android.material.R.attr.colorError, 0xFFB3261E.toInt())
    private val colorOutline = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOutline, 0xFF79747E.toInt())

    /** 是否具备悬浮窗权限。 */
    fun canShow(): Boolean = Settings.canDrawOverlays(context)

    fun show(): Boolean {
        if (root != null) return true
        if (!canShow()) return false
        val d = context.resources.displayMetrics.density

        // 容器：M3 表面卡片（普通 LinearLayout，规避 MaterialCardView 主题强校验崩溃）
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20 * d
                setColor(colorSurface)
            }
            setPadding(
                (12 * d).toInt(), (8 * d).toInt(),
                (12 * d).toInt(), (8 * d).toInt()
            )
        }
        ViewCompat.setElevation(card, 6 * d)

        // 引导圆点（表达状态：运行=主色，告警=错误色，断开=描边色）
        val dot = android.view.View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colorPrimary)
            }
        }.also { v ->
            val ds = (10 * d).toInt()
            val lp = LinearLayout.LayoutParams(ds, ds).apply { marginEnd = (8 * d).toInt() }
            v.layoutParams = lp
        }
        dotView = dot

        val status = TextView(context).apply {
            text = context.getString(R.string.pointer_float_running)
            setTextColor(colorOnSurface)
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding((6 * d).toInt(), 0, (6 * d).toInt(), 0)
        }
        statusText = status

        val btnMinus = makeChip(context, "−", onSensDown)
        val btnCenter = makeChip(context, "⊙", onRecenter)
        val btnStop = makeChip(context, "■", onStop)

        card.addView(dot)
        card.addView(status)
        card.addView(btnMinus)
        card.addView(btnCenter)
        card.addView(btnStop)

        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (16 * d).toInt()
            y = (140 * d).toInt()
        }

        // 拖动：抓住状态文字移动整个卡片（按钮各自消费点击，不触发拖动）
        var lastX = 0f
        var lastY = 0f
        status.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = ev.rawX - p.x
                    lastY = ev.rawY - p.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    p.x = (ev.rawX - lastX).toInt().coerceAtLeast(0)
                    p.y = (ev.rawY - lastY).toInt().coerceAtLeast(0)
                    runCatching { wm.updateViewLayout(card, p) }
                    true
                }
                else -> false
            }
        }

        runCatching { wm.addView(card, p) }
        root = card
        params = p
        refreshStatus()
        return true
    }

    fun setConnected(connected: Boolean) {
        this.connected = connected
        refreshStatus()
    }

    /** 设置/清除临时告警（无障碍服务未运行等）；null 表示恢复正常连接态文案。 */
    fun setWarn(text: String?) {
        warn = text
        refreshStatus()
    }

    private fun refreshStatus() {
        val text = warn ?: context.getString(
            if (connected) R.string.pointer_float_running else R.string.pointer_float_disconnected
        )
        statusText?.text = text
        // 告警态用错误色强调文本，其余用 M3 onSurface
        statusText?.setTextColor(if (warn != null) colorError else colorOnSurface)
        // 引导圆点颜色：告警=错误色，断开=描边色，运行=主色
        val dotColor = when {
            warn != null -> colorError
            connected -> colorPrimary
            else -> colorOutline
        }
        (dotView?.background as? GradientDrawable)?.setColor(dotColor)
    }

    fun hide() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        params = null
        statusText = null
        dotView = null
    }

    /** M3 胶囊按钮：primaryContainer 背景 + onPrimaryContainer 文本。 */
    private fun makeChip(ctx: Context, label: String, onClick: () -> Unit): TextView =
        TextView(ctx).apply {
            text = label
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(colorOnPrimaryContainer)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12 * ctx.resources.displayMetrics.density
                setColor(colorPrimaryContainer)
            }
            val pd = (10 * ctx.resources.displayMetrics.density).toInt()
            setPadding(pd, (4 * ctx.resources.displayMetrics.density).toInt(), pd, (4 * ctx.resources.displayMetrics.density).toInt())
            minimumWidth = 0
            minWidth = 0
            setOnClickListener { onClick() }
        }
}

/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.accessibilityservice.GestureDescription

/**
 * 无线指针（陀螺仪鼠标）无障碍服务：
 * 把 micro:bit 的倾斜增量转换成系统级触摸手势——
 *   · 移动：仅更新自绘跟随圆点（视觉跟随）。
 *         说明：Android 不允许第三方 App 移动「系统鼠标指针」，系统本就没有
 *         可被第三方控制的光标，因此移动只更新我们自己的圆点，点击/拖动才派发真实手势；
 *   · tap：在自绘圆点处单击；
 *   · setFingerDown：按下 / 抬起（按住 B 进入拖动，倾斜即拖拽）。
 *
 * 关键修复：dispatchGesture 与 WindowManager 视图更新必须在【主线程】执行——
 * 后台线程（USB 读线程 / BLE Binder 线程）调用会导致手势在多数机型静默失败，
 * 表现为「数据有、指针不动、AB 无反应」。本服务所有对外 API 内部统一切到主线程。
 */
open class PointerAccessibilityService : AccessibilityService() {

    companion object {
        private var instance: PointerAccessibilityService? = null

        fun isRunning(): Boolean = instance != null

        /** 当前运行中的服务实例（未启用/未运行时为 null）。 */
        fun current(): PointerAccessibilityService? = instance

        /** 用户是否已在系统设置里启用本服务。 */
        fun isEnabled(context: Context): Boolean {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
                as? android.view.accessibility.AccessibilityManager ?: return false
            val expected = ComponentName(context, PointerAccessibilityService::class.java)
            return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { info ->
                    info.resolveInfo?.serviceInfo?.let { si ->
                        ComponentName(si.packageName, si.name) == expected
                    } == true
                }
        }

        /**
         * 服务实例就绪（onServiceConnected）时回调，供 PointerService 在「无障碍服务晚于指针
         * 服务启动」的场景下补发 MOUSE:ON 并重建光标圆点；置空表示已销毁。
         */
        var onConnected: (() -> Unit)? = null

        /** 仅供单测注入实例，不用于生产代码。 */
        internal fun setInstanceForTest(svc: PointerAccessibilityService?) { instance = svc }

        /** 跳转系统无障碍设置页，让用户手动启用。 */
        fun openSettings(context: Context) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
    }

    private var windowManager: WindowManager? = null
    private var cursorView: View? = null
    private var cursorParams: WindowManager.LayoutParams? = null

    /** 光标圆点是否可见：开发板断开时隐藏，重连时显示。 */
    private var cursorVisible = true

    /** 虚拟指针位置（屏幕坐标）与「手指是否按下」。 */
    private var px = 0f
    private var py = 0f
    private var fingerDown = false

    private var screenW = 0
    private var screenH = 0

    /** API 30+ 支持 willContinue 连续手势；以下版本用离散划动作降级。 */
    private val canContinue: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /**
     * 所有手势派发与窗口操作必须走主线程：后台线程调用 dispatchGesture 在多数机型静默失败。
     * 对外 API 内部统一 post 到该 handler 后再执行。
     */
    private val mainHandler = Handler(Looper.getMainLooper())
    private val gestureHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val dm = resources.displayMetrics
        screenW = dm.widthPixels
        screenH = dm.heightPixels
        px = screenW / 2f
        py = screenH / 2f
        if (cursorVisible) showCursor()
        // 通知 PointerService：无障碍通道已就绪，可补发 MOUSE 流并接管光标
        runCatching { onConnected?.invoke() }
    }

    override fun onDestroy() {
        removeCursorOverlay()
        instance = null
        onConnected = null
        super.onDestroy()
    }

    override fun onInterrupt() {
        // 连续手势被系统中断：抬起手指
        fingerDown = false
    }

    /** 本服务只通过手势派发交互，不消费任何无障碍事件。 */
    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        // no-op：不需要监听界面内容
    }

    // ---------- 指针操作（由 PointerService / PointerModeActivity 调用） ----------
    // 注意：这些方法可在任意线程调用，内部统一切到主线程执行手势与窗口更新。

    /**
     * 倾斜增量 → 指针移动。
     * 普通模式只更新自绘圆点（视觉跟随，无需手势——系统没有可被第三方移动的光标）；
     * 拖动模式（按住 B，fingerDown）才派发 willContinue 连续手势形成「按住拖动」。
     */
    open fun moveBy(dx: Float, dy: Float) {
        mainHandler.post { moveByInternal(dx, dy) }
    }

    /** 单击（按钮 A）。 */
    fun tap() {
        mainHandler.post { tapInternal() }
    }

    /** 双击（按钮 A 可映射为双击）：连续两次轻点。 */
    fun doubleTap() {
        mainHandler.post {
            tapInternal()
            gestureHandler.postDelayed({ tapInternal() }, 60)
        }
    }

    /** 按下 / 抬起（按钮 B）：按下后倾斜即拖动，再按一次抬起。 */
    fun setFingerDown(down: Boolean) {
        mainHandler.post { setFingerDownInternal(down) }
    }

    fun isFingerDown(): Boolean = fingerDown

    /**
     * 设置光标圆点可见性。
     * 开发板断开时调用 false：移除跟随圆点（「指针消失」）；
     * 重连时调用 true：重新绘制。不影响手势派发本身。
     */
    fun setCursorVisible(visible: Boolean) {
        mainHandler.post {
            if (cursorVisible == visible) return@post
            cursorVisible = visible
            if (visible) showCursor() else hideCursor()
        }
    }

    /** 指针回屏幕中心。 */
    fun recenter() {
        mainHandler.post {
            px = screenW / 2f
            py = screenH / 2f
            updateCursor()
        }
    }

    // ---------- 内部实现（主线程） ----------

    private fun moveByInternal(dx: Float, dy: Float) {
        val nx = (px + dx).coerceIn(0f, (screenW - 1).toFloat())
        val ny = (py + dy).coerceIn(0f, (screenH - 1).toFloat())
        if (nx == px && ny == py) return
        // 仅拖动模式（按住 B）派发连续手势；普通模式只移动自绘圆点
        if (fingerDown && canContinue) {
            stroke(px, py, nx, ny, willContinue = true, durationMs = 120)
        }
        px = nx
        py = ny
        updateCursor()
    }

    /** 在自绘圆点处轻点，形成单击。 */
    private fun tapInternal() {
        stroke(px, py, px + 0.1f, py, willContinue = false, durationMs = 40)
    }

    private fun setFingerDownInternal(down: Boolean) {
        if (down == fingerDown) return
        fingerDown = down
        if (down) {
            // 按住：willContinue 长时保持「指尖按下」，倾斜即拖动；释放由抬起动作终止
            stroke(px, py, px + 0.1f, py, willContinue = true, durationMs = 300_000)
        } else {
            // 抬起：结束连续手势
            stroke(px, py, px + 0.1f, py, willContinue = false, durationMs = 1)
        }
        updateCursor()
    }

    private fun stroke(
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        willContinue: Boolean,
        durationMs: Long
    ) {
        val path = Path().apply {
            moveTo(fromX, fromY)
            lineTo(toX, toY)
        }
        val stroke = if (willContinue && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            GestureDescription.StrokeDescription(path, 0, durationMs, true)
        } else {
            GestureDescription.StrokeDescription(path, 0, durationMs)
        }
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        // 本方法已在主线程执行，dispatchGesture 可正常派发
        runCatching { dispatchGesture(gesture, null, null) }
    }

    private fun showCursor() {
        if (cursorView != null) return
        val size = (26 * resources.displayMetrics.density).toInt()
        val dot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xCCF2A900.toInt())
                setStroke((2 * resources.displayMetrics.density).toInt(), 0xFFFFFFFF.toInt())
            }
        }
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (px - size / 2).toInt()
            y = (py - size / 2).toInt()
        }
        runCatching { windowManager?.addView(dot, params) }
        cursorView = dot
        cursorParams = params
    }

    private fun hideCursor() {
        removeCursorOverlay()
    }

    private fun updateCursor() {
        val view = cursorView ?: return
        val params = cursorParams ?: return
        params.x = (px - view.width / 2f).toInt().coerceAtLeast(0)
        params.y = (py - view.height / 2f).toInt().coerceAtLeast(0)
        runCatching { windowManager?.updateViewLayout(view, params) }
    }

    private fun removeCursorOverlay() {
        val view = cursorView ?: return
        runCatching { windowManager?.removeView(view) }
        cursorView = null
        cursorParams = null
    }
}

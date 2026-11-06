/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.dev

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.view.ContextThemeWrapper
import com.google.android.material.color.MaterialColors
import com.lingyi.mbos.R
import com.lingyi.mbos.ui.M3Kit
import com.lingyi.mbos.util.ActivityTopHolder
import kotlin.math.abs

/**
 * 开发者测试版专属：悬浮调试窗（组件检视器）。
 *
 * 形态：一个可拖动的「调试」气泡（TYPE_APPLICATION_OVERLAY）。点一下气泡进入「抓取模式」，
 * 此时全屏半透明蒙层，点屏幕任意组件即在该点做命中检测，弹出一个面板显示：
 *   · 类名（代码标识，如 MaterialButton）
 *   · 资源 ID 名称（如 btnViewAll）
 *   · 专属稳定编号（视图树路径索引链 + 哈希，每次一致，便于反馈定位）
 *   · 所属页面（Activity 名）
 * 再点气泡退出抓取模式。
 *
 * 注意：抓取仅在**本 App 自身的 Activity** 内生效（跨 App 命中需要系统级辅助，超出范围）。
 * 全部用普通 View + GradientDrawable 构建，绝不使用 MaterialCardView（Service 上下文会因
 * 主题强校验崩溃，见历史坑）。
 *
 * 仅 devtest / preview flavor 注册（manifest android:enabled=@bool/devtest_only）。
 */
class ComponentInspectorService : Service() {

    private lateinit var wm: WindowManager
    private var bubble: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var captureLayer: FrameLayout? = null
    private var panel: View? = null
    private var capturing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.inspector_permission, Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }
        showBubble()
        return START_STICKY
    }

    override fun onDestroy() {
        removeBubble()
        removeCapture()
        removePanel()
        super.onDestroy()
    }

    private fun themed() = ContextThemeWrapper(this, R.style.Theme_MBOS)

    private fun accent(ctx: Context): Int =
        MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorPrimary, Color.parseColor("#0E7C86"))

    private fun overlayType() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    else WindowManager.LayoutParams.TYPE_PHONE

    // ---------------- 气泡 ----------------

    private fun showBubble() {
        if (bubble != null) return
        val ctx = themed()
        val pad = M3Kit.dp(ctx, 18)
        bubble = TextView(ctx).apply {
            text = getString(R.string.inspector_bubble)
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(accent(ctx))
            }
            setOnTouchListener { _, ev -> onBubbleTouch(ev) }
        }
        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = M3Kit.dp(ctx, 16)
            y = M3Kit.dp(ctx, 120)
        }
        wm.addView(bubble, bubbleParams)
    }

    private fun removeBubble() {
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
        bubbleParams = null
    }

    private var downX = 0f
    private var downY = 0f
    private var initX = 0
    private var initY = 0
    private var dragged = false

    private fun onBubbleTouch(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                initX = bubbleParams?.x ?: 0
                initY = bubbleParams?.y ?: 0
                dragged = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (ev.rawX - downX).toInt()
                val dy = (ev.rawY - downY).toInt()
                if (abs(dx) > 8 || abs(dy) > 8) dragged = true
                bubbleParams?.x = initX + dx
                bubbleParams?.y = initY + dy
                wm.updateViewLayout(bubble, bubbleParams)
            }
            MotionEvent.ACTION_UP -> {
                if (!dragged) onBubbleTap()
            }
        }
        return true
    }

    private fun onBubbleTap() {
        if (capturing) removeCapture() else startCapture()
    }

    // ---------------- 抓取模式 ----------------

    private fun startCapture() {
        if (captureLayer != null) return
        val ctx = themed()
        captureLayer = FrameLayout(ctx).apply {
            setBackgroundColor(Color.parseColor("#33000000"))
            setOnTouchListener { _, ev ->
                if (ev.action == MotionEvent.ACTION_DOWN) {
                    val x = ev.rawX.toInt()
                    val y = ev.rawY.toInt()
                    removeCapture()
                    doInspect(x, y)
                    true
                } else false
            }
        }
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(captureLayer, p)
        capturing = true
        Toast.makeText(this, R.string.inspector_capture_hint, Toast.LENGTH_SHORT).show()
    }

    private fun removeCapture() {
        captureLayer?.let { runCatching { wm.removeView(it) } }
        captureLayer = null
        capturing = false
    }

    // ---------------- 命中检测 ----------------

    private fun doInspect(screenX: Int, screenY: Int) {
        val activity = ActivityTopHolder.top
        if (activity == null) {
            Toast.makeText(this, R.string.inspector_none, Toast.LENGTH_SHORT).show()
            return
        }
        val decor = activity.window?.decorView ?: run {
            Toast.makeText(this, R.string.inspector_none, Toast.LENGTH_SHORT).show()
            return
        }
        val hit = hitTest(decor, screenX, screenY)
        if (hit == null) {
            Toast.makeText(this, R.string.inspector_none, Toast.LENGTH_SHORT).show()
            return
        }
        val idName = runCatching {
            if (hit.id != View.NO_ID) hit.resources.getResourceEntryName(hit.id) else null
        }.getOrNull()
        // 1) 优先沿当前 Activity 的 decor 求路径（最贴合「所属页面」语义）
        val underDecor = pathUnder(decor, hit)
        // 2) 兜底：沿控件自身父链向上走到真实根，覆盖对话框 / BottomSheet / 悬浮窗等子窗口里的组件
        val (selfPath, rootClass) = pathFromSelfRoot(hit)
        val path = underDecor ?: selfPath
        val scope = if (underDecor != null) activity.javaClass.simpleName else rootClass
        // 专属编号：活动(或子窗口根) + 视图路径 + 资源 ID 三者拼接后取稳定哈希，
        // 保证【每个组件都唯一且可复现】，不再出现「有的没编号」。
        val signature = "$scope|$path|${idName ?: ""}"
        val uid = signature.hashCode().toUInt().toString(36)
        showInfoPanel(
            className = hit.javaClass.name,
            idName = idName ?: "(无 id)",
            uid = "#$uid",
            path = path.ifBlank { "(根节点)" },
            activityName = activity.javaClass.simpleName
        )
    }

    private fun hitTest(view: View, x: Int, y: Int): View? {
        if (view.visibility != View.VISIBLE) return null
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        val left = loc[0]
        val top = loc[1]
        if (x < left || x > left + view.width || y < top || y > top + view.height) return null
        var deepest: View? = null
        if (view is ViewGroup) {
            for (i in view.childCount - 1 downTo 0) {
                val h = hitTest(view.getChildAt(i), x, y)
                if (h != null) { deepest = h; break }
            }
        }
        return deepest ?: view
    }

    /**
     * 从 [root] 向下 DFS 求 [target] 的索引路径（如 "0/2/1"）。
     * 找到返回路径；找不到（target 不属于该 root 子树，常见于对话框等子窗口组件）返回 null。
     */
    private fun pathUnder(root: View, target: View): String? {
        val chain = mutableListOf<Int>()
        fun dfs(v: View): Boolean {
            if (v === target) return true
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) {
                    chain.add(i)
                    if (dfs(v.getChildAt(i))) return true
                    chain.removeAt(chain.lastIndex)
                }
            }
            return false
        }
        return if (dfs(root)) chain.joinToString("/") else null
    }

    /**
     * 沿 [target] 自身父链向上走到真实根，求索引路径。
     * 不依赖 Activity decor，因此对话框 / BottomSheet / 悬浮窗里的组件也能稳定算出路径。
     * 返回 (路径, 真实根类名)；[target] 无父时路径为空串、根为自身。
     */
    private fun pathFromSelfRoot(target: View): Pair<String, String> {
        val idxs = mutableListOf<Int>()
        var v = target
        var root: View = target
        while (v.parent is ViewGroup) {
            val p = v.parent as ViewGroup
            idxs.add(p.indexOfChild(v))
            root = p
            v = p
        }
        idxs.reverse()
        return idxs.joinToString("/") to root.javaClass.simpleName
    }

    // ---------------- 信息面板 ----------------

    private fun showInfoPanel(className: String, idName: String, uid: String, path: String, activityName: String) {
        removePanel()
        val ctx = themed()
        val pad = M3Kit.dp(ctx, 16)
        val surface = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorSurface, Color.WHITE)
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(surface)
                cornerRadius = M3Kit.dp(ctx, 16).toFloat()
            }
        }
        card.addView(row(ctx, getString(R.string.inspector_class), className))
        card.addView(row(ctx, getString(R.string.inspector_id), idName))
        card.addView(row(ctx, getString(R.string.inspector_uid), uid))
        card.addView(row(ctx, getString(R.string.inspector_path), path))
        card.addView(row(ctx, getString(R.string.inspector_activity), activityName))
        val close = TextView(ctx).apply {
            text = "关闭"
            setTextColor(accent(ctx))
            textSize = 15f
            setPadding(0, M3Kit.dp(ctx, 12), 0, 0)
            setOnClickListener { removePanel() }
        }
        card.addView(close)

        val scroll = ScrollView(ctx).apply { addView(card) }
        panel = scroll
        val p = WindowManager.LayoutParams(
            M3Kit.dp(ctx, 300),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        wm.addView(scroll, p)
    }

    private fun row(ctx: Context, label: String, value: String): View {
        val pad = M3Kit.dp(ctx, 4)
        val labelColor = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)
        val valueColor = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
        val line = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, pad, 0, pad) }
        val l = TextView(ctx).apply {
            text = "$label："
            textSize = 13f
            setTextColor(labelColor)
        }
        val v = TextView(ctx).apply {
            text = value
            textSize = 13f
            setTextColor(valueColor)
            maxWidth = M3Kit.dp(ctx, 220)
        }
        line.addView(l)
        line.addView(v)
        return line
    }

    private fun removePanel() {
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
    }

    companion object {
        @Volatile
        var running: Boolean = false
            private set

        fun start(ctx: Context) {
            if (running) return
            ctx.startService(Intent(ctx, ComponentInspectorService::class.java))
            running = true
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, ComponentInspectorService::class.java))
            running = false
        }
    }
}

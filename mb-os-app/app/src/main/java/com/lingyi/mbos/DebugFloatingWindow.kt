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
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.Choreographer
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import androidx.core.widget.NestedScrollView
import com.lingyi.mbos.ble.ConnectionHub
import com.lingyi.mbos.ui.FeatureCatalog
import com.lingyi.mbos.ui.FeatureOpener
import com.lingyi.mbos.ui.M3Kit
import com.lingyi.mbos.ui.Protocol
import com.lingyi.mbos.util.ActionTrail
import com.lingyi.mbos.util.AppLog
import com.lingyi.mbos.util.AutoReconnect
import com.lingyi.mbos.util.DebugBus
import com.lingyi.mbos.util.DebugReport
import com.lingyi.mbos.util.Prefs
import com.lingyi.mbos.util.ViewTreeCapture

/**
 * 全局调试控制台悬浮窗（TYPE_APPLICATION_OVERLAY）——与「问题上报检查器」
 * 整合为同一入口：点一下气泡展开，九个页签覆盖真实调试工具：
 *
 *  1. 日志   ：实时 AppLog（自动刷新 / 复制 / 清空）
 *  2. 连接   ：连接层快照 + 自动重状态 + 探活 / 断开 / 重连
 *  3. 抓包   ：DebugBus 协议抓包开关与内容（App↔设备双向）
 *  4. 指令   ：任意串口指令注入 + 模拟设备回传（传感器数据演练）
 *  5. 元素   ：当前界面元素树（id / 类名 / 文案 / 可点击性）
 *  6. 存储   ：日志文件占用 + SharedPreferences 键值检查
 *  7. 性能   ：内存 / 线程 / FPS 实时监控
 *  8. 功能   ：FeatureCatalog 全部 88 项功能直达（无需回控制台翻找）
 *  9. 上报   ：问题上报检查器——自动附带当前界面、操作轨迹、日志、设备信息，
 *             用户描述 bug 位置后一键复制 / 保存 / 分享
 *
 * 视觉：M3 表面卡片 + 主色容器选中态。全部用普通 View + GradientDrawable
 * 程序化绘制（不用 MaterialCardView）——前台 Service 上下文做主题强校验
 * 在部分机型不稳（v3.1.8 指针悬浮窗教训），普通 View 零风险。
 */
class DebugFloatingWindow(
    private val ctx: Context,
    private val cb: Callbacks
) {

    interface Callbacks {
        /** 打开开发者页（悬浮窗里跳原生页面用）。 */
        fun onOpenDevPage()

        /** 强制崩溃（验证崩溃日志链路；会在 3 秒后杀进程）。 */
        fun onForceCrash()

        /** 关闭悬浮窗服务。 */
        fun onStopService()

        /** 轻提示。 */
        fun onToast(msg: String)

        /** 分享已保存的报告文件（路径）。 */
        fun onShare(path: String)
    }

    // ---------- 窗口基础设施 ----------

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var rootFrame: FrameLayout? = null
    private var bubbleView: View? = null
    private var panelView: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var expanded = false

    // 主题色（ContextThemeWrapper 已包 M3 主题；MaterialColors 取值带兜底）
    private val cSurface = color(com.google.android.material.R.attr.colorSurface, 0xFFFFFBFE.toInt())
    private val cOnSurface = color(com.google.android.material.R.attr.colorOnSurface, 0xFF1C1B1F.toInt())
    private val cOnSurfaceVariant = color(com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF49454F.toInt())
    private val cPrimary = color(com.google.android.material.R.attr.colorPrimary, 0xFF6750A4.toInt())
    private val cPrimaryContainer = color(com.google.android.material.R.attr.colorPrimaryContainer, 0xFFE9DDFF.toInt())
    private val cOnPrimaryContainer = color(com.google.android.material.R.attr.colorOnPrimaryContainer, 0xFF21005D.toInt())
    private val cError = color(com.google.android.material.R.attr.colorError, 0xFFB3261E.toInt())
    private val cOutline = color(com.google.android.material.R.attr.colorOutline, 0xFF79747E.toInt())
    private val cOutlineVariant = color(com.google.android.material.R.attr.colorOutlineVariant, 0xFFCAC4D0.toInt())
    private val cSurfaceVariant = color(com.google.android.material.R.attr.colorSurfaceVariant, 0xFFE7E0EC.toInt())
    private val cSecondaryContainer = color(com.google.android.material.R.attr.colorSecondaryContainer, 0xFFE8DEF8.toInt())
    private val cOnSecondaryContainer = color(com.google.android.material.R.attr.colorOnSecondaryContainer, 0xFF1E192B.toInt())

    private fun color(attr: Int, def: Int): Int =
        com.google.android.material.color.MaterialColors.getColor(ctx, attr, def)

    fun canShow(): Boolean = Settings.canDrawOverlays(ctx)

    // ---------- 页签状态 ----------

    private val tabTitles = listOf("日志", "连接", "抓包", "指令", "元素", "存储", "性能", "功能", "上报")
    private var activeTab = 0
    private var tabRow: LinearLayout? = null
    private var contentCol: LinearLayout? = null
    private var logMono: TextView? = null
    private var packetMono: TextView? = null
    private var reportEdit: EditText? = null
    private var reportMono: TextView? = null
    private var perfMono: TextView? = null
    private var viewsMono: TextView? = null
    private var storageMono: TextView? = null

    /** 页签自动刷新（日志 / 性能 / 连接 每秒刷一次，仅在展开时）。 */
    private val ticker = M3Kit.Ticker({ 1000L }, handler) {
        if (!expanded) false else {
            refreshActive()
            true
        }
    }

    // ---------- 显示 / 隐藏 ----------

    fun show(): Boolean {
        if (rootFrame != null) return true
        if (!canShow()) return false
        val root = FrameLayout(ctx)
        bubbleView = buildBubble().also { root.addView(it) }
        panelView = buildPanel().also { view ->
            view.visibility = View.GONE
            root.addView(view)
        }
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(160)
        }
        // addView 失败（少数 ROM 限制）时不持有 rootFrame，让调用方能感知失败
        val added = runCatching { wm.addView(root, p) }.isSuccess
        if (!added) {
            AppLog.e("DebugWin", "悬浮窗添加失败（WindowManager.addView 被拒绝）")
            return false
        }
        rootFrame = root
        params = p
        return true
    }

    fun hide() {
        ticker.stop()
        rootFrame?.let { runCatching { wm.removeView(it) } }
        rootFrame = null
        bubbleView = null
        panelView = null
        params = null
        expanded = false
    }

    // ---------- 气泡（入口：一点击即调出） ----------

    /**
     * 可拖动 + 可点击的气泡。
     *
     * 历史教训（v4.1.0「点不动」根因）：用 OnTouchListener 做拖动时，
     * ACTION_DOWN 返回 true 会消费掉 DOWN，View.onTouchEvent 不再执行，
     * performClick()/OnClickListener 永不触发——气泡点不开、面板内所有
     * 页签与按钮也点不动（面板根的监听先于子 View 分发）。改为自定义
     * View 自管理 onTouchEvent：自己消费事件，抬手未移动时手动 performClick()，
     * 拖动与点击互不干扰。
     */
    private inner class DragBubble(context: Context) : TextView(context) {
        private var lastX = 0f
        private var lastY = 0f
        private var moved = false

        init {
            isClickable = true
            isFocusable = false
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            val p = params ?: return super.onTouchEvent(ev)
            return when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = ev.rawX - p.x
                    lastY = ev.rawY - p.y
                    moved = false
                    // M3 Expressive 按压反馈（缩小回弹）
                    animate().scaleX(0.94f).scaleY(0.94f).setDuration(60L).start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = kotlin.math.abs(ev.rawX - lastX - p.x)
                    val dy = kotlin.math.abs(ev.rawY - lastY - p.y)
                    if (dx > dp(6) || dy > dp(6)) moved = true
                    p.x = (ev.rawX - lastX).toInt()
                    p.y = (ev.rawY - lastY).toInt()
                    rootFrame?.let { runCatching { wm.updateViewLayout(it, p) } }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    animate().scaleX(1f).scaleY(1f)
                        .setDuration(140L)
                        .setInterpolator(android.view.animation.OvershootInterpolator(1.6f))
                        .start()
                    if (!moved) performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    animate().scaleX(1f).scaleY(1f).setDuration(100L).start()
                    true
                }
                else -> super.onTouchEvent(ev)
            }
        }
    }

    private fun buildBubble(): View {
        val b = DragBubble(ctx).apply {
            text = "🐞 调试"
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(cOnPrimaryContainer)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(24).toFloat()
                setColor(cPrimaryContainer)
                setStroke(dp(1), cPrimary)
            }
            val ph = dp(16)
            setPadding(ph, dp(12), ph, dp(12))
        }
        b.setOnClickListener { expand() }
        return b
    }

    // ---------- 展开面板 ----------

    private fun expand() {
        if (expanded) return
        expanded = true
        panelView?.visibility = View.VISIBLE
        bubbleView?.visibility = View.GONE
        resizeForPanel()
        selectTab(activeTab)
        ticker.start(600L)
        AppLog.event("调试控制台", "展开悬浮窗")
        ActionTrail.record("打开调试控制台")
    }

    private fun collapse() {
        if (!expanded) return
        expanded = false
        ticker.stop()
        // 收起时先交还焦点并收起软键盘，避免键盘残留挡住界面
        setPanelFocusable(false)
        hideKeyboard()
        panelView?.visibility = View.GONE
        bubbleView?.visibility = View.VISIBLE
        resizeForBubble()
        AppLog.event("调试控制台", "收起为气泡")
    }

    private fun resizeForPanel() {
        val dm = ctx.resources.displayMetrics
        val w = minOf(dp(400), (dm.widthPixels * 0.92f).toInt())
        val h = minOf(dp(620), (dm.heightPixels * 0.80f).toInt())
        params?.let { p ->
            p.width = w
            p.height = h
            p.flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            rootFrame?.let { runCatching { wm.updateViewLayout(it, p) } }
        }
    }

    private fun resizeForBubble() {
        params?.let { p ->
            p.width = WindowManager.LayoutParams.WRAP_CONTENT
            p.height = WindowManager.LayoutParams.WRAP_CONTENT
            p.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            rootFrame?.let { runCatching { wm.updateViewLayout(it, p) } }
        }
    }

    /** 输入框聚焦时需要可获焦窗口（否则软键盘弹不出来）。 */
    private fun setPanelFocusable(on: Boolean) {
        params?.let { p ->
            p.flags = if (on) {
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            } else {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            }
            rootFrame?.let { runCatching { wm.updateViewLayout(it, p) } }
        }
    }

    // ---------- 面板骨架 ----------

    /**
     * 可拖动面板：只有事件「回流」到面板自身时（即子 View 未消费 DOWN 的
     * 空白区域）才进入 [onTouchEvent] 做拖动，因此页签 / 按钮 / 输入框 /
     * 开关的点击与聚焦完全不受影响。LinearLayout 必须置 isClickable=true，
     * 否则 View.onTouchEvent 不会消费 DOWN，后续 MOVE/UP 不会进来。
     */
    private inner class DragPanel(context: Context) : LinearLayout(context) {
        private var lastX = 0f
        private var lastY = 0f
        private var moved = false

        init {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = false
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            val p = params ?: return super.onTouchEvent(ev)
            return when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = ev.rawX - p.x
                    lastY = ev.rawY - p.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = kotlin.math.abs(ev.rawX - lastX - p.x)
                    val dy = kotlin.math.abs(ev.rawY - lastY - p.y)
                    if (dx > dp(6) || dy > dp(6)) moved = true
                    p.x = (ev.rawX - lastX).toInt()
                    p.y = (ev.rawY - lastY).toInt()
                    rootFrame?.let { runCatching { wm.updateViewLayout(it, p) } }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> super.onTouchEvent(ev)
            }
        }
    }

    private fun buildPanel(): LinearLayout {
        val panel = DragPanel(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(28).toFloat()
                setColor(cSurface)
                setStroke(dp(1), cOutlineVariant)
            }
        }
        // 头部
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val p = dp(12)
            setPadding(p, dp(10), p, dp(6))
        }
        val titleCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleCol.addView(TextView(ctx).apply {
            text = "🐞 调试控制台"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(cOnSurface)
        })
        titleCol.addView(TextView(ctx).apply {
            text = "点「上报」页签描述你遇到的问题"
            textSize = 11f
            setTextColor(cOnSurfaceVariant)
        })
        header.addView(titleCol)
        header.addView(miniButton("收起") { collapse() })
        header.addView(miniButton("关闭") { cb.onStopService() })
        panel.addView(header)

        // 页签行（内部已把 tabRow 指向真实行，这里只把可横向滚动的容器挂上面板）
        buildTabRow().also { panel.addView(it) }

        // 内容区（可滚动）
        val scroll = NestedScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            isFillViewport = true
        }
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(12)
            setPadding(p, dp(4), p, dp(12))
        }
        contentCol = col
        scroll.addView(col)
        panel.addView(scroll)

        // 底部常用动作
        val footer = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val p = dp(12)
            setPadding(p, dp(4), p, dp(10))
        }
        footer.addView(miniButton("模拟断线") {
            AppLog.event("调试", "模拟意外断线（演练自动重连）")
            ConnectionHub.debugSimulateDrop()
            cb.onToast("已模拟断线，观察「连接」页签的重连状态")
        })
        footer.addView(miniButton("强制崩溃") { cb.onForceCrash() })
        footer.addView(miniButton("开发者页") { cb.onOpenDevPage() })
        panel.addView(footer)

        // 面板整体可拖动（抓住空白处；由 DragPanel.onTouchEvent 处理，
        // 不再用 OnTouchListener——那会吞掉子 View 的点击事件）
        return panel
    }

    private fun buildTabRow(): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            val p = dp(10)
            setPadding(p, 0, p, dp(4))
        }
        tabTitles.forEachIndexed { i, t ->
            row.addView(TextView(ctx).apply {
                text = t
                textSize = 13f
                gravity = Gravity.CENTER
                val ph = dp(12)
                setPadding(ph, dp(8), ph, dp(8))
                setOnClickListener { selectTab(i) }
                M3Kit.pressFeedback(this)
                tag = i
            })
        }
        // 属性仍指向内部行（selectTab 选中态刷新用）
        tabRow = row
        // 9 个页签横排总宽约 450dp，超过面板可用宽度（窄屏约 330dp），
        // 必须可横向滚动——否则「性能 / 功能 / 上报」等页签被裁出屏幕点不到
        // （v4.1.1 真机反馈：文字显示不全）。
        return ScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(row)
        }
    }

    private fun selectTab(i: Int) {
        activeTab = i
        // 刷新页签选中态
        tabRow?.let { row ->
            for (k in 0 until row.childCount) {
                val tv = row.getChildAt(k) as? TextView ?: continue
                val selected = tv.tag == i
                tv.setTextColor(if (selected) cOnPrimaryContainer else cOnSurfaceVariant)
                tv.background = if (selected) {
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(20).toFloat()
                        setColor(cPrimaryContainer)
                    }
                } else null
                tv.setTypeface(null, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            }
        }
        // 重建内容
        contentCol?.let { col ->
            col.removeAllViews()
            when (i) {
                0 -> tabLog(col)
                1 -> tabConn(col)
                2 -> tabPackets(col)
                3 -> tabInject(col)
                4 -> tabViews(col)
                5 -> tabStorage(col)
                6 -> tabPerf(col)
                7 -> tabFeatures(col)
                8 -> tabReport(col)
            }
        }
        refreshActive()
    }

    /** 每秒刷新当前页签的动态内容（日志 / 连接 / 抓包 / 性能 / 元素）。 */
    private fun refreshActive() {
        if (!expanded) return
        when (activeTab) {
            0 -> logMono?.let { it.text = AppLog.recentLines(150).joinToString("\n") }
            1 -> refreshConnText()
            2 -> packetMono?.let { it.text = packetText() }
            4 -> viewsMono?.let { it.text = ViewTreeCapture.capture() }
            6 -> perfMono?.let { it.text = perfText() }
        }
    }

    // ---------- 页签 1：实时日志 ----------

    private fun tabLog(col: LinearLayout) {
        col.addView(caption("AppLog 最近 150 行（自动刷新）"))
        logMono = mono(260).also { col.addView(it) }
        col.addView(buttonRow(
            tonal("复制日志") {
                copyToClipboard(AppLog.recentLines(150).joinToString("\n"))
                cb.onToast("已复制日志")
            },
            outlined("清空日志") {
                AppLog.clear()
                cb.onToast("日志已清空")
                refreshActive()
            }
        ))
    }

    // ---------- 页签 2：连接检查器 ----------

    private var connMono: TextView? = null

    private fun tabConn(col: LinearLayout) {
        col.addView(caption("连接层快照 + 自动重连状态"))
        connMono = mono(200).also { col.addView(it) }
        refreshConnText()
        col.addView(buttonRow(
            tonal("INFO 探活") {
                val ok = ConnectionHub.send(Protocol.info())
                cb.onToast(if (ok) "已发送 INFO" else "未连接，发送失败")
            },
            outlined("断开") {
                ConnectionHub.disconnect()
                cb.onToast("已主动断开（不会自动重连）")
            }
        ))
        col.addView(buttonRow(
            text("立即重连") {
                val t = AutoReconnect.status().type.ifBlank { "ble" }
                runCatching { ConnectionHub.reconnect(t, ctx) }
                cb.onToast("已发起重连（$t）")
            },
            text("模拟接收数据") {
                ConnectionHub.debugEmitData("INFO:MBOS,1.0.0,25.0,120,42,0,0")
                cb.onToast("已广播一条模拟 INFO")
            }
        ))
    }

    private fun refreshConnText() {
        val ar = AutoReconnect.status()
        connMono?.text = buildString {
            append(DebugBus.connectionSnapshot())
            append("自动重连：").append(if (Prefs.isAutoReconnect(ctx)) "开" else "关").append('\n')
            val ht = ar.humanText()
            if (ht.isNotEmpty()) append(ht).append('\n')
        }
    }

    // ---------- 页签 3：协议抓包 ----------

    private fun tabPackets(col: LinearLayout) {
        val sw = Switch(ctx).apply {
            text = "启用抓包（App ↔ 设备）"
            textSize = 13f
            setTextColor(cOnSurface)
            isChecked = Prefs.isPacketCapture(ctx)
            setOnCheckedChangeListener { _, on ->
                Prefs.setPacketCapture(ctx, on)
                cb.onToast(if (on) "抓包已开启" else "抓包已关闭并清空")
            }
        }
        col.addView(sw)
        col.addView(caption("最近 120 条收发记录"))
        packetMono = mono(220).also { col.addView(it) }
        col.addView(buttonRow(
            tonal("复制抓包") {
                copyToClipboard(packetText())
                cb.onToast("已复制抓包内容")
            },
            outlined("清空") {
                DebugBus.clear()
                refreshActive()
            }
        ))
    }

    private fun packetText(): String {
        val ps = DebugBus.packets(120)
        return if (ps.isEmpty()) "(未开启抓包或无数据)" else ps.joinToString("\n") { it.render() }
    }

    // ---------- 页签 4：指令注入 ----------

    private fun tabInject(col: LinearLayout) {
        col.addView(caption("向设备发送任意串口指令"))
        val input = EditText(ctx).apply {
            hint = "如 SCROLL:HELLO / READ / TONE:440,300"
            textSize = 13f
            setTextColor(cOnSurface)
            setHintTextColor(cOnSurfaceVariant)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(cSurfaceVariant)
                setStroke(dp(1), cOutline)
            }
            val p = dp(12)
            setPadding(p, p, p, p)
            setSingleLine()
            setOnFocusChangeListener { _, hasFocus -> if (hasFocus) setPanelFocusable(true) }
        }
        col.addView(input)
        col.addView(buttonRow(
            tonal("发送") {
                val line = input.text.toString().trim()
                if (line.isEmpty()) {
                    cb.onToast("请输入指令")
                } else {
                    val ok = ConnectionHub.send(Protocol.custom(line))
                    cb.onToast(if (ok) "已发送：$line" else "未连接，发送失败")
                }
            },
            text("清空输入") { input.setText("") }
        ))
        col.addView(caption("快捷指令"))
        col.addView(chipRow(listOf(
            "INFO" to { ConnectionHub.send(Protocol.info()) },
            "READ" to { ConnectionHub.send(Protocol.read()) },
            "COMPASS" to { ConnectionHub.send(Protocol.compass()) },
            "SCROLL:HELLO" to { ConnectionHub.send(Protocol.scroll("HELLO")) },
            "CLEAR" to { ConnectionHub.send(Protocol.clear()) }
        )))
        col.addView(caption("模拟设备回传（调试接收/解析链路）"))
        val rxInput = EditText(ctx).apply {
            hint = "如 INFO:MBOS,1.0.0,25.0,120,42,0,0"
            textSize = 13f
            setTextColor(cOnSurface)
            setHintTextColor(cOnSurfaceVariant)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(cSurfaceVariant)
                setStroke(dp(1), cOutline)
            }
            val p = dp(12)
            setPadding(p, p, p, p)
            setSingleLine()
            setOnFocusChangeListener { _, hasFocus -> if (hasFocus) setPanelFocusable(true) }
        }
        col.addView(rxInput)
        col.addView(buttonRow(
            tonal("广播到 App") {
                val line = rxInput.text.toString().trim()
                if (line.isEmpty()) {
                    cb.onToast("请输入模拟数据")
                } else {
                    ConnectionHub.debugEmitData(line)
                    cb.onToast("已广播：$line")
                }
            },
            outlined("模拟传感器") {
                ConnectionHub.debugEmitData("TEMP:24.5")
                ConnectionHub.debugEmitData("LIGHT:120")
                ConnectionHub.debugEmitData("ACCEL:0,0,1024")
                cb.onToast("已注入温度/光线/加速度")
            }
        ))
    }

    // ---------- 页签 5：界面元素树 ----------

    private fun tabViews(col: LinearLayout) {
        col.addView(caption("当前顶层界面的视图树（点「上报」可自动附带）"))
        viewsMono = mono(300).also { col.addView(it) }
        col.addView(buttonRow(
            tonal("刷新") { refreshActive() },
            outlined("复制") {
                copyToClipboard(ViewTreeCapture.capture())
                cb.onToast("已复制元素树")
            }
        ))
    }

    // ---------- 页签 6：存储检查 ----------

    private fun tabStorage(col: LinearLayout) {
        col.addView(caption("日志文件与偏好存储占用"))
        storageMono = mono(260).also { col.addView(it) }
        col.addView(buttonRow(
            tonal("清理日志") {
                AppLog.clear()
                cb.onToast("日志已清理")
                refreshActive()
            },
            outlined("复制") {
                copyToClipboard(storageMono?.text?.toString() ?: "")
                cb.onToast("已复制存储信息")
            }
        ))
        storageMono?.text = storageText()
    }

    private fun storageText(): String = buildString {
        val files = AppLog.files()
        append("日志文件：").append(files.size).append(" 个 · 共 ")
            .append(AppLog.totalSize() / 1024).append(" KB\n")
        files.forEach { f ->
            append("  · ").append(f.name).append(" · ").append(f.length() / 1024).append(" KB\n")
        }
        append("\nSharedPreferences(mbos_prefs)：\n")
        runCatching {
            val sp = ctx.getSharedPreferences("mbos_prefs", Context.MODE_PRIVATE)
            sp.all.forEach { (k, v) ->
                val shown = when (v) {
                    is String -> if (k.contains("seq") || k.contains("macro")) "<${v.length}字符>" else v
                    else -> v.toString()
                }
                append("  · ").append(k).append(" = ").append(shown.take(60)).append('\n')
            }
        }.onFailure { append("  (读取失败：").append(it.message).append(")\n") }
    }

    // ---------- 页签 7：内存与性能 ----------

    private var frameCount = 0
    private var frameWindowStart = 0L
    private var fps = 0
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            frameCount++
            val now = System.currentTimeMillis()
            if (frameWindowStart == 0L) frameWindowStart = now
            if (now - frameWindowStart >= 1000L) {
                fps = frameCount
                frameCount = 0
                frameWindowStart = now
            }
            if (expanded && activeTab == 6) {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    private fun tabPerf(col: LinearLayout) {
        col.addView(caption("内存 / 线程 / FPS（展开时每秒刷新）"))
        perfMono = mono(220).also { col.addView(it) }
        col.addView(text("强制 GC（观察内存回落）") {
            System.gc()
            cb.onToast("已请求 GC")
        })
        frameCount = 0
        frameWindowStart = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun perfText(): String {
        val rt = Runtime.getRuntime()
        val used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024
        val free = rt.freeMemory() / 1024 / 1024
        val max = rt.maxMemory() / 1024 / 1024
        return buildString {
            append("已用堆：").append(used).append(" MB（空闲 ").append(free)
                .append(" / 上限 ").append(max).append("）\n")
            append("活动线程：").append(Thread.activeCount()).append('\n')
            append("FPS：").append(fps).append('\n')
            append("设备：").append(android.os.Build.MODEL)
                .append(" · Android ").append(android.os.Build.VERSION.RELEASE).append('\n')
            append("连接：").append(if (ConnectionHub.isConnected()) "已连接" else "未连接")
                .append(" · 抓包 ").append(DebugBus.size()).append(" 条\n")
        }
    }

    // ---------- 页签 8：功能直达 ----------

    private fun tabFeatures(col: LinearLayout) {
        col.addView(caption("全部 ${FeatureCatalog.FEATURES.size} 项功能直达（无需回控制台翻找）"))
        FeatureCatalog.Category.values().forEach { cat ->
            val feats = FeatureCatalog.byCategory(cat)
            col.addView(TextView(ctx).apply {
                text = "${cat.title}（${feats.size}）"
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(cPrimary)
                val p = dp(4)
                setPadding(0, dp(10), 0, p)
            })
            feats.forEach { f ->
                col.addView(TextView(ctx).apply {
                    text = f.title
                    textSize = 13f
                    setTextColor(cOnSurface)
                    val p = dp(10)
                    setPadding(p, p, p, p)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(10).toFloat()
                        setColor(cSurfaceVariant)
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(4) }
                    setOnClickListener {
                        val ok = FeatureOpener.open(f.id)
                        if (!ok) cb.onToast("请先打开一次控制台再使用直达")
                    }
                })
            }
        }
    }

    // ---------- 页签 9：问题上报 ----------

    private fun tabReport(col: LinearLayout) {
        col.addView(caption("描述你遇到的问题（界面 / 操作 / 现象）"))
        reportEdit = EditText(ctx).apply {
            hint = "例如：在「预览模式」里点刷新，画面一直停在加载中…"
            textSize = 13f
            setTextColor(cOnSurface)
            setHintTextColor(cOnSurfaceVariant)
            minLines = 4
            gravity = Gravity.TOP
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(cSurfaceVariant)
                setStroke(dp(1), cOutline)
            }
            val p = dp(12)
            setPadding(p, p, p, p)
            setOnFocusChangeListener { _, hasFocus -> if (hasFocus) setPanelFocusable(true) }
        }
        col.addView(reportEdit)
        col.addView(caption("快捷模板（点一下追加开头）"))
        col.addView(chipRow(listOf(
            "界面异常" to { appendTemplate("界面异常：在「%s」页面，%s操作后出现%s。") },
            "连接失败" to { appendTemplate("连接问题：%s方式连接时%s，已重试%d次。") },
            "闪退" to { appendTemplate("闪退：打开「%s」时应用直接退出。") },
            "数据不同步" to { appendTemplate("数据不同步：界面显示%s，实际设备%s。") }
        )))
        col.addView(tonal("生成诊断报告") {
            val desc = reportEdit?.text?.toString() ?: ""
            val report = DebugReport.build(ctx, desc)
            reportMono?.text = report
            AppLog.event("问题上报", "生成诊断报告（描述 ${desc.length} 字）")
            ActionTrail.record("生成问题上报")
            cb.onToast("报告已生成，可复制 / 保存 / 分享")
        })
        reportMono = mono(260).also { col.addView(it) }
        col.addView(buttonRow(
            tonal("复制报告") {
                val t = reportMono?.text?.toString()
                if (t.isNullOrBlank()) {
                    cb.onToast("请先点「生成诊断报告」")
                } else {
                    copyToClipboard(t)
                    cb.onToast("已复制，可直接粘贴发送")
                }
            },
            outlined("保存并分享") {
                val t = reportMono?.text?.toString()
                if (t.isNullOrBlank()) {
                    cb.onToast("请先点「生成诊断报告」")
                } else {
                    val f = DebugReport.save(ctx, t)
                    if (f != null) cb.onShare(f.absolutePath) else cb.onToast("保存失败")
                }
            }
        ))
        col.addView(caption("报告自动附带：设备信息 · 版本通道 · 连接状态 · 最近 40 条操作轨迹 · 界面元素树 · 日志尾部"))
    }

    private fun appendTemplate(tpl: String) {
        val cur = reportEdit?.text?.toString().orEmpty()
        val filled = tpl
            .replace("%s", "？")
            .replace("%d", "？")
        reportEdit?.setText(if (cur.isBlank()) filled else "$cur\n$filled")
        reportEdit?.setSelection(reportEdit?.text?.length ?: 0)
    }

    // ---------- 通用小组件（v4.2：全部委托 ui/M3Kit 设计系统，不再自建工具箱） ----------

    /** dp 换算唯一入口（M3Kit）。 */
    private fun dp(v: Float): Int = M3Kit.dp(ctx, v)

    /** 整型重载：`dp(12)` 与 `dp(12f)` 等价。 */
    private fun dp(v: Int): Int = dp(v.toFloat())

    /** 说明文字（BodySmall + OnSurfaceVariant）。 */
    private fun caption(text: String): TextView = M3Kit.cardDesc(ctx, text)

    /** 等宽块（细描边 + 12dp 圆角 + 内部滚动）。 */
    private fun mono(heightDp: Float): TextView = M3Kit.monoBlock(ctx, heightDp)

    /** 整型重载：`mono(260)` 与 `mono(260f)` 等价。 */
    private fun mono(heightDp: Int): TextView = mono(heightDp.toFloat())

    /**
     * 悬浮窗紧凑按钮（真实 MaterialButton + M3 Expressive 20dp 圆角 + 按压回弹）：
     * [bg] 非空=实底（tonal）、[stroke] 非空=描边（outlined）、都空=文字（text）。
     * 高度自适应 + minHeight 保底：长文案窄屏两行时不被垂直裁掉（v4.1.1 真机反馈）。
     */
    private fun baseButton(
        text: String,
        heightDp: Float,
        bg: Int?,
        stroke: Int?,
        onClick: () -> Unit
    ): MaterialButton =
        MaterialButton(ctx).apply {
            this.text = text
            textSize = 13f
            gravity = Gravity.CENTER
            isAllCaps = false
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            minHeight = dp(heightDp)
            minimumHeight = dp(heightDp)
            cornerRadius = dp(M3Kit.SHAPE_CARD)
            insetTop = 0
            insetBottom = 0
            when {
                bg != null -> {
                    backgroundTintList = android.content.res.ColorStateList.valueOf(bg)
                    setTextColor(cOnPrimaryContainer)
                }
                stroke != null -> {
                    strokeWidth = dp(1)
                    strokeColor = android.content.res.ColorStateList.valueOf(stroke)
                    setTextColor(cPrimary)
                }
                else -> setTextColor(cPrimary)
            }
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { topMargin = dp(8) }
            setOnClickListener { onClick() }
            M3Kit.pressFeedback(this)
        }

    private fun tonal(text: String, onClick: () -> Unit): MaterialButton =
        baseButton(text, 44f, cPrimaryContainer, null, onClick)

    private fun outlined(text: String, onClick: () -> Unit): MaterialButton =
        baseButton(text, 44f, null, cOutline, onClick)

    private fun text(text: String, onClick: () -> Unit): MaterialButton =
        baseButton(text, 40f, null, null, onClick)

    private fun buttonRow(left: MaterialButton, right: MaterialButton): LinearLayout {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(left)
        row.addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(8)
        })
        return row
    }

    /** 头部/底部行内小按钮（紧凑 40dp）。 */
    private fun miniButton(text: String, onClick: () -> Unit): MaterialButton =
        baseButton(text, 40f, null, cOutline, onClick).apply {
            minWidth = 0
            minimumWidth = 0
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

    private fun chipRow(items: List<Pair<String, () -> Unit>>): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            val p = dp(2)
            setPadding(p, p, p, p)
        }
        val scroll = ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isHorizontalScrollBarEnabled = false
        }
        items.forEach { (label, act) ->
            row.addView(TextView(ctx).apply {
                text = label
                textSize = 12f
                setTextColor(cOnSecondaryContainer)
                val p = dp(10)
                setPadding(p, dp(6), p, dp(6))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(8).toFloat()
                    setColor(cSecondaryContainer)
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(6) }
                setOnClickListener { act() }
                M3Kit.pressFeedback(this)
            })
        }
        scroll.addView(row)
        return scroll
    }

    private fun copyToClipboard(text: String) {
        runCatching {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("mbos-debug", text))
        }.onFailure { AppLog.w("DebugWin", "复制失败", it) }
    }

    /** 收起软键盘（面板收起时调用，避免键盘残留）。 */
    private fun hideKeyboard() {
        runCatching {
            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            rootFrame?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
        }
    }
}

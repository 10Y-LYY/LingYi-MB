/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.R as MR
import com.google.android.material.button.MaterialButton
import com.lingyi.mbos.ble.ConnectionHub
import com.lingyi.mbos.ble.SimulatorConnection
import com.lingyi.mbos.ui.Haptics
import com.lingyi.mbos.ui.SimulatorLedView
import com.lingyi.mbos.ui.M3Kit
import com.lingyi.mbos.util.themeColor

/**
 * micro:bit V2 模拟器悬浮窗（开发者模式）。
 *
 * 全局可见的控制面板：拖拽移动、可折叠成小气泡；实时展示模拟器状态与
 * 传感器值、LED 微预览，并提供按键 / 手势 / 虚拟方向键注入 / XYZ 与朝向微调——
 * 让「连接 → 控制台 → 预览 → 指针」全链路在没有真机时也可正式调试。
 * TONE / MELODY 由模拟器连接在手机端本地发声（真机由 micro:bit 扬声器发声）。
 *
 * 真机接入时 ConnectionHub 会先断开模拟器（让位），本窗随之进入
 * 「让位中」状态，可一键恢复模拟连接。
 */
class SimulatorService : Service() {

    private lateinit var windowManager: WindowManager

    /**
     * 悬浮窗视图统一用应用主题包装后 inflate：
     * 布局里的 ?attr/ 颜色令牌（M3）才能解析；
     * Service 自身的 base context 主题在部分环境下并非应用主题，显式包裹更稳。
     */
    private val themed: Context by lazy { ContextThemeWrapper(this, R.style.Theme_MBOS) }

    private var expandedView: View? = null
    private var bubbleView: View? = null
    private var lpExpanded: WindowManager.LayoutParams? = null
    private var lpBubble: WindowManager.LayoutParams? = null

    /** 姿态微调面板（默认折叠）与展开箭头。 */
    private var attitudePanel: View? = null
    private var attitudeArrow: ImageView? = null
    private var attitudeOpen = false

    private val handler = Handler(Looper.getMainLooper())

    /** 虚拟方向键当前偏移（mg）：某轴被按住时置值，松手清零。 */
    private var tiltX = 0
    private var tiltY = 0

    private val refreshTask = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    /** 监听连接变化：真机接入（让位）时立即刷新面板状态。 */
    private val connListener = object : com.lingyi.mbos.ble.MicrobitConnection.Listener {
        override fun onState(state: com.lingyi.mbos.ble.ConnState) {
            handler.post { render() }
        }

        override fun onData(data: String) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WindowManager::class.java)
        ConnectionHub.addListener(connListener)
        try {
            buildExpanded()
            buildBubble()
        } catch (e: Exception) {
            // 悬浮窗权限被收回等场景：体面退出而不是崩溃
            stopSelf()
            return
        }
        handler.post(refreshTask)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        ConnectionHub.removeListener(connListener)
        runCatching { expandedView?.let { windowManager.removeView(it) } }
        runCatching { bubbleView?.let { windowManager.removeView(it) } }
        expandedView = null
        bubbleView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------- 视图构建 ----------

    private fun overlayType(): Int =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun baseParams(x: Int, y: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            // 不抢焦点（不弹输入法、不影响底部导航）；自身可触摸
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }

    private fun buildExpanded() {
        val view = LayoutInflater.from(themed).inflate(R.layout.overlay_simulator, null)
        val lp = baseParams(INIT_X, INIT_Y)
        windowManager.addView(view, lp)
        expandedView = view
        lpExpanded = lp

        view.findViewById<ImageButton>(R.id.btnSimCollapse).setOnClickListener { collapse() }
        view.findViewById<ImageButton>(R.id.btnSimClose).setOnClickListener { stopSelf() }
        makeDraggable(view.findViewById(R.id.simHeader), view, lp)

        wireHoldKey(view, R.id.btnSimA) { sim, down -> sim.setButtonA(down) }
        wireHoldKey(view, R.id.btnSimB) { sim, down -> sim.setButtonB(down) }
        view.findViewById<MaterialButton>(R.id.btnSimShake).setOnClickListener {
            (ConnectionHub.current as? SimulatorConnection)?.injectGesture("shake")
        }

        wireDirKey(view, R.id.btnSimLeft, -1, 0)
        wireDirKey(view, R.id.btnSimRight, 1, 0)
        wireDirKey(view, R.id.btnSimUp, 0, -1)
        wireDirKey(view, R.id.btnSimDown, 0, 1)

        // 姿态微调（默认折叠）：展开即锁定手动 XYZ/朝向，暂停自动漂移
        attitudePanel = view.findViewById(R.id.simAttitudePanel)
        attitudeArrow = view.findViewById(R.id.btnSimAttitudeArrow)
        view.findViewById<View>(R.id.simAttitudeHeader).setOnClickListener { toggleAttitude() }
        val seekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                pushAttitude()
            }

            override fun onStartTrackingTouch(sb: SeekBar?) = Unit
            override fun onStopTrackingTouch(sb: SeekBar?) = Unit
        }
        view.findViewById<SeekBar>(R.id.seekSimX).setOnSeekBarChangeListener(seekListener)
        view.findViewById<SeekBar>(R.id.seekSimY).setOnSeekBarChangeListener(seekListener)
        view.findViewById<SeekBar>(R.id.seekSimZ).setOnSeekBarChangeListener(seekListener)
        view.findViewById<SeekBar>(R.id.seekSimC).setOnSeekBarChangeListener(seekListener)
        view.findViewById<MaterialButton>(R.id.btnSimAttitudeAuto).setOnClickListener {
            (ConnectionHub.current as? SimulatorConnection)?.resumeAutoSensors()
            if (attitudeOpen) toggleAttitude()
            Toast.makeText(this, R.string.sim_attitude_resumed, Toast.LENGTH_SHORT).show()
        }

        view.findViewById<MaterialButton>(R.id.btnSimPeriodic).setOnClickListener {
            val sim = ConnectionHub.current as? SimulatorConnection
            if (sim != null && sim.isConnected()) {
                sim.periodicReport = !sim.periodicReport
            }
            render()
        }
        view.findViewById<MaterialButton>(R.id.btnSimDisconnect).setOnClickListener {
            val sim = ConnectionHub.current as? SimulatorConnection
            if (sim != null && sim.isConnected()) {
                ConnectionHub.disconnect()
            } else if (!ConnectionHub.isConnected()) {
                ConnectionHub.connectSimulator(this, connListener)
            }
            render()
        }
    }

    private fun buildBubble() {
        val dp = { v: Int -> M3Kit.dp(themed, v) }
        val bubble = TextView(themed).apply {
            text = "🖥 模拟器"
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(themeColor(MR.attr.colorOnPrimaryContainer))
            setBackgroundResource(R.drawable.bg_overlay_bubble)
            val ph = dp(14)
            setPadding(ph, dp(10), ph, dp(10))
            // 悬浮窗没有系统阴影，手动 elevation 让它「浮」起来，不再扁平贴屏
            elevation = dp(4).toFloat()
            contentDescription = getString(R.string.sim_overlay_title)
            isClickable = true
            isFocusable = true
        }
        val lp = baseParams(INIT_X, INIT_Y + dp(120))
        windowManager.addView(bubble, lp)
        bubbleView = bubble
        lpBubble = lp
        bubble.setOnClickListener { expand() }
        makeDraggable(bubble, bubble, lp)
        bubble.visibility = View.GONE
    }

    /** 标题 / 气泡拖动：记录起点，随手指移动更新窗口位置。 */
    private fun makeDraggable(handle: View, target: View, lp: WindowManager.LayoutParams) {
        handle.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var downRawX = 0f
            private var downRawY = 0f
            private var dragged = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = lp.x
                        startY = lp.y
                        downRawX = event.rawX
                        downRawY = event.rawY
                        dragged = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downRawX
                        val dy = event.rawY - downRawY
                        if (!dragged && (kotlin.math.abs(dx) > M3Kit.dp(themed, 4) || kotlin.math.abs(dy) > M3Kit.dp(themed, 4))) {
                            dragged = true
                        }
                        if (dragged) {
                            lp.x = startX + dx.toInt()
                            lp.y = startY + dy.toInt()
                            runCatching { windowManager.updateViewLayout(target, lp) }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        // 未拖动视为点击：交还点击语义（气泡展开）
                        if (!dragged) handle.performClick()
                        return true
                    }
                }
                return false
            }
        })
    }

    // ---------- 控制 wiring ----------

    /**
     * A/B 键：真机式「按住」语义——按下即置位（带按压动效与触感），松手后至少保持
     * [MIN_HOLD_MS] 再抬起。控制台多个功能以 80–150ms 轮询读按键，瞬时点按会被漏掉，
     * 最小保持时长让快速点按也能被稳定捕获；按住不放则持续生效，与真机一致。
     * （历史上这里误实现成「点击切换」：点一下常按、再点一下才松开，手感全无。）
     */
    private fun wireHoldKey(root: View, id: Int, apply: (SimulatorConnection, Boolean) -> Unit) {
        val btn = root.findViewById<MaterialButton>(id)
        var downAt = 0L
        val release = Runnable {
            (ConnectionHub.current as? SimulatorConnection)?.let { apply(it, false) }
            setKeyVisual(btn, false)
        }
        btn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    handler.removeCallbacks(release)
                    downAt = event.eventTime
                    setKeyVisual(btn, true)
                    Haptics.tick(btn)
                    (ConnectionHub.current as? SimulatorConnection)?.let { apply(it, true) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 补足最小保持时长后再抬起（快速点按也不会被轮询漏掉）
                    val held = event.eventTime - downAt
                    handler.postDelayed(release, (MIN_HOLD_MS - held).coerceAtLeast(0L))
                    true
                }
                else -> false
            }
        }
        // 可访问性（TalkBack 等）只发 click 不发 touch：补一次最小保持时长的点按
        btn.setOnClickListener {
            handler.removeCallbacks(release)
            setKeyVisual(btn, true)
            (ConnectionHub.current as? SimulatorConnection)?.let { apply(it, true) }
            handler.postDelayed(release, MIN_HOLD_MS)
        }
    }

    /** A/B 键按压视觉：描边高亮 + 轻微缩放。 */
    private fun setKeyVisual(btn: MaterialButton, down: Boolean) {
        btn.isSelected = down
        btn.strokeColor = android.content.res.ColorStateList.valueOf(
            themeColor(if (down) MR.attr.colorPrimary else MR.attr.colorOutline)
        )
        btn.animate()
            .scaleX(if (down) 0.94f else 1f)
            .scaleY(if (down) 0.94f else 1f)
            .setDuration(60L)
            .start()
    }

    // ---------- 姿态微调 ----------

    /** 展开 / 收起姿态面板；展开时把当前有效姿态灌入滑杆并锁定手动模式。 */
    private fun toggleAttitude() {
        attitudeOpen = !attitudeOpen
        val panel = attitudePanel ?: return
        panel.visibility = if (attitudeOpen) View.VISIBLE else View.GONE
        attitudeArrow?.animate()
            ?.rotation(if (attitudeOpen) 180f else 0f)
            ?.setDuration(180L)
            ?.start()
        if (!attitudeOpen) return
        val sim = ConnectionHub.current as? SimulatorConnection ?: return
        val s = sim.snapshot()
        // 快照给的是「基值 + 方向键偏移」，扣掉偏移才是要写回的基值
        val bx = (s.accelX - tiltX).coerceIn(-2048, 2047)
        val by = (s.accelY - tiltY).coerceIn(-2048, 2047)
        val bz = s.accelZ.coerceIn(-2048, 2047)
        val view = expandedView ?: return
        view.findViewById<SeekBar>(R.id.seekSimX).progress = bx + 2048
        view.findViewById<SeekBar>(R.id.seekSimY).progress = by + 2048
        view.findViewById<SeekBar>(R.id.seekSimZ).progress = bz + 2048
        view.findViewById<SeekBar>(R.id.seekSimC).progress = s.heading
        updateAttitudeLabels()
        // 展开即接管姿态：暂停自动漂移，之后由滑杆精确控制
        sim.setAttitude(bx, by, bz, s.heading)
    }

    /** 把四个滑杆的当前位置写入模拟器，并刷新旁边数值。 */
    private fun pushAttitude() {
        val view = expandedView ?: return
        updateAttitudeLabels()
        val sim = ConnectionHub.current as? SimulatorConnection ?: return
        val x = view.findViewById<SeekBar>(R.id.seekSimX).progress - 2048
        val y = view.findViewById<SeekBar>(R.id.seekSimY).progress - 2048
        val z = view.findViewById<SeekBar>(R.id.seekSimZ).progress - 2048
        val c = view.findViewById<SeekBar>(R.id.seekSimC).progress
        sim.setAttitude(x, y, z, c)
    }

    private fun updateAttitudeLabels() {
        val view = expandedView ?: return
        view.findViewById<TextView>(R.id.tvSimXVal).text =
            (view.findViewById<SeekBar>(R.id.seekSimX).progress - 2048).toString()
        view.findViewById<TextView>(R.id.tvSimYVal).text =
            (view.findViewById<SeekBar>(R.id.seekSimY).progress - 2048).toString()
        view.findViewById<TextView>(R.id.tvSimZVal).text =
            (view.findViewById<SeekBar>(R.id.seekSimZ).progress - 2048).toString()
        view.findViewById<TextView>(R.id.tvSimCVal).text =
            "${view.findViewById<SeekBar>(R.id.seekSimC).progress}°"
    }

    /** 虚拟方向键：按住持续偏移姿态，松手清零（仅清零本轴）。 */
    private fun wireDirKey(root: View, id: Int, axisX: Int, axisY: Int) {
        val btn = root.findViewById<MaterialButton>(id)
        btn.setOnTouchListener { _, event ->
            val sim = ConnectionHub.current as? SimulatorConnection
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (axisX != 0) tiltX = axisX * TILT_BIAS_MG
                    if (axisY != 0) tiltY = axisY * TILT_BIAS_MG
                    sim?.setTiltBias(tiltX, tiltY)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (axisX != 0) tiltX = 0
                    if (axisY != 0) tiltY = 0
                    sim?.setTiltBias(tiltX, tiltY)
                    btn.performClick()
                    true
                }
                else -> false
            }
        }
    }

    // ---------- 渲染 ----------

    private fun render() {
        val view = expandedView ?: return
        val sim = ConnectionHub.current as? SimulatorConnection
        val status = view.findViewById<TextView>(R.id.tvSimStatus)
        val sensors = view.findViewById<TextView>(R.id.tvSimSensors)
        val ledView = view.findViewById<SimulatorLedView>(R.id.ledPreview)
        val btnPeriodic = view.findViewById<MaterialButton>(R.id.btnSimPeriodic)
        val btnDisconnect = view.findViewById<MaterialButton>(R.id.btnSimDisconnect)
        val btnA = view.findViewById<MaterialButton>(R.id.btnSimA)
        val btnB = view.findViewById<MaterialButton>(R.id.btnSimB)
        val tvAttitudeState = view.findViewById<TextView>(R.id.tvSimAttitudeState)

        if (sim != null && sim.isConnected()) {
            val s = sim.snapshot()
            status.text = getString(
                R.string.sim_status_connected,
                if (s.periodicReport) "开" else "关",
                if (s.mouseOn) "开" else "关"
            )
            sensors.text = buildString {
                append("温度 ${s.temperature}°C  光线 ${s.light}\n")
                append("X ${s.accelX}mg  Y ${s.accelY}mg\n")
                append("Z ${s.accelZ}mg  朝向 ${s.heading}°\n")
                append("手势 ${s.gesture}  A:${if (s.btnA) "按下" else "松开"}  B:${if (s.btnB) "按下" else "松开"}")
            }
            ledView.setMatrix(s.led)
            btnPeriodic.text = getString(
                if (s.periodicReport) R.string.sim_periodic_on else R.string.sim_periodic_off
            )
            btnDisconnect.text = getString(R.string.sim_disconnect)
            tvAttitudeState.text = getString(
                if (s.attitudeManual) R.string.sim_attitude_manual else R.string.sim_attitude_auto
            )
            // 同步 A/B 高亮（周期刷新可能落后于点击）
            syncToggle(btnA, s.btnA)
            syncToggle(btnB, s.btnB)
        } else if (ConnectionHub.isConnected()) {
            // 真机已接入：模拟器让位
            status.text = getString(R.string.sim_status_yield)
            sensors.text = "-"
            ledView.setMatrix(Array(5) { IntArray(5) })
            btnDisconnect.text = getString(R.string.sim_reconnect)
            tvAttitudeState.text = getString(R.string.sim_attitude_auto)
        } else {
            status.text = getString(R.string.sim_status_off)
            sensors.text = "-"
            ledView.setMatrix(Array(5) { IntArray(5) })
            btnDisconnect.text = getString(R.string.sim_reconnect)
            tvAttitudeState.text = getString(R.string.sim_attitude_auto)
        }
    }

    private fun syncToggle(btn: MaterialButton, down: Boolean) {
        if (btn.isSelected != down) {
            btn.isSelected = down
            btn.strokeColor = android.content.res.ColorStateList.valueOf(
                if (down) themeColor(MR.attr.colorPrimary) else themeColor(MR.attr.colorOutline)
            )
        }
    }

    // ---------- 折叠 / 展开 ----------

    private fun collapse() {
        expandedView?.visibility = View.GONE
        bubbleView?.visibility = View.VISIBLE
    }

    private fun expand() {
        bubbleView?.visibility = View.GONE
        expandedView?.visibility = View.VISIBLE
    }


    companion object {
        /** 悬浮窗是否正在运行（开发者页据此刷新入口状态）。 */
        var isRunning: Boolean = false
            private set

        private const val REFRESH_MS = 400L

        /** 虚拟方向键单轴偏移量（mg）：120mg ≈ 2.5px/帧，指针速度适中。 */
        private const val TILT_BIAS_MG = 120

        /** 初始位置：屏幕右上角，不遮挡主要内容。 */
        private const val INIT_X = 24
        private const val INIT_Y = 96

        /**
         * A/B 键最小保持时长（ms）：松手后仍保持按下这么久再抬起。
         * 控制台按钮监视器（150ms）/ 反应测试（80ms）/ 猜数字等按 80–150ms 轮询读按键，
         * 瞬时点按会被漏掉；180ms > 最大轮询间隔，保证任何快速点按至少被采到一次。
         */
        private const val MIN_HOLD_MS = 180L
    }
}

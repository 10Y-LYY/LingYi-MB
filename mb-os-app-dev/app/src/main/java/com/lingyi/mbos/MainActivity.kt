/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.R as MR
import com.lingyi.mbos.util.themeColor
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.lingyi.mbos.ble.ConnState
import com.lingyi.mbos.ble.ConnectionHub
import com.lingyi.mbos.ble.MicrobitConnection
import com.lingyi.mbos.ble.SimulatorConnection
import com.lingyi.mbos.dev.DevToolsActivity
import com.lingyi.mbos.databinding.ActivityMainBinding
import android.widget.Toast
import com.lingyi.mbos.databinding.DialogHelpBinding
import com.lingyi.mbos.databinding.DialogInputBinding
import com.lingyi.mbos.databinding.DialogLedBinding
import com.lingyi.mbos.databinding.DialogPlotBinding
import com.lingyi.mbos.databinding.DialogToneBinding
import com.lingyi.mbos.databinding.ItemDrawerRowBinding
import com.lingyi.mbos.databinding.ItemFuncRowBinding
import com.lingyi.mbos.databinding.ItemFuncTileBinding
import com.lingyi.mbos.databinding.ItemQuickCardBinding
import com.lingyi.mbos.ui.Gestures
import com.lingyi.mbos.ui.Patterns
import com.lingyi.mbos.ui.rowsToMatrix
import com.lingyi.mbos.ui.buildMatrixEditor
import com.lingyi.mbos.ui.setMatrixCell
import com.lingyi.mbos.ui.FeatureCatalog
import com.lingyi.mbos.ui.FeatureOpener
import com.lingyi.mbos.ui.FirmwareScript
import com.lingyi.mbos.ui.Haptics.setOnClickWithHaptic
import com.lingyi.mbos.ui.M3Kit
import com.lingyi.mbos.ui.Protocol
import com.lingyi.mbos.ui.QuickSlotSheet
import com.lingyi.mbos.ui.QuickSlots
import com.lingyi.mbos.ui.VoyageActions
import com.lingyi.mbos.ui.VoyageSkin
import com.lingyi.mbos.ui.openAnimationStudio
import com.lingyi.mbos.ui.openBarrage
import com.lingyi.mbos.ui.openBigDigit
import com.lingyi.mbos.ui.openBlackjack
import com.lingyi.mbos.ui.openBrightAuto
import com.lingyi.mbos.ui.openButtonMonitor
import com.lingyi.mbos.ui.openCodeCheck
import com.lingyi.mbos.ui.openCodeLib
import com.lingyi.mbos.ui.openCoin
import com.lingyi.mbos.ui.openCommandSeq
import com.lingyi.mbos.ui.openCompassRose
import com.lingyi.mbos.ui.openDice
import com.lingyi.mbos.ui.openDoorbell
import com.lingyi.mbos.ui.openDropTest
import com.lingyi.mbos.ui.openErrorDict
import com.lingyi.mbos.ui.openFindBell
import com.lingyi.mbos.ui.openFlashLog
import com.lingyi.mbos.ui.openGestureLock
import com.lingyi.mbos.ui.openGestureLog
import com.lingyi.mbos.ui.openGuessGame
import com.lingyi.mbos.ui.openHoldGame
import com.lingyi.mbos.ui.openHotPotato
import com.lingyi.mbos.ui.openIdleBot
import com.lingyi.mbos.ui.openLevel
import com.lingyi.mbos.ui.openLightMeter
import com.lingyi.mbos.ui.openLinkMonitor
import com.lingyi.mbos.ui.openLogStream
import com.lingyi.mbos.ui.openMarquee
import com.lingyi.mbos.ui.openMelodyMix
import com.lingyi.mbos.ui.openMetronome
import com.lingyi.mbos.ui.openMotionDetector
import com.lingyi.mbos.ui.openMorse
import com.lingyi.mbos.ui.openNightLight
import com.lingyi.mbos.ui.openPiano
import com.lingyi.mbos.ui.openPinTable
import com.lingyi.mbos.ui.openQuickPanel
import com.lingyi.mbos.ui.openQuizShow
import com.lingyi.mbos.ui.openReactionGame
import com.lingyi.mbos.ui.openRoulette
import com.lingyi.mbos.ui.openRps
import com.lingyi.mbos.ui.openSensorLogger
import com.lingyi.mbos.ui.openSensorTest
import com.lingyi.mbos.ui.openShakeButton
import com.lingyi.mbos.ui.openShakeCount
import com.lingyi.mbos.ui.openScriptInfo
import com.lingyi.mbos.ui.openSiren
import com.lingyi.mbos.ui.openSoundboard
import com.lingyi.mbos.ui.openTapCount
import com.lingyi.mbos.ui.openTempChart
import com.lingyi.mbos.ui.openThermometer
import com.lingyi.mbos.ui.openTorch
import com.lingyi.mbos.ui.openTruthDare
import com.lingyi.mbos.ui.openTunePad
import com.lingyi.mbos.ui.openTutorials
import com.lingyi.mbos.util.AppLog
import com.lingyi.mbos.util.AutoReconnect
import com.lingyi.mbos.util.Prefs
import com.lingyi.mbos.util.UpdateChecker
import com.lingyi.mbos.util.UpdateDialog

/**
 * 主控台（应用入口页）。
 *
 * 连接策略（v4.2.4，对齐华为运动健康「进 App 即自动连回上次设备」）：
 *  - 进页先按上次成功连接的方式静默重连（[maybeAutoConnect]），不弹任何对话框；
 *  - 未连接时，预览模式正下方给出灰色小字「未连接 · 点按重新连接」，点击即可重连；
 *  - 任何页面显示的状态都以 [ConnectionHub] 即时状态为准（不再缓存布尔值），
 *    握手过程中显示「正在连接…」而不是误报「未连接」。
 */
class MainActivity : BaseActivity() {

    private lateinit var b: ActivityMainBinding
    private var ledMatrix = Array(5) { BooleanArray(5) }
    private var connType = ""
    /** 仅当本页处于前台时才弹传感器对话框，避免进入预览后后台抢窗/报 BadToken。 */
    private var isForeground = false

    /** 定时任务循环：成员变量以便随时停止。 */
    private val timerHandler = Handler(Looper.getMainLooper())
    private var timerTicker: M3Kit.Ticker? = null

    /** 控制台展示风格（列表 / 大方格）：在设置页切换后，onResume 发现变化即重建列表。 */
    private var lastConsoleStyle = Prefs.CONSOLE_STYLE_LIST

    /** 当前快捷入口（两张卡），设置弹层保存后用于判断是否需要重建。 */
    private var quickSlots: List<String> = emptyList()

    /** 开发者入口行（侧栏），仅开发者模式可见。 */
    private var devRow: View? = null

    /** 发送失败提示节流：避免一次演示脚本连发多条时刷屏。 */
    private var lastSendFailAt = 0L

    /** 本次启动已弹过更新对话框的远端 versionCode：-1 表示还没弹过。用于避免重复打扰。 */
    private var shownUpdateCode = -1

    /** 自检更新的「在途」标记：避免频繁进出页面时重复发起网络请求。 */
    private var updateChecking = false

    /**
     * Voyage 风格皮肤（仅预览版 preview flavor 挂载）。
     * 叠加在原控制台之上：原视图继续承载 88 项功能对话框 / 连接 / 更新自检，
     * 皮肤只负责呈现与导航，所有交互通过 [VoyageActions] 回调交回本 Activity。
     * 稳定版 / 开发者测试版本字段恒为 null，界面与行为零改动。
     */
    private var voyageSkin: VoyageSkin? = null

    private val listener = object : MicrobitConnection.Listener {
        override fun onState(state: ConnState) {
            runOnUiThread { renderConnection(state.connected, state.message) }
        }

        override fun onData(data: String) {
            // 实时读数改由「实时仪表盘」对话框与预览页各自轮询展示，
            // 这里不再对每条 READ 弹模态框（避免模拟器周期上报时每秒弹窗、关不掉）。
        }
    }

    /** 自动重连状态（掉线重连时在状态行下方给出「x 秒后自动重连」）。 */
    private val autoReconnectListener = object : AutoReconnect.StatusListener {
        override fun onAutoReconnectStatus(status: AutoReconnect.Status) {
            runOnUiThread { renderAutoReconnect(status) }
        }
    }

    /** 重连前的运行时权限（Android 12+ 才需要「附近的设备」）。 */
    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) startReconnect()
        else Snackbar.make(b.root, R.string.guide_need_bluetooth, Snackbar.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        // 预览版专属：叠加 Voyage 风格皮肤（稳定版/开发版完全不进入此分支）
        if (BuildConfig.DEFAULT_CHANNEL == "preview") {
            runCatching { mountVoyageSkin() }
                .onFailure { AppLog.e("MainActivity", "Voyage 皮肤挂载失败，回落原界面", it) }
        }

        connType = ConnectionHub.current?.type ?: ""
        b.toolbar.setNavigationOnClickListener { b.drawer.openDrawer(b.navPanel) }

        b.btnViewAll.setOnClickListener { openPreview() }
        b.tvConnectHint.setOnClickListener { reconnectNow() }
        b.btnEditQuick.setOnClickListener { openQuickSheet() }

        buildDrawer()
        lastConsoleStyle = Prefs.getConsoleStyle(this)
        buildQuickCards()
        buildFuncList()

        ConnectionHub.addListener(listener)
        AutoReconnect.addListener(autoReconnectListener)
        // 注册功能直达：悬浮窗调试控制台的「功能」页签经此触发 88 项功能
        FeatureOpener.register(featureOpener)
        renderConnection(
            ConnectionHub.isConnected(),
            if (ConnectionHub.isConnected()) "已连接" else "未连接"
        )
        maybeAutoConnect()
    }

    override fun onResume() {
        super.onResume()
        isForeground = true
        // 控制台展示风格在设置页被切换后，返回本页按新风格重建（大方格 ⇄ 列表）
        val style = Prefs.getConsoleStyle(this)
        if (style != lastConsoleStyle) {
            lastConsoleStyle = style
            buildFuncList()
        }
        // 快捷入口可能被「设置 → 恢复默认」重置
        if (Prefs.getQuickSlots(this) != quickSlots) buildQuickCards()
        devRow?.visibility = if (Prefs.isDevMode(this)) View.VISIBLE else View.GONE
        renderConnection(
            ConnectionHub.isConnected(),
            if (ConnectionHub.isConnected()) "已连接" else "未连接"
        )
        // 进入主控台即自动自检更新（稳定/预览/开发三个版本一致）：发现新版本直接弹窗
        selfCheckUpdate()
    }

    /**
     * 主控台自检更新：每次进入主控台都联网查一次，发现新版本弹对话框。
     * 同一次启动内对同一版本只弹一次，避免从子页面返回时反复打扰。
     */
    private fun selfCheckUpdate() {
        if (updateChecking) return
        updateChecking = true
        UpdateChecker.check(Prefs.getChannel(this)) { info ->
            updateChecking = false
            if (this@MainActivity.isFinishing || this@MainActivity.isDestroyed) return@check
            if (!info.available || info.remoteVersionCode == shownUpdateCode) return@check
            shownUpdateCode = info.remoteVersionCode
            UpdateDialog.show(this@MainActivity, info)
        }
    }

    override fun onPause() {
        super.onPause()
        isForeground = false
    }

    override fun onDestroy() {
        super.onDestroy()
        voyageSkin?.destroy() // 取消皮肤未完成的入场动画与延迟回调，避免持有已销毁 Activity
        stopTimer() // 定时任务 Runnable 自投递，不显式移除会泄漏 Activity 并向死视图弹 Snackbar
        com.lingyi.mbos.ui.SensorPoller.stopAll() // 对话框宿主销毁兜底：停掉所有仍在轮询的读取器
        ConnectionHub.removeListener(listener)
        AutoReconnect.removeListener(autoReconnectListener)
        FeatureOpener.unregister(featureOpener)
    }

    /** 侧栏打开时先收侧栏（DrawerLayout 不自动处理返回键）。 */
    override fun onBackPressed() {
        // 预览版皮肤：非首页时先回首页，避免误退出
        if (voyageSkin?.onBack() == true) return
        if (b.drawer.isDrawerOpen(b.navPanel)) {
            b.drawer.closeDrawer(b.navPanel)
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    /** 功能直达桥引用（固定实例，供 register/unregister 比对）。 */
    private val featureOpener: (String) -> Unit = { id -> onFuncClick(id) }

    private fun openPreview() {
        startActivity(Intent(this, PreviewActivity::class.java))
    }

    // ==================== Voyage 皮肤（仅预览版） ====================

    /**
     * 挂载 Voyage 风格皮肤：作为覆盖层 addContentView 到原控制台之上。
     * 原 [b] 视图树保持完整（功能对话框、Snackbar、连接渲染全部照常工作），
     * 皮肤通过回调把 88 项功能、连接动作、页面跳转交回本 Activity 处理。
     */
    private fun mountVoyageSkin() {
        // 预览版专属：系统状态栏 / 导航栏 / 窗口底色统一为页面底色 #EDF2EE
        //（与 VoyageSkin 的 VColor.BG 完全一致）。三者同色后，顶部与底部不再出现
        // 比内容更深的灰绿色带或白色硬边，整屏从上到下连续，消除「上下有框」。
        // 稳定版 / 开发版不受影响。
        val voyageBg = Color.parseColor("#EDF2EE")
        window.statusBarColor = voyageBg
        window.navigationBarColor = voyageBg
        window.setBackgroundDrawable(ColorDrawable(voyageBg))
        val skin = VoyageSkin(this, object : VoyageActions {
            override fun onFeature(id: String) = onFuncClick(id)
            override fun onReconnect() = reconnectNow()
            override fun onOpenGuide() =
                startActivity(Intent(this@MainActivity, GuideActivity::class.java))
            override fun onOpenSettings() =
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            override fun onOpenLicense() =
                startActivity(Intent(this@MainActivity, LicenseActivity::class.java))
            override fun onOpenDevTools() = startActivity(
                Intent(this@MainActivity, DevToolsActivity::class.java)
            )
            override fun onOpenHelp() = openHelpDialog()
            override fun onOpenPreview() = openPreview()
            override fun onDisconnect() = disconnectNow()
            override fun onCheckUpdate() {
                if (updateChecking) return
                updateChecking = true
                Toast.makeText(this@MainActivity, "正在检查更新…", Toast.LENGTH_SHORT).show()
                UpdateChecker.check(Prefs.getChannel(this@MainActivity)) { info ->
                    updateChecking = false
                    if (isFinishing || isDestroyed) return@check
                    if (info.available) {
                        UpdateDialog.show(this@MainActivity, info)
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            info.error?.let { "检查失败：$it" } ?: "已是最新版本 v${UpdateChecker.currentVersionName()}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        })
        voyageSkin = skin
        addContentView(
            skin.root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        // 同步一次当前连接到皮肤状态条
        skin.onConnection(
            ConnectionHub.isConnected(),
            b.tvDevState.text.toString()
        )
    }

    // ==================== 连接状态渲染 ====================

    /**
     * 统一刷新「连接状态」相关 UI：Hero 卡徽标 / 副状态 / 重连区 / 侧栏状态。
     * [msg] 为连接层最新状态文案（如「正在连接 micro:bit…」），握手阶段直接透传，
     * 避免用户把「正在连接」误读成「未连接」。
     */
    private fun renderConnection(isConn: Boolean, msg: String) {
        connType = ConnectionHub.current?.type ?: connType
        val connecting = !isConn && ConnectionHub.current != null
        val modeText = when (connType) {
            "usb" -> "USB 串口"
            "ble" -> "蓝牙 BLE"
            "sim" -> "模拟器"
            else -> ""
        }

        b.tvDevState.text = when {
            isConn -> listOf(modeText, "已连接").filter { it.isNotEmpty() }.joinToString(" · ")
            connecting -> msg.ifBlank { getString(R.string.drawer_state_connecting) }
            else -> msg.ifBlank { getString(R.string.status_disconnected) }
        }

        val container = if (isConn) MR.attr.colorPrimaryContainer else MR.attr.colorSurfaceVariant
        val onContainer = if (isConn) MR.attr.colorOnPrimaryContainer else MR.attr.colorOnSurfaceVariant
        b.statusBadge.setCardBackgroundColor(themeColor(container))
        b.tvBadge.setTextColor(themeColor(onContainer))
        b.tvBadge.text = getString(if (isConn) R.string.online else R.string.offline)
        b.deviceIconWrap.setCardBackgroundColor(themeColor(container))
        (b.deviceIconWrap.getChildAt(0) as? android.widget.ImageView)?.imageTintList =
            ColorStateList.valueOf(themeColor(onContainer))

        b.tvDeviceSub.text = when {
            isConn -> listOf("micro:bit V2", modeText, "已连接")
                .filter { it.isNotEmpty() }.joinToString(" · ")
            connecting -> getString(R.string.drawer_state_connecting)
            else -> "micro:bit V2 · " + getString(R.string.status_disconnected)
        }

        // 未连接 / 握手中才显示灰色小字提示；已连接时彻底收起，避免误触断开
        val showHint = !isConn
        b.tvConnectHint.visibility = if (showHint) View.VISIBLE else View.GONE
        b.tvConnectHint.text = if (connecting) {
            getString(R.string.console_reconnecting)
        } else {
            getString(R.string.console_connect_hint)
        }
        b.tvConnectHint.isClickable = !connecting

        // 预览版：把连接状态同步到 Voyage 皮肤的首页状态条 / 我的页大卡
        voyageSkin?.onConnection(
            isConn,
            b.tvDevState.text.toString()
        )

        renderDrawerState(isConn, connecting, modeText)
    }

    private fun renderDrawerState(isConn: Boolean, connecting: Boolean, modeText: String) {
        b.tvDrawerDevice.text = when {
            isConn -> listOf("micro:bit V2", modeText).filter { it.isNotEmpty() }.joinToString(" · ")
            connecting -> getString(R.string.drawer_state_connecting)
            else -> "micro:bit V2 · " + getString(R.string.status_disconnected)
        }
        b.tvDrawerState.text = getString(
            when {
                isConn -> R.string.drawer_state_connected
                connecting -> R.string.drawer_state_connecting
                else -> R.string.drawer_state_disconnected
            }
        )
        val chipBg = if (isConn) MR.attr.colorPrimaryContainer else MR.attr.colorSurfaceVariant
        val chipFg = if (isConn) MR.attr.colorOnPrimaryContainer else MR.attr.colorOnSurfaceVariant
        b.drawerStateChip.setCardBackgroundColor(themeColor(chipBg))
        b.tvDrawerState.setTextColor(themeColor(chipFg))
        b.drawerStateDot.backgroundTintList = ColorStateList.valueOf(
            themeColor(if (isConn) MR.attr.colorPrimary else MR.attr.colorOutline)
        )
    }

    private fun renderAutoReconnect(status: AutoReconnect.Status) {
        val text = status.humanText()
        if (text.isBlank()) {
            b.tvAutoReconnect.visibility = View.GONE
        } else {
            b.tvAutoReconnect.text = text
            b.tvAutoReconnect.visibility = View.VISIBLE
        }
    }

    // ==================== 连接动作 ====================

    /**
     * 进页静默重连：仅在「有上次连接记录 + 已开启自动重连 + 当前确实没连上」时发起，
     * 全程不弹窗（失败由 AutoReconnect 退避重试，界面显示「正在重连…」）。
     */
    private fun maybeAutoConnect() {
        if (ConnectionHub.isConnected() || ConnectionHub.current != null) return
        if (Prefs.getLastLinkType(this) == null) return
        if (!Prefs.isAutoReconnect(this)) return
        ConnectionHub.ensureLink(this)
    }

    private fun ensureBlePermission(then: () -> Unit) {
        val perms = if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) then() else permLauncher.launch(missing.toTypedArray())
    }

    /** 「重新连接」：有记忆目标就原地重连，没有就引导去连接页（不弹对话框）。 */
    private fun reconnectNow() {
        if (ConnectionHub.isConnected()) {
            Snackbar.make(b.root, R.string.drawer_state_connected, Snackbar.LENGTH_SHORT).show()
            return
        }
        if (ConnectionHub.current != null) {
            Snackbar.make(b.root, R.string.console_reconnecting, Snackbar.LENGTH_SHORT).show()
            return
        }
        if (Prefs.getLastLinkType(this) == null) {
            // 首次使用 / 用户从未连过：直接进连接向导页，不用对话框打断
            startActivity(Intent(this, GuideActivity::class.java))
            return
        }
        ensureBlePermission { startReconnect() }
    }

    private fun startReconnect() {
        val started = ConnectionHub.ensureLink(this, force = true)
        if (!started) {
            startActivity(Intent(this, GuideActivity::class.java))
        }
    }

    private fun disconnectNow() {
        if (!ConnectionHub.isConnected() && ConnectionHub.current == null) {
            Snackbar.make(b.root, R.string.status_disconnected, Snackbar.LENGTH_SHORT).show()
            return
        }
        ConnectionHub.disconnect()
        renderConnection(false, getString(R.string.status_disconnected))
        Snackbar.make(b.root, R.string.drawer_disconnect, Snackbar.LENGTH_SHORT).show()
    }

    // ==================== 侧栏 ====================

    private fun buildDrawer() {
        b.navDeviceGroup.removeAllViews()
        b.navMoreGroup.removeAllViews()

        fun addRow(
            group: LinearLayout,
            icon: Int,
            label: Int,
            onClick: () -> Unit
        ): View {
            val ib = ItemDrawerRowBinding.inflate(layoutInflater, group, false)
            ib.ivIcon.setImageResource(icon)
            ib.tvLabel.setText(label)
            ib.root.setOnClickListener {
                b.drawer.closeDrawer(b.navPanel)
                onClick()
            }
            group.addView(ib.root)
            return ib.root
        }

        addRow(b.navDeviceGroup, R.drawable.ic_refresh, R.string.drawer_reconnect) { reconnectNow() }
        addRow(b.navDeviceGroup, R.drawable.ic_power, R.string.drawer_disconnect) { disconnectNow() }
        addRow(b.navMoreGroup, R.drawable.ic_help, R.string.drawer_firmware) { openHelpDialog() }
        addRow(b.navMoreGroup, R.drawable.ic_settings, R.string.drawer_settings) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        addRow(b.navMoreGroup, R.drawable.ic_info, R.string.drawer_about) {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        devRow = addRow(b.navMoreGroup, R.drawable.ic_code, R.string.drawer_dev) {
            startActivity(Intent(this, DeveloperActivity::class.java))
        }.apply {
            visibility = if (Prefs.isDevMode(this@MainActivity)) View.VISIBLE else View.GONE
        }
        b.tvDrawerVersion.text = getString(
            R.string.drawer_version,
            UpdateChecker.currentVersionName()
        )
    }

    // ==================== 快捷入口（可自定义两张卡） ====================

    private fun openQuickSheet() {
        QuickSlotSheet.show(this, QuickSlots.normalize(quickSlots)) { slots ->
            quickSlots = slots
            Prefs.setQuickSlots(this, slots)
            buildQuickCards()
        }
    }

    private fun buildQuickCards() {
        val container = b.quickRow
        container.removeAllViews()
        quickSlots = Prefs.getQuickSlots(this)
        val roles = listOf(
            MR.attr.colorPrimaryContainer to MR.attr.colorOnPrimaryContainer,
            MR.attr.colorSecondaryContainer to MR.attr.colorOnSecondaryContainer,
        )
        val gap = (12 * resources.displayMetrics.density).toInt()
        quickSlots.forEachIndexed { index, id ->
            val f = QuickSlots.featureOf(id) ?: return@forEachIndexed
            val card = ItemQuickCardBinding.inflate(layoutInflater, container, false)
            card.ivIcon.setImageResource(f.icon)
            card.tvTitle.text = f.title
            card.tvDesc.text = f.desc
            val role = roles[index % roles.size]
            card.iconWrap.setCardBackgroundColor(themeColor(role.first))
            card.ivIcon.imageTintList = ColorStateList.valueOf(themeColor(role.second))
            card.root.contentDescription = f.title
            card.root.setOnClickWithHaptic { onFuncClick(f.id) }
            container.addView(
                card.root,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index > 0) marginStart = gap
                }
            )
        }
    }

    // ==================== 功能列表 ====================

    private data class FuncRow(
        val id: String,
        val icon: Int,
        val title: String,
        val desc: String
    )

    /** 功能数据来自统一目录 [FeatureCatalog]（顺序 = 列表模式展示顺序，勿随意调整）。 */
    private fun funcRows(): List<FuncRow> = FeatureCatalog.FEATURES.map {
        FuncRow(it.id, it.icon, it.title, it.desc)
    }

    private fun buildFuncList() {
        // 兜底：功能列表构建异常（如个别 ROM 的视图 / 资源解析问题）不应让控制台起不来
        runCatching { buildFuncListInner() }
            .onFailure { AppLog.e("MainActivity", "功能列表构建失败", it) }
    }

    private fun buildFuncListInner() {
        val container = b.funcList
        container.removeAllViews()
        // 用原生 M3 的 primary / secondary / tertiary 容器色循环，保证色调统一且合规
        val roles = listOf(
            MR.attr.colorPrimaryContainer to MR.attr.colorOnPrimaryContainer,
            MR.attr.colorSecondaryContainer to MR.attr.colorOnSecondaryContainer,
            MR.attr.colorTertiaryContainer to MR.attr.colorOnTertiaryContainer,
        )
        val rows = funcRows()
        val dividerH = (resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val swDp = resources.configuration.smallestScreenWidthDp

        fun makeRow(index: Int, r: FuncRow): View {
            val ib = ItemFuncRowBinding.inflate(layoutInflater, container, false)
            ib.ivIcon.setImageResource(r.icon)
            ib.tvTitle.text = r.title
            ib.tvDesc.text = r.desc
            val role = roles[index % roles.size]
            val containerColor = themeColor(role.first)
            val onColor = themeColor(role.second)
            ib.iconWrap.setCardBackgroundColor(containerColor)
            ib.ivIcon.imageTintList = ColorStateList.valueOf(onColor)
            ib.root.setOnClickWithHaptic { onFuncClick(r.id) }
            return ib.root
        }

        fun divider(): View = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dividerH
            )
            setBackgroundColor(themeColor(MR.attr.colorOutlineVariant))
        }

        // 「大方格」主题：功能按分类整合成大卡，点卡进二级页面挑选
        if (Prefs.getConsoleStyle(this) == Prefs.CONSOLE_STYLE_GRID) {
            buildFuncGrid(container, swDp, roles)
            return
        }

        // 列表主题（默认）：平板 / 折叠屏内屏功能列表双列，宽屏信息密度更高
        val twoColumn = swDp >= 600

        if (!twoColumn) {
            rows.forEachIndexed { index, r ->
                container.addView(makeRow(index, r))
                if (index < rows.size - 1) container.addView(divider())
            }
            return
        }

        val gap = (12 * resources.displayMetrics.density).toInt()
        val columns = List(2) { col ->
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                ).also { if (col == 1) it.marginStart = gap }
            }
        }
        rows.forEachIndexed { index, r ->
            val col = columns[index % 2]
            col.addView(makeRow(index, r))
            // 本列还有后续行才画分隔线
            if (index + 2 < rows.size) col.addView(divider())
        }
        // 列放在横排容器里，再整体挂进纵向 funcList（直接在纵向容器里给 width=0 + weight 会渲染不出）
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        columns.forEach { grid.addView(it) }
        container.addView(grid)
    }

    /**
     * 大方格主题：每个分类一张 M3 大卡，横排容器包 N 个纵列（与列表双列同一方案）。
     * 列数随最窄宽度分档：折叠屏 / 平板更宽，一屏放更多分类。
     */
    private fun buildFuncGrid(
        container: LinearLayout,
        swDp: Int,
        roles: List<Pair<Int, Int>>
    ) {
        val gap = (12 * resources.displayMetrics.density).toInt()
        val columnCount = when {
            swDp >= 840 -> 4
            swDp >= 600 -> 3
            else -> 2
        }
        val columns = List(columnCount) { col ->
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                ).also { if (col > 0) it.marginStart = gap }
            }
        }
        FeatureCatalog.Category.entries.forEachIndexed { index, category ->
            val ib = ItemFuncTileBinding.inflate(layoutInflater, container, false)
            ib.ivIcon.setImageResource(category.icon)
            ib.tvTitle.text = category.title
            ib.tvDesc.text = category.desc
            ib.tvCount.text = getString(
                R.string.console_feature_count,
                FeatureCatalog.byCategory(category).size
            )
            val role = roles[index % roles.size]
            ib.iconWrap.setCardBackgroundColor(themeColor(role.first))
            ib.ivIcon.imageTintList = ColorStateList.valueOf(themeColor(role.second))
            ib.root.setOnClickWithHaptic { openCategory(category) }
            // 关键修复：tile 只会进「纵向」列容器，纵向 LinearLayout 的 weight
            // 不作用于宽度；若沿用 XML 的 0dp+weight 会得到 0 宽卡片（不可见）。
            // 这里显式覆盖为 MATCH_PARENT/WRAP_CONTENT，并给同列后续卡补纵向间距。
            val tile = ib.root
            tile.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { if (index >= columnCount) topMargin = gap }
            columns[index % columnCount].addView(tile)
        }
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        columns.forEach { grid.addView(it) }
        container.addView(grid)
    }

    /** 大方格主题：点分类卡 → 二级页挑选，回抛功能 id 后走既有分派。 */
    private val categoryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = result.data?.getStringExtra(ConsoleCategoryActivity.EXTRA_FEATURE_ID)
        if (result.resultCode == RESULT_OK && !id.isNullOrBlank()) onFuncClick(id)
    }

    private fun openCategory(category: FeatureCatalog.Category) {
        categoryLauncher.launch(ConsoleCategoryActivity.intent(this, category))
    }

    internal fun onFuncClick(id: String) {
        // 进功能前若没连上但有记忆目标，顺手静默拉起（不打断界面）
        if (!ConnectionHub.isConnected()) ConnectionHub.ensureLink(this)
        when (id) {
            "preview" -> openPreview()
            "scroll" -> openTextDialog()
            "led" -> openLedDialog()
            "plot" -> openPlotDialog()
            "emoji" -> openEmojiDialog()
            "tone" -> openToneDialog()
            "melody" -> openMelodyDialog()
            "demo" -> runDemo()
            "pointer" -> startActivity(Intent(this, PointerModeActivity::class.java))
            "number" -> openNumberDialog()
            "compass" -> openDashboardDialog()
            "gesture" -> openDashboardDialog()
            "brightness" -> openBrightnessDialog()
            "sensor" -> openDashboardDialog()
            "custom" -> openCustomDialog()
            "clear" -> send(Protocol.clear())
            "makecode" -> startActivity(Intent(this, MakeCodeActivity::class.java))
            "customflash" -> startActivity(Intent(this, CustomFlashActivity::class.java))
            "help" -> openHelpDialog()
            "dashboard" -> openDashboardDialog()
            "macro" -> openMacroDialog()
            "timer" -> openTimerDialog()
            "devtools" -> openDevToolsDialog()
            // v3.3.0-preview 新增（实现见 ui/ConsoleExtras.kt、ConsoleGames.kt、ConsoleTools.kt）
            "thermometer" -> openThermometer()
            "light" -> openLightMeter()
            "level" -> openLevel()
            "buttons" -> openButtonMonitor()
            "logger" -> openSensorLogger()
            "dice" -> openDice()
            "coin" -> openCoin()
            "reaction" -> openReactionGame()
            "guess" -> openGuessGame()
            "rps" -> openRps()
            "animation" -> openAnimationStudio()
            "piano" -> openPiano()
            "barrage" -> openBarrage()
            "morse" -> openMorse()
            "findbell" -> openFindBell()
            // 4.0.0-preview 新增（实现见 ui/ConsoleMore*.kt）
            // 显示与预览
            "marquee" -> openMarquee()
            "bigdigit" -> openBigDigit()
            "brightauto" -> openBrightAuto()
            "nightlight" -> openNightLight()
            // 声音与演示
            "metronome" -> openMetronome()
            "doorbell" -> openDoorbell()
            "siren" -> openSiren()
            "soundboard" -> openSoundboard()
            "tunepad" -> openTunePad()
            "melodymix" -> openMelodyMix()
            // 传感器与数据
            "compassrose" -> openCompassRose()
            "shakecount" -> openShakeCount()
            "gesturelog" -> openGestureLog()
            "droptest" -> openDropTest()
            "tempchart" -> openTempChart()
            "motiondetector" -> openMotionDetector()
            "sensortest" -> openSensorTest()
            // 编程与刷写
            "codelib" -> openCodeLib()
            "codecheck" -> openCodeCheck()
            "tutorials" -> openTutorials()
            "pintable" -> openPinTable()
            "errordict" -> openErrorDict()
            "flashlog" -> openFlashLog()
            "scriptinfo" -> openScriptInfo()
            // 指针与自动化
            "shakebutton" -> openShakeButton()
            "gesturelock" -> openGestureLock()
            "linkmonitor" -> openLinkMonitor()
            "commandseq" -> openCommandSeq()
            "quickpanel" -> openQuickPanel()
            "logstream" -> openLogStream()
            "idlebot" -> openIdleBot()
            // 游戏与互动
            "tapcount" -> openTapCount()
            "holdgame" -> openHoldGame()
            "hotpotato" -> openHotPotato()
            "roulette" -> openRoulette()
            "truthdare" -> openTruthDare()
            "quizshow" -> openQuizShow()
            "blackjack" -> openBlackjack()
            // 实用工具
            "torch" -> openTorch()
        }
    }

    private fun send(line: String): Boolean {
        if (isFinishing || isDestroyed) return false
        val ok = ConnectionHub.send(line)
        if (!ok) {
            // 未连接提示带上「重新连接」动作：不再让用户自己去翻菜单
            val now = System.currentTimeMillis()
            if (now - lastSendFailAt > 2500) {
                lastSendFailAt = now
                Snackbar.make(b.root, R.string.status_disconnected, Snackbar.LENGTH_LONG)
                    .setAction(R.string.console_reconnect) { reconnectNow() }
                    .show()
            }
        }
        return ok
    }

    /** 供 ui 包内控制台扩展功能（温度计 / 骰子等）复用：根视图 + 发送通道。 */
    internal val consoleRoot: View get() = b.root

    internal fun sendLine(line: String): Boolean = send(line)

    // ---------- 通用输入对话框（原生 Material3 TextInputLayout） ----------
    private fun inputDialog(
        title: String,
        hint: String,
        inputType: Int,
        positiveText: String = getString(R.string.send),
        onOk: (String) -> Unit
    ) {
        val vb = DialogInputBinding.inflate(layoutInflater)
        vb.til.hint = hint
        vb.etInput.inputType = inputType
        com.lingyi.mbos.ui.M3Kit.styleButtons(vb.root) // 统一 M3 Expressive 按钮规格（v4.2）
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(vb.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(positiveText) { _, _ ->
                val t = vb.etInput.text.toString().trim()
                if (t.isNotEmpty()) onOk(t)
            }
            .show()
    }

    // ---------- 滚动文字 ----------
    private fun openTextDialog() {
        inputDialog(
            title = getString(R.string.func_scroll),
            hint = getString(R.string.hint_text),
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        ) { send(Protocol.scroll(it)) }
    }

    // ---------- LED 画板 ----------
    private fun openLedDialog() {
        val d = DialogLedBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this).setView(d.root).create()
        buildLedGrid(d.gridLed)
        d.btnClear.setOnClickListener {
            ledMatrix.forEach { it.fill(false) }
            buildLedGrid(d.gridLed)
        }
        d.btnShow.setOnClickListener {
            send(Protocol.show(ledMatrix))
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun buildLedGrid(grid: GridLayout) {
        buildMatrixEditor(grid, ledMatrix) { x, y ->
            ledMatrix[y][x] = !ledMatrix[y][x]
            setMatrixCell(grid, x, y, ledMatrix[y][x])
        }
    }

    // ---------- 单点绘制 ----------
    private fun openPlotDialog() {
        val d = DialogPlotBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.func_plot)
            .setView(d.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.send) { _, _ ->
                val x = (d.etX.text.toString().toIntOrNull() ?: 0).coerceIn(0, 4)
                val y = (d.etY.text.toString().toIntOrNull() ?: 0).coerceIn(0, 4)
                send(Protocol.plot(x, y, d.switchOn.isChecked))
            }
            .show()
    }

    // ---------- 一键演示 ----------
    private fun runDemo() {
        if (!ConnectionHub.isConnected()) {
            Snackbar.make(b.root, R.string.status_disconnected, Snackbar.LENGTH_SHORT).show()
            return
        }
        Snackbar.make(b.root, R.string.demo_started, Snackbar.LENGTH_SHORT).show()
        val heart = rowsToMatrix(Patterns.HEART)
        val script = listOf(
            Protocol.clear(),
            Protocol.brightness(7),
            Protocol.scroll("Hello, 灵逸 MB"),
            Protocol.show(heart),
            Protocol.tone(523, 200),
            Protocol.tone(659, 200),
            Protocol.tone(784, 300),
            Protocol.melody("ODE"),
            Protocol.clear()
        )
        Thread {
            script.forEach { line ->
                if (isFinishing || isDestroyed) return@Thread
                runOnUiThread { send(line) }
                Thread.sleep(1200)
            }
        }.start()
    }

    // ---------- 表情 ----------
    private fun openEmojiDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_emoji, null)
        val container = view.findViewById<ViewGroup>(R.id.emojiContainer)
        val dialog = MaterialAlertDialogBuilder(this).setView(view).create()
        EMOJIS.forEach { (name, rows) ->
            val btn = com.google.android.material.button.MaterialButton(this).apply {
                text = name
                setOnClickListener {
                    val m = rowsToMatrix(rows)
                    ledMatrix = m
                    send(Protocol.show(m))
                    dialog.dismiss()
                }
            }
            container.addView(btn)
        }
        dialog.show()
    }

    // ---------- 音调 ----------
    private fun openToneDialog() {
        val d = DialogToneBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(this)
            .setView(d.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.play) { _, _ ->
                val f = d.etFreq.text.toString().toIntOrNull() ?: 440
                val dur = d.etDur.text.toString().toIntOrNull() ?: 500
                send(Protocol.tone(f, dur))
            }
            .show()
    }

    // ---------- 旋律 ----------
    private fun openMelodyDialog() {
        val names = MELODIES.map { it.first }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("播放旋律")
            .setItems(names) { _, which -> send(Protocol.melody(MELODIES[which].second)) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------- 数字 ----------
    private fun openNumberDialog() {
        inputDialog(
            title = "显示数字",
            hint = "输入一个数字，如 2026",
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        ) { it.toIntOrNull()?.let { n -> send(Protocol.number(n)) } }
    }

    // ---------- 亮度 ----------
    private fun openBrightnessDialog() {
        inputDialog(
            title = "屏幕亮度",
            hint = "亮度 0–9（0 最暗，9 最亮）",
            inputType = android.text.InputType.TYPE_CLASS_NUMBER,
            positiveText = "设置"
        ) { it.toIntOrNull()?.let { n -> send(Protocol.brightness(n)) } }
    }

    // ---------- 自定义指令 ----------
    private fun openCustomDialog() {
        inputDialog(
            title = getString(R.string.func_custom),
            hint = "SCROLL:hello",
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        ) { send(Protocol.custom(it)) }
    }

    // ---------- 实时仪表盘（整合预览读数能力，常驻控制台） ----------
    private fun openDashboardDialog() {
        val tv = TextView(this).apply {
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(24, 16, 24, 16)
        }
        val sv = ScrollView(this).apply { addView(tv) }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("实时仪表盘")
            .setView(sv)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        val handler = Handler(Looper.getMainLooper())
        val dataListener = object : MicrobitConnection.Listener {
            override fun onState(state: ConnState) {}
            override fun onData(data: String) {
                if (data.contains("A:") && data.contains("T:")) {
                    runOnUiThread { tv.text = dashboardText(data) }
                }
            }
        }
        val tick = M3Kit.Ticker({ 800L }, handler) {
            ConnectionHub.send(Protocol.read())
            true
        }
        dialog.setOnShowListener {
            ConnectionHub.addListener(dataListener)
            tick.start()
        }
        dialog.setOnDismissListener {
            ConnectionHub.removeListener(dataListener)
            tick.stop()
        }
        dialog.show()
    }

    private fun dashboardText(data: String): String {
        val fields = data.split('|').mapNotNull {
            val p = it.split(':', limit = 2)
            if (p.size == 2) p[0] to p[1] else null
        }.toMap()
        return buildString {
            append("按键 A：${if (fields["A"] == "1") "按下" else "松开"}    按键 B：${if (fields["B"] == "1") "按下" else "松开"}\n")
            append("温度：${fields["T"] ?: "-"} °C     光线：${fields["L"] ?: "-"}\n")
            append("加速度 X / Y / Z：${fields["X"] ?: "-"}/${fields["Y"] ?: "-"}/${fields["Z"] ?: "-"}\n")
            append("指南针朝向：${fields["C"] ?: "-"}°\n")
            append("手势：${Gestures.label(fields["G"])}")
        }
    }

    // ---------- 宏指令（收藏常用指令，一键发送） ----------
    private fun openMacroDialog() {
        val macros = Prefs.getMacros(this)
        if (macros.isEmpty()) {
            inputDialog("添加宏指令", "如 SCROLL:hello", android.text.InputType.TYPE_CLASS_TEXT) {
                Prefs.addMacro(this, it)
                Snackbar.make(b.root, "已收藏：$it", Snackbar.LENGTH_SHORT).show()
                openMacroDialog()
            }
            return
        }
        var d: android.content.DialogInterface? = null
        val items = macros.toTypedArray()
        d = MaterialAlertDialogBuilder(this)
            .setTitle("宏指令（点击发送）")
            .setItems(items) { _, which ->
                send(macros[which])
                d?.dismiss()
            }
            .setNegativeButton("添加") { _, _ ->
                inputDialog("添加宏指令", "如 SCROLL:hello", android.text.InputType.TYPE_CLASS_TEXT) {
                    Prefs.addMacro(this, it)
                    openMacroDialog()
                }
            }
            .setNeutralButton("清空") { _, _ ->
                macros.forEach { Prefs.removeMacro(this, it) }
                Snackbar.make(b.root, "已清空宏指令", Snackbar.LENGTH_SHORT).show()
            }
            .setPositiveButton(android.R.string.cancel, null)
            .show()
    }

    // ---------- 定时任务（按间隔循环发送指令） ----------
    private fun openTimerDialog() {
        if (timerTicker != null) {
            stopTimer()
            Snackbar.make(b.root, "定时任务已停止", Snackbar.LENGTH_SHORT).show()
            return
        }
        inputDialog("定时任务 · 指令", "要循环发送的内容", android.text.InputType.TYPE_CLASS_TEXT) { cmd ->
            inputDialog("定时任务 · 间隔（秒）", "1", android.text.InputType.TYPE_CLASS_NUMBER) { sec ->
                val ms = (sec.toIntOrNull()?.coerceAtLeast(1) ?: 1) * 1000L
                val ticker = M3Kit.Ticker({ ms }, timerHandler) {
                    send(cmd)
                    true
                }
                timerTicker = ticker
                ticker.start(ms)
                Snackbar.make(b.root, "定时任务已启动（再点「定时任务」可停止）", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun stopTimer() {
        timerTicker?.stop()
        timerTicker = null
    }

    // ---------- 开发者工具（整合开发者页：协议自检 / 连接诊断 / 指令注入 / 模拟器周期上报） ----------
    private fun openDevToolsDialog() {
        val actions = arrayOf(
            "协议自检（发送 INFO / READ）",
            "连接诊断",
            "指令注入",
            "模拟器周期上报：开 / 关"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("开发者工具")
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> {
                        send(Protocol.info())
                        send(Protocol.read())
                        Snackbar.make(b.root, "已发送自检指令，观察回显", Snackbar.LENGTH_SHORT).show()
                    }
                    1 -> showDiagnosisDialog()
                    2 -> openCustomDialog()
                    3 -> toggleSimulatorPeriodic()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** 模拟器周期上报开关：不依赖悬浮窗权限也能在控制台直接关掉持续推送。 */
    private fun toggleSimulatorPeriodic() {
        val sim = ConnectionHub.current as? SimulatorConnection
        if (sim == null || !sim.isConnected()) {
            Snackbar.make(b.root, "当前未连接模拟器（开发版）", Snackbar.LENGTH_SHORT).show()
            return
        }
        sim.periodicReport = !sim.periodicReport
        Snackbar.make(
            b.root,
            "模拟器周期上报已${if (sim.periodicReport) "开启" else "关闭"}",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun showDiagnosisDialog() {
        val type = ConnectionHub.current?.type ?: "未连接"
        val fw = Prefs.getKnownFirmwareVersion(this) ?: "未知"
        MaterialAlertDialogBuilder(this)
            .setTitle("连接诊断")
            .setMessage(
                "连接方式：$type\n连接状态：${if (ConnectionHub.isConnected()) "已连接" else "未连接"}\n" +
                "已知固件版本：$fw"
            )
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ---------- 帮助 ----------
    private fun openHelpDialog() {
        val d = DialogHelpBinding.inflate(layoutInflater)
        d.tvScript.text = FirmwareScript.script
        MaterialAlertDialogBuilder(this)
            .setView(d.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.copied) { _, _ ->
                val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("MB-OS firmware", FirmwareScript.script))
                Snackbar.make(b.root, R.string.copied, Snackbar.LENGTH_SHORT).show()
            }
            .show()
    }

    companion object {
        private val EMOJIS = listOf(
            "笑脸" to listOf("01010", "01010", "00000", "10001", "01110"),
            "难过" to listOf("01010", "01010", "00000", "01110", "10001"),
            "爱心" to Patterns.HEART,
            "对勾" to listOf("00000", "00001", "00010", "10100", "11110"),
            "叉" to Patterns.CROSS,
            "时钟" to listOf("00100", "01110", "10101", "10111", "00100"),
        )

        private val MELODIES = listOf(
            "生日快乐" to "BIRTHDAY",
            "Python 之歌" to "PYTHON",
            "Nyan 猫" to "NYAN",
            "演艺人" to "ENTERTAINER",
            "前奏曲" to "PRELUDE",
            "欢乐颂" to "ODE",
            "蓝调" to "BLUES",
            "放克" to "FUNK",
            "婚礼进行曲" to "WEDDING",
        )
    }
}

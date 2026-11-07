/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import com.google.android.material.R as MR
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.lingyi.mbos.databinding.ActivitySettingsBinding
import com.lingyi.mbos.ui.M3Kit
import com.lingyi.mbos.ui.StabilityFeatures
import com.lingyi.mbos.util.AppLog
import com.lingyi.mbos.util.Prefs
import com.lingyi.mbos.util.ThemeManager
import com.lingyi.mbos.util.UpdateChecker
import com.lingyi.mbos.util.themeColor

/**
 * 设置页：外观（主题模式 / 主题色）、
 * 连接（默认连接方式 / 实时读取间隔 / 屏幕常亮）、指针（默认灵敏度）、
 * 通用（触感反馈 / 检查更新 / 恢复默认 / 关于入口）。
 *
 * 主题变更除了 recreate() 自身，还会 bump「主题纪元」，
 * 让返回栈里的旧页面一并重建，保证全局配色一致（见 BaseActivity）。
 */
class SettingsActivity : BaseActivity() {

    private lateinit var b: ActivitySettingsBinding

    /** 同步 UI 状态时屏蔽 ChipGroup 回调，避免把初始值当成用户操作。 */
    private var syncing = true

    /** 是否正在联网检查更新（防止连点造成重复请求与状态错乱）。 */
    private var checking = false

    /** 界面风格分段选择器（仅预览版构建）；-1 表示不存在。 */
    private var skinGroup: ChipGroup? = null
    private var chipM3Id = -1
    private var chipVoyageId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)

        // M3 Expressive：整页可点击元素统一按压反馈（4.1.1-preview）
        M3Kit.applyPressFeedback(b.root)

        b.toolbar.setNavigationOnClickListener { finish() }

        setupSkinStyle()
        setupAppearance()
        setupConnection()
        setupPointer()
        setupGeneral()
        setupStability()

        loadState()
        syncing = false
    }

    override fun onResume() {
        super.onResume()
        // 从关于页 / 开发者页返回时同步最新的通道选择
        b.channelPicker.refresh()
    }

    // ---------- 界面风格切换（仅预览版） ----------

    /**
     * 设置里切换「稳定版 M3」与「Voyage 风格」。
     * 开关写入 Prefs 并 bump 主题纪元：设置页自身 recreate 立即呈现新配色/排版，
     * 返回栈里的主界面 onResume 检测纪元变化自我重建 —— 届时挂载或卸载 Voyage 皮肤，
     * 所有二级页的换肤、卡片排版、窗口与内容过渡也一起切换（判定点见 util/ThemeExt.kt）。
     * 稳定版 / 开发版不构建此卡片，界面零变化。
     */
    private fun setupSkinStyle() {
        if (BuildConfig.DEFAULT_CHANNEL != "preview") return

        val (card, body) = M3Kit.card(this)
        body.addView(M3Kit.cardTitle(this, "界面风格"))
        body.addView(
            M3Kit.cardDesc(
                this,
                "在 Voyage 新界面与稳定版 M3 原生界面之间切换。切换后主界面、二级页与过渡动画同步重建。"
            )
        )

        val group = ChipGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
        }
        val checkedBg = themeColor(MR.attr.colorPrimary)
        val normalBg = themeColor(MR.attr.colorSurfaceVariant)
        val checkedFg = themeColor(MR.attr.colorOnPrimary)
        val normalFg = themeColor(MR.attr.colorOnSurfaceVariant)

        // 分段选择器：选中项为深青绿实色胶囊 + 白字，与工具台分类选中态一致
        fun styledChip(text: String): Chip = Chip(this).apply {
            this.text = text
            id = View.generateViewId()
            isCheckable = true
            isCheckedIconVisible = false
            chipBackgroundColor = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(checkedBg, normalBg)
            )
            setTextColor(
                ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(checkedFg, normalFg)
                )
            )
        }

        val chipM3 = styledChip("稳定版 M3")
        val chipVoyage = styledChip("Voyage 风格")
        group.addView(chipM3)
        group.addView(chipVoyage)
        chipM3Id = chipM3.id
        chipVoyageId = chipVoyage.id
        body.addView(
            group,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = M3Kit.dp(this@SettingsActivity, 12f) }
        )

        group.setOnCheckedStateChangeListener { _, ids ->
            if (syncing) return@setOnCheckedStateChangeListener
            val on = ids.firstOrNull() == chipVoyageId
            Prefs.setVoyageSkin(this, on)
            Prefs.bumpThemeEpoch(this)
            recreate()
        }
        skinGroup = group

        // 插到「外观」分区标题之后、主题卡之前
        val idx = b.settingsContent.indexOfChild(b.tvSectionAppearance) + 1
        b.settingsContent.addView(card, idx)
        M3Kit.applyPressFeedback(card)
    }

    // ---------- 外观 ----------
    private fun setupAppearance() {
        b.cgThemeMode.setOnCheckedStateChangeListener { _, ids ->
            if (syncing) return@setOnCheckedStateChangeListener
            val mode = when (ids.firstOrNull()) {
                R.id.chipModeLight -> ThemeManager.MODE_LIGHT
                R.id.chipModeDark -> ThemeManager.MODE_DARK
                else -> ThemeManager.MODE_SYSTEM
            }
            Prefs.setThemeMode(this, mode)
            Prefs.bumpThemeEpoch(this)
            ThemeManager.applyNightMode(this)
            recreate()
        }

        b.cgThemeColor.setOnCheckedStateChangeListener { _, ids ->
            if (syncing) return@setOnCheckedStateChangeListener
            val picked = when (ids.firstOrNull()) {
                R.id.chipColorDynamic -> ThemeManager.COLOR_DYNAMIC
                R.id.chipColorM3 -> ThemeManager.COLOR_M3
                R.id.chipColorTeal -> ThemeManager.COLOR_TEAL
                R.id.chipColorGold -> ThemeManager.COLOR_GOLD
                R.id.chipColorIndigo -> ThemeManager.COLOR_INDIGO
                R.id.chipColorRose -> ThemeManager.COLOR_ROSE
                R.id.chipColorForest -> ThemeManager.COLOR_FOREST
                else -> ThemeManager.COLOR_M3
            }
            // 动态取色在低版本不可用：回退 M3 官方色并提示
            val color = if (picked == ThemeManager.COLOR_DYNAMIC && !ThemeManager.isDynamicColorAvailable()) {
                b.tvDynamicHint.visibility = android.view.View.VISIBLE
                ThemeManager.COLOR_M3
            } else {
                b.tvDynamicHint.visibility = android.view.View.GONE
                picked
            }
            Prefs.setThemeColor(this, color)
            Prefs.bumpThemeEpoch(this)
            recreate()
        }

        b.swAnimation.setOnCheckedChangeListener { _, on ->
            if (syncing) return@setOnCheckedChangeListener
            Prefs.setAnimationEnabled(this, on)
            applyWindowAnimations()
        }

        b.cgConsoleStyle.setOnCheckedStateChangeListener { _, ids ->
            if (syncing) return@setOnCheckedStateChangeListener
            // 控制台展示风格：只写偏好，MainActivity onResume 发现变化即按新风格重建
            Prefs.setConsoleStyle(
                this,
                if (ids.firstOrNull() == R.id.chipConsoleGrid) {
                    Prefs.CONSOLE_STYLE_GRID
                } else {
                    Prefs.CONSOLE_STYLE_LIST
                }
            )
        }
    }

    // ---------- 连接 ----------
    private fun setupConnection() {
        b.cgDefaultConn.setOnCheckedStateChangeListener { _, ids ->
            if (syncing) return@setOnCheckedStateChangeListener
            Prefs.setConnType(this, if (ids.firstOrNull() == R.id.chipConnUsb) "usb" else "ble")
        }

        b.cgReadInterval.setOnCheckedStateChangeListener { _, ids ->
            if (syncing) return@setOnCheckedStateChangeListener
            val ms = when (ids.firstOrNull()) {
                R.id.chipInterval2s -> 2000L
                R.id.chipInterval5s -> 5000L
                else -> 1000L
            }
            Prefs.setReadIntervalMs(this, ms)
        }

        b.swKeepScreenOn.setOnCheckedChangeListener { _, on ->
            if (syncing) return@setOnCheckedChangeListener
            Prefs.setKeepScreenOn(this, on)
            if (on) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        // 对外开发者接口总开关：开启后同机其它 App 可通过本 App 控制已连接的 micro:bit
        b.swExtApi.setOnCheckedChangeListener { _, on ->
            if (syncing) return@setOnCheckedChangeListener
            Prefs.setExtApiEnabled(this, on)
        }
    }

    // ---------- 指针 ----------
    private fun setupPointer() {
        // Prefs 读取时已吸附到滑块网格（0.4 + n×0.2），这里直接可用
        b.sliderPointerSens.value = Prefs.getPointerSensitivity(this)
        renderPointerSens(b.sliderPointerSens.value)
        b.sliderPointerSens.addOnChangeListener { _, value, _ ->
            if (syncing) return@addOnChangeListener
            Prefs.setPointerSensitivity(this, value)
            renderPointerSens(Prefs.getPointerSensitivity(this))
        }
    }

    private fun renderPointerSens(value: Float) {
        b.tvPointerSens.text = String.format("%.1f×", value)
    }

    // ---------- 稳定性（4.1.0-preview） ----------
    private fun setupStability() {
        b.swStartupSelfCheck.setOnCheckedChangeListener { _, on ->
            if (syncing) return@setOnCheckedChangeListener
            Prefs.setStartupSelfCheck(this, on)
        }

        b.swAutoReconnect.setOnCheckedChangeListener { _, on ->
            if (syncing) return@setOnCheckedChangeListener
            Prefs.setAutoReconnect(this, on)
            Snackbar.make(
                b.root,
                if (on) "已开启：意外断线将自动重连" else "已关闭：断线后需手动重连",
                Snackbar.LENGTH_SHORT
            ).show()
        }

        b.btnBatteryFix.setOnClickListener {
            StabilityFeatures.applyFix(this, StabilityFeatures.Fix.BATTERY)
        }
        b.btnNotifFix.setOnClickListener { StabilityFeatures.showNotificationChannelFix(this) }
        b.btnStorageCleanup.setOnClickListener { StabilityFeatures.showStorageCleanupDialog(this) }
        b.btnCrashGuard.setOnClickListener { StabilityFeatures.showCrashGuardInfo(this) }
    }

    // ---------- 通用 ----------
    private fun setupGeneral() {
        b.swHaptic.setOnCheckedChangeListener { _, on ->
            if (syncing) return@setOnCheckedChangeListener
            Prefs.setHapticEnabled(this, on)
        }

        // 更新通道下拉：切换后立即按新通道重新检查一次
        b.channelPicker.refresh()
        b.channelPicker.onChannelChanged = { channel ->
            Snackbar.make(
                b.root,
                getString(
                    R.string.update_channel_switched,
                    com.lingyi.mbos.util.UpdateChecker.channelLabel(channel)
                ),
                Snackbar.LENGTH_SHORT
            ).show()
            checkUpdate()
        }

        b.btnCheckUpdate.setOnClickListener { checkUpdate() }
        b.btnAbout.setOnClickListener { startActivity(Intent(this, AboutActivity::class.java)) }
        b.btnReset.setOnClickListener { confirmReset() }
    }

    private fun checkUpdate() {
        if (checking) return
        checking = true
        b.btnCheckUpdate.isEnabled = false
        b.tvUpdateStatus.text = getString(R.string.update_checking)
        com.lingyi.mbos.ui.Transitions.crossFade(b.tvUpdateStatus, true)
        // Material 3 不定圆环：联网检查可能持续数秒，明确告知「正在检查」
        com.lingyi.mbos.ui.Transitions.crossFade(b.pbCheckUpdate, true)
        // 预览版通道下检查 beta 清单，稳定版通道下检查正式清单
        UpdateChecker.check(Prefs.getChannel(this)) { info ->
            if (isDestroyed) return@check
            checking = false
            b.btnCheckUpdate.isEnabled = true
            com.lingyi.mbos.ui.Transitions.crossFade(b.pbCheckUpdate, false)
            when {
                info.error != null -> {
                    b.tvUpdateStatus.text = getString(R.string.update_failed, info.error)
                }
                info.available -> {
                    Prefs.setCachedUpdate(
                        this,
                        Prefs.UpdateCache(
                            available = true,
                            url = info.apkUrl,
                            page = info.pageUrl,
                            name = info.remoteVersionName,
                            notes = info.notes,
                            published = info.publishedAt
                        )
                    )
                    b.tvUpdateStatus.text = "发现新版本 ${info.remoteVersionName}"
                    MaterialAlertDialogBuilder(this)
                        .setTitle(
                            if (info.channel == UpdateChecker.CHANNEL_PREVIEW) {
                                R.string.update_title_preview
                            } else {
                                R.string.update_title
                            }
                        )
                        .setMessage(
                            "发现新版本 ${info.remoteVersionName}（本地 v${UpdateChecker.currentVersionName()}）。\n${info.notes}"
                        )
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.update_go) { _, _ ->
                            val target = info.pageUrl.ifBlank { info.apkUrl.ifBlank { UpdateChecker.PAGE_URL } }
                            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
                        }
                        .show()
                }
                else -> {
                    b.tvUpdateStatus.text = getString(
                        R.string.update_latest,
                        info.remoteVersionName.ifBlank { UpdateChecker.currentVersionName() },
                        UpdateChecker.currentVersionName()
                    )
                }
            }
        }
    }

    private fun confirmReset() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_reset)
            .setMessage(R.string.settings_reset_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.settings_reset_confirm_ok) { _, _ ->
                Prefs.resetAppearanceSettings(this)
                Prefs.bumpThemeEpoch(this)
                ThemeManager.applyNightMode(this)
                if (!Prefs.isKeepScreenOn(this)) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                Snackbar.make(b.root, R.string.settings_reset_done, Snackbar.LENGTH_SHORT).show()
                recreate()
            }
            .show()
    }

    /**
     * 状态加载：任一步异常只影响对应控件的初始值，绝不让页面起不来。
     * （定制 ROM 上 ChipGroup.check / Slider 设值等曾出现不可解释的崩溃，
     *  表现为「点进设置闪退回引导页」，这里统一兜底。）
     */
    private fun loadState() {
        runCatching {
            skinGroup?.check(if (Prefs.isVoyageSkin(this)) chipVoyageId else chipM3Id)
        }.onFailure { AppLog.e("Settings", "界面风格控件初始化失败", it) }
        runCatching {
            b.cgThemeMode.check(
                when (ThemeManager.normalizeMode(Prefs.getThemeMode(this))) {
                    ThemeManager.MODE_LIGHT -> R.id.chipModeLight
                    ThemeManager.MODE_DARK -> R.id.chipModeDark
                    else -> R.id.chipModeSystem
                }
            )
        }.onFailure { AppLog.e("Settings", "主题模式控件初始化失败", it) }
        runCatching {
            b.cgThemeColor.check(
                when (ThemeManager.normalizeColor(Prefs.getThemeColor(this))) {
                    ThemeManager.COLOR_DYNAMIC -> R.id.chipColorDynamic
                    ThemeManager.COLOR_M3 -> R.id.chipColorM3
                    ThemeManager.COLOR_TEAL -> R.id.chipColorTeal
                    ThemeManager.COLOR_GOLD -> R.id.chipColorGold
                    ThemeManager.COLOR_INDIGO -> R.id.chipColorIndigo
                    ThemeManager.COLOR_ROSE -> R.id.chipColorRose
                    ThemeManager.COLOR_FOREST -> R.id.chipColorForest
                    else -> R.id.chipColorM3
                }
            )
            if (Prefs.getThemeColor(this) == ThemeManager.COLOR_DYNAMIC && !ThemeManager.isDynamicColorAvailable()) {
                b.tvDynamicHint.visibility = android.view.View.VISIBLE
            }
        }.onFailure { AppLog.e("Settings", "主题色控件初始化失败", it) }
        runCatching {
            b.cgDefaultConn.check(if (Prefs.getConnType(this) == "usb") R.id.chipConnUsb else R.id.chipConnBle)
            b.cgReadInterval.check(
                when (Prefs.getReadIntervalMs(this)) {
                    2000L -> R.id.chipInterval2s
                    5000L -> R.id.chipInterval5s
                    else -> R.id.chipInterval1s
                }
            )
            b.swKeepScreenOn.isChecked = Prefs.isKeepScreenOn(this)
            b.swExtApi.isChecked = Prefs.isExtApiEnabled(this)
            b.swHaptic.isChecked = Prefs.isHapticEnabled(this)
            b.swAnimation.isChecked = Prefs.isAnimationEnabled(this)
        }.onFailure { AppLog.e("Settings", "连接/通用控件初始化失败", it) }
        runCatching {
            b.cgConsoleStyle.check(
                if (Prefs.getConsoleStyle(this) == Prefs.CONSOLE_STYLE_GRID) {
                    R.id.chipConsoleGrid
                } else {
                    R.id.chipConsoleList
                }
            )
        }.onFailure { AppLog.e("Settings", "控制台布局控件初始化失败", it) }
        runCatching {
            b.swStartupSelfCheck.isChecked = Prefs.isStartupSelfCheck(this)
            b.swAutoReconnect.isChecked = Prefs.isAutoReconnect(this)
        }.onFailure { AppLog.e("Settings", "稳定性控件初始化失败", it) }
    }
}

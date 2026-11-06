/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.lingyi.mbos.databinding.ActivityAboutBinding
import com.lingyi.mbos.ui.M3Kit
import com.lingyi.mbos.ui.StabilityFeatures
import com.lingyi.mbos.util.AppLog
import com.lingyi.mbos.util.CrashLog
import com.lingyi.mbos.util.LogMailer
import com.lingyi.mbos.util.Prefs
import com.lingyi.mbos.util.UpdateChecker
import com.lingyi.mbos.util.UpdateDialog

/**
 * 关于页：应用信息 + 「检查更新」中心（真实联网拉取 version.json）。
 * 从控制台菜单迁移而来，作为所有「更新/关于」能力的统一入口。
 */
class AboutActivity : BaseActivity() {

    private lateinit var b: ActivityAboutBinding
    private var devTaps = 0
    private var lastDevTapAt = 0L

    /** 是否正在联网检查更新（防止连点造成重复请求与状态错乱）。 */
    private var checking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(b.root)

        // M3 Expressive：整页可点击元素统一按压反馈（4.1.1-preview）
        M3Kit.applyPressFeedback(b.root)

        b.toolbar.setNavigationOnClickListener { finish() }

        val verName = UpdateChecker.currentVersionName()
        val verCode = UpdateChecker.currentVersionCode()
        b.tvVersion.text = "版本 $verName ($verCode)"

        b.btnCheckUpdate.setOnClickListener { checkUpdate() }
        b.btnVisitPage.setOnClickListener { openPage() }
        b.btnLicense.setOnClickListener { startActivity(Intent(this, LicenseActivity::class.java)) }
        b.btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        b.btnCopyVersion.setOnClickListener { copyVersion(verName, verCode) }
        b.btnDev.setOnClickListener { startActivity(Intent(this, DeveloperActivity::class.java)) }
        b.tvVersion.setOnClickListener { onDevHintTap() }
        syncDevEntry()

        // 错误日志与反馈：一键打包发送到开发者邮箱
        b.btnSendLog.setOnClickListener { sendLog() }
        b.btnShareLog.setOnClickListener { shareLog() }
        b.btnViewLog.setOnClickListener { viewLog() }
        b.btnClearLog.setOnClickListener { confirmClearLog() }
        refreshLogStatus()

        // 稳定性与反馈（4.1.0-preview）：诊断报告 / 反馈模板 / 更新重试 / 通道说明 / 依赖清单
        b.btnDiagnostic.setOnClickListener { StabilityFeatures.showDiagnosticReport(this) }
        b.btnFeedbackTemplate.setOnClickListener { StabilityFeatures.showFeedbackTemplate(this) }
        b.btnUpdateRetry.setOnClickListener { StabilityFeatures.showUpdateRetry(this) { checkUpdate() } }
        b.btnChannelExplainer.setOnClickListener { StabilityFeatures.showChannelExplainer(this) }
        b.btnDependencies.setOnClickListener { StabilityFeatures.showDependencies(this) }

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

        // 进入页面即自动真实检查一次
        checkUpdate()
    }

    override fun onResume() {
        super.onResume()
        syncDevEntry()
        refreshLogStatus()
        // 从开发者页返回时同步最新的通道选择
        b.channelPicker.refresh()
    }

    // ---------- 错误日志与反馈 ----------

    private fun refreshLogStatus() {
        val kb = AppLog.totalSize() / 1024
        val crashes = CrashLog.count()
        b.tvLogStatus.text = buildString {
            append("日志 ${AppLog.files().size} 个 · 约 $kb KB")
            if (crashes > 0) {
                append(" · 崩溃 $crashes 次")
                CrashLog.lastCrashTime()?.let { append("（最近 $it）") }
            } else {
                append(" · 无崩溃记录")
            }
        }
    }

    /** 一键发送：打包 zip（失败回退 txt）→ 唤起邮件客户端，收件人已预填。 */
    private fun sendLog() {
        CrashLog.markSeen(this)
        LogMailer.sendByEmail(this)
        refreshLogStatus()
    }

    /** 分享日志包到任意 App（微信 / QQ / 蓝牙 / 网盘等）。 */
    private fun shareLog() {
        CrashLog.markSeen(this)
        LogMailer.sharePackage(this)
        refreshLogStatus()
    }

    /** 查看：最近一次崩溃全文 + 运行日志尾部，可复制。 */
    private fun viewLog() {
        CrashLog.markSeen(this)
        val crash = CrashLog.readLastCrash(this)
        val text = buildString {
            if (!crash.isNullOrBlank()) {
                append("【最近一次崩溃】\n").append(crash).append("\n\n")
            }
            append("【运行日志尾部】\n").append(AppLog.latestTail().ifBlank { "(暂无)" })
        }
        val tv = android.widget.TextView(this).apply {
            this.text = text
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(24, 16, 24, 16)
        }
        val sv = android.widget.ScrollView(this).apply { addView(tv) }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.log_view_title)
            .setView(sv)
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.log_copy) { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("灵逸 MB 日志", text))
                Snackbar.make(b.root, R.string.log_copied, Snackbar.LENGTH_SHORT).show()
            }
            .setPositiveButton(R.string.crash_prompt_send) { _, _ -> LogMailer.sendByEmail(this) }
            .show()
        refreshLogStatus()
    }

    private fun confirmClearLog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.log_clear)
            .setMessage(R.string.log_clear_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.log_clear) { _, _ ->
                AppLog.clear()
                CrashLog.clear()
                refreshLogStatus()
                Snackbar.make(b.root, R.string.log_cleared, Snackbar.LENGTH_SHORT).show()
            }
            .show()
    }

    // ---------- 开发者调试模式：连按版本号 7 次解锁；预览版 / 开发版自带开发者模式，自动显示入口 ----------
    private fun syncDevEntry() {
        val visible = Prefs.isDevMode(this) || com.lingyi.mbos.BuildConfig.IS_DEVTEST
        b.btnDev.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun onDevHintTap() {
        val now = System.currentTimeMillis()
        devTaps = if (now - lastDevTapAt > 1500) 1 else devTaps + 1
        lastDevTapAt = now
        if (devTaps >= 7) {
            devTaps = 0
            Prefs.setDevMode(this, true)
            syncDevEntry()
            Snackbar.make(b.root, R.string.dev_unlocked, Snackbar.LENGTH_LONG).show()
        }
    }

    // ---------- 检查更新（真实联网） ----------
    private fun checkUpdate() {
        if (checking) return
        checking = true
        b.btnCheckUpdate.isEnabled = false
        b.tvUpdateStatus.text = getString(R.string.update_checking)
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
                    UpdateDialog.show(this, info)
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

    /** 打开下载页（优先 pageUrl，其次 apkUrl，最后发布页地址）。 */
    private fun openPage() {
        val cache = Prefs.getCachedUpdate(this)
        val target = (cache?.page?.takeIf { it.isNotBlank() }
            ?: cache?.url?.takeIf { it.isNotBlank() }
            ?: UpdateChecker.PAGE_URL)
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
            .onFailure { Snackbar.make(b.root, R.string.update_failed, Snackbar.LENGTH_SHORT).show() }
    }

    private fun copyVersion(verName: String, verCode: Int) {
        val text = "灵逸 MB v$verName ($verCode) · micro:bit V2 第三方客户端"
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("灵逸 MB 版本", text))
        Snackbar.make(b.root, R.string.about_copied, Snackbar.LENGTH_SHORT).show()
    }
}
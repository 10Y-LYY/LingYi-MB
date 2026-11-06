/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.R as MR
import com.lingyi.mbos.util.CrashLog
import com.lingyi.mbos.util.LogMailer
import com.lingyi.mbos.util.Prefs
import com.lingyi.mbos.util.UpdateChecker
import com.lingyi.mbos.util.themeColor
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.lingyi.mbos.ble.BleConnection
import com.lingyi.mbos.ble.ConnectionHub
import com.lingyi.mbos.ble.ConnState
import com.lingyi.mbos.ble.MicrobitConnection
import com.lingyi.mbos.databinding.ActivityGuideBinding
import com.lingyi.mbos.ui.BoardInfo
import com.lingyi.mbos.ui.FirmwareScript
import com.lingyi.mbos.ui.M3Kit
import com.lingyi.mbos.ui.Protocol
import com.lingyi.mbos.ui.StabilityFeatures
import com.lingyi.mbos.util.ActionTrail
import java.io.InputStream

class GuideActivity : BaseActivity() {

    private lateinit var b: ActivityGuideBinding
    private var connected = false

    /** 本次启动是否首次进入引导页（onCreate 时取样，markLaunched 会改掉它）。 */
    private var firstLaunch = true

    /** 是否有新版本 App 可更新（缓存值，联网复核后刷新）。 */
    private var updateAvailable = false

    /** 本次会话是否已收到板载 INFO 回显（连接成功后置 true，断开置 false）。 */
    private var boardChecked = false

    /** 首次启动延时弹出的自检对话框（onDestroy 时移除，避免向已销毁页面弹窗）。 */
    private var selfCheckPending: Runnable? = null

    /** 最近一次读到的板载信息（MB-OS 版本 + MicroPython 版本），未读取时为 null。 */
    private var boardInfo: BoardInfo? = null

    private var pendingAction: (() -> Unit)? = null

    /** 开发者模式：连按版本提示 7 次解锁。 */
    private var devTaps = 0
    private var lastDevTapAt = 0L

    /** 内置合并固件路径（assets 下） */
    private val FW_ASSET = "firmware/micropython-microbit-v2.hex"
    private val FW_NAME = "micropython-microbit-v2.hex"

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) pendingAction?.invoke()
        else Snackbar.make(b.root, "需要蓝牙权限才能连接", Snackbar.LENGTH_LONG).show()
        pendingAction = null
    }

    /** 选择 MICROBIT U 盘（SAF） */
    private val treeLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) {
            setFlashStatus(getString(R.string.guide_flash_idle))
            return@registerForActivityResult
        }
        writeFirmwareToTree(uri)
    }

    private fun ensurePermission(then: () -> Unit) {
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
        if (missing.isEmpty()) then() else { pendingAction = then; permLauncher.launch(missing.toTypedArray()) }
    }

    private val listener = object : MicrobitConnection.Listener {
        override fun onState(state: ConnState) {
            runOnUiThread {
                connected = state.connected
                b.blockLoading.visibility = View.GONE
                b.tvStatus.text = if (state.connected) getString(R.string.guide_connected) else state.message
                b.tvStatus.setTextColor(
                    this@GuideActivity.themeColor(
                        if (state.connected) MR.attr.colorPrimary else MR.attr.colorOnSurfaceVariant
                    )
                )
                b.btnEnter.isEnabled = state.connected
                b.btnConnect.isEnabled = !state.connected
                if (!state.connected) {
                    // 断开：清除板载信息缓存，版本行回退为「仅软件版本」
                    boardChecked = false
                    boardInfo = null
                    updateVersionInfo()
                }
                if (state.connected) {
                    // 连接就绪后回读板载固件版本，用于判断是否提示重新刷入
                    ConnectionHub.send(Protocol.info())
                }
            }
        }

        override fun onData(data: String) {
            // 引导页不展示回传数据，仅解析 INFO 行中的板载固件版本
            val info = Protocol.parseInfo(data) ?: return
            // 以板载真实版本刷新「已知固件版本」；板载未安装 MB-OS 时置空（视为需刷入）
            Prefs.setKnownFirmwareVersion(this@GuideActivity, info.mbos)
            boardChecked = true
            boardInfo = info
            runOnUiThread {
                applyCards()
                updateVersionInfo()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityGuideBinding.inflate(layoutInflater)
        setContentView(b.root)

        val saved = Prefs.getConnType(this)
        if (saved == "usb") b.chipGroup.check(R.id.chipUsb) else b.chipGroup.check(R.id.chipBle)
        updateHint()

        b.chipGroup.setOnCheckedChangeListener { _, _ ->
            updateHint()
            Prefs.setConnType(this, selectedType())
        }

        b.btnConnect.setOnClickListener { ensurePermission { doConnect() } }
        b.btnPickBonded.setOnClickListener { ensurePermission { showBondedPicker() } }
        b.btnFlash.setOnClickListener { onFlashClicked() }
        b.btnExportFw.setOnClickListener { exportFirmwareToDownloads() }
        b.btnEnter.setOnClickListener {
            if (connected) {
                startActivity(Intent(this, MainActivity::class.java))
            } else {
                Snackbar.make(b.root, R.string.guide_please_connect, Snackbar.LENGTH_SHORT).show()
            }
        }

        b.btnDev.setOnClickListener { startActivity(Intent(this, DeveloperActivity::class.java)) }
        // 开发者专用：未连接也可直接进入控制台调试界面
        b.btnDevConsole.setOnClickListener { startActivity(Intent(this, MainActivity::class.java)) }
        b.tvDevHint.setOnClickListener { onDevHintTap() }
        syncDevEntry()

        // ===== 稳定性工具（4.1.0-preview → 4.1.1 收敂为单一问号入口） =====
        // 全部稳定性工具（自检 / FAQ / 固件对比 / 连接向导）移入二级页面，
        // 引导页只保留一个问号入口，点击跳 StabilityToolsActivity。
        b.cardStabilityTools.setOnClickListener {
            startActivity(
                Intent(this, StabilityToolsActivity::class.java).apply {
                    putExtra(StabilityToolsActivity.EXTRA_MBOS, boardInfo?.mbos)
                    putExtra(StabilityToolsActivity.EXTRA_MPY, boardInfo?.micropython)
                }
            )
            ActionTrail.record("打开稳定性工具")
        }
        // M3 Expressive：整页可点击元素统一按压反馈 + 问号入口弹簧入场
        M3Kit.applyPressFeedback(b.root)
        com.lingyi.mbos.ui.Transitions.springIn(b.cardStabilityTools, 150L)
        updateOfflineCard()
        // 首次启动（且开启了开机自检）时延迟弹出自检，避免与页面入场动画抢焦点
        if (Prefs.isStartupSelfCheck(this) && Prefs.isFirstLaunch(this)) {
            selfCheckPending = Runnable { StabilityFeatures.showSelfCheckDialog(this) }
            b.root.postDelayed(selfCheckPending, 900)
        }

        // ===== 首次启动 / 真实检查更新 =====
        // 先按缓存展示，避免白屏；再异步联网复核。
        firstLaunch = Prefs.isFirstLaunch(this)
        updateAvailable = Prefs.getCachedUpdate(this)?.available == true
        updateVersionInfo()
        applyCards()
        b.btnUpdate.setOnClickListener { openUpdate() }

        // 预览版通道下检查 beta 清单，稳定版通道下检查正式清单
        UpdateChecker.check(Prefs.getChannel(this)) { info ->
            Prefs.setCachedUpdate(
                this,
                Prefs.UpdateCache(
                    available = info.available,
                    url = info.apkUrl,
                    page = info.pageUrl,
                    name = info.remoteVersionName,
                    notes = info.notes,
                    published = info.publishedAt
                )
            )
            updateAvailable = info.available
            applyCards()
        }

        // 仅在全新启动（非配置变更重建）时标记「已启动过」，保证「仅首次显示刷入固件」。
        if (savedInstanceState == null) Prefs.markLaunched(this)
    }

    override fun onResume() {
        super.onResume()
        syncDevEntry()
        updateOfflineCard()
        maybePromptPendingCrash()
    }

    // ---------- 崩溃反馈提示 ----------

    /** 本次进程是否已弹过崩溃提示（避免页面反复跳转时重复打扰）。 */
    private var crashPromptShown = false

    /**
     * 启动时检测「待处理崩溃」：MBOS 机制下进程崩溃后系统会重启回引导页，
     * 此时提示用户一键反馈，避免崩溃静默丢失。
     */
    private fun maybePromptPendingCrash() {
        if (crashPromptShown) return
        val pending = runCatching { CrashLog.pendingCrashes(this) }.getOrNull().orEmpty()
        if (pending.isEmpty()) return
        crashPromptShown = true
        val last = CrashLog.lastCrashTime() ?: "-"
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.crash_prompt_title)
            .setMessage(getString(R.string.crash_prompt_msg, pending.size, last))
            .setNegativeButton(R.string.crash_prompt_ignore) { _, _ -> CrashLog.markSeen(this) }
            .setNeutralButton(R.string.crash_prompt_view) { _, _ ->
                CrashLog.markSeen(this)
                showCrashDetail()
            }
            .setPositiveButton(R.string.crash_prompt_send) { _, _ ->
                CrashLog.markSeen(this)
                LogMailer.sendByEmail(this)
            }
            .show()
    }

    /** 崩溃详情对话框（等宽可选中复制）。 */
    private fun showCrashDetail() {
        val crash = CrashLog.readLastCrash(this) ?: return
        val tv = android.widget.TextView(this).apply {
            text = crash
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(24, 16, 24, 16)
        }
        val sv = android.widget.ScrollView(this).apply { addView(tv) }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dev_crash_title)
            .setView(sv)
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.dev_crash_copy) { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("灵逸 MB 崩溃日志", crash))
                Snackbar.make(b.root, R.string.dev_crash_copied, Snackbar.LENGTH_SHORT).show()
            }
            .setPositiveButton(R.string.crash_prompt_send) { _, _ -> LogMailer.sendByEmail(this) }
            .show()
    }

    // ---------- 开发者调试模式入口 ----------
    private fun syncDevEntry() {
        val dev = Prefs.isDevMode(this)
        com.lingyi.mbos.ui.Transitions.crossFade(b.btnDev, dev)
        com.lingyi.mbos.ui.Transitions.crossFade(b.btnDevConsole, dev)
    }

    // ---------- 无网络时展示离线提示卡（稳定性功能） ----------

    /** 无网络时展示离线提示卡（核心功能离线可用，减少「是不是坏了」的误判）。 */
    private fun updateOfflineCard() {
        b.cardOffline.visibility =
            if (StabilityFeatures.isNetworkAvailable(this)) View.GONE else View.VISIBLE
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

    /**
     * 根据「是否首次启动」「板载固件是否为旧版/未安装」「是否有 App 更新」切换三张卡：
     * 刷机卡 / 更新卡 / 未连接小提示。刷机卡在首次启动、检测到旧固件或板载未安装
     * MB-OS 固件时出现（兑现「新固件发布时提示重新刷入」），App 有更新时优先展示更新卡。
     */
    private fun applyCards() {
        val known = Prefs.getKnownFirmwareVersion(this)
        // 板载固件旧于本 App 内置版本 → 提示重新刷入（按数值逐段比较，兼容 2.9.1 < 3.0.0）
        val fwStale = known != null && Protocol.isOlder(known, FirmwareScript.VERSION)
        // 已连接且板载在跑裸 MicroPython（无 MB-OS 字段）→ 必须刷入才能用
        val fwMissing = boardChecked && boardInfo?.mbos.isNullOrBlank()
        val needFlash = firstLaunch || fwStale || fwMissing
        val showFlash = needFlash && !updateAvailable
        val showNoConn = !needFlash && !updateAvailable
        com.lingyi.mbos.ui.Transitions.crossFade(b.cardFlash, showFlash)
        com.lingyi.mbos.ui.Transitions.crossFade(b.cardNoConn, showNoConn)
        com.lingyi.mbos.ui.Transitions.crossFade(b.cardUpdate, updateAvailable)

        when {
            fwMissing -> {
                // 板载未安装 MB-OS 助手脚本：明确告知需刷入完整固件
                b.tvFlashTitle.setText(R.string.guide_flash_title_missing)
                b.tvFlashDesc.text = getString(
                    R.string.guide_flash_desc_missing,
                    boardInfo?.micropython ?: "-"
                )
            }
            fwStale -> {
                // 旧固件：标题/正文切换为更新提示，明确告知新旧版本差异
                b.tvFlashTitle.setText(R.string.guide_flash_title_update)
                b.tvFlashDesc.text = getString(R.string.guide_flash_desc_update, FirmwareScript.VERSION, known)
            }
            else -> {
                // 复位为首次刷机引导文案（例如刚刷完新固件，卡片因首次启动仍可见）
                b.tvFlashTitle.setText(R.string.guide_flash_title)
                b.tvFlashDesc.setText(R.string.guide_flash_desc)
            }
        }

        if (updateAvailable) {
            val cache = Prefs.getCachedUpdate(this)
            val name = cache?.name?.takeIf { it.isNotBlank() } ?: UpdateChecker.currentVersionName()
            val base = "灵逸 MB 新版本 $name 已发布"
            b.tvUpdateDesc.text = if (cache?.notes?.isNotBlank() == true) "$base：${cache.notes}" else base
        }
    }

    /**
     * 刷新版本信息行：连接后直读并展示「板载固件版本 + 软件版本」，
     * 未连接时仅展示软件版本。这是用户核对「固件版本 / 软件版本」的入口。
     */
    private fun updateVersionInfo() {
        val appVer = UpdateChecker.currentVersionName()
        b.tvVersionInfo.text = if (boardChecked) {
            val fw = boardInfo?.let { i ->
                if (!i.mbos.isNullOrBlank()) {
                    buildString {
                        append("MB-OS v${i.mbos}")
                        if (!i.micropython.isNullOrBlank()) append("（MicroPython ${i.micropython}）")
                    }
                } else {
                    i.micropython?.takeIf { it.isNotBlank() }?.let { "未安装 MB-OS（MicroPython $it）" }
                        ?: "未安装 MB-OS 固件"
                }
            } ?: "读取中…"
            getString(R.string.guide_version_with_board, fw, appVer)
        } else {
            getString(R.string.guide_version_app_only, appVer)
        }
    }

    /** 前往更新：优先打开下载页，其次直接下载 apk。 */
    private fun openUpdate() {
        val cache = Prefs.getCachedUpdate(this)
        val target = (cache?.page?.takeIf { it.isNotBlank() }
            ?: cache?.url?.takeIf { it.isNotBlank() }
            ?: UpdateChecker.PAGE_URL)
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(target)))
        }
    }

    private fun updateHint() {
        b.tvConnHint.text = if (selectedType() == "usb") getString(R.string.conn_usb_desc) else getString(R.string.conn_ble_desc)
    }

    private fun selectedType(): String = if (b.chipGroup.checkedChipId == R.id.chipUsb) "usb" else "ble"

    private fun showBondedPicker() {
        val devs = BleConnection.bondedDevices(this)
        if (devs.isEmpty()) {
            Snackbar.make(b.root, "系统蓝牙里没有已配对设备，请先用系统蓝牙配对 micro:bit", Snackbar.LENGTH_LONG).show()
            return
        }
        val labels = devs.map { it.name ?: it.address }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.guide_pick_bonded)
            .setItems(labels) { _, which ->
                val addr = devs[which].address
                b.btnConnect.isEnabled = false
                b.tvStatus.text = getString(R.string.guide_connecting)
                b.tvStatus.setTextColor(themeColor(MR.attr.colorOnSurfaceVariant))
                b.blockLoading.visibility = View.VISIBLE
                ConnectionHub.connectByAddress(addr, this, listener)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun doConnect() {
        b.btnConnect.isEnabled = false
        b.tvStatus.text = getString(R.string.guide_connecting)
        b.tvStatus.setTextColor(themeColor(MR.attr.colorOnSurfaceVariant))
        b.blockLoading.visibility = View.VISIBLE
        ConnectionHub.connect(selectedType(), this, listener)
    }

    // ===== 刷机（免电脑） =====

    private fun onFlashClicked() {
        if (!isMicrobitUsbConnected()) {
            Snackbar.make(b.root, R.string.guide_flash_no_otg, Snackbar.LENGTH_LONG).show()
            return
        }
        setFlashStatus(getString(R.string.guide_flash_pick))
        treeLauncher.launch(null)
    }

    /** 检测是否已用 OTG 数据线连接 micro:bit（VID 0x0D28） */
    private fun isMicrobitUsbConnected(): Boolean {
        return runCatching {
            val m = getSystemService(UsbManager::class.java) ?: return@runCatching false
            m.deviceList.values.any { it.vendorId == 0x0D28 }
        }.getOrDefault(false)
    }

    private fun writeFirmwareToTree(treeUri: Uri) {
        setFlashStatus(getString(R.string.guide_flash_writing))
        Thread {
            try {
                val tree = DocumentFile.fromTreeUri(this, treeUri)
                    ?: throw Exception("无法访问所选目录")
                // 若已存在同名文件则删除，避免写入失败
                tree.listFiles().firstOrNull { it.name == FW_NAME }?.delete()
                val target = tree.createFile("application/octet-stream", FW_NAME)
                    ?: throw Exception("该位置不可写入（部分机型需改用「导出到本机」再用文件管理器拷贝）")
                contentResolver.openOutputStream(target.uri)?.use { os ->
                    assets.open(FW_ASSET).use { `is`: InputStream ->
                        val buf = ByteArray(8192)
                        var n: Int
                        while (`is`.read(buf).also { n = it } > 0) os.write(buf, 0, n)
                    }
                } ?: throw Exception("无法打开输出流")
                runOnUiThread {
                    setFlashStatus(getString(R.string.guide_flash_done))
                    // 记录「板载已刷入当前内置固件版本」，供后续新固件发布时提示
                    Prefs.setKnownFirmwareVersion(this, FirmwareScript.VERSION)
                    applyCards()
                    Snackbar.make(b.root, R.string.guide_flash_done, Snackbar.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    val msg = getString(R.string.guide_flash_fail, e.message ?: "未知错误")
                    setFlashStatus(msg)
                    Snackbar.make(b.root, msg, Snackbar.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    /** 兜底：导出固件到本机「下载」，用户用文件管理器拷进 MICROBIT 盘 */
    private fun exportFirmwareToDownloads() {
        Thread {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val cv = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, FW_NAME)
                        put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val coll = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    val item = contentResolver.insert(coll, cv)
                        ?: throw Exception("无法写入下载目录")
                    contentResolver.openOutputStream(item)?.use { os ->
                        assets.open(FW_ASSET).use { `is` -> `is`.copyTo(os) }
                    } ?: throw Exception("无法打开输出流")
                    cv.clear()
                    cv.put(MediaStore.Downloads.IS_PENDING, 0)
                    contentResolver.update(item, cv, null, null)
                } else {
                    throw Exception("当前系统版本较低，请改用「一键刷」或电脑刷写")
                }
                runOnUiThread {
                    Snackbar.make(b.root, R.string.guide_flash_export_done, Snackbar.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Snackbar.make(b.root, getString(R.string.guide_flash_fail, e.message ?: "未知错误"), Snackbar.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun setFlashStatus(msg: String) {
        b.tvFlashStatus.text = msg
    }

    override fun onDestroy() {
        super.onDestroy()
        selfCheckPending?.let { b.root.removeCallbacks(it) } // 销毁后不再弹自检对话框（v4.2）
        selfCheckPending = null
        ConnectionHub.removeListener(listener)
    }
}
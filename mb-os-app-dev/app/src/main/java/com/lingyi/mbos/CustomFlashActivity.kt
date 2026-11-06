/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos

import android.hardware.usb.UsbManager
import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lingyi.mbos.databinding.ActivityCustomFlashBinding
import com.lingyi.mbos.ui.MicrobitGames
import com.lingyi.mbos.ui.M3Kit
import com.lingyi.mbos.ui.StabilityFeatures
import com.lingyi.mbos.util.Prefs
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * 自定义刷入页：把代码 / 固件一键刷进 micro:bit。三种模式：
 *
 *  1. 预设小游戏模式：点游戏卡片后一键刷入，直接写入「已把游戏脚本嵌入好的完整固件 HEX」。
 *     这是 micro:bit 官方推荐的刷写方式（等同拖拽 .hex），烧录完成后开发板会自动复位并立即运行游戏，
 *     不依赖「写 main.py 后是否自动重启」，因此最可靠。
 *  2. 自定义 Python 模式：先刷入「纯 MicroPython 运行环境」base hex，再写入用户编辑的 main.py。
 *  3. HEX 模式：直接写入用户导入的 .hex（MakeCode / 任意工具生成），整包刷入。
 */
class CustomFlashActivity : BaseActivity() {

    private lateinit var b: ActivityCustomFlashBinding

    private var hexUri: Uri? = null
    private var hexName: String = ""

    /** 当前选中的预设小游戏 id（非空时一键刷入走「嵌入固件」路径）。 */
    private var gameId: String? = null
    private var gameName: String = ""

    /** 程序性改写编辑框时置位，避免误判为用户手动编辑。 */
    private var suppressEdit = false

    // 刷写步骤状态机
    private var pendingStep = STEP_IDLE

    /** 选择 MICROBIT U 盘（SAF） */
    private val treeLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) {
            setStatus(getString(R.string.custom_status_idle))
            return@registerForActivityResult
        }
        when (pendingStep) {
            STEP_BASE_THEN_MAIN -> writeBaseThenMain(uri)
            STEP_MAIN_ONLY -> writeMainPy(uri)
            STEP_HEX -> writeHex(uri)
            STEP_GAME -> writeGameHex(uri)
            else -> {}
        }
    }

    /** 导入 .py / .hex 文件 */
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val name = queryName(uri)
        when {
            name.endsWith(".hex", ignoreCase = true) -> {
                hexUri = uri
                hexName = name
                setModeHex(name)
            }
            name.endsWith(".py", ignoreCase = true) -> loadPy(uri)
            else -> Toast.makeText(this, "请选择 .py 或 .hex 文件", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityCustomFlashBinding.inflate(layoutInflater)
        setContentView(b.root)

        // M3 Expressive：整页可点击元素统一按压反馈（4.1.1-preview）
        M3Kit.applyPressFeedback(b.root)

        b.etCode.setText(TEMPLATE)
        // 用户手动改动代码后，退出「预设小游戏」模式，改走自定义 Python 模式
        b.etCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!suppressEdit && gameId != null) {
                    gameId = null
                    updateModeUi()
                }
            }
        })

        b.btnImportPy.setOnClickListener { importLauncher.launch(arrayOf("*/*")) }
        b.btnImportHex.setOnClickListener { importLauncher.launch(arrayOf("*/*")) }
        b.btnFlash.setOnClickListener { onFlashClicked() }
        b.gamePlane.setOnClickListener { loadGame("plane") }
        b.gameSnake.setOnClickListener { loadGame("snake") }
        b.gameBricks.setOnClickListener { loadGame("bricks") }
        updateModeUi()

        // ===== 稳定性工具（4.1.0-preview）：环境自检 / 文件校验 / 失败兜底 =====
        b.toolbar.setNavigationOnClickListener { finish() }
        b.btnFlashSelfCheck.setOnClickListener { refreshFlashCheck(verbose = true) }
        b.btnVerifyHex.setOnClickListener { verifyPendingContent() }
        b.btnFlashFailHelp.setOnClickListener { showFlashFailHelp() }
        refreshFlashCheck(verbose = false)

        // 从「编程模块」跳转进来：直接弹出文件选择器选 .hex（MakeCode 下载的固件）
        if (intent.getBooleanExtra(MakeCodeActivity.EXTRA_AUTO_PICK_HEX, false)) {
            setStatus(getString(R.string.custom_pick_hex_from_makecode))
            importLauncher.launch(arrayOf("*/*"))
        }
        // 从「代码库」跳转进来：预填示例代码（自定义 Python 模式）
        val presetCode = intent.getStringExtra(EXTRA_PRESET_CODE)
        if (!presetCode.isNullOrBlank()) {
            setEditorText(presetCode)
            updateModeUi()
            setStatus("已从代码库载入示例，可直接刷入或继续编辑")
        }
    }

    override fun onResume() {
        super.onResume()
        // 回到本页（如从权限设置返回）时重跑环境自检，及时反映 OTG / 存储状态
        refreshFlashCheck(verbose = false)
    }

    private fun onFlashClicked() {
        if (!isMicrobitUsbConnected()) {
            Toast.makeText(this, getString(R.string.custom_no_otg), Toast.LENGTH_LONG).show()
            return
        }
        if (hexUri != null) {
            pendingStep = STEP_HEX
            setStatus(getString(R.string.custom_pick))
            treeLauncher.launch(null)
            return
        }
        if (gameId != null) {
            pendingStep = STEP_GAME
            setStatus(getString(R.string.custom_pick))
            treeLauncher.launch(null)
            return
        }
        val code = b.etCode.text.toString()
        if (code.isBlank()) {
            Toast.makeText(this, getString(R.string.custom_no_code), Toast.LENGTH_SHORT).show()
            return
        }
        pendingStep = STEP_BASE_THEN_MAIN
        setStatus(getString(R.string.custom_pick))
        treeLauncher.launch(null)
    }

    // ===== 稳定性工具：环境自检 / 文件校验 / 失败兜底（4.1.0-preview） =====

    /**
     * 刷入前环境自检：OTG 连接 / 应用存储可写 / 有待刷入内容。
     * 不过项给出可操作提示，把「刷不进去」消灭在点按钮之前。
     */
    private fun refreshFlashCheck(verbose: Boolean) {
        val otg = isMicrobitUsbConnected()
        val storageOk = runCatching {
            val dir = File(getExternalFilesDir(null), "exports").apply { mkdirs() }
            File(dir, ".probe").let { it.createNewFile() || it.exists() }
        }.getOrDefault(false)
        val content = when {
            hexUri != null -> "已导入 HEX：$hexName"
            gameId != null -> "预设游戏：$gameName"
            b.etCode.text.toString().isNotBlank() -> "自定义 Python 代码"
            else -> null
        }
        val bad = mutableListOf<String>()
        if (!otg) bad.add("未检测到 OTG 连接的 micro:bit（请换数据线，非充电线）")
        if (!storageOk) bad.add("应用存储不可写（可能影响导出兜底）")
        if (content == null) bad.add("还没有待刷入内容（选游戏 / 导入文件 / 写代码）")
        b.tvFlashCheck.text = buildString {
            append(if (otg) "✓ OTG 已连接 micro:bit" else "✗ 未检测到 OTG 连接的 micro:bit")
            append('\n')
            append(if (storageOk) "✓ 应用存储可写" else "✗ 应用存储不可写")
            append('\n')
            append(if (content != null) "✓ 待刷入：$content" else "✗ 暂无待刷入内容")
            if (bad.isNotEmpty()) {
                append('\n')
                append("待处理：${bad.joinToString("；")}")
            }
        }
        if (verbose) {
            val msg = if (bad.isEmpty()) "环境就绪，可以刷入" else "有 ${bad.size} 项待处理，详见上方"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    /** Intel HEX 基本校验（纯函数，便于单测）：行格式 / 长度 / EOF 记录。 */
    internal object HexCheck {
        data class Result(val ok: Boolean, val records: Int, val problems: List<String>)

        fun check(text: String): Result {
            val problems = mutableListOf<String>()
            val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            if (lines.isEmpty()) return Result(false, 0, listOf("文件为空"))
            if (!lines.first().startsWith(":")) problems.add("首行不是 Intel HEX 记录（应以 : 开头）")
            var records = 0
            lines.forEachIndexed { i, line ->
                if (!line.startsWith(":")) {
                    problems.add("第 ${i + 1} 行不是合法记录")
                    return@forEachIndexed
                }
                val body = line.substring(1)
                if (body.length < 10 || body.length % 2 != 0) {
                    problems.add("第 ${i + 1} 行长度异常")
                    return@forEachIndexed
                }
                if (!body.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                    problems.add("第 ${i + 1} 行含非十六进制字符")
                    return@forEachIndexed
                }
                if (body.startsWith("00")) records++
            }
            val eof = lines.any { it.equals(":00000001FF", ignoreCase = true) }
            if (!eof) problems.add("缺少 EOF 结束记录（:00000001FF），文件可能不完整")
            return Result(problems.isEmpty(), records, problems.take(8))
        }
    }

    /** 校验当前待刷入内容（HEX 格式 / 内置资产 / Python 代码）。 */
    private fun verifyPendingContent() {
        val uri = hexUri
        if (uri != null) {
            Thread {
                val bytes = readUriBytes(uri)
                val text = bytes?.toString(Charsets.US_ASCII)
                if (text.isNullOrBlank()) {
                    runOnUiThread { dialog("HEX 校验", "无法读取所选 .hex 文件") }
                    return@Thread
                }
                showHexCheckResult(HexCheck.check(text), hexName)
            }.start()
            return
        }
        if (gameId != null) {
            Thread {
                val bytes = readAssetGz("firmware/games/$gameId.hex.gz")
                val text = bytes?.toString(Charsets.US_ASCII)
                if (text.isNullOrBlank()) {
                    runOnUiThread { dialog("HEX 校验", "无法读取内置游戏固件（$gameName）") }
                    return@Thread
                }
                showHexCheckResult(HexCheck.check(text), "内置游戏固件（$gameName）")
            }.start()
            return
        }
        val code = b.etCode.text.toString()
        if (code.isBlank()) {
            Toast.makeText(this, "请先选游戏、导入文件或写点代码", Toast.LENGTH_SHORT).show()
            return
        }
        val hasUserMain = code.contains("def user_main")
        val hasWhileTrue = code.contains("while True")
        val msg = buildString {
            append("自定义 Python 模式（无需 HEX 校验）\n")
            append(if (hasUserMain) "✓ 包含 user_main() 入口" else "✗ 未找到 user_main()，刷入后不会执行你的逻辑")
            append('\n')
            append(if (hasWhileTrue) "✗ 检测到 while True，会阻塞 App 读数（框架已含循环）" else "✓ 未发现阻塞式 while True")
        }
        dialog("内容检查", msg)
    }

    private fun showHexCheckResult(r: HexCheck.Result, name: String) {
        runOnUiThread {
            val msg = buildString {
                append("文件：$name\n")
                append("数据记录：${r.records} 条\n")
                if (r.ok) {
                    append("结论：✓ HEX 格式完整，可刷入")
                } else {
                    append("结论：✗ 存在问题，可能刷入失败：\n")
                    r.problems.forEach { append("· $it\n") }
                }
            }
            dialog("HEX 校验", msg)
        }
    }

    /** 刷入失败兜底：排查步骤 + 导出到电脑 + 分享日志。 */
    private fun showFlashFailHelp() {
        MaterialAlertDialogBuilder(this)
            .setTitle("刷入失败？按顺序排查")
            .setMessage(
                "1. 换一根数据线（很多线仅能充电）并换 USB 口；\n" +
                    "2. 刷入前确认选中了 MICROBIT 盘（刷 hex 后盘会重挂载，属正常现象）；\n" +
                    "3. 部分机型对 SAF 写入限制严格，可用下方「导出到本机」再用电脑拖拽；\n" +
                    "4. 仍失败请分享日志包给开发者定位。"
            )
            .setNegativeButton("关闭", null)
            .setNeutralButton("分享日志") { _, _ -> StabilityFeatures.shareLogPackage(this) }
            .setPositiveButton("导出到本机") { _, _ -> exportBaseHexToDownloads() }
            .show()
    }

    /** 兜底：把 base hex 导出到本机「下载」目录，用户用电脑拖进 MICROBIT 盘。 */
    private fun exportBaseHexToDownloads() {
        Thread {
            try {
                val bytes = assets.open("firmware/micropython-microbit-v2-base.hex").use { it.readBytes() }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val name = "micropython-microbit-v2-base.hex"
                    val cv = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name)
                        put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val coll = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    val item = contentResolver.insert(coll, cv)
                        ?: throw Exception("无法写入下载目录")
                    contentResolver.openOutputStream(item)?.use { it.write(bytes) }
                        ?: throw Exception("无法打开输出流")
                    cv.clear()
                    cv.put(MediaStore.Downloads.IS_PENDING, 0)
                    contentResolver.update(item, cv, null, null)
                    runOnUiThread {
                        Toast.makeText(this, "已导出到「下载」目录：$name", Toast.LENGTH_LONG).show()
                    }
                } else {
                    throw Exception("当前系统版本较低，请改用电脑刷写")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "导出失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun dialog(title: String, msg: String) {
        val tv = android.widget.TextView(this).apply {
            text = msg
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(24, 16, 24, 16)
        }
        val sv = android.widget.ScrollView(this).apply { addView(tv) }
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(sv)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun isMicrobitUsbConnected(): Boolean {
        return runCatching {
            val m = getSystemService(UsbManager::class.java) ?: return@runCatching false
            m.deviceList.values.any { it.vendorId == 0x0D28 }
        }.getOrDefault(false)
    }

    // ===== 预设小游戏：直接刷入「嵌入脚本的完整固件」=====

    private fun writeGameHex(treeUri: Uri) {
        val id = gameId
        if (id == null) {
            fail("未选择游戏")
            return
        }
        showProgress()
        setStatus(getString(R.string.custom_writing_game, gameName))
        Thread {
            val bytes = readAssetGz("firmware/games/$id.hex.gz")
            if (bytes == null) {
                fail("无法读取内置游戏固件")
                return@Thread
            }
            if (writeBytesToTree(treeUri, "microbit.hex", bytes)) {
                done(getString(R.string.custom_game_done, gameName))
            } else {
                fail("无法写入游戏固件，请确认选中的是 MICROBIT 盘")
            }
        }.start()
    }

    // ===== Python 模式：base hex -> main.py =====

    private fun writeBaseThenMain(treeUri: Uri) {
        showProgress()
        setStatus(getString(R.string.custom_writing_base))
        Thread {
            val baseOk = writeAssetToTree(treeUri, "microbit.hex", "firmware/micropython-microbit-v2-base.hex")
            if (!baseOk) {
                fail("无法写入运行环境")
                return@Thread
            }
            // 同一棵树尝试写 main.py；刷 hex 后盘会重挂载，可能失败
            val code = b.etCode.text.toString().toByteArray(Charsets.UTF_8)
            val mainOk = writeBytesToTree(treeUri, "main.py", code)
            if (mainOk) {
                done(getString(R.string.custom_done))
            } else {
                // 重挂载：请用户重新选择 MICROBIT 盘再写 main.py
                runOnUiThread {
                    hideProgress()
                    pendingStep = STEP_MAIN_ONLY
                    setStatus(getString(R.string.custom_done_main))
                    Toast.makeText(this, getString(R.string.custom_done_main), Toast.LENGTH_LONG).show()
                    treeLauncher.launch(null)
                }
            }
        }.start()
    }

    private fun writeMainPy(treeUri: Uri) {
        showProgress()
        setStatus(getString(R.string.custom_writing_main))
        Thread {
            val code = b.etCode.text.toString().toByteArray(Charsets.UTF_8)
            if (writeBytesToTree(treeUri, "main.py", code)) {
                done(getString(R.string.custom_done))
            } else {
                fail("无法写入 main.py")
            }
        }.start()
    }

    // ===== HEX 模式 =====

    private fun writeHex(treeUri: Uri) {
        showProgress()
        setStatus(getString(R.string.custom_writing_hex))
        Thread {
            // hexUri 可能在 loadPy()/loadGame() 切标签时被置 null（v4.2 修复跨异步空解引用）
            val uri = hexUri ?: run {
                fail("所选文件已失效，请重新选择")
                return@Thread
            }
            val bytes = readUriBytes(uri)
            if (bytes == null) {
                fail("无法读取所选 .hex")
                return@Thread
            }
            if (writeBytesToTree(treeUri, "microbit.hex", bytes)) {
                done(getString(R.string.custom_done))
            } else {
                fail("无法写入 .hex")
            }
        }.start()
    }

    // ===== SAF 写入工具 =====

    private fun readAssetGz(asset: String): ByteArray? = runCatching {
        GZIPInputStream(assets.open(asset)).use { it.readBytes() }
    }.getOrNull()

    private fun writeAssetToTree(treeUri: Uri, name: String, asset: String): Boolean {
        val bytes = runCatching { assets.open(asset).use { it.readBytes() } }.getOrNull() ?: return false
        return writeBytesToTree(treeUri, name, bytes)
    }

    /**
     * 写文件到 MICROBIT 盘，带重试与文件名校验。
     * micro:bit 只识别盘根目录下名为 main.py / *.hex 的文件；
     * SAF 的 createFile 在同名文件残留时会生成「main (1).py」，导致开发板不执行，故此处强校验。
     */
    private fun writeBytesToTree(treeUri: Uri, name: String, bytes: ByteArray): Boolean {
        val stem = name.substringBeforeLast('.')
        repeat(3) { attempt ->
            val ok = runCatching {
                val tree = DocumentFile.fromTreeUri(this, treeUri) ?: return@runCatching false
                // 清掉同名及被系统改名的副本（main.py / main (1).py …）
                tree.listFiles()
                    .filter { it.name == name || it.name?.substringBeforeLast('.') == stem }
                    .forEach { runCatching { it.delete() } }
                val target = tree.createFile("application/octet-stream", name) ?: return@runCatching false
                if (target.name != name) {
                    runCatching { target.delete() }
                    return@runCatching false
                }
                contentResolver.openOutputStream(target.uri)?.use { os ->
                    os.write(bytes)
                    os.flush()
                } != null
            }.getOrDefault(false)
            if (ok) return true
            if (attempt < 2) runCatching { Thread.sleep(1500) }
        }
        return false
    }

    private fun readUriBytes(uri: Uri): ByteArray? {
        return runCatching {
            contentResolver.openInputStream(uri)?.use { `is` ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var n: Int
                while (`is`.read(buf).also { n = it } > 0) out.write(buf, 0, n)
                out.toByteArray()
            }
        }.getOrNull()
    }

    private fun queryName(uri: Uri): String {
        var n = ""
        runCatching {
            contentResolver.query(
                uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null
            )?.use { c ->
                if (c.moveToFirst()) n = c.getString(0) ?: ""
            }
        }
        if (n.isEmpty()) n = uri.lastPathSegment ?: ""
        return n
    }

    private fun loadPy(uri: Uri) {
        val bytes = readUriBytes(uri)
        if (bytes == null) {
            Toast.makeText(this, "无法读取 .py 文件", Toast.LENGTH_SHORT).show()
            return
        }
        setEditorText(String(bytes, Charsets.UTF_8))
        hexUri = null
        gameId = null
        updateModeUi()
        Toast.makeText(this, "已导入 .py，可直接刷入或继续编辑", Toast.LENGTH_SHORT).show()
    }

    private fun setModeHex(name: String) {
        gameId = null
        updateModeUi()
        setStatus(getString(R.string.custom_hex_ready, name))
    }

    private fun setEditorText(text: String) {
        suppressEdit = true
        b.etCode.setText(text)
        suppressEdit = false
    }

    private fun updateModeUi() {
        b.tvMode.text = when {
            hexUri != null -> getString(R.string.custom_mode_hex)
            gameId != null -> getString(R.string.custom_mode_game, gameName)
            else -> getString(R.string.custom_mode_py)
        }
    }

    /** 载入一个预设小游戏：代码进编辑框供查看，一键刷入则烧录「嵌入该游戏的固件」。 */
    private fun loadGame(id: String) {
        val g = MicrobitGames.presets.firstOrNull { it.id == id } ?: return
        gameId = g.id
        gameName = g.name
        hexUri = null
        setEditorText(g.code)
        updateModeUi()
        val msg = getString(R.string.custom_game_loaded, g.name)
        setStatus(msg)
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun setStatus(msg: String) {
        b.tvStatus.text = msg
    }

    private fun showProgress() {
        com.lingyi.mbos.ui.Transitions.crossFade(b.progressFlash, true)
    }

    private fun hideProgress() {
        com.lingyi.mbos.ui.Transitions.crossFade(b.progressFlash, false)
    }

    private fun done(msg: String) {
        // 板载已被自定义内容（游戏 / 自有 Python / 自有 HEX）占据，不再运行 MB-OS 助手：
        // 清空「已知固件版本」，避免引导页误提示重新刷入。
        Prefs.setKnownFirmwareVersion(this, null)
        com.lingyi.mbos.ui.FlashLog.append(this, currentTarget(), "成功")
        runOnUiThread {
            hideProgress()
            setStatus(msg)
            refreshFlashCheck(verbose = false)
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun fail(msg: String) {
        com.lingyi.mbos.ui.FlashLog.append(this, currentTarget(), "失败：$msg")
        runOnUiThread {
            hideProgress()
            val m = getString(R.string.custom_fail, msg)
            setStatus(m)
            refreshFlashCheck(verbose = false)
            Toast.makeText(this, m, Toast.LENGTH_LONG).show()
        }
    }

    /** 当前刷写目标的展示名（刷机日志用）。 */
    private fun currentTarget(): String = when {
        gameId != null -> "预设游戏：$gameName"
        hexUri != null -> "HEX：$hexName"
        else -> "自定义 Python"
    }

    companion object {
        private const val STEP_IDLE = 0
        private const val STEP_BASE_THEN_MAIN = 1
        private const val STEP_MAIN_ONLY = 2
        private const val STEP_HEX = 3
        private const val STEP_GAME = 4

        /** 从代码库携带一段 MicroPython 示例代码进入（预填编辑器）。 */
        const val EXTRA_PRESET_CODE = "com.lingyi.mbos.PRESET_CODE"

        /** 默认模板：用户写 user_main()，框架循环并兼容 App 读数。 */
        private val TEMPLATE = """
# 你的 micro:bit 代码 —— 由 灵逸 MB 一键刷入
# 在 user_main() 里写你自己的逻辑；框架负责循环并同时响应 App 读数。
# 注意：不要写 while True，否则会阻塞 App 读数。
from microbit import *
import music, os

def user_main():
    # >>> 在这里写你的代码（示例：滚动文字 + 显示爱心） <<<
    display.scroll("Hi 灵逸 MB")
    display.show(Image.HEART)
    sleep(800)
    # >>> 结束 <<<

# ---- 以下为 灵逸 MB 兼容读取，一般无需修改 ----
def parse(line):
    line = line.strip()
    if line == "READ":
        try:
            h = compass.heading()
        except Exception:
            h = -1
        uart.write("A:{}|B:{}|T:{}|L:{}|X:{}|Y:{}|Z:{}|C:{}|G:{}\n".format(
            int(button_a.is_pressed()), int(button_b.is_pressed()),
            temperature(), display.read_light_level(),
            accelerometer.get_x(), accelerometer.get_y(), accelerometer.get_z(), h, "none"))
    elif line == "INFO":
        uart.write("INFO:灵逸 MB|micro:bit V2\n")

recv = b""
while True:
    try:
        if uart.any():
            chunk = uart.read()
            if chunk:
                recv += chunk
                while b"\n" in recv:
                    raw, recv = recv.split(b"\n", 1)
                    parse(raw.decode("utf-8", "ignore").strip())
        else:
            user_main()
    except Exception:
        recv = b""
        sleep(50)
""".trimIndent()
    }
}
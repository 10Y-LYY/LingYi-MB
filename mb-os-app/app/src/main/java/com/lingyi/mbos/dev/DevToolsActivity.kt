/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.dev

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.NestedScrollView
import com.google.android.material.R as MR
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.appbar.MaterialToolbar
import com.lingyi.mbos.BaseActivity
import com.lingyi.mbos.R
import com.lingyi.mbos.ble.ConnectionHub
import com.lingyi.mbos.ble.ExternalApi
import com.lingyi.mbos.ui.FeatureCatalog
import com.lingyi.mbos.ui.M3Kit
import com.lingyi.mbos.ui.Protocol
import com.lingyi.mbos.util.AppLog
import com.lingyi.mbos.util.Prefs
import com.lingyi.mbos.util.UpdateChecker
import com.lingyi.mbos.util.themeColor
import kotlin.random.Random
import android.content.res.ColorStateList
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 开发者测试版 / 预览版专属：开发者工具箱。
 *
 * 仅 devtest / preview flavor 注册（manifest android:enabled=@bool/devtest_only），且
 * 由 DeveloperActivity 的「开发者工具箱」按钮在 IS_DEVTEST 时跳转进来。
 *
 * 界面严格走全 App 唯一的 [M3Kit] 设计系统（M3 Expressive）：
 *  - Hero 主卡（accentCard）：标题 + 状态胶囊（连接 / 通道 / 外部接口）
 *  - 分组标题（sectionHeader）+ 行式图标条目（toolRow）+ 状态开关卡
 *  - 终端风日志块（monoBlock）+ 对话框输入（FeatureDialogBuilder）
 *
 * 内含 6 个开发者工具 + 1 个 HTTP 接口开关：
 *  1) 组件检视器：悬浮气泡抓取任意组件，显示类名/资源 ID/专属编号（见 ComponentInspectorService）。
 *  2) 原始指令台：发送任意串口指令并查看收发回显。
 *  3) 协议模糊测试：批量生成/发送随机协议指令，压测固件。
 *  4) 连接日志查看器：查看 AppLog 近期日志。
 *  5) 对外接口自检：本进程直接调用 ExternalApi，验证「允许外部应用控制开发板」链路。
 *  6) 导出功能目录：把 FeatureCatalog 导出为 JSON，便于文档生成。
 *  + 本地 HTTP 接口开关（DevHttpServer）：127.0.0.1 起 HTTP 服务，curl/任意语言可调用。
 */
class DevToolsActivity : BaseActivity() {

    private lateinit var root: LinearLayout
    private lateinit var logBlock: TextView

    private lateinit var heroConn: TextView
    private lateinit var heroChannel: TextView
    private lateinit var heroApi: TextView

    private lateinit var btnInspector: MaterialButton
    private lateinit var pillInspector: TextView
    private lateinit var btnHttp: MaterialButton
    private lateinit var pillHttp: TextView

    private val logLines = ArrayDeque<String>()

    /** 悬浮窗授权后要执行的动作（组件检视器）。 */
    private var pendingOverlay: (() -> Unit)? = null

    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            pendingOverlay?.invoke()
        } else {
            Snackbar.make(root, R.string.inspector_permission, Snackbar.LENGTH_LONG).show()
        }
        pendingOverlay = null
        renderInspector()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val toolbar = MaterialToolbar(this).apply {
            title = getString(R.string.dev_tools_title)
        }
        root.addView(toolbar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(M3Kit.dp(this@DevToolsActivity, 16), M3Kit.dp(this@DevToolsActivity, 8),
                M3Kit.dp(this@DevToolsActivity, 16), M3Kit.dp(this@DevToolsActivity, 16))
        }

        buildHero(content)

        content.addView(M3Kit.sectionHeader(this, "对外开发者接口"))
        buildApiOverview(content)
        buildHttp(content)

        content.addView(M3Kit.sectionHeader(this, "调试工具"))
        buildInspector(content)
        buildRawConsole(content)
        buildFuzzer(content)
        buildLogViewer(content)
        buildApiSelfCheck(content)
        buildExport(content)

        content.addView(M3Kit.sectionHeader(this, "操作日志"))
        buildLogPanel(content)

        val scroll = NestedScrollView(this).apply {
            addView(content)
        }
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        setContentView(root)
        M3Kit.applyPressFeedback(root)
    }

    override fun onResume() {
        super.onResume()
        renderHero()
        renderInspector()
        renderHttp()
    }

    // ---------------- Hero 主卡 ----------------

    private fun buildHero(parent: LinearLayout) {
        val hero = M3Kit.accentCard(this)
        val hb = M3Kit.cardBody(this)

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topRow.addView(M3Kit.iconCircle(this, R.drawable.ic_code,
            MR.attr.colorPrimary, MR.attr.colorOnPrimary, 56f))
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = M3Kit.dp(this@DevToolsActivity, 14)
            }
        }
        texts.addView(TextView(this).apply {
            text = getString(R.string.dev_tools_title)
            setTextAppearance(MR.attr.textAppearanceHeadlineSmall)
            setTextColor(themeColor(MR.attr.colorOnPrimary))
            setTypeface(null, Typeface.BOLD)
        })
        texts.addView(TextView(this).apply {
            text = getString(R.string.dev_tools_subtitle)
            setTextAppearance(MR.attr.textAppearanceBodySmall)
            setTextColor(themeColor(MR.attr.colorOnPrimary))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = M3Kit.dp(this@DevToolsActivity, 2) }
        })
        topRow.addView(texts)
        hb.addView(topRow)

        val pills = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = M3Kit.dp(this@DevToolsActivity, 12) }
        }
        heroConn = M3Kit.pill(this, "未连接")
        heroChannel = M3Kit.pill(this, "通道 · -")
        heroApi = M3Kit.pill(this, "外部接口 · 关")
        listOf(heroConn, heroChannel, heroApi).forEach {
            it.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = M3Kit.dp(this@DevToolsActivity, 8) }
            pills.addView(it)
        }
        hb.addView(pills)

        hero.addView(hb)
        parent.addView(hero)
    }

    private fun renderHero() {
        val conn = ConnectionHub.isConnected()
        heroConn.text = if (conn) "已连接" else "未连接"
        paintPill(heroConn, conn)
        heroChannel.text = "通道 · ${UpdateChecker.channelLabel(Prefs.getChannel(this))}"
        paintNeutral(heroChannel)
        val api = Prefs.isExtApiEnabled(this)
        heroApi.text = if (api) "外部接口 · 开" else "外部接口 · 关"
        paintPill(heroApi, api)
    }

    // ---------------- 1. 对外接口总览 ----------------

    private fun buildApiOverview(parent: LinearLayout) {
        val (card, body) = M3Kit.card(this)
        body.addView(M3Kit.cardTitle(this, "你的 App 如何控制用户的 micro:bit"))
        body.addView(M3Kit.cardDesc(this, getString(R.string.settings_ext_api_desc)))
        val authority = "$packageName.external"
        body.addView(M3Kit.infoRow(this, "ContentProvider authority", authority) {
            copyText("authority", authority)
        })
        val httpUrl = "http://127.0.0.1:${DevHttpServer.PORT}/send?cmd=SCROLL:hello"
        body.addView(M3Kit.infoRow(this, "本地 HTTP 示例", httpUrl) {
            copyText("HTTP 示例", httpUrl)
        })
        body.addView(M3Kit.cardDesc(this,
            "两种方式都受「设置 → 允许外部应用控制开发板」开关保护；预览版 / 开发版默认开启。"))
        parent.addView(card)
    }

    // ---------------- 2 & 3. 状态开关卡（检视器 / HTTP） ----------------

    /** 通用「图标 + 标题 + 描述 + 状态胶囊 + 全宽按钮」开关卡。 */
    private fun addActionCard(
        parent: LinearLayout,
        icon: Int,
        title: String,
        desc: String,
        buttonText: String,
        onButton: () -> Unit
    ): Pair<TextView, MaterialButton> {
        val (card, body) = M3Kit.card(this, M3Kit.SP_8)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(M3Kit.iconCircle(this, icon))
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = M3Kit.dp(this@DevToolsActivity, 14)
            }
        }
        texts.addView(M3Kit.cardTitle(this, title))
        texts.addView(M3Kit.cardDesc(this, desc))
        row.addView(texts)
        val pill = M3Kit.pill(this, "已停止")
        pill.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = M3Kit.dp(this@DevToolsActivity, 12) }
        row.addView(pill)
        body.addView(row)
        val btn = M3Kit.button(this, buttonText, M3Kit.ButtonStyle.TONAL, onButton)
        body.addView(btn)
        parent.addView(card)
        return pill to btn
    }

    private fun buildInspector(parent: LinearLayout) {
        val (pill, btn) = addActionCard(
            parent, R.drawable.ic_pointer,
            getString(R.string.dev_tool_inspector),
            getString(R.string.dev_tool_inspector_desc),
            "启动调试气泡"
        ) { toggleInspector() }
        pillInspector = pill
        btnInspector = btn
    }

    private fun toggleInspector() {
        if (ComponentInspectorService.running) {
            ComponentInspectorService.stop(this)
        } else {
            if (!Settings.canDrawOverlays(this)) {
                pendingOverlay = {
                    ComponentInspectorService.start(this)
                    renderInspector()
                }
                Snackbar.make(root, R.string.inspector_permission, Snackbar.LENGTH_LONG)
                    .setAction(R.string.dev_simulator_overlay_open) {
                        overlayLauncher.launch(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                        )
                    }.show()
                renderInspector()
                return
            }
            ComponentInspectorService.start(this)
        }
        renderInspector()
    }

    private fun renderInspector() {
        val running = ComponentInspectorService.running
        btnInspector.text = if (running) "停止调试气泡" else "启动调试气泡"
        pillInspector.text = if (running) "运行中" else "已停止"
        paintPill(pillInspector, running)
    }

    private fun buildHttp(parent: LinearLayout) {
        val (pill, btn) = addActionCard(
            parent, R.drawable.ic_linkmonitor,
            getString(R.string.dev_tool_http),
            getString(R.string.dev_tool_http_desc),
            "启动本地 HTTP 接口"
        ) { toggleHttp() }
        pillHttp = pill
        btnHttp = btn
    }

    private fun toggleHttp() {
        if (DevHttpServer.running) DevHttpServer.stop(this) else DevHttpServer.start(this)
        renderHttp()
    }

    private fun renderHttp() {
        val running = DevHttpServer.running
        btnHttp.text = if (running) "停止本地 HTTP 接口" else "启动本地 HTTP 接口"
        pillHttp.text = if (running) "运行中" else "已停止"
        paintPill(pillHttp, running)
    }

    // ---------------- 4. 原始指令台 ----------------

    private fun buildRawConsole(parent: LinearLayout) {
        parent.addView(M3Kit.toolRow(
            this, R.drawable.ic_terminal,
            getString(R.string.dev_tool_raw_console),
            getString(R.string.dev_tool_raw_console_desc)
        ) { openRawConsole() })
    }

    private fun openRawConsole() {
        M3Kit.FeatureDialogBuilder(this)
            .title(getString(R.string.dev_tool_raw_console))
            .hint("向开发板发送一行指令（无需手动换行，系统会自动补 \\n）")
            .input("例如 SCROLL:hello", "")
            .confirmText("发送")
            .onConfirm { raw ->
                if (raw.isNotBlank()) dispatch(Protocol.custom(raw))
            }
            .show()
    }

    // ---------------- 5. 协议模糊测试 ----------------

    private fun buildFuzzer(parent: LinearLayout) {
        parent.addView(M3Kit.toolRow(
            this, R.drawable.ic_randomgen,
            getString(R.string.dev_tool_fuzzer),
            getString(R.string.dev_tool_fuzzer_desc)
        ) { openFuzzer() })
    }

    private fun openFuzzer() {
        M3Kit.FeatureDialogBuilder(this)
            .title(getString(R.string.dev_tool_fuzzer))
            .hint("随机生成并发送指定数量的协议指令，用于压测固件稳定性")
            .input("发送条数", "20", numeric = true)
            .confirmText("开始")
            .onConfirm { s ->
                val n = s.toIntOrNull()?.coerceIn(1, 200) ?: 20
                runFuzz(n)
            }
            .show()
    }

    private val words = listOf("hello", "microbit", "测试", "123", "LED", "ok", "ping", "buzz", "go", "x", "课", "灯")

    private fun randomPattern(): Array<BooleanArray> =
        Array(5) { BooleanArray(5) { Random.nextBoolean() } }

    private val fuzzers = listOf<() -> String>(
        { Protocol.scroll(words.random()) },
        { Protocol.show(randomPattern()) },
        { Protocol.number(Random.nextInt(0, 100000)) },
        { Protocol.tone(Random.nextInt(100, 2000), Random.nextInt(50, 1000)) },
        { Protocol.plot(Random.nextInt(0, 5), Random.nextInt(0, 5), Random.nextBoolean()) },
        { Protocol.brightness(Random.nextInt(0, 10)) },
        { Protocol.custom("SCROLL:${words.random()}") },
        { Protocol.read() },
        { Protocol.clear() }
    )

    private fun runFuzz(n: Int) {
        repeat(n) { dispatch(fuzzers.random().invoke()) }
        log("模糊测试：已发出 $n 条随机指令")
    }

    // ---------------- 6. 连接日志查看器 ----------------

    private fun buildLogViewer(parent: LinearLayout) {
        parent.addView(M3Kit.toolRow(
            this, R.drawable.ic_logstream,
            getString(R.string.dev_tool_log_viewer),
            getString(R.string.dev_tool_log_viewer_desc)
        ) {
            val text = AppLog.recentLines(200).joinToString("\n").ifBlank { "(暂无日志)" }
            showCode("AppLog 近期日志", text)
        })
    }

    // ---------------- 7. 对外接口自检 ----------------

    private fun buildApiSelfCheck(parent: LinearLayout) {
        parent.addView(M3Kit.toolRow(
            this, R.drawable.ic_codecheck,
            getString(R.string.dev_tool_api_selfcheck),
            getString(R.string.dev_tool_api_selfcheck_desc)
        ) { apiSelfCheck() })
    }

    private fun apiSelfCheck() {
        val sb = StringBuilder()
        val r1 = ExternalApi.dispatch(this, "status", null, { false }, { false })
        sb.appendLine("call(\"status\") → ok=${r1.ok}, reason=\"${r1.reason}\"")
        val r2 = ExternalApi.dispatch(this, "send", "SCROLL:hi", { true }, { true })
        sb.appendLine("call(\"send\",\"SCROLL:hi\") → ok=${r2.ok}, sent=\"${r2.sent}\", reason=\"${r2.reason}\"")
        val r3 = ExternalApi.dispatch(this, "unknownX", null, { false }, { false })
        sb.appendLine("call(\"unknownX\") → ok=${r3.ok}, reason=\"${r3.reason}\"")
        sb.appendLine("当前外部接口开关：${if (Prefs.isExtApiEnabled(this)) "已开启" else "已关闭（需在设置开启）"}")
        sb.appendLine("本机 authority：$packageName.external")
        log(sb.toString())
        showCode("对外接口自检", sb.toString())
    }

    // ---------------- 8. 导出功能目录 ----------------

    private fun buildExport(parent: LinearLayout) {
        parent.addView(M3Kit.toolRow(
            this, R.drawable.ic_scriptinfo,
            getString(R.string.dev_tool_export),
            getString(R.string.dev_tool_export_desc)
        ) { exportCatalog() })
    }

    private fun exportCatalog() {
        val json = buildString {
            appendLine("{")
            appendLine("  \"app\": \"灵逸 MB\",")
            appendLine("  \"features\": [")
            append(FeatureCatalog.FEATURES.mapIndexed { i, f ->
                val comma = if (i < FeatureCatalog.FEATURES.lastIndex) "," else ""
                "    {\"id\":\"${f.id}\",\"title\":\"${f.title}\",\"category\":\"${f.category.key}\"}$comma"
            }.joinToString("\n"))
            appendLine("\n  ]")
            appendLine("}")
        }
        showCode("FeatureCatalog.json", json)
    }

    // ---------------- 操作日志面板 ----------------

    private fun buildLogPanel(parent: LinearLayout) {
        val (card, body) = M3Kit.card(this)
        logBlock = M3Kit.monoBlock(this, 200f)
        logBlock.text = getString(R.string.dev_log_empty)
        body.addView(logBlock)
        val clear = M3Kit.button(this, "清空日志", M3Kit.ButtonStyle.TEXT) {
            logLines.clear(); logBlock.text = getString(R.string.dev_log_empty)
        }
        val copy = M3Kit.button(this, "复制日志", M3Kit.ButtonStyle.TEXT) {
            copyText("操作日志", logLines.joinToString("\n"))
        }
        body.addView(M3Kit.buttonRow(this, clear, copy))
        parent.addView(card)
    }

    // ---------------- 公共：发送 / 日志 / 弹窗 / 胶囊 ----------------

    /** 生成指令：已连接则真实下发，未连接则本地预览。 */
    private fun dispatch(line: String) {
        val pretty = line.trimEnd('\n')
        if (ConnectionHub.isConnected() && ConnectionHub.send(line)) {
            log("→ $pretty")
            Snackbar.make(root, "已发送：$pretty", Snackbar.LENGTH_SHORT).show()
        } else {
            log("→ (未发送) $pretty")
            Snackbar.make(root, "未连接，仅预览：$pretty", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun log(line: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        logLines.addLast("[$ts] $line")
        while (logLines.size > 200) logLines.removeFirst()
        logBlock.text = logLines.joinToString("\n")
    }

    private fun copyText(label: String, text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Snackbar.make(root, "已复制：$label", Snackbar.LENGTH_SHORT).show()
    }

    private fun showCode(title: String, code: String) {
        val tv = TextView(this).apply {
            text = code
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(24, 16, 24, 16)
        }
        val sv = ScrollView(this).apply { addView(tv) }
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(sv)
            .setNegativeButton(android.R.string.ok, null)
            .setPositiveButton(R.string.dev_copy_code) { _, _ -> copyText(title, code) }
            .show()
    }

    /** 状态胶囊着色：on=true 用主色容器，否则用中性容器。 */
    private fun paintPill(pill: TextView, on: Boolean) {
        val bgAttr = if (on) MR.attr.colorPrimaryContainer else MR.attr.colorSurfaceContainerHigh
        val fgAttr = if (on) MR.attr.colorOnPrimaryContainer else MR.attr.colorOnSurfaceVariant
        pill.background = MaterialShapeDrawable().apply {
            fillColor = ColorStateList.valueOf(themeColor(bgAttr))
            shapeAppearanceModel = ShapeAppearanceModel.builder()
                .setAllCornerSizes(M3Kit.dp(this@DevToolsActivity, M3Kit.SHAPE_PILL).toFloat())
                .build()
        }
        pill.setTextColor(themeColor(fgAttr))
    }

    /** 中性状态胶囊着色（如通道信息）。 */
    private fun paintNeutral(pill: TextView) {
        pill.background = MaterialShapeDrawable().apply {
            fillColor = ColorStateList.valueOf(themeColor(MR.attr.colorSurfaceContainerHigh))
            shapeAppearanceModel = ShapeAppearanceModel.builder()
                .setAllCornerSizes(M3Kit.dp(this@DevToolsActivity, M3Kit.SHAPE_PILL).toFloat())
                .build()
        }
        pill.setTextColor(themeColor(MR.attr.colorOnSurfaceVariant))
    }
}

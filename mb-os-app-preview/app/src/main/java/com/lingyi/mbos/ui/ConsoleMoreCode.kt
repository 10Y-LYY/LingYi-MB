/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

import com.lingyi.mbos.ui.M3Kit.doAfterTextChanged
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import com.google.android.material.R as MR
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.lingyi.mbos.CustomFlashActivity
import com.lingyi.mbos.MainActivity
import com.lingyi.mbos.util.Prefs
import com.lingyi.mbos.util.themeColor

/*
 * 控制台新功能 · 编程与刷写（预览版 4.0.0-preview 新增 7 项）：
 * 代码库 / 代码体检 / 新手教程 / 引脚图 / 错误字典 / 刷机日志 / 固件信息。
 */

// ---------- 1. 代码库 ----------

private data class CodeSnippet(val name: String, val desc: String, val code: String)

private val CODE_SNIPPETS: List<CodeSnippet> = listOf(
    CodeSnippet(
        "按钮点灯", "按 A 滚动问候语",
        """from microbit import *
while True:
    if button_a.was_pressed():
        display.scroll("Hello!")
    if button_b.was_pressed():
        display.show(Image.HEART)
        sleep(500)
        display.clear()
"""
    ),
    CodeSnippet(
        "温度显示", "读出并滚动温度",
        """from microbit import *
while True:
    t = temperature()
    display.scroll(str(t))
    sleep(1000)
"""
    ),
    CodeSnippet(
        "光线检测", "遮光时亮灯提醒",
        """from microbit import *
while True:
    if display.read_light() < 40:
        display.show(Image("99999:99999:99999:99999:99999"))
    else:
        display.clear()
    sleep(200)
"""
    ),
    CodeSnippet(
        "加速度读取", "三轴原始值",
        """from microbit import *
while True:
    x = accelerometer.get_x()
    y = accelerometer.get_y()
    z = accelerometer.get_z()
    print(x, y, z)
    sleep(200)
"""
    ),
    CodeSnippet(
        "指南针", "朝向角度（需先校准）",
        """from microbit import *
compass.calibrate()
while True:
    heading = compass.heading()
    display.scroll(str(heading))
    sleep(500)
"""
    ),
    CodeSnippet(
        "手势识别", "摇一摇换表情",
        """from microbit import *
while True:
    gesture = accelerometer.current_gesture()
    if gesture == "shake":
        display.show(Image.SURPRISED)
        sleep(500)
    elif gesture == "face up":
        display.show(Image.HAPPY)
    sleep(100)
"""
    ),
    CodeSnippet(
        "播放音乐", "内置旋律",
        """from microbit import *
import music
while True:
    if button_a.was_pressed():
        music.play(music.ODE)
    if button_b.was_pressed():
        music.pitch(440, 300)
    sleep(100)
"""
    ),
    CodeSnippet(
        "点阵动画", "逐帧闪烁爱心",
        """from microbit import *
frames = [Image.HEART, Image.HEART_SMALL]
while True:
    display.show(frames[0])
    sleep(400)
    display.show(frames[1])
    sleep(400)
"""
    ),
    CodeSnippet(
        "随机数", "摇一摇抽数",
        """from microbit import *
import random
while True:
    if accelerometer.was_gesture("shake"):
        n = random.randint(1, 6)
        display.show(str(n))
        sleep(800)
        display.clear()
"""
    ),
    CodeSnippet(
        "串口读数", "与灵逸 MB 配合的 READ 应答",
        """from microbit import *
while True:
    line = uart.readline()
    if line:
        cmd = str(line, "utf-8").strip()
        if cmd == "READ":
            t = temperature()
            print("A:%d|B:%d|T:%s" % (button_a.is_pressed(), button_b.is_pressed(), t))
"""
    ),
    CodeSnippet(
        "按键计数", "A 键点击计数",
        """from microbit import *
count = 0
while True:
    if button_a.was_pressed():
        count += 1
        display.scroll(str(count))
    sleep(50)
"""
    ),
    CodeSnippet(
        "摇一摇表情", "三种表情轮换",
        """from microbit import *
faces = [Image.HAPPY, Image.SAD, Image.ANGRY]
i = 0
while True:
    display.show(faces[i])
    if accelerometer.was_gesture("shake"):
        i = (i + 1) % len(faces)
        sleep(300)
"""
    ),
)

internal fun MainActivity.openCodeLib() {
    val ctx = this
    val root = M3Kit.dialogRoot(ctx)
    val tvHint = hintText(
        ctx,
        "12 段常用 MicroPython 示例，点「查看」复制或直接带去刷入页；" +
            "刷入后若要用 App 读数，请选带串口应答的模板再改。"
    )
    val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    val scroll = android.widget.ScrollView(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        addView(box)
    }
    CODE_SNIPPETS.forEach { snip ->
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        inner.addView(labelText(ctx, snip.name, bold = true).apply { textSize = 14f })
        inner.addView(
            TextView(ctx).apply {
                text = snip.desc
                textSize = 11f
                setTextColor(ctx.themeColor(MR.attr.colorOnSurfaceVariant))
            }
        )
        row.addView(inner)
        val btnView = MaterialButton(
            ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = "查看"
            textSize = 12f
            setOnClickListener { showSnippetDialog(snip) }
        }
        row.addView(btnView)
        box.addView(
            row,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = M3Kit.dp(ctx, 8) }
        )
    }
    root.addView(scroll)
    root.addView(tvHint)
    showFeatureDialog(root, "代码库")
}

private fun MainActivity.showSnippetDialog(snip: CodeSnippet) {
    val ctx = this
    val box = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(M3Kit.dp(ctx, 20), M3Kit.dp(ctx, 12), M3Kit.dp(ctx, 20), M3Kit.dp(ctx, 4))
    }
    val tvCode = TextView(ctx).apply {
        text = snip.code
        textSize = 11f
        typeface = android.graphics.Typeface.MONOSPACE
        setTextIsSelectable(true)
    }
    val scroll = android.widget.ScrollView(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        addView(tvCode)
    }
    box.addView(scroll)
    com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
        .setTitle("${snip.name} · ${snip.desc}")
        .setView(box)
        .setNegativeButton("关闭", null)
        .setNeutralButton("复制") { _, _ ->
            val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText(snip.name, snip.code))
            featureSnack("已复制「${snip.name}」代码")
        }
        .setPositiveButton("带去刷入") { _, _ ->
            ctx.startActivity(
                Intent(ctx, CustomFlashActivity::class.java)
                    .putExtra(CustomFlashActivity.EXTRA_PRESET_CODE, snip.code)
            )
        }
        .show()
}

// ---------- 2. 代码体检 ----------

internal fun MainActivity.openCodeCheck() {
    val ctx = this
    val root = M3Kit.dialogRoot(ctx)
    val et = EditText(ctx).apply {
        hint = "粘贴 MicroPython 代码"
        inputType = android.text.InputType.TYPE_CLASS_TEXT
        textSize = 12f
        typeface = android.graphics.Typeface.MONOSPACE
        minLines = 8
        gravity = Gravity.TOP
        setText(
            """from microbit import *
while True
    if button_a.was_pressed()
        display.scroll("Hi")
    sleep(100)"""
        )
    }
    val tvResult = TextView(ctx).apply {
        textSize = 12f
        typeface = android.graphics.Typeface.MONOSPACE
        setTextIsSelectable(true)
        setPadding(0, M3Kit.dp(ctx, 10), 0, 0)
    }
    val btnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    val btnCheck = MaterialButton(ctx).apply { text = "开始体检" }
    val btnClear = MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply { text = "清空" }
    btnRow.addView(btnCheck)
    btnRow.addView(
        btnClear,
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = M3Kit.dp(ctx, 10) }
    )
    val tvHint = hintText(ctx, "刷机前快速自查：括号配对、中文标点、缩进混用、缺冒号、print 写法；不替代解释器，但能挡掉大多数手滑。")
    root.addView(et)
    root.addView(
        btnRow,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 8) }
    )
    root.addView(tvResult)
    root.addView(tvHint)

    btnCheck.setOnClickListener {
        val issues = CodeChecker.check(et.text.toString())
        tvResult.text = if (issues.isEmpty()) {
            "✔ ${CodeChecker.summary(issues)}"
        } else {
            buildString {
                appendLine(CodeChecker.summary(issues))
                issues.take(20).forEach { i ->
                    val mark = if (i.level == CodeChecker.Level.ERROR) "✘" else "!"
                    appendLine("$mark 第 ${i.line} 行：${i.message}")
                }
                if (issues.size > 20) appendLine("……还有 ${issues.size - 20} 条")
            }
        }
    }
    btnClear.setOnClickListener {
        et.setText("")
        tvResult.text = ""
    }
    showFeatureDialog(root, "代码体检")
}

// ---------- 3. 新手教程 ----------

private val TUTORIAL_STEPS: List<Triple<String, String, String>> = listOf(
    Triple(
        "① 连接开发板",
        "USB 或蓝牙",
        "USB：打开开发者选项，用数据线连接 micro:bit（V2 需支持 OTG 的手机）。\n" +
            "蓝牙：在「设置」里配对后回到控制台，等状态变为「已连接」。"
    ),
    Triple(
        "② 刷入助手脚本",
        "让 App 能读数",
        "进入「固件与帮助」复制助手脚本，或用「编程模块」下载 .hex 后一键刷入；" +
            "刷好后开发板就能响应 SCROLL / SHOW / TONE / READ 指令。"
    ),
    Triple(
        "③ 第一个程序",
        "滚动一句问候",
        "回到控制台点「滚动文字」，输入 Hello 发送；再试「LED 画板」点几个格子发 SHOW。"
    ),
    Triple(
        "④ 玩传感器",
        "仪表盘与温度计",
        "点「实时仪表盘」看全部读数；「温度计」「光线计」「水平仪」各自是一个小实验；" +
            "读数异常时先检查连接与助手脚本。"
    ),
    Triple(
        "⑤ 玩游戏",
        "骰子到 21 点",
        "「游戏与互动」分类里有点一下就玩的骰子、猜数字、石头剪刀布，也有 21 点这种回合制；" +
            "想要跑在板子上的游戏，去「自定义刷入」选预设小游戏。"
    ),
    Triple(
        "⑥ 进阶：自动化",
        "宏 / 定时 / 快捷面板",
        "把常用指令收进「宏指令」；「定时任务」按间隔循环发送；「快捷面板」把常用功能收成一宫格。"
    ),
)

internal fun MainActivity.openTutorials() {
    val ctx = this
    val progress = HashSet(Prefs.getTutorialProgress(ctx))
    val root = M3Kit.dialogRoot(ctx)
    val tvInfo = labelText(ctx, "").apply { gravity = Gravity.CENTER }
    val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    val scroll = android.widget.ScrollView(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        addView(box)
    }
    fun refreshInfo() {
        tvInfo.text = "已完成 ${progress.size} / ${TUTORIAL_STEPS.size} 步"
    }
    TUTORIAL_STEPS.forEachIndexed { i, step ->
        val card = featureCard(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = if (i == 0) 0 else M3Kit.dp(ctx, 10) }
        }
        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(M3Kit.dp(ctx, 14), M3Kit.dp(ctx, 10), M3Kit.dp(ctx, 14), M3Kit.dp(ctx, 10))
        }
        val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val check = TextView(ctx).apply {
            text = if (progress.contains(i.toString())) "✔" else "○"
            textSize = 16f
        }
        val title = labelText(ctx, step.first, bold = true).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tag = TextView(ctx).apply {
            text = step.second
            textSize = 11f
            setTextColor(ctx.themeColor(MR.attr.colorPrimary))
        }
        head.addView(check)
        head.addView(title)
        head.addView(tag)
        val body = TextView(ctx).apply {
            text = step.third
            textSize = 12f
            setTextColor(ctx.themeColor(MR.attr.colorOnSurfaceVariant))
            setPadding(0, M3Kit.dp(ctx, 6), 0, 0)
        }
        inner.addView(head)
        inner.addView(body)
        card.addView(inner)
        card.setOnClickListener {
            val key = i.toString()
            if (progress.contains(key)) progress.remove(key) else progress.add(key)
            check.text = if (progress.contains(key)) "✔" else "○"
            Prefs.setTutorialProgress(ctx, progress)
            refreshInfo()
        }
        box.addView(card)
    }
    val btnReset = MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply {
        text = "重置进度"
        setOnClickListener {
            progress.clear()
            Prefs.setTutorialProgress(ctx, progress)
            for (i in 0 until box.childCount) {
                val card = box.getChildAt(i) as? com.google.android.material.card.MaterialCardView
                (card?.getChildAt(0) as? LinearLayout)?.let { head ->
                    (head.getChildAt(0) as? TextView)?.text = "○"
                }
            }
            refreshInfo()
        }
    }
    root.addView(tvInfo)
    root.addView(scroll)
    root.addView(
        btnReset,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 12) }
    )
    refreshInfo()
    showFeatureDialog(root, "新手教程")
}

// ---------- 4. 引脚图 ----------

private data class PinInfo(val name: String, val functions: String, val note: String)

private val PIN_TABLE: List<PinInfo> = listOf(
    PinInfo("P0（大环 0）", "GPIO · 模拟 · 触摸 · PWM · UART", "V2 还兼任扬声器输出；接蜂鸣器/喇叭常用它"),
    PinInfo("P1（大环 1）", "GPIO · 模拟 · 触摸 · PWM · UART", "模拟量程 0–3.3V"),
    PinInfo("P2（大环 2）", "GPIO · 模拟 · 触摸 · PWM · UART", "可做触摸按键"),
    PinInfo("3V 环", "电源输出 3.3V", "给外设供电，总电流有限"),
    PinInfo("GND 环", "公共地", "与所有 GND 连通"),
    PinInfo("P3", "GPIO · 模拟 · LED 列3 · PWM · UART", "与 LED 矩阵共用，用前先 display.disable()"),
    PinInfo("P4", "GPIO · 模拟 · LED 列1 · PWM · UART", "与 LED 矩阵共用"),
    PinInfo("P5", "GPIO · 按钮 A · PWM · UART", "默认接按钮 A，外接 10K 上拉"),
    PinInfo("P6", "GPIO · LED 列4 · PWM · UART", "与 LED 矩阵共用"),
    PinInfo("P7", "GPIO · LED 列2 · PWM · UART", "与 LED 矩阵共用"),
    PinInfo("P8", "GPIO · PWM · UART（NFC2）", "V2 起可配 NFC；默认关闭"),
    PinInfo("P9", "GPIO · PWM · UART（NFC1）", "V2 起可配 NFC；不再与 LED 共用"),
    PinInfo("P10", "GPIO · 模拟 · LED 列5 · PWM · UART", "与 LED 矩阵共用"),
    PinInfo("P11", "GPIO · 按钮 B · PWM · UART", "默认接按钮 B，外接 10K 上拉"),
    PinInfo("P12", "GPIO · 无障碍模式 · PWM · UART", "辅助无障碍专用，一般不要占用"),
    PinInfo("P13", "GPIO · SPI(SCLK) · PWM · UART", "SPI 时钟"),
    PinInfo("P14", "GPIO · SPI(MISO) · PWM · UART", "SPI 主入从出"),
    PinInfo("P15", "GPIO · SPI(MOSI) · PWM · UART", "SPI 主出从入"),
    PinInfo("P16", "GPIO · PWM · UART", "自由 GPIO"),
    PinInfo("3.3V / EN", "电源 / 使能", "断电 EN 到 GND 可硬关机"),
    PinInfo("GND", "公共地", "电源地"),
    PinInfo("P19", "GPIO · I2C(SCL) · PWM · UART", "I2C 总线，板载加速度计/罗盘也挂在这"),
    PinInfo("P20", "GPIO · I2C(SDA) · PWM · UART", "I2C 总线，4.7K 上拉"),
)

internal fun MainActivity.openPinTable() {
    val ctx = this
    val root = M3Kit.dialogRoot(ctx)
    val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    val scroll = android.widget.ScrollView(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        addView(box)
    }
    PIN_TABLE.forEach { pin ->
        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(M3Kit.dp(ctx, 12), M3Kit.dp(ctx, 8), M3Kit.dp(ctx, 12), M3Kit.dp(ctx, 8))
        }
        inner.addView(labelText(ctx, pin.name, bold = true).apply { textSize = 13f })
        inner.addView(
            TextView(ctx).apply {
                text = pin.functions
                textSize = 11f
                setTextColor(ctx.themeColor(MR.attr.colorPrimary))
            }
        )
        inner.addView(
            TextView(ctx).apply {
                text = pin.note
                textSize = 11f
                setTextColor(ctx.themeColor(MR.attr.colorOnSurfaceVariant))
            }
        )
        box.addView(
            featureCard(ctx).apply { addView(inner) },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = M3Kit.dp(ctx, 6) }
        )
    }
    val tvHint = hintText(
        ctx,
        "micro:bit V2 金手指 25 针速查。注意：PWM 同时最多 3 个；" +
            "整个金手指取电约 90mA，别直接驱动电机（会烧脚）。"
    )
    root.addView(scroll)
    root.addView(tvHint)
    showFeatureDialog(root, "引脚图")
}

// ---------- 5. 错误字典 ----------

private val ERROR_DICT: List<Triple<String, String, String>> = listOf(
    Triple("连不上蓝牙", "连接", "确认手机蓝牙已开、开发板已配对；远离 2.4G 干扰源；还不行就重启手机蓝牙或换 USB 连接。"),
    Triple("USB 没反应", "连接", "换数据线（充电线不行）、换手机 USB 口、确认手机支持 OTG；部分手机需在开发者选项里允许 USB 调试。"),
    Triple("读数全是 -", "读数", "助手脚本没刷或没运行：重新刷 MB-OS 脚本；若刚刷了游戏固件，需刷回助手脚本才恢复读数。"),
    Triple("NameError: name 'x' is not defined", "运行", "变量/函数名拼写或用在了定义之前；检查大小写。"),
    Triple("IndentationError", "运行", "缩进不统一：整个文件统一用 4 空格，别 Tab 空格混用（可用「代码体检」先查）。"),
    Triple("SyntaxError: invalid syntax", "运行", "缺冒号、括号不配对、中文标点；「代码体检」能定位大多数。"),
    Triple("ImportError: no module named 'x'", "运行", "模块名写错或固件没带该模块；micro:bit 内置模块有限，music / radio 等需对应固件支持。"),
    Triple("LED 不亮 / 很暗", "显示", "亮度被调到 0：「屏幕亮度」调回 5 以上；或 BRIGHT 指令发错。"),
    Triple("声音变调 / 没声", "声音", "TONE 频率超出蜂鸣器舒适区间（20Hz–20kHz 之外听不到）；模拟器本地发声需系统音量正常。"),
    Triple("罗盘乱跳", "传感器", "靠近磁铁 / 音箱 / 钢筋；按提示空中画 8 字重新校准。"),
    Triple("温度偏高", "传感器", "CPU 发热导致，通常比环境高 3–6°C，属正常；测环境温度请静置几分钟。"),
    Triple("手势不触发", "传感器", "手势要做得「干脆」：摇一摇用力、点击轻敲；轮询间隔内太快的动作可能漏采。"),
    Triple("刷写找不到 MICROBIT 盘", "刷机", "确认选中的是 MICROBIT U 盘；刷 hex 后盘会重挂载，按提示重新选一次再写 main.py。"),
    Triple("刷完开发板没反应", "刷机", "按背面复位键；确认 .hex 与 V2 匹配（V1 固件刷 V2 会失败）。"),
    Triple("更新检查失败", "更新", "网络不通或版本文件缓存；过会儿再试，或去发布页手动下载。"),
)

internal fun MainActivity.openErrorDict() {
    val ctx = this
    val root = M3Kit.dialogRoot(ctx)
    val et = EditText(ctx).apply {
        hint = "搜索关键词，如 蓝牙 / 缩进 / 刷"
        inputType = android.text.InputType.TYPE_CLASS_TEXT
        textSize = 14f
    }
    val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    val scroll = android.widget.ScrollView(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        addView(box)
    }
    fun render(filter: String) {
        box.removeAllViews()
        val items = ERROR_DICT.filter {
            filter.isBlank() || it.first.contains(filter, true) || it.second.contains(filter, true) ||
                it.third.contains(filter, true)
        }
        if (items.isEmpty()) {
            box.addView(labelText(ctx, "没有匹配的条目，换个关键词试试").apply { textSize = 13f })
            return
        }
        items.forEach { (q, cat, a) ->
            val inner = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(M3Kit.dp(ctx, 12), M3Kit.dp(ctx, 8), M3Kit.dp(ctx, 12), M3Kit.dp(ctx, 8))
            }
            val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            head.addView(
                labelText(ctx, q, bold = true).apply {
                    textSize = 13f
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
            )
            head.addView(
                TextView(ctx).apply {
                    text = cat
                    textSize = 11f
                    setTextColor(ctx.themeColor(MR.attr.colorPrimary))
                }
            )
            inner.addView(head)
            inner.addView(
                TextView(ctx).apply {
                    text = a
                    textSize = 12f
                    setTextColor(ctx.themeColor(MR.attr.colorOnSurfaceVariant))
                    setPadding(0, M3Kit.dp(ctx, 4), 0, 0)
                }
            )
            box.addView(
                featureCard(ctx).apply { addView(inner) },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = M3Kit.dp(ctx, 6) }
            )
        }
    }
    et.doAfterTextChanged { render(it) }
    render("")
    val tvHint = hintText(ctx, "连接 / 读数 / 运行 / 刷机 / 更新五类常见问题与处置办法，可搜索。")
    root.addView(et)
    root.addView(scroll)
    root.addView(tvHint)
    showFeatureDialog(root, "错误字典")
}

// ---------- 6. 刷机日志 ----------

internal fun MainActivity.openFlashLog() {
    val ctx = this
    val root = M3Kit.dialogRoot(ctx)
    val tvInfo = labelText(ctx, "").apply { gravity = Gravity.CENTER }
    val tvLog = TextView(ctx).apply {
        textSize = 12f
        typeface = android.graphics.Typeface.MONOSPACE
        setTextIsSelectable(true)
        setPadding(0, M3Kit.dp(ctx, 10), 0, 0)
    }
    val scroll = android.widget.ScrollView(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        addView(tvLog)
    }
    fun refresh() {
        val entries = FlashLog.all(ctx)
        tvInfo.text = "共 ${entries.size} 条（最多保留 200 条）"
        tvLog.text = if (entries.isEmpty()) {
            "（还没有刷写记录；用「自定义刷入固件」刷一次就会出现在这里）"
        } else {
            entries.joinToString("\n") { "${it.time}  ${it.target}  ${it.result}" }
        }
    }
    refresh()
    val btnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    val btnExport = MaterialButton(ctx).apply { text = "导出 txt" }
    val btnClear = MaterialButton(
        ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply { text = "清空" }
    btnRow.addView(btnExport)
    btnRow.addView(
        btnClear,
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = M3Kit.dp(ctx, 10) }
    )
    val tvHint = hintText(ctx, "每次刷写（游戏 / Python / HEX）的结果自动记录，可导出 txt 给同学老师留痕。")
    root.addView(tvInfo)
    root.addView(scroll)
    root.addView(
        btnRow,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 10) }
    )
    root.addView(tvHint)

    btnExport.setOnClickListener {
        val f = FlashLog.export(ctx)
        if (f == null) {
            featureSnack("暂无记录可导出")
            return@setOnClickListener
        }
        val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { ctx.startActivity(Intent.createChooser(intent, "导出刷机日志")) }
            .onFailure { featureSnack("没有可处理文本的应用") }
    }
    btnClear.setOnClickListener {
        FlashLog.clear(ctx)
        refresh()
        featureSnack("已清空刷机日志")
    }
    showFeatureDialog(root, "刷机日志")
}

// ---------- 7. 固件信息 ----------

internal fun MainActivity.openScriptInfo() {
    if (!requireConnected()) return
    val ctx = this
    val root = M3Kit.dialogRoot(ctx)
    val tvBig = bigReadout(ctx, "读取中…")
    val tvDetail = labelText(ctx, "").apply { gravity = Gravity.CENTER }
    val btn = MaterialButton(ctx).apply { text = "重新读取" }
    val tvHint = hintText(
        ctx,
        "读取板载 MB-OS 助手脚本 / MicroPython 版本；若为空说明板子在跑游戏或自定义代码，" +
            "App 的读数功能需要重新刷入助手脚本。"
    )
    root.addView(tvBig)
    root.addView(tvDetail)
    root.addView(
        btn,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = M3Kit.dp(ctx, 12) }
    )
    root.addView(tvHint)

    fun query() {
        tvBig.text = "读取中…"
        tvDetail.text = ""
        sendLine(Protocol.info())
    }

    // 一次性监听 INFO 回包
    val listener = object : com.lingyi.mbos.ble.MicrobitConnection.Listener {
        override fun onState(state: com.lingyi.mbos.ble.ConnState) {}
        override fun onData(data: String) {
            val info = Protocol.parseInfo(data) ?: return
            runOnUiThread {
                val mbos = info.mbos
                val mp = info.micropython
                tvBig.text = mbos?.let { "MB-OS v$it" } ?: "未检测到助手脚本"
                val older = mbos != null && Protocol.isOlder(mbos, "3.0.0")
                tvDetail.text = buildString {
                    append("MicroPython ${mp ?: "未知"}")
                    if (mbos != null) {
                        append("\n助手脚本 ${if (older) "版本偏旧，建议去「固件与帮助」更新" else "正常"}")
                        Prefs.setKnownFirmwareVersion(ctx, mbos)
                    } else {
                        append("\n板载可能在运行游戏 / 自定义代码，读数功能暂不可用")
                    }
                }
            }
        }
    }
    com.lingyi.mbos.ble.ConnectionHub.addListener(listener)

    btn.setOnClickListener { query() }
    showFeatureDialog(root, "固件信息", onDismiss = {
        com.lingyi.mbos.ble.ConnectionHub.removeListener(listener)
    })
    query()
}

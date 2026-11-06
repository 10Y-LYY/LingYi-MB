/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

import com.lingyi.mbos.R

/**
 * 控制台功能目录：全部功能的单一事实来源。
 *
 * 主控台两种展示风格共用同一份数据：
 *  - 「列表」：按 [FEATURES] 原始顺序逐条横排（历史行为，顺序即本表顺序）；
 *  - 「大方格」：按 [Category] 聚合成分类大卡，点卡进二级页面挑选。
 *
 * 新增功能只需在本表追加一项并归入对应分类，
 * [com.lingyi.mbos.MainActivity.onFuncClick] 的 id 分派同步补齐即可。
 */
object FeatureCatalog {

    /** 功能分类：大方格主题下的一张卡 / 二级页的一类。 */
    enum class Category(val key: String, val title: String, val desc: String, val icon: Int) {
        DISPLAY("display", "显示与预览", "文字、图案、数字与亮度", R.drawable.ic_grid),
        SOUND("sound", "声音与演示", "音调、旋律与一键演示", R.drawable.ic_music),
        SENSOR("sensor", "传感器与数据", "姿态、朝向与实时读数", R.drawable.ic_sensor),
        CODE("code", "编程与刷写", "MakeCode 编程与固件刷入", R.drawable.ic_code),
        AUTO("auto", "指针与自动化", "无线指针、宏指令与定时任务", R.drawable.ic_pointer),
        GAME("game", "游戏与互动", "骰子、猜数字与反应挑战", R.drawable.ic_games),
        TOOL("tool", "实用工具", "摩斯电码、寻物铃与数据记录", R.drawable.ic_tool),
    }

    data class Feature(
        val id: String,
        val icon: Int,
        val title: String,
        val desc: String,
        val category: Category
    )

    /** 全部功能（顺序 = 列表模式的展示顺序，勿随意调整）。 */
    val FEATURES: List<Feature> = listOf(
        Feature("preview", R.drawable.ic_info, "预览模式", "实时读取开发板全部信息", Category.DISPLAY),
        Feature("scroll", R.drawable.ic_text, "滚动文字", "在 LED 屏上滚动显示文字", Category.DISPLAY),
        Feature("led", R.drawable.ic_grid, "LED 画板", "点亮 5×5 矩阵，自定义图案", Category.DISPLAY),
        Feature("plot", R.drawable.ic_plot, "单点绘制", "只点亮或熄灭矩阵中的某一个点", Category.DISPLAY),
        Feature("emoji", R.drawable.ic_emoji, "表情图标", "发送预设表情与符号", Category.DISPLAY),
        Feature("tone", R.drawable.ic_tone, "播放音调", "用板载喇叭播放频率与节奏", Category.SOUND),
        Feature("melody", R.drawable.ic_music, "播放旋律", "内置曲目一键播放", Category.SOUND),
        Feature("demo", R.drawable.ic_play, "一键演示", "连续播放文字、图案与旋律", Category.SOUND),
        Feature("pointer", R.drawable.ic_pointer, "陀螺仪指针", "把 micro:bit 变成无线触摸笔（倾斜移动 / A 点击 / B 拖动）", Category.AUTO),
        Feature("number", R.drawable.ic_number, "显示数字", "在 LED 屏滚动显示一个数字", Category.DISPLAY),
        Feature("compass", R.drawable.ic_compass, "指南针", "读取开发板当前朝向角度", Category.SENSOR),
        Feature("gesture", R.drawable.ic_gesture, "手势检测", "摇一摇 / 点击等姿态", Category.SENSOR),
        Feature("brightness", R.drawable.ic_brightness, "屏幕亮度", "调节 LED 亮度（0–9）", Category.DISPLAY),
        Feature("sensor", R.drawable.ic_sensor, "读取传感器", "加速度 / 温度 / 光线 / 按键", Category.SENSOR),
        Feature("custom", R.drawable.ic_terminal, "自定义指令", "发送任意串口指令", Category.AUTO),
        Feature("clear", R.drawable.ic_clear, "清空屏幕", "熄灭 LED 矩阵", Category.DISPLAY),
        Feature("makecode", R.drawable.ic_code, "编程模块", "MakeCode 官网编程，下载 .hex 后一键刷入", Category.CODE),
        Feature("customflash", R.drawable.ic_flash, "自定义刷入固件", "刷入你的代码 / 预设小游戏 / .hex 到 micro:bit", Category.CODE),
        Feature("help", R.drawable.ic_help, "固件与帮助", "刷机脚本与连接说明", Category.CODE),
        Feature("dashboard", R.drawable.ic_sensor, "实时仪表盘", "常驻读取加速度 / 温度 / 光线 / 按键 / 手势", Category.SENSOR),
        Feature("macro", R.drawable.ic_terminal, "宏指令", "收藏常用指令，一键发送", Category.AUTO),
        Feature("timer", R.drawable.ic_refresh, "定时任务", "按间隔循环发送指令", Category.AUTO),
        Feature("devtools", R.drawable.ic_more_vert, "开发者工具", "协议自检 / 连接诊断 / 指令注入", Category.AUTO),

        // ---------- v3.3.0-preview 新增（15 项，全部复用既有协议，老固件免刷写） ----------
        // 传感器与数据：READ 轮询 + 记录导出
        Feature("thermometer", R.drawable.ic_thermometer, "温度计", "实时温度·极值统计·高温报警", Category.SENSOR),
        Feature("light", R.drawable.ic_light, "光线计", "环境光强度与明暗判断", Category.SENSOR),
        Feature("level", R.drawable.ic_level, "水平仪", "加速度倾角·气泡调平", Category.SENSOR),
        Feature("buttons", R.drawable.ic_button, "按钮监视器", "A / B 按键状态与事件记录", Category.SENSOR),
        Feature("logger", R.drawable.ic_record, "传感器记录仪", "定时采样·导出 CSV 表格", Category.SENSOR),
        // 游戏与互动：设备即玩具
        Feature("dice", R.drawable.ic_dice, "骰子", "点击或摇一摇掷点数", Category.GAME),
        Feature("coin", R.drawable.ic_coin, "硬币抛掷", "正反面随机·历史统计", Category.GAME),
        Feature("reaction", R.drawable.ic_reaction, "反应测试", "亮灯瞬间按键测反应速度", Category.GAME),
        Feature("guess", R.drawable.ic_guess, "猜数字", "你出题 App 猜·设备按键作答", Category.GAME),
        Feature("rps", R.drawable.ic_rps, "石头剪刀布", "与开发板对战·三局两胜", Category.GAME),
        // 显示与声音：创意编排
        Feature("animation", R.drawable.ic_animation, "LED 动画", "多帧动画编排·保存与播放", Category.DISPLAY),
        Feature("piano", R.drawable.ic_piano, "电子琴", "弹奏音符·录音回放", Category.SOUND),
        Feature("barrage", R.drawable.ic_barrage, "弹幕留言", "多条消息连续滚动播报", Category.DISPLAY),
        // 实用工具
        Feature("morse", R.drawable.ic_morse, "摩斯电码", "文本转电码·声光播放", Category.TOOL),
        Feature("findbell", R.drawable.ic_bell, "寻物铃", "让开发板响铃闪光帮你找它", Category.TOOL),

        // ---------- 预览版 4.0.0-preview 新增（50 项，每模块至少 7 项，全部复用既有协议，老固件免刷写） ----------
        // 显示与预览（7 项）
        Feature("marquee", R.drawable.ic_marquee, "跑马灯", "三档速度·循环遍数可选", Category.DISPLAY),
        Feature("bigdigit", R.drawable.ic_bigdigit, "大字数字", "3×5 大字形逐位播放任意数字", Category.DISPLAY),
        Feature("brightauto", R.drawable.ic_brightauto, "自动亮度", "按环境光自动调节点阵亮度", Category.DISPLAY),
        Feature("nightlight", R.drawable.ic_nightlight, "小夜灯", "月亮星星呼吸灯·渐变助眠", Category.DISPLAY),
        // 声音与演示（7 项）
        Feature("metronome", R.drawable.ic_metronome, "节拍器", "BPM 40-240·多种拍号·首拍重音", Category.SOUND),
        Feature("doorbell", R.drawable.ic_doorbell, "门铃", "叮咚/电子两种音色·多声连响", Category.SOUND),
        Feature("siren", R.drawable.ic_siren, "警报器", "往复扫频·速度可调·声光齐鸣", Category.SOUND),
        Feature("soundboard", R.drawable.ic_soundboard, "音效板", "8 个常用音效一键播放", Category.SOUND),
        Feature("tunepad", R.drawable.ic_tunepad, "谱曲台", "文本谱曲·示例曲谱·即编即播", Category.SOUND),
        Feature("melodymix", R.drawable.ic_melodymix, "旋律串烧", "21 曲多选编排·连续播放", Category.SOUND),
        // 传感器与数据（7 项）
        Feature("compassrose", R.drawable.ic_compassrose, "罗盘玫瑰", "指针随朝向转动·八方位识别", Category.SENSOR),
        Feature("shakecount", R.drawable.ic_shakecount, "摇动计数", "计摇动次数与频率·目标达成提示", Category.SENSOR),
        Feature("gesturelog", R.drawable.ic_gesturelog, "手势日志", "8 种手势实时识别与时间戳记录", Category.SENSOR),
        Feature("droptest", R.drawable.ic_droptest, "跌落测试", "自由落体时长与冲击力记录", Category.SENSOR),
        Feature("tempchart", R.drawable.ic_tempchart, "温度曲线", "实时折线图·极值与均值统计", Category.SENSOR),
        Feature("motiondetector", R.drawable.ic_motiondetector, "动静监测", "三轴变化检测·移动报警", Category.SENSOR),
        Feature("sensortest", R.drawable.ic_sensortest, "传感器体检", "五步体检流程·快速判断好坏", Category.SENSOR),
        // 编程与刷写（7 项）
        Feature("codelib", R.drawable.ic_codelib, "代码库", "12 段 MicroPython 示例·可复制可刷入", Category.CODE),
        Feature("codecheck", R.drawable.ic_codecheck, "代码体检", "静态检查常见笔误与坏味道", Category.CODE),
        Feature("tutorials", R.drawable.ic_tutorials, "新手教程", "六步上手路径·进度本地保存", Category.CODE),
        Feature("pintable", R.drawable.ic_pintable, "引脚速查", "micro:bit V2 全引脚用途表", Category.CODE),
        Feature("errordict", R.drawable.ic_errordict, "错误词典", "15 条常见报错与修复建议", Category.CODE),
        Feature("flashlog", R.drawable.ic_flashlog, "刷机日志", "历次刷入记录·可导出可清空", Category.CODE),
        Feature("scriptinfo", R.drawable.ic_scriptinfo, "脚本信息", "板载固件/MicroPython 版本直读", Category.CODE),
        // 指针与自动化（7 项）
        Feature("shakebutton", R.drawable.ic_shakebutton, "摇晃触发器", "摇一下执行指令/旋律/图案", Category.AUTO),
        Feature("gesturelock", R.drawable.ic_gesturelock, "手势锁", "自设手势口令·复现解锁", Category.AUTO),
        Feature("linkmonitor", R.drawable.ic_linkmonitor, "连接哨兵", "在线时长与断线告警", Category.AUTO),
        Feature("commandseq", R.drawable.ic_commandseq, "指令编排", "多指令带延时按序自动执行", Category.AUTO),
        Feature("quickpanel", R.drawable.ic_quickpanel, "快捷面板", "自选常用功能宫格·一键直达", Category.AUTO),
        Feature("logstream", R.drawable.ic_logstream, "实时日志", "串口收发全量留痕·过滤导出", Category.AUTO),
        Feature("idlebot", R.drawable.ic_idlebot, "空闲机器人", "无操作时眨眼问候·自动待机动画", Category.AUTO),
        // 游戏与互动（7 项）
        Feature("tapcount", R.drawable.ic_tapcount, "狂点挑战", "10 秒手速对战·最佳纪录", Category.GAME),
        Feature("holdgame", R.drawable.ic_holdgame, "忍耐挑战", "凭手感停计时·误差评级", Category.GAME),
        Feature("hotpotato", R.drawable.ic_hotpotato, "击鼓传花", "随机爆炸·惩罚内容滚动", Category.GAME),
        Feature("roulette", R.drawable.ic_roulette, "轮盘赌", "多人弹巢随机·中弹哀嚎", Category.GAME),
        Feature("truthdare", R.drawable.ic_truthdare, "真心话大冒险", "40 条题库·摇一摇抽题", Category.GAME),
        Feature("quizshow", R.drawable.ic_quizshow, "知识问答", "20 道判断·连对加分", Category.GAME),
        Feature("blackjack", R.drawable.ic_blackjack, "21 点", "与庄家对赌·A 要牌 B 停牌", Category.GAME),
        // 实用工具（8 项）
        Feature("torch", R.drawable.ic_torch, "手电筒", "常亮/爆闪/SOS 三档点阵灯", Category.TOOL),
    )

    /** 按分类取功能（保持 [FEATURES] 内的相对顺序）。 */
    fun byCategory(category: Category): List<Feature> = FEATURES.filter { it.category == category }

    /** 按 key 反查分类（非法 key 回落显示类，防御 intent extra 伪造）。 */
    fun categoryOf(key: String?): Category =
        Category.entries.firstOrNull { it.key == key } ?: Category.DISPLAY
}

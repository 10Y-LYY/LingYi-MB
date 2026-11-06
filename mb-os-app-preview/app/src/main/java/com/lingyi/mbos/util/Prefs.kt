/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.util

import android.content.Context

/** 简单的偏好存储：连接方式、首次启动、更新缓存、指针灵敏度、已知板载固件版本等。 */
object Prefs {

    private const val NAME = "mbos_prefs"
    private const val KEY_CONN = "conn_type"
    private const val KEY_SEEN_GUIDE = "seen_guide"
    private const val KEY_DEV_MODE = "dev_mode"
    private const val KEY_UPDATE_AVAILABLE = "update_available"
    private const val KEY_UPDATE_URL = "update_url"
    private const val KEY_UPDATE_PAGE = "update_page"
    private const val KEY_UPDATE_NAME = "update_name"
    private const val KEY_UPDATE_NOTES = "update_notes"
    private const val KEY_UPDATE_PUBLISHED = "update_published"
    private const val KEY_POINTER_SENSITIVITY = "pointer_sensitivity"
    private const val KEY_KNOWN_FW = "known_fw"

    /** 崩溃日志「已查看时间」：晚于它的崩溃文件视为待处理，启动时提醒用户反馈。 */
    private const val KEY_CRASH_SEEN_TS = "crash_seen_ts"

    // ---------- 设置：外观 ----------
    private const val KEY_THEME_MODE = "theme_mode"        // system / light / dark
    private const val KEY_THEME_COLOR = "theme_color"      // m3 / teal / gold / indigo / rose / forest

    /**
     * 控制台功能列表展示风格（主题级设置）：
     *  - "list"：一条一条的横排列表（默认，历史行为）；
     *  - "grid"：按分类整合的 M3 大方格，点格进二级页面挑选。
     */
    const val CONSOLE_STYLE_LIST = "list"
    const val CONSOLE_STYLE_GRID = "grid"
    private const val KEY_CONSOLE_STYLE = "console_style"

    /** 更新通道：stable / preview / dev，由构建 flavor 的 BuildConfig.DEFAULT_CHANNEL 决定默认通道。 */
    private const val KEY_CHANNEL = "update_channel"

    /** 反应测试最佳成绩（毫秒）。 */
    private const val KEY_REACTION_BEST = "reaction_best_ms"

    /**
     * 主题纪元：外观主题（模式 / 配色）每变更一次 +1。
     * 设置页只 recreate() 自己，返回栈里的旧页面不会自动重建，会出现
     * 「设置页新配色 + 主页面旧配色」的文字不匹配；各页面 onResume 时
     * 比对创建时记录的纪元，发现变化即自我重建（见 BaseActivity）。
     */
    private const val KEY_THEME_EPOCH = "theme_epoch"

    // ---------- 设置：连接 ----------
    private const val KEY_READ_INTERVAL = "read_interval"  // 实时读取轮询间隔（毫秒）
    private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"

    // ---------- 设置：通用 ----------
    private const val KEY_HAPTIC = "haptic"

    /** 界面过渡动画总开关（页面转场 + 内容入场 + 组件显隐），默认开。 */
    private const val KEY_ANIM_ENABLED = "anim_enabled"

    /** 是否首次启动（引导页）。首次进入后才标记，用于「仅首次显示刷入固件」。 */
    fun isFirstLaunch(ctx: Context): Boolean {
        val sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return !sp.getBoolean(KEY_SEEN_GUIDE, false)
    }

    /** 标记已启动过（首次进入完成后调用，配置变更重建不重复标记）。 */
    fun markLaunched(ctx: Context) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SEEN_GUIDE, true).apply()
    }

    // ---------- 开发者调试模式 ----------
    fun isDevMode(ctx: Context): Boolean =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(KEY_DEV_MODE, false)

    fun setDevMode(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DEV_MODE, on).apply()
    }

    /** 调试：重置首次启动标记，便于重新走一遍引导 / 刷机卡。 */
    fun resetFirstLaunch(ctx: Context) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_SEEN_GUIDE).apply()
    }

    /** 调试：清空更新缓存。 */
    fun clearUpdateCache(ctx: Context) {
        val sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        sp.edit().apply {
            remove(KEY_UPDATE_AVAILABLE)
            remove(KEY_UPDATE_URL)
            remove(KEY_UPDATE_PAGE)
            remove(KEY_UPDATE_NAME)
            remove(KEY_UPDATE_NOTES)
            remove(KEY_UPDATE_PUBLISHED)
            apply()
        }
    }

    fun getConnType(ctx: Context): String {
        val sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return sp.getString(KEY_CONN, "ble") ?: "ble"
    }

    fun setConnType(ctx: Context, type: String) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_CONN, type).apply()
    }

    /** 最近一次检查到的更新信息（缓存，便于离线时仍展示）。 */
    data class UpdateCache(
        val available: Boolean,
        val url: String,
        val page: String,
        val name: String,
        val notes: String,
        val published: String
    )

    fun getCachedUpdate(ctx: Context): UpdateCache? {
        val sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        if (!sp.contains(KEY_UPDATE_AVAILABLE)) return null
        return UpdateCache(
            available = sp.getBoolean(KEY_UPDATE_AVAILABLE, false),
            url = sp.getString(KEY_UPDATE_URL, "") ?: "",
            page = sp.getString(KEY_UPDATE_PAGE, "") ?: "",
            name = sp.getString(KEY_UPDATE_NAME, "") ?: "",
            notes = sp.getString(KEY_UPDATE_NOTES, "") ?: "",
            published = sp.getString(KEY_UPDATE_PUBLISHED, "") ?: ""
        )
    }

    fun setCachedUpdate(ctx: Context, u: UpdateCache) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().apply {
            putBoolean(KEY_UPDATE_AVAILABLE, u.available)
            putString(KEY_UPDATE_URL, u.url)
            putString(KEY_UPDATE_PAGE, u.page)
            putString(KEY_UPDATE_NAME, u.name)
            putString(KEY_UPDATE_NOTES, u.notes)
            putString(KEY_UPDATE_PUBLISHED, u.published)
            apply()
        }
    }

    // ---------- 无线指针（陀螺仪鼠标） ----------
    /**
     * 指针灵敏度网格（与布局里 Slider 的 valueFrom/stepSize/valueTo 严格一致）。
     * BaseSlider 要求 value 必须落在 valueFrom + n×stepSize 上，历史野值
     * （如实锤的 0.8333333）会让布局期 validateValues 直接抛异常崩页面。
     */
    const val SENS_MIN = 0.4f
    const val SENS_MAX = 3.0f
    const val SENS_STEP = 0.2f
    const val SENS_DEFAULT = 1.0f

    /**
     * 把灵敏度吸附到合法网格（0.4 + n×0.2，n=0..13）。
     * NaN / Infinity / 越界 / 脱网值一律收敛；读与写两条路径都经过它，
     * 保证任何调用方（设置页、指针页、固件倍率计算）拿到的都是合法值。
     */
    fun snapSensitivity(v: Float): Float {
        if (v.isNaN()) return SENS_DEFAULT
        val clamped = v.coerceIn(SENS_MIN, SENS_MAX) // ±Infinity 收敛到边界
        val steps = Math.round((clamped - SENS_MIN) / SENS_STEP).toInt().coerceIn(0, 13)
        // coerceAtMost 兜底 0.4f+13*0.2f 的浮点误差（可能得到 3.0000002f > valueTo）
        return (SENS_MIN + steps * SENS_STEP).coerceAtMost(SENS_MAX)
    }

    /** 指针灵敏度倍率（0.4–3.0，默认 1.0；读取时吸附到合法网格）。 */
    fun getPointerSensitivity(ctx: Context): Float =
        snapSensitivity(
            ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
                .getFloat(KEY_POINTER_SENSITIVITY, SENS_DEFAULT)
        )

    fun setPointerSensitivity(ctx: Context, value: Float) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_POINTER_SENSITIVITY, snapSensitivity(value)).apply()
    }

    // ---------- 无线指针（陀螺仪鼠标）高级控制 ----------
    const val POINTER_ACTION_TAP = "tap"        // 单击
    const val POINTER_ACTION_DOUBLE = "double"  // 双击
    const val POINTER_ACTION_DRAG = "drag"      // 按住拖动（按下沿切换）
    const val POINTER_CURVE_LINEAR = "linear"
    const val POINTER_CURVE_EXP = "exp"

    private const val KEY_POINTER_BTN_A = "pointer_btn_a"
    private const val KEY_POINTER_BTN_B = "pointer_btn_b"
    private const val KEY_POINTER_SCROLL = "pointer_scroll"
    private const val KEY_POINTER_DEADZONE = "pointer_deadzone"
    private const val KEY_POINTER_SMOOTH = "pointer_smooth"
    private const val KEY_POINTER_CURVE = "pointer_curve"
    private const val KEY_POINTER_AUTORECENTER = "pointer_autorecenter"

    // 死区 / 平滑的合法网格（与 activity_pointer.xml 的 Slider 参数严格一致）
    private const val DEADZONE_MAX = 6f
    private const val DEADZONE_STEP = 1f
    private const val DEADZONE_STEPS = 6
    private const val SMOOTH_MAX = 0.9f
    private const val SMOOTH_STEP = 0.1f
    private const val SMOOTH_STEPS = 9

    /** 按钮 A 动作：单击（默认）/ 双击 / 拖动切换。 */
    fun getPointerBtnA(ctx: Context): String =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_POINTER_BTN_A, POINTER_ACTION_TAP) ?: POINTER_ACTION_TAP

    fun setPointerBtnA(ctx: Context, v: String) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putString(KEY_POINTER_BTN_A, v).apply()
    }

    /** 按钮 B 动作：拖动切换（默认）/ 单击 / 双击。 */
    fun getPointerBtnB(ctx: Context): String =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_POINTER_BTN_B, POINTER_ACTION_DRAG) ?: POINTER_ACTION_DRAG

    fun setPointerBtnB(ctx: Context, v: String) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putString(KEY_POINTER_BTN_B, v).apply()
    }

    /** 滚动模式：纵向倾斜转为页面滚动（横向忽略）。 */
    fun isPointerScrollMode(ctx: Context): Boolean =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(KEY_POINTER_SCROLL, false)

    fun setPointerScrollMode(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_POINTER_SCROLL, on).apply()
    }

    /** 死区（px）：倾斜增量绝对值小于该值视为静默，抗漂移。0 关闭。
     *  合法网格 0..6 step 1（与 activity_pointer.xml 滑块对齐），读与写都经过吸附，
     *  防止旧版本/备份还原写入的脱网值让 Slider 布局期抛 IllegalStateException。 */
    fun snapDeadzone(v: Float): Float {
        if (v.isNaN()) return 0f
        val clamped = v.coerceIn(0f, DEADZONE_MAX) // ±Infinity 收敛到边界
        val steps = Math.round(clamped / DEADZONE_STEP).toInt().coerceIn(0, DEADZONE_STEPS)
        return (steps * DEADZONE_STEP).coerceAtMost(DEADZONE_MAX)
    }

    fun getPointerDeadzone(ctx: Context): Float =
        snapDeadzone(
            ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getFloat(KEY_POINTER_DEADZONE, 0f)
        )

    fun setPointerDeadzone(ctx: Context, v: Float) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_POINTER_DEADZONE, snapDeadzone(v)).apply()
    }

    /** 平滑系数 0–1：低通滤波，0 关闭（原值直通）。
     *  合法网格 0..0.9 step 0.1（与 activity_pointer.xml 滑块对齐）。 */
    fun snapSmoothing(v: Float): Float {
        if (v.isNaN()) return 0f
        val clamped = v.coerceIn(0f, SMOOTH_MAX)
        val steps = Math.round((clamped / SMOOTH_STEP).toDouble()).toInt().coerceIn(0, SMOOTH_STEPS)
        return (steps * SMOOTH_STEP).coerceAtMost(SMOOTH_MAX)
    }

    fun getPointerSmoothing(ctx: Context): Float =
        snapSmoothing(
            ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getFloat(KEY_POINTER_SMOOTH, 0f)
        )

    fun setPointerSmoothing(ctx: Context, v: Float) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_POINTER_SMOOTH, snapSmoothing(v)).apply()
    }

    fun getPointerCurve(ctx: Context): String =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_POINTER_CURVE, POINTER_CURVE_LINEAR) ?: POINTER_CURVE_LINEAR

    fun setPointerCurve(ctx: Context, v: String) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putString(KEY_POINTER_CURVE, v).apply()
    }

    /** 空闲自动回中：超过 1.5s 无指针数据则把系统指针移回屏幕中心。 */
    fun isPointerAutoRecenter(ctx: Context): Boolean =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(KEY_POINTER_AUTORECENTER, false)

    fun setPointerAutoRecenter(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_POINTER_AUTORECENTER, on).apply()
    }

    /** 当前指针配置快照，供前台服务实时消费（每次读取均吸附到合法值）。 */
    fun getPointerProfile(ctx: Context): PointerProfile = PointerProfile(
        sens = getPointerSensitivity(ctx),
        btnA = getPointerBtnA(ctx),
        btnB = getPointerBtnB(ctx),
        scrollMode = isPointerScrollMode(ctx),
        deadzone = getPointerDeadzone(ctx),
        smoothing = getPointerSmoothing(ctx),
        curve = getPointerCurve(ctx),
        autoRecenter = isPointerAutoRecenter(ctx)
    )

    // ---------- 板载固件版本追踪 ----------
    /**
     * 「已知的板载 MB-OS 固件版本」，用于在新固件发布时提示重新刷入：
     *  · 通过引导页一键刷入内置固件成功 → 记为当前内置版本；
     *  · 连接后收到 INFO 回显 → 以板载真实版本刷新；
     *  · 通过自定义刷入（游戏 / 自有 Python / 自有 HEX）→ 置空
     *    （板载不再运行 MB-OS 助手脚本，无法再响应 App 指令）。
     * null 表示未知（从未刷过，或板载跑的是自定义内容）。
     */
    fun getKnownFirmwareVersion(ctx: Context): String? =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(KEY_KNOWN_FW, null)

    /** 记录已知板载固件版本；传 null 表示清空（板载跑自定义内容）。 */
    fun setKnownFirmwareVersion(ctx: Context, version: String?) {
        val sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        sp.edit().apply {
            if (version == null) remove(KEY_KNOWN_FW) else putString(KEY_KNOWN_FW, version)
            apply()
        }
    }

    // ---------- 狂点挑战最佳纪录 ----------
    private const val KEY_TAP_BEST = "tap_best"

    fun getTapBest(ctx: Context): Int =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt(KEY_TAP_BEST, 0)

    fun setTapBest(ctx: Context, count: Int) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_TAP_BEST, count).apply()
    }

    // ---------- 新手教程进度（步骤索引的字符串集合） ----------
    private const val KEY_TUTORIAL_PROGRESS = "tutorial_progress"

    fun getTutorialProgress(ctx: Context): Set<String> =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_TUTORIAL_PROGRESS, emptySet()) ?: emptySet()

    fun setTutorialProgress(ctx: Context, steps: Set<String>) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_TUTORIAL_PROGRESS, steps).apply()
    }

    // ---------- 指令编排（CommandSeq）与快捷面板（QuickPanel） ----------
    private const val KEY_COMMAND_SEQ = "command_seq"
    private const val KEY_QUICK_PANEL = "quick_panel"

    /** 指令编排序列（JSON 字符串，格式见 ui/CommandSeqStore）。 */
    fun getCommandSeq(ctx: Context): String? =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(KEY_COMMAND_SEQ, null)

    fun setCommandSeq(ctx: Context, json: String) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_COMMAND_SEQ, json).apply()
    }

    /** 快捷面板勾选的功能 id 集合。 */
    fun getQuickPanel(ctx: Context): Set<String> =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_QUICK_PANEL, emptySet()) ?: emptySet()

    fun setQuickPanel(ctx: Context, ids: Set<String>) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_QUICK_PANEL, ids).apply()
    }

    // ---------- 便签速记（Scratchpad）/ 上课铃课表（BellSchedule）/ 随机数历史 ----------
    private const val KEY_SCRATCHPAD = "scratchpad"
    private const val KEY_BELL_SCHEDULE = "bell_schedule"
    private const val KEY_RANDOM_HISTORY = "random_history"

    /** 便签速记正文（多行纯文本，可为空）。 */
    fun getScratchpad(ctx: Context): String =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(KEY_SCRATCHPAD, "") ?: ""

    fun setScratchpad(ctx: Context, text: String) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SCRATCHPAD, text).apply()
    }

    /** 上课铃课表（JSON 字符串，格式见 ui/BellSchedule）。 */
    fun getBellSchedule(ctx: Context): String? =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(KEY_BELL_SCHEDULE, null)

    fun setBellSchedule(ctx: Context, json: String) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_BELL_SCHEDULE, json).apply()
    }

    /** 随机数生成历史（按“新→旧”返回，上限 30 条）。 */
    fun getRandomHistory(ctx: Context): List<Int> {
        val raw = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_RANDOM_HISTORY, "") ?: ""
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }.reversed()
    }

    /** 覆写随机数历史（values 按“新→旧”传入，内部反转存储并裁剪到 30 条）。 */
    fun setRandomHistory(ctx: Context, values: List<Int>) {
        val joined = values.take(30).reversed().joinToString(",")
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_RANDOM_HISTORY, joined).apply()
    }

    // ---------- 崩溃日志已查看时间 ----------
    /** 用户上次查看/处理崩溃日志的时间戳；0 表示从未查看。 */
    fun getCrashSeenTs(ctx: Context): Long =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getLong(KEY_CRASH_SEEN_TS, 0L)

    fun setCrashSeenTs(ctx: Context, ts: Long) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_CRASH_SEEN_TS, ts).apply()
    }

    // ---------- 设置：主题模式（跟随系统 / 浅色 / 深色） ----------
    /** "system" / "light" / "dark"，默认跟随系统。 */
    fun getThemeMode(ctx: Context): String =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(KEY_THEME_MODE, "system") ?: "system"

    fun setThemeMode(ctx: Context, mode: String) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME_MODE, mode).apply()
    }

    // ---------- 设置：主题色 ----------
    /** "m3" / "teal" / "gold" / "indigo" / "rose" / "forest"，默认安卓原生 M3 官方取色。 */
    fun getThemeColor(ctx: Context): String =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(KEY_THEME_COLOR, "m3") ?: "m3"

    fun setThemeColor(ctx: Context, color: String) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME_COLOR, color).apply()
    }

    // ---------- 设置：控制台展示风格（列表 / 大方格） ----------
    /** [CONSOLE_STYLE_LIST] / [CONSOLE_STYLE_GRID]，默认列表（历史行为）。 */
    fun getConsoleStyle(ctx: Context): String =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_CONSOLE_STYLE, CONSOLE_STYLE_LIST) ?: CONSOLE_STYLE_LIST

    fun setConsoleStyle(ctx: Context, style: String) {
        val v = if (style == CONSOLE_STYLE_GRID) CONSOLE_STYLE_GRID else CONSOLE_STYLE_LIST
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_CONSOLE_STYLE, v).apply()
    }

    // ---------- 控制台快捷入口（两张可自定义的大方格卡） ----------
    private const val KEY_QUICK_SLOTS = "quick_slots"

    /** 默认两张快捷卡：LED 画板 + 表情图标（与 UI/FeatureCatalog 的 id 对齐）。 */
    val QUICK_SLOTS_DEFAULT = listOf("led", "emoji")

    /**
     * 用户自定义的快捷入口 id（固定两项）。
     * 存储为「|」拼接的字符串，避免 StringSet 顺序丢失导致两张卡互换位置；
     * 读取时按 [com.lingyi.mbos.ui.QuickSlots.normalize] 校验（未知 id 回退默认）。
     */
    fun getQuickSlots(ctx: Context): List<String> {
        val raw = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_QUICK_SLOTS, null)
        val parsed = raw?.split('|')?.map { it.trim() }?.filter { it.isNotEmpty() }
        return com.lingyi.mbos.ui.QuickSlots.normalize(parsed)
    }

    fun setQuickSlots(ctx: Context, ids: List<String>) {
        val v = com.lingyi.mbos.ui.QuickSlots.normalize(ids)
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_QUICK_SLOTS, v.joinToString("|")).apply()
    }

    fun resetQuickSlots(ctx: Context) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_QUICK_SLOTS).apply()
    }

    // ---------- 上次成功连接（回到 App 静默重连，华为运动健康式） ----------
    private const val KEY_LAST_LINK_TYPE = "last_link_type"
    private const val KEY_LAST_LINK_ADDR = "last_link_addr"

    /** 上次成功连接的方式（"ble" / "usb" / "sim"），没有则 null。 */
    fun getLastLinkType(ctx: Context): String? =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_LINK_TYPE, null)?.takeIf { it.isNotBlank() }

    /** 上次成功连接的目标地址（BLE 直连用；USB / 模拟器为 null）。 */
    fun getLastLinkAddress(ctx: Context): String? =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_LINK_ADDR, null)?.takeIf { it.isNotBlank() }

    fun setLastLink(ctx: Context, type: String, address: String?) {
        if (type.isBlank() || type == "sim") return // 模拟器不进「自动重连」记忆
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_LAST_LINK_TYPE, type)
            if (address.isNullOrBlank()) remove(KEY_LAST_LINK_ADDR) else putString(KEY_LAST_LINK_ADDR, address)
            apply()
        }
    }

    fun clearLastLink(ctx: Context) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().apply {
            remove(KEY_LAST_LINK_TYPE)
            remove(KEY_LAST_LINK_ADDR)
            apply()
        }
    }

    /**
     * 当前更新通道（默认取 BuildConfig.DEFAULT_CHANNEL，由 flavor 决定：
     * stable 版默认稳定版、preview 版默认预览版、devtest 版默认开发版）。
     */
    fun getChannel(ctx: Context): String {
        val def = com.lingyi.mbos.BuildConfig.DEFAULT_CHANNEL
        return ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_CHANNEL, def) ?: def
    }

    fun setChannel(ctx: Context, channel: String) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_CHANNEL, channel).apply()
    }

    // ---------- 对外开发者接口开关（允许其它 App 通过本 App 控制已连接的 micro:bit） ----------
    // 默认随 flavor：开发版默认开，稳定版/预览版默认关，需用户在设置里手动开启。
    private const val KEY_EXT_API = "ext_api_enabled"

    fun isExtApiEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_EXT_API, com.lingyi.mbos.BuildConfig.IS_DEVTEST)

    fun setExtApiEnabled(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_EXT_API, on).apply()
    }

    // ---------- 反应测试最佳成绩（毫秒，0 = 暂无记录） ----------
    fun getReactionBestMs(ctx: Context): Int =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getInt(KEY_REACTION_BEST, 0)

    fun setReactionBestMs(ctx: Context, ms: Int) {
        if (ms <= 0) return
        val cur = getReactionBestMs(ctx)
        if (cur in 1 until ms) return // 只记更快的不覆盖
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_REACTION_BEST, ms).apply()
    }

    // ---------- 主题纪元（全局重建信号） ----------
    /** 当前主题纪元；页面创建时记录，onResume 发现变化即重建。 */
    fun getThemeEpoch(ctx: Context): Long =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getLong(KEY_THEME_EPOCH, 0L)

    /** 主题变更后调用：+1 纪元，让返回栈里的旧页面自我重建，避免新旧主题色同框。 */
    fun bumpThemeEpoch(ctx: Context) {
        val sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        sp.edit().putLong(KEY_THEME_EPOCH, sp.getLong(KEY_THEME_EPOCH, 0L) + 1L).apply()
    }

    // ---------- 设置：实时读取间隔 ----------
    /** 轮询间隔毫秒数；非正数或异常值回落 1000。 */
    fun getReadIntervalMs(ctx: Context): Long {
        val v = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getLong(KEY_READ_INTERVAL, 1000L)
        return if (v <= 0L) 1000L else v
    }

    fun setReadIntervalMs(ctx: Context, ms: Long) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_READ_INTERVAL, if (ms <= 0L) 1000L else ms).apply()
    }

    // ---------- 设置：保持屏幕常亮 ----------
    fun isKeepScreenOn(ctx: Context): Boolean =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(KEY_KEEP_SCREEN_ON, false)

    fun setKeepScreenOn(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_KEEP_SCREEN_ON, on).apply()
    }

    // ---------- 设置：触感反馈 ----------
    fun isHapticEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(KEY_HAPTIC, true)

    fun setHapticEnabled(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_HAPTIC, on).apply()
    }

    // ---------- 设置：界面过渡动画 ----------
    /** 过渡动画开关；默认开（新装与老用户都能看到 M3 转场）。 */
    fun isAnimationEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(KEY_ANIM_ENABLED, true)

    fun setAnimationEnabled(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ANIM_ENABLED, on).apply()
    }

    /** 恢复全部外观 / 连接 / 通用设置为默认值（不动连接方式、开发者模式、固件追踪等状态）。 */
    fun resetAppearanceSettings(ctx: Context) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().apply {
            remove(KEY_THEME_MODE)
            remove(KEY_THEME_COLOR)
            remove(KEY_READ_INTERVAL)
            remove(KEY_KEEP_SCREEN_ON)
            remove(KEY_HAPTIC)
            remove(KEY_ANIM_ENABLED)
            remove(KEY_CONSOLE_STYLE)
            apply()
        }
    }

    // ---------- 控制台：宏指令收藏 ----------
    private const val KEY_MACROS = "console_macros_v1"

    /** 读取已收藏的宏指令（去空、去重、上限 20 条）。 */
    fun getMacros(ctx: Context): List<String> {
        val raw = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(KEY_MACROS, null) ?: return emptyList()
        return runCatching {
            org.json.JSONArray(raw).let { arr -> (0 until arr.length()).map { arr.getString(it) } }
        }.getOrDefault(emptyList())
            .map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(20)
    }

    /** 追加一条宏指令（已存在则忽略），返回最新列表。 */
    fun addMacro(ctx: Context, cmd: String): List<String> {
        val list = (getMacros(ctx) + cmd.trim()).filter { it.isNotEmpty() }.distinct().take(20)
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_MACROS, org.json.JSONArray(list).toString()).apply()
        return list
    }

    /** 删除一条宏指令，返回最新列表。 */
    fun removeMacro(ctx: Context, cmd: String): List<String> {
        val list = getMacros(ctx).filter { it != cmd }
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_MACROS, org.json.JSONArray(list).toString()).apply()
        return list
    }

    // ================= 4.1.0-preview：稳定性功能偏好 =================

    /** 意外掉线后自动重连（指数退避）；默认开。 */
    fun isAutoReconnect(ctx: Context): Boolean =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_RECONNECT, true)

    fun setAutoReconnect(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_RECONNECT, on).apply()
        if (!on) AutoReconnect.cancel()
    }

    /** 进入控制台前自动跑启动自检（引导页/主控台）；默认开。 */
    fun isStartupSelfCheck(ctx: Context): Boolean =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(KEY_STARTUP_SELF_CHECK, true)

    fun setStartupSelfCheck(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_STARTUP_SELF_CHECK, on).apply()
    }

    /** 崩溃恢复保护：检测到上次崩溃后，下次启动先进安全模式（不进控制台）；默认开。 */
    fun isCrashGuardEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(KEY_CRASH_GUARD, true)

    fun setCrashGuardEnabled(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_CRASH_GUARD, on).apply()
    }

    /** 协议抓包（调试控制台用）；默认关，开启后由 DebugBus 记录收发。 */
    fun isPacketCapture(ctx: Context): Boolean =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(KEY_PACKET_CAPTURE, false)

    fun setPacketCapture(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PACKET_CAPTURE, on).apply()
        DebugBus.setCaptureEnabled(on)
    }

    /** 调试控制台悬浮窗是否开启（开发者模式入口）；默认关。 */
    fun isDebugConsole(ctx: Context): Boolean =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(KEY_DEBUG_CONSOLE, false)

    fun setDebugConsole(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DEBUG_CONSOLE, on).apply()
    }

    /** ANR 看门狗（主线程卡顿自动记录现场）；默认开。 */
    fun isAnrWatchdog(ctx: Context): Boolean =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(KEY_ANR_WATCHDOG, true)

    fun setAnrWatchdog(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ANR_WATCHDOG, on).apply()
    }

    /** 预览页轮询刷新间隔毫秒（100 / 300 / 1000，默认 300：兼顾实时与省电）。 */
    fun getPreviewPollMs(ctx: Context): Long {
        val v = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getLong(KEY_PREVIEW_POLL_MS, 300L)
        return when (v) {
            100L, 300L, 1000L -> v
            else -> 300L
        }
    }

    fun setPreviewPollMs(ctx: Context, ms: Long) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_PREVIEW_POLL_MS, if (ms == 100L || ms == 1000L) ms else 300L).apply()
    }

    /** 指针页防误触锁（锁定悬浮窗控制按钮，防止口袋误触）；默认关。 */
    fun isPointerLock(ctx: Context): Boolean =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(KEY_POINTER_LOCK, false)

    fun setPointerLock(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_POINTER_LOCK, on).apply()
    }

    /** 时钟校准：用户上次确认/忽略校准提醒的日期（yyyy-MM-dd），避免每天重复打扰。 */
    fun getClockCalibratedDay(ctx: Context): String =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_CLOCK_CALIBRATED_DAY, "") ?: ""

    fun setClockCalibratedDay(ctx: Context, day: String) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_CLOCK_CALIBRATED_DAY, day).apply()
    }

    /** 刷机前信息留存（上次刷机的设备/固件快照 JSON，失败重试时回显）。 */
    fun getFlashLastSnapshot(ctx: Context): String? =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_FLASH_LAST_SNAPSHOT, null)

    fun setFlashLastSnapshot(ctx: Context, json: String) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_FLASH_LAST_SNAPSHOT, json).apply()
    }

    /** 预览页上次读数快照（「键 = 值」多行文本，快照对比用）。 */
    fun getPreviewLastSnapshot(ctx: Context): String? =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_PREVIEW_LAST_SNAPSHOT, null)

    fun setPreviewLastSnapshot(ctx: Context, text: String) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_PREVIEW_LAST_SNAPSHOT, text).apply()
    }

    /** 恢复稳定性设置为默认（不动崩溃保护——它是安全项，只能显式关）。 */
    fun resetStabilitySettings(ctx: Context) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().apply {
            remove(KEY_AUTO_RECONNECT)
            remove(KEY_STARTUP_SELF_CHECK)
            remove(KEY_PACKET_CAPTURE)
            remove(KEY_DEBUG_CONSOLE)
            remove(KEY_ANR_WATCHDOG)
            remove(KEY_PREVIEW_POLL_MS)
            remove(KEY_POINTER_LOCK)
            apply()
        }
    }

    private const val KEY_AUTO_RECONNECT = "auto_reconnect"
    private const val KEY_STARTUP_SELF_CHECK = "startup_self_check"
    private const val KEY_CRASH_GUARD = "crash_guard"
    private const val KEY_PACKET_CAPTURE = "packet_capture"
    private const val KEY_DEBUG_CONSOLE = "debug_console"
    private const val KEY_ANR_WATCHDOG = "anr_watchdog"
    private const val KEY_PREVIEW_POLL_MS = "preview_poll_ms"
    private const val KEY_POINTER_LOCK = "pointer_lock"
    private const val KEY_CLOCK_CALIBRATED_DAY = "clock_calibrated_day"
    private const val KEY_FLASH_LAST_SNAPSHOT = "flash_last_snapshot"
    private const val KEY_PREVIEW_LAST_SNAPSHOT = "preview_last_snapshot"
}

/** 指针高级配置快照：灵敏度 / 按键动作 / 滚动模式 / 死区 / 平滑 / 曲线 / 自动回中。 */
data class PointerProfile(
    val sens: Float = 1.0f,
    val btnA: String = Prefs.POINTER_ACTION_TAP,
    val btnB: String = Prefs.POINTER_ACTION_DRAG,
    val scrollMode: Boolean = false,
    val deadzone: Float = 0f,
    val smoothing: Float = 0f,
    val curve: String = Prefs.POINTER_CURVE_LINEAR,
    val autoRecenter: Boolean = false
)
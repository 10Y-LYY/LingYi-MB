/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 *
 * Voyage 风格视觉皮肤（仅预览版 preview flavor 启用）。
 * 参考「Voyage / 川意工具箱」的界面语言与开屏动效：
 *  - 深青绿主色 #36685D + 浅薄荷背景 + 马卡龙彩色圆形图标 + 大圆角卡片
 *  - 左侧常驻导航栏（首页 / 工具台 / 我的）+ 顶部栏
 *  - 首页：问候语 + 搜索框 + 三张渐变横幅卡 + 常用工具 / 特色功能彩色图标网格
 *  - 工具台：左侧分类栏 + 右侧按分类分组的彩色图标网格
 *  - 我的：登录 / 设备大卡 + 分组设置列表
 *  - 动效：开屏风车 logo 缩放淡入 → 圆形描边绘制 → 圆形揭示(circularReveal)进入主页；
 *          骨架屏 → 内容错落淡入；横幅卡横向滑入；导航切换淡入。
 *
 * 设计原则：本皮肤是「纯视图层」，不持有任何业务状态。所有点击通过回调交回 MainActivity，
 * 复用其 88 项功能分派 / 连接管理 / 更新自检，稳定版与开发版完全不加载本类。
 */
package com.lingyi.mbos.ui

import com.lingyi.mbos.BuildConfig
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.widget.EditText
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.lingyi.mbos.R
import kotlin.math.hypot

/** Voyage 风格皮肤的所有交互回调。 */
interface VoyageActions {
    fun onFeature(id: String)
    fun onReconnect()
    fun onOpenGuide()
    fun onOpenSettings()
    fun onOpenLicense()
    fun onOpenDevTools()
    fun onOpenHelp()
    fun onOpenPreview()
    fun onCheckUpdate()
    fun onDisconnect()
}

/** 设计色板（取自参考截图，与 App 原生 M3 主题解耦）。 */
private object VColor {
    val BRAND = Color.parseColor("#36685D")
    val BRAND_DEEP = Color.parseColor("#28554B")
    val INK = Color.parseColor("#1E2A26")
    val INK_SUB = Color.parseColor("#5E6E68")
    val INK_TER = Color.parseColor("#93A19B")
    val BG = Color.parseColor("#EDF2EE")          // 页面浅薄荷灰底
    val SURFACE = Color.parseColor("#F7FAF7")      // 卡片底
    val SURFACE_2 = Color.parseColor("#E9F1EB")    // 列表行底
    val SELECTED = Color.parseColor("#D2E7E0")     // 左侧栏选中胶囊
    val DIVIDER = Color.parseColor("#DDE8E0")
    val WHITE = Color.parseColor("#FFFFFF")

    // 马卡龙彩色圆形图标：(底色, 前景描画色)
    val PALETTE = arrayOf(
        Color.parseColor("#FBE3D9") to Color.parseColor("#C4745C"), // 珊瑚粉
        Color.parseColor("#D9EEFC") to Color.parseColor("#4C84B8"), // 天蓝
        Color.parseColor("#E4EDBE") to Color.parseColor("#7C9440"), // 黄绿
        Color.parseColor("#D3F1CF") to Color.parseColor("#4E8C63"), // 薄荷绿
        Color.parseColor("#FBE6CB") to Color.parseColor("#C08A42"), // 杏橙
        Color.parseColor("#E6DCF7") to Color.parseColor("#8266B8"), // 淡紫
        Color.parseColor("#C9F2E4") to Color.parseColor("#2E8E76"), // 青绿
        Color.parseColor("#F7DBE4") to Color.parseColor("#B86A86"), // 玫粉
    )

    // 三张首页横幅卡渐变（青绿 / 天蓝 / 薄荷）
    val BANNERS = arrayOf(
        intArrayOf(Color.parseColor("#B7E4CE"), Color.parseColor("#9ED5C4")),
        intArrayOf(Color.parseColor("#BFE1F6"), Color.parseColor("#A6CFF0")),
        intArrayOf(Color.parseColor("#C4E6D2"), Color.parseColor("#A9D8C6")),
    )
}

/**
 * 几何 M 字标（开屏 + 关于页 + 导航栏复用）。纯 Canvas 绘制。
 * 「灵逸 MB」品牌标识，取代原风车图案。
 * 浅色底上用深青绿描边；[onDark] 为真时（关于页品牌深青绿圆底）反白为浅薄荷描边，
 * 避免深色圆把 M 也衬成深色一团、看不清字形。
 */
private class MBLogoView(context: Context, private val onDark: Boolean = false) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (onDark) Color.parseColor("#F7FAF7") else VColor.BRAND_DEEP
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val m = minOf(width, height) / 2f * 0.84f
        if (m <= 0f) return
        // 几何 M：左竖 → 中谷 → 右竖，圆角描边
        val left = cx - m * 0.66f
        val right = cx + m * 0.66f
        val top = cy - m * 0.74f
        val bottom = cy + m * 0.74f
        val valley = cy + m * 0.30f
        paint.strokeWidth = m * (if (onDark) 0.26f else 0.30f)
        val p = Path()
        p.moveTo(left, bottom)
        p.lineTo(left, top)
        p.lineTo(cx, valley)
        p.lineTo(right, top)
        p.lineTo(right, bottom)
        canvas.drawPath(p, paint)
    }
}

/**
 * 圆形描边（开屏「绘制圆」阶段）：用 ValueAnimator 驱动 sweep 角度。
 */
private class RingView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = VColor.BRAND
        style = Paint.Style.STROKE
        strokeWidth = 3f
        alpha = 220
    }
    private val rect = RectF()
    var sweep = 0f
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val r = minOf(width, height) / 2f - 6f
        val cx = width / 2f
        val cy = height / 2f
        rect.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawArc(rect, -90f, sweep, false, paint)
    }
}

/**
 * Voyage 皮肤根视图。由 [VoyageSkin] 构建并 attach 到 MainActivity 的 content 之上。
 */
class VoyageSkin(
    private val ctx: Context,
    private val actions: VoyageActions
) {
    private val dp: (Float) -> Int = { v -> (v * ctx.resources.displayMetrics.density + 0.5f).toInt() }
    private val handler = Handler(Looper.getMainLooper())

    val root: FrameLayout = FrameLayout(ctx).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(VColor.BG)
        // 消费所有触摸：皮肤叠加在旧控制台之上，避免点击穿透触发下层旧控件
        isClickable = true
        isFocusable = true
    }

    // 主页内容层（开屏动画结束后揭示）
    private val mainLayer = FrameLayout(ctx).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(VColor.BG)
    }
    // 开屏层
    private val splashLayer = FrameLayout(ctx).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(Color.parseColor("#F0F5F0"))
    }

    private lateinit var navRail: LinearLayout
    private lateinit var contentHost: FrameLayout
    private lateinit var pageHome: ScrollView
    private lateinit var pageTools: LinearLayout
    private lateinit var pageMine: ScrollView
    private lateinit var pageAbout: ScrollView
    private val navItems = mutableListOf<NavItem>()
    private var currentTab = -1

    // 自适应：是否手机形态（smallestScreenWidthDp < 600 → 底部导航 + 顶部分类胶囊）
    private var isPhone = false
    private lateinit var bottomNav: LinearLayout
    private val bottomItems = mutableListOf<NavItem>()

    private var animPlayed = false
    private var destroyed = false

    // 参与入场动效的视图（必须在 init 构建页面之前初始化）
    private val staggerViews = mutableListOf<View>()
    private val bannerViews = mutableListOf<View>()
    private lateinit var splashRefs: SplashRefs

    // 连接状态（由 MainActivity 推入）
    private lateinit var homeStatusText: TextView
    private lateinit var homeStatusDot: View
    private lateinit var mineStatusText: TextView

    private data class NavItem(
        val view: LinearLayout,
        val capsule: View,
        val label: TextView,
        val icon: ImageView
    )

    private data class SplashRefs(
        val pin: View, val ring: RingView, val titleCol: View, val foot: View
    )

    /** 工具台分类项的可变选中态：背景 drawable + 文字，用于点击后变实色并做颜色过渡。 */
    private data class CatSel(
        val bg: GradientDrawable,
        val label: TextView,
        val count: TextView?,
        val offBg: Int,
        var curBg: Int = offBg,
        var curLabel: Int = VColor.INK,
        var curCount: Int = VColor.INK_TER
    )

    /** 协议文档：标题 + 更新日期 + 若干带小标题的段落。 */
    private data class DocSection(val heading: String, val text: String)
    private data class Doc(
        val title: String, val updated: String, val sections: List<DocSection>
    )

    // ===== 关于页「协议与隐私」正式文本 =====
    // 与已发布版本的功能、AndroidManifest 权限、对外接口严格对应，非占位文案。
    private val docTerms = Doc(
        "用户协议",
        "最近更新：2026 年 11 月 · 适用于灵逸 MB v${BuildConfig.VERSION_NAME}（构建 ${BuildConfig.VERSION_CODE}）",
        listOf(
            DocSection("一、协议的接受",
                "欢迎使用灵逸 MB（micro:bit V2 客户端，下称「本软件」）。本软件由 10Y-灵逸云开发团队开发，用于连接与控制 micro:bit V2 开发板。你安装、启动或继续使用本软件，即表示已阅读并同意本协议；若不同意，请停止使用并卸载本软件。"),
            DocSection("二、软件用途与使用条件",
                "本软件通过蓝牙与板载 MB-OS 助手固件（当前内置 v4.0.0）通信，提供固件刷写、指令下发、图形化工具、调试控制台与设备状态查看等功能。你需要自备 micro:bit V2 硬件、运行 Android 7.0 及以上系统的设备，并按首次使用向导刷入内置固件；未连接硬件时，相关功能不可用。"),
            DocSection("三、开源授权",
                "本软件源码依 MIT License 开源。在保留版权声明与许可声明的前提下，你可以自由使用、复制、修改、合并、发布与分发本软件，亦可用于商业用途。源码包可从官方下载页获取。本软件按「现状」提供，不作任何明示或默示的担保。"),
            DocSection("四、第三方与商标",
                "micro:bit 及相关标识归其权利人所有。本软件为第三方客户端，并非官方产品，与其权利人不存在隶属、授权或背书关系。软件内对编程猫等第三方应用的兼容仅为蓝牙与串口协议互通，不代表任何形式的官方认可。"),
            DocSection("五、使用规范与风险",
                "刷写固件、控制外接设备、开启对外开发者接口等操作由你自行决定并承担相应风险。请在教学、创客与安全规范范围内使用，遵守所在地法律法规，不得利用本软件干扰他人设备或网络。因硬件差异、系统版本、刷写中断或操作不当造成的数据丢失与设备异常，开发者不承担责任。"),
            DocSection("六、对外开发者接口",
                "预览版与开发者测试版提供 ContentProvider 及本机 HTTP 接口（127.0.0.1:8765），供其它应用把指令转发给已连接的开发板。该能力默认受「允许外部应用控制开发板」开关保护，关闭时不响应任何调用；开启后仅在你设备本机生效。请仅授权可信应用。"),
            DocSection("七、协议更新",
                "本协议可能随版本更新而调整，修订后在应用内展示；更新后继续使用本软件，视为接受修订后的条款。"),
            DocSection("八、联系与反馈",
                "意见、问题与建议可通过应用内「帮助与反馈」或官方下载页提供的渠道反馈。10Y-灵逸云开发团队 · Powered by 闫冰峰。")
        )
    )

    private val docPrivacy = Doc(
        "隐私政策",
        "最近更新：2026 年 11 月 · 适用于灵逸 MB v${BuildConfig.VERSION_NAME}（构建 ${BuildConfig.VERSION_CODE}）",
        listOf(
            DocSection("一、概述",
                "我们重视你的隐私。灵逸 MB 不收集、不上传任何个人信息：本软件没有账号体系，不要求提供姓名、手机号、邮箱、通讯录或位置信息，也不包含广告 SDK 与统计埋点。"),
            DocSection("二、本地保存的数据",
                "本软件仅在应用私有目录保存你的设置项、连接偏好与调试日志，用于记住偏好和排查故障。这些数据只留在本机，不会上传；卸载应用或在系统设置中清除应用数据后即被删除。"),
            DocSection("三、权限与用途",
                "• 蓝牙（BLUETOOTH / BLUETOOTH_ADMIN / BLUETOOTH_SCAN / BLUETOOTH_CONNECT）：仅用于发现并连接你的 micro:bit，不会用于定位。BLUETOOTH_SCAN 已声明 neverForLocation，本软件不读取、不记录位置信息。\n• 互联网（INTERNET）：仅用于应用内检查版本更新（读取下载页的版本信息）与本机 HTTP 接口，不向外部服务器发送你的使用数据或个人资料。\n• 前台服务（FOREGROUND_SERVICE 及 connectedDevice / dataSync 类型）：用于在后台保持蓝牙连接与调试控制台稳定运行。\n• 悬浮窗（SYSTEM_ALERT_WINDOW）：仅在你主动开启「组件检视悬浮窗」时用于显示调试信息，关闭后不申请、不使用。\n• 应用内自定义权限（DYNAMIC_RECEIVER_NOT_EXPORTED）：用于应用内部组件间的安全通信，不涉及你的数据。"),
            DocSection("四、对外开发者接口",
                "预览版与开发者测试版可选择开启本机 HTTP 接口与 ContentProvider，使其它应用能向已连接的开发板转发指令。该能力默认关闭，且由「允许外部应用控制开发板」开关控制。我们不会通过该接口收集或外传任何信息。"),
            DocSection("五、第三方组件",
                "本软件使用 AndroidX AppCompat、Material Components、AndroidX Core 等开源组件，均不涉及个人信息采集。完整依赖清单见随附源码包。"),
            DocSection("六、儿童与教学场景",
                "本软件面向 micro:bit 教学与创客场景，不针对未成年人单独收集任何信息；未成年人请在监护人指导下使用。"),
            DocSection("七、你的控制权",
                "你可随时在系统「设置 → 应用 → 权限」中撤销蓝牙、悬浮窗等权限。撤销后相关功能可能受限，但不影响其它功能使用。卸载应用即删除全部本地数据。"),
            DocSection("八、更新与联系",
                "本政策随版本更新，修订后在应用内展示。如有疑问，可通过应用内「帮助与反馈」反馈。")
        )
    )

    private val docPermissions = Doc(
        "权限申请清单",
        "对应 AndroidManifest 声明 · 灵逸 MB v${BuildConfig.VERSION_NAME}",
        listOf(
            DocSection("一、连接与通信",
                "• BLUETOOTH / BLUETOOTH_ADMIN（仅 Android 11 及以下）：旧版本系统上的蓝牙发现与配对。\n• BLUETOOTH_SCAN（Android 12 及以上，neverForLocation）：扫描附近的 micro:bit，明确不用于定位。\n• BLUETOOTH_CONNECT（Android 12 及以上）：与已配对的 micro:bit 建立连接并收发数据。\n• INTERNET：应用内检查版本更新、读取下载页版本信息；以及本机 HTTP 接口（127.0.0.1:8765）。不用于上传使用数据。\n以上权限为连接开发板与检查更新所必需。"),
            DocSection("二、后台运行",
                "• FOREGROUND_SERVICE：启动前台服务。\n• FOREGROUND_SERVICE_CONNECTED_DEVICE：在后台保持与已连接设备的蓝牙会话。\n• FOREGROUND_SERVICE_DATA_SYNC：调试控制台的数据转发。\n这些权限仅在连接设备或使用调试控制台期间生效，用于避免系统中断连接。"),
            DocSection("三、调试与辅助",
                "• SYSTEM_ALERT_WINDOW（悬浮窗）：可选。仅在你主动开启「组件检视悬浮窗」时用于显示视图调试信息，关闭后不会申请或使用。\n• DYNAMIC_RECEIVER_NOT_EXPORTED（应用内自定义权限）：由系统用于应用内部广播的安全隔离，无需你操作。"),
            DocSection("四、本软件不会申请的权限",
                "本软件不申请精确或粗略位置、相机、麦克风、通讯录、短信、电话、外部存储读写、身体传感器、日历等权限，也不申请后台定位。"),
            DocSection("五、权限管理",
                "所有危险权限均在需要使用时由系统弹窗询问，你可随时在「系统设置 → 应用 → 灵逸 MB → 权限」中查看与撤销。撤销后对应功能会提示授权，不会影响其它功能。")
        )
    )

    init {
        root.addView(mainLayer)
        root.addView(splashLayer)
        applyEdgeSpacing()
        buildSplash()
        buildMain()
        // 兜底：若入场动画因任何原因未播完，3 秒后强制内容可见，避免白屏
        root.postDelayed({ if (!destroyed) ensureContentVisible() }, 3000L)
        root.post { if (!destroyed && !animPlayed) playIntro() }
    }

    /**
     * 上下留白适配：不强求消除系统状态栏 / 导航栏 / 挖孔区域的白框，
     * 而是按实际 inset 把所有内容（导航、标题、卡片、署名）整体向内收，
     * 使文字不再紧贴上下白框；带保底值与上限，避免不同设备差异过大。
     */
    private fun applyEdgeSpacing() {
        // 保底留白：即使部分窗口模式下 insets 回调未触发，内容也不会贴着上下白框
        mainLayer.setPadding(0, dp(22f), 0, dp(18f))
        splashLayer.setPadding(0, dp(22f), 0, dp(18f))
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cut = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val topPx = maxOf(sys.top, cut.top)
            val bottomPx = maxOf(sys.bottom, cut.bottom)
            val top = (topPx + dp(14f)).coerceIn(dp(20f), dp(52f))
            val bottom = (bottomPx + dp(10f)).coerceIn(dp(16f), dp(44f))
            if (mainLayer.paddingTop != top || mainLayer.paddingBottom != bottom) {
                mainLayer.setPadding(0, top, 0, bottom)
                splashLayer.setPadding(0, top, 0, bottom)
            }
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    /** 动画丢失时的可见性兜底：所有参与动效的视图强制回到可见状态。 */
    private fun ensureContentVisible() {
        (staggerViews + bannerViews).forEach {
            it.alpha = 1f
            it.translationY = 0f
            it.translationX = 0f
        }
        if (::splashRefs.isInitialized) splashLayer.visibility = View.GONE
    }

    // ==================== 通用构件 ====================

    private fun roundRect(color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun gradientBanner(colors: IntArray): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
            cornerRadius = dp(20f).toFloat()
        }

    private fun tv(
        text: String,
        sizeSp: Float,
        color: Int,
        bold: Boolean = false
    ): TextView = TextView(ctx).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        includeFontPadding = false
    }

    /** 马卡龙彩色圆形图标（FG 用矢量图标 tint）。 */
    private fun colorIcon(iconRes: Int, pairIndex: Int, sizeDp: Float = 46f): FrameLayout {
        val (bg, fg) = VColor.PALETTE[pairIndex % VColor.PALETTE.size]
        val size = dp(sizeDp)
        val wrap = FrameLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(size, size)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bg)
            }
        }
        val iv = AppCompatImageView(ctx).apply {
            setImageResource(iconRes)
            imageTintList = android.content.res.ColorStateList.valueOf(fg)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val inner = dp(sizeDp * 0.5f)
        wrap.addView(iv, FrameLayout.LayoutParams(inner, inner, Gravity.CENTER))
        return wrap
    }

    /** 带按压回弹的点击。 */
    private fun View.pressable(onClick: () -> Unit) {
        isClickable = true
        isFocusable = true
        setOnClickListener {
            animate().scaleX(0.96f).scaleY(0.96f).setDuration(70L)
                .withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(130L)
                        .setInterpolator(OvershootInterpolator(1.4f)).start()
                }.start()
            onClick()
        }
    }

    // ==================== 开屏动效 ====================

    private fun buildSplash() {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val boxSize = dp(84f)
        val pin = MBLogoView(ctx)
        splashLayer.addView(pin, FrameLayout.LayoutParams(boxSize, boxSize, Gravity.CENTER))
        pin.alpha = 0f
        pin.scaleX = 0.5f
        pin.scaleY = 0.5f

        val ringSize = dp(150f)
        val ring = RingView(ctx)
        splashLayer.addView(ring, FrameLayout.LayoutParams(ringSize, ringSize, Gravity.CENTER))
        ring.alpha = 0f

        // 标题区：logo 下方
        val title = tv("灵逸 MB", 30f, VColor.BRAND_DEEP, bold = true)
        val sub = tv("micro:bit V2 客户端", 14f, VColor.INK_SUB)
        val slogan = tv("启程所需，皆在手边", 13f, VColor.INK_TER)
        val titleCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            alpha = 0f
            addView(title)
            addView(sub, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2f) })
            addView(slogan, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6f) })
        }
        splashLayer.addView(titleCol, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_HORIZONTAL or Gravity.TOP
        ).apply { topMargin = dp(0f) })
        // 用 post 精确定位到中点偏下
        splashLayer.post {
            val lp = titleCol.layoutParams as FrameLayout.LayoutParams
            lp.topMargin = splashLayer.height / 2 + dp(96f)
            titleCol.layoutParams = lp
        }

        // 底部版权
        val foot = tv("© 10Y-灵逸云开发团队  ·  Powered by 闫冰峰", 11f, VColor.INK_TER)
        splashLayer.addView(foot, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
        ).apply { bottomMargin = dp(28f) })
        foot.alpha = 0f

        // 引用保存，供动画阶段使用（col 仅为占位容器，不参与布局）
        splashRefs = SplashRefs(pin, ring, titleCol, foot)
        col.visibility = View.GONE
        splashLayer.addView(col)
    }

    private fun playIntro() {
        animPlayed = true
        val r = splashRefs
        val dec = DecelerateInterpolator(1.2f)

        // ① 风车 logo 缩放淡入（带轻微回弹）
        r.pin.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(520L).setInterpolator(OvershootInterpolator(1.1f)).start()
        // ② 标题 / 版权淡入 + 上移
        r.titleCol.animate().alpha(1f).translationY(0f).setStartDelay(260L)
            .setDuration(420L).setInterpolator(dec).start()
        r.titleCol.translationY = dp(14f).toFloat()
        r.foot.animate().alpha(1f).setStartDelay(420L).setDuration(400L).start()

        // ③ 圆形描边绘制：0 → 360
        handler.postDelayed({
            if (destroyed) return@postDelayed
            r.ring.alpha = 1f
            ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 720L
                interpolator = dec
                addUpdateListener { r.ring.sweep = it.animatedValue as Float }
                start()
            }
        }, 620L)

        // ④ 圆形揭示进入主页（以屏幕中心为圆心）
        handler.postDelayed({
            if (destroyed) return@postDelayed
            revealMain()
        }, 1500L)
    }

    private fun revealMain() {
        val cx = root.width / 2
        val cy = root.height / 2
        val endRadius = hypot(root.width.toFloat(), root.height.toFloat())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            runCatching {
                val anim = ViewAnimationUtils.createCircularReveal(
                    mainLayer, cx, cy, 0f, endRadius
                )
                anim.duration = 620L
                anim.interpolator = DecelerateInterpolator(1.1f)
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (!destroyed) splashLayer.visibility = View.GONE
                    }
                })
                mainLayer.visibility = View.VISIBLE
                anim.start()
            }.onFailure {
                splashLayer.animate().alpha(0f).setDuration(420L)
                    .withEndAction { splashLayer.visibility = View.GONE }.start()
            }
        } else {
            splashLayer.animate().alpha(0f).setDuration(420L)
                .withEndAction { splashLayer.visibility = View.GONE }.start()
        }
        // 骨架屏 → 内容错落淡入
        handler.postDelayed({ if (!destroyed) revealHomeContent() }, 260L)
        // 横幅卡横向滑入
        handler.postDelayed({ if (!destroyed) slideInBanners() }, 120L)
    }

    private fun revealHomeContent() {
        staggerViews.forEachIndexed { i, v ->
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay((i * 55L).coerceAtMost(500L))
                .setDuration(420L).setInterpolator(DecelerateInterpolator(1.1f)).start()
        }
    }

    private fun slideInBanners() {
        bannerViews.forEachIndexed { i, v ->
            v.animate().alpha(1f).translationX(0f)
                .setStartDelay(120L + i * 90L)
                .setDuration(460L).setInterpolator(DecelerateInterpolator(1.1f)).start()
        }
    }

    /** 注册参与「错落淡入」的视图（初始隐藏）。 */
    private fun View.stagger() {
        alpha = 0f
        translationY = dp(18f).toFloat()
        staggerViews.add(this)
    }

    /** 注册参与「横向滑入」的横幅卡（初始隐藏）。 */
    private fun View.slideIn() {
        alpha = 0f
        translationX = dp(60f).toFloat()
        bannerViews.add(this)
    }

    // ==================== 主界面骨架 ====================

    /**
     * 主界面骨架：根据屏幕尺寸自适应。
     *  - 大屏（smallestScreenWidthDp ≥ 600）：左侧常驻导航栏 + 右侧内容（沿用 Voyage 桌面风格）。
     *  - 手机（< 600）：顶部内容 + 底部导航栏（标准手机交互，把横向空间全留给内容）。
     */
    private fun buildMain() {
        isPhone = ctx.resources.configuration.smallestScreenWidthDp < 600

        contentHost = FrameLayout(ctx).apply {
            setBackgroundColor(VColor.BG)
        }

        if (isPhone) {
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            // 垂直布局：weight 作用于高度，故 width=MATCH_PARENT、height=0、weight=1
            col.addView(contentHost, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

            // 底部导航栏上方的细分隔线
            val navDivider = View(ctx).apply { setBackgroundColor(VColor.DIVIDER) }
            col.addView(navDivider, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1f)))

            buildBottomNav()
            col.addView(bottomNav, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            mainLayer.addView(col)
        } else {
            val wide = ctx.resources.configuration.smallestScreenWidthDp >= 900
            val railWidth = if (wide) dp(210f) else dp(172f)
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            navRail = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#E4EDE6"))
                val p = dp(14f)
                setPadding(p, dp(22f), p, dp(16f))
            }
            row.addView(navRail, LinearLayout.LayoutParams(railWidth, ViewGroup.LayoutParams.MATCH_PARENT))
            row.addView(contentHost, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            mainLayer.addView(row)
            buildNavRail(wide = true)
        }

        buildPages()
        switchTab(0, animate = false)
    }

    private fun buildNavRail(wide: Boolean) {
        // 品牌头
        val brand = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8f), dp(6f), dp(8f), dp(22f))
        }
        val pin = MBLogoView(ctx)
        brand.addView(pin, LinearLayout.LayoutParams(dp(30f), dp(30f)))
        if (wide) {
            brand.addView(tv("灵逸 MB", 17f, VColor.BRAND_DEEP, bold = true),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(10f)
                })
        }
        navRail.addView(brand)

        fun addNav(icon: Int, label: String, tab: Int) {
            val capsule = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12f), dp(11f), dp(12f), dp(11f))
                background = roundRect(Color.TRANSPARENT, 14f)
            }
            val iv = AppCompatImageView(ctx).apply {
                setImageResource(icon)
                imageTintList = android.content.res.ColorStateList.valueOf(VColor.INK_SUB)
            }
            capsule.addView(iv, LinearLayout.LayoutParams(dp(21f), dp(21f)))
            val tvLabel = if (wide) tv(label, 14f, VColor.INK_SUB, bold = true) else
                tv(label, 10f, VColor.INK_SUB, bold = true)
            if (wide) {
                capsule.addView(tvLabel, LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                ).apply { marginStart = dp(12f) })
            } else {
                capsule.orientation = LinearLayout.VERTICAL
                capsule.gravity = Gravity.CENTER
                val lp = iv.layoutParams as LinearLayout.LayoutParams
                iv.layoutParams = lp
                capsule.addView(tvLabel, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(3f) })
            }
            capsule.pressable { switchTab(tab, animate = true) }
            navRail.addView(capsule, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6f) })
            navItems.add(NavItem(capsule, capsule, tvLabel, iv))
        }

        addNav(R.drawable.ic_grid, "首页", 0)
        addNav(R.drawable.ic_tool, "工具台", 1)
        addNav(R.drawable.ic_info, "我的固件", 2)
        addNav(R.drawable.ic_settings, "关于", 3)

        // 底部版本
        val ver = tv("v${BuildConfig.VERSION_NAME}", 11f, VColor.INK_TER)
        navRail.addView(ver, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10f); marginStart = dp(8f) })
        val spacer = View(ctx)
        navRail.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))
        val foot = tv("10Y-灵逸云", 10f, VColor.INK_TER)
        navRail.addView(foot, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(8f); bottomMargin = dp(6f) })
    }

    /** 手机形态：底部导航栏（图标 + 文字，等宽四项）。 */
    private fun buildBottomNav() {
        bottomNav = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#E4EDE6"))
            val p = dp(8f)
            setPadding(p, p, p, p + dp(6f))
        }

        fun addItem(icon: Int, label: String, tab: Int) {
            val item = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4f), dp(6f), dp(4f), dp(6f))
                background = roundRect(Color.TRANSPARENT, 14f)
            }
            val iv = AppCompatImageView(ctx).apply {
                setImageResource(icon)
                imageTintList = android.content.res.ColorStateList.valueOf(VColor.INK_SUB)
            }
            item.addView(iv, LinearLayout.LayoutParams(dp(23f), dp(23f)))
            val tvLabel = tv(label, 11f, VColor.INK_SUB, bold = true)
            item.addView(tvLabel, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3f) })
            item.pressable { switchTab(tab, animate = true) }
            bottomNav.addView(item, LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            bottomItems.add(NavItem(item, item, tvLabel, iv))
        }

        addItem(R.drawable.ic_grid, "首页", 0)
        addItem(R.drawable.ic_tool, "工具台", 1)
        addItem(R.drawable.ic_info, "我的固件", 2)
        addItem(R.drawable.ic_settings, "关于", 3)
    }

    private fun switchTab(tab: Int, animate: Boolean) {
        if (tab == currentTab) return
        currentTab = tab
        val pages = arrayOf<View>(pageHome, pageTools, pageMine, pageAbout)
        pages.forEachIndexed { i, p ->
            if (i == tab) {
                p.visibility = View.VISIBLE
                if (animate) {
                    p.alpha = 0f
                    p.translationY = dp(14f).toFloat()
                    p.animate().alpha(1f).translationY(0f).setDuration(300L)
                        .setInterpolator(DecelerateInterpolator(1.1f)).start()
                } else {
                    p.alpha = 1f
                    p.translationY = 0f
                }
            } else {
                p.visibility = View.GONE
            }
        }
        navItems.forEachIndexed { i, n ->
            val on = i == tab
            n.capsule.background = roundRect(
                if (on) VColor.SELECTED else Color.TRANSPARENT, 14f
            )
            n.label.setTextColor(if (on) VColor.BRAND_DEEP else VColor.INK_SUB)
            n.icon.imageTintList = android.content.res.ColorStateList.valueOf(
                if (on) VColor.BRAND else VColor.INK_SUB
            )
        }
        // 底部导航栏（手机形态）同步选中态
        bottomItems.forEachIndexed { i, n ->
            val on = i == tab
            n.capsule.background = roundRect(
                if (on) VColor.SELECTED else Color.TRANSPARENT, 14f
            )
            n.label.setTextColor(if (on) VColor.BRAND_DEEP else VColor.INK_SUB)
            n.icon.imageTintList = android.content.res.ColorStateList.valueOf(
                if (on) VColor.BRAND else VColor.INK_SUB
            )
        }
    }

    // ==================== 页面 ====================

    private fun buildPages() {
        pageHome = buildHomePage()
        pageTools = buildToolsPage()
        pageMine = buildMinePage()
        pageAbout = buildAboutPage()
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        contentHost.addView(pageHome, lp)
        contentHost.addView(pageTools, lp)
        contentHost.addView(pageMine, lp)
        contentHost.addView(pageAbout, lp)
        listOf(pageTools, pageMine, pageAbout).forEach { it.visibility = View.GONE }
    }

    /** 顶部栏（汉堡 / 标题 / 右侧图标）。 */
    private fun topBar(title: String, centerTitle: Boolean): LinearLayout {
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(26f), dp(20f), dp(26f), dp(12f))
        }
        val menu = AppCompatImageView(ctx).apply { setImageResource(R.drawable.ic_menu) }
        menu.imageTintList = android.content.res.ColorStateList.valueOf(VColor.INK)
        if (!isPhone) {
            bar.addView(menu, LinearLayout.LayoutParams(dp(22f), dp(22f)))
        }
        val t = tv(title, if (centerTitle) 18f else 22f, VColor.INK, bold = true)
        bar.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            if (centerTitle) marginStart = 0 else marginStart = dp(16f)
        })
        if (centerTitle) {
            t.gravity = Gravity.CENTER
        }
        val search = AppCompatImageView(ctx).apply { setImageResource(R.drawable.ic_more_vert) }
        search.imageTintList = android.content.res.ColorStateList.valueOf(VColor.INK)
        search.pressable { openSearch() }
        bar.addView(search, LinearLayout.LayoutParams(dp(20f), dp(20f)))
        return bar
    }

    // 全屏搜索覆盖层：列出全部工具，输入实时过滤，点击即跳转对应功能。
    // 注意：不能把 alpha 动画加在 overlay 上——那会让面板、输入框、结果列表一起半透明，
    // 主界面从背后透出，看起来像「搜索界面显示错乱」。透明度只用于面板入场/退出。
    private fun openSearch() {
        val overlay = FrameLayout(ctx).apply {
            isClickable = true
            setBackgroundColor(Color.TRANSPARENT)
        }

        // 不透明面板：底色与页面 / 系统栏一致，整屏连续，不产生上下色带
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(VColor.BG)
            setPadding(0, dp(18f), 0, dp(24f))
            alpha = 0f
            translationY = -dp(16f).toFloat()
        }
        panel.setOnClickListener { } // 消费点击，避免穿透到遮罩关闭

        // 搜索输入行
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18f), dp(4f), dp(18f), dp(10f))
        }
        val input = EditText(ctx).apply {
            hint = "搜索 ${FeatureCatalog.FEATURES.size} 个工具"
            textSize = 15f
            setTextColor(VColor.INK)
            setHintTextColor(VColor.INK_TER)
            background = null
            setSingleLine()
        }
        val cancel = tv("取消", 14f, VColor.BRAND, bold = true).apply {
            setPadding(dp(14f), dp(6f), dp(4f), dp(6f))
        }
        bar.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(cancel)
        panel.addView(bar)

        // 分隔线
        val divider = View(ctx).apply { setBackgroundColor(VColor.DIVIDER) }
        panel.addView(divider, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
            setMargins(dp(18f), dp(6f), dp(18f), 0)
        })

        // 结果列表
        val listScroll = ScrollView(ctx).apply { setPadding(dp(8f), dp(8f), dp(8f), 0) }
        val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        listScroll.addView(list)
        panel.addView(listScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        fun render(q: String) {
            list.removeAllViews()
            val key = q.trim().lowercase()
            val items = if (key.isEmpty()) FeatureCatalog.FEATURES
            else FeatureCatalog.FEATURES.filter {
                it.title.lowercase().contains(key) || it.id.lowercase().contains(key)
            }
            items.forEachIndexed { i, f ->
                val r = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16f), dp(11f), dp(16f), dp(11f))
                }
                r.addView(colorIcon(f.icon, i % VColor.PALETTE.size, 38f),
                    LinearLayout.LayoutParams(dp(38f), dp(38f)))
                r.addView(tv(f.title, 14f, VColor.INK), LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(13f) })
                r.pressable { actions.onFeature(f.id); removeSearch(overlay) }
                list.addView(r)
            }
            if (items.isEmpty()) {
                list.addView(tv("无匹配结果", 13f, VColor.INK_TER).apply { gravity = Gravity.CENTER },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(24f) })
            }
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(s: CharSequence?, p1: Int, p2: Int, p3: Int) {
                render(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        render("")

        cancel.pressable { removeSearch(overlay) }
        overlay.pressable { removeSearch(overlay) }
        overlay.addView(panel, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(overlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // 只让面板淡入 + 轻微下滑，overlay 自身始终保持完全不透明
        panel.animate().alpha(1f).translationY(0f).setDuration(200L).start()
        input.requestFocus()
    }

    private fun removeSearch(overlay: View) {
        overlay.animate().alpha(0f).setDuration(180).withEndAction {
            root.removeView(overlay)
        }.start()
    }

    /**
     * 协议文档弹层：纯代码 Voyage 风格卡片，居中、可滚动、点遮罩或「×」关闭。
     * 与搜索层一样叠加在 root 上，且不改 VoyageActions 接口，稳定版 / 开发版零影响。
     */
    private fun openDoc(doc: Doc) {
        val dm = ctx.resources.displayMetrics
        val maxW = minOf(dm.widthPixels - dp(36f), dp(560f))
        val maxH = (dm.heightPixels * 0.80f).toInt()

        val overlay = FrameLayout(ctx).apply {
            isClickable = true
            setBackgroundColor(Color.argb(110, 30, 42, 38))
            alpha = 0f
        }
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = roundRect(VColor.SURFACE, 22f)
            setPadding(dp(22f), dp(18f), dp(22f), dp(8f))
        }
        card.setOnClickListener { } // 消费卡片内点击，避免点正文就关闭

        val head = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(tv(doc.title, 18f, VColor.INK, bold = true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val close = tv("×", 23f, VColor.INK_SUB, bold = true).apply {
            setPadding(dp(10f), dp(0f), dp(2f), dp(6f))
        }
        head.addView(close)
        card.addView(head)
        card.addView(tv(doc.updated, 11.5f, VColor.INK_TER),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(3f) })

        val line = View(ctx).apply { setBackgroundColor(VColor.DIVIDER) }
        card.addView(line, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
            topMargin = dp(13f); bottomMargin = dp(2f)
        })

        val scroll = ScrollView(ctx)
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6f), 0, dp(22f))
        }
        doc.sections.forEach { s ->
            body.addView(tv(s.heading, 13f, VColor.BRAND_DEEP, bold = true),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14f) })
            body.addView(tv(s.text, 12.5f, VColor.INK_SUB).apply {
                setLineSpacing(dp(3f).toFloat(), 1f)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(5f) })
        }
        scroll.addView(body)
        card.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        close.pressable { closeDoc(overlay) }
        overlay.pressable { closeDoc(overlay) }
        overlay.addView(card, FrameLayout.LayoutParams(maxW, maxH, Gravity.CENTER))
        root.addView(overlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // 淡入到完全不透明；仅卡片做轻微上滑，遮罩不做常驻半透明
        overlay.animate().alpha(1f).setDuration(180L).start()
        card.translationY = dp(20f).toFloat()
        card.animate().translationY(0f).setDuration(220L).start()
    }

    private fun closeDoc(overlay: View) {
        overlay.animate().alpha(0f).setDuration(160L).withEndAction {
            root.removeView(overlay)
        }.start()
    }

    private fun sectionTitle(text: String, iconRes: Int): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(26f), dp(20f), dp(26f), dp(10f))
        }
        val ic = colorIcon(iconRes, 6, 26f)
        row.addView(ic, LinearLayout.LayoutParams(dp(26f), dp(26f)))
        row.addView(tv(text, 15f, VColor.INK, bold = true), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ).apply { marginStart = dp(9f) })
        return row
    }

    /** 彩色圆形图标 + 下方文字（工具网格单元）。 */
    private fun iconCell(iconRes: Int, label: String, pairIndex: Int, onClick: () -> Unit): LinearLayout {
        val cell = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4f), dp(8f), dp(4f), dp(8f))
        }
        cell.addView(colorIcon(iconRes, pairIndex, 48f),
            LinearLayout.LayoutParams(dp(48f), dp(48f)))
        cell.addView(TextView(ctx).apply {
            text = label
            textSize = 11.5f
            setTextColor(VColor.INK)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(dp(72f), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(7f)
        })
        cell.pressable(onClick)
        return cell
    }

    /** 把一批单元格按列数排成网格（自适应宽度）。 */
    private fun iconGrid(cells: List<LinearLayout>, columns: Int): LinearLayout {
        val grid = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(4f), dp(16f), dp(4f))
        }
        cells.chunked(columns).forEach { chunk ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START
            }
            chunk.forEach { c ->
                row.addView(c, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            // 补齐空位保持对齐
            repeat(columns - chunk.size) {
                row.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
            }
            grid.addView(row)
        }
        return grid
    }

    private fun columns(): Int {
        val sw = ctx.resources.configuration.smallestScreenWidthDp
        return when {
            sw >= 1000 -> 9
            sw >= 800 -> 7
            sw >= 600 -> 6
            else -> 4
        }
    }

    // ---------- 首页 ----------
    private fun buildHomePage(): ScrollView {
        val scroll = ScrollView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFillViewport = true
            setBackgroundColor(VColor.BG)
        }
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(col, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        col.addView(topBar("灵逸 MB", centerTitle = true))

        // 问候 + 日期
        val greet = tv(greeting(), 27f, VColor.INK, bold = true)
        val date = tv(todayText(), 13f, VColor.INK_SUB)
        val gh = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(26f), dp(10f), dp(26f), dp(14f))
        }
        gh.addView(greet); gh.addView(date)
        gh.stagger()
        col.addView(gh)

        // 搜索框
        val search = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundRect(Color.parseColor("#DDE7DE"), 26f)
            setPadding(dp(18f), dp(13f), dp(18f), dp(13f))
        }
        val sIcon = AppCompatImageView(ctx).apply { setImageResource(R.drawable.ic_more_vert) }
        sIcon.imageTintList = android.content.res.ColorStateList.valueOf(VColor.INK_SUB)
        search.addView(sIcon, LinearLayout.LayoutParams(dp(19f), dp(19f)))
        val sText = tv("搜索 ${FeatureCatalog.FEATURES.size} 个工具", 14f, VColor.INK_SUB)
        search.addView(sText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(12f)
        })
        search.pressable { openSearch() }
        val searchWrap = LinearLayout(ctx).apply {
            setPadding(dp(26f), 0, dp(26f), 0)
        }
        searchWrap.addView(search, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        searchWrap.stagger()
        col.addView(searchWrap)

        // 三张渐变横幅卡（横向滑动）
        val hs = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(dp(26f), dp(8f), dp(26f), dp(8f))
            clipToPadding = false
        }
        val bannerRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val bannerDefs = listOf(
            Triple("一站式效率", "常用工具，触手可及", "显示、声音与传感器的全部能力，集中管理，搜索后即可使用。") to "浏览工具",
            Triple("新功能", "无线指针，抬手即控", "把 micro:bit 变成空中鼠标，倾斜移动、A 键点击、B 键拖动。") to "立即体验",
            Triple("固件管理", "版本状态，清晰可查", "连接即播开机动画，一条 VERSION 指令读回板载固件版本。") to "查看连接",
        )
        val cardW = dp(268f)
        bannerDefs.forEachIndexed { i, (def, cta) ->
            val (tag, title, desc) = def
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                background = gradientBanner(VColor.BANNERS[i % VColor.BANNERS.size])
                setPadding(dp(18f), dp(18f), dp(18f), dp(18f))
            }
            val badge = FrameLayout(ctx).apply {
                background = roundRect(Color.parseColor("#4C7E70"), 12f)
            }
            val bIv = AppCompatImageView(ctx).apply {
                setImageResource(
                    when (i) { 0 -> R.drawable.ic_grid; 1 -> R.drawable.ic_pointer; else -> R.drawable.ic_refresh }
                )
                imageTintList = android.content.res.ColorStateList.valueOf(VColor.WHITE)
            }
            badge.addView(bIv, FrameLayout.LayoutParams(dp(16f), dp(16f), Gravity.CENTER))
            card.addView(badge, LinearLayout.LayoutParams(dp(30f), dp(30f)))
            card.addView(tv(tag, 12f, VColor.BRAND_DEEP, bold = true),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16f) })
            card.addView(tv(title, 19f, VColor.INK, bold = true),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(3f) })
            card.addView(tv(desc, 12f, Color.parseColor("#3B544C")),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6f) })
            val btn = tv("$cta  →", 13f, VColor.WHITE, bold = true).apply {
                background = roundRect(VColor.BRAND_DEEP, 20f)
                setPadding(dp(16f), dp(9f), dp(16f), dp(9f))
            }
            card.addView(btn, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14f) })
            card.pressable {
                when (i) {
                    0 -> switchTab(1, animate = true)
                    1 -> actions.onFeature("pointer")
                    else -> actions.onFeature("scriptinfo")
                }
            }
            card.slideIn()
            bannerRow.addView(card, LinearLayout.LayoutParams(cardW, dp(190f)).apply {
                if (i > 0) marginStart = dp(14f)
            })
        }
        hs.addView(bannerRow)
        col.addView(hs)

        // 设备状态条（骨架屏→淡入的核心块）
        val statusCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundRect(VColor.SURFACE, 16f)
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
        }
        homeStatusDot = View(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#B9C7BE"))
            }
        }
        statusCard.addView(homeStatusDot, LinearLayout.LayoutParams(dp(9f), dp(9f)))
        homeStatusText = tv("未连接 · 点按重新连接", 13f, VColor.INK_SUB)
        statusCard.addView(homeStatusText, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ).apply { marginStart = dp(10f) })
        val reconnect = tv("重新连接", 12f, VColor.BRAND, bold = true)
        statusCard.addView(reconnect)
        statusCard.pressable { actions.onReconnect() }
        val statusWrap = LinearLayout(ctx).apply { setPadding(dp(26f), dp(4f), dp(26f), 0) }
        statusWrap.addView(statusCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        statusWrap.stagger()
        col.addView(statusWrap)

        // 常用工具
        val quickHead = sectionTitle("常用工具", R.drawable.ic_grid)
        val customize = tv("自定义  →", 12f, VColor.INK_SUB)
        quickHead.addView(customize)
        customize.pressable { actions.onOpenSettings() }
        quickHead.stagger()
        col.addView(quickHead)

        val quickIds = listOf("led", "scroll", "emoji", "tone", "melody", "dashboard", "dice", "preview")
        val quickCells = quickIds.mapIndexed { i, id ->
            val f = FeatureCatalog.FEATURES.first { it.id == id }
            iconCell(f.icon, f.title, i) { actions.onFeature(id) }
        }
        val quickGrid = iconGrid(quickCells, columns())
        quickGrid.stagger()
        col.addView(quickGrid)

        // 特色功能
        val featHead = sectionTitle("特色功能", R.drawable.ic_bricks)
        val all = tv("全部工具  →", 12f, VColor.INK_SUB)
        featHead.addView(all)
        all.pressable { switchTab(1, animate = true) }
        featHead.stagger()
        col.addView(featHead)

        val featIds = listOf("animation", "piano", "marquee", "bigdigit", "level", "compassrose",
            "morsecodes", "blackjack").filter { id ->
            FeatureCatalog.FEATURES.any { it.id == id }
        }.ifEmpty { listOf("animation", "piano", "marquee", "bigdigit") }
        val featCells = featIds.mapIndexed { i, id ->
            val f = FeatureCatalog.FEATURES.first { it.id == id }
            iconCell(f.icon, f.title, i + 3) { actions.onFeature(id) }
        }
        val featGrid = iconGrid(featCells, columns())
        featGrid.stagger()
        col.addView(featGrid)

        col.addView(View(ctx), LinearLayout.LayoutParams(1, dp(28f)))
        return scroll
    }

    // ---------- 工具台 ----------
    private fun buildToolsPage(): LinearLayout {
        val page = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(VColor.BG)
        }
        page.addView(topBar("工具台", centerTitle = false))

        // 右：分组网格
        val rightScroll = ScrollView(ctx)
        val rightCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8f), dp(8f), dp(16f), dp(24f))
        }
        rightScroll.addView(rightCol)

        val cats = FeatureCatalog.Category.entries
        // 「最近使用 / 最近更新」两个虚拟分组（对标参考图）
        val recent = FeatureCatalog.FEATURES.take(9)
        val updated = FeatureCatalog.FEATURES.takeLast(10)

        val groupViews = mutableListOf<View>()

        fun addGroup(title: String, colorIdx: Int, features: List<FeatureCatalog.Feature>) {
            // 分组胶囊标题
            val head = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = roundRect(VColor.PALETTE[colorIdx % VColor.PALETTE.size].first, 14f)
                setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
            }
            head.addView(tv(title, 13f, VColor.INK, bold = true))
            head.addView(tv(" ${features.size}", 12f, VColor.INK_SUB))
            val headWrap = LinearLayout(ctx).apply {
                setPadding(dp(10f), dp(10f), dp(10f), dp(4f))
            }
            headWrap.addView(head, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            rightCol.addView(headWrap)

            val cells = features.mapIndexed { i, f ->
                iconCell(f.icon, f.title, colorIdx + i) { actions.onFeature(f.id) }
            }
            val grid = iconGrid(cells, columns().coerceAtLeast(4))
            rightCol.addView(grid)
            groupViews.add(headWrap)
        }

        addGroup("最近使用", 6, recent)
        addGroup("最近更新", 2, updated)
        cats.forEachIndexed { i, c ->
            addGroup(c.title, i, FeatureCatalog.byCategory(c))
        }

        // 分组导航定义（大屏侧栏 / 手机胶囊共用同一份）
        val catDefs = mutableListOf<Pair<String, View>>()
        catDefs.add("最近使用" to groupViews[0])
        catDefs.add("最近更新" to groupViews[1])
        cats.forEachIndexed { i, c -> catDefs.add(c.title to groupViews[i + 2]) }

        // 统一的选中态：点击把目标项变为「实色」(深青绿底 + 白字)，其余恢复，
        // 并用 ValueAnimator 做 180ms 颜色渐变过渡（对齐 MIUI 参考图的柔和转场）。
        val catSels = mutableListOf<CatSel>()
        var selectedCat = 0
        val onCountColor = Color.parseColor("#CFE3DA")
        fun tinted(from: Int, to: Int, apply: (Int) -> Unit) {
            if (from == to) { apply(to); return }
            ValueAnimator.ofArgb(from, to).apply {
                duration = 180L
                addUpdateListener { apply(it.animatedValue as Int) }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) { apply(to) }
                })
                start()
            }
        }
        fun refreshSelection() {
            catSels.forEachIndexed { i, s ->
                val on = i == selectedCat
                val toBg = if (on) VColor.BRAND else s.offBg
                val toLabel = if (on) VColor.WHITE else VColor.INK
                val toCount = if (on) onCountColor else VColor.INK_TER
                tinted(s.curBg, toBg) { c -> s.bg.setColor(c); s.bg.invalidateSelf() }
                tinted(s.curLabel, toLabel) { c -> s.label.setTextColor(c) }
                s.count?.let { cnt -> tinted(s.curCount, toCount) { c -> cnt.setTextColor(c) } }
                s.label.setTypeface(null, if (on) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                s.curBg = toBg; s.curLabel = toLabel; s.curCount = toCount
            }
        }
        fun selectCat(idx: Int, target: View) {
            selectedCat = idx
            refreshSelection()
            rightScroll.post { rightScroll.smoothScrollTo(0, target.top) }
        }

        if (isPhone) {
            // 手机：顶部横向滚动分类胶囊，下方全宽网格
            val chipScroll = HorizontalScrollView(ctx).apply {
                isHorizontalScrollBarEnabled = false
                setPadding(dp(16f), dp(10f), dp(16f), dp(2f))
                clipToPadding = false
            }
            val chipRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            catDefs.forEachIndexed { idx, (name, target) ->
                val bg = roundRect(VColor.SURFACE, 16f)
                val chip = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
                    background = bg
                }
                val label = tv(name, 13f, VColor.INK, bold = true)
                chip.addView(label)
                catSels.add(CatSel(bg, label, null, VColor.SURFACE))
                chip.pressable { selectCat(idx, target) }
                chipRow.addView(chip, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8f) })
            }
            chipScroll.addView(chipRow)
            page.addView(chipScroll)
            page.addView(rightScroll, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        } else {
            // 大屏：左侧分类栏 + 右侧网格
            val body = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
                )
            }
            page.addView(body)
            val catScroll = ScrollView(ctx).apply {
                setBackgroundColor(Color.parseColor("#E9F0EA"))
            }
            val catCol = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12f), dp(12f), dp(12f), dp(12f))
            }
            catScroll.addView(catCol)
            body.addView(catScroll, LinearLayout.LayoutParams(dp(150f), ViewGroup.LayoutParams.MATCH_PARENT))

            catDefs.forEachIndexed { idx, (name, target) ->
                val bg = roundRect(Color.TRANSPARENT, 12f)
                val item = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12f), dp(11f), dp(10f), dp(11f))
                    background = bg
                }
                val label = tv(name, 13f, VColor.INK)
                item.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                val cnt = when (idx) {
                    0 -> recent.size; 1 -> updated.size
                    else -> FeatureCatalog.byCategory(cats[idx - 2]).size
                }
                val count = tv("$cnt", 12f, VColor.INK_TER)
                item.addView(count)
                catSels.add(CatSel(bg, label, count, Color.TRANSPARENT))
                item.pressable { selectCat(idx, target) }
                catCol.addView(item, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(3f) })
            }

            body.addView(rightScroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        // 初始默认选中「最近使用」，直接应用实色（无动画）
        refreshSelection()
        return page
    }

    // ---------- 我的 ----------
    private fun buildMinePage(): ScrollView {
        val scroll = ScrollView(ctx).apply { isFillViewport = true }
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22f), 0, dp(22f), dp(30f))
        }
        scroll.addView(col)
        col.addView(topBar("我的固件", centerTitle = false))

        // 登录 / 设备大卡
        val hero = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#CDE9DC"), Color.parseColor("#B6DECF")))
                .apply { cornerRadius = dp(22f).toFloat() }
            setPadding(dp(22f), dp(24f), dp(22f), dp(22f))
        }
        val avatar = FrameLayout(ctx).apply {
            background = roundRect(VColor.BRAND, 40f)
        }
        val avatarIv = AppCompatImageView(ctx).apply {
            setImageResource(R.drawable.ic_info)
            imageTintList = android.content.res.ColorStateList.valueOf(VColor.WHITE)
        }
        avatar.addView(avatarIv, FrameLayout.LayoutParams(dp(22f), dp(22f), Gravity.CENTER))
        hero.addView(avatar, LinearLayout.LayoutParams(dp(52f), dp(52f)))
        mineStatusText = tv("尚未连接 micro:bit", 19f, VColor.BRAND_DEEP, bold = true)
        hero.addView(mineStatusText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12f) })
        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(16f), 0, 0)
        }
        val login = tv("连接设备", 14f, VColor.WHITE, bold = true).apply {
            background = roundRect(VColor.BRAND, 22f)
            setPadding(dp(22f), dp(10f), dp(22f), dp(10f))
        }
        login.pressable { actions.onReconnect() }
        btnRow.addView(login)
        val register = tv("连接向导", 14f, VColor.BRAND_DEEP, bold = true).apply {
            setPadding(dp(20f), dp(10f), dp(20f), dp(10f))
        }
        register.pressable { actions.onOpenGuide() }
        btnRow.addView(register)
        hero.addView(btnRow)
        hero.stagger()
        col.addView(hero)

        // 意见反馈
        val feedback = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundRect(VColor.SURFACE, 16f)
            setPadding(dp(14f), dp(13f), dp(14f), dp(13f))
        }
        feedback.addView(colorIcon(R.drawable.ic_edit, 1, 34f),
            LinearLayout.LayoutParams(dp(34f), dp(34f)))
        val fbTexts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), 0, 0, 0)
        }
        fbTexts.addView(tv("意见反馈", 14f, VColor.INK, bold = true))
        fbTexts.addView(tv("遇到问题或有建议，随时告诉我们", 11.5f, VColor.INK_SUB))
        feedback.addView(fbTexts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        feedback.addView(tv("›", 20f, VColor.INK_TER))
        feedback.pressable { actions.onOpenHelp() }
        val fbWrap = LinearLayout(ctx).apply { setPadding(0, dp(14f), 0, 0) }
        fbWrap.addView(feedback, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        fbWrap.stagger()
        col.addView(fbWrap)

        // 应用分组
        col.addView(sectionTitle("应用", R.drawable.ic_grid))
        val listCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = roundRect(VColor.SURFACE, 18f)
            setPadding(dp(6f), dp(4f), dp(6f), dp(4f))
        }
        fun row(icon: Int, colorIdx: Int, title: String, desc: String, onClick: () -> Unit) {
            val r = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12f), dp(11f), dp(12f), dp(11f))
            }
            r.addView(colorIcon(icon, colorIdx, 38f), LinearLayout.LayoutParams(dp(38f), dp(38f)))
            val texts = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(13f), 0, 0, 0)
            }
            texts.addView(tv(title, 14f, VColor.INK, bold = true))
            texts.addView(tv(desc, 11.5f, VColor.INK_SUB))
            r.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            r.addView(tv("›", 20f, VColor.INK_TER))
            r.pressable(onClick)
            listCard.addView(r)
        }
        row(R.drawable.ic_refresh, 6, "检查更新", "进入主控台即自动检测新版本") { actions.onCheckUpdate() }
        row(R.drawable.ic_grid, 1, "快捷入口", "自定义两张常用功能卡片") { actions.onOpenSettings() }
        row(R.drawable.ic_settings, 2, "设置", "主题、动效与连接偏好") { actions.onOpenSettings() }
        row(R.drawable.ic_code, 4, "开发者工具箱", "组件检视 / 指令台 / 接口自检") { actions.onOpenDevTools() }
        row(R.drawable.ic_help, 5, "固件与帮助", "刷机脚本与连接说明") { actions.onOpenHelp() }
        row(R.drawable.ic_power, 0, "断开连接", "断开当前 micro:bit 连接") { actions.onDisconnect() }
        val listWrap = LinearLayout(ctx).apply { setPadding(0, 0, 0, 0) }
        listWrap.addView(listCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        listWrap.stagger()
        col.addView(listWrap)
        return scroll
    }

    // ---------- 关于 ----------
    private fun buildAboutPage(): ScrollView {
        val scroll = ScrollView(ctx).apply { isFillViewport = true }
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(26f), 0, dp(26f), dp(34f))
        }
        scroll.addView(col)
        col.addView(topBar("关于", centerTitle = false))

        // 品牌头（对标参考图左侧大 logo 区）
        val brand = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(10f), dp(26f), dp(10f), dp(18f))
        }
        val logoBg = FrameLayout(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(VColor.BRAND)
            }
        }
        logoBg.addView(MBLogoView(ctx, onDark = true),
            FrameLayout.LayoutParams(dp(62f), dp(62f), Gravity.CENTER))
        brand.addView(logoBg, LinearLayout.LayoutParams(dp(88f), dp(88f)))
        brand.addView(tv("灵逸 MB", 30f, VColor.INK, bold = true),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14f) })
        brand.addView(tv("micro:bit V2 客户端", 15f, VColor.INK_SUB, bold = true))
        brand.addView(tv("启程所需，皆在手边", 12.5f, VColor.INK_TER),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4f) })
        val pills = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(14f), 0, 0)
        }
        val p1 = tv("v${BuildConfig.VERSION_NAME}", 12f, VColor.BRAND_DEEP, bold = true).apply {
            background = roundRect(VColor.SELECTED, 14f)
            setPadding(dp(12f), dp(5f), dp(12f), dp(5f))
        }
        val p2 = tv("构建 ${BuildConfig.VERSION_CODE}", 12f, VColor.INK_SUB, bold = true).apply {
            background = roundRect(Color.parseColor("#DCE6DE"), 14f)
            setPadding(dp(12f), dp(5f), dp(12f), dp(5f))
        }
        pills.addView(p1)
        pills.addView(p2, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(8f) })
        brand.addView(pills)
        brand.stagger()
        col.addView(brand)

        // 应用分组
        col.addView(sectionTitle("应用", R.drawable.ic_grid))
        val infoCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = roundRect(VColor.SURFACE, 18f)
            setPadding(dp(6f), dp(4f), dp(6f), dp(4f))
        }
        fun infoRow(icon: Int, ci: Int, title: String, value: String = "", onClick: () -> Unit = {}) {
            val r = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12f), dp(11f), dp(12f), dp(11f))
            }
            r.addView(colorIcon(icon, ci, 36f), LinearLayout.LayoutParams(dp(36f), dp(36f)))
            r.addView(tv(title, 14f, VColor.INK, bold = true),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(13f)
                })
            if (value.isNotEmpty()) {
                r.addView(tv(value, 12.5f, VColor.INK_SUB))
            }
            r.addView(tv("›", 20f, VColor.INK_TER))
            r.pressable(onClick)
            infoCard.addView(r)
        }
        infoRow(R.drawable.ic_refresh, 6, "应用更新") { actions.onCheckUpdate() }
        infoRow(R.drawable.ic_info, 1, "应用信息", BuildConfig.VERSION_NAME)
        infoRow(R.drawable.ic_code, 4, "开源许可", "MIT") { actions.onOpenLicense() }
        infoRow(R.drawable.ic_more_vert, 0, "固件版本", "4.0.0") { actions.onOpenHelp() }
        val infoWrap = LinearLayout(ctx)
        infoWrap.addView(infoCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        infoWrap.stagger()
        col.addView(infoWrap)

        // 协议与隐私
        col.addView(sectionTitle("协议与隐私", R.drawable.ic_help))
        val policyCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = roundRect(VColor.SURFACE, 18f)
            setPadding(dp(6f), dp(4f), dp(6f), dp(4f))
        }
        fun policyRow(icon: Int, ci: Int, title: String, doc: Doc) {
            val r = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12f), dp(11f), dp(12f), dp(11f))
            }
            r.addView(colorIcon(icon, ci, 36f), LinearLayout.LayoutParams(dp(36f), dp(36f)))
            r.addView(tv(title, 14f, VColor.INK, bold = true),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(13f)
                })
            r.addView(tv("›", 20f, VColor.INK_TER))
            r.pressable { openDoc(doc) }
            policyCard.addView(r)
        }
        policyRow(R.drawable.ic_help, 1, "用户协议", docTerms)
        policyRow(R.drawable.ic_settings, 3, "隐私政策", docPrivacy)
        policyRow(R.drawable.ic_grid, 5, "权限申请清单", docPermissions)
        val policyWrap = LinearLayout(ctx)
        policyWrap.addView(policyCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        policyWrap.stagger()
        col.addView(policyWrap)

        // 署名（用户指定：Powered by 闫冰峰 / © 10Y-灵逸云开发团队）
        val sign = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(30f), 0, 0)
        }
        sign.addView(tv("Powered by 闫冰峰", 13f, VColor.INK_SUB, bold = true))
        sign.addView(tv("© 10Y-灵逸云开发团队", 12f, VColor.INK_TER),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6f) })
        col.addView(sign)
        return scroll
    }

    // ==================== 外部状态推入 ====================

    /** MainActivity 在 renderConnection 时调用，刷新首页 / 我的页的连接状态。 */
    fun onConnection(connected: Boolean, detail: String) {
        if (!::homeStatusText.isInitialized || destroyed) return
        val label = when {
            !connected -> "未连接 · 点按重新连接"
            detail.isBlank() -> "已连接 micro:bit V2"
            else -> detail
        }
        homeStatusText.text = label
        homeStatusDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (connected) Color.parseColor("#2E9E6B") else Color.parseColor("#B9C7BE"))
        }
        if (::mineStatusText.isInitialized) {
            mineStatusText.text = if (connected) "已连接 micro:bit V2" else "尚未连接 micro:bit"
        }
    }

    /** 返回键：非首页先回首页，返回 true 表示已消费。 */
    fun onBack(): Boolean {
        return if (currentTab != 0) {
            switchTab(0, animate = true)
            true
        } else false
    }

    fun destroy() {
        destroyed = true
        handler.removeCallbacksAndMessages(null)
    }

    // ==================== 文案工具 ====================

    private fun greeting(): String {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when {
            h < 6 -> "凌晨好"
            h < 11 -> "早上好"
            h < 14 -> "中午好"
            h < 18 -> "下午好"
            else -> "晚上好"
        }
    }

    private fun todayText(): String {
        val c = java.util.Calendar.getInstance()
        val m = c.get(java.util.Calendar.MONTH) + 1
        val d = c.get(java.util.Calendar.DAY_OF_MONTH)
        val w = when (c.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.SUNDAY -> "星期日"
            java.util.Calendar.MONDAY -> "星期一"
            java.util.Calendar.TUESDAY -> "星期二"
            java.util.Calendar.WEDNESDAY -> "星期三"
            java.util.Calendar.THURSDAY -> "星期四"
            java.util.Calendar.FRIDAY -> "星期五"
            else -> "星期六"
        }
        return "${m}月${d}日 $w"
    }
}

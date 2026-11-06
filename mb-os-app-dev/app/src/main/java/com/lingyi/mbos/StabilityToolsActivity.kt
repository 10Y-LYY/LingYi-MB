/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.R as MR
import com.lingyi.mbos.ble.ConnectionHub
import com.lingyi.mbos.databinding.ActivityStabilityToolsBinding
import com.lingyi.mbos.ui.M3Kit
import com.lingyi.mbos.ui.StabilityFeatures
import com.lingyi.mbos.ui.Transitions
import com.lingyi.mbos.util.themeColor

/**
 * 稳定性工具二级页（4.1.1-preview）：引导页「问号」入口的目的地。
 *
 * 把散在引导页上的稳定性工具收拢到一页，按「先看进度（连接向导）→
 * 再逐项排查（自检 / 常见问题 / 固件对比）→ 仍然不行怎么办」的顺序组织，
 * 每项都是真实可用的排查工具而非摆设。
 */
class StabilityToolsActivity : BaseActivity() {

    private lateinit var b: ActivityStabilityToolsBinding

    /** 从引导页透传的板载固件版本（未连接时为 null）。 */
    private var mbos: String? = null
    private var mpy: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityStabilityToolsBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.toolbar.setNavigationOnClickListener { finish() }
        mbos = intent.getStringExtra(EXTRA_MBOS)
        mpy = intent.getStringExtra(EXTRA_MPY)

        b.cardSelfCheck.setOnClickListener { StabilityFeatures.showSelfCheckDialog(this) }
        b.cardFaq.setOnClickListener { StabilityFeatures.showFaqDialog(this) }
        b.cardFwCompare.setOnClickListener {
            StabilityFeatures.showFirmwareCompareDialog(this, mbos, mpy)
        }

        updateGuideSteps(ConnectionHub.isConnected())
        // M3 Expressive：整页可点击元素统一按压反馈
        M3Kit.applyPressFeedback(b.root)
        // M3 Expressive：内容卡片依次弹簧入场
        (b.scroll.getChildAt(0) as? ViewGroup)?.let { Transitions.springInCards(it, 60L) }
    }

    override fun onResume() {
        super.onResume()
        // 连接状态可能在外界变化（返回本页时刷新向导进度）
        updateGuideSteps(ConnectionHub.isConnected())
    }

    // ---------- 连接向导步骤条（从引导页迁入，逻辑不变） ----------

    /**
     * 程序化构建三步向导：① 准备（权限/蓝牙/线缆）② 连接 ③ 进入。
     * 每步一个圆形序号 + 标题 + 说明，已完成步为实色主色、当前步高亮描边、
     * 未到达步弱化，连不上时一眼看出卡在哪一步。
     */
    private fun updateGuideSteps(connectedNow: Boolean) {
        val ready = step1Ready()
        data class Step(val title: String, val desc: String, val state: Int)
        val steps = listOf(
            Step("准备", "权限 / 蓝牙 / OTG 线", if (ready) STATE_DONE else STATE_ACTIVE),
            Step("连接", "配对并连接 micro:bit", if (connectedNow) STATE_DONE else if (ready) STATE_ACTIVE else STATE_PENDING),
            Step("进入", "进入控制台开始使用", if (connectedNow) STATE_ACTIVE else STATE_PENDING)
        )
        val circleSize = M3Kit.dp(this, 30)
        b.llSteps.removeAllViews()
        steps.forEachIndexed { index, step ->
            if (index > 0) {
                TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                        weight = 1f
                    }
                    text = "——"
                    gravity = Gravity.CENTER
                    textSize = 11f
                    setTextColor(themeColor(MR.attr.colorOutline))
                }.also { b.llSteps.addView(it) }
            }
            val col = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    weight = 1.4f
                }
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            val circle = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(circleSize, circleSize)
                gravity = Gravity.CENTER
                text = "${index + 1}"
                textSize = 13f
                setTextIsSelectable(false)
                when (step.state) {
                    STATE_DONE -> {
                        background = android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.OVAL
                            setColor(themeColor(MR.attr.colorPrimary))
                        }
                        setTextColor(themeColor(MR.attr.colorOnPrimary))
                        text = "✓"
                    }
                    STATE_ACTIVE -> {
                        background = android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.OVAL
                            setColor(themeColor(MR.attr.colorPrimaryContainer))
                            setStroke(M3Kit.dp(this@StabilityToolsActivity, 2), themeColor(MR.attr.colorPrimary))
                        }
                        setTextColor(themeColor(MR.attr.colorPrimary))
                    }
                    else -> {
                        background = android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.OVAL
                            setColor(themeColor(MR.attr.colorSurfaceVariant))
                            setStroke(M3Kit.dp(this@StabilityToolsActivity, 1), themeColor(MR.attr.colorOutline))
                        }
                        setTextColor(themeColor(MR.attr.colorOnSurfaceVariant))
                    }
                }
            }
            val title = TextView(this).apply {
                setPadding(M3Kit.dp(this@StabilityToolsActivity, 2), M3Kit.dp(this@StabilityToolsActivity, 8), M3Kit.dp(this@StabilityToolsActivity, 2), 0)
                text = step.title
                textSize = 13f
                setTextColor(
                    themeColor(
                        if (step.state == STATE_PENDING) MR.attr.colorOnSurfaceVariant else MR.attr.colorOnSurface
                    )
                )
                if (step.state != STATE_PENDING) setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            val desc = TextView(this).apply {
                setPadding(M3Kit.dp(this@StabilityToolsActivity, 2), M3Kit.dp(this@StabilityToolsActivity, 2), M3Kit.dp(this@StabilityToolsActivity, 2), 0)
                gravity = Gravity.CENTER_HORIZONTAL
                text = step.desc
                textSize = 10f
                setTextColor(themeColor(MR.attr.colorOnSurfaceVariant))
            }
            col.addView(circle); col.addView(title); col.addView(desc)
            b.llSteps.addView(col)
        }
    }

    /** 第 1 步「准备」是否就绪：蓝牙已开且蓝牙权限已授予。 */
    private fun step1Ready(): Boolean {
        val btOn = runCatching {
            getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter?.isEnabled == true
        }.getOrDefault(false)
        if (!btOn) return false
        val perms = if (android.os.Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    companion object {
        const val EXTRA_MBOS = "extra_stab_mbos"
        const val EXTRA_MPY = "extra_stab_mpy"
        private const val STATE_PENDING = 0
        private const val STATE_ACTIVE = 1
        private const val STATE_DONE = 2
    }
}

/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.util

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/**
 * 界面元素树捕获：把当前顶层 Activity 的视图树拍平为可读文本，
 * 供「问题上报」与「调试控制台 → 界面元素树」使用。
 *
 * 输出每行一个控件：缩进表达层级，含 资源id / 类名 / 文案 / 可点击性。
 * 上限 [MAX_NODES] 个节点，防止巨型树（WebView 等）刷爆报告。
 */
object ViewTreeCapture {

    private const val MAX_NODES = 220

    /** 当前 resumed 的顶层 Activity；没有则返回 null。 */
    fun topActivity(): Activity? = ActivityTopHolder.top

    /** 捕获顶层 Activity 的视图树；无 Activity 时返回提示文本。 */
    fun capture(): String {
        val activity = topActivity() ?: return "(当前无可见 Activity，可能在悬浮窗/服务上下文)"
        val root = activity.window?.decorView
            ?: return "(无法获取 ${activity.javaClass.simpleName} 的视图树)"
        val sb = StringBuilder()
        sb.append("页面：").append(activity.javaClass.simpleName).append('\n')
        val count = intArrayOf(0)
        walk(root, 0, sb, count)
        if (count[0] >= MAX_NODES) sb.append("…（节点超上限，已截断）\n")
        return sb.toString()
    }

    private fun walk(view: View, depth: Int, sb: StringBuilder, count: IntArray) {
        if (count[0] >= MAX_NODES) return
        count[0]++
        val pad = "  ".repeat(depth.coerceAtMost(24))
        val idName = runCatching {
            if (view.id != View.NO_ID) view.resources.getResourceEntryName(view.id) else null
        }.getOrNull()
        val text = (view as? TextView)?.text?.toString()?.replace('\n', ' ')?.take(40)
        sb.append(pad)
            .append(view.javaClass.simpleName)
        if (!idName.isNullOrBlank()) sb.append(" #").append(idName)
        if (view.isClickable) sb.append(" [可点击]")
        if (view.isEnabled.not()) sb.append(" [禁用]")
        if (view.visibility != View.VISIBLE) sb.append(" [不可见]")
        if (!text.isNullOrBlank()) sb.append(" 「").append(text).append('」')
        sb.append('\n')
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                walk(view.getChildAt(i), depth + 1, sb, count)
            }
        }
    }
}

/**
 * 顶层 Activity 持有者：由 BaseActivity.onResume/onPause 维护。
 * 放这里而不是文件级 object，是为了让 ViewTreeCapture 与 DebugReport 共用同一事实来源。
 */
internal object ActivityTopHolder {
    @Volatile
    var top: Activity? = null
}

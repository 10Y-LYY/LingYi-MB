/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.util

import android.app.Activity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 操作轨迹：进程级单例，为「问题上报」提供「用户当时在做什么」的上下文。
 *
 * 两个来源：
 *  1. 页面事件：[record] 显式记录（BaseActivity.onCreate 记录页面创建，
 *     各功能打开/关闭时记录）；
 *  2. 点击坐标：[captureTouch] 由 BaseActivity.dispatchTouchEvent 在每次
 *     ACTION_UP 时调用——把坐标回打到当前视图树上找出被点的控件，
 *     描述其 id / 类名 / 文案。零侵入：不改任何业务控件的事件监听。
 *
 * 环形缓冲 [MAX_ENTRIES] 条，线程安全（UI 线程写，悬浮窗线程读）。
 */
object ActionTrail {

    private const val MAX_ENTRIES = 120

    private val lock = ReentrantLock()
    private val ring = ArrayDeque<String>()

    private val fmt = ThreadLocal.withInitial {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    }

    /** 显式记录一条操作（action=做什么，target=对象，可为空）。 */
    fun record(action: String, target: String = "") {
        val line = "${fmt.get()!!.format(Date())} $action" + if (target.isEmpty()) "" else " | $target"
        lock.withLock {
            ring.addLast(line)
            while (ring.size > MAX_ENTRIES) ring.removeFirst()
        }
    }

    /**
     * 由 Activity.dispatchTouchEvent 调用：记录一次点击命中的控件。
     * 找不到可识别控件时记录坐标本身（如空白区域）。
     */
    fun captureTouch(activity: Activity, event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_UP) return
        val x = event.rawX
        val y = event.rawY
        val desc = runCatching { describeAt(activity.window.decorView, x, y) }.getOrNull()
        record(
            if (desc == null) "点击空白区域" else "点击控件",
            desc ?: "(%.0f,%.0f)".format(x, y)
        )
    }

    /** 在视图树上找坐标处最深/最具体的可识别控件并描述。 */
    private fun describeAt(root: View, x: Float, y: Float): String? {
        val hit = findViewAt(root, x, y) ?: return null
        return describe(hit)
    }

    private fun findViewAt(view: View, x: Float, y: Float): View? {
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        val left = loc[0].toFloat()
        val top = loc[1].toFloat()
        val right = left + view.width
        val bottom = top + view.height
        if (view.width == 0 || view.height == 0) return null
        if (x < left || x > right || y < top || y > bottom) return null
        if (view is ViewGroup) {
            for (i in view.childCount - 1 downTo 0) {
                val child = view.getChildAt(i)
                if (child.visibility != View.VISIBLE) continue
                findViewAt(child, x, y)?.let { return it }
            }
        }
        return view
    }

    /** 描述一个控件：资源 id 名 > 文案 > 类名。 */
    fun describe(view: View): String {
        val idName = runCatching {
            if (view.id != View.NO_ID) {
                view.resources.getResourceEntryName(view.id)
            } else null
        }.getOrNull()
        val text = (view as? TextView)?.text?.toString()?.take(40)
        val cls = view.javaClass.simpleName
        return buildList {
            if (!idName.isNullOrBlank()) add("#$idName")
            add(cls)
            if (!text.isNullOrBlank()) add("「$text」")
        }.joinToString(" ")
    }

    /** 最近 [n] 条轨迹（正序）。 */
    fun recent(n: Int = MAX_ENTRIES): List<String> = lock.withLock { ring.toList().takeLast(n) }

    fun clear() = lock.withLock { ring.clear() }
}

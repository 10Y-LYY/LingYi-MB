/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

/**
 * 功能直达桥：让悬浮窗调试控制台（Service 上下文）能触发控制台里的 88 个功能。
 *
 * 分派逻辑只在 MainActivity.onFuncClick 一份，这里不重复实现——
 * MainActivity 启动时注册 opener，悬浮窗经此间接调用，避免两套入口漂移。
 */
object FeatureOpener {

    @Volatile
    private var opener: ((String) -> Unit)? = null

    /** MainActivity.onCreate 注册；onDestroy 注销。 */
    fun register(open: (String) -> Unit) {
        opener = open
    }

    fun unregister(open: (String) -> Unit) {
        if (opener === open) opener = null
    }

    /** 触发一个功能；未注册（未打开过控制台）时返回 false。 */
    fun open(id: String): Boolean {
        val f = opener ?: return false
        runCatching { f(id) }
        return true
    }

}

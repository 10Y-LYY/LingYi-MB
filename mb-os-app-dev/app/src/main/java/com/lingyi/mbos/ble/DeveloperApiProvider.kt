/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ble

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.lingyi.mbos.R
import com.lingyi.mbos.ui.Protocol
import com.lingyi.mbos.util.Prefs

/**
 * 对外开发者接口的核心分发逻辑（与 ContentProvider 解耦，便于单测）。
 *
 * 设计目标：让同机上的其它 App 能够把指令转发给「已连接在本 App 上的 micro:bit」。
 * 安全：是否真正生效由应用内开关 [Prefs.isExtApiEnabled] 决定（默认关，开发版默认开）；
 * 未开启时任何调用都返回 ok=false，不会触碰开发板。
 */
object ExternalApi {

    /** 一次调用的结果。 */
    data class Result(val ok: Boolean, val reason: String, val sent: String)

    /**
     * @param ctx 上下文（用于读取开关）
     * @param method "send"（转发指令）或 "status"（查询连接状态）
     * @param arg send 时的指令文本（如 "SCROLL:hello"）
     * @param isConnected 当前是否已连接开发板
     * @param send 实际发送函数（转发给 [com.lingyi.mbos.ble.ConnectionHub]）
     */
    fun dispatch(
        ctx: Context,
        method: String,
        arg: String?,
        isConnected: () -> Boolean,
        send: (String) -> Boolean
    ): Result {
        if (!Prefs.isExtApiEnabled(ctx)) {
            return Result(false, ctx.getString(R.string.ext_api_disabled), "")
        }
        return when (method) {
            "status" -> Result(true, if (isConnected()) "已连接" else "未连接", "")
            "send" -> {
                val cmd = arg ?: ""
                if (cmd.isBlank()) return Result(false, "指令为空", "")
                if (!isConnected()) return Result(false, "未连接开发板，无法发送", "")
                val line = Protocol.custom(cmd)
                val ok = send(line)
                Result(ok, if (ok) "已发送" else "发送失败", line.trim())
            }
            else -> Result(false, "未知方法：$method（支持 send / status）", "")
        }
    }

    /** 把 [Result] 打包成 ContentProvider.call 返回的 Bundle。 */
    fun toBundle(r: Result): Bundle = Bundle().apply {
        putBoolean("ok", r.ok)
        putString("reason", r.reason)
        putString("sent", r.sent)
    }
}

/**
 * 导出给其它 App 的 ContentProvider。
 *
 * - authority：`${applicationId}.external`（随 flavor 变化，例如
 *   `com.lingyi.mbos.external` / `com.lingyi.mbos.dev.external`）。
 * - 调用方式（Kotlin/Java/curl 见开发者文档）：
 *   `content://<auth>/send?cmd=SCROLL:hello` 或 `call("send", "SCROLL:hello", null)`，
 *   返回 Bundle{ ok, reason, sent }。
 * - 无论何种方式，都必须先在本 App「设置 → 允许外部应用控制开发板」开启开关。
 */
class DeveloperApiProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = context ?: return Bundle().apply {
            putBoolean("ok", false); putString("reason", "provider 未绑定上下文")
        }
        val r = ExternalApi.dispatch(
            ctx, method, arg,
            isConnected = { ConnectionHub.isConnected() },
            send = { ConnectionHub.send(it) }
        )
        return ExternalApi.toBundle(r)
    }

    // 以下为 ContentProvider 强制实现的占位方法（本接口只用 call）。
    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?
    ): Int = 0
}

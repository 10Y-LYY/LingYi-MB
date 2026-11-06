/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.util

import android.os.Handler
import android.os.Looper
import com.lingyi.mbos.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * 真实联网检查更新：拉取线上版本文件，与本地 BuildConfig.VERSION_CODE 比较。
 * 当线上 versionCode 大于本地时判定为「有更新」——这是真检查，不是写死的假提示。
 *
 * 双版本通道：
 *  - 稳定版（默认）：拉 [VERSION_URL]（version.json），全体用户优先稳定版；
 *  - 预览版（beta）：用户在「更新通道」下拉里选中预览版后拉 [VERSION_BETA_URL]
 *    （version-beta.json），让稳定版用户可主动升级到预览版。
 * 两个文件 schema 完全一致（扁平结构），互不影响、向后兼容。
 */
object UpdateChecker {

    /** 稳定版版本文件地址（与发布站点同目录，随站点一起发布）。 */
    const val VERSION_URL = "https://af8c03ac486ac1f4a.app.workbuddy.host/version.json"

    /** 预览版（Beta）版本文件地址：仅在更新通道选为「预览版」时检查。 */
    const val VERSION_BETA_URL = "https://af8c03ac486ac1f4a.app.workbuddy.host/version-beta.json"

    /** 开发者测试版版本文件地址：仅在更新通道选为「开发版」时检查。 */
    const val VERSION_DEV_URL = "https://af8c03ac486ac1f4a.app.workbuddy.host/version-dev.json"

    /**
     * 发布页地址：「前往更新」的跳转目标。
     * 注意不是 [VERSION_URL]——那个是给程序读的版本文件，浏览器打开只会看到
     * 一串原始 JSON；发布页才有版本说明与 APK 下载按钮。
     */
    const val PAGE_URL = "https://af8c03ac486ac1f4a.app.workbuddy.host/"

    const val CHANNEL_STABLE = "stable"
    const val CHANNEL_PREVIEW = "preview"
    const val CHANNEL_DEV = "dev"

    /** 通道的中文展示名（稳定版 / 预览版 / 开发版）。 */
    fun channelLabel(channel: String): String = when (channel) {
        CHANNEL_PREVIEW -> "预览版"
        CHANNEL_DEV -> "开发版"
        else -> "稳定版"
    }

    data class Info(
        val available: Boolean,
        val remoteVersionCode: Int,
        val remoteVersionName: String,
        val apkUrl: String,
        val pageUrl: String,
        val notes: String,
        val publishedAt: String,
        /** 该信息所属通道：[CHANNEL_STABLE] / [CHANNEL_PREVIEW]。 */
        val channel: String = CHANNEL_STABLE,
        /** 联网失败时非空，用于上层提示。 */
        val error: String? = null
    )

    fun currentVersionCode(): Int = BuildConfig.VERSION_CODE
    fun currentVersionName(): String = BuildConfig.VERSION_NAME

    /** 纯函数：远端版本号是否比本地新（便于单测）。 */
    fun isNewer(remoteVersionCode: Int): Boolean = remoteVersionCode > BuildConfig.VERSION_CODE

    /**
     * 后台联网检查，结果在主线程回调。
     * 失败时返回 available=false 且 error 非空，上层据此提示「检查失败」而非「有更新」。
     *
     * @param channel 检查的通道：[CHANNEL_STABLE] / [CHANNEL_PREVIEW] / [CHANNEL_DEV]，
     *                默认取 [BuildConfig.DEFAULT_CHANNEL]（由构建 flavor 决定）。
     */
    fun check(channel: String = BuildConfig.DEFAULT_CHANNEL, onResult: (Info) -> Unit) {
        val (url, ch) = when (channel) {
            CHANNEL_PREVIEW -> VERSION_BETA_URL to CHANNEL_PREVIEW
            CHANNEL_DEV -> VERSION_DEV_URL to CHANNEL_DEV
            else -> VERSION_URL to CHANNEL_STABLE
        }
        thread(name = "update-check") {
            val info = try {
                fetch(url, ch)
            } catch (e: Exception) {
                Info(
                    available = false,
                    remoteVersionCode = -1,
                    remoteVersionName = "",
                    apkUrl = "",
                    pageUrl = "",
                    notes = "",
                    publishedAt = "",
                    channel = ch,
                    error = e.message ?: "网络错误"
                )
            }
            Handler(Looper.getMainLooper()).post { onResult(info) }
        }
    }

    private fun fetch(url: String, channel: String): Info {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw Exception("HTTP $code")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            val remoteCode = json.optInt("versionCode", -1)
            val info = Info(
                available = isNewer(remoteCode),
                remoteVersionCode = remoteCode,
                remoteVersionName = json.optString("versionName", ""),
                apkUrl = json.optString("apkUrl", ""),
                // 版本文件未提供 pageUrl 时兜底为发布页：浏览器打开 version.json
                // 只会看到原始 JSON，「前往更新」必须落到发布页
                pageUrl = json.optString("pageUrl", "").ifBlank { PAGE_URL },
                notes = json.optString("releaseNotes", ""),
                publishedAt = json.optString("publishedAt", ""),
                channel = channel
            )
            return info
        } finally {
            conn.disconnect()
        }
    }
}

/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.util

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lingyi.mbos.R

/**
 * 「发现新版本」对话框：检查到更新时弹出，点「前往更新」打开发布页 / APK 直链。
 * 关于页与主控台共用，避免重复实现弹窗逻辑。
 */
object UpdateDialog {

    /** 展示更新对话框。Activity 已销毁/正在结束时直接跳过，避免泄漏窗口。 */
    fun show(activity: Activity, info: UpdateChecker.Info) {
        if (activity.isFinishing || activity.isDestroyed) return
        val titleRes = if (info.channel == UpdateChecker.CHANNEL_PREVIEW)
            R.string.update_title_preview else R.string.update_title
        MaterialAlertDialogBuilder(activity)
            .setTitle(titleRes)
            .setMessage(
                if (info.remoteVersionName.isNotBlank() && info.remoteVersionName != UpdateChecker.currentVersionName())
                    "发现新版本 ${info.remoteVersionName}（本地 v${UpdateChecker.currentVersionName()}）。\n${info.notes}"
                else
                    "发现新版本，建议前往更新以获取最新修复与优化。\n${info.notes}"
            )
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.update_go) { _, _ ->
                val target = info.pageUrl.ifBlank { info.apkUrl.ifBlank { UpdateChecker.PAGE_URL } }
                runCatching {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                }
            }
            .show()
    }
}

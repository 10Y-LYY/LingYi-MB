/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.lingyi.mbos.databinding.ActivityMakecodeBinding

/**
 * 编程模块（MakeCode 整合入口）：
 *  1. 一键打开 MakeCode 官网（makecode.microbit.org），在浏览器里图形化编程；
 *  2. 用户下载得到 .hex 固件后，回到本 App 点「选择 .hex 并刷入」直达自定义刷入页的 HEX 模式。
 *
 * 与「自定义刷入固件」是同一套刷写管线的不同入口，主体功能不变。
 */
class MakeCodeActivity : BaseActivity() {

    private lateinit var b: ActivityMakecodeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMakecodeBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.toolbar.setNavigationOnClickListener { finish() }

        b.btnOpenMakecode.setOnClickListener { openMakecode() }
        b.btnFlashHex.setOnClickListener { openHexFlash() }
    }

    /** 打开 MakeCode 官网（外部浏览器，体验最完整）。 */
    private fun openMakecode() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(MAKECODE_URL)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(intent) }
            .onFailure { Snackbar.make(b.root, R.string.makecode_open_failed, Snackbar.LENGTH_LONG).show() }
    }

    /** 直达自定义刷入页的 HEX 模式：进来就直接弹出文件选择器选 .hex。 */
    private fun openHexFlash() {
        val intent = Intent(this, CustomFlashActivity::class.java).apply {
            putExtra(EXTRA_AUTO_PICK_HEX, true)
        }
        runCatching { startActivity(intent) }
            .onFailure { Snackbar.make(b.root, R.string.makecode_open_failed, Snackbar.LENGTH_LONG).show() }
    }

    companion object {
        const val MAKECODE_URL = "https://makecode.microbit.org/"
        const val EXTRA_AUTO_PICK_HEX = "com.lingyi.mbos.AUTO_PICK_HEX"
    }
}

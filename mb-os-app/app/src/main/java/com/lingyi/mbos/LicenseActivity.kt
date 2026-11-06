/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.lingyi.mbos.databinding.ActivityLicenseBinding

/**
 * 开源许可证页：展示本项目采用的 MIT 许可证全文，以及第三方组件与素材鸣谢。
 * 许可证文本来自 assets/licenses/，与仓库根目录的 LICENSE / NOTICE.md 保持一致。
 */
class LicenseActivity : BaseActivity() {

    private lateinit var b: ActivityLicenseBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLicenseBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.toolbar.setNavigationOnClickListener { finish() }

        b.tvNotice.text = readAsset("notice.txt")
        b.btnViewFull.setOnClickListener { showFullText() }
        b.btnCopyLicense.setOnClickListener { copyLicense() }
    }

    private fun readAsset(name: String): String =
        runCatching { assets.open("licenses/$name").bufferedReader().use { it.readText() } }
            .getOrElse { "（无法读取 $name）" }

    private fun showFullText() {
        val tv = TextView(this).apply {
            text = readAsset("mit.txt")
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(28, 20, 28, 20)
        }
        val sv = NestedScrollView(this).apply { addView(tv) }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.license_full_title)
            .setView(sv)
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun copyLicense() {
        val text = readAsset("mit.txt")
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(getString(R.string.license_title), text))
        Snackbar.make(b.root, R.string.license_copied, Snackbar.LENGTH_SHORT).show()
    }
}
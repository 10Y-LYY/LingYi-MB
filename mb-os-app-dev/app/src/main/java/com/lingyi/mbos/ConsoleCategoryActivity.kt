/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.google.android.material.R as MR
import com.lingyi.mbos.databinding.ActivityConsoleCategoryBinding
import com.lingyi.mbos.databinding.ItemFuncRowBinding
import com.lingyi.mbos.ui.FeatureCatalog
import com.lingyi.mbos.ui.Haptics.setOnClickWithHaptic
import com.lingyi.mbos.util.AppLog
import com.lingyi.mbos.util.themeColor

/**
 * 控制台「大方格」主题的二级页面：展示某一分类下的全部功能，点击后回抛。
 *
 * 不自行执行功能——把 [EXTRA_FEATURE_ID] 通过 setResult 交回 MainActivity，
 * 由既有 onFuncClick(id) 统一分派，避免两套入口逻辑漂移。
 */
class ConsoleCategoryActivity : BaseActivity() {

    private lateinit var b: ActivityConsoleCategoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityConsoleCategoryBinding.inflate(layoutInflater)
        setContentView(b.root)

        val category = FeatureCatalog.categoryOf(intent.getStringExtra(EXTRA_CATEGORY))
        b.toolbar.setNavigationOnClickListener { finish() }
        b.toolbar.title = category.title
        b.toolbar.subtitle = getString(
            R.string.console_feature_count,
            FeatureCatalog.byCategory(category).size
        )

        buildFuncList(category)
    }

    private fun buildFuncList(category: FeatureCatalog.Category) {
        // 兜底：列表构建异常不应让二级页起不来（与主控台同一策略）
        runCatching { buildFuncListInner(category) }
            .onFailure { AppLog.e("ConsoleCategory", "功能列表构建失败", it) }
    }

    private fun buildFuncListInner(category: FeatureCatalog.Category) {
        val container = b.funcList
        container.removeAllViews()
        val features = FeatureCatalog.byCategory(category)

        // 与主控台一致的 M3 容器色循环，保证两种主题视觉连贯
        val roles = listOf(
            MR.attr.colorPrimaryContainer to MR.attr.colorOnPrimaryContainer,
            MR.attr.colorSecondaryContainer to MR.attr.colorOnSecondaryContainer,
            MR.attr.colorTertiaryContainer to MR.attr.colorOnTertiaryContainer,
        )
        val dividerH = (resources.displayMetrics.density).toInt().coerceAtLeast(1)

        features.forEachIndexed { index, feature ->
            val ib = ItemFuncRowBinding.inflate(layoutInflater, container, false)
            ib.ivIcon.setImageResource(feature.icon)
            ib.tvTitle.text = feature.title
            ib.tvDesc.text = feature.desc
            val role = roles[index % roles.size]
            ib.iconWrap.setCardBackgroundColor(themeColor(role.first))
            ib.ivIcon.imageTintList = android.content.res.ColorStateList.valueOf(themeColor(role.second))
            ib.root.setOnClickWithHaptic { pick(feature.id) }
            container.addView(ib.root)
            if (index < features.size - 1) {
                container.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dividerH
                    )
                    setBackgroundColor(themeColor(MR.attr.colorOutlineVariant))
                })
            }
        }
    }

    /** 选中功能：把 id 回抛给主控台后关闭本页。 */
    private fun pick(id: String) {
        setResult(
            RESULT_OK,
            Intent().putExtra(EXTRA_FEATURE_ID, id)
        )
        finish()
    }

    companion object {
        const val EXTRA_CATEGORY = "com.lingyi.mbos.extra.CATEGORY"
        const val EXTRA_FEATURE_ID = "com.lingyi.mbos.extra.FEATURE_ID"

        fun intent(context: Context, category: FeatureCatalog.Category): Intent =
            Intent(context, ConsoleCategoryActivity::class.java)
                .putExtra(EXTRA_CATEGORY, category.key)
    }
}

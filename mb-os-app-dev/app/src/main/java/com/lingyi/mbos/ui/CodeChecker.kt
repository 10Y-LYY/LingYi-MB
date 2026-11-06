/*
 * 灵逸 MB · micro:bit V2 第三方客户端
 * Copyright (c) 2026 10Y-灵逸云科技
 * SPDX-License-Identifier: MIT
 */

package com.lingyi.mbos.ui

/**
 * 代码体检（「代码体检」功能用）：对 MicroPython 代码做静态规则检查。
 * 只做「明显错误」级别的轻量检查，不追求编译器完整度——
 * 在刷机前先把括号不配对、缩进混用、中文标点这类高频坑指出来。
 */
object CodeChecker {

    enum class Level { ERROR, WARN }

    data class Issue(val level: Level, val line: Int, val message: String)

    /** 检查结果：按行号排序；没有问题返回空列表。 */
    fun check(code: String): List<Issue> {
        val issues = ArrayList<Issue>()
        val lines = code.split('\n')
        lines.forEachIndexed { idx, raw ->
            val lineNo = idx + 1
            val stripped = raw.trimEnd()
            // 中文全角标点（最高频坑：从网页/文档抄代码带进来）
            val fullWidth = mapOf(
                '，' to ',', '。' to '.', '：' to ':', '；' to ';',
                '（' to '(', '）' to ')', '【' to '[', '】' to ']', '＂' to '"'
            )
            fullWidth.forEach { (cn, en) ->
                if (stripped.contains(cn)) {
                    issues.add(Issue(Level.ERROR, lineNo, "含中文标点「$cn」，应改为英文「$en」"))
                }
            }
            // 行内制表符（与空格混用会导致 IndentationError）
            if (stripped.contains('\t') && stripped.contains("    ")) {
                issues.add(Issue(Level.ERROR, lineNo, "同一行混用了 Tab 和空格缩进，请统一"))
            }
            // 引号奇数（跨行字符串除外：保守起见只对不含三引号的行提示）
            if (!stripped.contains("\"\"\"")) {
                val quotes = stripped.count { it == '"' } + stripped.count { it == '\'' }
                if (quotes % 2 == 1) {
                    issues.add(Issue(Level.ERROR, lineNo, "引号数量为奇数，字符串可能没闭合"))
                }
            }
            // 块语句首行缺少冒号
            val trimmed = stripped.trimStart()
            val blockKw = listOf("if ", "elif ", "else", "for ", "while ", "def ", "class ", "try", "except", "finally", "with ")
            if (blockKw.any { trimmed.startsWith(it) } && !trimmed.endsWith(":") && !trimmed.endsWith(":\\")) {
                issues.add(Issue(Level.ERROR, lineNo, "语句块首行应以英文冒号「:」结尾"))
            }
            // 常见拼写坑：print 缺少括号（Python2 写法）
            if (Regex("(^|\\W)print\\s+[^(\\s]").containsMatchIn(trimmed)) {
                issues.add(Issue(Level.ERROR, lineNo, "print 是函数，需要写成 print(...)"))
            }
            // 过长行（只是提示）
            if (stripped.length > 120) {
                issues.add(Issue(Level.WARN, lineNo, "行过长（${stripped.length} 字符），建议换行"))
            }
        }
        // 全局括号配对
        val opens = code.count { it == '(' }
        val closes = code.count { it == ')' }
        if (opens != closes) {
            issues.add(Issue(Level.ERROR, 1, "全文小括号不配对：左 $opens 个 / 右 $closes 个"))
        }
        val sqOpens = code.count { it == '[' }
        val sqCloses = code.count { it == ']' }
        if (sqOpens != sqCloses) {
            issues.add(Issue(Level.ERROR, 1, "全文中括号不配对：左 $sqOpens 个 / 右 $sqCloses 个"))
        }
        // 文件为空
        if (code.isBlank()) {
            issues.add(Issue(Level.WARN, 1, "代码为空"))
        }
        return issues.sortedWith(compareBy({ it.line }, { it.level.ordinal }))
    }

    /** 汇总一句话结论（界面用）。 */
    fun summary(issues: List<Issue>): String = when {
        issues.isEmpty() -> "未发现明显问题，可以刷入试试"
        issues.none { it.level == Level.ERROR } -> "有 ${issues.size} 条小提醒，不挡刷机"
        else -> "发现 ${issues.count { it.level == Level.ERROR }} 处错误，建议先修再刷"
    }
}

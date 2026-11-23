package com.example.util

sealed interface MarkdownSegment {
    data class Prose(val text: String) : MarkdownSegment
    data class CodeBlock(val language: String, val code: String, val isDiff: Boolean) : MarkdownSegment
}

fun parseMarkdownSegments(markdown: String): List<MarkdownSegment> {
    if (markdown.isEmpty()) return emptyList()
    val lines = markdown.lines()
    val segments = mutableListOf<MarkdownSegment>()
    val proseBuffer = StringBuilder()
    val codeBuffer = StringBuilder()
    var inFence = false
    var fenceLang = ""

    fun flushProse() {
        val text = proseBuffer.toString().trim('\n')
        if (text.isNotBlank()) {
            segments.add(MarkdownSegment.Prose(text))
        }
        proseBuffer.clear()
    }

    for (line in lines) {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```")) {
            if (!inFence) {
                flushProse()
                inFence = true
                fenceLang = trimmed.removePrefix("```").trim().lowercase()
                codeBuffer.clear()
            } else {
                val codeText = codeBuffer.toString().trimEnd('\n')
                val looksLikeDiff = fenceLang == "diff" || fenceLang == "patch" ||
                    (codeText.lines().any { it.startsWith("@@ ") } &&
                        codeText.lines().any { it.startsWith("+") || it.startsWith("-") })
                segments.add(
                    MarkdownSegment.CodeBlock(
                        language = fenceLang.ifBlank { if (looksLikeDiff) "diff" else "code" },
                        code = codeText,
                        isDiff = looksLikeDiff
                    )
                )
                inFence = false
                fenceLang = ""
                codeBuffer.clear()
            }
        } else {
            if (inFence) {
                if (codeBuffer.isNotEmpty()) codeBuffer.append('\n')
                codeBuffer.append(line)
            } else {
                if (proseBuffer.isNotEmpty()) proseBuffer.append('\n')
                proseBuffer.append(line)
            }
        }
    }

    if (inFence) {
        val codeText = codeBuffer.toString().trimEnd('\n')
        val looksLikeDiff = fenceLang == "diff" || fenceLang == "patch"
        segments.add(
            MarkdownSegment.CodeBlock(
                language = fenceLang.ifBlank { "code" },
                code = codeText,
                isDiff = looksLikeDiff
            )
        )
    } else {
        flushProse()
    }

    return segments
}

enum class DiffLineType {
    UNCHANGED,
    ADDED,
    REMOVED
}

data class DiffLine(
    val type: DiffLineType,
    val leftLineNumber: Int?,
    val rightLineNumber: Int?,
    val content: String
)

data class DiffSummary(
    val lines: List<DiffLine>,
    val additions: Int,
    val removals: Int,
    val unchanged: Int
)

object DiffEngine {
    fun computeLineDiff(baseline: String, candidate: String): DiffSummary {
        val a = if (baseline.isEmpty()) emptyList() else baseline.lines()
        val b = if (candidate.isEmpty()) emptyList() else candidate.lines()
        val n = a.size
        val m = b.size

        // Cap DP matrix for extremely large inputs to keep mobile memory safe
        if (n * m > 250_000) {
            return fallbackLinearDiff(a, b)
        }

        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                dp[i][j] = if (a[i] == b[j]) {
                    1 + dp[i + 1][j + 1]
                } else {
                    maxOf(dp[i + 1][j], dp[i][j + 1])
                }
            }
        }

        val result = mutableListOf<DiffLine>()
        var i = 0
        var j = 0
        var leftNo = 1
        var rightNo = 1
        var adds = 0
        var dels = 0
        var same = 0

        while (i < n && j < m) {
            when {
                a[i] == b[j] -> {
                    result.add(DiffLine(DiffLineType.UNCHANGED, leftNo++, rightNo++, a[i]))
                    same++
                    i++
                    j++
                }
                dp[i + 1][j] >= dp[i][j + 1] -> {
                    result.add(DiffLine(DiffLineType.REMOVED, leftNo++, null, a[i]))
                    dels++
                    i++
                }
                else -> {
                    result.add(DiffLine(DiffLineType.ADDED, null, rightNo++, b[j]))
                    adds++
                    j++
                }
            }
        }
        while (i < n) {
            result.add(DiffLine(DiffLineType.REMOVED, leftNo++, null, a[i++]))
            dels++
        }
        while (j < m) {
            result.add(DiffLine(DiffLineType.ADDED, null, rightNo++, b[j++]))
            adds++
        }

        return DiffSummary(lines = result, additions = adds, removals = dels, unchanged = same)
    }

    private fun fallbackLinearDiff(a: List<String>, b: List<String>): DiffSummary {
        val result = mutableListOf<DiffLine>()
        val max = maxOf(a.size, b.size)
        var adds = 0
        var dels = 0
        var same = 0
        for (idx in 0 until max) {
            val left = a.getOrNull(idx)
            val right = b.getOrNull(idx)
            when {
                left == right && left != null -> {
                    result.add(DiffLine(DiffLineType.UNCHANGED, idx + 1, idx + 1, left))
                    same++
                }
                else -> {
                    if (left != null) {
                        result.add(DiffLine(DiffLineType.REMOVED, idx + 1, null, left))
                        dels++
                    }
                    if (right != null) {
                        result.add(DiffLine(DiffLineType.ADDED, null, idx + 1, right))
                        adds++
                    }
                }
            }
        }
        return DiffSummary(result, adds, dels, same)
    }
}

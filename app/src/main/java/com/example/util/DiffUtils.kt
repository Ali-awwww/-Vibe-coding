package com.example.util

import com.example.domain.model.DiffChangeType
import com.example.domain.model.DiffLine
import com.example.domain.model.DiffLineType
import com.example.domain.model.FileDiff

object DiffUtils {

    fun computeDiff(path: String, oldContent: String, newContent: String): FileDiff {
        if (oldContent.isEmpty() && newContent.isNotEmpty()) {
            val lines = newContent.lines()
            val diffLines = lines.mapIndexed { index, line ->
                DiffLine(DiffLineType.ADD, line, null, index + 1)
            }
            return FileDiff(
                path = path,
                changeType = DiffChangeType.CREATED,
                oldContent = "",
                newContent = newContent,
                additions = lines.size,
                deletions = 0,
                diffLines = diffLines
            )
        }

        if (oldContent.isNotEmpty() && newContent.isEmpty()) {
            val lines = oldContent.lines()
            val diffLines = lines.mapIndexed { index, line ->
                DiffLine(DiffLineType.REMOVE, line, index + 1, null)
            }
            return FileDiff(
                path = path,
                changeType = DiffChangeType.DELETED,
                oldContent = oldContent,
                newContent = "",
                additions = 0,
                deletions = lines.size,
                diffLines = diffLines
            )
        }

        val oldLines = oldContent.lines()
        val newLines = newContent.lines()

        val diffLines = mutableListOf<DiffLine>()
        var additions = 0
        var deletions = 0

        // Simple Myers/LCS diff algorithm implementation for clean line-by-line diff
        val lcs = computeLCS(oldLines, newLines)
        var i = 0
        var j = 0
        var lcsIdx = 0

        var oldLineNum = 1
        var newLineNum = 1

        while (i < oldLines.size || j < newLines.size) {
            if (lcsIdx < lcs.size && i < oldLines.size && j < newLines.size && oldLines[i] == lcs[lcsIdx] && newLines[j] == lcs[lcsIdx]) {
                diffLines.add(DiffLine(DiffLineType.CONTEXT, oldLines[i], oldLineNum, newLineNum))
                i++
                j++
                lcsIdx++
                oldLineNum++
                newLineNum++
            } else if (i < oldLines.size && (lcsIdx >= lcs.size || oldLines[i] != lcs[lcsIdx])) {
                diffLines.add(DiffLine(DiffLineType.REMOVE, oldLines[i], oldLineNum, null))
                i++
                deletions++
                oldLineNum++
            } else if (j < newLines.size && (lcsIdx >= lcs.size || newLines[j] != lcs[lcsIdx])) {
                diffLines.add(DiffLine(DiffLineType.ADD, newLines[j], null, newLineNum))
                j++
                additions++
                newLineNum++
            }
        }

        val changeType = if (additions == 0 && deletions == 0) DiffChangeType.MODIFIED else DiffChangeType.MODIFIED

        return FileDiff(
            path = path,
            changeType = changeType,
            oldContent = oldContent,
            newContent = newContent,
            additions = additions,
            deletions = deletions,
            diffLines = diffLines
        )
    }

    private fun computeLCS(a: List<String>, b: List<String>): List<String> {
        val m = a.size
        val n = b.size
        // Optimized LCS matrix
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0 until m) {
            for (j in 0 until n) {
                if (a[i] == b[j]) {
                    dp[i + 1][j + 1] = dp[i][j] + 1
                } else {
                    dp[i + 1][j + 1] = maxOf(dp[i + 1][j], dp[i][j + 1])
                }
            }
        }

        val result = mutableListOf<String>()
        var x = m
        var y = n
        while (x > 0 && y > 0) {
            if (a[x - 1] == b[y - 1]) {
                result.add(a[x - 1])
                x--
                y--
            } else if (dp[x - 1][y] >= dp[x][y - 1]) {
                x--
            } else {
                y--
            }
        }
        return result.reversed()
    }
}

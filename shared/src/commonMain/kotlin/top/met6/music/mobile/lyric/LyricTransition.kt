package top.met6.music.mobile.lyric

import top.met6.amll.LyricLine

fun applyTransitionLead(lines: List<LyricLine>, leadMs: Long): List<LyricLine> {
    var previousEnd = 0L
    return lines.map { line ->
        val start = line.words.firstOrNull()?.startTime ?: line.startTime
        val end = line.words.lastOrNull()?.endTime ?: line.endTime
        val earliest = if (start >= previousEnd) previousEnd else start
        val result = line.copy(startTime = maxOf(earliest, start - leadMs.coerceAtLeast(0L)))
        previousEnd = maxOf(previousEnd, end)
        result
    }
}

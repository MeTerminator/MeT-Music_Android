package top.met6.music.mobile.lyric

import top.met6.amll.LyricLine
import top.met6.amll.LyricWord
import top.met6.music.mobile.models.LyricResponse

/** Converts the service's ordinary LRC/QRC response to the shared AMLL model. */
object LyricParser {
    fun parse(
        response: LyricResponse,
        removeQrcSongInfo: Boolean = true,
    ): List<LyricLine> {
        response.qrc?.takeIf(String::isNotBlank)?.let { qrc ->
            val parsed = parseQrc(qrc).let { lines ->
                if (removeQrcSongInfo) removeQrcInformationLines(lines) else lines
            }
            if (parsed.isNotEmpty()) {
                val translated = alignSubLine(parsed, response.qrctrans ?: response.lrctrans, roman = false)
                return alignSubLine(translated, response.qrcroma, roman = true)
            }
        }
        response.lrc?.takeIf(String::isNotBlank)?.let { lrc ->
            return alignSubLine(parseLrc(lrc), response.lrctrans, roman = false)
        }
        return emptyList()
    }

    private fun parseLrc(source: String): List<LyricLine> {
        val regex = Regex("""^\[(\d+):(\d+)(?:\.(\d+))?\](.*)$""")
        val result = source.lines().mapNotNull { raw ->
            val match = regex.matchEntire(raw.trim()) ?: return@mapNotNull null
            val fraction = match.groupValues[3]
            val millis = if (fraction.isEmpty()) 0 else fraction.padEnd(3, '0').take(3).toLong()
            val start = match.groupValues[1].toLong() * 60_000 + match.groupValues[2].toLong() * 1_000 + millis
            val text = decodeEntities(match.groupValues[4].trim())
            if (text.isBlank() || "纯音乐，请您欣赏" in text) return@mapNotNull null
            LyricLine(
                words = listOf(LyricWord(start, start + 3_000, text)),
                startTime = start,
                endTime = start + 3_000,
            )
        }.toMutableList()
        for (index in 0 until result.lastIndex) {
            result[index] = result[index].copy(
                words = result[index].words.map { it.copy(endTime = result[index + 1].startTime) },
                endTime = result[index + 1].startTime,
            )
        }
        return result
    }

    private fun parseQrc(source: String): List<LyricLine> {
        val lineTime = Regex("""^\[(\d+),(\d+)\]""")
        val wordTime = Regex("""([^{}()]*)[({](\d+),(\d+)[})]""")
        return source.lines().mapNotNull { raw ->
            val lineMatch = lineTime.find(raw) ?: return@mapNotNull null
            val lineStart = lineMatch.groupValues[1].toLong()
            val lineEnd = lineStart + lineMatch.groupValues[2].toLong()
            val content = raw.substring(lineMatch.value.length).trim()
            if (content.isEmpty()) return@mapNotNull null
            val words = wordTime.findAll(content).mapNotNull { match ->
                val text = decodeEntities(match.groupValues[1])
                if (text.isEmpty()) null else {
                    val start = match.groupValues[2].toLong()
                    LyricWord(start, start + match.groupValues[3].toLong(), text)
                }
            }.toList().ifEmpty { listOf(LyricWord(lineStart, lineEnd, decodeEntities(content))) }
            LyricLine(words = words, startTime = lineStart, endTime = lineEnd)
        }
    }

    private fun removeQrcInformationLines(lines: List<LyricLine>): List<LyricLine> =
        lines.drop(1).dropWhile(::isQrcInformationLine)

    private fun isQrcInformationLine(line: LyricLine): Boolean {
        if (':' !in line.text && '：' !in line.text) return false
        val firstDuration = line.words.firstOrNull()?.let { it.endTime - it.startTime } ?: return false
        return line.words.all { it.endTime - it.startTime == firstDuration }
    }

    private fun alignSubLine(base: List<LyricLine>, source: String?, roman: Boolean): List<LyricLine> {
        if (source.isNullOrBlank()) return base
        val subLines = parseLrc(source)
        return base.map { line ->
            val sub = subLines.firstOrNull { kotlin.math.abs(it.startTime - line.startTime) < 150 }
                ?.text?.takeIf { it.isNotBlank() && it != "//" }
            when {
                sub == null -> line
                roman -> line.copy(romanLyric = sub)
                else -> line.copy(translatedLyric = sub)
            }
        }
    }

    private fun decodeEntities(text: String): String = text
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'")
        .replace("&#39;", "'").replace("&amp;", "&")
}

package top.met6.music.mobile.lyric

import top.met6.amll.LyricLine
import top.met6.amll.LyricWord
import top.met6.music.mobile.models.LyricResponse

enum class LyricFormat { LRC, QRC, YRC, TTML;
    fun transitionLeadMs(configuredMs: Long): Long = if (this == QRC || this == YRC) configuredMs else 0L
}

data class ParsedLyric(val lines: List<LyricLine>, val format: LyricFormat)

/** Converts the service's ordinary LRC/QRC/YRC response to the shared AMLL model. */
object LyricParser {
    fun parse(
        response: LyricResponse,
        removeQrcSongInfo: Boolean = true,
    ): List<LyricLine> = parseWithFormat(response, removeQrcSongInfo).lines

    fun parseWithFormat(response: LyricResponse, removeQrcSongInfo: Boolean = true): ParsedLyric {
        response.yrc?.takeIf(String::isNotBlank)?.let { yrc ->
            val parsed = parseYrc(yrc)
            val lines = if (removeQrcSongInfo) parsed.filterNot(::isYrcInformationLine) else parsed
            if (parsed.isNotEmpty()) return ParsedLyric(alignSubLine(
                alignSubLine(lines, response.ytlrc ?: response.lrctrans, roman = false),
                response.yromalrc ?: response.romalrc, roman = true), LyricFormat.YRC)
        }
        response.qrc?.takeIf(String::isNotBlank)?.let { qrc ->
            val parsed = parseQrc(qrc).let { lines ->
                if (removeQrcSongInfo) removeQrcInformationLines(lines) else lines
            }
            if (parsed.isNotEmpty()) {
                val translated = alignSubLine(parsed, response.qrctrans ?: response.lrctrans, roman = false)
                return ParsedLyric(alignSubLine(translated, response.qrcroma, roman = true), LyricFormat.QRC)
            }
        }
        response.lrc?.takeIf(String::isNotBlank)?.let { lrc ->
            return ParsedLyric(alignSubLine(alignSubLine(parseLrc(lrc), response.lrctrans, roman = false), response.romalrc, roman = true), LyricFormat.LRC)
        }
        return ParsedLyric(emptyList(), LyricFormat.LRC)
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

    private fun parseYrc(source: String): List<LyricLine> {
        val linePattern = Regex("""^\[(\d+),(\d+)\]""")
        val wordPattern = Regex("""\((\d+),(\d+),\d+\)([^()]*)""")
        return source.lines().mapNotNull { raw ->
            val line = linePattern.find(raw) ?: return@mapNotNull null
            val start = line.groupValues[1].toLong()
            val end = start + line.groupValues[2].toLong()
            val words = wordPattern.findAll(raw.substring(line.value.length)).map { match ->
                val wordStart = match.groupValues[1].toLong()
                LyricWord(wordStart, wordStart + match.groupValues[2].toLong(), decodeEntities(match.groupValues[3]))
            }.filter { it.word.isNotEmpty() }.toList()
            if (words.isEmpty()) null else LyricLine(words = words, startTime = start, endTime = end)
        }
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

    private val yrcCredit = Regex("""^(?:作词|作曲|词曲|填词|编曲|制作人?|出品|混音|母带|和声|录音|演唱|原唱|发行|版权|OP|SP|Lyrics|Composer|Producer|by)\s*[:：]""", RegexOption.IGNORE_CASE)
    private fun isYrcInformationLine(line: LyricLine): Boolean {
        val text = line.text.trim()
        return yrcCredit.containsMatchIn(text) ||
            (line.startTime == 0L && " - " in text) ||
            Regex("""未[经經].*[许許]可.*(?:不得|禁止)""").containsMatchIn(text)
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

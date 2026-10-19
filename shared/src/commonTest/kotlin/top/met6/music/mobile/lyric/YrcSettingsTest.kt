package top.met6.music.mobile.lyric

import kotlin.test.*
import top.met6.music.mobile.models.LyricResponse

class YrcSettingsTest {
    @Test fun yrcHonorsInformationFilterAndPreservesWordTimes() {
        val response = LyricResponse(
            yrc = "[0,1000](0,1000,0)作词：作者\n[2000,1000](2000,400,0)你(2400,600,0)好\n[4000,1000](4000,1000,0)编曲：作者",
            ytlrc = "[00:02.000]Hello")
        val parsed = LyricParser.parseWithFormat(response)
        assertEquals(LyricFormat.YRC, parsed.format)
        assertEquals(listOf("你好"), parsed.lines.map { it.text })
        assertEquals("Hello", parsed.lines.single().translatedLyric)
        assertEquals(3, LyricParser.parse(response, removeQrcSongInfo = false).size)
        val advanced = applyTransitionLead(parsed.lines, parsed.format.transitionLeadMs(250)).single()
        assertEquals(1750L, advanced.startTime)
        assertEquals(2000L, advanced.words.first().startTime)
        assertEquals(3000L, advanced.endTime)
        assertEquals(parsed.lines.single().words, advanced.words)
        assertEquals(250L, LyricFormat.QRC.transitionLeadMs(250))
        assertEquals(0L, LyricFormat.LRC.transitionLeadMs(250))
        assertEquals(0L, LyricFormat.TTML.transitionLeadMs(250))
    }

    @Test fun allMetadataStaysEmptyAndInvalidYrcFallsBackToLrc() {
        val empty = LyricParser.parseWithFormat(LyricResponse(lrc = "[00:00.000]作词：作者", yrc = "[0,1000](0,1000,0)作词：作者"))
        assertEquals(LyricFormat.YRC, empty.format)
        assertTrue(empty.lines.isEmpty())
        val fallback = LyricParser.parseWithFormat(LyricResponse(lrc = "[00:01.000]正文", yrc = "invalid"))
        assertEquals(LyricFormat.LRC, fallback.format)
        assertEquals("正文", fallback.lines.single().text)
    }

    @Test fun earlyTransitionDoesNotOverlapPreviousVocals() {
        val lines = LyricParser.parse(LyricResponse(yrc = "[1000,1000](1000,1000,0)一\n[2100,1000](2100,1000,0)二"))
        val advanced = applyTransitionLead(lines, 500)
        assertEquals(2000L, advanced[1].startTime)
        assertEquals(2100L, advanced[1].words.first().startTime)
        assertEquals(lines, applyTransitionLead(lines, 0))
    }
}

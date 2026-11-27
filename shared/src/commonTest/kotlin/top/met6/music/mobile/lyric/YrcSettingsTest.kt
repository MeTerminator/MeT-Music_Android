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
        val line = parsed.lines.single()
        assertEquals(2000L, line.words.first().startTime)
        assertEquals(3000L, line.endTime)
        assertEquals(250L, parsed.format.transitionLeadMs(250))
        assertFalse(parsed.format.holdsAdjacentLines)
        assertEquals(250L, LyricFormat.QRC.transitionLeadMs(250))
        assertTrue(LyricFormat.QRC.holdsAdjacentLines)
        assertEquals(0L, LyricFormat.LRC.transitionLeadMs(250))
        assertFalse(LyricFormat.LRC.holdsAdjacentLines)
        assertEquals(250L, LyricFormat.TTML.transitionLeadMs(250))
        assertTrue(LyricFormat.TTML.holdsAdjacentLines)
    }

    @Test fun allMetadataStaysEmptyAndInvalidYrcFallsBackToLrc() {
        val empty = LyricParser.parseWithFormat(LyricResponse(lrc = "[00:00.000]作词：作者", yrc = "[0,1000](0,1000,0)作词：作者"))
        assertEquals(LyricFormat.YRC, empty.format)
        assertTrue(empty.lines.isEmpty())
        val fallback = LyricParser.parseWithFormat(LyricResponse(lrc = "[00:01.000]正文", yrc = "invalid"))
        assertEquals(LyricFormat.LRC, fallback.format)
        assertEquals("正文", fallback.lines.single().text)
    }

    @Test fun qrcTimingOnlyRowsDoNotBecomeVisibleLyrics() {
        val parsed = LyricParser.parseWithFormat(LyricResponse(
            qrc = "[1000,1000](1000,1000)\n[2200,800]正(2200,400)文(2600,400)",
        ))
        assertEquals(LyricFormat.QRC, parsed.format)
        assertEquals(listOf("正文"), parsed.lines.map { it.text })

        val fallback = LyricParser.parseWithFormat(LyricResponse(
            qrc = "[1000,1000](1000,1000)",
            lrc = "[00:01.000]正文",
        ))
        assertEquals(LyricFormat.LRC, fallback.format)
    }
}

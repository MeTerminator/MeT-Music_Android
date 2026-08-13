package top.met6.music.mobile.lyric

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import top.met6.music.mobile.models.LyricResponse

class LyricParserTest {

    @Test
    fun parseQrcKeepsSongInfoWhenRemovalIsDisabled() {
        val qrcData = """<?xml version="1.0" encoding="utf-8"?>
<QrcInfos>
<QrcHeadInfo SaveTime="1730644424" Version="100"/>
<LyricInfo LyricCount="1">
<Lyric_1 LyricType="1" LyricContent="[ti:潮声回响]
[ar:洛天依]
[al:]
[by:krc转qrc工具]
[offset:0]
[168,257]洛(168,0)天(168,51)依 (219,51)- (270,0)潮(270,51)声(321,52)回(373,0)响(373,52)
[23501,4848]绞(23501,304)断(23805,357)的(24162,432)弦 (24594,795)能(25695,255)否(25950,465)重(26415,254)新(26669,357)演(27026,458)奏(27484,865)
"/>
</LyricInfo>
</QrcInfos>"""

        val response = LyricResponse(qrc = qrcData)
        val lines = LyricParser.parse(response, removeQrcSongInfo = false)

        assertTrue(lines.isNotEmpty())
        assertEquals(2, lines.size)

        val firstLine = lines[0]
        assertEquals(168L, firstLine.startTime)
        assertEquals(425L, firstLine.endTime)
        assertEquals("洛天依 - 潮声回响", firstLine.text)

        val secondLine = lines[1]
        assertEquals(23501L, secondLine.startTime)
        assertEquals(28349L, secondLine.endTime)
        assertEquals("绞断的弦 能否重新演奏", secondLine.text)
    }

    @Test
    fun parseQrcRemovesSongInfoByDefault() {
        val response = LyricResponse(
            qrc = """[168,257]洛(168,0)天(168,51)依 (219,51)- (270,0)潮(270,51)声(321,52)回(373,0)响(373,52)
[23501,4848]绞(23501,304)断(23805,357)的(24162,432)弦 (24594,795)能(25695,255)否(25950,465)重(26415,254)新(26669,357)演(27026,458)奏(27484,865)""",
        )

        val lines = LyricParser.parse(response)

        assertEquals(1, lines.size)
        assertEquals(23501L, lines.single().startTime)
        assertEquals("绞断的弦 能否重新演奏", lines.single().text)
    }

    @Test
    fun parseQrcRemovesConsecutiveUniformlyTimedInformationLines() {
        val response = LyricResponse(
            qrc = """[0,700]歌(0,100)手(100,100) (200,100)-(300,100) 歌(400,100)名(500,100)
[1000,700]作(1000,100)词(1100,100)：(1200,100)某(1300,100)某(1400,100)
[2000,700]Composer(2000,100):(2100,100)Someone(2200,100)
[3000,700]对白(3000,100)：(3100,200)开始(3300,100)
[4000,700]备注(4000,100)：(4100,100)这一行位于歌词之后(4200,100)""",
        )

        val lines = LyricParser.parse(response)

        assertEquals(2, lines.size)
        assertEquals("对白：开始", lines[0].text)
        assertEquals("备注：这一行位于歌词之后", lines[1].text)
    }
}

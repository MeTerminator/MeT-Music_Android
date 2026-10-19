package top.met6.music.mobile.lyric

import kotlin.test.*
import top.met6.music.mobile.models.Artist
import top.met6.music.mobile.models.LyricResponse
import top.met6.music.mobile.models.Song

class QrcInformationFilterTest {
    // The same bilingual and compound headers covered by the iOS parser tests.
    private val credits = listOf(
        "词曲:张三", "Producer:X", "作詞:山田", "編曲／混音:李四", "mixed by: Y",
        "词 Lyricist：五月天 阿信", "共同制作人 Co-Producer：詹宏业",
        "编曲 Music Arrangement：五月天 阿信", "编曲协力 Music Arrangement Coordinator：黄庆原",
        "和声编写/演唱 Backing Vocal/Arrangement：洪俊扬", "大合唱 Crowd Vocals：詹宏业",
        "母带后期处理工程师 Mastering Engineer：Wayson", "母带后期处理录音室 Mastering Studio：Wayson",
        "艺术总监：李朋", "母带处理工程师：赵靖", "总策划/监制：李静楠", "混音室：@SBMS",
        "配唱制作人：吴青峰/陈君豪", "音频剪辑：叶育轩/陈君豪", "词曲版权管理：索尼音乐",
        "器乐团队：乐手", "说唱作曲：作者", "戏曲：演员", "引用：作品",
    )

    private fun response(texts: List<String>, yrc: Boolean = false): LyricResponse {
        val timed = texts.mapIndexed { index, text ->
            val start = (index + 1) * 2000
            if (yrc) "[$start,1000]($start,1000,0)$text"
            else "[$start,1000]$text($start,1000)"
        }.joinToString("\n")
        val translation = "[00:02.000]译一\n[00:06.000]译二"
        val roman = "[00:02.000]roma1\n[00:06.000]roma2"
        return if (yrc) LyricResponse(yrc = timed, ytlrc = translation, yromalrc = roman)
        else LyricResponse(qrc = timed, qrctrans = translation, qrcroma = roman)
    }

    @Test fun creditsAnywhereUseTheSameRulesForQrcAndYrcAndKeepSidecarsAndTimings() {
        for (yrc in listOf(false, true)) for (credit in credits) {
            val source = response(listOf("歌词一", credit, "歌词二"), yrc)
            val original = LyricParser.parse(source, removeQrcSongInfo = false)
            val filtered = LyricParser.parse(source)
            assertEquals(listOf("歌词一", "歌词二"), filtered.map { it.text }, credit)
            assertEquals(listOf("译一", "译二"), filtered.map { it.translatedLyric }, credit)
            assertEquals(listOf("roma1", "roma2"), filtered.map { it.romanLyric }, credit)
            assertEquals(listOf(original.first(), original.last()), filtered, credit)
        }
    }

    @Test fun firstLyricsUniformTimingAndOrdinaryColonsArePreserved() {
        val texts = listOf("词穷了", "Producer of dreams", "Music is my life：yeah", "总有一天：我会", "女：", "男：", "合：", "A - B")
        for (yrc in listOf(false, true)) {
            assertEquals(texts, LyricParser.parse(response(texts, yrc)).map { it.text })
        }
        assertEquals("第一句啊", LyricParser.parse(LyricResponse(
            qrc = "[0,1000]第(0,250)一(250,250)句(500,250)啊(750,250)"
        )).single().text)
    }

    @Test fun songIdentityIdentifiesOnlyFirstTitleAndArtistLabels() {
        val texts = listOf("测试歌曲-另一位歌手", "歌手甲：", "歌词", "歌手甲：唱出歌词", "陌生人：", "测试歌曲-正文保留")
        val song = Song(id = "test", name = "测试歌曲", artists = listOf(Artist("artist", " 歌手甲 ")))
        for (yrc in listOf(false, true)) {
            assertEquals(texts.drop(2), LyricParser.parse(response(texts, yrc), song = song).map { it.text })
            assertEquals(2, LyricParser.parse(response(listOf("别的歌曲-歌手", "歌词"), yrc), song = song).size)
        }
    }

    @Test fun untimedTitlesCopyrightPromotionsAndByCreditsAreRemoved() {
        val texts = listOf("正文", "【未经著作权人书面许可，不得以任何方式使用。】", "QQ音乐计划", "计划支持酷狗", "音乐人扶持计划", "整理 By：某人", "歌词尾声")
        val qrc = response(texts).let { it.copy(qrc = "[0,0]晴(0,0)天(0,0)\n[0,0](0,0)\n" + it.qrc) }
        val yrc = response(texts, true).let { it.copy(yrc = "[0,0](0,0,0)晴天\n" + it.yrc) }
        for (source in listOf(qrc, yrc)) {
            assertEquals(listOf("正文", "歌词尾声"), LyricParser.parse(source).map { it.text })
        }
    }

    @Test fun allMetadataStaysEmptyAndMalformedQrcFallsBackToLrc() {
        val source = LyricResponse(lrc = "[00:00.000]作词：作者", qrc = "[0,1000]作词：作者(0,1000)")
        val parsed = LyricParser.parseWithFormat(source)
        assertEquals(LyricFormat.QRC, parsed.format)
        assertTrue(parsed.lines.isEmpty())
        assertEquals(1, LyricParser.parse(source, removeQrcSongInfo = false).size)
        val fallback = LyricParser.parseWithFormat(source.copy(qrc = "invalid", lrc = "[00:01.000]正文"))
        assertEquals(LyricFormat.LRC, fallback.format)
        assertEquals("正文", fallback.lines.single().text)
    }

    @Test fun repeatedHeadersCompleteWithoutBacktrackingAndOrdinarySuffixIsPreserved() {
        val header = "编曲".repeat(500)
        assertTrue(LyricParser.parse(response(listOf("$header：作者"))).isEmpty())
        assertEquals("${header}的梦：正文", LyricParser.parse(response(listOf("${header}的梦：正文"))).single().text)
    }
}

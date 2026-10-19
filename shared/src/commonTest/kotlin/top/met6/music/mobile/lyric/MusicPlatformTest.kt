package top.met6.music.mobile.lyric

import kotlin.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.met6.music.mobile.models.*
import top.met6.music.mobile.state.toRoomSong
import top.met6.music.mobile.state.toSong

class MusicPlatformTest {
    @Test fun platformSurvivesPersistenceAndRoomTransfer() {
        val song = Song(id = "123", name = "网易云歌曲", source = MusicPlatform.NETEASE)
        assertNotEquals(song.cacheKey, song.copy(source = MusicPlatform.QQ).cacheKey)
        assertNotEquals(song.audioKey("exhigh"), song.audioKey("lossless"))
        assertEquals(MusicPlatform.NETEASE, Json.decodeFromString<Song>(Json.encodeToString(song)).source)
        assertEquals(MusicPlatform.NETEASE, song.toRoomSong().toSong().source)
        assertEquals(MusicPlatform.QQ, Json.decodeFromString<Song>("""{"id":"123","name":"Old"}""").source)
        assertEquals(MusicPlatform.QQ, Json.decodeFromString<Song>("""{"id":"123","name":"QQ","source":"qqmusic"}""").source)
    }

    @Test fun nativeYrcUsesAbsoluteWordTimes() {
        val response = LyricResponse(lrc = "[00:01.000]旧歌词", yrc = "[1000,1000](1000,400,0)你(1400,600,0)好",
            ytlrc = "[00:01.000]Hello", yromalrc = "[00:01.000]ni hao")
        val line = LyricParser.parse(response).single()
        assertEquals("你好", line.text)
        assertEquals(1400L, line.words.last().startTime)
        assertEquals(2000L, line.words.last().endTime)
        assertEquals("Hello", line.translatedLyric)
        assertEquals("ni hao", line.romanLyric)
    }
}

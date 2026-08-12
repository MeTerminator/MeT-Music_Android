package top.met6.music.mobile.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class CreatorInfo(
    val nickname: String? = null,
    val avatarUrl: String? = null
)

@Serializable
data class CoverSizes(
    val s: String? = null,
    val m: String? = null,
    val l: String? = null,
    val xl: String? = null
)

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    @SerialName("coverImgUrl") val cover: String? = null,
    val coverSize: CoverSizes? = null,
    val trackCount: Int = 0,
    val playCount: Long = 0,
    val description: String? = null,
    val creator: CreatorInfo? = null
) {
    val bestCoverUrl: String
        get() = cover ?: coverSize?.l ?: coverSize?.m ?: coverSize?.s ?: ""
}

@Serializable
data class Artist(
    val id: String,
    val name: String
)

@Serializable
data class Album(
    val id: String,
    val name: String,
    val picUrl: String? = null
)

@Serializable
data class Song(
    val id: String,
    val mid: String? = null,
    val name: String,
    @SerialName("ar") val artists: List<Artist> = emptyList(),
    @SerialName("al") val album: Album? = null,
    val cover: String? = null,
    val coverSize: CoverSizes? = null,
    @SerialName("dt") val durationMs: Long = 0,
    val ttml: Int = 0
) {
    val hasTtml: Boolean get() = ttml == 1
    val lyricMid: String
        get() = mid?.takeIf(String::isNotBlank) ?: id

    val bestCoverUrl: String
        get() = cover ?: coverSize?.l ?: coverSize?.m ?: coverSize?.s ?: album?.picUrl ?: ""

    val formattedDuration: String
        get() {
            val sec = durationMs / 1000
            val m = sec / 60
            val s = sec % 60
            val mStr = if (m < 10) "0$m" else "$m"
            val sStr = if (s < 10) "0$s" else "$s"
            return "$mStr:$sStr"
        }
}

@Serializable
data class UserPlaylistsResponse(
    val playlist: List<Playlist> = emptyList(),
    val username: String? = null,
    val avatarUrl: String? = null
)

@Serializable
data class PlaylistDetail(
    val playlist: Playlist
)

@Serializable
data class PlaylistTracksResponse(
    val songs: List<Song> = emptyList()
)

@Serializable
data class SongUrlInfo(
    val id: String,
    val url: String? = null
)

@Serializable
data class SongUrlResponse(
    val data: List<SongUrlInfo> = emptyList()
)

@Serializable
data class LyricResponse(
    val lrc: String? = null,
    val lrctrans: String? = null,
    val qrc: String? = null,
    val qrctrans: String? = null,
    val qrcroma: String? = null
)

@Serializable
data class TtmlLyricResponse(
    val status: String,
    val content: String? = null,
)

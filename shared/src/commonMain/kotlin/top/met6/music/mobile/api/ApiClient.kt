package top.met6.music.mobile.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import top.met6.music.mobile.models.*

@Serializable
data class CreateRoomResponse(
    val code: String? = null
)

@Serializable
data class CheckRoomResponse(
    val exists: Boolean = false
)

@Serializable
data class RenewRoomRequest(
    val code: String
)

@Serializable
data class RenewRoomResponse(
    val status: String? = null,
    @SerialName("expires_at") val expiresAt: Double? = null,
    val detail: String? = null
)

@Serializable
data class ServerTimeResponse(
    @SerialName("server_time") val serverTime: Double
)

val httpClient = HttpClient {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            isLenient = true
        })
    }
    install(WebSockets)
}

object ApiClient {
    private const val BASE_URL = "https://music.met6.top:444/api/web"
    private const val ROOM_BASE_URL = "https://music.met6.top:444/api/room"

    suspend fun getUserPlaylists(uid: String): UserPlaylistsResponse {
        val timestamp = (0..99999999).random().toString()
        return httpClient.get("$BASE_URL/user/playlist") {
            parameter("uid", uid)
            parameter("limit", 100)
            parameter("timestamp", timestamp)
        }.body()
    }

    suspend fun getPlaylistDetail(id: String): PlaylistDetail {
        val timestamp = (0..99999999).random().toString()
        return httpClient.get("$BASE_URL/playlist/detail") {
            parameter("id", id)
            parameter("timestamp", timestamp)
        }.body()
    }

    suspend fun getPlaylistTracks(id: String, limit: Int = 500): PlaylistTracksResponse {
        val timestamp = (0..99999999).random().toString()
        return httpClient.get("$BASE_URL/playlist/track/all") {
            parameter("id", id)
            parameter("limit", limit)
            parameter("timestamp", timestamp)
        }.body()
    }

    suspend fun getSongUrl(id: String, level: String = "hq"): SongUrlResponse {
        val timestamp = (0..99999999).random().toString()
        return httpClient.get("$BASE_URL/song/url/v1") {
            parameter("id", id)
            parameter("level", level)
            parameter("timestamp", timestamp)
        }.body()
    }

    suspend fun getSongLyric(id: String): LyricResponse {
        return httpClient.get("$BASE_URL/lyric/new") {
            parameter("id", id)
        }.body()
    }

    suspend fun getTtmlLyric(mid: String): TtmlLyricResponse {
        return httpClient.get("$BASE_URL/lyric/ttml") {
            parameter("mid", mid)
        }.body()
    }

    // --- Listen Together Room APIs ---
    suspend fun checkRoom(code: String): CheckRoomResponse {
        return httpClient.get("$ROOM_BASE_URL/check") {
            parameter("code", code)
        }.body()
    }

    suspend fun createRoom(): CreateRoomResponse {
        return httpClient.post("$ROOM_BASE_URL/create").body()
    }

    suspend fun renewRoom(code: String): RenewRoomResponse {
        return httpClient.post("$ROOM_BASE_URL/renew") {
            contentType(ContentType.Application.Json)
            setBody(RenewRoomRequest(code))
        }.body()
    }

    suspend fun getServerTime(): ServerTimeResponse {
        return httpClient.get("$ROOM_BASE_URL/time").body()
    }
}

package top.met6.music.mobile.state

import androidx.compose.runtime.mutableStateOf
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.met6.music.mobile.api.ApiClient
import top.met6.music.mobile.api.httpClient
import top.met6.music.mobile.models.Song
import top.met6.music.mobile.models.Artist
import top.met6.music.mobile.models.Album
import top.met6.music.mobile.models.CoverSizes
import top.met6.music.mobile.storage.getCurrentTimeMs
import top.met6.music.mobile.lyric.LyricParser

@Serializable
data class RoomUserPayload(
    val nickname: String,
    val avatar: String,
    val qq: String = "",
    @SerialName("is_anonymous") val isAnonymous: Boolean = false
)

@Serializable
data class RoomMember(
    val userId: String = "",
    val nickname: String = "",
    val avatar: String = "",
    val qq: String = "",
    @SerialName("is_anonymous") val isAnonymous: Boolean = false,
    val user: RoomUserPayload? = null // Retain for backwards compatibility or edge cases
)

@Serializable
data class RoomLog(
    val timestamp: Double = 0.0,
    val user: String = "",
    val action: String = ""
)

@Serializable
data class RoomSong(
    val id: String,
    val mid: String? = null,
    val name: String,
    val artists: List<Artist> = emptyList(),
    val album: Album? = null,
    val cover: String? = null,
    val coverSize: CoverSizes? = null,
    val durationMs: Long = 0,
    val duration: String? = null
)

fun RoomSong.toSong(): Song {
    return Song(
        id = this.id,
        mid = this.mid,
        name = this.name,
        artists = this.artists,
        album = this.album,
        cover = this.cover,
        coverSize = this.coverSize,
        durationMs = if (this.durationMs > 0) this.durationMs else 0L
    )
}

fun Song.toRoomSong(): RoomSong {
    val bestPicUrl = this.bestCoverUrl.takeIf { it.isNotEmpty() }?.replace("http://", "https://")
    val coverStr = bestPicUrl?.let { "$it?param=500y500" }
    val coverSizeObj = bestPicUrl?.let {
        top.met6.music.mobile.models.CoverSizes(
            s = "$it?param=300y300",
            m = "$it?param=500y500",
            l = "$it?param=800y800",
            xl = "$it?param=800y800"
        )
    }

    val sec = durationMs / 1000
    val m = sec / 60
    val s = sec % 60
    val durationStr = "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"

    return RoomSong(
        id = this.id,
        mid = this.mid,
        name = this.name,
        artists = this.artists,
        album = this.album,
        cover = this.cover ?: coverStr,
        coverSize = this.coverSize ?: coverSizeObj,
        durationMs = this.durationMs,
        duration = durationStr
    )
}

@Serializable
data class RoomDetails(
    val code: String,
    val uuid: String,
    val playlist: List<RoomSong> = emptyList(),
    val members: List<RoomMember> = emptyList(),
    @SerialName("current_song_index") val currentSongIndex: Int = -1,
    @SerialName("is_playing") val isPlaying: Boolean = false,
    @SerialName("seek_position") val seekPosition: Double = 0.0,
    @SerialName("play_mode") val playMode: String = "normal",
    @SerialName("delete_after_played") val deleteAfterPlayed: Boolean = false,
    @SerialName("loop_playlist") val loopPlaylist: Boolean = true,
    @SerialName("expires_at") val expiresAt: Double = 0.0,
    val logs: List<RoomLog> = emptyList()
)

@Serializable
data class RoomStatePayload(
    val type: String,
    val event: String? = null,
    @SerialName("server_time") val serverTime: Double? = null,
    val room: RoomDetails? = null,
    val message: String? = null
)

@Serializable
data class WsAction(
    val action: String,
    val userId: String,
    val user: RoomUserPayload? = null,
    val data: WsActionData? = null
)

@Serializable
data class WsActionData(
    val currentTime: Double? = null,
    val song: RoomSong? = null,
    val index: Int? = null,
    val playlist: List<RoomSong>? = null,
    @SerialName("play_mode") val playMode: String? = null,
    val settings: WsActionSettings? = null
)

@Serializable
data class WsActionSettings(
    @SerialName("delete_after_played") val deleteAfterPlayed: Boolean,
    @SerialName("loop_playlist") val loopPlaylist: Boolean
)

object ListenTogetherState {
    private val scope = CoroutineScope(Dispatchers.Main)
    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        isLenient = true
    }
    private var wsSession: DefaultClientWebSocketSession? = null
    
    // Background timers / jobs
    private var heartbeatJob: Job? = null
    private var countdownJob: Job? = null
    private var timeSyncJob: Job? = null
    private var playbackSyncJob: Job? = null
    
    // Observable UI states
    val isInRoom = mutableStateOf(false)
    val roomCode = mutableStateOf("")
    val roomUuid = mutableStateOf("")
    val roomDetails = mutableStateOf<RoomDetails?>(null)
    val remainingTime = mutableStateOf(3600L)
    val autoRenew = mutableStateOf(true)
    val connecting = mutableStateOf(false)
    val errorMessage = mutableStateOf("")
    val successMessage = mutableStateOf("")
    
    private var renewCooldown = false
    private var expectingFirstState = false
    private var serverTimeOffset = 0L
    private var lastRoomStateServerTime = 0L
    private var lastRoomStateLocalTime = 0L
    private var roomPlaybackSongId: String? = null
    private var loadingRoomSongId: String? = null
    private var roomPlaybackGeneration = 0L

    fun getSessionId(): String {
        var sessionId = AppState.platformStorage.getText("sessionId")
        if (sessionId.isNullOrEmpty()) {
            val charPool : List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
            sessionId = (1..32)
                .map { kotlin.random.Random.nextInt(0, charPool.size) }
                .map(charPool::get)
                .joinToString("")
            AppState.platformStorage.saveText("sessionId", sessionId)
        }
        return sessionId
    }

    fun getCurrentUserPayload(): RoomUserPayload {
        val isAnonymousVal = AppState.platformStorage.getText("listen_together_is_anonymous") == "true"
        val nicknameVal = AppState.platformStorage.getText("listen_together_nickname") ?: ""
        
        val appNickname = AppState.nickname.value
        val defaultNickname = if (appNickname != "未登录" && appNickname.isNotEmpty()) appNickname else "Anonymous"
        val nicknameStr = if (nicknameVal.trim().isNotEmpty()) nicknameVal.trim() else defaultNickname
        
        val loggedInQQ = AppState.qqId.value
        val avatarUrlStr = if (isAnonymousVal) {
            "https://music.met6.top:444/app/images/pic/avatar.jpg"
        } else {
            if (loggedInQQ.isNotEmpty()) {
                "https://q1.qlogo.cn/g?b=qq&nk=$loggedInQQ&s=640"
            } else {
                AppState.platformStorage.getText("listen_together_avatar") ?: "https://music.met6.top:444/app/images/pic/avatar.jpg"
            }
        }

        return RoomUserPayload(
            nickname = if (isAnonymousVal) "Anonymous" else nicknameStr,
            avatar = avatarUrlStr,
            qq = if (isAnonymousVal) "" else loggedInQQ,
            isAnonymous = isAnonymousVal
        )
    }

    fun createAndConnectRoom(nickname: String, avatar: String, isAnonymous: Boolean) {
        AppState.platformStorage.saveText("listen_together_nickname", nickname)
        AppState.platformStorage.saveText("listen_together_avatar", avatar)
        AppState.platformStorage.saveText("listen_together_is_anonymous", isAnonymous.toString())
        
        scope.launch {
            try {
                connecting.value = true
                val response = ApiClient.createRoom()
                val code = response.code
                if (!code.isNullOrEmpty()) {
                    connectRoom(code)
                } else {
                    errorMessage.value = "创建房间失败，服务器未返回房间号"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage.value = "创建房间出错: ${e.message}"
            } finally {
                connecting.value = false
            }
        }
    }

    fun joinAndConnectRoom(code: String, nickname: String, avatar: String, isAnonymous: Boolean) {
        if (code.length != 6) {
            errorMessage.value = "请输入6位数房间号"
            return
        }
        AppState.platformStorage.saveText("listen_together_nickname", nickname)
        AppState.platformStorage.saveText("listen_together_avatar", avatar)
        AppState.platformStorage.saveText("listen_together_is_anonymous", isAnonymous.toString())
        
        scope.launch {
            try {
                connecting.value = true
                val response = ApiClient.checkRoom(code)
                if (response.exists) {
                    connectRoom(code)
                } else {
                    errorMessage.value = "房间不存在或已过期"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage.value = "加入房间出错: ${e.message}"
            } finally {
                connecting.value = false
            }
        }
    }

    private suspend fun connectRoom(code: String) {
        errorMessage.value = ""
        expectingFirstState = true
        roomPlaybackSongId = null
        loadingRoomSongId = null
        roomPlaybackGeneration++
        roomCode.value = code
        
        val userId = getSessionId()
        val userPayload = getCurrentUserPayload()
        
        try {
            wsSession = httpClient.webSocketSession {
                url("wss://music.met6.top:444/api/room/ws/$code")
            }
            
            val joinPayload = WsAction(
                action = "join",
                userId = userId,
                user = userPayload
            )
            wsSession?.send(Frame.Text(jsonConfig.encodeToString(joinPayload)))
            isInRoom.value = true
            
            startJobs()
            
            // Listen for incoming messages in a separate job
            scope.launch {
                wsSession?.let { session ->
                    try {
                        for (frame in session.incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                handleWsMessage(text)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        try {
                            val reason = session.closeReason.await()
                            val code = reason?.code?.toInt()
                            if (code == 4004) {
                                handleLocalExit("房间已过期或被关闭")
                            } else {
                                handleLocalExit(reason?.message?.takeIf { it.isNotEmpty() } ?: "连接已断开")
                            }
                        } catch (e: Exception) {
                            handleLocalExit("连接已断开")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            errorMessage.value = "连接房间失败: ${e.message}"
            handleLocalExit()
        }
    }

    private fun handleWsMessage(text: String) {
        try {
            val payload = jsonConfig.decodeFromString<RoomStatePayload>(text)
            if (payload.type == "room_state") {
                val isFirst = expectingFirstState
                expectingFirstState = false
                
                val room = payload.room
                if (room != null) {
                    val now = getCurrentTimeMs()
                    val serverTime = payload.serverTime?.toLong() ?: (now + serverTimeOffset)
                    
                    lastRoomStateServerTime = serverTime
                    lastRoomStateLocalTime = now
                    
                    roomDetails.value = room
                    roomUuid.value = room.uuid
                    
                    if (isFirst) {
                        scope.launch {
                            delay(300)
                            syncPlayback("first_state", serverTime)
                        }
                    } else {
                        syncPlayerState(payload.event ?: "", serverTime)
                    }
                }
            } else if (payload.type == "error") {
                val msg = payload.message ?: "服务器错误"
                errorMessage.value = msg
                if (msg.contains("解散") || msg.contains("到期") || msg.contains("不存在")) {
                    handleLocalExit(msg)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun syncPlayerState(eventType: String, serverTime: Long) {
        val room = roomDetails.value ?: return
        
        val playlistSongs = room.playlist.map { it.toSong() }
        AppState.currentQueue.value = playlistSongs
        AppState.currentIndex.value = room.currentSongIndex
        
        val currentRoomSong = room.playlist.getOrNull(room.currentSongIndex)?.toSong()
        if (currentRoomSong != null) {
            val localPlaySong = AppState.currentSong.value
            val isSameSong = localPlaySong?.id == currentRoomSong.id &&
                roomPlaybackSongId == currentRoomSong.id
            
            val now = getCurrentTimeMs()
            val serverNow = now + serverTimeOffset
            val elapsed = if (room.isPlaying) (serverNow - serverTime) / 1000.0 else 0.0
            if (!isSameSong) {
                AppState.currentSong.value = currentRoomSong
                playRoomSong(currentRoomSong)
            } else {
                if (room.isPlaying && !AppState.player.isPlaying.value) {
                    AppState.player.resume()
                } else if (!room.isPlaying && AppState.player.isPlaying.value) {
                    AppState.player.pause()
                }
                
                val localSeek = AppState.player.currentPositionMs.value / 1000.0
                val targetSeek = room.seekPosition + elapsed
                val thresholdSec = AppState.syncThresholdSec.value
                if (eventType == "seek" || kotlin.math.abs(localSeek - targetSeek) > thresholdSec) {
                    AppState.player.seekTo((targetSeek * 1000).toLong())
                }
            }
        } else {
            if (AppState.player.isPlaying.value || AppState.player.isReady.value) {
                AppState.player.stop()
                AppState.currentSong.value = null
                AppState.lyricLines.value = emptyList()
                AppState.currentLyricIndex.value = -1
            }
        }
    }

    private fun syncPlayback(eventType: String, serverTime: Long) {
        val room = roomDetails.value ?: return
        val currentRoomSong = room.playlist.getOrNull(room.currentSongIndex)?.toSong()
        if (currentRoomSong != null) {
            val playlistSongs = room.playlist.map { it.toSong() }
            AppState.currentQueue.value = playlistSongs
            AppState.currentIndex.value = room.currentSongIndex
            
            val now = getCurrentTimeMs()
            val serverNow = now + serverTimeOffset
            val elapsed = if (room.isPlaying) (serverNow - serverTime) / 1000.0 else 0.0
            val localPlaySong = AppState.currentSong.value
            val isSameSong = localPlaySong?.id == currentRoomSong.id &&
                roomPlaybackSongId == currentRoomSong.id

            if (isSameSong) {
                if (room.isPlaying && !AppState.player.isPlaying.value) {
                    AppState.player.resume()
                } else if (!room.isPlaying && AppState.player.isPlaying.value) {
                    AppState.player.pause()
                }
                
                val localSeek = AppState.player.currentPositionMs.value / 1000.0
                val targetSeek = room.seekPosition + elapsed
                val thresholdSec = AppState.syncThresholdSec.value
                if (eventType == "seek" || kotlin.math.abs(localSeek - targetSeek) > thresholdSec) {
                    AppState.player.seekTo((targetSeek * 1000).toLong())
                }
            } else {
                AppState.currentSong.value = currentRoomSong
                playRoomSong(currentRoomSong)
            }
        }
    }

    private fun playRoomSong(song: Song) {
        if (loadingRoomSongId == song.id) return
        val generation = ++roomPlaybackGeneration
        loadingRoomSongId = song.id
        roomPlaybackSongId = null
        AppState.clearStandaloneStreamingSource()
        AppState.player.stop()
        AppState.currentSong.value = song
        AppState.lyricLines.value = emptyList()
        AppState.currentLyricIndex.value = -1
        
        scope.launch {
            try {
                var audioUrl = AppState.storage.getCachedSongUrl(song.id)
                if (audioUrl == null) {
                    var remoteUrl = AppState.storage.getCachedRemoteUrl(song.id, AppState.soundQuality.value)
                    if (remoteUrl == null) {
                        val urlResponse = ApiClient.getSongUrl(song.id, AppState.soundQuality.value)
                        val rawUrl = urlResponse.data.firstOrNull()?.url
                        if (rawUrl.isNullOrEmpty()) {
                            return@launch
                        }
                        remoteUrl = rawUrl
                        AppState.storage.saveRemoteUrlToCache(song.id, AppState.soundQuality.value, rawUrl)
                    }
                    // Room playback must use a complete local asset. This gives every
                    // participant stable duration and sample-accurate random access.
                    audioUrl = AppState.storage.getSongAudioUrl(song.id, remoteUrl, waitForCache = true)
                }
                
                AppState.mediaSessionController.updateMetadata(
                    title = song.name,
                    artist = song.artists.joinToString(" & ") { it.name },
                    coverUrl = song.bestCoverUrl,
                    durationMs = song.durationMs
                )
                
                if (generation != roomPlaybackGeneration || !isInRoom.value) return@launch
                val latestRoom = roomDetails.value
                val latestSong = latestRoom?.playlist
                    ?.getOrNull(latestRoom.currentSongIndex)
                    ?.toSong()
                if (latestRoom == null || latestSong?.id != song.id) return@launch

                // Download time must not make a participant start several seconds behind.
                // Recalculate the room cursor immediately before opening the local file.
                val serverNow = getCurrentTimeMs() + serverTimeOffset
                val elapsed = if (latestRoom.isPlaying) {
                    (serverNow - lastRoomStateServerTime).coerceAtLeast(0L) / 1000.0
                } else {
                    0.0
                }
                val latestSeekMs = ((latestRoom.seekPosition + elapsed) * 1000).toLong()
                roomPlaybackSongId = song.id
                AppState.player.play(
                    audioUrl,
                    autoPlay = latestRoom.isPlaying,
                    startPositionMs = latestSeekMs,
                )

                AppState.lyricLines.value = AppState.loadLyrics(song)
                AppState.updateCacheSizes()
                AppState.storage.saveSongMetadata(song, AppState.soundQuality.value)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (generation == roomPlaybackGeneration) loadingRoomSongId = null
            }
        }
    }

    fun sendPlayOrPause() {
        val session = wsSession ?: return
        val action = if (AppState.player.isPlaying.value) "pause" else "play"
        scope.launch {
            try {
                val actionPayload = WsAction(
                    action = action,
                    userId = getSessionId(),
                    user = getCurrentUserPayload()
                )
                session.send(Frame.Text(jsonConfig.encodeToString(actionPayload)))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendPlayState(isPlaying: Boolean) {
        val session = wsSession ?: return
        val action = if (isPlaying) "play" else "pause"
        scope.launch {
            try {
                val actionPayload = WsAction(
                    action = action,
                    userId = getSessionId(),
                    user = getCurrentUserPayload()
                )
                session.send(Frame.Text(jsonConfig.encodeToString(actionPayload)))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendNext() {
        val session = wsSession ?: return
        scope.launch {
            try {
                val actionPayload = WsAction(
                    action = "next",
                    userId = getSessionId(),
                    user = getCurrentUserPayload()
                )
                session.send(Frame.Text(jsonConfig.encodeToString(actionPayload)))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendPrev() {
        val session = wsSession ?: return
        scope.launch {
            try {
                val actionPayload = WsAction(
                    action = "prev",
                    userId = getSessionId(),
                    user = getCurrentUserPayload()
                )
                session.send(Frame.Text(jsonConfig.encodeToString(actionPayload)))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendSeek(seekSec: Double) {
        val session = wsSession ?: return
        scope.launch {
            try {
                val actionPayload = WsAction(
                    action = "seek",
                    userId = getSessionId(),
                    user = getCurrentUserPayload(),
                    data = WsActionData(currentTime = seekSec)
                )
                session.send(Frame.Text(jsonConfig.encodeToString(actionPayload)))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addSong(song: Song) {
        val currentPlaylist = roomDetails.value?.playlist ?: emptyList()
        if (currentPlaylist.any { it.id == song.id }) {
            return
        }
        val session = wsSession ?: return
        scope.launch {
            try {
                val actionPayload = WsAction(
                    action = "playlist_add",
                    userId = getSessionId(),
                    user = getCurrentUserPayload(),
                    data = WsActionData(song = song.toRoomSong())
                )
                session.send(Frame.Text(jsonConfig.encodeToString(actionPayload)))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun removeSong(index: Int) {
        val session = wsSession ?: return
        scope.launch {
            try {
                val actionPayload = WsAction(
                    action = "playlist_remove",
                    userId = getSessionId(),
                    user = getCurrentUserPayload(),
                    data = WsActionData(index = index)
                )
                session.send(Frame.Text(jsonConfig.encodeToString(actionPayload)))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun playIndex(index: Int) {
        val session = wsSession ?: return
        scope.launch {
            try {
                val actionPayload = WsAction(
                    action = "play_index",
                    userId = getSessionId(),
                    user = getCurrentUserPayload(),
                    data = WsActionData(index = index)
                )
                session.send(Frame.Text(jsonConfig.encodeToString(actionPayload)))
                delay(100)
                sendPlayState(true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setPlayMode(mode: String) {
        val session = wsSession ?: return
        scope.launch {
            try {
                val actionPayload = WsAction(
                    action = "set_play_mode",
                    userId = getSessionId(),
                    user = getCurrentUserPayload(),
                    data = WsActionData(playMode = mode)
                )
                session.send(Frame.Text(jsonConfig.encodeToString(actionPayload)))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateSettings(deleteAfterPlayed: Boolean, loopPlaylist: Boolean) {
        val session = wsSession ?: return
        scope.launch {
            try {
                val actionPayload = WsAction(
                    action = "update_settings",
                    userId = getSessionId(),
                    user = getCurrentUserPayload(),
                    data = WsActionData(settings = WsActionSettings(deleteAfterPlayed, loopPlaylist))
                )
                session.send(Frame.Text(jsonConfig.encodeToString(actionPayload)))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteRoom() {
        val session = wsSession ?: return
        scope.launch {
            try {
                val actionPayload = WsAction(
                    action = "delete_room",
                    userId = getSessionId(),
                    user = getCurrentUserPayload()
                )
                session.send(Frame.Text(jsonConfig.encodeToString(actionPayload)))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun renewRoom() {
        if (renewCooldown) return
        renewCooldown = true
        scope.launch {
            try {
                val response = ApiClient.renewRoom(roomCode.value)
                if (response.status == "ok" && response.expiresAt != null) {
                    val currentDetails = roomDetails.value
                    if (currentDetails != null) {
                        roomDetails.value = currentDetails.copy(expiresAt = response.expiresAt)
                        // Trigger a WS sync by updating settings with the same values
                        updateSettings(currentDetails.deleteAfterPlayed, currentDetails.loopPlaylist)
                    }
                    successMessage.value = "房间已成功续期"
                    scope.launch {
                        delay(3000)
                        if (successMessage.value == "房间已成功续期") {
                            successMessage.value = ""
                        }
                    }
                } else {
                    errorMessage.value = response.detail ?: "续期失败"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage.value = "续期出错: ${e.message}"
            } finally {
                delay(10000)
                renewCooldown = false
            }
        }
    }

    fun leaveRoom() {
        scope.launch {
            try {
                wsSession?.close()
            } catch (e: Exception) {}
            handleLocalExit()
        }
    }

    fun handleLocalExit(reason: String = "") {
        if (!isInRoom.value) return
        isInRoom.value = false
        roomPlaybackGeneration++
        roomPlaybackSongId = null
        loadingRoomSongId = null
        roomCode.value = ""
        roomUuid.value = ""
        roomDetails.value = null
        
        wsSession = null
        stopJobs()
        
        if (reason.isNotEmpty()) {
            errorMessage.value = reason
        }
        
        if (AppState.player.isPlaying.value || AppState.player.isReady.value) {
            AppState.player.pause()
            AppState.showToast("一起听歌已结束，播放已暂停")
        }
    }

    private fun startJobs() {
        stopJobs()
        
        timeSyncJob = scope.launch {
            while (isActive) {
                try {
                    val t0 = getCurrentTimeMs()
                    val response = ApiClient.getServerTime()
                    val t1 = getCurrentTimeMs()
                    serverTimeOffset = response.serverTime.toLong() - (t0 + t1) / 2
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(10000)
            }
        }
        
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(30000)
                try {
                    val session = wsSession
                    if (session != null) {
                        val pingPayload = WsAction(
                            action = "ping",
                            userId = getSessionId()
                        )
                        session.send(Frame.Text(jsonConfig.encodeToString(pingPayload)))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        countdownJob = scope.launch {
            while (isActive) {
                val room = roomDetails.value
                if (room != null) {
                    val now = getCurrentTimeMs()
                    val diff = kotlin.math.max(0L, ((room.expiresAt - now) / 1000).toLong())
                    remainingTime.value = diff
                    
                    if (diff == 0L) {
                        handleLocalExit("房间已到期")
                    }
                    
                    if (autoRenew.value && diff in 1..1800) {
                        renewRoom()
                    }
                }
                delay(1000)
            }
        }
        
        playbackSyncJob = scope.launch {
            while (isActive) {
                delay(1000)
                checkAndSyncPlayback()
            }
        }
    }

    private fun checkAndSyncPlayback() {
        val room = roomDetails.value ?: return
        if (!room.isPlaying) return
        if (!AppState.player.isPlaying.value) return
        
        val currentRoomSong = room.playlist.getOrNull(room.currentSongIndex) ?: return
        if (AppState.currentSong.value?.id != currentRoomSong.id) return
        
        val now = getCurrentTimeMs()
        val serverNow = now + serverTimeOffset
        val elapsed = (serverNow - lastRoomStateServerTime) / 1000.0
        val targetSeek = room.seekPosition + elapsed
        val localSeek = AppState.player.currentPositionMs.value / 1000.0
        
        val diffMs = kotlin.math.abs(localSeek - targetSeek) * 1000.0
        val threshold = 300.0
        
        if (diffMs > threshold) {
            AppState.player.seekTo((targetSeek * 1000).toLong())
        }
    }

    private fun stopJobs() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        countdownJob?.cancel()
        countdownJob = null
        timeSyncJob?.cancel()
        timeSyncJob = null
        playbackSyncJob?.cancel()
        playbackSyncJob = null
    }
}

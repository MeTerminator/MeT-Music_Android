package top.met6.music.mobile.state

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import top.met6.music.mobile.api.ApiClient
import top.met6.music.mobile.models.Playlist
import top.met6.music.mobile.models.Song
import top.met6.music.mobile.player.AudioPlayer
import top.met6.music.mobile.player.MediaSessionController
import top.met6.music.mobile.player.createAudioPlayer
import top.met6.amll.LyricLine
import top.met6.amll.TtmlParser
import top.met6.music.mobile.lyric.LyricParser
import top.met6.music.mobile.storage.CacheManager
import top.met6.music.mobile.storage.PlatformContext
import top.met6.music.mobile.storage.PlatformStorage
import top.met6.music.mobile.storage.getPlatformStorage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed class Screen {
    object Login : Screen()
    object Playlists : Screen()
    data class PlaylistDetail(val playlist: Playlist) : Screen()
    object Settings : Screen()
    object CacheManagerDetail : Screen()
    object DesktopLyricSettings : Screen()
    object ListenTogether : Screen()
}

enum class PlaybackMode {
    SEQUENCE,
    LOOP_LIST,
    SHUFFLE,
    LOOP_SINGLE
}

object AppState {
    val PRESET_COLORS = listOf(
        "绿色" to Color(0xFF4CAF50),
        "红色" to Color(0xFFE53935),
        "蓝色" to Color(0xFF1E88E5),
        "紫色" to Color(0xFF8E24AA),
        "土黄色" to Color(0xFFD4AF37),
        "黑色" to Color(0xFF000000),
        "白色" to Color(0xFFFFFFFF)
    )
    lateinit var context: PlatformContext
    lateinit var storage: CacheManager
    lateinit var player: AudioPlayer
    lateinit var platformStorage: PlatformStorage
    lateinit var mediaSessionController: MediaSessionController
    private val scope = CoroutineScope(Dispatchers.Main)
    private var isInitialized = false
    private var standaloneStreamingSongId: String? = null
    private var standaloneStreamingUrl: String? = null
    private var accurateSeekGeneration = 0L

    // --- Navigation ---
    val currentScreen = mutableStateOf<Screen>(Screen.Login)
    val navigationStack = mutableListOf<Screen>()

    fun navigateTo(screen: Screen) {
        navigationStack.add(currentScreen.value)
        currentScreen.value = screen
    }

    fun navigateBack(): Boolean {
        if (navigationStack.isNotEmpty()) {
            currentScreen.value = navigationStack.removeAt(navigationStack.size - 1)
            return true
        }
        return false
    }
    
    // --- Global UI ---
    val globalToastMessage = mutableStateOf("")
    private var toastJob: Job? = null
    
    fun showToast(msg: String) {
        globalToastMessage.value = msg
        toastJob?.cancel()
        toastJob = scope.launch {
            kotlinx.coroutines.delay(2000)
            globalToastMessage.value = ""
        }
    }

    // --- User state ---
    val isLoggedIn = mutableStateOf(false)
    val qqId = mutableStateOf("")
    val nickname = mutableStateOf("未登录")
    val avatarUrl = mutableStateOf("")
    val playlists = mutableStateOf<List<Playlist>>(emptyList())
    val userPlaylistsLoading = mutableStateOf(false)

    // --- Playback Queue ---
    val currentQueue = mutableStateOf<List<Song>>(emptyList())
    val currentIndex = mutableStateOf(-1)
    val currentSong = mutableStateOf<Song?>(null)
    val savedPlaybackPositionMs = mutableStateOf(0L)
    val playbackMode = mutableStateOf(PlaybackMode.SEQUENCE)
    
    // --- Audio Details & Lyrics ---
    val lyricLines = mutableStateOf<List<LyricLine>>(emptyList())
    val currentLyricIndex = mutableStateOf(-1)
    val isFullScreenPlayer = mutableStateOf(false)
    val soundQuality = mutableStateOf("hq") // web, hq, sq, rs, dts, q360v1, q360v2, qai
    val syncThresholdSec = mutableStateOf(0.3f)

    // --- Cache sizes ---
    val playlistCacheSize = mutableStateOf(0.0)
    val coverCacheSize = mutableStateOf(0.0)
    val songCacheSize = mutableStateOf(0.0)
    val lyricCacheSize = mutableStateOf(0.0)
    val currentThemeColor = mutableStateOf(Color(0xFF1E1E24))
    val lyricOffsetMs = mutableStateOf(0L)
    val lyricLeadMs = mutableStateOf(300L)
    val lyricFontSize = mutableStateOf(20)
    val translationFontSize = mutableStateOf(14)
    val useSpotifyFont = mutableStateOf(false)

    // --- Desktop Lyric Settings ---
    val desktopLyricEnabled = mutableStateOf(false)
    val desktopLyricClickPlayPause = mutableStateOf(false)
    val desktopLyricShowWhenPaused = mutableStateOf(false)
    val desktopLyricShowContent = mutableStateOf("lyric") // lyric, song_name
    val desktopLyricAlignment = mutableStateOf("center") // left, center
    val desktopLyricX = mutableStateOf(50f)
    val desktopLyricY = mutableStateOf(3f)
    val desktopLyricWidth = mutableStateOf(90f)
    val desktopLyricOpacity = mutableStateOf(90f)
    val desktopLyricFontSize = mutableStateOf(16f)
    val desktopLyricColorName = mutableStateOf("绿色")

    fun savePlaybackState() {
        try {
            val queue = currentQueue.value
            val index = currentIndex.value
            val pos = player.currentPositionMs.value
            
            platformStorage.saveText("playback_queue", Json.encodeToString(queue))
            platformStorage.saveText("playback_index", index.toString())
            val posToSave = if (pos > 0) pos else savedPlaybackPositionMs.value
            platformStorage.saveText("playback_position", posToSave.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadSavedPlaybackState() {
        val savedQueueJson = platformStorage.getText("playback_queue")
        val savedIndexStr = platformStorage.getText("playback_index")
        val savedPosStr = platformStorage.getText("playback_position")
        
        if (!savedQueueJson.isNullOrEmpty() && !savedIndexStr.isNullOrEmpty()) {
            try {
                val queue = Json.decodeFromString<List<Song>>(savedQueueJson)
                val index = savedIndexStr.toInt()
                if (index in queue.indices) {
                    currentQueue.value = queue
                    currentIndex.value = index
                    val song = queue[index]
                    currentSong.value = song
                    
                    val pos = savedPosStr?.toLongOrNull() ?: 0L
                    savedPlaybackPositionMs.value = pos
                    
                    // Pre-load lyrics for saved song
                    scope.launch {
                        try {
                            lyricLines.value = loadLyrics(song)
                            updateLyricIndex(pos)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        if (ListenTogetherState.isInRoom.value) {
            ListenTogetherState.sendSeek(positionMs / 1000.0)
            return
        }
        savedPlaybackPositionMs.value = positionMs
        platformStorage.saveText("playback_position", positionMs.toString())
        updateLyricIndex(positionMs)

        val song = currentSong.value
        val remoteUrl = standaloneStreamingUrl
        val needsLocalAsset = player.accurateSeekRequiresLocalFile &&
            song != null &&
            standaloneStreamingSongId == song.id &&
            !remoteUrl.isNullOrBlank()
        if (!needsLocalAsset) {
            player.seekTo(positionMs)
            return
        }

        // AVFoundation may report the requested time while a progressively downloaded
        // MP3 is decoding from an approximate byte offset. Stop audible playback until
        // the already-running background cache provides a fully indexable local asset.
        val generation = ++accurateSeekGeneration
        val shouldResume = player.isPlaying.value
        player.pause()
        scope.launch {
            try {
                val localUrl = storage.getSongAudioUrl(
                    songId = song.id,
                    remoteUrl = remoteUrl,
                    waitForCache = true,
                )
                if (generation != accurateSeekGeneration ||
                    currentSong.value?.id != song.id ||
                    ListenTogetherState.isInRoom.value
                ) return@launch

                // Future seeks can now use AVPlayer directly against the complete file.
                standaloneStreamingUrl = null
                player.play(
                    urlOrPath = localUrl,
                    autoPlay = shouldResume,
                    startPositionMs = positionMs,
                )
            } catch (error: Exception) {
                error.printStackTrace()
                if (generation == accurateSeekGeneration && currentSong.value?.id == song.id) {
                    // Preserve basic seeking if caching genuinely fails.
                    player.seekTo(positionMs)
                    if (shouldResume) player.resume()
                    showToast("精确跳转缓存失败，已使用串流跳转")
                }
            }
        }
    }

    fun clearStandaloneStreamingSource() {
        accurateSeekGeneration++
        standaloneStreamingSongId = null
        standaloneStreamingUrl = null
    }

    fun init(context: PlatformContext) {
        if (isInitialized) return
        isInitialized = true
        this.context = context

        val storageImpl = getPlatformStorage(context)
        platformStorage = storageImpl
        storage = CacheManager(storageImpl)
        
        mediaSessionController = MediaSessionController(context)
        mediaSessionController.setCallbacks(
            onPlay = { resumeOrPlay() },
            onPause = { player.pause() },
            onNext = { nextSong() },
            onPrev = { prevSong() }
        )

        player = createAudioPlayer(context)
        player.onPlaybackComplete = {
            scope.launch {
                if (ListenTogetherState.isInRoom.value) {
                    ListenTogetherState.sendNext()
                } else {
                    nextSong(isAutoPlay = true)
                }
            }
        }

        // Read saved configs
        val savedQq = platformStorage.getText("qq_id")
        val savedQuality = platformStorage.getText("sound_quality")
        val savedMode = platformStorage.getText("playback_mode")
        val supportedQualities = setOf("web", "hq", "sq", "rs", "dts", "q360v1", "q360v2", "qai")
        if (!savedQuality.isNullOrEmpty() && supportedQualities.contains(savedQuality)) {
            soundQuality.value = savedQuality
        } else {
            soundQuality.value = "hq"
        }
        if (!savedMode.isNullOrEmpty()) {
            try {
                playbackMode.value = PlaybackMode.valueOf(savedMode)
            } catch (e: Exception) {}
        }
        
        val savedSyncThreshold = platformStorage.getText("sync_threshold")
        if (!savedSyncThreshold.isNullOrEmpty()) {
            syncThresholdSec.value = savedSyncThreshold.toFloatOrNull() ?: 0.3f
        } else {
            syncThresholdSec.value = 0.3f
        }

        val savedOffset = platformStorage.getText("lyric_offset")
        if (!savedOffset.isNullOrEmpty()) {
            lyricOffsetMs.value = savedOffset.toLongOrNull() ?: 0L
        } else {
            lyricOffsetMs.value = 0L
        }

        val savedLead = platformStorage.getText("lyric_lead")
        if (!savedLead.isNullOrEmpty()) {
            lyricLeadMs.value = savedLead.toLongOrNull() ?: 300L
        } else {
            lyricLeadMs.value = 300L
        }

        val savedLyricSize = platformStorage.getText("lyric_font_size")
        if (!savedLyricSize.isNullOrEmpty()) {
            lyricFontSize.value = savedLyricSize.toIntOrNull() ?: 20
        } else {
            lyricFontSize.value = 20
        }

        val savedTranslationSize = platformStorage.getText("translation_font_size")
        if (!savedTranslationSize.isNullOrEmpty()) {
            translationFontSize.value = savedTranslationSize.toIntOrNull() ?: 14
        } else {
            translationFontSize.value = 14
        }

        val savedSpotifyFont = platformStorage.getText("use_spotify_font")
        if (!savedSpotifyFont.isNullOrEmpty()) {
            useSpotifyFont.value = savedSpotifyFont.toBoolean()
        } else {
            useSpotifyFont.value = false
        }

        // Read saved desktop lyric configs
        val savedDesktopLyric = platformStorage.getText("desktop_lyric_enabled")?.toBoolean() ?: false
        desktopLyricEnabled.value = savedDesktopLyric
        if (savedDesktopLyric && top.met6.music.mobile.lyric.checkOverlayPermission(context)) {
            top.met6.music.mobile.lyric.startFloatingLyricService(context)
        }

        desktopLyricClickPlayPause.value = platformStorage.getText("desktop_lyric_click_play_pause")?.toBoolean() ?: false
        desktopLyricShowWhenPaused.value = platformStorage.getText("desktop_lyric_show_when_paused")?.toBoolean() ?: false
        desktopLyricShowContent.value = platformStorage.getText("desktop_lyric_show_content") ?: "lyric"
        desktopLyricAlignment.value = platformStorage.getText("desktop_lyric_alignment") ?: "center"
        desktopLyricX.value = platformStorage.getText("desktop_lyric_x")?.toFloatOrNull() ?: 50f
        desktopLyricY.value = platformStorage.getText("desktop_lyric_y")?.toFloatOrNull() ?: 3f
        desktopLyricWidth.value = platformStorage.getText("desktop_lyric_width")?.toFloatOrNull() ?: 90f
        desktopLyricOpacity.value = platformStorage.getText("desktop_lyric_opacity")?.toFloatOrNull() ?: 90f
        desktopLyricFontSize.value = platformStorage.getText("desktop_lyric_font_size")?.toFloatOrNull() ?: 16f
        desktopLyricColorName.value = platformStorage.getText("desktop_lyric_color_name") ?: "绿色"

        // Restore last playback state
        loadSavedPlaybackState()

        if (!savedQq.isNullOrEmpty()) {
            qqId.value = savedQq
            isLoggedIn.value = true
            currentScreen.value = Screen.Playlists
            loadUserPlaylists(savedQq)
        }

        // Start progress & lyrics tracker
        var ticks = 0
        var wasPlaying = false
        scope.launch {
            while (true) {
                val isPlaying = player.isPlaying.value
                if (isPlaying) {
                    val pos = player.currentPositionMs.value
                    updateLyricIndex(pos)
                    mediaSessionController.updatePlaybackState(true, pos)
                    
                    ticks++
                    if (ticks >= 150) { // Persist progress to disk approx every 4.5 seconds
                        platformStorage.saveText("playback_position", pos.toString())
                        ticks = 0
                    }
                } else if (wasPlaying) {
                    // Transitioned from playing to paused/stopped: write status immediately
                    platformStorage.saveText("playback_position", player.currentPositionMs.value.toString())
                    mediaSessionController.updatePlaybackState(false, player.currentPositionMs.value)
                }
                wasPlaying = isPlaying
                kotlinx.coroutines.delay(30)
            }
        }
        updateCacheSizes()
    }

    fun login(qqNumber: String, onResult: (Boolean, String) -> Unit) {
        if (qqNumber.trim().isEmpty()) {
            onResult(false, "请输入QQ号")
            return
        }
        userPlaylistsLoading.value = true
        scope.launch {
            try {
                // Fetch from network
                val response = ApiClient.getUserPlaylists(qqNumber)
                if (response.playlist.isNotEmpty()) {
                    // Success!
                    storage.savePlaylistsToCache(qqNumber, response.playlist)
                    
                    platformStorage.saveText("qq_id", qqNumber)
                    platformStorage.saveText("nickname", response.username ?: qqNumber)
                    platformStorage.saveText("avatar_url", response.avatarUrl ?: "")

                    qqId.value = qqNumber
                    nickname.value = response.username ?: qqNumber
                    avatarUrl.value = response.avatarUrl ?: ""
                    playlists.value = response.playlist
                    isLoggedIn.value = true
                    
                    userPlaylistsLoading.value = false
                    onResult(true, "登录成功")
                    navigateTo(Screen.Playlists)
                    updateCacheSizes()
                } else {
                    userPlaylistsLoading.value = false
                    onResult(false, "没有找到该用户的歌单")
                }
            } catch (e: Exception) {
                // Fallback to cache if available
                val cached = storage.getPlaylistsFromCache(qqNumber)
                if (cached != null) {
                    qqId.value = qqNumber
                    nickname.value = platformStorage.getText("nickname") ?: qqNumber
                    avatarUrl.value = platformStorage.getText("avatar_url") ?: ""
                    playlists.value = cached
                    isLoggedIn.value = true

                    userPlaylistsLoading.value = false
                    onResult(true, "网络已断开，已载入本地缓存歌单")
                    navigateTo(Screen.Playlists)
                } else {
                    userPlaylistsLoading.value = false
                    onResult(false, "登录失败，请检查网络或QQ号: ${e.message}")
                }
            }
        }
    }

    fun logout() {
        platformStorage.clearText("qq_id")
        platformStorage.clearText("nickname")
        platformStorage.clearText("avatar_url")
        
        isLoggedIn.value = false
        qqId.value = ""
        nickname.value = "未登录"
        avatarUrl.value = ""
        playlists.value = emptyList()
        currentQueue.value = emptyList()
        currentIndex.value = -1
        currentSong.value = null
        player.stop()

        navigationStack.clear()
        currentScreen.value = Screen.Login
    }

    fun loadUserPlaylists(qq: String) {
        userPlaylistsLoading.value = true
        
        // 1. Immediately display cached text info synchronously
        val cachedNickname = platformStorage.getText("nickname")
        if (!cachedNickname.isNullOrEmpty()) {
            nickname.value = cachedNickname
        } else {
            nickname.value = qq
        }
        val cachedAvatar = platformStorage.getText("avatar_url")
        if (!cachedAvatar.isNullOrEmpty()) {
            avatarUrl.value = cachedAvatar
        }

        scope.launch {
            // 2. Fetch and display cached playlists first
            try {
                val cached = storage.getPlaylistsFromCache(qq)
                if (cached != null) {
                    playlists.value = cached
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 3. Fetch from network and refresh
            try {
                val response = ApiClient.getUserPlaylists(qq)
                storage.savePlaylistsToCache(qq, response.playlist)
                
                platformStorage.saveText("nickname", response.username ?: qq)
                platformStorage.saveText("avatar_url", response.avatarUrl ?: "")

                playlists.value = response.playlist
                nickname.value = response.username ?: qq
                avatarUrl.value = response.avatarUrl ?: ""
            } catch (e: Exception) {
                // Network error: we keep showing the cached data that was loaded earlier.
                e.printStackTrace()
            } finally {
                userPlaylistsLoading.value = false
            }
        }
    }
    fun setSoundQualityLevel(quality: String) {
        soundQuality.value = quality
        platformStorage.saveText("sound_quality", quality)
        
        // If playing, reload the song with new quality
        currentSong.value?.let { playSong(it) }
    }

    fun setPlaybackMode(mode: PlaybackMode) {
        playbackMode.value = mode
        platformStorage.saveText("playback_mode", mode.name)
    }

    fun setLyricOffset(offsetMs: Long) {
        lyricOffsetMs.value = offsetMs
        platformStorage.saveText("lyric_offset", offsetMs.toString())
    }

    fun setLyricLead(leadMs: Long) {
        lyricLeadMs.value = leadMs
        platformStorage.saveText("lyric_lead", leadMs.toString())
    }

    fun setLyricFontSize(size: Int) {
        lyricFontSize.value = size
        platformStorage.saveText("lyric_font_size", size.toString())
    }

    fun setTranslationFontSize(size: Int) {
        translationFontSize.value = size
        platformStorage.saveText("translation_font_size", size.toString())
    }

    fun setUseSpotifyFont(enabled: Boolean) {
        useSpotifyFont.value = enabled
        platformStorage.saveText("use_spotify_font", enabled.toString())
    }

    fun updateCacheSizes() {
        scope.launch {
            playlistCacheSize.value = storage.getCacheSizeMb("playlists")
            coverCacheSize.value = storage.getCacheSizeMb("covers")
            songCacheSize.value = storage.getCacheSizeMb("songs")
            lyricCacheSize.value = storage.getCacheSizeMb("lyrics")
        }
    }

    fun clearCacheCategory(category: String) {
        scope.launch {
            storage.clearCacheCategory(category)
            if (category == "songs") {
                storage.clearCacheCategory("songs_metadata")
            }
            updateCacheSizes()
            if (category == "playlists" && isLoggedIn.value) {
                loadUserPlaylists(qqId.value)
            }
        }
    }

    // --- Audio Playback Functions ---
 
    fun playPlaylist(songsList: List<Song>, startIndex: Int) {
        if (ListenTogetherState.isInRoom.value) {
            val song = songsList.getOrNull(startIndex)
            if (song != null) {
                ListenTogetherState.addSong(song)
                showToast("已添加到一起听歌播放列表")
            }
            return
        }
        if (songsList.isEmpty()) return
        savedPlaybackPositionMs.value = 0L
        currentQueue.value = songsList
        playSongAtIndex(startIndex)
    }
 
    fun playSongAtIndex(index: Int) {
        val queue = currentQueue.value
        if (index < 0 || index >= queue.size) return
        if (index != currentIndex.value) {
            savedPlaybackPositionMs.value = 0L
        }
        currentIndex.value = index
        val song = queue[index]
        currentSong.value = song
        playSong(song)
    }
 
    fun nextSong(isAutoPlay: Boolean = false) {
        if (ListenTogetherState.isInRoom.value) {
            if (!isAutoPlay) {
                ListenTogetherState.sendNext()
            }
            return
        }
        val queue = currentQueue.value
        if (queue.isEmpty()) return
        savedPlaybackPositionMs.value = 0L
        
        var nextIdx = currentIndex.value
        when (playbackMode.value) {
            PlaybackMode.SEQUENCE -> {
                nextIdx++
                if (nextIdx >= queue.size) {
                    if (isAutoPlay) {
                        player.stop()
                        return
                    }
                    nextIdx = 0
                }
            }
            PlaybackMode.LOOP_LIST -> {
                nextIdx = (nextIdx + 1) % queue.size
            }
            PlaybackMode.LOOP_SINGLE -> {
                if (!isAutoPlay) {
                    nextIdx = (nextIdx + 1) % queue.size
                }
            }
            PlaybackMode.SHUFFLE -> {
                if (queue.size > 1) {
                    var rand = queue.indices.random()
                    while (rand == currentIndex.value) {
                        rand = queue.indices.random()
                    }
                    nextIdx = rand
                } else {
                    nextIdx = 0
                }
            }
        }
        playSongAtIndex(nextIdx)
    }
 
    fun prevSong() {
        if (ListenTogetherState.isInRoom.value) {
            ListenTogetherState.sendPrev()
            return
        }
        val queue = currentQueue.value
        if (queue.isEmpty()) return
        savedPlaybackPositionMs.value = 0L
        
        var prevIdx = currentIndex.value
        when (playbackMode.value) {
            PlaybackMode.SHUFFLE -> {
                if (queue.size > 1) {
                    var rand = queue.indices.random()
                    while (rand == currentIndex.value) {
                        rand = queue.indices.random()
                    }
                    prevIdx = rand
                } else {
                    prevIdx = 0
                }
            }
            else -> {
                prevIdx--
                if (prevIdx < 0) prevIdx = queue.size - 1
            }
        }
        playSongAtIndex(prevIdx)
    }
 
    fun resumeOrPlay() {
        if (ListenTogetherState.isInRoom.value) {
            ListenTogetherState.sendPlayOrPause()
            return
        }
        val song = currentSong.value
        if (song != null && !player.isReady.value) {
            playSong(song)
        } else {
            player.resume()
        }
    }

    fun togglePlayPause() {
        if (ListenTogetherState.isInRoom.value) {
            ListenTogetherState.sendPlayOrPause()
        } else {
            if (player.isPlaying.value) {
                player.pause()
            } else {
                resumeOrPlay()
            }
        }
    }

    private fun playSong(song: Song) {
        clearStandaloneStreamingSource()
        player.stop()
        lyricLines.value = emptyList()
        currentLyricIndex.value = -1
 
        scope.launch {
            try {
                // Standalone playback always starts from the network immediately.
                // Caching runs in the background and never delays AVPlayer preparation.
                var remoteUrl = storage.getCachedRemoteUrl(song.id, soundQuality.value)
                if (remoteUrl == null) {
                    val urlResponse = ApiClient.getSongUrl(song.id, soundQuality.value)
                    val rawUrl = urlResponse.data.firstOrNull()?.url
                    if (rawUrl.isNullOrEmpty()) {
                        nextSong() // skip on error
                        return@launch
                    }
                    remoteUrl = rawUrl
                    storage.saveRemoteUrlToCache(song.id, soundQuality.value, rawUrl)
                }
                val audioUrl = storage.getSongAudioUrl(song.id, remoteUrl, waitForCache = false)
                standaloneStreamingSongId = song.id
                standaloneStreamingUrl = remoteUrl

                mediaSessionController.updateMetadata(
                    title = song.name,
                    artist = song.artists.joinToString(" & ") { it.name },
                    coverUrl = song.bestCoverUrl,
                    durationMs = song.durationMs
                )

                // Hand the remote asset to the platform player before lyrics, cache
                // metrics, or preloading perform any additional network/disk work.
                val startupPos = savedPlaybackPositionMs.value
                player.play(audioUrl, startPositionMs = startupPos)
                if (startupPos > 0L) savedPlaybackPositionMs.value = 0L

                // Lyrics are independent of audio preparation and can arrive later.
                lyricLines.value = loadLyrics(song)
                updateCacheSizes()

                // Save song metadata for cache management
                storage.saveSongMetadata(song, soundQuality.value)

                // Preload the next song
                val next = getNextSong()
                if (next != null) {
                    preloadSong(next)
                }
 
                savePlaybackState()
            } catch (e: Exception) {
                e.printStackTrace()
                nextSong() // skip on error
            }
        }
    }

    fun getNextSong(): Song? {
        val queue = currentQueue.value
        if (queue.isEmpty()) return null
        val currentIndexVal = currentIndex.value
        if (currentIndexVal < 0 || currentIndexVal >= queue.size) return null
        
        var nextIdx = currentIndexVal
        when (playbackMode.value) {
            PlaybackMode.SEQUENCE -> {
                nextIdx++
                if (nextIdx >= queue.size) {
                    nextIdx = 0
                }
            }
            PlaybackMode.LOOP_LIST -> {
                nextIdx = (nextIdx + 1) % queue.size
            }
            PlaybackMode.LOOP_SINGLE -> {
                nextIdx = (nextIdx + 1) % queue.size
            }
            PlaybackMode.SHUFFLE -> {
                nextIdx = (nextIdx + 1) % queue.size
            }
        }
        if (nextIdx in queue.indices) {
            return queue[nextIdx]
        }
        return null
    }

    fun preloadSong(song: Song) {
        scope.launch {
            try {
                // 1. Preload Cover Image
                if (song.bestCoverUrl.isNotEmpty()) {
                    storage.getCoverImageUrl(song.id, song.bestCoverUrl)
                }

                // 2. Save Song Metadata
                storage.saveSongMetadata(song, soundQuality.value)

                // 3. Preload Audio Link & File
                val cachedLocalUrl = storage.getCachedSongUrl(song.id)
                if (cachedLocalUrl == null) {
                    var remoteUrl = storage.getCachedRemoteUrl(song.id, soundQuality.value)
                    if (remoteUrl == null) {
                        val urlResponse = ApiClient.getSongUrl(song.id, soundQuality.value)
                        val rawUrl = urlResponse.data.firstOrNull()?.url
                        if (!rawUrl.isNullOrEmpty()) {
                            remoteUrl = rawUrl
                            storage.saveRemoteUrlToCache(song.id, soundQuality.value, rawUrl)
                        }
                    }
                    if (remoteUrl != null) {
                        storage.getSongAudioUrl(song.id, remoteUrl, waitForCache = false)
                    }
                }

                // 4. Preload Lyrics
                try {
                    var lyricResponse = storage.getLyricFromCache(song.id)
                    if (lyricResponse == null) {
                        lyricResponse = ApiClient.getSongLyric(song.id)
                        storage.saveLyricToCache(song.id, lyricResponse)
                    }
                    // Keep the same request order as playback: ordinary lyric first, TTML second.
                    ApiClient.getTtmlLyric(song.lyricMid)
                } catch (e: Exception) {
                    // Ignore lyric preloading errors
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
 
    suspend fun loadLyrics(song: Song): List<LyricLine> {
        var ordinaryResponse = storage.getLyricFromCache(song.id)
        if (ordinaryResponse == null) {
            ordinaryResponse = ApiClient.getSongLyric(song.id)
            storage.saveLyricToCache(song.id, ordinaryResponse)
        }
        val ordinaryLines = LyricParser.parse(ordinaryResponse)

        return try {
            val ttmlResponse = ApiClient.getTtmlLyric(song.lyricMid)
            if (ttmlResponse.status == "success" && !ttmlResponse.content.isNullOrBlank()) {
                TtmlParser.parseAmll(ttmlResponse.content).lines.ifEmpty { ordinaryLines }
            } else {
                ordinaryLines
            }
        } catch (error: Exception) {
            error.printStackTrace()
            ordinaryLines
        }
    }

    private fun updateLyricIndex(currentTimeMs: Long) {
        val adjustedTime = currentTimeMs + lyricOffsetMs.value + lyricLeadMs.value // dynamic offset + dynamic early transition lead
        val lines = lyricLines.value
        if (lines.isEmpty()) return
        
        var index = -1
        for (i in lines.indices) {
            val line = lines[i]
            if (adjustedTime >= line.startTime && adjustedTime <= line.endTime) {
                index = i
                break
            } else if (adjustedTime < line.startTime) {
                // If it is before the first line or between lines
                if (i > 0) {
                    index = i - 1
                }
                break
            }
        }
        if (index == -1 && adjustedTime >= lines.last().endTime) {
            index = lines.size - 1
        }
        currentLyricIndex.value = index
    }

    fun setDesktopLyricEnabled(enabled: Boolean) {
        desktopLyricEnabled.value = enabled
        platformStorage.saveText("desktop_lyric_enabled", enabled.toString())
        if (enabled) {
            if (top.met6.music.mobile.lyric.checkOverlayPermission(context)) {
                top.met6.music.mobile.lyric.startFloatingLyricService(context)
            } else {
                desktopLyricEnabled.value = false
                platformStorage.saveText("desktop_lyric_enabled", "false")
                top.met6.music.mobile.lyric.requestOverlayPermission(context)
            }
        } else {
            top.met6.music.mobile.lyric.stopFloatingLyricService(context)
        }
    }

    fun setDesktopLyricClickPlayPause(enabled: Boolean) {
        desktopLyricClickPlayPause.value = enabled
        platformStorage.saveText("desktop_lyric_click_play_pause", enabled.toString())
    }

    fun setDesktopLyricShowWhenPaused(enabled: Boolean) {
        desktopLyricShowWhenPaused.value = enabled
        platformStorage.saveText("desktop_lyric_show_when_paused", enabled.toString())
    }

    fun setDesktopLyricShowContent(content: String) {
        desktopLyricShowContent.value = content
        platformStorage.saveText("desktop_lyric_show_content", content)
    }

    fun setDesktopLyricAlignment(alignment: String) {
        desktopLyricAlignment.value = alignment
        platformStorage.saveText("desktop_lyric_alignment", alignment)
    }

    fun setDesktopLyricX(x: Float) {
        desktopLyricX.value = x
        platformStorage.saveText("desktop_lyric_x", x.toString())
    }

    fun setDesktopLyricY(y: Float) {
        desktopLyricY.value = y
        platformStorage.saveText("desktop_lyric_y", y.toString())
    }

    fun setDesktopLyricWidth(w: Float) {
        desktopLyricWidth.value = w
        platformStorage.saveText("desktop_lyric_width", w.toString())
    }

    fun setDesktopLyricOpacity(o: Float) {
        desktopLyricOpacity.value = o
        platformStorage.saveText("desktop_lyric_opacity", o.toString())
    }

    fun setDesktopLyricFontSize(size: Float) {
        desktopLyricFontSize.value = size
        platformStorage.saveText("desktop_lyric_font_size", size.toString())
    }

    fun setDesktopLyricColorName(name: String) {
        desktopLyricColorName.value = name
        platformStorage.saveText("desktop_lyric_color_name", name)
    }
}

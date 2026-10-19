package top.met6.music.mobile.state

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import top.met6.music.mobile.api.ApiClient
import top.met6.music.mobile.models.MusicPlatform
import top.met6.music.mobile.models.Playlist
import top.met6.music.mobile.models.Song
import top.met6.music.mobile.player.AudioPlayer
import top.met6.music.mobile.player.MediaSessionController
import top.met6.music.mobile.player.createAudioPlayer
import top.met6.amll.LyricLine
import top.met6.amll.TtmlParser
import top.met6.music.mobile.models.TtmlLyricResponse
import top.met6.music.mobile.lyric.LyricFormat
import top.met6.music.mobile.lyric.LyricParser
import top.met6.music.mobile.storage.CacheManager
import top.met6.music.mobile.storage.PlatformContext
import top.met6.music.mobile.storage.PlatformStorage
import top.met6.music.mobile.storage.getPlatformStorage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed class Screen {
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
    private var playbackRequest = 0L

    // --- Navigation ---
    val currentScreen = mutableStateOf<Screen>(Screen.Playlists)
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
            kotlinx.coroutines.delay(3000)
            globalToastMessage.value = ""
        }
    }

    // --- User state ---
    val selectedPlatform = mutableStateOf(MusicPlatform.QQ)
    val accountIds = mutableStateOf<Map<MusicPlatform, String>>(emptyMap())
    val neteaseQuality = mutableStateOf("exhigh")
    fun qualityFor(song: Song): String = if (song.source == MusicPlatform.NETEASE) neteaseQuality.value else soundQuality.value
    private var accountRequest = 0
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
    val lyricFormats = mutableStateOf<Map<String, LyricFormat>>(emptyMap())
    fun currentLyricFormat(): LyricFormat = lyricFormats.value[currentSong.value?.cacheKey] ?: LyricFormat.LRC
    val lyricLines = mutableStateOf<List<LyricLine>>(emptyList())
    val ttmlSongIds = mutableStateOf<Set<String>>(emptySet())
    val ttmlAuthor = mutableStateOf<String?>(null)
    val currentLyricIndex = mutableStateOf(-1)
    val isFullScreenPlayer = mutableStateOf(false)
    val fullScreenPlayerPage = mutableStateOf(0)
    val soundQuality = mutableStateOf("hq") // web, hq, sq, rs, dts, q360v1, q360v2, qai
    val syncThresholdSec = mutableStateOf(0.3f)

    // --- Cache sizes ---
    val playlistCacheSize = mutableStateOf(0.0)
    val coverCacheSize = mutableStateOf(0.0)
    val songCacheSize = mutableStateOf(0.0)
    val lyricCacheSize = mutableStateOf(0.0)
    val currentThemeColor = mutableStateOf(Color(0xFF1E1E24))
    val lyricOffsetMs = mutableStateOf(0L)
    val lyricLeadMs = mutableStateOf(100L)
    val lyricFontSize = mutableStateOf(32)
    val translationFontSize = mutableStateOf(16)
    val useSpotifyFont = mutableStateOf(false)
    val removeQrcSongInfo = mutableStateOf(true)

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
            standaloneStreamingSongId == song.cacheKey &&
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
                    songId = song.audioKey(qualityFor(song)),
                    remoteUrl = remoteUrl,
                    waitForCache = true,
                )
                if (generation != accurateSeekGeneration ||
                    currentSong.value?.cacheKey != song.cacheKey ||
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
                if (generation == accurateSeekGeneration && currentSong.value?.cacheKey == song.cacheKey) {
                    // Preserve basic seeking if caching genuinely fails.
                    player.seekTo(positionMs)
                    if (shouldResume) player.resume()
                    showToast("精确跳转缓存失败，已使用串流跳转")
                }
            }
        }
    }

    fun clearStandaloneStreamingSource() {
        playbackRequest++
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
        neteaseQuality.value = platformStorage.getText("netease_sound_quality") ?: "exhigh"
        accountIds.value = MusicPlatform.entries.mapNotNull { platform ->
            platformStorage.getText(if (platform == MusicPlatform.QQ) "qq_id" else "netease_id")
                ?.takeIf { it.isNotBlank() }?.let { platform to it }
        }.toMap()
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
            lyricLeadMs.value = savedLead.toLongOrNull() ?: 100L
        } else {
            lyricLeadMs.value = 100L
        }

        val savedLyricSize = platformStorage.getText("lyric_font_size")
        if (!savedLyricSize.isNullOrEmpty()) {
            lyricFontSize.value = savedLyricSize.toIntOrNull() ?: 32
        } else {
            lyricFontSize.value = 32
        }

        val savedTranslationSize = platformStorage.getText("translation_font_size")
        if (!savedTranslationSize.isNullOrEmpty()) {
            translationFontSize.value = savedTranslationSize.toIntOrNull() ?: 16
        } else {
            translationFontSize.value = 16
        }

        val savedSpotifyFont = platformStorage.getText("use_spotify_font")
        if (!savedSpotifyFont.isNullOrEmpty()) {
            useSpotifyFont.value = savedSpotifyFont.toBoolean()
        } else {
            useSpotifyFont.value = false
        }

        removeQrcSongInfo.value = platformStorage
            .getText("remove_qrc_song_info")
            ?.toBoolean()
            ?: true

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

        if (savedQq.isNullOrEmpty() && accountIds.value.containsKey(MusicPlatform.NETEASE)) selectPlatform(MusicPlatform.NETEASE)
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

    fun selectPlatform(platform: MusicPlatform) {
        accountRequest++
        selectedPlatform.value = platform
        qqId.value = accountIds.value[platform].orEmpty()
        isLoggedIn.value = qqId.value.isNotEmpty()
        nickname.value = platformStorage.getText("${platform.apiValue}_nickname") ?: qqId.value.ifEmpty { "未登录" }
        avatarUrl.value = platformStorage.getText("${platform.apiValue}_avatar").orEmpty()
        playlists.value = emptyList()
        userPlaylistsLoading.value = false
        if (qqId.value.isNotEmpty()) loadUserPlaylists(qqId.value)
    }

    fun login(qqNumber: String, onResult: (Boolean, String) -> Unit) =
        loginAccount(selectedPlatform.value, qqNumber, onResult)

    fun loginAccount(platform: MusicPlatform, input: String, onResult: (Boolean, String) -> Unit) {
        val uid = input.trim()
        if (uid.isEmpty() || uid.any { it !in '0'..'9' }) {
            onResult(false, "请输入有效的数字用户 ID")
            return
        }
        scope.launch {
            try {
                val response = ApiClient.getUserPlaylists(uid, platform)
                if (response.code != 200 || response.username.isNullOrBlank()) {
                    onResult(false, "没有找到该用户的公开资料和歌单")
                    return@launch
                }
                val lists = response.playlist.map { it.copy(source = platform) }
                storage.savePlaylistsToCache(platform.key(uid), lists)
                platformStorage.saveText(if (platform == MusicPlatform.QQ) "qq_id" else "netease_id", uid)
                platformStorage.saveText("${platform.apiValue}_nickname", response.username ?: uid)
                platformStorage.saveText("${platform.apiValue}_avatar", response.avatarUrl.orEmpty())
                accountIds.value = accountIds.value + (platform to uid)
                selectPlatform(platform)
                onResult(true, "登录成功")
            } catch (error: Exception) {
                onResult(false, "登录失败，请检查用户 ID 或网络")
            }
        }
    }

    fun logout(platform: MusicPlatform = selectedPlatform.value) {
        platformStorage.clearText(if (platform == MusicPlatform.QQ) "qq_id" else "netease_id")
        platformStorage.clearText("${platform.apiValue}_nickname")
        platformStorage.clearText("${platform.apiValue}_avatar")
        accountIds.value = accountIds.value - platform
        if (selectedPlatform.value == platform) selectPlatform(platform)
    }

    fun loadUserPlaylists(qq: String) {
        val platform = selectedPlatform.value
        val request = ++accountRequest
        userPlaylistsLoading.value = true
        scope.launch {
            try {
                storage.getPlaylistsFromCache(platform.key(qq))?.let {
                    if (request == accountRequest) playlists.value = it.map { list -> list.copy(source = platform) }
                }
                val response = ApiClient.getUserPlaylists(qq, platform)
                val lists = response.playlist.map { it.copy(source = platform) }
                storage.savePlaylistsToCache(platform.key(qq), lists)
                if (request == accountRequest) {
                    playlists.value = lists
                    nickname.value = response.username ?: qq
                    avatarUrl.value = response.avatarUrl.orEmpty()
                }
            } catch (error: Exception) {
                if (request == accountRequest && playlists.value.isEmpty()) showToast("载入歌单失败，请检查网络")
            } finally {
                if (request == accountRequest) userPlaylistsLoading.value = false
            }
        }
    }

    fun setSoundQualityLevel(quality: String, platform: MusicPlatform = MusicPlatform.QQ) {
        if (platform == MusicPlatform.NETEASE) neteaseQuality.value = quality else soundQuality.value = quality
        platformStorage.saveText(if (platform == MusicPlatform.QQ) "sound_quality" else "netease_sound_quality", quality)
        currentSong.value?.takeIf { it.source == platform }?.let {
            if (!ListenTogetherState.isInRoom.value) {
                savedPlaybackPositionMs.value = player.currentPositionMs.value
                playSong(it)
            }
        }
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

    fun setRemoveQrcSongInfo(enabled: Boolean) {
        removeQrcSongInfo.value = enabled
        platformStorage.saveText("remove_qrc_song_info", enabled.toString())

        val song = currentSong.value ?: return
        scope.launch {
            val lines = try {
                loadLyrics(song)
            } catch (error: Exception) {
                error.printStackTrace()
                return@launch
            }
            if (currentSong.value?.cacheKey == song.cacheKey) {
                lyricLines.value = lines
                updateLyricIndex(player.currentPositionMs.value)
            }
        }
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
        val request = playbackRequest
        val quality = qualityFor(song)
        player.stop()
        lyricLines.value = emptyList()
        currentLyricIndex.value = -1
 
        scope.launch {
            try {
                // Standalone playback always starts from the network immediately.
                // Caching runs in the background and never delays AVPlayer preparation.
                var remoteUrl = storage.getCachedRemoteUrl(song.cacheKey, quality)
                if (remoteUrl == null) {
                    val urlResponse = ApiClient.getSongUrl(song.id, quality, song.source)
                    val rawUrl = urlResponse.data.firstOrNull()?.url
                    if (request != playbackRequest) return@launch
                    if (rawUrl.isNullOrEmpty()) {
                        nextSong() // skip on error
                        return@launch
                    }
                    remoteUrl = rawUrl
                    storage.saveRemoteUrlToCache(song.cacheKey, quality, rawUrl)
                }
                val audioUrl = storage.getSongAudioUrl(song.audioKey(quality), remoteUrl, waitForCache = false)
                if (request != playbackRequest) return@launch
                standaloneStreamingSongId = song.cacheKey
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

                // Show ordinary lyrics first. TTML is a second-stage upgrade; when it
                // finishes, replace the state so an already-open lyric page refreshes.
                val ordinaryLines = loadOrdinaryLyrics(song)
                if (request == playbackRequest && currentSong.value?.cacheKey == song.cacheKey) {
                    lyricLines.value = ordinaryLines
                }
                val ttmlResponse = try { ApiClient.getTtmlLyric(song.lyricMid, song.source) } catch (error: Exception) {
                    error.printStackTrace()
                    null
                }
                if (request == playbackRequest && currentSong.value?.cacheKey == song.cacheKey) {
                    lyricLines.value = applyTtmlLyrics(song, ordinaryLines, ttmlResponse)
                    updateLyricIndex(player.currentPositionMs.value)
                }
                updateCacheSizes()

                // Save song metadata for cache management
                storage.saveSongMetadata(song, quality)

                // Preload the next song
                val next = getNextSong()
                if (next != null) {
                    preloadSong(next)
                }
 
                savePlaybackState()
            } catch (e: Exception) {
                e.printStackTrace()
                if (request == playbackRequest) nextSong()
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
        val quality = qualityFor(song)
        scope.launch {
            try {
                // 1. Preload Cover Image
                if (song.bestCoverUrl.isNotEmpty()) {
                    storage.getCoverImageUrl(song.cacheKey, song.bestCoverUrl)
                }

                // 2. Save Song Metadata
                storage.saveSongMetadata(song, quality)

                // 3. Preload Audio Link & File
                val cachedLocalUrl = storage.getCachedSongUrl(song.audioKey(quality))
                if (cachedLocalUrl == null) {
                    var remoteUrl = storage.getCachedRemoteUrl(song.cacheKey, quality)
                    if (remoteUrl == null) {
                        val urlResponse = ApiClient.getSongUrl(song.id, quality, song.source)
                        val rawUrl = urlResponse.data.firstOrNull()?.url
                        if (!rawUrl.isNullOrEmpty()) {
                            remoteUrl = rawUrl
                            storage.saveRemoteUrlToCache(song.cacheKey, quality, rawUrl)
                        }
                    }
                    if (remoteUrl != null) {
                        storage.getSongAudioUrl(song.audioKey(quality), remoteUrl, waitForCache = false)
                    }
                }

                // 4. Preload Lyrics
                try {
                    var lyricResponse = storage.getLyricFromCache(song.cacheKey)
                    if (lyricResponse == null) {
                        lyricResponse = ApiClient.getSongLyric(song.id, song.source)
                        storage.saveLyricToCache(song.cacheKey, lyricResponse)
                    }
                } catch (e: Exception) {
                    // Ignore lyric preloading errors
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
 
    suspend fun loadLyrics(song: Song): List<LyricLine> {
        val ordinaryLines = loadOrdinaryLyrics(song)
        val ttmlResponse = try { ApiClient.getTtmlLyric(song.lyricMid, song.source) } catch (error: Exception) {
            error.printStackTrace()
            null
        }
        return applyTtmlLyrics(song, ordinaryLines, ttmlResponse)
    }

    private suspend fun loadOrdinaryLyrics(song: Song): List<LyricLine> {
        var response = storage.getLyricFromCache(song.cacheKey)
        if (response == null) {
            response = ApiClient.getSongLyric(song.id, song.source)
            storage.saveLyricToCache(song.cacheKey, response)
        }
        val parsed = LyricParser.parseWithFormat(response, removeQrcSongInfo.value, song)
        lyricFormats.value = lyricFormats.value + (song.cacheKey to parsed.format)
        return parsed.lines
    }

    private fun hasTtml(response: TtmlLyricResponse?): Boolean =
        response?.status == "success" && !response.content.isNullOrBlank()

    private fun applyTtmlLyrics(
        song: Song,
        ordinaryLines: List<LyricLine>,
        response: TtmlLyricResponse?,
    ): List<LyricLine> {
        if (!hasTtml(response)) {
            ttmlAuthor.value = null
            return ordinaryLines
        }
        val parsed = TtmlParser.parseAmll(response!!.content!!)
        if (parsed.lines.isEmpty()) return ordinaryLines

        lyricFormats.value = lyricFormats.value + (song.cacheKey to LyricFormat.TTML)
        ttmlSongIds.value = ttmlSongIds.value + song.cacheKey
        val author = parsed.metadata
            .firstOrNull { it.first == "ttmlAuthorGithubLogin" }
            ?.second
            ?.filter(String::isNotBlank)
            ?.distinct()
            ?.joinToString("、")
            ?.takeIf(String::isNotBlank)
        ttmlAuthor.value = author
        if (author != null) showToast("TTML 作者：$author")
        return parsed.lines
    }

    private fun updateLyricIndex(currentTimeMs: Long) {
        val adjustedTime = currentTimeMs + lyricOffsetMs.value + currentLyricFormat().transitionLeadMs(lyricLeadMs.value) // dynamic offset + dynamic early transition lead
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

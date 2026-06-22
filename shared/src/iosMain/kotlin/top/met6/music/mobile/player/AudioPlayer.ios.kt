@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
package top.met6.music.mobile.player

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import top.met6.music.mobile.storage.PlatformContext
import platform.AVFoundation.*
import platform.AVFAudio.*
import platform.Foundation.*
import platform.CoreMedia.*
import kotlinx.cinterop.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class IosAudioPlayer : AudioPlayer {
    override var onPlaybackComplete: (() -> Unit)? = null
    private var avPlayer: AVPlayer? = null

    init {
        try {
            val session = platform.AVFAudio.AVAudioSession.sharedInstance()
            session.setCategory(platform.AVFAudio.AVAudioSessionCategoryPlayback, error = null)
            session.setActive(true, error = null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private val _isPlaying = mutableStateOf(false)
    override val isPlaying: State<Boolean> = _isPlaying
    
    private val _isReady = mutableStateOf(false)
    override val isReady: State<Boolean> = _isReady
    
    private val _currentPositionMs = mutableStateOf(0L)
    override val currentPositionMs: State<Long> = _currentPositionMs
    
    private val _durationMs = mutableStateOf(0L)
    override val durationMs: State<Long> = _durationMs
    
    private val _volume = mutableStateOf(1.0f)
    override val volume: State<Float> = _volume

    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                avPlayer?.let { player ->
                    val isActuallyPlaying = player.timeControlStatus != AVPlayerTimeControlStatusPaused
                    if (_isPlaying.value != isActuallyPlaying) {
                        _isPlaying.value = isActuallyPlaying
                    }
                    if (_isPlaying.value) {
                        @OptIn(ExperimentalForeignApi::class)
                        val time = player.currentTime()
                        val currentSec = CMTimeGetSeconds(time)
                        if (!currentSec.isNaN() && !currentSec.isInfinite()) {
                            _currentPositionMs.value = (currentSec * 1000).toLong()
                        }
                    }
                }
                delay(30)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    override fun play(urlOrPath: String, autoPlay: Boolean, startPositionMs: Long) {
        stop()
        
        try {
            val session = platform.AVFAudio.AVAudioSession.sharedInstance()
            session.setCategory(platform.AVFAudio.AVAudioSessionCategoryPlayback, error = null)
            session.setActive(true, error = null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        val url = if (urlOrPath.startsWith("http")) {
            NSURL.URLWithString(urlOrPath)
        } else {
            NSURL.fileURLWithPath(urlOrPath)
        }
        
        if (url == null) return
        
        val playerItem = AVPlayerItem.playerItemWithURL(url)
        val player = AVPlayer.playerWithPlayerItem(playerItem)
        avPlayer = player
        
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = playerItem,
            queue = null
        ) { _ ->
            scope.launch(Dispatchers.Main) {
                _isPlaying.value = false
                stopProgressUpdates()
                _currentPositionMs.value = _durationMs.value
                onPlaybackComplete?.invoke()
            }
        }
        
        scope.launch(Dispatchers.Main) {
            var checks = 0
            while (avPlayer == player && checks < 60) {
                val item = player.currentItem
                if (item != null) {
                    if (item.status == AVPlayerItemStatusReadyToPlay) {
                        _isReady.value = true
                        @OptIn(ExperimentalForeignApi::class)
                        val durationTime = item.duration
                        val durationSec = CMTimeGetSeconds(durationTime)
                        if (!durationSec.isNaN() && !durationSec.isInfinite()) {
                            _durationMs.value = (durationSec * 1000).toLong()
                        }
                        player.volume = _volume.value
                        
                        if (startPositionMs > 0) {
                            @OptIn(ExperimentalForeignApi::class)
                            val seekTime = CMTimeMake((startPositionMs).toLong(), 1000)
                            player.seekToTime(seekTime)
                        }
                        
                        if (autoPlay) {
                            player.play()
                            _isPlaying.value = true
                            startProgressUpdates()
                        } else {
                            _isPlaying.value = false
                        }
                        break
                    } else if (item.status == AVPlayerItemStatusFailed) {
                        println("AVPlayerItem failed to load: ${item.error?.localizedDescription}")
                        stop()
                        onPlaybackComplete?.invoke()
                        break
                    }
                }
                delay(100)
                checks++
            }
        }
    }

    override fun pause() {
        avPlayer?.let { player ->
            player.pause()
            _isPlaying.value = false
            stopProgressUpdates()
        }
    }

    override fun resume() {
        avPlayer?.let { player ->
            if (_isReady.value) {
                try {
                    val session = platform.AVFAudio.AVAudioSession.sharedInstance()
                    session.setActive(true, error = null)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                player.play()
                _isPlaying.value = true
                startProgressUpdates()
            }
        }
    }

    override fun stop() {
        stopProgressUpdates()
        avPlayer?.pause()
        avPlayer = null
        _isPlaying.value = false
        _isReady.value = false
        _currentPositionMs.value = 0L
        _durationMs.value = 0L
    }

    override fun seekTo(positionMs: Long) {
        avPlayer?.let { player ->
            if (_isReady.value) {
                @OptIn(ExperimentalForeignApi::class)
                val time = CMTimeMake(positionMs, 1000)
                player.seekToTime(time)
                _currentPositionMs.value = positionMs
            }
        }
    }

    override fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0.0f, 1.0f)
        _volume.value = clamped
        avPlayer?.volume = clamped
    }

    override fun release() {
        stop()
    }
}

actual fun createAudioPlayer(context: PlatformContext): AudioPlayer {
    return IosAudioPlayer()
}

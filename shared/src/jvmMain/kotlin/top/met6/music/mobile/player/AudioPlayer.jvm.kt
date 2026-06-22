package top.met6.music.mobile.player

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import top.met6.music.mobile.storage.PlatformContext
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class JvmAudioPlayer : AudioPlayer {
    override var onPlaybackComplete: (() -> Unit)? = null
    private var mediaPlayer: MediaPlayer? = null
    
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

    init {
        try {
            com.sun.javafx.application.PlatformImpl.startup {}
        } catch (e: Exception) {
            // Already started or error
        }
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                mediaPlayer?.let { mp ->
                    if (_isPlaying.value) {
                        _currentPositionMs.value = mp.currentTime.toMillis().toLong()
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
            val media = Media(urlOrPath)
            val mp = MediaPlayer(media)
            mediaPlayer = mp
            
            mp.onReady = Runnable {
                _isReady.value = true
                _durationMs.value = media.duration.toMillis().toLong()
                mp.volume = _volume.value.toDouble()
                
                if (startPositionMs > 0) {
                    mp.seek(Duration.millis(startPositionMs.toDouble()))
                }
                
                if (autoPlay) {
                    mp.play()
                    _isPlaying.value = true
                    startProgressUpdates()
                } else {
                    _isPlaying.value = false
                }
            }
            
            mp.onEndOfMedia = Runnable {
                _isPlaying.value = false
                _currentPositionMs.value = _durationMs.value
                stopProgressUpdates()
                onPlaybackComplete?.invoke()
            }
            
            mp.onError = Runnable {
                _isReady.value = false
                _isPlaying.value = false
                stopProgressUpdates()
                mp.error?.printStackTrace()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _isReady.value = false
            _isPlaying.value = false
        }
    }

    override fun pause() {
        mediaPlayer?.let { mp ->
            mp.pause()
            _isPlaying.value = false
            stopProgressUpdates()
        }
    }

    override fun resume() {
        mediaPlayer?.let { mp ->
            if (_isReady.value) {
                mp.play()
                _isPlaying.value = true
                startProgressUpdates()
            }
        }
    }

    override fun stop() {
        stopProgressUpdates()
        mediaPlayer?.let { mp ->
            mp.stop()
            mp.dispose()
        }
        mediaPlayer = null
        _isPlaying.value = false
        _isReady.value = false
        _currentPositionMs.value = 0L
    }

    override fun seekTo(positionMs: Long) {
        mediaPlayer?.let { mp ->
            if (_isReady.value) {
                mp.seek(Duration.millis(positionMs.toDouble()))
                _currentPositionMs.value = positionMs
            }
        }
    }

    override fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0.0f, 1.0f)
        _volume.value = clamped
        mediaPlayer?.volume = clamped.toDouble()
    }

    override fun release() {
        stop()
    }
}

actual fun createAudioPlayer(context: PlatformContext): AudioPlayer {
    return JvmAudioPlayer()
}

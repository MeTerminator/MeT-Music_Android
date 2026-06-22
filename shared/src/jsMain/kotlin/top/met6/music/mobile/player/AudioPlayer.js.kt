package top.met6.music.mobile.player

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import top.met6.music.mobile.storage.PlatformContext
import org.w3c.dom.HTMLAudioElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class JsAudioPlayer : AudioPlayer {
    override var onPlaybackComplete: (() -> Unit)? = null
    private var audio: HTMLAudioElement? = null
    
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
                audio?.let { aud ->
                    if (!aud.paused) {
                        _currentPositionMs.value = (aud.currentTime * 1000).toLong()
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
        
        val aud = org.w3c.dom.Audio(urlOrPath) as HTMLAudioElement
        audio = aud
        aud.volume = _volume.value.toDouble()

        aud.onloadedmetadata = {
            _isReady.value = true
            _durationMs.value = (aud.duration * 1000).toLong()
            
            if (startPositionMs > 0) {
                aud.currentTime = startPositionMs / 1000.0
            }
            
            if (autoPlay) {
                aud.play()
            }
        }

        aud.onplay = {
            _isPlaying.value = true
            startProgressUpdates()
        }

        aud.onpause = {
            _isPlaying.value = false
            stopProgressUpdates()
        }

        aud.onended = {
            _isPlaying.value = false
            _currentPositionMs.value = _durationMs.value
            stopProgressUpdates()
            onPlaybackComplete?.invoke()
        }

        aud.onerror = { _, _, _, _, _ ->
            _isReady.value = false
            _isPlaying.value = false
            stopProgressUpdates()
        }
        
        // Let onloadedmetadata trigger play if autoPlay is true, but HTML5 sometimes requires direct play() to load.
        // Wait, onloadedmetadata will be called automatically once it loads metadata.
        // If we don't call play() now, it might not start loading on some mobile browsers.
        // Let's call aud.load() to force loading.
        aud.load()
    }

    override fun pause() {
        audio?.pause()
    }

    override fun resume() {
        audio?.play()
    }

    override fun stop() {
        stopProgressUpdates()
        audio?.pause()
        audio = null
        _isPlaying.value = false
        _isReady.value = false
        _currentPositionMs.value = 0L
    }

    override fun seekTo(positionMs: Long) {
        audio?.let { aud ->
            if (_isReady.value) {
                aud.currentTime = positionMs.toDouble() / 1000.0
                _currentPositionMs.value = positionMs
            }
        }
    }

    override fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0.0f, 1.0f)
        _volume.value = clamped
        audio?.volume = clamped.toDouble()
    }

    override fun release() {
        stop()
    }
}

actual fun createAudioPlayer(context: PlatformContext): AudioPlayer {
    return JsAudioPlayer()
}

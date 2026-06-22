package top.met6.music.mobile.player

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import top.met6.music.mobile.storage.PlatformContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@JsFun("(url) => window.metMusicPlayer.play(url)")
private external fun playJs(url: String)

@JsFun("() => window.metMusicPlayer.pause()")
private external fun pauseJs()

@JsFun("() => window.metMusicPlayer.resume()")
private external fun resumeJs()

@JsFun("() => window.metMusicPlayer.stop()")
private external fun stopJs()

@JsFun("(seconds) => window.metMusicPlayer.seekTo(seconds)")
private external fun seekToJs(seconds: Double)

@JsFun("(vol) => window.metMusicPlayer.setVolume(vol)")
private external fun setVolumeJs(vol: Double)

@JsFun("() => window.metMusicPlayer.getDuration()")
private external fun getDurationJs(): Double

@JsFun("() => window.metMusicPlayer.getCurrentTime()")
private external fun getCurrentTimeJs(): Double

@JsFun("() => window.metMusicPlayer.isPlaying()")
private external fun isPlayingJs(): Boolean

@JsFun("() => window.metMusicPlayer.isReady()")
private external fun isReadyJs(): Boolean

class WasmAudioPlayer : AudioPlayer {
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
                val active = isPlayingJs()
                val ready = isReadyJs()
                _isPlaying.value = active
                _isReady.value = ready
                
                if (ready) {
                    _durationMs.value = (getDurationJs() * 1000).toLong()
                }
                if (active) {
                    _currentPositionMs.value = (getCurrentTimeJs() * 1000).toLong()
                }
                delay(250)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    override fun play(urlOrPath: String, autoPlay: Boolean, startPositionMs: Long) {
        stop()
        playJs(urlOrPath)
        setVolumeJs(_volume.value.toDouble())
        
        if (startPositionMs > 0) {
            // Give it a tiny delay to initialize JS side before seeking
            scope.launch {
                delay(50)
                seekToJs(startPositionMs / 1000.0)
            }
        }
        
        if (!autoPlay) {
            scope.launch {
                delay(50)
                pauseJs()
                _isPlaying.value = false
            }
        } else {
            _isPlaying.value = true
        }
        
        startProgressUpdates()
    }

    override fun pause() {
        pauseJs()
        _isPlaying.value = false
    }

    override fun resume() {
        resumeJs()
        _isPlaying.value = true
    }

    override fun stop() {
        stopProgressUpdates()
        stopJs()
        _isPlaying.value = false
        _isReady.value = false
        _currentPositionMs.value = 0L
    }

    override fun seekTo(positionMs: Long) {
        seekToJs(positionMs.toDouble() / 1000.0)
        _currentPositionMs.value = positionMs
    }

    override fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0.0f, 1.0f)
        _volume.value = clamped
        setVolumeJs(clamped.toDouble())
    }

    override fun release() {
        stop()
    }
}

actual fun createAudioPlayer(context: PlatformContext): AudioPlayer {
    return WasmAudioPlayer()
}

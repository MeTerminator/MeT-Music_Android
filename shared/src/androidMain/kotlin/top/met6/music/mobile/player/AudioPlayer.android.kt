package top.met6.music.mobile.player

import android.media.MediaPlayer
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import top.met6.music.mobile.storage.PlatformContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AndroidAudioPlayer(private val context: PlatformContext) : AudioPlayer {
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

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        _currentPositionMs.value = mp.currentPosition.toLong()
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
        _isReady.value = false
        _isPlaying.value = false
        
        val mp = MediaPlayer().apply {
            setDataSource(urlOrPath)
            setOnPreparedListener {
                _isReady.value = true
                _durationMs.value = duration.toLong()
                setVolume(_volume.value, _volume.value)
                
                if (startPositionMs > 0) {
                    seekTo(startPositionMs.toInt())
                }
                
                if (autoPlay) {
                    start()
                    _isPlaying.value = true
                    startProgressUpdates()
                } else {
                    _isPlaying.value = false
                }
            }
            setOnCompletionListener {
                _isPlaying.value = false
                _currentPositionMs.value = duration.toLong()
                stopProgressUpdates()
                onPlaybackComplete?.invoke()
            }
            setOnErrorListener { _, _, _ ->
                _isReady.value = false
                _isPlaying.value = false
                stopProgressUpdates()
                true
            }
            prepareAsync()
        }
        mediaPlayer = mp
    }

    override fun pause() {
        mediaPlayer?.let { mp ->
            if (mp.isPlaying) {
                mp.pause()
                _isPlaying.value = false
                stopProgressUpdates()
            }
        }
    }

    override fun resume() {
        mediaPlayer?.let { mp ->
            if (!mp.isPlaying && _isReady.value) {
                mp.start()
                _isPlaying.value = true
                startProgressUpdates()
            }
        }
    }

    override fun stop() {
        stopProgressUpdates()
        mediaPlayer?.let { mp ->
            try {
                if (mp.isPlaying) {
                    mp.stop()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            mp.release()
        }
        mediaPlayer = null
        _isPlaying.value = false
        _isReady.value = false
        _currentPositionMs.value = 0L
        _durationMs.value = 0L
    }

    override fun seekTo(positionMs: Long) {
        mediaPlayer?.let { mp ->
            if (_isReady.value) {
                mp.seekTo(positionMs.toInt())
                _currentPositionMs.value = positionMs
            }
        }
    }

    override fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0.0f, 1.0f)
        _volume.value = clamped
        mediaPlayer?.setVolume(clamped, clamped)
    }

    override fun release() {
        stop()
    }
}

actual fun createAudioPlayer(context: PlatformContext): AudioPlayer {
    return AndroidAudioPlayer(context)
}

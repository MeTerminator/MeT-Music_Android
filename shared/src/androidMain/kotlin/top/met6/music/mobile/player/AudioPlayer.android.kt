package top.met6.music.mobile.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import top.met6.music.mobile.storage.PlatformContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class AndroidAudioPlayer(private val context: PlatformContext) : AudioPlayer {
    override var onPlaybackComplete: (() -> Unit)? = null
    private var exoPlayer: ExoPlayer? = null
    
    private val _isPlaying = mutableStateOf(false)
    override val isPlaying: State<Boolean> = _isPlaying
    
    private val _isReady = mutableStateOf(false)
    override val isReady: State<Boolean> = _isReady
    
    private val _currentPositionMs = mutableStateOf(0L)
    override val currentPositionMs: State<Long> = _currentPositionMs
    override val presentationPositionMs: State<Long> = _currentPositionMs
    
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
                exoPlayer?.let { player ->
                    if (player.isPlaying) {
                        _currentPositionMs.value = player.currentPosition
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

    @OptIn(UnstableApi::class)
    override fun play(urlOrPath: String, autoPlay: Boolean, startPositionMs: Long) {
        stop()
        _isReady.value = false
        _isPlaying.value = false
        
        val player = ExoPlayer.Builder(context.context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .build()
        
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        _isReady.value = true
                        _durationMs.value = player.duration.coerceAtLeast(0L)
                    }
                    Player.STATE_ENDED -> {
                        _isPlaying.value = false
                        _currentPositionMs.value = _durationMs.value
                        stopProgressUpdates()
                        onPlaybackComplete?.invoke()
                    }
                    Player.STATE_IDLE, Player.STATE_BUFFERING -> {
                        // no-op
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    startProgressUpdates()
                } else {
                    stopProgressUpdates()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                error.printStackTrace()
                _isReady.value = false
                _isPlaying.value = false
                stopProgressUpdates()
            }
        })
        
        // Build the media URI
        val mediaUri = when {
            urlOrPath.startsWith("file://") -> Uri.parse(urlOrPath)
            urlOrPath.startsWith("/") -> Uri.fromFile(File(urlOrPath))
            else -> Uri.parse(urlOrPath)
        }
        
        val mediaItem = MediaItem.fromUri(mediaUri)
        player.setMediaItem(mediaItem)
        player.volume = _volume.value
        player.playWhenReady = autoPlay
        player.prepare()
        
        if (startPositionMs > 0) {
            player.seekTo(startPositionMs)
        }
        
        exoPlayer = player
    }

    override fun pause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            }
        }
    }

    override fun resume() {
        exoPlayer?.let { player ->
            if (!player.isPlaying && _isReady.value) {
                player.play()
            }
        }
    }

    override fun stop() {
        stopProgressUpdates()
        exoPlayer?.let { player ->
            try {
                player.stop()
                player.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        exoPlayer = null
        _isPlaying.value = false
        _isReady.value = false
        _currentPositionMs.value = 0L
        _durationMs.value = 0L
    }

    override fun seekTo(positionMs: Long) {
        exoPlayer?.let { player ->
            if (_isReady.value) {
                player.seekTo(positionMs)
                _currentPositionMs.value = positionMs
            }
        }
    }

    override fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0.0f, 1.0f)
        _volume.value = clamped
        exoPlayer?.volume = clamped
    }

    override fun release() {
        stop()
    }
}

actual fun createAudioPlayer(context: PlatformContext): AudioPlayer {
    return AndroidAudioPlayer(context)
}

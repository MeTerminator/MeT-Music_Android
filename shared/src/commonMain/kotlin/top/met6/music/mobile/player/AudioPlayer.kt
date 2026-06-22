package top.met6.music.mobile.player

import androidx.compose.runtime.State
import top.met6.music.mobile.storage.PlatformContext

interface AudioPlayer {
    val isPlaying: State<Boolean>
    val isReady: State<Boolean>
    val currentPositionMs: State<Long>
    val durationMs: State<Long>
    val volume: State<Float>

    fun play(urlOrPath: String, autoPlay: Boolean = true, startPositionMs: Long = 0L)
    fun pause()
    fun resume()
    fun stop()
    fun seekTo(positionMs: Long)
    fun setVolume(vol: Float)
    fun release()
    
    var onPlaybackComplete: (() -> Unit)?
}

expect fun createAudioPlayer(context: PlatformContext): AudioPlayer

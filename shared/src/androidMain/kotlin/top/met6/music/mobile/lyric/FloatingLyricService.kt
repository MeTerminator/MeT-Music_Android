package top.met6.music.mobile.lyric

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import top.met6.music.mobile.state.AppState

class FloatingLyricService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var composeView: ComposeView

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        layoutParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
        }

        composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(this@FloatingLyricService)
            setViewTreeViewModelStoreOwner(this@FloatingLyricService)
            setViewTreeSavedStateRegistryOwner(this@FloatingLyricService)
            setContent {
                val currentSong = AppState.currentSong.value
                val isPlaying = AppState.player.isPlaying.value
                val showWhenPaused = AppState.desktopLyricShowWhenPaused.value

                val shouldShow = currentSong != null && (isPlaying || showWhenPaused)

                LaunchedEffect(shouldShow) {
                    visibility = if (shouldShow) View.VISIBLE else View.GONE
                }

                val xBias = AppState.desktopLyricX.value
                val yBias = AppState.desktopLyricY.value
                val widthPct = AppState.desktopLyricWidth.value
                val clickPlayPause = AppState.desktopLyricClickPlayPause.value

                LaunchedEffect(xBias, yBias, widthPct, clickPlayPause) {
                    updateWindowLayout(xBias, yBias, widthPct, clickPlayPause)
                }

                if (shouldShow) {
                    FloatingLyricContent()
                }
            }
        }

        try {
            windowManager.addView(composeView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    private fun updateWindowLayout(xBias: Float, yBias: Float, widthPct: Float, clickPlayPause: Boolean) {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val targetWidth = (screenWidth * (widthPct / 100f)).toInt()

        layoutParams.width = targetWidth
        layoutParams.x = ((screenWidth - targetWidth) * (xBias / 100f)).toInt()
        layoutParams.y = (screenHeight * (yBias / 100f)).toInt()

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        layoutParams.flags = if (clickPlayPause) {
            flags
        } else {
            flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        try {
            if (composeView.isAttachedToWindow) {
                windowManager.updateViewLayout(composeView, layoutParams)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        try {
            if (composeView.isAttachedToWindow) {
                windowManager.removeView(composeView)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FloatingLyricContent() {
    val currentSong = AppState.currentSong.value ?: return
    val isPlaying = AppState.player.isPlaying.value
    
    val showContent = AppState.desktopLyricShowContent.value
    val alignment = AppState.desktopLyricAlignment.value
    val fontSize = AppState.desktopLyricFontSize.value
    val opacity = AppState.desktopLyricOpacity.value / 100f
    val colorName = AppState.desktopLyricColorName.value

    val selectedColor = AppState.PRESET_COLORS.firstOrNull { it.first == colorName }?.second ?: Color(0xFF4CAF50)

    val horizontalAlignment = if (alignment == "center") Alignment.CenterHorizontally else Alignment.Start
    val textAlign = if (alignment == "center") TextAlign.Center else TextAlign.Left

    val textStyle = TextStyle(
        shadow = Shadow(
            color = Color.Black,
            offset = Offset(2f, 2f),
            blurRadius = 4f
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(opacity)
            .clickable(enabled = AppState.desktopLyricClickPlayPause.value) {
                if (isPlaying) {
                    AppState.player.pause()
                } else {
                    AppState.resumeOrPlay()
                }
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = horizontalAlignment
        ) {
            if (showContent == "song_name") {
                Text(
                    text = "${currentSong.name} - ${currentSong.artists.joinToString(" & ") { it.name }}",
                    color = selectedColor,
                    fontSize = fontSize.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = textAlign,
                    style = textStyle,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                val lines = AppState.lyricLines.value
                val activeIndex = AppState.currentLyricIndex.value

                if (lines.isEmpty() || activeIndex !in lines.indices) {
                    Text(
                        text = "${currentSong.name} - ${currentSong.artists.joinToString(" & ") { it.name }}",
                        color = selectedColor,
                        fontSize = fontSize.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = textAlign,
                        style = textStyle,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    val line = lines[activeIndex]
                    val currentTimeMs = AppState.player.currentPositionMs.value

                    if (line.words.size <= 1) {
                        Text(
                            text = line.text,
                            color = selectedColor,
                            fontSize = fontSize.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = textAlign,
                            style = textStyle,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (alignment == "center") Arrangement.Center else Arrangement.Start
                        ) {
                            line.words.forEach { word ->
                                val elapsedMs = (currentTimeMs + AppState.lyricOffsetMs.value) - word.startTime
                                val durationMs = word.endTime - word.startTime
                                val progress = when {
                                    elapsedMs < 0 -> 0f
                                    elapsedMs >= durationMs -> 1f
                                    durationMs > 0 -> elapsedMs.toFloat() / durationMs.toFloat()
                                    else -> 1f
                                }

                                Box(modifier = Modifier.padding(end = 2.dp)) {
                                    val baseColor = if (selectedColor == Color.White) Color.White.copy(alpha = 0.4f) else Color.White
                                    Text(
                                        text = word.word,
                                        color = baseColor,
                                        fontSize = fontSize.sp,
                                        fontWeight = FontWeight.Bold,
                                        style = textStyle
                                    )

                                    if (progress > 0f) {
                                        Text(
                                            text = word.word,
                                            color = selectedColor,
                                            fontSize = fontSize.sp,
                                            fontWeight = FontWeight.Bold,
                                            style = textStyle,
                                            modifier = Modifier.drawWithContent {
                                                clipRect(right = size.width * progress) {
                                                    this@drawWithContent.drawContent()
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

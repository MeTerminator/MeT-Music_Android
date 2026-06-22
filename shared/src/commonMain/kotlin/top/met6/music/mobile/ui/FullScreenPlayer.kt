package top.met6.music.mobile.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.met6.music.mobile.models.Song
import top.met6.music.mobile.state.AppState
import top.met6.music.mobile.lyric.LyricLine
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontFamily
import met_music_mobile.shared.generated.resources.Res
import met_music_mobile.shared.generated.resources.spotify_mix
import org.jetbrains.compose.resources.Font

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenPlayer() {
    val song = AppState.currentSong.value ?: return
    var showQueueSheet by remember { mutableStateOf(false) }
    var showTranslation by remember { mutableStateOf(true) }

    // Prevent screen from dimming/sleeping during playback
    KeepScreenOn()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C0E))
    ) {
        // Zoomed-in and heavily blurred cover image background
        val coverUrl = song.bestCoverUrl
        if (coverUrl.isNotEmpty()) {
            CachedImage(
                songId = song.id,
                imageUrl = coverUrl,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(60.dp),
                contentScale = ContentScale.Crop
            )
        }

        // Dark gradient overlay / scrim to ensure contrast and legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.5f),
                            Color.Black.copy(alpha = 0.75f),
                            Color(0xFF0A0A0C).copy(alpha = 0.95f)
                        )
                    )
                )
        )
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            val isLandscape = maxWidth > maxHeight
            val isTablet = minOf(maxWidth, maxHeight) >= 600.dp

            if (isLandscape) {
                if (isTablet) {
                    // Tablet Landscape: Left Cover + details & controls always visible, Right Lyrics
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                            .padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .pointerInput(Unit) {
                                    var totalDragY = 0f
                                    detectVerticalDragGestures(
                                        onDragStart = { totalDragY = 0f },
                                        onDragEnd = {
                                            if (totalDragY > 150f) {
                                                AppState.isFullScreenPlayer.value = false
                                            }
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume()
                                            totalDragY += dragAmount
                                        }
                                    )
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            PlayerHeader(isLandscape = true)
                            
                            val coverUrl = song.bestCoverUrl
                            CachedImage(
                                songId = song.id,
                                imageUrl = coverUrl,
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(16.dp)),
                                onColorExtracted = { AppState.currentThemeColor.value = it }
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            SongDetailsSection(song = song)
                            
                            PlaybackScrubber()
                            
                            PlaybackControls(isLandscape = true) {
                                showQueueSheet = true
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(32.dp))
                        
                        Column(
                            modifier = Modifier
                                .weight(1.2f)
                                .fillMaxHeight()
                        ) {
                            Text(
                                text = "歌词",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                                LyricsView(showTranslation = showTranslation)
                            }
                        }
                    }
                } else {
                    // Phone Landscape: Left Cover (click to overlay controls), Right Lyrics
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                            .padding(16.dp)
                    ) {
                        var showControlsOverCover by remember { mutableStateOf(false) }
                        
                        // Left Column: Cover + Controls Overlay
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(end = 8.dp)
                                .pointerInput(Unit) {
                                    var totalDragY = 0f
                                    detectVerticalDragGestures(
                                        onDragStart = { totalDragY = 0f },
                                        onDragEnd = {
                                            if (totalDragY > 150f) {
                                                AppState.isFullScreenPlayer.value = false
                                            }
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume()
                                            totalDragY += dragAmount
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val coverUrl = song.bestCoverUrl
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight(0.85f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { showControlsOverCover = !showControlsOverCover }
                            ) {
                                CachedImage(
                                    songId = song.id,
                                    imageUrl = coverUrl,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    onColorExtracted = { AppState.currentThemeColor.value = it }
                                )
                                
                                if (showControlsOverCover) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.75f))
                                            .padding(12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            SongDetailsSection(song = song)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            PlaybackScrubber()
                                            Spacer(modifier = Modifier.height(4.dp))
                                            PlaybackControls(isLandscape = true) {
                                                showQueueSheet = true
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Right Column: Lyrics
                        Column(
                            modifier = Modifier
                                .weight(1.1f)
                                .fillMaxHeight()
                                .padding(start = 8.dp)
                        ) {
                            Text(
                                text = "歌词",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                                LyricsView(showTranslation = showTranslation)
                            }
                        }
                    }
                }
            } else {
                // Portrait (Phone or Tablet): Vertical layout with horizontal pager (swipe left/right)
                Column(modifier = Modifier.fillMaxSize()) {
                    PlayerHeader(isLandscape = false)
                    
                    Box(modifier = Modifier.weight(1f)) {
                        PortraitPager(
                            song = song,
                            showTranslation = showTranslation,
                            onShowTranslationChange = { showTranslation = it }
                        ) {
                            showQueueSheet = true
                        }
                    }
                }
            }
        }

        // Current Queue Bottom Sheet
        if (showQueueSheet) {
            val scope = rememberCoroutineScope()
            val listState = rememberLazyListState()

            ModalBottomSheet(
                onDismissRequest = { showQueueSheet = false },
                containerColor = CardBackground,
                contentColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "播放列表",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        IconButton(onClick = {
                            val currentId = AppState.currentSong.value?.id
                            val queue = AppState.currentQueue.value
                            val index = queue.indexOfFirst { it.id == currentId }
                            if (index >= 0) {
                                scope.launch {
                                    listState.animateScrollToItem(index)
                                }
                            }
                        }) {
                            Icon(imageVector = Icons.Default.LocationOn, contentDescription = "Locate", tint = Color.LightGray)
                        }
                    }
                    
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)
                    ) {
                        itemsIndexed(AppState.currentQueue.value) { idx, s ->
                            SongRow(song = s, index = idx) {
                                AppState.playSongAtIndex(idx)
                                showQueueSheet = false
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun PlayerHeader(isLandscape: Boolean) {
    Row(
        modifier = Modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { AppState.isFullScreenPlayer.value = false }) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
        
        if (!isLandscape) {
            Text(
                text = "正在播放",
                color = Color.LightGray,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        Box(modifier = Modifier.size(48.dp)) // placeholder to balance close button
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PortraitPager(
    song: Song,
    showTranslation: Boolean,
    onShowTranslationChange: (Boolean) -> Unit,
    onShowQueue: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 2 })

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        if (page == 0) {
            // Page 1: Cover and Controls
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .pointerInput(Unit) {
                        var totalDragY = 0f
                        detectVerticalDragGestures(
                            onDragStart = { totalDragY = 0f },
                            onDragEnd = {
                                if (totalDragY > 150f) {
                                    AppState.isFullScreenPlayer.value = false
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                totalDragY += dragAmount
                            }
                        )
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceAround
            ) {
                val coverUrl = song.bestCoverUrl
                CachedImage(
                    songId = song.id,
                    imageUrl = coverUrl,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp)),
                    onColorExtracted = { AppState.currentThemeColor.value = it }
                )
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SongDetailsSection(song = song)
                    Spacer(modifier = Modifier.height(16.dp))
                    PlaybackScrubber()
                    Spacer(modifier = Modifier.height(8.dp))
                    PlaybackControls(isLandscape = false, onShowQueue = onShowQueue)
                }
            }
        } else {
            // Page 2: Lyrics Page
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
                // Header (cover + song + artist)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CachedImage(
                        songId = song.id,
                        imageUrl = song.bestCoverUrl,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.name,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = song.artists.joinToString(" & ") { it.name },
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Lyrics
                Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                    LyricsView(showTranslation = showTranslation)
                }

                // Bottom Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Translation toggle button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (showTranslation) Color.White.copy(alpha = 0.2f) else Color.Transparent)
                            .border(
                                width = 1.dp,
                                color = if (showTranslation) Color.Transparent else Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable { onShowTranslationChange(!showTranslation) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "翻译",
                            color = if (showTranslation) Color.White else Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Play / Pause / Skip controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Previous Song
                        IconButton(onClick = { AppState.prevSong() }) {
                            Icon(
                                imageVector = MusicIcons.SkipPrevious,
                                contentDescription = "Previous",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Play / Pause button
                        val isPlaying = AppState.player.isPlaying.value
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.1f))
                                .clickable {
                                    AppState.togglePlayPause()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) MusicIcons.Pause else MusicIcons.Play,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Next Song
                        IconButton(onClick = { AppState.nextSong() }) {
                            Icon(
                                imageVector = MusicIcons.SkipNext,
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SongDetailsSection(song: Song) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = song.name,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = song.artists.joinToString(" & ") { it.name },
            color = Color.LightGray,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun PlaybackScrubber() {
    val duration = if (AppState.player.durationMs.value > 0) {
        AppState.player.durationMs.value
    } else {
        AppState.currentSong.value?.durationMs ?: 0L
    }
    
    val pos = if (AppState.player.durationMs.value > 0) {
        AppState.player.currentPositionMs.value
    } else {
        AppState.savedPlaybackPositionMs.value
    }
    
    val progress = if (duration > 0) pos.toFloat() / duration.toFloat() else 0f
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Slider(
            value = progress,
            onValueChange = {
                val targetMs = (it * duration).toLong()
                AppState.seekTo(targetMs)
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = formatTime(pos), color = Color.Gray, fontSize = 12.sp)
            Text(text = formatTime(duration), color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
fun PlaybackControls(isLandscape: Boolean, onShowQueue: () -> Unit) {
    val isPlaying = AppState.player.isPlaying.value

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val currentMode = AppState.playbackMode.value
        val toggleMode = {
            val modes = top.met6.music.mobile.state.PlaybackMode.values()
            val nextOrdinal = (currentMode.ordinal + 1) % modes.size
            AppState.setPlaybackMode(modes[nextOrdinal])
        }

        if (!isLandscape) {
            // Queue button on left for portrait
            IconButton(onClick = onShowQueue) {
                Icon(imageVector = Icons.Default.List, contentDescription = "Queue", tint = Color.LightGray)
            }
        } else {
            // Playback mode button on left for landscape
            IconButton(onClick = toggleMode) {
                val icon = when (currentMode) {
                    top.met6.music.mobile.state.PlaybackMode.SEQUENCE -> MusicIcons.Repeat
                    top.met6.music.mobile.state.PlaybackMode.LOOP_LIST -> MusicIcons.Repeat
                    top.met6.music.mobile.state.PlaybackMode.SHUFFLE -> MusicIcons.Shuffle
                    top.met6.music.mobile.state.PlaybackMode.LOOP_SINGLE -> MusicIcons.RepeatOne
                }
                Icon(imageVector = icon, contentDescription = "Mode", tint = if (currentMode == top.met6.music.mobile.state.PlaybackMode.SEQUENCE) Color.LightGray else Color.White)
            }
        }

        // Previous
        IconButton(onClick = { AppState.prevSong() }) {
            Icon(
                imageVector = MusicIcons.SkipPrevious,
                contentDescription = "Previous",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        // Play / Pause
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
                .clickable {
                    AppState.togglePlayPause()
                }
        ) {
            Icon(
                imageVector = if (isPlaying) MusicIcons.Pause else MusicIcons.Play,
                contentDescription = "Play/Pause",
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }

        // Next
        IconButton(onClick = { AppState.nextSong() }) {
            Icon(
                imageVector = MusicIcons.SkipNext,
                contentDescription = "Next",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        if (!isLandscape) {
            // Playback mode on right for portrait
            IconButton(onClick = toggleMode) {
                val icon = when (currentMode) {
                    top.met6.music.mobile.state.PlaybackMode.SEQUENCE -> MusicIcons.Repeat
                    top.met6.music.mobile.state.PlaybackMode.LOOP_LIST -> MusicIcons.Repeat
                    top.met6.music.mobile.state.PlaybackMode.SHUFFLE -> MusicIcons.Shuffle
                    top.met6.music.mobile.state.PlaybackMode.LOOP_SINGLE -> MusicIcons.RepeatOne
                }
                Icon(imageVector = icon, contentDescription = "Mode", tint = if (currentMode == top.met6.music.mobile.state.PlaybackMode.SEQUENCE) Color.LightGray else Color.White)
            }
        } else {
            // Show queue button on the right for landscape
            IconButton(onClick = onShowQueue) {
                Icon(imageVector = Icons.Default.List, contentDescription = "Queue", tint = Color.LightGray)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LyricsView(showTranslation: Boolean = true) {
    val lines = AppState.lyricLines.value
    val activeIndex = AppState.currentLyricIndex.value
    val lazyListState = rememberLazyListState()
    val spotifyFont = if (AppState.useSpotifyFont.value) {
        Font(Res.font.spotify_mix)
    } else {
        null
    }
    val lyricFontFamily = remember(spotifyFont) {
        if (spotifyFont != null) {
            FontFamily(spotifyFont)
        } else {
            FontFamily.Default
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportHeightDp = maxHeight

        // Scroll active lyric line to the 1/5-from-top position.
        // contentPadding(top = viewportHeight/5) ensures scrollOffset=0 lands
        // the item exactly at viewport/5 from the top — no manual delta needed.
        LaunchedEffect(activeIndex) {
            if (activeIndex >= 0 && activeIndex < lines.size) {
                lazyListState.animateScrollToItem(
                    index = activeIndex,
                    scrollOffset = 0
                )
            }
        }

        if (lines.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "暂无歌词", color = Color.Gray, fontSize = 16.sp)
            }
        } else {
            val topPaddingDp = viewportHeightDp / 5
            val bottomPaddingDp = viewportHeightDp - topPaddingDp

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(top = topPaddingDp, bottom = bottomPaddingDp)
            ) {
                itemsIndexed(lines) { idx, line ->
                    val isActive = idx == activeIndex
                    val currentTimeMs = AppState.player.currentPositionMs.value
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val targetPos = line.startTimeMs - AppState.lyricOffsetMs.value
                                AppState.seekTo(targetPos.coerceAtLeast(0L))
                            },
                        horizontalAlignment = Alignment.Start
                    ) {
                        val baseLyricSize = AppState.lyricFontSize.value
                        val fontSizeSp = (if (isActive) baseLyricSize + 4 else baseLyricSize).sp
                        val lineHeightSp = (fontSizeSp.value * 1.3f).sp

                        if (line.syllables.isEmpty()) {
                            // Normal LRC line
                            val color = if (isActive) Color.White else Color.White.copy(alpha = 0.4f)
                            Text(
                                text = line.text,
                                color = color,
                                fontSize = fontSizeSp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                lineHeight = lineHeightSp,
                                fontFamily = lyricFontFamily,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            // Karaoke QRC line - using FlowRow for automatic wrapping and progressive highlight
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                line.syllables.forEach { syllable ->
                                    val elapsedMs = (currentTimeMs + AppState.lyricOffsetMs.value) - syllable.timeOffsetMs
                                    val durationMs = syllable.durationMs
                                    val progress = when {
                                        !isActive -> 0f
                                        elapsedMs < 0 -> 0f
                                        elapsedMs >= durationMs -> 1f
                                        durationMs > 0 -> elapsedMs.toFloat() / durationMs.toFloat()
                                        else -> 1f
                                    }

                                    Box {
                                        // Base layer (dimmed)
                                        Text(
                                            text = syllable.text,
                                            color = Color.White.copy(alpha = 0.4f),
                                            fontSize = fontSizeSp,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                            lineHeight = lineHeightSp,
                                            fontFamily = lyricFontFamily
                                        )

                                        if (isActive && progress > 0f) {
                                            // Overlay layer (white, clipped to progress)
                                            val activeFontSizeSp = (baseLyricSize + 4).sp
                                            val activeLineHeightSp = (activeFontSizeSp.value * 1.3f).sp
                                            Text(
                                                text = syllable.text,
                                                color = Color.White,
                                                fontSize = activeFontSizeSp,
                                                fontWeight = FontWeight.Bold,
                                                lineHeight = activeLineHeightSp,
                                                fontFamily = lyricFontFamily,
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
                        
                        line.translation?.let { tran ->
                            if (showTranslation && tran.isNotEmpty() && tran != "//") {
                                val baseTranSize = AppState.translationFontSize.value
                                val tranFontSizeSp = (if (isActive) baseTranSize + 2 else baseTranSize).sp
                                Text(
                                    text = tran,
                                    color = if (isActive) Color.White.copy(alpha = 0.8f) else Color.Gray,
                                    fontSize = tranFontSizeSp,
                                    lineHeight = (tranFontSizeSp.value * 1.3f).sp,
                                    fontFamily = lyricFontFamily,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val sec = ms / 1000
    val m = sec / 60
    val s = sec % 60
    val mStr = if (m < 10) "0$m" else "$m"
    val sStr = if (s < 10) "0$s" else "$s"
    return "$mStr:$sStr"
}

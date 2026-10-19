package top.met6.music.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.met6.music.mobile.api.ApiClient
import top.met6.music.mobile.models.Playlist
import top.met6.music.mobile.models.Song
import top.met6.music.mobile.state.AppState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(playlist: Playlist) {
    val songsState = remember { mutableStateOf<List<Song>>(emptyList()) }
    val loadingState = remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(playlist.cacheKey) {
        loadingState.value = true
        try {
            val cached = AppState.storage.getPlaylistTracksFromCache(playlist.cacheKey)
            if (cached != null) {
                songsState.value = cached
                loadingState.value = false
            }
            val response = ApiClient.getPlaylistTracks(playlist.id, platform = playlist.source)
            if (response.songs.isNotEmpty()) {
                AppState.storage.savePlaylistTracksToCache(playlist.cacheKey, response.songs.map { it.copy(source = playlist.source) })
                songsState.value = response.songs.map { it.copy(source = playlist.source) }
                AppState.updateCacheSizes()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            loadingState.value = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = playlist.name, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { AppState.navigateBack() }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val currentId = AppState.currentSong.value?.cacheKey
                        val songs = songsState.value
                        val index = songs.indexOfFirst { it.cacheKey == currentId }
                        if (index >= 0) {
                            scope.launch {
                                listState.animateScrollToItem(index + 1) // +1 for the header
                            }
                        }
                    }) {
                        Icon(imageVector = Icons.Default.LocationOn, contentDescription = "Locate", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (loadingState.value && songsState.value.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppleMusicPink)
                }
            } else {
                val songs = songsState.value
                
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    // Header section
                    item {
                        PlaylistHeader(playlist = playlist) {
                            if (songs.isNotEmpty()) {
                                AppState.playPlaylist(songs, 0)
                            }
                        }
                    }

                    // Tracklist
                    if (songs.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "歌单内暂无歌曲", color = Color.Gray, fontSize = 14.sp)
                            }
                        }
                    } else {
                        itemsIndexed(songs) { index, song ->
                            SongRow(song = song, index = index) {
                                AppState.playPlaylist(songs, index)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistHeader(playlist: Playlist, onPlayAll: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val coverUrl = playlist.bestCoverUrl
        CachedImage(
            songId = "playlist_${playlist.cacheKey}",
            imageUrl = coverUrl,
            modifier = Modifier
                .size(180.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = playlist.name,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        
        Text(
            text = "创建者: ${playlist.creator?.nickname ?: "未知"}",
            color = Color.LightGray,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp)
        )

        playlist.description?.let {
            if (it.isNotEmpty()) {
                Text(
                    text = it,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        if (!top.met6.music.mobile.state.ListenTogetherState.isInRoom.value) {
            Button(
                onClick = onPlayAll,
                colors = ButtonDefaults.buttonColors(containerColor = AppleMusicPink),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(48.dp)
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "播放全部", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SongRow(song: Song, index: Int, onClick: () -> Unit) {
    val isCurrent = AppState.currentSong.value?.cacheKey == song.cacheKey
    val hasTtml = song.hasTtml
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = (index + 1).toString(),
            color = if (isCurrent) AppleMusicPink else Color.Gray,
            fontSize = 14.sp,
            modifier = Modifier.width(32.dp)
        )
        
        val coverUrl = song.bestCoverUrl
        CachedImage(
            songId = song.cacheKey,
            imageUrl = coverUrl,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = song.name,
                    color = if (isCurrent) AppleMusicPink else Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (hasTtml) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = AppleMusicPink.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            text = "TTML",
                            color = AppleMusicPink,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Text(
                text = song.artists.joinToString(" & ") { it.name } + " - " + (song.album?.name ?: ""),
                color = Color.Gray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Text(
            text = song.formattedDuration,
            color = Color.Gray,
            fontSize = 13.sp
        )
    }
}

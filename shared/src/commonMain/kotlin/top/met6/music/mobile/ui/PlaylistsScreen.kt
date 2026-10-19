package top.met6.music.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.met6.music.mobile.models.MusicPlatform
import top.met6.music.mobile.models.Playlist
import top.met6.music.mobile.state.AppState
import top.met6.music.mobile.state.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // User Avatar
                        val avatar = AppState.avatarUrl.value
                        if (avatar.isNotEmpty()) {
                            CachedImage(
                                songId = "user_avatar_${AppState.selectedPlatform.value.apiValue}_${AppState.qqId.value}",
                                imageUrl = avatar,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.Gray)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column {
                            Text(
                                text = "资料库",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = AppState.nickname.value,
                                fontSize = 12.sp,
                                color = Color.LightGray
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { AppState.navigateTo(Screen.ListenTogether) }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Listen Together",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { AppState.navigateTo(Screen.Settings) }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
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
        Column(Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MusicPlatform.entries.forEach { platform ->
                    FilterChip(selected = AppState.selectedPlatform.value == platform,
                        onClick = { AppState.selectPlatform(platform) }, label = { Text(platform.title) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = CardBackground, labelColor = Color.White,
                            selectedContainerColor = AppleMusicPink, selectedLabelColor = Color.White))
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val list = AppState.playlists.value

                if (list.isEmpty()) {
                    if (AppState.userPlaylistsLoading.value) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AppleMusicPink)
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = if (AppState.isLoggedIn.value) "没有找到歌单" else "在设置中登录${AppState.selectedPlatform.value.title}账号以载入歌单", color = Color.Gray, fontSize = 16.sp)
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(list) { playlist ->
                            PlaylistItem(playlist = playlist) {
                                AppState.navigateTo(Screen.PlaylistDetail(playlist))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistItem(playlist: Playlist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        val coverUrl = playlist.bestCoverUrl
        CachedImage(
            songId = "playlist_${playlist.cacheKey}",
            imageUrl = coverUrl,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = playlist.name,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        Text(
            text = "${playlist.trackCount} 首歌曲",
            color = Color.Gray,
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}

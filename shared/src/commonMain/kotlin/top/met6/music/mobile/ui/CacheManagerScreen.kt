package top.met6.music.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.met6.music.mobile.models.Song
import top.met6.music.mobile.state.AppState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheManagerScreen() {
    val scope = rememberCoroutineScope()
    var cachedSongs by remember { mutableStateOf<List<top.met6.music.mobile.storage.CachedSongEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    fun refreshCache() {
        scope.launch {
            isLoading = true
            cachedSongs = AppState.storage.getCachedSongs()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshCache()
    }

    // Format size to MB with two decimal places
    fun formatSize(bytes: Long): String {
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        val integerPart = mb.toLong()
        val fractionalPart = ((mb - integerPart) * 100).toLong()
        val fracStr = if (fractionalPart < 0) -fractionalPart else fractionalPart
        val fracPad = if (fracStr < 10) "0$fracStr" else "$fracStr"
        return "$integerPart.$fracPad MB"
    }

    val totalSize = cachedSongs.sumOf { it.size }
    val totalCount = cachedSongs.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "已缓存音频管理", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { AppState.navigateBack() }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { refreshCache() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Summary Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "总计缓存", color = Color.Gray, fontSize = 12.sp)
                        Text(
                            text = formatSize(totalSize),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "歌曲数量", color = Color.Gray, fontSize = 12.sp)
                        Text(
                            text = "$totalCount 首",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppleMusicPink)
                }
            } else if (cachedSongs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text(text = "暂无已缓存的歌曲", color = Color.Gray, fontSize = 16.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(cachedSongs) { (song, quality, size, cacheFile) ->
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBackground),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CachedImage(
                                    songId = song.cacheKey,
                                    imageUrl = song.bestCoverUrl,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.name,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = song.artists.joinToString(" & ") { it.name },
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val qualityLabel = when (quality) {
                                            "web" -> "普通 WEB"
                                            "hq" -> "极高 HQ"
                                            "sq" -> "无损 SQ"
                                            "rs" -> "Hi-Res"
                                            "dts" -> "杜比 5.1"
                                            "q360v1" -> "全景声 V1"
                                            "q360v2" -> "全景声 V2"
                                            "qai" -> "臻品母带"
                                            else -> "未知"
                                        }
                                        val badgeColor = when (quality) {
                                            "qai" -> Color(0xFFE040FB) // purple/pink magenta
                                            "q360v2", "q360v1" -> Color(0xFF00E5FF) // cyan
                                            "dts" -> Color(0xFFFF9100) // orange
                                            "rs" -> Color(0xFFFFD700) // gold
                                            "sq" -> AppleMusicPink
                                            "hq" -> Color(0xFF00C853) // green
                                            "web" -> Color(0xFF29B6F6) // blue
                                            else -> Color.Gray
                                        }

                                        Box(
                                            modifier = Modifier
                                                .border(1.dp, badgeColor, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = qualityLabel,
                                                color = badgeColor,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Text(
                                            text = formatSize(size),
                                            color = Color.LightGray.copy(alpha = 0.8f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            AppState.storage.deleteCachedSong(cacheFile)
                                            AppState.updateCacheSizes()
                                            refreshCache()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Cache",
                                        tint = Color.LightGray
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

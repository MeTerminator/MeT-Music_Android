package top.met6.music.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.met6.music.mobile.state.AppState

// Material design icons for Play and Pause
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniPlayerBar() {
    val song = AppState.currentSong.value ?: return
    var showQueueSheet by remember { mutableStateOf(false) }
    val queueListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Box {
      Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable { AppState.isFullScreenPlayer.value = true },
        shape = RoundedCornerShape(32.dp),
        color = CardBackground.copy(alpha = 0.96f),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(60.dp).padding(horizontal = 12.dp)
      ) {
        val coverUrl = song.bestCoverUrl
        CachedImage(
            songId = song.cacheKey,
            imageUrl = coverUrl,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp))
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.name,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artists.joinToString(" & ") { it.name },
                color = Color.LightGray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // Play/Pause button
        IconButton(
            onClick = {
                AppState.togglePlayPause()
            }
        ) {
            Icon(
                imageVector = if (AppState.player.isPlaying.value) MusicIcons.Pause else MusicIcons.Play,
                contentDescription = "Play/Pause",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        
        IconButton(
            onClick = {
                showQueueSheet = true
            }
        ) {
            Icon(
                imageVector = Icons.Default.List,
                contentDescription = "播放列表",
                tint = Color.White,
                modifier = Modifier.size(25.dp)
            )
        }
      }
      }

      if (showQueueSheet) {
          ModalBottomSheet(
              onDismissRequest = { showQueueSheet = false },
              containerColor = CardBackground,
              contentColor = Color.White,
          ) {
              Column(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
              ) {
                  Row(
                      modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                  ) {
                      Text("播放列表", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                      IconButton(onClick = {
                          val index = AppState.currentQueue.value.indexOfFirst { it.cacheKey == AppState.currentSong.value?.cacheKey }
                          if (index >= 0) scope.launch { queueListState.animateScrollToItem(index) }
                      }) {
                          Icon(Icons.Default.LocationOn, contentDescription = "定位当前歌曲", tint = Color.LightGray)
                      }
                  }
                  LazyColumn(state = queueListState, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
                      itemsIndexed(AppState.currentQueue.value) { index, queueSong ->
                          SongRow(queueSong, index) {
                              AppState.playSongAtIndex(index)
                              showQueueSheet = false
                          }
                      }
                  }
                  Spacer(Modifier.height(24.dp))
              }
          }
      }
    }
}

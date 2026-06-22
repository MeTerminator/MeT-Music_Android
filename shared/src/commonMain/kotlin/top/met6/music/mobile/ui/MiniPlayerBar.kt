package top.met6.music.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape

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
import top.met6.music.mobile.state.AppState

// Material design icons for Play and Pause
@Composable
fun MiniPlayerBar() {
    val song = AppState.currentSong.value ?: return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(CardBackground.copy(alpha = 0.95f))
            .clickable { AppState.isFullScreenPlayer.value = true }
            .padding(horizontal = 16.dp)
    ) {
        val coverUrl = song.bestCoverUrl
        CachedImage(
            songId = song.id,
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
        
        Spacer(modifier = Modifier.width(4.dp))
        
        // Next button
        IconButton(
            onClick = { AppState.nextSong() }
        ) {
            Icon(
                imageVector = MusicIcons.SkipNext,
                contentDescription = "Next",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

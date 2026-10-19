package top.met6.music.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.met6.music.mobile.state.AppState
import top.met6.music.mobile.state.Screen

@Composable
fun AppNavigation() {
    val currentScreen = AppState.currentScreen.value

    // Handle system back button / gestures on Android
    val isBackEnabled = AppState.isFullScreenPlayer.value || AppState.navigationStack.isNotEmpty()
    BindBackHandler(enabled = isBackEnabled) {
        if (AppState.isFullScreenPlayer.value) {
            AppState.isFullScreenPlayer.value = false
        } else {
            AppState.navigateBack()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        val enteringPlaylistDetail = targetState is Screen.PlaylistDetail && initialState is Screen.Playlists
                        val leavingPlaylistDetail = initialState is Screen.PlaylistDetail && targetState is Screen.Playlists
                        when {
                            enteringPlaylistDetail ->
                                (fadeIn() + slideInHorizontally { it }) togetherWith
                                    (fadeOut() + slideOutHorizontally { -it })
                            leavingPlaylistDetail ->
                                (fadeIn() + slideInHorizontally { -it }) togetherWith
                                    (fadeOut() + slideOutHorizontally { it })
                            else ->
                                (fadeIn() + slideInHorizontally { it / 8 }) togetherWith
                                    (fadeOut() + slideOutHorizontally { -it / 8 })
                        }
                    },
                    label = "page transition",
                ) { screen ->
                    when (screen) {
                        is Screen.Playlists -> PlaylistsScreen()
                        is Screen.PlaylistDetail -> PlaylistDetailScreen(playlist = screen.playlist)
                        is Screen.Settings -> SettingsScreen()
                        is Screen.CacheManagerDetail -> CacheManagerScreen()
                        is Screen.DesktopLyricSettings -> DesktopLyricSettingsScreen()
                        is Screen.ListenTogether -> ListenTogetherScreen()
                    }
                }
            }

        }

        // Float above the page content without taking layout space.
        if (AppState.currentSong.value != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = maxHeight * 0.05f),
                contentAlignment = androidx.compose.ui.Alignment.BottomCenter,
            ) {
                MiniPlayerBar()
            }
        }

        AnimatedVisibility(
            visible = AppState.isFullScreenPlayer.value,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            FullScreenPlayer()
        }

        // Keep the global toast above the full-screen player and lyric surface.
        AnimatedVisibility(
            visible = AppState.globalToastMessage.value.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(bottom = 120.dp),
                contentAlignment = androidx.compose.ui.Alignment.BottomCenter
            ) {
                Surface(
                    color = Color(0xCC000000),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = AppState.globalToastMessage.value,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

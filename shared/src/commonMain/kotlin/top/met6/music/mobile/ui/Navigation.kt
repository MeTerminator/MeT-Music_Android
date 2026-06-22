package top.met6.music.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
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
    val isLoggedIn = AppState.isLoggedIn.value

    // Handle system back button / gestures on Android
    val isBackEnabled = AppState.isFullScreenPlayer.value || AppState.navigationStack.isNotEmpty()
    BindBackHandler(enabled = isBackEnabled) {
        if (AppState.isFullScreenPlayer.value) {
            AppState.isFullScreenPlayer.value = false
        } else {
            AppState.navigateBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (currentScreen) {
                    is Screen.Login -> LoginScreen()
                    is Screen.Playlists -> PlaylistsScreen()
                    is Screen.PlaylistDetail -> PlaylistDetailScreen(playlist = currentScreen.playlist)
                    is Screen.Settings -> SettingsScreen()
                    is Screen.CacheManagerDetail -> CacheManagerScreen()
                    is Screen.DesktopLyricSettings -> DesktopLyricSettingsScreen()
                    is Screen.ListenTogether -> ListenTogetherScreen()
                }
            }

            if (isLoggedIn && currentScreen != Screen.Login) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    MiniPlayerBar()

                    NavigationBar(
                        containerColor = CardBackground,
                        contentColor = Color.White
                    ) {
                        NavigationBarItem(
                            selected = currentScreen is Screen.Playlists || currentScreen is Screen.PlaylistDetail,
                            onClick = { AppState.navigateTo(Screen.Playlists) },
                            icon = { Icon(imageVector = Icons.Default.List, contentDescription = "Library") },
                            label = { Text("资料库", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AppleMusicPink,
                                selectedTextColor = AppleMusicPink,
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray,
                                indicatorColor = Color.Transparent
                            )
                        )
                        NavigationBarItem(
                            selected = currentScreen is Screen.ListenTogether,
                            onClick = { AppState.navigateTo(Screen.ListenTogether) },
                            icon = { Icon(imageVector = Icons.Default.Share, contentDescription = "Listen Together") },
                            label = { Text("一起听", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AppleMusicPink,
                                selectedTextColor = AppleMusicPink,
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray,
                                indicatorColor = Color.Transparent
                            )
                        )
                        NavigationBarItem(
                            selected = currentScreen is Screen.Settings || currentScreen is Screen.CacheManagerDetail || currentScreen is Screen.DesktopLyricSettings,
                            onClick = { AppState.navigateTo(Screen.Settings) },
                            icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings") },
                            label = { Text("设置", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AppleMusicPink,
                                selectedTextColor = AppleMusicPink,
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray,
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }

        // Global Toast
        if (AppState.globalToastMessage.value.isNotEmpty()) {
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

        AnimatedVisibility(
            visible = AppState.isFullScreenPlayer.value,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            FullScreenPlayer()
        }
    }
}

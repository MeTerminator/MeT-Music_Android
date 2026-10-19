package top.met6.music.mobile.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.met6.music.mobile.models.Song
import top.met6.music.mobile.state.AppState
import top.met6.music.mobile.state.ListenTogetherState
import top.met6.music.mobile.state.Screen
import top.met6.music.mobile.state.toSong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenTogetherScreen() {
    val isInRoom = ListenTogetherState.isInRoom.value

    Column(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        TopAppBar(
            title = { Text("一起听歌", color = Color.White) },
            navigationIcon = {
                IconButton(onClick = { AppState.navigateTo(Screen.Playlists) }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "返回资料库",
                        tint = Color.White,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = DarkBackground,
                titleContentColor = Color.White,
            ),
        )
        Box(modifier = Modifier.weight(1f)) {
            if (!isInRoom) {
                JoinOrCreateLayout()
            } else {
                RoomLayout()
            }
        }
    }
}

@Composable
fun JoinOrCreateLayout() {
    // Prefill details
    val defaultNickname = if (AppState.nickname.value != "未登录") AppState.nickname.value else ""
    val defaultAvatar = AppState.avatarUrl.value

    var nickname by remember { mutableStateOf(AppState.platformStorage.getText("listen_together_nickname") ?: defaultNickname) }
    var avatar by remember { mutableStateOf(AppState.platformStorage.getText("listen_together_avatar") ?: defaultAvatar) }
    var isAnonymous by remember { mutableStateOf(AppState.platformStorage.getText("listen_together_is_anonymous") == "true") }
    var roomCodeInput by remember { mutableStateOf("") }

    val connecting = ListenTogetherState.connecting.value
    val errorMessage = ListenTogetherState.errorMessage.value

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(bottom = 36.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "与好友实时共享播放队列，同步切歌与进度",
                fontSize = 13.sp,
                color = Color.LightGray.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
            )

            // User Info Setup Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "你的听歌档案",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Checkbox(
                            checked = isAnonymous,
                            onCheckedChange = { isAnonymous = it },
                            colors = CheckboxDefaults.colors(checkedColor = AppleMusicPink)
                        )
                        Text(text = "匿名加入 (使用随机生成的头像与昵称)", color = Color.White, fontSize = 14.sp)
                    }

                    if (!isAnonymous) {
                        OutlinedTextField(
                            value = nickname,
                            onValueChange = { nickname = it },
                            label = { Text("昵称", color = Color.Gray) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AppleMusicPink,
                                unfocusedBorderColor = Color.DarkGray
                            ),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        )

                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Create Room Section
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "成为房主",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
                    )
                    Text(
                        text = "创建一个新的共享房间，邀请好友进入听歌",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.Start).padding(bottom = 16.dp)
                    )

                    Button(
                        onClick = {
                            ListenTogetherState.createAndConnectRoom(nickname, avatar, isAnonymous)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppleMusicPink),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !connecting,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("创建房间", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Join Room Section
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "加入已有房间",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
                    )
                    Text(
                        text = "输入好友的6位数房间号直接进入",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.Start).padding(bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = roomCodeInput,
                        onValueChange = {
                            if (it.length <= 6) roomCodeInput = it
                        },
                        placeholder = { Text("请输入6位数房间号", color = Color.Gray) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        textStyle = TextStyle(textAlign = TextAlign.Center),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = AppleMusicPink,
                            unfocusedBorderColor = Color.DarkGray
                        ),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    )

                    Button(
                        onClick = {
                            ListenTogetherState.joinAndConnectRoom(roomCodeInput, nickname, avatar, isAnonymous)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !connecting && roomCodeInput.length == 6,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("加入房间", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            if (connecting) {
                Spacer(modifier = Modifier.height(24.dp))
                CircularProgressIndicator(color = AppleMusicPink)
            }

            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage,
                    color = Color.Red,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            val successMessage = ListenTogetherState.successMessage.value
            if (successMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = successMessage,
                    color = Color.Green,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomLayout() {
    val details = ListenTogetherState.roomDetails.value
    val code = ListenTogetherState.roomCode.value
    val remaining = ListenTogetherState.remainingTime.value
    val autoRenew = ListenTogetherState.autoRenew.value
    val playlist = remember(details) { details?.playlist?.map { it.toSong() } ?: emptyList() }

    val min = remaining / 60
    val sec = remaining % 60
    val countdownText = "${min}分${if (sec < 10) "0$sec" else "$sec"}秒"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("一起听歌房间", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { AppState.navigateTo(Screen.Playlists) }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    TextButton(onClick = { ListenTogetherState.renewRoom() }) {
                        Text("续期 1h", color = AppleMusicPink)
                    }
                    IconButton(onClick = { ListenTogetherState.leaveRoom() }) {
                        Icon(imageVector = Icons.Default.ExitToApp, contentDescription = "Exit Room", tint = Color.Red)
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Room Overview Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(text = "房间号", fontSize = 12.sp, color = Color.Gray)
                                Text(
                                    text = code,
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    letterSpacing = 2.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = "剩余时间", fontSize = 12.sp, color = Color.Gray)
                                Text(
                                    text = countdownText,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (remaining < 300) Color.Red else AppleMusicPink
                                )
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color.DarkGray)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = autoRenew,
                                onCheckedChange = { ListenTogetherState.autoRenew.value = it },
                                colors = CheckboxDefaults.colors(checkedColor = AppleMusicPink)
                            )
                            Text(text = "自动续期 (剩余不足30分钟时自动延长1小时)", color = Color.LightGray, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Room Configuration Card
            if (details != null) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "房间播放设置",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            ) {
                                Text(text = "播放模式", color = Color.LightGray, fontSize = 14.sp)
                                Row {
                                    val isNormal = details.playMode == "normal"
                                    Button(
                                        onClick = { ListenTogetherState.setPlayMode("normal") },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isNormal) AppleMusicPink else Color.DarkGray
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("顺序", fontSize = 12.sp, color = Color.White)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { ListenTogetherState.setPlayMode("random") },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (!isNormal) AppleMusicPink else Color.DarkGray
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("随机", fontSize = 12.sp, color = Color.White)
                                    }
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                Column {
                                    Text(text = "播放后自动删除", color = Color.LightGray, fontSize = 14.sp)
                                    Text(text = "从队列播放后自动移出歌曲", color = Color.Gray, fontSize = 11.sp)
                                }
                                Switch(
                                    checked = details.deleteAfterPlayed,
                                    onCheckedChange = {
                                        ListenTogetherState.updateSettings(it, details.loopPlaylist)
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = AppleMusicPink)
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    Text(text = "循环播放列表", color = Color.LightGray, fontSize = 14.sp)
                                    Text(text = "队列播放完毕后是否循环", color = Color.Gray, fontSize = 11.sp)
                                }
                                Switch(
                                    checked = details.loopPlaylist,
                                    onCheckedChange = {
                                        ListenTogetherState.updateSettings(details.deleteAfterPlayed, it)
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = AppleMusicPink)
                                )
                            }
                        }
                    }
                }

                // Online Members Card
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "在线成员 (${details.members.size})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(details.members) { member ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.width(60.dp)
                                    ) {
                                        val avatarUrl = member.avatar.takeIf { it.isNotEmpty() } ?: member.user?.avatar ?: ""
                                        if (avatarUrl.isNotEmpty()) {
                                            CachedImage(
                                                songId = "avatar_${member.userId}",
                                                imageUrl = avatarUrl,
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(CircleShape)
                                                    .border(1.5.dp, AppleMusicPink, CircleShape)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.Gray)
                                                    .border(1.5.dp, AppleMusicPink, CircleShape)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = member.nickname.takeIf { it.isNotEmpty() } ?: member.user?.nickname ?: "Unknown",
                                            fontSize = 11.sp,
                                            color = Color.LightGray,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Shared Queue List Card
                item {
                    Text(
                        text = "共享播放队列 (${playlist.size})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                if (playlist.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBackground),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "队列中暂无歌曲。在资料库或歌单里点击歌曲，可自动添加到这里一起听。",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(playlist) { idx, song ->
                        val isPlayingThis = details.currentSongIndex == idx
                        val containerBg = if (isPlayingThis) CardBackground.copy(alpha = 0.5f) else CardBackground
                        val borderMod = if (isPlayingThis) Modifier.border(1.dp, AppleMusicPink, RoundedCornerShape(12.dp)) else Modifier

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = containerBg),
                            modifier = Modifier.fillMaxWidth().then(borderMod)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Cover Image
                                CachedImage(
                                    songId = song.cacheKey,
                                    imageUrl = song.bestCoverUrl,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                // Title / Artist
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = song.name,
                                        color = if (isPlayingThis) AppleMusicPink else Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = song.artists.joinToString(" / ") { it.name },
                                        color = Color.LightGray,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Play button (if not currently playing)
                                IconButton(
                                    onClick = { ListenTogetherState.playIndex(idx) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlayingThis && details.isPlaying) Icons.Default.PlayArrow else Icons.Default.PlayArrow,
                                        contentDescription = "Play Index",
                                        tint = if (isPlayingThis) AppleMusicPink else Color.Gray
                                    )
                                }

                                // Delete Button
                                IconButton(
                                    onClick = { ListenTogetherState.removeSong(idx) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete from Room Playlist",
                                        tint = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }

                // Room Action Logs Card
                item {
                    Text(
                        text = "房间日志",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                }

                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        modifier = Modifier.fillMaxWidth().height(160.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                            val stateLogs = details.logs
                            if (stateLogs.isEmpty()) {
                                Text(
                                    text = "暂无日志",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    reverseLayout = true
                                ) {
                                    items(stateLogs.reversed()) { log ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "[${formatLogTime(log.timestamp.toLong())}]",
                                                color = AppleMusicPink,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "${log.user} ${log.action}",
                                                color = Color.LightGray,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = { ListenTogetherState.deleteRoom() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(25.dp)
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Room", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("解散房间", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

fun formatLogTime(timestamp: Long): String {
    val seconds = (timestamp / 1000) % 60
    val minutes = (timestamp / (1000 * 60)) % 60
    val hours = (timestamp / (1000 * 60 * 60) + 8) % 24
    val hStr = if (hours < 10) "0$hours" else "$hours"
    val mStr = if (minutes < 10) "0$minutes" else "$minutes"
    val sStr = if (seconds < 10) "0$seconds" else "$seconds"
    return "$hStr:$mStr:$sStr"
}

package top.met6.music.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.met6.music.mobile.state.AppState
import top.met6.music.mobile.lyric.isAndroid
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    // Refresh cache sizes when entering Settings
    LaunchedEffect(Unit) {
        AppState.updateCacheSizes()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "设置") },
                navigationIcon = {
                    IconButton(onClick = { AppState.navigateBack() }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // User Profile Section
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    val avatar = AppState.avatarUrl.value
                    if (avatar.isNotEmpty()) {
                        CachedImage(
                            songId = "user_avatar",
                            imageUrl = avatar,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                            .background(Color.Gray)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = AppState.nickname.value,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "QQ: ${AppState.qqId.value}",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                    
                    Button(
                        onClick = { AppState.logout() },
                        colors = ButtonDefaults.buttonColors(containerColor = AppleMusicPink),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text(text = "登出", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Sound Quality Section
            Text(
                text = "音质选择",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
            )
            
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    val qualities = listOf(
                        Triple("web", "普通 WEB", "在线流媒体音质"),
                        Triple("hq", "极高 HQ", "近 CD 品质的细节体验，最高 320kbps"),
                        Triple("sq", "无损 SQ", "高保真无损音质，最高 48kHz/24bit"),
                        Triple("rs", "高分辨率音源 Hi-Res", "索尼高品质音乐标准，高于 44.1kHz/16bit"),
                        Triple("dts", "杜比 5.1 声道", "六声道环绕声，使人产生犹如身临音乐厅的感觉"),
                        Triple("q360v1", "臻品全景声 V1", "独家自研空间音频，V1 版本，立体声"),
                        Triple("q360v2", "臻品全景声 V2", "独家自研空间音频，V2 版本，多声道"),
                        Triple("qai", "臻品母带", "还原声音细节，让声音还原更加极致")
                    )
                    
                    qualities.forEach { (key, label, tip) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { AppState.setSoundQualityLevel(key) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = AppState.soundQuality.value == key,
                                onClick = { AppState.setSoundQualityLevel(key) },
                                colors = RadioButtonDefaults.colors(selectedColor = AppleMusicPink)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(text = label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(text = tip, color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Lyric Offset Section
            Text(
                text = "歌词设置",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
            )
            
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Item 1: Overall Offset
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "歌词整体偏移 (整体同步)",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        val offset = AppState.lyricOffsetMs.value
                        val offsetStr = if (offset > 0) "+${offset}ms" else "${offset}ms"
                        Text(
                            text = offsetStr,
                            color = AppleMusicPink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val currentOffset = AppState.lyricOffsetMs.value.toFloat()
                    Slider(
                        value = currentOffset,
                        onValueChange = {
                            val targetOffset = (kotlin.math.round(it / 50f) * 50f).toLong()
                            AppState.setLyricOffset(targetOffset)
                        },
                        valueRange = -2000f..2000f,
                        colors = SliderDefaults.colors(
                            thumbColor = AppleMusicPink,
                            activeTrackColor = AppleMusicPink,
                            inactiveTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "提前 -2.0s", color = Color.Gray, fontSize = 11.sp)
                        Text(text = "默认 0ms", color = Color.Gray, fontSize = 11.sp)
                        Text(text = "延后 +2.0s", color = Color.Gray, fontSize = 11.sp)
                    }
                    
                    HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 16.dp))
                    
                    // Item 2: Early Transition Lead
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "提前切换时间 (换行灵敏度)",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        val lead = AppState.lyricLeadMs.value
                        Text(
                            text = "+${lead}ms",
                            color = AppleMusicPink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val currentLead = AppState.lyricLeadMs.value.toFloat()
                    Slider(
                        value = currentLead,
                        onValueChange = {
                            val targetLead = (kotlin.math.round(it / 50f) * 50f).toLong()
                            AppState.setLyricLead(targetLead)
                        },
                        valueRange = 0f..1000f,
                        colors = SliderDefaults.colors(
                            thumbColor = AppleMusicPink,
                            activeTrackColor = AppleMusicPink,
                            inactiveTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "无延迟 0ms", color = Color.Gray, fontSize = 11.sp)
                        Text(text = "默认 +300ms", color = Color.Gray, fontSize = 11.sp)
                        Text(text = "较早 +1000ms", color = Color.Gray, fontSize = 11.sp)
                    }
                    
                    HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 16.dp))
                    
                    // Item 3: Lyric Font Size
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "歌词字体大小",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        val lyricSize = AppState.lyricFontSize.value
                        Text(
                            text = "${lyricSize}sp",
                            color = AppleMusicPink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val currentLyricSize = AppState.lyricFontSize.value.toFloat()
                    Slider(
                        value = currentLyricSize,
                        onValueChange = {
                            AppState.setLyricFontSize(it.roundToInt())
                        },
                        valueRange = 14f..28f,
                        colors = SliderDefaults.colors(
                            thumbColor = AppleMusicPink,
                            activeTrackColor = AppleMusicPink,
                            inactiveTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "小 (14sp)", color = Color.Gray, fontSize = 11.sp)
                        Text(text = "默认 (20sp)", color = Color.Gray, fontSize = 11.sp)
                        Text(text = "大 (28sp)", color = Color.Gray, fontSize = 11.sp)
                    }
                    
                    HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 16.dp))
                    
                    // Item 4: Translation Font Size
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "翻译字体大小",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        val tranSize = AppState.translationFontSize.value
                        Text(
                            text = "${tranSize}sp",
                            color = AppleMusicPink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val currentTranSize = AppState.translationFontSize.value.toFloat()
                    Slider(
                        value = currentTranSize,
                        onValueChange = {
                            AppState.setTranslationFontSize(it.roundToInt())
                        },
                        valueRange = 10f..22f,
                        colors = SliderDefaults.colors(
                            thumbColor = AppleMusicPink,
                            activeTrackColor = AppleMusicPink,
                            inactiveTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "小 (10sp)", color = Color.Gray, fontSize = 11.sp)
                        Text(text = "默认 (14sp)", color = Color.Gray, fontSize = 11.sp)
                        Text(text = "大 (22sp)", color = Color.Gray, fontSize = 11.sp)
                    }

                    HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "歌词使用 Spotify 字体",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "在歌词播放页启用 Spotify 专用字体",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = AppState.useSpotifyFont.value,
                            onCheckedChange = { AppState.setUseSpotifyFont(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AppleMusicPink
                            )
                        )
                    }

                    if (isAndroid()) {
                        HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 16.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { AppState.navigateTo(top.met6.music.mobile.state.Screen.DesktopLyricSettings) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "桌面歌词 (悬浮窗)",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "在桌面上显示歌词，支持 KTV 逐字歌词样式",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = "设置 >",
                                color = AppleMusicPink,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            // Listen Together Settings Section
            Text(
                text = "一起听歌",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
            )
            
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "同步跳跃阈值: ${((AppState.syncThresholdSec.value * 10).toInt() / 10f)}s",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "阈值越小，两端进度差距稍微过大就会频繁触发跳转同步，导致卡顿；阈值越大，允许的进度误差越大，但在网络波动时音频会更平顺。",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                    Slider(
                        value = AppState.syncThresholdSec.value,
                        onValueChange = { 
                            AppState.syncThresholdSec.value = it
                        },
                        onValueChangeFinished = {
                            AppState.platformStorage.saveText("sync_threshold", AppState.syncThresholdSec.value.toString())
                        },
                        valueRange = 0.1f..5.0f,
                        steps = 48, // (5.0 - 0.1) / 0.1 - 1
                        colors = SliderDefaults.colors(
                            thumbColor = AppleMusicPink,
                            activeTrackColor = AppleMusicPink
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Cache Settings Section
            Text(
                text = "缓存管理",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
            )
            
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    CacheRow(
                        title = "歌单数据缓存",
                        sizeMb = AppState.playlistCacheSize.value
                    ) {
                        AppState.clearCacheCategory("playlists")
                    }
                    
                    Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 12.dp))
                    
                    CacheRow(
                        title = "歌词数据缓存",
                        sizeMb = AppState.lyricCacheSize.value
                    ) {
                        AppState.clearCacheCategory("lyrics")
                    }
                    
                    Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 12.dp))
                    
                    CacheRow(
                        title = "封面图片缓存",
                        sizeMb = AppState.coverCacheSize.value
                    ) {
                        AppState.clearCacheCategory("covers")
                    }
                    
                    Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 12.dp))
                    
                    CacheRow(
                        title = "音频文件缓存",
                        sizeMb = AppState.songCacheSize.value
                    ) {
                        AppState.clearCacheCategory("songs")
                    }

                    Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { AppState.navigateTo(top.met6.music.mobile.state.Screen.CacheManagerDetail) }
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "管理已缓存音频", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = "查看并单独清除已缓存的歌曲", color = Color.Gray, fontSize = 12.sp)
                        }
                        Text(text = "管理 >", color = AppleMusicPink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
fun CacheRow(title: String, sizeMb: Double, onClear: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            val integerPart = sizeMb.toLong()
            val fractionalPart = ((sizeMb - integerPart) * 100).toLong()
            val fracStr = if (fractionalPart < 0) -fractionalPart else fractionalPart
            val fracPad = if (fracStr < 10) "0$fracStr" else "$fracStr"
            Text(text = "$integerPart.$fracPad MB", color = Color.Gray, fontSize = 12.sp)
        }
        
        OutlinedButton(
            onClick = onClear,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppleMusicPink),
            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(AppleMusicPink)),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            Text(text = "清除", fontSize = 13.sp)
        }
    }
}

package com.example.earwormdiary.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.earwormdiary.data.network.NeteaseApi
import com.example.earwormdiary.ui.components.AlbumCover
import com.example.earwormdiary.utils.loadMusicFromCache
import kotlinx.coroutines.launch

@Composable
fun SongSelectionView(
    targetDate: java.time.LocalDate,
    folderUris: List<Uri>,
    onSongSelected: (com.example.earwormdiary.data.model.LocalSong) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 本地数据
    var allSongs by remember { mutableStateOf<List<com.example.earwormdiary.data.model.LocalSong>>(emptyList()) }
    var filteredSongs by remember { mutableStateOf<List<com.example.earwormdiary.data.model.LocalSong>>(emptyList()) }

    // 网络数据
    var networkSongs by remember { mutableStateOf<List<com.example.earwormdiary.data.model.LocalSong>>(emptyList()) }
    var isSearchingNetwork by remember { mutableStateOf(false) }
    var hasSearchedNetwork by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    // 初始化加载本地
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            allSongs = loadMusicFromCache(context)
            filteredSongs = allSongs
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    // 搜索框变化时：过滤本地 + 重置网络状态
    LaunchedEffect(searchQuery, allSongs) {
        hasSearchedNetwork = false
        networkSongs = emptyList()

        filteredSongs = if (searchQuery.isBlank()) {
            allSongs
        } else {
            allSongs.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.artist.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // 执行网络搜索
    fun performNetworkSearch() {
        if (searchQuery.isBlank()) return

        isSearchingNetwork = true
        scope.launch {
            val results = NeteaseApi.searchOnline(searchQuery)
            networkSongs = results
            isSearchingNetwork = false
            hasSearchedNetwork = true

            if (results.isEmpty()) {
                Toast.makeText(context, "未找到相关网络歌曲", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // 顶部栏
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "取消")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "选择记录",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        // 搜索框
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("搜索歌曲") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    // 1. “无”选项
                    item(key = "special_none") {
                        SongListItem(
                            song = com.example.earwormdiary.data.model.LocalSong.createNone(),
                            onClick = { onSongSelected(com.example.earwormdiary.data.model.LocalSong.createNone()) }
                        )
                    }

                    // 2. “纯文字”选项
                    if (searchQuery.isNotBlank()) {
                        item(key = "special_text_${searchQuery}") {
                            val textSong = com.example.earwormdiary.data.model.LocalSong.createText(searchQuery)
                            SongListItem(
                                song = textSong,
                                onClick = { onSongSelected(textSong) }
                            )
                        }
                    }

                    // 3. 本地结果
                    if (filteredSongs.isNotEmpty()) {
                        item {
                            Text("本地结果", style = MaterialTheme.typography.labelMedium, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp))
                        }
                        items(filteredSongs, key = { it.id }) { song ->
                            SongListItem(song = song, onClick = { onSongSelected(song) })
                        }
                    }

                    // 4. 联网搜索逻辑
                    if (searchQuery.isNotBlank()) {
                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }

                        if (!hasSearchedNetwork && !isSearchingNetwork) {
                            // A. 显示“启用联网检索”按钮
                            item {
                                Surface(
                                    onClick = { performNetworkSearch() },
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "联网搜索:  \"$searchQuery\"",
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        } else if (isSearchingNetwork) {
                            // B. 正在加载
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("正在连接网易云音乐...", color = Color.Gray)
                                }
                            }
                        } else if (networkSongs.isNotEmpty()) {
                            // C. 显示网络结果
                            item {
                                Text("网络结果 (网易云音乐)", style = MaterialTheme.typography.labelMedium, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp))
                            }
                            // 网络歌曲 ID 为了防重可以加前缀，但 LazyColumn key 只要唯一即可
                            items(networkSongs, key = { "net_${it.id}" }) { song ->
                                SongListItem(song = song, onClick = { onSongSelected(song) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SongListItem(song: com.example.earwormdiary.data.model.LocalSong, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumCover(
                song = song,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Column(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .weight(1f)
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (song.isText) {
                    Text(
                        text = "点击将其作为歌名记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (!song.isNone) {
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
package com.example.earwormdiary

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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

@Composable
fun SongSelectionView(
    targetDate: java.time.LocalDate,
    folderUris: List<Uri>,
    onSongSelected: (LocalSong) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current

    // 缓存的所有歌曲
    var allSongs by remember { mutableStateOf<List<LocalSong>>(emptyList()) }
    // 过滤后的歌曲
    var filteredSongs by remember { mutableStateOf<List<LocalSong>>(emptyList()) }

    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    // 加载缓存
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

    // 搜索过滤逻辑
    LaunchedEffect(searchQuery, allSongs) {
        filteredSongs = if (searchQuery.isBlank()) {
            allSongs
        } else {
            allSongs.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.artist.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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

                    // 始终显示“无”选项
                    item(key = "special_none") {
                        SongListItem(
                            song = LocalSong.createNone(),
                            onClick = { onSongSelected(LocalSong.createNone()) }
                        )
                    }

                    // 如果输入了文字，提供“记录纯文字”选项
                    if (searchQuery.isNotBlank()) {
                        item(key = "special_text_${searchQuery}") {
                            val textSong = LocalSong.createText(searchQuery)
                            SongListItem(
                                song = textSong,
                                onClick = { onSongSelected(textSong) }
                            )
                        }
                    }

                    // 普通歌曲列表
                    items(filteredSongs, key = { it.id }) { song ->
                        SongListItem(song = song, onClick = { onSongSelected(song) })
                    }

                    // 底部提示
                    if (filteredSongs.isEmpty() && searchQuery.isBlank()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                                Text("输入文字可直接记录歌曲", color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SongListItem(song: LocalSong, onClick: () -> Unit) {
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
                    maxLines = 1, // 限制行数
                    overflow = TextOverflow.Ellipsis
                )

                // 辅助信息
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
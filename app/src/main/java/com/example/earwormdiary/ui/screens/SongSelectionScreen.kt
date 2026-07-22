package com.example.earwormdiary.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.earwormdiary.data.model.LocalSong
import com.example.earwormdiary.data.model.RemoteSongSource
import com.example.earwormdiary.data.network.NeteaseApi
import com.example.earwormdiary.data.network.QqMusicApi
import com.example.earwormdiary.ui.components.AlbumCover
import com.example.earwormdiary.utils.loadMusicFromCache
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val ONLINE_RESULT_LIMIT = 20

private enum class OnlineSearchProvider(
    val label: String,
    val loadingLabel: String,
    val idHint: String
) {
    NETEASE(
        label = "网易云音乐",
        loadingLabel = "正在连接网易云音乐...",
        idHint = "没找到的话，可直接输入网易云歌曲或播客 ID 进行精确匹配。"
    ),
    QQ_MUSIC(
        label = "QQ 音乐",
        loadingLabel = "正在连接 QQ 音乐...",
        idHint = "没找到的话，可直接输入 QQ 音乐歌曲 ID 进行精确匹配。"
    )
}

private data class NetworkSearchResult(
    val songs: List<LocalSong>,
    val idMatchedKeys: Set<String>
)

@Composable
fun SongSelectionView(
    targetDate: java.time.LocalDate,
    folderUris: List<Uri>,
    allowNoneSelection: Boolean = true,
    excludedSongs: List<LocalSong> = emptyList(),
    onSongSelected: (LocalSong) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var allSongs by remember { mutableStateOf<List<LocalSong>>(emptyList()) }
    var filteredSongs by remember { mutableStateOf<List<LocalSong>>(emptyList()) }

    var networkSongs by remember { mutableStateOf<List<LocalSong>>(emptyList()) }
    var isSearchingNetwork by remember { mutableStateOf(false) }
    var hasSearchedNetwork by remember { mutableStateOf(false) }
    var idMatchedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedProvider by remember { mutableStateOf<OnlineSearchProvider?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    val excludedSongKeys = remember(excludedSongs) { excludedSongs.map(::songSelectionKey).toSet() }

    LaunchedEffect(Unit) {
        isLoading = true
        try {
            allSongs = loadMusicFromCache(context)
            filteredSongs = allSongs.filterNot { excludedSongKeys.contains(songSelectionKey(it)) }
        } catch (exception: Exception) {
            exception.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(searchQuery, allSongs, excludedSongKeys) {
        hasSearchedNetwork = false
        networkSongs = emptyList()
        idMatchedKeys = emptySet()
        selectedProvider = null

        filteredSongs = if (searchQuery.isBlank()) {
            allSongs.filterNot { excludedSongKeys.contains(songSelectionKey(it)) }
        } else {
            allSongs.filter {
                !excludedSongKeys.contains(songSelectionKey(it)) && (
                    it.title.contains(searchQuery, ignoreCase = true) ||
                        it.artist.contains(searchQuery, ignoreCase = true)
                )
            }
        }
    }

    fun performNetworkSearch(provider: OnlineSearchProvider) {
        val query = searchQuery.trim()
        if (query.isBlank()) return

        isSearchingNetwork = true
        idMatchedKeys = emptySet()
        selectedProvider = provider

        scope.launch {
            val result = when (provider) {
                OnlineSearchProvider.NETEASE -> searchNetease(query)
                OnlineSearchProvider.QQ_MUSIC -> searchQqMusic(query)
            }

            val visibleSongs = result.songs
                .filterNot { excludedSongKeys.contains(songSelectionKey(it)) }
                .take(ONLINE_RESULT_LIMIT)
            val visibleMatchKeys = visibleSongs
                .map(::networkSongKey)
                .filter(result.idMatchedKeys::contains)
                .toSet()

            networkSongs = visibleSongs
            idMatchedKeys = visibleMatchKeys
            isSearchingNetwork = false
            hasSearchedNetwork = true

            if (visibleSongs.isEmpty()) {
                Toast.makeText(context, "未找到相关网络歌曲", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
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
                    if (allowNoneSelection) {
                        item(key = "special_none") {
                            SongListItem(
                                song = LocalSong.createNone(),
                                onClick = { onSongSelected(LocalSong.createNone()) }
                            )
                        }
                    }

                    if (searchQuery.isNotBlank()) {
                        item(key = "special_text_${searchQuery}") {
                            val textSong = LocalSong.createText(searchQuery)
                            if (!excludedSongKeys.contains(songSelectionKey(textSong))) {
                                SongListItem(
                                    song = textSong,
                                    onClick = { onSongSelected(textSong) }
                                )
                            }
                        }
                    }

                    if (filteredSongs.isNotEmpty()) {
                        item("local_header") {
                            Text(
                                text = "本地结果",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Gray,
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                            )
                        }
                        items(filteredSongs, key = ::songItemStableKey) { song ->
                            SongListItem(song = song, onClick = { onSongSelected(song) })
                        }
                    }

                    if (searchQuery.isNotBlank()) {
                        item("network_divider") {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }

                        if (!hasSearchedNetwork && !isSearchingNetwork) {
                            item("network_search_action") {
                                val visibleProviders = selectedProvider?.let(::listOf) ?: OnlineSearchProvider.entries
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    visibleProviders.forEach { provider ->
                                        Surface(
                                            onClick = { performNetworkSearch(provider) },
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Cloud,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "${provider.label}搜索",
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (isSearchingNetwork) {
                            item("network_loading") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = selectedProvider?.loadingLabel.orEmpty(),
                                        color = Color.Gray
                                    )
                                }
                            }
                        } else {
                            item("network_header") {
                                Text(
                                    text = "网络结果（${selectedProvider?.label.orEmpty()}）",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                                )
                            }

                            if (networkSongs.isEmpty()) {
                                item("network_empty") {
                                    Text(
                                        text = "没有找到结果",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                                    )
                                }
                            } else {
                                items(networkSongs, key = ::songItemStableKey) { song ->
                                    val isIdMatch = networkSongKey(song) in idMatchedKeys

                                    Column {
                                        if (isIdMatch) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Link,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = preciseMatchLabel(song),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }

                                        SongListItem(
                                            song = song,
                                            onClick = { onSongSelected(song) },
                                            backgroundColor = if (isIdMatch) {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                            } else {
                                                Color.Transparent
                                            }
                                        )
                                    }
                                }

                                if (idMatchedKeys.isEmpty()) {
                                    item("network_id_hint") {
                                        Text(
                                            text = selectedProvider?.idHint.orEmpty(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
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
}

@Composable
fun SongListItem(
    song: LocalSong,
    onClick: () -> Unit,
    backgroundColor: Color = Color.Transparent
) {
    Surface(
        onClick = onClick,
        color = backgroundColor,
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

private suspend fun searchNetease(query: String): NetworkSearchResult = coroutineScope {
    val keywordSearch = async { NeteaseApi.searchSongs(query, ONLINE_RESULT_LIMIT) }
    val exactSong = if (looksLikeNeteaseId(query)) async { NeteaseApi.getSongDetail(query) } else null
    val exactPodcast = if (looksLikeNeteaseId(query)) async { NeteaseApi.getPodcastDetail(query) } else null

    val exactMatches = buildList {
        exactSong?.await()?.let(::add)
        exactPodcast?.await()?.let(::add)
    }
    val mergedSongs = dedupeNetworkSongs(exactMatches + keywordSearch.await())

    NetworkSearchResult(
        songs = mergedSongs,
        idMatchedKeys = exactMatches.map(::networkSongKey).toSet()
    )
}

private suspend fun searchQqMusic(query: String): NetworkSearchResult = coroutineScope {
    val keywordSearch = async { QqMusicApi.searchSongs(query, ONLINE_RESULT_LIMIT) }
    val exactSong = if (looksLikeQqMusicId(query)) async { QqMusicApi.getSongDetail(query) } else null

    val exactMatches = listOfNotNull(exactSong?.await())
    val mergedSongs = dedupeNetworkSongs(exactMatches + keywordSearch.await())

    NetworkSearchResult(
        songs = mergedSongs,
        idMatchedKeys = exactMatches.map(::networkSongKey).toSet()
    )
}

private fun dedupeNetworkSongs(songs: List<LocalSong>): List<LocalSong> {
    val seenKeys = mutableSetOf<String>()
    return songs.filter { song -> seenKeys.add(networkSongKey(song)) }
}

private fun preciseMatchLabel(song: LocalSong): String {
    val remoteId = song.displayRemoteId ?: song.id.toString()
    return when (song.remoteSource) {
        RemoteSongSource.NETEASE -> "网易云歌曲 ID 精确匹配: $remoteId"
        RemoteSongSource.NETEASE_PODCAST -> "网易云播客 ID 精确匹配: $remoteId"
        RemoteSongSource.QQ_MUSIC -> "QQ 音乐 ID 精确匹配: $remoteId"
        else -> "ID 精确匹配: $remoteId"
    }
}

private fun looksLikeNeteaseId(query: String): Boolean {
    return query.all(Char::isDigit)
}

private fun looksLikeQqMusicId(query: String): Boolean {
    val trimmed = query.trim()
    return trimmed.length in 8..20 &&
        trimmed.all { it.isLetterOrDigit() } &&
        trimmed.any { it.isLetter() }
}

private fun songSelectionKey(song: LocalSong): String {
    return when {
        song.isNone -> "none"
        song.isText -> "text:${song.title.trim().lowercase()}"
        else -> {
            val normalizedTitle = song.title.trim().lowercase()
            val normalizedArtist = song.artist.trim().lowercase()
            "song:$normalizedTitle::$normalizedArtist"
        }
    }
}

private fun songItemStableKey(song: LocalSong): String {
    return when {
        song.isNone -> "special:none"
        song.isText -> "special:text:${song.title.trim().lowercase()}"
        song.isRemote -> networkSongKey(song)
        else -> "local:${song.id}:${song.uri}"
    }
}

private fun networkSongKey(song: LocalSong): String {
    val source = song.remoteSource ?: "REMOTE"
    val remoteId = song.displayRemoteId ?: song.id.toString()
    return "$source:$remoteId"
}

package com.example.earwormdiary

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.net.URLDecoder

@Composable
fun SettingsScreen(
    folderUris: List<Uri>,
    onAddFolder: (Uri) -> Unit,
    onRemoveFolder: (Uri) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isScanning by remember { mutableStateOf(false) }

    // 重建索引
    fun rebuildIndex() {
        if (folderUris.isEmpty()) {
            Toast.makeText(context, "请先添加文件夹", Toast.LENGTH_SHORT).show()
            return
        }
        isScanning = true

        scope.launch {
            val songs = buildMusicIndex(context, folderUris)
            isScanning = false
            Toast.makeText(context, "扫描完成！共索引 ${songs.size} 首歌", Toast.LENGTH_LONG).show()
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            persistUriPermission(context, uri)
            onAddFolder(uri)
            // 添加后建议用户刷新
            Toast.makeText(context, "添加成功，请点击“刷新索引”", Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("音乐库管理", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "添加或删除文件夹后，必须点击下方的「刷新索引」按钮，否则搜索不到歌曲。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }

        // 文件夹列表
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(folderUris, key = { it.toString() }) { uri ->
                FolderItem(uri = uri, onRemove = { onRemoveFolder(uri) })
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isScanning) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text("正在建立索引...", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // 添加按钮
            Button(
                onClick = { folderPicker.launch(null) },
                modifier = Modifier.weight(1f),
                enabled = !isScanning
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加文件夹")
            }

            // 刷新索引按钮
            FilledTonalButton(
                onClick = { rebuildIndex() },
                modifier = Modifier.weight(1f),
                enabled = !isScanning
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("刷新索引")
            }
        }
    }
}

@Composable
fun FolderItem(uri: Uri, onRemove: () -> Unit) {
    val path = try {
        URLDecoder.decode(uri.toString(), "UTF-8").substringAfterLast(":")
    } catch (e: Exception) {
        uri.lastPathSegment ?: uri.toString()
    }
    Card(elevation = CardDefaults.cardElevation(2.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = Color.Gray)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = path, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color.Red) }
        }
    }
}
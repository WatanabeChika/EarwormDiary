package com.example.earwormdiary.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.earwormdiary.data.local.RecordStorage
import com.example.earwormdiary.data.model.Category
import com.example.earwormdiary.data.model.DailyRecord
import com.example.earwormdiary.utils.buildMusicIndex
import java.net.URLDecoder
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun SettingsMenuScreen(
    onNavigateToLibrary: () -> Unit,
    onNavigateToCategory: () -> Unit,
    onNavigateToBackup: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        SettingsMenuItem(
            icon = Icons.Default.Folder,
            title = "音乐库管理",
            subtitle = "添加文件夹并重建索引",
            onClick = onNavigateToLibrary
        )

        Spacer(modifier = Modifier.height(16.dp))

        SettingsMenuItem(
            icon = Icons.AutoMirrored.Filled.Label,
            title = "类别管理",
            subtitle = "维护歌曲类别标签",
            onClick = onNavigateToCategory
        )

        Spacer(modifier = Modifier.height(16.dp))

        SettingsMenuItem(
            icon = Icons.Default.ImportExport,
            title = "数据备份与恢复",
            subtitle = "导出 JSON，或从文件导入",
            onClick = onNavigateToBackup
        )
    }
}

@Composable
fun SettingsMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color.Gray
            )
        }
    }
}

@Composable
fun LibrarySettingsScreen(
    folderUris: List<Uri>,
    records: Map<LocalDate, DailyRecord>,
    onAddFolder: (Uri) -> Unit,
    onRemoveFolder: (Uri) -> Unit,
    onRecordsUpdated: (Map<LocalDate, DailyRecord>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isScanning by remember { mutableStateOf(false) }
    var showWarningDialog by remember { mutableStateOf(false) }
    var warningList by remember { mutableStateOf<List<String>>(emptyList()) }
    var scanResultMsg by remember { mutableStateOf("") }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                onAddFolder(uri)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "无法获取文件夹权限", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun rebuildIndex() {
        if (folderUris.isEmpty()) {
            Toast.makeText(context, "请先添加文件夹", Toast.LENGTH_SHORT).show()
            return
        }

        isScanning = true
        scope.launch {
            val scannedSongs = buildMusicIndex(context, folderUris)
            val localSongGroups = scannedSongs.groupBy { it.title }

            var fixCount = 0
            val newRecords = records.toMutableMap()
            val currentWarnings = mutableListOf<String>()

            records.forEach { (date, record) ->
                var recordChanged = false
                val updatedEntries = record.entries.mapIndexed { index, entry ->
                    val currentSong = entry.song
                    val needsUpgrade = currentSong.isText || currentSong.uri.toString().startsWith("http")
                    if (!needsUpgrade) {
                        entry
                    } else {
                        val matches = localSongGroups[currentSong.title]
                        if (matches.isNullOrEmpty()) {
                            entry
                        } else {
                            val replacement = if (matches.size == 1) {
                                matches[0]
                            } else {
                                val artistMatch = matches.find {
                                    it.artist.equals(currentSong.artist, ignoreCase = true) ||
                                        (currentSong.artist.isNotBlank() &&
                                            it.artist.contains(currentSong.artist, ignoreCase = true))
                                }

                                artistMatch ?: matches[0].also { fallback ->
                                    val typeStr = if (currentSong.isText) "纯文字" else "网络歌曲"
                                    currentWarnings.add(
                                        "日期 $date 第${index + 1}首 [${currentSong.title}] ($typeStr) 匹配到多个本地文件，已默认关联: ${fallback.artist}"
                                    )
                                }
                            }

                            if (replacement != currentSong) {
                                recordChanged = true
                                fixCount++
                                entry.copy(song = replacement)
                            } else {
                                entry
                            }
                        }
                    }
                }

                if (recordChanged) {
                    newRecords[date] = record.copy(entries = updatedEntries)
                }
            }

            scanResultMsg = "扫描完成，共索引 ${scannedSongs.size} 首歌，自动优化 $fixCount 条记录。"

            if (fixCount > 0) {
                onRecordsUpdated(newRecords)
                if (currentWarnings.isNotEmpty()) {
                    warningList = currentWarnings
                    showWarningDialog = true
                } else {
                    Toast.makeText(context, scanResultMsg, Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(context, "扫描完成，共索引 ${scannedSongs.size} 首歌。", Toast.LENGTH_SHORT).show()
            }

            isScanning = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(folderUris) { uri ->
                FolderItem(uri = uri, onRemove = { onRemoveFolder(uri) })
            }

            if (folderUris.isEmpty()) {
                item {
                    Text("暂未添加文件夹", color = Color.Gray, modifier = Modifier.padding(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isScanning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = { folderPicker.launch(null) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("添加文件夹")
            }

            Button(
                onClick = { rebuildIndex() },
                modifier = Modifier.weight(1f),
                enabled = !isScanning
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("刷新索引")
            }
        }
    }

    if (showWarningDialog) {
        AlertDialog(
            onDismissRequest = { showWarningDialog = false },
            title = { Text("智能修复完成") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = scanResultMsg,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "以下歌曲因本地存在重名文件且无法精确匹配，已默认选择第一项：",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    warningList.forEach { msg ->
                        Text(
                            text = "• $msg",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWarningDialog = false }) {
                    Text("知道了")
                }
            }
        )
    }
}

@Composable
fun FolderItem(uri: Uri, onRemove: () -> Unit) {
    val path = try {
        URLDecoder.decode(uri.toString(), "UTF-8").substringAfterLast(":")
    } catch (e: Exception) {
        uri.lastPathSegment ?: uri.toString()
    }

    Card(
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = Color.Gray)
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = path,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color.Red)
            }
        }
    }
}

@Composable
fun DataBackupScreen(
    records: Map<LocalDate, DailyRecord>,
    categories: List<Category>,
    onImportRecords: (Map<LocalDate, DailyRecord>) -> Unit,
    onCategoriesChanged: (List<Category>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isImporting by remember { mutableStateOf(false) }
    var showWarningDialog by remember { mutableStateOf(false) }
    var warningList by remember { mutableStateOf<List<String>>(emptyList()) }
    var importSuccessMsg by remember { mutableStateOf("") }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            RecordStorage.exportDataToUri(context, uri, records, categories)
            Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isImporting = true
            scope.launch {
                val (newRecords, newCategories, warnings) = RecordStorage.importDataFromUri(context, uri)

                if (newRecords.isNotEmpty()) {
                    onImportRecords(newRecords)
                    onCategoriesChanged(newCategories)
                    importSuccessMsg = "成功导入 ${newRecords.size} 条记录。"

                    if (warnings.isNotEmpty()) {
                        warningList = warnings
                        showWarningDialog = true
                    } else {
                        Toast.makeText(context, importSuccessMsg, Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(context, "导入失败或文件为空", Toast.LENGTH_SHORT).show()
                }
                isImporting = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("当前记录总数", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${records.size} 条",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Text(
                    "支持 .json 格式的数据迁移",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (isImporting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                "正在解析数据并匹配歌曲...",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = {
                val fileName = "EarwormDiary_Backup_${LocalDate.now()}.json"
                createDocumentLauncher.launch(fileName)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isImporting
        ) {
            Icon(Icons.Default.SaveAlt, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text("导出数据")
        }

        Spacer(modifier = Modifier.height(16.dp))

        FilledTonalButton(
            onClick = { openDocumentLauncher.launch(arrayOf("application/json")) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isImporting
        ) {
            Icon(Icons.Default.FileOpen, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text("导入数据")
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "注意：导入会覆盖相同日期的现有记录；如果本地没有对应歌曲文件，会自动退化为纯文字记录。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }

    if (showWarningDialog) {
        AlertDialog(
            onDismissRequest = { showWarningDialog = false },
            title = { Text("导入完成") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        importSuccessMsg,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "以下歌曲因本地存在重名文件且无法精确匹配，已默认选择第一项：",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    warningList.forEach { msg ->
                        Text(
                            text = "• $msg",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWarningDialog = false }) {
                    Text("知道了")
                }
            }
        )
    }
}

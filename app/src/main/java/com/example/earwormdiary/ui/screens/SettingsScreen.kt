package com.example.earwormdiary.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.earwormdiary.data.local.RecordStorage
import com.example.earwormdiary.data.model.Category
import com.example.earwormdiary.data.model.DailyRecord
import com.example.earwormdiary.utils.CalendarImageExporter
import com.example.earwormdiary.utils.buildMusicIndex
import java.net.URLDecoder
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
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
            title = "数据导出与导入",
            subtitle = "导出 JSON 或日历图片，也可从 JSON 导入",
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
                                        (
                                            currentSong.artist.isNotBlank() &&
                                                it.artist.contains(currentSong.artist, ignoreCase = true)
                                            )
                                }

                                artistMatch ?: matches[0].also { fallback ->
                                    val typeStr = if (currentSong.isText) "纯文字" else "网络歌曲"
                                    currentWarnings.add(
                                        "日期 $date 第 ${index + 1} 首《${currentSong.title}》($typeStr) 匹配到多个本地文件，已默认关联到 ${fallback.artist}"
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

            scanResultMsg = "扫描完成，共索引 ${scannedSongs.size} 首歌，自动修复 $fixCount 条记录。"

            if (fixCount > 0) {
                onRecordsUpdated(newRecords)
                if (currentWarnings.isNotEmpty()) {
                    warningList = currentWarnings
                    showWarningDialog = true
                } else {
                    Toast.makeText(context, scanResultMsg, Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(
                    context,
                    "扫描完成，共索引 ${scannedSongs.size} 首歌。",
                    Toast.LENGTH_SHORT
                ).show()
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
                    Text(
                        "还没有添加文件夹",
                        color = Color.Gray,
                        modifier = Modifier.padding(8.dp)
                    )
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
    } catch (_: Exception) {
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

private enum class JsonExportMode {
    ALL,
    YEAR,
    CUSTOM
}

private data class JsonExportRequest(
    val fileName: String,
    val records: Map<LocalDate, DailyRecord>
)

private data class CalendarExportRequest(
    val startMonth: YearMonth,
    val endMonth: YearMonth
)

@Composable
fun DataBackupScreen(
    records: Map<LocalDate, DailyRecord>,
    categories: List<Category>,
    onImportRecords: (Map<LocalDate, DailyRecord>) -> Unit,
    onCategoriesChanged: (List<Category>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val sortedDates = records.keys.sorted()
    val earliestDate = sortedDates.firstOrNull()
    val latestDate = sortedDates.lastOrNull()
    val availableYears = sortedDates.map { it.year }.distinct().sortedDescending()
    val quickYears = if (availableYears.size > 3) availableYears.take(3) else availableYears
    val latestMonth = latestDate?.let { YearMonth.from(it) } ?: YearMonth.now()

    var jsonExportMode by remember { mutableStateOf(JsonExportMode.ALL) }
    var selectedYear by remember(availableYears) {
        mutableIntStateOf(availableYears.firstOrNull() ?: LocalDate.now().year)
    }
    var customStartDate by remember(earliestDate) { mutableStateOf(earliestDate ?: LocalDate.now()) }
    var customEndDate by remember(latestDate) { mutableStateOf(latestDate ?: LocalDate.now()) }
    var calendarStartMonth by remember(latestMonth) { mutableStateOf(latestMonth) }
    var calendarEndMonth by remember(latestMonth) { mutableStateOf(latestMonth) }

    var pendingJsonExport by remember { mutableStateOf<JsonExportRequest?>(null) }
    var pendingCalendarExport by remember { mutableStateOf<CalendarExportRequest?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var isExportingCalendar by remember { mutableStateOf(false) }
    var showWarningDialog by remember { mutableStateOf(false) }
    var warningList by remember { mutableStateOf<List<String>>(emptyList()) }
    var importSuccessMsg by remember { mutableStateOf("") }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showStartMonthPicker by remember { mutableStateOf(false) }
    var showEndMonthPicker by remember { mutableStateOf(false) }

    val jsonRange = when (jsonExportMode) {
        JsonExportMode.ALL -> earliestDate to latestDate
        JsonExportMode.YEAR -> LocalDate.of(selectedYear, 1, 1) to LocalDate.of(selectedYear, 12, 31)
        JsonExportMode.CUSTOM -> customStartDate to customEndDate
    }

    val filteredRecords = run {
        val (startDate, endDate) = jsonRange
        when (jsonExportMode) {
            JsonExportMode.ALL -> records.toSortedMap()
            JsonExportMode.YEAR,
            JsonExportMode.CUSTOM -> {
                if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
                    emptyMap()
                } else {
                    records
                        .filterKeys { !it.isBefore(startDate) && !it.isAfter(endDate) }
                        .toSortedMap()
                }
            }
        }
    }

    val jsonRangeIsValid = when (jsonExportMode) {
        JsonExportMode.CUSTOM -> !customEndDate.isBefore(customStartDate)
        else -> true
    }

    val calendarMonthCount = run {
        if (calendarEndMonth.isBefore(calendarStartMonth)) {
            0L
        } else {
            ChronoUnit.MONTHS.between(calendarStartMonth.atDay(1), calendarEndMonth.atDay(1)) + 1
        }
    }
    val calendarRangeIsValid = calendarMonthCount in 1..12

    fun buildJsonFileName(): String {
        val suffix = when (jsonExportMode) {
            JsonExportMode.ALL -> "all"
            JsonExportMode.YEAR -> selectedYear.toString()
            JsonExportMode.CUSTOM -> "${customStartDate}_to_${customEndDate}"
        }
        return "EarwormDiary_Backup_$suffix.json"
    }

    fun performCalendarExport(request: CalendarExportRequest) {
        isExportingCalendar = true
        scope.launch {
            try {
                val result = CalendarImageExporter.exportMonths(
                    context = context,
                    records = records,
                    startMonth = request.startMonth,
                    endMonth = request.endMonth
                )
                val message = "日历图片已保存到 ${result.relativePath}"
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                val message = "导出失败：${e.message ?: "未知错误"}"
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            } finally {
                isExportingCalendar = false
                pendingCalendarExport = null
            }
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val request = pendingJsonExport
        if (uri != null && request != null) {
            val exported = RecordStorage.exportDataToUri(context, uri, request.records, categories)
            val message = if (exported) {
                "JSON 导出成功，共 ${request.records.size} 条记录。"
            } else {
                "JSON 导出失败。"
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
        pendingJsonExport = null
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

    val writePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val request = pendingCalendarExport
        if (granted && request != null) {
            performCalendarExport(request)
        } else if (!granted) {
            pendingCalendarExport = null
            Toast.makeText(context, "没有存储权限，无法保存图片", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchJsonExport() {
        if (!jsonRangeIsValid) {
            Toast.makeText(context, "请选择有效的日期范围", Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = buildJsonFileName()
        pendingJsonExport = JsonExportRequest(
            fileName = fileName,
            records = filteredRecords
        )
        createDocumentLauncher.launch(fileName)
    }

    fun launchCalendarExport() {
        if (!calendarRangeIsValid) {
            Toast.makeText(context, "请选择 1 到 12 个月的有效范围", Toast.LENGTH_SHORT).show()
            return
        }

        val request = CalendarExportRequest(
            startMonth = calendarStartMonth,
            endMonth = calendarEndMonth
        )
        pendingCalendarExport = request

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val permissionGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED

            if (!permissionGranted) {
                writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }

        performCalendarExport(request)
    }

    if (showStartMonthPicker) {
        YearMonthPickerDialog(
            initialYearMonth = calendarStartMonth,
            onDismissRequest = { showStartMonthPicker = false },
            onYearMonthSelected = {
                calendarStartMonth = it
                if (calendarEndMonth.isBefore(it)) {
                    calendarEndMonth = it
                }
                showStartMonthPicker = false
            }
        )
    }

    if (showEndMonthPicker) {
        YearMonthPickerDialog(
            initialYearMonth = calendarEndMonth,
            onDismissRequest = { showEndMonthPicker = false },
            onYearMonthSelected = {
                calendarEndMonth = it
                if (calendarStartMonth.isAfter(it)) {
                    calendarStartMonth = it
                }
                showEndMonthPicker = false
            }
        )
    }

    if (showStartDatePicker) {
        LocalDatePickerDialog(
            initialDate = customStartDate,
            minDate = earliestDate,
            maxDate = customEndDate,
            onDismissRequest = { showStartDatePicker = false },
            onDateSelected = {
                customStartDate = it
                showStartDatePicker = false
            }
        )
    }

    if (showEndDatePicker) {
        LocalDatePickerDialog(
            initialDate = customEndDate,
            minDate = customStartDate,
            maxDate = latestDate,
            onDismissRequest = { showEndDatePicker = false },
            onDateSelected = {
                customEndDate = it
                showEndDatePicker = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("当前记录总数", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = records.size.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Text(
                    text = when {
                        earliestDate != null && latestDate != null -> "数据范围：$earliestDate 至 $latestDate"
                        else -> "当前还没有可导出的记录"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }
        }

        ExportSectionCard(
            title = "导出耳虫日历图片",
            subtitle = "按月选择，最少 1 个月，最多 12 个月。图片会保存到 Pictures/EarwormDiary。"
        ) {
            if (isExportingCalendar) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "正在生成并保存日历图片…",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { showStartMonthPicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("开始月份：$calendarStartMonth")
                }

                OutlinedButton(
                    onClick = { showEndMonthPicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("结束月份：$calendarEndMonth")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (calendarRangeIsValid) {
                    "将导出 $calendarMonthCount 个月的日历图片。"
                } else {
                    "请选择 1 到 12 个月的有效范围。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (calendarRangeIsValid) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { launchCalendarExport() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isImporting && !isExportingCalendar && calendarRangeIsValid
            ) {
                Icon(Icons.Default.Image, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("导出日历图片")
            }
        }

        ExportSectionCard(
            title = "导出 JSON 数据",
            subtitle = "默认导出全部，也可以按年份快捷导出，或手动指定起止日期。"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = jsonExportMode == JsonExportMode.ALL,
                            onClick = { jsonExportMode = JsonExportMode.ALL },
                            label = { Text("全部") }
                        )
                    }
                    items(quickYears) { year ->
                        FilterChip(
                            selected = jsonExportMode == JsonExportMode.YEAR && selectedYear == year,
                            onClick = {
                                selectedYear = year
                                jsonExportMode = JsonExportMode.YEAR
                            },
                            label = { Text(year.toString()) }
                        )
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = jsonExportMode == JsonExportMode.CUSTOM,
                            onClick = { jsonExportMode = JsonExportMode.CUSTOM },
                            label = { Text("自定义日期") }
                        )
                    }
                }
            }

            if (jsonExportMode == JsonExportMode.CUSTOM) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { showStartDatePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("开始日期：$customStartDate")
                    }

                    OutlinedButton(
                        onClick = { showEndDatePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("结束日期：$customEndDate")
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val jsonRangeText = when (jsonExportMode) {
                JsonExportMode.ALL -> if (earliestDate != null && latestDate != null) {
                    "全部数据：$earliestDate 至 $latestDate"
                } else {
                    "全部数据：当前为空"
                }
                JsonExportMode.YEAR -> "整年导出：$selectedYear-01-01 至 $selectedYear-12-31"
                JsonExportMode.CUSTOM -> "自定义范围：$customStartDate 至 $customEndDate"
            }

            Text(
                text = "$jsonRangeText，共 ${filteredRecords.size} 条记录。",
                style = MaterialTheme.typography.bodyMedium,
                color = if (jsonRangeIsValid) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
            )

            if (!jsonRangeIsValid) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "结束日期不能早于开始日期。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { launchJsonExport() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isImporting && !isExportingCalendar && jsonRangeIsValid
            ) {
                Icon(Icons.Default.SaveAlt, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("导出 JSON 数据")
            }
        }

        ExportSectionCard(
            title = "从 JSON 导入数据",
            subtitle = "导入时会覆盖相同日期的记录；本地找不到对应歌曲时会自动降级为纯文字记录。"
        ) {
            if (isImporting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "正在解析数据并匹配歌曲…",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.size(8.dp))

            FilledTonalButton(
                onClick = { openDocumentLauncher.launch(arrayOf("application/json")) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isImporting && !isExportingCalendar
            ) {
                Icon(Icons.Default.FileOpen, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("导入 JSON 数据")
            }
        }

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

@Composable
private fun ExportSectionCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            content()
        }
    }
}

@Composable
private fun LocalDatePickerDialog(
    initialDate: LocalDate,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
    onDismissRequest: () -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    var selectionMode by remember { mutableStateOf(DateSelectionMode.YEAR) }
    var selectedYear by remember { mutableIntStateOf(initialDate.year) }
    var selectedMonth by remember { mutableIntStateOf(initialDate.monthValue) }
    var selectedDay by remember { mutableIntStateOf(initialDate.dayOfMonth) }

    val yearStart = minDate?.year ?: (initialDate.year - 50)
    val yearEnd = maxDate?.year ?: (initialDate.year + 50)
    val years = remember(yearStart, yearEnd) { (yearStart..yearEnd).toList() }
    val yearListState = rememberLazyGridState(
        initialFirstVisibleItemIndex = (years.indexOf(selectedYear) - 6).coerceAtLeast(0)
    )

    val daysInMonth = remember(selectedYear, selectedMonth) {
        YearMonth.of(selectedYear, selectedMonth).lengthOfMonth()
    }
    if (selectedDay > daysInMonth) {
        selectedDay = daysInMonth
    }

    fun isSelectableDate(year: Int, month: Int, day: Int): Boolean {
        val date = LocalDate.of(year, month, day)
        if (minDate != null && date.isBefore(minDate)) return false
        if (maxDate != null && date.isAfter(maxDate)) return false
        return true
    }

    val availableMonths = (1..12).filter { month ->
        val firstDay = LocalDate.of(selectedYear, month, 1)
        val lastDay = YearMonth.of(selectedYear, month).atEndOfMonth()
        (minDate == null || !lastDay.isBefore(minDate)) &&
            (maxDate == null || !firstDay.isAfter(maxDate))
    }
    val availableDays = (1..daysInMonth).filter { day ->
        isSelectableDate(selectedYear, selectedMonth, day)
    }
    if (selectedMonth !in availableMonths && availableMonths.isNotEmpty()) {
        selectedMonth = availableMonths.first()
    }
    if (selectedDay !in availableDays && availableDays.isNotEmpty()) {
        selectedDay = availableDays.first()
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { onDateSelected(LocalDate.now().coerceIn(minDate, maxDate)) }) {
                    Text("跳到今天")
                }
                TextButton(onClick = onDismissRequest) {
                    Text("取消")
                }
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { selectionMode = DateSelectionMode.YEAR },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = if (selectionMode == DateSelectionMode.YEAR) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Gray
                        }
                    )
                ) {
                    Text(
                        text = "${selectedYear}年",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = if (selectionMode == DateSelectionMode.YEAR) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        }
                    )
                }
                Text("/", style = MaterialTheme.typography.headlineSmall, color = Color.Gray)
                TextButton(
                    onClick = { selectionMode = DateSelectionMode.MONTH },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = if (selectionMode == DateSelectionMode.MONTH) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Gray
                        }
                    )
                ) {
                    Text(
                        text = "${selectedMonth}月",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = if (selectionMode == DateSelectionMode.MONTH) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        }
                    )
                }
                Text("/", style = MaterialTheme.typography.headlineSmall, color = Color.Gray)
                TextButton(
                    onClick = { selectionMode = DateSelectionMode.DAY },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = if (selectionMode == DateSelectionMode.DAY) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Gray
                        }
                    )
                ) {
                    Text(
                        text = "${selectedDay}日",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = if (selectionMode == DateSelectionMode.DAY) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        }
                    )
                }
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .width(300.dp)
                    .height(320.dp)
            ) {
                when (selectionMode) {
                    DateSelectionMode.YEAR -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            state = yearListState,
                            contentPadding = PaddingValues(4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(years) { year ->
                                val isSelected = year == selectedYear
                                OutlinedButton(
                                    onClick = {
                                        selectedYear = year
                                        selectionMode = DateSelectionMode.MONTH
                                    },
                                    colors = if (isSelected) {
                                        androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer
                                        )
                                    } else {
                                        androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                                    },
                                    border = if (isSelected) {
                                        androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        null
                                    }
                                ) {
                                    Text(
                                        text = year.toString(),
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Clip
                                    )
                                }
                            }
                        }
                    }

                    DateSelectionMode.MONTH -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(availableMonths) { month ->
                                val isSelected = month == selectedMonth
                                OutlinedButton(
                                    onClick = {
                                        selectedMonth = month
                                        selectionMode = DateSelectionMode.DAY
                                    },
                                    colors = if (isSelected) {
                                        androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                                        )
                                    } else {
                                        androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                                    }
                                ) {
                                    Text(
                                        text = "${month}月",
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Clip
                                    )
                                }
                            }
                        }
                    }

                    DateSelectionMode.DAY -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(availableDays) { day ->
                                val isSelected = day == selectedDay
                                OutlinedButton(
                                    onClick = {
                                        selectedDay = day
                                        onDateSelected(LocalDate.of(selectedYear, selectedMonth, day))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = if (isSelected) {
                                        androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer
                                        )
                                    } else {
                                        androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                                    }
                                ) {
                                    Text(
                                        text = "${day}日",
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Clip
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

private enum class DateSelectionMode {
    YEAR,
    MONTH,
    DAY
}

private fun LocalDate.coerceIn(minDate: LocalDate?, maxDate: LocalDate?): LocalDate {
    return when {
        minDate != null && this.isBefore(minDate) -> minDate
        maxDate != null && this.isAfter(maxDate) -> maxDate
        else -> this
    }
}
